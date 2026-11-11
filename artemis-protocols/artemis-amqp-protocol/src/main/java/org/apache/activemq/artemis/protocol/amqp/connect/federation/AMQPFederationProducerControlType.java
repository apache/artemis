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

import java.util.Objects;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.NotCompliantMBeanException;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.management.impl.AbstractControl;
import org.apache.activemq.artemis.core.management.impl.MBeanInfoHelper;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.utils.CompositeAddress;

/**
 * Management object used for AMQP federation address and queue producer to expose producer state.
 */
public class AMQPFederationProducerControlType extends AbstractControl implements AMQPFederationProducerControl {

   private final AMQPFederationSenderController senderController;

   private final String address;
   private final String queue;
   private final RoutingType routingType;
   private final String fqqn;
   private final String filterString;
   private final int priority;

   public AMQPFederationProducerControlType(AMQPFederationSenderController senderController) throws NotCompliantMBeanException {
      super(AMQPFederationProducerControl.class, senderController.getServer().getStorageManager());

      final ServerConsumer consumer = senderController.getServerConsumer();

      Objects.requireNonNull(consumer, "The provided sender controller must have an associated server consumer");

      this.senderController = senderController;
      this.address = consumer.getQueueAddress().toString();
      this.queue = consumer.getQueueName().toString();
      this.routingType = consumer.getQueueType();
      this.fqqn = CompositeAddress.toFullyQualified(address, queue);
      this.priority = consumer.getPriority();
      this.filterString = Objects.toString(consumer.getFilterString(), null);
   }

   @Override
   public long getMessagesSent() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMessagesSent(senderController);
      }
      clearIO();
      try {
         return senderController.getMessagesSent();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getRole() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRole(senderController);
      }
      clearIO();
      try {
         return senderController.getRole().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getQueueName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getQueueName(senderController);
      }
      clearIO();
      try {
         return queue;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getAddress() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAddress(senderController);
      }
      clearIO();
      try {
         return address;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getFqqn() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getFqqn(senderController);
      }
      clearIO();
      try {
         return fqqn;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getRoutingType() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRoutingType(senderController);
      }
      clearIO();
      try {
         return routingType.toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getFilterString() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getFilterString(senderController);
      }
      clearIO();
      try {
         return filterString;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getPriority() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getPriority(senderController);
      }
      clearIO();
      try {
         return priority;
      } finally {
         blockOnIO();
      }
   }

   @Override
   protected MBeanOperationInfo[] fillMBeanOperationInfo() {
      return MBeanInfoHelper.getMBeanOperationsInfo(AMQPFederationProducerControl.class);
   }

   @Override
   protected MBeanAttributeInfo[] fillMBeanAttributeInfo() {
      return MBeanInfoHelper.getMBeanAttributesInfo(AMQPFederationProducerControl.class);
   }
}
