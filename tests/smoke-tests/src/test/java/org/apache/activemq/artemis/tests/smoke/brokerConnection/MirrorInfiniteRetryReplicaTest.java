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
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test will keep a consumer active on both nodes. Up to the point an ack would give up. It has configured a
 * massive number of retries, so even after keeping the consumer on for some time should not make the ack retry to go
 * away. as soon as the consumer gives up the message the retry should succeed.
 */
public class MirrorInfiniteRetryReplicaTest extends SmokeTestBase {

   private static final String QUEUE_NAME = "MirrorInfiniteRetryReplicaTestQueue";

   public static final String DC1_NODE = "MirrorInfiniteRetryReplicaTest/DC1";
   public static final String DC2_NODE = "MirrorInfiniteRetryReplicaTest/DC2";
   public static final String DC2_REPLICA_NODE = "MirrorInfiniteRetryReplicaTest/DC2_REPLICA";
   public static final String DC1_REPLICA_NODE = "MirrorInfiniteRetryReplicaTest/DC1_REPLICA";

   volatile Process processDC1;
   volatile Process processDC2;
   volatile Process processDC1_REPLICA;
   volatile Process processDC2_REPLICA;


   // change this to true to have the server producing more detailed logs
   private static final boolean TRACE_LOGS = false;

   @AfterEach
   public void destroyServers() throws Exception {
      if (processDC2_REPLICA != null) {
         processDC2_REPLICA.destroyForcibly();
         processDC2_REPLICA.waitFor(1, TimeUnit.MINUTES);
         processDC2_REPLICA = null;
      }
      if (processDC1_REPLICA != null) {
         processDC1_REPLICA.destroyForcibly();
         processDC1_REPLICA.waitFor(1, TimeUnit.MINUTES);
         processDC1_REPLICA = null;
      }
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
   private static final String DC1_BACKUP_IP = "localhost:61617";
   private static final String DC2_IP = "localhost:61618";
   private static final String DC2_BACKUP_IP = "localhost:61619";

   private static String uri(String ip) {
      return "tcp://" + ip;
   }

   private static String uriWithAlternate(String ip, String alternate) {
      return "tcp://" + ip + "#tcp://" + alternate;
   }

   private static void createMirroredServer(String serverName,
                                            String connectionName,
                                            String mirrorURI,
                                            int portOffset,
                                            boolean replicated,
                                            String clusterStatic) throws Exception {
      File serverLocation = getFileServerLocation(serverName);
      deleteDirectory(serverLocation);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setAllowAnonymous(true).setArtemisInstance(serverLocation);
      cliCreateServer.setNoWeb(true);
      cliCreateServer.setArgs("--no-stomp-acceptor", "--no-hornetq-acceptor", "--no-mqtt-acceptor", "--no-amqp-acceptor", "--max-hops", "1", "--name", DC1_NODE, "--cluster-user", "my-cluster-user", "--cluster-password", "my-cluster-password");
      cliCreateServer.addArgs("--queues", QUEUE_NAME);
      cliCreateServer.setPortOffset(portOffset);
      if (replicated) {
         cliCreateServer.setReplicated(true);
         cliCreateServer.setStaticCluster(clusterStatic);
         cliCreateServer.setClustered(true);
      } else {
         cliCreateServer.setClustered(false);
      }

      cliCreateServer.createServer();

      Properties brokerProperties = new Properties();
      brokerProperties.put("messageExpiryScanPeriod", "1000");
      brokerProperties.put("AMQPConnections." + connectionName + ".uri", mirrorURI);
      brokerProperties.put("AMQPConnections." + connectionName + ".retryInterval", "1000");
      brokerProperties.put("AMQPConnections." + connectionName + ".type", AMQPBrokerConnectionAddressType.MIRROR.toString());
      brokerProperties.put("AMQPConnections." + connectionName + ".connectionElements.mirror.sync", "false");
      brokerProperties.put("largeMessageSync", "false");

      brokerProperties.put("addressSettings.#.maxSizeMessages", "50000");
      brokerProperties.put("addressSettings.#.maxReadPageMessages", "2000");
      brokerProperties.put("addressSettings.#.maxReadPageBytes", "-1");
      brokerProperties.put("addressSettings.#.prefetchPageMessages", "500");
      // if we don't use pageTransactions we may eventually get a few duplicates
      brokerProperties.put("mirrorPageTransaction", "true");
      brokerProperties.put("mirrorAckManagerQueueAttempts", "1000000"); // massive amount of retries, it should keep retrying even if there is a consumer holding a message

      File brokerPropertiesFile = new File(serverLocation, "broker.properties");
      saveProperties(brokerProperties, brokerPropertiesFile);

      if (TRACE_LOGS) {
         replaceLogs(serverLocation);
      }

   }

   private static void replaceLogs(File serverLocation) throws Exception {
      File log4j = new File(serverLocation, "/etc/log4j2.properties");
      assertTrue(FileUtil.findReplace(log4j, "logger.artemis_utils.level=INFO", """
         logger.artemis_utils.level=INFO

         logger.endpoint.name=org.apache.activemq.artemis.core.replication.ReplicationEndpoint
         logger.endpoint.level=DEBUG
         logger.ackmanager.name=org.apache.activemq.artemis.protocol.amqp.connect.mirror.AckManager
         logger.ackmanager.level=INFO
         logger.mirrorTarget.name=org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerTarget
         logger.mirrorTarget.level=INFO
         appender.console.filter.threshold.type = ThresholdFilter
         appender.console.filter.threshold.level = trace"""));
   }

   private static void createMirroredBackupServer(String serverName,
                                                  int portOffset,
                                                  String clusterStatic,
                                                  String mirrorURI) throws Exception {
      File serverLocation = getFileServerLocation(serverName);
      deleteDirectory(serverLocation);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setAllowAnonymous(true).setArtemisInstance(serverLocation);
      cliCreateServer.setMessageLoadBalancing("ON_DEMAND");
      cliCreateServer.setArgs("--no-stomp-acceptor", "--no-hornetq-acceptor", "--no-mqtt-acceptor", "--no-amqp-acceptor", "--max-hops", "1", "--name", DC1_NODE, "--cluster-user", "my-cluster-user", "--cluster-password", "my-cluster-password");
      cliCreateServer.setPortOffset(portOffset);
      cliCreateServer.setClustered(true);
      cliCreateServer.setReplicated(true);
      cliCreateServer.setBackup(true);
      cliCreateServer.setStaticCluster(clusterStatic);
      cliCreateServer.createServer();

      Properties brokerProperties = new Properties();
      brokerProperties.put("messageExpiryScanPeriod", "1000");
      brokerProperties.put("AMQPConnections.mirror.uri", mirrorURI);
      brokerProperties.put("AMQPConnections.mirror.retryInterval", "1000");
      brokerProperties.put("AMQPConnections.mirror.type", AMQPBrokerConnectionAddressType.MIRROR.toString());
      brokerProperties.put("AMQPConnections.mirror.connectionElements.mirror.sync", "false");
      brokerProperties.put("largeMessageSync", "false");

      brokerProperties.put("addressSettings.#.maxSizeMessages", "1");
      brokerProperties.put("addressSettings.#.maxReadPageMessages", "2000");
      brokerProperties.put("addressSettings.#.maxReadPageBytes", "-1");
      brokerProperties.put("addressSettings.#.prefetchPageMessages", "500");

      brokerProperties.put("mirrorAckManagerQueueAttempts", "1000000"); // massive amount of retries, it should keep retrying even if there is a consumer holding a message

      // if we don't use pageTransactions we may eventually get a few duplicates
      brokerProperties.put("mirrorPageTransaction", "true");
      File brokerPropertiesFile = new File(serverLocation, "broker.properties");
      saveProperties(brokerProperties, brokerPropertiesFile);

      File brokerXml = new File(serverLocation, "/etc/broker.xml");
      assertTrue(brokerXml.exists());
      // Adding redistribution delay to broker configuration
      assertTrue(FileUtil.findReplace(brokerXml, "<address-setting match=\"#\">", """
         <address-setting match="#">

                     <redistribution-delay>0</redistribution-delay> <!-- added by SimpleMirrorSoakTest.java -->
         """));
      assertTrue(FileUtil.findReplace(brokerXml, "<page-size-bytes>10M</page-size-bytes>", "<page-size-bytes>100K</page-size-bytes>"));

      if (TRACE_LOGS) {
         replaceLogs(serverLocation);
      }
   }

   public static void createRealServers() throws Exception {
      createMirroredServer(DC1_NODE, "mirror", uriWithAlternate(DC2_IP, DC2_BACKUP_IP), 0, true, uri(DC1_BACKUP_IP));
      createMirroredBackupServer(DC1_REPLICA_NODE, 1, uri(DC1_IP), uriWithAlternate(DC2_IP, DC2_BACKUP_IP));
      createMirroredServer(DC2_NODE, "mirror", uriWithAlternate(DC1_IP, DC1_BACKUP_IP), 2, true, uri(DC2_BACKUP_IP));
      createMirroredBackupServer(DC2_REPLICA_NODE, 3, uri(DC2_IP), uriWithAlternate(DC1_IP, DC1_BACKUP_IP));
   }

   @Test
   public void testConsumersAttached() throws Exception {
      createRealServers();

      SimpleManagement managementDC1 = new SimpleManagement(uri(DC1_IP), null, null);
      SimpleManagement managementDC2 = new SimpleManagement(uri(DC2_IP), null, null);

      processDC2 = startServer(DC2_NODE, -1, -1, new File(getServerLocation(DC2_NODE), "broker.properties"));
      processDC2_REPLICA = startServer(DC2_REPLICA_NODE, -1, -1, new File(getServerLocation(DC2_REPLICA_NODE), "broker.properties"));

      processDC1 = startServer(DC1_NODE, -1, -1, new File(getServerLocation(DC1_NODE), "broker.properties"));
      processDC1_REPLICA = startServer(DC1_REPLICA_NODE, -1, -1, new File(getServerLocation(DC1_REPLICA_NODE), "broker.properties"));

      ServerUtil.waitForServerToStart(2, 10_000);
      Wait.assertTrue(managementDC2::isReplicaSync);

      ServerUtil.waitForServerToStart(0, 10_000);
      Wait.assertTrue(managementDC1::isReplicaSync);

      runAfter(() -> managementDC1.close());
      runAfter(() -> managementDC2.close());

      ConnectionFactory connectionFactoryDC1A = CFUtil.createConnectionFactory("amqp", uri(DC1_IP));
      try (Connection connection = connectionFactoryDC1A.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
         TextMessage message = session.createTextMessage("Simple message");
         message.setIntProperty("i", 1);
         message.setBooleanProperty("large", false);
         producer.send(message);
         session.commit();
      }

      ConnectionFactory connectionFactoryDC2A = CFUtil.createConnectionFactory("amqp", uri(DC2_IP));
      try (Connection connectionDC2 = connectionFactoryDC2A.createConnection(); Connection connectionDC1 = connectionFactoryDC1A.createConnection()) {
         connectionDC2.start();
         connectionDC1.start();

         // we will receive the message and hold it...
         Session sessionDC2 = connectionDC2.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = sessionDC2.createQueue(QUEUE_NAME);
         MessageConsumer consumerDC2 = sessionDC2.createConsumer(queue);
         assertNotNull(consumerDC2.receive(5000));

         Session sessionDC1 = connectionDC1.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumerDC1 = sessionDC1.createConsumer(queue);
         assertNotNull(consumerDC1.receive(5000));
         sessionDC1.commit();

         assertEquals(1, managementDC2.getMessageCountOnQueue(QUEUE_NAME));

         // we roll it back and close the consumer, the message should now be back to be retried correctly
         sessionDC2.rollback();
         consumerDC2.close();
         Wait.assertEquals(0, () -> managementDC2.getDeliveringCountOnQueue(QUEUE_NAME));
         Wait.assertEquals(0, () -> managementDC2.getMessageCountOnQueue(QUEUE_NAME));
      }
   }

}