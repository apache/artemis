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
package org.apache.activemq.artemis.tests.integration.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BridgeRetryFullFailureTest extends ActiveMQTestBase {

   private ActiveMQServer server0;
   private ActiveMQServer server1;

   private String getServer0URL() {
      return "tcp://localhost:61616";
   }

   private String getServer1URL() {
      return "tcp://localhost:61617";
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server0 = createServer(false, createBasicConfig());
      server1 = createServer(false, createBasicConfig());
      server1.getConfiguration().clearAddressSettings();
      server1.getConfiguration().addAddressSetting("#", new AddressSettings().setMaxSizeMessages(10).setMaxSizeBytes(10000).setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL));
      server0.getConfiguration().addAcceptorConfiguration("acceptor", getServer0URL());
      server0.getConfiguration().addConnectorConfiguration("connector", getServer1URL());
      server1.getConfiguration().addAcceptorConfiguration("acceptor", getServer1URL());
      server0.start();
      server1.start();
   }

   @Test
   public void testFullServer() throws Exception {
      SimpleString source = SimpleString.of("source");
      SimpleString destination = SimpleString.of("destination");

      server0.createQueue(QueueConfiguration.of(source).setRoutingType(RoutingType.ANYCAST));
      Queue queueServer1 = server1.createQueue(QueueConfiguration.of(destination).setRoutingType(RoutingType.ANYCAST));

      server0.deployBridge(new BridgeConfiguration().setRoutingType(ComponentConfigurationRoutingType.ANYCAST).setName("bridge").setForwardingAddress(destination.toString()).setQueueName(source.toString()).setConfirmationWindowSize(10).setStaticConnectors(Arrays.asList("connector")).setRetryInterval(100).setReconnectAttempts(-1));

      ConnectionFactory factory0 = CFUtil.createConnectionFactory("CORE", getServer0URL());
      ConnectionFactory factory1 = CFUtil.createConnectionFactory("CORE", getServer1URL());

      int NUMBER_OF_MESSAGES = 1000;

      AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler();
      runAfter(() -> loggerHandler.close());

      try (Connection connection = factory0.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(session.createQueue(source.toString()));
         producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            Message message = session.createMessage();
            message.setIntProperty("i", i);
            producer.send(message);
         }
      }

      Wait.assertTrue(() -> loggerHandler.findText("AMQ229102"));

      // the reconnects and failure may introduce out of order issues. so we just check if they were all received
      Set<Integer> receivedIntegers = new HashSet<>();

      try (Connection connection = factory1.createConnection()) {
         connection.start();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(session.createQueue(destination.toString()));
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            Message message = consumer.receive(5000);
            assertNotNull(message);
            assertFalse(receivedIntegers.contains(message.getIntProperty("i")));
            receivedIntegers.add(message.getIntProperty("i"));
         }
         assertNull(consumer.receiveNoWait());
      }

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         assertTrue(receivedIntegers.contains(i));
      }
      // please bear with my OCD here
      // this is a moot check as I checked for all the elements
      // but I still wanted the extra validation here
      assertEquals(NUMBER_OF_MESSAGES, receivedIntegers.size());

   }
}