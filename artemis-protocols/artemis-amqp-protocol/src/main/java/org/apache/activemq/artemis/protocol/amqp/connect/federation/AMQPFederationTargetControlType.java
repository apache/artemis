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
 * Management service control instance for an AMQPFederationTarget instance that is the target of an AMQP broker
 * connection with federation configured. The target can behave much the same as a federation source but its scoped to
 * the connection and all operations cease as soon as the connection is closed.
 */
public final class AMQPFederationTargetControlType extends AbstractControl implements AMQPFederationControl {

   private final AMQPFederationTarget federation;

   public AMQPFederationTargetControlType(ActiveMQServer server, AMQPFederationTarget federation) throws NotCompliantMBeanException {
      super(AMQPFederationControl.class, server.getStorageManager());

      this.federation = federation;
   }

   @Override
   public String getName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getName(federation);
      }
      clearIO();
      try {
         return federation.getName();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getMessagesReceived() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMessagesReceived(federation);
      }
      clearIO();
      try {
         return federation.getMetrics().getMessagesReceived();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getMessagesSent() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMessagesSent(federation);
      }
      clearIO();
      try {
         return federation.getMetrics().getMessagesSent();
      } finally {
         blockOnIO();
      }
   }

   @Override
   protected MBeanOperationInfo[] fillMBeanOperationInfo() {
      return MBeanInfoHelper.getMBeanOperationsInfo(AMQPFederationControl.class);
   }

   @Override
   protected MBeanAttributeInfo[] fillMBeanAttributeInfo() {
      return MBeanInfoHelper.getMBeanAttributesInfo(AMQPFederationControl.class);
   }
}
