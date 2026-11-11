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
package org.apache.activemq.artemis.tests.integration.replication;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.util.collection.LongObjectHashMap;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.DistributedLockManagerConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationPrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.journal.collections.JournalHashMap;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.AckRetry;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.lockmanager.file.FileBasedLockManager;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AckManager;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AckManagerProvider;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RepeatStartBackupTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private DistributedLockManagerConfiguration managerConfiguration;
   ActiveMQServer backupServer;
   ActiveMQServer server;

   File newTemporaryFolder(String name) {
      File newFolder = new File(temporaryFolder, name);
      newFolder.mkdirs();
      return newFolder;
   }

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      managerConfiguration = new DistributedLockManagerConfiguration(FileBasedLockManager.class.getName(), Collections.singletonMap("locks-folder", newTemporaryFolder("manager").toString()));

      // start live
      Configuration liveConfiguration = createLiveConfiguration();

      server = addServer(ActiveMQServers.newActiveMQServer(liveConfiguration));
      server.setIdentity("PRIMARY");
      server.getConfiguration().setJournalFileSize(100 * 1024);

      server.start();

      server.addAddressInfo(new AddressInfo("t1").addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of("t1").setAddress("t1").setRoutingType(RoutingType.ANYCAST));

      // start backup
      Configuration backupConfiguration = createBackupConfiguration();
      ((ReplicationBackupPolicyConfiguration) backupConfiguration.getHAPolicyConfiguration()).setAllowFailBack(true);
      backupServer = addServer(ActiveMQServers.newActiveMQServer(backupConfiguration));
      backupServer.setIdentity("BACKUP");
   }

   private void startBackup(int timeout) throws Exception {
      backupServer.start();

      Wait.waitFor(backupServer::isStarted);

      Wait.assertTrue(() -> backupServer.isReplicaSync(), timeout);
   }

   @Test
   public void testLoopStart() throws Exception {
      startBackup(30_000);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {

         ExecutorService executorService = Executors.newFixedThreadPool(1);
         runAfter(executorService::shutdownNow);

         AtomicInteger errors = new AtomicInteger(0);
         AtomicBoolean running = new AtomicBoolean(true);

         runAfter(() -> running.set(false));
         CountDownLatch latch = new CountDownLatch(1);

         executorService.execute(() -> {
            try {
               ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
               try (Connection connection = factory.createConnection()) {
                  Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                  MessageConsumer consumer = session.createConsumer(session.createQueue("t1"));
                  MessageProducer producer = session.createProducer(session.createQueue("t1"));
                  connection.start();
                  while (running.get()) {
                     producer.send(session.createTextMessage("hello"));
                     assertNotNull(consumer.receive(1000));
                  }
               }
            } catch (Throwable e) {
               logger.warn(e.getMessage(), e);
               errors.incrementAndGet();
            } finally {
               latch.countDown();
            }
         });

         for (int i = 0; i < 5; i++) {
            logger.info("\n*******************************************************************************************************************************\ntest {}\n*******************************************************************************************************************************", i);
            backupServer.stop();
            Wait.assertFalse(backupServer::isStarted);
            backupServer.start();
            Wait.assertTrue(backupServer::isStarted);
            if (i % 2 == 1) {
               Wait.assertTrue(backupServer::isReplicaSync);
            }

            assertFalse(loggerHandler.findText("AMQ229254"));
            assertFalse(loggerHandler.findText("AMQ229006"));
            loggerHandler.clear();
         }

         running.set(false);

         assertTrue(latch.await(10, TimeUnit.SECONDS));

         assertEquals(0, errors.get());
      }
   }

   @Test
   public void testAckManagerRepetition() throws Exception {

      String queueName = "queue_" + RandomUtil.randomUUIDString();

      // some extremely large retry settings
      // just to make sure these records will never be removed
      server.getConfiguration().setMirrorAckManagerQueueAttempts(300000);
      server.getConfiguration().setMirrorAckManagerPageAttempts(300000);
      server.getConfiguration().setMirrorAckManagerRetryDelay(60_000);
      backupServer.getConfiguration().setMirrorAckManagerQueueAttempts(300000);
      backupServer.getConfiguration().setMirrorAckManagerPageAttempts(300000);
      backupServer.getConfiguration().setMirrorAckManagerRetryDelay(60_000);

      ExecutorService executorService = Executors.newFixedThreadPool(2);
      runAfter(executorService::shutdownNow);

      AtomicInteger errors = new AtomicInteger(0);
      AtomicBoolean running = new AtomicBoolean(true);

      runAfter(() -> running.set(false));
      CountDownLatch latch = new CountDownLatch(1);
      CountDownLatch backupStarted = new CountDownLatch(1);

      AtomicInteger recordsSent = new AtomicInteger(0);

      int starBackupAt = 100;
      assertFalse(server.isReplicaSync());
      assertFalse(backupServer.isStarted());

      AckManager liveAckManager = AckManagerProvider.getManager(server);
      server.addAddressInfo(new AddressInfo(queueName).addRoutingType(RoutingType.ANYCAST));
      Queue queueOnServerLive = server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(true));
      long queueIdOnServerLive = queueOnServerLive.getID();

      OperationContextImpl context = new OperationContextImpl(server.getExecutorFactory().getExecutor());

      executorService.execute(() -> {
         try {
            OperationContextImpl.setContext(context);
            while (running.get()) {
               int id = recordsSent.getAndIncrement();
               if (id == starBackupAt) {
                  executorService.execute(() -> {
                     try {
                        backupServer.start();
                     } catch (Throwable e) {
                        logger.warn(e.getMessage(), e);
                     } finally {
                        backupStarted.countDown();
                     }
                  });
               }
               CountDownLatch latchAcked = new CountDownLatch(1);
               liveAckManager.ack(server.getNodeID().toString(), queueOnServerLive, id, AckReason.NORMAL, true);
               OperationContextImpl.getContext().executeOnCompletion(new IOCallback() {
                  @Override
                  public void done() {
                     latchAcked.countDown();
                  }

                  @Override
                  public void onError(int errorCode, String errorMessage) {
                  }
               });
               if (!latchAcked.await(10, TimeUnit.SECONDS)) {
                  logger.warn("Could not wait ack to finish");
               }
               Thread.yield();
            }
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
            errors.incrementAndGet();
         } finally {
            latch.countDown();
         }
      });

      assertTrue(backupStarted.await(10, TimeUnit.SECONDS));

      Wait.assertTrue(server::isReplicaSync);
      Wait.assertTrue(() -> recordsSent.get() > 200);
      running.set(false);
      assertTrue(latch.await(10, TimeUnit.SECONDS));

      assertEquals(0, errors.get());

      validateAckManager(server, queueName, queueIdOnServerLive, recordsSent.get());

      server.stop();
      Wait.assertTrue(backupServer::isActive);

      validateAckManager(backupServer, queueName, queueIdOnServerLive, recordsSent.get());
   }

   private void validateAckManager(ActiveMQServer server,
                                   String queueName,
                                   long queueIdOnServerLive,
                                   int messagesSent) {
      AckManager liveManager = AckManagerProvider.getManager(server);
      Map<SimpleString, LongObjectHashMap<JournalHashMap<Long, AckRetry, AckRetry, Queue>>> sortedRetries = liveManager.sortRetries();
      assertEquals(1, sortedRetries.size());

      LongObjectHashMap<JournalHashMap<Long, AckRetry, AckRetry, Queue>> retryAddress = sortedRetries.get(SimpleString.of(queueName));
      JournalHashMap<Long, AckRetry, AckRetry, Queue> journalHashMapBackup = retryAddress.get(queueIdOnServerLive);
      assertEquals(messagesSent, journalHashMapBackup.size());
   }

   protected HAPolicyConfiguration createReplicationLiveConfiguration() {
      ReplicationPrimaryPolicyConfiguration haPolicy = ReplicationPrimaryPolicyConfiguration.withDefault();
      haPolicy.setDistributedManagerConfiguration(managerConfiguration);
      return haPolicy;
   }

   protected HAPolicyConfiguration createReplicationBackupConfiguration() {
      ReplicationBackupPolicyConfiguration haPolicy = ReplicationBackupPolicyConfiguration.withDefault();
      haPolicy.setDistributedManagerConfiguration(managerConfiguration);
      haPolicy.setClusterName("cluster");
      return haPolicy;
   }

   protected Configuration createLiveConfiguration() throws Exception {
      Configuration conf = new ConfigurationImpl();
      conf.setJournalType(JournalType.NIO);
      conf.setName("localhost::live");

      File liveDir = newTemporaryFolder("live");
      conf.setBrokerInstance(liveDir);

      conf.addAcceptorConfiguration("live", "tcp://localhost:61616");
      conf.addConnectorConfiguration("backup", "tcp://localhost:61617");
      conf.addConnectorConfiguration("live", "tcp://localhost:61616");

      conf.setClusterUser("mycluster");
      conf.setClusterPassword("mypassword");

      conf.setHAPolicyConfiguration(createReplicationLiveConfiguration());

      ClusterConnectionConfiguration ccconf = new ClusterConnectionConfiguration();
      ccconf.setStaticConnectors(new ArrayList<>()).getStaticConnectors().add("backup");
      ccconf.setName("cluster");
      ccconf.setConnectorName("live");
      conf.addClusterConfiguration(ccconf);

      conf.setSecurityEnabled(false).setJMXManagementEnabled(false).setJournalType(JournalType.NIO).setJournalFileSize(1024 * 512).setConnectionTTLOverride(60_000L);

      return conf;
   }

   protected Configuration createBackupConfiguration() throws Exception {
      Configuration conf = new ConfigurationImpl();
      conf.setName("localhost::backup");

      File backupDir = newTemporaryFolder("backup");
      conf.setBrokerInstance(backupDir);

      conf.setHAPolicyConfiguration(createReplicationBackupConfiguration());

      conf.addAcceptorConfiguration("backup", "tcp://localhost:61617");
      conf.addConnectorConfiguration("live", "tcp://localhost:61616");
      conf.addConnectorConfiguration("backup", "tcp://localhost:61617");

      conf.setClusterUser("mycluster");
      conf.setClusterPassword("mypassword");

      ClusterConnectionConfiguration ccconf = new ClusterConnectionConfiguration();
      ccconf.setStaticConnectors(new ArrayList<>()).getStaticConnectors().add("live");
      ccconf.setName("cluster");
      ccconf.setConnectorName("backup");
      conf.addClusterConfiguration(ccconf);

      conf.setSecurityEnabled(false).setJMXManagementEnabled(false).setJournalType(JournalType.NIO).setJournalFileSize(1024 * 512).setConnectionTTLOverride(60_000L);

      return conf;
   }



}
