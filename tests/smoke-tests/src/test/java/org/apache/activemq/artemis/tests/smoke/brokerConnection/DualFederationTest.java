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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

import java.io.File;

import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DualFederationTest extends SmokeTestBase {

   public static final String SERVER_NAME_A = "brokerConnect/federationA";
   public static final String SERVER_NAME_B = "brokerConnect/federationB";

   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_A);
      File server1Location = getFileServerLocation(SERVER_NAME_B);
      deleteDirectory(server1Location);
      deleteDirectory(server0Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setAllowAnonymous(true).setUser("A").setPassword("A").setRole("amq").setNoWeb(true).setConfiguration("./src/main/resources/servers/brokerConnect/federationA").setArtemisInstance(server0Location);
         cliCreateServer.createServer();
      }

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setAllowAnonymous(true).setUser("B").setPassword("B").setRole("amq").setNoWeb(true).setPortOffset(1).setConfiguration("./src/main/resources/servers/brokerConnect/federationB").setArtemisInstance(server1Location);
         cliCreateServer.createServer();
      }
   }


   Process processB;
   Process processA;

   @BeforeEach
   public void beforeClass() throws Exception {
      cleanupData(SERVER_NAME_A);
      cleanupData(SERVER_NAME_B);
      processB = startServer(SERVER_NAME_B, 0, 0);
      processA = startServer(SERVER_NAME_A, 0, 0);

      ServerUtil.waitForServerToStart(1, "B", "B", 30000);
      ServerUtil.waitForServerToStart(0, "A", "A", 30000);
   }

   @Test
   public void testFederatedBrokersAddressFederatedFromBtoA() throws Throwable {
      ConnectionFactory cfA = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61616");
      ConnectionFactory cfB = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61617");

      try (Connection connectionA = cfA.createConnection("A", "A");
           Connection connectionB = cfB.createConnection("B", "B")) {

         Session sessionA = connectionA.createSession(Session.AUTO_ACKNOWLEDGE);
         Session sessionB = connectionB.createSession(Session.AUTO_ACKNOWLEDGE);

         Topic topic = sessionA.createTopic("toA");

         connectionA.start();
         connectionB.start();

         // Create local demand on A for address 'toA' which should result in a receiver
         // being created to B on address 'toA'
         MessageConsumer consumerA = sessionA.createConsumer(topic);
         // Then a producer on broker B to address A should route to broker A
         MessageProducer producerB = sessionB.createProducer(topic);

         Thread.sleep(5_000); // Time for federation resources to build

         final Message message = sessionB.createTextMessage("message from broker B");
         producerB.send(message);

         final Message received = consumerA.receive(1_000);
         assertNotNull(received);
      }
   }

   @Test
   public void testFederatedBrokersQueueFederatedFromAtoB() throws Throwable {
      ConnectionFactory cfA = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61616");
      ConnectionFactory cfB = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61617");

      try (Connection connectionA = cfA.createConnection("A", "A");
           Connection connectionB = cfB.createConnection("B", "B")) {

         Session sessionA = connectionA.createSession(Session.AUTO_ACKNOWLEDGE);
         Session sessionB = connectionB.createSession(Session.AUTO_ACKNOWLEDGE);

         Queue queue = sessionA.createQueue("toB");

         connectionA.start();
         connectionB.start();

         // Create local demand on B for queue 'toB' which should result in a receiver
         // being created to A on queue 'toB'
         MessageConsumer consumerB = sessionB.createConsumer(queue);
         // Then a producer on broker A to queue toB should route to broker B
         MessageProducer producerA = sessionA.createProducer(queue);

         Thread.sleep(5_000); // Time for federation resources to build

         final Message message = sessionA.createTextMessage("message from broker A");
         producerA.send(message);

         final Message received = consumerB.receive(1_000);
         assertNotNull(received);
      }
   }
}
