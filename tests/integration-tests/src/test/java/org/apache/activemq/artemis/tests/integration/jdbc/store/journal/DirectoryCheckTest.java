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
package org.apache.activemq.artemis.tests.integration.jdbc.store.journal;

import java.io.File;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class DirectoryCheckTest extends ActiveMQTestBase {

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      // clear out anything automatically created by the test scaffolding
      File file = new File(getTestDir());
      deleteDirectory(file);
      file.mkdirs();
   }

   @Test
   public void testDirectoryCreationWithoutHA() throws Exception {
      testDirectoryCreation(false);
   }

   @Test
   public void testDirectoryCreationWithHA() throws Exception {
      testDirectoryCreation(true);
   }

   private void testDirectoryCreation(boolean ha) throws Exception {
      Configuration config = createDefaultJDBCConfig(false).setNodeManagerLockDirectory(getTestDir() + File.separator + "node-manager-lock-directory");
      if (ha) {
         config.setHAPolicyConfiguration(new SharedStorePrimaryPolicyConfiguration());
      }
      ActiveMQServer server = createServer(true, config, AddressSettings.DEFAULT_PAGE_SIZE, AddressSettings.DEFAULT_MAX_SIZE_BYTES);
      server.start();
      assertFalse(server.getConfiguration().getJournalLocation().exists());
      assertFalse(server.getConfiguration().getBindingsLocation().exists());
      assertFalse(server.getConfiguration().getPagingLocation().exists());
      assertFalse(server.getConfiguration().getLargeMessagesLocation().exists());

      // this directory will still be created unless we're using JDBC + HA. See ActiveMQServerImpl.createNodeManager().
      assertEquals(!ha, server.getConfiguration().getNodeManagerLockLocation().exists());
   }
}
