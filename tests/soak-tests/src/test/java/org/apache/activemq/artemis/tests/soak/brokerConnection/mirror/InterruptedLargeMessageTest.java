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
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterruptedLargeMessageTest extends SoakTestBase {

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

   public static final String DC1_NODE_A = "interruptLarge/DC1";
   public static final String DC2_NODE_A = "interruptLarge/DC2";

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

      String insert;
      {
         StringWriter insertWriter = new StringWriter();

         insertWriter.write("\n");
         insertWriter.write("      <metrics>\n");
         insertWriter.write("         <jvm-memory>false</jvm-memory>\n");
         insertWriter.write("         <jvm-gc>true</jvm-gc>\n");
         insertWriter.write("         <jvm-threads>true</jvm-threads>\n");
         insertWriter.write("         <netty-pool>true</netty-pool>\n");
         insertWriter.write("         <plugin class-name=\"org.apache.activemq.artemis.core.server.metrics.plugins.SimpleMetricsPlugin\">\n");
         insertWriter.write("            <property key=\"foo\" value=\"x\"/>\n");
         insertWriter.write("            <property key=\"bar\" value=\"y\"/>\n");
         insertWriter.write("            <property key=\"baz\" value=\"z\"/>\n");
         insertWriter.write("         </plugin>\n");
         insertWriter.write("      </metrics>\n");
         insertWriter.write("  </core>\n");
         insert = insertWriter.toString();
      }

      assertTrue(FileUtil.findReplace(new File(getServerLocation(serverName), "./etc/broker.xml"), "</core>", insert));
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
   public void testRandomProtocol() throws Exception {
      testInterrupt(randomProtocol());
   }


   private void preCreateInternalQueues(String serverLocation) throws Exception {
      Configuration configuration = createDefaultConfig(0, false);
      configuration.setJournalDirectory(getServerLocation(serverLocation) + "/data/journal");
      configuration.setJournalFileSize(ActiveMQDefaultConfiguration.getDefaultJournalFileSize());
      configuration.setBindingsDirectory(getServerLocation(serverLocation) + "/data/bindings");
      configuration.setLargeMessagesDirectory(getServerLocation(serverLocation) + "/data/large-messages");

      ActiveMQServer server = createServer(true, configuration);
      server.start();
      try {
         server.addAddressInfo(new AddressInfo(SNF_QUEUE).addRoutingType(RoutingType.ANYCAST).setInternal(false));
         server.createQueue(QueueConfiguration.of(SNF_QUEUE).setRoutingType(RoutingType.ANYCAST).setAddress(SNF_QUEUE).setDurable(true).setInternal(false));
      } catch (Throwable error) {
         logger.warn(error.getMessage(), error);
      }
      server.stop();
   }

   private void testInterrupt(final String protocol) throws Exception {
      // This will force internal queues as "non internal"
      // this is in an attempt to create issues between versions of the broker
      preCreateInternalQueues(DC1_NODE_A);
      preCreateInternalQueues(DC2_NODE_A);

      startDC1();

      final int numberOfMessages = 400;

      ConnectionFactory connectionFactoryDC1A = CFUtil.createConnectionFactory(protocol, DC1_NODEA_URI);
      ConnectionFactory connectionFactoryDC2A = CFUtil.createConnectionFactory(protocol, DC2_NODEA_URI);

      SimpleManagement simpleManagementDC1A = new SimpleManagement(DC1_NODEA_URI, null, null);
      SimpleManagement simpleManagementDC2A = new SimpleManagement(DC2_NODEA_URI, null, null);

      try (Connection connection = connectionFactoryDC1A.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(QUEUE_NAME);
         MessageProducer producer = session.createProducer(queue);

         for (int i = 0; i < numberOfMessages; i++) {
            TextMessage message = session.createTextMessage(largeBody);
            message.setIntProperty("id", i);
            producer.send(message);
            if (i % 10 == 0) {
               logger.debug("Sent {} messages", i);
               session.commit();
            }
         }
         session.commit();

         startDC2();

         // We will keep interrupting the servers alternatively until all messages were transferred
         boolean interruptSource = true;
         while (getNumberOfLargeMessages(DC2_NODE_A) < numberOfMessages) {
            if (interruptSource) {
               stopDC1();
            } else {
               stopDC2();
            }

            long messagesBeforeStart = getNumberOfLargeMessages(DC2_NODE_A);

            if (interruptSource) {
               startDC1();
            } else {
               startDC2();
            }

            interruptSource = !interruptSource; // switch which side we are interrupting next time

            long currentMessages = messagesBeforeStart;

            // Waiting some progress
            while (currentMessages == messagesBeforeStart && currentMessages < numberOfMessages) {
               currentMessages = getNumberOfLargeMessages(DC2_NODE_A);
               Thread.sleep(100);
            }

            Thread.sleep(2000);

            currentMessages = getNumberOfLargeMessages(DC2_NODE_A);
            if (logger.isDebugEnabled()) {
               logger.debug("*******************************************************************************************************************************");
               logger.debug("There are currently {} in the broker", currentMessages);
               logger.debug("*******************************************************************************************************************************");
            }
         }

      }

      try (Connection connection = connectionFactoryDC2A.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         connection.start();
         MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
         for (int i = 0; i < numberOfMessages; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals(i, message.getIntProperty("id"));
            if (i % 10 == 0) {
               session.commit();
               logger.debug("Received {} messages", i);
            }
         }
         session.commit();
      }

      Wait.assertEquals(0, () -> getMessageCount(simpleManagementDC1A, SNF_QUEUE));
      Wait.assertEquals(0, () -> getMessageCount(simpleManagementDC2A, SNF_QUEUE));
      Wait.assertEquals(0, () -> getMessageCount(simpleManagementDC2A, QUEUE_NAME));
      Wait.assertEquals(0, () -> getMessageCount(simpleManagementDC1A, QUEUE_NAME));

      Wait.assertEquals(0, () -> getNumberOfLargeMessages(DC1_NODE_A), 5000);
      Wait.assertEquals(0, () -> getNumberOfLargeMessages(DC2_NODE_A), 5000);
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