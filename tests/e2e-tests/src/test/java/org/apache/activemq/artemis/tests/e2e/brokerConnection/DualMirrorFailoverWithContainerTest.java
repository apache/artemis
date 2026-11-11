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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class DualMirrorFailoverWithContainerTest extends E2ETestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   Object network;

   public Object serverMainA;

   public Object serverBackupA;

   public Object serverMainB;

   public Object serverBackupB;

   ContainerService service = ContainerService.getService();

   private final String SERVER_MAIN_A_LOCATION = basedir + "/target/brokerConnect/replicaMainServerA";
   private final String SERVER_BACKUP_A_LOCATION = basedir + "/target/brokerConnect/replicaBackupServerA";
   private final String SERVER_MAIN_B_LOCATION = basedir + "/target/brokerConnect/replicaMainServerB";
   private final String SERVER_BACKUP_B_LOCATION = basedir + "/target/brokerConnect/replicaBackupServerB";

   @BeforeEach
   public void beforeStart() throws Exception {
      disableCheckThread();
      ValidateContainer.assumeArtemisContainer();

      assertNotNull(basedir);
      recreateBrokerDirectory(SERVER_MAIN_A_LOCATION);
      recreateBrokerDirectory(SERVER_BACKUP_A_LOCATION);
      recreateBrokerDirectory(SERVER_MAIN_B_LOCATION);
      recreateBrokerDirectory(SERVER_BACKUP_B_LOCATION);
      network = service.newNetwork();
      serverMainA = service.newBrokerImage();
      serverMainB = service.newBrokerImage();
      serverBackupA = service.newBrokerImage();
      serverBackupB = service.newBrokerImage();
      service.setNetwork(serverMainA, network);
      service.setNetwork(serverBackupA, network);
      service.setNetwork(serverMainB, network);
      service.setNetwork(serverBackupB, network);
      service.exposePorts(serverMainA, 61616);
      service.exposePorts(serverMainB, 61616);
      service.exposePorts(serverBackupA, 61616);
      service.exposePorts(serverBackupB, 61616);
      service.prepareInstance(SERVER_MAIN_A_LOCATION);
      service.prepareInstance(SERVER_MAIN_B_LOCATION);
      service.prepareInstance(SERVER_BACKUP_A_LOCATION);
      service.prepareInstance(SERVER_BACKUP_B_LOCATION);
      service.exposeBrokerHome(serverMainA, SERVER_MAIN_A_LOCATION);
      service.exposeBrokerHome(serverMainB, SERVER_MAIN_B_LOCATION);
      service.exposeBrokerHome(serverBackupA, SERVER_BACKUP_A_LOCATION);
      service.exposeBrokerHome(serverBackupB, SERVER_BACKUP_B_LOCATION);
      service.exposeHosts(serverMainA, "mainA");
      service.exposeHosts(serverBackupA, "backupA");
      service.exposeHosts(serverMainB, "mainB");
      service.exposeHosts(serverBackupB, "backupB");
      service.logWait(serverBackupA, ".*AMQ221024.*"); // replica is synchronized
      service.logWait(serverBackupB, ".*AMQ221024.*");

      service.start(serverMainA);
      service.start(serverMainB);
      service.start(serverBackupA);
      service.start(serverBackupB);

      cfA = service.createCF(serverMainA, "amqp");
   }


   @AfterEach
   public void afterStop() {
      service.stop(serverBackupA);
      service.stop(serverBackupB);
      service.stop(serverMainA);
      service.stop(serverMainB);
   }


   ConnectionFactory cfA;

   @Test
   public void testReconnectMirrorFailover() throws Throwable {
      try {

         roundTrip(serverMainA, serverMainB);

         service.kill(serverMainB);

         waitForServerToStart(service.createURI(serverBackupB, 61616), null, null, 10_000);

         roundTrip(serverMainA, serverBackupB);

         service.kill(serverMainA);

         waitForServerToStart(service.createURI(serverBackupA, 61616), null, null, 10_000);

         roundTrip(serverBackupA, serverBackupB);
      } catch (Throwable e) {
         // this is just so we can instant feedback in case of an assertion error, instead of having to wait tearDown the server,
         // which is useful when debugging the test
         logger.warn(e.getMessage(), e);
         throw e;
      }

   }

   private void roundTrip(Object serverA, Object serverB) throws Throwable {
      final String QUEUE_NAME = "exampleQueue";
      int NUMBER_OF_MESSAGES = 100;
      {
         ConnectionFactory factory = service.createCF(serverA, "amqp");
         Connection connection = factory.createConnection();
         Session session = connection.createSession();
         Queue queue = session.createQueue(QUEUE_NAME);
         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage msg = session.createTextMessage("message " + i);
            msg.setStringProperty("body", "message " + i);
            producer.send(msg);
         }
         connection.close();
      }

      Thread.sleep(1000);

      {
         ConnectionFactory factory = service.createCF(serverB, "amqp");
         Connection connection = factory.createConnection();
         connection.start();
         Session session = connection.createSession();
         Queue queue = session.createQueue(QUEUE_NAME);
         MessageConsumer consumer = session.createConsumer(queue);
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = (TextMessage)consumer.receive(5000);
            assertNotNull(message);
            assertEquals("message " + i, message.getText());
         }
         assertNull(consumer.receiveNoWait());

         // trying the way back
         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage msg = session.createTextMessage("next-message " + i);
            msg.setStringProperty("body", "next-message " + i);
            producer.send(msg);
         }
         connection.close();
      }

      Thread.sleep(1000);

      {
         ConnectionFactory factory = service.createCF(serverA, "amqp");
         Connection connection = factory.createConnection();
         connection.start();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(QUEUE_NAME);
         MessageConsumer consumer = session.createConsumer(queue);
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = (TextMessage)consumer.receive(5000);
            assertNotNull(message);
            assertEquals("next-message " + i, message.getText());
         }
         session.commit();
         connection.close();
      }

      Thread.sleep(1000);
   }

}
