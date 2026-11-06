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
package org.apache.activemq.artemis.cli.commands.messages.perf;

import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.activemq.artemis.utils.TableOut;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LiveStatisticsTest {

   private static String summary(boolean ascii) throws IOException {
      LiveStatistics statistics = new LiveStatistics(null, null, null, new RecordingMessageListener[0]);
      statistics.sampleMetrics(false);
      StringBuilder out = new StringBuilder();
      statistics.outSummary(out, ascii);
      statistics.close();
      return out.toString();
   }

   @Test
   public void testSummaryTables() throws IOException {
      String summary = summary(false);
      assertTrue(summary.contains("--- SUMMARY"), summary);
      assertTrue(summary.contains("--- result: success"), summary);
      assertTrue(summary.contains(TableOut.BOX_TOP_LEFT), summary);
      assertTrue(summary.contains(TableOut.BOX_VERTICAL + " received "), summary);
      assertTrue(summary.contains(TableOut.BOX_VERTICAL + " transfer time "), summary);
      assertFalse(summary.contains("send time"), summary);
   }

   @Test
   public void testSummaryTablesInAscii() throws IOException {
      String summary = summary(true);
      assertTrue(summary.contains("| received "), summary);
      assertTrue(summary.contains("| transfer time "), summary);
      assertFalse(summary.contains(TableOut.BOX_VERTICAL), summary);
   }

   private static int count(String text, String part) {
      return text.split(Pattern.quote(part), -1).length - 1;
   }

   private static String interval(LiveStatistics statistics, boolean warmup, boolean latencies) throws IOException {
      statistics.sampleMetrics(warmup);
      StringBuilder out = new StringBuilder();
      statistics.outAtInterval(warmup, out, LiveStatistics.ReportInterval.sec, latencies, false);
      return out.toString();
   }

   @Test
   public void testIntervalsAreRowsOfOneTable() throws IOException {
      LiveStatistics statistics = new LiveStatistics(null, null, null, new RecordingMessageListener[0]);

      String first = interval(statistics, true, false);
      assertEquals(1, count(first, TableOut.BOX_TOP_LEFT), first);
      assertTrue(first.contains("received msg/sec"), first);
      assertTrue(first.contains(" true "), first);

      String second = interval(statistics, false, false);
      assertFalse(second.contains(TableOut.BOX_TOP_LEFT), second);
      assertFalse(second.contains("received msg/sec"), second);
      assertTrue(second.contains(" false "), second);
      assertEquals(1, second.lines().count(), second);

      // the summary closes the table, once
      StringBuilder summary = new StringBuilder();
      statistics.outSummary(summary, false);
      assertTrue(summary.toString().startsWith(TableOut.BOX_BOTTOM_LEFT), summary.toString());
      statistics.close();
   }

   @Test
   public void testIntervalWithLatenciesIsATableOfItsOwn() throws IOException {
      LiveStatistics statistics = new LiveStatistics(null, null, null, new RecordingMessageListener[0]);
      for (int i = 0; i < 2; i++) {
         String interval = interval(statistics, false, true);
         assertEquals(2, count(interval, TableOut.BOX_TOP_LEFT), interval);
         assertEquals(2, count(interval, TableOut.BOX_BOTTOM_LEFT), interval);
         assertTrue(interval.contains("received msg/sec"), interval);
         assertTrue(interval.contains(TableOut.BOX_VERTICAL + " transfer time "), interval);
      }

      // nothing is left open for the summary to close
      StringBuilder summary = new StringBuilder();
      statistics.outSummary(summary, false);
      assertTrue(summary.toString().startsWith("\n--- SUMMARY"), summary.toString());
      statistics.close();
   }

   @Test
   public void testIntervalsInAscii() throws IOException {
      LiveStatistics statistics = new LiveStatistics(null, null, null, new RecordingMessageListener[0]);
      statistics.sampleMetrics(false);
      StringBuilder out = new StringBuilder();
      statistics.outAtInterval(false, out, LiveStatistics.ReportInterval.sec, false, true);
      statistics.outSummary(out, true);
      statistics.close();
      assertTrue(out.toString().contains("| received msg/sec |"), out.toString());
      assertFalse(out.toString().contains(TableOut.BOX_VERTICAL), out.toString());
   }
}
