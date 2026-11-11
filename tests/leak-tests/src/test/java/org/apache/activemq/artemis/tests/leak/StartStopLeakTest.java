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
package org.apache.activemq.artemis.tests.leak;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import io.github.checkleak.core.CheckLeak;
import org.apache.activemq.artemis.core.paging.impl.PagingManagerImpl;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreFactoryNIO;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.ServerStatus;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AckManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;


/** Making sure the server will not leak after started / stopped */
public class StartStopLeakTest extends ActiveMQTestBase {

   @Test
   public void testAckManagerLeak() throws Throwable {
      CheckLeak checkLeak = new CheckLeak();
      internalTest(checkLeak);

      clearServers();

      MemoryAssertions.assertMemory(checkLeak, 0, ActiveMQServerImpl.class.getName());
   }

   // Creating a sub method to facilitate clearing references towards ActiveMQServerImpl
   private void internalTest(CheckLeak checkLeak) throws Exception {
      assertNull(ServerStatus.getServer(), () -> "A previous test left a server hanging on ServerStatus -> " + ServerStatus.getServer());

      for (int i = 0; i < 5; i++) {
         clearServers();
         ActiveMQServer server = createServer(false, true);
         server.start();
         MemoryAssertions.assertMemory(checkLeak, 1, AckManager.class.getName());

         assertSame(server, ServerStatus.getServer());

         server.stop(false);
         assertEquals(0, server.getExternalComponents().size());

         assertNull(ServerStatus.getServer());
         MemoryAssertions.assertMemory(checkLeak, 1, PostOfficeImpl.class.getName());
      }

      MemoryAssertions.basicMemoryAsserts();

      clearServers();

      MemoryAssertions.assertMemory(checkLeak, 0, PostOfficeImpl.class.getName());
      MemoryAssertions.assertMemory(checkLeak, 0, AckManager.class.getName());
   }

   @Test
   public void testPagingManagerLeak() throws Throwable {
      CheckLeak checkLeak = new CheckLeak();
      HierarchicalRepository<AddressSettings> addressSettings = new HierarchicalObjectRepository<>();
      OrderedExecutorFactory orderedExecutorFactory = getOrderedExecutor();
      final StorageManager storageManager = new NullStorageManager().setContextSupplier(() -> OperationContextImpl.getContext(orderedExecutorFactory));
      ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
      runAfter(scheduledExecutorService::shutdownNow);

      for (int i = 0; i < 10; i++) {
         PagingStoreFactoryNIO storeFactory = new PagingStoreFactoryNIO(storageManager, getPageDirFile(), 100, scheduledExecutorService, orderedExecutorFactory, true, null);
         PagingManagerImpl managerImpl = new PagingManagerImpl(storeFactory, addressSettings, -1, -1, null, null);
         managerImpl.start();
         managerImpl.stop();
      }

      MemoryAssertions.assertMemory(checkLeak, 0, PagingManagerImpl.class.getName());
   }

   @Test
   public void testPagingManagerLeakThroughServer() throws Throwable {
      CheckLeak checkLeak = new CheckLeak();
      internalTestPagingManagerLeakThroughServer(checkLeak);
      clearServers();
      MemoryAssertions.assertMemory(checkLeak, 0, PagingManagerImpl.class.getName());
   }

   private void internalTestPagingManagerLeakThroughServer(CheckLeak checkLeak) throws Exception {
      // Start stop many different servers.
      for (int i = 0; i < 5; i++) {
         clearServers();
         ActiveMQServer server = createServer(true, true);
         server.start();
         server.stop(false);
      }
      MemoryAssertions.assertMemory(checkLeak, 1, PagingManagerImpl.class.getName());

      // Start stop same server
      clearServers();
      ActiveMQServer server = createServer(true, true);
      for (int i = 0; i < 5; i++) {
         server.start();
         server.stop(false);
      }
      MemoryAssertions.assertMemory(checkLeak, 1, PagingManagerImpl.class.getName());
   }

   @Test
   public void testPagingManagerLeakOnServerReinitialization() throws Throwable {
      CheckLeak checkLeak = new CheckLeak();
      // creating a sub method to make sure the method reference is gone from the JVM.
      internalPagingManagerLeak(checkLeak);
      clearServers();
      MemoryAssertions.assertMemory(checkLeak, 0, PagingManagerImpl.class.getName());
      MemoryAssertions.assertMemory(checkLeak, 0, ActiveMQServerImpl.class.getName());
   }

   private void internalPagingManagerLeak(CheckLeak checkLeak) throws Exception {
      ActiveMQServerImpl server = (ActiveMQServerImpl) createServer(true, true);

      for (int i = 0; i < 3; i++) {
         server.start();
         server.stop(false);
      }
      MemoryAssertions.assertMemory(checkLeak, 1, PagingManagerImpl.class.getName());
   }
}