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
package org.apache.activemq.artemis.tests.integration.paging;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.TestParameters;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** It is recommended to setup the variable ARTEMIS_NFS to use a NFS mount on this test. */
public class PageCleanupFolderTest extends ActiveMQTestBase {

   protected static final int PAGE_MAX = 100 * 1024;
   protected static final int PAGE_SIZE = 10 * 1024;
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   protected ServerLocator locator;
   protected ActiveMQServer server;
   protected ClientSessionFactory sf;
   private AssertionLoggerHandler loggerHandler;

   @Test
   public void testCleanupFolders() throws Exception {

      Configuration config = createDefaultConfig(true).setPurgePageFolders(true).setJournalType(JournalType.NIO);

      if (TestParameters.NFS_FOLDER != null) {
         File dataFolder = new File(TestParameters.NFS_FOLDER + "/PageCleanupFolderTest");
         deleteDirectory(dataFolder);
         config.setJournalDirectory(dataFolder.getAbsolutePath() + "/journal").
            setPagingDirectory(dataFolder.getAbsolutePath() + "/paging").
            setBindingsDirectory(dataFolder.getAbsolutePath() + "/binding").
            setLargeMessagesDirectory(dataFolder.getAbsolutePath() + "/largeMessages");
      }

      final int PAGE_MAX = 0;

      final int PAGE_SIZE = 1024 * 1024;

      server = createServer(true, config, PAGE_SIZE, PAGE_MAX, -1, -1);
      server.start();

      ExecutorService executorService = Executors.newFixedThreadPool(20);
      runAfter(executorService::shutdownNow);

      int destinations = 20;

      for (int serverStart = 0; serverStart < 3; serverStart++) {
         try (AssertionLoggerHandler assertionLoggerHandler = new AssertionLoggerHandler()) {
            AtomicInteger errors = new AtomicInteger(0);
            ReusableLatch done = new ReusableLatch(destinations);
            for (int i = 0; i < destinations; i++) {
               final int destinationID = i;
               executorService.execute(() -> {
                  ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
                  try (Connection connection = factory.createConnection()) {
                     Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
                     MessageProducer producer = session.createProducer(session.createQueue("queue" + destinationID));
                     for (int s = 0; s < 100; s++) {
                        producer.send(session.createTextMessage("hello " + s));
                     }
                     session.commit();
                  } catch (Exception e) {
                     errors.incrementAndGet();
                     logger.warn(e.getMessage(), e);
                  } finally {
                     done.countDown();
                  }
               });
            }
            assertTrue(done.await(50, TimeUnit.SECONDS));

            done.setCount(destinations);
            for (int i = 0; i < destinations; i++) {
               final int destinationID = i;
               executorService.execute(() -> {
                  ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
                  try (Connection connection = factory.createConnection()) {
                     Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
                     MessageConsumer consumer = session.createConsumer(session.createQueue("queue" + destinationID));
                     connection.start();
                     for (int s = 0; s < 100; s++) {
                        assertNotNull(consumer.receive(5000));
                     }
                     session.commit();
                  } catch (Exception e) {
                     errors.incrementAndGet();
                     logger.warn(e.getMessage(), e);
                  } finally {
                     done.countDown();
                  }
               });
            }
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(0, errors.get());

            for (int i = 0; i < destinations; i++) {
               Queue queue = server.locateQueue("queue" + i);
               assertNotNull(queue);
               Wait.assertFalse(queue.getPagingStore()::isPaging, 5000, 100);
            }

            // this is the code for purging a page folder
            Wait.assertTrue(() -> assertionLoggerHandler.countText("AMQ224146") >= destinations, 5000, 100);
            // this is the code to "fail to purge the page folder"
            assertEquals(0, assertionLoggerHandler.countText("AMQ224147"));

            File pageFolderLocation = server.getConfiguration().getPagingLocation();
            Wait.assertEquals(0, () -> pageFolderLocation.listFiles().length, 5000, 100);

            server.stop();
            server.start();

            Wait.assertEquals(0, () -> pageFolderLocation.listFiles().length, 5000, 100);
         }

      }
   }
}
