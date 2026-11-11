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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class MessageGroupingTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQServer server;

   private ClientSession clientSession;

   private ClientSessionFactory clientSessionFactory;

   private final SimpleString qName = SimpleString.of("MessageGroupingTestQueue");
   private ServerLocator locator;

   @Test
   public void testBasicGrouping() throws Exception {
      doTestBasicGrouping();
   }

   @Test
   public void testMultipleGrouping() throws Exception {
      doTestMultipleGrouping();
   }

   @Test
   public void testMultipleGroupingSingleConsumerWithDirectDelivery() throws Exception {
      doTestMultipleGroupingSingleConsumer(true);
   }

   @Test
   public void testMultipleGroupingSingleConsumerWithoutDirectDelivery() throws Exception {
      doTestMultipleGroupingSingleConsumer(false);
   }

   @Test
   public void testMultipleGroupingTXCommit() throws Exception {
      doTestMultipleGroupingTXCommit();
   }

   @Test
   public void testMultipleGroupingTXRollback() throws Exception {
      doTestMultipleGroupingTXRollback();
   }

   @Test
   public void testMultipleGroupingXACommit() throws Exception {
      dotestMultipleGroupingXACommit();
   }

   @Test
   public void testMultipleGroupingXARollback() throws Exception {
      doTestMultipleGroupingXARollback();
   }

   @Test
   public void testLoadBalanceGroups() throws Exception {
      assumeFalse(clientSessionFactory.getServerLocator().isAutoGroup(), "only makes sense withOUT auto-group");

      ClientProducer clientProducer = clientSession.createProducer(qName);
      ClientConsumer consumer1 = clientSession.createConsumer(qName);
      ClientConsumer consumer2 = clientSession.createConsumer(qName);
      ClientConsumer consumer3 = clientSession.createConsumer(qName);
      ClientConsumer[] consumers = new ClientConsumer[]{consumer1, consumer2, consumer3};
      int[] counts = new int[consumers.length];

      clientSession.start();
      try {
         //Add all messages for a particular group before moving onto the next
         for (int group = 0; group < 10; group++) {
            for (int messageId = 0; messageId < 3; messageId++) {
               ClientMessage message = clientSession.createMessage(false);
               message.putStringProperty("_AMQ_GROUP_ID", "" + group);
               clientProducer.send(message);
            }
         }

         for (int c = 0; c < consumers.length; c++) {
            while (true) {
               ClientMessage msg = consumers[c].receiveImmediate();
               if (msg == null) {
                  break;
               }
               counts[c]++;
            }
         }

         for (int count : counts) {
            assertNotEquals(30, count, "You shouldn't have all messages bound to a single consumer");
            assertNotEquals(0, count, "But you shouldn't have also a single consumer bound to none");
         }
      } finally {
         consumer1.close();
         consumer2.close();
         consumer3.close();
      }
   }

   private void doTestBasicGrouping() throws Exception {
      ClientProducer clientProducer = clientSession.createProducer(qName);
      ClientConsumer consumer = clientSession.createConsumer(qName);
      ClientConsumer consumer2 = clientSession.createConsumer(qName);
      clientSession.start();

      SimpleString groupId = SimpleString.of("grp1");
      int numMessages = 100;
      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(clientSession, "m" + i);
         message.putStringProperty(Message.HDR_GROUP_ID, groupId);
         clientProducer.send(message);
      }

      CountDownLatch latch = new CountDownLatch(numMessages);
      DummyMessageHandler dummyMessageHandler = new DummyMessageHandler(latch, true);
      consumer.setMessageHandler(dummyMessageHandler);
      DummyMessageHandler dummyMessageHandler2 = new DummyMessageHandler(latch, true);
      consumer2.setMessageHandler(dummyMessageHandler2);
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      assertEquals(100, dummyMessageHandler.list.size());
      assertEquals(0, dummyMessageHandler2.list.size());
      consumer.close();
      consumer2.close();
   }

   @Test
   public void testMultipleGroupingConsumeHalf() throws Exception {
      ClientProducer clientProducer = clientSession.createProducer(qName);
      ClientConsumer consumer = clientSession.createConsumer(qName);
      ClientConsumer consumer2 = clientSession.createConsumer(qName);
      clientSession.start();

      //need to wait a bit or consumers might be busy
      Thread.sleep(200);

      SimpleString groupId = SimpleString.of("grp1");
      SimpleString groupId2 = SimpleString.of("grp2");
      int numMessages = 100;
      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(clientSession, "m" + i);
         if (i % 2 == 0 || i == 0) {
            message.putStringProperty(Message.HDR_GROUP_ID, groupId);
         } else {
            message.putStringProperty(Message.HDR_GROUP_ID, groupId2);
         }
         clientProducer.send(message);
      }

      for (int i = 0; i < numMessages / 2; i++) {
         ClientMessage cm = consumer.receive(500);
         assertNotNull(cm);
         assertEquals(cm.getBodyBuffer().readString(), "m" + i);
         i++;
         cm = consumer2.receive(500);
         assertNotNull(cm);
         assertEquals(cm.getBodyBuffer().readString(), "m" + i);
      }

      logger.debug("closing consumer2");

      consumer2.close();

      consumer.close();
   }

   private void doTestMultipleGroupingSingleConsumer(final boolean directDelivery) throws Exception {
      ClientProducer clientProducer = clientSession.createProducer(qName);
      ClientConsumer consumer = clientSession.createConsumer(qName);
      if (directDelivery) {
         clientSession.start();
      }
      SimpleString groupId = SimpleString.of("grp1");
      SimpleString groupId2 = SimpleString.of("grp2");
      int numMessages = 100;
      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(clientSession, "m" + i);
         if (i % 2 == 0 || i == 0) {
            message.putStringProperty(Message.HDR_GROUP_ID, groupId);
         } else {
            message.putStringProperty(Message.HDR_GROUP_ID, groupId2);
         }
         clientProducer.send(message);
      }
      if (!directDelivery) {
         clientSession.start();
      }
      CountDownLatch latch = new CountDownLatch(numMessages);
      DummyMessageHandler dummyMessageHandler = new DummyMessageHandler(latch, true);
      consumer.setMessageHandler(dummyMessageHandler);
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      assertEquals(100, dummyMessageHandler.list.size());
      int i = 0;
      for (ClientMessage message : dummyMessageHandler.list) {
         assertEquals(message.getBodyBuffer().readString(), "m" + i);
         i += 1;
      }
      consumer.close();
   }

   private void doTestMultipleGroupingTXCommit() throws Exception {
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession clientSession = sessionFactory.createSession(false, false, false);
      ClientProducer clientProducer = this.clientSession.createProducer(qName);
      clientSession.start();

      ClientConsumer consumer = clientSession.createConsumer(qName);
      ClientConsumer consumer2 = clientSession.createConsumer(qName);

      //Wait a bit otherwise consumers might be busy
      Thread.sleep(200);

      SimpleString groupId = SimpleString.of("grp1");
      SimpleString groupId2 = SimpleString.of("grp2");
      int numMessages = 100;
      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(clientSession, "m" + i);
         if (i % 2 == 0 || i == 0) {
            message.putStringProperty(Message.HDR_GROUP_ID, groupId);
         } else {
            message.putStringProperty(Message.HDR_GROUP_ID, groupId2);
         }
         clientProducer.send(message);
      }

      CountDownLatch latch = new CountDownLatch(numMessages);
      DummyMessageHandler dummyMessageHandler = new DummyMessageHandler(latch, true);
      consumer.setMessageHandler(dummyMessageHandler);
      DummyMessageHandler dummyMessageHandler2 = new DummyMessageHandler(latch, true);
      consumer2.setMessageHandler(dummyMessageHandler2);
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      clientSession.commit();
      assertEquals(50, dummyMessageHandler.list.size());
      int i = 0;
      for (ClientMessage message : dummyMessageHandler.list) {
         assertEquals(message.getBodyBuffer().readString(), "m" + i);
         i += 2;
      }
      assertEquals(50, dummyMessageHandler2.list.size());
      i = 1;
      for (ClientMessage message : dummyMessageHandler2.list) {
         assertEquals(message.getBodyBuffer().readString(), "m" + i);
         i += 2;
      }
      consumer.close();
      consumer2.close();
      consumer = this.clientSession.createConsumer(qName);
      assertNull(consumer.receiveImmediate());
      clientSession.close();
      locator.close();
   }

   private void doTestMultipleGroupingTXRollback() throws Exception {
      ServerLocator locator = createInVMNonHALocator();
      locator.setBlockOnAcknowledge(true);
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession clientSession = sessionFactory.createSession(false, false, false);
      ClientProducer clientProducer = this.clientSession.createProducer(qName);
      ClientConsumer consumer = clientSession.createConsumer(qName);
      ClientConsumer consumer2 = clientSession.createConsumer(qName);
      clientSession.start();

      //need to wait a bit or consumers might be busy
      Thread.sleep(200);

      SimpleString groupId = SimpleString.of("grp1");
      SimpleString groupId2 = SimpleString.of("grp2");
      int numMessages = 100;
      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(clientSession, "m" + i);
         if (i % 2 == 0 || i == 0) {
            message.putStringProperty(Message.HDR_GROUP_ID, groupId);
         } else {
            message.putStringProperty(Message.HDR_GROUP_ID, groupId2);
         }
         clientProducer.send(message);
      }

      CountDownLatch latch = new CountDownLatch(numMessages);
      DummyMessageHandler dummyMessageHandler = new DummyMessageHandler(latch, true);
      consumer.setMessageHandler(dummyMessageHandler);
      DummyMessageHandler dummyMessageHandler2 = new DummyMessageHandler(latch, true);
      consumer2.setMessageHandler(dummyMessageHandler2);
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      assertEquals(50, dummyMessageHandler.list.size(), dummyMessageHandler.list.size());
      int i = 0;
      for (ClientMessage message : dummyMessageHandler.list) {
         assertEquals(message.getBodyBuffer().readString(), "m" + i);
         i += 2;
      }
      assertEquals(50, dummyMessageHandler2.list.size());
      i = 1;
      for (ClientMessage message : dummyMessageHandler2.list) {
         assertEquals(message.getBodyBuffer().readString(), "m" + i);
         i += 2;
      }
      latch = new CountDownLatch(numMessages);
      dummyMessageHandler.reset(latch);
      dummyMessageHandler2.reset(latch);
      clientSession.rollback();
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      assertEquals(50, dummyMessageHandler.list.size());
      i = 0;
      for (ClientMessage message : dummyMessageHandler.list) {
         assertEquals(message.getBodyBuffer().readString(), "m" + i);
         i += 2;
      }
      assertEquals(50, dummyMessageHandler2.list.size());
      i = 1;
      for (ClientMessage message : dummyMessageHandler2.list) {
         assertEquals(message.getBodyBuffer().readString(), "m" + i);
         i += 2;
      }
      consumer = this.clientSession.createConsumer(qName);
      assertNull(consumer.receiveImmediate());
      clientSession.close();
      locator.close();
   }

   private void dotestMultipleGroupingXACommit() throws Exception {
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession clientSession = sessionFactory.createSession(true, false, false);
      ClientProducer clientProducer = this.clientSession.createProducer(qName);
      ClientConsumer consumer = clientSession.createConsumer(qName);
      ClientConsumer consumer2 = clientSession.createConsumer(qName);
      clientSession.start();

      Xid xid = new XidImpl("bq".getBytes(), 4, "gtid".getBytes());
      clientSession.start(xid, XAResource.TMNOFLAGS);

      SimpleString groupId = SimpleString.of("grp1");
      SimpleString groupId2 = SimpleString.of("grp2");
      int numMessages = 100;
      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(clientSession, "m" + i);
         if (i % 2 == 0 || i == 0) {
            message.putStringProperty(Message.HDR_GROUP_ID, groupId);
         } else {
            message.putStringProperty(Message.HDR_GROUP_ID, groupId2);
         }
         clientProducer.send(message);
      }
      CountDownLatch latch = new CountDownLatch(numMessages);
      DummyMessageHandler dummyMessageHandler = new DummyMessageHandler(latch, true);
      consumer.setMessageHandler(dummyMessageHandler);
      DummyMessageHandler dummyMessageHandler2 = new DummyMessageHandler(latch, true);
      consumer2.setMessageHandler(dummyMessageHandler2);
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);
      clientSession.commit(xid, false);
      assertEquals(50, dummyMessageHandler.list.size());
      int i = 0;
      for (ClientMessage message : dummyMessageHandler.list) {
         assertEquals(message.getBodyBuffer().readString(), "m" + i);
         i += 2;
      }
      assertEquals(50, dummyMessageHandler2.list.size());
      i = 1;
      for (ClientMessage message : dummyMessageHandler2.list) {
         assertEquals(message.getBodyBuffer().readString(), "m" + i);
         i += 2;
      }
      consumer.close();
      consumer2.close();
      consumer = this.clientSession.createConsumer(qName);
      assertNull(consumer.receiveImmediate());
      clientSession.close();
      locator.close();
   }

   private void doTestMultipleGroupingXARollback() throws Exception {
      ServerLocator locator = createInVMNonHALocator();
      locator.setBlockOnAcknowledge(true);
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession clientSession = sessionFactory.createSession(true, false, false);
      ClientProducer clientProducer = this.clientSession.createProducer(qName);
      clientSession.start();

      ClientConsumer consumer = clientSession.createConsumer(qName);
      ClientConsumer consumer2 = clientSession.createConsumer(qName);
      Xid xid = new XidImpl("bq".getBytes(), 4, "gtid".getBytes());
      clientSession.start(xid, XAResource.TMNOFLAGS);

      SimpleString groupId = SimpleString.of("grp1");
      SimpleString groupId2 = SimpleString.of("grp2");
      int numMessages = 100;
      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(clientSession, "m" + i);
         if (i % 2 == 0 || i == 0) {
            message.putStringProperty(Message.HDR_GROUP_ID, groupId);
         } else {
            message.putStringProperty(Message.HDR_GROUP_ID, groupId2);
         }
         clientProducer.send(message);
      }

      CountDownLatch latch = new CountDownLatch(numMessages);
      DummyMessageHandler dummyMessageHandler = new DummyMessageHandler(latch, true);
      consumer.setMessageHandler(dummyMessageHandler);
      DummyMessageHandler dummyMessageHandler2 = new DummyMessageHandler(latch, true);
      consumer2.setMessageHandler(dummyMessageHandler2);
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      clientSession.end(xid, XAResource.TMSUCCESS);
      assertEquals(50, dummyMessageHandler.list.size());
      int i = 0;
      for (ClientMessage message : dummyMessageHandler.list) {
         assertEquals(message.getBodyBuffer().readString(), "m" + i);
         i += 2;
      }
      assertEquals(50, dummyMessageHandler2.list.size());
      i = 1;
      for (ClientMessage message : dummyMessageHandler2.list) {
         assertEquals(message.getBodyBuffer().readString(), "m" + i);
         i += 2;
      }
      latch = new CountDownLatch(numMessages);
      dummyMessageHandler.reset(latch);
      dummyMessageHandler2.reset(latch);
      clientSession.rollback(xid);
      clientSession.start(xid, XAResource.TMNOFLAGS);
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);
      clientSession.commit(xid, false);
      assertEquals(50, dummyMessageHandler.list.size());
      i = 0;
      for (ClientMessage message : dummyMessageHandler.list) {
         assertEquals(message.getBodyBuffer().readString(), "m" + i);
         i += 2;
      }
      assertEquals(50, dummyMessageHandler2.list.size());
      i = 1;
      for (ClientMessage message : dummyMessageHandler2.list) {
         assertEquals(message.getBodyBuffer().readString(), "m" + i);
         i += 2;
      }
      consumer = this.clientSession.createConsumer(qName);
      assertNull(consumer.receiveImmediate());
      clientSession.close();
      locator.close();
   }

   private void doTestMultipleGrouping() throws Exception {
      ClientProducer clientProducer = clientSession.createProducer(qName);
      ClientConsumer consumer = clientSession.createConsumer(qName);
      ClientConsumer consumer2 = clientSession.createConsumer(qName);
      clientSession.start();

      SimpleString groupId = SimpleString.of("grp1");
      SimpleString groupId2 = SimpleString.of("grp2");
      int numMessages = 4;
      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(clientSession, "m" + i);
         if (i % 2 == 0 || i == 0) {
            message.putStringProperty(Message.HDR_GROUP_ID, groupId);
         } else {
            message.putStringProperty(Message.HDR_GROUP_ID, groupId2);
         }
         clientProducer.send(message);
      }

      CountDownLatch latch = new CountDownLatch(numMessages);
      DummyMessageHandler dummyMessageHandler = new DummyMessageHandler(latch, true);
      consumer.setMessageHandler(dummyMessageHandler);
      DummyMessageHandler dummyMessageHandler2 = new DummyMessageHandler(latch, true);
      consumer2.setMessageHandler(dummyMessageHandler2);
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      assertEquals(numMessages / 2, dummyMessageHandler.list.size());
      int i = 0;
      for (ClientMessage message : dummyMessageHandler.list) {
         assertEquals(message.getBodyBuffer().readString(), "m" + i);
         i += 2;
      }
      assertEquals(numMessages / 2, dummyMessageHandler2.list.size());
      i = 1;
      for (ClientMessage message : dummyMessageHandler2.list) {
         assertEquals(message.getBodyBuffer().readString(), "m" + i);
         i += 2;
      }
      consumer.close();
      consumer2.close();
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      Configuration configuration = createDefaultInVMConfig();
      server = addServer(ActiveMQServers.newActiveMQServer(configuration, false));
      server.start();
      locator = createInVMNonHALocator();
      clientSessionFactory = createSessionFactory(locator);
      clientSession = addClientSession(clientSessionFactory.createSession(false, true, true));
      clientSession.createQueue(QueueConfiguration.of(qName).setDurable(false));
   }

   private static class DummyMessageHandler implements MessageHandler {

      List<ClientMessage> list = new ArrayList<>();

      private CountDownLatch latch;

      private final boolean acknowledge;

      private DummyMessageHandler(final CountDownLatch latch, final boolean acknowledge) {
         this.latch = latch;
         this.acknowledge = acknowledge;
      }

      @Override
      public void onMessage(final ClientMessage message) {
         list.add(message);
         if (acknowledge) {
            try {
               message.acknowledge();
            } catch (ActiveMQException e) {
               // ignore
            }
         }
         latch.countDown();
      }

      public void reset(final CountDownLatch latch) {
         list.clear();
         this.latch = latch;
      }
   }
}
