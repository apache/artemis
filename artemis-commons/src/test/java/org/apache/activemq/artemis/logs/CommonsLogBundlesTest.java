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
package org.apache.activemq.artemis.logs;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler.LogLevel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CommonsLogBundlesTest {

   private static final String AUDIT_LOGGERS = "org.apache.activemq.audit";
   private static final String UTIL_LOGGER = ActiveMQUtilLogger.class.getPackage().getName();
   private static LogLevel origAuditLoggersLevel;
   private static LogLevel origUtilLoggersLevel;

   @BeforeAll
   public static void setLogLevel() {
      origAuditLoggersLevel = AssertionLoggerHandler.setLevel(AUDIT_LOGGERS, LogLevel.INFO);
      origUtilLoggersLevel = AssertionLoggerHandler.setLevel(UTIL_LOGGER, LogLevel.INFO);
   }

   @AfterAll
   public static void restoreLogLevel() throws Exception {
      AssertionLoggerHandler.setLevel(AUDIT_LOGGERS, origAuditLoggersLevel);
      AssertionLoggerHandler.setLevel(UTIL_LOGGER, origUtilLoggersLevel);
   }

   @Test
   public void testActiveMQUtilLogger() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         ActiveMQUtilLogger.LOGGER.addressWasntReacheable("addressBreadCrumb");

         assertTrue(loggerHandler.findText("AMQ202002", "addressBreadCrumb"));
      }
   }

   @Test
   public void testActiveMQUtilBundle() throws Exception {
      ActiveMQIllegalStateException e = ActiveMQUtilBundle.BUNDLE.invalidProperty("breadcrumb");

      String message = e.getMessage();
      assertNotNull(message);
      assertTrue(message.startsWith("AMQ209000"), "unexpected message: " + message);
      assertTrue(message.contains("breadcrumb"), "unexpected message: " + message);
   }

   @Test
   public void testAuditLoggers() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         AuditLogger.BASE_LOGGER.getRoutingTypes("userBreadCrumb", "resourceBreadCrumb");

         assertTrue(loggerHandler.findText("AMQ601000", "userBreadCrumb", "resourceBreadCrumb"));
      }

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         AuditLogger.RESOURCE_LOGGER.createAddressSuccess("userBreadCrumb", "nameBreadCrumb", "routingBreadCrumb");

         assertTrue(loggerHandler.findText("AMQ601701", "userBreadCrumb", "nameBreadCrumb", "routingBreadCrumb"));
      }

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         AuditLogger.MESSAGE_LOGGER.coreSendMessage("userBreadCrumb", "messageBreadCrumb", "contextBreadCrumb", "txBreadCrumb");

         assertTrue(loggerHandler.findText("AMQ601500", "userBreadCrumb", "messageBreadCrumb", "contextBreadCrumb", "txBreadCrumb"));
      }
   }
}
