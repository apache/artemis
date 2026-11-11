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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.ANONYMOUS_RELAY;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.CONNECTION_OPEN_FAILED;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.CONTAINER_ID;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.DELAYED_DELIVERY;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.INVALID_FIELD;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.PRODUCT;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.VERSION;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.VersionLoader;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpValidator;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.engine.Connection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Tests for behaviors expected of the broker when clients connect to the broker
 */
public class AmqpInboundConnectionTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private static final String PRODUCT_NAME = "apache-activemq-artemis";

   @Test
   @Timeout(60)
   public void testCloseIsSentOnConnectionClose() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection amqpConnection = client.connect();

      try {
         for (RemotingConnection connection : server.getRemotingService().getConnections()) {
            server.getRemotingService().removeConnection(connection);
            connection.disconnect(true);
         }

         Wait.assertTrue(amqpConnection::isClosed);
         assertEquals(AmqpSupport.CONNECTION_FORCED, amqpConnection.getConnection().getRemoteCondition().getCondition());
      } finally {
         amqpConnection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testBrokerContainerId() throws Exception {
      final String containerId = server.getNodeID().toString();

      AmqpClient client = createAmqpClient();
      assertNotNull(client);

      client.setValidator(new AmqpValidator() {

         @Override
         public void inspectOpenedResource(Connection connection) {
            if (!containerId.equals(connection.getRemoteContainer())) {
               markAsInvalid("Broker did not send the expected container ID");
            }
         }
      });

      AmqpConnection connection = addConnection(client.connect());
      try {
         assertNotNull(connection);
         connection.getStateInspector().assertValid();
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testDefaultMaxFrameSize() throws Exception {
      AmqpClient client = createAmqpClient();
      assertNotNull(client);

      client.setValidator(new AmqpValidator() {

         @Override
         public void inspectOpenedResource(Connection connection) {
            int brokerMaxFrameSize = connection.getTransport().getRemoteMaxFrameSize();
            if (brokerMaxFrameSize != AmqpSupport.MAX_FRAME_SIZE_DEFAULT) {
               markAsInvalid("Broker did not send the expected max Frame Size");
            }
         }
      });

      AmqpConnection connection = addConnection(client.connect());
      try {
         assertNotNull(connection);
         connection.getStateInspector().assertValid();
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testBrokerConnectionProperties() throws Exception {
      AmqpClient client = createAmqpClient();

      client.setValidator(new AmqpValidator() {

         @Override
         public void inspectOpenedResource(Connection connection) {

            Map<Symbol, Object> properties = connection.getRemoteProperties();
            if (!properties.containsKey(PRODUCT)) {
               markAsInvalid("Broker did not send a queue product name value");
               return;
            }

            if (!properties.containsKey(VERSION)) {
               markAsInvalid("Broker did not send a queue version value");
               return;
            }

            if (!PRODUCT_NAME.equals(properties.get(PRODUCT))) {
               markAsInvalid("Broker did not send a the expected product name");
               return;
            }

            String brokerVersion = VersionLoader.getVersion().getFullVersion();
            if (!brokerVersion.equals(properties.get(VERSION))) {
               markAsInvalid("Broker did not send a the expected product version");
               return;
            }
         }
      });

      AmqpConnection connection = addConnection(client.connect());
      try {
         assertNotNull(connection);
         connection.getStateInspector().assertValid();
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testConnectionCarriesExpectedCapabilities() throws Exception {
      AmqpClient client = createAmqpClient();
      assertNotNull(client);

      client.setValidator(new AmqpValidator() {

         @Override
         public void inspectOpenedResource(Connection connection) {

            Symbol[] offered = connection.getRemoteOfferedCapabilities();

            if (!contains(offered, ANONYMOUS_RELAY)) {
               markAsInvalid("Broker did not indicate it support anonymous relay");
               return;
            }

            if (!contains(offered, DELAYED_DELIVERY)) {
               markAsInvalid("Broker did not indicate it support delayed message delivery");
               return;
            }
         }
      });

      AmqpConnection connection = addConnection(client.connect());
      try {
         assertNotNull(connection);
         connection.getStateInspector().assertValid();
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testCanConnectWithDifferentContainerIds() throws Exception {
      AmqpClient client = createAmqpClient();
      assertNotNull(client);

      AmqpConnection connection1 = addConnection(client.createConnection());
      AmqpConnection connection2 = addConnection(client.createConnection());

      connection1.setContainerId(getTestName() + "-Client:1");
      connection2.setContainerId(getTestName() + "-Client:2");

      connection1.connect();
      Wait.assertEquals(1, server::getConnectionCount);

      connection2.connect();
      Wait.assertEquals(2, server::getConnectionCount);

      connection1.close();
      Wait.assertEquals(1, server::getConnectionCount);

      connection2.close();
      Wait.assertEquals(0, server::getConnectionCount);
   }

   @Test
   @Timeout(60)
   public void testCannotConnectWithSameContainerId() throws Exception {
      AmqpClient client = createAmqpClient();

      List<Symbol> desiredCapabilities = new ArrayList<>(1);
      desiredCapabilities.add(AmqpSupport.SOLE_CONNECTION_CAPABILITY);

      assertNotNull(client);

      AmqpConnection connection1 = addConnection(client.createConnection());
      AmqpConnection connection2 = addConnection(client.createConnection());

      connection1.setDesiredCapabilities(desiredCapabilities);
      connection2.setDesiredCapabilities(desiredCapabilities);

      connection1.setContainerId(getTestName());
      connection2.setContainerId(getTestName());

      connection1.connect();
      assertEquals(1, server.getConnectionCount());

      connection2.setStateInspector(new AmqpValidator() {

         @Override
         public void inspectOpenedResource(Connection connection) {
            if (!connection.getRemoteProperties().containsKey(CONNECTION_OPEN_FAILED)) {
               markAsInvalid("Broker did not set connection establishment failed property");
            }
         }

         @Override
         public void inspectClosedResource(Connection connection) {
            ErrorCondition remoteError = connection.getRemoteCondition();
            if (remoteError == null || remoteError.getCondition() == null) {
               markAsInvalid("Broker did not add error condition for duplicate client ID");
            } else {
               if (!remoteError.getCondition().equals(AmqpError.INVALID_FIELD)) {
                  markAsInvalid("Broker did not set condition to " + AmqpError.INVALID_FIELD);
               }

               if (!remoteError.getCondition().equals(AmqpError.INVALID_FIELD)) {
                  markAsInvalid("Broker did not set condition to " + AmqpError.INVALID_FIELD);
               }
            }

            // Validate the info map contains a hint that the container/client id was the
            // problem
            Map<?, ?> infoMap = remoteError.getInfo();
            if (infoMap == null) {
               markAsInvalid("Broker did not set an info map on condition");
            } else if (!infoMap.containsKey(INVALID_FIELD)) {
               markAsInvalid("Info map does not contain expected key");
            } else {
               Object value = infoMap.get(INVALID_FIELD);
               if (!CONTAINER_ID.equals(value)) {
                  markAsInvalid("Info map does not contain expected value: " + value);
               }
            }
         }
      });

      try {
         connection2.connect();
         fail("Should not be able to connect with same container Id.");
      } catch (Exception ex) {
         logger.debug("Second connection with same container Id failed as expected.");
      }

      connection2.getStateInspector().assertValid();
      connection2.close();
      Wait.assertEquals(1, server::getConnectionCount);
      connection1.close();
      Wait.assertEquals(0, server::getConnectionCount);
   }
}
