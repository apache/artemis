/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.smoke.jmxmultiplefailback;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.File;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class ReplicatedMultipleFailbackTest extends SmokeTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @FunctionalInterface
   interface ThrowableFunction<T, R> {

      R apply(T t) throws Throwable;
   }

   private static <C, T> Optional<T> queryControl(JMXServiceURL serviceURI,
                                                  ObjectName objectName,
                                                  ThrowableFunction<C, T> queryControl,
                                                  Class<C> controlClass,
                                                  Function<Throwable, T> onThrowable) {
      try {
         try (JMXConnector jmx = JMXConnectorFactory.connect(serviceURI)) {
            final C control = MBeanServerInvocationHandler.newProxyInstance(jmx.getMBeanServerConnection(), objectName, controlClass, false);
            return Optional.ofNullable(queryControl.apply(control));
         }
      } catch (Throwable t) {
         return Optional.ofNullable(onThrowable.apply(t));
      }
   }

   private static Optional<Boolean> isBackup(JMXServiceURL serviceURI, ObjectNameBuilder builder) throws Exception {
      return queryControl(serviceURI, builder.getActiveMQServerObjectName(), ActiveMQServerControl::isBackup, ActiveMQServerControl.class, throwable -> null);
   }

   private static Optional<String> getNodeID(JMXServiceURL serviceURI, ObjectNameBuilder builder) throws Exception {
      return queryControl(serviceURI, builder.getActiveMQServerObjectName(), ActiveMQServerControl::getNodeID, ActiveMQServerControl.class, throwable -> null);
   }

   private static Optional<String> listNetworkTopology(JMXServiceURL serviceURI,
                                                       ObjectNameBuilder builder) throws Exception {
      return queryControl(serviceURI, builder.getActiveMQServerObjectName(), ActiveMQServerControl::listNetworkTopology, ActiveMQServerControl.class, throwable -> null);
   }

   private static Map<String, Pair<String, String>> decodeNetworkTopologyJson(String networkTopologyJson) {
      if (networkTopologyJson == null || networkTopologyJson.isEmpty()) {
         return Collections.emptyMap();
      }
      final JsonArray nodeIDs = JsonLoader.readArray(new StringReader(networkTopologyJson));
      final int nodeCount = nodeIDs.size();
      Map<String, Pair<String, String>> networkTopology = new HashMap<>(nodeCount);
      for (int i = 0; i < nodeCount; i++) {
         final JsonObject nodePair = nodeIDs.getJsonObject(i);
         try {
            final String nodeID = nodePair.getString("nodeID");
            final String primary = nodePair.getString("primary");
            final String backup = nodePair.getString("backup", null);
            networkTopology.put(nodeID, new Pair<>(primary, backup));
         } catch (Exception e) {
            logger.warn("Error on {}", nodePair, e);
         }
      }
      return networkTopology;
   }

   private static long countMembers(Map<String, Pair<String, String>> networkTopology) {
      final long count = networkTopology.values().stream()
         .map(Pair::getA).filter(primary -> primary != null && !primary.isEmpty())
         .count();
      return count;
   }

   private static long countNodes(Map<String, Pair<String, String>> networkTopology) {
      final long count = networkTopology.values().stream()
         .flatMap(pair -> Stream.of(pair.getA(), pair.getB()))
         .filter(primaryOrBackup -> primaryOrBackup != null && !primaryOrBackup.isEmpty())
         .count();
      return count;
   }

   private static boolean validateNetworkTopology(String networkTopologyJson, Predicate<Map<String, Pair<String, String>>> checkTopology) {
      final Map<String, Pair<String, String>> networkTopology = decodeNetworkTopologyJson(networkTopologyJson);
      return checkTopology.test(networkTopology);
   }

   private static String backupOf(String nodeID, Map<String, Pair<String, String>> networkTopology) {
      return networkTopology.get(nodeID).getB();
   }

   private static String primaryOf(String nodeID, Map<String, Pair<String, String>> networkTopology) {
      return networkTopology.get(nodeID).getA();
   }

   private static Predicate<Map<String, Pair<String, String>>> containsExactNodeIds(String... nodeID) {
      Objects.requireNonNull(nodeID);
      return topology -> topology.size() == nodeID.length && Stream.of(nodeID).allMatch(topology::containsKey);
   }

   private static Predicate<Map<String, Pair<String, String>>> withMembers(int count) {
      return topology -> countMembers(topology) == count;
   }

   private static Predicate<Map<String, Pair<String, String>>> withNodes(int count) {
      return topology -> countNodes(topology) == count;
   }

   private static Predicate<Map<String, Pair<String, String>>> withBackup(String nodeId, Predicate<String> compare) {
      return topology -> compare.test(backupOf(nodeId, topology));
   }

   private static Predicate<Map<String, Pair<String, String>>> withPrimary(String nodeId, Predicate<String> compare) {
      return topology -> compare.test(primaryOf(nodeId, topology));
   }

   private static final String JMX_SERVER_HOSTNAME = "localhost";
   private static final int JMX_PORT_PRIMARY_1 = 10099;
   private static final int JMX_PORT_PRIMARY_2 = 10199;
   private static final int JMX_PORT_PRIMARY_3 = 10299;
   private static final int JMX_PORT_BACKUP_1 = 10399;

   private static final String PRIMARY_1_DATA_FOLDER = "replicated-failback-primary1";
   private static final String PRIMARY_2_DATA_FOLDER = "replicated-failback-primary2";
   private static final String PRIMARY_3_DATA_FOLDER = "replicated-failback-primary3";
   private static final String BACKUP_1_DATA_FOLDER = "replicated-failback-backup1";

   private static final int PRIMARY_1_PORT_ID = 0;
   private static final int PRIMARY_2_PORT_ID = PRIMARY_1_PORT_ID + 100;
   private static final int PRIMARY_3_PORT_ID = PRIMARY_2_PORT_ID + 100;
   private static final int BACKUP_1_PORT_ID = PRIMARY_3_PORT_ID + 100;


   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(PRIMARY_1_DATA_FOLDER);
      deleteDirectory(server0Location);
      File server1Location = getFileServerLocation(PRIMARY_2_DATA_FOLDER);
      deleteDirectory(server1Location);
      File server2Location = getFileServerLocation(PRIMARY_3_DATA_FOLDER);
      deleteDirectory(server2Location);
      File server3Location = getFileServerLocation(BACKUP_1_DATA_FOLDER);
      deleteDirectory(server3Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(server0Location).setConfiguration("./src/main/resources/servers/replicated-failback-primary1").setArgs("--java-options", "-Djava.rmi.server.hostname=localhost");
         cliCreateServer.createServer();
      }

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(server1Location).setConfiguration("./src/main/resources/servers/replicated-failback-primary2").setArgs("--java-options", "-Djava.rmi.server.hostname=localhost");
         cliCreateServer.createServer();
      }

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(server2Location).setConfiguration("./src/main/resources/servers/replicated-failback-primary3").setArgs("--java-options", "-Djava.rmi.server.hostname=localhost");
         cliCreateServer.createServer();
      }

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(server3Location).setConfiguration("./src/main/resources/servers/replicated-failback-backup1").setArgs("--java-options", "-Djava.rmi.server.hostname=localhost");
         cliCreateServer.createServer();
      }
   }



   private enum Broker {
      primary1(JMX_PORT_PRIMARY_1, PRIMARY_1_DATA_FOLDER, PRIMARY_1_PORT_ID), primary2(JMX_PORT_PRIMARY_2, PRIMARY_2_DATA_FOLDER, PRIMARY_2_PORT_ID), primary3(JMX_PORT_PRIMARY_3, PRIMARY_3_DATA_FOLDER, PRIMARY_3_PORT_ID), backup1(JMX_PORT_BACKUP_1, BACKUP_1_DATA_FOLDER, BACKUP_1_PORT_ID);

      final ObjectNameBuilder objectNameBuilder;
      final String dataFolder;
      final JMXServiceURL jmxServiceURL;
      final int portID;

      Broker(int jmxPort, String dataFolder, int portID) {
         this.portID = portID;
         this.dataFolder = dataFolder;
         try {
            jmxServiceURL = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + JMX_SERVER_HOSTNAME + ":" + jmxPort + "/jmxrmi");
         } catch (MalformedURLException e) {
            throw new RuntimeException(e);
         }
         this.objectNameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), name(), true);
      }

      public Process startServer(SmokeTestBase env, int millisTimeout) throws Exception {
         return env.startServer(dataFolder, portID, millisTimeout);
      }

      public void cleanupData() {
         ReplicatedMultipleFailbackTest.cleanupData(dataFolder);
      }

      public Optional<Boolean> isBackup() throws Exception {
         return ReplicatedMultipleFailbackTest.isBackup(jmxServiceURL, objectNameBuilder);
      }

      public Optional<String> getNodeID() throws Exception {
         return ReplicatedMultipleFailbackTest.getNodeID(jmxServiceURL, objectNameBuilder);
      }

      public Optional<String> listNetworkTopology() throws Exception {
         return ReplicatedMultipleFailbackTest.listNetworkTopology(jmxServiceURL, objectNameBuilder);
      }
   }

   @BeforeEach
   public void before() {
      Stream.of(Broker.values()).forEach(Broker::cleanupData);
      disableCheckThread();
   }

   @Test
   public void testMultipleFailback() throws Exception {
      logger.info("TEST BOOTSTRAPPING START: STARTING brokers {}", Arrays.toString(Broker.values()));
      final int failbackRetries = 10;
      final int timeout = (int) TimeUnit.SECONDS.toMillis(30);
      Process primary1 = Broker.primary1.startServer(this, timeout);
      Wait.assertTrue(() -> !Broker.primary1.isBackup().orElse(true), timeout);
      Broker.primary2.startServer(this, timeout);
      Wait.assertTrue(() -> !Broker.primary2.isBackup().orElse(true), timeout);
      Broker.primary3.startServer(this, timeout);
      Wait.assertTrue(() -> !Broker.primary3.isBackup().orElse(true), timeout);
      Broker.backup1.startServer(this, 0);
      Wait.assertTrue(() -> Broker.backup1.isBackup().orElse(false), timeout);

      final String nodeIDprimary1 = Broker.primary1.getNodeID().get();
      final String nodeIDprimary2 = Broker.primary2.getNodeID().get();
      final String nodeIDprimary3 = Broker.primary3.getNodeID().get();

      for (Broker broker : Broker.values()) {
         logger.info("CHECKING NETWORK TOPOLOGY FOR {}", broker);
         Wait.assertTrue(() -> validateNetworkTopology(broker.listNetworkTopology().orElse(""),
                                                       containsExactNodeIds(nodeIDprimary1, nodeIDprimary2, nodeIDprimary3)
                                                          .and(withPrimary(nodeIDprimary1, Objects::nonNull))
                                                          .and(withBackup(nodeIDprimary1, Objects::nonNull))
                                                          .and(withMembers(3))
                                                          .and(withNodes(4))), timeout);
      }

      final String urlBackup1 = backupOf(nodeIDprimary1, decodeNetworkTopologyJson(Broker.backup1.listNetworkTopology().get()));
      assertNotNull(urlBackup1);
      final String urlPrimary1 = primaryOf(nodeIDprimary1, decodeNetworkTopologyJson(Broker.primary1.listNetworkTopology().get()));
      assertNotNull(urlPrimary1);
      assertNotEquals(urlPrimary1, urlBackup1);

      logger.info("Node ID primary 1 is {}", nodeIDprimary1);
      logger.info("Node ID primary 2 is {}", nodeIDprimary2);
      logger.info("Node ID primary 3 is {}", nodeIDprimary3);

      logger.info("{} has url: {}", Broker.primary1, urlPrimary1);
      logger.info("{} has url: {}", Broker.backup1, urlBackup1);

      logger.info("BOOTSTRAPPING ENDED: READ nodeIds and primary1/backup1 urls");

      for (int i = 0; i < failbackRetries; i++) {
         logger.info("START TEST {}", i + 1);
         logger.info("KILLING primary1");
         killServer(primary1);
         // wait until slave1 became live
         Wait.assertTrue(() -> !Broker.backup1.isBackup().orElse(true), timeout);
         logger.info("backup1 is PRIMARY");
         logger.info("VALIDATE TOPOLOGY OF ACTIVE BROKERS");
         Stream.of(Broker.primary2, Broker.primary3, Broker.backup1).forEach(
            broker -> Wait.assertTrue(() -> validateNetworkTopology(broker.listNetworkTopology().orElse(""),
                                                                    containsExactNodeIds(nodeIDprimary1, nodeIDprimary2, nodeIDprimary3)
                                                                       .and(withPrimary(nodeIDprimary1, urlBackup1::equals))
                                                                       .and(withBackup(nodeIDprimary1, Objects::isNull))
                                                                       .and(withMembers(3))
                                                                       .and(withNodes(3))), timeout)
         );
         // restart primary1
         logger.info("STARTING primary1");
         primary1 = Broker.primary1.startServer(this, 0);
         Wait.assertTrue(() -> Broker.backup1.isBackup().orElse(false), timeout);
         logger.info("backup1 is BACKUP");
         Wait.assertTrue(() -> !Broker.primary1.isBackup().orElse(true), timeout);
         logger.info("primary1 is PRIMARY");
         for (Broker broker : Broker.values()) {
            logger.info("CHECKING NETWORK TOPOLOGY FOR {}", broker);
            Wait.assertTrue(() -> validateNetworkTopology(broker.listNetworkTopology().orElse(""),
                                                          containsExactNodeIds(nodeIDprimary1, nodeIDprimary2, nodeIDprimary3)
                                                             .and(withPrimary(nodeIDprimary1, urlPrimary1::equals))
                                                             .and(withBackup(nodeIDprimary1, urlBackup1::equals))
                                                             .and(withMembers(3))
                                                             .and(withNodes(4))), timeout);
         }
      }
      logger.info("TEST COMPLETED");
   }
}
