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

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.management.UnixOperatingSystemMXBean;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientConsumerInternal;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.LargeServerMessageImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.largemessage.LargeMessageTestBase;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class LargeMessageTest extends LargeMessageTestBase {

   private static final Logger log = Logger.getLogger(LargeMessageTest.class);

   private static final int RECEIVE_WAIT_TIME = 10000;

   protected ServerLocator locator;

   protected boolean isCompressedTest = false;

   private int largeMessageSize;

   protected boolean isNetty() {
      return false;
   }

   public LargeMessageTest(StoreConfiguration.StoreType storeType) {
      super(storeType);
      // The JDBC Large Message store is pretty slow, to speed tests up we only test 5MB large messages
      largeMessageSize = (storeType == StoreConfiguration.StoreType.DATABASE) ? 5 * 1024 : 100 * 1024;
   }

   @Test
   public void testRollbackPartiallyConsumedBuffer() throws Exception {
      for (int i = 0; i < 1; i++) {
         log.debug("#test " + i);
         internalTestRollbackPartiallyConsumedBuffer(false);
         tearDown();
         setUp();
      }
   }

   @Test
   public void testRollbackPartiallyConsumedBufferWithRedeliveryDelay() throws Exception {
      internalTestRollbackPartiallyConsumedBuffer(true);
   }

   private void internalTestRollbackPartiallyConsumedBuffer(final boolean redeliveryDelay) throws Exception {
      final int messageSize = largeMessageSize;

      final ClientSession session;

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      AddressSettings settings = new AddressSettings();
      if (redeliveryDelay) {
         settings.setRedeliveryDelay(100);
         if (locator.isCompressLargeMessage()) {
            locator.setConsumerWindowSize(0);
         }
      }
      locator.setBlockOnNonDurableSend(false).setBlockOnNonDurableSend(false).setBlockOnAcknowledge(false);
      settings.setMaxDeliveryAttempts(-1);

      server.getAddressSettingsRepository().addMatch("#", settings);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      session = sf.createSession(false, false, false);

      session.createQueue(new QueueConfiguration(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      for (int i = 0; i < 20; i++) {
         Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

         clientFile.putIntProperty("value", i);

         producer.send(clientFile);
      }

      session.commit();

      session.start();

      final CountDownLatch latch = new CountDownLatch(1);

      final AtomicInteger errors = new AtomicInteger(0);

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      consumer.setMessageHandler(new MessageHandler() {
         int counter = 0;

         @Override
         public void onMessage(ClientMessage message) {
            message.getBodyBuffer().readByte();
            // System.out.println("message:" + message);
            try {
               if (counter++ < 20) {
                  // System.out.println("Rollback");
                  message.acknowledge();
                  session.rollback();
               } else {
                  message.acknowledge();
                  session.commit();
               }

               if (counter == 40) {
                  latch.countDown();
               }
            } catch (Exception e) {
               latch.countDown();
               e.printStackTrace();
               errors.incrementAndGet();
            }
         }
      });

      assertTrue(latch.await(40, TimeUnit.SECONDS));

      consumer.close();

      session.close();

      validateNoFilesOnLargeDir();
   }

   @Test
   public void testCloseConsumer() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ClientSession session = null;

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(new QueueConfiguration(ADDRESS).setAddress(ADDRESS).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      ClientMessage msg1 = consumer.receive(1000);
      msg1.acknowledge();
      session.commit();
      Assert.assertNotNull(msg1);

      consumer.close();

      try {
         msg1.getBodyBuffer().readByte();
         Assert.fail("Exception was expected");
      } catch (final Exception ignored) {
         // empty on purpose
      }

      session.close();

      validateNoFilesOnLargeDir();
   }

   @Test
   public void testDivertAndExpire() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);
      final String DIVERTED = "diverted";

      ClientSession session = null;

      ActiveMQServer server = createServer(true, isNetty(), storeType);
      server.getConfiguration().setMessageExpiryScanPeriod(100);

      server.start();

      server.createQueue(new QueueConfiguration(DIVERTED));

      server.getAddressSettingsRepository().addMatch(DIVERTED, new AddressSettings().setExpiryDelay(250L).setExpiryAddress(SimpleString.toSimpleString(DIVERTED + "Expiry")).setAutoCreateExpiryResources(true));

      server.deployDivert(new DivertConfiguration().setName("myDivert").setAddress(ADDRESS.toString()).setForwardingAddress(DIVERTED).setExclusive(true));

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(new QueueConfiguration(ADDRESS).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      producer.send(clientFile);

      session.commit();

      session.start();

      Wait.waitFor(() -> server.locateQueue(AddressSettings.DEFAULT_EXPIRY_QUEUE_PREFIX + DIVERTED) != null, 1000, 100);

      ClientConsumer consumer = session.createConsumer(AddressSettings.DEFAULT_EXPIRY_QUEUE_PREFIX + DIVERTED);
      ClientMessage msg1 = consumer.receive(1000);
      msg1.acknowledge();
      session.commit();
      Assert.assertNotNull(msg1);

      consumer.close();

      try {
         msg1.getBodyBuffer().readByte();
         Assert.fail("Exception was expected");
      } catch (final Exception ignored) {
         // empty on purpose
      }

      session.close();

      validateNoFilesOnLargeDir();
   }

   @Test
   public void testDeleteOnNoBinding() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = addClientSession(sf.createSession(false, true, false));

      ClientProducer producer = session.createProducer(UUID.randomUUID().toString());

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      producer.send(clientFile);

      session.close();

      validateNoFilesOnLargeDir();
   }


   @Test
   public void testPendingRecord() throws Exception {

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = addClientSession(sf.createSession(false, true, false));

      session.createQueue(new QueueConfiguration(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      // Send large message which should be dropped and deleted from the filesystem

      producer.send(clientFile);

      validateLargeMessageComplete(server);

      sf.close();

      server.stop();

      server = createServer(true, isNetty(), storeType);

      server.start();

      sf = addSessionFactory(createSessionFactory(locator));

      session = addClientSession(sf.createSession(false, true, false));

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      session.start();

      ClientMessage message = consumer.receiveImmediate();
      Assert.assertNotNull(message);
      for (int i = 0; i < messageSize; i++) {
         assertEquals("position = " + i, getSamplebyte(i), message.getBodyBuffer().readByte());
      }
      message.acknowledge();
      session.commit();

      validateNoFilesOnLargeDir();
   }

   protected void validateLargeMessageComplete(ActiveMQServer server) throws Exception {
      Queue queue = server.locateQueue(ADDRESS);

      Wait.assertEquals(1, queue::getMessageCount);

      LinkedListIterator<MessageReference> browserIterator = queue.browserIterator();

      while (browserIterator.hasNext()) {
         MessageReference ref = browserIterator.next();
         Message message = ref.getMessage();

         Assert.assertNotNull(message);
         Assert.assertTrue(message instanceof LargeServerMessage);

         Assert.assertFalse(((LargeServerMessage)message).hasPendingRecord());
      }
      browserIterator.close();
   }

   @Test
   public void testDeleteOnDrop() throws Exception {
      fillAddress();

      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = addClientSession(sf.createSession(false, true, false));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      // Send large message which should be dropped and deleted from the filesystem

      producer.send(clientFile);

      validateNoFilesOnLargeDir();
   }

   private void fillAddress() throws Exception {

      final int PAGE_MAX = 100 * 1024;
      final int PAGE_SIZE = 10 * 1024;
      final int MESSAGE_SIZE = 1024; // 1k

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      ActiveMQServer server = createServer(true, config, PAGE_SIZE, PAGE_MAX, new HashMap<String, AddressSettings>(), storeType);

      server.start();

      server.getAddressSettingsRepository().getMatch("#").setAddressFullMessagePolicy(AddressFullMessagePolicy.DROP);

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(new QueueConfiguration(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage message;

      byte[] body = new byte[MESSAGE_SIZE];

      ByteBuffer bb = ByteBuffer.wrap(body);

      for (int j = 1; j <= MESSAGE_SIZE; j++) {
         bb.put(getSamplebyte(j));
      }

      for (int i = 0; i < 5000; i++) {
         message = session.createMessage(true);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         message.putIntProperty(new SimpleString("id"), i);

         producer.send(message);
         if (i % 1000 == 0) {
            session.commit();
         }
      }
      session.commit();
      session.close();
   }

   @Test
   public void testLargeBufferTransacted() throws Exception {
      doTestLargeBuffer(true);
   }

   @Test
   public void testLargeBufferNotTransacted() throws Exception {
      doTestLargeBuffer(false);
   }

   public void doTestLargeBuffer(boolean transacted) throws Exception {
      final int journalsize = 100 * 1024;
      final int messageSize = 3 * journalsize;
      // final int messageSize = 5 * 1024;

      ClientSession session = null;

      Configuration config = storeType == StoreConfiguration.StoreType.DATABASE ? createDefaultJDBCConfig(isNetty()) : createDefaultConfig(isNetty());
      config.setJournalFileSize(journalsize).setJournalBufferSize_AIO(10 * 1024).setJournalBufferSize_NIO(10 * 1024);

      ActiveMQServer server = createServer(true, config);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      session = addClientSession(sf.createSession(!transacted, !transacted, 0));

      session.createQueue(new QueueConfiguration(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage clientFile = session.createMessage(true);
      for (int i = 0; i < messageSize; i++) {
         clientFile.getBodyBuffer().writeByte(getSamplebyte(i));
      }

      producer.send(clientFile);

      if (transacted) {
         session.commit();
      }

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      ClientMessage msg1 = consumer.receive(5000);
      assertNotNull(msg1);

      Assert.assertNotNull(msg1);

      for (int i = 0; i < messageSize; i++) {
         // System.out.print(msg1.getBodyBuffer().readByte() + "  ");
         // if (i % 100 == 0) System.out.println();
         assertEquals("position = " + i, getSamplebyte(i), msg1.getBodyBuffer().readByte());
      }

      msg1.acknowledge();

      consumer.close();

      if (transacted) {
         session.commit();
      }

      session.close();

      validateNoFilesOnLargeDir();
   }

   @Test
   public void testDLALargeMessage() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ClientSession session = null;

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(new QueueConfiguration(ADDRESS));
      session.createQueue(new QueueConfiguration(ADDRESS.concat("-2")).setAddress(ADDRESS));

      SimpleString ADDRESS_DLA = ADDRESS.concat("-dla");

      AddressSettings addressSettings = new AddressSettings().setDeadLetterAddress(ADDRESS_DLA).setMaxDeliveryAttempts(1);

      server.getAddressSettingsRepository().addMatch("*", addressSettings);

      session.createQueue(new QueueConfiguration(ADDRESS_DLA));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS_DLA);

      ClientConsumer consumerRollback = session.createConsumer(ADDRESS);
      ClientMessage msg1 = consumerRollback.receive(1000);
      Assert.assertNotNull(msg1);
      msg1.acknowledge();
      session.rollback();
      consumerRollback.close();

      msg1 = consumer.receive(10000);

      Assert.assertNotNull(msg1);

      for (int i = 0; i < messageSize; i++) {
         Assert.assertEquals(ActiveMQTestBase.getSamplebyte(i), msg1.getBodyBuffer().readByte());
      }

      session.close();
      server.stop();

      server = createServer(true, isNetty(), storeType);

      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(false, false, false);

      session.start();

      consumer = session.createConsumer(ADDRESS_DLA);

      msg1 = consumer.receive(10000);

      Assert.assertNotNull(msg1);

      for (int i = 0; i < messageSize; i++) {
         Assert.assertEquals(ActiveMQTestBase.getSamplebyte(i), msg1.getBodyBuffer().readByte());
      }

      msg1.acknowledge();

      session.commit();

      if (storeType != StoreConfiguration.StoreType.DATABASE) {
         validateNoFilesOnLargeDir(server.getConfiguration().getLargeMessagesDirectory(), isCompressedTest ? 0 : 1);
      }

      consumer = session.createConsumer(ADDRESS.concat("-2"));

      msg1 = consumer.receive(10000);

      Assert.assertNotNull(msg1);

      for (int i = 0; i < messageSize; i++) {
         Assert.assertEquals(ActiveMQTestBase.getSamplebyte(i), msg1.getBodyBuffer().readByte());
      }

      msg1.acknowledge();

      session.commit();

      session.close();

      if (storeType != StoreConfiguration.StoreType.DATABASE) {
         validateNoFilesOnLargeDir();
      }
   }

   @Test
   public void testDeliveryCount() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ClientSession session = null;

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      session = sf.createSession(false, false, false);

      session.createQueue(new QueueConfiguration(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);
      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      ClientMessage msg = consumer.receive(10000);
      Assert.assertNotNull(msg);
      msg.acknowledge();
      Assert.assertEquals(1, msg.getDeliveryCount());

      log.debug("body buffer is " + msg.getBodyBuffer());

      for (int i = 0; i < messageSize; i++) {
         Assert.assertEquals(ActiveMQTestBase.getSamplebyte(i), msg.getBodyBuffer().readByte());
      }
      session.rollback();

      session.close();

      session = sf.createSession(false, false, false);
      session.start();

      consumer = session.createConsumer(ADDRESS);
      msg = consumer.receive(10000);
      Assert.assertNotNull(msg);
      msg.acknowledge();
      for (int i = 0; i < messageSize; i++) {
         Assert.assertEquals(ActiveMQTestBase.getSamplebyte(i), msg.getBodyBuffer().readByte());
      }
      Assert.assertEquals(2, msg.getDeliveryCount());
      msg.acknowledge();
      consumer.close();

      session.commit();

      validateNoFilesOnLargeDir();

   }


   @Test
   public void testDLQAlmostLarge() throws Exception {
      SimpleString addressName = SimpleString.toSimpleString("SomewhatHugeNameToBeUsedxxxxxxxxxxxxxxxxxxxiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
      SimpleString dlqName = SimpleString.toSimpleString("DLQ" + addressName.toString());


      ClientSession session = null;

      ActiveMQServer server = createServer(true, isNetty(), storeType);
      server.getConfiguration().setJournalFileSize(1024 * 1024);
      server.getConfiguration().setJournalBufferSize_AIO(100 * 1024);
      server.start();

      server.getAddressSettingsRepository().clear();
      AddressSettings settings = new AddressSettings().setDeadLetterAddress(dlqName).setMaxDeliveryAttempts(1);
      server.getAddressSettingsRepository().addMatch("#", settings);

      createAnycastPair(server, dlqName.toString());
      createAnycastPair(server, addressName.toString());

      locator.setMinLargeMessageSize(1024 * 1024);
      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      session = sf.createSession(false, false, false);

      ClientProducer producer = session.createProducer(addressName);

      ClientMessage clientMessage = session.createMessage(true);
      clientMessage.getBodyBuffer().writeBytes(new byte[100 * 1024 - 900]);
      producer.send(clientMessage);

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(addressName);

      for (int i = 0; i < 2; i++) {
         if (i == 0) {
            ClientMessage msg = consumer.receive(10000);
            Assert.assertNotNull(msg);
            msg.acknowledge();
            session.rollback();
         } else {
            ClientMessage msg = consumer.receiveImmediate();
            Assert.assertNull(msg);
         }
      }

      consumer.close();

      consumer = session.createConsumer(dlqName);
      ClientMessage msg = consumer.receive(1000);
      Assert.assertNotNull(msg);

   }

   @Test
   public void testDLAOnExpiryNonDurableMessage() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ClientSession session = null;

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      SimpleString ADDRESS_DLA = ADDRESS.concat("-dla");
      SimpleString ADDRESS_EXPIRY = ADDRESS.concat("-expiry");

      AddressSettings addressSettings = new AddressSettings().setDeadLetterAddress(ADDRESS_DLA).setExpiryAddress(ADDRESS_EXPIRY).setMaxDeliveryAttempts(1);

      server.getAddressSettingsRepository().addMatch("*", addressSettings);

      session = sf.createSession(false, false, false);

      session.createQueue(new QueueConfiguration(ADDRESS));

      session.createQueue(new QueueConfiguration(ADDRESS_DLA));
      session.createQueue(new QueueConfiguration(ADDRESS_EXPIRY));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, false);
      clientFile.setExpiration(System.currentTimeMillis());

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumerExpired = session.createConsumer(ADDRESS);
      // to kick expiry quicker than waiting reaper thread
      Assert.assertNull(consumerExpired.receiveImmediate());
      consumerExpired.close();

      ClientConsumer consumerExpiry = session.createConsumer(ADDRESS_EXPIRY);

      ClientMessage msg1 = consumerExpiry.receive(5000);
      assertTrue(msg1.isLargeMessage());
      Assert.assertNotNull(msg1);
      msg1.acknowledge();

      for (int j = 0; j < messageSize; j++) {
         Assert.assertEquals(ActiveMQTestBase.getSamplebyte(j), msg1.getBodyBuffer().readByte());
      }

      session.rollback();

      consumerExpiry.close();

      for (int i = 0; i < 10; i++) {

         consumerExpiry = session.createConsumer(ADDRESS_DLA);

         msg1 = consumerExpiry.receive(5000);
         Assert.assertNotNull(msg1);
         msg1.acknowledge();

         for (int j = 0; j < messageSize; j++) {
            Assert.assertEquals(ActiveMQTestBase.getSamplebyte(j), msg1.getBodyBuffer().readByte());
         }

         session.rollback();

         consumerExpiry.close();
      }

      session.close();

      session = sf.createSession(false, false, false);

      session.start();

      consumerExpiry = session.createConsumer(ADDRESS_DLA);

      msg1 = consumerExpiry.receive(5000);

      Assert.assertNotNull(msg1);

      msg1.acknowledge();

      for (int i = 0; i < messageSize; i++) {
         Assert.assertEquals(ActiveMQTestBase.getSamplebyte(i), msg1.getBodyBuffer().readByte());
      }

      session.commit();

      consumerExpiry.close();

      session.commit();

      session.close();

      server.stop();

      server.start();

      validateNoFilesOnLargeDir();

   }

   @Test
   public void testDLAOnExpiry() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ClientSession session = null;

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      SimpleString ADDRESS_DLA = ADDRESS.concat("-dla");
      SimpleString ADDRESS_EXPIRY = ADDRESS.concat("-expiry");

      AddressSettings addressSettings = new AddressSettings().setDeadLetterAddress(ADDRESS_DLA).setExpiryAddress(ADDRESS_EXPIRY).setMaxDeliveryAttempts(1);

      server.getAddressSettingsRepository().addMatch("*", addressSettings);

      session = sf.createSession(false, false, false);

      session.createQueue(new QueueConfiguration(ADDRESS));

      session.createQueue(new QueueConfiguration(ADDRESS_DLA));
      session.createQueue(new QueueConfiguration(ADDRESS_EXPIRY));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);
      clientFile.setExpiration(System.currentTimeMillis());

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumerExpired = session.createConsumer(ADDRESS);
      // to kick expiry quicker than waiting reaper thread
      Assert.assertNull(consumerExpired.receiveImmediate());
      consumerExpired.close();

      ClientConsumer consumerExpiry = session.createConsumer(ADDRESS_EXPIRY);

      ClientMessage msg1 = consumerExpiry.receive(5000);
      Assert.assertNotNull(msg1);
      msg1.acknowledge();

      for (int j = 0; j < messageSize; j++) {
         Assert.assertEquals(ActiveMQTestBase.getSamplebyte(j), msg1.getBodyBuffer().readByte());
      }

      session.rollback();

      consumerExpiry.close();

      for (int i = 0; i < 10; i++) {
         consumerExpiry = session.createConsumer(ADDRESS_DLA);

         msg1 = consumerExpiry.receive(5000);
         Assert.assertNotNull(msg1);
         msg1.acknowledge();

         for (int j = 0; j < messageSize; j++) {
            Assert.assertEquals(ActiveMQTestBase.getSamplebyte(j), msg1.getBodyBuffer().readByte());
         }

         session.rollback();

         consumerExpiry.close();
      }

      session.close();
      server.stop();

      server = createServer(true, isNetty(), storeType);

      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(false, false, false);

      session.start();

      consumerExpiry = session.createConsumer(ADDRESS_DLA);

      msg1 = consumerExpiry.receive(5000);
      Assert.assertNotNull(msg1);
      msg1.acknowledge();

      for (int i = 0; i < messageSize; i++) {
         Assert.assertEquals(ActiveMQTestBase.getSamplebyte(i), msg1.getBodyBuffer().readByte());
      }

      session.commit();

      consumerExpiry.close();

      session.commit();

      session.close();

      validateNoFilesOnLargeDir();
   }

   @Test
   public void testExpiryLargeMessage() throws Exception {
      final int messageSize = 3 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE;

      ClientSession session = null;

      try {
         ActiveMQServer server = createServer(true, isNetty(), storeType);

         server.start();

         SimpleString ADDRESS_EXPIRY = ADDRESS.concat("-expiry");

         AddressSettings addressSettings = new AddressSettings().setExpiryAddress(ADDRESS_EXPIRY);

         server.getAddressSettingsRepository().addMatch("*", addressSettings);

         ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

         session = sf.createSession(false, false, false);

         session.createQueue(new QueueConfiguration(ADDRESS));

         session.createQueue(new QueueConfiguration(ADDRESS_EXPIRY));

         ClientProducer producer = session.createProducer(ADDRESS);

         Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

         clientFile.setExpiration(System.currentTimeMillis());

         producer.send(clientFile);

         session.commit();

         session.start();

         ClientConsumer consumer = session.createConsumer(ADDRESS_EXPIRY);

         // Creating a consumer just to make the expiry process go faster and not have to wait for the reaper
         ClientConsumer consumer2 = session.createConsumer(ADDRESS);
         Assert.assertNull(consumer2.receiveImmediate());

         ClientMessage msg1 = consumer.receive(50000);

         Assert.assertNotNull(msg1);

         for (int i = 0; i < messageSize; i++) {
            Assert.assertEquals(ActiveMQTestBase.getSamplebyte(i), msg1.getBodyBuffer().readByte());
         }

         session.close();
         server.stop();

         server = createServer(true, isNetty(), storeType);

         server.start();

         sf = createSessionFactory(locator);

         session = sf.createSession(false, false, false);

         session.start();

         consumer = session.createConsumer(ADDRESS_EXPIRY);

         msg1 = consumer.receive(10000);

         Assert.assertNotNull(msg1);

         for (int i = 0; i < messageSize; i++) {
            Assert.assertEquals(ActiveMQTestBase.getSamplebyte(i), msg1.getBodyBuffer().readByte());
         }

         msg1.acknowledge();

         session.commit();

         session.close();

         validateNoFilesOnLargeDir();
      } finally {
         try {
            session.close();
         } catch (Throwable ignored) {
         }
      }
   }

   @Test
   public void testSentWithDuplicateIDBridge() throws Exception {
      internalTestSentWithDuplicateID(true);
   }

   @Test
   public void testSentWithDuplicateID() throws Exception {
      internalTestSentWithDuplicateID(false);
   }

   private void internalTestSentWithDuplicateID(final boolean isSimulateBridge) throws Exception {
      final int messageSize = 3 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE;

      ClientSession session = null;

      try {
         ActiveMQServer server = createServer(true, isNetty(), storeType);

         server.start();

         ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

         session = sf.createSession(true, true, 0);

         session.createQueue(new QueueConfiguration(ADDRESS));

         ClientProducer producer = session.createProducer(ADDRESS);

         String someDuplicateInfo = "Anything";

         for (int i = 0; i < 10; i++) {
            Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

            if (isSimulateBridge) {
               clientFile.putBytesProperty(Message.HDR_BRIDGE_DUPLICATE_ID, someDuplicateInfo.getBytes());
            } else {
               clientFile.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, someDuplicateInfo.getBytes());
            }

            producer.send(clientFile);
         }

         ClientConsumer consumer = session.createConsumer(ADDRESS);

         session.start();

         ClientMessage msg = consumer.receive(10000);

         for (int i = 0; i < messageSize; i++) {
            assertEquals(getSamplebyte(i), msg.getBodyBuffer().readByte());
         }

         assertNotNull(msg);

         msg.acknowledge();

         assertNull(consumer.receiveImmediate());

         session.commit();

         validateNoFilesOnLargeDir();

      } finally {
         try {
            session.close();
         } catch (Throwable ignored) {
         }
      }
   }

   @Test
   public void testResendSmallStreamMessage() throws Exception {
      internalTestResendMessage(50000);
   }

   @Test
   public void testResendLargeStreamMessage() throws Exception {
      internalTestResendMessage(150 * 1024);
   }

   public void internalTestResendMessage(final long messageSize) throws Exception {
      clearDataRecreateServerDirs();
      ClientSession session = null;

      try {
         ActiveMQServer server = createServer(true, isNetty(), storeType);

         server.start();

         ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

         session = sf.createSession(false, false, false);

         session.createQueue(new QueueConfiguration(ADDRESS));

         SimpleString ADDRESS2 = ADDRESS.concat("-2");

         session.createQueue(new QueueConfiguration(ADDRESS2));

         ClientProducer producer = session.createProducer(ADDRESS);

         ClientProducer producer2 = session.createProducer(ADDRESS2);

         Message clientFile = createLargeClientMessageStreaming(session, messageSize, false);

         producer.send(clientFile);

         session.commit();

         session.start();

         ClientConsumer consumer = session.createConsumer(ADDRESS);
         ClientConsumer consumer2 = session.createConsumer(ADDRESS2);

         ClientMessage msg1 = consumer.receive(10000);
         msg1.acknowledge();

         producer2.send(msg1);

         session.commit();

         ClientMessage msg2 = consumer2.receive(10000);

         Assert.assertNotNull(msg2);

         msg2.acknowledge();

         session.commit();

         Assert.assertEquals(messageSize, msg2.getBodySize());

         compareString(messageSize, msg2);

         session.close();

         validateNoFilesOnLargeDir();
      } finally {
         try {
            session.close();
         } catch (Throwable ignored) {
         }
      }
   }

   @Test
   public void testResendCachedSmallStreamMessage() throws Exception {
      internalTestResendMessage(50000);
   }

   @Test
   public void testResendCachedLargeStreamMessage() throws Exception {
      internalTestCachedResendMessage(150 * 1024);
   }

   public void internalTestCachedResendMessage(final long messageSize) throws Exception {
      ClientSession session = null;

      try {
         ActiveMQServer server = createServer(true, isNetty(), storeType);

         server.start();

         locator.setMinLargeMessageSize(200).setCacheLargeMessagesClient(true);

         ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

         session = sf.createSession(false, false, false);

         session.createQueue(new QueueConfiguration(ADDRESS));

         ClientProducer producer = session.createProducer(ADDRESS);

         Message originalMsg = createLargeClientMessageStreaming(session, messageSize, false);

         producer.send(originalMsg);

         session.commit();

         ClientConsumer consumer = session.createConsumer(ADDRESS);

         session.start();

         ClientMessage msgReceived = consumer.receive(10000);
         msgReceived.acknowledge();

         session.commit();

         compareString(messageSize, msgReceived);

         msgReceived.getBodyBuffer().readerIndex(0);

         producer.send(msgReceived);

         session.commit();

         ClientMessage msgReceived2 = consumer.receive(10000);

         msgReceived2.acknowledge();

         compareString(messageSize, msgReceived2);

         session.commit();

         session.close();

         validateNoFilesOnLargeDir();
      } finally {
         try {
            session.close();
         } catch (Throwable ignored) {
         }
      }
   }

   /**
    * @param messageSize
    * @param msg
    */
   private void compareString(final long messageSize, ClientMessage msg) {
      assertNotNull(msg);
      for (long i = 0; i < messageSize; i++) {
         Assert.assertEquals("position " + i, ActiveMQTestBase.getSamplebyte(i), msg.getBodyBuffer().readByte());
      }
   }

   @Test
   public void testFilePersistenceOneHugeMessage() throws Exception {
      testChunks(false, false, false, true, true, false, false, false, false, 1, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0, 10 * 1024 * 1024, 1024 * 1024);
   }

   @Test
   public void testFilePersistenceOneMessageStreaming() throws Exception {
      testChunks(false, false, false, true, true, false, false, false, false, 1, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceSmallMessageStreaming() throws Exception {
      testChunks(false, false, false, true, true, false, false, false, false, 100, 1024, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceOneHugeMessageConsumer() throws Exception {
      testChunks(false, false, false, true, true, false, false, false, true, 1, largeMessageSize, 120000, 0, 10 * 1024 * 1024, 1024 * 1024);
   }

   @Test
   public void testFilePersistence() throws Exception {
      testChunks(false, false, true, false, true, false, false, true, false, 100, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceConsumer() throws Exception {
      testChunks(false, false, true, false, true, false, false, true, true, 2, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceXA() throws Exception {
      testChunks(true, false, true, false, true, false, false, true, false, 100, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceXAStream() throws Exception {
      testChunks(true, false, false, true, true, false, false, false, false, 1, 1024 * 1024, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceXAStreamRestart() throws Exception {
      testChunks(true, true, false, true, true, false, false, false, false, 1, 1024 * 1024, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceXAConsumer() throws Exception {
      testChunks(true, false, true, false, true, false, false, true, true, 100, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceXAConsumerRestart() throws Exception {
      testChunks(true, true, true, false, true, false, false, true, true, 100, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceBlocked() throws Exception {
      testChunks(false, false, true, false, true, false, true, true, false, 100, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceBlockedConsumer() throws Exception {
      testChunks(false, false, true, false, true, false, true, true, true, 100, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceBlockedXA() throws Exception {
      testChunks(true, false, true, false, true, false, true, true, false, 100, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceBlockedXAConsumer() throws Exception {
      testChunks(true, false, true, false, true, false, true, true, true, 100, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceBlockedPreACK() throws Exception {
      testChunks(false, false, true, false, true, true, true, true, false, 1, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceBlockedPreACKConsumer() throws Exception {
      testChunks(false, false, true, false, true, true, true, true, true, 1, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceBlockedPreACKXA() throws Exception {
      testChunks(true, false, true, false, true, true, true, true, false, 100, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceBlockedPreACKXARestart() throws Exception {
      testChunks(true, true, true, false, true, true, true, true, false, 100, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceBlockedPreACKXAConsumer() throws Exception {
      testChunks(true, false, true, false, true, true, true, true, true, 100, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceBlockedPreACKXAConsumerRestart() throws Exception {
      testChunks(true, true, true, false, true, true, true, true, true, 100, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testFilePersistenceDelayed() throws Exception {
      testChunks(false, false, true, false, true, false, false, false, false, 1, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 200);
   }

   @Test
   public void testFilePersistenceDelayedConsumer() throws Exception {
      testChunks(false, false, true, false, true, false, false, false, true, 1, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 200);
   }

   @Test
   public void testFilePersistenceDelayedXA() throws Exception {
      testChunks(true, false, true, false, true, false, false, false, false, 1, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 200);
   }

   @Test
   public void testFilePersistenceDelayedXAConsumer() throws Exception {
      testChunks(true, false, true, false, true, false, false, false, true, 1, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 200);
   }

   @Test
   public void testNullPersistence() throws Exception {
      testChunks(false, false, true, false, false, false, false, true, true, 1, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testNullPersistenceConsumer() throws Exception {
      testChunks(false, false, true, false, false, false, false, true, true, 1, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testNullPersistenceXA() throws Exception {
      testChunks(true, false, true, false, false, false, false, true, false, 1, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testNullPersistenceXAConsumer() throws Exception {
      testChunks(true, false, true, false, false, false, false, true, true, 1, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testNullPersistenceDelayed() throws Exception {
      testChunks(false, false, true, false, false, false, false, false, false, 100, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @Test
   public void testNullPersistenceDelayedConsumer() throws Exception {
      testChunks(false, false, true, false, false, false, false, false, true, 100, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @Test
   public void testNullPersistenceDelayedXA() throws Exception {
      testChunks(true, false, true, false, false, false, false, false, false, 100, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @Test
   public void testNullPersistenceDelayedXAConsumer() throws Exception {
      testChunks(true, false, true, false, false, false, false, false, true, 100, largeMessageSize, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @Test
   public void testPageOnLargeMessage() throws Exception {
      testPageOnLargeMessage(true, false);
   }

   @Test
   public void testSendSmallMessageXA() throws Exception {
      testChunks(true, false, true, false, true, false, false, true, false, 100, 4, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testSendSmallMessageXAConsumer() throws Exception {
      testChunks(true, false, true, false, true, false, false, true, true, 100, 4, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testSendSmallMessageNullPersistenceXA() throws Exception {
      testChunks(true, false, true, false, false, false, false, true, false, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testSendSmallMessageNullPersistenceXAConsumer() throws Exception {
      testChunks(true, false, true, false, false, false, false, true, true, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testSendRegularMessageNullPersistenceDelayed() throws Exception {
      testChunks(false, false, true, false, false, false, false, false, false, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @Test
   public void testSendRegularMessageNullPersistenceDelayedConsumer() throws Exception {
      testChunks(false, false, true, false, false, false, false, false, true, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @Test
   public void testSendRegularMessageNullPersistenceDelayedXA() throws Exception {
      testChunks(true, false, true, false, false, false, false, false, false, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @Test
   public void testSendRegularMessageNullPersistenceDelayedXAConsumer() throws Exception {
      testChunks(true, false, true, false, false, false, false, false, true, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @Test
   public void testSendRegularMessagePersistence() throws Exception {
      testChunks(false, false, true, false, true, false, false, true, false, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testSendRegularMessagePersistenceConsumer() throws Exception {
      testChunks(false, false, true, false, true, false, false, true, true, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testSendRegularMessagePersistenceXA() throws Exception {
      testChunks(true, false, true, false, true, false, false, true, false, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testSendRegularMessagePersistenceXAConsumer() throws Exception {
      testChunks(true, false, true, false, true, false, false, true, true, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @Test
   public void testSendRegularMessagePersistenceDelayed() throws Exception {
      testChunks(false, false, true, false, true, false, false, false, false, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @Test
   public void testSendRegularMessagePersistenceDelayedConsumer() throws Exception {
      testChunks(false, false, true, false, true, false, false, false, true, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @Test
   public void testSendRegularMessagePersistenceDelayedXA() throws Exception {
      testChunks(false, false, true, false, true, false, false, false, false, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @Test
   public void testSendRegularMessagePersistenceDelayedXAConsumer() throws Exception {
      testChunks(false, false, true, false, true, false, false, false, true, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @Test
   public void testTwoBindingsTwoStartedConsumers() throws Exception {
      // there are two bindings.. one is ACKed, the other is not, the server is restarted
      // The other binding is acked... The file must be deleted

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      SimpleString[] queue = new SimpleString[]{new SimpleString("queue1"), new SimpleString("queue2")};

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

      session.createQueue(new QueueConfiguration(queue[0]).setAddress(ADDRESS));
      session.createQueue(new QueueConfiguration(queue[1]).setAddress(ADDRESS));

      int numberOfBytes = 400000;

      Message clientFile = createLargeClientMessageStreaming(session, numberOfBytes);

      ClientProducer producer = session.createProducer(ADDRESS);

      session.start();

      producer.send(clientFile);

      producer.close();

      ClientConsumer consumer = session.createConsumer(queue[1]);
      ClientMessage msg = consumer.receive(LargeMessageTest.RECEIVE_WAIT_TIME);
      msg.getBodyBuffer().readByte();
      Assert.assertNull(consumer.receiveImmediate());
      Assert.assertNotNull(msg);

      msg.acknowledge();
      consumer.close();

      log.debug("Stopping");

      session.stop();

      ClientConsumer consumer1 = session.createConsumer(queue[0]);

      session.start();

      msg = consumer1.receive(LargeMessageTest.RECEIVE_WAIT_TIME);
      Assert.assertNotNull(msg);
      msg.acknowledge();
      consumer1.close();

      session.commit();

      session.close();

      validateNoFilesOnLargeDir();
   }

   @Test
   public void testTwoBindingsAndRestart() throws Exception {
      testTwoBindings(true);
   }

   @Test
   public void testTwoBindingsNoRestart() throws Exception {
      testTwoBindings(false);
   }

   public void testTwoBindings(final boolean restart) throws Exception {
      // there are two bindings.. one is ACKed, the other is not, the server is restarted
      // The other binding is acked... The file must be deleted
      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      SimpleString[] queue = new SimpleString[]{new SimpleString("queue1"), new SimpleString("queue2")};

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

      session.createQueue(new QueueConfiguration(queue[0]).setAddress(ADDRESS));
      session.createQueue(new QueueConfiguration(queue[1]).setAddress(ADDRESS));

      int numberOfBytes = 400000;

      Message clientFile = createLargeClientMessageStreaming(session, numberOfBytes);

      ClientProducer producer = session.createProducer(ADDRESS);
      producer.send(clientFile);

      producer.close();

      readMessage(session, queue[1], numberOfBytes);

      if (restart) {
         session.close();

         server.stop();

         server = createServer(true, isNetty(), storeType);

         server.start();

         sf = createSessionFactory(locator);

         session = sf.createSession(null, null, false, true, true, false, 0);
      }

      readMessage(session, queue[0], numberOfBytes);

      session.close();

      validateNoFilesOnLargeDir();
   }

   @Test
   public void testSendRollbackXADurable() throws Exception {
      internalTestSendRollback(true, true);
   }

   @Test
   public void testSendRollbackXANonDurable() throws Exception {
      internalTestSendRollback(true, false);
   }

   @Test
   public void testSendRollbackDurable() throws Exception {
      internalTestSendRollback(false, true);
   }

   @Test
   public void testSendRollbackNonDurable() throws Exception {
      internalTestSendRollback(false, false);
   }

   private void internalTestSendRollback(final boolean isXA, final boolean durable) throws Exception {
      ClientSession session = null;

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      session = sf.createSession(isXA, false, false);

      session.createQueue(new QueueConfiguration(ADDRESS));

      Xid xid = null;

      if (isXA) {
         xid = RandomUtil.randomXid();
         session.start(xid, XAResource.TMNOFLAGS);
      }

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, 50000, durable);

      for (int i = 0; i < 1; i++) {
         producer.send(clientFile);
      }

      if (isXA) {
         session.end(xid, XAResource.TMSUCCESS);
         session.prepare(xid);
         session.close();
         server.stop();
         server.start();
         sf = createSessionFactory(locator);
         session = sf.createSession(isXA, false, false);

         session.rollback(xid);
      } else {
         session.rollback();
      }

      session.close();

      validateNoFilesOnLargeDir();
   }

   @Test
   public void testSimpleRollback() throws Exception {
      simpleRollbackInternalTest(false);
   }

   @Test
   public void testSimpleRollbackXA() throws Exception {
      simpleRollbackInternalTest(true);
   }

   public void simpleRollbackInternalTest(final boolean isXA) throws Exception {
      // there are two bindings.. one is ACKed, the other is not, the server is restarted
      // The other binding is acked... The file must be deleted
      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(isXA, false, false);

      Xid xid = null;

      if (isXA) {
         xid = newXID();
         session.start(xid, XAResource.TMNOFLAGS);
      }

      session.createQueue(new QueueConfiguration(ADDRESS));

      int numberOfBytes = 200000;

      session.start();

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      for (int n = 0; n < 10; n++) {
         Message clientFile = createLargeClientMessageStreaming(session, numberOfBytes, n % 2 == 0);

         producer.send(clientFile);

         Assert.assertNull(consumer.receiveImmediate());

         if (isXA) {
            session.end(xid, XAResource.TMSUCCESS);
            session.rollback(xid);
            xid = newXID();
            session.start(xid, XAResource.TMNOFLAGS);
         } else {
            session.rollback();
         }

         clientFile = createLargeClientMessageStreaming(session, numberOfBytes, n % 2 == 0);

         producer.send(clientFile);

         Assert.assertNull(consumer.receiveImmediate());

         if (isXA) {
            session.end(xid, XAResource.TMSUCCESS);
            session.commit(xid, true);
            xid = newXID();
            session.start(xid, XAResource.TMNOFLAGS);
         } else {
            session.commit();
         }

         for (int i = 0; i < 2; i++) {

            ClientMessage clientMessage = consumer.receive(5000);

            Assert.assertNotNull(clientMessage);

            Assert.assertEquals(numberOfBytes, clientMessage.getBodySize());

            clientMessage.acknowledge();

            if (isXA) {
               if (i == 0) {
                  session.end(xid, XAResource.TMSUCCESS);
                  session.prepare(xid);
                  session.rollback(xid);
                  xid = newXID();
                  session.start(xid, XAResource.TMNOFLAGS);
               } else {
                  session.end(xid, XAResource.TMSUCCESS);
                  session.commit(xid, true);
                  xid = newXID();
                  session.start(xid, XAResource.TMNOFLAGS);
               }
            } else {
               if (i == 0) {
                  session.rollback();
               } else {
                  session.commit();
               }
            }
         }
      }

      session.close();

      validateNoFilesOnLargeDir();
   }

   @Test
   public void testBufferMultipleLargeMessages() throws Exception {
      ClientSession session = null;
      ActiveMQServer server = null;

      final int SIZE = 10 * 1024;
      final int NUMBER_OF_MESSAGES = 30;
      try {

         server = createServer(true, isNetty(), storeType);

         server.start();

         locator.setMinLargeMessageSize(1024).setConsumerWindowSize(1024 * 1024);

         ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

         session = sf.createSession(null, null, false, false, false, false, 0);

         session.createQueue(new QueueConfiguration(ADDRESS));

         ClientProducer producer = session.createProducer(ADDRESS);

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            ClientMessage clientFile = session.createMessage(true);
            clientFile.setBodyInputStream(ActiveMQTestBase.createFakeLargeStream(SIZE));
            producer.send(clientFile);

         }
         session.commit();
         producer.close();

         session.start();

         ClientConsumerInternal consumer = (ClientConsumerInternal) session.createConsumer(ADDRESS);

         Wait.waitFor(() -> consumer.getBufferSize() >= NUMBER_OF_MESSAGES, 30_000, 100);
         Assert.assertEquals(NUMBER_OF_MESSAGES, consumer.getBufferSize());

         // Reads the messages, rollback.. read them again
         for (int trans = 0; trans < 2; trans++) {

            for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
               ClientMessage msg = consumer.receive(10000);
               Assert.assertNotNull(msg);

               // it will ignore the buffer (not read it) on the first try
               if (trans == 0) {
                  for (int byteRead = 0; byteRead < SIZE; byteRead++) {
                     Assert.assertEquals(ActiveMQTestBase.getSamplebyte(byteRead), msg.getBodyBuffer().readByte());
                  }
               }

               msg.acknowledge();
            }
            if (trans == 0) {
               session.rollback();
            } else {
               session.commit();
            }
         }

         Assert.assertEquals(0, ((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
         Assert.assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable())));

      } finally {
         try {
            session.close();
         } catch (Throwable ignored) {
         }

         try {
            server.stop();
         } catch (Throwable ignored) {
         }
      }
   }

   @Test
   public void testReceiveMultipleMessages() throws Exception {
      ClientSession session = null;
      ActiveMQServer server = null;

      final int SIZE = 10 * 1024;
      final int NUMBER_OF_MESSAGES = 100;
      try {

         server = createServer(true, isNetty(), storeType);

         server.start();

         locator.setMinLargeMessageSize(1024).setConsumerWindowSize(1024 * 1024).setBlockOnDurableSend(false).setBlockOnNonDurableSend(false).setBlockOnAcknowledge(false);

         ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

         session = sf.createSession(null, null, false, false, false, false, 0);

         session.createQueue(new QueueConfiguration(ADDRESS));

         ClientProducer producer = session.createProducer(ADDRESS);

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            ClientMessage clientFile = session.createMessage(true);
            clientFile.setBodyInputStream(ActiveMQTestBase.createFakeLargeStream(SIZE));
            producer.send(clientFile);

         }
         session.commit();
         producer.close();

         session.start();

         // Reads the messages, rollback.. read them again
         for (int trans = 0; trans < 2; trans++) {

            ClientConsumerInternal consumer = (ClientConsumerInternal) session.createConsumer(ADDRESS);

            Assert.assertTrue(Wait.waitFor(() -> consumer.getBufferSize() >= 5, 30_000, 100));


            for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
               ClientMessage msg = consumer.receive(10000);
               Assert.assertNotNull(msg);

               // it will ignore the buffer (not read it) on the first try
               if (trans == 0) {
                  for (int byteRead = 0; byteRead < SIZE; byteRead++) {
                     Assert.assertEquals(ActiveMQTestBase.getSamplebyte(byteRead), msg.getBodyBuffer().readByte());
                  }
               }

               msg.acknowledge();
            }
            if (trans == 0) {
               session.rollback();
            } else {
               session.commit();
            }

            consumer.close();
         }

         Assert.assertEquals(0, ((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
         Assert.assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable())));

      } finally {
         try {
            session.close();
         } catch (Throwable ignored) {
         }

         try {
            server.stop();
         } catch (Throwable ignored) {
         }
      }
   }

   // JBPAPP-6237
   @Test
   public void testPageOnLargeMessageMultipleQueues() throws Exception {
      Configuration config = createDefaultConfig(isNetty());

      final int PAGE_MAX = 20 * 1024;

      final int PAGE_SIZE = 10 * 1024;

      HashMap<String, AddressSettings> map = new HashMap<>();

      AddressSettings value = new AddressSettings();
      map.put(ADDRESS.toString(), value);
      ActiveMQServer server = createServer(true, config, PAGE_SIZE, PAGE_MAX, map, storeType);
      server.start();

      final int numberOfBytes = 1024;

      final int numberOfBytesBigMessage = 400000;

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

      session.createQueue(new QueueConfiguration(ADDRESS.concat("-0")).setAddress(ADDRESS));
      session.createQueue(new QueueConfiguration(ADDRESS.concat("-1")).setAddress(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage message = null;

      for (int i = 0; i < 100; i++) {
         message = session.createMessage(true);

         message.getBodyBuffer().writerIndex(0);

         message.getBodyBuffer().writeBytes(new byte[numberOfBytes]);

         for (int j = 1; j <= numberOfBytes; j++) {
            message.getBodyBuffer().writeInt(j);
         }

         producer.send(message);
      }

      ClientMessage clientFile = createLargeClientMessageStreaming(session, numberOfBytesBigMessage);
      clientFile.putBooleanProperty("TestLarge", true);
      producer.send(clientFile);

      for (int i = 0; i < 100; i++) {
         message = session.createMessage(true);

         message.getBodyBuffer().writeBytes(new byte[numberOfBytes]);

         producer.send(message);
      }

      session.close();

      server.stop();

      server = createServer(true, config, PAGE_SIZE, PAGE_MAX, map, storeType);
      server.start();

      sf = createSessionFactory(locator);

      for (int ad = 0; ad < 2; ad++) {
         session = sf.createSession(false, false, false);

         ClientConsumer consumer = session.createConsumer(ADDRESS.concat("-" + ad));

         session.start();

         for (int i = 0; i < 100; i++) {
            ClientMessage message2 = consumer.receive(LargeMessageTest.RECEIVE_WAIT_TIME);

            Assert.assertNotNull(message2);

            message2.acknowledge();

            Assert.assertNotNull(message2);
         }

         session.commit();

         for (int i = 0; i < 5; i++) {
            ClientMessage messageLarge = consumer.receive(RECEIVE_WAIT_TIME);

            assertTrue(messageLarge.getBooleanProperty("TestLarge"));

            Assert.assertNotNull(messageLarge);

            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            messageLarge.acknowledge();
            messageLarge.saveToOutputStream(bout);
            byte[] body = bout.toByteArray();
            assertEquals(numberOfBytesBigMessage, body.length);
            for (int bi = 0; bi < body.length; bi++) {
               assertEquals(getSamplebyte(bi), body[bi]);
            }

            if (i < 4)
               session.rollback();
            else
               session.commit();
         }

         for (int i = 0; i < 100; i++) {
            ClientMessage message2 = consumer.receive(LargeMessageTest.RECEIVE_WAIT_TIME);

            Assert.assertNotNull(message2);

            message2.acknowledge();

            Assert.assertNotNull(message2);
         }

         session.commit();

         consumer.close();

         session.close();
      }
   }

   // JBPAPP-6237
   @Test
   public void testPageOnLargeMessageMultipleQueues2() throws Exception {
      Configuration config = createDefaultConfig(isNetty());

      final int PAGE_MAX = 20 * 1024;

      final int PAGE_SIZE = 10 * 1024;

      HashMap<String, AddressSettings> map = new HashMap<>();

      AddressSettings value = new AddressSettings();
      map.put(ADDRESS.toString(), value);
      ActiveMQServer server = createServer(true, config, PAGE_SIZE, PAGE_MAX, map, storeType);
      server.start();

      final int numberOfBytes = 1024;

      final int numberOfBytesBigMessage = 400000;

      locator.setBlockOnNonDurableSend(false).setBlockOnDurableSend(false).setBlockOnAcknowledge(false).setCompressLargeMessage(true);

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(new QueueConfiguration(ADDRESS.concat("-0")).setAddress(ADDRESS));
      session.createQueue(new QueueConfiguration(ADDRESS.concat("-1")).setAddress(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);
      int msgId = 0;

      for (int i = 0; i < 100; i++) {
         ClientMessage message = session.createMessage(true);

         message.putIntProperty("msgID", msgId++);

         message.putBooleanProperty("TestLarge", false);

         message.getBodyBuffer().writerIndex(0);

         message.getBodyBuffer().writeBytes(new byte[numberOfBytes]);

         for (int j = 1; j <= numberOfBytes; j++) {
            message.getBodyBuffer().writeInt(j);
         }

         producer.send(message);
      }

      for (int i = 0; i < 10; i++) {
         ClientMessage clientFile = createLargeClientMessageStreaming(session, numberOfBytesBigMessage);
         clientFile.putBooleanProperty("TestLarge", true);
         producer.send(clientFile);
      }

      session.close();

      for (int ad = 0; ad < 2; ad++) {
         session = sf.createSession(false, false, false);

         ClientConsumer consumer = session.createConsumer(ADDRESS.concat("-" + ad));

         session.start();

         for (int received = 0; received < 5; received++) {
            for (int i = 0; i < 100; i++) {
               ClientMessage message2 = consumer.receive(LargeMessageTest.RECEIVE_WAIT_TIME);

               Assert.assertNotNull(message2);

               assertFalse(message2.getBooleanProperty("TestLarge"));

               message2.acknowledge();

               Assert.assertNotNull(message2);
            }

            for (int i = 0; i < 10; i++) {
               ClientMessage messageLarge = consumer.receive(RECEIVE_WAIT_TIME);

               Assert.assertNotNull(messageLarge);

               assertTrue(messageLarge.getBooleanProperty("TestLarge"));

               ByteArrayOutputStream bout = new ByteArrayOutputStream();

               messageLarge.acknowledge();

               messageLarge.saveToOutputStream(bout);
               byte[] body = bout.toByteArray();
               assertEquals(numberOfBytesBigMessage, body.length);
            }

            session.rollback();
         }

         session.commit();

         consumer.close();

         session.close();
      }
   }

   @Test
   public void testSendStreamingSingleMessage() throws Exception {
      ClientSession session = null;
      ActiveMQServer server = null;

      final int SIZE = 10 * 1024 * 1024;
      try {

         server = createServer(true, isNetty(), storeType);

         server.start();

         locator.setMinLargeMessageSize(largeMessageSize * 1024);

         ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

         session = sf.createSession(null, null, false, true, true, false, 0);

         session.createQueue(new QueueConfiguration(ADDRESS));

         ClientMessage clientFile = session.createMessage(true);
         clientFile.setBodyInputStream(ActiveMQTestBase.createFakeLargeStream(SIZE));

         ClientProducer producer = session.createProducer(ADDRESS);

         session.start();

         log.debug("Sending");
         producer.send(clientFile);

         producer.close();

         log.debug("Waiting");

         ClientConsumer consumer = session.createConsumer(ADDRESS);

         ClientMessage msg2 = consumer.receive(10000);

         msg2.acknowledge();

         msg2.setOutputStream(createFakeOutputStream());
         Assert.assertTrue(msg2.waitOutputStreamCompletion(0));

         session.commit();

         Assert.assertEquals(0, ((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
         Assert.assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable())));

      } catch (Throwable t) {
         t.printStackTrace();
         throw t;
      } finally {
         try {
            session.close();
         } catch (Throwable ignored) {
         }

         try {
            server.stop();
         } catch (Throwable ignored) {
         }
      }
   }

   // https://issues.apache.org/jira/browse/ARTEMIS-331
   @Test
   public void testSendStreamingSingleEmptyMessage() throws Exception {
      final String propertyName = "myStringPropertyName";
      final String propertyValue = "myStringPropertyValue";
      ClientSession session = null;
      ActiveMQServer server = null;

      final int SIZE = 0;
      try {

         server = createServer(true, isNetty(), storeType);

         server.start();

         locator.setMinLargeMessageSize(largeMessageSize * 1024);

         ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

         session = sf.createSession(null, null, false, true, true, false, 0);

         session.createQueue(new QueueConfiguration(ADDRESS));

         ClientMessage clientFile = session.createMessage(true);
         clientFile.setBodyInputStream(ActiveMQTestBase.createFakeLargeStream(SIZE));
         clientFile.putStringProperty(propertyName, propertyValue);

         ClientProducer producer = session.createProducer(ADDRESS);

         session.start();

         log.debug("Sending");
         producer.send(clientFile);

         producer.close();

         log.debug("Waiting");

         ClientConsumer consumer = session.createConsumer(ADDRESS);

         ClientMessage msg2 = consumer.receive(10000);

         msg2.acknowledge();

         msg2.setOutputStream(createFakeOutputStream());
         Assert.assertTrue(msg2.waitOutputStreamCompletion(60000));
         Assert.assertEquals(propertyValue, msg2.getStringProperty(propertyName));

         session.commit();

         Assert.assertEquals(0, ((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
         Assert.assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable())));

      } finally {
         try {
            session.close();
         } catch (Throwable ignored) {
         }

         try {
            server.stop();
         } catch (Throwable ignored) {
         }
      }
   }

   // https://issues.apache.org/jira/browse/ARTEMIS-331
   @Test
   public void testSendStreamingEmptyMessagesWithRestart() throws Exception {
      final String propertyName = "myStringPropertyName";
      final String propertyValue = "myStringPropertyValue";
      ClientSession session = null;
      ActiveMQServer server = null;

      final int SIZE = 0;
      try {

         server = createServer(true, isNetty(), storeType);

         server.start();

         locator.setMinLargeMessageSize(largeMessageSize * 1024);

         ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

         session = sf.createSession(null, null, false, true, true, false, 0);

         session.createQueue(new QueueConfiguration(ADDRESS));

         ClientProducer producer = session.createProducer(ADDRESS);

         for (int i = 0; i < 10; i++) {
            ClientMessage clientFile = session.createMessage(true);
            clientFile.setBodyInputStream(ActiveMQTestBase.createFakeLargeStream(SIZE));
            clientFile.putStringProperty(propertyName, propertyValue + i);
            producer.send(clientFile);
         }

         producer.close();

         session.close();

         sf.close();

         server.stop();

         server.start();

         sf = addSessionFactory(createSessionFactory(locator));

         session = sf.createSession(null, null, false, true, true, false, 0);

         ClientConsumer consumer = session.createConsumer(ADDRESS);

         session.start();

         for (int i = 0; i < 10; i++) {
            ClientMessage msg2 = consumer.receive(10000);

            msg2.acknowledge();

            msg2.setOutputStream(createFakeOutputStream());
            Assert.assertTrue(msg2.waitOutputStreamCompletion(60000));
            Assert.assertEquals(propertyValue + i, msg2.getStringProperty(propertyName));

            session.commit();
         }

         Assert.assertEquals(0, ((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
         Assert.assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable())));

      } finally {
         try {
            session.close();
         } catch (Throwable ignored) {
         }

         try {
            server.stop();
         } catch (Throwable ignored) {
         }
      }
   }

   /**
    * Receive messages but never reads them, leaving the buffer pending
    */
   @Test
   public void testIgnoreStreaming() throws Exception {
      ClientSession session = null;
      ActiveMQServer server = null;

      final int SIZE = 10 * 1024;
      final int NUMBER_OF_MESSAGES = 1;

      server = createServer(true, isNetty(), storeType);

      server.start();

      locator.setMinLargeMessageSize(1024);

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      session = sf.createSession(null, null, false, true, true, false, 0);

      session.createQueue(new QueueConfiguration(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         ClientMessage msg = session.createMessage(true);
         msg.setBodyInputStream(ActiveMQTestBase.createFakeLargeStream(SIZE));
         msg.putIntProperty(new SimpleString("key"), i);
         producer.send(msg);

         log.debug("Sent msg " + i);
      }

      session.start();

      log.debug("Sending");

      producer.close();

      log.debug("Waiting");

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         ClientMessage msg = consumer.receive(50000);
         Assert.assertNotNull(msg);

         Assert.assertEquals(i, msg.getObjectProperty(new SimpleString("key")));

         msg.acknowledge();
      }

      consumer.close();

      session.commit();

      Assert.assertEquals(0, ((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
      Assert.assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable())));

      log.debug("Thread done");
   }

   @Test
   public void testLargeMessageBodySize() throws Exception {
      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      LargeServerMessageImpl fileMessage = new LargeServerMessageImpl((JournalStorageManager) server.getStorageManager());

      fileMessage.setMessageID(1005);

      Assert.assertEquals(0, fileMessage.getBodyBufferSize());

      for (int i = 0; i < largeMessageSize; i++) {
         fileMessage.addBytes(new byte[]{ActiveMQTestBase.getSamplebyte(i)});
      }

      Assert.assertEquals(largeMessageSize, fileMessage.getBodyBufferSize());

      // The server would be doing this
      fileMessage.putLongProperty(Message.HDR_LARGE_BODY_SIZE, largeMessageSize);

      fileMessage.releaseResources(false, false);

      Assert.assertEquals(largeMessageSize, fileMessage.getBodyBufferSize());
   }

   // The ClientConsumer should be able to also send ServerLargeMessages as that's done by the CoreBridge
   @Test
   public void testSendServerMessage() throws Exception {
      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(false, false);

      LargeServerMessageImpl fileMessage = new LargeServerMessageImpl((JournalStorageManager) server.getStorageManager());

      fileMessage.setMessageID(1005);

      for (int i = 0; i < largeMessageSize; i++) {
         fileMessage.addBytes(new byte[]{ActiveMQTestBase.getSamplebyte(i)});
      }

      // The server would be doing this
      fileMessage.putLongProperty(Message.HDR_LARGE_BODY_SIZE, largeMessageSize);

      fileMessage.releaseResources(false, false);

      session.createQueue(new QueueConfiguration(ADDRESS));

      ClientProducer prod = session.createProducer(ADDRESS);

      prod.send(fileMessage);

      fileMessage.deleteFile();

      session.commit();

      session.start();

      ClientConsumer cons = session.createConsumer(ADDRESS);

      ClientMessage msg = cons.receive(5000);

      Assert.assertNotNull(msg);

      Assert.assertEquals(msg.getBodySize(), largeMessageSize);

      for (int i = 0; i < largeMessageSize; i++) {
         Assert.assertEquals(ActiveMQTestBase.getSamplebyte(i), msg.getBodyBuffer().readByte());
      }

      msg.acknowledge();

      session.commit();
   }



   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();
      locator = createFactory(isNetty());
      locator.setCallTimeout(100000000);
   }

   protected void testPageOnLargeMessage(final boolean realFiles, final boolean sendBlocking) throws Exception {
      Configuration config = createDefaultConfig(isNetty());

      final int PAGE_MAX = 20 * 1024;

      final int PAGE_SIZE = 10 * 1024;

      HashMap<String, AddressSettings> map = new HashMap<>();

      AddressSettings value = new AddressSettings();
      map.put(ADDRESS.toString(), value);
      ActiveMQServer server = createServer(realFiles, config, PAGE_SIZE, PAGE_MAX, map, storeType);
      server.start();

      final int numberOfBytes = 1024;

      final int numberOfBytesBigMessage = 400000;

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      if (sendBlocking) {
         sf.getServerLocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);
      }

      ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

      session.createQueue(new QueueConfiguration(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage message = null;

      for (int i = 0; i < 100; i++) {
         message = session.createMessage(true);

         // TODO: Why do I need to reset the writerIndex?
         message.getBodyBuffer().writerIndex(0);

         for (int j = 1; j <= numberOfBytes; j++) {
            message.getBodyBuffer().writeInt(j);
         }

         producer.send(message);
      }

      ClientMessage clientFile = createLargeClientMessageStreaming(session, numberOfBytesBigMessage);

      producer.send(clientFile);

      session.close();

      if (realFiles) {
         server.stop();

         server = createServer(true, config, PAGE_SIZE, PAGE_MAX, map, storeType);
         server.start();

         sf = createSessionFactory(locator);
      }

      session = sf.createSession(null, null, false, true, true, false, 0);

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      session.start();

      for (int i = 0; i < 100; i++) {
         ClientMessage message2 = consumer.receive(LargeMessageTest.RECEIVE_WAIT_TIME);

         Assert.assertNotNull(message2);

         message2.acknowledge();

         Assert.assertNotNull(message2);

         message.getBodyBuffer().readerIndex(0);

         for (int j = 1; j <= numberOfBytes; j++) {
            Assert.assertEquals(j, message.getBodyBuffer().readInt());
         }
      }

      consumer.close();

      session.close();

      session = sf.createSession(null, null, false, true, true, false, 0);

      readMessage(session, ADDRESS, numberOfBytesBigMessage);

      // printBuffer("message received : ", message2.getBody());

      session.close();
   }

   @Test
   public void testGlobalSizeBytesAndAddressSizeOnPage() throws Exception {
      testGlobalSizeBytesAndAddressSize(true);
   }

   @Test
   public void testGlobalSizeBytesAndAddressSize() throws Exception {
      testGlobalSizeBytesAndAddressSize(false);
   }

   public void testGlobalSizeBytesAndAddressSize(boolean isPage) throws Exception {
      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(false, false);

      LargeServerMessageImpl fileMessage = new LargeServerMessageImpl((JournalStorageManager) server.getStorageManager());

      fileMessage.setMessageID(1005);

      for (int i = 0; i < largeMessageSize; i++) {
         fileMessage.addBytes(new byte[]{ActiveMQTestBase.getSamplebyte(i)});
      }

      fileMessage.releaseResources(false, false);

      session.createQueue(new QueueConfiguration(ADDRESS));

      PagingStore store = server.getPagingManager().getPageStore(ADDRESS);

      if (isPage) {
         store.startPaging();
      }

      ClientProducer prod = session.createProducer(ADDRESS);

      prod.send(fileMessage);

      fileMessage.deleteFile();

      session.commit();

      if (isPage) {
         server.getPagingManager().getPageStore(ADDRESS).getCursorProvider().clearCache();
      }

      if (isPage) {
         Assert.assertEquals(0, server.getPagingManager().getPageStore(ADDRESS).getAddressSize());
         Assert.assertEquals(0, server.getPagingManager().getGlobalSize());
      } else {
         Assert.assertNotEquals(0, server.getPagingManager().getPageStore(ADDRESS).getAddressSize());
         Assert.assertNotEquals(0, server.getPagingManager().getGlobalSize());
      }

      session.start();

      ClientConsumer cons = session.createConsumer(ADDRESS);

      ClientMessage msg = cons.receive(5000);

      Assert.assertNotNull(msg);

      msg.acknowledge();

      session.commit();

      Wait.assertEquals(0, server.getPagingManager().getPageStore(ADDRESS)::getAddressSize);

      Wait.assertEquals(0, server.getPagingManager()::getGlobalSize);

      session.close();

      cons.close();
   }

   @Test
   public void testAMQPLargeMessageFDs() throws Exception {
      OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

      Assume.assumeTrue(os instanceof UnixOperatingSystemMXBean);

      final SimpleString MY_QUEUE = new SimpleString("MY-QUEUE");
      final int numberOfMessages = 30;
      ActiveMQServer server = createServer(true, true);

      server.start();

      long fdBefore = ((UnixOperatingSystemMXBean)os).getOpenFileDescriptorCount();

      server.createQueue(new QueueConfiguration(MY_QUEUE).setRoutingType(RoutingType.ANYCAST));

      ConnectionFactory connectionFactory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:61616");
      Connection connection = connectionFactory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      byte[] bufferSample = new byte[300 * 1024];

      for (int i = 0; i < bufferSample.length; i++) {
         bufferSample[i] = getSamplebyte(i);
      }

      javax.jms.Queue jmsQueue = session.createQueue(MY_QUEUE.toString());

      MessageProducer producer = session.createProducer(jmsQueue);
      producer.setTimeToLive(300);

      for (int i = 0; i < numberOfMessages; i++) {
         BytesMessage message = session.createBytesMessage();
         message.writeBytes(bufferSample);

         message.setIntProperty("count", i);

         producer.send(message);
      }

      session.close();
      connection.close();

      Wait.assertTrue(() -> ((UnixOperatingSystemMXBean)os).getOpenFileDescriptorCount() - fdBefore < 3);
   }



}