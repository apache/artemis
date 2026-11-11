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
package org.apache.activemq.artemis.tests.integration.paging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.paging.impl.PagingManagerImplAccessor;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.SizeAwareMetric;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class MaxMessagesPagingTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected static final int PAGE_MAX = 100 * 1024;
   protected static final int PAGE_SIZE = 10 * 1024;
   protected ActiveMQServer server;

   @Test
   public void testGlobalMaxMessages() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("testGlobalMaxMessages");
      clearDataRecreateServerDirs();

      Configuration config = createDefaultInVMConfig();

      final int PAGE_MAX = 100 * 1024;

      final int PAGE_SIZE = 10 * 1024;

      ActiveMQServer server = createServer(true, config, PAGE_SIZE, PAGE_MAX);
      server.getConfiguration().setGlobalMaxMessages(100);
      server.start();

      ServerLocator locator = createInVMNonHALocator();

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

      session.createQueue(QueueConfiguration.of(ADDRESS).setAddress(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage message = null;

      int messageSize = 1 * 1024;

      for (int i = 0; i < 30; i++) {
         message = session.createMessage(true);

         message.getBodyBuffer().writerIndex(0);

         message.getBodyBuffer().writeBytes(new byte[messageSize]);

         for (int j = 1; j <= messageSize; j++) {
            message.getBodyBuffer().writeInt(j);
         }

         producer.send(message);
      }

      Queue queue = server.locateQueue(ADDRESS);

      Wait.assertTrue(queue.getPagingStore()::isPaging);

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      session.start();

      for (int i = 0; i < 30; i++) {
         message = consumer.receive(5000);
         assertNotNull(message);
         message.acknowledge();
      }
      session.commit();

      Wait.assertFalse(queue.getPagingStore()::isPaging);

      messageSize = 1;

      for (int i = 0; i < 102; i++) {
         message = session.createMessage(true);

         message.getBodyBuffer().writerIndex(0);

         message.getBodyBuffer().writeBytes(new byte[messageSize]);

         producer.send(message);
         if (i == 30) {
            // it should not kick based on the size of the address
            Wait.assertFalse(queue.getPagingStore()::isPaging);
         }
      }

      Wait.assertTrue(queue.getPagingStore()::isPaging);

      SizeAwareMetric globalSizeMetric = PagingManagerImplAccessor.globalSizeAwareMetric(server.getPagingManager());

      // this is validating the test is actually validating paging after over elements
      assertTrue(globalSizeMetric.isOverElements());
      assertFalse(globalSizeMetric.isOverSize());

      session.close();
   }

   @Test
   public void testGlobalMaxMessagesMultipleQueues() throws Exception {
      final String baseAddress = "testGlobal";
      clearDataRecreateServerDirs();

      Configuration config = createDefaultInVMConfig();

      final int PAGE_MAX = 100 * 1024;

      final int PAGE_SIZE = 10 * 1024;

      ActiveMQServer server = createServer(true, config, PAGE_SIZE, PAGE_MAX);
      server.getConfiguration().setGlobalMaxMessages(50);
      server.start();

      ServerLocator locator = createInVMNonHALocator();

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

      for (int adr = 1; adr <= 2; adr++) {
         SimpleString address = SimpleString.of(baseAddress + adr);
         session.createQueue(QueueConfiguration.of(address).setAddress(address));
      }

      for (int adr = 1; adr <= 2; adr++) {
         SimpleString address = SimpleString.of(baseAddress + adr);
         ClientProducer producer = session.createProducer(address);

         ClientMessage message = null;

         for (int i = 0; i < 30; i++) {
            message = session.createMessage(true);

            message.getBodyBuffer().writerIndex(0);

            message.getBodyBuffer().writeBytes(new byte[1]);

            producer.send(message);
         }

         Queue queue = server.locateQueue(address);
         if (adr == 1) {
            // first address is fine
            Wait.assertFalse(queue.getPagingStore()::isPaging);
         } else {
            // on second one we reach max
            Wait.assertTrue(queue.getPagingStore()::isPaging);
         }
      }

      SizeAwareMetric globalSizeMetric = PagingManagerImplAccessor.globalSizeAwareMetric(server.getPagingManager());

      // this is validating the test is actually validating paging after over elements
      assertTrue(globalSizeMetric.isOverElements());
      assertFalse(globalSizeMetric.isOverSize());

      session.close();
   }

   @Test
   public void testMaxOnAddress() throws Exception {
      final String baseAddress = "testMaxOnAddress";
      clearDataRecreateServerDirs();

      Configuration config = createDefaultInVMConfig();

      final int PAGE_MAX = 100 * 1024;

      final int PAGE_SIZE = 10 * 1024;

      ActiveMQServer server = createServer(true, config, PAGE_SIZE, PAGE_MAX);
      server.getConfiguration().setGlobalMaxMessages(50);
      server.start();

      AddressSettings max5 = new AddressSettings().setMaxSizeMessages(5);
      server.getAddressSettingsRepository().addMatch("#", max5);

      ServerLocator locator = createInVMNonHALocator();

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

      for (int adr = 1; adr <= 2; adr++) {
         SimpleString address = SimpleString.of(baseAddress + adr);
         session.createQueue(QueueConfiguration.of(address).setAddress(address));
      }

      for (int adr = 1; adr <= 1; adr++) {
         SimpleString address = SimpleString.of(baseAddress + adr);
         ClientProducer producer = session.createProducer(address);

         ClientMessage message = null;

         Queue queue = server.locateQueue(address);
         for (int i = 0; i < 10; i++) {
            message = session.createMessage(true);

            message.getBodyBuffer().writerIndex(0);

            message.getBodyBuffer().writeBytes(new byte[1]);

            producer.send(message);
            if (i >= 4) {
               Wait.assertTrue(queue.getPagingStore()::isPaging);
            } else {
               assertFalse(queue.getPagingStore().isPaging());
            }
         }
      }
   }

   @Test
   public void testMaxOnAddressHitGlobal() throws Exception {
      final String baseAddress = "testMaxOnAddress";
      clearDataRecreateServerDirs();

      Configuration config = createDefaultInVMConfig();

      final int PAGE_MAX = 100 * 1024;

      final int PAGE_SIZE = 10 * 1024;

      ActiveMQServer server = createServer(true, config, PAGE_SIZE, PAGE_MAX);
      server.getConfiguration().setGlobalMaxMessages(40);
      server.start();

      AddressSettings max5 = new AddressSettings().setMaxSizeMessages(5).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      server.getAddressSettingsRepository().addMatch("#", max5);

      ServerLocator locator = createInVMNonHALocator();

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

      for (int adr = 0; adr < 11; adr++) {
         SimpleString address = SimpleString.of(baseAddress + adr);
         session.createQueue(QueueConfiguration.of(address).setAddress(address));
         ClientProducer producer = session.createProducer(address);

         ClientMessage message = null;

         Queue queue = server.locateQueue(address);
         for (int i = 0; i < 4; i++) {
            message = session.createMessage(true);

            message.getBodyBuffer().writerIndex(0);

            message.getBodyBuffer().writeBytes(new byte[1]);

            producer.send(message);
         }

         if (adr >= 9) {
            Wait.assertTrue(queue.getPagingStore()::isPaging);
         } else {
            assertFalse(queue.getPagingStore().isPaging());
         }
      }
   }

   @Test
   public void testFailMaxMessage() throws Exception {
      internalFailMaxMessge(false);
   }

   @Test
   public void testFailMaxMessageGlobal() throws Exception {
      internalFailMaxMessge(true);
   }

   private void internalFailMaxMessge(boolean global) throws Exception {
      clearDataRecreateServerDirs();

      Configuration config = createDefaultConfig(true);

      if (global) {
         config.setGlobalMaxMessages(10);
      }

      server = createServer(true, config, 1024, 5 * 1024, new HashMap<>());

      server.start();

      internalFailMaxMessages("CORE", server, global);
      internalFailMaxMessages("AMQP", server, global);
      internalFailMaxMessages("OPENWIRE", server, global);
   }

   private void internalFailMaxMessages(String protocol, ActiveMQServer server, boolean global) throws Exception {

      final String ADDRESS = "FAIL_MAX_MESSAGES_" + protocol;
      final int MESSAGE_COUNT = 10;

      AddressSettings set = new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      if (global) {
         set.setMaxSizeBytes(-1).setMaxSizeMessages(-1);
      } else {
         set.setMaxSizeBytes(-1).setMaxSizeMessages(MESSAGE_COUNT);
      }

      server.getAddressSettingsRepository().addMatch(ADDRESS, set);


      server.addAddressInfo(new AddressInfo(ADDRESS).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(ADDRESS).setRoutingType(RoutingType.ANYCAST));

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      Connection conn = factory.createConnection();

      runAfter(conn::close); // not using closeable because OPENWIRE might not support it depending on the version

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      conn.start();

      Queue queue = server.locateQueue(ADDRESS);

      for (int repeat = 0; repeat < 5; repeat++) {
         boolean durable = repeat % 2 == 0;

         MessageProducer producer = session.createProducer(session.createQueue(ADDRESS));

         // Mixing persistent and non persistent just to challenge counters a bit more
         // in case there's a different counter for persistent and non persistent on the server's impl
         producer.setDeliveryMode(durable ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
         for (int i = 0; i < MESSAGE_COUNT; i++) {
            producer.send(session.createTextMessage("OK"));
         }

         Wait.assertEquals(MESSAGE_COUNT, queue::getMessageCount);

         try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
            producer.send(session.createTextMessage("should fail"));
            if (durable) {
               fail(("supposed to fail"));
            } else {
               // in case of async send, the exception will not propagate to the client, and we should still check the logger on that case
               Wait.assertTrue(() -> loggerHandler.findTrace("is full")); // my intention was to assert for "AMQ229102" howerver openwire is not using the code here
            }
         } catch (Exception expected) {
         }

         MessageConsumer consumer = session.createConsumer(session.createQueue(ADDRESS));

         for (int i = 0; i < 10; i++) {
            TextMessage message = (TextMessage) consumer.receive(1000);
            assertNotNull(message);
            assertEquals("OK", message.getText());
         }

         assertNull(consumer.receiveNoWait());

         consumer.close();
         producer.close();
      }

      conn.close();
   }


   @Test
   public void testBlockMaxMessage() throws Exception {
      internalBlockMaxMessge(false);
   }

   @Test
   public void testBlockMaxMessageGlobal() throws Exception {
      internalBlockMaxMessge(true);
   }

   private void internalBlockMaxMessge(boolean global) throws Exception {
      clearDataRecreateServerDirs();

      Configuration config = createDefaultConfig(true);

      if (global) {
         config.setGlobalMaxMessages(10);
      }

      server = createServer(true, config, 1024, 5 * 1024, new HashMap<>());

      server.start();

      internalBlockMaxMessages("AMQP", "CORE", server, global);
      internalBlockMaxMessages("AMQP", "OPENWIRE", server, global);
      internalBlockMaxMessages("AMQP", "AMQP", server, global);
      internalBlockMaxMessages("CORE", "CORE", server, global);
      internalBlockMaxMessages("CORE", "AMQP", server, global);
      internalBlockMaxMessages("CORE", "OPENWIRE", server, global);
      internalBlockMaxMessages("OPENWIRE", "OPENWIRE", server, global);
      internalBlockMaxMessages("OPENWIRE", "AMQP", server, global);
      internalBlockMaxMessages("OPENWIRE", "CORE", server, global);
   }

   private void internalBlockMaxMessages(String protocolSend, String protocolReceive, ActiveMQServer server, boolean global) throws Exception {

      final int MESSAGES = 1200;

      logger.info("\n{}\nSending {}, Receiving {}\n{}", "*".repeat(80), protocolSend, protocolReceive, "*".repeat(80));

      final String ADDRESS = "FAIL_MAX_MESSAGES_" + protocolSend + "_" + protocolReceive;

      AddressSettings set = new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK);
      if (global) {
         set.setMaxSizeBytes(-1).setMaxSizeMessages(-1);
      } else {
         set.setMaxSizeBytes(-1).setMaxSizeMessages(10);
      }

      server.getAddressSettingsRepository().addMatch(ADDRESS, set);

      server.addAddressInfo(new AddressInfo(ADDRESS).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(ADDRESS).setRoutingType(RoutingType.ANYCAST));

      ConnectionFactory factorySend = CFUtil.createConnectionFactory(protocolSend, "tcp://localhost:61616");
      Connection connSend = factorySend.createConnection();

      ConnectionFactory factoryReceive = CFUtil.createConnectionFactory(protocolReceive, "tcp://localhost:61616");
      Connection connReceive = factoryReceive.createConnection();
      connReceive.start();

      runAfter(connSend::close); // not using closeable because OPENWIRE might not support it depending on the version
      runAfter(connReceive::close); // not using closeable because OPENWIRE might not support it depending on the version

      ExecutorService executorService = Executors.newSingleThreadExecutor();
      runAfter(executorService::shutdownNow);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         CountDownLatch done = new CountDownLatch(1);
         executorService.execute(() -> {
            try {
               Session session = connSend.createSession(false, Session.AUTO_ACKNOWLEDGE);
               MessageProducer producer = session.createProducer(session.createQueue(ADDRESS));
               producer.setDeliveryMode(protocolSend.equals("OPENWIRE") ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
               for (int i = 0; i < MESSAGES; i++) {
                  logger.debug("Sending {} protocol {}", i, protocolSend);
                  producer.send(session.createTextMessage("OK!" + i));
               }
               session.close();
            } catch (Exception e) {
               // e.printStackTrace();
            }

            done.countDown();
         });

         Wait.assertTrue(() -> loggerHandler.findText("AMQ222183"), 5000, 10); //unblock
         assertFalse(loggerHandler.findText("AMQ221046")); // should not been unblocked

         assertFalse(done.await(200, TimeUnit.MILLISECONDS));
      }

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         Session sessionReceive = connReceive.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = sessionReceive.createConsumer(sessionReceive.createQueue(ADDRESS));
         for (int i = 0; i < MESSAGES; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals("OK!" + i, message.getText());
         }
         sessionReceive.close();

         Wait.assertTrue(() -> loggerHandler.findText("AMQ221046"), 5000, 10); // unblock
      }
   }



   @Test
   public void testDropMaxMessage() throws Exception {
      internalDropMaxMessge(false);
   }

   @Test
   public void testDropMaxMessageGlobal() throws Exception {
      internalDropMaxMessge(true);
   }

   private void internalDropMaxMessge(boolean global) throws Exception {
      clearDataRecreateServerDirs();

      Configuration config = createDefaultConfig(true);

      if (global) {
         config.setGlobalMaxMessages(10);
      }

      server = createServer(true, config, 1024, 5 * 1024, new HashMap<>());

      server.start();

      internalDropMaxMessages("AMQP", "CORE", server, global);
      internalDropMaxMessages("AMQP", "OPENWIRE", server, global);
      internalDropMaxMessages("AMQP", "AMQP", server, global);
      internalDropMaxMessages("CORE", "CORE", server, global);
      internalDropMaxMessages("CORE", "AMQP", server, global);
      internalDropMaxMessages("CORE", "OPENWIRE", server, global);
      internalDropMaxMessages("OPENWIRE", "OPENWIRE", server, global);
      internalDropMaxMessages("OPENWIRE", "AMQP", server, global);
      internalDropMaxMessages("OPENWIRE", "CORE", server, global);
   }

   private void internalDropMaxMessages(String protocolSend, String protocolReceive, ActiveMQServer server, boolean global) throws Exception {

      final int MESSAGES = 20;

      logger.info("\n{}\nSending {}, Receiving {}\n", "*".repeat(80), protocolSend, protocolReceive, "*".repeat(80));

      final String ADDRESS = "FAIL_MAX_MESSAGES_" + protocolSend + "_" + protocolReceive;

      AddressSettings set = new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.DROP);
      if (global) {
         set.setMaxSizeBytes(-1).setMaxSizeMessages(-1);
      } else {
         set.setMaxSizeBytes(-1).setMaxSizeMessages(10);
      }

      server.getAddressSettingsRepository().addMatch(ADDRESS, set);

      server.addAddressInfo(new AddressInfo(ADDRESS).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(ADDRESS).setRoutingType(RoutingType.ANYCAST));

      ConnectionFactory factorySend = CFUtil.createConnectionFactory(protocolSend, "tcp://localhost:61616");
      Connection connSend = factorySend.createConnection();

      ConnectionFactory factoryReceive = CFUtil.createConnectionFactory(protocolReceive, "tcp://localhost:61616");
      Connection connReceive = factoryReceive.createConnection();
      connReceive.start();

      runAfter(connSend::close); // not using closeable because OPENWIRE might not support it depending on the version
      runAfter(connReceive::close); // not using closeable because OPENWIRE might not support it depending on the version

      for (int repeat = 0; repeat < 5; repeat++) {
         try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
            Session session = connSend.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(session.createQueue(ADDRESS));
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            for (int i = 0; i < MESSAGES; i++) {
               producer.send(session.createTextMessage("OK!" + i));
            }
            session.close();

            if (repeat == 0) {
               // the server will only log it on the first repeat as expected
               assertTrue(loggerHandler.findText("AMQ222039")); // dropped messages
            }

            Session sessionReceive = connReceive.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumer = sessionReceive.createConsumer(sessionReceive.createQueue(ADDRESS));
            for (int i = 0; i < 10; i++) {
               TextMessage message = (TextMessage) consumer.receive(5000);
               assertNotNull(message);
            }
            assertNull(consumer.receiveNoWait());
            sessionReceive.close();
         }
      }

   }

}
