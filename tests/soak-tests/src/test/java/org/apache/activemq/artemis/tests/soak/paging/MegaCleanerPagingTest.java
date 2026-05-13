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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PageCleanup should still be able to perform it well.
 */
// supressing because the helper methods need to be public as they are called from a spawned java
@SuppressWarnings("JUnit4TestNotRun")
public class MegaCleanerPagingTest extends ActiveMQTestBase {

   // set this to true to have the test to be called directly
   // useful for debugging, keeping it false
   private static final boolean DIRECT_CALL = false;

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int OK = 35; // Abitrary code the spawn must return if ok.

   @Test
   public void testCleanup() throws Throwable {
      if (DIRECT_CALL) {
         internalTestRegular();
      } else {
         remoteCall("internalTestRegular");
      }
   }

   @Test
   public void testCleanupMidstream() throws Throwable {
      if (DIRECT_CALL) {
         internalTestMidstream();
      } else {
         remoteCall("internalTestMidstream");
      }
   }


   @Test
   public void testRestart() throws Throwable {
      remoteCall("populate");
      logger.debug("Resuming...");
      remoteCall("resume");
      logger.debug("....done");
   }

   private void remoteCall(String methodName) throws Exception {
      // Using a spawn to limit memory consumption to the test
      Process process = SpawnedVMSupport.spawnVM(MegaCleanerPagingTest.class.getName(), new String[]{"-Xmx512M"}, getTestDir(), methodName);
      logger.debug("process PID {}", process.pid());
      assertTrue(process.waitFor(10, TimeUnit.MINUTES));
      assertEquals(OK, process.exitValue());
   }

   // I am using a separate VM to limit memory..
   // and the test will pass the parameters needed for the JUnit Class to be able to proceed
   public static void main(String[] arg) {
      try {
         MegaCleanerPagingTest megaCleanerPagingTest = new MegaCleanerPagingTest();
         megaCleanerPagingTest.setTestDir(arg[0]);
         String methodName = arg[1];


         Method method = megaCleanerPagingTest.getClass().getMethod(methodName);
         method.invoke(megaCleanerPagingTest);
         System.exit(OK);
      } catch (Throwable e) {
         e.printStackTrace();
         logger.warn(e.getMessage(), e);
         System.exit(-1);
      }
   }

   public void internalTestMidstream() throws Throwable {
      internalTest(true);
   }

   public void internalTestRegular() throws Throwable {
      internalTest(false);
   }

   public void populate() throws Throwable {
      ActiveMQServer server = createServer(true, true);
      server.getConfiguration().clearAddressSettings();
      server.getConfiguration().addAddressSetting("#", new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxSizeMessages(1000).setPageSizeBytes(10 * 1024 * 1024));
      server.start();

      // if I didn't limit the memory on this test, the NUMBER_OF_MESSAGES would have to be something huge such as 500_000, and that could be a moving target
      // if more memory is set to the JUNIT Runner.
      // This is the main reason we limit memory on this test
      int NUMBER_OF_MESSAGES = 100_000;

      String queueName = "testPageAndDepage";

      server.addAddressInfo(new AddressInfo(queueName).addRoutingType(RoutingType.ANYCAST).setAutoCreated(false));
      server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));

      ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      Connection connection = factory.createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      Queue queue = session.createQueue(queueName);
      MessageProducer producer = session.createProducer(queue);


      org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(queueName);
      assertNotNull(serverQueue);
      serverQueue.getPagingStore().startPaging();

      ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616?consumerWindowSize=0");
      assertEquals(0, ((ActiveMQConnectionFactory)cf).getServerLocator().getConsumerWindowSize());

      final int SIZE = 10 * 1024;
      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         producer.send(session.createTextMessage(createBuffer(i, SIZE)));
         if (i % 1000 == 0) {
            logger.debug("sent {} messages", i);
            session.commit();
         }
      }
      session.commit();


      PagingStoreImpl store = (PagingStoreImpl) server.getPagingManager().getPageStore(SimpleString.of(queueName));
      store.disableCleanup();

      MessageConsumer consumer = session.createConsumer(queue);
      connection.start();

      for (int i = 0; i < NUMBER_OF_MESSAGES / 2; i++) {
         TextMessage message = (TextMessage) consumer.receive(5000);
         assertNotNull(message);
         assertEquals(createBuffer(i, SIZE), message.getText());

         if (i % 1000 == 0) {
            logger.debug("received {} messages", i);
            session.commit();
         }
      }
      session.commit();
      connection.close();

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         server.stop();
         assertFalse(loggerHandler.findText("AMQ222023")); // error associated with OME
         assertFalse(loggerHandler.findText("AMQ222010")); // critical IO Error
      }
   }


   public void resume() throws Throwable {
      ActiveMQServer server = createServer(true, true);
      server.getConfiguration().clearAddressSettings();
      server.getConfiguration().addAddressSetting("#", new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxSizeMessages(1000).setPageSizeBytes(10 * 1024 * 1024));
      server.start();

      // if I didn't limit the memory on this test, the NUMBER_OF_MESSAGES would have to be something huge such as 500_000, and that could be a moving target
      // if more memory is set to the JUNIT Runner.
      // This is the main reason we limit memory on this test
      int NUMBER_OF_MESSAGES = 100_000;

      String queueName = "testPageAndDepage";

      ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      Connection connection = factory.createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      Queue queue = session.createQueue(queueName);


      org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(queueName);
      assertNotNull(serverQueue);
      serverQueue.getPagingStore().startPaging();

      ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616?consumerWindowSize=0");
      assertEquals(0, ((ActiveMQConnectionFactory)cf).getServerLocator().getConsumerWindowSize());

      final int SIZE = 10 * 1024;
      session.commit();


      PagingStoreImpl store = (PagingStoreImpl) server.getPagingManager().getPageStore(SimpleString.of(queueName));
      store.disableCleanup();

      MessageConsumer consumer = session.createConsumer(queue);
      connection.start();

      for (int i = NUMBER_OF_MESSAGES / 2; i < NUMBER_OF_MESSAGES; i++) {
         TextMessage message = (TextMessage) consumer.receive(5000);
         assertNotNull(message);
         assertEquals(createBuffer(i, SIZE), message.getText());

         if (i % 1000 == 0) {
            logger.debug("received {} messages", i);
            session.commit();
         }
      }
      session.commit();
      assertNull(consumer.receiveNoWait());
      connection.close();

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         store.getCursorProvider().resumeCleanup();

         server.stop();
         assertFalse(loggerHandler.findText("AMQ222023")); // error associated with OME
         assertFalse(loggerHandler.findText("AMQ222010")); // critical IO Error
      }
   }

   public void internalTest(boolean midstream) throws Throwable {
      ActiveMQServer server = createServer(true, true);
      server.getConfiguration().clearAddressSettings();
      server.getConfiguration().addAddressSetting("#", new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxSizeMessages(1000).setPageSizeBytes(10 * 1024 * 1024));
      server.start();

      // if I didn't limit the memory on this test, the NUMBER_OF_MESSAGES would have to be something huge such as 500_000, and that could be a moving target
      // if more memory is set to the JUNIT Runner.
      // This is the main reason we limit memory on this test
      int NUMBER_OF_MESSAGES = 50_000;

      String queueName = "testPageAndDepage";

      server.addAddressInfo(new AddressInfo(queueName).addRoutingType(RoutingType.ANYCAST).setAutoCreated(false));
      server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));

      ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      Connection connection = factory.createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      Queue queue = session.createQueue(queueName);
      MessageProducer producer = session.createProducer(queue);


      org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(queueName);
      assertNotNull(serverQueue);
      serverQueue.getPagingStore().startPaging();

      ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616?consumerWindowSize=0");
      assertEquals(0, ((ActiveMQConnectionFactory)cf).getServerLocator().getConsumerWindowSize());
      Connection slowConnection = cf.createConnection();
      Session slowSession = slowConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      Queue slowQueue = slowSession.createQueue(queueName);
      MessageProducer slowProducer = slowSession.createProducer(slowQueue);
      if (midstream) {
         slowProducer.send(session.createTextMessage("slow"));
      }
      slowConnection.start();
      MessageConsumer slowConsumer = slowSession.createConsumer(slowQueue);
      TextMessage slowMessage;

      if (midstream) {
         slowMessage = (TextMessage) slowConsumer.receive(5000);
         assertNotNull(slowMessage);
         assertEquals("slow", slowMessage.getText());
      } else {
         slowMessage = (TextMessage) slowConsumer.receiveNoWait();
         assertNull(slowMessage);
      }


      final int SIZE = 10 * 1024;
      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         producer.send(session.createTextMessage(createBuffer(i, SIZE)));
         if (i % 1000 == 0) {
            logger.debug("sent {} messages", i);
            session.commit();
         }
      }
      session.commit();


      PagingStoreImpl store = (PagingStoreImpl) server.getPagingManager().getPageStore(SimpleString.of(queueName));
      store.disableCleanup();

      MessageConsumer consumer = session.createConsumer(queue);
      connection.start();

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         TextMessage message = (TextMessage) consumer.receive(5000);
         assertNotNull(message);
         assertEquals(createBuffer(i, SIZE), message.getText());

         if (i % 1000 == 0) {
            logger.debug("received {} messages", i);
            session.commit();
         }
      }
      session.commit();
      assertNull(consumer.receiveNoWait());
      connection.close();
      slowConnection.close();

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         store.enableCleanup();
         store.getCursorProvider().scheduleCleanup();
         if (!midstream) {
            Wait.assertFalse(store::isPaging);
         }
         server.stop();
         assertFalse(loggerHandler.findText("AMQ222023")); // error associated with OME
         assertFalse(loggerHandler.findText("AMQ222010")); // critical IO Error
      }
   }


   String createBuffer(int msgNumber, int size) {
      StringBuilder sb = new StringBuilder();
      sb.append("message " + msgNumber + " ");
      while (sb.length() < size) {
         sb.append(" Lorem Ipsum Whatever it's saying in there... ");
      }
      return sb.toString();
   }
}