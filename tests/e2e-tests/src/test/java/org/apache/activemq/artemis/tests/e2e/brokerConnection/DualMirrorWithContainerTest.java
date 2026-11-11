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
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.tests.e2e.common.ContainerService;
import org.apache.activemq.artemis.tests.e2e.common.E2ETestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DualMirrorWithContainerTest extends E2ETestBase {


   Object network;

   public Object serverA;

   public Object serverB;

   private final String SERVER_A_LOCATION = basedir + "/target/brokerConnect/serverA";
   private final String SERVER_B_LOCATION = basedir + "/target/brokerConnect/serverB";

   @BeforeEach
   public void beforeStart() throws Exception {
      disableCheckThread();
      ValidateContainer.assumeArtemisContainer();

      assertNotNull(basedir);
      recreateBrokerDirectory(SERVER_B_LOCATION);
      recreateBrokerDirectory(SERVER_A_LOCATION);
      ContainerService service = ContainerService.getService();
      network = service.newNetwork();
      serverA = service.newBrokerImage();
      serverB = service.newBrokerImage();
      service.setNetwork(serverA, network);
      service.setNetwork(serverB, network);
      service.exposePorts(serverA, 61616);
      service.exposePorts(serverB, 61616);
      service.prepareInstance(SERVER_A_LOCATION);
      service.prepareInstance(SERVER_B_LOCATION);
      service.exposeBrokerHome(serverA, SERVER_A_LOCATION);
      service.exposeBrokerHome(serverB, SERVER_B_LOCATION);
      service.exposeHosts(serverA, "serverA");
      service.exposeHosts(serverB, "serverB");

      service.start(serverA);
      service.start(serverB);

      service.waitForServerToStart(serverA, "artemis", "artemis", 10_000);
      service.waitForServerToStart(serverB, "artemis", "artemis", 10_000);

      cfA = service.createCF(serverA, "amqp");
      cfB = service.createCF(serverA, "amqp");
   }


   @AfterEach
   public void afterStop() {
      ContainerService.getService().stop(serverA);
      ContainerService.getService().stop(serverB);
   }


   ConnectionFactory cfA;
   ConnectionFactory cfB;

   @Test
   public void testReconnectMirror() throws Throwable {
      testReconnectMirror(false);
   }

   @Test
   public void testReconnectMirrorLarge() throws Throwable {
      testReconnectMirror(true);
   }

   private void testReconnectMirror(boolean largemessage) throws Throwable {

      int NUMBER_OF_MESSAGES = 1_000;
      int FAILURE_INTERVAL = 500;
      String extraBody = largemessage ? RandomUtil.randomAlphaNumericString(200 * 1024) : "message ";

      try (Connection connectionA = cfA.createConnection("artemis", "artemis")) {

         // Testing things on the direction from mirroring from A to B...
         Session sessionA = connectionA.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = sessionA.createQueue("someQueue");
         MessageProducer producerA = sessionA.createProducer(queue);
         producerA.setDeliveryMode(DeliveryMode.PERSISTENT);

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = sessionA.createTextMessage(extraBody + i);
            message.setStringProperty("color", i % 2 == 0 ? "yellow" : "red");
            message.setIntProperty("i", i);
            producerA.send(message);

            if (i % 100 == 0 && i > 0) {
               System.out.println("Message " + i);
               sessionA.commit();
            }

            if (i % FAILURE_INTERVAL == 0 && i > 0) {
               restartB();
            }
         }

         sessionA.commit();

         connectionA.start();

      }

      try (Connection connectionB = cfB.createConnection("artemis", "artemis")) {

         // Testing things on the direction from mirroring from A to B...
         Session sessionB = connectionB.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = sessionB.createQueue("someQueue");

         connectionB.start();

         MessageConsumer consumerB = sessionB.createConsumer(queue);

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = (TextMessage)consumerB.receive(5_000);
            assertNotNull(message, "expected message at " + i);
            assertEquals(extraBody + i, message.getText());
         }
         assertNull(consumerB.receiveNoWait());
         sessionB.rollback();
      }

      int restarted = 0;

      try (Connection connectionB = cfB.createConnection("artemis", "artemis")) {

         // Testing things on the direction from mirroring from A to B...
         Session sessionB = connectionB.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = sessionB.createQueue("someQueue");

         connectionB.start();

         MessageConsumer consumerB = sessionB.createConsumer(queue, "color='yellow'");

         int op = 0;
         for (int i = 0; i < NUMBER_OF_MESSAGES; i += 2) {
            TextMessage message = (TextMessage)consumerB.receive(5_000);
            assertNotNull(message, "expected message at " + i);
            assertEquals(extraBody + i, message.getText());

            if (op++ > 0 && op % FAILURE_INTERVAL == 0) {
               restartA(++restarted);
            }
         }

         assertNull(consumerB.receiveNoWait());
      }

      System.out.println("Restarted serverA " + restarted + " times");

      Thread.sleep(5000);

      try (Connection connectionA = cfA.createConnection("artemis", "artemis")) {

         // Testing things on the direction from mirroring from A to B...
         Session sessionA = connectionA.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = sessionA.createQueue("someQueue");

         connectionA.start();

         MessageConsumer consumerA = sessionA.createConsumer(queue);

         for (int i = 1; i < NUMBER_OF_MESSAGES; i += 2) {
            TextMessage message = (TextMessage)consumerA.receive(5_000);
            assertNotNull(message, "expected message at " + i);
            // We should only have red left
            assertEquals("red", message.getStringProperty("color"), "Unexpected message at " + i + " with i=" + message.getIntProperty("i"));
            assertEquals(extraBody + i, message.getText());
         }

         sessionA.commit();

         assertNull(consumerA.receiveNoWait());
      }

      Thread.sleep(5000);

      try (Connection connectionB = cfB.createConnection("artemis", "artemis")) {

         // Testing things on the direction from mirroring from A to B...
         Session sessionB = connectionB.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = sessionB.createQueue("someQueue");

         connectionB.start();

         MessageConsumer consumerB = sessionB.createConsumer(queue);

         TextMessage message = (TextMessage)consumerB.receiveNoWait();

         if (message != null) {
            fail("was expected null, however received " + message.getText());
         }
      }

   }

   private void restartB() throws Exception {
      ContainerService.getService().restartWithKill(serverB);
      cfB = ContainerService.getService().createCF(serverB, "amqp");
   }

   private void restartA(int restartNumber) throws Exception {
      ContainerService.getService().restartWithKill(serverA);
      cfA = ContainerService.getService().createCF(serverB, "amqp");
   }

}
