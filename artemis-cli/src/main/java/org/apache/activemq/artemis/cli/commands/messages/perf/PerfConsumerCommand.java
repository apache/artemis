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
package org.apache.activemq.artemis.cli.commands.messages.perf;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "consumer", description = "Consume messages from a queue.")
public class PerfConsumerCommand extends PerfCommand {

   @Option(names = "--tx", description = "Consume in transactions, committing every --commit-interval messages, or every message without it. Default: disabled.")
   protected boolean transaction;

   @Option(names = "--shared", description = "Create shared subscription. Default: 0.")
   protected int sharedSubscription = 0;

   @Option(names = "--durable", description = "Enabled durable subscription. Default: disabled.")
   protected boolean durableSubscription = false;

   @Option(names = "--num-connections", description = "Number of connections to be used. Default: same as the total number of consumers.")
   protected int connections = 0;

   @Option(names = "--consumers", description = "Number of consumer to use for each generated destination. Default: 1.")
   protected int consumersPerDestination = 1;

   private BenchmarkService benchmark;

   @Override
   protected void onExecuteBenchmark(final ConnectionFactory factory,
                                     final Destination[] jmsDestinations,
                                     final ActionContext context) throws Exception {
      final MicrosTimeProvider timeProvider = MicrosClock.timeProvider(context);
      if (connections == 0) {
         if (sharedSubscription > 0) {
            if (clientID == null) {
               connections = sharedSubscription * consumersPerDestination * jmsDestinations.length;
            } else {
               connections = sharedSubscription * jmsDestinations.length;
            }
         } else {
            connections = consumersPerDestination * jmsDestinations.length;
         }
      }

      boolean warmingUp = warmup != 0;
      final StringBuilder skratchBuffer = new StringBuilder();
      final LiveStatistics statistics;
      try (MessageListenerBenchmark benchmark = new MessageListenerBenchmarkBuilder()
         .setClientID(getClientID())
         .setDestinations(jmsDestinations)
         .setFactory(factory)
         .setTransacted(transaction)
         .setCommitInterval(commitInterval)
         .setConsumers(consumersPerDestination)
         .setMessageCount(messageCount)
         .setConnections(connections)
         .setTimeProvider(timeProvider)
         .setSharedSubscription(sharedSubscription)
         .setDurableSubscription(durableSubscription)
         .createMessageListenerBenchmark()) {
         this.benchmark = benchmark;
         benchmark.start();
         final long now = System.currentTimeMillis();
         final long endWarmup = warmup > 0 ? now + TimeUnit.SECONDS.toMillis(warmup) : 0;
         final long end = duration > 0 ? now + TimeUnit.SECONDS.toMillis(duration) : 0;
         statistics = new LiveStatistics(reportFileName, hdrFileName, null, benchmark.getListeners());
         LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
         warmingUp = collectAndReportStatisticsWhileRunning(warmingUp, statistics, skratchBuffer, endWarmup, end, benchmark);
      }
      // last sample must be collected while the whole benchmark is complete
      statistics.sampleMetrics(warmingUp);
      skratchBuffer.setLength(0);
      statistics.outSummary(skratchBuffer, ascii);
      if (!isSilentInput()) {
         context.out.println(skratchBuffer);
      }
      statistics.close();
   }

   @Override
   protected void onInterruptBenchmark() {
      final BenchmarkService benchmark = this.benchmark;
      if (benchmark != null) {
         benchmark.close();
      }
   }
}
