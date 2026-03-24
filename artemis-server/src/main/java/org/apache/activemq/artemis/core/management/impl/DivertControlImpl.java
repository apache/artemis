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

import java.util.Collections;
import java.util.Map;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.management.DivertControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.Divert;
import org.apache.activemq.artemis.core.server.transformer.RegisteredTransformer;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.activemq.artemis.logs.AuditLogger;

public class DivertControlImpl extends AbstractControl implements DivertControl {


   private final Divert divert;

   private final String internalNamingPrefix;


   // DivertControlMBean implementation ---------------------------

   public DivertControlImpl(final Divert divert,
                            final StorageManager storageManager,
                            final String internalNamingPrefix) throws Exception {
      super(DivertControl.class, storageManager);
      this.divert = divert;
      this.internalNamingPrefix = internalNamingPrefix;
   }

   @Override
   public String getAddress() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAddress(this.divert);
      }
      clearIO();
      try {
         return divert.getAddress().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getFilter() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getFilter(this.divert);
      }
      clearIO();
      try {
         Filter filter = divert.getFilter();
         return filter != null ? filter.getFilterString().toString() : null;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getForwardingAddress() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getForwardingAddress(this.divert);
      }
      clearIO();
      try {
         return divert.getForwardAddress().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getRoutingName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRoutingName(this.divert);
      }
      clearIO();
      try {
         return divert.getRoutingName().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getTransformerClassName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getTransformerClassName(this.divert);
      }
      clearIO();
      try {
         Transformer transformer = divert.getTransformer();
         return transformer != null ? (transformer instanceof RegisteredTransformer ?
            ((RegisteredTransformer)transformer).getTransformer() : transformer).getClass().getName() : null;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getTransformerPropertiesAsJSON() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getTransformerPropertiesAsJSON(this.divert);
      }
      return JsonUtil.toJsonObject(getTransformerProperties()).toString();
   }

   @Override
   public Map<String, String> getTransformerProperties() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getTransformerProperties(this.divert);
      }
      clearIO();
      try {
         Transformer transformer = divert.getTransformer();
         return transformer != null && transformer instanceof RegisteredTransformer ?
            ((RegisteredTransformer)transformer).getProperties() : Collections.emptyMap();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getRoutingType() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRoutingType(this.divert);
      }
      clearIO();
      try {
         return divert.getRoutingType().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getUniqueName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getUniqueName(this.divert);
      }
      clearIO();
      try {
         return divert.getUniqueName().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isExclusive() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isExclusive(this.divert);
      }
      clearIO();
      try {
         return divert.isExclusive();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isRetroactiveResource() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isRetroactiveResource(this.divert);
      }
      return ResourceNames.isRetroactiveResource(internalNamingPrefix, divert.getUniqueName());
   }

   @Override
   protected MBeanOperationInfo[] fillMBeanOperationInfo() {
      return MBeanInfoHelper.getMBeanOperationsInfo(DivertControl.class);
   }

   @Override
   protected MBeanAttributeInfo[] fillMBeanAttributeInfo() {
      return MBeanInfoHelper.getMBeanAttributesInfo(DivertControl.class);
   }


}
