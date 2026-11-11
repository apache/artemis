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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.remoting.impl.ssl.SSLSupport;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.Message;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;
import org.junit.jupiter.api.Test;

/**
 * See the tests/security-resources/build.sh script for details on the security resources used.
 */
public class MQTTSecurityCRLTest extends ActiveMQTestBase {

   @Test
   public void crlRevokedTest() throws Exception {

      ActiveMQServer server1 = initServer();
      BlockingConnection connection1 = null;
      try {
         server1.start();

         while (!server1.isStarted()) {
            Thread.sleep(50);
         }

         connection1 = retrieveMQTTConnection("ssl://localhost:1883", "server-ca-truststore.jks", "securepass", "other-client-keystore.jks", "securepass");

         // Subscribe to topics
         Topic[] topics = {new Topic("test/+/some/#", QoS.AT_MOST_ONCE)};
         connection1.subscribe(topics);

         // Publish Messages
         String payload1 = "This is message 1";

         connection1.publish("test/1/some/la", payload1.getBytes(), QoS.AT_LEAST_ONCE, false);

         Message message1 = connection1.receive(5, TimeUnit.SECONDS);

         assertEquals(payload1, new String(message1.getPayload()));
         fail("We expect an exception of some sort!");
      } catch (SSLException expected) {
      } catch (EOFException canHappenAlso) {
      } finally {
         if (connection1 != null) {
            connection1.disconnect();
         }
         if (server1.isStarted()) {
            server1.stop();
         }
      }
   }

   @Test
   public void crlNotRevokedTest() throws Exception {

      ActiveMQServer server1 = initServer();
      BlockingConnection connection1 = null;
      try {
         server1.start();

         while (!server1.isStarted()) {
            Thread.sleep(50);
         }

         connection1 = retrieveMQTTConnection("ssl://localhost:1883", "server-ca-truststore.jks", "securepass", "client-keystore.jks", "securepass");

         // Subscribe to topics
         Topic[] topics = {new Topic("test/+/some/#", QoS.AT_MOST_ONCE)};
         connection1.subscribe(topics);

         // Publish Messages
         String payload1 = "This is message 1";

         connection1.publish("test/1/some/la", payload1.getBytes(), QoS.AT_LEAST_ONCE, false);

         Message message1 = connection1.receive(5, TimeUnit.SECONDS);

         assertEquals(payload1, new String(message1.getPayload()));

      } finally {
         if (connection1 != null) {
            connection1.disconnect();
         }
         if (server1.isStarted()) {
            server1.stop();
         }
      }
   }


   private ActiveMQServer initServer() throws Exception {
      Configuration configuration = createDefaultNettyConfig().setSecurityEnabled(false);

      addMqttTransportConfiguration(configuration);
      addWildCardConfiguration(configuration);

      ActiveMQServer server = createServer(true, configuration);
      return server;
   }

   private void addWildCardConfiguration(Configuration configuration) {
      WildcardConfiguration wildcardConfiguration = new WildcardConfiguration();
      wildcardConfiguration.setAnyWords('#');
      wildcardConfiguration.setDelimiter('/');
      wildcardConfiguration.setRoutingEnabled(true);
      wildcardConfiguration.setSingleWord('+');

      configuration.setWildCardConfiguration(wildcardConfiguration);
   }

   private void addMqttTransportConfiguration(Configuration configuration) throws IOException {
      TransportConfiguration transportConfiguration = new TransportConfiguration(NettyAcceptorFactory.class.getCanonicalName(), null, "mqtt", null);

      transportConfiguration.getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      transportConfiguration.getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "client-ca-truststore.jks");
      transportConfiguration.getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      transportConfiguration.getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "server-keystore.jks");
      transportConfiguration.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      transportConfiguration.getParams().put(TransportConstants.CRL_PATH_PROP_NAME, "other-client-crl.pem");
      transportConfiguration.getParams().put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, "true");
      transportConfiguration.getParams().put(TransportConstants.PORT_PROP_NAME, "1883");
      transportConfiguration.getParams().put(TransportConstants.HOST_PROP_NAME, "localhost");
      transportConfiguration.getParams().put(TransportConstants.PROTOCOLS_PROP_NAME, "MQTT");

      configuration.getAcceptorConfigurations().add(transportConfiguration);
   }

   private BlockingConnection retrieveMQTTConnection(String host, String truststorePath, String truststorePass, String keystorePath, String keystorePass) throws Exception {
      MQTT mqtt = new MQTT();
      mqtt.setConnectAttemptsMax(1);
      mqtt.setReconnectAttemptsMax(0);
      mqtt.setHost(host);
      SSLContext sslContext = new SSLSupport()
         .setKeystorePath(keystorePath)
         .setKeystorePassword(keystorePass)
         .setTruststorePath(truststorePath)
         .setTruststorePassword(truststorePass)
         .createContext();
      mqtt.setSslContext(sslContext);

      BlockingConnection connection = mqtt.blockingConnection();
      connection.connect();
      return connection;
   }


}
