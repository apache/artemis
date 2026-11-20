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
package org.apache.activemq.artemis.tests.db.dbstorage.integration;

import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.db.common.Database;
import org.apache.activemq.artemis.tests.db.dbstorage.statements.AbstractStatementTest;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.TcpProxy;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisabledIf("isNoDatabaseSelected")
@ExtendWith(ParameterizedTestExtension.class)
public class DataWorkerRetryTest extends AbstractStatementTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String QUEUE_NAME = "retryTestQueue" + RandomUtil.randomUUIDString();

   private static final int PROXY_PORT = 45632;

   private TcpProxy tcpProxy;
   private int realPort;

   private static int getDatabasePort(Database database) {
      return switch (database) {
         case POSTGRES -> 5432;
         case MYSQL -> 3306;
         case MSSQL -> 1433;
         case ORACLE -> 1521;
         case DB2 -> 50000;
         default -> throw new IllegalArgumentException("Unsupported database for proxy test: " + database);
      };
   }

   private static String getProxiedJdbcUrl(Database database, int proxyPort) {
      return switch (database) {
         case POSTGRES -> "jdbc:postgresql://localhost:" + proxyPort + "/artemis?user=artemis&password=artemis";
         case MYSQL -> "jdbc:mysql://localhost:" + proxyPort + "/ARTEMIS-TEST?user=root&password=artemis";
         case MSSQL -> "jdbc:sqlserver://localhost:" + proxyPort + ";user=sa;password=ActiveMQ*Artemis";
         case ORACLE -> "jdbc:oracle:thin:system/artemis@localhost:" + proxyPort + ":FREE";
         case DB2 -> "jdbc:db2://localhost:" + proxyPort + "/artemis:user=db2inst1;password=artemis;";
         default -> throw new IllegalArgumentException("Unsupported database for proxy test: " + database);
      };
   }

   @BeforeEach
   @Override
   public void setupTest() throws Exception {
      super.setupTest();
      assumeTrue(database != Database.HSQL, "HSQL uses an in-VM database with no TCP port to proxy");

      realPort = getDatabasePort(database);

      tcpProxy = new TcpProxy("localhost", realPort, PROXY_PORT, false);
      tcpProxy.startProxy();
      runAfter(tcpProxy::stopProxy);

      storageConfiguration.setJdbcConnectionUrl(getProxiedJdbcUrl(database, PROXY_PORT));
      storageConfiguration.setDatabaseMaxRetries(100);
      storageConfiguration.setDatabaseRetryIntervalMillis(500L);

      configuration.addAddressConfiguration(new CoreAddressConfiguration().setName("DLQ").addRoutingType(RoutingType.ANYCAST).addQueueConfiguration(QueueConfiguration.of("DLQ").setRoutingType(RoutingType.ANYCAST)));
      configuration.addAddressConfiguration(new CoreAddressConfiguration().setName("ExpiryQueue").addRoutingType(RoutingType.ANYCAST).addQueueConfiguration(QueueConfiguration.of("ExpiryQueue").setRoutingType(RoutingType.ANYCAST)));

   }

   @TestTemplate
   public void testRetryAfterDatabaseHickup() throws Exception {
      ActiveMQServer server = createServer(true, configuration);
      server.start();

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

      // send some initial messages to verify things work through the proxy
      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
            for (int i = 0; i < 10; i++) {
               producer.send(session.createTextMessage("before-disruption-" + i));
            }
            session.commit();
         }
      }

      checkMessageCounts(10, true);

      ExecutorService service = Executors.newSingleThreadExecutor();
      runAfter(service::shutdownNow);
      CountDownLatch done = new CountDownLatch(1);
      AtomicInteger errors = new AtomicInteger(0);

      // kill the proxy to break DB connections, then restart it to allow retry to succeed
      tcpProxy.stopProxy();

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         service.execute(() -> {

            // send more messages — the DataWorker should retry and succeed
            try (javax.jms.Connection connection = factory.createConnection()) {
               try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
                  MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
                  for (int i = 0; i < 10; i++) {
                     producer.send(session.createTextMessage("after-disruption-" + i));
                  }
                  session.commit();
               }
            } catch (Exception e) {
               logger.warn(e.getMessage());
               errors.incrementAndGet();
            } finally {
               done.countDown();
            }
         });

         // Wait the retry SQL to be logged.
         Wait.assertTrue(() -> loggerHandler.findText("AMQ232001"));
      }

      assertFalse(done.await(100, TimeUnit.MILLISECONDS));
      logger.info("Restarting proxy....");
      tcpProxy.startProxy();
      assertTrue(done.await(60, TimeUnit.SECONDS));
      assertEquals(0, errors.get());

      runAfter(tcpProxy::stopProxy);

      checkMessageCounts(20, false);

      server.stop();
      server.start();

      // verify messages are consumable
      try (javax.jms.Connection connection = factory.createConnection()) {
         connection.start();
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
            for (int i = 0; i < 20; i++) {
               assertNotNull(consumer.receive(5000), "Expected message " + i);
            }
         }
      }

      server.stop();
   }

   @TestTemplate
   public void testCriticalErrorAfterRetriesExhausted() throws Exception {
      storageConfiguration.setDatabaseMaxRetries(2);
      storageConfiguration.setDatabaseRetryIntervalMillis(10L);

      ActiveMQServer server = createServer(true, configuration);
      server.start();

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

      // send initial messages through the proxy
      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
            for (int i = 0; i < 5; i++) {
               producer.send(session.createTextMessage("before-" + i));
            }
            session.commit();
         }
      }

      checkMessageCounts(5, true);

      // kill the proxy and keep it down — retries should exhaust and trigger critical error
      tcpProxy.stopProxy();

      ExecutorService service = Executors.newSingleThreadExecutor();
      runAfter(service::shutdownNow);

      service.execute(() -> {
         // send messages that will fail to persist
         try (javax.jms.Connection connection = factory.createConnection()) {
            try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
               MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
               for (int i = 0; i < 5; i++) {
                  producer.send(session.createTextMessage("should-fail-" + i));
               }
               session.commit();
            }
         } catch (Exception e) {
            // expected — the server may reject or the commit may fail
            logger.info("Expected exception after DB is down: {}", e.getMessage());
         }
      });

      // the server should be stopping or stopped due to critical error
      Wait.assertTrue(() -> !server.isStarted(), 30_000, 100);
   }

   @TestTemplate
   public void testReaderRetryAfterDatabaseHickup() throws Exception {
      int nMessages = 50;

      configuration.getAddressSettings().clear();
      configuration.addAddressSetting("#", new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxSizeMessages(10));
      configuration.addAddressConfiguration(new CoreAddressConfiguration().setName(QUEUE_NAME).addRoutingType(RoutingType.ANYCAST).addQueueConfiguration(QueueConfiguration.of(QUEUE_NAME).setRoutingType(RoutingType.ANYCAST)));

      ActiveMQServer server = createServer(true, configuration);
      server.start();

      Queue queue = server.locateQueue(QUEUE_NAME);
      queue.pause();

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

      try (javax.jms.Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
            for (int i = 0; i < nMessages; i++) {
               producer.send(session.createTextMessage("message-" + i));
               if ((i + 1) % 10 == 0) {
                  session.commit();
               }
            }
            session.commit();
         }
      }

      // kill the proxy — all DB connections are now dead
      tcpProxy.stopProxy();

      // resume the queue — triggers depage reads which will fail and enter the reconnect loop
      queue.resume();

      Thread.sleep(2000);
      logger.info("Restarting proxy for reader retry test...");
      tcpProxy.startProxy();
      runAfter(tcpProxy::stopProxy);

      // consume all messages — the reader should have recovered and depaged
      try (javax.jms.Connection connection = factory.createConnection()) {
         connection.start();
         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
            for (int i = 0; i < nMessages; i++) {
               assertNotNull(consumer.receive(60_000), "Expected message " + i);
               if ((i + 1) % 10 == 0) {
                  session.commit();
               }
            }
            session.commit();
         }
      }

      assertTrue(server.isStarted(), "Server should still be running (no critical error)");
      server.stop();
   }

}
