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

package org.apache.activemq.artemis.tests.soak.brokerConnection.sender;

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

import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SenderSoakTest extends SoakTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static String largeBody;
   private static String smallBody = "This is a small body";

   {
      StringWriter writer = new StringWriter();
      while (writer.getBuffer().length() < 1024 * 1024) {
         writer.append("This is a large string ..... ");
      }
      largeBody = writer.toString();
   }

   public static final String DC1_NODE_A = "sender/DC1/A";
   public static final String DC2_NODE_A = "sender/DC2/A";

   Process processDC1_node_A;
   Process processDC2_node_A;

   private static void createServer(String serverName, int portOffset) throws Exception {
      File serverLocation = getFileServerLocation(serverName);
      deleteDirectory(serverLocation);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(serverLocation);
      cliCreateServer.setMessageLoadBalancing("STRICT");
      cliCreateServer.setClustered(false);
      cliCreateServer.setNoWeb(true);
      cliCreateServer.setArgs("--no-stomp-acceptor", "--no-hornetq-acceptor", "--no-mqtt-acceptor", "--no-amqp-acceptor", "--max-hops", "1", "--name", DC1_NODE_A);
      cliCreateServer.addArgs("--addresses", "order");
      cliCreateServer.addArgs("--queues", "myQueue");
      cliCreateServer.setPortOffset(portOffset);
      cliCreateServer.createServer();
   }

   public static void createServers(boolean useMirror) throws Exception {
      createServer(DC1_NODE_A, 0);

      if (useMirror) {
         Properties brokerProperties = new Properties();
         brokerProperties.put("AMQPConnections.sender.uri", "tcp://localhost:61618");
         brokerProperties.put("AMQPConnections.sender.retryInterval", "100");
         brokerProperties.put("AMQPConnections.sender.connectionElements.sender.type", "MIRROR");
         brokerProperties.put("largeMessageSync", "false");
         File brokerPropertiesFile = new File(getServerLocation(DC1_NODE_A), "broker.properties");
         saveProperties(brokerProperties, brokerPropertiesFile);
      } else {
         Properties brokerProperties = new Properties();
         brokerProperties.put("AMQPConnections.sender.uri", "tcp://localhost:61618");
         brokerProperties.put("AMQPConnections.sender.retryInterval", "100");
         brokerProperties.put("AMQPConnections.sender.connectionElements.sender.type", "SENDER");
         brokerProperties.put("AMQPConnections.sender.connectionElements.sender.queueName", "myQueue");
         brokerProperties.put("largeMessageSync", "false");
         File brokerPropertiesFile = new File(getServerLocation(DC1_NODE_A), "broker.properties");
         saveProperties(brokerProperties, brokerPropertiesFile);
      }

      createServer(DC2_NODE_A, 2);
   }

   private void startServers() throws Exception {
      processDC2_node_A = startServer(DC2_NODE_A, -1, -1);
      processDC1_node_A = startServer(DC1_NODE_A, -1, -1, new File(getServerLocation(DC1_NODE_A), "broker.properties"));

      ServerUtil.waitForServerToStart(0, 10_000);
      ServerUtil.waitForServerToStart(2, 10_000);
   }

   @Test
   public void testMirror() throws Exception {
      testSender(true);
   }

   @Test
   public void testSender() throws Exception {
      testSender(false);
   }

   public void testSender(boolean mirror) throws Exception {
      createServers(mirror);
      startServers();

      final int numberOfMessages = 1000;

      assertEquals(0, numberOfMessages % 2, "numberOfMessages must be even");

      ConnectionFactory connectionFactoryDC1A = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61616");
      ConnectionFactory connectionFactoryDC2A = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61618");

      try (Connection connection = connectionFactoryDC1A.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue("myQueue");
         MessageProducer producer = session.createProducer(queue);

         for (int i = 0; i < numberOfMessages; i++) {
            TextMessage message;
            boolean large;
            if (i % 1 == 10) {
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
            }
         }
         session.commit();
      }

      logger.debug("All messages were sent");

      try (Connection connection = connectionFactoryDC2A.createConnection()) {
         connection.start();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue("myQueue");
         MessageConsumer consumer = session.createConsumer(queue);

         for (int i = 0; i < numberOfMessages; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            logger.debug("Received message {}, large={}", message.getIntProperty("i"), message.getBooleanProperty("large"));
         }
         session.commit();
      }
   }

}
