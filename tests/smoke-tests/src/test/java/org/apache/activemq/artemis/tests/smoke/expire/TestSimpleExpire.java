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
package org.apache.activemq.artemis.tests.smoke.expire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import java.io.File;

import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestSimpleExpire extends SmokeTestBase {

   public static final String SERVER_NAME_0 = "expire";

   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_0);
      deleteDirectory(server0Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(server0Location).setArgs("--disable-persistence");
         cliCreateServer.createServer();
      }
   }

   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_0);
      disableCheckThread();
      startServer(SERVER_NAME_0, 0, 30000);
   }

   @Test
   public void testSendExpire() throws Exception {
      final long NUMBER_EXPIRED = 1000;
      final long NUMBER_NON_EXPIRED = 500;
      ConnectionFactory factory = new JmsConnectionFactory("amqp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

         Queue queue = session.createQueue("q0");
         MessageProducer producer = session.createProducer(queue);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         producer.setTimeToLive(100);
         for (int i = 0; i < NUMBER_EXPIRED; i++) {
            producer.send(session.createTextMessage("expired"));
            if (i % 100 == 0) {
               session.commit();
               System.out.println("Sent " + i + " + messages");
            }

         }

         session.commit();


         Thread.sleep(500);
         producer.setTimeToLive(0);
         for (int i = 0; i < 500; i++) {
            producer.send(session.createTextMessage("ok"));

         }
         session.commit();

         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();

         for (int i = 0; i < NUMBER_NON_EXPIRED; i++) {
            TextMessage txt = (TextMessage) consumer.receive(10000);
            assertNotNull(txt);
            assertEquals("ok", txt.getText());
         }
         assertNull(consumer.receiveNoWait());

         session.commit();
      }
   }

}
