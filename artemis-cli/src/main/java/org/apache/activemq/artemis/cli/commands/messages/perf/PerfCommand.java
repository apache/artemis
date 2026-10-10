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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Session;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.messages.ConnectionAbstract;
import org.apache.activemq.artemis.cli.commands.messages.DestAbstract;
import org.apache.activemq.artemis.cli.factory.ConnectionFactoryClosable;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static java.util.Collections.singletonList;

public abstract class PerfCommand extends ConnectionAbstract {

   @Option(names = "--show-latency", description = "Show latencies at interval on output. Default: disabled.")
   protected boolean showLatency = false;

   @Option(names = "--json", description = "Report file name. Default: none.")
   protected String reportFileName = null;

   @Option(names = "--hdr", description = "HDR Histogram Report file name. Default: none.")
   protected String hdrFileName = null;

   @Option(names = "--duration", description = "Test duration (in seconds). Default: 0.")
   protected int duration = 0;

   @Option(names = "--warmup", description = "Warmup time (in seconds). Default: 0.")
   protected int warmup = 0;

   @Option(names = "--message-count", description = "Total number of messages. Default: 0.")
   protected long messageCount = 0;

   @Option(names = "--num-destinations", description = "If present, generate --num-destinations for each destination name, using it as a prefix and adding a number [0,--num-destinations) as suffix. Default: none.")
   protected int numDestinations = 1;

   @Option(names = "--tx-size", description = "Transaction size.", hidden = true)
   protected long txSize;

   @Option(names = "--commit-interval", description = "Transaction size.")
   protected long commitInterval;

   @Parameters(description = "List of destination names. Each name can be prefixed with queue:// or topic:// and can be an FQQN in the form of <address>::<queue>. Default: queue://TEST.")
   protected List<String> destinations;

   private final CountDownLatch completed = new CountDownLatch(1);

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);
      if (txSize > 0) {
         context.out.println("--tx-size is deprecated, please use --commit-interval");
         commitInterval = txSize;
      }
      try (ConnectionFactoryClosable factory = createConnectionFactory(brokerURL, user, password, null, protocol)) {
         final Destination[] jmsDestinations = lookupDestinations(factory, destinations, numDestinations);
         Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            onInterruptBenchmark();
            try {
               // Use a timed await to prevent permanent shutdown hangs if thread/driver is deadlocked
               if (!completed.await(5, TimeUnit.SECONDS)) {
                  System.err.println("Benchmark shutdown timed out waiting for completion latch.");
               }
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
            }
         }));
         try {
            onExecuteBenchmark(factory, jmsDestinations, context);
         } finally {
            completed.countDown();
         }
      }
      return null;
   }

   protected abstract void onExecuteBenchmark(ConnectionFactory factory,
                                              Destination[] jmsDestinations,
                                              ActionContext context) throws Exception;

   protected abstract void onInterruptBenchmark();

   protected boolean collectAndReportStatisticsWhileRunning(boolean warmingUp,
                                                            final LiveStatistics statistics,
                                                            final StringBuilder skratchBuffer,
                                                            final long endWarmup,
                                                            final long end,
                                                            final BenchmarkService benchmark) throws IOException {
      while (benchmark.isRunning() && !Thread.currentThread().isInterrupted()) {
         if (end != 0) {
            final long tick = System.currentTimeMillis();
            if (tick - end >= 0) {
               break;
            }
         }
         if (endWarmup != 0 && warmingUp) {
            final long tick = System.currentTimeMillis();
            if (tick - endWarmup >= 0) {
               warmingUp = false;
            }
         }
         statistics.sampleMetrics(warmingUp);
         skratchBuffer.setLength(0);
         statistics.outAtInterval(warmingUp, skratchBuffer, LiveStatistics.ReportInterval.sec, showLatency);
         if (!isSilentInput()) {
            getActionContext().out.println(skratchBuffer);
         }
         LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
      }
      return warmingUp;
   }

   protected final Destination[] lookupDestinations(final ConnectionFactory factory) throws Exception {
      return lookupDestinations(factory, destinations, numDestinations);
   }

   private static Destination[] lookupDestinations(final ConnectionFactory factory,
                                                   final List<String> destinations,
                                                   final int numDestinations) throws Exception {
      final List<String> destinationNames;
      if (destinations == null || destinations.isEmpty()) {
         destinationNames = singletonList("queue://TEST");
      } else {
         destinationNames = destinations;
      }
      final Destination[] jmsDestinations = new Destination[destinationNames.size() * numDestinations];
      try (Connection connection = factory.createConnection();
           Session session = connection.createSession()) {
         int i = 0;
         for (String destinationName : destinationNames) {
            if (numDestinations == 1) {
               jmsDestinations[i] = DestAbstract.getDestination(session, destinationName);
               i++;
            } else {
               for (int suffix = 0; suffix < numDestinations; suffix++) {
                  jmsDestinations[i] = DestAbstract.getDestination(session, destinationName + suffix);
                  i++;
               }
            }
         }
      }
      return jmsDestinations;
   }

   public boolean isShowLatency() {
      return showLatency;
   }

   public PerfCommand setShowLatency(boolean showLatency) {
      this.showLatency = showLatency;
      return this;
   }

   public String getReportFileName() {
      return reportFileName;
   }

   public PerfCommand setReportFileName(String reportFileName) {
      this.reportFileName = reportFileName;
      return this;
   }

   public String getHdrFileName() {
      return hdrFileName;
   }

   public PerfCommand setHdrFileName(String hdrFileName) {
      this.hdrFileName = hdrFileName;
      return this;
   }

   public int getDuration() {
      return duration;
   }

   public PerfCommand setDuration(int duration) {
      this.duration = duration;
      return this;
   }

   public int getWarmup() {
      return warmup;
   }

   public PerfCommand setWarmup(int warmup) {
      this.warmup = warmup;
      return this;
   }

   public long getMessageCount() {
      return messageCount;
   }

   public PerfCommand setMessageCount(long messageCount) {
      this.messageCount = messageCount;
      return this;
   }

   public int getNumDestinations() {
      return numDestinations;
   }

   public PerfCommand setNumDestinations(int numDestinations) {
      this.numDestinations = numDestinations;
      return this;
   }

   public List<String> getDestinations() {
      return destinations;
   }

   public PerfCommand setDestinations(List<String> destinations) {
      this.destinations = destinations;
      return this;
   }

   public CountDownLatch getCompleted() {
      return completed;
   }
}
