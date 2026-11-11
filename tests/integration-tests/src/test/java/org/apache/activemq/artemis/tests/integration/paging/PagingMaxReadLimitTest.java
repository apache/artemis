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
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PagingMaxReadLimitTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   ActiveMQServer server;

   @Test
   public void testMaxReadPageMessages() throws Exception {

      ExecutorService service = Executors.newSingleThreadExecutor();
      runAfter(service::shutdownNow);

      Configuration config = createDefaultConfig(true);
      config.setJournalSyncTransactional(false).setJournalSyncTransactional(false);

      final int PAGE_MAX = 20 * 1024;

      final int PAGE_SIZE = 10 * 1024;

      server = createServer(true, config, PAGE_SIZE, PAGE_MAX, 100, -1, (long) (PAGE_MAX * 10), null, null, null);
      server.start();

      server.addAddressInfo(new AddressInfo(getName()).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(getName()).setRoutingType(RoutingType.ANYCAST));

      Wait.assertTrue(() -> server.locateQueue(getName()) != null);

      org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(getName());

      ConnectionFactory connectionFactory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

      try (Connection connection = connectionFactory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(getName());
         MessageProducer producer = session.createProducer(queue);
         producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         for (int i = 0; i < 500; i++) {
            producer.send(session.createTextMessage("Hello " + i));
         }
         session.commit();

         assertTrue(serverQueue.getPagingStore().isPaging());
      }

      AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler();
      runAfter(() -> loggerHandler.close());

      AtomicInteger errorCounter = new AtomicInteger(0);
      CountDownLatch done = new CountDownLatch(1);

      service.execute(() -> {
         try (Connection connection = connectionFactory.createConnection()) {
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue queue = session.createQueue(getName());
            MessageConsumer consumer = session.createConsumer(queue);

            for (int i = 0; i < 500;) {
               Message message = consumer.receive(10);
               if (message == null) {
                  session.commit();
               } else {
                  i++;
               }
            }
            session.commit();
         } catch (Throwable e) {
            logger.debug(e.getMessage(), e);
            errorCounter.incrementAndGet();
         } finally {
            done.countDown();
         }
      });

      assertTrue(done.await(5, TimeUnit.SECONDS));
      Wait.assertTrue(() -> loggerHandler.findText("AMQ224127"), 2000, 10);
      assertEquals(0, errorCounter.get());

   }

}