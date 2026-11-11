/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.tests.integration.plugin;

import java.util.Arrays;
import java.util.Set;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.apache.activemq.artemis.core.config.MetricsConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptor;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.metrics.BrokerMetricNames;
import org.apache.activemq.artemis.core.server.metrics.plugins.SimpleMetricsPlugin;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExecutorServiceMetricsTest extends ActiveMQTestBase {

   @Test
   public void testExecutorServiceMetricsPositive() throws Exception {
      internalTestMetrics(true);
   }

   @Test
   public void testExecutorServiceMetricsNegative() throws Exception {
      internalTestMetrics(false);
   }

   private void internalTestMetrics(boolean enabled) throws Exception {
      ActiveMQServer server = createServer(false, createDefaultNettyConfig().addAcceptorConfiguration("foo", "tcp://127.0.0.1:61617").setMetricsConfiguration(new MetricsConfiguration().setPlugin(new SimpleMetricsPlugin().init(null)).setExecutorServices(enabled)));
      server.start();
      Set<Meter.Id> metrics = MetricsPluginTest.getMetrics(server).keySet();
      for (String nameTagValue : Arrays.asList(BrokerMetricNames.GENERAL_EXECUTOR_SERVICE, BrokerMetricNames.IO_EXECUTOR_SERVICE, BrokerMetricNames.PAGE_EXECUTOR_SERVICE, BrokerMetricNames.SCHEDULED_EXECUTOR_SERVICE)) {
         Tags defaultTags = Tags.of(Tag.of("broker", server.getConfiguration().getName()), Tag.of("name", nameTagValue));
         assertEquals(enabled, metrics.contains(new Meter.Id("executor", defaultTags, null, null, null)));
         assertEquals(enabled, metrics.contains(new Meter.Id("executor.completed", defaultTags, null, null, null)));
         assertEquals(enabled, metrics.contains(new Meter.Id("executor.active", defaultTags, null, null, null)));
         assertEquals(enabled, metrics.contains(new Meter.Id("executor.idle", defaultTags, null, null, null)));
         assertEquals(enabled, metrics.contains(new Meter.Id("executor.queued", defaultTags, null, null, null)));
         assertEquals(enabled, metrics.contains(new Meter.Id("executor.queue.remaining", defaultTags, null, null, null)));
         assertEquals(enabled, metrics.contains(new Meter.Id("executor.pool.core", defaultTags, null, null, null)));
         assertEquals(enabled, metrics.contains(new Meter.Id("executor.pool.size", defaultTags, null, null, null)));
         assertEquals(enabled, metrics.contains(new Meter.Id("executor.pool.max", defaultTags, null, null, null)));
         if (nameTagValue.equals(BrokerMetricNames.SCHEDULED_EXECUTOR_SERVICE)) {
            assertEquals(enabled, metrics.contains(new Meter.Id("executor.scheduled.repetitively", defaultTags, null, null, null)));
            assertEquals(enabled, metrics.contains(new Meter.Id("executor.scheduled.once", defaultTags, null, null, null)));
         }
      }
      boolean nettyAcceptorExists = false;
      for (Acceptor acceptor : server.getRemotingService().getAcceptors().values()) {
         if (acceptor instanceof NettyAcceptor nettyAcceptor) {
            nettyAcceptorExists = true;
            for (int i = 0; i < nettyAcceptor.getRemotingThreads(); i++) {
               Tags defaultTags = Tags.of(Tag.of("broker", "localhost"), Tag.of("name", "Thread-" + i + " (activemq-remoting-" + nettyAcceptor.getName() + "-" + server.getConfiguration().getName() + ")"));
               assertEquals(enabled, metrics.contains(new Meter.Id("netty.eventexecutor.tasks.pending", defaultTags, null, null, null)));
            }
            acceptor.stop();
            metrics = MetricsPluginTest.getMetrics(server).keySet();
            for (int i = 0; i < nettyAcceptor.getRemotingThreads(); i++) {
               Tags defaultTags = Tags.of(Tag.of("broker", "localhost"), Tag.of("name", "Thread-" + i + " (activemq-remoting-" + nettyAcceptor.getName() + "-" + server.getConfiguration().getName() + ")"));
               assertFalse(metrics.contains(new Meter.Id("netty.eventexecutor.tasks.pending", defaultTags, null, null, null)));
            }
         }
      }
      assertTrue(nettyAcceptorExists);
   }
}