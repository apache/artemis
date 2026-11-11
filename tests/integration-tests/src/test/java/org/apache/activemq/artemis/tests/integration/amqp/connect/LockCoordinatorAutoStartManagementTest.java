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
package org.apache.activemq.artemis.tests.integration.amqp.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.LockCoordinatorConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.lock.LockCoordinator;
import org.apache.activemq.artemis.lockmanager.file.FileBasedLockManager;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A lock coordinator configured with {@code auto-start="false"} must stay stopped after the server activates, and
 * the elements bound to it (in this test, an acceptor) must stay closed, until it's started explicitly through
 * management. This also exercises {@code startLockCoordinator}/{@code stopLockCoordinator}, including a start
 * following a previous stop, an already-started coordinator being a no-op, and an unknown name being rejected.
 */
public class LockCoordinatorAutoStartManagementTest extends ActiveMQTestBase {

   private static final String LOCK_NAME = "theLock";
   private static final String ACCEPTOR_NAME = "locked";
   private static final int ACCEPTOR_PORT = 61617;

   private ActiveMQServer server;

   @AfterEach
   public void stopServer() throws Exception {
      if (server != null) {
         server.stop();
      }
   }

   @Test
   public void testAutoStartFalseIsControllableThroughManagement() throws Exception {
      Configuration config = createDefaultConfig(false);
      config.clearAcceptorConfigurations();

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.PORT_PROP_NAME, ACCEPTOR_PORT);
      TransportConfiguration acceptorConfig = new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params, ACCEPTOR_NAME, new HashMap<>());
      acceptorConfig.setLockCoordinator(LOCK_NAME);
      config.addAcceptorConfiguration(acceptorConfig);

      Map<String, String> lockProperties = new HashMap<>();
      lockProperties.put("locks-folder", getTemporaryDir());
      LockCoordinatorConfiguration lockCoordinatorConfiguration = new LockCoordinatorConfiguration(lockProperties)
         .setName(LOCK_NAME).setLockId(LOCK_NAME).setClassName(FileBasedLockManager.class.getName()).setCheckPeriod(100).setAutoStart(false);
      config.addLockCoordinatorConfiguration(lockCoordinatorConfiguration);

      server = addServer(createServer(false, config));
      server.start();
      Wait.assertTrue(server::isActive);

      // auto-start=false: the coordinator must stay stopped after activation, and the acceptor bound to it must
      // stay closed as a consequence.
      LockCoordinator lockCoordinator = server.getLockCoordinator(LOCK_NAME);
      assertNotNull(lockCoordinator, "the coordinator must still be created even with auto-start=false");
      assertEquals("Stopped", lockCoordinator.getStatus());

      Acceptor acceptor = server.getRemotingService().getAcceptor(ACCEPTOR_NAME);
      assertNotNull(acceptor);
      assertFalse(acceptor.isStarted(), "the acceptor must stay closed while its lock coordinator hasn't acquired the lock");

      ActiveMQServerControl serverControl = server.getActiveMQServerControl();

      // an unknown lock coordinator name must be rejected
      assertThrows(IllegalArgumentException.class, () -> serverControl.startLockCoordinator("does-not-exist"));
      assertThrows(IllegalArgumentException.class, () -> serverControl.stopLockCoordinator("does-not-exist"));

      // start it explicitly through management
      serverControl.startLockCoordinator(LOCK_NAME);
      Wait.assertEquals("Locked", lockCoordinator::getStatus, 10_000, 100);
      Wait.assertTrue(acceptor::isStarted, 10_000, 100);

      // calling start again on an already started coordinator is a no-op
      serverControl.startLockCoordinator(LOCK_NAME);
      assertEquals("Locked", lockCoordinator.getStatus());
      assertTrue(acceptor.isStarted());

      // stop it: the lock is released and the acceptor closes again
      serverControl.stopLockCoordinator(LOCK_NAME);
      Wait.assertEquals("Stopped", lockCoordinator::getStatus, 10_000, 100);
      Wait.assertFalse(acceptor::isStarted, 10_000, 100);

      // and a fresh start after a stop must work just as well
      serverControl.startLockCoordinator(LOCK_NAME);
      Wait.assertEquals("Locked", lockCoordinator::getStatus, 10_000, 100);
      Wait.assertTrue(acceptor::isStarted, 10_000, 100);
   }
}
