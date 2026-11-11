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
package org.apache.activemq.artemis.tests.unit.core.server.impl;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Set;

import org.apache.activemq.artemis.core.config.ha.ReplicationPrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.impl.FileLockNodeManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.UUID;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileLockTest extends ActiveMQTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      File file = new File(getTestDir());
      file.mkdirs();
   }

   @Test
   public void testSetNodeID() throws Exception {
      FileLockNodeManager underTest = new FileLockNodeManager(getTestDirfile(), false);
      ReplicationPrimaryPolicyConfiguration replicationPrimaryPolicyConfiguration = ReplicationPrimaryPolicyConfiguration.withDefault();
      String seed = "";
      for (int i = 0; i < 20; i++) {
         replicationPrimaryPolicyConfiguration.setCoordinationId(seed);
         if (replicationPrimaryPolicyConfiguration.getCoordinationId() != null) {
            underTest.setNodeID(replicationPrimaryPolicyConfiguration.getCoordinationId());
         }
         seed += String.valueOf(i);
      }

      replicationPrimaryPolicyConfiguration.setCoordinationId("somme-dash-and-odd");
      if (replicationPrimaryPolicyConfiguration.getCoordinationId() != null) {
         underTest.setNodeID(replicationPrimaryPolicyConfiguration.getCoordinationId());
      }
   }

   @Test
   public void testNodeManagerStartPersistence() throws Exception {
      final File managerDirectory = getTestDirfile();
      FileLockNodeManager manager = new FileLockNodeManager(managerDirectory, false);
      manager.start();
      Set<File> files = Arrays.stream(managerDirectory.listFiles(pathname -> pathname.isFile())).collect(toSet());
      final Set<String> expectedFileNames = Arrays.stream(new String[]{FileLockNodeManager.SERVER_LOCK_NAME, "serverlock.1", "serverlock.2"})
         .collect(toSet());
      assertEquals(expectedFileNames, files.stream().map(File::getName).collect(toSet()));
      final File nodeIdFile = files.stream().filter(file -> file.getName().equals(FileLockNodeManager.SERVER_LOCK_NAME)).findFirst().get();
      final byte[] encodedNodeId = manager.getUUID().asBytes();
      try (FileChannel serverLock = FileChannel.open(nodeIdFile.toPath(), StandardOpenOption.READ)) {
         assertEquals(16, encodedNodeId.length);
         assertEquals(19, serverLock.size());
         final ByteBuffer readNodeId = ByteBuffer.allocate(16);
         serverLock.read(readNodeId, 3);
         readNodeId.flip();
         assertArrayEquals(encodedNodeId, readNodeId.array());
      }
      assertEquals(NodeManager.NULL_NODE_ACTIVATION_SEQUENCE, manager.getNodeActivationSequence());
      assertEquals(NodeManager.NULL_NODE_ACTIVATION_SEQUENCE, manager.readNodeActivationSequence());
      assertEquals(3, managerDirectory.listFiles(pathname -> pathname.isFile()).length);
      manager.stop();
   }

   @Test
   public void testReplicateBackupNodeManagerStartPersistence() throws Exception {
      final File managerDirectory = getTestDirfile();
      FileLockNodeManager manager = new FileLockNodeManager(managerDirectory, true);
      manager.start();
      Set<File> files = Arrays.stream(managerDirectory.listFiles(pathname -> pathname.isFile())).collect(toSet());
      assertTrue(files.isEmpty());
      assertNull(manager.getNodeId());
      assertNull(manager.getUUID());
      assertEquals(NodeManager.NULL_NODE_ACTIVATION_SEQUENCE, manager.getNodeActivationSequence());
      assertEquals(NodeManager.NULL_NODE_ACTIVATION_SEQUENCE, manager.readNodeActivationSequence());
      assertEquals(0, managerDirectory.listFiles(pathname -> pathname.isFile()).length);
      manager.stop();
   }

   @Test
   public void testReplicatedStopBackupPersistence() throws Exception {
      final FileLockNodeManager manager = new FileLockNodeManager(getTestDirfile(), false);
      manager.start();
      assertNotNull(manager.getUUID());
      manager.writeNodeActivationSequence(1);
      final long nodeActivationSequence = manager.getNodeActivationSequence();
      assertEquals(1, nodeActivationSequence);
      manager.stop();
      // replicated manager read activation sequence (if any) but ignore NodeId
      final FileLockNodeManager replicatedManager = new FileLockNodeManager(getTestDirfile(), true);
      replicatedManager.start();
      assertNull(replicatedManager.getUUID());
      assertEquals(1, replicatedManager.getNodeActivationSequence());
      UUID storedNodeId = UUIDGenerator.getInstance().generateUUID();
      replicatedManager.setNodeID(storedNodeId.toString());
      replicatedManager.setNodeActivationSequence(2);
      replicatedManager.stopBackup();
      replicatedManager.setNodeID(UUIDGenerator.getInstance().generateStringUUID());
      replicatedManager.setNodeActivationSequence(3);
      replicatedManager.stop();
      // start read whatever has been persisted by stopBackup
      manager.start();
      assertEquals(storedNodeId, manager.getUUID());
      assertEquals(2, manager.getNodeActivationSequence());
      manager.stop();
   }

   @Test
   public void testWriteNodeActivationSequence() throws Exception {
      final FileLockNodeManager manager = new FileLockNodeManager(getTestDirfile(), false);
      manager.start();
      UUID id = manager.getUUID();
      assertNotNull(manager.getUUID());
      manager.writeNodeActivationSequence(1);
      final long nodeActivationSequence = manager.getNodeActivationSequence();
      assertEquals(1, nodeActivationSequence);
      manager.stop();
      final FileLockNodeManager otherManager = new FileLockNodeManager(getTestDirfile(), false);
      otherManager.start();
      assertEquals(id, otherManager.getUUID());
      assertEquals(1, otherManager.getNodeActivationSequence());
      otherManager.stop();
   }

   @Test
   public void testNIOLock() throws Exception {
      doTestLock(new FileLockNodeManager(getTestDirfile(), false), new FileLockNodeManager(getTestDirfile(), false));

   }

   public void doTestLock(final FileLockNodeManager lockManager1,
                          final FileLockNodeManager lockManager2) throws Exception {
      lockManager1.start();
      lockManager2.start();

      lockManager1.startPrimaryNode();

      Thread t = new Thread(() -> {
         try {
            lockManager2.startPrimaryNode();
         } catch (Exception e) {
            e.printStackTrace();
         }
      });

      t.start();

      assertTrue(lockManager1.isPrimaryLocked());
      Thread.sleep(500);
      assertFalse(lockManager2.isPrimaryLocked());

      lockManager1.crashPrimaryServer();

      t.join();

      assertFalse(lockManager1.isPrimaryLocked());
      assertTrue(lockManager2.isPrimaryLocked());

      lockManager2.crashPrimaryServer();

      lockManager1.stop();
      lockManager2.stop();

   }

}
