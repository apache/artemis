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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BackupSyncPagingTest extends BackupSyncJournalTest {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      setNumberOfMessages(100);
   }

   @Override
   protected ActiveMQServer createInVMFailoverServer(final boolean realFiles,
                                                     final Configuration configuration,
                                                     final NodeManager nodeManager,
                                                     int id) {
      Map<String, AddressSettings> conf = new HashMap<>();
      AddressSettings as = new AddressSettings().setMaxSizeBytes(PAGE_MAX).setPageSizeBytes(PAGE_SIZE).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      conf.put(ADDRESS.toString(), as);
      return createInVMFailoverServer(realFiles, configuration, PAGE_SIZE, PAGE_MAX, conf, nodeManager, id);
   }

   @Test
   public void testReplicationWithPageFileComplete() throws Exception {
      // we could get a first page complete easier with this number
      setNumberOfMessages(20);
      createProducerSendSomeMessages();
      backupServer.start();
      waitForRemoteBackup(sessionFactory, BACKUP_WAIT_TIME, false, backupServer.getServer());

      sendMessages(session, producer, getNumberOfMessages());
      session.commit();

      receiveMsgsInRange(0, getNumberOfMessages());

      finishSyncAndFailover();

      receiveMsgsInRange(0, getNumberOfMessages());
      assertNoMoreMessages();
   }

}
