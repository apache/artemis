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
import javax.management.NotCompliantMBeanException;

import org.apache.activemq.artemis.api.core.management.RemoteBrokerConnectionControl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.RemoteBrokerConnection;
import org.apache.activemq.artemis.logs.AuditLogger;

public class RemoteBrokerConnectionControlImpl extends AbstractControl implements RemoteBrokerConnectionControl {

   private final RemoteBrokerConnection connection;

   public RemoteBrokerConnectionControlImpl(RemoteBrokerConnection connection,
                                            StorageManager storageManager) throws NotCompliantMBeanException {
      super(RemoteBrokerConnectionControl.class, storageManager);

      this.connection = connection;
   }

   @Override
   public String getName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getName(connection);
      }
      clearIO();
      try {
         return connection.getName();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getNodeId() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getNodeID(connection);
      }
      clearIO();
      try {
         return connection.getNodeId();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getProtocol() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getProtocol(connection);
      }
      clearIO();
      try {
         return connection.getProtocol();
      } finally {
         blockOnIO();
      }
   }

   @Override
   protected MBeanOperationInfo[] fillMBeanOperationInfo() {
      return MBeanInfoHelper.getMBeanOperationsInfo(RemoteBrokerConnectionControl.class);
   }

   @Override
   protected MBeanAttributeInfo[] fillMBeanAttributeInfo() {
      return MBeanInfoHelper.getMBeanAttributesInfo(RemoteBrokerConnectionControl.class);
   }
}
