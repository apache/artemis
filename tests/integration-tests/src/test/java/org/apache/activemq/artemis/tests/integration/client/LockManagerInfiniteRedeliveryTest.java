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
package org.apache.activemq.artemis.tests.integration.client;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.ha.DistributedLockManagerConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationBackupPolicyConfiguration;
import org.apache.activemq.artemis.lockmanager.file.FileBasedLockManager;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.util.ReplicatedBackupUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

// Parameters set by super class
@ExtendWith(ParameterizedTestExtension.class)
public class LockManagerInfiniteRedeliveryTest extends InfiniteRedeliveryTest {

   private DistributedLockManagerConfiguration managerConfiguration;

   public LockManagerInfiniteRedeliveryTest(String protocol, boolean useCLI) {
      super(protocol, useCLI);
   }

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
      this.managerConfiguration = new DistributedLockManagerConfiguration(FileBasedLockManager.class.getName(),
                                                                          Collections.singletonMap("locks-folder", newFolder(temporaryFolder, "manager").toString()));
   }

   @Override
   protected void configureReplicationPair(TransportConfiguration backupConnector,
                                           TransportConfiguration backupAcceptor,
                                           TransportConfiguration primaryConnector) {

      ReplicatedBackupUtils.configurePluggableQuorumReplicationPair(backupConfig, backupConnector, backupAcceptor, primaryConfig, primaryConnector, null,
                                                                    managerConfiguration, managerConfiguration);
      ((ReplicationBackupPolicyConfiguration) backupConfig.getHAPolicyConfiguration())
         .setMaxSavedReplicatedJournalsSize(-1).setAllowFailBack(true);
   }

   private static File newFolder(File root, String subFolder) throws IOException {
      File result = new File(root, subFolder);
      if (!result.mkdirs()) {
         throw new IOException("Couldn't create folders " + root);
      }
      return result;
   }
}
