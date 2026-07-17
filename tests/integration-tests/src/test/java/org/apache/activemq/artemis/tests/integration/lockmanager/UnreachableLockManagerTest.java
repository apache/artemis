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
package org.apache.activemq.artemis.tests.integration.lockmanager;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.LockCoordinatorConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * A lock coordinator unable to reach ZooKeeper must not make the broker unstoppable.
 */
public class UnreachableLockManagerTest extends ActiveMQTestBase {

   private static final String CURATOR_LOCK_MANAGER = "org.apache.activemq.artemis.lockmanager.zookeeper.CuratorDistributedLockManager";
   private static final int CHECK_PERIOD = 100;

   /**
    * The lock coordinator is not referenced by any acceptor or broker connection, still it is started with the
    * broker: with an unreachable ZooKeeper its periodic task used to park forever on the lock manager start,
    * holding the ordered executor the stop is relying on, hence the broker could not be stopped or restarted.
    */
   @Test
   @Timeout(value = 120, unit = TimeUnit.SECONDS)
   public void testStopWithUnreachableZooKeeper() throws Exception {
      final Configuration config = createDefaultInVMConfig();

      final Map<String, String> properties = new HashMap<>();
      // nothing is listening on this port: the ZooKeeper ensemble is unreachable
      properties.put("connect-string", "localhost:" + unusedPort());
      properties.put("namespace", "activemq-artemis");
      properties.put("session-ms", "2000");
      properties.put("connection-ms", "2000");
      final LockCoordinatorConfiguration lockCoordinatorConfiguration = new LockCoordinatorConfiguration(properties);
      lockCoordinatorConfiguration.setName("zk").setClassName(CURATOR_LOCK_MANAGER).setCheckPeriod(CHECK_PERIOD).setLockId("zk");
      config.addLockCoordinatorConfiguration(lockCoordinatorConfiguration);

      // the server is created outside of the test base on purpose: an unstoppable broker would hang the tear down
      final ActiveMQServerImpl server = (ActiveMQServerImpl) ActiveMQServers.newActiveMQServer(config, false);
      server.start();
      Wait.assertTrue(() -> server.getLockCoordinators().size() == 1, 10_000, 100);
      // let the periodic task run and try to connect
      Wait.assertTrue(() -> "Unlocked".equals(server.getLockCoordinator("zk").getStatus()), 10_000, 100);

      final CountDownLatch stopped = new CountDownLatch(1);
      final Thread stopper = new Thread(() -> {
         try {
            server.stop();
         } catch (Exception e) {
            e.printStackTrace();
         } finally {
            stopped.countDown();
         }
      }, "unreachable-lock-manager-server-stopper");
      stopper.start();
      try {
         assertTrue(stopped.await(60, TimeUnit.SECONDS), "the broker cannot be stopped while the lock coordinator cannot reach ZooKeeper");
      } finally {
         stopper.join(TimeUnit.SECONDS.toMillis(10));
      }
   }

   private static int unusedPort() throws IOException {
      try (ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
   }
}
