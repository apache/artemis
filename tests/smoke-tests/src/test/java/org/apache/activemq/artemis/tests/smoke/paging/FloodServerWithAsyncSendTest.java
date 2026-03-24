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

package org.apache.activemq.artemis.tests.smoke.paging;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class FloodServerWithAsyncSendTest extends SmokeTestBase {

   private static final Logger logger = Logger.getLogger(FloodServerWithAsyncSendTest.class);
   public static final String SERVER_NAME_0 = "paging";

   volatile boolean running = true;

   AtomicInteger errors = new AtomicInteger(0);

   @Before
   public void before() throws Exception {
      cleanupData(SERVER_NAME_0);
      startServer(SERVER_NAME_0, 0, 30000);
   }

   @Test
   public void testAsyncPagingOpenWire() throws Exception {
      String protocol = "OPENWIRE";
      internalTest(protocol);

   }

   ConnectionFactory newCF(String protocol) {
      if (protocol.equalsIgnoreCase("OPENWIRE")) {
         return CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616?jms.useAsyncSend=true");
      } else {
         Assert.fail("unsuported protocol");
         return null;
      }
   }

   private void internalTest(String protocol) throws Exception {
      ExecutorService executorService = Executors.newFixedThreadPool(4);
      try {
         for (int i = 0; i < 2; i++) {
            final String queueName = "queue" + i;
            executorService.execute(() -> produce(protocol, queueName));
            executorService.execute(() -> infiniteConsume(protocol, queueName));
         }

         Thread.sleep(10_000);

         running = false;

         executorService.shutdown();
         Assert.assertTrue(executorService.awaitTermination(1, TimeUnit.MINUTES));

         for (int i = 0; i < 2; i++) {
            Assert.assertEquals("should have received at least a few messages", 20, consume(protocol, "queue" + i, 20));
         }

         ConnectionFactory factory = newCF("openwire");
         Connection connection = factory.createConnection();
         connection.start();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue("queue3");
         MessageConsumer consumer = session.createConsumer(queue);

         MessageProducer producer = session.createProducer(queue);

         String random = RandomUtil.randomString();

         producer.send(session.createTextMessage(random));
         TextMessage message = (TextMessage) consumer.receive(1000);
         Assert.assertNotNull(message);
         Assert.assertEquals(random, message.getText());
         connection.close();

         Assert.assertEquals(0, errors.get());
      } finally {
         running = false;
         executorService.shutdownNow(); // just to avoid thread leakage in case anything failed on the test
      }

   }


   protected int infiniteConsume(String protocol, String queueName) {
      ConnectionFactory factory = newCF(protocol);
      Connection connection = null;
      int rec = 0;
      try {
         connection = factory.createConnection();
         connection.start();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);
         MessageConsumer consumer = session.createConsumer(queue);
         while (running) {
            if (consumer.receive(5000) != null) {
               rec++;
            } else {
               break;
            }
            if (rec % 10 == 0) {
               logger.info(queueName + " receive " + rec);
            }
         }

         return rec;
      } catch (Exception e) {
         e.printStackTrace();
         errors.incrementAndGet();
         return -1;
      } finally {
         try {
            connection.close();
         } catch (Exception ignored) {
         }
      }
   }



   protected int consume(String protocol, String queueName, int maxCount) throws Exception {
      ConnectionFactory factory = newCF(protocol);
      Connection connection = null;
      int rec = 0;
      try {
         connection = factory.createConnection();
         connection.start();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);
         MessageConsumer consumer = session.createConsumer(queue);
         while (rec < maxCount) {
            if (consumer.receive(5000) != null) {
               rec++;
            } else {
               break;
            }
            if (rec % 10 == 0) {
               logger.info(queueName + " receive " + rec);
            }
         }

         return rec;
      } finally {
         try {
            connection.close();
         } catch (Exception ignored) {
         }
      }
   }

   protected void produce(String protocol, String queueName) {

      int produced = 0;
      ConnectionFactory factory = newCF(protocol);
      Connection connection = null;
      try {

         connection = factory.createConnection();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);
         MessageProducer producer = session.createProducer(queue);
         String randomString;
         {
            StringBuffer buffer = new StringBuffer();
            while (buffer.length() < 10000) {
               buffer.append(RandomUtil.randomString());
            }
            randomString = buffer.toString();
         }

         while (running) {
            if (++produced % 10 == 0) {
               logger.info(queueName + " produced " + produced + " messages");
            }
            producer.send(session.createTextMessage(randomString));
         }

      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
         errors.incrementAndGet();
      } finally {
         try {
            connection.close();
         } catch (Exception ignored) {
         }
      }
   }

}
