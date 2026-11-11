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
package org.apache.activemq.artemis.tests.integration.mqtt;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URL;
import java.util.Arrays;

import org.apache.activemq.artemis.tests.util.Wait;
import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class MQTTSecurityPerAcceptorTest extends MQTTTestSupport {

   static {
      String path = System.getProperty("java.security.auth.login.config");
      if (path == null) {
         URL resource = MQTTSecurityPerAcceptorTest.class.getClassLoader().getResource("login.config");
         if (resource != null) {
            path = resource.getFile();
            System.setProperty("java.security.auth.login.config", path);
         }
      }
   }

   @Override
   public boolean isSecurityEnabled() {
      return true;
   }

   @Override
   public void configureBroker() throws Exception {
      server = createServer(true, createDefaultConfig(true).setSecurityEnabled(true));
      server.getConfiguration().addAcceptorConfiguration("MQTT", "tcp://localhost:" + port + "?securityDomain=PropertiesLogin");
   }

   @Test
   @Timeout(30)
   public void testConnectionPositive() throws Exception {
      internalTestConnection("first", true);
   }

   @Test
   @Timeout(30)
   public void testConnectionNegative() throws Exception {
      internalTestConnection("fail", false);
   }

   private void internalTestConnection(String username, boolean succeed) throws Exception {
      for (String version : Arrays.asList("3.1", "3.1.1")) {

         BlockingConnection connection = null;
         try {
            MQTT mqtt = createMQTTConnection("test-" + version, true);
            mqtt.setUserName(username);
            mqtt.setPassword("secret");
            mqtt.setConnectAttemptsMax(1);
            mqtt.setVersion(version);
            connection = mqtt.blockingConnection();
            try {
               connection.connect();
               if (!succeed) {
                  fail("Connection should have failed");
               }
            } catch (Exception e) {
               if (succeed) {
                  fail("Connection should have succeeded");
               }
            }
            BlockingConnection finalConnection = connection;
            if (succeed) {
               assertTrue(Wait.waitFor(() -> finalConnection.isConnected(), 2000, 100), "Should be connected");
            }
         } finally {
            if (connection != null && connection.isConnected()) {
               connection.disconnect();
            }
         }
      }
   }
}
