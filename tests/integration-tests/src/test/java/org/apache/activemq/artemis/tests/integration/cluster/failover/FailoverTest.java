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

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryImpl;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.server.cluster.ha.HAPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicaPolicy;
import org.apache.activemq.artemis.core.server.cluster.ha.ReplicationBackupPolicy;
import org.apache.activemq.artemis.core.server.files.FileMoveManager;
import org.apache.activemq.artemis.tests.integration.cluster.util.TestableServer;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FailoverTest extends FailoverTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = getServerLocator();
   }

   @Test
   @Timeout(120)
   public void testNonTransacted() throws Exception {
      createSessionFactory();

      ClientSession session = createSession(sf, true, true);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      sendMessagesSomeDurable(session, producer);

      crash(session);

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      receiveDurableMessages(consumer);

      session.close();

      sf.close();

      assertEquals(0, sf.numSessions());

      assertEquals(0, sf.numConnections());
   }

   protected void waitForBackupConfig(ClientSessionFactoryInternal sf) throws NoSuchFieldException, IllegalAccessException, InterruptedException {
      TransportConfiguration initialBackup = getFieldFromSF(sf, "backupConnectorConfig");
      int cnt = 50;
      while (initialBackup == null && cnt > 0) {
         cnt--;
         Thread.sleep(200);
         initialBackup = getFieldFromSF(sf, "backupConnectorConfig");
      }
   }

   protected void setSFFieldValue(ClientSessionFactoryInternal sf,
                                  String tcName,
                                  Object value) throws NoSuchFieldException, IllegalAccessException {
      Field tcField = ClientSessionFactoryImpl.class.getDeclaredField(tcName);
      tcField.setAccessible(true);
      tcField.set(sf, value);
   }

   protected TransportConfiguration getFieldFromSF(ClientSessionFactoryInternal sf,
                                                   String tcName) throws NoSuchFieldException, IllegalAccessException {
      Field tcField = ClientSessionFactoryImpl.class.getDeclaredField(tcName);
      tcField.setAccessible(true);
      return (TransportConfiguration) tcField.get(sf);
   }

   /**
    * Basic fail-back test.
    */
   @Test
   @Timeout(120)
   public void testFailBack() throws Exception {
      boolean doFailBack = true;
      HAPolicy haPolicy = backupServer.getServer().getHAPolicy();
      if (haPolicy instanceof ReplicaPolicy policy) {
         policy.setMaxSavedReplicatedJournalsSize(1);
      }

      simpleFailover(haPolicy instanceof ReplicaPolicy || haPolicy instanceof ReplicationBackupPolicy, doFailBack);
   }

   @Test
   @Timeout(120)
   public void testSimpleFailover() throws Exception {
      HAPolicy haPolicy = backupServer.getServer().getHAPolicy();

      simpleFailover(haPolicy instanceof ReplicaPolicy || haPolicy instanceof ReplicationBackupPolicy, false);
   }

   @Test
   @Timeout(120)
   public void testWithoutUsingTheBackup() throws Exception {
      createSessionFactory();
      ClientSession session = createSessionAndQueue();

      ClientProducer producer = addClientProducer(session.createProducer(FailoverTestBase.ADDRESS));

      sendMessages(session, producer, NUM_MESSAGES);
      producer.close();
      session.commit();

      backupServer.stop(); // Backup stops!
      backupServer.start();

      waitForRemoteBackupSynchronization(backupServer.getServer());

      session.start();
      ClientConsumer consumer = addClientConsumer(session.createConsumer(FailoverTestBase.ADDRESS));
      receiveMessages(consumer);
      assertNoMoreMessages(consumer);
      consumer.close();
      session.commit();

      session.start();
      producer = addClientProducer(session.createProducer(FailoverTestBase.ADDRESS));
      sendMessages(session, producer, NUM_MESSAGES);
      producer.close();
      session.commit();
      backupServer.stop(); // Backup stops!
      beforeRestart(backupServer);
      backupServer.start();
      waitForRemoteBackupSynchronization(backupServer.getServer());
      backupServer.stop(); // Backup stops!

      primaryServer.stop();
      beforeRestart(primaryServer);
      primaryServer.start();
      primaryServer.getServer().waitForActivation(10, TimeUnit.SECONDS);

      ClientSession session2 = createSession(sf, false, false);
      session2.start();
      ClientConsumer consumer2 = session2.createConsumer(FailoverTestBase.ADDRESS);
      receiveMessages(consumer2, 0, NUM_MESSAGES, true);
      assertNoMoreMessages(consumer2);
      session2.commit();
   }

   private void simpleFailover(boolean isReplicated, boolean doFailBack) throws Exception {
      createSessionFactory();
      ClientSession session = createSessionAndQueue();

      ClientProducer producer = addClientProducer(session.createProducer(FailoverTestBase.ADDRESS));

      sendMessages(session, producer, NUM_MESSAGES);
      producer.close();
      session.commit();
      SimpleString primaryId = primaryServer.getServer().getNodeID();
      crash(session);

      session.start();
      ClientConsumer consumer = addClientConsumer(session.createConsumer(FailoverTestBase.ADDRESS));
      receiveMessages(consumer);
      assertNoMoreMessages(consumer);
      consumer.close();

      producer = addClientProducer(session.createProducer(FailoverTestBase.ADDRESS));
      sendMessages(session, producer, NUM_MESSAGES);
      producer.close();
      session.commit();

      assertEquals(primaryId, backupServer.getServer().getNodeID(), "backup must be running with the same nodeID");
      if (doFailBack) {
         assertFalse(primaryServer.getServer().getHAPolicy().isBackup(), "must NOT be a backup");
         adaptPrimaryConfigForReplicatedFailBack(primaryServer);
         beforeRestart(primaryServer);
         primaryServer.start();
         assertTrue(primaryServer.getServer().waitForActivation(40, TimeUnit.SECONDS), "primary initialized...");
         if (isReplicated) {
            // wait until it switch role again
            Wait.assertTrue(() -> backupServer.getServer().getHAPolicy().isBackup());
            // wait until started
            Wait.assertTrue(backupServer::isStarted);
            // wait until is an in-sync replica
            Wait.assertTrue(backupServer.getServer()::isReplicaSync);
         } else {
            Wait.assertTrue(backupServer::isStarted);
            backupServer.getServer().waitForActivation(5, TimeUnit.SECONDS);
            assertTrue(backupServer.isStarted());
         }
         if (isReplicated) {
            FileMoveManager moveManager = new FileMoveManager(backupServer.getServer().getConfiguration().getJournalLocation(), 0);
            // backup has not had a chance to restart as a backup and cleanup
            Wait.assertTrue(() -> moveManager.getNumberOfFolders() <= 2);
         }
      } else {
         backupServer.stop();
         beforeRestart(backupServer);
         backupServer.start();
         assertTrue(backupServer.getServer().waitForActivation(10, TimeUnit.SECONDS));
      }

      ClientSession session2 = createSession(sf, false, false);
      session2.start();
      ClientConsumer consumer2 = session2.createConsumer(FailoverTestBase.ADDRESS);
      receiveMessages(consumer2, 0, NUM_MESSAGES, true);
      assertNoMoreMessages(consumer2);
      session2.commit();
   }

   protected void assertNoMoreMessages(ClientConsumer consumer) throws ActiveMQException {
      ClientMessage msg = consumer.receiveImmediate();
      assertNull(msg, "there should be no more messages to receive! " + msg);
   }

   protected void createSessionFactory() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setReconnectAttempts(300).setRetryInterval(100);

      sf = createSessionFactoryAndWaitForTopology(locator, 2);
   }

   protected ClientSession createSessionAndQueue() throws Exception {
      ClientSession session = createSession(sf, false, false);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));
      return session;
   }

   protected void sendMessagesSomeDurable(ClientSession session, ClientProducer producer) throws Exception {
      for (int i = 0; i < NUM_MESSAGES; i++) {
         // some are durable, some are not!
         producer.send(createMessage(session, i, isDurable(i)));
      }
   }

   @Test
   @Timeout(120)
   public void testSimpleSendAfterFailoverDurableTemporary() throws Exception {
      doSimpleSendAfterFailover(true, true);
   }

   @Test
   @Timeout(120)
   public void testSimpleSendAfterFailoverNonDurableTemporary() throws Exception {
      doSimpleSendAfterFailover(false, true);
   }

   @Test
   @Timeout(120)
   public void testSimpleSendAfterFailoverDurableNonTemporary() throws Exception {
      doSimpleSendAfterFailover(true, false);
   }

   @Test
   @Timeout(120)
   public void testSimpleSendAfterFailoverNonDurableNonTemporary() throws Exception {
      doSimpleSendAfterFailover(false, false);
   }

   private void doSimpleSendAfterFailover(final boolean durable, final boolean temporary) throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true).setReconnectAttempts(300).setRetryInterval(100);

      sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = createSession(sf, true, true, 0);

      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS).setDurable(durable && !temporary).setTemporary(temporary));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      crash(session);

      sendMessagesSomeDurable(session, producer);

      receiveMessages(consumer);
   }

   protected void beforeRestart(TestableServer primaryServer1) {
      // no-op
   }

   protected void decrementActivationSequenceForForceRestartOf(TestableServer primaryServer) throws Exception {
      // no-op
   }

}
