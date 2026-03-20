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
package org.apache.activemq.artemis.tests.db.dbstorage.integration;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.tests.db.common.Database;
import org.apache.activemq.artemis.tests.db.common.ParameterDBTestBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.FileUtil;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(ParameterizedTestExtension.class)
public class RealServerTest extends ParameterDBTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int NUM_MESSAGES = 100;

   @Parameters(name = "db={0}")
   public static Collection<Object[]> parameters() {
      List<Database> dbList = Database.selectedList();
      dbList.remove(Database.JOURNAL);
      return convertParameters(dbList);
   }

   private String getServerName() {
      return "new-" + database.getName();
   }

   @TestTemplate
   public void testSendRestartConsume() throws Exception {
      String serverName = getServerName();

      cleanupData(serverName);

      File logFile = new File(getServerLocation(serverName), "log/artemis.log");

      Process serverProcess = startServer(serverName, 0, 60_000);

      try {
         ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

         try (Connection connection = factory.createConnection()) {
            try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
               MessageProducer producer = session.createProducer(session.createQueue("TEST"));
               for (int i = 0; i < NUM_MESSAGES; i++) {
                  producer.send(session.createTextMessage("message-" + i));
               }
               session.commit();
            }
         }

         stopServerWithFile(getServerLocation(serverName), serverProcess, 1, TimeUnit.MINUTES);
         serverProcess.waitFor(10, TimeUnit.SECONDS);
         assertFalse(serverProcess.isAlive());

         serverProcess = startServer(serverName, 0, 60_000);

         try (Connection connection = factory.createConnection()) {
            connection.start();
            try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
               MessageConsumer consumer = session.createConsumer(session.createQueue("TEST"));
               for (int i = 0; i < NUM_MESSAGES; i++) {
                  TextMessage message = (TextMessage) consumer.receive(5000);
                  assertNotNull(message, "Expected message " + i);
                  logger.debug("Received: {}", message.getText());
               }
               session.commit();
            }
         }

         stopServerWithFile(getServerLocation(serverName), serverProcess, 1, TimeUnit.MINUTES);
         assertFalse(serverProcess.isAlive());
         serverProcess = null;

         assertFalse(FileUtil.find(logFile, line -> line.contains("SQLException") || line.contains("BatchUpdateException")), "Server log should not contain any SQL exceptions");

      } finally {
         if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.destroyForcibly();
         }
      }
   }
}
