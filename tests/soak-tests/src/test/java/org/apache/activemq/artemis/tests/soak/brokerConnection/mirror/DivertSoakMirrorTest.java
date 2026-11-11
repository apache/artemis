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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.TestParameters;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DivertSoakMirrorTest extends SoakTestBase {

   private static final String TEST_NAME = "DIVERT_MIRROR";

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String SNF_QUEUE = "$ACTIVEMQ_ARTEMIS_MIRROR_mirror";

   private static String DIVERT_CONFIGURATION_FILE_LOCATION = "DivertSoakMirrorTest-divert.txt";

   // Set this to true and log4j will be configured with some relevant log.trace for the AckManager at the server's
   private static final boolean TRACE_LOGS = Boolean.parseBoolean(TestParameters.testProperty(TEST_NAME, "TRACE_LOGS", "trace"));
   private static final boolean REUSE_SERVERS = Boolean.parseBoolean(TestParameters.testProperty(TEST_NAME, "REUSE_SERVERS", "false"));

   /*
    * Time each consumer takes to process a message received to allow some messages accumulating.
    * This sleep happens right before the commit.
    */
   private static final String QUEUE_NAME_LIST = "queueTest,Div,Div.0,Div.1,Div.2";
   private static final String QUEUE_NAME = "queueTest";

   public static final String DC1_NODE = "DivertSoakMirrorTest/DC1";
   public static final String DC2_NODE = "DivertSoakMirrorTest/DC2";

   volatile Process processDC1;
   volatile Process processDC2;

   @AfterEach
   public void destroyServers() throws Exception {
      if (processDC1 != null) {
         processDC1.destroyForcibly();
         processDC1.waitFor(1, TimeUnit.MINUTES);
         processDC1 = null;
      }
      if (processDC2 != null) {
         processDC2.destroyForcibly();
         processDC2.waitFor(1, TimeUnit.MINUTES);
         processDC2 = null;
      }
   }

   private static final String DC1_IP = "localhost:61616";
   private static final String DC2_IP = "localhost:61618";

   private static String uri(String ip) {
      return "tcp://" + ip;
   }

   private void startServers() throws Exception {
      processDC2 = startServer(DC2_NODE, -1, -1, new File(getServerLocation(DC2_NODE), "broker.properties"));
      processDC1 = startServer(DC1_NODE, -1, -1, new File(getServerLocation(DC1_NODE), "broker.properties"));
      ServerUtil.waitForServerToStart(2, 10_000);
      ServerUtil.waitForServerToStart(0, 10_000);

      // Waiting both nodes to be connected
      //Wait.assertTrue(() -> FileUtil.find(new File(getFileServerLocation(DC1_NODE), "log/artemis.log"), l -> l.contains("AMQ111003")), 5000, 100);
      //Wait.assertTrue(() -> FileUtil.find(new File(getFileServerLocation(DC2_NODE), "log/artemis.log"), l -> l.contains("AMQ111003")), 5000, 100);
   }

   private static void createMirroredServer(boolean paging,
                                            String serverName,
                                            String connectionName,
                                            String mirrorURI,
                                            int portOffset,
                                            boolean exclusiveDivert) throws Exception {
      File serverLocation = getFileServerLocation(serverName);
      if (REUSE_SERVERS && serverLocation.exists()) {
         deleteDirectory(new File(serverLocation, "data"));
         return;
      }
      deleteDirectory(serverLocation);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setAllowAnonymous(true).setArtemisInstance(serverLocation);
      cliCreateServer.setArgs("--no-stomp-acceptor", "--no-hornetq-acceptor", "--no-mqtt-acceptor", "--no-amqp-acceptor", "--max-hops", "1", "--name", DC1_NODE);
      cliCreateServer.addArgs("--queues", QUEUE_NAME_LIST);
      cliCreateServer.setPortOffset(portOffset);
      cliCreateServer.setClustered(false);

      cliCreateServer.createServer();

      Properties brokerProperties = new Properties();
      brokerProperties.put("messageExpiryScanPeriod", "1000");
      brokerProperties.put("AMQPConnections." + connectionName + ".uri", mirrorURI);
      brokerProperties.put("AMQPConnections." + connectionName + ".retryInterval", "1000");
      brokerProperties.put("AMQPConnections." + connectionName + ".type", AMQPBrokerConnectionAddressType.MIRROR.toString());
      brokerProperties.put("AMQPConnections." + connectionName + ".connectionElements.mirror.sync", "false");
      brokerProperties.put("largeMessageSync", "false");

      if (paging) {
         brokerProperties.put("addressSettings.#.maxSizeMessages", "50");
         brokerProperties.put("addressSettings.#.maxReadPageMessages", "2000");
         brokerProperties.put("addressSettings.#.maxReadPageBytes", "-1");
         brokerProperties.put("addressSettings.#.prefetchPageMessages", "500");
         brokerProperties.put("mirrorPageTransaction", "true");
      }

      File brokerPropertiesFile = new File(serverLocation, "broker.properties");
      saveProperties(brokerProperties, brokerPropertiesFile);

      File brokerXml = new File(serverLocation, "/etc/broker.xml");

      assertTrue(brokerXml.exists());
      assertTrue(FileUtil.findReplace(brokerXml, "<page-size-bytes>10M</page-size-bytes>", "<page-size-bytes>100K</page-size-bytes>"));
      assertTrue(FileUtil.findReplace(brokerXml, "amqpDuplicateDetection=true;", "amqpDuplicateDetection=true;ackManagerFlushTimeout=" + TimeUnit.MINUTES.toMillis(10) + ";"));

      installDivert(brokerXml, exclusiveDivert);

      if (TRACE_LOGS) {
         replaceLogs(serverLocation);
      }

   }

   private static void installDivert(File brokerXml, boolean exclusive) throws Exception {
      String divertConfig = FileUtil.readFile(DivertSoakMirrorTest.class.getClassLoader().getResourceAsStream(DIVERT_CONFIGURATION_FILE_LOCATION));
      assertNotNull(divertConfig);
      divertConfig = divertConfig.replaceAll("BOOLEAN_VALUE", String.valueOf(exclusive));
      assertTrue(FileUtil.findReplace(brokerXml, "</acceptors>", "</acceptors>\n" + divertConfig));
   }

   private static void replaceLogs(File serverLocation) throws Exception {
      File log4j = new File(serverLocation, "/etc/log4j2.properties");
      assertTrue(FileUtil.findReplace(log4j, "logger.artemis_utils.level=INFO", "logger.artemis_utils.level=INFO\n" + "\n" + "logger.divert.name=org.apache.activemq.artemis.core.server.impl.DivertImpl\n" + "logger.divert.level=TRACE\n" + "logger.target.name=org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerTarget\n" + "logger.target.level=TRACE\n" + "appender.console.filter.threshold.type = ThresholdFilter\n" + "appender.console.filter.threshold.level = INFO"));
   }

   private static void replaceLogsOnServer2(File serverLocation) throws Exception {
      File log4j = new File(serverLocation, "/etc/log4j2.properties");
      assertTrue(FileUtil.findReplace(log4j, "logger.artemis_utils.level=INFO", "logger.artemis_utils.level=INFO\n" + "\n" + "logger.divert.name=org.apache.activemq.artemis.core.server.impl.DivertImpl\n" + "logger.divert.level=TRACE\n" + "logger.target.name=org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource\n" + "logger.target.level=DEBUG\n" + "appender.console.filter.threshold.type = ThresholdFilter\n" + "appender.console.filter.threshold.level = TRACE"));
   }

   public static void createRealServers(boolean paging, boolean divert, boolean exclusiveDivert) throws Exception {
      createMirroredServer(paging, DC1_NODE, "mirror", uri(DC2_IP), 0, exclusiveDivert);
      createMirroredServer(paging, DC2_NODE, "mirror", uri(DC1_IP), 2, exclusiveDivert);
   }

   @Test
   public void testDivertsExclusive() throws Exception {
      testDiverts(true, false);
   }

   @Test
   public void testDivertsExclusiveNoConsume() throws Exception {
      testDiverts(true, false, false);
   }

   @Test
   public void testDivertsNonExclusive() throws Exception {
      testDiverts(false, false);
   }

   @Test
   public void testDivertsExclusiveSwitchOver() throws Exception {
      testDiverts(true, true);
   }

   @Test
   public void testDivertsNonExclusiveSwitchOver() throws Exception {
      testDiverts(false, true);
   }

   private void testDiverts(boolean exclusive, boolean switchOver) throws Exception {
      testDiverts(exclusive, switchOver, true);
   }

   private void testDiverts(boolean exclusive, boolean switchOver, boolean consume) throws Exception {
      String protocol = "OPENWIRE";
      createRealServers(false, true, exclusive);

      SimpleManagement managementDC1 = new SimpleManagement(uri(DC1_IP), null, null);
      SimpleManagement managementDC2 = new SimpleManagement(uri(DC2_IP), null, null);

      //replaceLogsOnServer2(getFileServerLocation(DC2_NODE));
      startServers();

      ConnectionFactory connectionFactoryDC1A = CFUtil.createConnectionFactory(protocol, uri(DC1_IP));
      ConnectionFactory connectionFactoryConsume;

      if (switchOver) {
         logger.info("Switching over consumption to DC2");
         connectionFactoryConsume = CFUtil.createConnectionFactory(protocol, uri(DC2_IP));
      } else {
         connectionFactoryConsume = CFUtil.createConnectionFactory(protocol, uri(DC1_IP));
      }

      int numberOfDiverts = 3;
      long messagesPerDivert = 100;
      long messages = numberOfDiverts * messagesPerDivert;

      try (Connection connection = connectionFactoryDC1A.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

         MessageProducer producer = session.createProducer(session.createQueue("Div"));

         for (int i = 0; i < messages; i++) {
            TextMessage message = session.createTextMessage("i = " + i);
            message.setIntProperty("div", (i % numberOfDiverts));
            message.setIntProperty("i", i);
            producer.setPriority(RandomUtil.randomInterval(0, 9));
            producer.send(message);
         }
         session.commit();
      }

      Wait.assertEquals(0L, () -> getMessageCount(managementDC1, SNF_QUEUE), 5000, 100);
      Wait.assertEquals(0L, () -> getMessageCount(managementDC2, SNF_QUEUE), 5000, 100);

      Wait.assertEquals(0L, () -> getMessageCount(managementDC1, QUEUE_NAME), 5000, 100);
      Wait.assertEquals(exclusive ? 0L : messagesPerDivert * numberOfDiverts, () -> getMessageCount(managementDC1, "Div"), 5000, 100);
      Wait.assertEquals(messagesPerDivert, () -> getMessageCount(managementDC1, "Div.0"), 5000, 100);
      Wait.assertEquals(messagesPerDivert, () -> getMessageCount(managementDC1, "Div.1"), 5000, 100);
      Wait.assertEquals(messagesPerDivert, () -> getMessageCount(managementDC1, "Div.2"), 5000, 100);

      Wait.assertEquals(0L, () -> getMessageCount(managementDC2, QUEUE_NAME), 5000, 100);
      Wait.assertEquals(exclusive ? 0L : messagesPerDivert * numberOfDiverts, () -> getMessageCount(managementDC2, "Div"), 5000, 100);
      Wait.assertEquals(messagesPerDivert, () -> getMessageCount(managementDC2, "Div.0"), 5000, 100);
      Wait.assertEquals(messagesPerDivert, () -> getMessageCount(managementDC2, "Div.1"), 5000, 100);
      Wait.assertEquals(messagesPerDivert, () -> getMessageCount(managementDC2, "Div.2"), 5000, 100);

      LogAssert.assertServerLogsForMirror(getFileServerLocation(DC1_NODE));
      LogAssert.assertServerLogsForMirror(getFileServerLocation(DC2_NODE));

      LogAssert.assertServerLogsForMirror(getFileServerLocation(DC1_NODE));
      LogAssert.assertServerLogsForMirror(getFileServerLocation(DC2_NODE));

      if (consume) {
         try (Connection connection = connectionFactoryConsume.createConnection()) {
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

            connection.start();

            // the main divert.. as the diverts are non exclusive
            try (MessageConsumer consumer = session.createConsumer(session.createQueue("Div"))) {
               if (!exclusive) {
                  for (int i = 0; i < messagesPerDivert * numberOfDiverts; i++) {
                     TextMessage message = (TextMessage) consumer.receive(5000);
                     assertNotNull(message);
                     if (i % 100 == 0) {
                        logger.info("processed {}", i);
                     }
                     if (i == 0) {
                        checkProperties(connection, message);
                     }
                     session.commit();
                  }
               }
               assertNull(consumer.receiveNoWait());
            }
            session.commit();
         }

         for (int div = 0; div <= 2; div++) {
            for (int i = 0; i < messagesPerDivert; i++) {
               try (Connection connection = connectionFactoryConsume.createConnection()) {
                  try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
                     try (MessageConsumer consumer = session.createConsumer(session.createQueue("Div." + div))) {
                        connection.start();
                        TextMessage message = (TextMessage) consumer.receive(5000);
                        if (i % 10 == 0) {
                           logger.info("i={} on div {}", i, div);
                        }
                        MessageProducer testProducer = session.createProducer(session.createQueue(QUEUE_NAME));
                        testProducer.send(message);
                        assertNotNull(message);
                        session.commit();
                     }
                  }
               }
            }

            try (Connection connection = connectionFactoryConsume.createConnection()) {
               try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
                  try (MessageConsumer consumer = session.createConsumer(session.createQueue("Div." + div))) {
                     assertNull(consumer.receiveNoWait(), "div " + div + " received a message");
                  }
                  session.commit();
               }
            }
         }

         try (Connection connection = connectionFactoryConsume.createConnection()) {
            try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
               try (MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME))) {
                  connection.start();
                  for (int i = 0; i < messagesPerDivert * numberOfDiverts; i++) {
                     TextMessage message = (TextMessage) consumer.receive(5000);
                     assertNotNull(message);
                     if (i == 0) {
                        checkProperties(connection, message);
                     }
                     session.commit();
                  }
                  assertNull(consumer.receiveNoWait());
                  session.commit();
               }
            }
         }

         Wait.assertEquals(0L, () -> getMessageCount(managementDC1, SNF_QUEUE), 30_000, 100);
         Wait.assertEquals(0L, () -> getMessageCount(managementDC2, SNF_QUEUE), 30_000, 100);

         Wait.assertEquals(0L, () -> getMessageCount(managementDC1, QUEUE_NAME), 5000, 100);
         Wait.assertEquals(0L, () -> getMessageCount(managementDC1, "Div"), 5000, 100);
         Wait.assertEquals(0L, () -> getMessageCount(managementDC1, "Div.0"), 5000, 100);
         Wait.assertEquals(0L, () -> getMessageCount(managementDC1, "Div.1"), 5000, 100);
         Wait.assertEquals(0L, () -> getMessageCount(managementDC1, "Div.2"), 5000, 100);

         Wait.assertEquals(0L, () -> getMessageCount(managementDC2, QUEUE_NAME), 5000, 100);
         Wait.assertEquals(0L, () -> getMessageCount(managementDC2, "Div"), 5000, 100);
         Wait.assertEquals(0L, () -> getMessageCount(managementDC2, "Div.0"), 5000, 100);
         Wait.assertEquals(0L, () -> getMessageCount(managementDC2, "Div.1"), 5000, 100);
         Wait.assertEquals(0L, () -> getMessageCount(managementDC2, "Div.2"), 5000, 100);
      }

      LogAssert.assertServerLogsForMirror(getFileServerLocation(DC1_NODE));
      LogAssert.assertServerLogsForMirror(getFileServerLocation(DC2_NODE));
   }

   private void checkProperties(Connection connection, javax.jms.Message message) throws Exception {
      try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
         TemporaryQueue temporaryQueue = session.createTemporaryQueue();
         MessageProducer producer = session.createProducer(temporaryQueue);
         producer.send(message);
         connection.start();
         MessageConsumer consumer = session.createConsumer(temporaryQueue);
         javax.jms.Message receivedMessage = consumer.receive(5000);
         assertNotNull(receivedMessage);

         // The cleanup for x-opt happens on server's side.
         // we may receive if coming directly from a mirrored queue,
         // however we should cleanup on the next send to avoid invalid IDs on the server
         Enumeration propertyNames = receivedMessage.getPropertyNames();
         while (propertyNames.hasMoreElements()) {
            String property = String.valueOf(propertyNames.nextElement());
            assertFalse(property.startsWith("x-opt"));
         }
      }
   }

}