/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.smoke.lockmanager;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import static org.apache.activemq.artemis.tests.util.Jmx.containsExactNodeIds;
import static org.apache.activemq.artemis.tests.util.Jmx.decodeNetworkTopologyJson;
import static org.apache.activemq.artemis.tests.util.Jmx.primaryOf;
import static org.apache.activemq.artemis.tests.util.Jmx.validateNetworkTopology;
import static org.apache.activemq.artemis.tests.util.Jmx.withBackup;
import static org.apache.activemq.artemis.tests.util.Jmx.withPrimary;
import static org.apache.activemq.artemis.tests.util.Jmx.withMembers;
import static org.apache.activemq.artemis.tests.util.Jmx.withNodes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

//Parameters set in super class
@ExtendWith(ParameterizedTestExtension.class)
public class ZookeeperLockManagerPeerTest extends ZookeeperLockManagerSinglePairTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public ZookeeperLockManagerPeerTest() {
      super();
      // accepting the primary/backup vars to reuse the test, for peers, these are interchangeable as either can take
      // both roles as both wish to be primary but will revert to backup
      primary = new BrokerControl("primary-peer-a", JMX_PORT_PRIMARY, "zkReplicationPrimaryPeerA", PRIMARY_PORT_OFFSET);
      backup = new BrokerControl("primary-peer-b", JMX_PORT_BACKUP, "zkReplicationPrimaryPeerB", BACKUP_PORT_OFFSET);
      brokers = Arrays.asList(primary, backup);
   }

   @Disabled
   @Override
   @TestTemplate
   public void testBackupFailoverAndPrimaryFailback() throws Exception {
      // peers don't request fail back by default
   }

   @TestTemplate
   public void testBackupCannotForgetPeerIdOnLostQuorum() throws Exception {
      // see FileLockTest::testCorrelationId to get more info why this is not peer-journal-001 as in broker.xml
      final String coordinationId = "peer.journal.001";
      final int timeout = (int) TimeUnit.SECONDS.toMillis(30);
      logger.info("starting peer a");
      final Process primary = this.primary.startServer(this, 0);
      logger.info("waiting peer a to increase coordinated activation sequence to 1");
      Wait.assertEquals(1L, () -> this.primary.getActivationSequence().orElse(Long.MAX_VALUE).longValue(), timeout);
      assertEquals(coordinationId, this.primary.getNodeID().get());
      Wait.waitFor(() -> this.primary.listNetworkTopology().isPresent(), timeout);
      final String urlPeerA = primaryOf(coordinationId, decodeNetworkTopologyJson(this.primary.listNetworkTopology().get()));
      assertNotNull(urlPeerA);
      logger.info("peer a acceptor: {}", urlPeerA);
      logger.info("killing peer a");
      ServerUtil.killServer(primary, forceKill);
      logger.info("starting peer b");
      Process emptyBackup = backup.startServer(this, 0);
      logger.info("waiting until peer b act as empty backup");
      Wait.assertTrue(() -> backup.isBackup().orElse(false), timeout);
      logger.info("Stop majority of quorum nodes");
      final int[] majority = stopMajority();
      logger.info("Wait peer b to deactivate");
      Thread.sleep(2000);
      logger.info("Restart majority of quorum nodes");
      restart(majority);
      logger.info("Restart peer a as legit last live");
      final Process restartedPrimary = this.primary.startServer(this, 0);
      logger.info("waiting peer a to increase coordinated activation sequence to 2");
      Wait.assertEquals(2L, () -> this.primary.getActivationSequence().orElse(Long.MAX_VALUE).longValue(), timeout);
      assertEquals(coordinationId, this.primary.getNodeID().get());
      logger.info("waiting peer b to be a replica");
      Wait.waitFor(() -> backup.isReplicaSync().orElse(false));
      Wait.assertEquals(2L, () -> backup.getActivationSequence().get().longValue());
      final String expectedUrlPeerA = primaryOf(coordinationId, decodeNetworkTopologyJson(this.primary.listNetworkTopology().get()));
      assertEquals(urlPeerA, expectedUrlPeerA);
   }

   @TestTemplate
   public void testMultiPrimary_Peer() throws Exception {

      final int timeout = (int) TimeUnit.SECONDS.toMillis(30);
      logger.info("starting peer b primary");

      Process backupInstance = backup.startServer(this, timeout);

      // alive as unreplicated, it has configured node id
      assertTrue(Wait.waitFor(() -> 1L == backup.getActivationSequence().orElse(Long.MAX_VALUE).longValue()));

      final String nodeID = backup.getNodeID().get();
      assertNotNull(nodeID);
      logger.info("NodeID: {}", nodeID);

      logger.info("starting peer a primary");
      primary.startServer(this, 0);
      Wait.assertTrue(() -> primary.isBackup().orElse(false), timeout);

      Wait.assertTrue(() -> !backup.isBackup().orElse(true), timeout);

      for (BrokerControl broker : brokers) {
         Wait.assertTrue(() -> validateNetworkTopology(broker.listNetworkTopology().orElse(""),
                                                       containsExactNodeIds(nodeID)
                                                          .and(withPrimary(nodeID, Objects::nonNull))
                                                          .and(withBackup(nodeID, Objects::nonNull))
                                                          .and(withMembers(1))
                                                          .and(withNodes(2))), timeout);
      }

      logger.info("primary topology is: {}", primary.listNetworkTopology().get());
      logger.info("backup topology is: {}", backup.listNetworkTopology().get());
      assertTrue(backup.isReplicaSync().get());
      assertTrue(primary.isReplicaSync().get());


      logger.info("killing peer-b");
      ServerUtil.killServer(backupInstance, forceKill);

      // peer-a now UNREPLICATED
      Wait.assertTrue(() -> 2L == primary.getActivationSequence().get().longValue());

      logger.info("restarting peer-b");
      backup.startServer(this, 0);

      assertTrue(Wait.waitFor(() -> nodeID.equals(backup.getNodeID().orElse("not set yet"))));
      // peer-b now a REPLICA
      Wait.waitFor(() -> backup.isReplicaSync().get());
      Wait.assertTrue(() -> 2L == backup.getActivationSequence().get().longValue());
   }
}
