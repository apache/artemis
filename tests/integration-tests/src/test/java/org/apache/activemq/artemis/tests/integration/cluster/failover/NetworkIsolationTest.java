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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class NetworkIsolationTest extends FailoverTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // This address is guaranteed to fail... reserved for documentation https://tools.ietf.org/html/rfc5737
   private static final String badAddress = "203.0.113.1";

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      this.startBackupServer = false;
      super.setUp();
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live) {
      return TransportConfigurationUtils.getNettyAcceptor(live, 1);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live) {
      return TransportConfigurationUtils.getNettyConnector(live, 1);
   }

   @Test
   public void testReactivate() throws Exception {
      primaryServer.getServer().getConfiguration().setNetworkCheckPeriod(100).setNetworkCheckTimeout(200);
      primaryServer.start();

      assertTrue(Wait.waitFor(primaryServer::isActive));

      primaryServer.getServer().getNetworkHealthCheck().addAddress(badAddress);

      Wait.assertFalse(primaryServer::isStarted);

      primaryServer.getServer().getNetworkHealthCheck().clearAddresses();

      assertTrue(Wait.waitFor(primaryServer::isStarted));
   }

   @Test
   public void testDoNotActivateOnIsolation() throws Exception {

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         ServerLocator locator = getServerLocator();

         // this block here is just to validate if ignoring loopback addresses logic is in place
         {
            backupServer.getServer().getNetworkHealthCheck().addAddress("127.0.0.1");

            assertTrue(loggerHandler.findText("AMQ202001"));

            backupServer.getServer().getNetworkHealthCheck().setIgnoreLoopback(true).addAddress("127.0.0.1");

            assertEquals(1, loggerHandler.countText("AMQ202001"));

            backupServer.getServer().getNetworkHealthCheck().clearAddresses();
         }

         backupServer.getServer().getNetworkHealthCheck().addAddress(badAddress);
         backupServer.getServer().start();

         ClientSessionFactory sf = addSessionFactory(locator.createSessionFactory());

         ClientSession session = createSession(sf, false, true, true);

         session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

         assertFalse(backupServer.getServer().getNetworkHealthCheck().check());

         crash(false, true, session);

         for (int i = 0; i < 1000 && !backupServer.isStarted(); i++) {
            Thread.sleep(10);
         }

         assertTrue(backupServer.isStarted());
         assertFalse(backupServer.isActive());

         primaryServer.start();

         for (int i = 0; i < 1000 && getReplicationEndpoint(backupServer.getServer()) != null && !getReplicationEndpoint(backupServer.getServer()).isStarted(); i++) {
            Thread.sleep(10);
         }

         backupServer.getServer().getNetworkHealthCheck().clearAddresses();

         // This will make sure the backup got synchronized after the network was activated again
         assertTrue(getReplicationEndpoint(backupServer.getServer()).isStarted());
      }
   }

   @Test
   public void testPrimaryIsolated() throws Exception {
      backupServer.stop();

      FakeServiceComponent component = new FakeServiceComponent("Component for " + getName());

      primaryServer.getServer().addExternalComponent(component, true);
      primaryServer.getServer().getConfiguration().setNetworkCheckList(badAddress).
         setNetworkCheckPeriod(100).setNetworkCheckTimeout(100);
      ((ActiveMQServerImpl) primaryServer.getServer()).reloadNetworkHealthCheck();

      try {

         assertEquals(100L, primaryServer.getServer().getNetworkHealthCheck().getPeriod());

         primaryServer.getServer().getNetworkHealthCheck().setTimeUnit(TimeUnit.MILLISECONDS);

         assertFalse(primaryServer.getServer().getNetworkHealthCheck().check());

         Wait.assertFalse(primaryServer::isStarted);

         primaryServer.getServer().getNetworkHealthCheck().setIgnoreLoopback(true).addAddress("127.0.0.1");

         Wait.assertTrue(primaryServer::isStarted);

         assertTrue(component.isStarted());
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
         throw e;
      } finally {
         primaryServer.getServer().stop();
         backupServer.getServer().stop();
      }

   }

   @Override
   protected void createConfigs() throws Exception {
      createReplicatedConfigs();
   }

   @Override
   protected void crash(boolean failover, boolean waitFailure, ClientSession... sessions) throws Exception {
      if (sessions.length > 0) {
         for (ClientSession session : sessions) {
            waitForRemoteBackup(session.getSessionFactory(), 5, true, backupServer.getServer());
         }
      } else {
         waitForRemoteBackup(null, 5, true, backupServer.getServer());
      }
      super.crash(failover, waitFailure, sessions);
   }
}
