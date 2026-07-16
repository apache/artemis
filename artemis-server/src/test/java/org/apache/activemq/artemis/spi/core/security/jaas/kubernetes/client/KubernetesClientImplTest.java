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
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static org.apache.activemq.artemis.spi.core.security.jaas.KubernetesLoginModuleTest.AUTH_JSON;
import static org.apache.activemq.artemis.spi.core.security.jaas.KubernetesLoginModuleTest.UNAUTH_JSON;
import static org.apache.activemq.artemis.spi.core.security.jaas.KubernetesLoginModuleTest.USERNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.spi.core.security.jaas.kubernetes.model.TokenReview;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.MatchType;
import org.mockserver.socket.PortFactory;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesClientImplTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String API_PATH = "/apis/authentication.k8s.io/v1/tokenreviews";
   private static ClientAndServer mockServer;
   private static final String host = "localhost";
   private static String port;

   @TempDir
   static File tempDir;

   private static final String BOB_REQUEST = """
         {"apiVersion": "authentication.k8s.io/v1",\
         "kind": "TokenReview", "spec": {"token": "bob_token"}}""";

   private static final String KERMIT_REQUEST = """
         {"apiVersion": "authentication.k8s.io/v1",\
         "kind": "TokenReview", "spec": {"token": "kermit_token"}}""";

   @BeforeAll
   public static void startServer() {
      ConfigurationProperties.directoryToSaveDynamicSSLCertificate(tempDir.getAbsolutePath());
      ConfigurationProperties.certificateAuthorityPrivateKey(KubernetesClientImplTest.class.getClassLoader().getResource("server-ca.pem").getPath());
      ConfigurationProperties.certificateAuthorityCertificate(KubernetesClientImplTest.class.getClassLoader().getResource("server-ca-cert.pem").getPath());
      ConfigurationProperties.preventCertificateDynamicUpdate(false);
      ConfigurationProperties.proactivelyInitialiseTLS(true);

      Configuration configuration = Configuration.configuration();

      mockServer = ClientAndServer.startClientAndServer(configuration, PortFactory.findFreePort());
      port = Integer.toString(mockServer.getPort());

      assertNotNull(mockServer);
      assertTrue(mockServer.hasStarted());
      System.setProperty("KUBERNETES_SERVICE_HOST", host);
      System.setProperty("KUBERNETES_SERVICE_PORT", port);
      System.setProperty("KUBERNETES_TOKEN_PATH",
            KubernetesClientImplTest.class.getClassLoader().getResource("client_token").getPath());

      URL caPath = KubernetesClientImplTest.class.getClassLoader()
         .getResource("client-and-server-ca-certs.pem");

      assertNotNull(caPath);
      logger.info("Setting KUBERNETES_CA_PATH {}", caPath.getPath());
      System.setProperty("KUBERNETES_CA_PATH", caPath.getPath());
   }

   @AfterAll
   public static void stopServer() {
      System.clearProperty("KUBERNETES_SERVICE_HOST");
      System.clearProperty("KUBERNETES_SERVICE_PORT");
      System.clearProperty("KUBERNETES_TOKEN_PATH");
      System.clearProperty("KUBERNETES_CA_PATH");
      mockServer.stop();
   }

   @BeforeEach
   public void reset() {
      KubernetesClientImpl.clearHttpClient();
      mockServer.reset();
   }

   @Test
   public void testGetTokenReview() {

      mockServer.when(
            request()
               .withMethod("POST")
               .withPath(API_PATH)
               .withBody(json(BOB_REQUEST, MatchType.STRICT)))
         .respond(
            response()
               .withStatusCode(HTTP_CREATED)
               .withBody(UNAUTH_JSON));

      mockServer.when(
            request()
               .withMethod("POST")
               .withPath(API_PATH)
               .withBody(json(KERMIT_REQUEST, MatchType.STRICT)))
         .respond(
            response()
               .withStatusCode(HTTP_CREATED)
               .withBody(AUTH_JSON));

      mockServer.when(
            request()
               .withMethod("POST")
               .withPath(API_PATH))
         .respond(
            response()
               .withStatusCode(HTTP_INTERNAL_ERROR));

      KubernetesClient client = new KubernetesClientImpl();

      TokenReview tr = client.getTokenReview("bob_token");
      assertNotNull(tr);
      assertFalse(tr.isAuthenticated());
      assertNull(tr.getUser());
      assertNull(tr.getUsername());

      tr = client.getTokenReview("kermit_token");
      assertNotNull(tr);
      assertNotNull(tr.getUser());
      assertEquals(USERNAME, tr.getUsername());
      assertEquals(USERNAME, tr.getUser().getUsername());

      tr = client.getTokenReview("other");
      assertNotNull(tr);
      assertFalse(tr.isAuthenticated());

      mockServer.verify(request().withPath(API_PATH), VerificationTimes.exactly(3));

   }

   @Test
   public void testGetParam() throws Exception {
      Set<Map.Entry<String, String>> env = System.getenv().entrySet();

      for (Map.Entry<String, String> envKv : env) {

         if (System.getProperty(envKv.getKey()) == null) {

            KubernetesClientImpl clientImpl = new KubernetesClientImpl();
            assertEquals(envKv.getValue(), KubernetesClientImpl.getParam(envKv.getKey(), null));

            final String valFromProp = "bla";
            try {
               System.setProperty(envKv.getKey(), valFromProp);
               assertEquals(valFromProp, KubernetesClientImpl.getParam(envKv.getKey(), null));
            } finally {
               System.clearProperty(envKv.getKey());
            }

            // verify default param for non exist env or prop
            String candidate = valFromProp;
            for (int i = 0; i < 10; i++) {
               if (System.getenv(candidate) == null && System.getProperty(candidate) == null) {
                  assertEquals(candidate, KubernetesClientImpl.getParam(candidate, candidate));
                  break;
               }
               candidate += i;
            }

            // one test is sufficient!
            break;
         }
      }
   }

   @Test
   public void testSingeltonHttpClient() throws Exception {
      KubernetesClientImpl clientImplFirst = new KubernetesClientImpl();
      KubernetesClientImpl clientImplSecond = new KubernetesClientImpl();
      assertEquals(clientImplFirst.getHttpClient(), clientImplSecond.getHttpClient());
   }
}
