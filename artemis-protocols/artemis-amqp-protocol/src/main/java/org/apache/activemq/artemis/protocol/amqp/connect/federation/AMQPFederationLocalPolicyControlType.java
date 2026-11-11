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
package org.apache.activemq.artemis.protocol.amqp.connect.federation;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.NotCompliantMBeanException;

import org.apache.activemq.artemis.core.management.impl.AbstractControl;
import org.apache.activemq.artemis.core.management.impl.MBeanInfoHelper;
import org.apache.activemq.artemis.logs.AuditLogger;

/**
 * Management service control for an AMQP Federation local policy manager instance.
 */
public class AMQPFederationLocalPolicyControlType extends AbstractControl implements AMQPFederationLocalPolicyControl {

   private final AMQPFederationLocalPolicyManager policyManager;

   public AMQPFederationLocalPolicyControlType(AMQPFederationLocalPolicyManager policyManager) throws NotCompliantMBeanException {
      super(AMQPFederationLocalPolicyControl.class, policyManager.getFederation().getServer().getStorageManager());

      this.policyManager = policyManager;
   }

   @Override
   public String getType() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getType(this.policyManager);
      }
      clearIO();
      try {
         return policyManager.getPolicyType().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getName(this.policyManager);
      }
      clearIO();
      try {
         return policyManager.getPolicyName();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getMessagesReceived() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMessagesReceived(this.policyManager);
      }
      clearIO();
      try {
         return policyManager.getMetrics().getMessagesReceived();
      } finally {
         blockOnIO();
      }
   }

   @Override
   protected MBeanOperationInfo[] fillMBeanOperationInfo() {
      return MBeanInfoHelper.getMBeanOperationsInfo(AMQPFederationLocalPolicyControl.class);
   }

   @Override
   protected MBeanAttributeInfo[] fillMBeanAttributeInfo() {
      return MBeanInfoHelper.getMBeanAttributesInfo(AMQPFederationLocalPolicyControl.class);
   }
}
