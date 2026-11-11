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
package org.apache.activemq.artemis.tests.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler.LogLevel;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.spi.core.security.ActiveMQBasicSecurityManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MultiThreadedAuditLoggingTest extends ActiveMQTestBase {

   protected ActiveMQServer server;
   private static final int MESSAGE_COUNT = 10;
   private static final String MESSAGE_AUDIT_LOGGER_NAME = AuditLogger.MESSAGE_LOGGER.getLogger().getName();

   private static LogLevel previousLevel = null;

   private static AssertionLoggerHandler loggerHandler;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(true, createDefaultInVMConfig().setSecurityEnabled(true));
      server.setSecurityManager(new ActiveMQBasicSecurityManager());
      server.start();
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("queue1", true, true, true, true, true, true, true, true, true, true, false, false));
      server.getSecurityRepository().addMatch("queue1", roles);
      roles = new HashSet<>();
      roles.add(new Role("queue2", true, true, true, true, true, true, true, true, true, true, false, false));
      server.getSecurityRepository().addMatch("queue2", roles);
      server.getActiveMQServerControl().addUser("queue1", "queue1", "queue1", true);
      server.getActiveMQServerControl().addUser("queue2", "queue2", "queue2", true);
   }

   @BeforeAll
   public static void prepareLogger() {
      previousLevel = AssertionLoggerHandler.setLevel(MESSAGE_AUDIT_LOGGER_NAME, LogLevel.INFO);
      loggerHandler = new AssertionLoggerHandler();
   }

   @AfterAll
   public static void clearLogger() throws Exception {
      try {
         loggerHandler.close();
      } finally {
         AssertionLoggerHandler.setLevel(MESSAGE_AUDIT_LOGGER_NAME, previousLevel);
      }
   }

   class SomeConsumer extends Thread {

      boolean failed = false;
      protected ClientSession session;
      protected ClientSessionFactory sf;
      protected ServerLocator locator;
      String queue;

      SomeConsumer(String queue) throws Exception {
         this.queue = queue;
         locator = createInVMNonHALocator();
         sf = createSessionFactory(locator);
         session = addClientSession(sf.createSession(queue, queue, false, true, true, false, 0));
      }

      @Override
      public void run() {
         ClientConsumer consumer = null;

         try {
            try {
               session.createQueue(QueueConfiguration.of(queue).setRoutingType(RoutingType.ANYCAST));
            } catch (Exception e) {
               // ignore
            }
            consumer = session.createConsumer(queue);
            session.start();
            for (int i = 0; i < MESSAGE_COUNT; i++) {
               consumer.receive();
            }
         } catch (Throwable e) {
            failed = true;
         } finally {
            try {
               if (consumer != null) {
                  consumer.close();
               }
               if (session != null) {
                  session.close();
               }
               if (sf != null) {
                  sf.close();
               }
               if (locator != null) {
                  locator.close();
               }
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      }
   }

   class SomeProducer extends Thread {

      boolean failed = false;
      protected ClientSession session;
      protected ClientSessionFactory sf;
      protected ServerLocator locator;
      String queue;

      SomeProducer(String queue) throws Exception {
         this.queue = queue;
         locator = createInVMNonHALocator();
         sf = createSessionFactory(locator);
         session = addClientSession(sf.createSession(queue, queue, false, true, true, false, 0));
      }

      @Override
      public void run() {
         final String data = "Simple Text " + UUID.randomUUID().toString();
         ClientProducer producer = null;

         try {
            try {
               session.createQueue(QueueConfiguration.of(queue).setRoutingType(RoutingType.ANYCAST));
            } catch (Exception e) {
               // ignore
            }
            producer = session.createProducer(queue);
            ClientMessage message = session.createMessage(false);
            message.getBodyBuffer().writeString(data);
            for (int i = 0; i < MESSAGE_COUNT; i++) {
               producer.send(message);
            }
         } catch (Throwable e) {
            failed = true;
         } finally {
            try {
               if (producer != null) {
                  producer.close();
               }
               if (session != null) {
                  session.close();
               }
               if (sf != null) {
                  sf.close();
               }
               if (locator != null) {
                  locator.close();
               }
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      }
   }

   @Test
   public void testConcurrentLogging() throws Exception {

      int nThreads = 6;
      SomeConsumer[] consumers = new SomeConsumer[nThreads];
      SomeProducer[] producers = new SomeProducer[nThreads];

      for (int j = 0; j < 8; j++) {

         for (int i = 0; i < nThreads / 2; i++) {
            consumers[i] = new SomeConsumer("queue1");
         }

         for (int i = nThreads / 2; i < nThreads; i++) {
            consumers[i] = new SomeConsumer("queue2");
         }

         for (int i = 0; i < nThreads; i++) {
            consumers[i].start();
         }

         for (int i = 0; i < nThreads / 2; i++) {
            producers[i] = new SomeProducer("queue1");
         }

         for (int i = nThreads / 2; i < nThreads; i++) {
            producers[i] = new SomeProducer("queue2");
         }

         for (int i = 0; i < nThreads; i++) {
            producers[i].start();
         }

         for (SomeConsumer consumer : consumers) {
            consumer.join();
            assertFalse(consumer.failed);
         }

         for (SomeProducer producer : producers) {
            producer.join();
            assertFalse(producer.failed);
         }

         assertFalse(loggerHandler.matchText(".*User queue1\\(queue1\\).* is consuming a message from queue2.*"));
         assertFalse(loggerHandler.matchText(".*User queue2\\(queue2\\).* is consuming a message from queue1.*"));
         assertTrue(loggerHandler.matchText(".*User queue2\\(queue2\\).* is consuming a message from queue2.*"));
         assertTrue(loggerHandler.matchText(".*User queue1\\(queue1\\).* is consuming a message from queue1.*"));
      }
   }
}
