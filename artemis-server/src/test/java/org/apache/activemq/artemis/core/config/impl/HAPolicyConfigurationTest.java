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
package org.apache.activemq.artemis.core.config.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.config.ha.LiveOnlyPolicyConfiguration;
import org.apache.activemq.artemis.core.server.cluster.ha.ColocatedPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.HAPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.PrimaryOnlyPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicaPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicatedPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicationBackupPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicationPrimaryPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ScaleDownPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.SharedStorePrimaryPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.SharedStoreBackupPolicy;
import org.apache.activemq.artemis.core.server.impl.Activation;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.ColocatedActivation;
import org.apache.activemq.artemis.core.server.impl.FileLockNodeManager;
import org.apache.activemq.artemis.core.server.impl.PrimaryOnlyActivation;
import org.apache.activemq.artemis.core.server.impl.ReplicationBackupActivation;
import org.apache.activemq.artemis.core.server.impl.ReplicationPrimaryActivation;
import org.apache.activemq.artemis.core.server.impl.SharedNothingBackupActivation;
import org.apache.activemq.artemis.core.server.impl.SharedNothingPrimaryActivation;
import org.apache.activemq.artemis.core.server.impl.SharedStoreBackupActivation;
import org.apache.activemq.artemis.core.server.impl.SharedStorePrimaryActivation;
import org.apache.activemq.artemis.lockmanager.DistributedLock;
import org.apache.activemq.artemis.lockmanager.DistributedLockManager;
import org.apache.activemq.artemis.lockmanager.DistributedLockManagerFactory;
import org.apache.activemq.artemis.lockmanager.MutableLong;
import org.apache.activemq.artemis.lockmanager.Registry;
import org.apache.activemq.artemis.lockmanager.UnavailableStateException;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class HAPolicyConfigurationTest extends ServerTestBase {

   @BeforeAll
   public static void register() {
      Registry.getInstance().register(new FakeDistributedLockManagerFactory());
   }

   @AfterAll
   public static void unregister() {
      Registry.getInstance().unregisterWithType("fake");
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      super.tearDown();

      shutdownDerby();
   }

   @Test
   public void shouldNotUseJdbcNodeManagerWithoutHAPolicy() throws Exception {
      Configuration configuration = createConfiguration("database-store-no-hapolicy-config.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      assertEquals(StoreConfiguration.StoreType.DATABASE, server.getConfiguration().getStoreConfiguration().getStoreType());
      assertEquals(HAPolicyConfiguration.TYPE.PRIMARY_ONLY, server.getConfiguration().getHAPolicyConfiguration().getType());
      try {
         server.start();
         assertInstanceOf(FileLockNodeManager.class, server.getNodeManager(), server.getNodeManager() + " is not an instance of FileLockNodeManager");
      } finally {
         server.stop();
      }
   }

   @Test
   public void primaryOnlyTest() throws Exception {
      Configuration configuration = createConfiguration("primary-only-hapolicy-config.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(PrimaryOnlyActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(PrimaryOnlyPolicy.class, haPolicy);
         PrimaryOnlyPolicy primaryOnlyPolicy = (PrimaryOnlyPolicy) haPolicy;
         ScaleDownPolicy scaleDownPolicy = primaryOnlyPolicy.getScaleDownPolicy();
         assertNotNull(scaleDownPolicy);
         assertEquals("boo!", scaleDownPolicy.getGroupName());
         assertEquals("wahey", scaleDownPolicy.getDiscoveryGroup());
         List<String> connectors = scaleDownPolicy.getConnectors();
         assertNotNull(connectors);
         assertEquals(0, connectors.size());
      } finally {
         server.stop();
      }
   }

   @Test
   public void primaryOnlyTest2() throws Exception {
      Configuration configuration = createConfiguration("primary-only-hapolicy-config2.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(PrimaryOnlyActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(PrimaryOnlyPolicy.class, haPolicy);
         PrimaryOnlyPolicy primaryOnlyPolicy = (PrimaryOnlyPolicy) haPolicy;
         ScaleDownPolicy scaleDownPolicy = primaryOnlyPolicy.getScaleDownPolicy();
         assertNotNull(scaleDownPolicy);
         assertFalse(scaleDownPolicy.isEnabled());
         assertEquals("boo!", scaleDownPolicy.getGroupName());
         assertNull(scaleDownPolicy.getDiscoveryGroup());
         List<String> connectors = scaleDownPolicy.getConnectors();
         assertNotNull(connectors);
         assertEquals(2, connectors.size());
         assertTrue(connectors.contains("sd-connector1"));
         assertTrue(connectors.contains("sd-connector2"));
      } finally {
         server.stop();
      }
   }

   @Test
   public void primaryOnlyTest3() throws Exception {
      primaryOnlyTest("primary-only-hapolicy-config3.xml");
   }

   @Test
   public void primaryOnlyTest4() throws Exception {
      primaryOnlyTest("primary-only-hapolicy-config4.xml");
   }

   @Test
   public void primaryOnlyTest5() throws Exception {
      primaryOnlyTest("primary-only-hapolicy-config5.xml");
   }

   @Test
   public void liveOnlyTest() throws Exception {
      ActiveMQServerImpl server = new ActiveMQServerImpl(createDefaultConfig(0, true));
      server.getConfiguration().setHAPolicyConfiguration(new LiveOnlyPolicyConfiguration());
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(PrimaryOnlyActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(PrimaryOnlyPolicy.class, haPolicy);
      } finally {
         server.stop();
      }
   }

   public static class FakeDistributedLockManagerFactory implements DistributedLockManagerFactory {

      @Override
      public DistributedLockManager build(Map<String, String> properties) {
         return new FakeDistributedLockManager(properties);
      }

      @Override
      public String getName() {
         return "fake";
      }

      @Override
      public String getImplName() {
         return FakeDistributedLockManager.class.getName();
      }

      @Override
      public Set<String> getValidParametersList() {
         return Set.of();
      }
   }

   public static class FakeDistributedLockManager implements DistributedLockManager {

      private final Map<String, String> config;
      private boolean started;
      private DistributedLock lock;

      public FakeDistributedLockManager(Map<String, String> config) {
         this.config = config;
         this.started = false;
      }

      public Map<String, String> getConfig() {
         return config;
      }

      @Override
      public void addUnavailableManagerListener(UnavailableManagerListener listener) {
         // no op
      }

      @Override
      public void removeUnavailableManagerListener(UnavailableManagerListener listener) {
         // no op
      }

      @Override
      public boolean start(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException {
         started = true;
         return true;
      }

      @Override
      public void start() throws InterruptedException, ExecutionException {
         started = true;
      }

      @Override
      public boolean isStarted() {
         return started;
      }

      @Override
      public void stop() {
         started = false;
         if (lock != null) {
            lock.close();
         }
         lock = null;
      }

      @Override
      public DistributedLock getDistributedLock(String lockId) {
         if (!started) {
            throw new IllegalStateException("need to start first");
         }
         if (lock == null) {
            lock = new DistributedLock() {

               private boolean held;

               @Override
               public String getLockId() {
                  return lockId;
               }

               @Override
               public boolean isHeldByCaller() throws UnavailableStateException {
                  return held;
               }

               @Override
               public boolean tryLock() throws UnavailableStateException, InterruptedException {
                  if (held) {
                     return false;
                  }
                  held = true;
                  return true;
               }

               @Override
               public void unlock() throws UnavailableStateException {
                  held = false;
               }

               @Override
               public void addListener(UnavailableLockListener listener) {

               }

               @Override
               public void removeListener(UnavailableLockListener listener) {

               }

               @Override
               public void close() {
                  held = false;
               }
            };
         } else if (!lock.getLockId().equals(lockId)) {
            throw new IllegalStateException("This shouldn't happen");
         }
         return lock;
      }

      @Override
      public MutableLong getMutableLong(String mutableLongId) {
         // use a lock file - but with a prefix
         return new MutableLong() {

            private long value = 0;

            @Override
            public String getMutableLongId() {
               return mutableLongId;
            }

            @Override
            public long get() {
               return value;
            }

            @Override
            public void set(long value) {
               this.value = value;
            }

            @Override
            public void close() {

            }
         };
      }

      @Override
      public void close() {
         stop();
      }
   }

   private static void validateManagerConfig(Map<String, String> config) {
      assertEquals("127.0.0.1:6666", config.get("connect-string"));
      assertEquals("16000", config.get("session-ms"));
      assertEquals("2000", config.get("connection-ms"));
      assertEquals("2", config.get("retries"));
      assertEquals("2000", config.get("retries-ms"));
      assertEquals("test", config.get("namespace"));
      assertEquals("10", config.get("session-percent"));
      assertEquals(7, config.size());
   }

   @Test
   public void PrimaryReplicationTest() throws Exception {
      Configuration configuration = createConfiguration("primary-hapolicy-config.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(ReplicationPrimaryActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(ReplicationPrimaryPolicy.class, haPolicy);
         ReplicationPrimaryPolicy policy = (ReplicationPrimaryPolicy) haPolicy;
         assertFalse(policy.isAllowAutoFailBack());
         assertEquals(9876, policy.getInitialReplicationSyncTimeout());
         assertFalse(policy.canScaleDown());
         assertFalse(policy.isBackup());
         assertFalse(policy.isSharedStore());
         assertTrue(policy.isWaitForActivation());
         assertEquals("purple", policy.getGroupName());
         assertEquals("purple", policy.getBackupGroupName());
         assertEquals("abcdefg", policy.getClusterName());
         assertFalse(policy.useQuorumManager());
         // check failback companion backup policy
         ReplicationBackupPolicy failbackPolicy = policy.getBackupPolicy();
         assertNotNull(failbackPolicy);
         assertSame(policy, failbackPolicy.getPrimaryPolicy());
         assertEquals(policy.getGroupName(), failbackPolicy.getGroupName());
         assertEquals(policy.getBackupGroupName(), failbackPolicy.getBackupGroupName());
         assertEquals(policy.getClusterName(), failbackPolicy.getClusterName());
         assertEquals(73, failbackPolicy.getMaxSavedReplicatedJournalsSize());
         assertTrue(failbackPolicy.isTryFailback());
         assertTrue(failbackPolicy.isBackup());
         assertFalse(failbackPolicy.isSharedStore());
         assertTrue(failbackPolicy.isWaitForActivation());
         assertFalse(failbackPolicy.useQuorumManager());
         assertEquals(12345, failbackPolicy.getRetryReplicationWait());
         // check scale-down properties
         assertFalse(failbackPolicy.canScaleDown());
         assertNull(failbackPolicy.getScaleDownClustername());
         assertNull(failbackPolicy.getScaleDownGroupName());
         // validate manager
         DistributedLockManager manager = ((ReplicationPrimaryActivation) activation).getDistributedManager();
         assertNotNull(manager);
         assertEquals(FakeDistributedLockManager.class.getName(), manager.getClass().getName());
         assertInstanceOf(FakeDistributedLockManager.class, manager, manager + " is not an instance of FakeDistributedLockManager");
         FakeDistributedLockManager forwardingManager = (FakeDistributedLockManager) manager;
         // validate manager config
         validateManagerConfig(forwardingManager.getConfig());
      } finally {
         server.stop();
      }
   }

   @Test
   public void BackupReplicationTest() throws Exception {
      Configuration configuration = createConfiguration("backup-hapolicy-config.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(ReplicationBackupActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(ReplicationBackupPolicy.class, haPolicy);
         ReplicationBackupPolicy policy = (ReplicationBackupPolicy) haPolicy;
         assertEquals("tiddles", policy.getGroupName());
         assertEquals("tiddles", policy.getBackupGroupName());
         assertEquals("33rrrrr", policy.getClusterName());
         assertEquals(22, policy.getMaxSavedReplicatedJournalsSize());
         assertFalse(policy.isTryFailback());
         assertTrue(policy.isBackup());
         assertFalse(policy.isSharedStore());
         assertTrue(policy.isWaitForActivation());
         assertFalse(policy.useQuorumManager());
         assertEquals(12345, policy.getRetryReplicationWait());
         // check scale-down properties
         assertFalse(policy.canScaleDown());
         assertNull(policy.getScaleDownClustername());
         assertNull(policy.getScaleDownGroupName());
         // check failover companion primary policy
         ReplicationPrimaryPolicy failoverPrimaryPolicy = policy.getPrimaryPolicy();
         assertNotNull(failoverPrimaryPolicy);
         assertSame(policy, failoverPrimaryPolicy.getBackupPolicy());
         assertFalse(failoverPrimaryPolicy.isAllowAutoFailBack());
         assertEquals(9876, failoverPrimaryPolicy.getInitialReplicationSyncTimeout());
         assertFalse(failoverPrimaryPolicy.canScaleDown());
         assertFalse(failoverPrimaryPolicy.isBackup());
         assertFalse(failoverPrimaryPolicy.isSharedStore());
         assertTrue(failoverPrimaryPolicy.isWaitForActivation());
         assertEquals(policy.getGroupName(), failoverPrimaryPolicy.getGroupName());
         assertEquals(policy.getClusterName(), failoverPrimaryPolicy.getClusterName());
         assertEquals(policy.getBackupGroupName(), failoverPrimaryPolicy.getBackupGroupName());
         assertFalse(failoverPrimaryPolicy.useQuorumManager());
         // check scale-down properties
         assertFalse(failoverPrimaryPolicy.canScaleDown());
         assertNull(failoverPrimaryPolicy.getScaleDownClustername());
         assertNull(failoverPrimaryPolicy.getScaleDownGroupName());
         // validate manager
         DistributedLockManager manager = ((ReplicationBackupActivation) activation).getDistributedManager();
         assertNotNull(manager);
         assertEquals(FakeDistributedLockManager.class.getName(), manager.getClass().getName());
         assertInstanceOf(FakeDistributedLockManager.class, manager);
         FakeDistributedLockManager forwardingManager = (FakeDistributedLockManager) manager;
         // validate manager config
         validateManagerConfig(forwardingManager.getConfig());
      } finally {
         server.stop();
      }
   }

   @Test
   public void ReplicatedTest() throws Exception {
      Configuration configuration = createConfiguration("replicated-hapolicy-config.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(SharedNothingPrimaryActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(ReplicatedPolicy.class, haPolicy);
         ReplicatedPolicy replicatedPolicy = (ReplicatedPolicy) haPolicy;
         assertEquals("purple", replicatedPolicy.getGroupName());
         assertTrue(replicatedPolicy.isCheckForPrimaryServer());
         assertEquals("abcdefg", replicatedPolicy.getClusterName());
         assertEquals(9876, replicatedPolicy.getInitialReplicationSyncTimeout());
         assertEquals(12345, replicatedPolicy.getRetryReplicationWait());
         assertEquals(73, replicatedPolicy.getMaxSavedReplicatedJournalsSize());
      } finally {
         server.stop();
      }
   }

   @Test
   public void ReplicaTest() throws Exception {
      Configuration configuration = createConfiguration("replica-hapolicy-config.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(SharedNothingBackupActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(ReplicaPolicy.class, haPolicy);
         ReplicaPolicy replicaPolicy = (ReplicaPolicy) haPolicy;
         assertEquals("tiddles", replicaPolicy.getGroupName());
         assertEquals(22, replicaPolicy.getMaxSavedReplicatedJournalsSize());
         assertEquals("33rrrrr", replicaPolicy.getClusterName());
         assertFalse(replicaPolicy.isRestartBackup());
         assertTrue(replicaPolicy.isAllowFailback());
         assertEquals(9876, replicaPolicy.getInitialReplicationSyncTimeout());
         assertEquals(12345, replicaPolicy.getRetryReplicationWait());
         ScaleDownPolicy scaleDownPolicy = replicaPolicy.getScaleDownPolicy();
         assertNotNull(scaleDownPolicy);
         assertEquals("boo!", scaleDownPolicy.getGroupName());
         assertEquals("wahey", scaleDownPolicy.getDiscoveryGroup());
         List<String> connectors = scaleDownPolicy.getConnectors();
         assertNotNull(connectors);
         assertEquals(0, connectors.size());
      } finally {
         server.stop();
      }
   }

   @Test
   public void ReplicaTest2() throws Exception {
      Configuration configuration = createConfiguration("replica-hapolicy-config2.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(SharedNothingBackupActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(ReplicaPolicy.class, haPolicy);
         ReplicaPolicy replicaPolicy = (ReplicaPolicy) haPolicy;
         assertEquals("tiddles", replicaPolicy.getGroupName());
         assertEquals(22, replicaPolicy.getMaxSavedReplicatedJournalsSize());
         assertEquals("33rrrrr", replicaPolicy.getClusterName());
         assertFalse(replicaPolicy.isRestartBackup());
         ScaleDownPolicy scaleDownPolicy = replicaPolicy.getScaleDownPolicy();
         assertNotNull(scaleDownPolicy);
         assertEquals("boo!", scaleDownPolicy.getGroupName());
         assertNull(scaleDownPolicy.getDiscoveryGroup());
         List<String> connectors = scaleDownPolicy.getConnectors();
         assertNotNull(connectors);
         assertEquals(2, connectors.size());
         assertTrue(connectors.contains("sd-connector1"));
         assertTrue(connectors.contains("sd-connector2"));
      } finally {
         server.stop();
      }
   }

   @Test
   public void ReplicaTest3() throws Exception {
      Configuration configuration = createConfiguration("replica-hapolicy-config3.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(SharedNothingBackupActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(ReplicaPolicy.class, haPolicy);
         ReplicaPolicy replicaPolicy = (ReplicaPolicy) haPolicy;
         assertEquals("tiddles", replicaPolicy.getGroupName());
         assertEquals(22, replicaPolicy.getMaxSavedReplicatedJournalsSize());
         assertEquals("33rrrrr", replicaPolicy.getClusterName());
         assertFalse(replicaPolicy.isRestartBackup());
         ScaleDownPolicy scaleDownPolicy = replicaPolicy.getScaleDownPolicy();
         assertNull(scaleDownPolicy);
      } finally {
         server.stop();
      }
   }

   @Test
   public void SharedStorePrimaryTest() throws Exception {
      Configuration configuration = createConfiguration("shared-store-primary-hapolicy-config.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(SharedStorePrimaryActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(SharedStorePrimaryPolicy.class, haPolicy);
         SharedStorePrimaryPolicy primaryPolicy = (SharedStorePrimaryPolicy) haPolicy;
         assertFalse(primaryPolicy.isFailoverOnServerShutdown());
      } finally {
         server.stop();
      }
   }

   @Test
   public void SharedStoreBackupTest() throws Exception {
      Configuration configuration = createConfiguration("shared-store-backup-hapolicy-config.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(SharedStoreBackupActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(SharedStoreBackupPolicy.class, haPolicy);
         SharedStoreBackupPolicy sharedStoreBackupPolicy = (SharedStoreBackupPolicy) haPolicy;
         assertFalse(sharedStoreBackupPolicy.isFailoverOnServerShutdown());
         assertFalse(sharedStoreBackupPolicy.isRestartBackup());
         ScaleDownPolicy scaleDownPolicy = sharedStoreBackupPolicy.getScaleDownPolicy();
         assertNotNull(scaleDownPolicy);
         assertEquals("boo!", scaleDownPolicy.getGroupName());
         assertEquals("wahey", scaleDownPolicy.getDiscoveryGroup());
         List<String> connectors = scaleDownPolicy.getConnectors();
         assertNotNull(connectors);
         assertEquals(0, connectors.size());
      } finally {
         server.stop();
      }
   }

   @Test
   public void SharedStoreBackupTest2() throws Exception {
      Configuration configuration = createConfiguration("shared-store-backup-hapolicy-config2.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(SharedStoreBackupActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(SharedStoreBackupPolicy.class, haPolicy);
         SharedStoreBackupPolicy sharedStoreBackupPolicy = (SharedStoreBackupPolicy) haPolicy;
         assertTrue(sharedStoreBackupPolicy.isFailoverOnServerShutdown());
         assertTrue(sharedStoreBackupPolicy.isRestartBackup());
         ScaleDownPolicy scaleDownPolicy = sharedStoreBackupPolicy.getScaleDownPolicy();
         assertNotNull(scaleDownPolicy);
         assertEquals("boo!", scaleDownPolicy.getGroupName());
         assertNull(scaleDownPolicy.getDiscoveryGroup());
         List<String> connectors = scaleDownPolicy.getConnectors();
         assertNotNull(connectors);
         assertEquals(2, connectors.size());
         assertTrue(connectors.contains("sd-connector1"));
         assertTrue(connectors.contains("sd-connector2"));
      } finally {
         server.stop();
      }
   }

   @Test
   public void SharedStoreBackupTest3() throws Exception {
      Configuration configuration = createConfiguration("shared-store-backup-hapolicy-config3.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(SharedStoreBackupActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(SharedStoreBackupPolicy.class, haPolicy);
         SharedStoreBackupPolicy sharedStoreBackupPolicy = (SharedStoreBackupPolicy) haPolicy;
         assertTrue(sharedStoreBackupPolicy.isFailoverOnServerShutdown());
         assertTrue(sharedStoreBackupPolicy.isRestartBackup());
         ScaleDownPolicy scaleDownPolicy = sharedStoreBackupPolicy.getScaleDownPolicy();
         assertNull(scaleDownPolicy);
      } finally {
         server.stop();
      }
   }

   @Test
   public void colocatedTest() throws Exception {
      Configuration configuration = createConfiguration("colocated-hapolicy-config.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(ColocatedActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(ColocatedPolicy.class, haPolicy);
         ColocatedPolicy colocatedPolicy = (ColocatedPolicy) haPolicy;
         ReplicatedPolicy primaryPolicy = (ReplicatedPolicy) colocatedPolicy.getPrimaryPolicy();
         assertNotNull(primaryPolicy);

         assertEquals("purple", primaryPolicy.getGroupName());
         assertTrue(primaryPolicy.isCheckForPrimaryServer());
         assertEquals("abcdefg", primaryPolicy.getClusterName());
         ReplicaPolicy backupPolicy = (ReplicaPolicy) colocatedPolicy.getBackupPolicy();
         assertNotNull(backupPolicy);
         assertEquals("tiddles", backupPolicy.getGroupName());
         assertEquals(22, backupPolicy.getMaxSavedReplicatedJournalsSize());
         assertEquals("33rrrrr", backupPolicy.getClusterName());
         assertFalse(backupPolicy.isRestartBackup());
      } finally {
         server.stop();
      }
   }

   @Test
   public void colocatedTestNullBackup() throws Exception {
      Configuration configuration = createConfiguration("colocated-hapolicy-config-null-backup.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(ColocatedActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(ColocatedPolicy.class, haPolicy);
         ColocatedPolicy colocatedPolicy = (ColocatedPolicy) haPolicy;
         ReplicatedPolicy primaryPolicy = (ReplicatedPolicy) colocatedPolicy.getPrimaryPolicy();
         assertNotNull(primaryPolicy);

         assertEquals("purple", primaryPolicy.getGroupName());
         assertEquals(primaryPolicy.getGroupName(), primaryPolicy.getBackupGroupName());
         assertEquals(primaryPolicy.getBackupGroupName(), haPolicy.getBackupGroupName());
         assertTrue(primaryPolicy.isCheckForPrimaryServer());
         assertEquals("abcdefg", primaryPolicy.getClusterName());
         ReplicaPolicy backupPolicy = (ReplicaPolicy) colocatedPolicy.getBackupPolicy();
         assertNotNull(backupPolicy);
      } finally {
         server.stop();
      }
   }

   @Test
   public void colocatedTest2() throws Exception {
      Configuration configuration = createConfiguration("colocated-hapolicy-config2.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(ColocatedActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(ColocatedPolicy.class, haPolicy);
         ColocatedPolicy colocatedPolicy = (ColocatedPolicy) haPolicy;
         SharedStorePrimaryPolicy primaryPolicy = (SharedStorePrimaryPolicy) colocatedPolicy.getPrimaryPolicy();
         assertNotNull(primaryPolicy);

         assertFalse(primaryPolicy.isFailoverOnServerShutdown());
         SharedStoreBackupPolicy backupPolicy = (SharedStoreBackupPolicy) colocatedPolicy.getBackupPolicy();
         assertNotNull(backupPolicy);
         assertFalse(backupPolicy.isFailoverOnServerShutdown());
         assertFalse(backupPolicy.isRestartBackup());
      } finally {
         server.stop();
      }
   }

   @Test
   public void colocatedTest2nullbackup() throws Exception {
      Configuration configuration = createConfiguration("colocated-hapolicy-config2-null-backup.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(ColocatedActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(ColocatedPolicy.class, haPolicy);
         ColocatedPolicy colocatedPolicy = (ColocatedPolicy) haPolicy;
         SharedStorePrimaryPolicy primaryPolicy = (SharedStorePrimaryPolicy) colocatedPolicy.getPrimaryPolicy();
         assertNotNull(primaryPolicy);

         assertFalse(primaryPolicy.isFailoverOnServerShutdown());
         SharedStoreBackupPolicy backupPolicy = (SharedStoreBackupPolicy) colocatedPolicy.getBackupPolicy();
         assertNotNull(backupPolicy);
      } finally {
         server.stop();
      }
   }

   private void primaryOnlyTest(String file) throws Exception {
      Configuration configuration = createConfiguration(file);
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         Activation activation = server.getActivation();
         assertInstanceOf(PrimaryOnlyActivation.class, activation);
         HAPolicy haPolicy = server.getHAPolicy();
         assertInstanceOf(PrimaryOnlyPolicy.class, haPolicy);
         PrimaryOnlyPolicy primaryOnlyPolicy = (PrimaryOnlyPolicy) haPolicy;
         ScaleDownPolicy scaleDownPolicy = primaryOnlyPolicy.getScaleDownPolicy();
         assertNull(scaleDownPolicy);
      } finally {
         server.stop();
      }
   }

   protected Configuration createConfiguration(String fileName) throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager(fileName);
      deploymentManager.addDeployable(fc);

      deploymentManager.readConfiguration();

      // we need this otherwise the data folder will be located under activemq-server and not on the temporary directory
      fc.setPagingDirectory(getTestDir() + "/" + fc.getPagingDirectory());
      fc.setLargeMessagesDirectory(getTestDir() + "/" + fc.getLargeMessagesDirectory());
      fc.setJournalDirectory(getTestDir() + "/" + fc.getJournalDirectory());
      fc.setBindingsDirectory(getTestDir() + "/" + fc.getBindingsDirectory());

      return fc;
   }
}
