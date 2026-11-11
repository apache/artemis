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
package org.apache.activemq.artemis.tests.integration.replication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.mapped.MappedSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.LoaderCallback;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.message.impl.CoreMessagePersister;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.tests.extensions.TargetTempDirFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.lang.invoke.MethodHandles;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SharedNothingReplicationTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // Temp folder at ./target/tmp/<TestClassName>/<generated>
   @TempDir(factory = TargetTempDirFactory.class)
   public File brokersFolder;
   ExecutorService sendMessageExecutor;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
      sendMessageExecutor = Executors.newSingleThreadExecutor();
      CoreMessagePersister.registerPersister(SlowMessagePersister._getInstance());
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      CoreMessagePersister.resetPersister();
      sendMessageExecutor.shutdownNow();
      super.tearDown();
   }

   @Test
   public void testReplicateFromSlowLive() throws Exception {
      // start primary
      Configuration primaryConfiguration = createPrimaryConfiguration();
      ActiveMQServer primaryServer = addServer(ActiveMQServers.newActiveMQServer(primaryConfiguration));
      primaryServer.start();

      Wait.waitFor(primaryServer::isStarted);

      final CountDownLatch replicated = new CountDownLatch(1);

      ServerLocator locator = ServerLocatorImpl.newLocator("tcp://localhost:61616");
      locator.setCallTimeout(60_000L);
      locator.setConnectionTTL(60_000L);
      locator.addClusterTopologyListener(new ClusterTopologyListener() {
         @Override
         public void nodeUP(TopologyMember member, boolean last) {
            logger.debug("nodeUP fired last={}, primary={}, backup={}", last, member.getPrimary(), member.getBackup());
            if (member.getBackup() != null) {
               replicated.countDown();
            }
         }

         @Override
         public void nodeDown(long eventUID, String nodeID) {

         }
      });

      final ClientSessionFactory csf = locator.createSessionFactory();
      ClientSession sess = csf.createSession();
      sess.createQueue(QueueConfiguration.of("slow").setRoutingType(RoutingType.ANYCAST));
      sess.close();

      // let's write some messages
      int i = 0;
      final int j = 50;
      final CountDownLatch allMessageSent = new CountDownLatch(j);
      while (i < 5) {
         sendMessageExecutor.execute(() -> {
            try {
               ClientSession session = csf.createSession(true, true);
               ClientProducer producer = session.createProducer("slow");
               ClientMessage message = session.createMessage(true);
               // this will make journal's append executor busy
               message.putLongProperty("delay", 500L);
               logger.debug("try to send a message before replicated");
               producer.send(message);
               logger.debug("send message done");
               producer.close();
               session.close();

               allMessageSent.countDown();
            } catch (ActiveMQException e) {
               logger.error("send message", e);
            }
         });
         i++;
      }

      // start backup
      Configuration backupConfiguration = createBackupConfiguration();
      ActiveMQServer backupServer = addServer(ActiveMQServers.newActiveMQServer(backupConfiguration));
      backupServer.start();

      Wait.waitFor(() -> backupServer.isStarted());

      assertTrue(replicated.await(30, TimeUnit.SECONDS), "can not replicate in 30 seconds");

      while (i < j) {
         sendMessageExecutor.execute(() -> {
            try {
               ClientSession session = csf.createSession(true, true);
               ClientProducer producer = session.createProducer("slow");
               ClientMessage message = session.createMessage(true);
               message.putLongProperty("delay", 0L);
               logger.debug("try to send a message after replicated");
               producer.send(message);
               logger.debug("send message done");
               producer.close();
               session.close();

               allMessageSent.countDown();
            } catch (ActiveMQException e) {
               logger.error("send message", e);
            }
         });
         i++;
      }

      assertTrue(allMessageSent.await(30, TimeUnit.SECONDS), "all message sent");

      csf.close();
      locator.close();
      backupServer.stop(true);
      primaryServer.stop(true);

      SequentialFileFactory fileFactory;

      File primaryJournalDir = brokersFolder.toPath().resolve("live").resolve("data").resolve("journal").toFile();
      fileFactory = new MappedSequentialFileFactory(primaryConfiguration.getJournalLocation(), primaryConfiguration.getJournalFileSize(), false, primaryConfiguration.getJournalBufferSize_NIO(), primaryConfiguration.getJournalBufferTimeout_NIO(), null);

      JournalImpl primaryMessageJournal = new JournalImpl(primaryConfiguration.getJournalFileSize(), primaryConfiguration.getJournalMinFiles(), primaryConfiguration.getJournalPoolFiles(), primaryConfiguration.getJournalCompactMinFiles(), primaryConfiguration.getJournalCompactPercentage(), fileFactory, "activemq-data", "amq", fileFactory.getMaxIO());

      primaryMessageJournal.start();
      final AtomicInteger primaryJournalCounter = new AtomicInteger();
      primaryMessageJournal.load(new AddRecordLoaderCallback() {
         @Override
         public void addRecord(RecordInfo info) {
            if (!(info.userRecordType == JournalRecordIds.ADD_MESSAGE_PROTOCOL)) {
               // ignore
            }
            logger.debug("got primary message {}", info.id);
            primaryJournalCounter.incrementAndGet();
         }
      });

      // read backup's journal
      File backupJournalDir = brokersFolder.toPath().resolve("backup").resolve("data").resolve("journal").toFile();
      fileFactory = new MappedSequentialFileFactory(backupConfiguration.getJournalLocation(), backupConfiguration.getJournalFileSize(), false, backupConfiguration.getJournalBufferSize_NIO(), backupConfiguration.getJournalBufferTimeout_NIO(), null);

      JournalImpl backupMessageJournal = new JournalImpl(backupConfiguration.getJournalFileSize(), backupConfiguration.getJournalMinFiles(), backupConfiguration.getJournalPoolFiles(), backupConfiguration.getJournalCompactMinFiles(), backupConfiguration.getJournalCompactPercentage(), fileFactory, "activemq-data", "amq", fileFactory.getMaxIO());

      backupMessageJournal.start();

      final AtomicInteger replicationCounter = new AtomicInteger();
      backupMessageJournal.load(new AddRecordLoaderCallback() {
         @Override
         public void addRecord(RecordInfo info) {
            if (!(info.userRecordType == JournalRecordIds.ADD_MESSAGE_PROTOCOL)) {
               // ignore
            }
            logger.debug("replicated message {}", info.id);
            replicationCounter.incrementAndGet();
         }
      });

      logger.debug("expected {} messages, primary={}, backup={}", j, primaryJournalCounter.get(), replicationCounter.get());
      assertEquals(j, primaryJournalCounter.get(), "Primary lost journal record");
      assertEquals(j, replicationCounter.get(), "Backup did not replicated all journal");

      // if this ever happens.. you need to make sure this persister is registered instead of the CoreMessagePersister
      assertTrue(SlowMessagePersister._getInstance().used, "The test is not valid, slow persister stopped being used");
   }

   protected HAPolicyConfiguration createReplicationPrimaryConfiguration() {
      return new ReplicatedPolicyConfiguration()
         .setVoteOnReplicationFailure(false)
         .setCheckForActiveServer(false);
   }

   protected Configuration createPrimaryConfiguration() throws Exception {
      Configuration conf = new ConfigurationImpl();
      conf.setName("localhost::primary");

      File primaryDir = newFolder(brokersFolder, "primary");
      conf.setBrokerInstance(primaryDir);

      conf.addAcceptorConfiguration("primary", "tcp://localhost:61616");
      conf.addConnectorConfiguration("backup", "tcp://localhost:61617");
      conf.addConnectorConfiguration("primary", "tcp://localhost:61616");

      conf.setClusterUser("mycluster");
      conf.setClusterPassword("mypassword");

      conf.setHAPolicyConfiguration(createReplicationPrimaryConfiguration());

      ClusterConnectionConfiguration ccconf = new ClusterConnectionConfiguration();
      ccconf.setStaticConnectors(new ArrayList<>()).getStaticConnectors().add("backup");
      ccconf.setName("cluster");
      ccconf.setConnectorName("primary");
      conf.addClusterConfiguration(ccconf);

      conf.setSecurityEnabled(false).setJMXManagementEnabled(false).setJournalType(JournalType.MAPPED).setJournalFileSize(1024 * 512).setConnectionTTLOverride(60_000L);

      return conf;
   }

   protected HAPolicyConfiguration createReplicationBackupConfiguration() {
      return new ReplicaPolicyConfiguration().setClusterName("cluster");
   }

   protected Configuration createBackupConfiguration() throws Exception {
      Configuration conf = new ConfigurationImpl();
      conf.setName("localhost::backup");

      File backupDir = newFolder(brokersFolder, "backup");
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

      conf.setSecurityEnabled(false).setJMXManagementEnabled(false).setJournalType(JournalType.MAPPED).setJournalFileSize(1024 * 512).setConnectionTTLOverride(60_000L);

      return conf;
   }

   static class SlowMessagePersister extends CoreMessagePersister implements Persister<Message> {

      boolean used = false;

      private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

      static SlowMessagePersister theInstance;

      private final CoreMessagePersister persister;

      private SlowMessagePersister() {
         persister = CoreMessagePersister.getInstance();
      }

      static SlowMessagePersister _getInstance() {
         if (theInstance == null) {
            theInstance = new SlowMessagePersister();
         }
         return theInstance;
      }

      @Override
      public byte getID() {
         return persister.getID();
      }

      @Override
      public int getEncodeSize(Message record) {
         return persister.getEncodeSize(record);
      }

      @Override
      public void encode(ActiveMQBuffer buffer, Message record) {
         used = true;
         try {
            Long delay = record.getLongProperty("delay");
            if (delay == null || delay.longValue() <= 0) {
               logger.debug("encode message {}, caller={}", record.getMessageID(), Thread.currentThread().getName());
            } else {
               logger.debug("sleep {} ms before encode message {}, caller={}", delay.longValue(), record.getMessageID(), Thread.currentThread().getName());
               Thread.sleep(delay.longValue());
            }
         } catch (InterruptedException e) {
            // it's ok
         }
         persister.encode(buffer, record);
      }

      @Override
      public Message decode(ActiveMQBuffer buffer, Message message, CoreMessageObjectPools pool) {
         return persister.decode(buffer, message, pool);
      }
   }

   abstract class AddRecordLoaderCallback implements LoaderCallback {

      @Override
      public void addPreparedTransaction(PreparedTransactionInfo preparedTransaction) {

      }

      @Override
      public void deleteRecord(long id) {

      }

      @Override
      public void updateRecord(RecordInfo info) {

      }

      @Override
      public void failedTransaction(long transactionID, List<RecordInfo> records, List<RecordInfo> recordsToDelete) {

      }
   }

   private static File newFolder(File root, String subFolder) throws IOException {
      File result = new File(root, subFolder);
      if (!result.mkdirs()) {
         throw new IOException("Couldn't create folders " + root);
      }
      return result;
   }

}