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
package org.apache.activemq.artemis.tests.integration.amqp.connect;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.QueueImplTestAccessor;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AMQPMirrorLargeMessageDivertDroppedTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // notice I'm not placing this as static as I prefer such a large buffer to be referenced from the test instance
   // which I know Garbage Collection happens much earlier than the class release.
   private final String LARGE_BUFFER = RandomUtil.randomAlphaNumericString(300 * 1024);

   private static final long DROP_LIMIT = 5;
   private static final long NUMBER_OF_MESSAGES = 10;
   private static final int PORT_SERVER_A = 5671;
   private static final int PORT_SERVER_B = 6671;
   private static final String URI_SERVER_A = "tcp://localhost:" + PORT_SERVER_A;
   private static final String URI_SERVER_B = "tcp://localhost:" + PORT_SERVER_B;

   // Basic queue is a simple queue, no diverts. We send stuff to validate mirror isn't interrupted and everything works
   private static final String BASIC_QUEUE = AMQPMirrorLargeMessageDivertDroppedTest.class.getName() + "_BASIC";

   // Expiry has a 'divert' towards ExpiryDivert
   private static final String EXPIRY_QUEUE = AMQPMirrorLargeMessageDivertDroppedTest.class.getName() + "_ExpiryOut";
   private static final String EXPIRY_DIVERT = AMQPMirrorLargeMessageDivertDroppedTest.class.getName() + "_ExpiryDiverted";

   // RegularQueue has a 'divert' towards RegularDivert
   private static final String REGULAR_QUEUE = AMQPMirrorLargeMessageDivertDroppedTest.class.getName() + "_RegularQueue";
   private static final String REGULAR_QUEUE_DIVERT = AMQPMirrorLargeMessageDivertDroppedTest.class.getName() + "RegularQueueDivert";

   ActiveMQServer serverA;
   ActiveMQServer serverB;

   protected TransportConfiguration newAcceptorConfig(int port, String name) {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.PORT_PROP_NAME, String.valueOf(port));
      params.put(TransportConstants.PROTOCOLS_PROP_NAME, "AMQP,CORE");
      Map<String, Object> amqpParams = new HashMap<>();
      TransportConfiguration tc = new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params, name, amqpParams);
      return tc;
   }

   protected ActiveMQServer createServer(int port, String brokerName) throws Exception {

      final ActiveMQServer server = this.createServer(true, true);

      server.getConfiguration().getAcceptorConfigurations().clear();
      server.getConfiguration().getAcceptorConfigurations().add(newAcceptorConfig(port, "netty-acceptor"));
      server.getConfiguration().setName(brokerName);
      server.getConfiguration().setJournalDirectory(server.getConfiguration().getJournalDirectory() + port);
      server.getConfiguration().setBindingsDirectory(server.getConfiguration().getBindingsDirectory() + port);
      server.getConfiguration().setPagingDirectory(server.getConfiguration().getPagingDirectory() + port);
      server.getConfiguration().setLargeMessagesDirectory(server.getConfiguration().getLargeMessagesDirectory() + port);
      server.getConfiguration().setMessageExpiryScanPeriod(5);

      server.getConfiguration().addAddressSetting("#", new AddressSettings().setExpiryAddress(SimpleString.of(EXPIRY_QUEUE)));

      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(EXPIRY_QUEUE).setRoutingType(RoutingType.ANYCAST));
      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(EXPIRY_DIVERT).setRoutingType(RoutingType.ANYCAST));

      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(REGULAR_QUEUE).setRoutingType(RoutingType.ANYCAST));
      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(REGULAR_QUEUE_DIVERT).setRoutingType(RoutingType.ANYCAST));

      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(BASIC_QUEUE).setRoutingType(RoutingType.ANYCAST));

      {
         DivertConfiguration divertConfiguration = new DivertConfiguration().setName("divertExpiry").setAddress(EXPIRY_QUEUE).setForwardingAddress(EXPIRY_DIVERT).setExclusive(true);
         server.getConfiguration().addDivertConfiguration(divertConfiguration);
      }

      {
         DivertConfiguration divertConfiguration = new DivertConfiguration().setName("divertRegular").setAddress(REGULAR_QUEUE).setForwardingAddress(REGULAR_QUEUE_DIVERT).setExclusive(true);
         server.getConfiguration().addDivertConfiguration(divertConfiguration);
      }

      return server;
   }

   @Test
   public void testDropThroughExpiryAMQP() throws Exception {
      startServers();
      internalTestDropThroughExpiry("AMQP");
   }

   @Test
   public void testDropThroughExpiryCORE() throws Exception {
      startServers();
      internalTestDropThroughExpiry("CORE");
   }

   @Test
   public void testDropThroughRegularAMQP() throws Exception {
      startServers();
      internalTestDropThroughRegular("AMQP");
   }

   @Test
   public void testDropThroughRegularCORE() throws Exception {
      startServers();
      internalTestDropThroughRegular("CORE");
   }

   private void internalTestDropThroughExpiry(String protocol) throws Exception {
      String queueName = getTestMethodName() + "_" + RandomUtil.randomUUIDString();
      serverA.createQueue(QueueConfiguration.of(queueName).setName(queueName).setRoutingType(RoutingType.ANYCAST));

      Queue expiryA = serverA.locateQueue(EXPIRY_QUEUE);
      assertNotNull(expiryA);

      Queue expiryDiverted = serverA.locateQueue(EXPIRY_DIVERT);
      assertNotNull(expiryDiverted);
      assertEquals(0, expiryDiverted.getMessageCount());

      Queue expiryDivertedB = serverB.locateQueue(EXPIRY_DIVERT);
      assertNotNull(expiryDivertedB);
      assertEquals(0, expiryDivertedB.getMessageCount());

      Queue snfQueue = serverA.locateQueue(QueueConfiguration.MIRROR_ADDRESS + "_" + getTestMethodName());
      assertNotNull(snfQueue);
      Wait.assertEquals(0L, snfQueue::getMessageCount, 5000, 100);

      ConnectionFactory factoryA = CFUtil.createConnectionFactory(protocol, URI_SERVER_A);
      ConnectionFactory factoryB = CFUtil.createConnectionFactory(protocol, URI_SERVER_B);

      try (Connection connection = factoryA.createConnection()) {
         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            try (MessageProducer producer = session.createProducer(session.createQueue(queueName))) {
               producer.setTimeToLive(100);

               Wait.assertEquals(0L, expiryDiverted::getMessageCount, 5000, 100);

               for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
                  producer.send(session.createTextMessage(generateTextBody(i)));
               }
               session.commit();
               Wait.assertEquals(DROP_LIMIT, expiryDiverted::getMessageCount, 5000, 100);
            }
         }
      }

      Wait.assertEquals(DROP_LIMIT, expiryDiverted::getMessageCount, 5000, 100);

      // Expiry happens on target, we should have all message expiring on target
      // I disabled DROP on target to valdiate this
      Wait.assertEquals(NUMBER_OF_MESSAGES, expiryDivertedB::getMessageCount, 5000, 100);

      Wait.assertEquals(0L, snfQueue::getMessageCount, 5000, 100);

      // Firs we consume from B, as there's no DROP limit there, we validate the messages
      consumeMessages(factoryB, EXPIRY_DIVERT, NUMBER_OF_MESSAGES);
      consumeMessages(factoryA, EXPIRY_DIVERT, DROP_LIMIT);

      Wait.assertEquals(0L, expiryDiverted::getMessageCount);
      Wait.assertEquals(0L, expiryDivertedB::getMessageCount);

      assertEquals(0, QueueImplTestAccessor.getQueueMemorySize(expiryDiverted));
      assertEquals(0, QueueImplTestAccessor.getQueueMemorySize(expiryDivertedB));

      // This is to validate Mirror was able to finish the load
      // The issue the test was reproducing was an issue where mirror would disconnect and never complete execution
      Wait.assertEquals(0L, snfQueue::getMessageCount, 5000, 100);

      // validate the mirror still operational
      validateBasicQueue(protocol);
   }

   private void consumeMessages(ConnectionFactory factory, String queueName, long numberOfMessages) throws JMSException {
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(session.createQueue(queueName));
         connection.start();
         for (int i = 0; i < numberOfMessages; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals(generateTextBody(i), message.getText());
         }
         assertNull(consumer.receiveNoWait());
      }
   }

   private @NonNull String generateTextBody(int i) {
      return "hello" + i + "_" + LARGE_BUFFER;
   }

   // to make sure basic operation still works fine on the mirror
   private void validateBasicQueue(String protocol) throws Exception {
      ConnectionFactory cfA = CFUtil.createConnectionFactory(protocol, URI_SERVER_A);

      {
         try (Connection connection = cfA.createConnection();
              Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
              MessageProducer producer = session.createProducer(session.createQueue(BASIC_QUEUE))) {
            for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
               producer.send(session.createTextMessage("hello" + i));
            }
         }
      }

      Queue basicA = serverA.locateQueue(BASIC_QUEUE);
      assertNotNull(basicA);
      Wait.assertEquals(NUMBER_OF_MESSAGES, basicA::getMessageCount, 5000, 100);

      Queue basicB = serverB.locateQueue(BASIC_QUEUE);
      assertNotNull(basicB);
      Wait.assertEquals(NUMBER_OF_MESSAGES, basicB::getMessageCount, 5000, 100);

      {
         try (Connection connectionA = cfA.createConnection();
              Session sessionA = connectionA.createSession(true, Session.SESSION_TRANSACTED);
              MessageConsumer consumerA = sessionA.createConsumer(sessionA.createQueue(BASIC_QUEUE))) {
            connectionA.start();
            for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
               TextMessage message = (TextMessage) consumerA.receive(1000);
               assertNotNull(message);
               assertEquals("hello" + i, message.getText());
            }
            sessionA.commit();
         }
      }

      Wait.assertEquals(0L, basicA::getMessageCount, 5000, 100);
      Wait.assertEquals(0L, basicB::getMessageCount, 5000, 100);

   }

   private void internalTestDropThroughRegular(String protocol) throws Exception {
      Queue regularQueueA = serverA.locateQueue(REGULAR_QUEUE);
      assertNotNull(regularQueueA);

      Queue regularQueueB = serverB.locateQueue(REGULAR_QUEUE);
      assertNotNull(regularQueueB);

      Queue regularDivertedA = serverA.locateQueue(REGULAR_QUEUE_DIVERT);
      assertNotNull(regularDivertedA);
      assertEquals(0, regularDivertedA.getMessageCount());

      Queue regularDivertedB = serverB.locateQueue(REGULAR_QUEUE_DIVERT);
      assertNotNull(regularDivertedB);
      assertEquals(0, regularDivertedB.getMessageCount());

      Queue snfQueue = serverA.locateQueue(QueueConfiguration.MIRROR_ADDRESS + "_" + getTestMethodName());
      assertNotNull(snfQueue);
      Wait.assertEquals(0L, snfQueue::getMessageCount, 5000, 100);

      ConnectionFactory factoryA = CFUtil.createConnectionFactory(protocol, URI_SERVER_A);
      ConnectionFactory factoryB = CFUtil.createConnectionFactory(protocol, URI_SERVER_B);

      try (Connection connection = factoryA.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(session.createQueue(REGULAR_QUEUE));

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            producer.send(session.createTextMessage(generateTextBody(i)));
         }
      }

      Wait.assertEquals(DROP_LIMIT, regularDivertedA::getMessageCount, 5000, 100);
      consumeMessages(factoryA, REGULAR_QUEUE_DIVERT, DROP_LIMIT);

      Wait.assertEquals(0L, regularDivertedA::getMessageCount, 5000, 100);

      assertEquals(0, regularQueueA.getMessageCount());
      // making sure the sizes match
      assertEquals(0L, QueueImplTestAccessor.getQueueMemorySize(regularQueueA));
      assertEquals(0, regularDivertedA.getMessageCount());
      assertEquals(0L, QueueImplTestAccessor.getQueueMemorySize(regularDivertedA));

      Wait.assertEquals(0L, regularDivertedB::getMessageCount, 5000, 100);
      // making sure the sizes match
      assertEquals(0L, QueueImplTestAccessor.getQueueMemorySize(regularQueueB));
      assertEquals(0, regularDivertedB.getMessageCount());
      assertEquals(0L, QueueImplTestAccessor.getQueueMemorySize(regularDivertedB));

      // This is to validate Mirror was able to finish the load
      // The issue the test was reproducing was an issue where mirror would disconnect and never complete execution
      Wait.assertEquals(0L, snfQueue::getMessageCount, 5000, 100);
      assertEquals(0L, QueueImplTestAccessor.getQueueMemorySize(snfQueue));

      // no messages should be consumed on ServerB
      consumeMessages(factoryB, REGULAR_QUEUE_DIVERT, 0);

      // validate the mirror still operational
      validateBasicQueue(protocol);
   }

   private void startServers() throws Exception {
      serverB = createServer(PORT_SERVER_B, getTestMethodName() + "_B");
      serverB.start();

      serverA = createServer(PORT_SERVER_A, getTestMethodName() + "_A");
      // only setting drop on the main servers. This is to validate mirror semantics between the two servers
      serverA.getConfiguration().addAddressSetting(REGULAR_QUEUE_DIVERT.toString(), new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.DROP).setMaxSizeMessages(DROP_LIMIT));
      serverA.getConfiguration().addAddressSetting(EXPIRY_DIVERT.toString(), new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.DROP).setMaxSizeMessages(DROP_LIMIT));
      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(getTestMethodName(), URI_SERVER_B).setReconnectAttempts(5).setRetryInterval(500);
      AMQPMirrorBrokerConnectionElement replica = new AMQPMirrorBrokerConnectionElement().setType(AMQPBrokerConnectionAddressType.MIRROR).setDurable(true).setMessageAcknowledgements(true);
      amqpConnection.addElement(replica);
      serverA.getConfiguration().addAMQPConnection(amqpConnection);
      serverA.setIdentity(getTestMethodName() + "_A");
      serverA.start();
   }

}