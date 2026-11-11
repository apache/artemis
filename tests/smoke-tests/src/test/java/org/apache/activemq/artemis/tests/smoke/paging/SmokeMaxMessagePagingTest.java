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

package org.apache.activemq.artemis.tests.smoke.paging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.messages.Producer;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SmokeMaxMessagePagingTest extends SmokeTestBase {

   public static final String SERVER_NAME_GLOBAL = "pagingGlobalMaxMessages";
   public static final String SERVER_NAME_ADDRESS = "pagingAddressMaxMessages";


   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_GLOBAL);
      deleteDirectory(server0Location);
      File server1Location = getFileServerLocation(SERVER_NAME_ADDRESS);
      deleteDirectory(server1Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(server0Location);
         cliCreateServer.setArgs("--java-options", "-Djava.rmi.server.hostname=localhost", "--global-max-messages", "1000");
         cliCreateServer.createServer();
      }

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(server1Location).
            setConfiguration("./src/main/resources/servers/" + SERVER_NAME_ADDRESS);
         cliCreateServer.createServer();
      }
   }

   @BeforeEach
   public void before() throws Exception {
   }

   @Test
   public void testGlobalMaxSend() throws Exception {
      internalTestSend(SERVER_NAME_GLOBAL);
   }

   @Test
   public void testAddressMaxSend() throws Exception {
      internalTestSend(SERVER_NAME_ADDRESS);
   }

   public void internalTestSend(String serverName) throws Exception {
      cleanupData(serverName);
      startServer(serverName, 0, 30000);
      internalSend("core", 2000);
      assertTrue(isPaging(serverName), "System did not page");
   }

   boolean isPaging(String serverName) {
      File location = new File(getServerLocation(serverName));
      File paging = new File(location, "data/paging");
      File[] pagingContents = paging.listFiles();
      return pagingContents != null && pagingContents.length > 0;
   }


   @Test
   public void testGlobalMaxSendRestart() throws Exception {
      internalTestSendWithRestart(SERVER_NAME_GLOBAL);
   }

   @Test
   public void testAddressMaxSendRestart() throws Exception {
      internalTestSendWithRestart(SERVER_NAME_ADDRESS);
   }

   public void internalTestSendWithRestart(String serverName) throws Exception {
      cleanupData(serverName);
      Process process = startServer(serverName, 0, 30000);
      internalSend("core", 500);

      assertFalse(isPaging(serverName));

      process.destroy();
      process = startServer(serverName, 0, 30000);
      internalSend("core", 1500);

      assertTrue(isPaging(serverName));
   }

   private void internalSend(String protocol, int numberOfMessages) throws Exception {
      Producer producer = (Producer)new Producer().setMessageSize(1).setMessageCount(numberOfMessages).setTxBatchSize(500);
      producer.setProtocol(protocol);
      producer.setSilentInput(true);
      producer.execute(new ActionContext());
   }

}
