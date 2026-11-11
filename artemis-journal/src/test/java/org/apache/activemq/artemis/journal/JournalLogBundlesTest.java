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
package org.apache.activemq.artemis.journal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.ActiveMQIOErrorException;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler.LogLevel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class JournalLogBundlesTest {

   private static final String LOGGER_NAME = ActiveMQJournalLogger.class.getPackage().getName();
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
   public void testActiveMQJournalLogger() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         ActiveMQJournalLogger.LOGGER.deletingOrphanedFile("fileNameBreadCrumb");

         assertTrue(loggerHandler.findText("AMQ142019", "fileNameBreadCrumb"));
      }
   }

   @Test
   public void testActiveMQJournalBundle() throws Exception {
      ActiveMQIOErrorException e = ActiveMQJournalBundle.BUNDLE.ioRenameFileError("oldFileNameBreadCrumb", "oldFileNameBreadCrumb");

      String message = e.getMessage();
      assertNotNull(message);
      assertTrue(message.startsWith("AMQ149000"), "unexpected message: " + message);
      assertTrue(message.contains(String.valueOf("oldFileNameBreadCrumb")), "unexpected message: " + message);
      assertTrue(message.contains(String.valueOf("oldFileNameBreadCrumb")), "unexpected message: " + message);
   }
}
