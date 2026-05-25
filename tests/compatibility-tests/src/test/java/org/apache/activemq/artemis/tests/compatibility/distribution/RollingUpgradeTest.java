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
package org.apache.activemq.artemis.tests.compatibility.distribution;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.RealServerTestBase;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.apache.activemq.artemis.utils.TestParameters;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.cli.commands.helper.HelperBase;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class RollingUpgradeTest extends RealServerTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String TWO_THIRTY = "./target/old-releases/apache-artemis-2.30.0";

   private static final String TWO_THIRTY_SEVEN = "./target/old-releases/apache-artemis-2.37.0";

   private static final String QUEUE_NAME = "RollQueue";

   public static final String LIVE_0 = "RolledUpgradeTest/live0";
   public static final String LIVE_1 = "RolledUpgradeTest/live1";
   public static final String LIVE_2 = "RolledUpgradeTest/live2";

   public static final String BKP_0 = "RolledUpgradeTest/bkp0";
   public static final String BKP_1 = "RolledUpgradeTest/bkp1";
   public static final String BKP_2 = "RolledUpgradeTest/bkp2";

   private static final String LIVE0_URI = "tcp://localhost:61616";
   private static final String LIVE1_URI = "tcp://localhost:61617";
   private static final String LIVE2_URI = "tcp://localhost:61618";
   private static final String BACKUP0_URI = "tcp://localhost:61619";
   private static final String BACKUP1_URI = "tcp://localhost:61620";
   private static final String BACKUP2_URI = "tcp://localhost:61621";

   Process live0Process;
   Process live1Process;
   Process live2Process;

   Process bkp0Process;
   Process bkp1Process;
   Process bkp2Process;

   private static String getHost(int nodeID) {
      return "localhost";
   }

   private static int getPort(int nodeID) {
      return 61616 + nodeID;
   }

   private static int getPortOffeset(int nodeID) {
      return nodeID;
   }

   private static void createServer(File homeLocation,
                                 String location,
                                 int serverID,
                                 String replicaGroupName,
                                 boolean live,
                                 int[] connectedNodes) throws Exception {
      File serverLocation = getFileServerLocation(location);
      List<String> parameters = new ArrayList<>();
      deleteDirectory(serverLocation);

      StringBuilder clusterList = new StringBuilder();
      for (int i = 0; i < connectedNodes.length; i++) {
         if (i > 0) {
            clusterList.append(",");
         }
         clusterList.append("tcp://" + getHost(connectedNodes[i]) + ":" + getPort(connectedNodes[i]));
      }

      parameters.add(homeLocation.getAbsolutePath() + "/bin/artemis");
      parameters.add("create");
      parameters.add("--silent");
      parameters.add("--queues");
      parameters.add(QUEUE_NAME);
      parameters.add("--user");
      parameters.add("guest");
      parameters.add("--password");
      parameters.add("guest");
      parameters.add("--port-offset");
      parameters.add(String.valueOf(getPortOffeset(serverID)));
      parameters.add("--allow-anonymous");
      parameters.add("--no-web");
      parameters.add("--no-autotune");
      parameters.add("--host");
      parameters.add("localhost");
      parameters.add("--clustered");
      parameters.add("--staticCluster");
      parameters.add(clusterList.toString());
      parameters.add("--replicated");
      if (!live) {
         parameters.add("--slave");
      }
      parameters.add("--no-amqp-acceptor");
      parameters.add("--no-mqtt-acceptor");
      parameters.add("--no-hornetq-acceptor");
      parameters.add("--no-stomp-acceptor");
      parameters.add(serverLocation.getAbsolutePath());

      ProcessBuilder processBuilder = new ProcessBuilder();
      processBuilder.command(parameters.toArray(new String[parameters.size()]));
      Process process = processBuilder.start();
      SpawnedVMSupport.spawnLoggers(null, null, "ArtemisCreate", true, true, process);
      assertTrue(process.waitFor(10, TimeUnit.SECONDS));

      File brokerXml = new File(serverLocation, "/etc/broker.xml");

      assertTrue(FileUtil.findReplace(brokerXml, "ON_DEMAND", "OFF"));

      if (live) {
         boolean replacedMaster = FileUtil.findReplace(brokerXml, "</primary>", "   <group-name>" + replicaGroupName + "</group-name>\n" + "               <check-for-live-server>true</check-for-live-server>\n" + "               <quorum-size>2</quorum-size>\n" + "            </primary>");
         replacedMaster |= FileUtil.findReplace(brokerXml, "</master>", "   <group-name>" + replicaGroupName + "</group-name>\n" + "               <check-for-live-server>true</check-for-live-server>\n" + "               <quorum-size>2</quorum-size>\n" + "            </master>");

         assertTrue(replacedMaster, "couldn't find either master or primary on broker.xml");
      } else {
         boolean replacedSlave = FileUtil.findReplace(brokerXml, "<slave/>", "<slave>\n" + "              <group-name>" + replicaGroupName + "</group-name>\n" + "              <allow-failback>false</allow-failback>\n" + "              <quorum-size>2</quorum-size>\n" + "            </slave>");

         replacedSlave |= FileUtil.findReplace(brokerXml, "<backup/>", "<backup>\n" + "              <group-name>" + replicaGroupName + "</group-name>\n" + "              <allow-failback>false</allow-failback>\n" + "              <quorum-size>2</quorum-size>\n" + "            </backup>");
         assertTrue(replacedSlave, "couldn't find slave on backup to replace on broker.xml");
      }

   }

   public static void createServers(File sourceHome) throws Exception {
      createServer(sourceHome, LIVE_0, 0, "live0", true, new int[]{1, 2, 3, 4, 5});
      createServer(sourceHome, LIVE_1, 1, "live1", true, new int[]{0, 2, 3, 4, 5});
      createServer(sourceHome, LIVE_2, 2, "live2", true, new int[]{0, 1, 3, 4, 5});
      createServer(sourceHome, BKP_0, 3, "live0", false, new int[]{0, 1, 2, 4, 5});
      createServer(sourceHome, BKP_1, 4, "live1", false, new int[]{0, 1, 2, 3, 5});
      createServer(sourceHome, BKP_2, 5, "live2", false, new int[]{0, 1, 2, 3, 4});
   }

   private void upgrade(File home, File instance) throws Exception {
      ProcessBuilder upgradeBuilder = new ProcessBuilder();
      upgradeBuilder.command(home.getAbsolutePath() + "/bin/artemis", "upgrade", instance.getAbsolutePath());
      Process process = upgradeBuilder.start();
      SpawnedVMSupport.spawnLoggers(null, null, null, true, true, process);
      assertTrue(process.waitFor(10, TimeUnit.SECONDS));
   }

   @Test
   public void testRollUpgrade_2_30() throws Exception {
      testRollUpgrade(new File(TWO_THIRTY), HelperBase.getHome(ARTEMIS_HOME_PROPERTY));
   }

   @Test
   public void testRollUpgrade_2_37() throws Exception {
      testRollUpgrade(new File(TWO_THIRTY_SEVEN), HelperBase.getHome(ARTEMIS_HOME_PROPERTY));
   }

   // Define a System Property TEST_ROLLED_DISTRIBUTION towards the Artemis Home of your choice and this will
   // perform the tests towards that distribution
   // Define a System Property TEST_ROLLED_DISTRIBUTION_UPGRADE towards the new Artemis home (by default the test will use BaseHelper.getHome()
   @Test
   public void testRollUpgrade_Provided_Distribution() throws Exception {
      String distribution = TestParameters.testProperty("ROLLED", "DISTRIBUTION", null);
      assumeTrue(distribution != null);

      String distributionUpgrading = TestParameters.testProperty("ROLLED", "DISTRIBUTION_UPGRADE", HelperBase.getHome(ARTEMIS_HOME_PROPERTY).getAbsolutePath());
      testRollUpgrade(new File(distribution), new File(distributionUpgrading));
   }

   private void testRollUpgrade(File artemisHome, File upgradingArtemisHome) throws Exception {
      assumeTrue(artemisHome.exists());
      createServers(artemisHome);

      live0Process = startServer(LIVE_0, 0, -1);
      live1Process = startServer(LIVE_1, 1, -1);
      live2Process = startServer(LIVE_2, 2, -1);

      ServerUtil.waitForServerToStart(0, 30_000);
      ServerUtil.waitForServerToStart(1, 30_000);
      ServerUtil.waitForServerToStart(2, 30_000);

      SimpleManagement managementLive0 = new SimpleManagement(LIVE0_URI, null, null);
      SimpleManagement managementLive1 = new SimpleManagement(LIVE1_URI, null, null);
      SimpleManagement managementLive2 = new SimpleManagement(LIVE2_URI, null, null);
      runAfter(managementLive0::close);
      runAfter(managementLive1::close);
      runAfter(managementLive2::close);

      SimpleManagement managementBKP0 = new SimpleManagement(BACKUP0_URI, null, null);
      SimpleManagement managementBKP1 = new SimpleManagement(BACKUP1_URI, null, null);
      SimpleManagement managementBKP2 = new SimpleManagement(BACKUP2_URI, null, null);
      runAfter(managementBKP0::close);
      runAfter(managementBKP1::close);
      runAfter(managementBKP2::close);

      bkp0Process = startServer(BKP_0, 0, 0);
      bkp1Process = startServer(BKP_1, 0, 0);
      bkp2Process = startServer(BKP_2, 0, 0);

      Wait.assertTrue(managementLive0::isReplicaSync, 10_000, 100);
      Wait.assertTrue(managementLive1::isReplicaSync, 10_000, 100);
      Wait.assertTrue(managementLive2::isReplicaSync, 10_000, 100);

      sendMessage(LIVE0_URI, "AMQP");
      sendMessage(LIVE0_URI, "CORE");
      sendMessage(LIVE0_URI, "OPENWIRE");

      sendMessage(LIVE1_URI, "AMQP");
      sendMessage(LIVE1_URI, "CORE");
      sendMessage(LIVE1_URI, "OPENWIRE");

      sendMessage(LIVE2_URI, "AMQP");
      sendMessage(LIVE2_URI, "CORE");
      sendMessage(LIVE2_URI, "OPENWIRE");

      Pair<Process, Process> updatedProcess;

      updatedProcess = rollUpgrade(0, LIVE_0, managementLive0, live0Process, 3, BKP_0, managementBKP0, bkp0Process, upgradingArtemisHome);
      this.live0Process = updatedProcess.getA();
      this.bkp0Process = updatedProcess.getB();

      updatedProcess = rollUpgrade(1, LIVE_1, managementLive1, live1Process, 4, BKP_1, managementBKP1, bkp1Process, upgradingArtemisHome);
      this.live1Process = updatedProcess.getA();
      this.bkp1Process = updatedProcess.getB();

      updatedProcess = rollUpgrade(2, LIVE_2, managementLive2, live2Process, 5, BKP_2, managementBKP2, bkp2Process, upgradingArtemisHome);
      this.live2Process = updatedProcess.getA();
      this.bkp2Process = updatedProcess.getB();

      consumeMessage(LIVE0_URI, "AMQP");
      consumeMessage(LIVE0_URI, "CORE");
      consumeMessage(LIVE0_URI, "OPENWIRE");
      checkNoMessages(LIVE0_URI);

      consumeMessage(LIVE1_URI, "AMQP");
      consumeMessage(LIVE1_URI, "CORE");
      consumeMessage(LIVE1_URI, "OPENWIRE");
      checkNoMessages(LIVE1_URI);

      consumeMessage(LIVE2_URI, "AMQP");
      consumeMessage(LIVE2_URI, "CORE");
      consumeMessage(LIVE2_URI, "OPENWIRE");
      checkNoMessages(LIVE2_URI);

   }

   private void sendMessage(String uri, String protocol) throws Exception {
      ConnectionFactory cf = CFUtil.createConnectionFactory(protocol, uri);
      try (Connection connection = cf.createConnection();
           Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
         MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
         TextMessage message = session.createTextMessage("hello from protocol " + protocol);
         message.setStringProperty("protocolUsed", protocol);
         logger.info("sending message {}", message.getText());
         producer.send(message);
         producer.close();
      }
   }

   private void consumeMessage(String uri, String protocol) throws Exception {
      ConnectionFactory cf = CFUtil.createConnectionFactory(protocol, uri);
      try (Connection connection = cf.createConnection();
           Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
         MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
         connection.start();
         TextMessage message = (TextMessage) consumer.receive(5_000);
         assertNotNull(message);
         logger.info("receiving message {}", message.getText());
         assertEquals(protocol, message.getStringProperty("protocolUsed"));
         consumer.close();
      }
   }

   private void checkNoMessages(String uri) throws Exception {
      ConnectionFactory cf = CFUtil.createConnectionFactory("CORE", uri);
      try (Connection connection = cf.createConnection();
           Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
         MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
         connection.start();
         assertNull(consumer.receiveNoWait());
         consumer.close();
      }
   }

   private Pair<Process, Process> rollUpgrade(int liveID,
                                              String liveServerToStop,
                                              SimpleManagement liveManagement,
                                              Process liveProcess,
                                              int backupID,
                                              String backupToStop,
                                              SimpleManagement backupManagement,
                                              Process backupProcess,
                                              File targetRelease) throws Exception {

      logger.info("Stopping server {}", liveServerToStop);
      stopServerWithFile(getServerLocation(liveServerToStop), liveProcess, 10, TimeUnit.SECONDS);

      // waiting backup to activate after the live stop
      logger.info("Waiting backup {} to become live", backupID);
      ServerUtil.waitForServerToStart(backupID, 30_000);

      logger.info("Upgrading {}", liveServerToStop);
      upgrade(targetRelease, getFileServerLocation(liveServerToStop));

      logger.info("Starting server {}", liveServerToStop);
      Process newLiveProcess = startServer(liveServerToStop, 0, 0);

      logger.info("Waiting replica to be sync");
      Wait.assertTrue(() -> repeatCallUntilConnected(backupManagement::isReplicaSync), 10_000, 100);

      logger.info("Stopping backup {}", backupToStop);
      stopServerWithFile(getServerLocation(backupToStop), backupProcess, 10, TimeUnit.SECONDS);

      logger.info("Waiting Activation on serverID {}", liveID);
      // waiting former live to activate after stopping backup
      ServerUtil.waitForServerToStart(liveID, 30_000);

      logger.info("upgrading {}", backupToStop);
      upgrade(targetRelease, getFileServerLocation(backupToStop));

      Process newBackupProcess = startServer(backupToStop, 0, 0);

      logger.info("Waiting live to have its replica sync");
      Wait.assertTrue(() -> repeatCallUntilConnected(liveManagement::isReplicaSync), 10_000, 100);

      return new Pair<>(newLiveProcess, newBackupProcess);
   }

   boolean repeatCallUntilConnected(Wait.Condition condition) {
      Throwable lastException = null;
      long timeLimit = System.currentTimeMillis() + 10_000;
      do {
         try {
            return condition.isSatisfied();
         } catch (Throwable e) {
            lastException = e;
         }
      }
      while (System.currentTimeMillis() > timeLimit);

      if (lastException != null) {
         throw new RuntimeException(lastException.getMessage(), lastException);
      } else {
         // if it gets here I'm a bad programmer!
         throw new IllegalStateException("didn't complete for some reason");
      }
   }
}
