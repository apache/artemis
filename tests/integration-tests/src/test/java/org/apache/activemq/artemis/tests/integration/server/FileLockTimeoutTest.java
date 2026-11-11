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
package org.apache.activemq.artemis.tests.integration.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileLockTimeoutTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected void doTest(final boolean useAIO) throws Exception {
      if (useAIO) {
         assertTrue(LibaioContext.isLoaded(), String.format("libAIO is not loaded on %s %s %s", System.getProperty("os.name"), System.getProperty("os.arch"), System.getProperty("os.version")));
      }
      Configuration config = super.createDefaultInVMConfig().setHAPolicyConfiguration(new SharedStorePrimaryPolicyConfiguration()).clearAcceptorConfigurations();

      ActiveMQServer server1 = createServer(true, config);
      if (useAIO) {
         server1.getConfiguration().setJournalType(JournalType.ASYNCIO);
      } else {
         server1.getConfiguration().setJournalType(JournalType.NIO);
      }
      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      final ActiveMQServer server2 = createServer(true, config);
      if (useAIO) {
         server2.getConfiguration().setJournalType(JournalType.ASYNCIO);
      } else {
         server2.getConfiguration().setJournalType(JournalType.NIO);
      }
      server2.getConfiguration().setJournalLockAcquisitionTimeout(5000);

      // if something happens that causes the timeout to misbehave we don't want the test to hang
      ExecutorService service = Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      Runnable r = () -> {
         try {
            server2.start();
         } catch (final Exception e) {
            throw new RuntimeException(e);
         }
      };

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         Future<?> f = service.submit(r);

         try {
            f.get(15, TimeUnit.SECONDS);
         } catch (Exception e) {
            logger.warn("aborting test because server is taking too long to start");
         }

         service.shutdown();

         assertTrue(loggerHandler.findText("AMQ224000"), "Expected to find AMQ224000");
         assertTrue(loggerHandler.findTrace("Timed out waiting for lock"), "Expected to find \"Timed out waiting for lock\"");
      }
   }
}
