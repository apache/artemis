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
package org.apache.activemq.artemis.tests.unit.core.remoting.impl.invm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnection;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.BaseConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

public class InVMConnectionTest {

   @Test
   public void testIsTargetNode() throws Exception {

      int serverID = 0;
      InVMConnection conn = new InVMConnection(serverID, null, null, null);

      Map<String, Object> config0 = new HashMap<>();
      config0.put(TransportConstants.SERVER_ID_PROP_NAME, 0);
      TransportConfiguration tf0 = new TransportConfiguration(InVMConnectorFactory.class.getName(), config0, "tf0");

      Map<String, Object> config1 = new HashMap<>();
      config1.put(TransportConstants.SERVER_ID_PROP_NAME, 1);
      TransportConfiguration tf1 = new TransportConfiguration(InVMConnectorFactory.class.getName(), config1, "tf1");

      Map<String, Object> config2 = new HashMap<>();
      config2.put(TransportConstants.SERVER_ID_PROP_NAME, 2);
      TransportConfiguration tf2 = new TransportConfiguration(InVMConnectorFactory.class.getName(), config2, "tf2");

      assertTrue(conn.isSameTarget(tf0));
      assertFalse(conn.isSameTarget(tf1));
      assertFalse(conn.isSameTarget(tf2));
      assertTrue(conn.isSameTarget(tf0, tf1));
      assertTrue(conn.isSameTarget(tf2, tf0));
      assertFalse(conn.isSameTarget(tf2, tf1));
   }

   @Test
   public void testConcurrentCloseFiresConnectionDestroyedExactlyOnce() throws Exception {
      final int threads = 16;
      // Repeat several rounds to widen the window for catching the race.
      for (int round = 0; round < 50; round++) {
         final CountingLifeCycleListener listener = new CountingLifeCycleListener();
         final InVMConnection conn = new InVMConnection(0, null, listener, null);

         final CyclicBarrier barrier = new CyclicBarrier(threads);
         final Thread[] workers = new Thread[threads];
         final AtomicInteger prematureReturns = new AtomicInteger();

         for (int i = 0; i < threads; i++) {
            final boolean disconnect = (i % 2 == 0);
            workers[i] = new Thread(() -> {
               try {
                  // Line up all threads so they hit close()/disconnect() together.
                  barrier.await();
               } catch (Exception e) {
                  throw new RuntimeException(e);
               }
               if (disconnect) {
                  conn.disconnect();
               } else {
                  conn.close();
               }
               // by the time any close()/disconnect() call returns, connectionDestroyed must already have fired.
               if (!listener.destroyFired) {
                  prematureReturns.incrementAndGet();
               }
            });
         }

         for (Thread worker : workers) {
            worker.start();
         }
         for (Thread worker : workers) {
            worker.join();
         }

         assertEquals(1, listener.destroyedCount.get(),
                      "connectionDestroyed must be fired exactly once per connection (round " + round + ")");
         assertEquals(0, prematureReturns.get(),
                      "close()/disconnect() must not return before connectionDestroyed has fired (round " + round + ")");
      }
   }

   private static final class CountingLifeCycleListener implements BaseConnectionLifeCycleListener<ProtocolManager> {

      private final AtomicInteger destroyedCount = new AtomicInteger();

      private volatile boolean destroyFired;

      @Override
      public void connectionCreated(ActiveMQComponent component, Connection connection, ProtocolManager protocol) {
      }

      @Override
      public void connectionDestroyed(Object connectionID, boolean failed) {
         // Hold inside the callback for a moment to widen the window in which a
         // concurrent, non-atomic close() could wrongly observe the connection
         // as "closing" and return early before this callback completes.
         try {
            Thread.sleep(5);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         }
         destroyedCount.incrementAndGet();
         destroyFired = true;
      }

      @Override
      public void connectionException(Object connectionID, ActiveMQException me) {
      }

      @Override
      public void connectionReadyForWrites(Object connectionID, boolean ready) {
      }
   }
}
