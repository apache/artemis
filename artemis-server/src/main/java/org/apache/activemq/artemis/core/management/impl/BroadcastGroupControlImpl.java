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

import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.management.BroadcastGroupControl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.cluster.BroadcastGroup;
import org.apache.activemq.artemis.logs.AuditLogger;

public class BroadcastGroupControlImpl extends BaseBroadcastGroupControlImpl implements BroadcastGroupControl {


   private UDPBroadcastEndpointFactory endpointFactory;



   public BroadcastGroupControlImpl(final BroadcastGroup broadcastGroup,
                                    final StorageManager storageManager,
                                    final BroadcastGroupConfiguration configuration,
                                    final UDPBroadcastEndpointFactory endpointFactory) throws Exception {
      super(BroadcastGroupControl.class, broadcastGroup, storageManager, configuration);
      this.endpointFactory = endpointFactory;
   }

   // BroadcastGroupControlMBean implementation ---------------------

   //todo ghoward we should deal with this properly
   @Override
   public String getGroupAddress() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getGroupAddress(this.getBroadcastGroup());
      }
      clearIO();
      try {
         return endpointFactory.getGroupAddress();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getGroupPort() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getGroupPort(this.getBroadcastGroup());
      }
      clearIO();
      try {
         return endpointFactory.getGroupPort();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getLocalBindPort() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getLocalBindPort(this.getBroadcastGroup());
      }
      clearIO();
      try {
         return endpointFactory.getLocalBindPort();
      } finally {
         blockOnIO();
      }
   }

   // MessagingComponentControlMBean implementation -----------------



}
