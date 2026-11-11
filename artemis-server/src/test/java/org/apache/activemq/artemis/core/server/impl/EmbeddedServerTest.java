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
package org.apache.activemq.artemis.core.server.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.ServiceComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EmbeddedServerTest {

   private static final String SERVER_LOCK_NAME = "server.lock";
   private static final String SERVER_JOURNAL_DIR = "target/data/journal";

   private ActiveMQServer server;
   private Configuration configuration;

   @BeforeEach
   public void setup() {
      configuration = new ConfigurationImpl().setJournalDirectory(SERVER_JOURNAL_DIR).setPersistenceEnabled(false).setSecurityEnabled(false).addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName()));

      server = ActiveMQServers.newActiveMQServer(configuration);
      try {
         server.start();
      } catch (Exception e) {
         fail();
      }
   }

   @AfterEach
   public void teardown() {
      try {
         server.stop();
      } catch (Exception e) {
         // Do Nothing
      }
   }

   @Test
   public void testNoLockFileWithPersistenceFalse() {
      Path journalDir = Paths.get(SERVER_JOURNAL_DIR, SERVER_LOCK_NAME);
      boolean lockExists = Files.exists(journalDir);
      assertFalse(lockExists);
   }

   @Test
   //make sure the correct stop/exit API is called.
   public void testExternalComponentStop() throws Exception {
      FakeExternalComponent normalComponent = new FakeExternalComponent();
      FakeExternalServiceComponent serviceComponent = new FakeExternalServiceComponent();

      server.addExternalComponent(normalComponent, false);
      server.addExternalComponent(serviceComponent, false);

      server.stop(false);
      assertTrue(normalComponent.stopCalled);

      assertTrue(serviceComponent.stopCalled);
      assertFalse(serviceComponent.exitCalled);

      normalComponent.resetFlags();
      serviceComponent.resetFlags();

      server.start();
      server.stop();
      assertTrue(normalComponent.stopCalled);

      assertFalse(serviceComponent.stopCalled);
      assertTrue(serviceComponent.exitCalled);
   }

   public static class FakeExternalComponent implements ActiveMQComponent {

      volatile boolean startCalled;
      volatile boolean stopCalled;

      @Override
      public void start() throws Exception {
         startCalled = true;
      }

      @Override
      public void stop() throws Exception {
         stopCalled = true;
      }

      @Override
      public boolean isStarted() {
         return startCalled;
      }

      public void resetFlags() {
         startCalled = false;
         stopCalled = false;
      }
   }

   public static class FakeExternalServiceComponent extends FakeExternalComponent implements ServiceComponent {

      volatile boolean exitCalled;

      @Override
      public void stop(boolean isShutdown) throws Exception {
         if (isShutdown) {
            exitCalled = true;
         } else {
            stop();
         }
      }

      @Override
      public void resetFlags() {
         super.resetFlags();
         exitCalled = false;
      }
   }
}
