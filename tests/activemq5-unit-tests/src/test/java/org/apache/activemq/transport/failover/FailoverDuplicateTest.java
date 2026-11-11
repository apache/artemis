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

import javax.jms.Connection;
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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.activemq.broker.artemiswrapper.OpenwireArtemisBaseTest;
import org.apache.activemq.util.Wait;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMRules;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(BMUnitRunner.class)
public class FailoverDuplicateTest extends OpenwireArtemisBaseTest {

   private static final Logger LOG = LoggerFactory.getLogger(FailoverDuplicateTest.class);
   private static final String QUEUE_NAME = "TestQueue";

   private static final AtomicBoolean doByteman = new AtomicBoolean(false);
   private static final AtomicBoolean first = new AtomicBoolean(false);
   private static final CountDownLatch gotMessageLatch = new CountDownLatch(1);
   private static final CountDownLatch producersDone = new CountDownLatch(1);

   private String url = newURI(0);
   EmbeddedJMS broker;

   @After
   public void tearDown() throws Exception {
      stopBroker();
   }

   protected void stopBroker() throws Exception {
      if (broker != null) {
         broker.stop();
      }
   }

   public void configureConnectionFactory(ActiveMQConnectionFactory factory) {
      factory.setAuditMaximumProducerNumber(2048);
      factory.setOptimizeAcknowledge(true);
   }

   @Test
   @BMRules(
      rules = {@BMRule(
         name = "set no return response and stop the broker",
         targetClass = "org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection$CommandProcessor",
         targetMethod = "processMessage",
         targetLocation = "EXIT",
         action = "org.apache.activemq.transport.failover.FailoverDuplicateTest.holdResponseAndStopConn($0)")})
   public void testFailoverSendReplyLost() throws Exception {

      broker = createBroker();
      broker.start();
      doByteman.set(true);

      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + url + ")?jms.watchTopicAdvisories=false");
      configureConnectionFactory(cf);
      Connection sendConnection = cf.createConnection();
      sendConnection.start();

      final Session sendSession = sendConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      final Queue destination = sendSession.createQueue(QUEUE_NAME);

      final AtomicInteger receivedCount = new AtomicInteger();
      MessageListener listener = message -> {
         gotMessageLatch.countDown();
         receivedCount.incrementAndGet();
      };
      Connection receiveConnection;
      Session receiveSession = null;
      receiveConnection = cf.createConnection();
      receiveConnection.start();
      receiveSession = receiveConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      receiveSession.createConsumer(destination).setMessageListener(listener);

      final CountDownLatch sendDoneLatch = new CountDownLatch(1);
      // broker will die on send reply so this will hang till restart
      new Thread(() -> {
         LOG.info("doing async send...");
         try {
            produceMessage(sendSession, destination, "will resend", 1);
         } catch (JMSException e) {
            LOG.error("got send exception: ", e);
            Assert.fail("got unexpected send exception" + e);
         }
         sendDoneLatch.countDown();
         LOG.info("done async send");
      }).start();

      Assert.assertTrue("one message got through on time", gotMessageLatch.await(20, TimeUnit.SECONDS));
      // send more messages, blow producer audit
      final int numProducers = 1050;
      final int numPerProducer = 2;
      final int totalSent = numPerProducer * numProducers + 1;
      for (int i = 0; i < numProducers; i++) {
         produceMessage(receiveSession, destination, "new producer " + i, numPerProducer);
         // release resend when we half done, cursor audit exhausted
         // and concurrent dispatch with the resend
         if (i == 1025) {
            LOG.info("count down producers done");
            producersDone.countDown();
         }
      }

      Assert.assertTrue("message sent complete through failover", sendDoneLatch.await(30, TimeUnit.SECONDS));

      Wait.waitFor(() -> {
         LOG.info("received count:" + receivedCount.get());
         return totalSent <= receivedCount.get();
      });
      Assert.assertEquals("we got all produced messages", totalSent, receivedCount.get());
      sendConnection.close();
      receiveConnection.close();

      // ensure no dangling messages with fresh broker etc
      broker.stop();
      doByteman.set(false);

      LOG.info("Checking for remaining/hung messages with second restart..");
      broker = createBroker();
      broker.start();

      // after restart, ensure no dangling messages
      cf = new ActiveMQConnectionFactory("failover:(" + url + ")");
      configureConnectionFactory(cf);
      sendConnection = cf.createConnection();
      sendConnection.start();
      Session session2 = sendConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer = session2.createConsumer(destination);
      Message msg = consumer.receive(1000);
      if (msg == null) {
         msg = consumer.receive(5000);
      }
      Assert.assertNull("no messges left dangling but got: " + msg, msg);

      sendConnection.close();
   }

   private void produceMessage(final Session producerSession,
                               Queue destination,
                               final String text,
                               final int count) throws JMSException {
      MessageProducer producer = producerSession.createProducer(destination);
      for (int i = 0; i < count; i++) {
         TextMessage message = producerSession.createTextMessage(text + ", count:" + i);
         producer.send(message);
      }
      producer.close();
   }

   public static void holdResponseAndStopConn(final OpenWireConnection.CommandProcessor context) {
      if (doByteman.get()) {
         if (first.compareAndSet(false, true)) {
            context.getContext().setDontSendReponse(true);
            new Thread(() -> {
               try {
                  LOG.info("Waiting for recepit");
                  Assert.assertTrue("message received on time", gotMessageLatch.await(60, TimeUnit.SECONDS));
                  Assert.assertTrue("new producers done on time", producersDone.await(120, TimeUnit.SECONDS));
                  LOG.info("Stopping connection post send and receive and multiple producers");
                  context.getContext().getConnection().fail(null, "test Failoverduplicatetest");
               } catch (Exception e) {
                  e.printStackTrace();
               }
            }).start();
         }
      }
   }

}
