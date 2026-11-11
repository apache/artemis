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
package org.apache.activemq.artemis.tests.leak;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;

import io.github.checkleak.core.CheckLeak;
import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.core.protocol.core.impl.RemotingConnectionImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.server.impl.ServerStatus;
import org.apache.activemq.artemis.jms.client.ActiveMQSession;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.tests.leak.MemoryAssertions.assertMemory;
import static org.apache.activemq.artemis.tests.leak.MemoryAssertions.basicMemoryAsserts;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SimpleManagementLeakTest extends AbstractLeakTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   ActiveMQServer server;

   @BeforeAll
   public static void beforeClass() throws Exception {
      assumeTrue(CheckLeak.isLoaded());
   }

   @AfterEach
   public void validateServer() throws Exception {
      CheckLeak checkLeak = new CheckLeak();

      // I am doing this check here because the test method might hold a client connection
      // so this check has to be done after the test, and before the server is stopped
      assertMemory(checkLeak, 0, RemotingConnectionImpl.class.getName());

      server.stop();

      server = null;

      clearServers();

      ServerStatus.clear();

      assertMemory(checkLeak, 0, ActiveMQServerImpl.class.getName());
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      server = createServer(true, createDefaultConfig(1, true));
      server.getConfiguration().setJournalPoolFiles(4).setJournalMinFiles(2);
      server.start();
   }

   @Test
   public void testSimpleManagement() throws Exception {
      internalTest(false);
      basicMemoryAsserts();
   }

   @Test
   public void testSimpleManagementInjectedSession() throws Exception {
      internalTest(true);
      basicMemoryAsserts();
   }

   // a method to isolate variables and let them go into GC
   private void internalTest(boolean injectSession) throws Exception {
      CheckLeak checkLeak = new CheckLeak();
      String queueName = "myQueue";
      ConnectionFactory connectionFactory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = connectionFactory.createConnection()) {
         connection.start();

         ActiveMQSession sessionForManagement = (ActiveMQSession) connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         for (int i = 0; i < 10; i++) {
            SimpleManagement simpleManagement = new SimpleManagement("tcp://localhost:61616", null, null);
            if (injectSession) {
               simpleManagement.setSession(sessionForManagement.getCoreSession());
            }
            simpleManagement.listNetworkTopology();
         }
         assertMemory(checkLeak, 5, QueueImpl.class.getName());

      }
   }
}