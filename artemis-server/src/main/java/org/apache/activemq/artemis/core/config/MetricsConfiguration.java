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
package org.apache.activemq.artemis.core.config;

import java.io.Serializable;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.server.metrics.ActiveMQMetricsPlugin;

public class MetricsConfiguration implements Serializable {

   private boolean jvmMemory = ActiveMQDefaultConfiguration.getDefaultJvmMemoryMetrics();
   private boolean jvmGc = ActiveMQDefaultConfiguration.getDefaultJvmGcMetrics();
   private boolean jvmThread = ActiveMQDefaultConfiguration.getDefaultJvmThreadMetrics();
   private boolean nettyPool = ActiveMQDefaultConfiguration.getDefaultNettyPoolMetrics();
   private boolean fileDescriptors = ActiveMQDefaultConfiguration.getDefaultFileDescriptorsMetrics();
   private boolean processor = ActiveMQDefaultConfiguration.getDefaultProcessorMetrics();
   private boolean uptime = ActiveMQDefaultConfiguration.getDefaultUptimeMetrics();
   private boolean logging = ActiveMQDefaultConfiguration.getDefaultLoggingMetrics();
   private boolean securityCaches = ActiveMQDefaultConfiguration.getDefaultSecurityCacheMetrics();
   private boolean executorServices = ActiveMQDefaultConfiguration.getDefaultExecutorServiceMetrics();
   private ActiveMQMetricsPlugin plugin;

   public boolean isJvmMemory() {
      return jvmMemory;
   }

   public MetricsConfiguration setJvmMemory(boolean jvmMemory) {
      this.jvmMemory = jvmMemory;
      return this;
   }

   public boolean isJvmGc() {
      return jvmGc;
   }

   public MetricsConfiguration setJvmGc(boolean jvmGc) {
      this.jvmGc = jvmGc;
      return this;
   }

   public boolean isJvmThread() {
      return jvmThread;
   }

   public MetricsConfiguration setJvmThread(boolean jvmThread) {
      this.jvmThread = jvmThread;
      return this;
   }

   public boolean isNettyPool() {
      return nettyPool;
   }

   public MetricsConfiguration setNettyPool(boolean nettyPool) {
      this.nettyPool = nettyPool;
      return this;
   }

   public boolean isFileDescriptors() {
      return fileDescriptors;
   }

   public MetricsConfiguration setFileDescriptors(boolean fileDescriptors) {
      this.fileDescriptors = fileDescriptors;
      return this;
   }

   public boolean isProcessor() {
      return processor;
   }

   public MetricsConfiguration setProcessor(boolean processor) {
      this.processor = processor;
      return this;
   }

   public boolean isUptime() {
      return uptime;
   }

   public MetricsConfiguration setUptime(boolean uptime) {
      this.uptime = uptime;
      return this;
   }

   public boolean isLogging() {
      return logging;
   }

   public MetricsConfiguration setLogging(boolean logging) {
      this.logging = logging;
      return this;
   }

   public ActiveMQMetricsPlugin getPlugin() {
      return plugin;
   }

   public MetricsConfiguration setPlugin(ActiveMQMetricsPlugin plugin) {
      this.plugin = plugin;
      return this;
   }

   public boolean isSecurityCaches() {
      return securityCaches;
   }

   public MetricsConfiguration setSecurityCaches(boolean securityCaches) {
      this.securityCaches = securityCaches;
      return this;
   }

   public boolean isExecutorServices() {
      return executorServices;
   }

   public MetricsConfiguration setExecutorServices(boolean executorServices) {
      this.executorServices = executorServices;
      return this;
   }
}
