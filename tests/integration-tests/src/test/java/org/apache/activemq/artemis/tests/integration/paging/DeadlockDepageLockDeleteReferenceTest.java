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

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Regression test for ARTEMIS-6179.
 *
 * <p>QueueImpl.depage() acquires {@code depageLock} then {@code synchronized(this)}.
 * QueueImpl.deleteReference() is {@code synchronized(this)}, then calls {@code iterQueue()}
 * which acquires {@code depageLock}. Concurrent execution causes a classic ABBA deadlock.
 *
 * <p>Bug introduced in commit e9dbc11 (ARTEMIS-5376, Feb 27 2026). First affected release: 2.53.0.
 */
public class DeadlockDepageLockDeleteReferenceTest extends ActiveMQTestBase {

   private static final String ADDRESS = "test.deadlock.address";
   private static final String QUEUE   = "test.deadlock.queue";

   // Small address limit so paging kicks in after ~50 messages.
   private static final int PAGE_SIZE_BYTES = 128 * 1024;  // 128 KB per page file
   private static final int MAX_SIZE_BYTES  = 512 * 1024;  // 512 KB address limit
   private static final int MESSAGE_SIZE    = 10 * 1024;   // 10 KB per message
   // 100 × 10 KB = 1 MB >> 512 KB; keeps ~50 messages paged to disk.
   private static final int INITIAL_MESSAGES = 100;

   private static final long DEADLOCK_TIMEOUT_MS = 5_000;

   protected ServerLocator locator;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = createInVMNonHALocator();
   }

   @Test
   @Timeout(60)
   public void testNoDeadlockDuringDepageAndDeleteReference() throws Exception {
      ActiveMQServer server = createServer(true, createDefaultInVMConfig(), PAGE_SIZE_BYTES, MAX_SIZE_BYTES);
      server.start();

      server.createQueue(QueueConfiguration.of(QUEUE)
            .setAddress(ADDRESS)
            .setRoutingType(RoutingType.ANYCAST)
            .setDurable(true));

      QueueControl queueControl = (QueueControl) server.getManagementService()
            .getResource(ResourceNames.QUEUE + QUEUE);

      ClientSessionFactory factory = locator.createSessionFactory();

      // Fill queue past address limit to trigger paging.
      try (ClientSession sendSession = factory.createSession(false, true, true)) {
         ClientProducer producer = sendSession.createProducer(ADDRESS);
         byte[] body = new byte[MESSAGE_SIZE];
         Arrays.fill(body, (byte) 'X');
         for (int i = 0; i < INITIAL_MESSAGES; i++) {
            ClientMessage msg = sendSession.createMessage(true);
            msg.getBodyBuffer().writeBytes(body);
            producer.send(msg);
         }
      }

      // Background sender: keeps paging active by replenishing faster than the consumer drains.
      ClientSession senderSession = factory.createSession(false, true, true);
      ClientProducer bgProducer = senderSession.createProducer(ADDRESS);
      byte[] bgBody = new byte[MESSAGE_SIZE];
      Arrays.fill(bgBody, (byte) 'Y');
      Thread senderThread = new Thread(() -> {
         try {
            while (!Thread.currentThread().isInterrupted()) {
               ClientMessage msg = senderSession.createMessage(true);
               msg.getBodyBuffer().writeBytes(bgBody);
               bgProducer.send(msg);
               Thread.sleep(10); // ~100 msg/s >> consumer rate, keeps paging active
            }
         } catch (InterruptedException ignored) {
         } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted()) {
               logger.warn("sender error: {}", e.getMessage());
            }
         }
      }, "repro-sender");
      senderThread.setDaemon(true);
      senderThread.start();

      // Consumer: triggers continuous depage() (acquires depageLock, then synchronized(this)).
      ClientSession consumeSession = factory.createSession(false, false, false);
      consumeSession.start();
      ClientConsumer consumer = consumeSession.createConsumer(QUEUE);
      Thread consumerThread = new Thread(() -> {
         try {
            int batch = 0;
            while (!Thread.currentThread().isInterrupted()) {
               ClientMessage msg = consumer.receive(200);
               if (msg != null) {
                  msg.acknowledge();
                  if (++batch % 10 == 0) {
                     consumeSession.commit();
                  }
                  Thread.sleep(100); // ~10 msg/s; sender at 100 msg/s keeps queue growing
               }
            }
         } catch (InterruptedException ignored) {
         } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted()) {
               logger.warn("consumer error: {}", e.getMessage());
            }
         }
      }, "repro-consumer");
      consumerThread.setDaemon(true);
      consumerThread.start();

      AtomicBoolean stop = new AtomicBoolean(false);
      ExecutorService pool = Executors.newCachedThreadPool(r -> {
         Thread t = new Thread(r);
         t.setDaemon(true);
         return t;
      });

      // Management threads: removeMessage() -> deleteReference() -> iterQueue() -> depageLock.
      // deleteReference() holds synchronized(QueueImpl.this), then acquires depageLock inside
      // iterQueue(). This is the opposite order from depage(), causing the ABBA deadlock.
      for (int i = 0; i < 5; i++) {
         pool.submit(() -> {
            while (!stop.get()) {
               try {
                  @SuppressWarnings("unchecked")
                  Map<String, Object>[] msgs = queueControl.listMessages(null);
                  if (msgs != null && msgs.length > 0) {
                     Object id = msgs[0].get("messageID");
                     if (id instanceof Number n) {
                        queueControl.removeMessage(n.longValue());
                     }
                  }
               } catch (Exception ignored) {
               }
            }
         });
      }

      // Probe: isPaused() requires synchronized(QueueImpl.this). If the monitor is deadlocked,
      // this call will block until the @Timeout triggers and fails the test.
      long deadline = System.currentTimeMillis() + 30_000;
      boolean deadlockDetected = false;
      while (System.currentTimeMillis() < deadline) {
         Thread.sleep(1_000);

         AtomicBoolean probeReturned = new AtomicBoolean(false);
         Thread probe = new Thread(() -> {
            try {
               queueControl.isPaused();
               probeReturned.set(true);
            } catch (Exception ignored) {
               probeReturned.set(true);
            }
         }, "deadlock-probe");
         probe.setDaemon(true);
         probe.start();
         probe.join(DEADLOCK_TIMEOUT_MS);

         if (!probeReturned.get()) {
            deadlockDetected = true;
            break;
         }
      }

      stop.set(true);
      pool.shutdownNow();
      senderThread.interrupt();
      consumerThread.interrupt();
      senderThread.join(2_000);
      consumerThread.join(2_000);
      consumeSession.close();
      senderSession.close();
      factory.close();

      if (deadlockDetected) {
         fail("Deadlock detected: isPaused() blocked for >" + DEADLOCK_TIMEOUT_MS +
               "ms — ARTEMIS-6179: depageLock / synchronized(QueueImpl.this) ordering inversion");
      }
   }
}