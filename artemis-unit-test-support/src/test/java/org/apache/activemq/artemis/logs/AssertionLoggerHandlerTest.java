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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.logs.AssertionLoggerHandler.LogLevel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssertionLoggerHandlerTest {

   private static final String LOGGER_NAME = MethodHandles.lookup().lookupClass().getName();
   private static final Logger logger = LoggerFactory.getLogger(LOGGER_NAME);

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
   public void testFindText() throws Exception {
      final String prefix = "123prefix";
      final String middle = "middle456";
      final String suffix = "suffix789";

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         // Try without logging anything
         assertFalse(loggerHandler.findText(prefix), "should not have found prefix, not yet logged");
         assertFalse(loggerHandler.findText(middle), "should not have found middle, not yet logged");
         assertFalse(loggerHandler.findText(suffix), "should not have found suffix, not yet logged");
         assertFalse(loggerHandler.findText(prefix, middle, suffix), "should not have found combination, not yet logged");

         // Now log only the prefix.
         logger.info("{} -after", prefix);

         assertTrue(loggerHandler.findText(prefix), "should have found prefix logged");
         assertFalse(loggerHandler.findText(middle), "should not have found middle, not yet logged");
         assertFalse(loggerHandler.findText(suffix), "should not have found suffix, not yet logged");
         assertFalse(loggerHandler.findText(prefix, middle, suffix), "should not have found combination, not yet logged");

         // Now log only the middle.
         logger.info("before- {} -after", middle);

         assertTrue(loggerHandler.findText(prefix), "should have found prefix logged");
         assertTrue(loggerHandler.findText(middle), "should have found middle logged");
         assertFalse(loggerHandler.findText(suffix), "should not have found suffix, not yet logged");
         assertFalse(loggerHandler.findText(prefix, middle, suffix), "should not have found full combination, not yet logged");

         // Now log only the suffix.
         logger.info("before- {}", suffix);

         assertTrue(loggerHandler.findText(prefix), "should have found prefix logged");
         assertTrue(loggerHandler.findText(middle), "should have found middle logged");
         assertTrue(loggerHandler.findText(suffix), "should have found suffix logged");
         assertFalse(loggerHandler.findText(prefix, suffix), "should not have found alternative combination, not yet logged");
         assertFalse(loggerHandler.findText(prefix, middle), "should not have found alternative combination, not yet logged");
         assertFalse(loggerHandler.findText(middle, suffix), "should not have found alternative combination, not yet logged");
         assertFalse(loggerHandler.findText(prefix, middle, suffix), "should not have found full combination, not yet logged");
      }

      // Use a new AssertionLoggerHandler to start fresh
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         // Try again without logging anything
         assertFalse(loggerHandler.findText(prefix), "should not have found prefix, not yet logged");
         assertFalse(loggerHandler.findText(middle), "should not have found middle, not yet logged");
         assertFalse(loggerHandler.findText(suffix), "should not have found suffix, not yet logged");
         assertFalse(loggerHandler.findText(prefix, middle, suffix), "should not have found full combination, not yet logged");

         // Now log the prefix AND suffix, but NOT middle.
         logger.info("{} -inbetween- {}", prefix, suffix);

         assertTrue(loggerHandler.findText(prefix), "should have found prefix logged");
         assertFalse(loggerHandler.findText(middle), "should not have found middle, not yet logged");
         assertTrue(loggerHandler.findText(suffix), "should have found suffix logged");
         assertTrue(loggerHandler.findText(prefix, suffix), "should have found combination logged");
         assertFalse(loggerHandler.findText(prefix, middle), "should not have found alternative combination, not yet logged");
         assertFalse(loggerHandler.findText(middle, suffix), "should not have found alternative combination, not yet logged");
         assertFalse(loggerHandler.findText(prefix, middle, suffix), "should not have found full combination, not yet logged");
      }

      // Use a new AssertionLoggerHandler to start fresh
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         // Try again without logging anything
         assertFalse(loggerHandler.findText(prefix), "should not have found prefix, not yet logged");
         assertFalse(loggerHandler.findText(middle), "should not have found middle, not yet logged");
         assertFalse(loggerHandler.findText(suffix), "should not have found suffix, not yet logged");
         assertFalse(loggerHandler.findText(prefix, middle, suffix), "should not have found combination, not yet logged");

         // Now log the prefix AND middle AND suffix.
         logger.info("{} - {} - {}", prefix, middle, suffix);

         assertTrue(loggerHandler.findText(prefix), "should have found prefix logged");
         assertTrue(loggerHandler.findText(middle), "should have found middle logged");
         assertTrue(loggerHandler.findText(suffix), "should have found suffix logged");
         assertTrue(loggerHandler.findText(prefix, suffix), "should have found combination logged");
         assertTrue(loggerHandler.findText(prefix, middle), "should have found combination logged");
         assertTrue(loggerHandler.findText(middle, suffix), "should have found combination logged");
         assertTrue(loggerHandler.findText(prefix, middle, suffix), "should have found full combination logged");
      }
   }

}
