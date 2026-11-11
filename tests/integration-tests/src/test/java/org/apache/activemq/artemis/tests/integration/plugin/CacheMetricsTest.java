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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.apache.activemq.artemis.core.config.MetricsConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.metrics.plugins.SimpleMetricsPlugin;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CacheMetricsTest extends ActiveMQTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }

   @Test
   public void testCacheMetricsEnabled() throws Exception {
      testCacheMetrics(true);
   }

   @Test
   public void testCacheMetricsDisabled() throws Exception {
      testCacheMetrics(false);
   }

   private void testCacheMetrics(boolean enabled) throws Exception {
      ActiveMQServer server = createServer(false, createDefaultInVMConfig().setSecurityEnabled(true)
         .setMetricsConfiguration(new MetricsConfiguration()
                                     .setPlugin(new SimpleMetricsPlugin().init(null))
                                     .setSecurityCaches(enabled)));
      server.start();
      List<Meter.Id> metersToMatch = new ArrayList<>();
      for (String cacheTagValue : Arrays.asList("authentication", "authorization")) {
         Tags defaultTags = Tags.of(Tag.of("broker", "localhost"), Tag.of("cache", cacheTagValue));
         metersToMatch.add(new Meter.Id("cache.size", defaultTags, null, null, null));
         metersToMatch.add(new Meter.Id("cache.puts", defaultTags, null, null, null));
         metersToMatch.add(new Meter.Id("cache.gets", defaultTags.and(Tag.of("result", "miss")), null, null, null));
         metersToMatch.add(new Meter.Id("cache.gets", defaultTags.and(Tag.of("result", "hit")), null, null, null));
         metersToMatch.add(new Meter.Id("cache.evictions", defaultTags, null, null, null));
         metersToMatch.add(new Meter.Id("cache.eviction.weight", defaultTags, null, null, null));
      }
      assertEquals(enabled, MetricsPluginTest.getMetrics(server).keySet().containsAll(metersToMatch));
   }
}
