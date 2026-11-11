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
import javax.jms.TransactionRolledBackException;
import java.io.File;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdempotentACKTest extends SoakTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static String largeBody;

   static {
      StringWriter writer = new StringWriter();
      while (writer.getBuffer().length() < 1024 * 1024) {
         writer.append("This is a large string ..... ");
      }
      largeBody = writer.toString();
   }

   private static final String QUEUE_NAME = "myQueue";

   public static final String DC1_NODE_A = "idempotentMirror/DC1";
   public static final String DC2_NODE_A = "idempotentMirror/DC2";

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
      cliCreateServer.setNoWeb(true);
      cliCreateServer.setArgs("--no-stomp-acceptor", "--no-hornetq-acceptor", "--no-mqtt-acceptor", "--no-amqp-acceptor", "--max-hops", "1", "--name", DC1_NODE_A);
      cliCreateServer.addArgs("--queues", QUEUE_NAME);
      cliCreateServer.setPortOffset(portOffset);
      cliCreateServer.createServer();

      Properties brokerProperties = new Properties();
      brokerProperties.put("AMQPConnections." + connectionName + ".uri", mirrorURI);
      brokerProperties.put("AMQPConnections." + connectionName + ".retryInterval", "1000");
      brokerProperties.put("AMQPConnections." + connectionName + ".type", AMQPBrokerConnectionAddressType.MIRROR.toString());
      brokerProperties.put("AMQPConnections." + connectionName + ".connectionElements.mirror.sync", "false");
      brokerProperties.put("largeMessageSync", "false");
      File brokerPropertiesFile = new File(serverLocation, "broker.properties");
      saveProperties(brokerProperties, brokerPropertiesFile);
   }

   @BeforeAll
   public static void createServers() throws Exception {
      createServer(DC1_NODE_A, "mirror", DC2_NODEA_URI, 0);
      createServer(DC2_NODE_A, "mirror", DC1_NODEA_URI, 2);
   }

   private void startServers() throws Exception {
      processDC1_node_A = startServer(DC1_NODE_A, -1, -1, new File(getServerLocation(DC1_NODE_A), "broker.properties"));
      processDC2_node_A = startServer(DC2_NODE_A, -1, -1, new File(getServerLocation(DC2_NODE_A), "broker.properties"));

      ServerUtil.waitForServerToStart(0, 10_000);
      ServerUtil.waitForServerToStart(2, 10_000);
   }

   @BeforeEach
   public void cleanupServers() {
      cleanupData(DC1_NODE_A);
      cleanupData(DC2_NODE_A);
   }

   private void transactSend(Session session, MessageProducer producer, int initialCounter, int numberOfMessages, int largeMessageFactor) throws Throwable {
      try {
         for (int i = initialCounter; i < initialCounter + numberOfMessages; i++) {
            TextMessage message;
            String unique = "Unique " + i;
            if (i % largeMessageFactor == 0) {
               message = session.createTextMessage(largeBody);
               message.setBooleanProperty("large", true);
            } else {
               message = session.createTextMessage("this is small");
               message.setBooleanProperty("large", false);
            }
            message.setIntProperty("i", i);
            message.setStringProperty(org.apache.activemq.artemis.api.core.Message.HDR_DUPLICATE_DETECTION_ID.toString(), unique);
            producer.send(message);
         }
         session.commit();
      } catch (JMSException e) {
         if (e instanceof TransactionRolledBackException && e.getMessage().contains("Duplicate message detected")) {
            logger.debug("OK Exception {}", e.getMessage(), e);
            return; // ok
         } else {
            logger.warn("Not OK Exception {}", e.getMessage(), e);
            throw e;
         }
      }
   }


   @Test
   public void testRandomProtocol() throws Exception {
      testACKs(randomProtocol());
   }

   private void testACKs(final String protocol) throws Exception {
      startServers();

      final int consumers = 10;
      final int numberOfMessages = 1000;
      final int largeMessageFactor = 30;
      final int messagesPerConsumer = 30;

      // Just a reminder: if you change number on this test, this needs to be true:
      assertEquals(0, numberOfMessages % consumers, "Invalid test config");

      AtomicBoolean running = new AtomicBoolean(true);
      runAfter(() -> running.set(false));

      String snfQueue = "$ACTIVEMQ_ARTEMIS_MIRROR_mirror";

      ExecutorService executor = Executors.newFixedThreadPool(consumers);
      runAfter(executor::shutdownNow);

      final ConnectionFactory connectionFactoryDC1A;
      connectionFactoryDC1A = CFUtil.createConnectionFactory(protocol, DC1_NODEA_URI);
      CountDownLatch sendDone = new CountDownLatch(1);
      CountDownLatch killSend = new CountDownLatch(1);

      executor.execute(() -> {
         int messagesSent = 0;
         while (running.get() && messagesSent < numberOfMessages) {
            try (Connection connection = connectionFactoryDC1A.createConnection()) {
               Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
               Queue queue = session.createQueue(QUEUE_NAME);
               MessageProducer producer = session.createProducer(queue);
               if (messagesSent < 100) {
                  transactSend(session, producer, messagesSent, 1, 1);
                  messagesSent++;
                  logger.debug("Sent {}", messagesSent);
                  if (messagesSent == 100) {
                     logger.debug("Signal to kill");
                     killSend.countDown();
                  }
               } else {
                  transactSend(session, producer, messagesSent, 100, largeMessageFactor);
                  messagesSent += 100;
                  logger.debug("Sent {}", messagesSent);
               }
            } catch (Throwable e) {
               logger.warn(e.getMessage(), e);
               try {
                  Thread.sleep(100);
               } catch (Throwable ignored) {
               }
            }
         }
         sendDone.countDown();
      });

      assertTrue(killSend.await(50, TimeUnit.SECONDS));

      restartDC1_ServerA();

      assertTrue(sendDone.await(50, TimeUnit.SECONDS));

      SimpleManagement simpleManagementDC1A = new SimpleManagement(DC1_NODEA_URI, null, null);
      SimpleManagement simpleManagementDC2A = new SimpleManagement(DC2_NODEA_URI, null, null);

      Wait.assertEquals(0, () -> getMessageCount(simpleManagementDC1A, snfQueue));
      Wait.assertEquals(numberOfMessages, () -> getMessageCount(simpleManagementDC1A, QUEUE_NAME));
      Wait.assertEquals(numberOfMessages, () -> getMessageCount(simpleManagementDC2A, QUEUE_NAME));

      CountDownLatch latchKill = new CountDownLatch(consumers);

      CountDownLatch latchDone = new CountDownLatch(consumers);

      Runnable runnableConsumer = () -> {
         int messagesConsumed = 0;
         while (running.get() && messagesConsumed < messagesPerConsumer) {
            try (Connection connection = connectionFactoryDC1A.createConnection()) {
               Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
               Queue queue = session.createQueue(QUEUE_NAME);
               MessageConsumer consumer = session.createConsumer(queue);
               connection.start();
               while (messagesConsumed < messagesPerConsumer) {
                  Message message = consumer.receive(100);
                  if (message instanceof TextMessage) {
                     logger.debug("message received={}", message);
                     session.commit();
                     messagesConsumed++;
                     logger.debug("Received {}", messagesConsumed);
                     if (messagesConsumed == 10) {
                        latchKill.countDown();
                     }
                  } else {
                     logger.info("no messages...");
                  }
               }
            } catch (Throwable e) {
               logger.warn(e.getMessage(), e);
               try {
                  Thread.sleep(100);
               } catch (Throwable ignored) {
               }
            }
         }
         latchDone.countDown();
      };

      for (int i = 0; i < consumers; i++) {
         executor.execute(runnableConsumer);
      }

      assertTrue(latchKill.await(10, TimeUnit.SECONDS));

      restartDC1_ServerA();

      assertTrue(latchDone.await(4, TimeUnit.MINUTES));

      long flushedMessages = 0;

      try (Connection connection = connectionFactoryDC1A.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(QUEUE_NAME);
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
         while (consumer.receive(500) != null) {
            flushedMessages++;
         }
         session.commit();
      }

      logger.debug("Flushed {}", flushedMessages);

      // after all flushed messages, we should have 0 messages on both nodes

      Wait.assertEquals(0, () -> getMessageCount(simpleManagementDC1A, snfQueue));
      Wait.assertEquals(0, () -> getMessageCount(simpleManagementDC2A, snfQueue));
      Wait.assertEquals(0, () -> getMessageCount(simpleManagementDC1A, QUEUE_NAME));
      Wait.assertEquals(0, () -> getMessageCount(simpleManagementDC2A, QUEUE_NAME));
   }

   private void restartDC1_ServerA() throws Exception {
      processDC1_node_A.destroyForcibly();
      assertTrue(processDC1_node_A.waitFor(10, TimeUnit.SECONDS));
      processDC1_node_A = startServer(DC1_NODE_A, -1, -1, new File(getServerLocation(DC1_NODE_A), "broker.properties"));
      ServerUtil.waitForServerToStart(0, 10_000);
   }
}
