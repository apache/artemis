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
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import java.io.File;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.util.Properties;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiMirrorSoakTest extends SoakTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static String largeBody;
   private static String smallBody = "This is a small body";

   static {
      StringWriter writer = new StringWriter();
      while (writer.getBuffer().length() < 1024 * 1024) {
         writer.append("This is a large string ..... ");
      }
      largeBody = writer.toString();
   }

   public static final String DC1_NODE_A = "MultiMirrorSoakTest/DC1";
   public static final String DC2_NODE_A = "MultiMirrorSoakTest/DC2";
   public static final String DC3_NODE_A = "MultiMirrorSoakTest/DC3";

   Process processDC1_node_A;
   Process processDC2_node_A;
   Process processDC3_node_A;

   private static String DC1_NODEA_URI = "tcp://localhost:61616";
   private static String DC2_NODEA_URI = "tcp://localhost:61617";
   private static String DC3_NODEA_URI = "tcp://localhost:61618";

   private static void createServer(String serverName,
                                    int portOffset,
                                    boolean paging,
                                    String... mirrorTo) throws Exception {
      File serverLocation = getFileServerLocation(serverName);
      deleteDirectory(serverLocation);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setAllowAnonymous(true).setArtemisInstance(serverLocation);
      cliCreateServer.setMessageLoadBalancing("ON_DEMAND");
      cliCreateServer.setClustered(false);
      cliCreateServer.setNoWeb(true);
      cliCreateServer.setArgs("--no-stomp-acceptor", "--no-hornetq-acceptor", "--no-mqtt-acceptor", "--no-amqp-acceptor", "--max-hops", "1", "--name", DC1_NODE_A);
      cliCreateServer.addArgs("--addresses", "order");
      cliCreateServer.addArgs("--queues", "myQueue");
      cliCreateServer.setPortOffset(portOffset);
      cliCreateServer.createServer();

      Properties brokerProperties = new Properties();
      brokerProperties.put("largeMessageSync", "false");
      if (mirrorTo != null && mirrorTo.length > 0) {
         int mirrorID = 0;
         for (String p : mirrorTo) {
            brokerProperties.put("AMQPConnections.mirror" + mirrorID + ".uri", p);
            brokerProperties.put("AMQPConnections.mirror" + mirrorID + ".retryInterval", "100");
            brokerProperties.put("AMQPConnections.mirror" + mirrorID + ".type", AMQPBrokerConnectionAddressType.MIRROR.toString());
            brokerProperties.put("AMQPConnections.mirror" + mirrorID + ".connectionElements.mirror.sync", "false");
            mirrorID++;
         }
      }
      File brokerPropertiesFile = new File(serverLocation, "broker.properties");
      saveProperties(brokerProperties, brokerPropertiesFile);

      File brokerXml = new File(serverLocation, "/etc/broker.xml");
      assertTrue(brokerXml.exists());
      // Adding redistribution delay to broker configuration
      assertTrue(FileUtil.findReplace(brokerXml, "<address-setting match=\"#\">", "<address-setting match=\"#\">\n\n" + "            <redistribution-delay>0</redistribution-delay> <!-- added by ClusteredMirrorSoakTest.java --> \n"));
      if (paging) {
         assertTrue(FileUtil.findReplace(brokerXml, "<max-size-messages>-1</max-size-messages>", "<max-size-messages>1</max-size-messages>"));
      }
   }

   public static void createRealServers(boolean paging) throws Exception {
      createServer(DC1_NODE_A, 0, paging, DC2_NODEA_URI, DC3_NODEA_URI);
      createServer(DC2_NODE_A, 1, paging, DC1_NODEA_URI);
      createServer(DC3_NODE_A, 2, paging, DC1_NODEA_URI);
   }

   private void startServers() throws Exception {
      processDC1_node_A = startServer(DC1_NODE_A, -1, -1, new File(getServerLocation(DC1_NODE_A), "broker.properties"));
      processDC2_node_A = startServer(DC2_NODE_A, -1, -1, new File(getServerLocation(DC2_NODE_A), "broker.properties"));
      processDC3_node_A = startServer(DC3_NODE_A, -1, -1, new File(getServerLocation(DC3_NODE_A), "broker.properties"));

      ServerUtil.waitForServerToStart(0, 10_000);
      ServerUtil.waitForServerToStart(1, 10_000);
      ServerUtil.waitForServerToStart(2, 10_000);
   }

   @Test
   public void testMultiMirror() throws Exception {
      createRealServers(false);
      startServers();
      internalMirror(DC1_NODEA_URI, DC3_NODEA_URI);
      internalMirror(DC1_NODEA_URI, DC2_NODEA_URI);
      internalMirror(DC3_NODEA_URI, DC1_NODEA_URI);
      internalMirror(DC1_NODEA_URI, DC1_NODEA_URI);
      internalMirror(DC2_NODEA_URI, DC3_NODEA_URI);
   }

   public void internalMirror(String producerURI, String consumerURi) throws Exception {
      final int numberOfMessages = 200;

      assertEquals(0, numberOfMessages % 2, "numberOfMessages must be even");

      ConnectionFactory producerCF = CFUtil.createConnectionFactory("amqp", producerURI);

      SimpleManagement simpleManagementDC1A = new SimpleManagement(DC1_NODEA_URI, null, null);
      SimpleManagement simpleManagementDC2A = new SimpleManagement(DC2_NODEA_URI, null, null);
      SimpleManagement simpleManagementDC3A = new SimpleManagement(DC3_NODEA_URI, null, null);

      String queueName = "myQueue";

      try (Connection connection = producerCF.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(queueName);
         MessageProducer producer = session.createProducer(queue);

         for (int i = 0; i < numberOfMessages; i++) {
            TextMessage message;
            boolean large;
            if (i % 1 == 2) {
               message = session.createTextMessage(largeBody);
               large = true;
            } else {
               message = session.createTextMessage(smallBody);
               large = false;
            }
            message.setIntProperty("i", i);
            message.setBooleanProperty("large", large);
            producer.send(message);
            if (i % 100 == 0) {
               logger.debug("commit {}", i);
               session.commit();

               Wait.assertEquals(i + 1, () -> simpleManagementDC1A.getMessageCountOnQueue(queueName));
               Wait.assertEquals(i + 1, () -> simpleManagementDC2A.getMessageCountOnQueue(queueName));
               Wait.assertEquals(i + 1, () -> simpleManagementDC3A.getMessageCountOnQueue(queueName));
            }
         }
         session.commit();
      }

      Wait.assertEquals(numberOfMessages, () -> simpleManagementDC1A.getMessageCountOnQueue(queueName));
      Wait.assertEquals(numberOfMessages, () -> simpleManagementDC2A.getMessageCountOnQueue(queueName));
      Wait.assertEquals(numberOfMessages, () -> simpleManagementDC3A.getMessageCountOnQueue(queueName));

      ConnectionFactory consumerCF = CFUtil.createConnectionFactory("amqp", consumerURi);

      try (Connection connection = consumerCF.createConnection()) {
         connection.start();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(queueName);
         MessageConsumer consumer = session.createConsumer(queue);

         for (int i = 0; i < numberOfMessages; i++) {
            TextMessage message;
            boolean large;
            if (i % 1 == 2) {
               large = true;
            } else {
               large = false;
            }
            message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals(i, message.getIntProperty("i"));
            assertEquals(large, message.getBooleanProperty("large"));
            if (i % 100 == 0) {
               logger.debug("commit {}", i);
               session.commit();

               Wait.assertEquals(numberOfMessages - i - 1, () -> simpleManagementDC1A.getMessageCountOnQueue(queueName), 5000);
               Wait.assertEquals(numberOfMessages - i - 1, () -> simpleManagementDC2A.getMessageCountOnQueue(queueName), 5000);
               Wait.assertEquals(numberOfMessages - i - 1, () -> simpleManagementDC3A.getMessageCountOnQueue(queueName), 5000);
            }
         }
         session.commit();
      }
      Wait.assertEquals(0, () -> simpleManagementDC1A.getMessageCountOnQueue(queueName));
      Wait.assertEquals(0, () -> simpleManagementDC2A.getMessageCountOnQueue(queueName));
      Wait.assertEquals(0, () -> simpleManagementDC3A.getMessageCountOnQueue(queueName));
   }
}