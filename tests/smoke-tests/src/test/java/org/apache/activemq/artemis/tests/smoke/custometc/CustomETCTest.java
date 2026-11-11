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
package org.apache.activemq.artemis.tests.smoke.custometc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CustomETCTest extends SmokeTestBase {

   public static final String SERVER_NAME_0 = "customETC/server";

   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_0);
      deleteDirectory(server0Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).
            setArgs("--etc", new File("./target/customETC/theCustomETC").getAbsolutePath()).
            setArtemisInstance(server0Location);
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
   public void testSimpleSendReceive() throws Exception {

      ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(getName());
         MessageConsumer consumer = session.createConsumer(queue);
         MessageProducer producer = session.createProducer(queue);
         String text = RandomUtil.randomUUIDString();
         connection.start();
         producer.send(session.createTextMessage(text));
         TextMessage txtMessage = (TextMessage) consumer.receive(5000);
         assertNotNull(txtMessage);
         assertEquals(text, txtMessage.getText());
      }

      File logLocation = new File(getServerLocation(SERVER_NAME_0) + "/log/artemis.log");
      assertTrue(logLocation.exists());

      AtomicBoolean started = new AtomicBoolean(false);
      Files.lines(logLocation.toPath()).forEach(line -> {
         if (line.contains("AMQ221007")) { // server started
            started.set(true);
         }
      });

      assertTrue(started.get());
   }

}
