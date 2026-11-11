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
package org.apache.activemq.transport.failover;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQMessageConsumer;
import org.apache.activemq.ActiveMQMessageTransformation;
import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.activemq.broker.artemiswrapper.OpenwireArtemisBaseTest;
import org.apache.activemq.command.ConsumerId;
import org.apache.activemq.command.SessionId;
import org.apache.activemq.util.Wait;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMRules;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertTrue;

// see https://issues.apache.org/activemq/browse/AMQ-2573
@RunWith(BMUnitRunner.class)
@Ignore // This test is using byteMan. it's not really accurate.
public class FailoverConsumerUnconsumedTest extends OpenwireArtemisBaseTest {

   private static final Logger LOG = LoggerFactory.getLogger(FailoverConsumerUnconsumedTest.class);
   private static final String QUEUE_NAME = "FailoverWithUnconsumed";
   private static final AtomicBoolean doByteman = new AtomicBoolean(false);

   private static int maxConsumers = 2;
   private static AtomicInteger consumerCount = new AtomicInteger(0);
   private static CountDownLatch brokerStopLatch = new CountDownLatch(1);
   private static AtomicBoolean watchTopicAdvisories = new AtomicBoolean(false);

   private String url = newURI(0);
   final int prefetch = 10;
   private static EmbeddedJMS broker;

   @After
   public void tearDown() throws Exception {
      if (broker != null) {
         broker.stop();
         broker = null;
      }
   }

   @Before
   public void setUp() throws Exception {
      consumerCount.set(0);
   }

   @Test
   @BMRules(
      rules = {@BMRule(
         name = "set no return response and stop the broker",
         targetClass = "org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection$CommandProcessor",
         targetMethod = "processAddConsumer",
         targetLocation = "ENTRY",
         action = "org.apache.activemq.transport.failover.FailoverConsumerUnconsumedTest.holdResponseAndStopBroker2($0)")})
   public void testFailoverConsumerDups() throws Exception {
      watchTopicAdvisories.set(true);
      doTestFailoverConsumerDups(true);
   }

   @Test
   @BMRules(
      rules = {@BMRule(
         name = "set no return response and stop the broker",
         targetClass = "org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection$CommandProcessor",
         targetMethod = "processAddConsumer",
         targetLocation = "ENTRY",
         action = "org.apache.activemq.transport.failover.FailoverConsumerUnconsumedTest.holdResponseAndStopBroker2($0)")})
   public void testFailoverConsumerDupsNoAdvisoryWatch() throws Exception {
      watchTopicAdvisories.set(false);
      doTestFailoverConsumerDups(false);
   }

   @Test
   @BMRules(
      rules = {@BMRule(
         name = "set no return response and stop the broker",
         targetClass = "org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection$CommandProcessor",
         targetMethod = "processAddConsumer",
         targetLocation = "ENTRY",
         action = "org.apache.activemq.transport.failover.FailoverConsumerUnconsumedTest.holdResponseAndStopBroker($0)")})
   public void testFailoverClientAckMissingRedelivery() throws Exception {
      maxConsumers = 2;
      brokerStopLatch = new CountDownLatch(1);
      broker = createBroker();
      broker.start();

      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + url + ")");
      cf.setWatchTopicAdvisories(false);

      final ActiveMQConnection connection = (ActiveMQConnection) cf.createConnection();
      connection.start();

      final Session consumerSession = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      final Queue destination = consumerSession.createQueue(QUEUE_NAME + "?jms.consumer.prefetch=" + prefetch);

      doByteman.set(true);

      final Vector<TestConsumer> testConsumers = new Vector<>();
      TestConsumer testConsumer = new TestConsumer(consumerSession, destination, connection);
      testConsumer.setMessageListener(message -> {
         try {
            LOG.info("onMessage:" + message.getJMSMessageID());
         } catch (JMSException e) {
            e.printStackTrace();
         }
      });
      testConsumers.add(testConsumer);

      produceMessage(consumerSession, destination, maxConsumers * prefetch);

      assertTrue("add messages are delivered", Wait.waitFor(() -> {
         int totalDelivered = 0;
         for (TestConsumer testConsumer13 : testConsumers) {
            long delivered = testConsumer13.deliveredSize();
            LOG.info(testConsumer13.getConsumerId() + " delivered: " + delivered);
            totalDelivered += delivered;
         }
         return totalDelivered == maxConsumers * prefetch;
      }));

      final CountDownLatch shutdownConsumerAdded = new CountDownLatch(1);

      new Thread(() -> {
         try {
            LOG.info("add last consumer...");
            TestConsumer testConsumer12 = new TestConsumer(consumerSession, destination, connection);
            testConsumer12.setMessageListener(message -> {
               try {
                  LOG.info("onMessage:" + message.getJMSMessageID());
               } catch (JMSException e) {
                  e.printStackTrace();
               }
            });
            testConsumers.add(testConsumer12);
            shutdownConsumerAdded.countDown();
            LOG.info("done add last consumer");
         } catch (Exception e) {
            e.printStackTrace();
         }
      }).start();

      brokerStopLatch.await();
      doByteman.set(false);

      broker = createBroker();
      broker.start();

      assertTrue("consumer added through failover", shutdownConsumerAdded.await(30, TimeUnit.SECONDS));

      // each should again get prefetch messages - all unacked deliveries should be rolledback
      assertTrue("after restart all messages are re dispatched", Wait.waitFor(() -> {
         int totalDelivered = 0;
         for (TestConsumer testConsumer1 : testConsumers) {
            long delivered = testConsumer1.deliveredSize();
            LOG.info(testConsumer1.getConsumerId() + " delivered: " + delivered);
            totalDelivered += delivered;
         }
         return totalDelivered == maxConsumers * prefetch;
      }));

      connection.close();
   }

   public void doTestFailoverConsumerDups(final boolean watchTopicAdvisories) throws Exception {

      maxConsumers = 4;
      broker = createBroker();
      broker.start();

      brokerStopLatch = new CountDownLatch(1);
      doByteman.set(true);

      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + url + ")");
      cf.setWatchTopicAdvisories(watchTopicAdvisories);

      final ActiveMQConnection connection = (ActiveMQConnection) cf.createConnection();
      connection.start();

      final Session consumerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      final Queue destination = consumerSession.createQueue(QUEUE_NAME + "?jms.consumer.prefetch=" + prefetch);

      final Vector<TestConsumer> testConsumers = new Vector<>();
      for (int i = 0; i < maxConsumers - 1; i++) {
         testConsumers.add(new TestConsumer(consumerSession, destination, connection));
      }

      assureQueueMessages(0, SimpleString.of(QUEUE_NAME));

      produceMessage(consumerSession, destination, maxConsumers * prefetch);

      assertTrue("add messages are dispatched", Wait.waitFor(() -> {
         int totalUnconsumed = 0;
         for (TestConsumer testConsumer : testConsumers) {
            long unconsumed = testConsumer.unconsumedSize();
            LOG.info(testConsumer.getConsumerId() + " unconsumed: " + unconsumed);
            totalUnconsumed += unconsumed;
         }
         return totalUnconsumed == (maxConsumers - 1) * prefetch;
      }));

      final CountDownLatch shutdownConsumerAdded = new CountDownLatch(1);

      new Thread(() -> {
         try {
            LOG.info("add last consumer...");
            testConsumers.add(new TestConsumer(consumerSession, destination, connection));
            shutdownConsumerAdded.countDown();
            LOG.info("done add last consumer");
         } catch (Exception e) {
            e.printStackTrace();
         }
      }).start();

      // verify interrupt
      assertTrue("add messages dispatched and unconsumed are cleaned up", Wait.waitFor(() -> {
         int totalUnconsumed = 0;
         for (TestConsumer testConsumer : testConsumers) {
            long unconsumed = testConsumer.unconsumedSize();
            LOG.info(testConsumer.getConsumerId() + " unconsumed: " + unconsumed);
            totalUnconsumed += unconsumed;
         }
         return totalUnconsumed == 0;
      }));

      brokerStopLatch.await();
      doByteman.set(false);

      broker = createBroker();
      broker.start();

      assertTrue("consumer added through failover", shutdownConsumerAdded.await(30, TimeUnit.SECONDS));

      // each should again get prefetch messages - all unconsumed deliveries should be rolledback
      assertTrue("after start all messages are re dispatched", Wait.waitFor(() -> {
         int totalUnconsumed = 0;
         for (TestConsumer testConsumer : testConsumers) {
            long unconsumed = testConsumer.unconsumedSize();
            LOG.info(testConsumer.getConsumerId() + " after restart: unconsumed: " + unconsumed);
            totalUnconsumed += unconsumed;
         }
         return totalUnconsumed == (maxConsumers) * prefetch;
      }));

      connection.close();
   }

   private void assureQueueMessages(int num, SimpleString queueName) {
      QueueImpl queue = (QueueImpl) broker.getActiveMQServer().getPostOffice().getBinding(queueName).getBindable();
      Assert.assertEquals(num, queue.getMessageCount());
   }

   private void produceMessage(final Session producerSession, Queue destination, long count) throws JMSException {
      MessageProducer producer = producerSession.createProducer(destination);
      for (int i = 0; i < count; i++) {
         TextMessage message = producerSession.createTextMessage("Test message " + i);
         producer.send(message);
      }
      producer.close();
   }

   // allow access to unconsumedMessages
   class TestConsumer extends ActiveMQMessageConsumer {

      TestConsumer(Session consumerSession, Destination destination, ActiveMQConnection connection) throws Exception {
         super((ActiveMQSession) consumerSession, new ConsumerId(new SessionId(connection.getConnectionInfo().getConnectionId(), 1), nextGen()), ActiveMQMessageTransformation.transformDestination(destination), null, "", prefetch, -1, false, false, true, null);
      }

      public int unconsumedSize() {
         return unconsumedMessages.size();
      }

      public int deliveredSize() {
         return deliveredMessages.size();
      }
   }

   static long idGen = 100;

   private static long nextGen() {
      idGen -= 5;
      return idGen;
   }

   public static void holdResponseAndStopBroker(OpenWireConnection.CommandProcessor context) {
      if (doByteman.get()) {
         if (consumerCount.incrementAndGet() == maxConsumers) {
            context.getContext().setDontSendReponse(true);
            new Thread(() -> {
               try {
                  broker.stop();
                  brokerStopLatch.countDown();
               } catch (Exception e) {
                  e.printStackTrace();
               }
            }).start();
         }
      }
   }

   public static void holdResponseAndStopBroker2(OpenWireConnection.CommandProcessor context) {
      if (doByteman.get()) {
         if (consumerCount.incrementAndGet() == maxConsumers + (watchTopicAdvisories.get() ? 1 : 0)) {
            context.getContext().setDontSendReponse(true);
            new Thread(() -> {
               try {
                  broker.stop();
                  Assert.assertEquals(1, brokerStopLatch.getCount());
                  brokerStopLatch.countDown();
               } catch (Exception e) {
                  e.printStackTrace();
               }
            }).start();
         }
      }
   }

}
