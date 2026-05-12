/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.tests.soak.clusterNotificationsContinuity;

import static org.apache.activemq.artemis.utils.TestParameters.testProperty;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import javax.jms.Connection;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

/**
 * Refer to ./scripts/parameters.sh for suggested parameters
 * <p>
 * Tests for an issue that's dependent on high overall system load. The following parameters are used to tune the
 * resource demands of the test: NUMBER_OF_SERVERS, NUMBER_OF_QUEUES, NUMBER_OF_CONSUMERS
 */
public class ClusterNotificationsContinuityTest extends SoakTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String SERVER_NAME_BASE = "clusterNotifications/cncBroker-";
   public static final int SERVER_PORT_BASE = 61616;
   private static final String TEST_NAME = "CLUSTER_NOTIFICATIONS_CONTINUITY";
   private static final boolean TEST_ENABLED = Boolean.parseBoolean(testProperty(TEST_NAME, "TEST_ENABLED", "true"));

   private static final int NUMBER_OF_SERVERS = testProperty(TEST_NAME, "NUMBER_OF_SERVERS", 3);
   private static final int NUMBER_OF_QUEUES = testProperty(TEST_NAME, "NUMBER_OF_QUEUES", 200);
   private static final int NUMBER_OF_WORKERS = testProperty(TEST_NAME, "NUMBER_OF_WORKERS", 10);
   private static final String QUEUE_NAME_PREFIX = "TEST.QUEUE.";
   private final Process[] serverProcesses = new Process[NUMBER_OF_SERVERS];
   private Process dmlcProcess;

   @BeforeAll
   public static void createServers() throws Exception {
      for (int s = 0; s < NUMBER_OF_SERVERS; s++) {
         String serverName = SERVER_NAME_BASE + s;

         String staticClusterURI;

         {
            StringBuilder urlBuffer = new StringBuilder();
            boolean first = true;
            for (int i = 0; i < NUMBER_OF_SERVERS; i++) {
               if (i != s) {
                  if (!first) {
                     urlBuffer.append(",");
                  }
                  first = false;
                  urlBuffer.append("tcp://localhost:" + (SERVER_PORT_BASE + i));
               }
            }

            staticClusterURI = urlBuffer.toString();
         }

         File serverLocation = getFileServerLocation(serverName);
         deleteDirectory(serverLocation);

         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer
            .setRole("amq")
            .setUser("admin")
            .setPassword("admin")
            .setAllowAnonymous(true)
            .setNoWeb(true)
            .setArtemisInstance(serverLocation)
            .setPortOffset(s)
            .setClustered(true);

         cliCreateServer.setMessageLoadBalancing("OFF_WITH_REDISTRIBUTION");
         cliCreateServer.setStaticCluster(staticClusterURI);
         cliCreateServer.setArgs("--no-stomp-acceptor", "--no-hornetq-acceptor", "--no-mqtt-acceptor", "--no-amqp-acceptor", "--max-hops", "1", "--cluster-user", "my-cluster-user", "--cluster-password", "my-cluster-password");

         cliCreateServer.createServer();

         Properties brokerProperties = new Properties();
         brokerProperties.put("addressesSettings.#.redistributionDelay", "0");

         File brokerPropertiesFile = new File(serverLocation, "broker.properties");
         saveProperties(brokerProperties, brokerPropertiesFile);
      }
   }

   @BeforeEach
   public void before() throws Exception {
      assumeTrue(TEST_ENABLED);
      for (int i = 0; i < NUMBER_OF_SERVERS; i++) {
         String serverName = SERVER_NAME_BASE + i;

         cleanupData(serverName);
         File brokerPropertiesFile = new File(getServerLocation(serverName), "broker.properties");
         serverProcesses[i] = startServer(serverName, 0, 0, brokerPropertiesFile);
      }

      for (int i = 0; i < NUMBER_OF_SERVERS; i++) {
         ServerUtil.waitForServerToStart(i, 10_000);
         SimpleManagement simpleManagement = new SimpleManagement("tcp://localhost:" + (SERVER_PORT_BASE + i), null, null);
         Wait.assertEquals(NUMBER_OF_SERVERS, () -> simpleManagement.listNetworkTopology().size(), 5000);
      }
   }

   @Test
   public void testClusterNotificationsContinuity() throws Throwable {
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:61616");
      runAfter(factory::close);

      CountDownLatch latch = new CountDownLatch(NUMBER_OF_QUEUES);
      ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_WORKERS);
      runAfter(executorService::shutdownNow);

      //run dmlc in spawned process to more easily manage its lifecycle
      dmlcProcess = SpawnedVMSupport.spawnVM("org.apache.activemq.artemis.tests.soak.clusterNotificationsContinuity.ClusterNotificationsContinuityTest");
      runAfter(dmlcProcess::destroyForcibly);

      for (int i = 0; i < NUMBER_OF_QUEUES; i++) {
         Queue queue = ActiveMQDestination.createQueue(QUEUE_NAME_PREFIX + i);

         executorService.execute(() -> {
            try (Connection connection = factory.createConnection();
                 Session session = connection.createSession(Session.SESSION_TRANSACTED)) {

               logger.debug("Sending message to queue: {}", queue.getQueueName());
               session.createProducer(queue).send(session.createTextMessage("Message"));
               session.commit();

               latch.countDown();
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
            }
         });
      }

      latch.await(5000, TimeUnit.MILLISECONDS);

      if (!dmlcProcess.waitFor(30_000, TimeUnit.MILLISECONDS)) {
         dmlcProcess.destroyForcibly();
      }

      for (int i = 0; i < NUMBER_OF_SERVERS; i++) {
         String serverName = SERVER_NAME_BASE + i;

         File artemisLog = new File("target/" + serverName + "/log/artemis.log");
         assertFalse(findLogRecord(artemisLog, "AMQ224037"));
      }

   }

   @AfterEach
   public void cleanup() {
      SpawnedVMSupport.forceKill();

      for (int i = 0; i < NUMBER_OF_SERVERS; i++) {
         serverProcesses[i].destroy();
         String serverName = SERVER_NAME_BASE + i;
         cleanupData(serverName);
      }
   }

   public static void main(String[] args) throws Exception {
      ClusterNotificationsContinuityTest cncTest = new ClusterNotificationsContinuityTest();
      cncTest.runDMLCClient();
   }

   private void runDMLCClient() throws Exception {
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:61616");
      runAfter(factory::close);

      CountDownLatch latch = new CountDownLatch(NUMBER_OF_QUEUES);
      List<DefaultMessageListenerContainer> containers = new ArrayList<>();

      for (int i = 0; i < NUMBER_OF_QUEUES; i++) {
         try {
            String queueName = QUEUE_NAME_PREFIX + i;

            DefaultMessageListenerContainer container = new DefaultMessageListenerContainer();
            container.setCacheLevelName("CACHE_NONE");
            container.setSessionTransacted(true);
            container.setSessionAcknowledgeModeName("SESSION_TRANSACTED");
            container.setConcurrentConsumers(NUMBER_OF_WORKERS);
            container.setConnectionFactory(new CachingConnectionFactory(factory));
            container.setDestinationName(queueName);
            container.setReceiveTimeout(100);
            container.setMessageListener((MessageListener) msg -> {
               logger.debug("Message received on queue: {} ", queueName);
               latch.countDown();
            });

            container.initialize();
            container.start();
            containers.add(container);
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
         }
      }

      latch.await(10_000, TimeUnit.MILLISECONDS);

      containers.parallelStream().forEach(dmlc -> {
         try {
            dmlc.stop();
            Wait.waitFor(() -> dmlc.getActiveConsumerCount() == 0);
            dmlc.shutdown();
            ((CachingConnectionFactory) dmlc.getConnectionFactory()).destroy();
         } catch (Exception ignore) {
         }
      });

   }

}
