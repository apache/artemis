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
package org.apache.activemq.artemis.tests.integration.cluster.failover.lockmanager;

import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.config.ha.ReplicationBackupPolicyConfiguration;
import org.apache.activemq.artemis.tests.integration.cluster.util.BackupSyncDelay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class LockManagerReplicatedLargeMessageWithDelayFailoverTest extends LockManagerReplicatedLargeMessageFailoverTest {

   private BackupSyncDelay syncDelay;

   @Override
   protected boolean supportsRetention() {
      return false;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      startBackupServer = false;
      super.setUp();
      syncDelay = new BackupSyncDelay(backupServer, primaryServer);

      /*
       * Using getName() here is a bit of a hack, but if the backup is started for this test then the test will fail
       * intermittently due to an InterruptedException.
       */
      if (!getName().equals("testBackupServerNotRemoved")) {
         backupServer.start();
      }
   }

   @Override
   protected void crash(ClientSession... sessions) throws Exception {
      crash(true, sessions);
   }

   @Override
   protected void crash(boolean waitFailure, ClientSession... sessions) throws Exception {
      syncDelay.deliverUpToDateMsg();
      waitForBackup(null, 30);
      super.crash(waitFailure, sessions);
   }

   @Override
   protected void createConfigs() throws Exception {
      createPluggableReplicatedConfigs();
   }

   @Override
   protected void setupHAPolicyConfiguration() {
      ((ReplicationBackupPolicyConfiguration) backupConfig.getHAPolicyConfiguration())
         .setMaxSavedReplicatedJournalsSize(2).setAllowFailBack(true);
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      syncDelay.deliverUpToDateMsg();
      super.tearDown();
   }

}
