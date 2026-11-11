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
package org.apache.activemq.artemis.tests.soak.interrupt;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JournalFlushInterruptTest extends SoakTestBase {
   public static final String SERVER_NAME_0 = "interruptjf";

   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_0);
      deleteDirectory(server0Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setRole("amq").setUser("artemis").setPassword("artemis").setAllowAnonymous(true).setNoWeb(false).setArtemisInstance(server0Location).
            setConfiguration("./src/main/resources/servers/interruptjf");
         cliCreateServer.setArgs("--java-options", "-Djava.rmi.server.hostname=localhost", "--queues", "JournalFlushInterruptTest", "--name", "interruptjf");
         cliCreateServer.createServer();
      }
   }

   private static final String JMX_SERVER_HOSTNAME = "localhost";
   private static final int JMX_SERVER_PORT_0 = 1099;
   static String liveURI = "service:jmx:rmi:///jndi/rmi://" + JMX_SERVER_HOSTNAME + ":" + JMX_SERVER_PORT_0 + "/jmxrmi";
   static ObjectNameBuilder nameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), "jfinterrupt", true);
   Process serverProcess;

   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_0);
      serverProcess = startServer(SERVER_NAME_0, 0, 30000);
      disableCheckThread();
   }

   private void killProcess(Process process) throws Exception {
      Runtime.getRuntime().exec("kill -9 " + process.pid());
   }

   @Test
   public void testInterruptJF() throws Throwable {
      final ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      final String queueName = "JournalFlushInterruptTest";
      final int messageCount = 100;

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(session.createQueue(queueName));
         for (int i = 0; i < messageCount; i++) {
            producer.send(session.createTextMessage("MessageCount: " + i));
         }
      }

      QueueControl queueControl = getQueueControl(liveURI, nameBuilder, queueName, queueName, RoutingType.ANYCAST, 5000);

      Wait.assertEquals(messageCount, queueControl::getMessageCount, 5000);
      Thread.sleep(100);

      killProcess(serverProcess);
      assertTrue(serverProcess.waitFor(1, TimeUnit.MINUTES));
      serverProcess = startServer(SERVER_NAME_0, 0, 0);

      ServerUtil.waitForServerToStart("tcp://localhost:61616", "artemis", "artemis", 5000);
      queueControl = getQueueControl(liveURI, nameBuilder, queueName, queueName, RoutingType.ANYCAST, 5000);

      Wait.assertEquals(messageCount, queueControl::getMessageCount);

   }

}
