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

import java.util.UUID;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class PotentialOOMELoggingTest extends ActiveMQTestBase {

   /**
    * When running this test from an IDE add this to the test command line so that the {@link AssertionLoggerHandler} works
    * properly:
    * <pre>
    * -Dlog4j2.configurationFile=file:${path-to-source}/tests/config/log4j2-tests-config.properties
    * </pre>
    */
   @Test
   public void testBlockLogging() throws Exception {
      ActiveMQServer server = createServer(false, createDefaultInVMConfig());
      for (int i = 0; i < 200; i++) {
         server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(UUID.randomUUID().toString()));
      }
      server.getConfiguration().setGlobalMaxSize(-1);
      server.getConfiguration().getAddressSettings().put("#", new AddressSettings().setMaxSizeBytes(10485760 * 10));

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         server.start();

         // Using the code only so the test doesn't fail just because someone edits the log text
         assertTrue(loggerHandler.findText("AMQ222205"), "Expected to find 222205");
      }
   }
}
