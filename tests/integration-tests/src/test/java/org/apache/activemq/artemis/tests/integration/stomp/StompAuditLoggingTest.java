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
package org.apache.activemq.artemis.tests.integration.stomp;

import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler.LogLevel;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.integration.stomp.util.ClientStompFrame;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StompAuditLoggingTest extends StompTestBase {

   private static final String BASE_AUDIT_LOGGER_NAME = AuditLogger.BASE_LOGGER.getLogger().getName();
   private static LogLevel previousLevel = null;
   private static AssertionLoggerHandler loggerHandler;
   protected StompClientConnection conn;
   private final String user = "nopriv";
   private final String pass = user;
   private final String role = "nopriv";

   public StompAuditLoggingTest() {
      super("tcp+v10.stomp");
   }

   @Override
   public boolean isSecurityEnabled() {
      return true;
   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      server = super.createServer();

      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();

      securityManager.getConfiguration().addUser(user, pass);
      securityManager.getConfiguration().addRole(user, role);
      server.getConfiguration().getSecurityRoles().put("#", new HashSet<>(Set.of(new Role(role, false, false, false, false, false, false, false, false, false, false, false, false))));

      return server;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      conn = StompClientConnectionFactory.createClientConnection(uri);
   }

   @BeforeAll
   public static void prepareLogger() {
      previousLevel = AssertionLoggerHandler.setLevel(BASE_AUDIT_LOGGER_NAME, LogLevel.INFO);
      loggerHandler = new AssertionLoggerHandler();
   }

   @AfterAll
   public static void clearLogger() throws Exception {
      try {
         loggerHandler.close();
      } finally {
         AssertionLoggerHandler.setLevel(BASE_AUDIT_LOGGER_NAME, previousLevel);
      }
   }

   @Test
   public void testAuthzFailureAuditLogging() throws Exception {
      conn.connect(user, pass);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND);
      frame.addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName());
      frame.setBody(RandomUtil.randomUUIDString());

      try {
         conn.sendFrame(frame);
      } catch (Exception e) {
         // ignore
      }

      conn.disconnect();

      Wait.assertTrue(() -> loggerHandler.matchText(".*User nopriv\\(nopriv\\).* gets security check failure.*"), 2000, 100);
   }
}
