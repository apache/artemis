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

import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.activemq.broker.artemiswrapper.OpenwireArtemisBaseTest;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMRules;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(BMUnitRunner.class)
public class FailoverConsumerOutstandingCommitTest extends OpenwireArtemisBaseTest {

   private static final Logger LOG = LoggerFactory.getLogger(FailoverConsumerOutstandingCommitTest.class);
   private static final String QUEUE_NAME = "FailoverWithOutstandingCommit";
   private static final String MESSAGE_TEXT = "Test message ";
   private static final String url = newURI(0);
   final int prefetch = 10;
   private static EmbeddedJMS server;
   private static final AtomicBoolean doByteman = new AtomicBoolean(false);
   private static CountDownLatch brokerStopLatch = new CountDownLatch(1);

   @After
   public void stopBroker() throws Exception {
      if (server != null) {
         server.stop();
      }
   }

   @Test
   @BMRules(
      rules = {@BMRule(
         name = "set no return response",
         targetClass = "org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection$CommandProcessor",
         targetMethod = "processCommitTransactionOnePhase",
         targetLocation = "ENTRY",
         action = "org.apache.activemq.transport.failover.FailoverConsumerOutstandingCommitTest.holdResponse($0)"), @BMRule(
         name = "stop broker before commit",
         targetClass = "org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection$CommandProcessor",
         targetMethod = "processCommitTransactionOnePhase",
         targetLocation = "ENTRY",
         action = "org.apache.activemq.transport.failover.FailoverConsumerOutstandingCommitTest.stopServerInTransaction()"),})
   public void testFailoverConsumerDups() throws Exception {
      doTestFailoverConsumerDups(true);
   }

   public void doTestFailoverConsumerDups(final boolean watchTopicAdvisories) throws Exception {

      server = createBroker();
      server.start();
      brokerStopLatch = new CountDownLatch(1);

      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + url + ")");
      cf.setWatchTopicAdvisories(watchTopicAdvisories);
      cf.setDispatchAsync(false);

      final ActiveMQConnection connection = (ActiveMQConnection) cf.createConnection();
      connection.start();

      final Session producerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      final Queue destination = producerSession.createQueue(QUEUE_NAME + "?consumer.prefetchSize=" + prefetch);

      final Session consumerSession = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);

      final CountDownLatch commitDoneLatch = new CountDownLatch(1);
      final CountDownLatch messagesReceived = new CountDownLatch(2);

      final MessageConsumer testConsumer = consumerSession.createConsumer(destination);
      doByteman.set(true);
      testConsumer.setMessageListener(message -> {
         LOG.info("consume one and commit");

         assertNotNull("got message", message);

         try {
            consumerSession.commit();
         } catch (JMSException e) {
            e.printStackTrace();
         }
         commitDoneLatch.countDown();
         messagesReceived.countDown();
         LOG.info("done commit");
      });

      // may block if broker shutodwn happens quickly
      new Thread(() -> {
         LOG.info("producer started");
         try {
            produceMessage(producerSession, destination, prefetch * 2);
         } catch (IllegalStateException SessionClosedExpectedOnShutdown) {
         } catch (JMSException e) {
            e.printStackTrace();
            fail("unexpceted ex on producer: " + e);
         }
         LOG.info("producer done");
      }).start();

      // will be stopped by the plugin
      brokerStopLatch.await();
      server.stop();
      server = createBroker();
      doByteman.set(false);
      server.start();

      assertTrue("consumer added through failover", commitDoneLatch.await(20, TimeUnit.SECONDS));
      assertTrue("another message was received after failover", messagesReceived.await(20, TimeUnit.SECONDS));

      connection.close();
   }

   @Test
   public void testRollbackFailoverConsumerTx() throws Exception {
      server = createBroker();
      server.start();

      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + url + ")");
      cf.setConsumerFailoverRedeliveryWaitPeriod(10000);
      final ActiveMQConnection connection = (ActiveMQConnection) cf.createConnection();
      connection.start();

      final Session producerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      final Queue destination = producerSession.createQueue(QUEUE_NAME);

      final Session consumerSession = connection.createSession(true, Session.SESSION_TRANSACTED);
      final MessageConsumer testConsumer = consumerSession.createConsumer(destination);
      assertNull("no message yet", testConsumer.receiveNoWait());

      produceMessage(producerSession, destination, 1);
      producerSession.close();

      // consume then rollback after restart
      Message msg = testConsumer.receive(5000);
      assertNotNull(msg);

      // restart with outstanding delivered message
      server.stop();
      server = createBroker();
      server.start();

      consumerSession.rollback();

      // receive again
      msg = testConsumer.receive(10000);
      assertNotNull("got message again after rollback", msg);

      consumerSession.commit();

      // close before sweep
      consumerSession.close();
      msg = receiveMessage(cf, destination);
      assertNull("should be nothing left after commit", msg);
      connection.close();
   }

   private Message receiveMessage(ActiveMQConnectionFactory cf, Queue destination) throws Exception {
      final ActiveMQConnection connection = (ActiveMQConnection) cf.createConnection();
      connection.start();
      final Session consumerSession = connection.createSession(true, Session.SESSION_TRANSACTED);
      final MessageConsumer consumer = consumerSession.createConsumer(destination);
      Message msg = consumer.receive(5000);
      consumerSession.commit();
      connection.close();
      return msg;
   }

   private void produceMessage(final Session producerSession, Queue destination, long count) throws JMSException {
      MessageProducer producer = producerSession.createProducer(destination);
      for (int i = 0; i < count; i++) {
         TextMessage message = producerSession.createTextMessage(MESSAGE_TEXT + i);
         producer.send(message);
      }
      producer.close();
   }

   public static void holdResponse(OpenWireConnection.CommandProcessor context) {
      if (doByteman.get()) {
         context.getContext().setDontSendReponse(true);
      }
   }

   public static void stopServerInTransaction() {
      if (doByteman.get()) {
         new Thread(() -> {
            LOG.info("Stopping broker in transaction...");
            try {
               server.stop();
            } catch (Exception e) {
               e.printStackTrace();
            } finally {
               brokerStopLatch.countDown();
            }
         }).start();
      }
   }
}
