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

package org.apache.activemq.artemis.tests.smoke.checkTest;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.cli.CLIException;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.check.NodeCheck;
import org.apache.activemq.artemis.cli.commands.check.QueueCheck;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

public class CheckTest extends SmokeTestBase {

   private static ActionContext ACTION_CONTEXT = new ActionContext(System.in, System.out, System.out);

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String SERVER_NAME_1 = "check-test/live";
   private static final String SERVER_NAME_2 = "check-test/backup";

   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_1);
      File server1Location = getFileServerLocation(SERVER_NAME_2);
      deleteDirectory(server1Location);
      deleteDirectory(server0Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setSharedStore(true).setBackup(false).setSharedStore(true).setDataFolder("./target/check-test/data").setFailoverOnShutdown(true).setStaticCluster("tcp://localhost:61716").setArtemisInstance(server0Location).setArgs("--cluster-user", "my-cluster-user", "--cluster-password", "my-cluster-password");
         cliCreateServer.createServer();
      }

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setSharedStore(true).setBackup(true).setSharedStore(true).setDataFolder("./target/check-test/data").setFailoverOnShutdown(true).setStaticCluster("tcp://localhost:61616").setPortOffset(100).setArtemisInstance(server1Location).setArgs("--cluster-user", "my-cluster-user", "--cluster-password", "my-cluster-password");
         cliCreateServer.createServer();
      }
   }

   Process primaryProcess;
   Process backupProcess;

   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_1);
      cleanupData(SERVER_NAME_2);

      File location = new File(getServerLocation(SERVER_NAME_1));
      deleteDirectory(new File(location, "../data"));

      disableCheckThread();
   }

   @Test
   @Timeout(60)
   public void testNodeCheckActions() throws Exception {
      primaryProcess = startServer(SERVER_NAME_1, 0, 0);
      ServerUtil.waitForServerToStart("tcp://localhost:61616", 5_000);

      NodeCheck nodeCheck = new NodeCheck();
      nodeCheck.setUser("admin");
      nodeCheck.setPassword("admin");
      assertEquals(1, nodeCheck.execute(ACTION_CONTEXT));

      nodeCheck = new NodeCheck();
      nodeCheck.setUser("admin");
      nodeCheck.setPassword("admin");
      nodeCheck.setUp(true);
      assertEquals(1, nodeCheck.execute(ACTION_CONTEXT));

      nodeCheck = new NodeCheck();
      nodeCheck.setUser("admin");
      nodeCheck.setPassword("admin");
      nodeCheck.setDiskUsage(-1);
      assertEquals(1, nodeCheck.execute(ACTION_CONTEXT));

      nodeCheck = new NodeCheck();
      nodeCheck.setUser("admin");
      nodeCheck.setPassword("admin");
      nodeCheck.setDiskUsage(90);
      assertEquals(1, nodeCheck.execute(ACTION_CONTEXT));

      try {
         nodeCheck = new NodeCheck();
         nodeCheck.setUser("admin");
         nodeCheck.setPassword("admin");
         nodeCheck.setDiskUsage(0);
         nodeCheck.execute(ACTION_CONTEXT);

         fail("CLIException expected.");
      } catch (Exception e) {
         assertInstanceOf(CLIException.class, e, "CLIException expected.");
      }

      nodeCheck = new NodeCheck();
      nodeCheck.setUser("admin");
      nodeCheck.setPassword("admin");
      nodeCheck.setMemoryUsage(90);
      assertEquals(1, nodeCheck.execute(ACTION_CONTEXT));

      try {
         nodeCheck = new NodeCheck();
         nodeCheck.setUser("admin");
         nodeCheck.setPassword("admin");
         nodeCheck.setMemoryUsage(-1);
         nodeCheck.execute(ACTION_CONTEXT);

         fail("CLIException expected.");
      } catch (Exception e) {
         assertInstanceOf(CLIException.class, e, "CLIException expected.");
      }
   }

   @Test
   @Timeout(60)
   public void testCheckTopology() throws Exception {
      primaryProcess = startServer(SERVER_NAME_1, 0, 0);
      ServerUtil.waitForServerToStart("tcp://localhost:61616", 5_000);

      NodeCheck nodeCheck = new NodeCheck();
      nodeCheck.setUser("admin");
      nodeCheck.setPassword("admin");
      nodeCheck.setPrimary(true);
      assertEquals(1, nodeCheck.execute(ACTION_CONTEXT));

      try {
         nodeCheck = new NodeCheck();
         nodeCheck.setUser("admin");
         nodeCheck.setPassword("admin");
         nodeCheck.setBackup(true);
         nodeCheck.execute(ACTION_CONTEXT);

         fail("CLIException expected.");
      } catch (Exception e) {
         assertInstanceOf(CLIException.class, e, "CLIException expected.");
      }

      nodeCheck = new NodeCheck();
      nodeCheck.setUser("admin");
      nodeCheck.setPassword("admin");
      nodeCheck.setPrimary(true);
      assertEquals(1, nodeCheck.execute(ACTION_CONTEXT));

      try {
         nodeCheck = new NodeCheck();
         nodeCheck.setUser("admin");
         nodeCheck.setPassword("admin");
         nodeCheck.setBackup(true);
         nodeCheck.execute(ACTION_CONTEXT);

         fail("CLIException expected.");
      } catch (Exception e) {
         assertInstanceOf(CLIException.class, e, "CLIException expected.");
      }

      backupProcess = startServer(SERVER_NAME_2, 0, 0);

      SimpleManagement simpleManagement = new SimpleManagement("tcp://localhost:61616", "admin", "admin");
      Wait.assertTrue(() -> hasBackup(simpleManagement), 5000, 1_000);
      nodeCheck = new NodeCheck();
      nodeCheck.setUser("admin");
      nodeCheck.setPassword("admin");
      nodeCheck.setPrimary(true);
      nodeCheck.setBackup(true);
      nodeCheck.setPeers(2);
      assertEquals(3, nodeCheck.execute(ACTION_CONTEXT));
   }

   public boolean hasBackup(SimpleManagement simpleManagement) {
      try {
         JsonArray topology = simpleManagement.listNetworkTopology();
         if (topology.size() != 1) {
            return false;
         }

         return topology.getJsonObject(0).getString("backup", null) != null;
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
      }

      return false;
   }

   @Test
   @Timeout(60)
   public void testQueueCheckUp() throws Exception {
      primaryProcess = startServer(SERVER_NAME_1, 0, 0);
      ServerUtil.waitForServerToStart("tcp://localhost:61616", 5_000);

      SimpleManagement simpleManagement = new SimpleManagement("tcp://localhost:61616", "admin", "admin");

      String queueName = "Q_" + RandomUtil.randomUUIDString();

      QueueCheck queueCheck;

      try {
         queueCheck = new QueueCheck();
         queueCheck.setUser("admin");
         queueCheck.setPassword("admin");
         queueCheck.setName(queueName);
         queueCheck.execute(ACTION_CONTEXT);

         fail("CLIException expected.");
      } catch (Exception e) {
         assertInstanceOf(CLIException.class, e, "CLIException expected.");
      }

      ServerLocator locator = ServerLocatorImpl.newLocator("tcp://localhost:61616");
      try (ClientSessionFactory factory = locator.createSessionFactory(); ClientSession session = factory.createSession()) {
         session.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      }

      try {
         queueCheck = new QueueCheck();
         queueCheck.setUser("admin");
         queueCheck.setPassword("admin");
         queueCheck.setName(queueName);
         queueCheck.setConsume(1);
         queueCheck.setTimeout(100);
         queueCheck.execute(ACTION_CONTEXT);

         fail("CLIException expected.");
      } catch (Exception e) {
         assertInstanceOf(CLIException.class, e, "CLIException expected.");
      }


      queueCheck = new QueueCheck();
      queueCheck.setUser("admin");
      queueCheck.setPassword("admin");
      queueCheck.setName(queueName);
      assertEquals(1, queueCheck.execute(ACTION_CONTEXT));

      queueCheck = new QueueCheck();
      queueCheck.setUser("admin");
      queueCheck.setPassword("admin");
      queueCheck.setUp(true);
      queueCheck.setName(queueName);
      assertEquals(1, queueCheck.execute(ACTION_CONTEXT));

      ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");

      final int messages = 3;

      queueCheck = new QueueCheck();
      queueCheck.setUser("admin");
      queueCheck.setPassword("admin");
      queueCheck.setName(queueName);
      queueCheck.setBrowse(null);
      assertEquals(1, queueCheck.execute(ACTION_CONTEXT));


      queueCheck = new QueueCheck();
      queueCheck.setUser("admin");
      queueCheck.setPassword("admin");
      queueCheck.setName(queueName);
      queueCheck.setConsume(null);
      assertEquals(1, queueCheck.execute(ACTION_CONTEXT));


      try (Connection connection = cf.createConnection(); Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
         MessageProducer producer = session.createProducer(session.createQueue(queueName));
         for (int i = 0; i < messages; i++) {
            TextMessage message = session.createTextMessage("hello " + i);
            message.setStringProperty("local", String.valueOf(i));
            producer.send(message);
         }
         session.commit();
         Wait.assertEquals(messages, () -> getMessageCount(simpleManagement, queueName), 1_000);
      }


      queueCheck = new QueueCheck();
      queueCheck.setUser("admin");
      queueCheck.setPassword("admin");
      queueCheck.setName(queueName);
      queueCheck.setBrowse(messages);
      assertEquals(1, queueCheck.execute(ACTION_CONTEXT));

      queueCheck = new QueueCheck();
      queueCheck.setUser("admin");
      queueCheck.setPassword("admin");
      queueCheck.setName(queueName);
      queueCheck.setConsume(messages);
      assertEquals(1, queueCheck.execute(ACTION_CONTEXT));

      queueCheck = new QueueCheck();
      queueCheck.setUser("admin");
      queueCheck.setPassword("admin");
      queueCheck.setName(queueName);
      queueCheck.setProduce(messages);
      assertEquals(1, queueCheck.execute(ACTION_CONTEXT));

      Wait.assertEquals(messages, () -> getMessageCount(simpleManagement, queueName), 1_000);


      queueCheck = new QueueCheck();
      queueCheck.setUser("admin");
      queueCheck.setPassword("admin");
      queueCheck.setName(queueName);
      queueCheck.setConsume(messages);
      assertEquals(1, queueCheck.execute(ACTION_CONTEXT));

      Wait.assertEquals(0, () -> getMessageCount(simpleManagement, queueName), 1_000);

      queueCheck = new QueueCheck();
      queueCheck.setUser("admin");
      queueCheck.setPassword("admin");
      queueCheck.setName(queueName);
      queueCheck.setProduce(messages);
      queueCheck.setBrowse(messages);
      queueCheck.setConsume(messages);
      assertEquals(3, queueCheck.execute(ACTION_CONTEXT));

      Wait.assertEquals(0, () -> getMessageCount(simpleManagement, queueName), 1_000);
   }
}
