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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.management.MBeanServer;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
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
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionReceiveMessage;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.QueueFactoryImpl;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.server.impl.ServerSessionImpl;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This test will simulate a consumer hanging on the delivery packet due to unbehaved clients and it will make sure we
 * can still perform certain operations on the queue such as produce and verify the counters
 */
public class HangConsumerTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQServer server;

   private final SimpleString QUEUE = SimpleString.of("ConsumerTestQueue");

   private Queue queue;

   private ServerLocator locator;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      Configuration config = createDefaultInVMConfig().setMessageExpiryScanPeriod(10);

      ActiveMQSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), new SecurityConfiguration());

      server = addServer(new MyActiveMQServer(config, ManagementFactory.getPlatformMBeanServer(), securityManager));

      server.start();

      locator = createInVMNonHALocator();
   }

   @Test
   public void testHangOnDelivery() throws Exception {
      queue = server.createQueue(QueueConfiguration.of(QUEUE).setRoutingType(RoutingType.ANYCAST));
      try {

         ClientSessionFactory factory = locator.createSessionFactory();
         ClientSession sessionProducer = factory.createSession(false, false, false);

         ServerLocator consumerLocator = createInVMNonHALocator();
         ClientSessionFactory factoryConsumer = consumerLocator.createSessionFactory();
         ClientSession sessionConsumer = factoryConsumer.createSession();

         ClientProducer producer = sessionProducer.createProducer(QUEUE);

         ClientConsumer consumer = sessionConsumer.createConsumer(QUEUE);

         producer.send(sessionProducer.createMessage(true));

         blockConsumers();

         sessionProducer.commit();

         sessionConsumer.start();

         awaitBlocking();

         // this shouldn't lock
         producer.send(sessionProducer.createMessage(true));
         sessionProducer.commit();

         // These three operations should finish without the test hanging
         queue.getMessagesAdded();
         queue.getMessageCount();

         releaseConsumers();

         // a rollback to make sure everything will be reset on the deliveries
         // and that both consumers will receive each a message
         // this is to guarantee the server will have both consumers regsitered
         sessionConsumer.rollback();

         // a flush to guarantee any pending task is finished on flushing out delivery and pending msgs
         queue.flushExecutor();
         Wait.waitFor(() -> getMessageCount(queue) == 2);
         Wait.assertEquals(2, queue::getMessageCount);
         Wait.assertEquals(2, queue::getMessagesAdded);

         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();

         msg = consumer.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();

         sessionProducer.commit();
         sessionConsumer.commit();

         sessionProducer.close();
         sessionConsumer.close();
      } finally {
         releaseConsumers();
      }
   }

   protected void releaseConsumers() {
      callbackSemaphore.release();
   }

   protected void awaitBlocking() throws InterruptedException {
      assertTrue(this.inCall.await(5000));
   }

   protected void blockConsumers() throws InterruptedException {
      this.callbackSemaphore.acquire();
   }

   /**
    * This would recreate the scenario where a queue was duplicated
    */
   @Test
   public void testHangDuplicateQueues() throws Exception {
      final Semaphore blocked = new Semaphore(1);
      final CountDownLatch latchDelete = new CountDownLatch(1);
      class MyQueueWithBlocking extends QueueImpl {

         MyQueueWithBlocking(final QueueConfiguration queueConfiguration,
                             final Filter filter,
                             final PagingStore pagingStore,
                             final PageSubscription pageSubscription,
                             final ScheduledExecutorService scheduledExecutor,
                             final PostOffice postOffice,
                             final StorageManager storageManager,
                             final HierarchicalRepository<AddressSettings> addressSettingsRepository,
                             final ArtemisExecutor executor, final ActiveMQServer server) {
            super(queueConfiguration,
                  filter,
                  pagingStore,
                  pageSubscription,
                  scheduledExecutor,
                  postOffice,
                  storageManager,
                  addressSettingsRepository,
                  executor,
                  server,
                  null);
         }

         @Override
         public boolean allowsReferenceCallback() {
            return false;
         }

         @Override
         public synchronized int deleteMatchingReferences(final int flushLimit, final Filter filter) throws Exception {
            latchDelete.countDown();
            blocked.acquire();
            blocked.release();
            return super.deleteMatchingReferences(flushLimit, filter);
         }

         @Override
         public void deliverScheduledMessages() {
         }
      }

      class LocalFactory extends QueueFactoryImpl {

         LocalFactory(final ExecutorFactory executorFactory,
                      final ScheduledExecutorService scheduledExecutor,
                      final HierarchicalRepository<AddressSettings> addressSettingsRepository,
                      final StorageManager storageManager, final ActiveMQServer server) {
            super(executorFactory, scheduledExecutor, addressSettingsRepository, storageManager, server);
         }

         @Override
         public Queue createQueueWith(final QueueConfiguration config, PagingManager pagingManager, Filter filter) {
            PageSubscription pageSubscription = getPageSubscription(config, pagingManager, filter);
            queue = new MyQueueWithBlocking(config, filter, pageSubscription != null ? pageSubscription.getPagingStore() : null, pageSubscription, scheduledExecutor,
                                            postOffice, storageManager, addressSettingsRepository,
                                            executorFactory.getExecutor(), server);
            return queue;
         }
      }

      LocalFactory queueFactory = new LocalFactory(server.getExecutorFactory(), server.getScheduledPool(), server.getAddressSettingsRepository(), server.getStorageManager(), server);

      queueFactory.setPostOffice(server.getPostOffice());

      ((ActiveMQServerImpl) server).replaceQueueFactory(queueFactory);

      queue = server.createQueue(QueueConfiguration.of(QUEUE).setRoutingType(RoutingType.ANYCAST));

      blocked.acquire();

      ClientSessionFactory factory = locator.createSessionFactory();
      ClientSession session = factory.createSession(false, false, false);

      ClientProducer producer = session.createProducer(QUEUE);

      producer.send(session.createMessage(true));
      session.commit();

      Thread tDelete = new Thread(() -> {
         try {
            server.destroyQueue(QUEUE);
         } catch (Exception e) {
            e.printStackTrace();
         }
      });

      tDelete.start();

      assertTrue(latchDelete.await(10, TimeUnit.SECONDS));

      try {
         server.createQueue(QueueConfiguration.of(QUEUE).setRoutingType(RoutingType.ANYCAST));
      } catch (Exception expected) {
      }

      blocked.release();

      server.stop();

      tDelete.join();

      session.close();

      // a duplicate binding would impede the server from starting
      server.start();
      waitForServerToStart(server);

      server.stop();

   }

   /**
    * This would force a journal duplication on bindings even with the scenario that generated fixed, the server
    * shouldn't hold of from starting
    */
   @Test
   public void testForceDuplicationOnBindings() throws Exception {
      queue = server.createQueue(QueueConfiguration.of(QUEUE).setRoutingType(RoutingType.ANYCAST));

      ClientSessionFactory factory = locator.createSessionFactory();
      ClientSession session = factory.createSession(false, false, false);

      ClientProducer producer = session.createProducer(QUEUE);

      producer.send(session.createMessage(true));
      session.commit();

      long queueID = server.getStorageManager().generateID();
      long txID = server.getStorageManager().generateID();

      // Forcing a situation where the server would unexpectedly create a duplicated queue. The server should still start normally
      LocalQueueBinding newBinding = new LocalQueueBinding(QUEUE,
                                                           new QueueImpl(QueueConfiguration.of(QUEUE).setRoutingType(RoutingType.ANYCAST).setId(queueID), null, null, null, null, null, null, null, null, server, null),
                                                           server.getNodeID());
      server.getStorageManager().addQueueBinding(txID, newBinding);
      server.getStorageManager().commitBindings(txID);

      server.stop();

      // a duplicate binding would impede the server from starting
      server.start();
      waitForServerToStart(server);

      server.stop();

   }

   // An exception during delivery shouldn't make the message disappear
   @Test
   public void testExceptionWhileDelivering() throws Exception {
      queue = server.createQueue(QueueConfiguration.of(QUEUE).setRoutingType(RoutingType.ANYCAST));

      HangInterceptor hangInt = new HangInterceptor();
      try {
         locator.addIncomingInterceptor(hangInt);

         ClientSessionFactory factory = locator.createSessionFactory();
         ClientSession session = factory.createSession(false, false, false);

         ClientProducer producer = session.createProducer(QUEUE);

         ClientConsumer consumer = session.createConsumer(QUEUE);

         producer.send(session.createMessage(true));
         session.commit();

         hangInt.close();

         session.start();

         assertTrue(hangInt.reusableLatch.await(10, TimeUnit.SECONDS));

         hangInt.pendingException = new ActiveMQException();

         hangInt.open();

         session.close();

         session = factory.createSession(false, false);
         session.start();

         consumer = session.createConsumer(QUEUE);

         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();

         session.commit();
      } finally {
         hangInt.open();
      }

   }

   /**
    * This will simulate what would happen with topic creationg where a single record is supposed to be created on the
    * journal
    */
   @Test
   public void testDuplicateDestinationsOnTopic() throws Exception {
      try {
         for (int i = 0; i < 5; i++) {
            if (server.locateQueue(SimpleString.of("tt")) == null) {
               server.createQueue(QueueConfiguration.of("tt").setRoutingType(RoutingType.ANYCAST).setFilterString(Filter.GENERIC_IGNORED_FILTER));
            }

            server.stop();

            SequentialFileFactory messagesFF = new NIOSequentialFileFactory(server.getConfiguration().getBindingsLocation(), null, 1);

            JournalImpl messagesJournal = new JournalImpl(1024 * 1024, 2, 2, 0, 0, messagesFF, "artemis-bindings", "bindings", 1);

            messagesJournal.start();

            LinkedList<RecordInfo> infos = new LinkedList<>();

            messagesJournal.load(infos, null, null);

            int bindings = 0;
            for (RecordInfo info : infos) {
               logger.debug("info: {}", info);
               if (info.getUserRecordType() == JournalRecordIds.QUEUE_BINDING_RECORD) {
                  bindings++;
               }
            }
            assertEquals(1, bindings);

            logger.debug("Bindings: {}", bindings);
            messagesJournal.stop();
            if (i < 4)
               server.start();
         }
      } finally {
         try {
            server.stop();
         } catch (Throwable ignored) {
         }
      }
   }

   ReusableLatch inCall = new ReusableLatch(1);
   Semaphore callbackSemaphore = new Semaphore(1);

   class MyCallback implements SessionCallback {

      @Override
      public boolean hasCredits(ServerConsumer consumerID) {
         return true;
      }

      final SessionCallback targetCallback;

      MyCallback(SessionCallback parameter) {
         this.targetCallback = parameter;
      }

      @Override
      public void sendProducerCreditsMessage(int credits, SimpleString address) {
         targetCallback.sendProducerCreditsMessage(credits, address);
      }

      @Override
      public boolean updateDeliveryCountAfterCancel(ServerConsumer consumer, MessageReference ref, boolean failed) {
         return false;
      }

      @Override
      public void browserFinished(ServerConsumer consumer) {
      }

      @Override
      public boolean isWritable(ReadyListener callback, Object protocolContext) {
         return true;
      }

      @Override
      public void afterDelivery() throws Exception {
      }

      @Override
      public void sendProducerCreditsFailMessage(int credits, SimpleString address) {
         targetCallback.sendProducerCreditsFailMessage(credits, address);
      }

      @Override
      public int sendMessage(MessageReference ref, ServerConsumer consumer, int deliveryCount) {
         inCall.countDown();
         try {
            callbackSemaphore.acquire();
         } catch (InterruptedException e) {
            inCall.countUp();
            return -1;
         }

         try {
            return targetCallback.sendMessage(ref, consumer, deliveryCount);
         } finally {
            callbackSemaphore.release();
            inCall.countUp();
         }
      }

      @Override
      public int sendLargeMessage(MessageReference ref,
                                  ServerConsumer consumer,
                                  long bodySize,
                                  int deliveryCount) {
         return targetCallback.sendLargeMessage(ref, consumer, bodySize, deliveryCount);
      }

      @Override
      public int sendLargeMessageContinuation(ServerConsumer consumer,
                                              byte[] body,
                                              boolean continues,
                                              boolean requiresResponse) {
         return targetCallback.sendLargeMessageContinuation(consumer, body, continues, requiresResponse);
      }

      @Override
      public void closed() {
         targetCallback.closed();
      }

      @Override
      public void disconnect(ServerConsumer consumerId, String errorMessage) {
      }
   }

   class MyActiveMQServer extends ActiveMQServerImpl {

      MyActiveMQServer(Configuration configuration, MBeanServer mbeanServer, ActiveMQSecurityManager securityManager) {
         super(configuration, mbeanServer, securityManager);
      }

      @Override
      protected ServerSessionImpl internalCreateSession(String name,
                                                        String username,
                                                        String password,
                                                        String validatedUser,
                                                        int minLargeMessageSize,
                                                        RemotingConnection connection,
                                                        boolean autoCommitSends,
                                                        boolean autoCommitAcks,
                                                        boolean preAcknowledge,
                                                        boolean xa,
                                                        String defaultAddress,
                                                        SessionCallback callback,
                                                        OperationContext context,
                                                        boolean autoCreateQueue,
                                                        Map<SimpleString, RoutingType> prefixes,
                                                        String securityDomain,
                                                        boolean isLegacyProducer) throws Exception {
         return new ServerSessionImpl(name, username, password, validatedUser, minLargeMessageSize, autoCommitSends, autoCommitAcks, preAcknowledge, getConfiguration().isPersistDeliveryCountBeforeDelivery(), xa, connection, this, defaultAddress == null ? null : SimpleString.of(defaultAddress), new MyCallback(callback), context, prefixes, securityDomain, isLegacyProducer);
      }
   }

   class HangInterceptor implements Interceptor {

      Semaphore semaphore = new Semaphore(1);

      ReusableLatch reusableLatch = new ReusableLatch(1);

      volatile ActiveMQException pendingException = null;

      public void close() throws Exception {
         semaphore.acquire();
      }

      public void open() throws Exception {
         semaphore.release();
      }

      @Override
      public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
         if (packet instanceof SessionReceiveMessage) {
            logger.debug("Receiving message");
            try {
               reusableLatch.countDown();
               semaphore.acquire();
               semaphore.release();
               reusableLatch.countUp();
            } catch (Exception e) {
               e.printStackTrace();
            }
         }

         if (pendingException != null) {
            ActiveMQException exToThrow = pendingException;
            pendingException = null;
            throw exToThrow;
         }
         return true;
      }

   }
}
