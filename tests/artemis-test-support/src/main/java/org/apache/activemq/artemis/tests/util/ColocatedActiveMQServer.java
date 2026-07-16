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
package org.apache.activemq.artemis.tests.util;

import javax.management.MBeanServer;
import java.io.File;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.FileLockNodeManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;

public class ColocatedActiveMQServer extends ActiveMQServerImpl {

   private final NodeManager nodeManagerPrimary;
   private final NodeManager nodeManagerBackup;
   boolean backup = false;
   public ColocatedActiveMQServer backupServer;

   public ColocatedActiveMQServer(FileConfiguration fc,
                                  ActiveMQSecurityManager sm,
                                  NodeManager nodeManagerPrimary,
                                  NodeManager nodeManagerBackup) {
      super(fc, sm);
      this.nodeManagerPrimary = nodeManagerPrimary;
      this.nodeManagerBackup = nodeManagerBackup;
   }

   public ColocatedActiveMQServer(Configuration backupServerConfiguration,
                                  ActiveMQServer parentServer,
                                  NodeManager nodeManagerBackup,
                                  NodeManager nodeManagerPrimary) {
      super(backupServerConfiguration, null, null, parentServer);
      this.nodeManagerPrimary = nodeManagerPrimary;
      this.nodeManagerBackup = nodeManagerBackup;
   }

   public ColocatedActiveMQServer(Configuration configuration,
                                  MBeanServer platformMBeanServer,
                                  ActiveMQSecurityManager securityManager,
                                  NodeManager nodeManagerPrimary,
                                  NodeManager nodeManagerBackup) {
      super(configuration, platformMBeanServer, securityManager);
      this.nodeManagerPrimary = nodeManagerPrimary;
      this.nodeManagerBackup = nodeManagerBackup;
   }

   @Override
   protected NodeManager createNodeManager(final File directory, boolean replicatingBackup) {
      if (replicatingBackup) {
         return new FileLockNodeManager(directory, replicatingBackup, getConfiguration(), null);
      } else {
         if (backup) {
            return nodeManagerBackup;
         } else {
            return nodeManagerPrimary;
         }
      }
   }

   @Override
   public ActiveMQServer createBackupServer(Configuration configuration) {
      ColocatedActiveMQServer backup = new ColocatedActiveMQServer(configuration, this, nodeManagerBackup, nodeManagerPrimary);
      backup.backup = true;
      this.backupServer = backup;
      return backup;
   }
}
