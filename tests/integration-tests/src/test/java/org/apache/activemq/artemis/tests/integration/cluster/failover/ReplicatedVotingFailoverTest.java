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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.tests.extensions.TestMethodNameMatchExtension;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ReplicatedVotingFailoverTest extends FailoverTestBase {

   private static final String TEST_BACKUP_FAILS_VOTE_FAILS = "testBackupFailsVoteFails";

   @RegisterExtension
   TestMethodNameMatchExtension testBackupFailsVoteFails = new TestMethodNameMatchExtension(TEST_BACKUP_FAILS_VOTE_FAILS);

   protected void beforeWaitForRemoteBackupSynchronization() {
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live) {
      return TransportConfigurationUtils.getInVMAcceptor(live);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live) {
      return TransportConfigurationUtils.getInVMConnector(live);
   }

   @Test
   public void testBackupFailsVoteSuccess() throws Exception {
      try {
         beforeWaitForRemoteBackupSynchronization();

         waitForRemoteBackupSynchronization(backupServer.getServer());

         backupServer.stop();

         ServerLocator locator = createInVMLocator(0);
         ClientSessionFactory sessionFactory = locator.createSessionFactory();
         ClientSession session = sessionFactory.createSession();
         addClientSession(session);
         ClientProducer producer = session.createProducer("testAddress");
         producer.send(session.createMessage(true));
         assertTrue(primaryServer.isActive());


      } finally {
         try {
            primaryServer.getServer().stop();
         } catch (Throwable ignored) {
         }
         try {
            backupServer.getServer().stop();
         } catch (Throwable ignored) {
         }
      }
   }

   @Test
   public void testBackupFailsVoteFails() throws Exception {
      try {
         beforeWaitForRemoteBackupSynchronization();

         waitForRemoteBackupSynchronization(backupServer.getServer());

         backupServer.stop();

         try {
            ServerLocator locator = createInVMLocator(0);
            ClientSessionFactory sessionFactory = locator.createSessionFactory();
            ClientSession session = sessionFactory.createSession();
            addClientSession(session);
            ClientProducer producer = session.createProducer("testAddress");
            producer.send(session.createMessage(true));
         } catch (Exception e) {
            //expected
         }
         waitForServerToStop(primaryServer.getServer());
         assertFalse(primaryServer.isStarted());


      } finally {
         try {
            primaryServer.getServer().stop();
         } catch (Throwable ignored) {
         }
         try {
            backupServer.getServer().stop();
         } catch (Throwable ignored) {
         }
      }
   }

   @Override
   protected void createConfigs() throws Exception {
      createReplicatedConfigs();
   }

   @Override
   protected void setupHAPolicyConfiguration() {
      ((ReplicatedPolicyConfiguration) primaryConfig.getHAPolicyConfiguration()).setCheckForActiveServer(true);
      ((ReplicatedPolicyConfiguration) primaryConfig.getHAPolicyConfiguration()).setVoteOnReplicationFailure(true);
      ((ReplicaPolicyConfiguration) backupConfig.getHAPolicyConfiguration()).setMaxSavedReplicatedJournalsSize(2).setAllowFailBack(true);
      ((ReplicaPolicyConfiguration) backupConfig.getHAPolicyConfiguration()).setRestartBackup(false);
      if (testBackupFailsVoteFails.matches()) {
         ((ReplicatedPolicyConfiguration) primaryConfig.getHAPolicyConfiguration()).setQuorumSize(2);
      }
   }

   @Override
   protected void crash(boolean waitFailure, ClientSession... sessions) throws Exception {
      if (sessions.length > 0) {
         for (ClientSession session : sessions) {
            waitForRemoteBackup(session.getSessionFactory(), 5, true, backupServer.getServer());
         }
      } else {
         waitForRemoteBackup(null, 5, true, backupServer.getServer());
      }
      super.crash(waitFailure, sessions);
   }

   @Override
   protected void crash(ClientSession... sessions) throws Exception {
      if (sessions.length > 0) {
         for (ClientSession session : sessions) {
            waitForRemoteBackup(session.getSessionFactory(), 5, true, backupServer.getServer());
         }
      } else {
         waitForRemoteBackup(null, 5, true, backupServer.getServer());
      }
      super.crash(sessions);
   }
}
