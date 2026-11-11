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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CreateQueueIdempotentTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig(), true));
      server.start();
   }

   @Test
   public void testSequentialCreateQueueIdempotency() throws Exception {
      final SimpleString QUEUE = SimpleString.of("SequentialCreateQueueIdempotency");

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE));

      try {
         session.createQueue(QueueConfiguration.of(QUEUE));
         fail("Expected exception, queue already exists");
      } catch (ActiveMQQueueExistsException qee) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
   }

   @Test
   public void testConcurrentCreateQueueIdempotency() throws Exception {
      final String QUEUE = "ConcurrentCreateQueueIdempotency";
      AtomicInteger queuesCreated = new AtomicInteger(0);
      AtomicInteger failedAttempts = new AtomicInteger(0);

      final int NUM_THREADS = 5;

      QueueCreator[] queueCreators = new QueueCreator[NUM_THREADS];

      for (int i = 0; i < NUM_THREADS; i++) {
         QueueCreator queueCreator = new QueueCreator(QUEUE, queuesCreated, failedAttempts);
         queueCreators[i] = queueCreator;
      }

      for (int i = 0; i < NUM_THREADS; i++) {
         queueCreators[i].start();
      }

      for (int i = 0; i < NUM_THREADS; i++) {
         queueCreators[i].join();
      }

      server.stop();

      // re-starting the server appears to be an unreliable guide
      server.start();

      assertEquals(1, queuesCreated.intValue());
      assertEquals(NUM_THREADS - 1, failedAttempts.intValue());
   }

   class QueueCreator extends Thread {

      private String queueName = null;
      private AtomicInteger queuesCreated = null;
      private AtomicInteger failedAttempts = null;

      QueueCreator(String queueName, AtomicInteger queuesCreated, AtomicInteger failedAttempts) {
         this.queueName = queueName;
         this.queuesCreated = queuesCreated;
         this.failedAttempts = failedAttempts;
      }

      @Override
      public void run() {
         ServerLocator locator = null;
         ClientSession session = null;

         try {
            locator = createInVMNonHALocator();
            ClientSessionFactory sf = createSessionFactory(locator);
            session = sf.createSession(false, true, true);
            final SimpleString QUEUE = SimpleString.of(queueName);
            session.createQueue(QueueConfiguration.of(QUEUE));
            queuesCreated.incrementAndGet();
         } catch (ActiveMQQueueExistsException qne) {
            failedAttempts.incrementAndGet();
         } catch (Exception e) {
            e.printStackTrace();
         } finally {
            if (locator != null) {
               locator.close();
            }
            if (session != null) {
               try {
                  session.close();
               } catch (ActiveMQException e) {
                  e.printStackTrace();
               }
            }
         }
      }
   }
}
