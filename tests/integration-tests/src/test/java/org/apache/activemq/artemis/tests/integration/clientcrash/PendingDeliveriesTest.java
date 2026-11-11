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
package org.apache.activemq.artemis.tests.integration.clientcrash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PendingDeliveriesTest extends ClientTestBase {

   @BeforeEach
   public void createQueue() throws Exception {
      server.addAddressInfo(new AddressInfo(SimpleString.of("queue1"), RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of("queue1").setRoutingType(RoutingType.ANYCAST));
   }

   private static final String AMQP_URI = "amqp://localhost:61616?amqp.saslLayer=false";
   private static final String CORE_URI_NO_RECONNECT = "tcp://localhost:61616?confirmationWindowSize=-1";
   private static final String CORE_URI_WITH_RECONNECT = "tcp://localhost:61616?confirmationWindowSize=" + (1024 * 1024);
   private static final int NUMBER_OF_MESSAGES = 100;

   public static void main(String[] arg) {
      if (arg.length != 3) {
         System.err.println("Usage:: URI destinationName cleanShutdown");
         System.exit(-1);
      }

      String uri = arg[0];
      String destinationName = arg[1];
      boolean cleanShutdown = Boolean.valueOf(arg[2]);

      ConnectionFactory factory;

      factory = createCF(uri);

      try {
         Connection connection = factory.createConnection();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = session.createQueue(destinationName);

         System.err.println("***** " + destination);
         connection.start();
         MessageConsumer consumer = session.createConsumer(destination);
         MessageProducer producer = session.createProducer(destination);

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            producer.send(session.createTextMessage("hello"));
         }

         System.err.println("CleanShutdown::" + cleanShutdown);

         if (cleanShutdown) {
            consumer.close();
            connection.close();
         }

         System.exit(0);

      } catch (Throwable e) {
         e.printStackTrace();
         System.exit(-1);
      }

   }

   private static ConnectionFactory createCF(String uri) {
      ConnectionFactory factory;
      if (uri.startsWith("amqp")) {
         factory = new JmsConnectionFactory(uri);
      } else {
         factory = new ActiveMQConnectionFactory(uri);
      }
      return factory;
   }

   @Test
   public void testWithoutReconnect() throws Exception {

      internalNoReconnect(AMQP_URI, "queue1");
      internalNoReconnect(CORE_URI_NO_RECONNECT, "queue1");
   }

   private void internalNoReconnect(String uriToUse, String destinationName) throws Exception {
      startClient(uriToUse, destinationName, true, false);

      ConnectionFactory cf = createCF(uriToUse);
      Connection connection = cf.createConnection();
      connection.start();
      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = session.createQueue(destinationName);
         MessageConsumer consumer = session.createConsumer(destination);

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            // give more time to receive first message but do not wait so long for last as all message were most likely sent
            assertNotNull(i == NUMBER_OF_MESSAGES - 1 ? consumer.receive(500) : consumer.receive(5000), "consumer.receive(...) returned null for " + i + "th message. Number of expected messages" +
                    " to be received is " + NUMBER_OF_MESSAGES);
         }
      } finally {
         connection.stop();
         connection.close();

      }

      if (cf instanceof ActiveMQConnectionFactory factory) {
         factory.close();
      }

   }

   @Test
   public void testWithtReconnect() throws Exception {
      startClient(CORE_URI_WITH_RECONNECT, "queue1", true, false);
      ConnectionFactory cf = createCF(CORE_URI_WITH_RECONNECT);
      Connection connection = cf.createConnection();
      connection.start();
      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = session.createQueue("queue1");
         MessageConsumer consumer = session.createConsumer(destination);

         int i = 0;
         for (; i < NUMBER_OF_MESSAGES; i++) {
            Message msg = consumer.receive(1000);
            if (msg == null) {
               break;
            }
         }

         assertTrue(i < NUMBER_OF_MESSAGES);
      } finally {
         connection.stop();
         connection.close();

      }
   }

   @Test
   public void testCleanShutdownNoLogger() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         startClient(CORE_URI_NO_RECONNECT, "queue1", false, true);
         Thread.sleep(500);
         assertFalse(loggerHandler.findText("clearing up resources"));
      }
   }

   @Test
   public void testBadShutdownLogger() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         startClient(CORE_URI_NO_RECONNECT, "queue1", false, false);
         Wait.assertTrue(() -> loggerHandler.findText("clearing up resources"), 1000);
      }
   }

   @Test
   public void testCleanShutdown() throws Exception {

   }

   private void startClient(String uriToUse,
                            String destinationName,
                            boolean log,
                            boolean cleanShutdown) throws Exception {
      Process process = SpawnedVMSupport.spawnVM(PendingDeliveriesTest.class.getName(), log, uriToUse, destinationName, Boolean.toString(cleanShutdown));
      assertEquals(0, process.waitFor());
   }

}
