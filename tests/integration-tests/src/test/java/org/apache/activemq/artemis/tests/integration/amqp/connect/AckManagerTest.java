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
package org.apache.activemq.artemis.tests.integration.amqp.connect;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.util.collection.LongObjectHashMap;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.journal.collections.JournalHashMap;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.AckRetry;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.protocol.amqp.broker.ActiveMQProtonRemotingConnection;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerTarget;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AckManager;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AckManagerProvider;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.ReferenceIDSupplier;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonAbstractReceiver;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.amqp.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.INTERNAL_DESTINATION;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.INTERNAL_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AckManagerTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   ActiveMQServer server1;

   private static final String SNF_NAME = "$ACTIVEMQ_ARTEMIS_MIRROR_other";

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      server1 = createServer(true, createDefaultConfig(0, true), 100024, -1, -1, -1);
      server1.getConfiguration().addAddressSetting(SNF_NAME, new AddressSettings().setMaxSizeBytes(-1).setMaxSizeMessages(-1).setMaxReadPageMessages(20).setMaxReadPageMessages(-1));
      server1.getConfiguration().addAddressSetting("#", new AddressSettings().setMaxSizeBytes(-1).setMaxSizeMessages(-1).setMaxReadPageMessages(-1).setMaxReadPageMessages(-1));
      server1.getConfiguration().getAcceptorConfigurations().clear();
      server1.getConfiguration().addAcceptorConfiguration("server", "tcp://localhost:61616");
   }

   @Test
   public void testDirectACK() throws Throwable {
      server1.start();

      String protocol = "AMQP";

      SimpleString TOPIC_NAME = SimpleString.of("tp" + RandomUtil.randomUUIDString());

      server1.addAddressInfo(new AddressInfo(TOPIC_NAME).addRoutingType(RoutingType.MULTICAST));

      ConnectionFactory connectionFactory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      // creating 5 subscriptions
      for (int i = 0; i < 5; i++) {
         try (Connection connection = connectionFactory.createConnection()) {
            connection.setClientID("c" + i);
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(TOPIC_NAME.toString());
            session.createDurableSubscriber(topic, "s" + i);
         }
      }

      int numberOfMessages = 500;
      int numberOfAcksC1 = 100;
      int numberOfAcksC2 = 200;

      Queue c1s1 = server1.locateQueue("c1.s1");
      assertNotNull(c1s1);
      Queue c2s2 = server1.locateQueue("c2.s2");
      assertNotNull(c2s2);

      PagingStore store = server1.getPagingManager().getPageStore(TOPIC_NAME);
      store.startPaging();

      try (Connection connection = connectionFactory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Topic topic = session.createTopic(TOPIC_NAME.toString());
         MessageProducer producer = session.createProducer(topic);

         for (int i = 0; i < numberOfMessages; i++) {
            Message m = session.createTextMessage("hello " + i);
            m.setIntProperty("i", i);
            producer.send(m);
            if ((i + 1) % 100 == 0) {
               c1s1.pause();
               c2s2.pause();
               session.commit();
            }
         }
         session.commit();
      }

      ReferenceIDSupplier referenceIDSupplier = new ReferenceIDSupplier(server1);

      {
         AckManager ackManager = AckManagerProvider.getManager(server1);

         AtomicInteger counter = new AtomicInteger(0);

         for (long pageID = store.getFirstPage(); pageID <= store.getCurrentWritingPage(); pageID++) {
            Page page = store.usePage(pageID);
            try {
               page.getMessages().forEach(pagedMessage -> {
                  int increment = counter.incrementAndGet();
                  if (increment <= numberOfAcksC1) {
                     ackManager.addRetry(referenceIDSupplier.getServerID(pagedMessage.getMessage()), c1s1, referenceIDSupplier.getID(pagedMessage.getMessage()), AckReason.NORMAL);
                  }
                  if (increment <= numberOfAcksC2) {
                     ackManager.addRetry(referenceIDSupplier.getServerID(pagedMessage.getMessage()), c2s2, referenceIDSupplier.getID(pagedMessage.getMessage()), AckReason.NORMAL);
                  }
               });
            } finally {
               page.usageDown();
            }
         }
      }

      // in this following loop we will get the ackManager, compare the stored retries. stop the server and validate if they were reloaded correctly
      for (int repeat = 0; repeat < 2; repeat++) {
         logger.info("Repeating {}", repeat);
         AckManager ackManager = AckManagerProvider.getManager(server1);

         Map<SimpleString, LongObjectHashMap<JournalHashMap<AckRetry, AckRetry, Queue>>> sortedRetries = ackManager.sortRetries();

         assertEquals(1, sortedRetries.size());

         LongObjectHashMap<JournalHashMap<AckRetry, AckRetry, Queue>> acksOnAddress = sortedRetries.get(c1s1.getAddress());

         assertEquals(2, acksOnAddress.size());

         JournalHashMap<AckRetry, AckRetry, Queue> acksOnc1s1 = acksOnAddress.get(c1s1.getID());
         JournalHashMap<AckRetry, AckRetry, Queue> acksOnc2s2 = acksOnAddress.get(c2s2.getID());
         assertEquals(numberOfAcksC1, acksOnc1s1.size());
         assertEquals(numberOfAcksC2, acksOnc2s2.size());

         ackManager.start();
         Wait.assertEquals(numberOfMessages, c1s1::getMessageCount);
         Wait.assertEquals(numberOfMessages, c2s2::getMessageCount);

         AckManager originalManager = AckManagerProvider.getManager(server1);
         server1.stop();
         assertEquals(0, AckManagerProvider.getSize());
         server1.start();
         AckManager newManager = AckManagerProvider.getManager(server1);
         assertEquals(1, AckManagerProvider.getSize());
         assertNotSame(originalManager, AckManagerProvider.getManager(server1));
         AckManager manager = AckManagerProvider.getManager(server1);
         Wait.assertTrue(manager::isStarted, 5_000);

         assertEquals(1, AckManagerProvider.getSize());
         assertNotSame(newManager, ackManager);
      }

      AckManager ackManager = AckManagerProvider.getManager(server1);
      ackManager.start();
      Map<SimpleString, LongObjectHashMap<JournalHashMap<AckRetry, AckRetry, Queue>>> sortedRetries = ackManager.sortRetries();
      assertEquals(1, sortedRetries.size());
      LongObjectHashMap<JournalHashMap<AckRetry, AckRetry, Queue>> acksOnAddress = sortedRetries.get(c1s1.getAddress());
      JournalHashMap<AckRetry, AckRetry, Queue> acksOnc1s1 = acksOnAddress.get(c1s1.getID());
      JournalHashMap<AckRetry, AckRetry, Queue> acksOnc2s2 = acksOnAddress.get(c2s2.getID());

      Wait.assertEquals(0, () -> acksOnc1s1.size());
      Wait.assertEquals(0, () -> acksOnc2s2.size());

      for (int i = 0; i < 5; i++) {
         AtomicInteger counter = new AtomicInteger(0);

         for (long pageID = store.getFirstPage(); pageID <= store.getCurrentWritingPage(); pageID++) {
            Page page = store.usePage(pageID);
            try {
               page.getMessages().forEach(pagedMessage -> {
                  int increment = counter.incrementAndGet();
                  if (increment <= numberOfAcksC1) {
                     ackManager.addRetry(referenceIDSupplier.getServerID(pagedMessage.getMessage()), c1s1, referenceIDSupplier.getID(pagedMessage.getMessage()), AckReason.NORMAL);
                  }
                  if (increment <= numberOfAcksC2) {
                     ackManager.addRetry(referenceIDSupplier.getServerID(pagedMessage.getMessage()), c2s2, referenceIDSupplier.getID(pagedMessage.getMessage()), AckReason.NORMAL);
                  }
               });
            } finally {
               page.usageDown();
            }
         }
         Wait.assertEquals(0, () -> acksOnc1s1.size());
         Wait.assertEquals(0, () -> acksOnc2s2.size());
      }

      c1s1.resume();
      c2s2.resume();

      // creating 5 subscriptions
      for (int i = 0; i < 5; i++) {
         try (Connection connection = connectionFactory.createConnection()) {
            connection.setClientID("c" + i);
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(TOPIC_NAME.toString());
            TopicSubscriber subscriber = session.createDurableSubscriber(topic, "s" + i);

            int start = switch (i) {
               case 1 -> numberOfAcksC1;
               case 2 -> numberOfAcksC2;
               default -> 0;
            };

            logger.debug("receiving messages for {}", i);

            for (int m = start; m < numberOfMessages; m++) {
               logger.debug("Receiving i={}, m={}", i, m);
               TextMessage message = (TextMessage) subscriber.receive(5000);
               assertNotNull(message);
               assertEquals("hello " + m, message.getText());
               assertEquals(m, message.getIntProperty("i"));
            }
            assertNull(subscriber.receiveNoWait());

         }
      }

      server1.getStorageManager().getMessageJournal().scheduleCompactAndBlock(10_000);
      assertEquals(0, getCounter(JournalRecordIds.ACK_RETRY, countJournal(server1.getConfiguration())));

      assertEquals(1, AckManagerProvider.getSize());

      // the server was restarted at least once, locating it again
      Queue c1s1AfterRestart = server1.locateQueue("c1.s1");
      assertNotNull(c1s1AfterRestart);

      ackManager.addRetry(referenceIDSupplier.getDefaultNodeID(), c1s1, 10_000_000L, AckReason.NORMAL);
      ackManager.addRetry(referenceIDSupplier.getDefaultNodeID(), c1s1, 10_000_001L, AckReason.NORMAL);

      Wait.assertTrue(() -> ackManager.sortRetries().isEmpty(), 5000);

      server1.getStorageManager().getMessageJournal().scheduleCompactAndBlock(10_000);
      assertEquals(0, getCounter(JournalRecordIds.ACK_RETRY, countJournal(server1.getConfiguration())));

      server1.stop();

      assertEquals(0, AckManagerProvider.getSize());
   }

   @Test
   public void testLogUnack() throws Throwable {
      server1.start();
      String protocol = "AMQP";

      SimpleString TOPIC_NAME = SimpleString.of("tp" + RandomUtil.randomUUIDString());

      server1.addAddressInfo(new AddressInfo(TOPIC_NAME).addRoutingType(RoutingType.MULTICAST));

      ConnectionFactory connectionFactory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      // creating 5 subscriptions
      for (int i = 0; i < 5; i++) {
         try (Connection connection = connectionFactory.createConnection()) {
            connection.setClientID("c" + i);
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(TOPIC_NAME.toString());
            session.createDurableSubscriber(topic, "s" + i);
         }
      }

      Queue c1s1 = server1.locateQueue("c1.s1");
      assertNotNull(c1s1);
      Queue c2s2 = server1.locateQueue("c2.s2");
      assertNotNull(c2s2);

      try (AssertionLoggerHandler assertionLoggerHandler = new AssertionLoggerHandler()) {
         server1.getConfiguration().setMirrorAckManagerWarnUnacked(true);
         c1s1.addConsumer(Mockito.mock(Consumer.class));
         AckManager ackManager = AckManagerProvider.getManager(server1);
         ackManager.ack("neverFound", c1s1, 1000, AckReason.NORMAL, true);

         // ID for there are consumers
         Wait.assertTrue(() -> assertionLoggerHandler.findText("AMQ111011"), 5000, 100);
         // ID for give up retry
         Wait.assertTrue(() -> assertionLoggerHandler.findText("AMQ111012"), 5000, 100);

         server1.getConfiguration().setMirrorAckManagerWarnUnacked(false);
         assertionLoggerHandler.clear();

         ackManager.ack("neverFound", c1s1, 1000, AckReason.NORMAL, true);

         // ID for there are consumers
         assertFalse(assertionLoggerHandler.findText("AMQ111011"));
         // ID for give up retry
         assertFalse(assertionLoggerHandler.findText("AMQ111012"));
      }
   }

   @Test
   public void testRetryFromPaging() throws Throwable {
      server1.start();

      String protocol = "AMQP";

      SimpleString TOPIC_NAME = SimpleString.of("tp" + RandomUtil.randomUUIDString());

      server1.addAddressInfo(new AddressInfo(TOPIC_NAME).addRoutingType(RoutingType.MULTICAST));

      ConnectionFactory connectionFactory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      // creating 5 subscriptions
      for (int i = 0; i < 2; i++) {
         try (Connection connection = connectionFactory.createConnection()) {
            connection.setClientID("c" + i);
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(TOPIC_NAME.toString());
            session.createDurableSubscriber(topic, "s" + i);
         }
      }

      int numberOfMessages = 15000;
      int numberOfAcksC0 = 100;
      int numberOfAcksC1 = 14999;

      String c0s0Name = "c0.s0";
      String c1s1Name = "c1.s1";

      final Queue c0s0 = server1.locateQueue(c0s0Name);
      assertNotNull(c0s0);
      final Queue c1s1 = server1.locateQueue(c1s1Name);
      assertNotNull(c1s1);

      PagingStore store = server1.getPagingManager().getPageStore(TOPIC_NAME);
      store.startPaging();

      try (Connection connection = connectionFactory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Topic topic = session.createTopic(TOPIC_NAME.toString());
         MessageProducer producer = session.createProducer(topic);

         for (int i = 0; i < numberOfMessages; i++) {
            Message m = session.createTextMessage("hello " + i);
            m.setIntProperty("i", i);
            producer.send(m);
            if ((i + 1) % 100 == 0) {
               c1s1.pause();
               c0s0.pause();
               session.commit();
            }
         }
         session.commit();
      }

      ReferenceIDSupplier referenceIDSupplier = new ReferenceIDSupplier(server1);

      {
         AckManager ackManager = AckManagerProvider.getManager(server1);
         ackManager.stop();

         AtomicInteger counter = new AtomicInteger(0);

         for (long pageID = store.getFirstPage(); pageID <= store.getCurrentWritingPage(); pageID++) {
            Page page = store.usePage(pageID);
            try {
               page.getMessages().forEach(pagedMessage -> {
                  int increment = counter.incrementAndGet();
                  if (increment <= numberOfAcksC0) {
                     ackManager.addRetry(referenceIDSupplier.getServerID(pagedMessage.getMessage()), c0s0, referenceIDSupplier.getID(pagedMessage.getMessage()), AckReason.NORMAL);
                  }
                  if (increment <= numberOfAcksC1) {
                     ackManager.addRetry(referenceIDSupplier.getServerID(pagedMessage.getMessage()), c1s1, referenceIDSupplier.getID(pagedMessage.getMessage()), AckReason.NORMAL);
                  }
               });
            } finally {
               page.usageDown();
            }
         }
      }

      server1.stop();

      server1.start();


      Queue c0s0AfterRestart = server1.locateQueue(c0s0Name);
      assertNotNull(c0s0AfterRestart);
      Queue c1s1AfterRestart = server1.locateQueue(c1s1Name);
      assertNotNull(c1s1AfterRestart);

      Wait.assertEquals(numberOfMessages - numberOfAcksC1, c1s1AfterRestart::getMessageCount, 10_000);
      Wait.assertEquals(numberOfAcksC1, c1s1AfterRestart::getMessagesAcknowledged, 10_000);
      Wait.assertEquals(numberOfMessages - numberOfAcksC0, c0s0AfterRestart::getMessageCount, 10_000);
      Wait.assertEquals(numberOfAcksC0, c0s0AfterRestart::getMessagesAcknowledged, 10_000);

      server1.stop();

      assertEquals(0, AckManagerProvider.getSize());
   }



   @Test
   public void testFlowControlOnPendingAcks() throws Throwable {

      server1.getConfiguration().getAcceptorConfigurations().clear();
      server1.getConfiguration().addAcceptorConfiguration("server", "tcp://localhost:61616?mirrorMaxPendingAcks=100&amqpCredits=100");
      server1.start();

      String protocol = "AMQP";

      SimpleString QUEUE_NAME = SimpleString.of("queue_" + RandomUtil.randomUUIDString());

      Queue testQueue = server1.createQueue(QueueConfiguration.of(QUEUE_NAME).setRoutingType(RoutingType.ANYCAST));

      ConnectionFactory connectionFactory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      // First step... adding messages to a queue // 50% paging 50% queue
      try (Connection connection = connectionFactory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         javax.jms.Queue jmsQueue = session.createQueue(QUEUE_NAME.toString());
         MessageProducer producer = session.createProducer(jmsQueue);
         for (int i = 0; i < 100; i++) {
            if (i == 50) {
               session.commit();
               testQueue.getPagingStore().startPaging();
            }
            producer.send(session.createTextMessage("hello there " + i));
         }
         session.commit();
      }

      Wait.assertEquals(100, testQueue::getMessageCount);

      AckManager ackManager = AckManagerProvider.getManager(server1);
      assertTrue(ackManager.isStarted());
      ackManager.pause();
      assertFalse(ackManager.isStarted());
      assertSame(ackManager, AckManagerProvider.getManager(server1));

      // adding fake retries to flood the manager beyond capacity
      addFakeRetries(ackManager, testQueue, 1000);

      AmqpClient client = new AmqpClient(new URI("tcp://localhost:61616"), null, null);
      AmqpConnection connection = client.connect();
      runAfter(connection::close);
      AmqpSession session = connection.createSession();

      Map<Symbol, Object> properties = new HashMap<>();
      properties.put(AMQPMirrorControllerSource.BROKER_ID, "whatever");

      // this is simulating a mirror connection...
      // we play with a direct sender here to make sure flow control is working as expected when the records are beyond capacity.
      AmqpSender sender = session.createSender(QueueConfiguration.MIRROR_ADDRESS, true, new Symbol[]{Symbol.getSymbol("amq.mirror")}, new Symbol[]{Symbol.getSymbol("amq.mirror")}, properties);

      AMQPMirrorControllerTarget mirrorControllerTarget = Wait.assertNotNull(() -> locateMirrorTarget(server1), 5000, 100);
      assertEquals(100, mirrorControllerTarget.getConnection().getProtocolManager().getMirrorMaxPendingAcks());
      assertTrue(mirrorControllerTarget.isBusy());
      // first connection it should be beyond flow control capacity, we should not have any credits here now
      assertEquals(0, sender.getEndpoint().getCredit());
      ackManager.start();

      // manager resumed and the records will be eventually removed, we should be back to capacity
      Wait.assertEquals(100, () -> sender.getEndpoint().getCredit(), 5000, 100);
      ackManager.pause();

      addFakeRetries(ackManager, testQueue, 1000);
      assertEquals(1000, ackManager.size());

      // we should be able to send 100 messages
      for (int i = 0; i < 100; i++) {
         AmqpMessage message = new AmqpMessage();
         message.setAddress(testQueue.getAddress().toString());
         message.setText("hello again " + i);
         message.setDeliveryAnnotation(INTERNAL_ID.toString(), server1.getStorageManager().generateID());
         message.setDeliveryAnnotation(INTERNAL_DESTINATION.toString(), testQueue.getAddress().toString());
         sender.send(message);
      }
      // we should not get any credits
      assertEquals(0, sender.getEndpoint().getCredit());

      Wait.assertEquals(200, testQueue::getMessageCount);

      ackManager.start();
      // after restart, we should eventually get replenished on credits
      Wait.assertEquals(100, () -> sender.getEndpoint().getCredit(), 5000, 100);

      Wait.assertEquals(200L, testQueue::getMessageCount, 5000, 100);

      AtomicInteger acked = new AtomicInteger(0);
      ackManager.pause();

      // Adding real deletes, we should still flow control credits
      testQueue.forEach(ref -> {
         long messageID = ref.getMessageID();

         Long internalID = (Long) ref.getMessage().getAnnotation(AMQPMirrorControllerSource.INTERNAL_ID_EXTRA_PROPERTY);
         String nodeId = (String) ref.getMessage().getAnnotation(AMQPMirrorControllerSource.BROKER_ID_SIMPLE_STRING);
         if (internalID != null) {
            messageID = internalID.longValue();
         }
         ackManager.addRetry(nodeId, testQueue, messageID, AckReason.NORMAL);
         acked.incrementAndGet();
      });

      assertEquals(200, acked.get());
      ackManager.start();

      // Adding hot data... we should be able to flow credits during that
      for (int i = 0; i < 100; i++) {
         AmqpMessage message = new AmqpMessage();
         message.setAddress(testQueue.getAddress().toString());
         message.setText("one of the last 100");
         message.setDeliveryAnnotation(INTERNAL_ID.toString(), server1.getStorageManager().generateID());
         message.setDeliveryAnnotation(INTERNAL_DESTINATION.toString(), testQueue.getAddress().toString());
         sender.send(message);
      }
      Wait.assertTrue(() -> sender.getEndpoint().getCredit() > 0, 5000, 100);

      Wait.assertEquals(100L, testQueue::getMessageCount, 5000, 100);

      ackManager.stop();

      connection.close();

      server1.stop();

      assertEquals(0, AckManagerProvider.getSize());
   }

   private void addFakeRetries(AckManager ackManager, Queue testQueue, int size) {
      for (int i = 0; i < size; i++) {
         // adding retries that will never succeed, just to fillup the storage hashmap
         ackManager.addRetry(null, testQueue, server1.getStorageManager().generateID(), AckReason.NORMAL);
      }
   }

   private int getCounter(byte typeRecord, Map<Integer, AtomicInteger> values) {
      AtomicInteger value = values.get((int) typeRecord);
      if (value == null) {
         return 0;
      } else {
         return value.get();
      }
   }

   protected static AMQPMirrorControllerTarget locateMirrorTarget(ActiveMQServer server) {
      ActiveMQServerImpl theServer = (ActiveMQServerImpl) server;

      for (RemotingConnection connection : theServer.getRemotingService().getConnections()) {
         if (connection instanceof ActiveMQProtonRemotingConnection protonRC) {
            for (AMQPSessionContext sessionContext : protonRC.getAmqpConnection().getSessions().values()) {
               for (ProtonAbstractReceiver receiver : sessionContext.getReceivers().values()) {
                  if (receiver instanceof AMQPMirrorControllerTarget target) {
                     return target;
                  }
               }
            }
         }
      }

      return null;
   }

}
