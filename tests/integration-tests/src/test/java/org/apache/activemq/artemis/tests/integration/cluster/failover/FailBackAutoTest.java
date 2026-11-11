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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.server.cluster.ha.SharedStoreBackupPolicy;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.tests.util.CountDownSessionFailureListener;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FailBackAutoTest extends FailoverTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int NUM_MESSAGES = 100;
   private ServerLocatorInternal locator;
   private ClientSessionFactoryInternal sf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = getServerLocator();
   }

   @Test
   public void testAutoFailback() throws Exception {
      ((SharedStoreBackupPolicy) backupServer.getServer().getHAPolicy()).setRestartBackup(false);
      createSessionFactory();
      final CountDownLatch latch = new CountDownLatch(1);

      ClientSession session = sendAndConsume(sf, true);

      CountDownSessionFailureListener listener = new CountDownSessionFailureListener(latch, session);

      session.addFailureListener(listener);

      primaryServer.crash();

      assertTrue(latch.await(5, TimeUnit.SECONDS));

      logger.debug("backup (now primary) topology = {}", backupServer.getServer().getClusterManager().getDefaultConnection(null).getTopology().describe());

      logger.debug("Server Crash!!!");

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage message = session.createMessage(true);

      setBody(0, message);

      producer.send(message);

      verifyMessageOnServer(1, 1);

      session.removeFailureListener(listener);

      final CountDownLatch latch2 = new CountDownLatch(1);

      listener = new CountDownSessionFailureListener(latch2, session);

      session.addFailureListener(listener);

      logger.debug("******* starting primary server back");
      primaryServer.start();

      Thread.sleep(1000);

      logger.debug("After failback: {}", locator.getTopology().describe());

      assertTrue(latch2.await(5, TimeUnit.SECONDS));

      message = session.createMessage(true);

      setBody(1, message);

      producer.send(message);

      session.close();

      verifyMessageOnServer(0, 1);

      wrapUpSessionFactory();
   }

   private void verifyMessageOnServer(final int server, final int numberOfMessages) throws Exception {
      ServerLocator backupLocator = createInVMLocator(server);
      ClientSessionFactory factorybkp = addSessionFactory(createSessionFactory(backupLocator));
      ClientSession sessionbkp = factorybkp.createSession(false, false);
      sessionbkp.start();
      ClientConsumer consumerbkp = sessionbkp.createConsumer(ADDRESS);
      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage msg = consumerbkp.receive(1000);
         assertNotNull(msg);
         msg.acknowledge();
         sessionbkp.commit();
      }
      sessionbkp.close();
      factorybkp.close();
      backupLocator.close();
   }

   @Test
   public void testAutoFailbackThenFailover() throws Exception {
      createSessionFactory();
      ClientSession session = sendAndConsume(sf, true);

      CountDownSessionFailureListener listener = new CountDownSessionFailureListener(session);

      session.addFailureListener(listener);

      logger.debug("Crashing primary server...");

      primaryServer.crash(session);

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage message = session.createMessage(true);

      setBody(0, message);

      producer.send(message);

      session.removeFailureListener(listener);

      listener = new CountDownSessionFailureListener(session);

      session.addFailureListener(listener);
      logger.debug("restarting primary node now");
      primaryServer.start();

      assertTrue(listener.getLatch().await(5, TimeUnit.SECONDS), "expected a session failure 1");

      message = session.createMessage(true);

      setBody(1, message);

      producer.send(message);

      session.removeFailureListener(listener);

      listener = new CountDownSessionFailureListener(session);

      session.addFailureListener(listener);

      waitForBackup(sf, 10);

      logger.debug("Crashing primary server again...");

      primaryServer.crash();

      assertTrue(listener.getLatch().await(5, TimeUnit.SECONDS), "expected a session failure 2");

      session.close();

      wrapUpSessionFactory();
   }

   /**
    * Basic fail-back test.
    */
   @Test
   public void testFailBack() throws Exception {
      ((SharedStoreBackupPolicy) backupServer.getServer().getHAPolicy()).setRestartBackup(false);
      createSessionFactory();
      ClientSession session = sendAndConsume(sf, true);

      ClientProducer producer = session.createProducer(ADDRESS);

      sendMessages(session, producer, NUM_MESSAGES);
      session.commit();

      crash(session);

      session.start();
      ClientConsumer consumer = session.createConsumer(ADDRESS);
      receiveMessages(consumer, 0, NUM_MESSAGES, true);
      producer = session.createProducer(ADDRESS);
      sendMessages(session, producer, 2 * NUM_MESSAGES);
      session.commit();
      assertFalse(primaryServer.getServer().getHAPolicy().isBackup(), "must NOT be a backup");
      adaptPrimaryConfigForReplicatedFailBack(primaryServer);

      CountDownSessionFailureListener listener = new CountDownSessionFailureListener(session);
      session.addFailureListener(listener);

      primaryServer.start();

      assertTrue(listener.getLatch().await(5, TimeUnit.SECONDS));
      assertTrue(primaryServer.getServer().waitForActivation(15, TimeUnit.SECONDS), "primary initialized after restart");

      session.start();
      receiveMessages(consumer, 0, NUM_MESSAGES, true);
   }

   private void createSessionFactory() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true) // unnecessary?
         .setReconnectAttempts(15);
      sf = createSessionFactoryAndWaitForTopology(locator, 2);
   }

   private void wrapUpSessionFactory() {
      sf.close();
      assertEquals(0, sf.numSessions());
      assertEquals(0, sf.numConnections());
   }

   @Override
   protected void createConfigs() throws Exception {
      nodeManager = new InVMNodeManager(false);

      TransportConfiguration primaryConnector = getConnectorTransportConfiguration(true);
      TransportConfiguration backupConnector = getConnectorTransportConfiguration(false);

      backupConfig = super.createDefaultInVMConfig().clearAcceptorConfigurations().addAcceptorConfiguration(getAcceptorTransportConfiguration(false)).setHAPolicyConfiguration(new SharedStoreBackupPolicyConfiguration().setRestartBackup(true)).addConnectorConfiguration(primaryConnector.getName(), primaryConnector).addConnectorConfiguration(backupConnector.getName(), backupConnector).addClusterConfiguration(basicClusterConnectionConfig(backupConnector.getName(), primaryConnector.getName()));

      backupServer = createTestableServer(backupConfig);

      primaryConfig = super.createDefaultInVMConfig().clearAcceptorConfigurations().addAcceptorConfiguration(getAcceptorTransportConfiguration(true)).setHAPolicyConfiguration(new SharedStorePrimaryPolicyConfiguration()).addClusterConfiguration(basicClusterConnectionConfig(primaryConnector.getName(), backupConnector.getName())).addConnectorConfiguration(primaryConnector.getName(), primaryConnector).addConnectorConfiguration(backupConnector.getName(), backupConnector);

      primaryServer = createTestableServer(primaryConfig);
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean primary) {
      return TransportConfigurationUtils.getInVMAcceptor(primary);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean primary) {
      return TransportConfigurationUtils.getInVMConnector(primary);
   }
}
