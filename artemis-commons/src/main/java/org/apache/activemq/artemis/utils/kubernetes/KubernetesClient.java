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
package org.apache.activemq.artemis.utils.kubernetes;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.ssl.KeyStoreSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesClient {

   private static final Logger logger = LoggerFactory.getLogger(KubernetesClient.class);

   public static final String KUBERNETES_HOST = "KUBERNETES_SERVICE_HOST";
   public static final String KUBERNETES_PORT = "KUBERNETES_SERVICE_PORT";
   public static final String KUBERNETES_TOKEN_PATH = "KUBERNETES_TOKEN_PATH";
   public static final String KUBERNETES_CA_PATH = "KUBERNETES_CA_PATH";
   public static final String KUBERNETES_API_URI = "KUBERNETES_API_URI";

   private static final String DEFAULT_KUBERNETES_TOKEN_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token";
   private static final String DEFAULT_KUBERNETES_CA_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";

   private static String authToken;

   private static volatile HttpClient _httpClient;
   private static volatile Map<String, String> params;
   private static URI apiUri;

   private KubernetesClient() {
   }

   public static HttpClient getHttpClient() throws KubernetesApiException {
      HttpClient result = _httpClient;
      if (result != null) {
         return result;
      }
      synchronized (KubernetesClient.class) {
         if (_httpClient == null) {
            try {
               _httpClient = HttpClient.newBuilder().sslContext(buildSSLContext()).build();
            } catch (Exception e) {
               logger.error("Unable to build a valid SSLContext or HttpClient", e);
            }
         }
         if (authToken == null) {
            String tokenPath = getParam(KUBERNETES_TOKEN_PATH, DEFAULT_KUBERNETES_TOKEN_PATH);
            try {
               logger.debug("Loading client authentication token from {}", tokenPath);
               authToken = readFile(tokenPath);
               logger.debug("Loaded client authentication token from {}", tokenPath);
            } catch (IOException e) {
               logger.error("Cannot retrieve Service Account Authentication Token from " + tokenPath, e);
               throw new KubernetesInternalException("cannot retrieve token");
            }
         }

         {
            String apiURIParameter = getParam(KUBERNETES_API_URI);
            if (apiURIParameter != null) {
               apiUri = URI.create(apiURIParameter);
            }
         }

         if (apiUri == null) {
            String host = getParam(KUBERNETES_HOST);
            String port = getParam(KUBERNETES_PORT);
            apiUri = URI.create("https://" + host + ":" + port);
         }
      }
      return _httpClient;
   }

   // for tests
   public static void setParam(String name, String value) {
      if (params == null) {
         synchronized (KubernetesClient.class) {
            if (params == null) {
               params = new ConcurrentHashMap<>();
            }
         }
      }
      params.put(name, value);
   }

   // for tests
   public static void clear(boolean clearParams) {
      if (clearParams) {
         if (params != null) {
            params = null;
         }
      }
      _httpClient = null;
      authToken = null;
      apiUri = null;
   }

   public static String getParam(String name, String defaultValue) {
      String value = null;
      if (params != null) {
         value = params.get(name);
      }
      if (value == null) {
         value = System.getProperty(name);
      }
      if (value == null) {
         value = System.getenv(name);
      }
      if (value == null) {
         value = defaultValue;
      }
      return value;
   }

   public static String getParam(String name) {
      return getParam(name, null);
   }

   public static JsonObject get(String path) throws KubernetesApiException {
      HttpClient theClient = getHttpClient();
      HttpRequest request = HttpRequest.newBuilder()
         .uri(apiUri.resolve(path))
         .header("Authorization", "Bearer " + authToken)
         .header("Accept", "application/json; charset=utf-8")
         .GET()
         .build();

      HttpResponse<String> response = doSend(theClient, request);

      if (response.statusCode() == 404) {
         return null;
      }

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
         throw new KubernetesApiException(response.statusCode(), response.body());
      }

      return JsonLoader.readObject(new StringReader(response.body()));
   }

   private static HttpResponse<String> doSend(HttpClient theClient,
                                                             HttpRequest request) throws KubernetesApiException {
      HttpResponse<String> response;
      try {
         response = theClient.send(request, HttpResponse.BodyHandlers.ofString());
      } catch (InterruptedException | IOException e) {
         throw new KubernetesInternalException(e.getMessage(), e);
      }
      return response;
   }

   public static JsonObject put(String path, String jsonBody) throws KubernetesApiException {
      HttpClient theClient = getHttpClient();
      HttpRequest request = HttpRequest.newBuilder()
         .uri(apiUri.resolve(path))
         .header("Authorization", "Bearer " + authToken)
         .header("Content-Type", "application/json")
         .header("Accept", "application/json; charset=utf-8")
         .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
         .build();

      HttpResponse<String> response = doSend(theClient, request);

      if (response.statusCode() == 409) {
         throw new KubernetesConflictException(response.body());
      }

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
         throw new KubernetesApiException(response.statusCode(), response.body());
      }

      return JsonLoader.readObject(new StringReader(response.body()));
   }

   public static void delete(String path) throws KubernetesApiException {
      HttpClient theClient = getHttpClient();
      HttpRequest request = HttpRequest.newBuilder()
         .uri(apiUri.resolve(path))
         .header("Authorization", "Bearer " + authToken)
         .header("Accept", "application/json")
         .DELETE()
         .build();

      HttpResponse<String> response = doSend(theClient, request);

      if (response.statusCode() == 409) {
         throw new KubernetesConflictException(response.body());
      }

      if (response.statusCode() != 404 && (response.statusCode() < 200 || response.statusCode() >= 300)) {
         throw new KubernetesApiException(response.statusCode(), response.body());
      }
   }

   public static JsonObject post(String path, String jsonBody) throws KubernetesApiException {
      HttpClient theClient = getHttpClient();
      HttpRequest request = HttpRequest.newBuilder()
         .uri(apiUri.resolve(path))
         .header("Authorization", "Bearer " + authToken)
         .header("Accept", "application/json; charset=utf-8")
         .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
         .build();

      HttpResponse<String> response = doSend(theClient, request);

      if (response.statusCode() == 409) {
         throw new KubernetesConflictException(response.body());
      }

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
         throw new KubernetesApiException(response.statusCode(), response.body());
      }

      return JsonLoader.readObject(new StringReader(response.body()));
   }

   private static String readFile(String path) throws IOException {
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

   private static SSLContext buildSSLContext() throws Exception {
      SSLContext ctx = SSLContext.getInstance("TLS");
      String caPath = getParam(KUBERNETES_CA_PATH, DEFAULT_KUBERNETES_CA_PATH);
      File certFile = new File(caPath);
      if (!certFile.exists()) {
         throw new KubernetesInternalException("no certFile available");
      }
      KeyStore trustStore = KeyStoreSupport.loadKeystore(null, KeyStoreSupport.PEMCA, caPath, null);
      TrustManagerFactory tmFactory = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmFactory.init(trustStore);

      ctx.init(null, tmFactory.getTrustManagers(), new SecureRandom());
      return ctx;
   }

   public static String getResourceVersion(JsonObject resource) {
      JsonObject metadata = resource.getJsonObject("metadata");
      String resourceVersion = metadata != null ? metadata.getString("resourceVersion", "") : "";
      return resourceVersion;
   }

}
