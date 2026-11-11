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
package org.apache.activemq.artemis.tests.integration.plugin;

import java.util.Map;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.activemq.artemis.core.config.MetricsConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.metrics.ActiveMQMetricsPlugin;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.apache.activemq.artemis.core.server.metrics.MetricsManager.BROKER_TAG_NAME;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PassthroughMetricsPluginTest extends MetricsPluginTest {

   private MeterRegistry meterRegistry;

   @Override
   protected void configureServer(ActiveMQServer server) {
      meterRegistry = new SimpleMeterRegistry();
      server.getConfiguration().setMetricsConfiguration(new MetricsConfiguration().setPlugin(new ActiveMQMetricsPlugin() {
         @Override
         public ActiveMQMetricsPlugin init(Map<String, String> map) {
            return this;
         }

         @Override
         public MeterRegistry getRegistry() {
            return meterRegistry;
         }
      }));
   }

   @Test
   public void testPassthroughMeterRegistry() {
      final String meterName = getName();
      meterRegistry.gauge(meterName, Tags.of(meterName, RandomUtil.randomUUIDString()), RandomUtil.randomDouble(), random -> random.doubleValue());
      boolean found = false;
      for (Meter.Id meter : getMetrics().keySet()) {
         if (meter.getName().equals(meterName)) {
            found = true;
            assertNull(meter.getTag(BROKER_TAG_NAME));
            assertNotNull(meter.getTag(meterName));
         }
      }
      assertTrue(found);
   }

   @Disabled
   @Override
   public void testMetricsPluginRegistration() {
      // this overrides & disables a test that is not applicable for this use-case
   }
}
