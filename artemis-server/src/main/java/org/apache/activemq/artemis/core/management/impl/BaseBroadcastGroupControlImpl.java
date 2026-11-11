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

import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.management.BaseBroadcastGroupControl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.cluster.BroadcastGroup;
import org.apache.activemq.artemis.logs.AuditLogger;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;

public class BaseBroadcastGroupControlImpl extends AbstractControl implements BaseBroadcastGroupControl {

   private Class broadcastGroupControlClass;

   private final BroadcastGroup broadcastGroup;

   private final BroadcastGroupConfiguration configuration;


   public BaseBroadcastGroupControlImpl(final BroadcastGroup broadcastGroup,
                                        final StorageManager storageManager,
                                        final BroadcastGroupConfiguration configuration) throws Exception {
      this(BaseBroadcastGroupControl.class, broadcastGroup, storageManager, configuration);
   }

   public BaseBroadcastGroupControlImpl(final Class broadcastGroupControlClass,
                                        final BroadcastGroup broadcastGroup,
                                        final StorageManager storageManager,
                                        final BroadcastGroupConfiguration configuration) throws Exception {
      super(broadcastGroupControlClass, storageManager);
      this.broadcastGroupControlClass = broadcastGroupControlClass;
      this.broadcastGroup = broadcastGroup;
      this.configuration = configuration;
   }

   // BroadcastGroupControlMBean implementation ---------------------

   @Override
   public String getName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getName(this.broadcastGroup);
      }
      clearIO();
      try {
         return configuration.getName();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getBroadcastPeriod() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getBroadcastPeriod(this.broadcastGroup);
      }
      clearIO();
      try {
         return configuration.getBroadcastPeriod();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public Object[] getConnectorPairs() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getConnectorPairs(this.broadcastGroup);
      }
      clearIO();
      try {
         Object[] ret = new Object[configuration.getConnectorInfos().size()];

         int i = 0;
         for (String connector : configuration.getConnectorInfos()) {
            ret[i++] = connector;
         }
         return ret;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getConnectorPairsAsJSON() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getConnectorPairsAsJSON(this.broadcastGroup);
      }
      clearIO();
      try {
         return JsonUtil.toJsonArray(configuration.getConnectorInfos()).toString();
      } finally {
         blockOnIO();
      }
   }

   // MessagingComponentControlMBean implementation -----------------

   @Override
   public boolean isStarted() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isStarted(this.broadcastGroup);
      }
      clearIO();
      try {
         return broadcastGroup.isStarted();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void start() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.startBroadcastGroup(this.broadcastGroup);
      }
      clearIO();
      try {
         broadcastGroup.start();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void stop() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.stopBroadcastGroup(this.broadcastGroup);
      }
      clearIO();
      try {
         broadcastGroup.stop();
      } finally {
         blockOnIO();
      }
   }

   @Override
   protected MBeanOperationInfo[] fillMBeanOperationInfo() {
      return MBeanInfoHelper.getMBeanOperationsInfo(broadcastGroupControlClass);
   }

   @Override
   protected MBeanAttributeInfo[] fillMBeanAttributeInfo() {
      return MBeanInfoHelper.getMBeanAttributesInfo(broadcastGroupControlClass);
   }

   protected BroadcastGroup getBroadcastGroup() {
      return broadcastGroup;
   }


}
