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

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PerfCommandTest {

   private static final class AlwaysRunningBenchmark implements BenchmarkService {
      @Override
      public BenchmarkService start() {
         return this;
      }

      @Override
      public boolean anyError() {
         return false;
      }

      @Override
      public boolean isRunning() {
         return true;
      }

      @Override
      public void close() {
      }
   }

   private static final class NoOpPerfCommand extends PerfCommand {
      @Override
      protected void onExecuteBenchmark(ConnectionFactory factory, Destination[] jmsDestinations, ActionContext context) {
      }

      @Override
      protected void onInterruptBenchmark() {
      }
   }

   /**
    * A benchmark that never reports itself as finished must not keep the reporting loop running forever once its thread
    * has been interrupted.
    */
   @Test
   public void testCollectAndReportStatisticsStopsOnInterrupt() throws Exception {
      final PerfCommand command = new NoOpPerfCommand();
      final LiveStatistics statistics = new LiveStatistics(null, null, null, null);
      final StringBuilder scratchBuffer = new StringBuilder();
      final BenchmarkService benchmark = new AlwaysRunningBenchmark();

      final CountDownLatch loopStarted = new CountDownLatch(1);
      final CountDownLatch loopExited = new CountDownLatch(1);
      final Thread runner = new Thread(() -> {
         loopStarted.countDown();
         try {
            command.collectAndReportStatisticsWhileRunning(false, statistics, scratchBuffer, 0, 0, benchmark);
         } catch (Exception e) {
            throw new RuntimeException(e);
         } finally {
            loopExited.countDown();
         }
      });
      runner.start();

      assertTrue(loopStarted.await(5, TimeUnit.SECONDS));
      runner.interrupt();

      assertTrue(loopExited.await(5, TimeUnit.SECONDS),
                 "collectAndReportStatisticsWhileRunning should return once its thread is interrupted, " +
                 "even if the benchmark never reports itself as finished");

      runner.join(5000);
   }
}
