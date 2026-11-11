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
package org.apache.activemq.artemis.tests.integration.openwire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQMessageConsumer;
import org.apache.activemq.ActiveMQPrefetchPolicy;
import org.apache.activemq.RedeliveryPolicy;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.cli.commands.tools.PrintData;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.LocalTransactionId;
import org.apache.activemq.command.MessageAck;
import org.apache.activemq.command.TransactionId;
import org.apache.activemq.command.TransactionInfo;
import org.apache.activemq.transport.Transport;
import org.apache.activemq.transport.failover.FailoverTransport;
import org.apache.activemq.transport.tcp.TcpTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrefetchRedeliveryCountOpenwireTest extends OpenWireTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      realStore = true;
      super.setUp();
   }

   @Override
   protected void configureAddressSettings(Map<String, AddressSettings> addressSettingsMap) {
      super.configureAddressSettings(addressSettingsMap);
      // force send to dlq early
      addressSettingsMap.put("exampleQueue", new AddressSettings().setAutoCreateQueues(false).setAutoCreateAddresses(false).setDeadLetterAddress(SimpleString.of("ActiveMQ.DLQ")).setAutoCreateAddresses(true).setMaxDeliveryAttempts(2));
      // force send to dlq late
      addressSettingsMap.put("exampleQueueTwo", new AddressSettings().setAutoCreateQueues(false).setAutoCreateAddresses(false).setDeadLetterAddress(SimpleString.of("ActiveMQ.DLQ")).setAutoCreateAddresses(true).setMaxDeliveryAttempts(-1));
   }

   @Override
   protected void extraServerConfig(Configuration serverConfig) {
      // useful to debug if there is some contention that causes a concurrent rollback,
      // when true, the rollback journal update uses the operation context and the failure propagates
      //serverConfig.setJournalSyncTransactional(true);
   }

   @Test
   @Timeout(60)
   public void testConsumerSingleMessageLoopExclusive() throws Exception {
      doTestConsumerSingleMessageLoop(true);
   }

   @Test
   @Timeout(60)
   public void testConsumerSingleMessageLoopNonExclusive() throws Exception {
      doTestConsumerSingleMessageLoop(false);
   }

   public void doTestConsumerSingleMessageLoop(boolean exclusive) throws Exception {
      Connection exConn = null;

      SimpleString durableQueue = SimpleString.of("exampleQueue");
      this.server.createQueue(QueueConfiguration.of(durableQueue).setRoutingType(RoutingType.ANYCAST).setExclusive(exclusive));

      try {
         ActiveMQConnectionFactory exFact = new ActiveMQConnectionFactory();
         exFact.setWatchTopicAdvisories(false);

         Queue queue = new ActiveMQQueue("exampleQueue");

         exConn = exFact.createConnection();

         exConn.start();

         Session session = exConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer producer = session.createProducer(queue);

         TextMessage message = session.createTextMessage("This is a text message");

         int numMessages = 20;
         for (int i = 0; i < numMessages; i++) {
            producer.send(message);
         }

         for (int i = 0; i < numMessages; i++) {
            // consumer per message
            MessageConsumer messageConsumer = session.createConsumer(queue);

            TextMessage messageReceived = (TextMessage) messageConsumer.receive(5000);
            assertNotNull(messageReceived);

            assertEquals("This is a text message", messageReceived.getText());
            messageConsumer.close();
         }
      } finally {
         if (exConn != null) {
            exConn.close();
         }
      }
   }

   @Test
   @Timeout(60)
   public void testExclusiveConsumerOrderOnReconnectionLargePrefetch() throws Exception {
      Connection exConn = null;

      SimpleString durableQueue = SimpleString.of("exampleQueueTwo");
      this.server.createQueue(QueueConfiguration.of(durableQueue).setRoutingType(RoutingType.ANYCAST).setExclusive(true));

      try {
         ActiveMQConnectionFactory exFact = new ActiveMQConnectionFactory();
         exFact.setWatchTopicAdvisories(false);

         ActiveMQPrefetchPolicy prefetchPastMaxDeliveriesInLoop = new ActiveMQPrefetchPolicy();
         prefetchPastMaxDeliveriesInLoop.setAll(2000);
         exFact.setPrefetchPolicy(prefetchPastMaxDeliveriesInLoop);

         RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
         redeliveryPolicy.setMaximumRedeliveries(4000);
         exFact.setRedeliveryPolicy(redeliveryPolicy);

         Queue queue = new ActiveMQQueue("exampleQueueTwo");

         exConn = exFact.createConnection();

         exConn.start();

         Session session = exConn.createSession(true, Session.AUTO_ACKNOWLEDGE);

         MessageProducer producer = session.createProducer(queue);

         TextMessage message = session.createTextMessage("This is a text message");

         int numMessages = 10000;
         for (int i = 0; i < numMessages; i++) {
            message.setIntProperty("SEQ", i);
            producer.send(message);
         }
         session.commit();
         exConn.close();

         final int batch = 200;
         for (int i = 0; i < numMessages; i += batch) {
            // connection per batch
            exConn = exFact.createConnection();
            exConn.start();

            session = exConn.createSession(true, Session.SESSION_TRANSACTED);

            MessageConsumer messageConsumer = session.createConsumer(queue);
            TextMessage messageReceived;
            for (int j = 0; j < batch; j++) { // a small batch
               messageReceived = (TextMessage) messageConsumer.receive(5000);
               assertNotNull(messageReceived, "null @ i=" + i);
               assertEquals(i + j, messageReceived.getIntProperty("SEQ"));

               assertEquals("This is a text message", messageReceived.getText());
            }
            session.commit();

            // force a local socket close such that the broker sees an exception on the connection and fails the consumer via close
            ((org.apache.activemq.ActiveMQConnection)exConn).getTransport().narrow(FailoverTransport.class).stop();
            exConn.close();
         }
      } finally {
         if (exConn != null) {
            exConn.close();
         }
      }
   }

   @Test
   @Timeout(60)
   public void testServerSideRollbackOnCloseOrder() throws Exception {

      final ArrayList<Throwable> errors = new ArrayList<>();
      SimpleString durableQueue = SimpleString.of("exampleQueueTwo");
      this.server.createQueue(QueueConfiguration.of(durableQueue).setRoutingType(RoutingType.ANYCAST).setExclusive(true));

      Queue queue = new ActiveMQQueue(durableQueue.toString());

      final ActiveMQConnectionFactory exFact = new ActiveMQConnectionFactory("failover:(tcp://localhost:61616?closeAsync=false)?startupMaxReconnectAttempts=10&maxReconnectAttempts=0&timeout=1000");
      exFact.setWatchTopicAdvisories(false);
      exFact.setConnectResponseTimeout(10000);

      ActiveMQPrefetchPolicy prefetchPastMaxDeliveriesInLoop = new ActiveMQPrefetchPolicy();
      prefetchPastMaxDeliveriesInLoop.setAll(2000);
      exFact.setPrefetchPolicy(prefetchPastMaxDeliveriesInLoop);

      RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
      redeliveryPolicy.setRedeliveryDelay(0);
      redeliveryPolicy.setMaximumRedeliveries(-1);
      exFact.setRedeliveryPolicy(redeliveryPolicy);

      Connection exConn = exFact.createConnection();
      exConn.start();

      Session session = exConn.createSession(true, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(queue);
      TextMessage message = session.createTextMessage("This is a text message");

      int numMessages = 1000;

      for (int i = 0; i < numMessages; i++) {
         message.setIntProperty("SEQ", i);
         producer.send(message);
      }
      session.commit();
      exConn.close();

      final int numConsumers = 2;
      ExecutorService executorService = Executors.newCachedThreadPool();
      // consume under load
      final int numLoadProducers = 2;
      underLoad(numLoadProducers, ()-> {

         // a bunch of concurrent batch consumers, expecting order
         AtomicBoolean done = new AtomicBoolean(false);
         AtomicInteger receivedCount = new AtomicInteger();
         AtomicInteger inProgressBatch = new AtomicInteger();

         final int batch = 100;

         final ExecutorService commitExecutor = Executors.newCachedThreadPool();

         Runnable consumerTask = () -> {

            Connection toCloseOnError = null;
            while (!done.get() && receivedCount.get() < 20 * numMessages) {
               try (Connection consumerConnection = exFact.createConnection()) {

                  toCloseOnError = consumerConnection;
                  ((ActiveMQConnection) consumerConnection).setCloseTimeout(1); // so rollback on close won't block after socket close exception

                  consumerConnection.start();

                  Session consumerConnectionSession = consumerConnection.createSession(true, Session.SESSION_TRANSACTED);
                  MessageConsumer messageConsumer = consumerConnectionSession.createConsumer(queue);
                  TextMessage messageReceived = null;

                  int i = 0;
                  for (; i < batch; i++) {
                     messageReceived = (TextMessage) messageConsumer.receive(2000);
                     if (messageReceived == null) {
                        break;
                     }

                     receivedCount.incrementAndGet();
                     // need to infer batch from seq number and adjust - client never gets commit response
                     int receivedSeq = messageReceived.getIntProperty("SEQ");
                     int currentBatch = (receivedSeq / batch);
                     if (i == 0) {
                        if (inProgressBatch.get() != currentBatch) {
                           if (inProgressBatch.get() + 1 == currentBatch) {
                              inProgressBatch.incrementAndGet(); // all good, next batch
                              logger.info("@:" + receivedCount.get() + ", current batch increment to: " + inProgressBatch.get() + ", Received Seq: " + receivedSeq + ", Message: " + messageReceived);
                           } else {
                              // we have an order problem
                              done.set(true);
                              throw new AssertionError("@:" + receivedCount.get() + ", batch out of sequence, expected: " + (inProgressBatch.get() + 1) + ", but have: " + currentBatch + " @" + receivedSeq + ", Message: " + messageReceived);
                           }
                        }
                     }
                     // verify within batch order
                     assertEquals(((long) batch * inProgressBatch.get()) + i, receivedSeq, "@:" + receivedCount.get() + " batch out of order");
                  }

                  if (i != batch) {
                     continue;
                  }

                  // manual ack in tx to setup server for rollback work on fail
                  Transport transport = ((ActiveMQConnection) consumerConnection).getTransport();
                  TransactionId txId = new LocalTransactionId(((ActiveMQConnection) consumerConnection).getConnectionInfo().getConnectionId(), receivedCount.get());
                  TransactionInfo tx = new TransactionInfo(((ActiveMQConnection) consumerConnection).getConnectionInfo().getConnectionId(), txId, TransactionInfo.BEGIN);
                  transport.request(tx);
                  MessageAck ack = new MessageAck();
                  ActiveMQMessage mqMessage = (ActiveMQMessage) messageReceived;
                  ack.setDestination(mqMessage.getDestination());
                  ack.setMessageID(mqMessage.getMessageId());
                  ack.setMessageCount(batch);
                  ack.setTransactionId(tx.getTransactionId());
                  ack.setConsumerId(((ActiveMQMessageConsumer)messageConsumer).getConsumerId());

                  transport.request(ack);

                  try {
                     // force a local socket close such that the broker sees an exception on the connection and fails the consumer via serverConsumer close
                     ((ActiveMQConnection) consumerConnection).getTransport().narrow(TcpTransport.class).stop();
                  } catch (Throwable expected) {
                  }

               } catch (ConcurrentModificationException | NullPointerException ignored) {
               } catch (JMSException ignored) {
                  // expected on executor stop
               } catch (Throwable unexpected) {
                  unexpected.printStackTrace();
                  errors.add(unexpected);
                  done.set(true);
               } finally {
                  if (toCloseOnError != null) {
                     try {
                        toCloseOnError.close();
                     } catch (Throwable ignored) {
                     }
                  }
               }
            }
         };

         for (int i = 0; i < numConsumers; i++) {
            executorService.submit(consumerTask);
         }
         executorService.shutdown();


         try {
            assertTrue(executorService.awaitTermination(30, TimeUnit.SECONDS));
            assertTrue(errors.isEmpty());
         } catch (Throwable t) {
            errors.add(t);
         } finally {
            done.set(true);
            commitExecutor.shutdownNow();
            executorService.shutdownNow();
         }

         assertTrue(errors.isEmpty(), "errors: " + errors);
      });

      assertTrue(errors.isEmpty());
   }

   @Test
   @Timeout(60)
   public void testExclusiveConsumerBatchOrderUnderLoad() throws Exception {

      final ArrayList<Throwable> errors = new ArrayList<>();
      SimpleString durableQueue = SimpleString.of("exampleQueueTwo");
      this.server.createQueue(QueueConfiguration.of(durableQueue).setRoutingType(RoutingType.ANYCAST).setExclusive(true));

      Queue queue = new ActiveMQQueue(durableQueue.toString());

      final ActiveMQConnectionFactory exFact = new ActiveMQConnectionFactory("failover:(tcp://localhost:61616?closeAsync=false)?startupMaxReconnectAttempts=10&maxReconnectAttempts=0&timeout=1000");
      exFact.setWatchTopicAdvisories(false);
      exFact.setConnectResponseTimeout(10000);

      ActiveMQPrefetchPolicy prefetchPastMaxDeliveriesInLoop = new ActiveMQPrefetchPolicy();
      prefetchPastMaxDeliveriesInLoop.setAll(2000);
      exFact.setPrefetchPolicy(prefetchPastMaxDeliveriesInLoop);

      RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
      redeliveryPolicy.setRedeliveryDelay(0);
      redeliveryPolicy.setMaximumRedeliveries(-1);
      exFact.setRedeliveryPolicy(redeliveryPolicy);

      Connection exConn = exFact.createConnection();
      exConn.start();

      Session session = exConn.createSession(true, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(queue);
      TextMessage message = session.createTextMessage("This is a text message");

      int numMessages = 10000;

      for (int i = 0; i < numMessages; i++) {
         message.setIntProperty("SEQ", i);
         producer.send(message);
      }
      session.commit();
      exConn.close();

      final int numConsumers = 4;
      ExecutorService executorService = Executors.newCachedThreadPool();
      // consume under load
      final int numLoadProducers = 4;
      underLoad(numLoadProducers, ()-> {

         // a bunch of concurrent batch consumers, expecting order
         AtomicBoolean done = new AtomicBoolean(false);
         AtomicInteger receivedCount = new AtomicInteger();
         AtomicInteger inProgressBatch = new AtomicInteger();

         final int batch = 200;

         final ExecutorService commitExecutor = Executors.newCachedThreadPool();

         Runnable consumerTask = () -> {

            Connection toCloseOnError = null;
            while (!done.get() && server.locateQueue(durableQueue).getMessageCount() > 0L) {
               try (Connection consumerConnection = exFact.createConnection()) {

                  toCloseOnError = consumerConnection;
                  ((ActiveMQConnection) consumerConnection).setCloseTimeout(1); // so rollback on close won't block after socket close exception

                  consumerConnection.start();

                  Session consumerConnectionSession = consumerConnection.createSession(true, Session.SESSION_TRANSACTED);
                  MessageConsumer messageConsumer = consumerConnectionSession.createConsumer(queue);
                  TextMessage messageReceived;

                  int i = 0;
                  for (; i < batch; i++) {
                     messageReceived = (TextMessage) messageConsumer.receive(2000);
                     if (messageReceived == null) {
                        break;
                     }

                     receivedCount.incrementAndGet();
                     // need to infer batch from seq number and adjust - client never gets commit response
                     int receivedSeq = messageReceived.getIntProperty("SEQ");
                     int currentBatch = (receivedSeq / batch);
                     if (i == 0) {
                        if (inProgressBatch.get() != currentBatch) {
                           if (inProgressBatch.get() + 1 == currentBatch) {
                              inProgressBatch.incrementAndGet(); // all good, next batch
                              logger.info("@:" + receivedCount.get() + ", current batch increment to: " + inProgressBatch.get() + ", Received Seq: " + receivedSeq + ", Message: " + messageReceived);
                           } else {
                              // we have an order problem
                              done.set(true);
                              throw new AssertionError("@:" + receivedCount.get() + ", batch out of sequence, expected: " + (inProgressBatch.get() + 1) + ", but have: " + currentBatch + " @" + receivedSeq + ", Message: " + messageReceived);
                           }
                        }
                     }
                     // verify within batch order
                     assertEquals(((long) batch * inProgressBatch.get()) + i, receivedSeq, "@:" + receivedCount.get() + " batch out of order");
                  }

                  if (i != batch) {
                     continue;
                  }

                  // arrange concurrent commit - ack/commit of batch
                  // with server side error, potential for ack/commit and close-on-fail to contend
                  final CountDownLatch latch = new CountDownLatch(1);
                  final Session finalSession = consumerConnectionSession;
                  commitExecutor.submit(() -> {
                     try {
                        latch.countDown();
                        finalSession.commit();

                     } catch (Throwable expected) {
                     }
                  });

                  latch.await(1, TimeUnit.SECONDS);

                  // give a chance to have a batch complete to make progress!
                  TimeUnit.MILLISECONDS.sleep(15);

                  try {
                     // force a local socket close such that the broker sees an exception on the connection and fails the consumer via serverConsumer close
                     ((ActiveMQConnection) consumerConnection).getTransport().narrow(TcpTransport.class).stop();
                  } catch (Throwable expected) {
                  }

               } catch (InterruptedException | ConcurrentModificationException | NullPointerException ignored) {
               } catch (JMSException ignored) {
                  // expected on executor stop
               } catch (Throwable unexpected) {
                  unexpected.printStackTrace();
                  errors.add(unexpected);
                  done.set(true);
               } finally {
                  if (toCloseOnError != null) {
                     try {
                        toCloseOnError.close();
                     } catch (Throwable ignored) {
                     }
                  }
               }
            }
         };

         for (int i = 0; i < numConsumers; i++) {
            executorService.submit(consumerTask);
         }
         executorService.shutdown();

         try {
            Wait.assertEquals(0L, () -> {
               if (!errors.isEmpty()) {
                  return -1;
               }
               return server.locateQueue(durableQueue).getMessageCount();
            }, 30 * 1000);
         } catch (Throwable t) {
            errors.add(t);
         } finally {
            done.set(true);
            commitExecutor.shutdownNow();
            executorService.shutdownNow();
         }

         assertTrue(errors.isEmpty());
      });

      assertTrue(errors.isEmpty());
   }

   public void underLoad(final int numProducers, Runnable r) throws Exception {
      // produce some load with a producer(s)/consumer
      SimpleString durableQueue = SimpleString.of("exampleQueue");
      this.server.createQueue(QueueConfiguration.of(durableQueue).setRoutingType(RoutingType.ANYCAST));

      ExecutorService executor = Executors.newFixedThreadPool(numProducers + 1);

      Queue queue;
      ConnectionFactory cf;
      boolean useCoreForLoad = true;

      if (useCoreForLoad) {
         org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory connectionFactory = new org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory();
         connectionFactory.setConfirmationWindowSize(1000000);
         connectionFactory.setBlockOnDurableSend(true);
         connectionFactory.setBlockOnNonDurableSend(true);
         cf = connectionFactory;

         queue = connectionFactory.createContext().createQueue(durableQueue.toString());

      } else {
         ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("failover:(tcp://localhost:61616)?startupMaxReconnectAttempts=0&maxReconnectAttempts=0&timeout=1000");
         connectionFactory.setWatchTopicAdvisories(false);
         connectionFactory.setCloseTimeout(1);
         connectionFactory.setSendTimeout(2000);

         cf = connectionFactory;
         queue = new ActiveMQQueue(durableQueue.toString());
      }


      final Queue destination = queue;
      final ConnectionFactory connectionFactory = cf;
      final AtomicBoolean done = new AtomicBoolean();
      Runnable producerTask = ()-> {

         try (Connection exConn = connectionFactory.createConnection()) {

            exConn.start();

            final Session session = exConn.createSession(true, Session.SESSION_TRANSACTED);
            final MessageProducer producer = session.createProducer(destination);
            final TextMessage message = session.createTextMessage("This is a text message");

            int count = 1;
            while (!done.get()) {
               producer.send(message);
               if ((count++ % 100) == 0) {
                  session.commit();
               }
            }
         } catch (Exception ignored) {
         }
      };

      for (int i = 0; i < numProducers; i++) {
         executor.submit(producerTask);
      }
      // one consumer
      executor.submit(()-> {

         try (Connection exConn = connectionFactory.createConnection()) {
            exConn.start();

            Session session = exConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer messageConsumer = session.createConsumer(destination);

            while (!done.get()) {
               messageConsumer.receive(200);
            }
         } catch (Exception ignored) {
         }
      });


      try {
         r.run();
      } finally {
         done.set(true);
         executor.shutdown();
         if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            executor.shutdownNow();
         }
         logger.info("LOAD ADDED: " + server.locateQueue(durableQueue).getMessagesAdded());
      }
   }

   @Test
   @Timeout(120)
   public void testExclusiveConsumerTransactionalBatchOnReconnectionLargePrefetch() throws Exception {
      doTestExclusiveConsumerTransactionalBatchOnReconnectionLargePrefetch();
   }

   public void doTestExclusiveConsumerTransactionalBatchOnReconnectionLargePrefetch() throws Exception {
      Connection exConn = null;

      ExecutorService executorService = Executors.newFixedThreadPool(10);
      SimpleString durableQueue = SimpleString.of("exampleQueueTwo");
      this.server.createQueue(QueueConfiguration.of(durableQueue).setRoutingType(RoutingType.ANYCAST).setExclusive(true));
      AtomicInteger batchConsumed = new AtomicInteger(0);

      try {
         ActiveMQConnectionFactory exFact = new ActiveMQConnectionFactory();
         exFact.setWatchTopicAdvisories(false);

         RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
         redeliveryPolicy.setMaximumRedeliveries(4000);
         exFact.setRedeliveryPolicy(redeliveryPolicy);

         Queue queue = new ActiveMQQueue("exampleQueueTwo");

         exConn = exFact.createConnection();

         exConn.start();

         Session session = exConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer producer = session.createProducer(queue);

         TextMessage message = session.createTextMessage("This is a text message");

         int numMessages = 1000;
         for (int i = 0; i < numMessages; i++) {
            message.setIntProperty("SEQ", i);
            producer.send(message);
         }
         session.close();
         exConn.close();

         final int batch = 200;
         AtomicBoolean done = new AtomicBoolean(false);
         while (!done.get()) {
            // connection per batch attempt
            exConn = exFact.createConnection();
            ((ActiveMQConnection) exConn).setCloseTimeout(1); // so rollback on close won't block after socket close exception

            exConn.start();

            session = exConn.createSession(true, Session.SESSION_TRANSACTED);

            MessageConsumer messageConsumer = session.createConsumer(queue);
            TextMessage messageReceived;
            int received = 0;
            for (int j = 0; j < batch; j++) {
               messageReceived = (TextMessage) messageConsumer.receive(2000);
               if (messageReceived == null) {
                  done.set(true);
                  break;
               }
               received++;
               batchConsumed.incrementAndGet();
               assertEquals("This is a text message", messageReceived.getText());

               int receivedSeq = messageReceived.getIntProperty("SEQ");
               // need to infer batch from seq number and adjust - client never gets commit response
               assertEquals((batch * (receivedSeq / batch)) + j, receivedSeq, "@:" + received + ", out of order");
            }

            // arrange concurrent commit - ack/commit
            // with server side error, potential for ack/commit and close-on-fail to contend
            final CountDownLatch latch = new CountDownLatch(1);
            Session finalSession = session;
            executorService.submit(() -> {
               try {
                  latch.countDown();
                  finalSession.commit();

               } catch (JMSException ignored) {
               }
            });

            latch.await(1, TimeUnit.SECONDS);
            // force a local socket close such that the broker sees an exception on the connection and fails the consumer via serverConsumer close
            ((org.apache.activemq.ActiveMQConnection) exConn).getTransport().narrow(FailoverTransport.class).stop();
            // retry asap, not waiting for client close
            final Connection finalConToClose = exConn;
            executorService.submit(() -> {
               try {
                  finalConToClose.close();
               } catch (JMSException ignored) {
               }
            });
         }
      } finally {
         if (exConn != null) {
            exConn.close();
         }
         executorService.shutdownNow();
      }

      logger.info("Done after: {}, queue: {}", batchConsumed.get(), server.locateQueue(durableQueue));
      try {
         Wait.assertEquals(0L, () -> server.locateQueue(durableQueue).getDeliveringCount(), 1000);
      } catch (Throwable e) {

         final AtomicBoolean doOut = new AtomicBoolean(false);
         PrintStream out = new PrintStream(System.out) {

            @Override
            public void println(String s) {
               if (doOut.get()) {
                  super.println(s);
               } else {
                  if (s.startsWith("### Failed Transactions")) {
                     doOut.set(true);
                     super.println(s);
                  }
               }
            }
         };
         PrintData.printData(server.getConfiguration().getBindingsLocation(), server.getConfiguration().getJournalLocation(), server.getConfiguration().getPagingLocation(), out, true, true, true, false, -1);

         throw e;
      }
   }

}
