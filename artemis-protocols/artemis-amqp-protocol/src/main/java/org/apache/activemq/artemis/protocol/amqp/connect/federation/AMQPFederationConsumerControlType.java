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
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumerInfo;

/**
 * Management object used for AMQP federation address and queue consumers to expose consumer state.
 */
public class AMQPFederationConsumerControlType extends AbstractControl implements AMQPFederationConsumerControl {

   private final AMQPFederationConsumer consumer;
   private final FederationConsumerInfo consumerInfo;

   public AMQPFederationConsumerControlType(AMQPFederationConsumer consumer) throws NotCompliantMBeanException {
      super(AMQPFederationConsumerControl.class, consumer.getFederation().getServer().getStorageManager());

      this.consumer = consumer;
      this.consumerInfo = consumer.getConsumerInfo();
   }

   @Override
   public long getMessagesReceived() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMessagesReceived(this.consumerInfo);
      }
      clearIO();
      try {
         return consumer.getMessagesReceived();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getRole() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRole(this.consumerInfo);
      }
      clearIO();
      try {
         return consumerInfo.getRole().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getQueueName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getQueueName(this.consumerInfo);
      }
      clearIO();
      try {
         return consumerInfo.getQueueName();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getAddress() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAddress(this.consumerInfo);
      }
      clearIO();
      try {
         return consumerInfo.getTargetAddress();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getFqqn() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getFqqn(this.consumerInfo);
      }
      clearIO();
      try {
         return consumerInfo.getTargetFqqn();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getRoutingType() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRoutingType(this.consumerInfo);
      }
      clearIO();
      try {
         return consumerInfo.getRoutingType().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getFilterString() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getFilterString(this.consumerInfo);
      }
      clearIO();
      try {
         return consumerInfo.getFilterString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getPriority() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getPriority(this.consumerInfo);
      }
      clearIO();
      try {
         return consumerInfo.getPriority();
      } finally {
         blockOnIO();
      }
   }

   @Override
   protected MBeanOperationInfo[] fillMBeanOperationInfo() {
      return MBeanInfoHelper.getMBeanOperationsInfo(AMQPFederationConsumerControl.class);
   }

   @Override
   protected MBeanAttributeInfo[] fillMBeanAttributeInfo() {
      return MBeanInfoHelper.getMBeanAttributesInfo(AMQPFederationConsumerControl.class);
   }
}
