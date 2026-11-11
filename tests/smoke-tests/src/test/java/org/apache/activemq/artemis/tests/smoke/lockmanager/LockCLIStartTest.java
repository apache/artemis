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
package org.apache.activemq.artemis.tests.smoke.lockmanager;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Properties;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.cli.Artemis;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class LockCLIStartTest extends SmokeTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String SERVER_NAME = "lockmanager/lockCLIStart";
   private static final File SERVER_LOCATION = getFileServerLocation(SERVER_NAME);

   private static final String LOCK_NAME = "testlock";
   private static final int LOCKED_ACCEPTOR_PORT = 61617;
   private static final String MANAGEMENT_URI = "tcp://localhost:61616";

   @BeforeEach
   public void setupServer() throws Exception {
      deleteDirectory(SERVER_LOCATION);

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setAllowAnonymous(true)
                     .setNoWeb(false)
                     .setArtemisInstance(SERVER_LOCATION);
      cliCreateServer.createServer();

      File fileLockFolder = new File(SERVER_LOCATION, "file-locks");
      fileLockFolder.mkdirs();

      Properties properties = new Properties();

      properties.put("lockCoordinatorConfigurations." + LOCK_NAME + ".name", LOCK_NAME);
      properties.put("lockCoordinatorConfigurations." + LOCK_NAME + ".lockId", LOCK_NAME);
      properties.put("lockCoordinatorConfigurations." + LOCK_NAME + ".className", "org.apache.activemq.artemis.lockmanager.file.FileBasedLockManager");
      properties.put("lockCoordinatorConfigurations." + LOCK_NAME + ".checkPeriod", "100");
      properties.put("lockCoordinatorConfigurations." + LOCK_NAME + ".autoStart", "false");
      properties.put("lockCoordinatorConfigurations." + LOCK_NAME + ".properties.locks-folder", fileLockFolder.getAbsolutePath());

      properties.put("acceptorConfigurations.locked.factoryClassName", "org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory");
      properties.put("acceptorConfigurations.locked.name", "locked");
      properties.put("acceptorConfigurations.locked.lockCoordinator", LOCK_NAME);
      properties.put("acceptorConfigurations.locked.params.port", String.valueOf(LOCKED_ACCEPTOR_PORT));
      properties.put("acceptorConfigurations.locked.params.host", "localhost");
      properties.put("acceptorConfigurations.locked.params.protocols", "CORE,AMQP");
      properties.put("acceptorConfigurations.locked.params.scheme", "tcp");

      File propertiesFile = new File(SERVER_LOCATION, "broker.properties");
      saveProperties(properties, propertiesFile);
   }

   @Test
   public void testStartLockCoordinatorViaCLI() throws Exception {
      File propertiesFile = new File(getServerLocation(SERVER_NAME), "broker.properties");
      Process process = startServer(SERVER_NAME, 0, 30_000, propertiesFile);
      runAfter(() -> process.destroyForcibly());

      try (SimpleManagement simpleManagement = new SimpleManagement(MANAGEMENT_URI, null, null).open()) {

         // verify the lock coordinator exists and is stopped (auto-start=false)
         Wait.assertTrue(() -> {
            try {
               JsonArray locks = simpleManagement.listLockCoordinators();
               return locks.size() > 0;
            } catch (Exception e) {
               return false;
            }
         }, 10_000);

         JsonObject lockCoordinator = findLockCoordinator(simpleManagement, LOCK_NAME);
         assertNotNull(lockCoordinator, "Lock coordinator '" + LOCK_NAME + "' should exist");
         assertEquals("Stopped", lockCoordinator.getString("status"), "Lock coordinator should be stopped (auto-start=false)");

         // verify the locked acceptor is NOT accepting connections
         assertAcceptorNotListening(LOCKED_ACCEPTOR_PORT);

         // start the lock coordinator using the CLI command
         logger.debug("Starting lock coordinator '{}' via CLI", LOCK_NAME);

         File serverLocation = getFileServerLocation(SERVER_NAME);
         File etcServerLocation = new File(serverLocation, "etc");
         Artemis.internalExecute(false, (File) null, serverLocation, etcServerLocation, new String[]{"lock", "start", "--url", MANAGEMENT_URI, LOCK_NAME}, new ActionContext());

         // wait for the lock coordinator to acquire the lock
         Wait.assertTrue(() -> {
            try {
               JsonObject lock = findLockCoordinator(simpleManagement, LOCK_NAME);
               return lock != null && "Locked".equals(lock.getString("status"));
            } catch (Exception e) {
               return false;
            }
         }, 10_000, 500);

         // verify we can now connect, send, and receive through the locked acceptor
         ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:" + LOCKED_ACCEPTOR_PORT);

         try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(session.createQueue("testQueue"));
            producer.send(session.createTextMessage("hello"));
         }

         try (Connection connection = factory.createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumer = session.createConsumer(session.createQueue("testQueue"));
            TextMessage message = (TextMessage) consumer.receive(5_000);
            assertNotNull(message, "Should have received a message through the lock-coordinated acceptor");
            assertEquals("hello", message.getText());
         }

         Artemis.internalExecute(false, (File) null, serverLocation, etcServerLocation, new String[]{"lock", "stop", "--url", MANAGEMENT_URI, LOCK_NAME}, new ActionContext());

         // wait for the lock coordinator to release the lock
         Wait.assertTrue(() -> {
            try {
               JsonObject lock = findLockCoordinator(simpleManagement, LOCK_NAME);
               return lock != null && "Stopped".equals(lock.getString("status"));
            } catch (Exception e) {
               return false;
            }
         });

         // verify the locked acceptor is NOT accepting connections
         assertAcceptorNotListening(LOCKED_ACCEPTOR_PORT);
      }
   }

   private static JsonObject findLockCoordinator(SimpleManagement simpleManagement, String name) throws Exception {
      JsonArray locks = simpleManagement.listLockCoordinators();
      for (int i = 0; i < locks.size(); i++) {
         JsonObject lock = locks.getJsonObject(i);
         if (name.equals(lock.getString("name"))) {
            return lock;
         }
      }
      return null;
   }

   private static void assertAcceptorNotListening(int port) {
      try {
         ActiveMQConnectionFactory factory = (ActiveMQConnectionFactory) CFUtil.createConnectionFactory("CORE", "tcp://localhost:" + port);
         factory.setRetryInterval(100);
         factory.setReconnectAttempts(1);
         Connection connection = factory.createConnection();
         connection.close();
         fail("Acceptor on port " + port + " should not be accepting connections yet");
      } catch (Exception e) {
         logger.info("Acceptor on port {} correctly not listening: {}", port, e.getMessage());
      }
   }
}
