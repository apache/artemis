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
package org.apache.activemq.artemis.core.server.impl;

import java.lang.ref.WeakReference;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ActiveMQServerImplTest extends ServerTestBase {

   @Test
   public void testAddingAndStartingExternalComponent() throws Exception {
      ActiveMQServer server = createServer(false);
      server.start();
      EmbeddedServerTest.FakeExternalComponent component = new EmbeddedServerTest.FakeExternalComponent();
      server.addExternalComponent(component, true);
      assertTrue(component.isStarted());
      assertTrue(server.getExternalComponents().contains(component), server.getExternalComponents() + " does not contain " + component);
   }

   @Test
   public void testAddingWithoutStartingExternalComponent() throws Exception {
      ActiveMQServer server = createServer(false);
      server.start();
      EmbeddedServerTest.FakeExternalComponent component = new EmbeddedServerTest.FakeExternalComponent();
      server.addExternalComponent(component, false);
      assertFalse(component.isStarted());
      assertTrue(server.getExternalComponents().contains(component), server.getExternalComponents() + " does not contain " + component);
   }

   @Test
   public void testCannotAddExternalComponentsIfNotStarting() throws Exception {
      ActiveMQServer server = createServer(false);
      EmbeddedServerTest.FakeExternalComponent component = new EmbeddedServerTest.FakeExternalComponent();
      try {
         server.addExternalComponent(component, false);
         fail();
      } catch (IllegalStateException ex) {
         assertFalse(component.isStarted());
         assertEquals(0, server.getExternalComponents().size());
      }
   }

   @Test
   public void testCannotAddExternalComponentsIfStopped() throws Exception {
      ActiveMQServer server = createServer(false);
      server.start();
      server.stop();
      EmbeddedServerTest.FakeExternalComponent component = new EmbeddedServerTest.FakeExternalComponent();
      try {
         server.addExternalComponent(component, false);
         fail();
      } catch (IllegalStateException ex) {
         assertFalse(component.isStarted());
         assertEquals(0, server.getExternalComponents().size());
      }
   }

   @Test
   public void testScheduledPoolGC() throws Exception {
      ActiveMQServer server = createServer(false);

      server.start();

      // if this is converted to a lambda or method references the test will fail
      Runnable scheduledRunnable = new Runnable() {
         @Override
         public void run() {
            fail();
         }
      };
      WeakReference<Runnable> scheduledRunnableRef = new WeakReference<>(scheduledRunnable);

      ScheduledExecutorService scheduledPool = server.getScheduledPool();
      ScheduledFuture scheduledFuture = scheduledPool.schedule(scheduledRunnable, 5000, TimeUnit.MILLISECONDS);

      assertFalse(scheduledFuture.isCancelled());
      assertTrue(scheduledFuture.cancel(true));
      assertTrue(scheduledFuture.isCancelled());

      assertNotNull(scheduledRunnableRef.get());

      scheduledRunnable = null;

      forceGC();

      assertNull(scheduledRunnableRef.get());

      server.stop();
   }

}
