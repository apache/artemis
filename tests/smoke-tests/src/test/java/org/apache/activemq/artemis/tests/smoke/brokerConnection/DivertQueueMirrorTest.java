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

package org.apache.activemq.artemis.tests.smoke.brokerConnection;

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
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DivertQueueMirrorTest extends SmokeTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static String body;

   static {
      StringWriter writer = new StringWriter();
      while (writer.getBuffer().length() < 20 * 1024) {
         writer.append("This is a string ..... ");
      }
      body = writer.toString();
   }

   private static final String CREATE_QUEUE = "outQueue1,outQueue2";

   public static final String DC1_NODE_A = "DivertQueueMirrorTest/DC1";
   public static final String DC2_NODE_A = "DivertQueueMirrorTest/DC2";

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
      cliCreateServer.addArgs("--queues", CREATE_QUEUE);
      cliCreateServer.addArgs("--java-memory", "512M");
      cliCreateServer.setPortOffset(portOffset);
      cliCreateServer.createServer();

      Properties brokerProperties = new Properties();
      brokerProperties.put("divertConfigurations.myDivert.routingName", "inputQueue");
      brokerProperties.put("divertConfigurations.myDivert.address", "inputQueue");
      brokerProperties.put("divertConfigurations.myDivert.forwardingAddress", "outQueue1");
      brokerProperties.put("divertConfigurations.myDivert.exclusive", "true");

      brokerProperties.put("divertConfigurations.myDivert2.routingName", "inputQueue");
      brokerProperties.put("divertConfigurations.myDivert2.address", "inputQueue");
      brokerProperties.put("divertConfigurations.myDivert2.forwardingAddress", "outQueue2");
      brokerProperties.put("divertConfigurations.myDivert2.exclusive", "true");


      brokerProperties.put("AMQPConnections." + connectionName + ".uri", mirrorURI);
      brokerProperties.put("AMQPConnections." + connectionName + ".retryInterval", "1000");
      brokerProperties.put("AMQPConnections." + connectionName + ".type", AMQPBrokerConnectionAddressType.MIRROR.toString());
      brokerProperties.put("AMQPConnections." + connectionName + ".connectionElements.mirror.sync", "false");
      brokerProperties.put("largeMessageSync", "false");

      brokerProperties.put("addressSettings.#.maxSizeBytes", Integer.toString(100 * 1024 * 1024));
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
   @Timeout(value = 240_000L, unit = TimeUnit.MILLISECONDS)
   public void testDivertAndMirror() throws Exception {
      String protocol = "AMQP"; // no need to run this test using multiple protocols. this is about validating paging works correctly

      processDC1_node_A = startServer(DC1_NODE_A, -1, -1, new File(getServerLocation(DC1_NODE_A), "broker.properties"));
      processDC2_node_A = startServer(DC2_NODE_A, -1, -1, new File(getServerLocation(DC2_NODE_A), "broker.properties"));
      ServerUtil.waitForServerToStart(0, 10_000);
      ServerUtil.waitForServerToStart(2, 10_000);

      final int numberOfMessages = 500;
      final int commitInterval = 100;

      ConnectionFactory connectionFactoryDC1A = CFUtil.createConnectionFactory(protocol, DC1_NODEA_URI);
      ConnectionFactory connectionFactoryDC2A = CFUtil.createConnectionFactory(protocol, DC2_NODEA_URI);

      SimpleManagement simpleManagementDC1A = new SimpleManagement(DC1_NODEA_URI, null, null);
      SimpleManagement simpleManagementDC2A = new SimpleManagement(DC2_NODEA_URI, null, null);
      try (Connection connection = connectionFactoryDC1A.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         connection.start();
         Queue queue = session.createQueue("inputQueue");
         MessageProducer producer = session.createProducer(queue);

         connection.start();

         for (int i = 0; i < numberOfMessages; i++) {
            producer.send(session.createTextMessage(body));
            if (i > 0 && i % commitInterval == 0) {
               session.commit();
            }
         }
         session.commit();
      }

      Wait.assertEquals((long)numberOfMessages, () -> getMessageCount(simpleManagementDC1A, "outQueue1"), 60_000, 500);
      Wait.assertEquals((long)numberOfMessages, () -> getMessageCount(simpleManagementDC1A, "outQueue2"), 60_000, 500);
      Wait.assertEquals(0L, () -> getMessageCount(simpleManagementDC1A, "inputQueue"), 60_000, 500);

      Wait.assertEquals((long)numberOfMessages, () -> getMessageCount(simpleManagementDC2A, "outQueue1"), 60_000, 500);
      Wait.assertEquals((long)numberOfMessages, () -> getMessageCount(simpleManagementDC2A, "outQueue2"), 60_000, 500);
      Wait.assertEquals(0L, () -> getMessageCount(simpleManagementDC2A, "inputQueue"), 60_000, 500);

      try (Connection connection = connectionFactoryDC2A.createConnection()) {
         connection.start();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         for (int q = 1; q <= 2; q++) {
            Queue queue = session.createQueue("outQueue" + q);

            try (MessageConsumer consumer = session.createConsumer(queue)) {
               logger.info("Consuming from queue {}", queue);

               for (int i = 0; i < numberOfMessages; i++) {
                  TextMessage message = (TextMessage) consumer.receive(5000);
                  Assertions.assertNotNull(message, "expecting message on queue " + queue);
                  if (i > 0 && i % commitInterval == 0) {
                     logger.info("Received {}, queue={}", i, queue);
                     session.commit();
                  }
               }
               session.commit();
            }
         }
      }

      Wait.assertEquals(0L, () -> getMessageCount(simpleManagementDC1A, SNF_QUEUE), 240_000, 500);
      Wait.assertEquals(0L, () -> getMessageCount(simpleManagementDC2A, SNF_QUEUE), 240_000, 500);
      Wait.assertEquals(0L, () -> getMessageCount(simpleManagementDC1A, "outQueue1"), 60_000, 500);
      Wait.assertEquals(0L, () -> getMessageCount(simpleManagementDC1A, "outQueue2"), 60_000, 500);
   }

}