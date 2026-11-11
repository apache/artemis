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
package org.apache.activemq.artemis.tests.smoke.brokerConnection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import java.io.File;

import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.activemq.artemis.tests.util.CFUtil;

public class BrokerConnectionBridgeSecurityTest extends SmokeTestBase {

   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_A);
      File server1Location = getFileServerLocation(SERVER_NAME_B);
      deleteDirectory(server0Location);
      deleteDirectory(server1Location);


      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setAllowAnonymous(false).setUser("A").setPassword("A").setNoWeb(true).setConfiguration("./src/main/resources/servers/brokerConnect/bridgeSecurityA").setArtemisInstance(server0Location);
         cliCreateServer.createServer();
      }

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setAllowAnonymous(false).setUser("A").setPassword("A").setNoWeb(true).setConfiguration("./src/main/resources/servers/brokerConnect/bridgeSecurityB").setArtemisInstance(server1Location);
         cliCreateServer.createServer();
      }
   }

   public static final String SERVER_NAME_A = "brokerConnect/bridgeSecurityA";
   public static final String SERVER_NAME_B = "brokerConnect/bridgeSecurityB";

   @BeforeEach
   public void before() throws Exception {
      // no need to cleanup, these servers don't have persistence
      // start serverB first, after all ServerA needs it alive to create connections
      startServer(SERVER_NAME_B, 0, 0);
      startServer(SERVER_NAME_A, 0, 0);

      ServerUtil.waitForServerToStart(1, "B", "B", 30000);
      ServerUtil.waitForServerToStart(0, "A", "A", 30000);
   }

   @Test
   public void testBridgeOverBokerConnection() throws Throwable {
      ConnectionFactory cfA = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61616");
      ConnectionFactory cfB = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61617");


      try (Connection connectionA = cfA.createConnection("A", "A");
           Connection connectionB = cfB.createConnection("B", "B")) {
         Session sessionA = connectionA.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queueToB = sessionA.createQueue("toB");
         Queue queueToA = sessionA.createQueue("toA");
         MessageProducer producerA = sessionA.createProducer(queueToB);
         for (int i = 0; i < 10; i++) {
            producerA.send(sessionA.createTextMessage("toB"));
         }

         Session sessionB = connectionB.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumerB = sessionB.createConsumer(queueToB);
         connectionB.start();

         for (int i = 0; i < 10; i++) {
            TextMessage message = (TextMessage) consumerB.receive(1000);
            assertNotNull(message);
            assertEquals("toB", message.getText());
         }

         MessageProducer producerB = sessionB.createProducer(queueToA);
         for (int i = 0; i < 10; i++) {
            producerB.send(sessionA.createTextMessage("toA"));
         }
         assertNull(consumerB.receiveNoWait());

         connectionA.start();

         MessageConsumer consumerA = sessionA.createConsumer(queueToA);
         for (int i = 0; i < 10; i++) {
            TextMessage message = (TextMessage) consumerA.receive(1000);
            assertNotNull(message);
            assertEquals("toA", message.getText());
         }
         assertNull(consumerA.receiveNoWait());

      }
   }

}
