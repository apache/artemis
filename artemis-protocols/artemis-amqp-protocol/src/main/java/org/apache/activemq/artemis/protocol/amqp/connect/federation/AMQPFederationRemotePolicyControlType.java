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
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.logs.AuditLogger;

/**
 * Management service control instance for an AMQPFederation policy manager instance that federates messages from this
 * broker to the opposing side of the broker connection. These can be created either for a local broker connection that
 * has bi-directional federation configured, or as a view of a remote broker connection pulling messages from the
 * target.
 */
public class AMQPFederationRemotePolicyControlType extends AbstractControl implements AMQPFederationRemotePolicyControl {

   private final AMQPFederationRemotePolicyManager manager;

   public AMQPFederationRemotePolicyControlType(ActiveMQServer server, AMQPFederationRemotePolicyManager manager) throws NotCompliantMBeanException {
      super(AMQPFederationRemotePolicyControl.class, server.getStorageManager());

      this.manager = manager;
   }

   @Override
   public String getType() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getType(manager);
      }
      clearIO();
      try {
         return manager.getPolicyType().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getName(manager);
      }
      clearIO();
      try {
         return manager.getPolicyName();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getMessagesSent() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMessagesSent(manager);
      }
      clearIO();
      try {
         return manager.getMetrics().getMessagesSent();
      } finally {
         blockOnIO();
      }
   }

   @Override
   protected MBeanOperationInfo[] fillMBeanOperationInfo() {
      return MBeanInfoHelper.getMBeanOperationsInfo(AMQPFederationRemotePolicyControl.class);
   }

   @Override
   protected MBeanAttributeInfo[] fillMBeanAttributeInfo() {
      return MBeanInfoHelper.getMBeanAttributesInfo(AMQPFederationRemotePolicyControl.class);
   }
}
