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
package org.apache.activemq.artemis.core.server.metrics.plugins;

import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.metrics.ActiveMQMetricsPlugin;

/**
 * A very simple metrics plugin used for testing
 */
public class SimpleMetricsPlugin implements ActiveMQMetricsPlugin {

   private transient MeterRegistry meterRegistry;

   private Map<String, String> options;

   private ActiveMQServer server;

   @Override
   public ActiveMQMetricsPlugin init(Map<String, String> options) {
      this.meterRegistry = new SimpleMeterRegistry();
      this.options = options;
      return this;
   }

   @Override
   public MeterRegistry getRegistry() {
      return meterRegistry;
   }

   @Override
   public void registered(ActiveMQServer server) {
      this.server = server;
   }

   public Map<String, String> getOptions() {
      return options;
   }

   public ActiveMQServer getServer() {
      return server;
   }
}
