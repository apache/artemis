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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler.LogLevel;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AddressFullLoggingTest extends ActiveMQTestBase {

   private static final String SERVER_LOGGER_NAME = ActiveMQServerLogger.class.getPackage().getName();

   private static LogLevel previousLevel;

   @BeforeAll
   public static void prepareLogger() {
      previousLevel = AssertionLoggerHandler.setLevel(SERVER_LOGGER_NAME, LogLevel.INFO);
   }

   @AfterAll
   public static void clearLogger() throws Exception {
      AssertionLoggerHandler.setLevel(SERVER_LOGGER_NAME, previousLevel);
   }

   @Test
   public void testBlockLogging() throws Exception {
      final int MAX_MESSAGES = 200;
      final String MY_ADDRESS = "myAddress";
      final String MY_QUEUE = "myQueue";

      ActiveMQServer server = createServer(false);

      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(10 * 1024).setMaxSizeBytes(20 * 1024).setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK);
      server.getAddressSettingsRepository().addMatch("#", defaultSetting);
      server.getConfiguration().setDiskScanPeriod(100);
      server.start();

      internalTest(MAX_MESSAGES, MY_ADDRESS, MY_QUEUE, server);
   }

   @Test
   public void testGlobalBlockLogging() throws Exception {
      final int MAX_MESSAGES = 200;
      final String MY_ADDRESS = "myAddress";
      final String MY_QUEUE = "myQueue";

      ActiveMQServer server = createServer(false);

      AddressSettings defaultSetting = new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK);
      server.getAddressSettingsRepository().addMatch("#", defaultSetting);
      server.getConfiguration().setGlobalMaxSize(20 * 1024).setDiskScanPeriod(100);

      server.start();

      internalTest(MAX_MESSAGES, MY_ADDRESS, MY_QUEUE, server);
   }

   private void internalTest(int MAX_MESSAGES,
                             String MY_ADDRESS,
                             String MY_QUEUE,
                             ActiveMQServer server) throws Exception {
      ServerLocator locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);

      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = factory.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(MY_QUEUE).setAddress(MY_ADDRESS));

      final ClientProducer producer = session.createProducer(MY_ADDRESS);

      final ClientMessage message = session.createMessage(false);
      message.getBodyBuffer().writeBytes(new byte[1024]);

      ExecutorService executor = Executors.newFixedThreadPool(1, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      Callable<Object> sendMessageTask = () -> {
         producer.send(message);
         return null;
      };

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         int sendCount = 0;

         for (int i = 0; i < MAX_MESSAGES; i++) {
            Future<Object> future = executor.submit(sendMessageTask);
            try {
               future.get(3, TimeUnit.SECONDS);
               sendCount++;
            } catch (TimeoutException ex) {
               // message sending has been blocked
               break;
            } finally {
               future.cancel(true); // may or may not desire this
            }
         }

         executor.shutdown();
         session.close();
         session = factory.createSession(false, true, true);
         session.start();
         ClientConsumer consumer = session.createConsumer(MY_QUEUE);
         for (int i = 0; i < sendCount; i++) {
            ClientMessage msg = consumer.receive(250);
            if (msg == null) {
               break;
            }
            msg.acknowledge();
         }

         //this is needed to allow to kick-in at least one disk scan
         TimeUnit.MILLISECONDS.sleep(server.getConfiguration().getDiskScanPeriod() * 2);
         session.close();
         locator.close();
         server.stop();

         // Using the code only so the test doesn't fail just because someone edits the log text
         assertTrue(loggerHandler.findText("AMQ222183", MY_ADDRESS), "Expected to find AMQ222183");
         assertTrue(loggerHandler.findText("AMQ221046", MY_ADDRESS), "Expected to find AMQ221046");
         assertFalse(loggerHandler.findText("AMQ222211"), "Expected to not find AMQ222211");
      }
   }

}
