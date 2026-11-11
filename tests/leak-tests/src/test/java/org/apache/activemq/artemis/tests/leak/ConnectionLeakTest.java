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
package org.apache.activemq.artemis.tests.leak;

import static org.apache.activemq.artemis.tests.leak.MemoryAssertions.assertMemory;
import static org.apache.activemq.artemis.tests.leak.MemoryAssertions.basicMemoryAsserts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.checkleak.core.CheckLeak;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.protocol.core.impl.RemotingConnectionImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.MessageReferenceImpl;
import org.apache.activemq.artemis.core.server.impl.ServerConsumerImpl;
import org.apache.activemq.artemis.core.server.impl.ServerStatus;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.collections.LinkedListImpl;
import org.apache.qpid.proton.engine.impl.DeliveryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionLeakTest extends AbstractLeakTest {

   private ConnectionFactory createConnectionFactory(String protocol) {
      if (protocol.equals("AMQP")) {
         return CFUtil.createConnectionFactory("AMQP", "amqp://localhost:61616?amqp.idleTimeout=120000&failover.maxReconnectAttempts=1&jms.prefetchPolicy.all=10&jms.forceAsyncAcks=true");
      } else {
         return CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      }
   }

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   ActiveMQServer server;

   @BeforeAll
   public static void beforeClass() throws Exception {
      assumeTrue(CheckLeak.isLoaded());
   }

   @AfterEach
   public void validateServer() throws Exception {
      CheckLeak checkLeak = new CheckLeak();

      // I am doing this check here because the test method might hold a client connection
      // so this check has to be done after the test, and before the server is stopped
      assertMemory(checkLeak, 0, RemotingConnectionImpl.class.getName());

      server.stop();

      server = null;

      clearServers();
      ServerStatus.clear();

      assertMemory(checkLeak, 0, ActiveMQServerImpl.class.getName());
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      server = createServer(true, createDefaultConfig(1, true));
      server.getConfiguration().setJournalPoolFiles(4).setJournalMinFiles(2);
      server.start();
   }

   @Test
   public void testManyConsumersAMQP() throws Exception {
      doTestManyConsumers("AMQP");
   }

   @Test
   public void testManyConsumersCore() throws Exception {
      doTestManyConsumers("CORE");
   }

   @Test
   public void testManyConsumersOpenWire() throws Exception {
      doTestManyConsumers("OPENWIRE");
   }

   private void doTestManyConsumers(String protocol) throws Exception {
      CheckLeak checkLeak = new CheckLeak();
      // Some protocols may create ServerConsumers
      int originalConsumers = checkLeak.getAllObjects(ServerConsumerImpl.class).length;
      int REPEATS = 100;
      int MESSAGES = 20;
      basicMemoryAsserts();

      // The separate method here exists to ensure basicMemoryAsserts runs correctly.
      // Since I'm evaluating RemotingConnectionImpl the connectionFactory inside the method may still
      // hold a reference that would only be released at the end of the method,
      // because of that I need to make these calls on a separate method.
      // I tried not needing this by creating a context but that was not enough to release the references.
      doManyConsumers(protocol, REPEATS, MESSAGES, checkLeak);

      basicMemoryAsserts();
   }

   private void doManyConsumers(String protocol, int REPEATS, int MESSAGES, CheckLeak checkLeak) throws Exception {
      ConnectionFactory cf = createConnectionFactory(protocol);

      try (Connection producerConnection = cf.createConnection(); Connection consumerConnection = cf.createConnection()) {

         Session producerSession = producerConnection.createSession(true, Session.SESSION_TRANSACTED);

         Session consumerSession = consumerConnection.createSession(true, Session.SESSION_TRANSACTED);
         consumerConnection.start();

         for (int i = 0; i < REPEATS; i++) {
            {
               Destination source = producerSession.createQueue("source");
               try (MessageProducer sourceProducer = producerSession.createProducer(source)) {
                  for (int msg = 0; msg < MESSAGES; msg++) {
                     Message message = producerSession.createTextMessage("hello " + msg);
                     message.setIntProperty("i", msg);
                     sourceProducer.send(message);
                  }
                  producerSession.commit();
               }
            }
            {
               Destination source = consumerSession.createQueue("source");
               Destination target = consumerSession.createQueue("target");
               // notice I am not closing the consumer directly, just relying on the connection closing
               MessageProducer targetProducer = consumerSession.createProducer(target);
               // I am receiving messages, and pushing them to a different queue
               try (MessageConsumer sourceConsumer = consumerSession.createConsumer(source)) {
                  for (int msg = 0; msg < MESSAGES; msg++) {

                     TextMessage m = (TextMessage) sourceConsumer.receive(5000);
                     assertNotNull(m);
                     assertEquals("hello " + msg, m.getText());
                     assertEquals(msg, m.getIntProperty("i"));
                     targetProducer.send(m);
                  }
                  assertNull(sourceConsumer.receiveNoWait());
                  consumerSession.commit();

                  Wait.assertTrue(() -> validateClosedConsumers(checkLeak));
               }
            }
         }
      }

      assertMemory(new CheckLeak(), 0, ServerConsumerImpl.class.getName());

      // this is just to drain the messages
      try (Connection targetConnection = cf.createConnection(); Connection consumerConnection = cf.createConnection()) {
         Session targetSession = targetConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = targetSession.createConsumer(targetSession.createQueue("target"));
         targetConnection.start();

         for (int msgI = 0; msgI < REPEATS * MESSAGES; msgI++) {
            assertNotNull(consumer.receive(5000));
         }

         assertNull(consumer.receiveNoWait());
         assertMemory(new CheckLeak(), 0, DeliveryImpl.class.getName());
         Wait.assertTrue(() -> validateClosedConsumers(checkLeak));
         consumer = null;
      }

      Queue sourceQueue = server.locateQueue("source");
      Queue targetQueue = server.locateQueue("target");

      Wait.assertEquals(0, sourceQueue::getMessageCount);
      Wait.assertEquals(0, targetQueue::getMessageCount);

      assertMemory(checkLeak, 0, MessageReferenceImpl.class.getName());
      assertMemory(checkLeak, 0, AMQPStandardMessage.class.getName());

      if (cf instanceof ActiveMQConnectionFactory factory) {
         factory.close();
      }

   }

   @Test
   public void testCancelledDeliveries() throws Exception {
      doTestCancelledDelivery("AMQP");
   }

   private void doTestCancelledDelivery(String protocol) throws Exception {

      CheckLeak checkLeak = new CheckLeak();
      // Some protocols may create ServerConsumers
      int originalConsumers = checkLeak.getAllObjects(ServerConsumerImpl.class).length;
      int REPEATS = 10;
      int MESSAGES = 100;
      int SLEEP_PRODUCER = 10;
      int CONSUMERS = 1; // The test here is using a single consumer. But I'm keeping this code around as I may use more load eventually even if just for a test
      String queueName = getName();

      ExecutorService executorService = Executors.newFixedThreadPool(CONSUMERS + 1); // there's always one producer
      runAfter(executorService::shutdownNow);

      Queue serverQueue = server.createQueue(QueueConfiguration.of(getName()).setRoutingType(RoutingType.ANYCAST));

      ConnectionFactory cf = createConnectionFactory(protocol);


      Connection[] connectionConsumers = new Connection[CONSUMERS];
      Session[] sessionConsumer = new Session[CONSUMERS];

      for (int i = 0; i < CONSUMERS; i++) {
         connectionConsumers[i] = cf.createConnection();
         connectionConsumers[i].start();
         sessionConsumer[i] = connectionConsumers[i].createSession(true, Session.SESSION_TRANSACTED);
      }

      AtomicInteger errors = new AtomicInteger(0);
      try (Connection connection = cf.createConnection()) {
         for (int i = 0; i < REPEATS; i++) {
            logger.info("Retrying {}", i);
            CountDownLatch done = new CountDownLatch(CONSUMERS + 1); // there's always one producer
            AtomicInteger recevied = new AtomicInteger(0);
            {
               executorService.execute(() -> {
                  try (Session producerSession = connection.createSession(true, Session.SESSION_TRANSACTED)) {
                     Destination queue = producerSession.createQueue(queueName);
                     MessageProducer producer = producerSession.createProducer(queue);
                     for (int msg = 0; msg < MESSAGES; msg++) {
                        Message message = producerSession.createTextMessage("hello " + msg);
                        message.setIntProperty("i", msg);
                        producer.send(message);
                        if (msg % 10 == 0) {
                           producerSession.commit();
                           Thread.sleep(SLEEP_PRODUCER);
                        }
                     }
                     producerSession.commit();
                  } catch (Exception e) {
                     logger.warn(e.getMessage(), e);
                     errors.incrementAndGet();
                  } finally {
                     done.countDown();
                  }
               });

               for (int cons = 0; cons < CONSUMERS; cons++) {
                  final Session consumerSession = sessionConsumer[cons];
                  executorService.execute(() -> {
                     try {
                        javax.jms.Queue queue = consumerSession.createQueue(queueName);
                        MessageConsumer consumer = consumerSession.createConsumer(queue);
                        while (recevied.get() < MESSAGES) {
                           TextMessage message = (TextMessage) consumer.receiveNoWait();
                           if (message != null) {
                              consumer.close();
                              consumerSession.commit();
                              consumer = consumerSession.createConsumer(queue);
                              recevied.incrementAndGet();
                           }
                        }
                        consumer.close();
                     } catch (Throwable e) {
                        logger.warn(e.getMessage(), e);
                        errors.incrementAndGet();
                     } finally {
                        done.countDown();
                     }
                  });
               }

               assertTrue(done.await(10, TimeUnit.SECONDS));
               assertEquals(0, errors.get());
               Wait.assertEquals(0, serverQueue::getMessageCount);
               assertMemory(checkLeak, 0, 5, 1, AMQPStandardMessage.class.getName());
               assertMemory(checkLeak, 0, 5, 1, DeliveryImpl.class.getName());
            }
         }
      }

      for (Connection connection : connectionConsumers) {
         connection.close();
      }

      basicMemoryAsserts();
   }


   @Test
   public void testCheckIteratorsAMQP() throws Exception {
      testCheckIterators("AMQP");
   }

   @Test
   public void testCheckIteratorsOpenWire() throws Exception {
      testCheckIterators("OPENWIRE");
   }

   @Test
   public void testCheckIteratorsCORE() throws Exception {
      testCheckIterators("CORE");
   }

   public void testCheckIterators(String protocol) throws Exception {
      CheckLeak checkLeak = new CheckLeak();

      String queueName = getName();

      Queue queue = server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));

      ConnectionFactory cf = createConnectionFactory(protocol);
      for (int i = 0; i < 10; i++) {
         Connection connection = cf.createConnection();
         connection.start();
         for (int j = 0; j < 10; j++) {
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            MessageProducer producer = session.createProducer(session.createQueue(queueName));
            producer.send(session.createTextMessage("test"));
            session.commit();
            session.close();
         }

         for (int j = 0; j < 10; j++) {
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            MessageConsumer consumer = session.createConsumer(session.createQueue(queueName));
            consumer.receiveNoWait(); // it doesn't matter if it received or not, just doing something in the queue to kick the iterators
            session.commit();
         }
         connection.close();

         assertMemory(checkLeak, 0, 1, 1, ServerConsumerImpl.class.getName());
         assertMemory(checkLeak, 0, 2, 1, LinkedListImpl.Iterator.class.getName());
      }
   }


   private boolean validateClosedConsumers(CheckLeak checkLeak) throws Exception {
      Object[] objecs = checkLeak.getAllObjects(ServerConsumerImpl.class);
      for (Object obj : objecs) {
         ServerConsumerImpl consumer = (ServerConsumerImpl) obj;
         if (consumer.isClosed()) {
            logger.info("References to closedConsumer {}\n{}", consumer, checkLeak.exploreObjectReferences(3, 1, true, consumer));
            return false;
         }
      }
      return true;
   }
}