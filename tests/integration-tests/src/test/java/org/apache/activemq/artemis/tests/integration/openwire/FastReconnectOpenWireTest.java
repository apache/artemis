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

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.transport.tcp.TcpTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastReconnectOpenWireTest extends OpenWireTestBase {

   // change this number to give the test a bit more of spinning
   private static final int NUM_ITERATIONS = 50;

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected void configureAddressSettings(Map<String, AddressSettings> addressSettingsMap) {
      super.configureAddressSettings(addressSettingsMap);
      // force send to dlq early
      addressSettingsMap.put("exampleQueue", new AddressSettings().setAutoCreateQueues(false).setAutoCreateAddresses(false).setDeadLetterAddress(SimpleString.of("ActiveMQ.DLQ")).setAutoCreateAddresses(true).setMaxDeliveryAttempts(2));
      // force send to dlq late
      addressSettingsMap.put("exampleQueueTwo", new AddressSettings().setAutoCreateQueues(false).setAutoCreateAddresses(false).setDeadLetterAddress(SimpleString.of("ActiveMQ.DLQ")).setAutoCreateAddresses(true).setMaxDeliveryAttempts(-1));
   }


   @Test
   @Timeout(60)
   public void testFastReconnectCreateConsumerNoErrors() throws Exception {

      final ArrayList<Throwable> errors = new ArrayList<>();
      SimpleString durableQueue = SimpleString.of("exampleQueueTwo");
      this.server.createQueue(QueueConfiguration.of(durableQueue).setRoutingType(RoutingType.ANYCAST));

      Queue queue = new ActiveMQQueue(durableQueue.toString());

      final ActiveMQConnectionFactory exFact = new ActiveMQConnectionFactory("failover:(tcp://localhost:61616?closeAsync=false)?startupMaxReconnectAttempts=-1&maxReconnectAttempts=-1&timeout=5000");
      exFact.setWatchTopicAdvisories(false);
      exFact.setConnectResponseTimeout(10000);
      exFact.setClientID("myID");

      RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
      redeliveryPolicy.setRedeliveryDelay(0);
      redeliveryPolicy.setMaximumRedeliveries(-1);
      exFact.setRedeliveryPolicy(redeliveryPolicy);

      publish(1000, durableQueue.toString());

      final AtomicInteger numIterations = new AtomicInteger(NUM_ITERATIONS);
      ExecutorService executor = Executors.newCachedThreadPool();
      runAfter(executor::shutdownNow);

      final int concurrent = 2;
      final CountDownLatch done = new CountDownLatch(concurrent);
      for (int i = 0; i < concurrent; i++) {
         executor.execute(() -> {
            try {
               while (numIterations.decrementAndGet() > 0) {
                  try (Connection conn = exFact.createConnection(); Session consumerConnectionSession = conn.createSession(true, Session.SESSION_TRANSACTED); MessageConsumer messageConsumer = consumerConnectionSession.createConsumer(queue)) {

                     messageConsumer.receiveNoWait();

                     try {
                        // force a local socket close such that the broker sees an exception on the connection and fails the consumer via serverConsumer close
                        ((ActiveMQConnection) conn).getTransport().narrow(TcpTransport.class).stop();
                     } catch (Throwable expected) {
                     }

                  } catch (javax.jms.InvalidClientIDException expected) {
                     // deliberate clash across concurrent connections
                  } catch (Throwable unexpected) {
                     logger.warn(unexpected.getMessage(), unexpected);
                     errors.add(unexpected);
                     numIterations.set(0);
                  }
               }
            } finally {
               done.countDown();
            }
         });
      }

      assertTrue(done.await(30, TimeUnit.SECONDS));

      Wait.assertEquals(0, () -> server.locateQueue(durableQueue).getConsumerCount(), 5000);
      assertTrue(errors.isEmpty());

   }

   @Test
   @Timeout(60)
   public void testFastReconnectCreateConsumerNoErrorsNoClientId() throws Exception {

      final ArrayList<Throwable> errors = new ArrayList<>();
      SimpleString durableQueue = SimpleString.of("exampleQueueTwo");
      this.server.createQueue(QueueConfiguration.of(durableQueue).setRoutingType(RoutingType.ANYCAST));

      Queue queue = new ActiveMQQueue(durableQueue.toString());

      final ActiveMQConnectionFactory exFact = new ActiveMQConnectionFactory("failover:(tcp://localhost:61616?closeAsync=false)?startupMaxReconnectAttempts=-1&maxReconnectAttempts=-1&timeout=5000");
      exFact.setWatchTopicAdvisories(false);
      exFact.setConnectResponseTimeout(10000);

      RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
      redeliveryPolicy.setRedeliveryDelay(0);
      redeliveryPolicy.setMaximumRedeliveries(-1);
      exFact.setRedeliveryPolicy(redeliveryPolicy);

      publish(1000, durableQueue.toString());

      final AtomicInteger numIterations = new AtomicInteger(NUM_ITERATIONS);
      ExecutorService executor = Executors.newCachedThreadPool();
      runAfter(executor::shutdownNow);

      final int concurrent = 2;
      CountDownLatch done = new CountDownLatch(concurrent);
      for (int i = 0; i < concurrent; i++) {
         executor.execute(() -> {
            try (Connection conn = exFact.createConnection();
                 Session consumerConnectionSession = conn.createSession(true, Session.SESSION_TRANSACTED);
                 MessageConsumer messageConsumer = consumerConnectionSession.createConsumer(queue)
            ) {
               conn.start();
               while (numIterations.decrementAndGet() > 0) {
                  try {
                     messageConsumer.receiveNoWait();

                     try {
                        // force a local socket close such that the broker sees an exception on the connection and fails the consumer via serverConsumer close
                        ((ActiveMQConnection) conn).getTransport().narrow(TcpTransport.class).stop();
                     } catch (Throwable expected) {
                     }
                  } catch (Throwable unexpected) {
                     errors.add(unexpected);
                  }
               }
            } catch (Throwable unexpected) {
               numIterations.set(0);
               unexpected.printStackTrace();
               errors.add(unexpected);
            } finally {
               done.countDown();
            }
         });
      }

      assertTrue(done.await(30, TimeUnit.SECONDS));

      Wait.assertEquals(0, () -> server.locateQueue(durableQueue).getConsumerCount(), 5000);

      assertTrue(errors.isEmpty());

   }

   private void publish(int numMessages, String name) throws Exception {
      final ActiveMQConnectionFactory exFact = new ActiveMQConnectionFactory();
      Connection exConn = exFact.createConnection();
      exConn.start();

      Queue queue = new ActiveMQQueue(name);

      // put a few messages on the queue to have the broker do some dispatch work
      Session session = exConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(queue);

      TextMessage message = session.createTextMessage("This is a text message");
      for (int i = 0; i < numMessages; i++) {
         message.setIntProperty("SEQ", i);
         producer.send(message);
      }
      session.close();
      exConn.close();

   }
}
