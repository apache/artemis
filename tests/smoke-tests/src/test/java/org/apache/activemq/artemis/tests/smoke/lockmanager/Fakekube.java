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
package org.apache.activemq.artemis.tests.smoke.lockmanager;

import java.io.File;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.util.HashMap;

import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.kubernetes.KubernetesClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.socket.PortFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fake Kubernetes API server for testing using MockServer.
 * Simulates Kubernetes Lease API endpoints for lock manager testing.
 */
public class Fakekube implements AutoCloseable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ClientAndServer mockServer;
   private File tokenFile;
   private File tempCertDir;
   private final FakekubeLeaseState leaseState;
   private final FakekubeConfigMapState configMapState;
   private String apiUri;

   public Fakekube() {
      this.leaseState = new FakekubeLeaseState();
      this.configMapState = new FakekubeConfigMapState();
   }

   /**
    * Start the fake Kubernetes API server
    * @param testDir directory to store temporary files
    * @return the HTTPS API URI of the mock server
    */
   public String start(File testDir) throws Exception {
      // Clear any previous Kubernetes client state
      KubernetesClient.clear(true);

      // Set up SSL certificates for MockServer
      tempCertDir = new File(testDir, "mock-certs");
      tempCertDir.mkdirs();

      // Minimize MockServer logging
      ConfigurationProperties.logLevel("ERROR");
      ConfigurationProperties.disableSystemOut(true);

      // Suppress TLS warnings
      ConfigurationProperties.tlsAllowInsecureProtocols(false);

      ConfigurationProperties.directoryToSaveDynamicSSLCertificate(tempCertDir.getAbsolutePath());
      ConfigurationProperties.certificateAuthorityPrivateKey(
         Fakekube.class.getClassLoader().getResource("server-ca.pem").getPath());
      ConfigurationProperties.certificateAuthorityCertificate(
         Fakekube.class.getClassLoader().getResource("server-ca-cert.pem").getPath());
      ConfigurationProperties.preventCertificateDynamicUpdate(false);
      ConfigurationProperties.proactivelyInitialiseTLS(true);

      // Start MockServer with HTTPS
      Configuration configuration = Configuration.configuration();
      mockServer = ClientAndServer.startClientAndServer(configuration, PortFactory.findFreePort());
      int port = mockServer.getPort();
      apiUri = "https://localhost:" + port;

      // Create fake token file
      tokenFile = new File(testDir, "fake-token");
      Files.writeString(tokenFile.toPath(), "fake-token-for-testing");

      // Get CA certificate from test resources
      java.net.URL caPath = Fakekube.class.getClassLoader()
         .getResource("client-and-server-ca-certs.pem");
      if (caPath == null) {
         throw new IllegalStateException("CA certificate not found in test resources");
      }

      // Configure KubernetesClient to use our fake server
      KubernetesClient.setParam(KubernetesClient.KUBERNETES_API_URI, apiUri);
      KubernetesClient.setParam(KubernetesClient.KUBERNETES_TOKEN_PATH, tokenFile.getAbsolutePath());
      KubernetesClient.setParam(KubernetesClient.KUBERNETES_CA_PATH, caPath.getPath());

      // Set up mock endpoints
      setupMockEndpoints();

      return apiUri;
   }

   /**
    * Get the API URI of the fake server
    */
   public String getApiUri() {
      return apiUri;
   }

   /**
    * Stop the fake Kubernetes API server and clean up resources
    */
   public void stop() {
      if (mockServer != null) {
         mockServer.stop();
      }
      if (tokenFile != null && tokenFile.exists()) {
         tokenFile.delete();
      }
      if (tempCertDir != null && tempCertDir.exists()) {
         deleteDirectory(tempCertDir);
      }
      KubernetesClient.clear(true);
   }

   @Override
   public void close() {
      stop();
   }

   private void setupMockEndpoints() {
      // GET /apis/coordination.k8s.io/v1/namespaces/{namespace}/leases/{name}
      mockServer.when(
         HttpRequest.request()
            .withMethod("GET")
            .withPath("/apis/coordination.k8s.io/v1/namespaces/.*/leases/.*")
      ).respond(req -> {
         String path = req.getPath().getValue();
         String[] parts = path.split("/");
         String leaseName = parts[parts.length - 1];
         String namespace = parts[parts.length - 3];

         String leaseJson = leaseState.getLease(namespace, leaseName);
         if (leaseJson == null) {
            return HttpResponse.response().withStatusCode(404);
         }
         return HttpResponse.response()
            .withStatusCode(200)
            .withHeader("Content-Type", "application/json")
            .withBody(leaseJson);
      });

      // POST /apis/coordination.k8s.io/v1/namespaces/{namespace}/leases
      mockServer.when(
         HttpRequest.request()
            .withMethod("POST")
            .withPath("/apis/coordination.k8s.io/v1/namespaces/.*/leases")
      ).respond(req -> {
         String path = req.getPath().getValue();
         String[] parts = path.split("/");
         String namespace = parts[parts.length - 2];
         String body = req.getBodyAsString();

         try {
            String response = leaseState.createLease(namespace, body);
            return HttpResponse.response()
               .withStatusCode(201)
               .withHeader("Content-Type", "application/json")
               .withBody(response);
         } catch (FakekubeLeaseState.ConflictException e) {
            return HttpResponse.response()
               .withStatusCode(409)
               .withHeader("Content-Type", "application/json")
               .withBody("{\"kind\":\"Status\",\"apiVersion\":\"v1\",\"metadata\":{},\"status\":\"Failure\",\"message\":\"" + e.getMessage() + "\",\"reason\":\"AlreadyExists\",\"code\":409}");
         }
      });

      // PUT /apis/coordination.k8s.io/v1/namespaces/{namespace}/leases/{name}
      mockServer.when(
         HttpRequest.request()
            .withMethod("PUT")
            .withPath("/apis/coordination.k8s.io/v1/namespaces/.*/leases/.*")
      ).respond(req -> {
         String path = req.getPath().getValue();
         String[] parts = path.split("/");
         String leaseName = parts[parts.length - 1];
         String namespace = parts[parts.length - 3];
         String body = req.getBodyAsString();

         try {
            String response = leaseState.updateLease(namespace, leaseName, body);
            return HttpResponse.response()
               .withStatusCode(200)
               .withHeader("Content-Type", "application/json")
               .withBody(response);
         } catch (FakekubeLeaseState.ConflictException e) {
            return HttpResponse.response()
               .withStatusCode(409)
               .withHeader("Content-Type", "application/json")
               .withBody("{\"kind\":\"Status\",\"apiVersion\":\"v1\",\"metadata\":{},\"status\":\"Failure\",\"message\":\"" + e.getMessage() + "\",\"reason\":\"Conflict\",\"code\":409}");
         }
      });

      // DELETE /apis/coordination.k8s.io/v1/namespaces/{namespace}/leases/{name}
      mockServer.when(
         HttpRequest.request()
            .withMethod("DELETE")
            .withPath("/apis/coordination.k8s.io/v1/namespaces/.*/leases/.*")
      ).respond(req -> {
         String path = req.getPath().getValue();
         String[] parts = path.split("/");
         String leaseName = parts[parts.length - 1];
         String namespace = parts[parts.length - 3];

         leaseState.deleteLease(namespace, leaseName);
         return HttpResponse.response()
            .withStatusCode(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"kind\":\"Status\",\"apiVersion\":\"v1\",\"metadata\":{},\"status\":\"Success\"}");
      });

      // ConfigMap endpoints for MutableLong support

      // GET /api/v1/namespaces/{namespace}/configmaps/{name}
      mockServer.when(
         HttpRequest.request()
            .withMethod("GET")
            .withPath("/api/v1/namespaces/.*/configmaps/.*")
      ).respond(req -> {
         String path = req.getPath().getValue();
         String[] parts = path.split("/");
         String configMapName = parts[parts.length - 1];
         String namespace = parts[parts.length - 3];

         String configMapJson = configMapState.getConfigMap(namespace, configMapName);
         if (configMapJson == null) {
            return HttpResponse.response().withStatusCode(404);
         }
         return HttpResponse.response()
            .withStatusCode(200)
            .withHeader("Content-Type", "application/json")
            .withBody(configMapJson);
      });

      // POST /api/v1/namespaces/{namespace}/configmaps
      mockServer.when(
         HttpRequest.request()
            .withMethod("POST")
            .withPath("/api/v1/namespaces/.*/configmaps")
      ).respond(req -> {
         String path = req.getPath().getValue();
         String[] parts = path.split("/");
         String namespace = parts[parts.length - 2];
         String body = req.getBodyAsString();

         try {
            String response = configMapState.createConfigMap(namespace, body);
            return HttpResponse.response()
               .withStatusCode(201)
               .withHeader("Content-Type", "application/json")
               .withBody(response);
         } catch (FakekubeConfigMapState.ConflictException e) {
            return HttpResponse.response()
               .withStatusCode(409)
               .withHeader("Content-Type", "application/json")
               .withBody("{\"kind\":\"Status\",\"apiVersion\":\"v1\",\"metadata\":{},\"status\":\"Failure\",\"message\":\"" + e.getMessage() + "\",\"reason\":\"AlreadyExists\",\"code\":409}");
         }
      });

      // PUT /api/v1/namespaces/{namespace}/configmaps/{name}
      mockServer.when(
         HttpRequest.request()
            .withMethod("PUT")
            .withPath("/api/v1/namespaces/.*/configmaps/.*")
      ).respond(req -> {
         String path = req.getPath().getValue();
         String[] parts = path.split("/");
         String configMapName = parts[parts.length - 1];
         String namespace = parts[parts.length - 3];
         String body = req.getBodyAsString();

         try {
            String response = configMapState.updateConfigMap(namespace, configMapName, body);
            return HttpResponse.response()
               .withStatusCode(200)
               .withHeader("Content-Type", "application/json")
               .withBody(response);
         } catch (FakekubeConfigMapState.ConflictException e) {
            return HttpResponse.response()
               .withStatusCode(409)
               .withHeader("Content-Type", "application/json")
               .withBody("{\"kind\":\"Status\",\"apiVersion\":\"v1\",\"metadata\":{},\"status\":\"Failure\",\"message\":\"" + e.getMessage() + "\",\"reason\":\"Conflict\",\"code\":409}");
         }
      });

      // DELETE /api/v1/namespaces/{namespace}/configmaps/{name}
      mockServer.when(
         HttpRequest.request()
            .withMethod("DELETE")
            .withPath("/api/v1/namespaces/.*/configmaps/.*")
      ).respond(req -> {
         String path = req.getPath().getValue();
         String[] parts = path.split("/");
         String configMapName = parts[parts.length - 1];
         String namespace = parts[parts.length - 3];

         configMapState.deleteConfigMap(namespace, configMapName);
         return HttpResponse.response()
            .withStatusCode(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"kind\":\"Status\",\"apiVersion\":\"v1\",\"metadata\":{},\"status\":\"Success\"}");
      });
   }

   private void deleteDirectory(File directory) {
      File[] files = directory.listFiles();
      if (files != null) {
         for (File file : files) {
            if (file.isDirectory()) {
               deleteDirectory(file);
            } else {
               file.delete();
            }
         }
      }
      directory.delete();
   }

   /**
    * Manages the state of Kubernetes Leases for the fake server
    */
   static class FakekubeLeaseState {
      private final HashMap<String, String> leases = new HashMap<>();
      private final HashMap<String, Integer> resourceVersions = new HashMap<>();
      private int nextResourceVersion = 1;

      static class ConflictException extends Exception {
         ConflictException(String message) {
            super(message);
         }
      }

      private String getLeaseKey(String namespace, String name) {
         return namespace + "/" + name;
      }

      synchronized String getLease(String namespace, String name) {
         return leases.get(getLeaseKey(namespace, name));
      }

      synchronized String createLease(String namespace, String body) throws ConflictException {
         JsonObject lease = JsonLoader.readObject(new StringReader(body));
         String name = lease.getJsonObject("metadata").getString("name");
         String key = getLeaseKey(namespace, name);

         if (leases.containsKey(key)) {
            throw new ConflictException("Lease already exists: " + name);
         }

         int resourceVersion = nextResourceVersion++;
         resourceVersions.put(key, resourceVersion);

         String leaseWithVersion = addResourceVersion(body, resourceVersion);
         leases.put(key, leaseWithVersion);
         return leaseWithVersion;
      }

      synchronized String updateLease(String namespace, String name, String body) throws ConflictException {
         String key = getLeaseKey(namespace, name);

         JsonObject lease = JsonLoader.readObject(new StringReader(body));
         JsonObject metadata = lease.getJsonObject("metadata");
         String requestedVersion = metadata != null ? metadata.getString("resourceVersion", null) : null;

         Integer currentVersion = resourceVersions.get(key);
         if (currentVersion != null && requestedVersion != null && !requestedVersion.equals(String.valueOf(currentVersion))) {
            throw new ConflictException("Resource version mismatch");
         }

         int newResourceVersion = nextResourceVersion++;
         resourceVersions.put(key, newResourceVersion);

         String leaseWithVersion = addResourceVersion(body, newResourceVersion);
         leases.put(key, leaseWithVersion);
         return leaseWithVersion;
      }

      synchronized void deleteLease(String namespace, String name) {
         String key = getLeaseKey(namespace, name);
         leases.remove(key);
         resourceVersions.remove(key);
      }

      private String addResourceVersion(String leaseJson, int resourceVersion) {
         JsonObject lease = JsonLoader.readObject(new StringReader(leaseJson));
         JsonObject metadata = lease.getJsonObject("metadata");

         JsonObjectBuilder metadataBuilder = JsonLoader.createObjectBuilder();
         if (metadata != null) {
            metadataBuilder.add("name", metadata.getString("name"));
            metadataBuilder.add("namespace", metadata.getString("namespace"));
         }
         metadataBuilder.add("resourceVersion", String.valueOf(resourceVersion));

         JsonObjectBuilder leaseBuilder = JsonLoader.createObjectBuilder()
            .add("apiVersion", lease.getString("apiVersion"))
            .add("kind", lease.getString("kind"))
            .add("metadata", metadataBuilder)
            .add("spec", lease.getJsonObject("spec"));

         return leaseBuilder.build().toString();
      }
   }

   /**
    * Manages the state of Kubernetes ConfigMaps for the fake server
    */
   static class FakekubeConfigMapState {
      private final HashMap<String, String> configMaps = new HashMap<>();
      private final HashMap<String, Integer> resourceVersions = new HashMap<>();
      private int nextResourceVersion = 1;

      static class ConflictException extends Exception {
         ConflictException(String message) {
            super(message);
         }
      }

      private String getConfigMapKey(String namespace, String name) {
         return namespace + "/" + name;
      }

      synchronized String getConfigMap(String namespace, String name) {
         return configMaps.get(getConfigMapKey(namespace, name));
      }

      synchronized String createConfigMap(String namespace, String body) throws ConflictException {
         logger.info("add body {}", body);
         JsonObject configMap = JsonLoader.readObject(new StringReader(body));
         String name = configMap.getJsonObject("metadata").getString("name");
         String key = getConfigMapKey(namespace, name);

         if (configMaps.containsKey(key)) {
            throw new ConflictException("ConfigMap already exists: " + name);
         }

         int resourceVersion = nextResourceVersion++;
         resourceVersions.put(key, resourceVersion);

         String configMapWithVersion = addResourceVersion(body, resourceVersion);
         configMaps.put(key, configMapWithVersion);
         return configMapWithVersion;
      }

      synchronized String updateConfigMap(String namespace, String name, String body) throws ConflictException {
         logger.info("update body {}", body);
         String key = getConfigMapKey(namespace, name);

         JsonObject configMap = JsonLoader.readObject(new StringReader(body));
         JsonObject metadata = configMap.getJsonObject("metadata");
         String requestedVersion = metadata != null ? metadata.getString("resourceVersion", null) : null;

         Integer currentVersion = resourceVersions.get(key);
         if (currentVersion != null && requestedVersion != null && !requestedVersion.equals(String.valueOf(currentVersion))) {
            throw new ConflictException("Resource version mismatch");
         }

         int newResourceVersion = nextResourceVersion++;
         resourceVersions.put(key, newResourceVersion);

         String configMapWithVersion = addResourceVersion(body, newResourceVersion);
         configMaps.put(key, configMapWithVersion);
         return configMapWithVersion;
      }

      synchronized void deleteConfigMap(String namespace, String name) {
         String key = getConfigMapKey(namespace, name);
         configMaps.remove(key);
         resourceVersions.remove(key);
      }

      private String addResourceVersion(String configMapJson, int resourceVersion) {
         JsonObject configMap = JsonLoader.readObject(new StringReader(configMapJson));
         JsonObject metadata = configMap.getJsonObject("metadata");

         JsonObjectBuilder metadataBuilder = JsonLoader.createObjectBuilder();
         if (metadata != null) {
            metadataBuilder.add("name", metadata.getString("name"));
            metadataBuilder.add("namespace", metadata.getString("namespace"));
         }
         metadataBuilder.add("resourceVersion", String.valueOf(resourceVersion));

         JsonObjectBuilder configMapBuilder = JsonLoader.createObjectBuilder()
            .add("apiVersion", configMap.getString("apiVersion"))
            .add("kind", configMap.getString("kind"))
            .add("metadata", metadataBuilder)
            .add("data", configMap.getJsonObject("data"));

         return configMapBuilder.build().toString();
      }
   }
}
