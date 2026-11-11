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

import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.component.WebServerComponent;
import org.apache.activemq.artemis.core.config.ha.DistributedLockManagerConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.ServiceComponent;
import org.apache.activemq.artemis.dto.AppDTO;
import org.apache.activemq.artemis.dto.BindingDTO;
import org.apache.activemq.artemis.dto.WebServerDTO;
import org.apache.activemq.artemis.lockmanager.MutableLong;
import org.apache.activemq.artemis.lockmanager.file.FileBasedLockManager;
import org.apache.activemq.artemis.tests.integration.cluster.failover.FailoverTest;
import org.apache.activemq.artemis.tests.integration.cluster.util.TestableServer;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LockManagerNettyNoGroupNameReplicatedFailoverTest extends FailoverTest {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected void beforeWaitForRemoteBackupSynchronization() {
   }

   private void waitForSync(ActiveMQServer server) throws Exception {
      Wait.waitFor(server::isReplicaSync);
   }

   /**
    * Default maxSavedReplicatedJournalsSize is 2, this means the backup will fall back to replicated only twice, after
    * this it is stopped permanently.
    */
   @Test
   @Timeout(120)
   public void testReplicatedFailback() throws Exception {
      try {
         beforeWaitForRemoteBackupSynchronization();

         waitForSync(backupServer.getServer());

         createSessionFactory();

         ClientSession session = createSession(sf, true, true);

         session.createQueue(QueueConfiguration.of(ADDRESS));

         crash(session);

         primaryServer.start();

         waitForSync(primaryServer.getServer());

         waitForSync(backupServer.getServer());

         waitForServerToStart(primaryServer.getServer());

         session = createSession(sf, true, true);

         crash(session);

         primaryServer.start();

         waitForSync(primaryServer.getServer());

         waitForSync(backupServer.getServer());

         waitForServerToStart(primaryServer.getServer());

         session = createSession(sf, true, true);

         crash(session);

         primaryServer.start();

         waitForSync(primaryServer.getServer());

         primaryServer.getServer().waitForActivation(5, TimeUnit.SECONDS);

         waitForSync(primaryServer.getServer());

         waitForServerToStart(backupServer.getServer());

         assertTrue(backupServer.getServer().isStarted());

      } finally {
         if (sf != null) {
            sf.close();
         }
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
   public void testReplicatedFailbackBackupFromPrimaryBackToBackup() throws Exception {
      InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8787);
      HttpServer httpServer = HttpServer.create(address, 100);
      httpServer.start();

      try {
         httpServer.createContext("/", t -> {
            String response = "<html><body><b>This is a unit test</b></body></html>";
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
         });
         AppDTO appDTO = new AppDTO();
         appDTO.war = "console.war";
         appDTO.url = "console";
         BindingDTO bindingDTO = new BindingDTO();
         bindingDTO.uri = "http://localhost:0";
         bindingDTO.apps = new ArrayList<>();
         bindingDTO.apps.add(appDTO);
         WebServerDTO wdto = new WebServerDTO();
         wdto.setBindings(Collections.singletonList(bindingDTO));
         wdto.path = "console";
         WebServerComponent webServerComponent = new WebServerComponent();
         webServerComponent.configure(wdto, ".", ".");
         webServerComponent.start();

         backupServer.getServer().getNetworkHealthCheck().parseURIList("http://localhost:8787");
         assertTrue(backupServer.getServer().getNetworkHealthCheck().isStarted());
         backupServer.getServer().addExternalComponent(webServerComponent, false);
         // this is called when backup servers go from primary back to backup
         backupServer.getServer().fail(true);
         assertTrue(backupServer.getServer().getNetworkHealthCheck().isStarted());
         assertTrue(backupServer.getServer().getExternalComponents().get(0).isStarted());
         ((ServiceComponent) (backupServer.getServer().getExternalComponents().get(0))).stop(true);
      } finally {
         httpServer.stop(0);
      }

   }

   @Override
   protected void createConfigs() throws Exception {
      createPluggableReplicatedConfigs();
   }

   @Override
   protected void setupHAPolicyConfiguration() {
      ((ReplicationBackupPolicyConfiguration) backupConfig.getHAPolicyConfiguration())
         .setMaxSavedReplicatedJournalsSize(2)
         .setAllowFailBack(true);
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean primary) {
      return getNettyAcceptorTransportConfiguration(primary);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean primary) {
      return getNettyConnectorTransportConfiguration(primary);
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

   @Override
   protected void decrementActivationSequenceForForceRestartOf(TestableServer primaryServer) throws Exception {
      doDecrementActivationSequenceForForceRestartOf(logger, nodeManager, managerConfiguration);
   }

   public static void doDecrementActivationSequenceForForceRestartOf(Logger log, NodeManager nodeManager, DistributedLockManagerConfiguration DistributedLockManagerConfiguration) throws Exception {
      nodeManager.start();
      long localActivation = nodeManager.readNodeActivationSequence();
      // file based
      FileBasedLockManager fileBasedPrimitiveManager = new FileBasedLockManager(DistributedLockManagerConfiguration.getProperties());
      fileBasedPrimitiveManager.start();
      try {
         MutableLong mutableLong = fileBasedPrimitiveManager.getMutableLong(nodeManager.getNodeId().toString());

         if (!mutableLong.compareAndSet(localActivation + 1, localActivation)) {
            throw new Exception("Failed to decrement coordinated activation sequence to:" + localActivation + ", not +1 : " + mutableLong.get());
         }
         logger.warn("Intentionally decrementing coordinated activation sequence for test, may result is lost data");

      } finally {
         fileBasedPrimitiveManager.stop();
         nodeManager.stop();
      }
   }

}
