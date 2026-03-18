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
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.cli.commands.tools.PrintData;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DualMirrorNoContainerTest extends SmokeTestBase {

   // Change this to true to generate a print-data in certain cases on this test
   private static final boolean PRINT_DATA = false;

   public static final String SERVER_NAME_A = "brokerConnect/mirrorSecurityA";
   public static final String SERVER_NAME_B = "brokerConnect/mirrorSecurityB";

   Process processB;
   Process processA;

   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_A);
      File server1Location = getFileServerLocation(SERVER_NAME_B);
      deleteDirectory(server1Location);
      deleteDirectory(server0Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setAllowAnonymous(false).setUser("A").setPassword("A").setNoWeb(true).setConfiguration("./src/main/resources/servers/brokerConnect/mirrorSecurityA").setArtemisInstance(server0Location);
         cliCreateServer.createServer();
      }

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setAllowAnonymous(false).setUser("B").setPassword("B").setNoWeb(true).setPortOffset(1).setConfiguration("./src/main/resources/servers/brokerConnect/mirrorSecurityB").setArtemisInstance(server1Location);
         cliCreateServer.createServer();
      }
   }

   @BeforeEach
   public  void beforeClass() throws Exception {
      cleanupData(SERVER_NAME_A);
      cleanupData(SERVER_NAME_B);
      processB = startServer(SERVER_NAME_B, 0, 0);
      processA = startServer(SERVER_NAME_A, 0, 0);

      ServerUtil.waitForServerToStart(1, "B", "B", 30000);
      ServerUtil.waitForServerToStart(0, "A", "A", 30000);
   }

   @Test
   public void testMirrorWithTX() throws Throwable {
      testMirrorOverBokerConnection(true);
   }

   @Test
   public void testMirrorWithoutTX() throws Throwable {
      testMirrorOverBokerConnection(false);
   }

   @Test
   public void testRollback() throws Throwable {
      ConnectionFactory cfA = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61616");
      ConnectionFactory cfB = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61617");


      try (Connection connectionA = cfA.createConnection("A", "A");
           Connection connectionB = cfB.createConnection("B", "B")) {

         // Testing things on the direction from mirroring from A to B...
         Session sessionA = connectionA.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = sessionA.createQueue("someQueue");
         MessageProducer producerA = sessionA.createProducer(queue);
         sendMessages(true, sessionA, producerA, 0, 99);

         connectionA.start();

         MessageConsumer consumerA = sessionA.createConsumer(queue);
         receiveMessages(true, sessionA, consumerA, 0, 49);
         receiveMessages(false, sessionA, consumerA, 50, 70);
         // notice we will leave the messages not committed here, and we will move to the other side.. messages should still be there.


         // Switching consumption to the server B

         Thread.sleep(1000); // The bridge on acks is asynchronous. We need to wait some time to avoid intermittent failures
         // I could replace the wait here with a Wait clause, but I would need to configure JMX or management in order to get the Queue Counts

         Session sessionB = connectionB.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumerB = sessionB.createConsumer(queue);
         MessageProducer producerB = sessionB.createProducer(queue);
         connectionB.start();

         receiveMessages(false, sessionB, consumerB, 50, 99);
         consumerA.close();
         sessionA.rollback(); // this is needed to clear up delivering state

         sessionB.commit();
      }

   }

   public void testMirrorOverBokerConnection(boolean tx) throws Throwable {
      ConnectionFactory cfA = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61616");
      ConnectionFactory cfB = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61617");


      try (Connection connectionA = cfA.createConnection("A", "A");
           Connection connectionB = cfB.createConnection("B", "B")) {

         // Testing things on the direction from mirroring from A to B...
         Session sessionA = connectionA.createSession(tx, tx ? Session.SESSION_TRANSACTED : Session.AUTO_ACKNOWLEDGE);
         Queue queue = sessionA.createQueue("someQueue");
         MessageProducer producerA = sessionA.createProducer(queue);
         sendMessages(tx, sessionA, producerA, 0, 9);

         connectionA.start();

         MessageConsumer consumerA = sessionA.createConsumer(queue);
         receiveMessages(tx, sessionA, consumerA, 0, 4);
         consumerA.close();


         // Switching consumption to the server B

         Thread.sleep(1000); // The bridge on acks is asynchronous. We need to wait some time to avoid intermittent failures
                                   // I could replace the wait here with a Wait clause, but I would need to configure JMX or management in order to get the Queue Counts

         Session sessionB = connectionB.createSession(tx, tx ? Session.SESSION_TRANSACTED : Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumerB = sessionB.createConsumer(queue);
         MessageProducer producerB = sessionB.createProducer(queue);
         connectionB.start();

         receiveMessages(tx, sessionB, consumerB, 5, 9);
         assertNull(consumerB.receiveNoWait());

         sendMessages(tx, sessionB, producerB, 0, 19);
         receiveMessages(tx, sessionB, consumerB, 0, 9);

         consumerB.close();

         // switching over back again to A, some sleep here
         Thread.sleep(1000);

         consumerA = sessionA.createConsumer(queue);

         receiveMessages(tx, sessionA, consumerA, 10, 19);

         assertNull(consumerA.receiveNoWait());
      }
   }

   @Test
   public void testReconnectMirror() throws Throwable {
      testReconnectMirror(false);
   }

   @Test
   public void testReconnectMirrorLargeMessage() throws Throwable {
      testReconnectMirror(true);
   }

   private void testReconnectMirror(boolean isLarge) throws Throwable {
      ConnectionFactory cfA = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61616");
      ConnectionFactory cfB = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61617");

      String largeBuffer = RandomUtil.randomAlphaNumericString(200 * 1024);

      int NUMBER_OF_MESSAGES = isLarge ? 100 : 1_000;
      int FAILURE_INTERVAL = isLarge ? 10 : 100;

      try (Connection connectionA = cfA.createConnection("A", "A")) {

         // Testing things on the direction from mirroring from A to B...
         Session sessionA = connectionA.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = sessionA.createQueue("someQueue");
         MessageProducer producerA = sessionA.createProducer(queue);
         producerA.setDeliveryMode(DeliveryMode.PERSISTENT);

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = sessionA.createTextMessage("message " + i + largeBuffer);
            message.setStringProperty("color", i % 2 == 0 ? "yellow" : "red");
            message.setIntProperty("i", i);
            producerA.send(message);

            if (i % 1000 == 0 && i > 0) {
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

      try (Connection connectionB = cfB.createConnection("B", "B")) {

         // Testing things on the direction from mirroring from A to B...
         Session sessionB = connectionB.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = sessionB.createQueue("someQueue");

         connectionB.start();

         MessageConsumer consumerB = sessionB.createConsumer(queue);

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = (TextMessage)consumerB.receive(5_000);
            assertNotNull(message, "expected message at " + i);
            assertEquals("message " + i + largeBuffer, message.getText());
         }
         assertNull(consumerB.receiveNoWait());
         sessionB.rollback();
      }

      int restarted = 0;

      try (Connection connectionB = cfB.createConnection("B", "B")) {

         // Testing things on the direction from mirroring from A to B...
         Session sessionB = connectionB.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = sessionB.createQueue("someQueue");

         connectionB.start();

         MessageConsumer consumerB = sessionB.createConsumer(queue, "color='yellow'");

         int op = 0;
         for (int i = 0; i < NUMBER_OF_MESSAGES; i += 2) {
            //System.out.println("Received message on i=" + i);
            TextMessage message = (TextMessage)consumerB.receive(5_000);
            assertNotNull(message, "expected message at " + i);
            assertEquals("message " + i + largeBuffer, message.getText());

            if (op++ > 0 && op % FAILURE_INTERVAL == 0) {
               restartA(++restarted);
            }
         }

         assertNull(consumerB.receiveNoWait());
      }

      System.out.println("Restarted serverA " + restarted + " times");

      Thread.sleep(1000);

      try (Connection connectionA = cfA.createConnection("A", "A")) {

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
            assertEquals("message " + i + largeBuffer, message.getText());
         }

         sessionA.commit();

         assertNull(consumerA.receiveNoWait());
      }

      Thread.sleep(5000);

      try (Connection connectionB = cfB.createConnection("B", "B")) {

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
      processB.destroyForcibly();

      Thread.sleep(500);

      /*String localtionServerB = getServerLocation(SERVER_NAME_B);
      File fileB = new File(localtionServerB + "/data");
      PrintData.printData(new File(fileB, "bindings"), new File(fileB, "journal"), new File(fileB, "paging"), false); */

      processB = startServer(SERVER_NAME_B, 0, 0);
      ServerUtil.waitForServerToStart(1, "B", "B", 30000);
   }

   private void restartA(int restartNumber) throws Exception {

      System.out.println("Restarting A");
      processA.destroyForcibly();

      Thread.sleep(1000);

      if (PRINT_DATA) {
         String localtionServerA = getServerLocation(SERVER_NAME_A);
         File fileA = new File(localtionServerA + "/data");
         File fileOutput = new File(localtionServerA + "/log/print-data-" + restartNumber + ".txt");
         FileOutputStream fileOutputStream = new FileOutputStream(fileOutput);
         PrintData.printData(new File(fileA, "bindings"), new File(fileA, "journal"), new File(fileA, "paging"), new PrintStream(fileOutputStream), false, false);
         fileOutputStream.close();
      }

      processA = startServer(SERVER_NAME_A, 0, 0);
      ServerUtil.waitForServerToStart(0, "A", "A", 30000);
      Thread.sleep(1000);
   }

   private void receiveMessages(boolean tx, Session session, MessageConsumer consumer, int start, int end) throws JMSException {
      for (int i = start; i <= end; i++) {
         TextMessage message = (TextMessage) consumer.receive(1000);
         assertNotNull(message);
         assertEquals("message " + i, message.getText());
      }
      if (tx) {
         session.commit();
      }
   }

   private void sendMessages(boolean tx, Session session, MessageProducer producer, int start, int end) throws JMSException {
      for (int i = start; i <= end; i++) {
         producer.send(session.createTextMessage("message " + i));
      }
      if (tx) {
         session.commit();
      }
   }

}
