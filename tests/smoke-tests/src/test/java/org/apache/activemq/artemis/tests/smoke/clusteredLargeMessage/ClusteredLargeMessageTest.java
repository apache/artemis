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

package org.apache.activemq.artemis.tests.smoke.clusteredLargeMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import java.io.File;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClusteredLargeMessageTest extends SmokeTestBase {

   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_0);
      File server1Location = getFileServerLocation(SERVER_NAME_1);
      deleteDirectory(server1Location);
      deleteDirectory(server0Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setRole("amq").setUser("artemis").setPassword("artemis").setAllowAnonymous(true).setNoWeb(true).
            setArtemisInstance(server0Location).setClustered(true).
               setStaticCluster("tcp://localhost:61716").setArgs("--name", "cluster1", "--max-hops", "1", "--queues", "testQueue", "--cluster-user", "artemis", "--cluster-password", "artemis");
         cliCreateServer.createServer();
      }
      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setRole("amq").setUser("artemis").setPassword("artemis").setAllowAnonymous(true).setNoWeb(true).setPortOffset(100).
            setArtemisInstance(server1Location).setClustered(true).
               setStaticCluster("tcp://localhost:61616").setArgs("--name", "cluster2", "--max-hops", "1", "--queues", "testQueue", "--cluster-user", "artemis", "--cluster-password", "artemis");
         cliCreateServer.createServer();
      }
   }



   public static final String SERVER_NAME_0 = "clusteredLargeMessage/cluster1";
   public static final String SERVER_NAME_1 = "clusteredLargeMessage/cluster2";

   Process server0Process;
   Process server1Process;

   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_0);
      cleanupData(SERVER_NAME_1);
      server0Process = startServer(SERVER_NAME_0, 0, 0);
      server1Process = startServer(SERVER_NAME_1, 0, 0);

      ServerUtil.waitForServerToStart(0, null, null, 30000);
      ServerUtil.waitForServerToStart(100, null, null, 30000);

      SimpleManagement simpleManagement61616 = new SimpleManagement("tcp://localhost:61616", null, null);
      Wait.assertEquals(2, () -> simpleManagement61616.listNetworkTopology().size());
      SimpleManagement simpleManagement61716 = new SimpleManagement("tcp://localhost:61716", null, null);
      Wait.assertEquals(2, () -> simpleManagement61716.listNetworkTopology().size());

   }

   @Test
   public void testLargeMessage() throws Exception {

      // I'm calling all 3 here as I want to run all of these with a single server start
      // without having to deal with beforeClass and afterClass on this test
      internalTestLargeMessge("CORE");
      internalTestLargeMessge("AMQP");
      internalTestLargeMessge("OPENWIRE");
   }

   private void internalTestLargeMessge(String protocol) throws Exception {

      ConnectionFactory server2CF = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61716");
      Connection connection2 = server2CF.createConnection();
      Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue queue2 = session2.createQueue("testQueue");
      MessageConsumer consumer2 = session2.createConsumer(queue2);
      connection2.start();

      ConnectionFactory server1CF = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      Connection connection1 = server1CF.createConnection();
      Session session1 = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue queue1 = session1.createQueue("testQueue");
      MessageProducer producer1 = session1.createProducer(queue1);

      String largeBody = RandomUtil.randomAlphaNumericString(2_000_000);

      for (int i = 0; i < 10; i++) {
         TextMessage message = session1.createTextMessage(largeBody);
         message.setStringProperty("i", Integer.toString(i));
         producer1.send(message);
      }

      for (int i = 0; i < 10; i++) {
         TextMessage message = (TextMessage) consumer2.receive(5000);
         assertNotNull(message);
         assertEquals(largeBody, message.getText());
      }

      connection1.close();
      connection2.close();
   }

   @Test
   public void testKillWhileSendingLargeCORE() throws Exception {
      testKillWhileSendingLarge("CORE");
   }

   @Test
   public void testKillWhileSendingLargeAMQP() throws Exception {
      testKillWhileSendingLarge("AMQP");
   }

   public void testKillWhileSendingLarge(String protocol) throws Exception {

      ConnectionFactory server2CF = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61716");
      Connection keepConsumerConnection = server2CF.createConnection();
      Session keepConsumerSession = keepConsumerConnection.createSession(true, Session.AUTO_ACKNOWLEDGE);
      // a consumer that we should keep to induce message redistribution
      MessageConsumer keepConsumer = keepConsumerSession.createConsumer(keepConsumerSession.createQueue("testQueue"));

      String largeBody = RandomUtil.randomAlphaNumericString(1024 * 1024);

      int NMESSAGES = 10;

      ConnectionFactory server1CF = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      try (Connection connection1 = server1CF.createConnection()) {
         Session session1 = connection1.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue1 = session1.createQueue("testQueue");
         MessageProducer producer1 = session1.createProducer(queue1);
         for (int i = 0; i < NMESSAGES; i++) {
            TextMessage message = session1.createTextMessage(largeBody);
            message.setStringProperty("i", Integer.toString(i));
            producer1.send(message);

            if (i == 5) {
               session1.commit();
            }
         }
         session1.commit();
      }

      keepConsumerConnection.close();
      server1Process.destroyForcibly();
      server1Process = startServer(SERVER_NAME_1, 100, 0);

      for (int i = 0; i < 100; i++) {
         // retrying the connection until the server is up
         try (Connection willbegone = server2CF.createConnection()) {
            break;
         } catch (Exception ignored) {
            Thread.sleep(100);
         }
      }

      try (Connection connection2 = server2CF.createConnection()) {
         Session session2 = connection2.createSession(true, Session.AUTO_ACKNOWLEDGE);
         Queue queue2 = session2.createQueue("testQueue");
         MessageConsumer consumer2 = session2.createConsumer(queue2);
         connection2.start();

         for (int i = 0; i < NMESSAGES; i++) {
            TextMessage message = (TextMessage) consumer2.receive(5000);
            assertNotNull(message);
            assertEquals(largeBody, message.getText());
         }
      }
   }
}

