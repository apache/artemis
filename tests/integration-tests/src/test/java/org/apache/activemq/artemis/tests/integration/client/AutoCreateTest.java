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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeTestAccessor;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class AutoCreateTest extends ActiveMQTestBase {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public final SimpleString addressA = SimpleString.of("addressA");
   public final SimpleString queueA = SimpleString.of("queueA");

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(true, true);
      AddressSettings settings = new AddressSettings().setAutoCreateAddresses(true).setAutoDeleteAddresses(true).setAutoCreateQueues(true).setAutoDeleteQueues(true);

      server.getConfiguration().getAddressSettings().clear();
      server.getConfiguration().getAddressSettings().put("#", settings);
   }

   @Test
   public void testAutoCreateDeleteRecreate() throws Exception {
      // This test is about validating the behaviour of queues recreates with the default configuration
      assertEquals(ActiveMQDefaultConfiguration.getDefaultAddressQueueScanPeriod(), server.getConfiguration().getAddressQueueScanPeriod(), "Supposed to use default configuration on this test");
      server.start();
      int THREADS = 40;
      ExecutorService executor = Executors.newFixedThreadPool(THREADS);
      try {

         String QUEUE_NAME = getName();

         AtomicInteger errors = new AtomicInteger(0);

         for (int i = 0; i < 50; i++) {
            ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
            logger.debug("*******************************************************************************************************************************");
            logger.debug("run {}", i);
            CyclicBarrier barrier = new CyclicBarrier(THREADS + 1);
            CountDownLatch done = new CountDownLatch(THREADS);
            Runnable consumerThread = () -> {
               try (Connection connection = cf.createConnection()) {
                  Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                  barrier.await(10, TimeUnit.SECONDS);
                  Queue queue = session.createQueue(QUEUE_NAME);
                  MessageConsumer consumer = session.createConsumer(queue);
                  connection.start();
               } catch (Throwable e) {
                  logger.warn(e.getMessage(), e);
                  errors.incrementAndGet();
               } finally {
                  done.countDown();
               }
            };

            for (int t = 0; t < THREADS; t++) {
               executor.execute(consumerThread);
            }

            barrier.await(10, TimeUnit.SECONDS);
            assertTrue(done.await(10, TimeUnit.SECONDS));
            assertEquals(0, errors.get());

            try (Connection connection = cf.createConnection()) {
               Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
               Queue queue = session.createQueue(QUEUE_NAME);
               MessageConsumer consumer = session.createConsumer(queue);
               connection.start();

               MessageProducer producer = session.createProducer(queue);
               producer.send(session.createTextMessage("hello"));

               assertNotNull(consumer.receive(5000));
            }
         }
      } finally {
         executor.shutdownNow();
      }
   }

   @Test
   public void testSweep() throws Exception {

      server.getConfiguration().setAddressQueueScanPeriod(-1); // disabling scanner, we will perform it manually
      server.start();
      String QUEUE_NAME = "autoCreateAndRecreate";

      ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(QUEUE_NAME);
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
      }

      try (Connection connection = cf.createConnection()) {
         org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(QUEUE_NAME);
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(QUEUE_NAME);
         PostOfficeTestAccessor.reapAddresses((PostOfficeImpl) server.getPostOffice());
         assertTrue(serverQueue.isSwept());
         MessageConsumer consumer = session.createConsumer(queue);
         // no need to wait reaping to wait reaping to set it false. A simple add consumer on the queue should clear this
         Wait.assertFalse(serverQueue::isSwept);
         connection.start();
      }

      AddressInfo info = server.getPostOffice().getAddressInfo(SimpleString.of(QUEUE_NAME));
      assertNotNull(info);
      assertTrue(info.isAutoCreated());

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         PostOfficeTestAccessor.reapAddresses((PostOfficeImpl) server.getPostOffice());
         assertFalse(loggerHandler.findText("AMQ224112"));
         PostOfficeTestAccessor.reapAddresses((PostOfficeImpl) server.getPostOffice());
         assertTrue(loggerHandler.findText("AMQ224112"));
         assertTrue(loggerHandler.findText(QUEUE_NAME), "Queue name should be mentioned on logs");
         PostOfficeTestAccessor.reapAddresses((PostOfficeImpl) server.getPostOffice());
         assertTrue(loggerHandler.findText("AMQ224113")); // we need another sweep to remove it
      }
   }

   @Test
   public void testSweepAddress() throws Exception {
      server.getConfiguration().setAddressQueueScanPeriod(-1); // disabling scanner, we will perform it manually
      AddressSettings settings = new AddressSettings().setAutoDeleteQueues(true).setAutoDeleteAddresses(true).setAutoDeleteAddressesDelay(10).setAutoDeleteQueuesDelay(10);
      server.getConfiguration().getAddressSettings().clear();
      server.getConfiguration().getAddressSettings().put("#", settings);
      server.start();
      String ADDRESS_NAME = getName();

      AddressInfo info = new AddressInfo(ADDRESS_NAME).addRoutingType(RoutingType.MULTICAST).setAutoCreated(true);
      server.getPostOffice().addAddressInfo(info);
      info = server.getPostOffice().getAddressInfo(SimpleString.of(ADDRESS_NAME));

      ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(ADDRESS_NAME);
         session.createConsumer(topic);
      }

      { // just a namespace area
         final AddressInfo infoRef = info;
         Wait.assertTrue(() -> infoRef.getBindingRemovedTimestamp() != -1);
      }

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         assertFalse(loggerHandler.findText("AMQ224113"));
         Thread.sleep(50);
         assertFalse(info.isSwept());
         PostOfficeTestAccessor.reapAddresses((PostOfficeImpl) server.getPostOffice());
         assertFalse(loggerHandler.findText("AMQ224113"));
         assertTrue(info.isSwept());
         PostOfficeTestAccessor.reapAddresses((PostOfficeImpl) server.getPostOffice());
         assertTrue(loggerHandler.findText("AMQ224113"));
      }
   }


   @Test
   public void testNegativeSweepAddress() throws Exception {
      server.getConfiguration().setAddressQueueScanPeriod(-1); // disabling scanner, we will perform it manually
      AddressSettings settings = new AddressSettings().setAutoDeleteQueues(true).setAutoDeleteAddresses(true).setAutoDeleteAddressesDelay(10).setAutoDeleteQueuesDelay(10);
      server.getConfiguration().getAddressSettings().clear();
      server.getConfiguration().getAddressSettings().put("#", settings);
      server.start();
      String ADDRESS_NAME = getName();

      AddressInfo info = new AddressInfo(ADDRESS_NAME).addRoutingType(RoutingType.MULTICAST).setAutoCreated(true);
      server.getPostOffice().addAddressInfo(info);
      info = server.getPostOffice().getAddressInfo(SimpleString.of(ADDRESS_NAME));

      ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(ADDRESS_NAME);
         session.createConsumer(topic);
      }

      { // just a namespace area
         final AddressInfo infoRef = info;
         Wait.assertTrue(() -> infoRef.getBindingRemovedTimestamp() != -1);
      }

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         assertFalse(loggerHandler.findText("AMQ224113"));
         Thread.sleep(50);
         PostOfficeTestAccessor.reapAddresses((PostOfficeImpl) server.getPostOffice());
         assertFalse(loggerHandler.findText("AMQ224113"));
         assertTrue(info.isSwept());
         try (Connection connection = cf.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(ADDRESS_NAME);
            session.createConsumer(topic);
            PostOfficeTestAccessor.reapAddresses((PostOfficeImpl) server.getPostOffice());
            assertFalse(loggerHandler.findText("AMQ224113"));
            assertFalse(info.isSwept()); // it should be cleared because there is a consumer now
         }
      }
   }

   @Test
   public void testNegativeSweepBecauseOfConsumer() throws Exception {

      server.getConfiguration().setAddressQueueScanPeriod(-1); // disabling scanner, we will perform it manually
      server.start();
      String QUEUE_NAME = getName();

      ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(QUEUE_NAME);
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
      }

      AddressInfo info = server.getPostOffice().getAddressInfo(SimpleString.of(QUEUE_NAME));
      assertNotNull(info);
      assertTrue(info.isAutoCreated());

      try (Connection connection = cf.createConnection();
         AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(QUEUE_NAME);
         PostOfficeTestAccessor.reapAddresses((PostOfficeImpl) server.getPostOffice());
         org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(QUEUE_NAME);
         assertTrue(serverQueue.isSwept());
         assertFalse(loggerHandler.findText("AMQ224112"));
         MessageConsumer consumer = session.createConsumer(queue);
         PostOfficeTestAccessor.reapAddresses((PostOfficeImpl) server.getPostOffice());
         assertFalse(serverQueue.isSwept());
         PostOfficeTestAccessor.reapAddresses((PostOfficeImpl) server.getPostOffice());
         assertFalse(loggerHandler.findText("AMQ224113")); // we need another sweep to remove it
         assertFalse(loggerHandler.findText("AMQ224112"));
      }
   }

   @Test
   public void testNegativeSweepBecauseOfSend() throws Exception {

      server.getConfiguration().setAddressQueueScanPeriod(-1); // disabling scanner, we will perform it manually
      server.start();
      String QUEUE_NAME = getName();

      ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(QUEUE_NAME);
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
      }

      AddressInfo info = server.getPostOffice().getAddressInfo(SimpleString.of(QUEUE_NAME));
      assertNotNull(info);
      assertTrue(info.isAutoCreated());


      try (Connection connection = cf.createConnection();
         AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(QUEUE_NAME);
         PostOfficeTestAccessor.reapAddresses((PostOfficeImpl) server.getPostOffice());
         org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(QUEUE_NAME);
         assertTrue(serverQueue.isSwept());
         assertFalse(loggerHandler.findText("AMQ224112"));
         MessageProducer producer = session.createProducer(queue);
         producer.send(session.createTextMessage("hello"));
         Wait.assertEquals(1, serverQueue::getMessageCount);
         PostOfficeTestAccessor.reapAddresses((PostOfficeImpl) server.getPostOffice());
         assertFalse(serverQueue.isSwept());
         PostOfficeTestAccessor.reapAddresses((PostOfficeImpl) server.getPostOffice());
         assertFalse(loggerHandler.findText("AMQ224113")); // we need another sweep to remove it
         assertFalse(loggerHandler.findText("AMQ224112"));
      }
   }

   @Test
   public void testCleanupAfterRebootOpenWire() throws Exception {
      testCleanupAfterReboot("OPENWIRE", false);
   }

   @Test
   public void testCleanupAfterRebootCore() throws Exception {
      // there is no need to duplicate the test between usedelay and not.
      // doing it in one of the protocols should be enough
      testCleanupAfterReboot("CORE", true);
   }

   @Test
   public void testCleanupAfterRebootAMQP() throws Exception {
      testCleanupAfterReboot("AMQP", false);
   }

   public void testCleanupAfterReboot(String protocol, boolean useDelay) throws Exception {

      if (useDelay) {
         // setting up a delay, to make things a bit more challenging
         server.getAddressSettingsRepository().addMatch(getName(), new AddressSettings().setAutoCreateAddresses(true).setAutoDeleteAddressesDelay(TimeUnit.DAYS.toMillis(1)).setAutoDeleteQueuesDelay(TimeUnit.DAYS.toMillis(1)));
      }

      server.getConfiguration().setAddressQueueScanPeriod(-1); // disabling scanner, we will perform it manually
      server.start();
      String QUEUE_NAME = "QUEUE_" + getName();
      String TOPIC_NAME = "TOPIC_" + getName();

      ConnectionFactory cf = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(QUEUE_NAME);
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
      }

      AddressInfo info = server.getPostOffice().getAddressInfo(SimpleString.of(QUEUE_NAME));
      assertNotNull(info);
      assertTrue(info.isAutoCreated());

      server.stop();

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         server.start();

         assertTrue(loggerHandler.findText("AMQ224113"));
         assertTrue(loggerHandler.findText("AMQ224112"));
      }

      String randomString = "random " + RandomUtil.randomUUIDString();

      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(QUEUE_NAME);
         Topic topic = session.createTopic(TOPIC_NAME);
         MessageProducer producer = session.createProducer(null);
         producer.send(queue, session.createTextMessage(randomString));
         producer.send(topic, session.createTextMessage(randomString));
      }

      info = server.getPostOffice().getAddressInfo(SimpleString.of(QUEUE_NAME));
      assertNotNull(info);
      assertTrue(info.isAutoCreated());

      info = server.getPostOffice().getAddressInfo(SimpleString.of(TOPIC_NAME));
      assertNotNull(info);
      assertTrue(info.isAutoCreated());

      server.stop();
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         server.start();

         // this time around the address QUEUE_NAME had a queue with messages, it has to exist
         assertFalse(loggerHandler.matchText("AMQ224113.*" + QUEUE_NAME));
         assertFalse(loggerHandler.findText("AMQ224112"));
         // the address TOPIC_NAME had no queues, it has to be removed
         assertTrue(loggerHandler.matchText("AMQ224113.*" + TOPIC_NAME));
      }

      info = server.getPostOffice().getAddressInfo(SimpleString.of(QUEUE_NAME));
      assertNotNull(info);
      assertTrue(info.isAutoCreated());

      info = server.getPostOffice().getAddressInfo(SimpleString.of(TOPIC_NAME));
      assertNull(info);

      { // just a namespace
         org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(QUEUE_NAME);
         Wait.assertEquals(1, serverQueue::getMessageCount);
      }


      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         connection.start();
         Queue queue = session.createQueue(QUEUE_NAME);
         MessageConsumer consumer = session.createConsumer(queue);
         TextMessage message = (TextMessage)consumer.receive(5000);
         assertEquals(randomString, message.getText());
      }

   }



}
