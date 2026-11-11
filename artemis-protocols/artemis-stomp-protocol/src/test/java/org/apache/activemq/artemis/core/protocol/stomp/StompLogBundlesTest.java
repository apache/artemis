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
package org.apache.activemq.artemis.core.protocol.stomp;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler.LogLevel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class StompLogBundlesTest {

   private static final String LOGGER_NAME = ActiveMQStompProtocolLogger.class.getPackage().getName();
   private static LogLevel origLevel;

   @BeforeAll
   public static void setLogLevel() {
      origLevel = AssertionLoggerHandler.setLevel(LOGGER_NAME, LogLevel.INFO);
   }

   @AfterAll
   public static void restoreLogLevel() throws Exception {
      AssertionLoggerHandler.setLevel(LOGGER_NAME, origLevel);
   }

   @Test
   public void testActiveMQStompProtocolLogger() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         ActiveMQStompProtocolLogger.LOGGER.sentErrorToClient("addressBreadCrumb", "messageBreadCrumb");

         assertTrue(loggerHandler.findText("AMQ332069", "addressBreadCrumb", "messageBreadCrumb"));
      }
   }

   @Test
   public void testActiveMQStompProtocolMessageBundle() throws Exception {
      ActiveMQStompException e = ActiveMQStompProtocolMessageBundle.BUNDLE.destinationNotExist("destinationBreadcrumb");

      String message = e.getMessage();
      assertNotNull(message);
      assertTrue(message.startsWith("AMQ339001"), "unexpected message: " + message);
      assertTrue(message.contains("destinationBreadcrumb"), "unexpected message: " + message);
   }
}
