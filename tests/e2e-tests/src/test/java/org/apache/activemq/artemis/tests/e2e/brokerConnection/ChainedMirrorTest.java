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
package org.apache.activemq.artemis.tests.e2e.brokerConnection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.tests.e2e.common.ContainerService;
import org.apache.activemq.artemis.tests.e2e.common.E2ETestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ChainedMirrorTest extends E2ETestBase {

   Object network;

   public Object serverMainA;

   public Object serverMainB;

   public Object serverRoot;

   ContainerService service = ContainerService.getService();

   private final String SERVER_ROOT = basedir + "/target/brokerConnect/chainedMirror/serverRoot";
   private final String SERVER_A = basedir + "/target/brokerConnect/chainedMirror/serverA";
   private final String SERVER_B = basedir + "/target/brokerConnect/chainedMirror/serverB";

   @BeforeEach
   public void beforeStart() throws Exception {
      disableCheckThread();
      ValidateContainer.assumeArtemisContainer();

      assertNotNull(basedir);
      recreateBrokerDirectory(SERVER_ROOT);
      recreateBrokerDirectory(SERVER_A);
      recreateBrokerDirectory(SERVER_B);
      network = service.newNetwork();
      serverMainA = service.newBrokerImage();
      serverMainB = service.newBrokerImage();
      serverRoot = service.newBrokerImage();
      service.setNetwork(serverMainA, network);
      service.setNetwork(serverMainB, network);
      service.setNetwork(serverRoot, network);
      service.exposePorts(serverMainA, 61616);
      service.exposePorts(serverMainB, 61616);
      service.exposePorts(serverRoot, 61616);
      service.prepareInstance(SERVER_ROOT);
      service.prepareInstance(SERVER_A);
      service.prepareInstance(SERVER_B);
      service.exposeBrokerHome(serverMainA, SERVER_A);
      service.exposeBrokerHome(serverMainB, SERVER_B);
      service.exposeBrokerHome(serverRoot, SERVER_ROOT);
      service.exposeHosts(serverRoot, "artemisTestRoot");
      service.exposeHosts(serverMainA, "artemisTestA");
      service.exposeHosts(serverMainB, "artemisTestB");

      service.startLogging(serverMainB, "ServerB:");
      service.start(serverMainB);
      service.start(serverMainA);
      service.start(serverRoot);
   }

   @AfterEach
   public void afterStop() {
      service.stop(serverRoot);
      service.stop(serverMainA);
      service.stop(serverMainB);
   }


   private String getQueueName() {
      return "someQueue";
   }

   @Test
   public void testChained() throws Throwable {
      ConnectionFactory factory = service.createCF(serverRoot, "amqp");
      ConnectionFactory factory2 = service.createCF(serverMainA, "amqp");
      ConnectionFactory factory3 = service.createCF(serverMainB, "amqp");

      try (Connection conn = factory.createConnection()) {
         Session session = conn.createSession();
         MessageProducer producer = session.createProducer(session.createQueue(getQueueName()));
         for (int i = 0; i < 40; i++) {
            producer.send(session.createTextMessage("message " + i));
         }
      }

      Thread.sleep(5000); // some time to allow eventual loops

      try (Connection conn = factory.createConnection()) {
         Session session = conn.createSession();
         conn.start();
         MessageConsumer consumer = session.createConsumer(session.createQueue(getQueueName()));
         for (int i = 0; i < 10; i++) {
            TextMessage message = (TextMessage) consumer.receive(1000);
            assertNotNull(message);
            assertEquals("message " + i, message.getText());
         }
         consumer.close();
      }

      Thread.sleep(5000); // some time to allow eventual loops

      try (Connection conn = factory2.createConnection()) {
         Session session = conn.createSession();
         conn.start();
         MessageConsumer consumer = session.createConsumer(session.createQueue(getQueueName()));
         for (int i = 10; i < 20; i++) {
            TextMessage message = (TextMessage) consumer.receive(1000);
            assertNotNull(message);
            assertEquals("message " + i, message.getText());
         }
         consumer.close();
      }

      Thread.sleep(5000); // some time to allow eventual loops

      try (Connection conn = factory3.createConnection()) {
         Session session = conn.createSession();
         conn.start();
         MessageConsumer consumer = session.createConsumer(session.createQueue(getQueueName()));
         for (int i = 20; i < 30; i++) {
            TextMessage message = (TextMessage) consumer.receive(1000);
            assertNotNull(message);
            assertEquals("message " + i, message.getText());
         }
         consumer.close();
      }


      Thread.sleep(5000); // some time to allow eventual loops

      try (Connection conn = factory.createConnection()) {
         Session session = conn.createSession();
         conn.start();
         MessageConsumer consumer = session.createConsumer(session.createQueue(getQueueName()));
         for (int i = 30; i < 40; i++) {
            TextMessage message = (TextMessage) consumer.receive(1000);
            assertNotNull(message);
            assertEquals("message " + i, message.getText());
         }
         consumer.close();
      }

      Thread.sleep(5000); // some time to allow eventual loops

      try (Connection conn = factory.createConnection()) {
         Session session = conn.createSession();
         conn.start();
         MessageConsumer consumer = session.createConsumer(session.createQueue(getQueueName()));
         assertNull(consumer.receiveNoWait());
         consumer.close();
      }

      try (Connection conn = factory2.createConnection()) {
         Session session = conn.createSession();
         conn.start();
         MessageConsumer consumer = session.createConsumer(session.createQueue(getQueueName()));
         assertNull(consumer.receiveNoWait());
         consumer.close();
      }

      try (Connection conn = factory3.createConnection()) {
         Session session = conn.createSession();
         conn.start();
         MessageConsumer consumer = session.createConsumer(session.createQueue(getQueueName()));
         assertNull(consumer.receiveNoWait());
         consumer.close();
      }

   }

}
