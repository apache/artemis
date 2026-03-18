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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import java.io.PrintStream;
import java.net.URI;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.cli.commands.tools.PrintData;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.LastValueQueue;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.selector.filter.Filterable;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.StringPrintStream;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class BrokerInSyncTest extends AmqpClientTestSupport {

   protected static final int AMQP_PORT_2 = 5673;
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   ActiveMQServer server_2;
   private AssertionLoggerHandler loggerHandler;

   @AfterEach
   public void stopServer1() throws Exception {
      if (server != null) {
         server.stop();
      }
   }

   @AfterEach
   public void stopServer2() throws Exception {
      if (server_2 != null) {
         server_2.stop();
      }
   }

   @BeforeEach
   public void startLogging() {
      loggerHandler = new AssertionLoggerHandler();

   }

   @AfterEach
   public void stopLogging() throws Exception {
      try {
         assertFalse(loggerHandler.findText("AMQ222214"));
      } finally {
         loggerHandler.close();
      }
   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      return createServer(AMQP_PORT, false);
   }

   @Test
   public void testSyncOnCreateQueues() throws Exception {
      server.setIdentity("Server1");
      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("connectTowardsServer2", "tcp://localhost:" + AMQP_PORT_2).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server.getConfiguration().addAMQPConnection(amqpConnection);
      }
      server.start();

      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("Server2");

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("connectTowardsServer1", "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server_2.getConfiguration().addAMQPConnection(amqpConnection);
      }

      server_2.start();

      server_2.addAddressInfo(new AddressInfo("sometest").setAutoCreated(false));
      server_2.createQueue(QueueConfiguration.of("sometest").setDurable(true));

      Wait.assertTrue(() -> server_2.locateQueue("sometest") != null);
      Wait.assertTrue(() -> server.locateQueue("sometest") != null);

      server.addAddressInfo(new AddressInfo("OnServer1").setAutoCreated(false));
      server.createQueue(QueueConfiguration.of("OnServer1").setDurable(true));

      Wait.assertTrue(() -> server.locateQueue("OnServer1") != null);
      Wait.assertTrue("Sync is not working on the way back", () -> server_2.locateQueue("OnServer1") != null, 2000);

      Wait.assertTrue(() -> server_2.locateQueue("sometest") != null);
      Wait.assertTrue(() -> server.locateQueue("sometest") != null);

      for (int i = 0; i < 10; i++) {
         final int queueID = i;
         server_2.createQueue(QueueConfiguration.of("test2_" + i).setDurable(true));
         server.createQueue(QueueConfiguration.of("test1_" + i).setDurable(true));
         Wait.assertTrue(() -> server.locateQueue("test2_" + queueID) != null);
         Wait.assertTrue(() -> server.locateQueue("test1_" + queueID) != null);
         Wait.assertTrue(() -> server_2.locateQueue("test2_" + queueID) != null);
         Wait.assertTrue(() -> server_2.locateQueue("test1_" + queueID) != null);
      }

      server_2.stop();
      server.stop();
   }

   @Test
   public void testSingleMessage() throws Exception {
      testSingleMessage("AMQP");
   }

   @Test
   public void testSingleMessageCore() throws Exception {
      testSingleMessage("CORE");
   }

   @Test
   public void testSingleMessageOpenWire() throws Exception {
      testSingleMessage("OPENWIRE");
   }

   public void testSingleMessage(String protocol) throws Exception {
      server.setIdentity("Server1");
      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("connectTowardsServer2", "tcp://localhost:" + AMQP_PORT_2).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server.getConfiguration().addAMQPConnection(amqpConnection);
      }
      server.start();

      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("Server2");

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("connectTowardsServer1", "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server_2.getConfiguration().addAMQPConnection(amqpConnection);
      }

      server_2.start();

      server_2.addAddressInfo(new AddressInfo(getQueueName()).setAutoCreated(false).addRoutingType(RoutingType.ANYCAST));
      server_2.createQueue(QueueConfiguration.of(getQueueName()).setDurable(true).setRoutingType(RoutingType.ANYCAST));

      Wait.assertTrue(() -> server_2.locateQueue(getQueueName()) != null);
      Wait.assertTrue(() -> server.locateQueue(getQueueName()) != null);

      ConnectionFactory cf1 = CFUtil.createConnectionFactory(protocol, "tcp://localhost:" + AMQP_PORT);
      Connection connection1 = cf1.createConnection();
      Session session1 = connection1.createSession(true, Session.SESSION_TRANSACTED);
      connection1.start();

      ConnectionFactory cf2 = CFUtil.createConnectionFactory(protocol, "tcp://localhost:" + AMQP_PORT_2);
      Connection connection2 = cf2.createConnection();
      Session session2 = connection2.createSession(true, Session.SESSION_TRANSACTED);
      connection2.start();

      Queue queue = session1.createQueue(getQueueName());

      MessageProducer producerServer1 = session1.createProducer(queue);
      MessageProducer producerServer2 = session2.createProducer(queue);

      TextMessage message = session1.createTextMessage("test");
      message.setIntProperty("i", 0);
      message.setStringProperty("server", server.getIdentity());
      producerServer1.send(message);
      session1.commit();

      org.apache.activemq.artemis.core.server.Queue queueOnServer1 = server.locateQueue(getQueueName());
      org.apache.activemq.artemis.core.server.Queue queueOnServer2 = server_2.locateQueue(getQueueName());
      assertNotNull(queueOnServer1);
      assertNotNull(queueOnServer2);

      Wait.assertEquals(1, queueOnServer1::getMessageCount);
      Wait.assertEquals(1, queueOnServer2::getMessageCount);

      message = session1.createTextMessage("test");
      message.setIntProperty("i", 1);
      message.setStringProperty("server", server_2.getIdentity());
      producerServer2.send(message);
      session2.commit();

      if (logger.isDebugEnabled() && !Wait.waitFor(() -> queueOnServer1.getMessageCount() == 2)) {
         debugData();
      }

      Wait.assertEquals(2, queueOnServer1::getMessageCount);
      Wait.assertEquals(2, queueOnServer2::getMessageCount);


      connection2.start();
      try (MessageConsumer consumer = session2.createConsumer(queue)) {
         javax.jms.Message receivedMessage = consumer.receive(5000);
         assertNotNull(message);
         checkProperties(connection2, receivedMessage);
         session2.commit();
      }

      Wait.assertEquals(1L, queueOnServer1::getMessageCount, 5000, 100);
      Wait.assertEquals(1L, queueOnServer2::getMessageCount, 5000, 100);

      connection1.start();
      try (MessageConsumer consumer = session1.createConsumer(queue)) {
         javax.jms.Message receivedMessage = consumer.receive(5000);
         assertNotNull(message);
         checkProperties(connection1, receivedMessage);
         session1.commit();
      }

      connection1.close();
      connection2.close();

      Wait.assertEquals(0L, queueOnServer1::getMessageCount, 5000, 100);
      Wait.assertEquals(0L, queueOnServer2::getMessageCount, 5000, 100);

      server_2.stop();
      server.stop();
   }

   @Test
   public void testNoTemporaryAddressesOrQueues() throws Exception {
      final String snfOnServer1Name = "$ACTIVEMQ_ARTEMIS_MIRROR_connectTowardsServer2";

      server.getConfiguration().setAddressQueueScanPeriod(100);
      server.setIdentity("Server1");
      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("connectTowardsServer2", "tcp://localhost:" + AMQP_PORT_2).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server.getConfiguration().addAMQPConnection(amqpConnection);
      }

      server.start();
      server.addAddressInfo(new AddressInfo(getQueueName()).setAutoCreated(false).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(getQueueName()).setDurable(true).setRoutingType(RoutingType.ANYCAST));

      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("Server2");

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("connectTowardsServer1", "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server_2.getConfiguration().addAMQPConnection(amqpConnection);
      }
      server_2.start();
      server_2.addAddressInfo(new AddressInfo(getQueueName()).setAutoCreated(false).addRoutingType(RoutingType.ANYCAST));
      server_2.createQueue(QueueConfiguration.of(getQueueName()).setDurable(true).setRoutingType(RoutingType.ANYCAST));


      Wait.waitFor(() -> server.locateQueue(snfOnServer1Name) != null);

      ConnectionFactory factoryServer1 = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

      // exercising a legal mirror operation to make sure things are working properly
      try (Connection connection = factoryServer1.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(getQueueName());
         MessageProducer producer = session.createProducer(queue);
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
         producer.send(session.createMessage());
         session.commit();
         assertNotNull(consumer.receive(5000));
         session.commit();
      }

      org.apache.activemq.artemis.core.server.Queue snfOnServer1 = server.locateQueue(snfOnServer1Name);
      Wait.assertEquals(0L, snfOnServer1::getMessageCount, 5000, 100);


      // stopping the server to let things accumulate so we can assert the SNF
      server_2.stop();

      tempAddressSendAndReceive(factoryServer1, snfOnServer1, true);
      tempAddressSendAndReceive(factoryServer1, snfOnServer1, false);

      server_2.stop();
      server.stop();
   }

   private void tempAddressSendAndReceive(ConnectionFactory factoryServer1, org.apache.activemq.artemis.core.server.Queue snfQueue, boolean useTopic) throws Exception {
      String addressName;
      try (Connection connectionServer1 = factoryServer1.createConnection()) {
         Session session = connectionServer1.createSession(true, Session.SESSION_TRANSACTED);
         Destination destination;

         if (useTopic) {
            TemporaryTopic topic = session.createTemporaryTopic();
            destination = topic;
            addressName = topic.getTopicName();
         } else {
            TemporaryQueue queue = session.createTemporaryQueue();
            destination = queue;
            addressName = queue.getQueueName();
         }


         MessageConsumer consumer = session.createConsumer(destination);

         connectionServer1.start();

         MessageProducer producer = session.createProducer(destination);
         producer.send(session.createTextMessage());
         session.commit();

         assertNotNull(consumer.receive(5000));

         session.commit();

         Wait.assertEquals(0L, snfQueue::getMessageCount, 5000, 100);

         // stopping the server to validate things are not accumulating

         for (int i = 0; i < 100; i++) {
            // sends should not make into the SNF either
            producer.send(session.createTextMessage());
         }
         session.commit();
         // no temporary sends
         Wait.assertEquals(0L, snfQueue::getMessageCount, 5000, 100);

         for (int i = 0; i < 100; i++) {
            assertNotNull(consumer.receive(5000));
         }
         session.commit();

         // no temporary acks
         Wait.assertEquals(0L, snfQueue::getMessageCount, 5000, 100);
      }

      Wait.assertTrue(() -> server.getAddressInfo(SimpleString.of(addressName)) == null, 5000, 100);
      Wait.assertEquals(0L, snfQueue::getMessageCount, 5000, 100);
   }

   private void checkProperties(Connection connection, javax.jms.Message message) throws Exception {
      try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
         TemporaryQueue temporaryQueue = session.createTemporaryQueue();
         MessageProducer producer = session.createProducer(temporaryQueue);
         producer.send(message);
         connection.start();
         MessageConsumer consumer = session.createConsumer(temporaryQueue);
         javax.jms.Message receivedMessage = consumer.receive(5000);
         assertNotNull(receivedMessage);

         // The cleanup for x-opt happens on server's side.
         // we may receive if coming directly from a mirrored queue,
         // however we should cleanup on the next send to avoid invalid IDs on the server
         Enumeration propertyNames = receivedMessage.getPropertyNames();
         while (propertyNames.hasMoreElements()) {
            String property = String.valueOf(propertyNames.nextElement());
            assertFalse(property.startsWith("x-opt"));
         }
      }
   }


   private org.apache.activemq.artemis.core.server.Queue locateQueueWithWait(ActiveMQServer server, String queueName) throws Exception {
      Wait.assertTrue(() -> server.locateQueue(queueName) != null);
      org.apache.activemq.artemis.core.server.Queue queue = server.locateQueue(queueName);
      assertNotNull(queue);
      return queue;
   }

   @Test
   public void testExpiryNoReaper() throws Exception {
      internalExpiry(false);
   }

   @Test
   public void testExpiry() throws Exception {
      internalExpiry(true);
   }

   private void internalExpiry(boolean useReaper) throws Exception {
      server.setIdentity("Server1");
      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("to_2", "tcp://localhost:" + AMQP_PORT_2).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server.getConfiguration().addAMQPConnection(amqpConnection);
      }

      server.getConfiguration().addAddressSetting("#", new AddressSettings().setExpiryAddress(SimpleString.of("expiryQueue")));

      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName("expiryQueue"));
      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of("expiryQueue").setRoutingType(RoutingType.ANYCAST));
      if (!useReaper) {
         server.getConfiguration().setMessageExpiryScanPeriod(-1);
      }

      server.start();

      server_2 = createServer(AMQP_PORT_2, false);

      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName("expiryQueue"));
      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of("expiryQueue").setRoutingType(RoutingType.ANYCAST));
      if (!useReaper) {
         server_2.getConfiguration().setMessageExpiryScanPeriod(-1);
      }

      server_2.setIdentity("Server2");

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("to_1", "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server_2.getConfiguration().addAMQPConnection(amqpConnection);
      }

      server_2.getConfiguration().addAddressSetting("#", new AddressSettings().setExpiryAddress(SimpleString.of("expiryQueue")));

      server_2.start();

      org.apache.activemq.artemis.core.server.Queue to1 = locateQueueWithWait(server_2, "$ACTIVEMQ_ARTEMIS_MIRROR_to_1");
      org.apache.activemq.artemis.core.server.Queue to2 = locateQueueWithWait(server, "$ACTIVEMQ_ARTEMIS_MIRROR_to_2");
      org.apache.activemq.artemis.core.server.Queue expiry1 = locateQueueWithWait(server, "expiryQueue");
      org.apache.activemq.artemis.core.server.Queue expiry2 = locateQueueWithWait(server_2, "expiryQueue");

      server_2.addAddressInfo(new AddressInfo(getQueueName()).setAutoCreated(false).addRoutingType(RoutingType.ANYCAST));
      server_2.createQueue(QueueConfiguration.of(getQueueName()).setDurable(true).setRoutingType(RoutingType.ANYCAST));

      org.apache.activemq.artemis.core.server.Queue queueOnServer1 = locateQueueWithWait(server, getQueueName());
      org.apache.activemq.artemis.core.server.Queue queueOnServer2 = locateQueueWithWait(server_2, getQueueName());

      ConnectionFactory cf1 = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
      Connection connection1 = cf1.createConnection();
      Session session1 = connection1.createSession(true, Session.SESSION_TRANSACTED);
      connection1.start();

      Queue queue = session1.createQueue(getQueueName());

      MessageProducer producerServer1 = session1.createProducer(queue);

      producerServer1.setTimeToLive(1000);

      TextMessage message = session1.createTextMessage("test");
      message.setIntProperty("i", 0);
      message.setStringProperty("server", server.getIdentity());
      producerServer1.send(message);
      session1.commit();

      Wait.assertEquals(1L, queueOnServer1::getMessageCount, 1000, 100);
      Wait.assertEquals(1L, queueOnServer2::getMessageCount, 1000, 100);

      if (useReaper) {
         // if using the reaper, I want to test what would happen with races between the reaper and the Mirror target
         // for that reason we only pause the SNFs if we are using the reaper
         to1.pause();
         to2.pause();
      }

      Thread.sleep(1500);
      if (!useReaper) {
         queueOnServer1.expireReferences(); // we will expire in just on queue hoping the other gets through mirror
      }

      Wait.assertEquals(0L, queueOnServer1::getMessageCount, 2000, 100);
      Wait.assertEquals(0L, queueOnServer2::getMessageCount, 2000, 100);

      Wait.assertEquals(1L, expiry1::getMessageCount, 1000, 100);
      Wait.assertEquals(1L, expiry2::getMessageCount, 1000, 100);

      to1.resume();
      to2.resume();

      if (!useReaper) {
         queueOnServer1.expireReferences(); // in just one queue
      }

      Wait.assertEquals(1L, expiry1::getMessageCount, 1000, 100);
      Wait.assertEquals(1L, expiry2::getMessageCount, 1000, 100);

      connection1.close();

      server_2.stop();
      server.stop();
   }

   @Test
   public void testDLA() throws Exception {
      server.setIdentity("Server1");
      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("to_2", "tcp://localhost:" + AMQP_PORT_2).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server.getConfiguration().addAMQPConnection(amqpConnection);
      }

      server.getConfiguration().addAddressSetting("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("deadLetterQueue")).setMaxDeliveryAttempts(2));

      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName("deadLetterQueue"));
      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of("deadLetterQueue").setRoutingType(RoutingType.ANYCAST));
      server.getConfiguration().setMessageExpiryScanPeriod(-1);

      server.start();

      server_2 = createServer(AMQP_PORT_2, false);

      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName("deadLetterQueue"));
      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of("deadLetterQueue").setRoutingType(RoutingType.ANYCAST));
      server_2.getConfiguration().setMessageExpiryScanPeriod(-1);

      server_2.setIdentity("Server2");

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("to_1", "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server_2.getConfiguration().addAMQPConnection(amqpConnection);
      }

      server_2.getConfiguration().addAddressSetting("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("deadLetterQueue")).setMaxDeliveryAttempts(2));

      server_2.start();

      org.apache.activemq.artemis.core.server.Queue to1 = locateQueueWithWait(server_2, "$ACTIVEMQ_ARTEMIS_MIRROR_to_1");
      org.apache.activemq.artemis.core.server.Queue to2 = locateQueueWithWait(server, "$ACTIVEMQ_ARTEMIS_MIRROR_to_2");
      org.apache.activemq.artemis.core.server.Queue dlq1 = locateQueueWithWait(server, "deadLetterQueue");
      org.apache.activemq.artemis.core.server.Queue dlq2 = locateQueueWithWait(server_2, "deadLetterQueue");

      server_2.addAddressInfo(new AddressInfo(getQueueName()).setAutoCreated(false).addRoutingType(RoutingType.ANYCAST));
      server_2.createQueue(QueueConfiguration.of(getQueueName()).setDurable(true).setRoutingType(RoutingType.ANYCAST));

      org.apache.activemq.artemis.core.server.Queue queueOnServer1 = locateQueueWithWait(server, getQueueName());
      org.apache.activemq.artemis.core.server.Queue queueOnServer2 = locateQueueWithWait(server_2, getQueueName());

      ConnectionFactory cf1 = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
      Connection connection1 = cf1.createConnection();
      Session session1 = connection1.createSession(true, Session.SESSION_TRANSACTED);
      connection1.start();

      Queue queue = session1.createQueue(getQueueName());

      MessageProducer producerServer1 = session1.createProducer(queue);

      TextMessage message = session1.createTextMessage("test");
      message.setIntProperty("i", 0);
      message.setStringProperty("server", server.getIdentity());
      producerServer1.send(message);
      session1.commit();


      MessageConsumer consumer1 = session1.createConsumer(queue);
      connection1.start();

      for (int i = 0; i < 2; i++) {
         TextMessage messageToCancel = (TextMessage) consumer1.receive(1000);
         assertNotNull(messageToCancel);
         session1.rollback();
      }

      assertNull(consumer1.receiveNoWait());

      Wait.assertEquals(0L, queueOnServer1::getMessageCount, 1000, 100);
      Wait.assertEquals(0L, queueOnServer2::getMessageCount, 1000, 100);

      Wait.assertEquals(1L, dlq1::getMessageCount, 1000, 100);
      Wait.assertEquals(1L, dlq2::getMessageCount, 1000, 100);


      dlq1.retryMessages(new Filter() {
         @Override
         public boolean match(Message message) {
            return true;
         }

         @Override
         public boolean match(Map<String, String> map) {
            return true;
         }

         @Override
         public boolean match(Filterable filterable) {
            return true;
         }

         @Override
         public SimpleString getFilterString() {
            return SimpleString.of("Test");
         }
      });

      Wait.assertEquals(1L, queueOnServer1::getMessageCount, 1000, 100);
      Wait.assertEquals(1L, queueOnServer2::getMessageCount, 1000, 100);

      Wait.assertEquals(0L, dlq1::getMessageCount, 1000, 100);
      Wait.assertEquals(0L, dlq2::getMessageCount, 1000, 100);


      connection1.close();

      server_2.stop();
      server.stop();
   }


   @Test
   public void testCreateInternalQueue() throws Exception {
      server.setIdentity("Server1");
      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("to_2", "tcp://localhost:" + AMQP_PORT_2).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server.getConfiguration().addAMQPConnection(amqpConnection);
      }

      server.getConfiguration().addAddressSetting("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("deadLetterQueue")).setMaxDeliveryAttempts(2));

      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName("deadLetterQueue"));
      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of("deadLetterQueue").setRoutingType(RoutingType.ANYCAST));
      server.getConfiguration().setMessageExpiryScanPeriod(-1);

      server.start();

      server_2 = createServer(AMQP_PORT_2, false);

      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName("deadLetterQueue"));
      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of("deadLetterQueue").setRoutingType(RoutingType.ANYCAST));
      server_2.getConfiguration().setMessageExpiryScanPeriod(-1);

      server_2.setIdentity("Server2");

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("to_1", "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server_2.getConfiguration().addAMQPConnection(amqpConnection);
      }

      server_2.getConfiguration().addAddressSetting("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("deadLetterQueue")).setMaxDeliveryAttempts(2));

      server_2.start();

      org.apache.activemq.artemis.core.server.Queue to1 = locateQueueWithWait(server_2, "$ACTIVEMQ_ARTEMIS_MIRROR_to_1");
      assertNotNull(to1);

      long messagesAddedOnS2 = to1.getMessagesAdded();

      String internalQueueName = getQueueName() + "Internal";

      server.addAddressInfo(new AddressInfo(internalQueueName).setAutoCreated(false).addRoutingType(RoutingType.ANYCAST).setInternal(true));
      server.createQueue(QueueConfiguration.of(internalQueueName).setDurable(true).setRoutingType(RoutingType.ANYCAST).setInternal(true));

      server.addAddressInfo(new AddressInfo(getQueueName()).setAutoCreated(false).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(getQueueName()).setDurable(true).setRoutingType(RoutingType.ANYCAST));

      Wait.assertTrue(() -> server_2.locateQueue(getQueueName()) != null, 5000);
      assertNull(server_2.getAddressInfo(SimpleString.of(internalQueueName)));
      assertNull(server_2.locateQueue(internalQueueName));

      assertEquals(messagesAddedOnS2, to1.getMessagesAdded());

      server_2.stop();
      server.stop();
   }




   @Test
   public void testLVQ() throws Exception {
      AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler();
      runAfter(() -> loggerHandler.close());

      server.setIdentity("Server1");
      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("to_2", "tcp://localhost:" + AMQP_PORT_2).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server.getConfiguration().addAMQPConnection(amqpConnection);
      }

      server.getConfiguration().addAddressSetting("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("deadLetterQueue")).setMaxDeliveryAttempts(2));

      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName("deadLetterQueue"));
      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of("deadLetterQueue").setRoutingType(RoutingType.ANYCAST));

      String lvqName = "testLVQ_" + RandomUtil.randomUUIDString();

      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName(lvqName));
      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(lvqName).setRoutingType(RoutingType.ANYCAST).setLastValue(true).setLastValueKey("KEY"));
      server.getConfiguration().setMessageExpiryScanPeriod(-1);

      server.start();

      server_2 = createServer(AMQP_PORT_2, false);

      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName("deadLetterQueue"));
      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of("deadLetterQueue").setRoutingType(RoutingType.ANYCAST));
      server_2.getConfiguration().setMessageExpiryScanPeriod(-1);

      server_2.setIdentity("Server2");

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("to_1", "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server_2.getConfiguration().addAMQPConnection(amqpConnection);
      }

      server_2.getConfiguration().addAddressSetting("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("deadLetterQueue")).setMaxDeliveryAttempts(2));
      server_2.start();

      org.apache.activemq.artemis.core.server.Queue lvqQueue1 = locateQueueWithWait(server, lvqName);
      org.apache.activemq.artemis.core.server.Queue lvqQueue2 = locateQueueWithWait(server, lvqName);

      assertTrue(lvqQueue1.isLastValue());
      assertTrue(lvqQueue2.isLastValue());
      assertInstanceOf(LastValueQueue.class, lvqQueue1);
      assertInstanceOf(LastValueQueue.class, lvqQueue2);
      assertEquals("KEY", lvqQueue1.getQueueConfiguration().getLastValueKey().toString());
      assertEquals("KEY", lvqQueue2.getQueueConfiguration().getLastValueKey().toString());

      ConnectionFactory cf1 = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
      try (Connection connection1 = cf1.createConnection()) {
         Session session1 = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session1.createQueue(lvqName);
         MessageProducer producerServer1 = session1.createProducer(queue);
         connection1.start();
         for (int i = 0; i < 1000; i++) {
            TextMessage message = session1.createTextMessage("test");
            message.setIntProperty("i", 0);
            message.setStringProperty("KEY", "" + (i % 10));
            producerServer1.send(message);
         }
      }
      assertFalse(loggerHandler.findText("AMQ222214"));

      Wait.assertEquals(10L, lvqQueue1::getMessageCount, 2000, 100);
      Wait.assertEquals(10L, lvqQueue2::getMessageCount, 2000, 100);


      server_2.stop();
      server.stop();

      assertFalse(loggerHandler.findText("AMQ222153"));
   }


   @Test
   public void testSyncData() throws Exception {
      int NUMBER_OF_MESSAGES = 100;
      server.setIdentity("Server1");
      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("connectTowardsServer2", "tcp://localhost:" + AMQP_PORT_2).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server.getConfiguration().addAMQPConnection(amqpConnection);
      }
      server.start();

      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("Server2");

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("connectTowardsServer1", "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server_2.getConfiguration().addAMQPConnection(amqpConnection);
      }

      server_2.start();

      server_2.addAddressInfo(new AddressInfo(getQueueName()).setAutoCreated(false).addRoutingType(RoutingType.ANYCAST));
      server_2.createQueue(QueueConfiguration.of(getQueueName()).setDurable(true).setRoutingType(RoutingType.ANYCAST));

      Wait.assertTrue(() -> server_2.locateQueue(getQueueName()) != null);
      Wait.assertTrue(() -> server.locateQueue(getQueueName()) != null);

      ConnectionFactory cf1 = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
      Connection connection1 = cf1.createConnection();
      Session session1 = connection1.createSession(true, Session.SESSION_TRANSACTED);
      connection1.start();

      ConnectionFactory cf2 = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT_2);
      Connection connection2 = cf2.createConnection();
      Session session2 = connection2.createSession(true, Session.SESSION_TRANSACTED);
      connection2.start();

      Queue queue = session1.createQueue(getQueueName());

      MessageProducer producerServer1 = session1.createProducer(queue);
      MessageProducer producerServer2 = session2.createProducer(queue);

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         TextMessage message = session1.createTextMessage("test " + i);
         message.setIntProperty("i", i);
         message.setStringProperty("server", server.getIdentity());
         producerServer1.send(message);
      }
      session1.commit();

      org.apache.activemq.artemis.core.server.Queue queueOnServer1 = server.locateQueue(getQueueName());
      org.apache.activemq.artemis.core.server.Queue queueOnServer2 = server_2.locateQueue(getQueueName());
      assertNotNull(queueOnServer1);
      assertNotNull(queueOnServer2);

      Wait.assertEquals(NUMBER_OF_MESSAGES, queueOnServer1::getMessageCount);
      Wait.assertEquals(NUMBER_OF_MESSAGES, queueOnServer2::getMessageCount);

      for (int i = NUMBER_OF_MESSAGES; i < NUMBER_OF_MESSAGES * 2; i++) {
         TextMessage message = session1.createTextMessage("test " + i);
         message.setIntProperty("i", i);
         message.setStringProperty("server", server_2.getIdentity());
         producerServer2.send(message);
      }
      session2.commit();

      if (logger.isDebugEnabled() && !Wait.waitFor(() -> queueOnServer1.getMessageCount() == NUMBER_OF_MESSAGES * 2)) {
         debugData();
      }

      Wait.assertEquals(NUMBER_OF_MESSAGES * 2, queueOnServer1::getMessageCount);
      Wait.assertEquals(NUMBER_OF_MESSAGES * 2, queueOnServer2::getMessageCount);

      MessageConsumer consumerOn1 = session1.createConsumer(queue);
      for (int i = 0; i < NUMBER_OF_MESSAGES * 2; i++) {
         TextMessage message = (TextMessage) consumerOn1.receive(5000);
         logger.debug("### Client acking message({}) on server 1, a message that was original sent on {} text = {}", i, message.getStringProperty("server"), message.getText());
         assertNotNull(message);
         assertEquals(i, message.getIntProperty("i"));
         assertEquals("test " + i, message.getText());
         session1.commit();
      }

      boolean bothConsumed = Wait.waitFor(() -> {
         long q1 = queueOnServer1.getMessageCount();
         long q2 = queueOnServer2.getMessageCount();
         logger.debug("Queue on Server 1 = {}", q1);
         logger.debug("Queue on Server 2 = {}", q2);
         return q1 == 0 && q2 == 0;
      }, 5_000, 1000);

      if (logger.isDebugEnabled() && !bothConsumed) {
         debugData();
         fail("q1 = " + queueOnServer1.getMessageCount() + ", q2 = " + queueOnServer2.getMessageCount());
      }

      assertEquals(0, queueOnServer1.getMessageCount());
      assertEquals(0, queueOnServer2.getConsumerCount());

      System.out.println("Queue on Server 1 = " + queueOnServer1.getMessageCount());
      System.out.println("Queue on Server 2 = " + queueOnServer2.getMessageCount());

      assertEquals(0, queueOnServer1.getDeliveringCount());
      assertEquals(0, queueOnServer2.getDeliveringCount());
      assertEquals(0, queueOnServer1.getDurableDeliveringCount());
      assertEquals(0, queueOnServer2.getDurableDeliveringCount());
      assertEquals(0, queueOnServer1.getDurableDeliveringSize());
      assertEquals(0, queueOnServer2.getDurableDeliveringSize());
      assertEquals(0, queueOnServer1.getDeliveringSize());
      assertEquals(0, queueOnServer2.getDeliveringSize());

      server_2.stop();
      server.stop();
   }



   @Test
   public void testStats() throws Exception {
      int NUMBER_OF_MESSAGES = 1;
      server.setIdentity("Server1");
      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("connectTowardsServer2", "tcp://localhost:" + AMQP_PORT_2).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server.getConfiguration().addAMQPConnection(amqpConnection);
      }
      server.start();

      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("Server2");

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("connectTowardsServer1", "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server_2.getConfiguration().addAMQPConnection(amqpConnection);
      }

      server_2.start();

      server_2.addAddressInfo(new AddressInfo(getQueueName()).setAutoCreated(false).addRoutingType(RoutingType.ANYCAST));
      server_2.createQueue(QueueConfiguration.of(getQueueName()).setDurable(true).setRoutingType(RoutingType.ANYCAST));

      Wait.assertTrue(() -> server_2.locateQueue(getQueueName()) != null);
      Wait.assertTrue(() -> server.locateQueue(getQueueName()) != null, 5000);

      ConnectionFactory cf1 = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
      Connection connection1 = cf1.createConnection();
      Session session1 = connection1.createSession(true, Session.SESSION_TRANSACTED);
      connection1.start();

      ConnectionFactory cf2 = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT_2);
      Connection connection2 = cf2.createConnection();
      Session session2 = connection2.createSession(true, Session.SESSION_TRANSACTED);
      connection2.start();

      Queue queue = session1.createQueue(getQueueName());

      MessageProducer producerServer1 = session1.createProducer(queue);
      MessageProducer producerServer2 = session2.createProducer(queue);

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         TextMessage message = session1.createTextMessage("test " + i);
         message.setIntProperty("i", i);
         message.setStringProperty("server", server.getIdentity());
         producerServer1.send(message);
      }
      session1.commit();

      org.apache.activemq.artemis.core.server.Queue queueOnServer1 = server.locateQueue(getQueueName());
      org.apache.activemq.artemis.core.server.Queue queueOnServer2 = server_2.locateQueue(getQueueName());
      assertNotNull(queueOnServer1);
      assertNotNull(queueOnServer2);

      Wait.assertEquals(NUMBER_OF_MESSAGES, queueOnServer1::getMessageCount);
      Wait.assertEquals(NUMBER_OF_MESSAGES, queueOnServer2::getMessageCount);

      assertEquals(0, queueOnServer1.getDeliveringSize());
      assertEquals(0, queueOnServer2.getDeliveringSize());

      MessageConsumer consumerOn1 = session1.createConsumer(queue);
      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         TextMessage message = (TextMessage) consumerOn1.receive(5000);
         logger.debug("### Client acking message({}) on server 1, a message that was original sent on {} text = {}", i, message.getStringProperty("server"), message.getText());
         assertNotNull(message);
         assertEquals(i, message.getIntProperty("i"));
         assertEquals("test " + i, message.getText());
         session1.commit();
      }

      Wait.assertEquals(0L, queueOnServer1::getMessageCount, 2000, 100);
      Wait.assertEquals(0L, queueOnServer2::getMessageCount, 2000, 100);
      assertEquals(0, queueOnServer1.getDeliveringCount());
      assertEquals(0, queueOnServer2.getDeliveringCount());
      assertEquals(0, queueOnServer1.getDurableDeliveringCount());
      assertEquals(0, queueOnServer2.getDurableDeliveringCount());
      assertEquals(0, queueOnServer1.getDurableDeliveringSize());
      assertEquals(0, queueOnServer2.getDurableDeliveringSize());
      assertEquals(0, queueOnServer1.getDeliveringSize());
      assertEquals(0, queueOnServer2.getDeliveringSize());

      server_2.stop();
      server.stop();
   }

   private void debugData() throws Exception {
      StringPrintStream stringPrintStream = new StringPrintStream();
      PrintStream out = stringPrintStream.newStream();
      org.apache.activemq.artemis.core.server.Queue queueToDebugOn1 = server.locateQueue(getQueueName());
      org.apache.activemq.artemis.core.server.Queue queueToDebugOn2 = server_2.locateQueue(getQueueName());
      out.println("*******************************************************************************************************************************");
      out.println("Queue on Server 1 with count = " + queueToDebugOn1.getMessageCount());
      queueToDebugOn1.forEach((r) -> out.println("Server1 has reference " + r.getMessage()));
      out.println("*******************************************************************************************************************************");
      out.println("Queue on Server 2 with count = " + queueToDebugOn2.getMessageCount());
      queueToDebugOn2.forEach((r) -> out.println("Server2 has reference " + r.getMessage()));
      out.println("*******************************************************************************************************************************");
      out.println("PrintData Server 1");
      PrintData.printMessages(server.getConfiguration().getJournalLocation(), out, false, false, true, false);
      out.println("*******************************************************************************************************************************");
      out.println("PrintData Server 2");
      PrintData.printMessages(server_2.getConfiguration().getJournalLocation(), out, false, false, true, false);
      logger.debug("Data Available on Servers:\n{}", stringPrintStream.toString());
   }

   @Test
   public void testSyncDataNoSuppliedID() throws Exception {
      int NUMBER_OF_MESSAGES = 100;
      server.setIdentity("Server1");

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("connectTowardsServer2", "tcp://localhost:" + AMQP_PORT_2).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server.getConfiguration().addAMQPConnection(amqpConnection);
      }
      server.start();

      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("Server2");

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("connectTowardsServer1", "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server_2.getConfiguration().addAMQPConnection(amqpConnection);
      }

      server_2.start();

      server_2.addAddressInfo(new AddressInfo(getQueueName()).setAutoCreated(false).addRoutingType(RoutingType.ANYCAST));
      server_2.createQueue(QueueConfiguration.of(getQueueName()).setDurable(true).setRoutingType(RoutingType.ANYCAST));

      Wait.assertTrue(() -> server_2.locateQueue(getQueueName()) != null);
      Wait.assertTrue(() -> server.locateQueue(getQueueName()) != null);

      AmqpClient cf1 = new AmqpClient(new URI("tcp://localhost:" + AMQP_PORT), null, null);
      AmqpConnection connection1 = cf1.createConnection();
      connection1.connect();
      AmqpSession session1 = connection1.createSession();

      AmqpClient cf2 = new AmqpClient(new URI("tcp://localhost:" + AMQP_PORT_2), null, null);
      AmqpConnection connection2 = cf2.createConnection();
      connection2.connect();
      AmqpSession session2 = connection2.createSession();

      AmqpSender producerServer1 = session1.createSender(getQueueName());
      AmqpSender producerServer2 = session2.createSender(getQueueName());

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         AmqpMessage message = new AmqpMessage();
         message.setDurable(true);
         message.setApplicationProperty("i", i);
         producerServer1.send(message);
      }

      org.apache.activemq.artemis.core.server.Queue queueOnServer1 = server.locateQueue(getQueueName());
      org.apache.activemq.artemis.core.server.Queue queueOnServer2 = server_2.locateQueue(getQueueName());
      assertNotNull(queueOnServer1);
      assertNotNull(queueOnServer2);

      Wait.assertEquals(NUMBER_OF_MESSAGES, queueOnServer1::getMessageCount);
      Wait.assertEquals(NUMBER_OF_MESSAGES, queueOnServer2::getMessageCount);

      for (int i = NUMBER_OF_MESSAGES; i < NUMBER_OF_MESSAGES * 2; i++) {
         AmqpMessage message = new AmqpMessage();
         message.setDurable(true);
         message.setApplicationProperty("i", i);
         producerServer2.send(message);
      }

      Wait.assertEquals(NUMBER_OF_MESSAGES * 2, queueOnServer1::getMessageCount);
      Wait.assertEquals(NUMBER_OF_MESSAGES * 2, queueOnServer2::getMessageCount);

      AmqpReceiver consumerOn1 = session1.createReceiver(getQueueName());
      consumerOn1.flow(NUMBER_OF_MESSAGES * 2 + 1);
      for (int i = 0; i < NUMBER_OF_MESSAGES * 2; i++) {
         AmqpMessage message = consumerOn1.receive(5, TimeUnit.SECONDS);
         assertNotNull(message);
         message.accept();
         assertEquals(i, (int) message.getApplicationProperty("i"));
      }

      Wait.assertEquals(0, queueOnServer1::getMessageCount);
      Wait.assertEquals(0, () -> {
         System.out.println(queueOnServer2.getMessageCount());
         return queueOnServer2.getMessageCount();
      });

      connection1.close();
      connection2.close();
      server_2.stop();
      server.stop();
   }

   @Test
   public void testLargeMessageInSync() throws Exception {
      String queueName = "testSyncLargeMessage";
      server.setIdentity("Server1");
      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("connectTowardsServer2", "tcp://localhost:" + AMQP_PORT_2).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server.getConfiguration().addAMQPConnection(amqpConnection);
      }
      server.start();

      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("Server2");

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("connectTowardsServer1", "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server_2.getConfiguration().addAMQPConnection(amqpConnection);
      }

      server_2.start();

      server_2.addAddressInfo(new AddressInfo(queueName).setAutoCreated(false).addRoutingType(RoutingType.ANYCAST));
      server_2.createQueue(QueueConfiguration.of(queueName).setDurable(true).setRoutingType(RoutingType.ANYCAST));

      Wait.assertTrue(() -> server_2.locateQueue(queueName) != null);
      Wait.assertTrue(() -> server.locateQueue(queueName) != null);

      String bigString = RandomUtil.randomAlphaNumericString(200 * 1024);

      ConnectionFactory factory1 = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
      ConnectionFactory factory2 = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT_2);

      try (Connection connection = factory1.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);
         MessageProducer producer = session.createProducer(queue);
         producer.send(session.createTextMessage(bigString));
      }

      try (Connection connection = factory2.createConnection()) {
         org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(queueName);
         Wait.assertEquals(1, serverQueue::getMessageCount);
         Wait.assertEquals(1, () -> getNumberOfFiles(server_2.getConfiguration().getLargeMessagesLocation()), 5000, 100);
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
         TextMessage message = (TextMessage)consumer.receive(5000);
         assertNotNull(message);
         assertEquals(bigString, message.getText());
         Wait.assertEquals(0, () -> getNumberOfFiles(server_2.getConfiguration().getLargeMessagesLocation()), 5000, 100);
      }

      try (Connection connection = factory1.createConnection()) {
         org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(queueName);

         Wait.assertEquals(0L, serverQueue::getMessageCount, 2000, 100);

         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
         TextMessage message = (TextMessage)consumer.receiveNoWait();
         assertNull(message);
         server.stop();
         server_2.stop();
         Wait.assertEquals(0, () -> getNumberOfFiles(server.getConfiguration().getLargeMessagesLocation()), 1000, 100);
      }
   }

}
