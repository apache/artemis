/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.smoke.jmxrbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.VersionLoader;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// clone of JmxRBACTest with jmx security settings in broker.xml and new guard that delegates to security settings
// configured via -Djavax.management.builder.initial=org.apache.activemq.artemis.core.server.management.ArtemisRbacMBeanServerBuilder
public class JmxRBACBrokerSecurityTest extends SmokeTestBase {

   private static final String JMX_SERVER_HOSTNAME = "localhost";
   private static final String JOLOKIA_URL = "http://localhost:8161/console/jolokia";
   private static final int JMX_SERVER_PORT = 10099;

   public static final String BROKER_NAME = "0.0.0.0";

   public static final String SERVER_NAME_0 = "jmx-rbac-broker-security";

   public static final String SERVER_ADMIN = "admin";
   public static final String SERVER_USER = "user";

   public static final String ADDRESS_TEST = "TEST";

   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_0);
      deleteDirectory(server0Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setRole("amq").setUser(SERVER_ADMIN).setPassword(SERVER_ADMIN).setAllowAnonymous(false).setNoWeb(false).setArtemisInstance(server0Location).
            setConfiguration("./src/main/resources/servers/jmx-rbac-broker-security").setArgs("--java-options", "-Djava.rmi.server.hostname=localhost -Djavax.management.builder.initial=org.apache.activemq.artemis.core.server.management.ArtemisRbacMBeanServerBuilder");
         cliCreateServer.createServer();
      }
   }


   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_0);
      disableCheckThread();
      startServer(SERVER_NAME_0, 0, 0);
      ServerUtil.waitForServerToStart(0, SERVER_ADMIN, SERVER_ADMIN, 30000);
   }

   @Test
   public void testManagementRoleAccess() throws Exception {

      // I don't specify both ports here manually on purpose. See actual RMI registry connection port extraction below.
      String urlString = "service:jmx:rmi:///jndi/rmi://" + JMX_SERVER_HOSTNAME + ":" + JMX_SERVER_PORT + "/jmxrmi";

      JMXServiceURL url = new JMXServiceURL(urlString);
      JMXConnector jmxConnector;

      try {
         //Connect using the admin.
         jmxConnector = JMXConnectorFactory.connect(url, Collections.singletonMap(
            "jmx.remote.credentials", new String[] {SERVER_ADMIN, SERVER_ADMIN}));
         System.out.println("Successfully connected to: " + urlString);
      } catch (Exception e) {
         jmxConnector = null;
         e.printStackTrace();
         fail(e.getMessage());
      }

      try {
         //Create an user.
         MBeanServerConnection mBeanServerConnection = jmxConnector.getMBeanServerConnection();
         ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), BROKER_NAME, true);
         ActiveMQServerControl activeMQServerControl = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection, objectNameBuilder.getActiveMQServerObjectName(), ActiveMQServerControl.class, false);
         ObjectName memoryObjectName = new ObjectName("java.lang:type=Memory");

         try {
            activeMQServerControl.removeUser(SERVER_USER);
         } catch (Exception ignore) {
         }
         activeMQServerControl.addUser(SERVER_USER, SERVER_USER, "amq-user", true);

         activeMQServerControl.getVersion();

         try {
            mBeanServerConnection.invoke(memoryObjectName, "gc", null, null);
            fail(SERVER_ADMIN + " should not access to " + memoryObjectName);
         } catch (Exception e) {
            assertEquals(SecurityException.class, e.getClass());
         }
      } finally {
         jmxConnector.close();
      }

      try {
         //Connect using an user.
         jmxConnector = JMXConnectorFactory.connect(url, Collections.singletonMap(
            "jmx.remote.credentials", new String[] {SERVER_USER, SERVER_USER}));
         System.out.println("Successfully connected to: " + urlString);
      } catch (Exception e) {
         jmxConnector = null;
         e.printStackTrace();
         fail(e.getMessage());
      }


      try {
         MBeanServerConnection mBeanServerConnection = jmxConnector.getMBeanServerConnection();
         ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), BROKER_NAME, true);
         ActiveMQServerControl activeMQServerControl = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection, objectNameBuilder.getActiveMQServerObjectName(), ActiveMQServerControl.class, false);
         ObjectName memoryObjectName = new ObjectName("java.lang:type=Memory");

         mBeanServerConnection.invoke(memoryObjectName, "gc", null, null);

         try {
            activeMQServerControl.getVersion();
            fail(SERVER_USER + " should not access to " + objectNameBuilder.getActiveMQServerObjectName());
         } catch (Exception e) {
            assertEquals(SecurityException.class, e.getClass());
         }
      } finally {
         jmxConnector.close();
      }
   }

   @Test
   public void testSendMessageWithoutUserAndPassword() throws Exception {

      // I don't specify both ports here manually on purpose. See actual RMI registry connection port extraction below.
      String urlString = "service:jmx:rmi:///jndi/rmi://" + JMX_SERVER_HOSTNAME + ":" + JMX_SERVER_PORT + "/jmxrmi";

      JMXServiceURL url = new JMXServiceURL(urlString);
      JMXConnector jmxConnector;

      try {
         //Connect using the admin.
         jmxConnector = JMXConnectorFactory.connect(url, Collections.singletonMap(
            "jmx.remote.credentials", new String[] {SERVER_ADMIN, SERVER_ADMIN}));
         System.out.println("Successfully connected to: " + urlString);
      } catch (Exception e) {
         jmxConnector = null;
         e.printStackTrace();
         fail(e.getMessage());
      }

      try {
         MBeanServerConnection mBeanServerConnection = jmxConnector.getMBeanServerConnection();
         ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), BROKER_NAME, true);
         ActiveMQServerControl activeMQServerControl = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection, objectNameBuilder.getActiveMQServerObjectName(), ActiveMQServerControl.class, false);

         activeMQServerControl.createAddress(ADDRESS_TEST, RoutingType.MULTICAST.name());
         AddressControl testAddressControl = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection, objectNameBuilder.getAddressObjectName(SimpleString.of(ADDRESS_TEST)), AddressControl.class, false);

         testAddressControl.sendMessage(null, Message.TEXT_TYPE, ADDRESS_TEST, true, null, null);


         try {
            activeMQServerControl.removeUser(SERVER_USER);
         } catch (Exception ignore) {
         }
         activeMQServerControl.addUser(SERVER_USER, SERVER_USER, "amq-user", true);
      } finally {
         jmxConnector.close();
      }

      try {
         //Connect using an user.
         jmxConnector = JMXConnectorFactory.connect(url, Collections.singletonMap(
            "jmx.remote.credentials", new String[] {SERVER_USER, SERVER_USER}));
         System.out.println("Successfully connected to: " + urlString);
      } catch (Exception e) {
         jmxConnector = null;
         e.printStackTrace();
         fail(e.getMessage());
      }

      try {
         MBeanServerConnection mBeanServerConnection = jmxConnector.getMBeanServerConnection();
         ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), BROKER_NAME, true);
         AddressControl testAddressControl = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection, objectNameBuilder.getAddressObjectName(SimpleString.of("TEST")), AddressControl.class, false);

         try {
            testAddressControl.sendMessage(null, Message.TEXT_TYPE, ADDRESS_TEST, true, null, null);
            fail(SERVER_USER + " should not have permissions to send a message to the address " + ADDRESS_TEST);
         } catch (Exception e) {
            assertEquals(SecurityException.class, e.getClass());
         }
      } finally {
         jmxConnector.close();
      }
   }

   @Test
   public void testJolokiaWithServerAdmin() throws Exception {
      // Read an attribute via jolokia (view permission)
      String readRequest = JsonLoader.createObjectBuilder()
         .add("type", "read")
         .add("mbean", "org.apache.activemq.artemis:broker=\"" + BROKER_NAME + "\"")
         .add("attribute", "Version")
         .build()
         .toString();

      makeJolokiaRequest(JOLOKIA_URL, readRequest, SERVER_ADMIN, SERVER_ADMIN, response -> {
         assertNotNull(response);
         assertEquals(200, response.getStatusLine().getStatusCode());

         String responseBody = getResponseBody(response);
         assertNotNull(responseBody);

         JsonObject jsonResponse = JsonUtil.readJsonObject(responseBody);
         assertTrue(jsonResponse.containsKey("status"));
         assertEquals(200, jsonResponse.getInt("status"));
         assertTrue(jsonResponse.containsKey("value"));
         assertEquals(VersionLoader.getVersion().getFullVersion(), jsonResponse.getString("value"));
      });

      // Query MBeans via jolokia
      String queryRequest = JsonLoader.createObjectBuilder()
         .add("type", "search")
         .add("mbean", "org.apache.activemq.artemis:*")
         .build()
         .toString();

      makeJolokiaRequest(JOLOKIA_URL, queryRequest, SERVER_ADMIN, SERVER_ADMIN, response -> {
         assertNotNull(response);
         assertEquals(200, response.getStatusLine().getStatusCode());

         String responseBody = getResponseBody(response);
         assertNotNull(responseBody);
      });

   }

   @Test
   public void testJolokiaDisabledDetectors() throws Exception {
      // Read an attribute via jolokia (view permission)
      String readRequest = JsonLoader.createObjectBuilder()
         .add("type", "read")
         .add("mbean", "org.apache.activemq.artemis:broker=\"" + BROKER_NAME + "\"")
         .add("attribute", "Version")
         .build()
         .toString();

      makeJolokiaRequest(JOLOKIA_URL, readRequest, SERVER_ADMIN, SERVER_ADMIN, response -> {
         assertNotNull(response);
         assertEquals(200, response.getStatusLine().getStatusCode());
      });

      // Verify artemis log does not contain AMQ229032 errors
      try (Stream<String> lines = Files.lines(Path.of("target/" + SERVER_NAME_0 + "/log/artemis.log"))) {
         assertTrue(lines.noneMatch(line -> line.contains("ActiveMQDetector") || line.contains("AMQ229032")));
      }

      // Verify audit log does not contain AMQ229032 errors
      try (Stream<String> lines = Files.lines(Path.of("target/" + SERVER_NAME_0 + "/log/audit.log"))) {
         assertTrue(lines.noneMatch(line -> line.contains("ActiveMQDetector") || line.contains("AMQ229032")));
      }
   }

   private String getResponseBody(HttpResponse response) {
      String responseBody;
      try {
         responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
      return responseBody;
   }

   private void makeJolokiaRequest(String url, String jsonBody, String username, String password, Consumer<HttpResponse> responseConsumer) throws IOException {
      try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
         HttpPost httpPost = new HttpPost(url);

         // Set authentication header
         String auth = username + ":" + password;
         String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
         httpPost.setHeader("Authorization", "Basic " + encodedAuth);

         // Set required headers for jolokia
         httpPost.setHeader("Content-Type", "application/json");
         httpPost.setHeader("Origin", "http://localhost");

         // Set request body
         StringEntity entity = new StringEntity(jsonBody, StandardCharsets.UTF_8);
         httpPost.setEntity(entity);

         responseConsumer.accept(httpClient.execute(httpPost));
      }
   }
}
