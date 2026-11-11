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
import org.apache.activemq.artemis.api.core.ChannelBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.management.JGroupsChannelBroadcastGroupControl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.cluster.BroadcastGroup;
import org.apache.activemq.artemis.logs.AuditLogger;

public class JGroupsChannelBroadcastGroupControlImpl extends BaseBroadcastGroupControlImpl implements JGroupsChannelBroadcastGroupControl {


   private ChannelBroadcastEndpointFactory endpointFactory;

   public JGroupsChannelBroadcastGroupControlImpl(final BroadcastGroup broadcastGroup,
                                                  final StorageManager storageManager,
                                                  final BroadcastGroupConfiguration configuration,
                                                  final ChannelBroadcastEndpointFactory endpointFactory) throws Exception {
      super(JGroupsChannelBroadcastGroupControl.class, broadcastGroup, storageManager, configuration);
      this.endpointFactory = endpointFactory;
   }

   // BroadcastGroupControlMBean implementation ---------------------

   @Override
   public String getChannelName() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getChannelName(this.endpointFactory.getChannelName());
      }
      return endpointFactory.getChannelName();
   }


}
