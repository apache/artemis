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
package org.apache.activemq.artemis.tests.integration.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.NetworkHealthCheck;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler.LogLevel;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class NetworkHealthCheckTest extends ActiveMQTestBase {

   private static final String HEALTH_CHECK_LOGGER_NAME = NetworkHealthCheck.class.getName();
   private static LogLevel previousLevel;

   @BeforeAll
   public static void prepareLogger() {
      previousLevel = AssertionLoggerHandler.setLevel(HEALTH_CHECK_LOGGER_NAME, LogLevel.DEBUG);
   }

   @AfterAll
   public static void clearLogger() {
      AssertionLoggerHandler.setLevel(HEALTH_CHECK_LOGGER_NAME, previousLevel);
   }


   @Test
   public void testCustomIpv4Command() throws Exception {
      final int checkingTimeout = 1;
      final String checkingHost = "10.0.0.1";
      final String customIpv4Command = "DUMMYPING %d %s";


      Configuration config = createBasicConfig()
         .setNetworkCheckTimeout(checkingTimeout)
         .setNetworkCheckList(checkingHost)
         .setNetworkCheckPingCommand(customIpv4Command);

      ActiveMQServer server = createServer(false, config);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         server.start();
         assertTrue(loggerHandler.findText("executing ping:: " + String.format(customIpv4Command, checkingTimeout, checkingHost)));
         assertFalse(loggerHandler.findText(String.format(NetworkHealthCheck.IPV4_DEFAULT_COMMAND, checkingTimeout, checkingHost)));
      } finally {
         server.stop();
      }
   }
}
