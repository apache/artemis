/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.critical;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzerPolicy;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.critical.CriticalCloseable;
import org.apache.activemq.artemis.utils.critical.CriticalComponent;
import org.junit.jupiter.api.Test;

public class CriticalSimpleTest extends ActiveMQTestBase {
   @Test
   public void testSimpleShutdown() throws Exception {

      Configuration configuration = createDefaultConfig(false);
      configuration.setCriticalAnalyzerCheckPeriod(10).setCriticalAnalyzerPolicy(CriticalAnalyzerPolicy.SHUTDOWN);
      ActiveMQServer server = createServer(false, configuration, AddressSettings.DEFAULT_PAGE_SIZE, AddressSettings.DEFAULT_MAX_SIZE_BYTES);
      server.start();

      try {

         CountDownLatch latch = new CountDownLatch(1);

         server.getConfiguration().registerBrokerPlugin(new ActiveMQServerPlugin() {
            @Override
            public void criticalFailure(CriticalComponent components) throws ActiveMQException {
               latch.countDown();
            }
         });

         server.getCriticalAnalyzer().add(new CriticalComponent() {

            @Override
            public CriticalAnalyzer getCriticalAnalyzer() {
               return null;
            }

            @Override
            public CriticalCloseable measureCritical(int path) {
               return null;
            }

            @Override
            public boolean checkExpiration(long timeout, boolean reset) {
               return true;
            }
         });

         assertTrue(latch.await(10, TimeUnit.SECONDS));
         Wait.waitFor(() -> !server.isStarted());


         assertFalse(server.isStarted());

      } finally {
         server.stop();
      }


   }

   @Test
   public void testCriticalOff() throws Exception {

      Configuration configuration = createDefaultConfig(false);
      configuration.setCriticalAnalyzerCheckPeriod(10).setCriticalAnalyzer(false);
      ActiveMQServer server = createServer(false, configuration, AddressSettings.DEFAULT_PAGE_SIZE, AddressSettings.DEFAULT_MAX_SIZE_BYTES);
      server.start();

      try {
         server.getCriticalAnalyzer().add(new CriticalComponent() {

            @Override
            public CriticalAnalyzer getCriticalAnalyzer() {
               return null;
            }

            @Override
            public CriticalCloseable measureCritical(int path) {
               return null;
            }

            @Override
            public boolean checkExpiration(long timeout, boolean reset) {
               return true;
            }
         });

         Wait.waitFor(() -> !server.isStarted(), 500, 10);


         assertTrue(server.isStarted());

      } finally {
         server.stop();
      }


   }

}
