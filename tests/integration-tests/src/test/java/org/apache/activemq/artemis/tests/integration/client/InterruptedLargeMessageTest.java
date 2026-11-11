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

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
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
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionContinuationMessage;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.QueueFactory;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.QueueFactoryImpl;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.integration.largemessage.LargeMessageTestBase;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

//Parameters set in superclass
@ExtendWith(ParameterizedTestExtension.class)
public class InterruptedLargeMessageTest extends LargeMessageTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   static final int RECEIVE_WAIT_TIME = 60000;

   private final int LARGE_MESSAGE_SIZE = ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE * 3;

   public InterruptedLargeMessageTest(StoreConfiguration.StoreType storeType) {
      super(storeType);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      LargeMessageTestInterceptorIgnoreLastPacket.clearInterrupt();
   }

   @Override
   protected boolean isNetty() {
      return false;
   }

   @TestTemplate
   public void testInterruptLargeMessageSend() throws Exception {

      ClientSession session = null;

      LargeMessageTestInterceptorIgnoreLastPacket.clearInterrupt();
      ActiveMQServer server = createServer(true, isNetty());

      server.getConfiguration().getIncomingInterceptorClassNames().add(LargeMessageTestInterceptorIgnoreLastPacket.class.getName());

      server.start();

      locator.setBlockOnNonDurableSend(false).setBlockOnDurableSend(false);

      ClientSessionFactory sf = createSessionFactory(locator);

      session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, LARGE_MESSAGE_SIZE, true);

      clientFile.setExpiration(System.currentTimeMillis());

      producer.send(clientFile);

      Thread.sleep(500);
//
//      for (ServerSession srvSession : server.getSessions()) {
//         ((ServerSessionImpl) srvSession).clearLargeMessage();
//      }

      server.fail(false);

      server.start();

      server.stop();

      validateNoFilesOnLargeDir();
   }

   @TestTemplate
   public void testCloseConsumerDuringTransmission() throws Exception {
      ActiveMQServer server = createServer(true, isNetty());

      LargeMessageTestInterceptorIgnoreLastPacket.disableInterrupt();

      server.start();

      locator.setBlockOnNonDurableSend(false).setBlockOnDurableSend(false).addIncomingInterceptor(new LargeMessageTestInterceptorIgnoreLastPacket());

      ClientSessionFactory sf = createSessionFactory(locator);

      final ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, LARGE_MESSAGE_SIZE, true);

      producer.send(clientFile);

      session.commit();

      LargeMessageTestInterceptorIgnoreLastPacket.clearInterrupt();

      final AtomicInteger unexpectedErrors = new AtomicInteger(0);
      final AtomicInteger expectedErrors = new AtomicInteger(0);
      final ClientConsumer cons = session.createConsumer(ADDRESS);

      session.start();

      Thread t = new Thread(() -> {
         try {
            logger.debug("Receiving message");
            ClientMessage msg = cons.receive(5000);
            if (msg == null) {
               System.err.println("Message not received");
               unexpectedErrors.incrementAndGet();
               return;
            }

            msg.checkCompletion();
         } catch (ActiveMQException e) {
            e.printStackTrace();
            expectedErrors.incrementAndGet();
         }
      });

      t.start();

      LargeMessageTestInterceptorIgnoreLastPacket.awaitInterrupt();

      cons.close();

      t.join();

      assertEquals(0, unexpectedErrors.get());
      assertEquals(1, expectedErrors.get());

      session.close();

      server.stop();
   }

   @TestTemplate
   public void testForcedInterruptUsingJMS() throws Exception {
      ActiveMQServer server = createServer(true, isNetty());

      server.start();

      SimpleString jmsAddress = SimpleString.of("Test");

      server.createQueue(QueueConfiguration.of(jmsAddress).setRoutingType(RoutingType.ANYCAST));

      final AtomicInteger unexpectedErrors = new AtomicInteger(0);
      final AtomicInteger expectedErrors = new AtomicInteger(0);
      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("vm://0");
      Connection connection = cf.createConnection();
      Session session = connection.createSession(Session.SESSION_TRANSACTED);
      connection.start();
      final MessageConsumer consumer = session.createConsumer(session.createQueue(jmsAddress.toString()));

      Thread t = new Thread(() -> {
         try {
            logger.debug("Receiving message");
            javax.jms.Message msg = consumer.receive(5000);
            if (msg == null) {
               System.err.println("Message not received");
               unexpectedErrors.incrementAndGet();
               return;
            }
         } catch (JMSException e) {
            logger.debug("This exception was ok as it was expected", e);
            expectedErrors.incrementAndGet();
         } catch (Throwable e) {
            logger.warn("Captured unexpected exception", e);
            unexpectedErrors.incrementAndGet();
         }
      });

      t.start();
      t.interrupt();

      t.join();

      assertEquals(0, unexpectedErrors.get());
      assertEquals(1, expectedErrors.get());

      session.close();

      server.stop();
   }

   @TestTemplate
   public void testSendNonPersistentQueue() throws Exception {

      ClientSession session = null;

      LargeMessageTestInterceptorIgnoreLastPacket.disableInterrupt();
      ActiveMQServer server = createServer(true, isNetty());

      server.start();

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);

      ClientSessionFactory sf = createSessionFactory(locator);

      session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(ADDRESS).setDurable(false));

      ClientProducer producer = session.createProducer(ADDRESS);

      for (int i = 0; i < 10; i++) {
         Message clientFile = createLargeClientMessageStreaming(session, LARGE_MESSAGE_SIZE, true);

         producer.send(clientFile);
      }
      session.commit();

      session.close();

      session = sf.createSession(false, false);

      ClientConsumer cons = session.createConsumer(ADDRESS);

      session.start();

      for (int h = 0; h < 5; h++) {
         for (int i = 0; i < 10; i++) {
            ClientMessage clientMessage = cons.receive(5000);
            assertNotNull(clientMessage);
            for (int countByte = 0; countByte < LARGE_MESSAGE_SIZE; countByte++) {
               assertEquals(ActiveMQTestBase.getSamplebyte(countByte), clientMessage.getBodyBuffer().readByte());
            }
            clientMessage.acknowledge();
         }
         session.rollback();
      }

      server.fail(false);
      server.start();

      server.stop();

      validateNoFilesOnLargeDir();
   }

   @TestTemplate
   public void testSendPaging() throws Exception {

      ClientSession session = null;

      LargeMessageTestInterceptorIgnoreLastPacket.disableInterrupt();
      ActiveMQServer server = createServer(true, createDefaultConfig(isNetty()), 10000, 20000, new HashMap<>());

      // server.getConfiguration()
      // .getIncomingInterceptorClassNames()
      // .add(LargeMessageTestInterceptorIgnoreLastPacket.class.getName());

      server.start();

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);

      ClientSessionFactory sf = createSessionFactory(locator);

      session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      server.getPagingManager().getPageStore(ADDRESS).startPaging();

      ClientProducer producer = session.createProducer(ADDRESS);

      for (int i = 0; i < 10; i++) {
         Message clientFile = createLargeClientMessageStreaming(session, LARGE_MESSAGE_SIZE, true);

         producer.send(clientFile);
      }
      session.commit();

      validateNoFilesOnLargeDir(server.getConfiguration().getLargeMessagesDirectory(), 10);

      for (int h = 0; h < 5; h++) {
         session.close();

         sf.close();

         server.stop();

         server.start();

         sf = createSessionFactory(locator);

         session = sf.createSession(false, false);

         ClientConsumer cons = session.createConsumer(ADDRESS);

         session.start();

         for (int i = 0; i < 10; i++) {
            ClientMessage clientMessage = cons.receive(5000);
            assertNotNull(clientMessage);
            for (int countByte = 0; countByte < LARGE_MESSAGE_SIZE; countByte++) {
               assertEquals(ActiveMQTestBase.getSamplebyte(countByte), clientMessage.getBodyBuffer().readByte());
            }
            clientMessage.acknowledge();
         }
         if (h == 4) {
            session.commit();
         } else {
            session.rollback();
         }

         session.close();
         sf.close();
      }

      server.fail(false);
      server.start();

      validateNoFilesOnLargeDir();

   }

   @TestTemplate
   public void testSendPreparedXA() throws Exception {
      ClientSession session = null;

      LargeMessageTestInterceptorIgnoreLastPacket.disableInterrupt();

      ActiveMQServer server = createServer(true, createDefaultConfig(isNetty()), 10000, 20000, new HashMap<>());

      server.getConfiguration().getIncomingInterceptorClassNames().add(LargeMessageTestInterceptorIgnoreLastPacket.class.getName());

      server.start();

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);

      ClientSessionFactory sf = createSessionFactory(locator);

      session = sf.createSession(true, false, false);

      Xid xid1 = newXID();
      Xid xid2 = newXID();

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      session.start(xid1, XAResource.TMNOFLAGS);

      for (int i = 0; i < 10; i++) {
         Message clientFile = createLargeClientMessageStreaming(session, LARGE_MESSAGE_SIZE, true);
         clientFile.putIntProperty("txid", 1);
         producer.send(clientFile);
      }
      session.end(xid1, XAResource.TMSUCCESS);

      session.prepare(xid1);

      session.start(xid2, XAResource.TMNOFLAGS);

      for (int i = 0; i < 10; i++) {
         Message clientFile = createLargeClientMessageStreaming(session, LARGE_MESSAGE_SIZE, true);
         clientFile.putIntProperty("txid", 2);
         clientFile.putIntProperty("i", i);
         producer.send(clientFile);
      }
      session.end(xid2, XAResource.TMSUCCESS);

      session.prepare(xid2);

      session.close();
      sf.close();

      server.fail(false);
      server.start();

      for (int start = 0; start < 2; start++) {
         logger.debug("Start {}", start);

         sf = createSessionFactory(locator);

         if (start == 0) {
            session = sf.createSession(true, false, false);
            session.commit(xid1, false);
            session.close();
         }

         session = sf.createSession(false, false, false);
         ClientConsumer cons1 = session.createConsumer(ADDRESS);
         session.start();
         for (int i = 0; i < 10; i++) {
            logger.info("I = {}", i);
            ClientMessage msg = cons1.receive(5000);
            assertNotNull(msg);
            assertEquals(1, msg.getIntProperty("txid").intValue());
            msg.acknowledge();
         }

         if (start == 1) {
            session.commit();
         } else {
            session.rollback();
         }

         session.close();
         sf.close();

         server.stop();
         server.start();
      }
      server.stop();

      validateNoFilesOnLargeDir(server.getConfiguration().getLargeMessagesDirectory(), 10);

      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(true, false, false);
      session.rollback(xid2);

      sf.close();

      server.stop();
      server.start();
      server.stop();

      validateNoFilesOnLargeDir();
   }

   @TestTemplate
   public void testRestartBeforeDelete() throws Exception {

      class NoPostACKQueue extends QueueImpl {

         NoPostACKQueue(QueueConfiguration queueConfiguration,
                               Filter filter,
                               PagingStore pagingStore,
                               PageSubscription pageSubscription,
                               ScheduledExecutorService scheduledExecutor,
                               PostOffice postOffice,
                               StorageManager storageManager,
                               HierarchicalRepository<AddressSettings> addressSettingsRepository,
                               ArtemisExecutor executor,
                               ActiveMQServer server,
                               QueueFactory factory) {
            super(queueConfiguration, filter, pagingStore, pageSubscription, scheduledExecutor, postOffice, storageManager, addressSettingsRepository, executor, server, factory);
         }

         @Override
         public void postAcknowledge(final MessageReference ref, AckReason reason) {
            logger.debug("Ignoring postACK on message {}", ref);
         }

         @Override
         public void deliverScheduledMessages() {
         }
      }

      final class NoPostACKQueueFactory implements QueueFactory {

         final StorageManager storageManager;

         final PostOffice postOffice;

         final ScheduledExecutorService scheduledExecutor;

         final HierarchicalRepository<AddressSettings> addressSettingsRepository;

         final ExecutorFactory execFactory;

         final ActiveMQServer server;

         NoPostACKQueueFactory(ActiveMQServer server,
                               StorageManager storageManager,
                               PostOffice postOffice,
                               ScheduledExecutorService scheduledExecutor,
                               HierarchicalRepository<AddressSettings> addressSettingsRepository,
                               final ExecutorFactory execFactory) {
            this.storageManager = storageManager;
            this.postOffice = postOffice;
            this.scheduledExecutor = scheduledExecutor;
            this.addressSettingsRepository = addressSettingsRepository;
            this.execFactory = execFactory;
            this.server = server;
         }

         @Override
         public Queue createQueueWith(QueueConfiguration config, PagingManager pagingManager, Filter filter) throws Exception {
            PageSubscription pageSubscription = QueueFactoryImpl.getPageSubscription(config, pagingManager, filter);
            return new NoPostACKQueue(config, filter, pageSubscription != null ? pageSubscription.getPagingStore() : null, pageSubscription, scheduledExecutor, postOffice, storageManager, addressSettingsRepository, execFactory.getExecutor(), server, this);
         }

         @Override
         public void setPostOffice(PostOffice postOffice) {
         }
      }

      ClientSession session = null;

      LargeMessageTestInterceptorIgnoreLastPacket.disableInterrupt();

      ActiveMQServer server = createServer(true, isNetty());
      server.start();

      QueueFactory original = server.getQueueFactory();

      ((ActiveMQServerImpl) server).replaceQueueFactory(new NoPostACKQueueFactory(server, server.getStorageManager(), server.getPostOffice(), server.getScheduledPool(), server.getAddressSettingsRepository(), server.getExecutorFactory()));

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);

      ClientSessionFactory sf = createSessionFactory(locator);

      session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      for (int i = 0; i < 10; i++) {
         Message clientFile = createLargeClientMessageStreaming(session, LARGE_MESSAGE_SIZE, true);

         producer.send(clientFile);
      }
      session.commit();

      session.close();

      session = sf.createSession(false, false);

      ClientConsumer cons = session.createConsumer(ADDRESS);

      session.start();

      for (int i = 0; i < 10; i++) {
         ClientMessage msg = cons.receive(5000);
         assertNotNull(msg);
         msg.saveToOutputStream(new java.io.OutputStream() {
            @Override
            public void write(int b) throws IOException {
            }
         });
         msg.acknowledge();
         session.commit();
      }

      ((ActiveMQServerImpl) server).replaceQueueFactory(original);
      server.fail(false);
      server.start();

      server.stop();

      validateNoFilesOnLargeDir();
   }

   @TestTemplate
   public void testConsumeAfterRestart() throws Exception {
      ClientSession session = null;

      LargeMessageTestInterceptorIgnoreLastPacket.clearInterrupt();

      ActiveMQServer server = createServer(true, isNetty());
      server.start();

      QueueFactory original = server.getQueueFactory();

      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);

      ClientSessionFactory sf = createSessionFactory(locator);

      session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      for (int i = 0; i < 10; i++) {
         Message clientFile = createLargeClientMessageStreaming(session, LARGE_MESSAGE_SIZE, true);

         producer.send(clientFile);
      }
      session.commit();

      session.close();
      sf.close();

      server.stop();
      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(false, false);

      ClientConsumer cons = session.createConsumer(ADDRESS);

      session.start();

      for (int i = 0; i < 10; i++) {
         ClientMessage msg = cons.receive(5000);
         assertNotNull(msg);
         msg.saveToOutputStream(new java.io.OutputStream() {
            @Override
            public void write(int b) throws IOException {
            }
         });
         msg.acknowledge();
         session.commit();
      }

      ((ActiveMQServerImpl) server).replaceQueueFactory(original);
      server.fail(false);
      server.start();

      server.stop();

      validateNoFilesOnLargeDir();
   }

   public static class LargeMessageTestInterceptorIgnoreLastPacket implements Interceptor {

      public static void clearInterrupt() {
         intMessages = true;
         latch = new CountDownLatch(1);
      }

      public static void disableInterrupt() {
         intMessages = false;
      }

      public static void awaitInterrupt() throws Exception {
         latch.await();
      }

      private static boolean intMessages = false;

      private static CountDownLatch latch = new CountDownLatch(1);

      @Override
      public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
         if (packet instanceof SessionContinuationMessage sessionContinuationMessage) {
            if (!sessionContinuationMessage.isContinues() && intMessages) {
               logger.debug("Ignored a message");
               latch.countDown();
               return false;
            }
         }
         return true;
      }
   }
}
