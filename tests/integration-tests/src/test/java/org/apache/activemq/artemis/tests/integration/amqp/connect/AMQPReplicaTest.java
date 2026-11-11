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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorMessageFactory;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
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

public class AMQPReplicaTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected static final int AMQP_PORT_2 = 5673;
   protected static final int AMQP_PORT_3 = 5674;
   public static final int TIME_BEFORE_RESTART = 1000;

   ActiveMQServer server_2;

   private AssertionLoggerHandler loggerHandler;

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
   protected String getConfiguredProtocols() {
      return "AMQP,OPENWIRE,CORE";
   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      return createServer(AMQP_PORT, false);
   }

   @Test
   public void testReplicaCatchupOnQueueCreates() throws Exception {
      server.setIdentity("Server1");
      server.stop();

      server_2 = createServer(AMQP_PORT_2, false);

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("test", "tcp://localhost:" + AMQP_PORT);
      amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement());
      server_2.getConfiguration().addAMQPConnection(amqpConnection);

      server_2.start();

      server_2.addAddressInfo(new AddressInfo("sometest").setAutoCreated(false));
      server_2.createQueue(QueueConfiguration.of("sometest").setDurable(true));

      Wait.assertTrue(() -> server_2.locateQueue("sometest") != null);

      server_2.stop();

      server.start();
      assertNull(server.locateQueue("sometest"));
      Wait.assertTrue(server::isActive);
      server_2.start();
      // if this does not succeed the catch up did not arrive at the other server
      Wait.assertTrue(() -> server.locateQueue("sometest") != null);
      server_2.stop();
      server.stop();
   }


   @Test
   public void testNotFoundRetries() throws Exception {
      server.setIdentity("Server1");

      server.start();

      server_2 = createServer(AMQP_PORT_2, false);

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("test", "tcp://localhost:" + AMQP_PORT);
      amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
      server_2.getConfiguration().addAMQPConnection(amqpConnection);

      server_2.start();

      server_2.addAddressInfo(new AddressInfo("sometest").setAutoCreated(false));
      server_2.createQueue(QueueConfiguration.of("sometest").setDurable(true));


      Wait.assertTrue(() -> server_2.locateQueue("sometest") != null);


      Wait.waitFor(() -> server_2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_test") != null);
      Queue mirrorQueue = server_2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_test");
      assertNotNull(mirrorQueue);


      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {

         // Adding some PostAck event that will never be found on the target for an expiry
         org.apache.activemq.artemis.api.core.Message message = AMQPMirrorMessageFactory.createMessage(mirrorQueue.getAddress().toString(), SimpleString.of("sometest"), SimpleString.of("sometest"), AMQPMirrorControllerSource.POST_ACK, "0000", 3333L, AckReason.EXPIRED).setDurable(true);
         message.setMessageID(server_2.getStorageManager().generateID());
         server_2.getPostOffice().route(message, false);

         // Adding some PostAck event that will never be found on the target for a regular ack
         message = AMQPMirrorMessageFactory.createMessage(mirrorQueue.getAddress().toString(), SimpleString.of("sometest"), SimpleString.of("sometest"), AMQPMirrorControllerSource.POST_ACK, "0000", 3334L, AckReason.NORMAL).setDurable(true);
         message.setMessageID(server_2.getStorageManager().generateID());
         server_2.getPostOffice().route(message, false);

         Wait.assertEquals(0L, mirrorQueue::getMessageCount, 2000, 100);
         assertFalse(loggerHandler.findText("AMQ224041"));
      }

      server_2.stop();
      server.stop();
   }


   @Test
   public void testDeleteQueueWithRemoveFalse() throws Exception {
      server.setIdentity("Server1");
      server.start();

      server_2 = createServer(AMQP_PORT_2, false);

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("test", "tcp://localhost:" + AMQP_PORT);
      amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setQueueRemoval(false));
      server_2.getConfiguration().addAMQPConnection(amqpConnection);

      server_2.start();

      SimpleString queueName = RandomUtil.randomUUIDSimpleString();

      server_2.addAddressInfo(new AddressInfo(queueName).setAutoCreated(false));
      server_2.createQueue(QueueConfiguration.of(queueName).setDurable(true));

      Wait.assertTrue(() -> server_2.locateQueue(queueName) != null);
      Wait.assertTrue(() -> server.locateQueue(queueName) != null);


      server_2.destroyQueue(queueName);
      Wait.assertTrue(() -> server_2.locateQueue(queueName) == null);
      Thread.sleep(100);

      assertNotNull(server.locateQueue(queueName), "Queue was removed when it was configured to not remove it");

      server_2.stop();
      server.stop();
   }


   @Test
   public void testSendCreateQueue() throws Exception {
      doSendCreateQueueTestImpl(true);
   }

   @Test
   public void testDoNotSendCreateQueue() throws Exception {
      doSendCreateQueueTestImpl(false);
   }

   private void doSendCreateQueueTestImpl(boolean sendCreate) throws Exception {
      server.start();

      final SimpleString ADDRESS_NAME = SimpleString.of("address");

      server_2 = createServer(AMQP_PORT_2, false);

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("test", "tcp://localhost:" + AMQP_PORT);
      AMQPMirrorBrokerConnectionElement mirror = new AMQPMirrorBrokerConnectionElement();
      if (sendCreate) {
         mirror.setQueueCreation(true);
         mirror.setQueueRemoval(false);
      } else {
         mirror.setQueueCreation(false);
      }
      amqpConnection.addElement(mirror);
      server_2.getConfiguration().addAMQPConnection(amqpConnection);

      server_2.start();
      Wait.assertTrue(server::isActive);

      server_2.addAddressInfo(new AddressInfo(ADDRESS_NAME).addRoutingType(RoutingType.ANYCAST));
      server_2.createQueue(QueueConfiguration.of(ADDRESS_NAME).setDurable(true).setAddress(ADDRESS_NAME));

      if (sendCreate) {
         Wait.assertTrue(() -> server.locateQueue(ADDRESS_NAME) != null);
         Wait.assertTrue(() -> server.getAddressInfo(ADDRESS_NAME) != null);
      } else {
         Thread.sleep(250); // things are asynchronous, I need to wait some time to make sure things are transferred over
         assertNull(server.locateQueue(ADDRESS_NAME));
         assertNull(server.getAddressInfo(ADDRESS_NAME));
      }
      server_2.stop();
      server.stop();
   }

   @Test
   public void testReplicaCatchupOnQueueCreatesAndDeletes() throws Exception {
      server.start();
      server.setIdentity("Server1");
      server.addAddressInfo(new AddressInfo("sometest").setAutoCreated(false).addRoutingType(RoutingType.MULTICAST));
      // This queue will disappear from the source, so it should go
      server.createQueue(QueueConfiguration.of("ToBeGone").setDurable(true).setRoutingType(RoutingType.MULTICAST));
      server.stop();

      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("server_2");

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("test", "tcp://localhost:" + AMQP_PORT);
      amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement());
      server_2.getConfiguration().addAMQPConnection(amqpConnection);

      server_2.start();

      server_2.addAddressInfo(new AddressInfo("sometest").setAutoCreated(false).addRoutingType(RoutingType.MULTICAST));
      server_2.createQueue(QueueConfiguration.of("sometest").setDurable(true).setRoutingType(RoutingType.MULTICAST));

      Wait.assertTrue(() -> server_2.locateQueue("sometest") != null);

      server_2.stop();

      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("server_2");

      amqpConnection = new AMQPBrokerConnectConfiguration("test", "tcp://localhost:" + AMQP_PORT);
      amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement());
      server_2.getConfiguration().addAMQPConnection(amqpConnection);

      server.start();
      assertNull(server.locateQueue("sometest"));
      assertNotNull(server.locateQueue("ToBeGone"));
      Wait.assertTrue(server::isActive);
      server_2.start();
      // if this does not succeed the catch up did not arrive at the other server
      Wait.assertTrue(() -> server.locateQueue("sometest") != null);
      server_2.stop();
      server.stop();
   }


   @Test
   public void testReplicaWithDurable() throws Exception {
      server.start();
      server.setIdentity("Server1");
      server.addAddressInfo(new AddressInfo("sometest").setAutoCreated(false).addRoutingType(RoutingType.MULTICAST));
      server.stop();

      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("server_2");

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("test", "tcp://localhost:" + AMQP_PORT);
      amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement());
      server_2.getConfiguration().addAMQPConnection(amqpConnection);

      server_2.start();

      server_2.addAddressInfo(new AddressInfo("sometest").setAutoCreated(false).addRoutingType(RoutingType.MULTICAST));
      server_2.createQueue(QueueConfiguration.of("sometest").setDurable(true).setRoutingType(RoutingType.MULTICAST));

      Wait.assertTrue(() -> server_2.locateQueue("sometest") != null);

      server_2.stop();

      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("server_2");

      amqpConnection = new AMQPBrokerConnectConfiguration("test", "tcp://localhost:" + AMQP_PORT);
      amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement());
      server_2.getConfiguration().addAMQPConnection(amqpConnection);

      server.start();
      assertNull(server.locateQueue("sometest"));
      Wait.assertTrue(server::isActive);
      server_2.start();
      // if this does not succeed the catch up did not arrive at the other server
      Wait.assertTrue(() -> server.locateQueue("sometest") != null);
      server_2.stop();
      server.stop();
   }

   @Test
   public void testReplicaLargeMessages() throws Exception {
      replicaTest(true, true, false, false, false, false, false);
   }

   @Test
   public void testReplicaLargeMessagesPagingEverywhere() throws Exception {
      replicaTest(true, true, true, true, false, false, false);
   }

   @Test
   public void testReplica() throws Exception {
      replicaTest(false, true, false, false, false, false, false);
   }

   @Test
   public void testReplicaRestartBrokerConnection() throws Exception {
      replicaTest(false, true, false, false, false, false, true);
   }

   @Test
   public void testReplicaRestart() throws Exception {
      replicaTest(false, true, false, false, false, true, false);
   }

   @Test
   public void testReplicaDeferredStart() throws Exception {
      replicaTest(false, true, false, false, true, false, false);
   }

   @Test
   public void testReplicaCopyOnly() throws Exception {
      replicaTest(false, false, false, false, false, false, false);
   }

   @Test
   public void testReplicaPagedTarget() throws Exception {
      replicaTest(false, true, true, false, false, false, false);
   }

   @Test
   public void testReplicaPagingEverywhere() throws Exception {
      replicaTest(false, true, true, true, false, false, false);
   }

   private String getText(boolean large, int i) {
      if (!large) {
         return "Text " + i;
      } else {
         StringBuilder sb = new StringBuilder();
         while (sb.length() < 110 * 1024) {
            sb.append("Text " + i + " ");
         }
         return sb.toString();
      }
   }

   /**
    * This test is validating that annotations sent to the original broker are not translated to the receiving side.
    * Also annotations could eventually damage the body if the broker did not take that into consideration. So, this
    * test is sending delivery annotations on messages.
    */
   @Test
   public void testLargeMessagesWithDeliveryAnnotations() throws Exception {
      server.setIdentity("targetServer");
      server.start();
      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("server_2");

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("test", "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(-1).setRetryInterval(100);
      AMQPMirrorBrokerConnectionElement replica = new AMQPMirrorBrokerConnectionElement().setMessageAcknowledgements(true);
      amqpConnection.addElement(replica);
      server_2.getConfiguration().addAMQPConnection(amqpConnection);

      int NUMBER_OF_MESSAGES = 20;

      server_2.start();
      Wait.assertTrue(server_2::isStarted);

      // We create the address to avoid auto delete on the queue
      server_2.addAddressInfo(new AddressInfo(getQueueName()).addRoutingType(RoutingType.ANYCAST).setAutoCreated(false));
      server_2.createQueue(QueueConfiguration.of(getQueueName()).setRoutingType(RoutingType.ANYCAST).setAddress(getQueueName()).setAutoCreated(false));

      assertFalse(loggerHandler.findText("AMQ222214"));

      // Get the Queue View early to avoid racing the delivery.
      final Queue queueView = locateQueue(server_2, getQueueName());
      final Queue queueViewReplica = locateQueue(server_2, getQueueName());

      { // sender
         AmqpClient client = new AmqpClient(new URI("tcp://localhost:" + AMQP_PORT_2), null, null);
         AmqpConnection connection = addConnection(client.connect());
         AmqpSession session = connection.createSession();

         AmqpSender sender = session.createSender(getQueueName());

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            AmqpMessage message = new AmqpMessage();
            message.setDeliveryAnnotation("gone", "test");
            message.setText(getText(true, i));
            sender.send(message);
         }
         sender.close();
         connection.close();
      }

      Wait.assertEquals(NUMBER_OF_MESSAGES, queueView::getMessageCount);
      Wait.assertEquals(NUMBER_OF_MESSAGES, queueViewReplica::getMessageCount);

      { // receiver on replica
         AmqpClient client = new AmqpClient(new URI("tcp://localhost:" + AMQP_PORT), null, null);
         AmqpConnection connection = addConnection(client.connect());
         AmqpSession session = connection.createSession();
         // Now try and get the message

         AmqpReceiver receiver = session.createReceiver(getQueueName());
         receiver.flow(NUMBER_OF_MESSAGES + 1);
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
            assertNotNull(received);
            assertEquals(getText(true, i), received.getText());
            assertNull(received.getDeliveryAnnotation("gone"));
         }
         assertNull(receiver.receiveNoWait());

         connection.close();
      }
   }

   /**
    * This is setting delivery annotations and sending messages with no address. The broker should know how to deal with
    * the annotations and no address on the message.
    */
   @Test
   public void testNoAddressWithAnnotations() throws Exception {
      server.setIdentity("targetServer");
      server.start();
      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("server_2");

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("test", "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(-1).setRetryInterval(100);
      AMQPMirrorBrokerConnectionElement replica = new AMQPMirrorBrokerConnectionElement().setMessageAcknowledgements(true);
      amqpConnection.addElement(replica);
      server_2.getConfiguration().addAMQPConnection(amqpConnection);

      int NUMBER_OF_MESSAGES = 20;

      server_2.start();
      Wait.assertTrue(server_2::isStarted);

      // We create the address to avoid auto delete on the queue
      server_2.addAddressInfo(new AddressInfo(getQueueName()).addRoutingType(RoutingType.ANYCAST).setAutoCreated(false));
      server_2.createQueue(QueueConfiguration.of(getQueueName()).setRoutingType(RoutingType.ANYCAST).setAddress(getQueueName()).setAutoCreated(false));

      assertFalse(loggerHandler.findText("AMQ222214"));

      { // sender
         AmqpClient client = new AmqpClient(new URI("tcp://localhost:" + AMQP_PORT_2), null, null);
         AmqpConnection connection = addConnection(client.connect());
         AmqpSession session = connection.createSession();

         AmqpSender sender = session.createSender(getQueueName());

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            AmqpMessage message = new AmqpMessage();
            message.setDeliveryAnnotation("gone", "test");
            message.setText(getText(false, i));
            sender.send(message);
         }
         sender.close();
         connection.close();
      }

      { // receiver on replica
         AmqpClient client = new AmqpClient(new URI("tcp://localhost:" + AMQP_PORT), null, null);
         AmqpConnection connection = addConnection(client.connect());
         AmqpSession session = connection.createSession();
         // Now try and get the message

         AmqpReceiver receiver = session.createReceiver(getQueueName());
         receiver.flow(NUMBER_OF_MESSAGES + 1);
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
            assertNotNull(received);
            assertEquals(getText(false, i), received.getText());
            assertNull(received.getDeliveryAnnotation("gone"));
         }
         assertNull(receiver.receiveNoWait());

         connection.close();
      }
   }

   @Test
   public void testAddressFilter() throws Exception {
      final String REPLICATED = "replicated";
      final String NON_REPLICATED = "nonReplicated";
      final String ADDRESS_FILTER = REPLICATED + "," + "!" + NON_REPLICATED;
      final String MSG = "msg";

      server.start();

      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("server_2");
      server_2.getConfiguration().setName("server_2");

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("mirror-source", "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(-1).setRetryInterval(100);
      AMQPMirrorBrokerConnectionElement replica = new AMQPMirrorBrokerConnectionElement().setDurable(true).setAddressFilter(ADDRESS_FILTER);
      amqpConnection.addElement(replica);
      server_2.getConfiguration().addAMQPConnection(amqpConnection);

      server_2.start();

      try (Connection connection = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT_2).createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         // Send to non replicated address
         try (MessageProducer producer = session.createProducer(session.createQueue(NON_REPLICATED))) {
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            for (int i = 0; i < 2; i++) {
               producer.send(session.createTextMessage("never receive"));
            }
         }

         // Check nothing was added to SnF queue
         assertEquals(0, server_2.locateQueue(replica.getMirrorSNF()).getMessagesAdded());

         // Send to replicated address
         try (MessageProducer producer = session.createProducer(session.createQueue(REPLICATED))) {
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            for (int i = 0; i < 2; i++) {
               producer.send(session.createTextMessage(MSG));
            }
         }

         // Check some messages were sent to SnF queue
         assertTrue(server_2.locateQueue(replica.getMirrorSNF()).getMessagesAdded() > 0);
      }

      SimpleManagement simpleManagement = new SimpleManagement("tcp://localhost:" + AMQP_PORT_2, null, null);
      Wait.assertEquals(0, () -> simpleManagement.getMessageCountOnQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirror-source"), 5000);

      try (Connection connection = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT).createConnection()) {
         connection.start();

         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         try (MessageConsumer consumer = session.createConsumer(session.createQueue(REPLICATED))) {
            Message message = consumer.receive(3000);
            assertNotNull(message);
            assertEquals(MSG, message.getBody(String.class));
         }

         try (MessageConsumer consumer = session.createConsumer(session.createQueue(NON_REPLICATED))) {
            assertNull(consumer.receiveNoWait());
         }
      }

   }

   @Test
   public void testRouteSurviving() throws Exception {
      testRouteSurvivor(false);
   }

   @Test
   public void testRouteSurvivingStop() throws Exception {
      testRouteSurvivor(true);
   }


   private void testRouteSurvivor(boolean server1Stopped) throws Exception {
      if (!server1Stopped) {
         server.start();
      }
      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("server_2");
      server_2.getConfiguration().setName("thisone");

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("OtherSide", "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(-1).setRetryInterval(100);
      AMQPMirrorBrokerConnectionElement replica = new AMQPMirrorBrokerConnectionElement().setDurable(true);
      amqpConnection.addElement(replica);
      server_2.getConfiguration().addAMQPConnection(amqpConnection);

      server_2.start();

      // We create the address to avoid auto delete on the queue
      server_2.addAddressInfo(new AddressInfo(getQueueName()).addRoutingType(RoutingType.ANYCAST).setAutoCreated(false));
      server_2.createQueue(QueueConfiguration.of(getQueueName()).setRoutingType(RoutingType.ANYCAST).setAddress(getQueueName()).setAutoCreated(false));

      int NUMBER_OF_MESSAGES = 200;

      ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT_2);
      Connection connection = factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(session.createQueue(getQueueName()));
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         producer.send(session.createTextMessage("i=" + i));
      }

      connection.close();

      {
         if (!server1Stopped) {
            Wait.assertTrue(() -> server.locateQueue(getQueueName()) != null);
            Queue queueServer1 = server.locateQueue(getQueueName());
            Wait.assertEquals(NUMBER_OF_MESSAGES, queueServer1::getMessageCount);
         }
         Wait.assertTrue(() -> server_2.locateQueue(getQueueName()) != null);
         Queue queueServer2 = server_2.locateQueue(getQueueName());
         Wait.assertEquals(NUMBER_OF_MESSAGES, queueServer2::getMessageCount);
      }

      if (!server1Stopped) {
         server.stop();
      }
      server_2.stop();

      server.start();
      server_2.start();


      Wait.assertTrue(() -> server.locateQueue(getQueueName()) != null);
      Wait.assertTrue(() -> server_2.locateQueue(getQueueName()) != null);
      Queue queueServer1 = server.locateQueue(getQueueName());
      Queue queueServer2 = server_2.locateQueue(getQueueName());

      Wait.assertEquals(NUMBER_OF_MESSAGES, queueServer1::getMessageCount);
      Wait.assertEquals(NUMBER_OF_MESSAGES, queueServer2::getMessageCount);
   }


   private void replicaTest(boolean largeMessage,
                            boolean acks,
                            boolean pagingTarget,
                            boolean pagingSource,
                            boolean deferredStart,
                            boolean restartAndDisconnect,
                            boolean restartBrokerConnection) throws Exception {

      String brokerConnectionName = "brokerConnectionName:" + UUIDGenerator.getInstance().generateStringUUID();
      server.setIdentity("targetServer");
      if (deferredStart) {
         server.stop();
      } else {
         server.start();
      }
      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("server_2");
      server_2.getConfiguration().setName("thisone");

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(brokerConnectionName, "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(-1).setRetryInterval(100);
      AMQPMirrorBrokerConnectionElement replica = new AMQPMirrorBrokerConnectionElement().setMessageAcknowledgements(acks).setDurable(true);
      replica.setName("theReplica");
      amqpConnection.addElement(replica);
      server_2.getConfiguration().addAMQPConnection(amqpConnection);
      server_2.getConfiguration().setName("server_2");

      int NUMBER_OF_MESSAGES = 200;

      server_2.start();
      Wait.assertTrue(server_2::isStarted);

      // We create the address to avoid auto delete on the queue
      server_2.addAddressInfo(new AddressInfo(getQueueName()).addRoutingType(RoutingType.ANYCAST).setAutoCreated(false));
      server_2.createQueue(QueueConfiguration.of(getQueueName()).setRoutingType(RoutingType.ANYCAST).setAddress(getQueueName()).setAutoCreated(false));

      ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT_2);
      Connection connection = factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(session.createQueue(getQueueName()));

      if (!deferredStart) {
         Queue queueOnServer1 = locateQueue(server, getQueueName());

         if (pagingTarget) {
            queueOnServer1.getPagingStore().startPaging();
         }
      }

      if (pagingSource) {
         Queue queueOnServer2 = server_2.locateQueue(getQueueName());
         queueOnServer2.getPagingStore().startPaging();
      }

      assertFalse(loggerHandler.findText("AMQ222214"));


      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         Message message = session.createTextMessage(getText(largeMessage, i));
         message.setIntProperty("i", i);
         producer.send(message);
      }

      assertFalse(loggerHandler.findText("AMQ222214"));

      Queue queueOnServer1;
      if (deferredStart) {
         Thread.sleep(TIME_BEFORE_RESTART);
         server.start();
         Wait.assertTrue(server::isActive);
         queueOnServer1 = locateQueue(server, getQueueName());
         if (pagingTarget) {
            queueOnServer1.getPagingStore().startPaging();
         }
      } else {
         queueOnServer1 = locateQueue(server, getQueueName());
      }
      Queue snfreplica = server_2.locateQueue(replica.getMirrorSNF());

      assertNotNull(snfreplica);

      Wait.assertEquals(0, snfreplica::getMessageCount);

      Wait.assertEquals(NUMBER_OF_MESSAGES, queueOnServer1::getMessageCount, 2000);
      Queue queueOnServer2 = locateQueue(server_2, getQueueName());
      Wait.assertEquals(NUMBER_OF_MESSAGES, queueOnServer1::getMessageCount);
      Wait.assertEquals(NUMBER_OF_MESSAGES, queueOnServer2::getMessageCount);

      if (restartBrokerConnection) {
         // stop and start the broker connection, making sure we wouldn't duplicate the mirror
         server_2.stopBrokerConnection(brokerConnectionName);
         Thread.sleep(1000);
         server_2.startBrokerConnection(brokerConnectionName);
      }

      assertSame(snfreplica, server_2.locateQueue(replica.getMirrorSNF()));

      if (pagingTarget) {
         assertTrue(queueOnServer1.getPagingStore().isPaging());
      }

      if (acks) {
         consumeMessages(largeMessage, 0, NUMBER_OF_MESSAGES / 2 - 1, AMQP_PORT_2, false);
         // Replica is async, so we need to wait acks to arrive before we finish consuming there
         Wait.assertEquals(0, snfreplica::getMessageCount);
         Wait.assertEquals(NUMBER_OF_MESSAGES / 2, queueOnServer1::getMessageCount);
         // we consume on replica, as half the messages were acked
         consumeMessages(largeMessage, NUMBER_OF_MESSAGES / 2, NUMBER_OF_MESSAGES - 1, AMQP_PORT, true); // We consume on both servers as this is currently replicated
         Wait.assertEquals(0, snfreplica::getMessageCount);
         consumeMessages(largeMessage, NUMBER_OF_MESSAGES / 2, NUMBER_OF_MESSAGES - 1, AMQP_PORT_2, false);
         Wait.assertEquals(0, snfreplica::getMessageCount);

         if (largeMessage) {
            validateNoFilesOnLargeDir(server.getConfiguration().getLargeMessagesDirectory(), 0);
         }
      } else {

         consumeMessages(largeMessage, 0, NUMBER_OF_MESSAGES - 1, AMQP_PORT_2, true);
         Wait.assertEquals(0, snfreplica::getMessageCount);
         consumeMessages(largeMessage, 0, NUMBER_OF_MESSAGES - 1, AMQP_PORT, true);
         Wait.assertEquals(0, snfreplica::getMessageCount);
         if (largeMessage) {
            validateNoFilesOnLargeDir(server.getConfiguration().getLargeMessagesDirectory(), 0);
            validateNoFilesOnLargeDir(server_2.getConfiguration().getLargeMessagesDirectory(), 0); // we kept half of the messages
         }
      }

      if (restartAndDisconnect) {
         server.stop();

         Thread.sleep(TIME_BEFORE_RESTART);

         server.start();
         Wait.assertTrue(server::isActive);

         consumeMessages(largeMessage, 0, -1, AMQP_PORT_2, true);
         consumeMessages(largeMessage, 0, -1, AMQP_PORT, true);
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            Message message = session.createTextMessage(getText(largeMessage, i));
            message.setIntProperty("i", i);
            producer.send(message);
         }

         consumeMessages(largeMessage, 0, NUMBER_OF_MESSAGES - 1, AMQP_PORT, true);
         consumeMessages(largeMessage, 0, NUMBER_OF_MESSAGES - 1, AMQP_PORT_2, true);
      }
   }

   @Test
   public void testDualStandardRestartBrokerConnection() throws Exception {
      dualReplica(false, false, false, true);
   }

   @Test
   public void testDualStandard() throws Exception {
      dualReplica(false, false, false, false);
   }

   @Test
   public void testDualRegularPagedTargets() throws Exception {
      dualReplica(false, false, true, false);
   }

   @Test
   public void testDualRegularPagedEverything() throws Exception {
      dualReplica(false, true, true, false);
   }

   @Test
   public void testDualRegularLarge() throws Exception {
      dualReplica(true, false, false, false);
   }

   public Queue locateQueue(ActiveMQServer server, String queueName) throws Exception {
      assertNotNull(queueName);
      assertNotNull(server);
      Wait.waitFor(() -> server.locateQueue(queueName) != null);
      return server.locateQueue(queueName);
   }

   private void dualReplica(boolean largeMessage, boolean pagingSource, boolean pagingTarget, boolean restartBC) throws Exception {
      server.setIdentity("server_1");
      server.start();

      ActiveMQServer server_3 = createServer(AMQP_PORT_3, false);
      server_3.setIdentity("server_3");
      server_3.start();
      Wait.assertTrue(server_3::isStarted);

      ConnectionFactory factory_3 = CFUtil.createConnectionFactory("amqp", "tcp://localhost:" + AMQP_PORT_3);
      factory_3.createConnection().close();

      server_2 = createServer(AMQP_PORT_2, false);

      String brokerConnectionOne = "brokerConnection1:" + UUIDGenerator.getInstance().generateStringUUID();
      String brokerConnectionTwo = "brokerConnection2:" + UUIDGenerator.getInstance().generateStringUUID();

      AMQPBrokerConnectConfiguration amqpConnection1 = new AMQPBrokerConnectConfiguration(brokerConnectionOne, "tcp://localhost:" + AMQP_PORT);
      AMQPMirrorBrokerConnectionElement replica1 = new AMQPMirrorBrokerConnectionElement().setType(AMQPBrokerConnectionAddressType.MIRROR);
      amqpConnection1.addElement(replica1);
      server_2.getConfiguration().addAMQPConnection(amqpConnection1);

      AMQPBrokerConnectConfiguration amqpConnection3 = new AMQPBrokerConnectConfiguration(brokerConnectionTwo, "tcp://localhost:" + AMQP_PORT_3);
      AMQPMirrorBrokerConnectionElement replica2 = new AMQPMirrorBrokerConnectionElement().setType(AMQPBrokerConnectionAddressType.MIRROR);
      amqpConnection3.addElement(replica2);
      server_2.getConfiguration().addAMQPConnection(amqpConnection3);

      int NUMBER_OF_MESSAGES = 200;

      server_2.start();
      Wait.assertTrue(server_2::isStarted);

      ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT_2);
      Connection connection = factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(session.createQueue(getQueueName()));
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);

      Queue queue_server_2 = locateQueue(server_2, getQueueName());
      Queue queue_server_1 = locateQueue(server, getQueueName());
      Queue queue_server_3 = locateQueue(server_3, getQueueName());

      if (pagingSource) {
         queue_server_2.getPagingStore().startPaging();
      }

      if (pagingTarget) {
         queue_server_1.getPagingStore().startPaging();
         queue_server_3.getPagingStore().startPaging();
      }

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         Message message = session.createTextMessage(getText(largeMessage, i));
         message.setIntProperty("i", i);
         producer.send(message);

         if (i == NUMBER_OF_MESSAGES / 2) {
            if (restartBC) {
               // half we restart it
               Wait.assertEquals(NUMBER_OF_MESSAGES / 2 + 1, queue_server_2::getMessageCount);
               Wait.assertEquals(NUMBER_OF_MESSAGES / 2 + 1, queue_server_3::getMessageCount);
               Wait.assertEquals(NUMBER_OF_MESSAGES / 2 + 1, queue_server_1::getMessageCount);
               server_2.stopBrokerConnection(brokerConnectionOne);
               server_2.stopBrokerConnection(brokerConnectionTwo);
               Thread.sleep(1000);
               server_2.startBrokerConnection(brokerConnectionOne);
               server_2.startBrokerConnection(brokerConnectionTwo);
            }
         }
      }

      Wait.assertEquals(NUMBER_OF_MESSAGES, queue_server_2::getMessageCount);
      Wait.assertEquals(NUMBER_OF_MESSAGES, queue_server_3::getMessageCount);
      Wait.assertEquals(NUMBER_OF_MESSAGES, queue_server_1::getMessageCount);

      Queue replica1Queue = server_2.locateQueue(replica1.getMirrorSNF());
      Queue replica2Queue = server_2.locateQueue(replica2.getMirrorSNF());

      Wait.assertEquals(0L, replica2Queue.getPagingStore()::getAddressSize, 1000, 100);
      Wait.assertEquals(0L, replica1Queue.getPagingStore()::getAddressSize, 1000, 100);

      if (pagingTarget) {
         assertTrue(queue_server_1.getPagingStore().isPaging());
         assertTrue(queue_server_3.getPagingStore().isPaging());
      }

      if (pagingSource) {
         assertTrue(queue_server_2.getPagingStore().isPaging());
      }

      consumeMessages(largeMessage, 0, NUMBER_OF_MESSAGES / 2 - 1, AMQP_PORT_2, false);

      Wait.assertEquals(NUMBER_OF_MESSAGES / 2, queue_server_1::getMessageCount);
      Wait.assertEquals(NUMBER_OF_MESSAGES / 2, queue_server_2::getMessageCount);
      Wait.assertEquals(NUMBER_OF_MESSAGES / 2, queue_server_3::getMessageCount);

      // Replica is async, so we need to wait acks to arrive before we finish consuming there
      Wait.assertEquals(NUMBER_OF_MESSAGES / 2, queue_server_1::getMessageCount);

      consumeMessages(largeMessage, NUMBER_OF_MESSAGES / 2, NUMBER_OF_MESSAGES - 1, AMQP_PORT, true); // We consume on both servers as this is currently replicated
      consumeMessages(largeMessage, NUMBER_OF_MESSAGES / 2, NUMBER_OF_MESSAGES - 1, AMQP_PORT_3, true); // We consume on both servers as this is currently replicated
      consumeMessages(largeMessage, NUMBER_OF_MESSAGES / 2, NUMBER_OF_MESSAGES - 1, AMQP_PORT_2, true); // We consume on both servers as this is currently replicated

      validateNoFilesOnLargeDir(server.getConfiguration().getLargeMessagesDirectory(), 0);
      validateNoFilesOnLargeDir(server_3.getConfiguration().getLargeMessagesDirectory(), 0);
      validateNoFilesOnLargeDir(server_2.getConfiguration().getLargeMessagesDirectory(), 0);

   }

   @Test
   public void testWithTXLargeMessage() throws Exception {
      testWithTX(true);
   }

   @Test
   public void testWithTX() throws Exception {
      testWithTX(false);
   }


   private void testWithTX(boolean largeMessage) throws Exception {

      server.setIdentity("server_1");
      server.start();

      ActiveMQServer server_3 = createServer(AMQP_PORT_3, false);
      server_3.setIdentity("server_3");
      server_3.start();
      Wait.assertTrue(server_3::isStarted);

      ConnectionFactory factory_3 = CFUtil.createConnectionFactory("amqp", "tcp://localhost:" + AMQP_PORT_3);
      factory_3.createConnection().close();

      server_2 = createServer(AMQP_PORT_2, false);

      String brokerConnectionOne = "brokerConnection1:" + UUIDGenerator.getInstance().generateStringUUID();
      String brokerConnectionTwo = "brokerConnection2:" + UUIDGenerator.getInstance().generateStringUUID();

      AMQPBrokerConnectConfiguration amqpConnection1 = new AMQPBrokerConnectConfiguration(brokerConnectionOne, "tcp://localhost:" + AMQP_PORT);
      AMQPMirrorBrokerConnectionElement replica1 = new AMQPMirrorBrokerConnectionElement().setType(AMQPBrokerConnectionAddressType.MIRROR);
      amqpConnection1.addElement(replica1);
      server_2.getConfiguration().addAMQPConnection(amqpConnection1);

      AMQPBrokerConnectConfiguration amqpConnection3 = new AMQPBrokerConnectConfiguration(brokerConnectionTwo, "tcp://localhost:" + AMQP_PORT_3);
      AMQPMirrorBrokerConnectionElement replica2 = new AMQPMirrorBrokerConnectionElement().setType(AMQPBrokerConnectionAddressType.MIRROR);
      amqpConnection3.addElement(replica2);
      server_2.getConfiguration().addAMQPConnection(amqpConnection3);

      int NUMBER_OF_MESSAGES = 5;

      server_2.start();
      Wait.assertTrue(server_2::isStarted);

      ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT_2);
      Connection connection = factory.createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      MessageProducer producer = session.createProducer(session.createQueue(getQueueName()));
      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

      Queue queue_server_2 = locateQueue(server_2, getQueueName());
      Queue queue_server_1 = locateQueue(server, getQueueName());
      Queue queue_server_3 = locateQueue(server_3, getQueueName());

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         Message message = session.createTextMessage(getText(largeMessage, i));
         message.setIntProperty("i", i);
         producer.send(message);
      }
      session.rollback();

      // Allowing a window in which message could be sent on the replica
      Thread.sleep(100);

      Wait.assertEquals(0, queue_server_2::getMessageCount);
      Wait.assertEquals(0, queue_server_3::getMessageCount);
      Wait.assertEquals(0, queue_server_1::getMessageCount);


      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         Message message = session.createTextMessage(getText(largeMessage, i));
         message.setIntProperty("i", i);
         producer.send(message);
      }
      session.commit();


      Wait.assertEquals(NUMBER_OF_MESSAGES, queue_server_2::getMessageCount);
      Wait.assertEquals(NUMBER_OF_MESSAGES, queue_server_3::getMessageCount);
      Wait.assertEquals(NUMBER_OF_MESSAGES, queue_server_1::getMessageCount);
   }

   /**
    * this might be helpful for debugging
    */
   private void printMessages(String printInfo, Queue queue) {
      System.out.println("*******************************************************************************************************************************");
      System.out.println(printInfo);
      System.out.println();
      LinkedListIterator<MessageReference> referencesIterator = queue.browserIterator();
      while (referencesIterator.hasNext()) {
         System.out.println("message " + referencesIterator.next().getMessage());
      }
      referencesIterator.close();
      System.out.println("*******************************************************************************************************************************");
   }

   private void consumeMessages(boolean largeMessage,
                                int START_ID,
                                int LAST_ID,
                                int port,
                                boolean assertNull) throws JMSException {
      ConnectionFactory cf = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + port);
      Connection conn = cf.createConnection();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      conn.start();

      Set<Integer> idsReceived = new HashSet<>();

      MessageConsumer consumer = sess.createConsumer(sess.createQueue(getQueueName()));
      for (int i = START_ID; i <= LAST_ID; i++) {
         Message message = consumer.receive(3000);
         assertNotNull(message);
         Integer id = message.getIntProperty("i");
         assertNotNull(id);
         assertTrue(idsReceived.add(id));
      }

      if (assertNull) {
         assertNull(consumer.receiveNoWait());
      }

      for (int i = START_ID; i <= LAST_ID; i++) {
         assertTrue(idsReceived.remove(i));
      }

      assertTrue(idsReceived.isEmpty());
      conn.close();
   }

   private void consumeSubscription(int START_ID,
                                int LAST_ID,
                                int port,
                                String clientID,
                                String queueName,
                                String subscriptionName,
                                boolean assertNull) throws JMSException {
      ConnectionFactory cf = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + port);
      Connection conn = cf.createConnection();
      conn.setClientID(clientID);
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      conn.start();

      Set<Integer> idsReceived = new HashSet<>();

      Topic topic = sess.createTopic(queueName);

      MessageConsumer consumer = sess.createDurableConsumer(topic, subscriptionName);
      for (int i = START_ID; i <= LAST_ID; i++) {
         Message message = consumer.receive(3000);
         assertNotNull(message);
         Integer id = message.getIntProperty("i");
         assertNotNull(id);
         assertTrue(idsReceived.add(id));
      }

      if (assertNull) {
         assertNull(consumer.receiveNoWait());
      }

      for (int i = START_ID; i <= LAST_ID; i++) {
         assertTrue(idsReceived.remove(i));
      }

      assertTrue(idsReceived.isEmpty());
      conn.close();
   }

   @Test
   public void testMulticast() throws Exception {
      multiCastReplicaTest(false, false, false, false, true);
   }


   @Test
   public void testMulticastSerializeConsumption() throws Exception {
      multiCastReplicaTest(false, false, false, false, false);
   }

   @Test
   public void testMulticastTargetPaging() throws Exception {
      multiCastReplicaTest(false, true, false, false, true);
   }

   @Test
   public void testMulticastTargetSourcePaging() throws Exception {
      multiCastReplicaTest(false, true, true, true, true);
   }

   @Test
   public void testMulticastTargetLargeMessage() throws Exception {
      multiCastReplicaTest(true, true, true, true, true);
   }


   private void multiCastReplicaTest(boolean largeMessage,
                            boolean pagingTarget,
                            boolean pagingSource,
                            boolean restartBrokerConnection, boolean multiThreadConsumers) throws Exception {

      String brokerConnectionName = "brokerConnectionName:" + UUIDGenerator.getInstance().generateStringUUID();
      final ActiveMQServer server = this.server;
      server.setIdentity("targetServer");

      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("server_2");
      server_2.getConfiguration().setName("thisone");

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(brokerConnectionName, "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(-1).setRetryInterval(100);
      AMQPMirrorBrokerConnectionElement replica = new AMQPMirrorBrokerConnectionElement().setMessageAcknowledgements(true).setDurable(true);
      replica.setName("theReplica");
      amqpConnection.addElement(replica);
      server_2.getConfiguration().addAMQPConnection(amqpConnection);
      server_2.getConfiguration().setName("server_2");

      int NUMBER_OF_MESSAGES = 200;

      server_2.start();
      server.start();
      Wait.assertTrue(server_2::isStarted);
      Wait.assertTrue(server::isStarted);

      // We create the address to avoid auto delete on the queue
      server_2.addAddressInfo(new AddressInfo(getTopicName()).addRoutingType(RoutingType.MULTICAST).setAutoCreated(false));


      ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT_2);
      Connection connection = factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Topic topic = session.createTopic(getTopicName());
      MessageProducer producer = session.createProducer(topic);

      for (int i = 0; i <= 1; i++) {
         // just creating the subscription and not consuming anything
         consumeSubscription(0, -1, AMQP_PORT_2, "client" + i, getTopicName(), "subscription" + i, false);
      }

      String subs0Name = "client0.subscription0";
      String subs1Name = "client1.subscription1";


      Queue subs0Server1 = locateQueue(server, subs0Name);
      Queue subs1Server1 = locateQueue(server, subs1Name);
      assertNotNull(subs0Server1);
      assertNotNull(subs1Server1);

      Queue subs0Server2 = locateQueue(server_2, subs0Name);
      Queue subs1Server2 = locateQueue(server_2, subs1Name);
      assertNotNull(subs0Server2);
      assertNotNull(subs1Server2);

      if (pagingTarget) {
         subs0Server1.getPagingStore().startPaging();
      }

      if (pagingSource) {
         subs0Server2.getPagingStore().startPaging();
      }

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         Message message = session.createTextMessage(getText(largeMessage, i));
         message.setIntProperty("i", i);
         producer.send(message);
      }

      if (pagingTarget) {
         subs0Server1.getPagingStore().startPaging();
      }

      Queue snfreplica = server_2.locateQueue(replica.getMirrorSNF());

      assertNotNull(snfreplica);

      Wait.assertEquals(0, snfreplica::getMessageCount);

      Wait.assertEquals(NUMBER_OF_MESSAGES, subs0Server1::getMessageCount, 2000);
      Wait.assertEquals(NUMBER_OF_MESSAGES, subs1Server1::getMessageCount, 2000);

      Wait.assertEquals(NUMBER_OF_MESSAGES, subs0Server2::getMessageCount, 2000);
      Wait.assertEquals(NUMBER_OF_MESSAGES, subs1Server2::getMessageCount, 2000);

      if (restartBrokerConnection) {
         // stop and start the broker connection, making sure we wouldn't duplicate the mirror
         server_2.stopBrokerConnection(brokerConnectionName);
         Thread.sleep(1000);
         server_2.startBrokerConnection(brokerConnectionName);
      }

      assertSame(snfreplica, server_2.locateQueue(replica.getMirrorSNF()));

      if (pagingTarget) {
         assertTrue(subs0Server1.getPagingStore().isPaging());
         assertTrue(subs1Server1.getPagingStore().isPaging());
      }

      ExecutorService executorService = Executors.newFixedThreadPool(2);
      runAfter(executorService::shutdownNow);

      CountDownLatch done = new CountDownLatch(2);
      AtomicInteger errors = new AtomicInteger(0);

      for (int i = 0; i <= 1; i++) {
         CountDownLatch threadDone = new CountDownLatch(1);
         int subscriptionID = i;
         executorService.execute(() -> {
            try {
               consumeSubscription(0, NUMBER_OF_MESSAGES - 1, AMQP_PORT_2, "client" + subscriptionID, getTopicName(), "subscription" + subscriptionID, false);
            } catch (Throwable e) {
               logger.warn(e.getMessage(), e);
               errors.incrementAndGet();
            } finally {
               done.countDown();
               threadDone.countDown();
            }
         });
         if (!multiThreadConsumers) {
            threadDone.await(1, TimeUnit.MINUTES);
         }
      }

      assertTrue(done.await(60, TimeUnit.SECONDS));
      assertEquals(0, errors.get());

      // Replica is async, so we need to wait acks to arrive before we finish consuming there
      Wait.assertEquals(0, snfreplica::getMessageCount);
      Wait.assertEquals(0L, subs0Server1::getMessageCount, 2000, 100);
      Wait.assertEquals(0L, subs1Server1::getMessageCount, 2000, 100);
      Wait.assertEquals(0L, subs0Server2::getMessageCount, 2000, 100);
      Wait.assertEquals(0L, subs1Server2::getMessageCount, 2000, 100);


      if (largeMessage) {
         validateNoFilesOnLargeDir(server.getConfiguration().getLargeMessagesDirectory(), 0);
         validateNoFilesOnLargeDir(server_2.getConfiguration().getLargeMessagesDirectory(), 0);
      }
   }


   @Test
   public void testSimpleReplicaTX() throws Exception {

      String brokerConnectionName = "brokerConnectionName:" + UUIDGenerator.getInstance().generateStringUUID();
      server.setIdentity("targetServer");
      server.start();
      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("server_2");
      server_2.getConfiguration().setName("thisone");

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(brokerConnectionName, "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(-1).setRetryInterval(100);
      AMQPMirrorBrokerConnectionElement replica = new AMQPMirrorBrokerConnectionElement().setMessageAcknowledgements(true).setDurable(true);
      replica.setName("theReplica");
      amqpConnection.addElement(replica);
      server_2.getConfiguration().addAMQPConnection(amqpConnection);
      server_2.getConfiguration().setName("server_2");

      int NUMBER_OF_MESSAGES = 10;

      server_2.start();
      Wait.assertTrue(server_2::isStarted);

      // We create the address to avoid auto delete on the queue
      server_2.addAddressInfo(new AddressInfo(getQueueName()).addRoutingType(RoutingType.ANYCAST).setAutoCreated(false));
      server_2.createQueue(QueueConfiguration.of(getQueueName()).setRoutingType(RoutingType.ANYCAST).setAddress(getQueueName()).setAutoCreated(false));

      ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT_2);
      Connection connection = factory.createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      MessageProducer producer = session.createProducer(session.createQueue(getQueueName()));

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         Message message = session.createTextMessage(getText(true, i));
         message.setIntProperty("i", i);
         producer.send(message);
      }
      session.commit();

      Queue queueOnServer1 = locateQueue(server, getQueueName());
      Queue snfreplica = server_2.locateQueue(replica.getMirrorSNF());
      assertNotNull(snfreplica);

      Wait.assertEquals(0, snfreplica::getMessageCount);

      Wait.assertEquals(NUMBER_OF_MESSAGES, queueOnServer1::getMessageCount, 2000);
      Queue queueOnServer2 = locateQueue(server_2, getQueueName());
      Wait.assertEquals(NUMBER_OF_MESSAGES, queueOnServer1::getMessageCount);
      Wait.assertEquals(NUMBER_OF_MESSAGES, queueOnServer2::getMessageCount);

      MessageConsumer consumer = session.createConsumer(session.createQueue(getQueueName()));
      connection.start();

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         Message m = consumer.receive(1000);
         assertNotNull(m);
      }
      session.commit();

      Wait.assertEquals(0, snfreplica::getMessageCount);
      Wait.assertEquals(0, queueOnServer1::getMessageCount);
      Wait.assertEquals(0, queueOnServer2::getMessageCount);
   }


   @Test
   public void testCalculationSize() throws Exception {
      testCalculationSize(false);
   }

   @Test
   public void testCalculationSizeRestartSource() throws Exception {
      testCalculationSize(true);
   }

   private void testCalculationSize(boolean restartSource) throws Exception {
      String brokerConnectionName = "brokerConnectionName:" + UUIDGenerator.getInstance().generateStringUUID();
      server.setIdentity("targetServer");
      server.start();

      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("server_2");
      server_2.getConfiguration().setName("server2");

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(brokerConnectionName, "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(-1).setRetryInterval(100);
      AMQPMirrorBrokerConnectionElement replica = new AMQPMirrorBrokerConnectionElement().setMessageAcknowledgements(true).setDurable(true);
      replica.setName("theReplica");
      amqpConnection.addElement(replica);
      server_2.getConfiguration().addAMQPConnection(amqpConnection);
      server_2.getConfiguration().setName("server_2");

      int NUMBER_OF_MESSAGES = 1;

      server_2.start();
      Wait.assertTrue(server_2::isStarted);

      // We create the address to avoid auto delete on the queue
      server_2.addAddressInfo(new AddressInfo(getQueueName()).addRoutingType(RoutingType.ANYCAST).setAutoCreated(false));
      server_2.createQueue(QueueConfiguration.of(getQueueName()).setRoutingType(RoutingType.ANYCAST).setAddress(getQueueName()).setAutoCreated(false));

      ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT_2);
      Connection connection = factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(session.createQueue(getQueueName()));

      Queue queueOnServer2 = locateQueue(server_2, getQueueName());

      server.stop();

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         Message message = session.createTextMessage(getText(false, i));
         message.setIntProperty("i", i);
         producer.send(message);
      }

      if (restartSource) {
         server_2.stop();
         // Have to disable queueCreation, otherwise once the server restarts more data will be added to the SNF queue what will messup with the Assertions.
         // The idea is to make sure the reference counting between two addresses will act correctly
         replica.setQueueCreation(false).setQueueRemoval(false);
         server_2.start();
      }

      Queue snfreplica = server_2.locateQueue(replica.getMirrorSNF());
      assertNotNull(snfreplica);

      logger.info("Size on queueOnServer2:: {}", queueOnServer2.getPagingStore().getAddressSize());
      logger.info("Size on SNF:: {}", snfreplica.getPagingStore().getAddressSize());

      Wait.assertTrue(() -> queueOnServer2.getPagingStore().getAddressSize() == snfreplica.getPagingStore().getAddressSize(), 5000);
      Wait.assertTrue(() -> queueOnServer2.getPagingStore().getAddressElements() == snfreplica.getPagingStore().getAddressElements(), 5000);

      logger.info("Size on queueOnServer2:: {}, elements={}", queueOnServer2.getPagingStore().getAddressSize(), queueOnServer2.getPagingStore().getAddressElements());
      logger.info("Size on SNF:: {}, elements={}", snfreplica.getPagingStore().getAddressSize(), snfreplica.getPagingStore().getAddressElements());

      server.start();
      Wait.assertTrue(server::isStarted);

      Queue queueOnServer1 = locateQueue(server, getQueueName());
      assertFalse(loggerHandler.findText("AMQ222214"));

      Wait.assertEquals(0L, snfreplica.getPagingStore()::getAddressElements);
      Wait.assertEquals(0L, snfreplica.getPagingStore()::getAddressSize);

      Wait.assertEquals(NUMBER_OF_MESSAGES, queueOnServer1::getMessageCount, 2000);
      Wait.assertEquals(NUMBER_OF_MESSAGES, queueOnServer1::getMessageCount);
      Wait.assertEquals(NUMBER_OF_MESSAGES, queueOnServer2::getMessageCount);

      assertSame(snfreplica, server_2.locateQueue(replica.getMirrorSNF()));
   }

   @Test
   public void topicHierarchy() throws Exception {

      String brokerConnectionName = "brokerConnectionName:" + UUIDGenerator.getInstance().generateStringUUID();

      server.setIdentity("sourceServer");

      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("targetServer");
      server_2.getConfiguration().setName("server2");

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(brokerConnectionName, "tcp://localhost:" + AMQP_PORT_2).setReconnectAttempts(-1).setRetryInterval(100);
      AMQPMirrorBrokerConnectionElement replica = new AMQPMirrorBrokerConnectionElement().setMessageAcknowledgements(true).setDurable(true);
      replica.setName("theReplica");
      amqpConnection.addElement(replica);
      server.getConfiguration().addAMQPConnection(amqpConnection);

      server_2.start();

      server.start();
      Connection connection = null;
      try {
         ConnectionFactory cf = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
         connection = cf.createConnection();
         connection.setClientID("myID");

         SimpleString rootName = SimpleString.of("root.#");
         SimpleString topicAName = SimpleString.of("root.A");

         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Topic rootTopic = session.createTopic(rootName.toString());

         MessageConsumer messageConsumer = session.createDurableConsumer(rootTopic, "iAmDurable");

         MessageProducer producer = session.createProducer(null);
         TextMessage messageToA = session.createTextMessage("messageToA");

         Topic topicA = session.createTopic(topicAName.toString());

         producer.send(topicA, messageToA);
         validSizeStore(1, topicAName);
         validSizeStore(0, rootName);

         connection.start();
         TextMessage messageReceived1 = (TextMessage) messageConsumer.receive(5000);
         assertNotNull(messageReceived1);
         assertNull(messageConsumer.receiveNoWait());

         validSizeStore(0, topicAName);
         validSizeStore(0, rootName);

      } finally {
         // Step 12. Be sure to close our resources!
         if (connection != null) {
            connection.close();
         }
      }
   }

   void validSizeStore(int expectedMessages, SimpleString name) throws Exception {
      {
         PagingStoreImpl store = (PagingStoreImpl) server_2.getPagingManager().getPageStore(name);
         assertNotNull(store);
         Wait.assertEquals((long)expectedMessages, () -> store.getAddressElements(), 5000, 100);
      }

      {
         PagingStoreImpl store = (PagingStoreImpl) server.getPagingManager().getPageStore(name);
         assertNotNull(store);
         Wait.assertEquals((long)expectedMessages, () -> store.getAddressElements(), 5000, 100);
      }
   }
}