/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.spi.core.security.jaas.kubernetes.client;

import static java.net.HttpURLConnection.HTTP_CREATED;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Scanner;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.activemq.artemis.core.remoting.impl.ssl.SSLSupport;
import org.apache.activemq.artemis.spi.core.security.jaas.kubernetes.model.TokenReview;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesClientImpl implements KubernetesClient {

   private static final Logger logger = LoggerFactory.getLogger(KubernetesClientImpl.class);

   private static final String KUBERNETES_HOST = "KUBERNETES_SERVICE_HOST";
   private static final String KUBERNETES_PORT = "KUBERNETES_SERVICE_PORT";
   private static final String KUBERNETES_TOKEN_PATH = "KUBERNETES_TOKEN_PATH";
   private static final String KUBERNETES_CA_PATH = "KUBERNETES_CA_PATH";

   private static final String KUBERNETES_TOKENREVIEW_URI_PATTERN = "https://%s:%s/apis/authentication.k8s.io/v1/tokenreviews";

   private static final String DEFAULT_KUBERNETES_TOKEN_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token";
   private static final String DEFAULT_KUBERNETES_CA_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";

   private URI apiUri;
   private String tokenPath;

   private static volatile HttpClient httpClient;

   private static HttpClient getHttpClientSingleton() {
      HttpClient result = httpClient;
      if (result != null) {
         return result;
      }
      synchronized (KubernetesClientImpl.class) {
         if (httpClient == null) {
            try {
               httpClient = HttpClient.newBuilder().sslContext(buildSSLContext()).build();
            } catch (Exception e) {
               logger.error("Unable to build a valid SSLContext or HttpClient", e);
            }
         }
      }
      return httpClient;
   }

   // for tests
   public static void clearHttpClient() {
      httpClient = null;
   }

   public KubernetesClientImpl() {
      this.tokenPath = getParam(KUBERNETES_TOKEN_PATH, DEFAULT_KUBERNETES_TOKEN_PATH);
      String host = getParam(KUBERNETES_HOST);
      String port = getParam(KUBERNETES_PORT);
      this.apiUri = URI.create(String.format(KUBERNETES_TOKENREVIEW_URI_PATTERN, host, port));
      logger.debug("using apiUri {}", apiUri);
   }

   public static String getParam(String name, String defaultValue) {
      String value = System.getProperty(name);
      if (value == null) {
         value = System.getenv(name);
      }
      if (value == null) {
         return defaultValue;
      }
      return value;
   }

   private String getParam(String name) {
      return getParam(name, null);
   }

   @Override
   public TokenReview getTokenReview(String token) {
      TokenReview tokenReview = new TokenReview();
      String authToken = null;
      try {
         logger.debug("Loading client authentication token from {}", tokenPath);
         authToken = readFile(tokenPath);
         logger.debug("Loaded client authentication token from {}", tokenPath);
      } catch (IOException e) {
         logger.error("Cannot retrieve Service Account Authentication Token from " + tokenPath, e);
         return tokenReview;
      }
      String jsonRequest = buildJsonRequest(token);

      HttpClient client = getHttpClient();
      if (client == null) {
         return tokenReview;
      }

      HttpRequest request = HttpRequest.newBuilder(apiUri)
            .header("Authorization", "Bearer " + authToken)
            .header("Accept", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(jsonRequest)).build();
      logger.debug("Submit TokenReview request to Kubernetes API");

      try {
         HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
         if (response.statusCode() == HTTP_CREATED) {
            logger.debug("Received valid TokenReview response");
            return TokenReview.fromJsonString(response.body());
         }
         logger.error("Unable to retrieve a valid TokenReview. Received StatusCode: {}. Body: {}",
               response.statusCode(), response.body());
      } catch (IOException | InterruptedException e) {
         logger.error("Unable to request ReviewToken", e);
      }
      return tokenReview;
   }

   protected HttpClient getHttpClient() {
      return KubernetesClientImpl.getHttpClientSingleton();
   }

   private String readFile(String path) throws IOException {
      try (Scanner scanner = new Scanner(Path.of(path))) {
         StringBuilder buffer = new StringBuilder();
         while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (!line.isBlank() && !line.startsWith("#")) {
               buffer.append(line);
            }
         }
         return buffer.toString();
      }
   }

   private String buildJsonRequest(String clientToken) {
      return JsonLoader.createObjectBuilder()
            .add("apiVersion", "authentication.k8s.io/v1")
            .add("kind", "TokenReview")
            .add("spec", JsonLoader.createObjectBuilder()
                  .add("token", clientToken)
                  .build())
            .build().toString();
   }

   private static SSLContext buildSSLContext() throws Exception {
      SSLContext ctx = SSLContext.getInstance("TLS");
      String caPath = getParam(KUBERNETES_CA_PATH, DEFAULT_KUBERNETES_CA_PATH);
      File certFile = new File(caPath);
      if (!certFile.exists()) {
         logger.debug("Kubernetes CA certificate not found at: {}. Truststore not configured", caPath);
         return ctx;
      }
      KeyStore trustStore = SSLSupport.loadKeystore(null, "PEMCA", caPath, null);
      TrustManagerFactory tmFactory = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmFactory.init(trustStore);

      ctx.init(null, tmFactory.getTrustManagers(), new SecureRandom());
      return ctx;
   }
}
