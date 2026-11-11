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

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Vector;
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

import static org.junit.Assert.assertTrue;

// see: https://issues.apache.org/activemq/browse/AMQ-2877
@RunWith(BMUnitRunner.class)
public class FailoverPrefetchZeroTest extends OpenwireArtemisBaseTest {

   private static final Logger LOG = LoggerFactory.getLogger(FailoverPrefetchZeroTest.class);
   private static final String QUEUE_NAME = "FailoverPrefetchZero";

   private static final AtomicBoolean doByteman = new AtomicBoolean(false);
   private static final CountDownLatch pullDone = new CountDownLatch(1);
   private static CountDownLatch brokerStopLatch = new CountDownLatch(1);

   private String url = newURI(0);
   final int prefetch = 0;
   private static EmbeddedJMS broker;

   @After
   public void stopBroker() throws Exception {
      if (broker != null) {
         broker.stop();
      }
   }

   @Test
   @BMRules(
      rules = {@BMRule(
         name = "set no return response and stop the broker",
         targetClass = "org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection$CommandProcessor",
         targetMethod = "processMessagePull",
         targetLocation = "ENTRY",
         action = "org.apache.activemq.transport.failover.FailoverPrefetchZeroTest.holdResponseAndStopBroker($0)")})
   public void testPrefetchZeroConsumerThroughRestart() throws Exception {
      broker = createBroker();
      broker.start();
      doByteman.set(true);

      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + url + ")");
      cf.setWatchTopicAdvisories(false);

      final ActiveMQConnection connection = (ActiveMQConnection) cf.createConnection();
      connection.start();

      final Session consumerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      final Queue destination = consumerSession.createQueue(QUEUE_NAME + "?consumer.prefetchSize=" + prefetch);

      final MessageConsumer consumer = consumerSession.createConsumer(destination);
      produceMessage(consumerSession, destination, 1);

      final CountDownLatch receiveDone = new CountDownLatch(1);
      final Vector<Message> received = new Vector<>();
      new Thread(() -> {
         try {
            LOG.info("receive one...");
            Message msg = consumer.receive(30000);
            if (msg != null) {
               received.add(msg);
            }
            receiveDone.countDown();
            LOG.info("done receive");
         } catch (Exception e) {
            e.printStackTrace();
         }
      }).start();

      // will be stopped by the plugin
      assertTrue("pull completed on broker", pullDone.await(30, TimeUnit.SECONDS));
      brokerStopLatch.await();
      doByteman.set(false);
      broker = createBroker();
      broker.start();

      assertTrue("receive completed through failover", receiveDone.await(30, TimeUnit.SECONDS));

      assertTrue("we got our message:", !received.isEmpty());

      connection.close();
   }

   private void produceMessage(final Session producerSession, Queue destination, long count) throws JMSException {
      MessageProducer producer = producerSession.createProducer(destination);
      for (int i = 0; i < count; i++) {
         TextMessage message = producerSession.createTextMessage("Test message " + i);
         producer.send(message);
      }
      producer.close();
   }

   public static void holdResponseAndStopBroker(final OpenWireConnection.CommandProcessor context) {
      new Exception("trace").printStackTrace();
      if (doByteman.get()) {
         context.getContext().setDontSendReponse(true);
         pullDone.countDown();
         new Thread(() -> {
            try {
               broker.stop();
            } catch (Exception e) {
               e.printStackTrace();
            } finally {
               brokerStopLatch.countDown();
            }
         }).start();
      }
   }

}
