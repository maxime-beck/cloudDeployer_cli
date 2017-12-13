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

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;

public class Main {
    // User scoped properties
    private static final String KUBERNETES_HOST_IP = "35.195.68.233";
    private static final String PROJECT_ID = "tomcat-in-the-cloud";
    private static final String TOMCAT_IN_THE_CLOUD_BASEDIR = "/home/mbeck/work/undergit/tomcat-in-the-cloud_repos/tomcat-in-the-cloud";
    private static final String DOCKER_AUTH_FILE = "tomcat-in-the-cloud-8947ffac8c02.json";
    private static final String NODE_NAME = "tomcat-deployer-test-2";
    private static final String DEPLOYMENT_PORT = "8080";
    private static final String EXPOSED_PORT = "80";
    private static final String REPLICAS = "3";
    private static final String ACCESS_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4teDF2MTgiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjQyMGI1NjEzLWRmNDItMTFlNy1hYjQwLTQyMDEwYTg0MDE2NSIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.YP3UHBoy4bZSAgkA6pYeAv8fFnsKRxgzTmbD_itS13jC4ebxJDXok5KR9MQsbobyn2m4jTv7XsWcaTP9PBj7c9ezta2dRzx4vO2IDGw0ytNED71jnukZfehHwC2Fnve4h7paNYuO3anM2jeznEWcaJmCsWzcxBHm2YD0vWE0OQnigUHf_GJwRk1LlUZUm4Sj_J9hBX98iYyvX6O6duS4ueI26bz_Tj1KMmRvtzqzsv-z8Ua4Ved5-37fO-YqRpoCY3XuwRJn-Hl9Yqvilm_JfKXu0lNVes6IMojnnTJ17e8LBZ1_gx_WCq4djoJk-uarjCJfTNa9VODQVB_WwxZFJw";
    // ----

    // Application scoped properties
    private static final String DEPLOY_URL = "https://" + KUBERNETES_HOST_IP + "/apis/extensions/v1beta1/namespaces/default/deployments";
    private static final String EXPOSE_URL = "https://" + KUBERNETES_HOST_IP + "/api/v1/namespaces/default/services";
    private static final String DOCKER_IMAGE_TAG = "gcr.io/" + PROJECT_ID + "/tomcat-in-the-cloud:v1";

    private static CloseableHttpClient httpclient;
    private static DockerClient dockerClient;
    private static AuthConfig dockerAuth;

    public static void main(String args[]) {
        try {
            init();
            dockerBuild(TOMCAT_IN_THE_CLOUD_BASEDIR, DOCKER_IMAGE_TAG);
            dockerPush(DOCKER_IMAGE_TAG);
            deploy("./resources/deployment.json");
            expose("./resources/expose.json");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void init() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, IOException {
        // HTTP
        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
        httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();

        // Docker
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        dockerClient = DockerClientBuilder.getInstance(config).build();
        dockerAuth = dockerAuthconfig(DOCKER_AUTH_FILE);
    }

    private static AuthConfig dockerAuthconfig(String authFile) throws IOException {
        String password = readFile(authFile, StandardCharsets.UTF_8);
        return new AuthConfig()
                    .withUsername("_json_key")
                    .withPassword(password)
                    .withRegistryAddress("https://gcr.io");
    }

    private static void deploy(String specFile) throws IOException {
        System.out.println("Deploying...");
        String specs = readFile(specFile, StandardCharsets.UTF_8);
        specs = specs.replace("$NAME" , NODE_NAME);
        specs = specs.replace("$IMAGE" , DOCKER_IMAGE_TAG);
        specs = specs.replace("$PORT" , DEPLOYMENT_PORT);
        specs = specs.replace("$REPLICAS" , REPLICAS);
        handleRequestPOST(DEPLOY_URL, specs);
    }

    private static void expose(String specFile) throws IOException {
        System.out.println("Exposing...");
        String specs = readFile(specFile, StandardCharsets.UTF_8);
        specs = specs.replace("$NAME" , NODE_NAME);
        specs = specs.replace("$DEPLOYED_PORT" , DEPLOYMENT_PORT);
        specs = specs.replace("$EXPOSED_PORT" , EXPOSED_PORT);
        handleRequestPOST(EXPOSE_URL, specs);
    }

    private static void handleRequestPOST(String url, String specs) throws IOException {
        CloseableHttpResponse response = null;
        try {
            response = sslRequestPOST(url, specs);
            print(response);
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

        dockerClient.pushImageCmd(tag)
                .withAuthConfig(dockerAuth)
                .exec(pushCallback).awaitSuccess();
    }

    static String readFile(String path, Charset encoding)
            throws IOException
    {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }
}