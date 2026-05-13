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
package org.apache.activemq.artemis.tests.integration.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.ConcurrentModificationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.LastValueQueue;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LVQTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQServer server;

   private ClientSession clientSession;

   private ClientSession clientSessionTxReceives;

   private ClientSession clientSessionTxSends;

   private final SimpleString address = SimpleString.of("LVQTestAddress");

   private final SimpleString qName1 = SimpleString.of("LVQTestQ1");

   @Test
   public void testSimple() throws Exception {
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      SimpleString rh = SimpleString.of("SMID1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      producer.send(m1);
      producer.send(m2);

      Queue lvQueue = server.locateQueue(qName1);
      // delivering is asynchronous. We have to make sure that all messages were sent
      Wait.assertEquals(2L, lvQueue::getMessagesAdded, 5000, 100);
      // messages were replaced by the LVQ so it should have only one
      Wait.assertEquals(1L, lvQueue::getMessageCount, 5000, 100);
      Wait.assertEquals(1L, lvQueue::getMessagesReplaced, 5000, 100);

      clientSession.start();
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();

      assertEquals("m2", m.getBodyBuffer().readString());
   }

   @Test
   public void testSimpleExclusive() throws Exception {
      ServerLocator locator = createNettyNonHALocator().setConsumerWindowSize(0);
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession clientSession = addClientSession(sf.createSession(false, true, false));
      final String EXCLUSIVE_QUEUE = "exclusiveQueue";

      clientSession.createQueue(QueueConfiguration.of(EXCLUSIVE_QUEUE).setExclusive(true).setLastValue(true));
      ClientProducer producer = clientSession.createProducer(EXCLUSIVE_QUEUE);
      ClientConsumer consumer = clientSession.createConsumer(EXCLUSIVE_QUEUE);
      clientSession.start();
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      SimpleString rh = SimpleString.of("SMID1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      producer.send(m1);
      producer.send(m2);
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m2", m.getBodyBuffer().readString());
   }

   @Test
   public void testSimpleRestart() throws Exception {
      ClientProducer producer = clientSession.createProducer(address);
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      SimpleString rh = SimpleString.of("SMID1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      producer.send(m1);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      producer.send(m2);
      Wait.assertEquals(1, () -> server.locateQueue(qName1).getMessageCount());
      clientSession.close();

      server.stop();
      server.start();

      assertEquals(1, server.locateQueue(qName1).getMessageCount());
      ServerLocator locator = createNettyNonHALocator().setBlockOnAcknowledge(true).setAckBatchSize(0);
      ClientSessionFactory sf = createSessionFactory(locator);
      clientSession = addClientSession(sf.createSession(false, true, false));
      producer = clientSession.createProducer(address);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      producer.send(m3);
      // wait b/c prune takes a deliver attempt which is async
      Wait.assertEquals(1, () -> server.locateQueue(qName1).getMessageCount());

      ClientConsumer consumer = clientSession.createConsumer(qName1);
      clientSession.start();
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m3", m.getBodyBuffer().readString());
   }

   @Test
   public void testMultipleMessages() throws Exception {
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      SimpleString messageId1 = SimpleString.of("SMID1");
      SimpleString messageId2 = SimpleString.of("SMID2");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId2);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId2);
      producer.send(m1);
      producer.send(m2);
      producer.send(m3);
      producer.send(m4);
      Queue serverQueue = server.locateQueue(qName1);
      Wait.assertEquals(2L, serverQueue::getMessageCount, 5000, 100);
      Wait.assertEquals(4L, serverQueue::getMessagesAdded, 5000, 100);
      Wait.assertEquals(2L, serverQueue::getMessagesReplaced, 5000, 100);
      clientSession.start();
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m3", m.getBodyBuffer().readString());
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m4", m.getBodyBuffer().readString());
      assertNull(consumer.receiveImmediate());
   }

   @Test
   public void testMultipleMessagesWithoutLastValue() throws Exception {
      ClientProducer producer = clientSession.createProducer(address);
      ClientMessage m1 = createTextMessage(clientSession, "message1");
      ClientMessage m2 = createTextMessage(clientSession, "message2");
      producer.send(m1);
      producer.send(m2);

      Wait.assertEquals(2L, () -> server.locateQueue(qName1).getMessageCount(), 2000, 100);

      ClientConsumer consumer = clientSession.createConsumer(qName1);
      clientSession.start();
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("message1", m.getBodyBuffer().readString());
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("message2", m.getBodyBuffer().readString());
   }

   @Test
   public void testMultipleRollback() throws Exception {
      AddressSettings qs = new AddressSettings();
      qs.setDefaultLastValueQueue(true);
      qs.setRedeliveryDelay(1);
      server.getAddressSettingsRepository().addMatch(address.toString(), qs);

      ClientProducer producer = clientSessionTxReceives.createProducer(address);
      ClientConsumer consumer = clientSessionTxReceives.createConsumer(qName1);
      SimpleString messageId1 = SimpleString.of("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      producer.send(m1);
      clientSessionTxReceives.start();
      for (int i = 0; i < 10; i++) {
         logger.debug("#Deliver {}", i);
         ClientMessage m = consumer.receive(5000);
         assertNotNull(m);
         m.acknowledge();
         clientSessionTxReceives.rollback();
      }
      m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      producer.send(m1);
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m1", m.getBodyBuffer().readString());
      assertNull(consumer.receiveImmediate());
      clientSessionTxReceives.commit();
   }

   @Test
   public void testFirstMessageReceivedButAckedAfter() throws Exception {
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      SimpleString rh = SimpleString.of("SMID1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      producer.send(m1);
      clientSession.start();
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      producer.send(m2);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m1", m.getBodyBuffer().readString());
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m2", m.getBodyBuffer().readString());
   }

   @Test
   public void testFirstMessageReceivedAndCancelled() throws Exception {
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      SimpleString rh = SimpleString.of("SMID1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      producer.send(m1);
      clientSession.start();
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      producer.send(m2);
      consumer.close();
      consumer = clientSession.createConsumer(qName1);
      m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      clientSession.commit();
      m = consumer.receiveImmediate();
      assertNull(m);
   }

   @Test
   public void testManyMessagesReceivedAndCancelled() throws Exception {
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);

      SimpleString rh = SimpleString.of("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m5 = createTextMessage(clientSession, "m5");
      m5.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m6 = createTextMessage(clientSession, "m6");
      m6.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      clientSession.start();
      producer.send(m1);
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      producer.send(m2);
      m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      producer.send(m3);
      m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      producer.send(m4);
      m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
      producer.send(m5);
      m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals("m5", m.getBodyBuffer().readString());
      producer.send(m6);
      m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals("m6", m.getBodyBuffer().readString());
      consumer.close();
      consumer = clientSession.createConsumer(qName1);
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m6", m.getBodyBuffer().readString());
      m = consumer.receiveImmediate();
      assertNull(m);
   }

   @Test
   public void testSimpleInTx() throws Exception {

      ClientProducer producer = clientSessionTxReceives.createProducer(address);
      ClientConsumer consumer = clientSessionTxReceives.createConsumer(qName1);
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      SimpleString rh = SimpleString.of("SMID1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      producer.send(m1);
      producer.send(m2);
      clientSessionTxReceives.start();
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m2", m.getBodyBuffer().readString());
   }

   @Test
   public void testMultipleMessagesInTx() throws Exception {
      ClientProducer producer = clientSessionTxReceives.createProducer(address);
      SimpleString messageId1 = SimpleString.of("SMID1");
      SimpleString messageId2 = SimpleString.of("SMID2");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId2);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId2);
      producer.send(m1);
      producer.send(m2);
      producer.send(m3);
      producer.send(m4);
      ClientConsumer consumer = clientSessionTxReceives.createConsumer(qName1);
      clientSessionTxReceives.start();
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m3", m.getBodyBuffer().readString());
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m4", m.getBodyBuffer().readString());
      clientSessionTxReceives.commit();
      m = consumer.receiveImmediate();
      assertNull(m);
   }

   @Test
   public void testMultipleMessagesInTxRollback() throws Exception {
      ClientProducer producer = clientSessionTxReceives.createProducer(address);
      ClientConsumer consumer = clientSessionTxReceives.createConsumer(qName1);
      SimpleString messageId1 = SimpleString.of("SMID1");
      SimpleString messageId2 = SimpleString.of("SMID2");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId2);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId2);
      producer.send(m1);
      producer.send(m2);
      clientSessionTxReceives.start();
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m1", m.getBodyBuffer().readString());
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m2", m.getBodyBuffer().readString());
      producer.send(m3);
      producer.send(m4);
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m3", m.getBodyBuffer().readString());
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m4", m.getBodyBuffer().readString());
      clientSessionTxReceives.rollback();
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m3", m.getBodyBuffer().readString());
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m4", m.getBodyBuffer().readString());
   }

   @Test
   public void testSingleTXRollback() throws Exception {
      ClientProducer producer = clientSessionTxReceives.createProducer(address);
      ClientConsumer consumer = clientSessionTxReceives.createConsumer(qName1);
      SimpleString messageId1 = SimpleString.of("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      producer.send(m1);
      clientSessionTxReceives.start();
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSessionTxReceives.rollback();
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSessionTxReceives.commit();
      assertEquals("m1", m.getBodyBuffer().readString());
      assertNull(consumer.receiveImmediate());
   }

   @Test
   public void testMultipleMessagesInTxSend() throws Exception {
      ClientProducer producer = clientSessionTxSends.createProducer(address);
      ClientConsumer consumer = clientSessionTxSends.createConsumer(qName1);
      clientSessionTxSends.start();
      SimpleString rh = SimpleString.of("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m5 = createTextMessage(clientSession, "m5");
      m5.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m6 = createTextMessage(clientSession, "m6");
      m6.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      producer.send(m1);
      producer.send(m2);
      producer.send(m3);
      producer.send(m4);
      producer.send(m5);
      producer.send(m6);
      clientSessionTxSends.commit();
      for (int i = 1; i < 6; i++) {
         ClientMessage m = consumer.receive(1000);
         assertNotNull(m);
         m.acknowledge();
         clientSessionTxSends.commit();
         assertEquals("m" + i, m.getBodyBuffer().readString());
      }
      consumer.close();
      consumer = clientSessionTxSends.createConsumer(qName1);
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSessionTxSends.commit();
      assertEquals("m6", m.getBodyBuffer().readString());
   }

   @Test
   public void testMultipleMessagesPersistedCorrectly() throws Exception {
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      SimpleString rh = SimpleString.of("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m1.setDurable(true);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m2.setDurable(true);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m3.setDurable(true);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m4.setDurable(true);
      ClientMessage m5 = createTextMessage(clientSession, "m5");
      m5.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m5.setDurable(true);
      ClientMessage m6 = createTextMessage(clientSession, "m6");
      m6.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m6.setDurable(true);
      producer.send(m1);
      producer.send(m2);
      producer.send(m3);
      producer.send(m4);
      producer.send(m5);
      producer.send(m6);
      clientSession.start();
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m6", m.getBodyBuffer().readString());
      m = consumer.receiveImmediate();
      assertNull(m);
      clientSession.commit();
   }

   @Test
   public void testMultipleMessagesPersistedCorrectlyInTx() throws Exception {
      ClientProducer producer = clientSessionTxSends.createProducer(address);
      SimpleString rh = SimpleString.of("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m1.setDurable(true);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m2.setDurable(true);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m3.setDurable(true);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m4.setDurable(true);
      ClientMessage m5 = createTextMessage(clientSession, "m5");
      m5.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m5.setDurable(true);
      ClientMessage m6 = createTextMessage(clientSession, "m6");
      m6.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m6.setDurable(true);
      producer.send(m1);
      clientSessionTxSends.commit();
      producer.send(m2);
      clientSessionTxSends.commit();
      producer.send(m3);
      clientSessionTxSends.commit();
      producer.send(m4);
      clientSessionTxSends.commit();
      producer.send(m5);
      clientSessionTxSends.commit();
      producer.send(m6);
      clientSessionTxSends.commit();
      clientSessionTxSends.start();
      ClientConsumer consumer = clientSessionTxSends.createConsumer(qName1);
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m6", m.getBodyBuffer().readString());
      m = consumer.receiveImmediate();
      assertNull(m);
   }

   @Test
   public void testMultipleAcksPersistedCorrectly() throws Exception {

      Queue queue = server.locateQueue(qName1);
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      SimpleString rh = SimpleString.of("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m1.setDurable(true);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m2.setDurable(true);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m3.setDurable(true);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m4.setDurable(true);
      ClientMessage m5 = createTextMessage(clientSession, "m5");
      m5.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m5.setDurable(true);
      ClientMessage m6 = createTextMessage(clientSession, "m6");
      m6.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m6.setDurable(true);
      clientSession.start();
      producer.send(m1);
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m1", m.getBodyBuffer().readString());
      producer.send(m2);
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m2", m.getBodyBuffer().readString());
      producer.send(m3);
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m3", m.getBodyBuffer().readString());
      producer.send(m4);
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m4", m.getBodyBuffer().readString());
      producer.send(m5);
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m5", m.getBodyBuffer().readString());
      producer.send(m6);
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m6", m.getBodyBuffer().readString());

      assertEquals(0, queue.getDeliveringCount());
   }

   @Test
   public void testRemoveMessageThroughManagement() throws Exception {

      Queue queue = server.locateQueue(qName1);
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      SimpleString rh = SimpleString.of("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m1.setDurable(true);
      producer.send(m1);
      clientSession.commit();

      queue.deleteAllReferences();

      producer.send(m1);

      clientSession.start();
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m1", m.getBodyBuffer().readString());

      assertEquals(0, queue.getDeliveringCount());
   }

   @Test
   public void testScheduledMessages() throws Exception {
      final long DELAY_TIME = 100;
      final int MESSAGE_COUNT = 5;
      Queue queue = server.locateQueue(qName1);
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      SimpleString rh = SimpleString.of("SMID1");
      long timeSent = 0;
      for (int i = 0; i < MESSAGE_COUNT; i++) {
         ClientMessage m = createTextMessage(clientSession, "m" + i);
         m.setDurable(true);
         m.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
         timeSent = System.currentTimeMillis();
         m.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, timeSent + (i * DELAY_TIME));
         producer.send(m);
      }

      // allow schedules to elapse so the messages will be delivered to the queue
      Wait.waitFor(() -> queue.getScheduledCount() == 0);

      Wait.assertEquals((long) MESSAGE_COUNT, queue::getMessagesAdded, 5000, 100);
      Wait.assertEquals((long) (MESSAGE_COUNT - 1), queue::getMessagesReplaced, 5000, 100);
      queue.flushExecutor();
      CountDownLatch latch = new CountDownLatch(1);
      queue.flushOnIntermediate(latch::countDown);
      assertTrue(latch.await(10, TimeUnit.SECONDS));

      clientSession.start();
      ClientMessage m = consumer.receive(5000);
      assertNotNull(m);
      assertEquals("m" + (MESSAGE_COUNT - 1), m.getBodyBuffer().readString());
      assertEquals(0, queue.getScheduledCount());
   }

   @Test
   public void testMultipleAcksPersistedCorrectly2() throws Exception {

      Queue queue = server.locateQueue(qName1);
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      SimpleString rh = SimpleString.of("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m1.setDurable(true);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m2.setDurable(true);
      clientSession.start();
      producer.send(m1);
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m1", m.getBodyBuffer().readString());
      producer.send(m2);
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      clientSession.commit();
      assertEquals("m2", m.getBodyBuffer().readString());

      assertEquals(0, queue.getDeliveringCount());
   }

   @Test
   public void testMultipleAcksPersistedCorrectlyInTx() throws Exception {
      ClientProducer producer = clientSessionTxReceives.createProducer(address);
      ClientConsumer consumer = clientSessionTxReceives.createConsumer(qName1);
      SimpleString rh = SimpleString.of("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m1.setDurable(true);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m2.setDurable(true);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m3.setDurable(true);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m4.setDurable(true);
      ClientMessage m5 = createTextMessage(clientSession, "m5");
      m5.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m5.setDurable(true);
      ClientMessage m6 = createTextMessage(clientSession, "m6");
      m6.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m6.setDurable(true);
      clientSessionTxReceives.start();
      producer.send(m1);
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m1", m.getBodyBuffer().readString());
      producer.send(m2);
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m2", m.getBodyBuffer().readString());
      producer.send(m3);
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m3", m.getBodyBuffer().readString());
      producer.send(m4);
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m4", m.getBodyBuffer().readString());
      producer.send(m5);
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m5", m.getBodyBuffer().readString());
      producer.send(m6);
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m6", m.getBodyBuffer().readString());
      clientSessionTxReceives.commit();
   }

   @Test
   public void testLargeMessage() throws Exception {
      ClientProducer producer = clientSessionTxReceives.createProducer(address);
      SimpleString rh = SimpleString.of("SMID1");

      for (int i = 0; i < 5; i++) {
         ClientMessage message = clientSession.createMessage(true);
         message.setBodyInputStream(createFakeLargeStream(300 * 1024));
         message.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
         producer.send(message);
         clientSession.commit();
      }
      ClientConsumer consumer = clientSessionTxReceives.createConsumer(qName1);
      clientSessionTxReceives.start();
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertNull(consumer.receiveImmediate());
      clientSessionTxReceives.commit();
   }

   @Test
   public void testSizeInReplace() throws Exception {
      ClientProducer producer = clientSession.createProducer(address);
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      SimpleString rh = SimpleString.of("SMID1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);

      ClientMessage m2 = clientSession.createMessage(true);
      m2.setBodyInputStream(createFakeLargeStream(10 * 1024));
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);

      Queue queue = server.locateQueue(qName1);
      producer.send(m1);
      Wait.assertEquals(123, () -> queue.getPersistentSize());
      producer.send(m2);
      // encoded size is a little larger than payload
      Wait.assertTrue(() -> queue.getPersistentSize() > 10 * 1024);
      Wait.assertEquals(0L, () -> queue.getDeliveringSize(), 5000, 100);
   }

   @Test
   public void testDeleteReference() throws Exception {
      ClientProducer producer = clientSession.createProducer(address);
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      SimpleString rh = SimpleString.of("SMID1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m1.setBodyInputStream(createFakeLargeStream(2 * 1024));

      ClientMessage m2 = clientSession.createMessage(true);
      m2.setBodyInputStream(createFakeLargeStream(10 * 1024));
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);

      Queue queue = server.locateQueue(qName1);
      producer.send(m1);
      LinkedListIterator<MessageReference> browserIterator = queue.browserIterator();
      // Wait for message delivered to queue
      Wait.assertTrue(() -> browserIterator.hasNext(), 10_000, 2);
      long messageId = browserIterator.next().getMessage().getMessageID();
      browserIterator.close();

      queue.deleteReference(messageId);
      // Wait for delete tx's afterCommit called
      Wait.assertEquals(0L, () -> queue.getDeliveringSize(), 10_000, 2);
      assertEquals(0, queue.getPersistentSize());
      assertTrue(((LastValueQueue)queue).getLastValueKeys().isEmpty());

      producer.send(m2);
      // Wait for message delivered to queue
      Wait.assertTrue(() -> queue.getPersistentSize() > 10 * 1024, 10_000, 2);
      assertEquals(0, queue.getDeliveringSize());
   }

   @Test
   public void testChangeReferencePriority() throws Exception {
      ClientProducer producer = clientSession.createProducer(address);
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      SimpleString rh = SimpleString.of("SMID1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);

      Queue queue = server.locateQueue(qName1);
      producer.send(m1);
      Wait.assertEquals(1, queue::getMessageCount);
      LinkedListIterator<MessageReference> browserIterator = queue.browserIterator();
      // Wait for message delivered to queue
      assertTrue(browserIterator.hasNext());
      long messageId = browserIterator.next().getMessage().getMessageID();
      browserIterator.close();
      long oldSize = queue.getPersistentSize();

      assertTrue(queue.changeReferencePriority(messageId, (byte) 1));
      // Wait for message delivered to queue
      Wait.assertEquals(oldSize, () -> queue.getPersistentSize(), 10_000, 2);
      assertEquals(0, queue.getDeliveringSize());
   }

   @Test
   public void testConcurrency() throws Exception {
      AtomicBoolean cme = new AtomicBoolean(false);

      AtomicBoolean hash = new AtomicBoolean(true);
      Queue lvq = server.locateQueue(qName1);
      Thread hashCodeThread = new Thread(() -> {
         while (hash.get()) {
            try {
               int hashCode = lvq.hashCode();
            } catch (ConcurrentModificationException e) {
               cme.set(true);
               return;
            }
         }
      });
      hashCodeThread.start();

      AtomicBoolean consume = new AtomicBoolean(true);
      ClientConsumer consumer = clientSessionTxReceives.createConsumer(qName1);
      clientSessionTxReceives.start();
      Thread consumerThread = new Thread(() -> {
         while (consume.get()) {
            try {
               ClientMessage m = consumer.receive();
               m.acknowledge();
               clientSessionTxReceives.commit();
            } catch (ActiveMQException e) {
               e.printStackTrace();
               return;
            }
         }
      });
      consumerThread.start();

      ClientProducer producer = clientSessionTxSends.createProducer(address);
      SimpleString lastValue = RandomUtil.randomUUIDSimpleString();
      AtomicBoolean produce = new AtomicBoolean(true);
      Thread producerThread = new Thread(() -> {
         for (int i = 0; !cme.get() && produce.get(); i++) {
            ClientMessage m = createTextMessage(clientSession, "m" + i, false);
            m.putStringProperty(Message.HDR_LAST_VALUE_NAME, lastValue);
            try {
               producer.send(m);
               clientSessionTxSends.commit();
            } catch (ActiveMQException e) {
               e.printStackTrace();
               return;
            }
         }
      });
      producerThread.start();
      producerThread.join(500);

      try {
         assertFalse(cme.get());
      } finally {
         produce.set(false);
         producerThread.join();
         consume.set(false);
         consumerThread.join();
         hash.set(false);
         hashCodeThread.join();
      }
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultNettyConfig(), true));
      // start the server
      server.start();

      server.getAddressSettingsRepository().addMatch(address.toString(), new AddressSettings().setDefaultLastValueQueue(true));
      // then we create a client as normalServer
      ServerLocator locator = createNettyNonHALocator().setBlockOnAcknowledge(true).setAckBatchSize(0);

      ClientSessionFactory sf = createSessionFactory(locator);
      clientSession = addClientSession(sf.createSession(false, true, false));
      clientSessionTxReceives = addClientSession(sf.createSession(false, true, false));
      clientSessionTxSends = addClientSession(sf.createSession(false, false, false));
      clientSession.createQueue(QueueConfiguration.of(qName1).setAddress(address));
   }
}
