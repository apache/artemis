/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.core.protocol.mqtt;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MQTTStateManagerTest {

   @Test
   @Timeout(60)
   public void testGetSessionStateNeverReturnsNullUnderConcurrentRemoval() throws Exception {
      final ActiveMQServer server = mock(ActiveMQServer.class);
      final Configuration configuration = mock(Configuration.class);
      when(server.getConfiguration()).thenReturn(configuration);
      when(configuration.isMqttSubscriptionPersistenceEnabled()).thenReturn(false);

      final MQTTStateManager manager = MQTTStateManager.getInstance(server);
      try {
         final String clientId = "link-stealing-client";
         final int iterations = 2_000_000;
         final int removerThreads = 3;

         final AtomicReference<Throwable> failure = new AtomicReference<>();
         final AtomicBoolean stop = new AtomicBoolean(false);
         final CountDownLatch start = new CountDownLatch(1);
         final ExecutorService pool = Executors.newFixedThreadPool(removerThreads + 1);

         for (int i = 0; i < removerThreads; i++) {
            pool.submit(() -> {
               awaitQuietly(start);
               while (!stop.get() && failure.get() == null) {
                  try {
                     manager.removeSessionState(clientId);
                  } catch (Throwable t) {
                     failure.compareAndSet(null, t);
                     return;
                  }
               }
            });
         }

         pool.submit(() -> {
            awaitQuietly(start);
            try {
               for (int i = 0; i < iterations && failure.get() == null; i++) {
                  MQTTSessionState state = manager.getSessionState(clientId);
                  assertNotNull(state, "getSessionState(String) must never return null (ARTEMIS-6085)");
               }
            } catch (Throwable t) {
               failure.compareAndSet(null, t);
            } finally {
               stop.set(true);
            }
         });

         start.countDown();
         pool.shutdown();
         assertTrue(pool.awaitTermination(50, TimeUnit.SECONDS), "concurrency test did not finish in time");

         if (failure.get() != null) {
            fail("getSessionState() returned null / threw under concurrent removal (ARTEMIS-6085)", failure.get());
         }
      } finally {
         MQTTStateManager.removeInstance(server);
      }
   }

   private static void awaitQuietly(CountDownLatch latch) {
      try {
         latch.await();
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }
}
