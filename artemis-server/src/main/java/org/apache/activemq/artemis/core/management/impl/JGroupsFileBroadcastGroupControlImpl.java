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
import org.apache.activemq.artemis.api.core.JGroupsFileBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.management.JGroupsFileBroadcastGroupControl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.cluster.BroadcastGroup;
import org.apache.activemq.artemis.logs.AuditLogger;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

public class JGroupsFileBroadcastGroupControlImpl extends BaseBroadcastGroupControlImpl implements JGroupsFileBroadcastGroupControl {



   private JGroupsFileBroadcastEndpointFactory endpointFactory;


   public JGroupsFileBroadcastGroupControlImpl(final BroadcastGroup broadcastGroup,
                                               final StorageManager storageManager,
                                               final BroadcastGroupConfiguration configuration,
                                               final JGroupsFileBroadcastEndpointFactory endpointFactory) throws Exception {
      super(JGroupsFileBroadcastGroupControl.class, broadcastGroup, storageManager, configuration);
      this.endpointFactory = endpointFactory;
   }

   // BroadcastGroupControlMBean implementation ---------------------

   @Override
   public String getFileContents() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getFileContents(this.getBroadcastGroup());
      }
      URL resource = this.getClass().getClassLoader().getResource(this.getFile());
      File file = new File(resource.getFile());
      return new String(Files.readAllBytes(Paths.get(file.getPath())));
   }

   @Override
   public String getChannelName() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getChannelName(this.getBroadcastGroup());
      }
      return endpointFactory.getChannelName();
   }

   @Override
   public String getFile() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getFile(this.getBroadcastGroup());
      }
      return endpointFactory.getFile();
   }


}
