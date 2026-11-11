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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessageRateTest extends ActiveMQTestBase {


   private final SimpleString ADDRESS = SimpleString.of("ADDRESS");

   private ServerLocator locator;



   @Test
   public void testProduceRate() throws Exception {
      ActiveMQServer server = createServer(false);

      server.start();

      locator.setProducerMaxRate(10);
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);
      long start = System.currentTimeMillis();
      for (int i = 0; i < 10; i++) {
         producer.send(session.createMessage(false));
      }
      long end = System.currentTimeMillis();

      assertTrue(end - start >= 1000, "TotalTime = " + (end - start));

      session.close();
   }

   @Test
   public void testConsumeRate() throws Exception {
      ActiveMQServer server = createServer(false);

      server.start();

      locator.setConsumerMaxRate(10);
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      for (int i = 0; i < 12; i++) {
         producer.send(session.createMessage(false));
      }

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      long start = System.currentTimeMillis();

      for (int i = 0; i < 12; i++) {
         consumer.receive(1000);
      }

      long end = System.currentTimeMillis();

      assertTrue(end - start >= 1000, "TotalTime = " + (end - start));

      session.close();
   }

   @Test
   public void testConsumeRate2() throws Exception {
      ActiveMQServer server = createServer(false);

      server.start();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      for (int i = 0; i < 12; i++) {
         producer.send(session.createMessage(false));
      }

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS, null, 1024 * 1024, 10, false);

      long start = System.currentTimeMillis();

      for (int i = 0; i < 12; i++) {
         consumer.receive(1000);
      }

      long end = System.currentTimeMillis();

      assertTrue(end - start >= 1000, "TotalTime = " + (end - start));

      session.close();
   }

   @Test
   public void testConsumeRateListener() throws Exception {
      ActiveMQServer server = createServer(false);

      server.start();

      locator.setConsumerMaxRate(10);
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      for (int i = 0; i < 12; i++) {
         producer.send(session.createMessage(false));
      }

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      final AtomicInteger failures = new AtomicInteger(0);

      final CountDownLatch messages = new CountDownLatch(12);

      consumer.setMessageHandler(message -> {
         try {
            message.acknowledge();
            messages.countDown();
         } catch (Exception e) {
            e.printStackTrace(); // Hudson report
            failures.incrementAndGet();
         }
      });

      long start = System.currentTimeMillis();
      session.start();
      assertTrue(messages.await(5, TimeUnit.SECONDS));
      long end = System.currentTimeMillis();

      assertTrue(end - start >= 1000, "TotalTime = " + (end - start));

      session.close();
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      locator = createInVMNonHALocator();
   }

}
