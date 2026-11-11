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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.apache.activemq.artemis.core.config.MetricsConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.metrics.plugins.SimpleMetricsPlugin;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingMetricsTest extends ActiveMQTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }

   @Test
   public void testLoggingMetrics() throws Exception {
      final String idName = "log4j2.events";
      final Tag brokerTag = Tag.of("broker", "localhost");
      final String levelName = "level";
      int start = 0;
      String message = "";
      ActiveMQServer server = createServer(false, createDefaultInVMConfig()
         .setMetricsConfiguration(new MetricsConfiguration()
                                     .setPlugin(new SimpleMetricsPlugin().init(null))
                                     .setLogging(true)));
      server.start();

      Configurator.setLevel(MethodHandles.lookup().lookupClass(), Level.TRACE);
      final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

      Meter.Id trace = new Meter.Id(idName, Tags.of(brokerTag, Tag.of(levelName, "trace")), null, null, null);
      assertTrue(MetricsPluginTest.getMetrics(server).containsKey(trace));
      start = MetricsPluginTest.getMetrics(server).get(trace).intValue();
      logger.trace(message);
      logger.trace(message);
      logger.trace(message);
      assertEquals(3, MetricsPluginTest.getMetrics(server).get(trace).intValue() - start);

      Meter.Id debug = new Meter.Id(idName, Tags.of(brokerTag, Tag.of(levelName, "debug")), null, null, null);
      assertTrue(MetricsPluginTest.getMetrics(server).containsKey(debug));
      start = MetricsPluginTest.getMetrics(server).get(debug).intValue();
      logger.debug(message);
      logger.debug(message);
      logger.debug(message);
      assertEquals(3, MetricsPluginTest.getMetrics(server).get(debug).intValue() - start);

      Meter.Id info = new Meter.Id(idName, Tags.of(brokerTag, Tag.of(levelName, "info")), null, null, null);
      assertTrue(MetricsPluginTest.getMetrics(server).containsKey(info));
      start = MetricsPluginTest.getMetrics(server).get(info).intValue();
      logger.info(message);
      logger.info(message);
      logger.info(message);
      assertEquals(3, MetricsPluginTest.getMetrics(server).get(info).intValue() - start);

      Meter.Id warn = new Meter.Id(idName, Tags.of(brokerTag, Tag.of(levelName, "warn")), null, null, null);
      assertTrue(MetricsPluginTest.getMetrics(server).containsKey(warn));
      start = MetricsPluginTest.getMetrics(server).get(warn).intValue();
      logger.warn(message);
      logger.warn(message);
      logger.warn(message);
      assertEquals(3, MetricsPluginTest.getMetrics(server).get(warn).intValue() - start);

      Meter.Id error = new Meter.Id(idName, Tags.of(brokerTag, Tag.of(levelName, "error")), null, null, null);
      assertTrue(MetricsPluginTest.getMetrics(server).containsKey(error));
      start = MetricsPluginTest.getMetrics(server).get(error).intValue();
      logger.error(message);
      logger.error(message);
      logger.error(message);
      assertEquals(3, MetricsPluginTest.getMetrics(server).get(error).intValue() - start);
   }
}
