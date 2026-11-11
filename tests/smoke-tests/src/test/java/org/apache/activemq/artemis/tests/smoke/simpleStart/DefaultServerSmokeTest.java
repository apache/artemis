/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.tests.smoke.simpleStart;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class DefaultServerSmokeTest extends SmokeTestBase {

   private static final String SERVER_NAME = "default-server";
   private static File SERVER_LOCATION = getFileServerLocation(SERVER_NAME);

   private final String queueName = "queue" + RandomUtil.randomUUIDString();

   @BeforeEach
   public void setupServer() throws Exception {

      deleteDirectory(SERVER_LOCATION);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setRole("amq").setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(false).setArtemisInstance(SERVER_LOCATION);
         cliCreateServer.addArgs("--queues", queueName);
         cliCreateServer.createServer();
      }
   }

   @Test
   public void testValidateDefaultConfigurationNoWarning() throws Exception {

      Process process = startServer(SERVER_NAME, 0, 5000);

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(session.createQueue(queueName));
         producer.send(session.createTextMessage("hello"));
      }

      try (Connection connection = factory.createConnection()) {
         connection.start();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer c = session.createConsumer(session.createQueue(queueName));
         TextMessage message = (TextMessage) c.receive(5000);
         assertNotNull(message);
         assertEquals("hello", message.getText());
      }

      stopServerWithFile(SERVER_LOCATION.getAbsolutePath());

      process.waitFor(1, TimeUnit.SECONDS);

      File log = new File(SERVER_LOCATION, "/log/artemis.log");
      assertFalse(FileUtil.find(log, l -> l.contains("WARN")));

   }

}
