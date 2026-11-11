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
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.management.UnixOperatingSystemMXBean;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
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
import org.apache.activemq.artemis.core.persistence.impl.journal.LargeServerMessageImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.ServerProducer;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerMessagePlugin;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.integration.largemessage.LargeMessageTestBase;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.utils.RandomUtil.randomBytes;
import static org.apache.activemq.artemis.utils.RandomUtil.randomInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// Parameters set in superclass
@ExtendWith(ParameterizedTestExtension.class)
public class LargeMessageTest extends LargeMessageTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int RECEIVE_WAIT_TIME = 10000;

   protected ServerLocator locator;

   private static final int LARGE_MESSAGE_SIZE = 5 * 1024;

   public LargeMessageTest(StoreConfiguration.StoreType storeType) {
      super(storeType);
   }

   @TestTemplate
   public void testRollbackPartiallyConsumedBuffer() throws Exception {
      for (int i = 0; i < 1; i++) {
         logger.debug("#test {}", i);
         internalTestRollbackPartiallyConsumedBuffer(false);
         tearDown();
         setUp();
      }
   }

   @TestTemplate
   public void testRollbackPartiallyConsumedBufferWithRedeliveryDelay() throws Exception {
      internalTestRollbackPartiallyConsumedBuffer(true);
   }

   private void internalTestRollbackPartiallyConsumedBuffer(final boolean redeliveryDelay) throws Exception {
      final int messageSize = LARGE_MESSAGE_SIZE;

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

      session.createQueue(QueueConfiguration.of(ADDRESS));

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

   @TestTemplate
   public void testCloseConsumer() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ClientSession session = null;

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(QueueConfiguration.of(ADDRESS).setAddress(ADDRESS).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      ClientMessage msg1 = consumer.receive(1000);
      msg1.acknowledge();
      session.commit();
      assertNotNull(msg1);

      consumer.close();

      try {
         msg1.getBodyBuffer().readByte();
         fail("Exception was expected");
      } catch (final Exception ignored) {
         // empty on purpose
      }

      session.close();

      validateNoFilesOnLargeDir();
   }

   @TestTemplate
   public void testDivertAndExpire() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);
      final String DIVERTED = "diverted";

      ClientSession session = null;

      ActiveMQServer server = createServer(true, isNetty(), storeType);
      server.getConfiguration().setMessageExpiryScanPeriod(100);

      server.start();

      server.createQueue(QueueConfiguration.of(DIVERTED));

      server.getAddressSettingsRepository().addMatch(DIVERTED, new AddressSettings().setExpiryDelay(250L).setExpiryAddress(SimpleString.of(DIVERTED + "Expiry")).setAutoCreateExpiryResources(true));

      server.deployDivert(new DivertConfiguration().setName("myDivert").setAddress(ADDRESS.toString()).setForwardingAddress(DIVERTED).setExclusive(true));

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(QueueConfiguration.of(ADDRESS).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      logger.debug("****** Send message");
      producer.send(clientFile);

      session.commit();

      session.start();

      Wait.waitFor(() -> server.locateQueue(AddressSettings.DEFAULT_EXPIRY_QUEUE_PREFIX + DIVERTED) != null, 1000, 100);

      ClientConsumer consumer = session.createConsumer(AddressSettings.DEFAULT_EXPIRY_QUEUE_PREFIX + DIVERTED);
      ClientMessage msg1 = consumer.receive(1000);
      msg1.acknowledge();
      session.commit();
      assertNotNull(msg1);

      consumer.close();

      try {
         msg1.getBodyBuffer().readByte();
         fail("Exception was expected");
      } catch (final Exception ignored) {
         logger.debug(ignored.getMessage(), ignored);
      }

      session.close();

      validateNoFilesOnLargeDir();
   }

   @TestTemplate
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

   @TestTemplate
   public void testFileRemovalOnFailure() throws Exception {
      final AtomicBoolean throwException = new AtomicBoolean(false);
      final String queueName = RandomUtil.randomUUIDString();
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      server.registerBrokerPlugin(new ActiveMQServerMessagePlugin() {
         @Override
         public void beforeMessageRoute(Message message, RoutingContext context, boolean direct, boolean rejectDuplicates) throws ActiveMQException {
            if (throwException.get()) {
               throw new ActiveMQException();
            }
         }
      });

      server.createQueue(QueueConfiguration.of(queueName));

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = addClientSession(sf.createSession(false, true, false));

      ClientProducer producer = session.createProducer(queueName);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      try {
         throwException.set(true);
         producer.send(clientFile);
         fail("Should have thrown an exception here");
      } catch (Exception e) {
         // expected exception from plugin
      } finally {
         throwException.set(false);
      }

      assertEquals(0, server.locateQueue(queueName).getMessageCount());

      session.close();

      validateNoFilesOnLargeDir();
   }

   @TestTemplate
   public void testDeleteUnreferencedMessage() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      server.createQueue(QueueConfiguration.of(getName()).setRoutingType(RoutingType.ANYCAST).setDurable(true));

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = addClientSession(sf.createSession(false, true, false));
      ClientProducer producer = session.createProducer(getName());

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      producer.send(clientFile);

      session.close();

      final Queue queue = server.locateQueue(getName());
      queue.forEach(ref -> {
         // simulating an ACK but the server crashed before the delete of the record, and the large message file
         if (ref.getMessage().isLargeMessage()) {
            try {
               server.getStorageManager().storeAcknowledge(queue.getID(), ref.getMessageID());
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
            }
         }
      });
      server.stop();

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         server.start();
         assertTrue(loggerHandler.findText("AMQ221019"));
      }

      validateNoFilesOnLargeDir();
      runAfter(server::stop);

   }


   @TestTemplate
   public void testPendingRecord() throws Exception {

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = addClientSession(sf.createSession(false, true, false));

      session.createQueue(QueueConfiguration.of(ADDRESS));

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
      assertNotNull(message);
      for (int i = 0; i < messageSize; i++) {
         assertEquals(getSamplebyte(i), message.getBodyBuffer().readByte(), "position = " + i);
      }
      message.acknowledge();
      session.commit();

      validateNoFilesOnLargeDir();
   }

   @TestTemplate
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

      ActiveMQServer server = createServer(true, config, PAGE_SIZE, PAGE_MAX, new HashMap<>(), storeType);

      server.start();

      server.getAddressSettingsRepository().getMatch("#").setAddressFullMessagePolicy(AddressFullMessagePolicy.DROP);

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(ADDRESS));

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

         message.putIntProperty(SimpleString.of("id"), i);

         producer.send(message);
         if (i % 1000 == 0) {
            session.commit();
         }
      }
      session.commit();
      session.close();
   }

   @TestTemplate
   public void testLargeBufferTransacted() throws Exception {
      doTestLargeBuffer(true);
   }

   @TestTemplate
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

      session.createQueue(QueueConfiguration.of(ADDRESS));

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

      assertNotNull(msg1);

      for (int i = 0; i < messageSize; i++) {
         // System.out.print(msg1.getBodyBuffer().readByte() + "  ");
         // if (i % 100 == 0) System.out.println();
         assertEquals(getSamplebyte(i), msg1.getBodyBuffer().readByte(), "position = " + i);
      }

      msg1.acknowledge();

      consumer.close();

      if (transacted) {
         session.commit();
      }

      session.close();

      validateNoFilesOnLargeDir();
   }

   @TestTemplate
   public void testDLALargeMessage() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ClientSession session = null;

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(QueueConfiguration.of(ADDRESS));
      session.createQueue(QueueConfiguration.of(ADDRESS.concat("-2")).setAddress(ADDRESS));

      SimpleString ADDRESS_DLA = ADDRESS.concat("-dla");

      AddressSettings addressSettings = new AddressSettings().setDeadLetterAddress(ADDRESS_DLA).setMaxDeliveryAttempts(1);

      server.getAddressSettingsRepository().addMatch("*", addressSettings);

      session.createQueue(QueueConfiguration.of(ADDRESS_DLA));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS_DLA);

      ClientConsumer consumerRollback = session.createConsumer(ADDRESS);
      ClientMessage msg1 = consumerRollback.receive(1000);
      assertNotNull(msg1);
      msg1.acknowledge();
      session.rollback();
      consumerRollback.close();

      msg1 = consumer.receive(10000);

      assertNotNull(msg1);

      for (int i = 0; i < messageSize; i++) {
         assertEquals(ActiveMQTestBase.getSamplebyte(i), msg1.getBodyBuffer().readByte());
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

      assertNotNull(msg1);

      for (int i = 0; i < messageSize; i++) {
         assertEquals(ActiveMQTestBase.getSamplebyte(i), msg1.getBodyBuffer().readByte());
      }

      msg1.acknowledge();

      session.commit();

      if (storeType != StoreConfiguration.StoreType.DATABASE) {
         validateNoFilesOnLargeDir(server.getConfiguration().getLargeMessagesDirectory(), isCompressedTest ? 0 : 1);
      }

      consumer = session.createConsumer(ADDRESS.concat("-2"));

      msg1 = consumer.receive(10000);

      assertNotNull(msg1);

      for (int i = 0; i < messageSize; i++) {
         assertEquals(ActiveMQTestBase.getSamplebyte(i), msg1.getBodyBuffer().readByte());
      }

      msg1.acknowledge();

      session.commit();

      session.close();

      if (storeType != StoreConfiguration.StoreType.DATABASE) {
         validateNoFilesOnLargeDir();
      }
   }

   @TestTemplate
   public void testDeliveryCount() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ClientSession session = null;

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      session = sf.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);
      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      ClientMessage msg = consumer.receive(10000);
      assertNotNull(msg);
      msg.acknowledge();
      assertEquals(1, msg.getDeliveryCount());

      logger.debug("body buffer is {}", msg.getBodyBuffer());

      for (int i = 0; i < messageSize; i++) {
         assertEquals(ActiveMQTestBase.getSamplebyte(i), msg.getBodyBuffer().readByte());
      }
      session.rollback();

      session.close();

      session = sf.createSession(false, false, false);
      session.start();

      consumer = session.createConsumer(ADDRESS);
      msg = consumer.receive(10000);
      assertNotNull(msg);
      msg.acknowledge();
      for (int i = 0; i < messageSize; i++) {
         assertEquals(ActiveMQTestBase.getSamplebyte(i), msg.getBodyBuffer().readByte());
      }
      assertEquals(2, msg.getDeliveryCount());
      msg.acknowledge();
      consumer.close();

      session.commit();

      validateNoFilesOnLargeDir();

   }


   @TestTemplate
   public void testDLQAlmostLarge() throws Exception {
      SimpleString addressName = SimpleString.of("SomewhatHugeNameToBeUsedxxxxxxxxxxxxxxxxxxxiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
      SimpleString dlqName = SimpleString.of("DLQ" + addressName.toString());


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
            assertNotNull(msg);
            msg.acknowledge();
            session.rollback();
         } else {
            ClientMessage msg = consumer.receiveImmediate();
            assertNull(msg);
         }
      }

      consumer.close();

      consumer = session.createConsumer(dlqName);
      ClientMessage msg = consumer.receive(1000);
      assertNotNull(msg);

   }

   @TestTemplate
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

      session.createQueue(QueueConfiguration.of(ADDRESS));

      session.createQueue(QueueConfiguration.of(ADDRESS_DLA));
      session.createQueue(QueueConfiguration.of(ADDRESS_EXPIRY));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, false);
      clientFile.setExpiration(System.currentTimeMillis());

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumerExpired = session.createConsumer(ADDRESS);
      // to kick expiry quicker than waiting reaper thread
      assertNull(consumerExpired.receiveImmediate());
      consumerExpired.close();

      ClientConsumer consumerExpiry = session.createConsumer(ADDRESS_EXPIRY);

      ClientMessage msg1 = consumerExpiry.receive(5000);
      assertTrue(msg1.isLargeMessage());
      assertNotNull(msg1);
      msg1.acknowledge();

      for (int j = 0; j < messageSize; j++) {
         assertEquals(ActiveMQTestBase.getSamplebyte(j), msg1.getBodyBuffer().readByte());
      }

      session.rollback();

      consumerExpiry.close();

      for (int i = 0; i < 10; i++) {

         consumerExpiry = session.createConsumer(ADDRESS_DLA);

         msg1 = consumerExpiry.receive(5000);
         assertNotNull(msg1);
         msg1.acknowledge();

         for (int j = 0; j < messageSize; j++) {
            assertEquals(ActiveMQTestBase.getSamplebyte(j), msg1.getBodyBuffer().readByte());
         }

         session.rollback();

         consumerExpiry.close();
      }

      session.close();

      session = sf.createSession(false, false, false);

      session.start();

      consumerExpiry = session.createConsumer(ADDRESS_DLA);

      msg1 = consumerExpiry.receive(5000);

      assertNotNull(msg1);

      msg1.acknowledge();

      for (int i = 0; i < messageSize; i++) {
         assertEquals(ActiveMQTestBase.getSamplebyte(i), msg1.getBodyBuffer().readByte());
      }

      session.commit();

      consumerExpiry.close();

      session.commit();

      session.close();

      server.stop();

      server.start();

      validateNoFilesOnLargeDir();

   }

   @TestTemplate
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

      session.createQueue(QueueConfiguration.of(ADDRESS));

      session.createQueue(QueueConfiguration.of(ADDRESS_DLA));
      session.createQueue(QueueConfiguration.of(ADDRESS_EXPIRY));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);
      clientFile.setExpiration(System.currentTimeMillis());

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumerExpired = session.createConsumer(ADDRESS);
      // to kick expiry quicker than waiting reaper thread
      assertNull(consumerExpired.receiveImmediate());
      consumerExpired.close();

      ClientConsumer consumerExpiry = session.createConsumer(ADDRESS_EXPIRY);

      ClientMessage msg1 = consumerExpiry.receive(5000);
      assertNotNull(msg1);
      msg1.acknowledge();

      for (int j = 0; j < messageSize; j++) {
         assertEquals(ActiveMQTestBase.getSamplebyte(j), msg1.getBodyBuffer().readByte());
      }

      session.rollback();

      consumerExpiry.close();

      for (int i = 0; i < 10; i++) {
         consumerExpiry = session.createConsumer(ADDRESS_DLA);

         msg1 = consumerExpiry.receive(5000);
         assertNotNull(msg1);
         msg1.acknowledge();

         for (int j = 0; j < messageSize; j++) {
            assertEquals(ActiveMQTestBase.getSamplebyte(j), msg1.getBodyBuffer().readByte());
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
      assertNotNull(msg1);
      msg1.acknowledge();

      for (int i = 0; i < messageSize; i++) {
         assertEquals(ActiveMQTestBase.getSamplebyte(i), msg1.getBodyBuffer().readByte());
      }

      session.commit();

      consumerExpiry.close();

      session.commit();

      session.close();

      validateNoFilesOnLargeDir();
   }

   @TestTemplate
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

         session.createQueue(QueueConfiguration.of(ADDRESS));

         session.createQueue(QueueConfiguration.of(ADDRESS_EXPIRY));

         ClientProducer producer = session.createProducer(ADDRESS);

         Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

         clientFile.setExpiration(System.currentTimeMillis());

         producer.send(clientFile);

         session.commit();

         session.start();

         ClientConsumer consumer = session.createConsumer(ADDRESS_EXPIRY);

         // Creating a consumer just to make the expiry process go faster and not have to wait for the reaper
         ClientConsumer consumer2 = session.createConsumer(ADDRESS);
         assertNull(consumer2.receiveImmediate());

         ClientMessage msg1 = consumer.receive(50000);

         assertNotNull(msg1);

         for (int i = 0; i < messageSize; i++) {
            assertEquals(ActiveMQTestBase.getSamplebyte(i), msg1.getBodyBuffer().readByte());
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

         assertNotNull(msg1);

         for (int i = 0; i < messageSize; i++) {
            assertEquals(ActiveMQTestBase.getSamplebyte(i), msg1.getBodyBuffer().readByte());
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

   @TestTemplate
   public void testSentWithDuplicateIDBridge() throws Exception {
      internalTestSentWithDuplicateID(true);
   }

   @TestTemplate
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

         session.createQueue(QueueConfiguration.of(ADDRESS));

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

   @TestTemplate
   public void testResendSmallStreamMessage() throws Exception {
      internalTestResendMessage(50000);
   }

   @TestTemplate
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

         session.createQueue(QueueConfiguration.of(ADDRESS));

         SimpleString ADDRESS2 = ADDRESS.concat("-2");

         session.createQueue(QueueConfiguration.of(ADDRESS2));

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

         assertNotNull(msg2);

         msg2.acknowledge();

         session.commit();

         assertEquals(messageSize, msg2.getBodySize());

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

   @TestTemplate
   public void testResendCachedSmallStreamMessage() throws Exception {
      internalTestResendMessage(50000);
   }

   @TestTemplate
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

         session.createQueue(QueueConfiguration.of(ADDRESS));

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

   private void compareString(final long messageSize, ClientMessage msg) {
      assertNotNull(msg);
      for (long i = 0; i < messageSize; i++) {
         assertEquals(ActiveMQTestBase.getSamplebyte(i), msg.getBodyBuffer().readByte(), "position " + i);
      }
   }

   @TestTemplate
   public void testFilePersistenceOneHugeMessage() throws Exception {
      testChunks(false, false, false, true, true, false, false, false, false, 1, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0, 10 * 1024 * 1024, 1024 * 1024);
   }

   @TestTemplate
   public void testFilePersistenceOneMessageStreaming() throws Exception {
      testChunks(false, false, false, true, true, false, false, false, false, 1, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceSmallMessageStreaming() throws Exception {
      testChunks(false, false, false, true, true, false, false, false, false, 10, 1024, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceOneHugeMessageConsumer() throws Exception {
      testChunks(false, false, false, true, true, false, false, false, true, 1, LARGE_MESSAGE_SIZE, 120000, 0, 10 * 1024 * 1024, 1024 * 1024);
   }

   @TestTemplate
   public void testFilePersistence() throws Exception {
      testChunks(false, false, true, false, true, false, false, true, false, 10, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceConsumer() throws Exception {
      testChunks(false, false, true, false, true, false, false, true, true, 2, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceXA() throws Exception {
      testChunks(true, false, true, false, true, false, false, true, false, 10, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceXAStream() throws Exception {
      testChunks(true, false, false, true, true, false, false, false, false, 1, 1024 * 1024, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceXAStreamRestart() throws Exception {
      testChunks(true, true, false, true, true, false, false, false, false, 1, 1024 * 1024, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceXAConsumer() throws Exception {
      testChunks(true, false, true, false, true, false, false, true, true, 10, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceXAConsumerRestart() throws Exception {
      testChunks(true, true, true, false, true, false, false, true, true, 10, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceBlocked() throws Exception {
      testChunks(false, false, true, false, true, false, true, true, false, 10, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceBlockedConsumer() throws Exception {
      testChunks(false, false, true, false, true, false, true, true, true, 10, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceBlockedXA() throws Exception {
      testChunks(true, false, true, false, true, false, true, true, false, 10, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceBlockedXAConsumer() throws Exception {
      testChunks(true, false, true, false, true, false, true, true, true, 10, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceBlockedPreACK() throws Exception {
      testChunks(false, false, true, false, true, true, true, true, false, 1, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceBlockedPreACKConsumer() throws Exception {
      testChunks(false, false, true, false, true, true, true, true, true, 1, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceBlockedPreACKXA() throws Exception {
      testChunks(true, false, true, false, true, true, true, true, false, 10, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceBlockedPreACKXARestart() throws Exception {
      testChunks(true, true, true, false, true, true, true, true, false, 10, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceBlockedPreACKXAConsumer() throws Exception {
      testChunks(true, false, true, false, true, true, true, true, true, 10, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceBlockedPreACKXAConsumerRestart() throws Exception {
      testChunks(true, true, true, false, true, true, true, true, true, 10, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testFilePersistenceDelayed() throws Exception {
      testChunks(false, false, true, false, true, false, false, false, false, 1, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 200);
   }

   @TestTemplate
   public void testFilePersistenceDelayedConsumer() throws Exception {
      testChunks(false, false, true, false, true, false, false, false, true, 1, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 200);
   }

   @TestTemplate
   public void testFilePersistenceDelayedXA() throws Exception {
      testChunks(true, false, true, false, true, false, false, false, false, 1, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 200);
   }

   @TestTemplate
   public void testFilePersistenceDelayedXAConsumer() throws Exception {
      testChunks(true, false, true, false, true, false, false, false, true, 1, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 200);
   }

   @TestTemplate
   public void testNullPersistence() throws Exception {
      testChunks(false, false, true, false, false, false, false, true, true, 1, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testNullPersistenceConsumer() throws Exception {
      testChunks(false, false, true, false, false, false, false, true, true, 1, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testNullPersistenceXA() throws Exception {
      testChunks(true, false, true, false, false, false, false, true, false, 1, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testNullPersistenceXAConsumer() throws Exception {
      testChunks(true, false, true, false, false, false, false, true, true, 1, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testNullPersistenceDelayed() throws Exception {
      testChunks(false, false, true, false, false, false, false, false, false, 10, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @TestTemplate
   public void testNullPersistenceDelayedConsumer() throws Exception {
      testChunks(false, false, true, false, false, false, false, false, true, 10, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @TestTemplate
   public void testNullPersistenceDelayedXA() throws Exception {
      testChunks(true, false, true, false, false, false, false, false, false, 10, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @TestTemplate
   public void testNullPersistenceDelayedXAConsumer() throws Exception {
      testChunks(true, false, true, false, false, false, false, false, true, 10, LARGE_MESSAGE_SIZE, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @TestTemplate
   public void testPageOnLargeMessage() throws Exception {
      testPageOnLargeMessage(true, false);
   }

   @TestTemplate
   public void testSendSmallMessageXA() throws Exception {
      testChunks(true, false, true, false, true, false, false, true, false, 100, 4, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testSendSmallMessageXAConsumer() throws Exception {
      testChunks(true, false, true, false, true, false, false, true, true, 100, 4, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testSendSmallMessageNullPersistenceXA() throws Exception {
      testChunks(true, false, true, false, false, false, false, true, false, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testSendSmallMessageNullPersistenceXAConsumer() throws Exception {
      testChunks(true, false, true, false, false, false, false, true, true, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testSendRegularMessageNullPersistenceDelayed() throws Exception {
      testChunks(false, false, true, false, false, false, false, false, false, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @TestTemplate
   public void testSendRegularMessageNullPersistenceDelayedConsumer() throws Exception {
      testChunks(false, false, true, false, false, false, false, false, true, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @TestTemplate
   public void testSendRegularMessageNullPersistenceDelayedXA() throws Exception {
      testChunks(true, false, true, false, false, false, false, false, false, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @TestTemplate
   public void testSendRegularMessageNullPersistenceDelayedXAConsumer() throws Exception {
      testChunks(true, false, true, false, false, false, false, false, true, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @TestTemplate
   public void testSendRegularMessagePersistence() throws Exception {
      testChunks(false, false, true, false, true, false, false, true, false, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testSendRegularMessagePersistenceConsumer() throws Exception {
      testChunks(false, false, true, false, true, false, false, true, true, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testSendRegularMessagePersistenceXA() throws Exception {
      testChunks(true, false, true, false, true, false, false, true, false, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testSendRegularMessagePersistenceXAConsumer() throws Exception {
      testChunks(true, false, true, false, true, false, false, true, true, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 0);
   }

   @TestTemplate
   public void testSendRegularMessagePersistenceDelayed() throws Exception {
      testChunks(false, false, true, false, true, false, false, false, false, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @TestTemplate
   public void testSendRegularMessagePersistenceDelayedConsumer() throws Exception {
      testChunks(false, false, true, false, true, false, false, false, true, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @TestTemplate
   public void testSendRegularMessagePersistenceDelayedXA() throws Exception {
      testChunks(false, false, true, false, true, false, false, false, false, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @TestTemplate
   public void testSendRegularMessagePersistenceDelayedXAConsumer() throws Exception {
      testChunks(false, false, true, false, true, false, false, false, true, 100, 100, LargeMessageTest.RECEIVE_WAIT_TIME, 100);
   }

   @TestTemplate
   public void testTwoBindingsTwoStartedConsumers() throws Exception {
      // there are two bindings.. one is ACKed, the other is not, the server is restarted
      // The other binding is acked... The file must be deleted

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      SimpleString[] queue = new SimpleString[]{SimpleString.of("queue1"), SimpleString.of("queue2")};

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

      session.createQueue(QueueConfiguration.of(queue[0]).setAddress(ADDRESS));
      session.createQueue(QueueConfiguration.of(queue[1]).setAddress(ADDRESS));

      int numberOfBytes = 400000;

      Message clientFile = createLargeClientMessageStreaming(session, numberOfBytes);

      ClientProducer producer = session.createProducer(ADDRESS);

      session.start();

      producer.send(clientFile);

      producer.close();

      ClientConsumer consumer = session.createConsumer(queue[1]);
      ClientMessage msg = consumer.receive(LargeMessageTest.RECEIVE_WAIT_TIME);
      msg.getBodyBuffer().readByte();
      assertNull(consumer.receiveImmediate());
      assertNotNull(msg);

      msg.acknowledge();
      consumer.close();

      logger.debug("Stopping");

      session.stop();

      ClientConsumer consumer1 = session.createConsumer(queue[0]);

      session.start();

      msg = consumer1.receive(LargeMessageTest.RECEIVE_WAIT_TIME);
      assertNotNull(msg);
      msg.acknowledge();
      consumer1.close();

      session.commit();

      session.close();

      validateNoFilesOnLargeDir();
   }

   @TestTemplate
   public void testTwoBindingsAndRestart() throws Exception {
      testTwoBindings(true);
   }

   @TestTemplate
   public void testTwoBindingsNoRestart() throws Exception {
      testTwoBindings(false);
   }

   public void testTwoBindings(final boolean restart) throws Exception {
      // there are two bindings.. one is ACKed, the other is not, the server is restarted
      // The other binding is acked... The file must be deleted
      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      SimpleString[] queue = new SimpleString[]{SimpleString.of("queue1"), SimpleString.of("queue2")};

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

      session.createQueue(QueueConfiguration.of(queue[0]).setAddress(ADDRESS));
      session.createQueue(QueueConfiguration.of(queue[1]).setAddress(ADDRESS));

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

   @TestTemplate
   public void testSendRollbackXADurable() throws Exception {
      internalTestSendRollback(true, true);
   }

   @TestTemplate
   public void testSendRollbackXANonDurable() throws Exception {
      internalTestSendRollback(true, false);
   }

   @TestTemplate
   public void testSendRollbackDurable() throws Exception {
      internalTestSendRollback(false, true);
   }

   @TestTemplate
   public void testSendRollbackNonDurable() throws Exception {
      internalTestSendRollback(false, false);
   }

   private void internalTestSendRollback(final boolean isXA, final boolean durable) throws Exception {
      ClientSession session = null;

      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      session = sf.createSession(isXA, false, false);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      Xid xid = null;

      if (isXA) {
         xid = new XidImpl(randomBytes(), randomInt(), randomBytes());
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

   @TestTemplate
   public void testSimpleRollback() throws Exception {
      simpleRollbackInternalTest(false);
   }

   @TestTemplate
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

      session.createQueue(QueueConfiguration.of(ADDRESS));

      int numberOfBytes = 200000;

      session.start();

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      for (int n = 0; n < 10; n++) {
         Message clientFile = createLargeClientMessageStreaming(session, numberOfBytes, n % 2 == 0);

         producer.send(clientFile);

         assertNull(consumer.receiveImmediate());

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

         assertNull(consumer.receiveImmediate());

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

            assertNotNull(clientMessage);

            assertEquals(numberOfBytes, clientMessage.getBodySize());

            clientMessage.acknowledge();

            if (isXA) {
               session.end(xid, XAResource.TMSUCCESS);
               if (i == 0) {
                  session.prepare(xid);
                  session.rollback(xid);
               } else {
                  session.commit(xid, true);
               }
               xid = newXID();
               session.start(xid, XAResource.TMNOFLAGS);
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

   @TestTemplate
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

         session.createQueue(QueueConfiguration.of(ADDRESS));

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
         assertEquals(NUMBER_OF_MESSAGES, consumer.getBufferSize());

         // Reads the messages, rollback.. read them again
         for (int trans = 0; trans < 2; trans++) {

            for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
               ClientMessage msg = consumer.receive(10000);
               assertNotNull(msg);

               // it will ignore the buffer (not read it) on the first try
               if (trans == 0) {
                  for (int byteRead = 0; byteRead < SIZE; byteRead++) {
                     assertEquals(ActiveMQTestBase.getSamplebyte(byteRead), msg.getBodyBuffer().readByte());
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

         assertEquals(0, ((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
         assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable())));

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

   @TestTemplate
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

         session.createQueue(QueueConfiguration.of(ADDRESS));

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

            assertTrue(Wait.waitFor(() -> consumer.getBufferSize() >= 5, 30_000, 100));


            for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
               ClientMessage msg = consumer.receive(10000);
               assertNotNull(msg);

               // it will ignore the buffer (not read it) on the first try
               if (trans == 0) {
                  for (int byteRead = 0; byteRead < SIZE; byteRead++) {
                     assertEquals(ActiveMQTestBase.getSamplebyte(byteRead), msg.getBodyBuffer().readByte());
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

         assertEquals(0, ((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
         assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable())));

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

   @TestTemplate
   public void testPageOnLargeMessageMultipleQueues() throws Exception {

      AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler();
      runAfter(() -> loggerHandler.close());

      Configuration config = createDefaultConfig(isNetty());

      final int PAGE_MAX = 20 * 1024;

      final int PAGE_SIZE = 10 * 1024;

      Map<String, AddressSettings> map = new HashMap<>();

      AddressSettings value = new AddressSettings();
      map.put(ADDRESS.toString(), value);
      ActiveMQServer server = createServer(true, config, PAGE_SIZE, PAGE_MAX, map, storeType);
      server.start();

      final int numberOfBytes = 1024;

      final int numberOfBytesBigMessage = 4000;

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true).setMinLargeMessageSize(1000);

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(null, null, false, false, true, false, 0);

      session.createQueue(QueueConfiguration.of(ADDRESS.concat("-0")).setAddress(ADDRESS));
      session.createQueue(QueueConfiguration.of(ADDRESS.concat("-1")).setAddress(ADDRESS));

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

      session.commit();

      ClientMessage clientFile = createLargeClientMessageStreaming(session, numberOfBytesBigMessage);
      clientFile.putBooleanProperty("TestLarge", true);
      producer.send(clientFile);

      for (int i = 0; i < 10; i++) {
         message = session.createMessage(true);

         message.getBodyBuffer().writeBytes(new byte[numberOfBytes]);

         producer.send(message);
      }
      session.commit();

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

            assertNotNull(message2);

            message2.acknowledge();

            assertNotNull(message2);
         }

         session.commit();

         for (int i = 0; i < 5; i++) {
            ClientMessage messageLarge = consumer.receive(RECEIVE_WAIT_TIME);

            assertTrue(messageLarge.getBooleanProperty("TestLarge"));

            assertNotNull(messageLarge);

            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            messageLarge.acknowledge();
            messageLarge.saveToOutputStream(bout);
            byte[] body = bout.toByteArray();
            assertEquals(numberOfBytesBigMessage, body.length);
            for (int bi = 0; bi < body.length; bi++) {
               assertEquals(getSamplebyte(bi), body[bi]);
            }

            if (i < 4) {
               session.rollback();
            } else {
               session.commit();
            }
         }

         for (int i = 0; i < 5; i++) {
            ClientMessage message2 = consumer.receive(LargeMessageTest.RECEIVE_WAIT_TIME);

            assertNotNull(message2);

            message2.acknowledge();

            assertNotNull(message2);
         }

         session.commit();

         consumer.close();

         session.close();
      }

      // Reference Counting negative errors
      assertFalse(loggerHandler.findText("AMQ214034"));
   }

   @TestTemplate
   public void testPageOnLargeMessageMultipleQueues2() throws Exception {
      Configuration config = createDefaultConfig(isNetty());

      final int PAGE_MAX = 20 * 1024;

      final int PAGE_SIZE = 10 * 1024;

      Map<String, AddressSettings> map = new HashMap<>();

      AddressSettings value = new AddressSettings();
      map.put(ADDRESS.toString(), value);
      ActiveMQServer server = createServer(true, config, PAGE_SIZE, PAGE_MAX, map, storeType);
      server.start();

      final int numberOfBytes = 1024;

      final int numberOfBytesBigMessage = 400000;

      locator.setBlockOnNonDurableSend(false).setBlockOnDurableSend(false).setBlockOnAcknowledge(false).setCompressLargeMessage(true);

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(ADDRESS.concat("-0")).setAddress(ADDRESS));
      session.createQueue(QueueConfiguration.of(ADDRESS.concat("-1")).setAddress(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);
      int msgId = 0;

      for (int i = 0; i < 10; i++) {
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
            for (int i = 0; i < 10; i++) {
               ClientMessage message2 = consumer.receive(LargeMessageTest.RECEIVE_WAIT_TIME);

               assertNotNull(message2);

               assertFalse(message2.getBooleanProperty("TestLarge"));

               message2.acknowledge();

               assertNotNull(message2);
            }

            for (int i = 0; i < 10; i++) {
               ClientMessage messageLarge = consumer.receive(RECEIVE_WAIT_TIME);

               assertNotNull(messageLarge);

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

   @TestTemplate
   public void testSendStreamingSingleMessage() throws Exception {
      ClientSession session = null;
      ActiveMQServer server = null;

      final int SIZE = 10 * 1024 * 1024;
      try {

         server = createServer(true, isNetty(), storeType);

         server.start();

         locator.setMinLargeMessageSize(LARGE_MESSAGE_SIZE * 1024);

         ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

         session = sf.createSession(null, null, false, true, true, false, 0);

         session.createQueue(QueueConfiguration.of(ADDRESS));

         ClientMessage clientFile = session.createMessage(true);
         clientFile.setBodyInputStream(ActiveMQTestBase.createFakeLargeStream(SIZE));

         ClientProducer producer = session.createProducer(ADDRESS);

         session.start();

         logger.debug("Sending");
         producer.send(clientFile);

         producer.close();

         logger.debug("Waiting");

         ClientConsumer consumer = session.createConsumer(ADDRESS);

         ClientMessage msg2 = consumer.receive(10000);

         msg2.acknowledge();

         msg2.setOutputStream(createFakeOutputStream());
         assertTrue(msg2.waitOutputStreamCompletion(0));

         session.commit();

         assertEquals(0, ((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
         assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable())));

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
   @TestTemplate
   public void testSendStreamingSingleEmptyMessage() throws Exception {
      final String propertyName = "myStringPropertyName";
      final String propertyValue = "myStringPropertyValue";
      ClientSession session = null;
      ActiveMQServer server = null;

      final int SIZE = 0;
      try {

         server = createServer(true, isNetty(), storeType);

         server.start();

         locator.setMinLargeMessageSize(LARGE_MESSAGE_SIZE * 1024);

         ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

         session = sf.createSession(null, null, false, true, true, false, 0);

         session.createQueue(QueueConfiguration.of(ADDRESS));

         ClientMessage clientFile = session.createMessage(true);
         clientFile.setBodyInputStream(ActiveMQTestBase.createFakeLargeStream(SIZE));
         clientFile.putStringProperty(propertyName, propertyValue);

         ClientProducer producer = session.createProducer(ADDRESS);

         session.start();

         logger.debug("Sending");
         producer.send(clientFile);

         producer.close();

         logger.debug("Waiting");

         ClientConsumer consumer = session.createConsumer(ADDRESS);

         ClientMessage msg2 = consumer.receive(10000);

         msg2.acknowledge();

         msg2.setOutputStream(createFakeOutputStream());
         assertTrue(msg2.waitOutputStreamCompletion(60000));
         assertEquals(propertyValue, msg2.getStringProperty(propertyName));

         session.commit();

         assertEquals(0, ((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
         assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable())));

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
   @TestTemplate
   public void testSendStreamingEmptyMessagesWithRestart() throws Exception {
      final String propertyName = "myStringPropertyName";
      final String propertyValue = "myStringPropertyValue";
      ClientSession session = null;
      ActiveMQServer server = null;

      final int SIZE = 0;
      try {

         server = createServer(true, isNetty(), storeType);

         server.start();

         locator.setMinLargeMessageSize(LARGE_MESSAGE_SIZE * 1024);

         ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

         session = sf.createSession(null, null, false, true, true, false, 0);

         session.createQueue(QueueConfiguration.of(ADDRESS));

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
            assertTrue(msg2.waitOutputStreamCompletion(60000));
            assertEquals(propertyValue + i, msg2.getStringProperty(propertyName));

            session.commit();
         }

         assertEquals(0, ((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
         assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable())));

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
   @TestTemplate
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

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         ClientMessage msg = session.createMessage(true);
         msg.setBodyInputStream(ActiveMQTestBase.createFakeLargeStream(SIZE));
         msg.putIntProperty(SimpleString.of("key"), i);
         producer.send(msg);

         logger.debug("Sent msg {}", i);
      }

      session.start();

      logger.debug("Sending");

      producer.close();

      logger.debug("Waiting");

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         ClientMessage msg = consumer.receive(50000);
         assertNotNull(msg);

         assertEquals(i, msg.getObjectProperty(SimpleString.of("key")));

         msg.acknowledge();
      }

      consumer.close();

      session.commit();

      assertEquals(0, ((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
      assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable())));

      logger.debug("Thread done");
   }

   @TestTemplate
   public void testLargeMessageBodySize() throws Exception {
      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      LargeServerMessageImpl fileMessage = new LargeServerMessageImpl(server.getStorageManager());

      fileMessage.setMessageID(1005);

      assertEquals(0, fileMessage.getBodyBufferSize());

      for (int i = 0; i < LARGE_MESSAGE_SIZE; i++) {
         fileMessage.addBytes(new byte[]{ActiveMQTestBase.getSamplebyte(i)});
      }
      fileMessage.releaseResources(true, false);

      assertEquals(LARGE_MESSAGE_SIZE, fileMessage.getBodyBufferSize());

      // The server would be doing this
      fileMessage.putLongProperty(Message.HDR_LARGE_BODY_SIZE, LARGE_MESSAGE_SIZE);

      fileMessage.releaseResources(true, false);

      assertEquals(LARGE_MESSAGE_SIZE, fileMessage.getBodyBufferSize());
   }

   // The ClientConsumer should be able to also send ServerLargeMessages as that's done by the CoreBridge
   @TestTemplate
   public void testSendServerMessage() throws Exception {
      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(false, false);

      LargeServerMessageImpl fileMessage = new LargeServerMessageImpl(server.getStorageManager());

      fileMessage.setMessageID(1005);

      for (int i = 0; i < LARGE_MESSAGE_SIZE; i++) {
         fileMessage.addBytes(new byte[]{ActiveMQTestBase.getSamplebyte(i)});
      }

      // The server would be doing this
      fileMessage.putLongProperty(Message.HDR_LARGE_BODY_SIZE, LARGE_MESSAGE_SIZE);

      fileMessage.releaseResources(true, false);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer prod = session.createProducer(ADDRESS);

      prod.send(fileMessage);

      fileMessage.deleteFile();

      session.commit();

      session.start();

      ClientConsumer cons = session.createConsumer(ADDRESS);

      ClientMessage msg = cons.receive(5000);

      assertNotNull(msg);

      assertEquals(msg.getBodySize(), LARGE_MESSAGE_SIZE);

      for (int i = 0; i < LARGE_MESSAGE_SIZE; i++) {
         assertEquals(ActiveMQTestBase.getSamplebyte(i), msg.getBodyBuffer().readByte());
      }

      msg.acknowledge();

      session.commit();
   }

   @TestTemplate
   public void testSendServerMessageMetrics() throws Exception {
      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(false, false);

      LargeServerMessageImpl fileMessage = new LargeServerMessageImpl(server.getStorageManager());

      fileMessage.setMessageID(1005);

      for (int i = 0; i < LARGE_MESSAGE_SIZE; i++) {
         fileMessage.addBytes(new byte[]{ActiveMQTestBase.getSamplebyte(i)});
      }

      // The server would be doing this
      fileMessage.putLongProperty(Message.HDR_LARGE_BODY_SIZE, LARGE_MESSAGE_SIZE);

      fileMessage.releaseResources(true, false);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer prod = session.createProducer(ADDRESS);

      prod.send(fileMessage);

      fileMessage.deleteFile();

      session.commit();

      Collection<ServerProducer> serverProducers = server.getSessions().iterator().next().getServerProducers();

      assertEquals(1, serverProducers.size());

      ServerProducer producer = serverProducers.iterator().next();

      assertEquals(1, producer.getMessagesSent());

      assertEquals(LARGE_MESSAGE_SIZE, producer.getMessagesSentSize());

      fileMessage = new LargeServerMessageImpl(server.getStorageManager());

      fileMessage.setMessageID(1006);

      for (int i = 0; i < LARGE_MESSAGE_SIZE; i++) {
         fileMessage.addBytes(new byte[]{ActiveMQTestBase.getSamplebyte(i)});
      }

      // The server would be doing this
      fileMessage.putLongProperty(Message.HDR_LARGE_BODY_SIZE, LARGE_MESSAGE_SIZE);

      fileMessage.releaseResources(true, false);

      prod.send(fileMessage);

      fileMessage.deleteFile();

      session.commit();

      serverProducers = server.getSessions().iterator().next().getServerProducers();

      assertEquals(1, serverProducers.size());

      producer = serverProducers.iterator().next();

      assertEquals(2, producer.getMessagesSent());

      assertEquals(LARGE_MESSAGE_SIZE * 2, producer.getMessagesSentSize());
   }



   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = createFactory(isNetty());
      locator.setCallTimeout(100000000);
   }

   protected void testPageOnLargeMessage(final boolean realFiles, final boolean sendBlocking) throws Exception {
      Configuration config = createDefaultConfig(isNetty());

      final int PAGE_MAX = 20 * 1024;

      final int PAGE_SIZE = 10 * 1024;

      Map<String, AddressSettings> map = new HashMap<>();

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

      session.createQueue(QueueConfiguration.of(ADDRESS));

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

         assertNotNull(message2);

         message2.acknowledge();

         assertNotNull(message2);

         message.getBodyBuffer().readerIndex(0);

         for (int j = 1; j <= numberOfBytes; j++) {
            assertEquals(j, message.getBodyBuffer().readInt());
         }
      }

      consumer.close();

      session.close();

      session = sf.createSession(null, null, false, true, true, false, 0);

      readMessage(session, ADDRESS, numberOfBytesBigMessage);

      // printBuffer("message received : ", message2.getBody());

      session.close();
   }

   @TestTemplate
   public void testGlobalSizeBytesAndAddressSizeOnPage() throws Exception {
      testGlobalSizeBytesAndAddressSize(true);
   }

   @TestTemplate
   public void testGlobalSizeBytesAndAddressSize() throws Exception {
      testGlobalSizeBytesAndAddressSize(false);
   }

   public void testGlobalSizeBytesAndAddressSize(boolean isPage) throws Exception {
      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(false, false);

      LargeServerMessageImpl fileMessage = new LargeServerMessageImpl(server.getStorageManager());

      fileMessage.setMessageID(1005);

      for (int i = 0; i < LARGE_MESSAGE_SIZE; i++) {
         fileMessage.addBytes(new byte[]{ActiveMQTestBase.getSamplebyte(i)});
      }

      fileMessage.releaseResources(true, false);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      PagingStore store = server.getPagingManager().getPageStore(ADDRESS);

      if (isPage) {
         store.startPaging();
      }

      ClientProducer prod = session.createProducer(ADDRESS);

      prod.send(fileMessage);

      fileMessage.deleteFile();

      session.commit();

      if (isPage) {
         assertEquals(0, server.getPagingManager().getPageStore(ADDRESS).getAddressSize());
         assertEquals(0, server.getPagingManager().getGlobalSize());
      } else {
         assertNotEquals(0, server.getPagingManager().getPageStore(ADDRESS).getAddressSize());
         assertNotEquals(0, server.getPagingManager().getGlobalSize());
      }

      session.start();

      ClientConsumer cons = session.createConsumer(ADDRESS);

      ClientMessage msg = cons.receive(5000);

      assertNotNull(msg);

      msg.acknowledge();

      session.commit();

      Wait.assertEquals(0, server.getPagingManager().getPageStore(ADDRESS)::getAddressSize);

      Wait.assertEquals(0, server.getPagingManager()::getGlobalSize);

      session.close();

      cons.close();
   }

   @TestTemplate
   public void testAMQPLargeMessageFDs() throws Exception {
      OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

      assumeTrue(os instanceof UnixOperatingSystemMXBean);

      final SimpleString MY_QUEUE = SimpleString.of("MY-QUEUE");
      final int numberOfMessages = 30;
      ActiveMQServer server = createServer(true, true);

      server.start();

      long fdBefore = ((UnixOperatingSystemMXBean)os).getOpenFileDescriptorCount();

      server.createQueue(QueueConfiguration.of(MY_QUEUE).setRoutingType(RoutingType.ANYCAST));

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

   @TestTemplate
   public void testStreamedMessage() throws Exception {
      testStream(false);
   }

   @TestTemplate
   public void testStreamedMessageCompressed() throws Exception {
      testStream(true);
   }

   private void testStream(boolean compressed) throws Exception {
      ActiveMQServer server = createServer(true, isNetty(), storeType);

      server.start();

      locator.setCompressLargeMessage(compressed);

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(false, false);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      AtomicBoolean closed = new AtomicBoolean(false);

      final int BYTES = 1_000;

      InputStream inputStream = new InputStream() {
         int bytes = BYTES;
         @Override
         public int read() throws IOException {
            if (bytes-- > 0) {
               return 10;
            } else {
               return -1;
            }
         }

         @Override
         public void close() {
            closed.set(true);
         }

         @Override
         public int available() {
            return bytes;
         }
      };

      ClientMessage message = session.createMessage(true);
      message.setBodyInputStream(inputStream);
      producer.send(message);

      Wait.assertTrue(closed::get);

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      ClientMessage receivedMessage = consumer.receive(5000);
      assertNotNull(receivedMessage);

      ActiveMQBuffer buffer = receivedMessage.getBodyBuffer();
      assertEquals(BYTES, buffer.readableBytes());

      for (int i = 0; i < BYTES; i++) {
         assertEquals((byte)10, buffer.readByte());
      }

      assertEquals(0, buffer.readableBytes());

      session.close();

   }

}