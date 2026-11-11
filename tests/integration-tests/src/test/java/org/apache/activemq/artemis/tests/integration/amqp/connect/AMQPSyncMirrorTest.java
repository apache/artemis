/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.amqp.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSConstants;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.journal.JournalUpdateCallback;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AMQPSyncMirrorTest extends AmqpClientTestSupport {

   Logger logger = LoggerFactory.getLogger(AMQPSyncMirrorTest.class);

   private static final String SLOW_SERVER_NAME = "slow";
   private static final int SLOW_SERVER_PORT = AMQP_PORT + 1;

   private ActiveMQServer slowServer;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP,OPENWIRE,CORE";
   }

   @Test
   public void testPersistedSendAMQP() throws Exception {
      testPersistedSend("AMQP", false, 100);
   }

   @Test
   public void testPersistedSendAMQPLarge() throws Exception {
      testPersistedSend("AMQP", false, 200 * 1024);
   }

   @Test
   public void testPersistedSendCore() throws Exception {
      testPersistedSend("CORE", false, 100);
   }

   @Test
   public void testPersistedSendCoreLarge() throws Exception {
      testPersistedSend("CORE", false, 200 * 1024);
   }

   @Test
   public void testPersistedSendAMQPTXLarge() throws Exception {
      testPersistedSend("AMQP", true, 200 * 1024);
   }

   @Test
   public void testPersistedSendAMQPTX() throws Exception {
      testPersistedSend("AMQP", true, 100);
   }

   @Test
   public void testPersistedSendCoreTX() throws Exception {
      testPersistedSend("CORE", true, 100);
   }

   @Test
   public void testPersistedSendCoreTXLarge() throws Exception {
      testPersistedSend("CORE", true, 200 * 1024);
   }

   private void testPersistedSend(String protocol, boolean transactional, int messageSize) throws Exception {
      ReusableLatch sendPending = new ReusableLatch(0);
      Semaphore semSend = new Semaphore(1);
      Semaphore semAck = new Semaphore(1);
      AtomicInteger errors = new AtomicInteger(0);

      try {
         final int NUMBER_OF_MESSAGES = 10;

         AtomicInteger countStored = new AtomicInteger(0);

         slowServer = createServerWithCallbackStorage(SLOW_SERVER_PORT, SLOW_SERVER_NAME, (isUpdate, isTX, txId, id, recordType, persister, record) -> {
            if (logger.isDebugEnabled()) {
               logger.debug("StorageCallback::slow isUpdate={}, isTX={}, txID={}, id={},recordType={}, record={}", isUpdate, isTX, txId, id, recordType, record);
            }
            if (transactional) {
               if (isTX) {
                  try {
                     if (countStored.get() > 0) {
                        countStored.incrementAndGet();
                        logger.debug("semSend.tryAcquire");
                        if (semSend.tryAcquire(20, TimeUnit.SECONDS)) {
                           logger.debug("acquired TX, now release");
                           semSend.release();
                        }
                     }
                  } catch (Exception e) {
                     logger.warn(e.getMessage(), e);
                  }
               }
            }
            if (recordType == JournalRecordIds.ACKNOWLEDGE_REF) {
               logger.debug("slow ACK REF");
               try {
                  if (semAck.tryAcquire(20, TimeUnit.SECONDS)) {
                     semAck.release();
                     logger.debug("slow acquired ACK semaphore");
                  } else {
                     logger.debug("Semaphore wasn't acquired");
                  }
               } catch (Exception e) {
                  logger.warn(e.getMessage(), e);
               }
            }
            if (recordType == JournalRecordIds.ADD_MESSAGE_PROTOCOL ||
                recordType == JournalRecordIds.ADD_LARGE_MESSAGE) {
               try {
                  countStored.incrementAndGet();
                  if (!transactional) {
                     logger.debug("semSend.tryAcquire");
                     if (semSend.tryAcquire(20, TimeUnit.SECONDS)) {
                        logger.debug("acquired non TX now release");
                        semSend.release();
                     }
                  }
               } catch (Exception e) {
                  logger.warn(e.getMessage(), e);
                  errors.incrementAndGet();
               }
            }
         });
         slowServer.setIdentity("slowServer");
         server.setIdentity("server");

         ExecutorService pool = Executors.newFixedThreadPool(5);
         runAfter(pool::shutdown);

         configureMirrorTowardsSlow(server);

         slowServer.getConfiguration().setName("slow");
         server.getConfiguration().setName("fast");
         slowServer.start();
         server.start();

         waitForServerToStart(slowServer);
         waitForServerToStart(server);

         server.addAddressInfo(new AddressInfo(getQueueName()).addRoutingType(RoutingType.ANYCAST).setAutoCreated(false));
         server.createQueue(QueueConfiguration.of(getQueueName()).setRoutingType(RoutingType.ANYCAST).setAddress(getQueueName()).setAutoCreated(false));

         Wait.waitFor(() -> slowServer.locateQueue(getQueueName()) != null);
         Queue replicatedQueue = slowServer.locateQueue(getQueueName());

         ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:" + AMQP_PORT);

         if (factory instanceof ActiveMQConnectionFactory connectionFactory) {
            connectionFactory.getServerLocator().setBlockOnAcknowledge(true);
         }

         Connection connection = factory.createConnection();
         runAfter(connection::close);
         Session session = connection.createSession(transactional, transactional ? Session.SESSION_TRANSACTED : Session.CLIENT_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(session.createQueue(getQueueName()));

         connection.start();

         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         final String bodyMessage = "large Buffer...".repeat(messageSize);

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            logger.debug("===>>> send message {}", i);
            int theI = i;
            sendPending.countUp();
            logger.debug("semSend.acquire");
            semSend.acquire();
            if (!transactional) {
               pool.execute(() -> {
                  try {
                     logger.debug("Entering non TX send with sendPending = {}", sendPending.getCount());
                     TextMessage message = session.createTextMessage(bodyMessage);
                     message.setStringProperty("strProperty", "" + theI);
                     producer.send(message);
                     sendPending.countDown();
                     logger.debug("leaving non TX send with sendPending = {}", sendPending.getCount());
                  } catch (Throwable e) {
                     logger.warn(e.getMessage(), e);
                     errors.incrementAndGet();
                  }
               });
            } else {
               CountDownLatch sendDone = new CountDownLatch(1);
               pool.execute(() -> {
                  try {
                     TextMessage message = session.createTextMessage(bodyMessage);
                     message.setStringProperty("strProperty", "" + theI);
                     producer.send(message);
                  } catch (Throwable e) {
                     errors.incrementAndGet();
                     logger.warn(e.getMessage(), e);
                  }
                  sendDone.countDown();
               });

               Wait.assertEquals(i, replicatedQueue::getMessageCount);

               assertTrue(sendDone.await(10, TimeUnit.SECONDS));

               pool.execute(() -> {
                  try {
                     session.commit();
                     sendPending.countDown();
                  } catch (Throwable e) {
                     logger.warn(e.getMessage(), e);
                  }
               });
            }

            assertFalse(sendPending.await(10, TimeUnit.MILLISECONDS), "sendPending.await() not supposed to succeed");
            logger.debug("semSend.release");
            semSend.release();
            assertTrue(sendPending.await(10, TimeUnit.SECONDS));
            Wait.assertEquals(i + 1, replicatedQueue::getMessageCount);
         }

         if (!transactional) {
            Wait.assertEquals(NUMBER_OF_MESSAGES, countStored::get);
         }
         Wait.assertEquals(NUMBER_OF_MESSAGES, replicatedQueue::getMessageCount);

         connection.start();

         Session clientSession = transactional ? connection.createSession(true, Session.AUTO_ACKNOWLEDGE) : connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer consumer = clientSession.createConsumer(clientSession.createQueue(getQueueName()));

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            logger.debug("===<<< Receiving message {}", i);
            Message message = consumer.receive(5000);
            assertNotNull(message);
            semAck.acquire();

            CountDownLatch countDownLatch = new CountDownLatch(1);
            pool.execute(() -> {
               try {
                  if (transactional) {
                     clientSession.commit();
                  } else {
                     message.acknowledge();
                  }
               } catch (Exception e) {
                  logger.warn(e.getMessage(), e);
                  errors.incrementAndGet();
               } finally {
                  countDownLatch.countDown();
               }
            });

            if (!transactional && protocol.equals("AMQP")) {
               // non transactional ack in AMQP is always async. No need to verify anything else here
               logger.debug("non transactional and amqp is always asynchronous. No need to verify anything");
            } else {
               assertFalse(countDownLatch.await(10, TimeUnit.MILLISECONDS));
            }

            semAck.release();
            assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
            Wait.assertEquals(NUMBER_OF_MESSAGES - i - 1, replicatedQueue::getMessageCount);
         }

         assertEquals(0, errors.get());
      } finally {
         semAck.release();
         semSend.release();
      }
   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      ActiveMQServer server = createServerWithCallbackStorage(AMQP_PORT, "fastServer", (isUpdate, isTX, txId, id, recordType, persister, record) -> {
         if (logger.isDebugEnabled()) {
            logger.debug("StorageCallback::fast isUpdate={}, isTX={}, txID={}, id={},recordType={}, record={}", isUpdate, isTX, txId, id, recordType, record);
         }
      });
      addServer(server);
      return server;
   }

   private void configureMirrorTowardsSlow(ActiveMQServer source) {
      AMQPBrokerConnectConfiguration connection = new AMQPBrokerConnectConfiguration("mirror", "tcp://localhost:" + SLOW_SERVER_PORT).setReconnectAttempts(-1).setRetryInterval(100);
      AMQPMirrorBrokerConnectionElement replication = new AMQPMirrorBrokerConnectionElement().setDurable(true).setSync(true).setMessageAcknowledgements(true);
      connection.addElement(replication);

      source.getConfiguration().addAMQPConnection(connection);
   }

   private ActiveMQServer createServerWithCallbackStorage(int port, String name, StorageCallback storageCallback) throws Exception {
      ActiveMQSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), new SecurityConfiguration());
      ActiveMQServer server = new ActiveMQServerImpl(createBasicConfig(port), mBeanServer, securityManager) {
         @Override
         protected StorageManager createStorageManager() {
            return AMQPSyncMirrorTest.this.createCallbackStorageManager(getConfiguration(), getCriticalAnalyzer(), executorFactory, scheduledPool, ioExecutorFactory, ioCriticalErrorListener, storageCallback);
         }
      };

      server.getConfiguration().setName(name);
      server.getConfiguration().getAcceptorConfigurations().clear();
      server.getConfiguration().getAcceptorConfigurations().add(addAcceptorConfiguration(slowServer, port));
      server.getConfiguration().setMessageExpiryScanPeriod(-1);

      server.getConfiguration().setJMXManagementEnabled(true);

      configureAddressPolicy(server);
      configureBrokerSecurity(server);

      addServer(server);

      return server;
   }

   private interface StorageCallback {

      void storage(boolean isUpdate,
                   boolean isCommit,
                   long txID,
                   long id,
                   byte recordType,
                   Persister persister,
                   Object record);
   }

   private StorageManager createCallbackStorageManager(Configuration configuration,
                                               CriticalAnalyzer criticalAnalyzer,
                                               ExecutorFactory executorFactory,
                                               ScheduledExecutorService scheduledPool,
                                               ExecutorFactory ioExecutorFactory,
                                               IOCriticalErrorListener ioCriticalErrorListener,
                                               StorageCallback storageCallback) {
      return new JournalStorageManager(configuration, criticalAnalyzer, executorFactory, scheduledPool, ioExecutorFactory, ioCriticalErrorListener) {
         @Override
         protected Journal createMessageJournal(Configuration config,
                                                IOCriticalErrorListener criticalErrorListener,
                                                int fileSize) {
            return new JournalImpl(ioExecutorFactory, fileSize, config.getJournalMinFiles(), config.getJournalPoolFiles(), config.getJournalCompactMinFiles(), config.getJournalCompactPercentage(), config.getJournalFileOpenTimeout(), journalFF, "activemq-data", "amq", journalFF.getMaxIO(), 0, criticalErrorListener, config.getJournalMaxAtticFiles()) {
               @Override
               public void appendAddRecordTransactional(long txID,
                                                        long id,
                                                        byte recordType,
                                                        Persister persister,
                                                        Object record) throws Exception {
                  storageCallback.storage(false, false, txID, id, recordType, persister, record);
                  super.appendAddRecordTransactional(txID, id, recordType, persister, record);
               }

               @Override
               public void appendAddRecord(long id,
                                           byte recordType,
                                           Persister persister,
                                           Object record,
                                           boolean sync,
                                           IOCompletion callback) throws Exception {
                  storageCallback.storage(false, false, -1, id, recordType, persister, record);
                  super.appendAddRecord(id, recordType, persister, record, sync, callback);
               }

               @Override
               public void appendUpdateRecord(long id,
                                              byte recordType,
                                              EncodingSupport record,
                                              boolean sync) throws Exception {
                  storageCallback.storage(true, false, -1, id, recordType, null, record);
                  super.appendUpdateRecord(id, recordType, record, sync);
               }

               @Override
               public void appendUpdateRecordTransactional(long txID,
                                                           long id,
                                                           byte recordType,
                                                           EncodingSupport record) throws Exception {
                  storageCallback.storage(true, false, txID, id, recordType, null, record);
                  super.appendUpdateRecordTransactional(txID, id, recordType, record);
               }

               @Override
               public void appendCommitRecord(long txID,
                                              boolean sync,
                                              IOCompletion callback,
                                              boolean lineUpContext) throws Exception {
                  storageCallback.storage(false, true, txID, txID, (byte) 0, null, null);
                  super.appendCommitRecord(txID, sync, callback, lineUpContext);
               }

               @Override
               public void tryAppendUpdateRecord(long id,
                                          byte recordType,
                                          Persister persister,
                                          Object record,
                                          boolean sync,
                                          boolean replaceableUpdate,
                                          JournalUpdateCallback updateCallback,
                                          IOCompletion callback) throws Exception {
                     storageCallback.storage(true, false, -1, -1, recordType, persister, record);
                     super.tryAppendUpdateRecord(id, recordType, persister, record, sync, replaceableUpdate, updateCallback, callback);
               }
            };
         }
      };
   }


   @Test
   public void testSimpleACK_TX_AMQP() throws Exception {
      testSimpleAckSync("AMQP", true, false, 1024);
   }

   @Test
   public void testSimpleACK_TX_CORE() throws Exception {
      testSimpleAckSync("CORE", true, false, 1024);
   }

   @Test
   public void testSimpleACK_NoTX_AMQP() throws Exception {
      testSimpleAckSync("AMQP", false, false, 1024);
   }

   @Test
   public void testSimpleACK_NoTX_CORE() throws Exception {
      testSimpleAckSync("CORE", false, false, 1024);
   }

   @Test
   public void testSimpleACK_NoTX_CORE_Large() throws Exception {
      testSimpleAckSync("CORE", false, false, 255 * 1024);
   }

   @Test
   public void testSimpleACK_TX_CORE_Large() throws Exception {
      testSimpleAckSync("CORE", true, false, 255 * 1024);
   }

   @Test
   public void testSimple_Core_Individual_Large() throws Exception {
      testSimpleAckSync("CORE", false, true, 255 * 1024);
   }

   @Test
   public void testSimple_Core_Individual() throws Exception {
      testSimpleAckSync("CORE", false, true, 1024);
   }

   public void testSimpleAckSync(final String protocol, final boolean tx, final boolean individualAck, int messageSize) throws Exception {
      AtomicInteger errors = new AtomicInteger(0);

      final int NUMBER_OF_MESSAGES = 10;

      slowServer = createServerWithCallbackStorage(SLOW_SERVER_PORT, SLOW_SERVER_NAME, (isUpdate, isTX, txId, id, recordType, persister, record) -> {
      });
      slowServer.setIdentity("slowServer");
      server.setIdentity("server");

      ExecutorService pool = Executors.newFixedThreadPool(5);
      runAfter(pool::shutdown);

      configureMirrorTowardsSlow(server);

      slowServer.getConfiguration().setName("slow");
      server.getConfiguration().setName("fast");
      slowServer.start();
      server.start();

      waitForServerToStart(slowServer);
      waitForServerToStart(server);

      Wait.waitFor(() -> server.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirror") != null, 5000);
      Queue snf = server.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_mirror");
      assertNotNull(snf);
      // Mirror is configured to block, we cannot allow anything other than block
      assertEquals(AddressFullMessagePolicy.BLOCK, snf.getPagingStore().getAddressFullMessagePolicy());

      server.addAddressInfo(new AddressInfo(getQueueName()).addRoutingType(RoutingType.ANYCAST).setAutoCreated(false));
      server.createQueue(QueueConfiguration.of(getQueueName()).setRoutingType(RoutingType.ANYCAST).setAddress(getQueueName()).setAutoCreated(false));

      Wait.waitFor(() -> slowServer.locateQueue(getQueueName()) != null);
      Queue replicatedQueue = slowServer.locateQueue(getQueueName());

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:" + AMQP_PORT);

      if (factory instanceof ActiveMQConnectionFactory connectionFactory) {
         connectionFactory.getServerLocator().setBlockOnAcknowledge(true);
      }

      Connection connection = factory.createConnection();
      runAfter(connection::close);
      Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(session.createQueue(getQueueName()));

      connection.start();

      producer.setDeliveryMode(DeliveryMode.PERSISTENT);

      final String bodyMessage = "large Buffer...".repeat(messageSize);

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         int theI = i;
         TextMessage message = session.createTextMessage(bodyMessage);
         message.setStringProperty("strProperty", "" + theI);
         producer.send(message);
         Wait.assertEquals(i + 1, replicatedQueue::getMessageCount, 5000);
      }

      Wait.assertEquals(NUMBER_OF_MESSAGES, replicatedQueue::getMessageCount);

      connection.start();

      Session clientSession = connection.createSession(tx, tx ? Session.SESSION_TRANSACTED : (individualAck ? ActiveMQJMSConstants.INDIVIDUAL_ACKNOWLEDGE : Session.CLIENT_ACKNOWLEDGE));
      MessageConsumer consumer = clientSession.createConsumer(clientSession.createQueue(getQueueName()));

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         Message message = consumer.receive(5000);
         assertNotNull(message);

         message.acknowledge();

         if (tx) {
            clientSession.commit();
         }

         Wait.assertEquals(NUMBER_OF_MESSAGES - i - 1, replicatedQueue::getMessageCount, 5000);
      }

      assertEquals(0, errors.get());
   }

}