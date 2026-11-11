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

package org.apache.activemq.artemis.tests.smoke.topologycheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.check.ClusterNodeVerifier;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonString;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyCheckTest extends SmokeTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String SERVER_NAME_1 = "topology-check/broker1";
   private static final String SERVER_NAME_2 = "topology-check/broker2";
   private static final String SERVER_NAME_3 = "topology-check/broker3";
   private static final String SERVER_NAME_4 = "topology-check/broker4";

   private static final String URI_1 = "tcp://localhost:61616";
   private static final String URI_2 = "tcp://localhost:61617";
   private static final String URI_3 = "tcp://localhost:61618";
   private static final String URI_4 = "tcp://localhost:61619";

   /*
                 <execution>
                  <phase>test-compile</phase>
                  <id>create-topology-check-one</id>
                  <goals>
                     <goal>create</goal>
                  </goals>
                  <configuration>
                     <role>amq</role>
                     <user>admin</user>
                     <password>admin</password>
                     <allowAnonymous>false</allowAnonymous>
                     <noWeb>false</noWeb>
                     <clustered>true</clustered>
                     <instance>${basedir}/target/topology-check/broker1</instance>
                     <configuration>${basedir}/target/classes/servers/topology-check/broker1</configuration>
                  </configuration>
               </execution>
               <execution>
                  <phase>test-compile</phase>
                  <id>create-topology-check-two</id>
                  <goals>
                     <goal>create</goal>
                  </goals>
                  <configuration>
                     <role>amq</role>
                     <user>admin</user>
                     <password>admin</password>
                     <portOffset>1</portOffset>
                     <allowAnonymous>false</allowAnonymous>
                     <noWeb>false</noWeb>
                     <clustered>true</clustered>
                     <instance>${basedir}/target/topology-check/broker2</instance>
                     <configuration>${basedir}/target/classes/servers/topology-check/broker2</configuration>
                  </configuration>
               </execution>
               <execution>
                  <phase>test-compile</phase>
                  <id>create-topology-check-three</id>
                  <goals>
                     <goal>create</goal>
                  </goals>
                  <configuration>
                     <role>amq</role>
                     <user>admin</user>
                     <password>admin</password>
                     <portOffset>2</portOffset>
                     <allowAnonymous>false</allowAnonymous>
                     <noWeb>false</noWeb>
                     <clustered>true</clustered>
                     <instance>${basedir}/target/topology-check/broker3</instance>
                     <configuration>${basedir}/target/classes/servers/topology-check/broker3</configuration>
                  </configuration>
               </execution>
               <execution>
                  <phase>test-compile</phase>
                  <id>create-topology-check-four</id>
                  <goals>
                     <goal>create</goal>
                  </goals>
                  <configuration>
                     <role>amq</role>
                     <user>admin</user>
                     <password>admin</password>
                     <portOffset>3</portOffset>
                     <allowAnonymous>false</allowAnonymous>
                     <noWeb>false</noWeb>
                     <clustered>true</clustered>
                     <instance>${basedir}/target/topology-check/broker4</instance>
                     <configuration>${basedir}/target/classes/servers/topology-check/broker4</configuration>
                  </configuration>
               </execution>

    */


   @BeforeAll
   public static void createServers() throws Exception {

      for (int i = 1; i <= 4; i++) {
         String serverConfigName = "topology-check/broker" + i;

         File server0Location = getFileServerLocation(serverConfigName);
         deleteDirectory(server0Location);

         {
            HelperCreate cliCreateServer = helperCreate();
            cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(server0Location).
               setConfiguration("./src/main/resources/servers/" + serverConfigName);
            cliCreateServer.createServer();
         }

      }

   }


   Process[] process = new Process[4];
   String[] URLS = new String[]{URI_1, URI_2, URI_3, URI_4};
   String[] SERVER_NAMES = new String[]{SERVER_NAME_1, SERVER_NAME_2, SERVER_NAME_3, SERVER_NAME_4};

   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_1);
      cleanupData(SERVER_NAME_2);
      cleanupData(SERVER_NAME_3);
      cleanupData(SERVER_NAME_4);
      for (int i = 0; i < process.length; i++) {
         process[i] = startServer(SERVER_NAMES[i], i, 0);
      }

      for (int i = 0; i < process.length; i++) {
         ServerUtil.waitForServerToStart(i, "admin", "admin", 30_000);
      }

      disableCheckThread();
   }

   @Test
   public void testCheckTopology() throws Throwable {
      String[] uris = new String[]{URI_1, URI_2, URI_3, URI_4};
      String[] nodes = new String[4];
      for (int i = 0; i < 4; i++) {
         SimpleManagement simpleManagement = new SimpleManagement(uris[i], "admin", "admin");
         nodes[i] = simpleManagement.getNodeID();
         logger.info("node[{}]={}", i, nodes[i]);
      }

      validateTopology(uris, nodes, 0, 1, 2, 3);

      shutdown(process[1]);
      validateTopology(uris, nodes, 0, 2, 3);

      shutdown(process[3]);
      validateTopology(uris, nodes, 0, 2);

      process[1] = startServer(SERVER_NAMES[1], 1, 10_000);
      validateTopology(uris, nodes, 0, 1, 2);

      process[3] = startServer(SERVER_NAMES[3], 3, 10_000);
      validateTopology(uris, nodes, 0, 1, 2, 3);

      shutdown(process[0]);
      process[0] = startServer(SERVER_NAMES[0], 0, 10_000);
      validateTopology(uris, nodes, 0, 1, 2, 3);

      shutdown(process[0]);
      shutdown(process[1]);
      shutdown(process[2]);
      process[2] = startServer(SERVER_NAMES[2], 2, 10_000);
      validateTopology(uris, nodes, 2, 3);

      process[0] = startServer(SERVER_NAMES[0], 0, 10_000);
      process[1] = startServer(SERVER_NAMES[1], 1, 10_000);
      validateTopology(uris, nodes, 0, 1, 2, 3);

      shutdown(process[3]);
      process[3] = startServer(SERVER_NAMES[3], 0, 10_000);
      validateTopology(uris, nodes, 0, 1, 2, 3);
   }

   private void shutdown(Process process) {
      process.destroy();
      Wait.assertFalse(process::isAlive);
      removeProcess(process);
   }

   private void validateTopology(String[] uris, String[] nodeIDs, int... validNodes) throws Exception {
      for (int i : validNodes) {
         SimpleManagement simpleManagement = new SimpleManagement(uris[i], "admin", "adming");
         Wait.assertEquals(validNodes.length, () -> simpleManagement.listNetworkTopology().size(), 500, 5000);

         JsonArray topologyArray = simpleManagement.listNetworkTopology();
         assertNotNull(topologyArray);

         for (int j : validNodes) {
            JsonObject itemTopology = findTopologyNode(nodeIDs[j], topologyArray);
            assertNotNull(itemTopology);
            JsonString jsonString = (JsonString) itemTopology.get("live");
            assertEquals(uris[j], "tcp://" + jsonString.getString());
         }
      }

      ClusterNodeVerifier clusterVerifier = new ClusterNodeVerifier(uris[validNodes[0]], "admin", "admin");
      assertTrue(clusterVerifier.verify(new ActionContext()));
   }

   JsonObject findTopologyNode(String nodeID, JsonArray topologyArray) {
      for (int i = 0; i < topologyArray.size(); i++) {
         JsonObject object = topologyArray.getJsonObject(i);
         if (nodeID.equals(object.getString("nodeID"))) {
            return object;
         }
      }
      return null;
   }
}