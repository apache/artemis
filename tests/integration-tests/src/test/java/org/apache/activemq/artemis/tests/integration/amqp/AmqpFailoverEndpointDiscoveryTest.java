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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.cluster.failover.FailoverTestBase;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * See the tests/security-resources/build.sh script for details on the security resources used.
 */
@ExtendWith(ParameterizedTestExtension.class)
public class AmqpFailoverEndpointDiscoveryTest extends FailoverTestBase {

   // this will ensure that all tests in this class are run twice,
   // once with "true" passed to the class' constructor and once with "false"
   @Parameters(name = "{0}")
   public static Collection<?> getParameters() {

      // these 3 are for comparison
      return Arrays.asList(new Object[][]{{"NON_SSL", 0}
         /*, {"SSL", 1} */ });
   }

   private final int protocol;

   public AmqpFailoverEndpointDiscoveryTest(String name, int protocol) {
      this.protocol = protocol;
   }

   @Override
   protected void createConfigs() throws Exception {
      nodeManager = createNodeManager();
      TransportConfiguration primaryConnector = getConnectorTransportConfiguration(true);
      TransportConfiguration backupConnector = getConnectorTransportConfiguration(false);

      backupConfig = super.createDefaultNettyConfig().clearAcceptorConfigurations().addAcceptorConfiguration(getAcceptorTransportConfiguration(false)).setHAPolicyConfiguration(new SharedStoreBackupPolicyConfiguration()).addConnectorConfiguration(primaryConnector.getName(), primaryConnector).addConnectorConfiguration(backupConnector.getName(), backupConnector).addClusterConfiguration(createBasicClusterConfig(backupConnector.getName(), primaryConnector.getName()));

      backupServer = createTestableServer(backupConfig);

      primaryConfig = super.createDefaultNettyConfig().clearAcceptorConfigurations().addAcceptorConfiguration(getAcceptorTransportConfiguration(true)).setHAPolicyConfiguration(new SharedStorePrimaryPolicyConfiguration()).addClusterConfiguration(createBasicClusterConfig(primaryConnector.getName())).addConnectorConfiguration(primaryConnector.getName(), primaryConnector);

      primaryServer = createTestableServer(primaryConfig);
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live) {
      return getNettyAcceptorTransportConfig(live);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live) {
      return getNettyConnectorTransportConfig(live);
   }

   @TestTemplate
   @Timeout(120)
   public void testFailoverListWithAMQP() throws Exception {
      JmsConnectionFactory factory = getJmsConnectionFactory();
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(ADDRESS.toString());
         MessageProducer producer = session.createProducer(queue);
         producer.send(session.createTextMessage("hello before failover"));
         primaryServer.crash(true, true);
         producer.send(session.createTextMessage("hello after failover"));
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
         TextMessage receive = (TextMessage) consumer.receive(5000);
         assertNotNull(receive);
         assertEquals("hello before failover", receive.getText());
         receive = (TextMessage) consumer.receive(5000);
         assertEquals("hello after failover", receive.getText());
         assertNotNull(receive);
      }
   }

   private JmsConnectionFactory getJmsConnectionFactory() {
      if (protocol == 0) {
         return new JmsConnectionFactory("failover:(amqp://localhost:61616)");
      } else {
         String keystore = this.getClass().getClassLoader().getResource("client-keystore.jks").getFile();
         String truststore = this.getClass().getClassLoader().getResource("server-ca-truststore.jks").getFile();
         return new JmsConnectionFactory("failover:(amqps://localhost:61616?transport.keyStoreLocation=" + keystore + "&transport.keyStorePassword=securepass&transport.trustStoreLocation=" + truststore + "&transport.trustStorePassword=securepass&transport.verifyHost=false)");
      }
   }

   private TransportConfiguration getNettyAcceptorTransportConfig(final boolean live) {
      Map<String, Object> server1Params = new HashMap<>();
      if (protocol == 1) {
         server1Params.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");

         server1Params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "server-keystore.jks");
         server1Params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
         server1Params.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "client-ca-truststore.jks");
         server1Params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      }

      if (live) {
         return new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, server1Params);
      }

      server1Params.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.DEFAULT_PORT + 1);

      return new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, server1Params);
   }

   private TransportConfiguration getNettyConnectorTransportConfig(final boolean live) {
      Map<String, Object> server1Params = new HashMap<>();
      if (protocol == 1) {
         server1Params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
         server1Params.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
         server1Params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
         server1Params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
         server1Params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      }
      if (live) {
         return new TransportConfiguration(NETTY_CONNECTOR_FACTORY, server1Params);
      }
      server1Params.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.DEFAULT_PORT + 1);
      return new TransportConfiguration(NETTY_CONNECTOR_FACTORY, server1Params);
   }
}
