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
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.tests.e2e.common.ContainerService;
import org.apache.activemq.artemis.tests.e2e.common.E2ETestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SplitMirrorTest extends E2ETestBase {

   Object network;

   public Object serverMainA;

   public Object serverMainB;

   public Object serverRoot;

   ContainerService service = ContainerService.getService();

   private final String SERVER_ROOT = basedir + "/target/brokerConnect/splitMirror/serverRoot";
   private final String SERVER_A = basedir + "/target/brokerConnect/splitMirror/serverA";
   private final String SERVER_B = basedir + "/target/brokerConnect/splitMirror/serverB";

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
      service.exposeHosts(serverRoot, "artemisRoot");
      service.exposeHosts(serverMainA, "artemisA");
      service.exposeHosts(serverMainB, "artemisB");

      service.start(serverMainA);
      service.start(serverMainB);
      service.start(serverRoot);
   }

   @AfterEach
   public void afterStop() {
      service.stop(serverRoot);
      service.stop(serverMainA);
      service.stop(serverMainB);
   }

   @Test
   public void testSplitMirror() throws Throwable {
      ConnectionFactory cfRoot = service.createCF(serverRoot, "amqp");
      ConnectionFactory cfA = service.createCF(serverMainA, "amqp");
      ConnectionFactory cfB = service.createCF(serverMainB, "amqp");
      try (Connection connection = cfRoot.createConnection()) {
         Session session = connection.createSession();
         Queue queue = session.createQueue("someQueue");
         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < 10; i++) {
            producer.send(session.createTextMessage("sessionRoot " + i));
         }
         connection.start();

         MessageConsumer consumer = session.createConsumer(queue);
         for (int i = 0; i < 5; i++) {
            assertNotNull(consumer.receive(5000));
         }
         consumer.close();
      }

      Thread.sleep(1000);

      try (Connection connection = cfA.createConnection()) {
         Session session = connection.createSession();
         Queue queue = session.createQueue("someQueue");
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
         for (int i = 5; i < 10; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
         }
         assertNull(consumer.receiveNoWait());
      }

      try (Connection connection = cfB.createConnection()) {
         Session session = connection.createSession();
         Queue queue = session.createQueue("someQueue");
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
         for (int i = 5; i < 10; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
         }
         assertNull(consumer.receiveNoWait());
      }

      try (Connection connection = cfRoot.createConnection()) {
         Session session = connection.createSession();
         Queue queue = session.createQueue("someQueue");
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
         for (int i = 5; i < 10; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
         }
         assertNull(consumer.receiveNoWait());
         consumer.close();
         service.kill(serverMainA);
         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < 33; i++) {
            producer.send(session.createTextMessage("afterKill " + i));
         }
      }

      Thread.sleep(1000);

      try (Connection connection = cfB.createConnection()) {
         Session session = connection.createSession();
         Queue queue = session.createQueue("someQueue");
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
         for (int i = 0; i < 33; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            System.out.println("message.getText() = " + message.getText() + " i = " + i);
            assertEquals("afterKill " + i, message.getText());
         }
         assertNull(consumer.receiveNoWait());
      }

      service.start(serverMainA);

      cfA = service.createCF(serverMainA, "amqp");

      try (Connection connection = cfA.createConnection()) {
         Session session = connection.createSession();
         Queue queue = session.createQueue("someQueue");
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
         for (int i = 0; i < 33; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals("afterKill " + i, message.getText());
         }
         assertNull(consumer.receiveNoWait());
      }
   }

}
