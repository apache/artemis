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
package org.apache.activemq.artemis.tests.smoke.resourcetest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSSecurityException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
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

public class MaxQueueResourceTest extends SmokeTestBase {

   public static final String SERVER_NAME_A = "MaxQueueResourceTest";

   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_A);
      deleteDirectory(server0Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setUser("A").setPassword("A").setAllowAnonymous(false).setNoWeb(true).setArtemisInstance(server0Location).setConfiguration("./src/main/resources/servers/MaxQueueResourceTest");
         cliCreateServer.createServer();
      }
   }


   @BeforeEach
   public void before() throws Exception {
      startServer(SERVER_NAME_A, 0, 0);
      ServerUtil.waitForServerToStart(0, "admin", "admin", 30000);
   }

   @Test
   public void testMaxQueue() throws Throwable {
      // We call the three protocols in sequence here for two reasons:
      // 1st: to actually test each protocol
      // 2nd: Having more users creating stuff, makes the test more challenging (just in case)
      //
      // Notice that each protocol will concatenate the protocol name to the user and the clientID,
      // which has been prepared by the server used on this test.
      internalMaxQueue("core");
      internalMaxQueue("openwire");
      internalMaxQueue("amqp");
   }

   private void internalMaxQueue(String protocol) throws Throwable {
      ConnectionFactory cfA = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");


      try (Connection connectionA = cfA.createConnection("john" + protocol, "doe")) {
         connectionA.setClientID("c1" + protocol);
         Session sessionA = connectionA.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = sessionA.createTopic("myTopic");
         MessageConsumer consumer1 = sessionA.createDurableSubscriber(topic, "t1");
         MessageConsumer consumer2 = sessionA.createDurableSubscriber(topic, "t2");
         MessageConsumer consumer3 = sessionA.createDurableSubscriber(topic, "t3");
         Exception exception = null;
         MessageConsumer consumer4 = null;

         try {
            consumer4 = sessionA.createDurableSubscriber(topic, "t4");
         } catch (JMSSecurityException e) {
            exception = e;
         }
         assertNull(consumer4);
         assertNotNull(exception);
         MessageProducer producerA = sessionA.createProducer(topic);
         for (int i = 0; i < 10; i++) {
            producerA.send(sessionA.createTextMessage("toB"));
         }

      }
   }

}
