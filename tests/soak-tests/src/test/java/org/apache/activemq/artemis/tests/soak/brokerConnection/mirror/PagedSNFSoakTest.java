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

package org.apache.activemq.artemis.tests.soak.brokerConnection.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PagedSNFSoakTest extends SoakTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static String body;

   static {
      StringWriter writer = new StringWriter();
      while (writer.getBuffer().length() < 10 * 1024) {
         writer.append("This is a string ..... ");
      }
      body = writer.toString();
   }

   private static final String QUEUE_NAME = "PagedSNFSoakQueue";

   public static final String DC1_NODE_A = "PagedSNFSoakTest/DC1";
   public static final String DC2_NODE_A = "PagedSNFSoakTest/DC2";

   private static final String SNF_QUEUE = "$ACTIVEMQ_ARTEMIS_MIRROR_mirror";

   Process processDC1_node_A;
   Process processDC2_node_A;

   private static String DC1_NODEA_URI = "tcp://localhost:61616";
   private static String DC2_NODEA_URI = "tcp://localhost:61618";

   private static void createServer(String serverName,
                                    String connectionName,
                                    String mirrorURI,
                                    int portOffset) throws Exception {
      File serverLocation = getFileServerLocation(serverName);
      deleteDirectory(serverLocation);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(serverLocation);
      cliCreateServer.setMessageLoadBalancing("ON_DEMAND");
      cliCreateServer.setClustered(false);
      cliCreateServer.setNoWeb(false);
      cliCreateServer.setArgs("--no-stomp-acceptor", "--no-hornetq-acceptor", "--no-mqtt-acceptor", "--no-amqp-acceptor", "--max-hops", "1", "--name", DC1_NODE_A);
      cliCreateServer.addArgs("--queues", QUEUE_NAME);
      cliCreateServer.addArgs("--java-memory", "512M");
      cliCreateServer.setPortOffset(portOffset);
      cliCreateServer.createServer();

      Properties brokerProperties = new Properties();
      brokerProperties.put("AMQPConnections." + connectionName + ".uri", mirrorURI);
      brokerProperties.put("AMQPConnections." + connectionName + ".retryInterval", "1000");
      brokerProperties.put("AMQPConnections." + connectionName + ".type", AMQPBrokerConnectionAddressType.MIRROR.toString());
      brokerProperties.put("AMQPConnections." + connectionName + ".connectionElements.mirror.sync", "false");
      brokerProperties.put("largeMessageSync", "false");

      brokerProperties.put("addressSettings.#.maxSizeMessages", "100");
      brokerProperties.put("addressSettings.#.addressFullMessagePolicy", "PAGING");

      File brokerPropertiesFile = new File(serverLocation, "broker.properties");
      saveProperties(brokerProperties, brokerPropertiesFile);
   }

   @BeforeAll
   public static void createServers() throws Exception {
      createServer(DC1_NODE_A, "mirror", DC2_NODEA_URI, 0);
      createServer(DC2_NODE_A, "mirror", DC1_NODEA_URI, 2);
   }

   @BeforeEach
   public void cleanupServers() {
      cleanupData(DC1_NODE_A);
      cleanupData(DC2_NODE_A);
   }

   @Test
   @Timeout(240)
   public void testAMQP() throws Exception {
      testAccumulateAndSend("AMQP");
   }

   @Test
   @Timeout(240)
   public void testCORE() throws Exception {
      testAccumulateAndSend("CORE");
   }

   @Test
   @Timeout(240)
   public void testOPENWIRE() throws Exception {
      testAccumulateAndSend("OPENWIRE");
   }

   private void testAccumulateAndSend(final String protocol) throws Exception {
      startDC1();

      final int numberOfMessages = 400;

      ConnectionFactory connectionFactoryDC1A = CFUtil.createConnectionFactory(protocol, DC1_NODEA_URI);
      ConnectionFactory connectionFactoryDC2A = CFUtil.createConnectionFactory(protocol, DC2_NODEA_URI);

      SimpleManagement simpleManagementDC1A = new SimpleManagement(DC1_NODEA_URI, null, null);
      SimpleManagement simpleManagementDC2A = new SimpleManagement(DC2_NODEA_URI, null, null);

      try (Connection connection = connectionFactoryDC1A.createConnection()) {
         connection.start();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(QUEUE_NAME);
         send(session, queue, numberOfMessages, null);
         consume(session, numberOfMessages);
         send(session, queue, numberOfMessages, null);

         startDC2();

         consume(session, numberOfMessages);
         for (int i = 0; i < 20; i++) {
            final int loopI = i;
            System.err.println("Sent " + i);
            logger.info("Sent {}", i);
            send(session, queue, 10, m -> m.setIntProperty("loop", loopI));
            consume(session, 10);
         }
         send(session, queue, numberOfMessages, null);
      }

      Wait.assertEquals((long) numberOfMessages, () -> simpleManagementDC1A.getMessageCountOnQueue(QUEUE_NAME), 5000, 100);

      Wait.assertEquals((long) 0, () -> getMessageCount(simpleManagementDC1A, SNF_QUEUE), 5_000, 100);
      Wait.assertEquals((long) numberOfMessages, () -> getMessageCount(simpleManagementDC2A, QUEUE_NAME), 5_000, 100);

      try (Connection connection = connectionFactoryDC1A.createConnection()) {
         connection.start();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

         consume(session, numberOfMessages);
      }

      Wait.assertEquals((long) 0, () -> simpleManagementDC1A.getMessageCountOnQueue(SNF_QUEUE), 5000, 100);
      Wait.assertEquals((long) 0, () -> simpleManagementDC2A.getMessageCountOnQueue(SNF_QUEUE), 5000, 100);

      Wait.assertEquals((long) 0, () -> simpleManagementDC1A.getMessageCountOnQueue(QUEUE_NAME), 5000, 100);
      Wait.assertEquals((long) 0, () -> simpleManagementDC2A.getMessageCountOnQueue(QUEUE_NAME), 5000, 100);

   }

   @Test
   public void testLargeBatches() throws Exception {
      String protocol = "AMQP";
      startDC1();
      startDC2();

      final int numberOfMessages = 5_000;
      final int batchSize = 1_000;
      final int numberOfBatches = 4;

      ConnectionFactory[] cfs = new ConnectionFactory[]{CFUtil.createConnectionFactory(protocol, DC1_NODEA_URI), CFUtil.createConnectionFactory(protocol, DC2_NODEA_URI)};
      SimpleManagement[] sm = new SimpleManagement[] {new SimpleManagement(DC1_NODEA_URI, null, null), new SimpleManagement(DC2_NODEA_URI, null, null)};

      for (int nbatch = 0; nbatch < numberOfBatches; nbatch++) {

         // I want to make permutations between which server to consume, and which server to produce
         // I am using a bit operation to make that permutation in a easier way
         int serverToProduce = ((nbatch & 1) > 0) ? 1 : 0;
         int serverToConsume = ((nbatch & 2) > 0) ? 1 : 0;

         logger.debug("Batch {}, sending on server {}. consuming on server {}", nbatch, serverToProduce, serverToConsume);

         try (Connection connection = cfs[serverToProduce].createConnection()) {
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
            for (int i = 0; i < numberOfMessages; i++) {
               producer.send(session.createTextMessage("msg " + i));
               if (i > 0 && i % batchSize == 0) {
                  logger.debug("Commit send {}", i);
                  session.commit();
               }
            }
            session.commit();
         }

         for (SimpleManagement s : sm) {
            logger.debug("Checking counts on SNF for {}", s.getUri());
            Wait.assertEquals((long) 0, () -> s.getMessageCountOnQueue(SNF_QUEUE), 120_000, 100);
            logger.debug("Checking counts on {} on {}", QUEUE_NAME, s.getUri());
            Wait.assertEquals((long) numberOfMessages, () -> s.getMessageCountOnQueue(QUEUE_NAME), 60_000, 100);
         }

         try (Connection connection = cfs[serverToConsume].createConnection()) {
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
            for (int i = 0; i < numberOfMessages; i++) {
               Message message = consumer.receive(5000);
               assertNotNull(message);
               if (i > 0 && i % batchSize == 0) {
                  logger.debug("Commit consume {}", i);
                  session.commit();
               }
            }
            session.commit();
         }

         for (SimpleManagement s : sm) {
            logger.debug("Checking 0 counts on SNF for {}", s.getUri());
            Wait.assertEquals((long) 0, () -> s.getMessageCountOnQueue(SNF_QUEUE), 120_000, 100);
            logger.debug("Checking for empty queue on {}", s.getUri());
            Wait.assertEquals((long) 0, () -> s.getMessageCountOnQueue(QUEUE_NAME), 60_000, 100);
         }
      }
   }

   private static void consume(Session session, int numberOfMessages) throws JMSException {
      Queue queue = session.createQueue(QUEUE_NAME);
      try (MessageConsumer consumer = session.createConsumer(queue)) {
         for (int i = 0; i < numberOfMessages; i++) {
            TextMessage message = (TextMessage) consumer.receive(10_000);
            assertNotNull(message);
            assertEquals(body, message.getText());
            assertEquals(i, message.getIntProperty("id"));
            logger.debug("received {}", i);
            if ((i + 1) % 10 == 0) {
               session.commit();
            }
         }
         session.commit();
      }
   }

   private interface Receive<T> {
      void accept(T message) throws Exception;
   }

   private static void send(Session session,
                            Queue queue,
                            int numberOfMessages,
                            Receive<Message> setter) throws JMSException {
      try (MessageProducer producer = session.createProducer(queue)) {
         for (int i = 0; i < numberOfMessages; i++) {
            TextMessage message = session.createTextMessage(body);
            message.setIntProperty("id", i);
            logger.debug("send {}", i);
            if (setter != null) {
               try {
                  setter.accept(message);
               } catch (Throwable ignored) {
               }
            }
            producer.send(message);
            if (i % 10 == 0) {
               logger.debug("Sent {} messages", i);
               session.commit();
            }
         }
         session.commit();
      }
   }

   int getNumberOfLargeMessages(String serverName) throws Exception {
      File lmFolder = new File(getServerLocation(serverName) + "/data/large-messages");
      assertTrue(lmFolder.exists());
      return lmFolder.list().length;
   }

   private void startDC1() throws Exception {
      processDC1_node_A = startServer(DC1_NODE_A, -1, -1, new File(getServerLocation(DC1_NODE_A), "broker.properties"));
      ServerUtil.waitForServerToStart(0, 10_000);
   }

   private void stopDC1() throws Exception {
      processDC1_node_A.destroyForcibly();
      assertTrue(processDC1_node_A.waitFor(10, TimeUnit.SECONDS));
   }

   private void stopDC2() throws Exception {
      processDC2_node_A.destroyForcibly();
      assertTrue(processDC2_node_A.waitFor(10, TimeUnit.SECONDS));
   }

   private void startDC2() throws Exception {
      processDC2_node_A = startServer(DC2_NODE_A, -1, -1, new File(getServerLocation(DC2_NODE_A), "broker.properties"));
      ServerUtil.waitForServerToStart(2, 10_000);
   }
}