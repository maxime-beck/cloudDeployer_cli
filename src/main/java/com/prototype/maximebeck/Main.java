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
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
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
import java.util.Properties;
import java.awt.Desktop;
import java.net.URI;

public class Main {
    private static final String DOCKER_AUTH_JSON_KEY_USER = "_json_key";

    private static String deployUrl;
    private static String exposeUrl;
    private static String routeUrl;

    private static CloseableHttpClient httpclient;
    private static DockerClient dockerClient;
    private static AuthConfig dockerAuth;
    private static String dockerImageTag = "";
    private static Provider provider;
    private static Mode mode;
    private static Properties props;

    public enum Provider {
        GCLOUD,
        AWS,
        AZURE,
        OPENSHIFT;
    }

    public enum Mode {
        MONOLITHIC,
        MICROSERVICE;
    }

    public static void main(String args[]) {
        try {
            init();

            if(mode.equals(Mode.MONOLITHIC))
              dockerBuild("./", dockerImageTag);
            else if (mode.equals(Mode.MICROSERVICE))
              dockerBuild(props.getProperty("TOMCAT_IN_THE_CLOUD_BASEDIR"), dockerImageTag);
            else {
              // TODO Implement logs and exception handlers
              System.out.println("Error : Mode property must be set to either MONOLITHIC or MICROSERVICE.");
            }

            dockerPush(dockerImageTag);
            deploy("./resources/deployment.json");
            expose("./resources/expose.json");

            if(provider.equals(Provider.OPENSHIFT))
                createRoute("./resources/route.json");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void initProperties() {
        // Properties file
        props = new Properties();
        InputStream input = null;

        try {
            input = new FileInputStream("./config/cloud.properties");
            // load a properties file
            props.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        deployUrl = "https://" + props.getProperty("HOST_ADDRESS") + "/apis/extensions/v1beta1/namespaces/" + props.getProperty("REGISTRY_ID") + "/deployments";
        exposeUrl = "https://" + props.getProperty("HOST_ADDRESS") + "/api/v1/namespaces/" + props.getProperty("REGISTRY_ID") + "/services";
        routeUrl = "https://" + props.getProperty("HOST_ADDRESS") + "/oapi/v1/namespaces/" + props.getProperty("REGISTRY_ID") + "/routes";
    }

    public static void init() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, IOException {
        initProperties();

        // Setting variables
        provider = Provider.valueOf(props.getProperty("PROVIDER"));
        mode = Mode.valueOf(props.getProperty("MODE"));

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

        if(props.getProperty("DOCKER_AUTH_FILE") != null)
            dockerAuth = dockerAuthconfig(props.getProperty("DOCKER_AUTH_FILE"));
        else if (props.getProperty("DOCKER_USERNAME") != null && props.getProperty("DOCKER_TOKEN") != null)
            dockerAuth = dockerAuthconfig(props.getProperty("DOCKER_USERNAME"), props.getProperty("DOCKER_TOKEN"));

        //TODO add support for Azure
        switch (provider) {
            case GCLOUD:
            case OPENSHIFT:
                dockerImageTag = props.getProperty("REGISTRY_ADDRESS") + "/" +
                                 props.getProperty("REGISTRY_ID") + "/" +
                                 props.getProperty("DOCKER_IMAGE_NAME") + ":" + props.getProperty("DOCKER_IMAGE_VERSION");
                break;
            case AWS:
                dockerImageTag = props.getProperty("REGISTRY_ID") + "." +
                                 props.getProperty("REGISTRY_ADDRESS") + "/" +
                                 props.getProperty("REPOSITORY_NAME") + ":" + props.getProperty("DOCKER_IMAGE_VERSION");
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
                .withRegistryAddress("https://" + props.getProperty("REGISTRY_ADDRESS"));
    }

    private static void deploy(String specFile) throws IOException {
        System.out.println("Deploying...");
        String specs = readFile(specFile, StandardCharsets.UTF_8);
        specs = specs.replace("$NAME" , props.getProperty("DEPLOYMENT_NAME"));
        specs = specs.replace("$IMAGE" , dockerImageTag);
        specs = specs.replace("$PORT" , props.getProperty("DEPLOYMENT_PORT"));
        specs = specs.replace("$REPLICAS" , props.getProperty("REPLICAS"));
        handleRequestPOST(deployUrl, specs);
    }

    private static void expose(String specFile) throws IOException {
        System.out.println("Exposing...");
        String specs = readFile(specFile, StandardCharsets.UTF_8);
        specs = specs.replace("$NAME" , props.getProperty("DEPLOYMENT_NAME"));
        specs = specs.replace("$DEPLOYED_PORT" , props.getProperty("DEPLOYMENT_PORT"));
        specs = specs.replace("$EXPOSED_PORT" , props.getProperty("EXPOSED_PORT"));
        handleRequestPOST(exposeUrl, specs);
    }

    private static void createRoute(String specFile) throws IOException {
        System.out.println("Creating route...");
        String specs = readFile(specFile, StandardCharsets.UTF_8);

        System.out.println(props.getProperty("HOST_ADDRESS"));
        String host_address = (props.getProperty("HOST_ADDRESS").split(":"))[0];
        System.out.println("Host Address : " + host_address);
        host_address = (host_address.split("-"))[0] + "-80-" + (host_address.split("-"))[2];

        String host = props.getProperty("DEPLOYMENT_NAME") + "-" +
                      props.getProperty("REGISTRY_ID") + "." +
                      host_address;
        specs = specs.replace("$SERVICE_NAME" , props.getProperty("DEPLOYMENT_NAME"));
        specs = specs.replace("$NAMESPACE" , props.getProperty("REGISTRY_ID"));
        specs = specs.replace("$HOST" , host);
        handleRequestPOST(routeUrl, specs);
        if (Desktop.isDesktopSupported()) {
          try {
            Desktop.getDesktop().browse(new URI("http://" + host));
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
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
        request.addHeader("Authorization", "Bearer " + props.getProperty("DOCKER_TOKEN"));
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
                .withBuildArg("registry_id", props.getProperty("REGISTRY_ID"))
                .withBuildArg("war", props.getProperty("WAR"))
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
