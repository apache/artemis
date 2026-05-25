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
package org.apache.activemq.artemis.service.extensions;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler.LogLevel;
import org.apache.activemq.artemis.service.extensions.xa.recovery.ActiveMQXARecoveryLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ServiceExtensionLogBundlesTest {

   private static final String EXTENSIONS_LOGGER = ActiveMQServiceExtensionLogger.class.getPackage().getName();
   private static final String RECOVERY_LOGGER = ActiveMQXARecoveryLogger.class.getPackage().getName();
   private static LogLevel origExtensionsLoggerLevel;
   private static LogLevel origRecoveryLoggerLevel;

   @BeforeAll
   public static void setLogLevel() {
      origExtensionsLoggerLevel = AssertionLoggerHandler.setLevel(EXTENSIONS_LOGGER, LogLevel.INFO);
      origRecoveryLoggerLevel = AssertionLoggerHandler.setLevel(RECOVERY_LOGGER, LogLevel.INFO);
   }

   @AfterAll
   public static void restoreLogLevel() throws Exception {
      AssertionLoggerHandler.setLevel(EXTENSIONS_LOGGER, origExtensionsLoggerLevel);
      AssertionLoggerHandler.setLevel(RECOVERY_LOGGER, origRecoveryLoggerLevel);
   }

   @Test
   public void testActiveMQServiceExtensionLogger() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         ActiveMQServiceExtensionLogger.LOGGER.transactionManagerNotFound();

         assertTrue(loggerHandler.findText("AMQ352000"));
      }
   }

   @Test
   public void testActiveMQXARecoveryLogger() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         ActiveMQXARecoveryLogger.LOGGER.noQueueOnTopic("queueBreadCrumb", "addressBreadCrumb");

         assertTrue(loggerHandler.findText("AMQ172007", "queueBreadCrumb", "addressBreadCrumb"));
      }
   }
}
