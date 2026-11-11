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
package org.apache.activemq.artemis.protocol.amqp.connect.bridge;

import java.util.List;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.NotCompliantMBeanException;

import org.apache.activemq.artemis.core.management.impl.AbstractControl;
import org.apache.activemq.artemis.core.management.impl.MBeanInfoHelper;
import org.apache.activemq.artemis.logs.AuditLogger;

/**
 * Management object used for AMQP bridge address and queue receivers to expose receiver state.
 */
public class AMQPBridgeReceiverControlType extends AbstractControl implements AMQPBridgeReceiverControl {

   private final AMQPBridgeReceiver receiver;
   private final AMQPBridgeReceiverInfo receiverInfo;

   public AMQPBridgeReceiverControlType(AMQPBridgeReceiver receiver) throws NotCompliantMBeanException {
      super(AMQPBridgeReceiverControl.class, receiver.getBridgeManager().getServer().getStorageManager());

      this.receiver = receiver;
      this.receiverInfo = receiver.getReceiverInfo();
   }

   @Override
   public long getMessagesReceived() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMessagesReceived(receiverInfo);
      }
      clearIO();
      try {
         return receiver.getMessagesReceived();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getRole() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRole(receiverInfo);
      }
      clearIO();
      try {
         return receiverInfo.getRole().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getQueueName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getQueueName(receiverInfo);
      }
      clearIO();
      try {
         return receiverInfo.getLocalQueue();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getAddress() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAddress(receiverInfo);
      }
      clearIO();
      try {
         return receiverInfo.getLocalAddress();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getFqqn() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getFqqn(receiverInfo);
      }
      clearIO();
      try {
         return receiverInfo.getLocalFqqn();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getRoutingType() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRoutingType(receiverInfo);
      }
      clearIO();
      try {
         return receiverInfo.getRoutingType().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getFilterString() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getFilterString(receiverInfo);
      }
      clearIO();
      try {
         return receiverInfo.getFilterString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getPriority() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getPriority(receiverInfo);
      }
      List.of(new Object[] {"one"});
      clearIO();
      try {
         return receiverInfo.getPriority();
      } finally {
         blockOnIO();
      }
   }

   @Override
   protected MBeanOperationInfo[] fillMBeanOperationInfo() {
      return MBeanInfoHelper.getMBeanOperationsInfo(AMQPBridgeReceiverControl.class);
   }

   @Override
   protected MBeanAttributeInfo[] fillMBeanAttributeInfo() {
      return MBeanInfoHelper.getMBeanAttributesInfo(AMQPBridgeReceiverControl.class);
   }
}
