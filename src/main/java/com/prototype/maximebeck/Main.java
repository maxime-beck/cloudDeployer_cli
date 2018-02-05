package com.prototype.maximebeck;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.dockerjava.core.command.PushImageResultCallback;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;

public class Main {
    // User scoped properties
    private static final String PROVIDER = "OPENSHIFT";                                                         // GCLOUD, AWS, AZURE, OPENSHIFT
    private static final String PROVIDER_REGISTRY_DOMAIN = "10.33.144.141.xip.io";                              // gcloud : gcr.io, AWS : dkr.ecr.[zone].amazonaws.com, OpenShift : docker-registry-default.<ip>.xip.io
    private static final String DOCKER_REGISTRY_NAME = "docker-registry-default";                               // only for Openshift
    private static final String KUBERNETES_HOST_ADDRESS = "bela1:8443";
    private static final String REGISTRY_ID = "tomcat-in-the-cloud";                                            // PROJECT_ID on gcloud and Openshift
    private static final String REPOSITORY_NAME = "";                                                           // Only needed for AWS
    private static final String TOMCAT_IN_THE_CLOUD_BASEDIR = "tomcat-in-the-cloud";

    private static final String DOCKER_AUTH_FILE = "";
    private static final String DOCKER_USERNAME = "maxime";
    private static final String DOCKER_PASSWORD = "rzfq-_LxHZzt4Dp98UfT8m8L6v-vsgqcSvWOwyLPTys";

    private static final String DEPLOYMENT_NAME = "tomcat-deployer";
    private static final String DEPLOYMENT_PORT = "8080";
    private static final String EXPOSED_PORT = "80";
    private static final String REPLICAS = "3";
    private static final String ACCESS_TOKEN = "rzfq-_LxHZzt4Dp98UfT8m8L6v-vsgqcSvWOwyLPTys";
    // ----

    // Application scoped properties
    private static final String DEPLOY_URL = "https://" + KUBERNETES_HOST_ADDRESS + "/apis/extensions/v1beta1/namespaces/" + REGISTRY_ID + "/deployments";
    private static final String EXPOSE_URL = "https://" + KUBERNETES_HOST_ADDRESS + "/api/v1/namespaces/" + REGISTRY_ID + "/services";
    private static final String ROUTE_URL = "https://" + KUBERNETES_HOST_ADDRESS + "/oapi/v1/namespaces/" + REGISTRY_ID + "/routes";
    private static final String DOCKER_IMAGE_NAME = "tomcat-in-the-cloud";
    private static final String DOCKER_IMAGE_VERSION = "v1";
    private static final String DOCKER_AUTH_JSON_KEY_USER = "_json_key";

    private static CloseableHttpClient httpclient;
    private static DockerClient dockerClient;
    private static AuthConfig dockerAuth;
    private static String dockerImageTag = "";
    private static Provider provider;

    public enum Provider {
        GCLOUD,
        AWS,
        AZURE,
        OPENSHIFT;
    }

    public static void main(String args[]) {
        try {
            init();
            dockerBuild(TOMCAT_IN_THE_CLOUD_BASEDIR, dockerImageTag);
            dockerPush(dockerImageTag);
            deploy("./resources/deployment.json");
            expose("./resources/expose.json");

            if(provider.equals(Provider.OPENSHIFT))
                createRoute("./resources/route.json");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void init() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, IOException {
        // Provider
        provider = Provider.valueOf(PROVIDER);

        // HTTPS
        /** Solution with TrustSelfSignedStrategy() -> Works for AWS and GCloud | Doesn't for Openshift */
        //SSLContextBuilder builder = new SSLContextBuilder();
        //builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
        //SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
        //SSLConnectionSocketFactory sslsf = RelaxedSSLContext.getInstance().getSocketFactory();
        //httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();

        /** Solution by accepting all certificate (insecure) */
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(new KeyManager[0], new TrustManager[] {new DefaultTrustManager()}, new SecureRandom());
        httpclient = HttpClients.custom().setSSLContext(ctx).setSSLHostnameVerifier((new HostnameVerifier() {
            @Override
            public boolean verify(String arg0, SSLSession arg1) {
                return true;
            }
        })).build();

        // Docker
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        dockerClient = DockerClientBuilder.getInstance(config).build();
        dockerAuth = null;

        if(DOCKER_AUTH_FILE != "")
            dockerAuth = dockerAuthconfig(DOCKER_AUTH_FILE);
        else if (DOCKER_USERNAME != "" && DOCKER_PASSWORD != "")
            dockerAuth = dockerAuthconfig(DOCKER_USERNAME, DOCKER_PASSWORD);

        //TODO add support for Openshift and Azure
        switch (provider) {
            case GCLOUD:
            case OPENSHIFT:
                dockerImageTag = DOCKER_REGISTRY_NAME + "." + PROVIDER_REGISTRY_DOMAIN + "/" + REGISTRY_ID + "/" + DOCKER_IMAGE_NAME + ":" + DOCKER_IMAGE_VERSION;
                break;
            case AWS:
                dockerImageTag = REGISTRY_ID + "." + PROVIDER_REGISTRY_DOMAIN + "/" + REPOSITORY_NAME + ":" + DOCKER_IMAGE_VERSION;
                break;
        }
    }

    private static AuthConfig dockerAuthconfig(String authFile) throws IOException {
        return dockerAuthconfig(DOCKER_AUTH_JSON_KEY_USER, readFile(authFile, StandardCharsets.UTF_8));
    }

    private static AuthConfig dockerAuthconfig(String username, String password) throws IOException {
        return new AuthConfig()
                .withUsername(username)
                .withPassword(password)
                .withRegistryAddress("https://" + PROVIDER_REGISTRY_DOMAIN);
    }

    private static void deploy(String specFile) throws IOException {
        System.out.println("Deploying...");
        String specs = readFile(specFile, StandardCharsets.UTF_8);
        specs = specs.replace("$NAME" , DEPLOYMENT_NAME);
        specs = specs.replace("$IMAGE" , dockerImageTag);
        specs = specs.replace("$PORT" , DEPLOYMENT_PORT);
        specs = specs.replace("$REPLICAS" , REPLICAS);
        handleRequestPOST(DEPLOY_URL, specs);
    }

    private static void expose(String specFile) throws IOException {
        System.out.println("Exposing...");
        String specs = readFile(specFile, StandardCharsets.UTF_8);
        specs = specs.replace("$NAME" , DEPLOYMENT_NAME);
        specs = specs.replace("$DEPLOYED_PORT" , DEPLOYMENT_PORT);
        specs = specs.replace("$EXPOSED_PORT" , EXPOSED_PORT);
        handleRequestPOST(EXPOSE_URL, specs);
    }

    private static void createRoute(String specFile) throws IOException {
        System.out.println("Creating route...");
        String specs = readFile(specFile, StandardCharsets.UTF_8);
        specs = specs.replace("$SERVICE_NAME" , DEPLOYMENT_NAME);
        specs = specs.replace("$NAMESPACE" , REGISTRY_ID);
        specs = specs.replace("$HOST" , DEPLOYMENT_NAME + "-" + REGISTRY_ID + "." + PROVIDER_REGISTRY_DOMAIN);
        handleRequestPOST(ROUTE_URL, specs);
    }

    private static void handleRequestPOST(String url, String specs) throws IOException {
        CloseableHttpResponse response = null;
        try {
            response = sslRequestPOST(url, specs);
            print(response);
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
            response.close();
        }
    }

    private static void print(CloseableHttpResponse response) throws IOException {
        System.out.println(response.getStatusLine());
        HttpEntity entity = response.getEntity();
        BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
        String line = "";
        while ((line = rd.readLine()) != null) {
            System.out.println(line);
        }
        EntityUtils.consume(entity);
    }

    private static CloseableHttpResponse sslRequestPOST(String url, String jsonBody) throws IOException {
        HttpPost request = new HttpPost(url);
        request.addHeader("Content-Type", "application/json");
        request.addHeader("Accept", "application/json");
        request.addHeader("Authorization", "Bearer " + ACCESS_TOKEN);
        StringEntity entity_json = new StringEntity(jsonBody);
        request.setEntity(entity_json);
        return httpclient.execute(request);
    }

    private static CloseableHttpResponse sslRequestGET(String url) throws IOException {
        HttpGet request = new HttpGet(url);
        return httpclient.execute(request);
    }

    private static void dockerBuild(String dockerfileBaseDir, String tag) {
        File dockerFile = new File(dockerfileBaseDir);

        System.out.println("Building docker image...");
        BuildImageResultCallback callback = new BuildImageResultCallback() {
            @Override
            public void onNext(BuildResponseItem item) {
                System.out.println("" + item);
                super.onNext(item);
            }
        };

        dockerClient.buildImageCmd(dockerFile)
                .withTags(new HashSet<String>(Arrays.asList(tag)))
                .exec(callback).awaitImageId();
    }

    private static void dockerPush(String tag) {
        System.out.println("Pushing docker image...");
        PushImageResultCallback pushCallback = new PushImageResultCallback() {
            @Override
            public void onNext(PushResponseItem item) {
                System.out.println("" + item);
                super.onNext(item);
            }
        };

        if(dockerAuth != null)
            dockerClient.pushImageCmd(tag)
                    .withAuthConfig(dockerAuth)
                    .exec(pushCallback).awaitSuccess();
        else
            dockerClient.pushImageCmd(tag)
                    .exec(pushCallback).awaitSuccess();
    }

    static String readFile(String path, Charset encoding)
            throws IOException
    {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }
}