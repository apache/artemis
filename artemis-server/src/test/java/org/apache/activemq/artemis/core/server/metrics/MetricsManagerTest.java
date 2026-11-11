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
package org.apache.activemq.artemis.core.server.metrics;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.core.config.MetricsConfiguration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.server.metrics.plugins.SimpleMetricsPlugin;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetricsManagerTest {

   @Test
   public void testQueueMetricsEnabled() throws Exception {
      testQueueMetrics(true);
   }

   @Test
   public void testQueueMetricsDisabled() throws Exception {
      testQueueMetrics(false);
   }

   public void testQueueMetrics(boolean enableMetrics) throws Exception {
      final String address = RandomUtil.randomAlphaNumericString(4);
      final String queue = RandomUtil.randomAlphaNumericString(4);
      HierarchicalRepository<AddressSettings> addressSettingsRepository = new HierarchicalObjectRepository<>(new WildcardConfiguration());
      addressSettingsRepository.addMatch("#", new AddressSettings().setEnableMetrics(!enableMetrics));
      addressSettingsRepository.addMatch(address, new AddressSettings().setEnableMetrics(enableMetrics));

      MetricsConfiguration metricsConfiguration = new MetricsConfiguration();
      metricsConfiguration.setPlugin(new SimpleMetricsPlugin().init(null));
      MetricsManager metricsManager = new MetricsManager(RandomUtil.randomUUIDString(), metricsConfiguration, addressSettingsRepository, null);

      AtomicBoolean test = new AtomicBoolean(false);
      metricsManager.registerQueueGauge(address, queue, (builder) -> {
         test.set(true);
      });
      assertEquals(enableMetrics, test.get());
   }
}
