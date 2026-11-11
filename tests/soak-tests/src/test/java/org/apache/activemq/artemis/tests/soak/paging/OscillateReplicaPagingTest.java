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

package org.apache.activemq.artemis.tests.soak.paging;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.PagingStoreFactory;
import org.apache.activemq.artemis.core.paging.impl.PagingManagerImpl;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreFactoryNIO;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OscillateReplicaPagingTest extends SoakTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String propertyIdentification = "osc_I";

   private static final boolean USE_KILL = false;

   private static final String BODY_STRING = "hello " + "a".repeat(1_000);

   public OscillateReplicaPagingTest() {
   }

   public static final String SERVER_NAME_0 = "oscillate-server-live";
   public static final String SERVER_NAME_1 = "oscillate-server-replica";

   AtomicInteger produced = new AtomicInteger(0);
   AtomicInteger consumed = new AtomicInteger(0);

   ConcurrentHashMap<Integer, Integer> receivedMessages = new ConcurrentHashMap<>();

   private Process serverLive;

   private Process serverReplica;

   protected void startLive() throws Exception {
      serverLive = startServer(SERVER_NAME_0, 0, 30000);
   }

   protected void startBackup() throws Exception {
      serverReplica = startServer(SERVER_NAME_1, -1, -1);
   }

   private void waitReplicaSync() throws Exception {

      for (int i = 0; i < 4; i++) {
         SimpleManagement simpleManagement = new SimpleManagement("tcp://localhost:61616", "guest", "guest");
         try {
            runAfter(simpleManagement::close);
            if (Wait.waitFor(() -> wrapIsReplicaSync(simpleManagement), TimeUnit.SECONDS.toMillis(30), 250)) {
               return;
            } else {
               jstack(serverLive.pid(), "Server LIVE");
               jstack(serverReplica.pid(), "Server BACKUP");
            }
         } finally {
            try {
               simpleManagement.close();
            } catch (Throwable ignored) {
            }
         }
      }
      Assertions.fail("Replica is not sync");
   }

   private boolean wrapIsReplicaSync(SimpleManagement simpleManagement) {
      try {
         return simpleManagement.isReplicaSync();
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         return false;
      }
   }

   private void destroyLive() throws Exception {
      if (USE_KILL) {
         serverLive.destroyForcibly();
      } else {
         stopServerWithFile(getServerLocation(SERVER_NAME_0));
      }
      assertTrue(serverLive.waitFor(1, TimeUnit.MINUTES));
   }

   private void destroyBackup() throws Exception {
      if (USE_KILL) {
         serverReplica.destroyForcibly();
      } else {
         stopServerWithFile(getServerLocation(SERVER_NAME_1));
      }
      assertTrue(serverReplica.waitFor(1, TimeUnit.MINUTES));
   }

   @BeforeEach
   public void createServers() throws Exception {
      {
         File serverLocation = getFileServerLocation(SERVER_NAME_0);
         deleteDirectory(serverLocation);

         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(serverLocation);
         cliCreateServer.setConfiguration("./src/main/resources/servers/page-oscillation/live");
         cliCreateServer.createServer();
         configurePurgePageFolders0(false);
      }
      {
         File serverLocation = getFileServerLocation(SERVER_NAME_1);
         deleteDirectory(serverLocation);

         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(serverLocation);
         cliCreateServer.setConfiguration("./src/main/resources/servers/page-oscillation/replica");
         cliCreateServer.createServer();
         configurePurgePageFolders1(false);
      }
   }


   private void configurePurgePageFolders0(boolean purgePageFolders) throws Exception {
      configurePurgePageFolders(getFileServerLocation(SERVER_NAME_0), "./src/main/resources/servers/page-oscillation/live/broker.xml", purgePageFolders);
   }

   private void configurePurgePageFolders1(boolean purgePageFolders) throws Exception {
      configurePurgePageFolders(getFileServerLocation(SERVER_NAME_1), "./src/main/resources/servers/page-oscillation/replica/broker.xml", purgePageFolders);
   }

   private void configurePurgePageFolders(File serverLocation, String originalFile, boolean purgePageFolders) throws Exception {

      File tempFile = new File(serverLocation, "/etc/temp-xml");
      if (tempFile.exists()) {
         tempFile.delete(); // just in case
      }
      Files.copy(new File(originalFile).toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      assertTrue(FileUtil.findReplace(tempFile, "&&REPLACE-HERE&&", (purgePageFolders ? "true" : "false")));

      File targetConfig = new File(serverLocation, "/etc/broker.xml");
      Files.copy(tempFile.toPath(), targetConfig.toPath(), StandardCopyOption.REPLACE_EXISTING);
      tempFile.delete();
   }


   @BeforeEach
   public void before() throws Exception {
      sequenceGenerator.set(0);
      cleanupData(SERVER_NAME_0);
      cleanupData(SERVER_NAME_1);
   }

   static final String BUSY_QUEUE = "busyQueue";
   static final String OSCILLATE_QUEUE_NAME = "oscillateSubs";
   static final String OSCILLATE_ADDRESS = "oscillateAddress";
   static final String OSCILLATE_QUEUE_EXPRESSION = OSCILLATE_ADDRESS + "::" + OSCILLATE_QUEUE_NAME;

   static final int BROWSERS = 1;
   static final int SPIKE_THREADS = 1;
   static final int OSCILLATE_CONSUMERS = 50;
   static final int OSCILLATE_PRODUCERS = 10;
   static final int BUSY_PRODUCERS = 1;
   static final int BUSY_CONSUMERS = 1;

   public int optionOscillatedRuntime() {
      return 5_000;
   }
   private final int OSCILLATED_RUNTIME = optionOscillatedRuntime();

   protected int optionRepeats() {
      return 2;
   }
   private final int REPEATS = optionRepeats();

   protected int optionNumberOfMessagesFailback() {
      return 100;
   }
   private final int NUMBER_OF_MESSAGES_FAILBACK = optionNumberOfMessagesFailback();

   protected int optionNumberOfMessagesDestroyPage() {
      return 10_000;
   }
   private final int NUMBER_OF_MESSAGES_DESTROY_PAGE = optionNumberOfMessagesDestroyPage();

   /**
    * One of the tests need an additional destination full of messages.
    * This is not really part of the test, however it's here to fillup the replication catchup with extra stuff
    * and introduce races. */
   protected int optionBusyLoadSize() {
      return 2000;
   }
   private final int BUSY_LOAD_SIZE = optionBusyLoadSize();

   AtomicInteger errors = new AtomicInteger(0);
   CountDownLatch done;
   static AtomicInteger sequenceGenerator = new AtomicInteger(0);

   volatile boolean running = true;

   @Test
   public void testOscillateLoad() throws Throwable {

      startLive();
      startBackup();

      waitReplicaSync();

      final int TOTAL_THREADS = OSCILLATE_CONSUMERS + OSCILLATE_PRODUCERS + BUSY_PRODUCERS + BUSY_CONSUMERS + BROWSERS + SPIKE_THREADS;
      done = new CountDownLatch(TOTAL_THREADS);

      ExecutorService executorService = Executors.newFixedThreadPool(TOTAL_THREADS);
      runAfter(executorService::shutdownNow);

      int threadsUsed = 0;

      for (int i = 0; i < SPIKE_THREADS; i++) {
         executorService.execute(this::oscillateSpikeProducer);
         threadsUsed++;
      }

      // busy consumer and producer are here just guarantee the storage is full of stuff and it's working
      for (int i = 0; i < BUSY_PRODUCERS; i++) {
         executorService.execute(this::busyProducer);
         threadsUsed++;
      }
      for (int i = 0; i < BUSY_CONSUMERS; i++) {
         executorService.execute(this::busyConsumer);
         threadsUsed++;
      }

      for (int i = 0; i < OSCILLATE_CONSUMERS; i++) {
         threadsUsed++;
         executorService.execute(this::oscillateConsumer);
      }

      for (int i = 0; i < OSCILLATE_PRODUCERS; i++) {
         executorService.execute(this::oscillateProducer);
         threadsUsed++;
      }

      for (int i = 0; i < BROWSERS; i++) {
         executorService.execute(this::oscillateBrowser);
         threadsUsed++;
      }

      // this is just to validate I configured it correctly
      assertEquals(threadsUsed, TOTAL_THREADS);

      Thread.sleep(OSCILLATED_RUNTIME);
      running = false;
      assertTrue(done.await(100, TimeUnit.SECONDS));

      finalConsume();

      assertTrue(produced.get() > 0);

      if (produced.get() != consumed.get()) {
         logger.debug("produced = {}, consumed = {}, receivedMessages size = {}", produced.get(), consumed.get(), receivedMessages.size());
         receivedMessages.forEach((a, b) -> {
            logger.debug("Message {} is nowhere to be found", a);
         });
      }

      assertEquals(produced.get(), consumed.get());
      assertEquals(0, errors.get());

      destroyBackup();

      destroyLive();
   }

   ActiveMQConnectionFactory createConnectionFactory(String uri) {
      ActiveMQConnectionFactory factory = (ActiveMQConnectionFactory) CFUtil.createConnectionFactory("CORE", uri);
      factory.setCallTimeout(1_000);
      factory.setCallFailoverTimeout(1_000);
      return factory;
   }

   @Test
   public void testHoldMessagesAndDestroyPage() throws Exception {
      startLive();

      ActiveMQConnectionFactory factory = createConnectionFactory("tcp://localhost:61616");
      factory.setBlockOnDurableSend(false);

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createTopic(OSCILLATE_ADDRESS));

         for (int i = 0; i < NUMBER_OF_MESSAGES_DESTROY_PAGE; i++) {
            Message message = session.createTextMessage(BODY_STRING);
            message.setIntProperty("originalI", i);
            producer.send(message);
            if (i % 1000 == 0) {
               session.commit();
               logger.debug("Sending {}", i);
            }
         }
         logger.debug("Sent {}", NUMBER_OF_MESSAGES_DESTROY_PAGE);
         session.commit();

         MessageConsumer consumer = session.createConsumer(session.createQueue(OSCILLATE_QUEUE_EXPRESSION), "originalI >= 100 AND originalI < 90000");
         connection.start();
         for (int i = 100; i < NUMBER_OF_MESSAGES_DESTROY_PAGE - 1000; i++) {
            Message message = consumer.receive(5000);
            assertNotNull(message);
            assertEquals(i, message.getIntProperty("originalI"));
            if (i % 1000 == 0) {
               logger.debug("Receiving {}", i);
               session.commit();
            }
         }
         session.commit();
         logger.debug("Received {}", NUMBER_OF_MESSAGES_DESTROY_PAGE - 1);

         for (int i = NUMBER_OF_MESSAGES_DESTROY_PAGE; i < NUMBER_OF_MESSAGES_DESTROY_PAGE + 5000; i++) {
            Message message = session.createTextMessage(BODY_STRING);
            message.setIntProperty("originalI", i);
            producer.send(message);
            if (i % 1000 == 0) {
               session.commit();
               logger.debug("Sending {}", i);
            }
         }
         session.commit();
      }

      destroyLive();

      File pagingFolder = new File(getServerLocation(SERVER_NAME_0), "/data/paging");
      FileUtil.deleteDirectory(pagingFolder);
      pagingFolder.mkdirs();

      startLive();

      long messageCount;

      try (SimpleManagement simpleManagement = new SimpleManagement("tcp://localhost:61616", "guest", "guest")) {
         messageCount = simpleManagement.getMessageCountOnQueue(OSCILLATE_QUEUE_NAME);
         logger.debug("There still {}", messageCount);
      }

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer messageConsumer = session.createConsumer(session.createQueue(OSCILLATE_QUEUE_EXPRESSION));
         connection.start();
         for (int i = 0; i < messageCount; i++) {
            Message message = messageConsumer.receive(5000);
         }
      }

      int lastMessages = NUMBER_OF_MESSAGES_DESTROY_PAGE;

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createTopic(OSCILLATE_ADDRESS));

         for (int i = 0; i < lastMessages; i++) {
            TextMessage message = session.createTextMessage(BODY_STRING);
            message.setIntProperty("i", i);
            producer.send(message);
            if (i % 1000 == 0) {
               session.commit();
               logger.debug("last Sending {}", i);
            }
         }
         logger.debug("last Sent {}", lastMessages);
         session.commit();
      }

      destroyLive();

      startLive();

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer fastConsumer = session.createConsumer(session.createQueue(OSCILLATE_QUEUE_EXPRESSION));
         connection.start();
         for (int i = 0; i < lastMessages; i++) {
            Message message = fastConsumer.receive(5000);
            if (message == null) {
               logger.debug("Expected message at {}", i);
            }
            assertNotNull(message);
            assertEquals(i, message.getIntProperty("i"));
            if (i % 1000 == 0) {
               logger.debug("final Receiving {}", i);
               session.commit();
            }
         }
         session.commit();
         logger.debug("final Received {}", NUMBER_OF_MESSAGES_DESTROY_PAGE - 1);
      }

   }

   @Test
   public void testOscillateAndFailback() throws Exception {
      internalOscillateAndFailback(false);
   }

   @Test
   public void testOscillateAndFailbackPurge() throws Exception {
      internalOscillateAndFailback(true);
   }

   private void internalOscillateAndFailback(boolean purgePageFolders) throws Exception {
      logger.info("running internalOscillateAndFailback with purgePageFolders={}", purgePageFolders);
      // reconfiguring servers
      configurePurgePageFolders0(purgePageFolders);
      configurePurgePageFolders1(purgePageFolders);

      ExecutorService executorService = Executors.newFixedThreadPool(2);
      runAfter(executorService::shutdownNow);

      startLive();
      startBackup();
      waitReplicaSync();

      ActiveMQConnectionFactory factory = createConnectionFactory("tcp://localhost:61616");

      Integer originalBindingCounts = null;

      for (int repeat = 0; repeat < REPEATS; repeat++) {
         Configuration configuration = new ConfigurationImpl();
         configuration.setBindingsDirectory(new File(getServerLocation(SERVER_NAME_0), "/data/bindings").getAbsolutePath());
         HashMap<Integer, AtomicInteger> bindingsCounter = countBindingJournal(configuration);

         AtomicInteger bindingsRecordCount = bindingsCounter.get((int) JournalRecordIds.QUEUE_BINDING_RECORD);
         assertNotNull(bindingsRecordCount);

         if (originalBindingCounts == null) {
            originalBindingCounts = bindingsRecordCount.intValue();
         } else {
            // make sure the bindings is not changed between restarts
            assertEquals(originalBindingCounts.intValue(), bindingsRecordCount.intValue());
         }

         logger.info("Repeat ######################################################################################################################## {}", repeat);
         try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

            if (repeat == 0) {
               MessageProducer producer = session.createProducer(session.createQueue(BUSY_QUEUE));
               // We need a large amount of messages in paging, so sync-replication takes time
               for (int i = 0; i < BUSY_LOAD_SIZE; i++) {
                  Message message = session.createTextMessage(BODY_STRING);
                  message.setIntProperty(propertyIdentification, i);
                  message.setStringProperty("_AMQ_GROUP_ID", "group" + (i % 10));
                  producer.send(message);
                  if (i % 1000 == 0) {
                     session.commit();
                     logger.debug("Sending {}", i);
                  }
               }
               session.commit();
               producer.close();
            }

            MessageProducer producer = session.createProducer(session.createTopic(OSCILLATE_ADDRESS));
            for (int i = 0; i < NUMBER_OF_MESSAGES_FAILBACK; i++) {
               Message message = session.createTextMessage(BODY_STRING);
               message.setIntProperty(propertyIdentification, i);
               message.setStringProperty("_AMQ_GROUP_ID", "group" + (i % 10));
               producer.send(message);
               if (i % 1000 == 0) {
                  session.commit();
                  logger.debug("Sending {}", i);
               }
            }
            logger.debug("Sent {}", NUMBER_OF_MESSAGES_FAILBACK);
            session.commit();

            MessageConsumer consumer = session.createConsumer(session.createQueue(OSCILLATE_QUEUE_EXPRESSION));
            connection.start();
            for (int i = 0; i < NUMBER_OF_MESSAGES_FAILBACK; i++) {
               Message message = consumer.receive(5000);
               assertNotNull(message);
               assertEquals(i, message.getIntProperty(propertyIdentification));
               if (i % 1000 == 0) {
                  logger.debug("Receiving {}", i);
                  session.commit();
               }
            }
            session.commit();
            consumer.close();
         }

         AtomicInteger produced = new AtomicInteger(0);
         AtomicInteger consumed = new AtomicInteger(0);

         CountDownLatch done = new CountDownLatch(2);
         AtomicBoolean running = new AtomicBoolean(true);
         runAfter(() -> running.set(false));
         executorService.execute((() -> loadGeneratorConsumer(running, done, consumed)));
         executorService.execute((() -> loadGeneratorProducer(running, done, produced)));

         long firstPage = getFirstPageID();

         logger.debug("First page before starting {}", firstPage);

         Thread.sleep(1000);
         // rebooting backup
         destroyBackup();
         startBackup();

         //rebooting backup with a delay to restart it
         logger.debug("******************************************************************************************************************************* Destroying live");
         destroyLive();
         Thread.sleep(5000); // allow some processing on backup
         logger.debug("******************************************************************************************************************************* starting live");
         startLive();
         waitReplicaSync();
         Thread.sleep(2000); // allow some processing on live
         // restart just live now
         destroyLive();
         startLive();
         waitReplicaSync();
         Thread.sleep(2000); // allow some processing on live

         running.set(false);
         assertTrue(done.await(1, TimeUnit.MINUTES));

         waitReplicaSync();

         long firstPageAtEnd = getFirstPageID();

         logger.debug("First page at end = {}", firstPageAtEnd);

         if (firstPageAtEnd < firstPage) {
            if (purgePageFolders) {
               // it is expected
               logger.debug("First page was {}, and now first page is {}", firstPage, firstPageAtEnd);
            } else {
               logger.warn(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> First page was {}, and now first page is {}", firstPage, firstPageAtEnd);
            }
         }

         try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumer = session.createConsumer(session.createQueue(OSCILLATE_QUEUE_EXPRESSION));
            connection.start();
            while (true) {
               Message message = consumer.receive(500);
               consumed.incrementAndGet();
               if (message == null) {
                  break;
               }
            }
         }

         assertTrue(consumed.get() >= produced.get(), "consumed = " + consumed + ", produced = " + produced);

      }


   }

   private static long getFirstPageID() throws Exception {
      ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {

         HierarchicalObjectRepository<AddressSettings> settings = new HierarchicalObjectRepository<>();
         settings.addMatch("#", new AddressSettings());

         PagingStoreFactory pagingStoreFactory = new PagingStoreFactoryNIO(new NullStorageManager(), new File(getServerLocation(SERVER_NAME_0), "/data/paging"), 1000, scheduledExecutor, new OrderedExecutorFactory(executor), true, null, () -> false);
         PagingManagerImpl pagingManager = new PagingManagerImpl(pagingStoreFactory, settings);
         pagingManager.start();
         PagingStore store = pagingManager.getPageStore(SimpleString.of(OSCILLATE_ADDRESS));
         long firstPage = store.getFirstPage();
         store.stop();
         pagingManager.stop();
         return firstPage;
      } finally {
         scheduledExecutor.shutdownNow();
         executor.shutdownNow();
      }
   }

   public void loadGeneratorConsumer(AtomicBoolean running, CountDownLatch done, AtomicInteger consumed) {

      try {
         while (running.get()) {

            try (Connection connection = connectEither(running)) {
               Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
               MessageConsumer consumer = session.createConsumer(session.createQueue(OSCILLATE_QUEUE_EXPRESSION));
               connection.start();
               while (running.get()) {
                  Message message = consumer.receive(5000);
                  if (message != null) {
                     int c = consumed.incrementAndGet();
                     if (c % 100 == 0) {
                        logger.debug("Load on consumer {}", consumed);
                     }
                  }
               }

            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
            }

         }
      } finally {
         done.countDown();
      }
   }

   public void loadGeneratorProducer(AtomicBoolean running, CountDownLatch done, AtomicInteger produced) {

      try {
         while (running.get()) {

            try (Connection connection = connectEither(running)) {
               Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
               MessageProducer producer = session.createProducer(session.createTopic(OSCILLATE_ADDRESS));
               int sent = 0;
               while (running.get()) {
                  Message message = session.createTextMessage(BODY_STRING);
                  message.setStringProperty("loadGenerator", "yes");
                  producer.send(message);
                  sent++;
                  if (sent == 25) {
                     session.commit();
                     produced.addAndGet(sent);
                     sent = 0;
                     logger.debug("load on producer {}", produced.addAndGet(sent));

                     Thread.sleep(20);
                  }
               }
               session.commit();
               produced.addAndGet(sent);

            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
            }

         }
      } finally {
         done.countDown();
      }
   }

   public Connection connectEither(AtomicBoolean running) {
      while (running.get()) {
         try {
            ConnectionFactory factory = createConnectionFactory("tcp://localhost:61616");
            return factory.createConnection();
         } catch (Exception ignored) {
         }
         try {
            ConnectionFactory factory = createConnectionFactory("tcp://localhost:61617");
            return factory.createConnection();
         } catch (Exception ignored) {
         }
      }
      return null;
   }

   void messageAdded(Integer messageID) {
      receivedMessages.put(messageID, messageID);
      produced.incrementAndGet();
   }

   void messageFound(Integer messageID) {
      receivedMessages.remove(messageID);
      consumed.incrementAndGet();
   }

   private void oscillateProducer() {
      ConnectionFactory factory = createConnectionFactory("tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createTopic(OSCILLATE_ADDRESS));

         long waitOscillation = 500;

         while (running) {
            for (int i = 0; i < 100; i++) {
               TextMessage message = session.createTextMessage(BODY_STRING);
               int id = sequenceGenerator.incrementAndGet();
               message.setIntProperty(propertyIdentification, id);
               messageAdded(id);
               producer.send(message);
               session.commit();
            }
            logger.debug("Sent on oscillation = {}, currentSleep={}", produced.get(), waitOscillation);
            Thread.sleep(waitOscillation);
            waitOscillation -= 100;
            if (waitOscillation <= 100) {
               waitOscillation = 1000;
            }
         }

      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         errors.incrementAndGet();
      } finally {
         done.countDown();
      }

   }

   private void oscillateSpikeProducer() {
      ConnectionFactory factory = createConnectionFactory("tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createTopic(OSCILLATE_ADDRESS));

         long SPIKE_INTERVAL = 20_000;
         int SPIKE_SIZE = 10_0000;

         long nextSpike = System.currentTimeMillis() + SPIKE_INTERVAL;

         while (running) {
            while (nextSpike > System.currentTimeMillis() && running) {
               Thread.sleep(1000);
               logger.debug("Generating spike in {} milliseconds", nextSpike - System.currentTimeMillis());
            }

            if (!running) {
               break;
            }

            logger.debug("Generating a spike");

            for (int i = 0; i < SPIKE_SIZE; i++) {
               TextMessage message = session.createTextMessage(BODY_STRING);
               int id = sequenceGenerator.incrementAndGet();
               message.setIntProperty(propertyIdentification, id);
               messageAdded(id);
               producer.send(message);
               if (i % 1000 == 0) {
                  logger.debug("Spike sent {}", i);
                  session.commit();
               }
            }
            session.commit();
            logger.debug("Generated spike of {}", SPIKE_SIZE);
            nextSpike = System.currentTimeMillis() + SPIKE_INTERVAL;
         }
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         errors.incrementAndGet();
      } finally {
         done.countDown();
      }

   }

   private void finalConsume() {
      ConnectionFactory factory = createConnectionFactory("tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         SimpleManagement simpleManagement = new SimpleManagement("tcp://localhost:61616", "guest", "guest");

         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(session.createQueue(OSCILLATE_QUEUE_EXPRESSION));
         connection.start();

         for (; ; ) {
            Message message = consumer.receive(2000);
            if (message == null) {
               break;
            }
            messageFound(message.getIntProperty(propertyIdentification));
         }
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         errors.incrementAndGet();
      } finally {
         done.countDown();
      }
   }

   private void restartReplica() {
      try {
         Thread.sleep(20_000);
         destroyBackup();
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         errors.incrementAndGet();
      } finally {
         done.countDown();
      }
   }

   private void oscillateConsumer() {
      ConnectionFactory factory = createConnectionFactory("tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(session.createQueue(OSCILLATE_QUEUE_EXPRESSION));
         connection.start();

         long rec = 0;

         while (running) {
            TextMessage message = (TextMessage) consumer.receive(100);
            if (message != null) {
               if (++rec % 200 == 0) {
                  logger.debug("Received {} on oscillateConsumer", rec);
               }
               messageFound(message.getIntProperty(propertyIdentification));
            }
         }

      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         errors.incrementAndGet();
      } finally {
         done.countDown();
      }
   }

   private void oscillateBrowser() {
      ConnectionFactory factory = createConnectionFactory("tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         while (running) {
            try (QueueBrowser browser = session.createBrowser(session.createQueue(OSCILLATE_QUEUE_EXPRESSION))) {
               Enumeration<Message> messageEnum = browser.getEnumeration();
               int messagesOnBrowser = 0;
               while (running && messageEnum.hasMoreElements()) {
                  Message message = messageEnum.nextElement();
                  if (message != null) {
                     messagesOnBrowser++;
                  }
               }
               logger.debug("there was {} messages through browsing", messagesOnBrowser);
               Thread.sleep(250);
            }
            connection.start();
         }
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         errors.incrementAndGet();
      } finally {
         done.countDown();
      }
   }

   private void busyProducer() {
      ConnectionFactory factory = createConnectionFactory("tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(BUSY_QUEUE));

         while (running) {
            producer.send(session.createTextMessage(BODY_STRING));
            session.commit();
         }

      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         errors.incrementAndGet();
      } finally {
         done.countDown();
      }

   }

   private void busyConsumer() {
      ConnectionFactory factory = createConnectionFactory("tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(session.createQueue(BUSY_QUEUE));
         connection.start();

         long rec = 0;

         while (running) {
            TextMessage message = (TextMessage) consumer.receive(100);
            if (message != null && rec++ % 1000 == 0) {
               logger.debug("Received {} on busy", rec);
            }
         }

      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         errors.incrementAndGet();
      } finally {
         done.countDown();
      }
   }
}

