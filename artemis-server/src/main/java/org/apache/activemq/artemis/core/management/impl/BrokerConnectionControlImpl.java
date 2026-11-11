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

import org.apache.activemq.artemis.api.core.management.BrokerConnectionControl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.BrokerConnection;
import org.apache.activemq.artemis.logs.AuditLogger;

public class BrokerConnectionControlImpl extends AbstractControl implements BrokerConnectionControl {

   private final BrokerConnection brokerConnection;

   public BrokerConnectionControlImpl(BrokerConnection brokerConnection,
                                      StorageManager storageManager) throws NotCompliantMBeanException {
      super(BrokerConnectionControl.class, storageManager);
      this.brokerConnection = brokerConnection;
   }

   @Override
   public boolean isStarted() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isStarted(brokerConnection);
      }
      clearIO();
      try {
         return brokerConnection.isStarted();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isConnected() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isConnected(brokerConnection);
      }
      clearIO();
      try {
         return brokerConnection.isConnected();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void start() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.startBrokerConnection(brokerConnection.getName());
      }
      clearIO();
      try {
         brokerConnection.start();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public void stop() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.stopBrokerConnection(brokerConnection.getName());
      }
      clearIO();
      try {
         brokerConnection.stop();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getName(brokerConnection);
      }
      clearIO();
      try {
         return brokerConnection.getName();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getUri() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getUri(brokerConnection);
      }
      clearIO();
      try {
         return brokerConnection.getConfiguration().getUri();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getUser() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getUser(brokerConnection);
      }
      clearIO();
      try {
         return brokerConnection.getConfiguration().getUser();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getProtocol() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getProtocol(brokerConnection);
      }
      clearIO();
      try {
         return brokerConnection.getProtocol();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getRetryInterval() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRetryInterval(brokerConnection);
      }
      clearIO();
      try {
         return brokerConnection.getConfiguration().getRetryInterval();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getReconnectAttempts() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getReconnectAttempts(brokerConnection);
      }
      clearIO();
      try {
         return brokerConnection.getConfiguration().getReconnectAttempts();
      } finally {
         blockOnIO();
      }
   }

   @Override
   protected MBeanOperationInfo[] fillMBeanOperationInfo() {
      return MBeanInfoHelper.getMBeanOperationsInfo(BrokerConnectionControl.class);
   }

   @Override
   protected MBeanAttributeInfo[] fillMBeanAttributeInfo() {
      return MBeanInfoHelper.getMBeanAttributesInfo(BrokerConnectionControl.class);
   }
}
