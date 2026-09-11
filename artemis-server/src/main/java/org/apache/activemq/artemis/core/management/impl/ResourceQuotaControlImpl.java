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

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;

import org.apache.activemq.artemis.api.core.management.ResourceQuotaControl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuota;

/**
 * Implementation of ResourceQuotaControl for JMX management.
 */
public class ResourceQuotaControlImpl extends AbstractControl implements ResourceQuotaControl {

   private final ResourceQuota resourceQuota;

   public ResourceQuotaControlImpl(final ResourceQuota resourceQuota,
                                    final StorageManager storageManager) throws Exception {
      super(ResourceQuotaControl.class, storageManager);
      this.resourceQuota = resourceQuota;
   }

   @Override
   protected MBeanAttributeInfo[] fillMBeanAttributeInfo() {
      return MBeanInfoHelper.getMBeanAttributesInfo(ResourceQuotaControl.class);
   }

   @Override
   protected MBeanOperationInfo[] fillMBeanOperationInfo() {
      return MBeanInfoHelper.getMBeanOperationsInfo(ResourceQuotaControl.class);
   }

   @Override
   public String getName() {
      clearIO();
      try {
         return resourceQuota.getName();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getPartOf() {
      clearIO();
      try {
         return resourceQuota.getPartOf();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getMaxAddresses() {
      clearIO();
      try {
         return resourceQuota.getMaxAddresses();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getCurrentAddressCount() {
      clearIO();
      try {
         return resourceQuota.getCurrentAddressCount();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public double getAddressUtilizationPercent() {
      clearIO();
      try {
         return resourceQuota.getAddressUtilizationPercent();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getMaxQueues() {
      clearIO();
      try {
         return resourceQuota.getMaxQueues();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getCurrentQueueCount() {
      clearIO();
      try {
         return resourceQuota.getCurrentQueueCount();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public double getQueueUtilizationPercent() {
      clearIO();
      try {
         return resourceQuota.getQueueUtilizationPercent();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getMaxMessageBytes() {
      clearIO();
      try {
         return resourceQuota.getMaxMessageBytes();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getCurrentMessageBytes() {
      clearIO();
      try {
         return resourceQuota.getCurrentMessageBytes();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public double getMessageBytesUtilizationPercent() {
      clearIO();
      try {
         return resourceQuota.getByteUtilizationPercent();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean hasLimits() {
      clearIO();
      try {
         return resourceQuota.hasLimits();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isLimitReached() {
      clearIO();
      try {
         return resourceQuota.isAddressLimitReached() ||
                resourceQuota.isQueueLimitReached() ||
                resourceQuota.isByteLimitReached();
      } finally {
         blockOnIO();
      }
   }
}
