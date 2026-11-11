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
package org.apache.activemq.artemis.core.management.impl;

import java.util.List;
import java.util.Map;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.management.BridgeControl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.cluster.Bridge;
import org.apache.activemq.artemis.logs.AuditLogger;

public class BridgeControlImpl extends AbstractControl implements BridgeControl {


   private final Bridge bridge;


   public BridgeControlImpl(final Bridge bridge,
                            final StorageManager storageManager) throws Exception {
      super(BridgeControl.class, storageManager);
      this.bridge = bridge;
   }

   // BridgeControlMBean implementation ---------------------------

   @Override
   public String[] getStaticConnectors() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getStaticConnectors(this.bridge);
      }
      clearIO();
      try {
         List<String> staticConnectors = bridge.getConfiguration().getStaticConnectors();
         return staticConnectors.toArray(new String[staticConnectors.size()]);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getForwardingAddress() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getForwardingAddress(this.bridge);
      }
      clearIO();
      try {
         return bridge.getConfiguration().getForwardingAddress();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getQueueName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getQueueName(this.bridge);
      }
      clearIO();
      try {
         return bridge.getConfiguration().getQueueName();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getDiscoveryGroupName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getDiscoveryGroupName(this.bridge);
      }
      clearIO();
      try {
         return bridge.getConfiguration().getDiscoveryGroupName();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getFilterString() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getFilterString(this.bridge);
      }
      clearIO();
      try {
         return bridge.getConfiguration().getFilterString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getReconnectAttempts() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getReconnectAttempts(this.bridge);
      }
      clearIO();
      try {
         return bridge.getConfiguration().getReconnectAttempts();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getName(this.bridge);
      }
      clearIO();
      try {
         return bridge.getConfiguration().getName();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getRetryInterval() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRetryInterval(this.bridge);
      }
      clearIO();
      try {
         return bridge.getConfiguration().getRetryInterval();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public double getRetryIntervalMultiplier() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRetryIntervalMultiplier(this.bridge);
      }
      clearIO();
      try {
         return bridge.getConfiguration().getRetryIntervalMultiplier();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getMaxRetryInterval() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMaxRetryInterval(this.bridge);
      }
      clearIO();
      try {
         return bridge.getConfiguration().getMaxRetryInterval();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getTransformerClassName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getTransformerClassName(this.bridge);
      }
      clearIO();
      try {
         return bridge.getConfiguration().getTransformerConfiguration() == null ? null : bridge.getConfiguration().getTransformerConfiguration().getClassName();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getTransformerPropertiesAsJSON() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getTransformerPropertiesAsJSON(this.bridge);
      }
      return JsonUtil.toJsonObject(getTransformerProperties()).toString();
   }

   @Override
   public Map<String, String> getTransformerProperties() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getTransformerProperties(this.bridge);
      }
      clearIO();
      try {
         return bridge.getConfiguration().getTransformerConfiguration() == null ? null : bridge.getConfiguration().getTransformerConfiguration().getProperties();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isStarted() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isStartedBridge(this.bridge);
      }
      clearIO();
      try {
         return bridge.isStarted();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isUseDuplicateDetection() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isUseDuplicateDetection(this.bridge);
      }
      clearIO();
      try {
         return bridge.getConfiguration().isUseDuplicateDetection();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isHA() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isHA(this.bridge);
      }
      clearIO();
      try {
         return bridge.getConfiguration().isHA();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void start() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.startBridge(this.bridge);
      }
      clearIO();
      try {
         bridge.start();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void stop() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.stopBridge(this.bridge);
      }
      clearIO();
      try {
         bridge.stop();
         bridge.flushExecutor();
      } finally {
         blockOnIO();
      }
   }

   @Override
   protected MBeanOperationInfo[] fillMBeanOperationInfo() {
      return MBeanInfoHelper.getMBeanOperationsInfo(BridgeControl.class);
   }

   @Override
   protected MBeanAttributeInfo[] fillMBeanAttributeInfo() {
      return MBeanInfoHelper.getMBeanAttributesInfo(BridgeControl.class);
   }

   @Override
   public long getMessagesPendingAcknowledgement() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMessagesPendingAcknowledgement(this.bridge);
      }
      clearIO();
      try {
         return bridge.getMetrics().getMessagesPendingAcknowledgement();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getMessagesAcknowledged() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMessagesAcknowledged(this.bridge);
      }
      clearIO();
      try {
         return bridge.getMetrics().getMessagesAcknowledged();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public Map<String, Object> getMetrics() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMetrics(this.bridge);
      }
      clearIO();
      try {
         return bridge.getMetrics().convertToMap();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isConnected() {
      return bridge.isConnected();
   }
}
