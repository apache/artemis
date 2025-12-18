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

package org.apache.activemq.artemis.tests.db.largeMessages;

import static org.apache.activemq.artemis.utils.TestParameters.testProperty;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.tests.db.common.Database;
import org.apache.activemq.artemis.tests.db.common.ParameterDBTestBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(ParameterizedTestExtension.class)
public class RealServerDatabaseLargeMessageTest extends ParameterDBTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String TEST_NAME = "LMDB";

   private static final int MESSAGE_SIZE = Integer.parseInt(testProperty(TEST_NAME, "MESSAGE_SIZE", "300000"));

   private static final int PRODUCERS = 50;

   private static final int MESSAGES_PER_PRODUCER = 2;

   private ExecutorService executorService;

   Process serverProcess;

   @Parameters(name = "db={0}")
   public static Collection<Object[]> parameters() {
      return convertParameters(Database.selectedList());
   }


   @BeforeEach
   public void before() throws Exception {
      serverProcess = startServer(database.getName(), 0, 60_000);
      executorService = Executors.newFixedThreadPool(PRODUCERS + 1); // there will be one consumer
      runAfter(executorService::shutdownNow);
   }

   @TestTemplate
   public void testLargeMessage() throws Exception {
      testLargeMessage("CORE");
      testLargeMessage("AMQP");
      testLargeMessage("OPENWIRE");
   }

   public void testLargeMessage(String protocol) throws Exception {
      if (database == Database.POSTGRES) {
         prepareCheckPostgres();
      }
      logger.info("testLargeMessage({})", protocol);
      final String queueName = "QUEUE_" + RandomUtil.randomUUIDString() + "_" + protocol + "_" + database;

      ConnectionFactory connectionFactory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      AtomicInteger errors = new AtomicInteger(0);
      CyclicBarrier startflag = new CyclicBarrier(PRODUCERS);
      CountDownLatch done = new CountDownLatch(PRODUCERS + 1);

      byte[] messageLoad = RandomUtil.randomBytes(MESSAGE_SIZE);

      for (int i = 0; i < PRODUCERS; i++) {
         executorService.execute(() -> {
            try {
               try (Connection connection = connectionFactory.createConnection()) {
                  Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
                  Queue queue = session.createQueue(queueName);
                  MessageProducer producer = session.createProducer(queue);

                  // align all producers right before start sending
                  startflag.await(5, TimeUnit.SECONDS);

                  for (int messageI = 0; messageI < MESSAGES_PER_PRODUCER; messageI++) {
                     BytesMessage message = session.createBytesMessage();
                     message.writeBytes(messageLoad);
                     producer.send(message);
                     session.commit();
                  }
               }
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
               errors.incrementAndGet();
            } finally {
               done.countDown();
            }
         });
      }

      executorService.execute(() -> {
         try (Connection connection = connectionFactory.createConnection()) {
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue queue = session.createQueue(queueName);
            MessageConsumer consumer = session.createConsumer(queue);
            for (int messageI = 0; messageI < PRODUCERS * MESSAGES_PER_PRODUCER; messageI++) {
               BytesMessage message = (BytesMessage) consumer.receive(10_000);
               assertNotNull(message);
               logger.debug("Received message");
               assertEquals(messageLoad.length, message.getBodyLength());
               byte[] messageRead = new byte[(int)message.getBodyLength()];
               message.readBytes(messageRead);
               assertArrayEquals(messageLoad, messageRead);
               if (messageI % 5 == 0) {
                  session.commit();
               }
            }
            session.commit();
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
            errors.incrementAndGet();
         } finally {
            done.countDown();
         }
      });

      assertTrue(done.await(120, TimeUnit.SECONDS));
      assertEquals(0, errors.get());

      if (database == Database.POSTGRES) {
         checkPostgres();
      }

   }

}
