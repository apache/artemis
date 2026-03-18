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
package org.apache.activemq.artemis.tests.integration.paging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MultipleProducersPagingTest extends ActiveMQTestBase {

   private static final int CONSUMER_WAIT_TIME_MS = 250;
   private static final int PRODUCERS = 5;
   private static final long MESSAGES_PER_PRODUCER = 2000;
   private static final long TOTAL_MSG = MESSAGES_PER_PRODUCER * PRODUCERS;
   private ExecutorService executor;
   private CountDownLatch runnersLatch;
   private CyclicBarrier barrierLatch;

   private AtomicLong msgReceived;
   private AtomicLong msgSent;
   private final Set<Connection> connections = new HashSet<>();
   private ActiveMQServer server;
   private ConnectionFactory cf;
   private Queue queue;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      executor = Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));

      server = createServer(createBasicConfig()
                               .setPersistenceEnabled(false)
                               .setAddressSettings(Collections.singletonMap("#", new AddressSettings()
                                  .setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE)
                                  .setPageSizeBytes(50000)
                                  .setMaxSizeBytes(404850)))
                               .setAcceptorConfigurations(Collections.singleton(new TransportConfiguration(NettyAcceptorFactory.class.getName()))));

      server.start();

      cf = ActiveMQJMSClient.createConnectionFactory("tcp://127.0.0.1:61616", "cf");
      queue = ActiveMQJMSClient.createQueue("simple");

      barrierLatch = new CyclicBarrier(PRODUCERS + 1);
      runnersLatch = new CountDownLatch(PRODUCERS + 1);
      msgReceived = new AtomicLong(0);
      msgSent = new AtomicLong(0);
   }

   @Test
   public void testQueue() throws InterruptedException {
      executor.execute(new ConsumerRun());
      for (int i = 0; i < PRODUCERS; i++) {
         executor.execute(new ProducerRun());
      }
      assertTrue(runnersLatch.await(1, TimeUnit.MINUTES), "must take less than a minute to run");
      assertEquals(TOTAL_MSG, msgSent.longValue(), "number sent");
      assertEquals(TOTAL_MSG, msgReceived.longValue(), "number received");
   }

   private synchronized Session createSession() throws JMSException {
      Connection connection = cf.createConnection();
      connections.add(connection);
      connection.start();
      return connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
   }

   final class ConsumerRun implements Runnable {

      @Override
      public void run() {
         try {
            Session session = createSession();
            MessageConsumer consumer = session.createConsumer(queue);
            barrierLatch.await();
            while (true) {
               Message msg = consumer.receive(CONSUMER_WAIT_TIME_MS);
               if (msg == null) {
                  break;
               }
               msgReceived.incrementAndGet();
            }
         } catch (Exception e) {
            throw new RuntimeException(e);
         } finally {
            runnersLatch.countDown();
         }
      }

   }

   final class ProducerRun implements Runnable {

      @Override
      public void run() {
         try {
            Session session = createSession();
            MessageProducer producer = session.createProducer(queue);
            barrierLatch.await();

            for (int i = 0; i < MESSAGES_PER_PRODUCER; i++) {
               producer.send(session.createTextMessage(this.hashCode() + " counter " + i));
               msgSent.incrementAndGet();
            }
         } catch (Exception cause) {
            throw new RuntimeException(cause);
         } finally {
            runnersLatch.countDown();
         }
      }
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      executor.shutdown();
      for (Connection conn : connections) {
         conn.close();
      }
      connections.clear();
      if (server != null) {
         server.stop();
      }
      super.tearDown();
   }
}
