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

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.NotCompliantMBeanException;

import org.apache.activemq.artemis.core.management.impl.AbstractControl;
import org.apache.activemq.artemis.core.management.impl.MBeanInfoHelper;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.logs.AuditLogger;

/**
 * Management service control instance for an AMQP bridge manager instance that bridges messages to
 * or from the remote peer on the opposing side of this broker connection. The bridgeManager manager has
 * a lifetime that matches that of its parent broker connection.
 */
public final class AMQPBridgeManagerControlType extends AbstractControl implements AMQPBridgeManagerControl {

   private final AMQPBridgeManager bridge;

   public AMQPBridgeManagerControlType(ActiveMQServer server, AMQPBridgeManager bridge) throws NotCompliantMBeanException {
      super(AMQPBridgeManagerControl.class, server.getStorageManager());

      this.bridge = bridge;
   }

   @Override
   public String getName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getName(bridge);
      }
      clearIO();
      try {
         return bridge.getName();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getMessagesReceived() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMessagesReceived(bridge);
      }
      clearIO();
      try {
         return bridge.getMetrics().getMessagesReceived();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getMessagesSent() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMessagesSent(bridge);
      }
      clearIO();
      try {
         return bridge.getMetrics().getMessagesSent();
      } finally {
         blockOnIO();
      }
   }

   @Override
   protected MBeanOperationInfo[] fillMBeanOperationInfo() {
      return MBeanInfoHelper.getMBeanOperationsInfo(AMQPBridgeManagerControl.class);
   }

   @Override
   protected MBeanAttributeInfo[] fillMBeanAttributeInfo() {
      return MBeanInfoHelper.getMBeanAttributesInfo(AMQPBridgeManagerControl.class);
   }
}
