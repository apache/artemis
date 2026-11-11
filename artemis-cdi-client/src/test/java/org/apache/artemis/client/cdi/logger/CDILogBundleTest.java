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
package org.apache.artemis.client.cdi.logger;

import static org.junit.Assert.assertTrue;

import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler.LogLevel;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class CDILogBundleTest {

   private static final String LOGGER_NAME = ActiveMQCDILogger.class.getPackage().getName();
   private static LogLevel origLevel;

   @BeforeClass
   public static void setLogLevel() {
      origLevel = AssertionLoggerHandler.setLevel(LOGGER_NAME, LogLevel.DEBUG);
   }

   @AfterClass
   public static void restoreLogLevel() throws Exception {
      AssertionLoggerHandler.setLevel(LOGGER_NAME, origLevel);
   }

   @Test
   public void testActiveMQCDILogger() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(false)) {
         ActiveMQCDILogger.LOGGER.notUsingDefaultConfiguration();

         assertTrue(loggerHandler.findText("AMQ573000", "not using built in configuration"));
      }
   }

}
