/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.logs.annotation.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler.LogEntry;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler.LogLevel;

public class SimpleBundleTest {

   @Test
   public void testSimple() {
      assertEquals("TST1: Test", SimpleBundle.MESSAGES.simpleTest());
      System.out.println(SimpleBundle.MESSAGES.simpleTest());
   }

   @Test
   public void testParameters() {
      assertEquals("TST2: V1-bb", SimpleBundle.MESSAGES.parameters(1, "bb"));
   }

   @Test
   public void testException() {
      Exception ex = SimpleBundle.MESSAGES.someException();
      assertEquals("TST3: EX", ex.getMessage());
      StringWriter stringWriter = new StringWriter();
      PrintWriter writer = new PrintWriter(stringWriter);
      ex.printStackTrace(writer);
      assertEquals(-1, stringWriter.toString().lastIndexOf("someException"), "The method name (someException) should not be part of the stack trace");
   }

   @Test
   public void testSomeExceptionParameter() {
      String uuid = UUID.randomUUID().toString();
      assertEquals(new Exception("TST4: EX-" + uuid).toString(), SimpleBundle.MESSAGES.someExceptionParameter(uuid).toString());
   }

   @Test
   public void testPrint() throws IOException {
      try (AssertionLoggerHandler logHandler = new AssertionLoggerHandler()) {
         SimpleBundle.MESSAGES.printMessage();

         for (int i = 0; i < 10; i++) {
            SimpleBundle.MESSAGES.printMessage(i);
         }

         assertEquals(11, logHandler.getNumberOfMessages());
         assertEquals(11, logHandler.countText("This is a print!!!"));

         String expectedMessage1 = "TST5: This is a print!!!";
         assertTrue(logHandler.findText(expectedMessage1), "message not found in logs");

         String expectedMessage2prefix = "TST6: This is a print!!! ";
         for (int i = 0; i < 10; i++) {
            assertTrue(logHandler.findText(expectedMessage2prefix + i), "message not found in logs");
         }

         List<LogEntry> entries = logHandler.getLogEntries();

         assertEquals(11, entries.size());
         entries.forEach(entry -> {
            assertEquals(SimpleBundle.class.getName(), entry.getLoggerName(), "logger name not as expected");
         });
      }
   }

   @Test
   public void testMultiLines() {
      SimpleBundle.MESSAGES.multiLines();
   }


   @Test
   public void testWithException() {
      Exception myCause = new Exception("this is myCause");
      String logRandomString = "" + System.currentTimeMillis();
      MyException myException = SimpleBundle.MESSAGES.someExceptionWithCause(logRandomString, myCause);
      assertEquals("TST8: EX" + logRandomString, myException.getMessage());
      assertSame(myCause, myException.getCause());
   }

   @Test
   public void testABCD() {
      System.out.println(SimpleBundle.MESSAGES.abcd("A", "B", "C", "D"));
      assertEquals("TST9: A B C D", SimpleBundle.MESSAGES.abcd("A", "B", "C", "D"));
   }

   @Test
   public void testObjectsABCD() {
      System.out.println(SimpleBundle.MESSAGES.abcd("A", "B", "C", "D"));
      assertEquals("TST10: A B C D", SimpleBundle.MESSAGES.objectsAbcd(new MyObject("A"), new MyObject("B"), new MyObject("C"), new MyObject("D")));
   }


   @Test
   public void exceptions() {
      SimpleBundle.MESSAGES.parameterException("hello", new IOException("this is an exception"));
      SimpleBundle.MESSAGES.myExceptionLogger("hello2", new MyException("this is an exception"));
   }

   @Test
   public void longList() throws Exception {
      try (AssertionLoggerHandler logHandler = new AssertionLoggerHandler()) {
         SimpleBundle.MESSAGES.longParameters("1", "2", "3", "4", "5");
         assertTrue(logHandler.findText("p1"), "parameter not found");
         assertTrue(logHandler.findText("p2"), "parameter not found");
         assertTrue(logHandler.findText("p3"), "parameter not found");
         assertTrue(logHandler.findText("p4"), "parameter not found");
         assertTrue(logHandler.findText("p5"), "parameter not found");
         assertFalse(logHandler.findText("{}"), "{}");
      }
   }


   @Test
   public void onlyException() throws Exception {
      try (AssertionLoggerHandler logHandler = new AssertionLoggerHandler()) {
         SimpleBundle.MESSAGES.onlyException(createMyExceptionBreadcrumbMethod("MSG7777"));

         assertTrue(logHandler.findText("TST14"));
         assertFalse(logHandler.findText("MSG7777"));
      }

      try (AssertionLoggerHandler logHandler = new AssertionLoggerHandler(true)) {
         SimpleBundle.MESSAGES.onlyException(createMyExceptionBreadcrumbMethod("MSG7777"));
         assertTrue(logHandler.findText("TST14"));
         assertTrue(logHandler.findTrace("MSG7777"));
         assertTrue(logHandler.findTrace("createMyExceptionBreadcrumbMethod"));
      }
   }


   // I'm doing it on a method just to assert if this method will appear on the stack trace
   private static MyException createMyExceptionBreadcrumbMethod(String message) {
      return new MyException(message);
   }


   @Test
   public void testGetLogger() {
      Logger logger = SimpleBundle.MESSAGES.getLogger();
      assertNotNull(logger);
      assertEquals(SimpleBundle.class.getName(), logger.getName());
   }

   @Test
   public void testLoggerNameOverrideWithParameters() throws Exception {
      try (AssertionLoggerHandler logHandler = new AssertionLoggerHandler()) {
         SimpleBundle.MESSAGES.overrideLoggerNameWithParameter("BREADCRUMB");

         final String expectedMessage = "TST15: Logger name overridden to add .OVERRIDE.PARAMS suffix. BREADCRUMB";

         assertTrue(logHandler.findText(expectedMessage), "message not found in logs");
         assertEquals(1, logHandler.getNumberOfMessages());

         List<LogEntry> entries = logHandler.getLogEntries();
         assertEquals(1, entries.size());

         LogEntry entry = entries.get(0);
         assertEquals(expectedMessage, entry.getMessage(), "message not as expected");
         assertEquals(LogLevel.WARN, entry.getLogLevel(), "level not as expected");
         assertEquals(SimpleBundle.LOGGER_NAME_OVERRIDE_PARAMS, entry.getLoggerName(), "logger name not as expected");
      }
   }

   @Test
   public void testLoggerNameOverrideWithoutParameters() throws Exception {
      try (AssertionLoggerHandler logHandler = new AssertionLoggerHandler()) {
         SimpleBundle.MESSAGES.overrideLoggerNameWithoutParameter();

         final String expectedMessage = "TST16: Logger name overridden to add .OVERRIDE.NO_PARAMS suffix.";

         assertTrue(logHandler.findText(expectedMessage), "message not found in logs");
         assertEquals(1, logHandler.getNumberOfMessages());

         List<LogEntry> entries = logHandler.getLogEntries();
         assertEquals(1, entries.size());

         LogEntry entry = entries.get(0);
         assertEquals(expectedMessage, entry.getMessage(), "message not as expected");
         assertEquals(LogLevel.WARN, entry.getLogLevel(), "level not as expected");
         assertEquals(SimpleBundle.LOGGER_NAME_OVERRIDE_NO_PARAMS, entry.getLoggerName(), "logger name not as expected");
      }
   }
}
