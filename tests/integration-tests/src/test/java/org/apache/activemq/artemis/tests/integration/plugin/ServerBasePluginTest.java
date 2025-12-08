/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.tests.integration.plugin;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBasePlugin;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerBasePluginTest extends ActiveMQTestBase {

   private final MethodCalledVerifier verifier = new MethodCalledVerifier();

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }

   @Test
   public void testBasePluginLifecycleNotification() throws Exception {
      AtomicInteger count = new AtomicInteger(0);

      ActiveMQServer server = createServer(false, createDefaultInVMConfig());
      server.getConfiguration().registerBrokerPlugin(verifier);

      server.getConfiguration().registerBrokerPlugin(new ActiveMQServerBasePlugin() {
         @Override
         public void afterStarted(ActiveMQServer server) {
            assertTrue(server.isStarted());
            count.getAndIncrement();
         }

         @Override
         public void beforeStopped(ActiveMQServer server) {
            assertTrue(server.isStarted());
            count.getAndIncrement();
         }
      });

      verifier.validatePluginMethodsEquals(0, MethodCalledVerifier.AFTER_STARTED, MethodCalledVerifier.BEFORE_STOPPED);
      server.start();
      verifier.validatePluginMethodsEquals(1, 1000L, 10L, MethodCalledVerifier.AFTER_STARTED);
      verifier.validatePluginMethodsEquals(0, 1000L, 10L, MethodCalledVerifier.BEFORE_STOPPED);
      server.stop();
      verifier.validatePluginMethodsEquals(1, 1000L, 10L, MethodCalledVerifier.AFTER_STARTED, MethodCalledVerifier.BEFORE_STOPPED);
      assertEquals(2, count.get());

   }

}
