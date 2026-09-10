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
package org.apache.activemq.artemis.core.cluster;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.BroadcastEndpoint;
import org.apache.activemq.artemis.api.core.BroadcastEndpointFactory;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@code stop(0L)} returns immediately without joining the discovery thread,
 * while the no-arg {@code stop()} still delegates to the configured {@code stoppingTimeout}.
 */
public class DiscoveryGroupStopTimeoutTest {

   // Fake endpoint blocks 8s after close() — longer than the 3s pass threshold,
   // shorter than the 30s stoppingTimeout, so a blocked join is clearly visible.
   private static final long SLOW_ENDPOINT_BLOCK_MS = 8_000L;
   private static final long LARGE_STOPPING_TIMEOUT = 30_000L;
   private static final long FAST_STOP_THRESHOLD_MS  =  3_000L;

   private DiscoveryGroup dg;

   @AfterEach
   public void tearDown() {
      if (dg != null && dg.isStarted()) {
         dg.stop(0L); // use 0 to avoid blocking teardown
      }
   }

   /** stop(0L) must return without waiting for the thread join, even with a slow endpoint. */
   @Test
   public void testStopWithZeroTimeoutDoesNotBlock() throws Exception {
      dg = new DiscoveryGroup(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(),
                              500L, LARGE_STOPPING_TIMEOUT,
                              new SlowEndpointFactory(SLOW_ENDPOINT_BLOCK_MS),
                              null);
      dg.start();
      assertTrue(dg.isStarted());

      long before = System.currentTimeMillis();
      dg.stop(0L);
      long elapsed = System.currentTimeMillis() - before;

      assertFalse(dg.isStarted(), "DiscoveryGroup should be stopped after stop(0L)");
      assertTrue(elapsed < FAST_STOP_THRESHOLD_MS,
                 "stop(0L) blocked for " + elapsed + " ms — expected immediate return (< " + FAST_STOP_THRESHOLD_MS + " ms).");
   }

   /** stop() with stoppingTimeout=0 must also return immediately — no hang, no NPE. */
   @Test
   public void testNoArgStopDelegatesToStoppingTimeout() throws Exception {
      dg = new DiscoveryGroup(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(),
                              500L, 0L,
                              new SlowEndpointFactory(SLOW_ENDPOINT_BLOCK_MS),
                              null);
      dg.start();
      assertTrue(dg.isStarted());

      long before = System.currentTimeMillis();
      dg.stop(); // stoppingTimeout=0 → stop(0L) → must not hang
      long elapsed = System.currentTimeMillis() - before;

      assertFalse(dg.isStarted(), "DiscoveryGroup should be stopped after stop()");
      assertTrue(elapsed < FAST_STOP_THRESHOLD_MS,
                 "stop() with stoppingTimeout=0 blocked for " + elapsed + " ms — expected immediate return");
   }

   // Fake endpoint: blocks until close(), then sleeps SLOW_ENDPOINT_BLOCK_MS ignoring interrupts,
   // simulating a thread that is slow to exit so that thread.join() would actually block.

   private static final class SlowEndpointFactory implements BroadcastEndpointFactory {

      private final long blockMs;

      SlowEndpointFactory(long blockMs) {
         this.blockMs = blockMs;
      }

      @Override
      public BroadcastEndpoint createBroadcastEndpoint() {
         return new SlowBroadcastEndpoint(blockMs);
      }
   }

   private static final class SlowBroadcastEndpoint implements BroadcastEndpoint {

      private final long blockMs;
      private volatile boolean closed = false;

      SlowBroadcastEndpoint(long blockMs) {
         this.blockMs = blockMs;
      }

      @Override
      public void openClient() {
         closed = false;
      }

      @Override
      public void openBroadcaster() {
      }

      @Override
      public void close(boolean isBroadcaster) {
         closed = true;
      }

      // InterruptedException is deliberately swallowed so the thread stays alive
      // for blockMs after close() — forcing thread.join() to actually wait.
      @Override
      public byte[] receiveBroadcast() throws Exception {
         // wait until close() is called
         while (!closed) {
            try {
               Thread.sleep(10);
            } catch (InterruptedException ignored) {
               // swallow — the thread won't exit until close() + blockMs have elapsed
            }
         }
         // simulate slow teardown after close
         try {
            Thread.sleep(blockMs);
         } catch (InterruptedException ignored) {
            // swallow — stays blocked
         }
         return null;
      }

      @Override
      public byte[] receiveBroadcast(long time, TimeUnit unit) throws Exception {
         return receiveBroadcast();
      }

      @Override
      public void broadcast(byte[] data) {
      }
   }
}
