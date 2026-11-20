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
package org.apache.activemq.artemis.logs.log4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter.Result;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.properties.PropertiesConfigurationFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ArtemisLogCodeFilterTest {

   private static final String LOGGER_NAME = "org.apache.activemq.artemis.core.server";

   private static final String ANNOUNCEMENT_TEXT = "Log entries with";


   private static ArtemisLogCodeFilter filter(String codes) {
      return ArtemisLogCodeFilter.newBuilder().setCodes(codes).build();
   }

   private static LogEvent event(String format, Object... params) {
      return Log4jLogEvent.newBuilder()
         .setLoggerName(LOGGER_NAME)
         .setLevel(Level.WARN)
         .setMessage(new ParameterizedMessage(format, params))
         .build();
   }

   @Test
   public void testMatchingCodeIsDenied() {
      ArtemisLogCodeFilter filter = filter("222061");
      assertEquals(Result.DENY, filter.filter(event("AMQ222061: Client is not being consistent on the use of {}", "x")));
   }

   @Test
   public void testNonMatchingCodeIsNeutral() {
      ArtemisLogCodeFilter filter = filter("222061");
      assertEquals(Result.NEUTRAL, filter.filter(event("AMQ222062: Something else happened")));
   }

   @Test
   public void testAllConfiguredCodesAreDenied() {
      ArtemisLogCodeFilter filter = filter("224000,221007,222061");
      assertEquals(Result.DENY, filter.filter(event("AMQ222061: first")));
      assertEquals(Result.DENY, filter.filter(event("AMQ221007: second")));
      assertEquals(Result.DENY, filter.filter(event("AMQ224000: third")));
      assertEquals(Result.NEUTRAL, filter.filter(event("AMQ221008: untouched")));
   }

   @Test
   public void testWhitespaceAroundCodesIsTolerated() {
      ArtemisLogCodeFilter filter = filter("  222061 ,\t224000  ");
      assertEquals(Result.DENY, filter.filter(event("AMQ222061: first")));
      assertEquals(Result.DENY, filter.filter(event("AMQ224000: second")));
   }

   @Test
   public void testMultiLineMessageIsDenied() {
      // the regex equivalent of this filter silently fails to match these unless (?s) is set
      ArtemisLogCodeFilter filter = filter("224000");
      assertEquals(Result.DENY, filter.filter(event("AMQ224000: first line\nsecond line {}", "x")));
   }

   @Test
   public void testLongerCodeWithSharedPrefixIsNotDenied() {
      ArtemisLogCodeFilter filter = filter("2220");
      assertEquals(Result.NEUTRAL, filter.filter(event("AMQ222061: not the configured code")));
   }

   @Test
   public void testCustomResultsAreHonoured() {
      ArtemisLogCodeFilter filter = ArtemisLogCodeFilter.newBuilder()
         .setCodes("222061")
         .setOnMatch(Result.ACCEPT)
         .setOnMismatch(Result.DENY)
         .build();
      assertEquals(Result.ACCEPT, filter.filter(event("AMQ222061: matched")));
      assertEquals(Result.DENY, filter.filter(event("AMQ222062: mismatched")));
   }

   @Test
   public void testCodeSuppliedAsParameterIsNotMatched() {
      // the log site here has no code of its own; the code only appears after substitution, so it is not this entry's
      // contract code and must not be suppressed on its behalf
      ArtemisLogCodeFilter filter = filter("222061");
      assertEquals(Result.NEUTRAL, filter.filter(event("{}", "AMQ222061: wrapped exception message")));
   }

   @Test
   public void testCodeElsewhereInMessageIsNotMatched() {
      ArtemisLogCodeFilter filter = filter("222061");
      assertEquals(Result.NEUTRAL, filter.filter(event("AMQ222062: see also AMQ222061: for details")));
   }

   @Test
   public void testMessageWithoutProjectCodeIsNeutral() {
      ArtemisLogCodeFilter filter = filter("222061");
      assertEquals(Result.NEUTRAL, filter.filter(event("222061: no AMQ prefix")));
      assertEquals(Result.NEUTRAL, filter.filter(event("Some entirely uncoded message")));
      assertEquals(Result.NEUTRAL, filter.filter(event("")));
   }

   @Test
   public void testMessageWithoutColonTerminatorIsNeutral() {
      ArtemisLogCodeFilter filter = filter("222061");
      assertEquals(Result.NEUTRAL, filter.filter(event("AMQ222061")));
      assertEquals(Result.NEUTRAL, filter.filter(event("AMQ222061 no colon here")));
   }

   @Test
   public void testMalformedCodeTokenIsNeutral() {
      ArtemisLogCodeFilter filter = filter("222061");
      assertEquals(Result.NEUTRAL, filter.filter(event("AMQ: empty code")));
      assertEquals(Result.NEUTRAL, filter.filter(event("AMQ22x061: non-numeric code")));
      assertEquals(Result.NEUTRAL, filter.filter(event("AMQ1234567890123: implausibly long code")));
   }

   @Test
   public void testNullsAreNeutral() {
      ArtemisLogCodeFilter filter = filter("222061");
      assertEquals(Result.NEUTRAL, filter.filter((LogEvent) null));
      assertEquals(Result.NEUTRAL, filter.filter(null, Level.WARN, null, (Object) null, (Throwable) null));
      assertEquals(Result.NEUTRAL, filter.filter(null, Level.WARN, null, (String) null, new Object[0]));
   }

   @Test
   public void testStringOverload() {
      ArtemisLogCodeFilter filter = filter("222061");
      assertEquals(Result.DENY, filter.filter(null, Level.WARN, null, "AMQ222061: msg {}", "x"));
      assertEquals(Result.NEUTRAL, filter.filter(null, Level.WARN, null, "AMQ222062: msg {}", "x"));
   }

   @Test
   public void testMessageOverload() {
      ArtemisLogCodeFilter filter = filter("222061");
      assertEquals(Result.DENY, filter.filter(null, Level.WARN, null, new SimpleMessage("AMQ222061: msg"), null));
      assertEquals(Result.NEUTRAL, filter.filter(null, Level.WARN, null, new SimpleMessage("AMQ222062: msg"), null));
   }

   @Test
   public void testObjectOverload() {
      ArtemisLogCodeFilter filter = filter("222061");
      assertEquals(Result.DENY, filter.filter(null, Level.WARN, null, (Object) "AMQ222061: msg", (Throwable) null));
      assertEquals(Result.NEUTRAL, filter.filter(null, Level.WARN, null, new Object(), (Throwable) null));
   }

   @Test
   public void testMissingCodesIsRejected() {
      assertNull(filter(null));
      assertNull(filter(""));
      assertNull(filter("   "));
   }

   @Test
   public void testCodeWithProjectCodePrefixIsRejected() {
      // the AMQ prefix is a constant and is deliberately not part of the configuration
      assertNull(filter("AMQ222061"));
      assertNull(filter("222061,AMQ224000"));
   }

   @Test
   public void testNonNumericCodeIsRejected() {
      assertNull(filter("not-a-code"));
      assertNull(filter("-222061"));
   }

   @Test
   public void testEmptyCodeIsRejected() {
      assertNull(filter("222061,"));
      assertNull(filter(",222061"));
      assertNull(filter("222061,,224000"));
   }

   /**
    * Starts an isolated context whose appender carries the filter. The filter has to be built through the
    * plugin system rather than directly, since that is what supplies the configuration it announces from.
    */
   private LoggerContext startContextWithFilter(Path log, String codes, String onMatch) {
      String properties = """
         rootLogger.level = INFO
         rootLogger.appenderRef.log_file.ref = log_file

         appender.log_file.type = File
         appender.log_file.name = log_file
         appender.log_file.fileName = %s
         appender.log_file.layout.type = PatternLayout
         appender.log_file.layout.pattern = %%msg%%n
         appender.log_file.filter.suppressed.type = ArtemisLogCodeFilter
         appender.log_file.filter.suppressed.codes = %s
         %s
         """.formatted(log.toString().replace("\\", "/"), codes,
                       onMatch == null ? "" : "appender.log_file.filter.suppressed.onMatch = " + onMatch);

      LoggerContext context = new LoggerContext("ArtemisLogCodeFilterTest");
      try {
         ConfigurationSource source = new ConfigurationSource(new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
         Configuration configuration = new PropertiesConfigurationFactory().getConfiguration(context, source);
         assertNotNull(configuration, "the configuration should have parsed");
         context.start(configuration);
         return context;
      } catch (IOException e) {
         context.close();
         throw new UncheckedIOException(e);
      }
   }

   private static List<String> announcementsIn(Path log) throws IOException {
      return Files.readAllLines(log).stream().filter(line -> line.contains(ANNOUNCEMENT_TEXT)).toList();
   }

   @Test
   public void testSuppressedCodesAreAnnouncedOnce(@TempDir Path tmp) throws IOException {
      Path log = tmp.resolve("artemis.log");
      try (LoggerContext context = startContextWithFilter(log, "224000, 222061", null)) {
         // events flowing through the filter must not produce any further announcement
         org.apache.logging.log4j.Logger logger = context.getLogger(LOGGER_NAME);
         for (int i = 0; i < 10; i++) {
            logger.warn("AMQ222061: suppressed");
            logger.warn("AMQ222062: passed through");
         }
      }

      List<String> announcements = announcementsIn(log);
      assertEquals(1, announcements.size(), announcements.toString());
      assertTrue(announcements.get(0).startsWith(ANNOUNCEMENT_TEXT), announcements.get(0));
      assertTrue(announcements.get(0).endsWith("222061, 224000"), announcements.get(0));
   }

   @Test
   public void testAnnouncementPrecedesWhatItSuppresses(@TempDir Path tmp) throws IOException {
      Path log = tmp.resolve("artemis.log");
      try (LoggerContext context = startContextWithFilter(log, "222061", null)) {
         context.getLogger(LOGGER_NAME).warn("AMQ222062: passed through");
      }

      // the announcement accounts for entries which are missing, so it has to come before them
      List<String> lines = Files.readAllLines(log);
      assertTrue(lines.get(0).contains(ANNOUNCEMENT_TEXT), lines.toString());
   }

   @Test
   public void testNothingIsAnnouncedWhenNotSuppressing(@TempDir Path tmp) throws IOException {
      // with onMatch other than DENY the filter suppresses nothing, so there is nothing to announce
      Path log = tmp.resolve("accepting.log");
      try (LoggerContext context = startContextWithFilter(log, "222061", "ACCEPT")) {
         context.getLogger(LOGGER_NAME).warn("AMQ222061: matched");
      }

      assertTrue(announcementsIn(log).isEmpty(), announcementsIn(log).toString());
   }

   @Test
   public void testToStringRendersCodesWithoutPrefix() {
      assertEquals("ArtemisLogCodeFilter[222061, 224000]", filter("224000,222061").toString());
   }

   @Test
   public void testSuppressionViaAppenderFilter(@TempDir Path tmp) throws IOException {
      assertSuppression("appender.log_file.filter.suppressed", tmp);
   }

   @Test
   public void testSuppressionViaLoggerFilter(@TempDir Path tmp) throws IOException {
      assertSuppression("logger.artemis_server.filter.suppressed", tmp);
   }

   @Test
   public void testSuppressionViaContextWideFilter(@TempDir Path tmp) throws IOException {
      assertSuppression("filter.suppressed", tmp);
   }

   private void assertSuppression(String filterPrefix, Path tmp) throws IOException {
      Path log = tmp.resolve("artemis.log");
      String properties = """
         rootLogger.level = INFO
         rootLogger.appenderRef.log_file.ref = log_file

         logger.artemis_server.name = %s
         logger.artemis_server.level = INFO

         appender.log_file.type = File
         appender.log_file.name = log_file
         appender.log_file.fileName = %s
         appender.log_file.layout.type = PatternLayout
         appender.log_file.layout.pattern = %%msg%%n

         %s.type = ArtemisLogCodeFilter
         %s.codes = 222061, 224000
         """.formatted(LOGGER_NAME, log.toString().replace("\\", "/"), filterPrefix, filterPrefix);

      // an isolated context so the surrounding test JVM's logging configuration is left alone
      try (LoggerContext context = new LoggerContext("ArtemisLogCodeFilterTest")) {
         ConfigurationSource source = new ConfigurationSource(new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
         Configuration configuration = new PropertiesConfigurationFactory().getConfiguration(context, source);
         assertNotNull(configuration, "the configuration should have parsed");
         context.start(configuration);

         org.apache.logging.log4j.Logger logger = context.getLogger(LOGGER_NAME);
         logger.warn("AMQ222061: suppressed with a parameter {}", "x");
         logger.warn("AMQ224000: suppressed across\nmultiple lines");
         logger.warn("AMQ222062: retained with two parameters {} {}", "a", "b");
         logger.info("AMQ221007: retained with no parameters");
         logger.warn("AMQ222063: retained with a throwable", new Exception("boom"));
         logger.warn("{}", "AMQ222061: retained because the code is only a parameter");
      }

      List<String> lines = Files.readAllLines(log);
      String output = String.join("\n", lines);

      assertFalse(output.contains("AMQ222061: suppressed"), "AMQ222061 should have been suppressed: " + output);
      assertFalse(output.contains("AMQ224000"), "AMQ224000 should have been suppressed: " + output);

      assertTrue(output.contains("AMQ222062: retained with two parameters a b"), output);
      assertTrue(output.contains("AMQ221007: retained with no parameters"), output);
      assertTrue(output.contains("AMQ222063: retained with a throwable"), output);
      assertTrue(output.contains("AMQ222061: retained because the code is only a parameter"), output);
   }
}
