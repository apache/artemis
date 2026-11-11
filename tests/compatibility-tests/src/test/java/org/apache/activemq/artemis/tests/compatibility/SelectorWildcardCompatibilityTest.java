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

package org.apache.activemq.artemis.tests.compatibility;

import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_44_0;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.selector.filter.ComparisonExpressionTestAccessor;
import org.apache.activemq.artemis.tests.compatibility.base.ClasspathBase;
import org.apache.activemq.artemis.utils.FileUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SelectorWildcardCompatibilityTest extends ClasspathBase {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testWildcardSelector_2_44_0_to_Snapshot() throws Throwable {
      ClassLoader senderLoader = getClasspath(ARTEMIS_2_44_0);
      FileUtil.deleteDirectory(serverFolder);
      serverFolder.mkdirs();

      try {
         setVariable(senderLoader, "persistent", true);
         startServer(serverFolder, senderLoader, "wildcardTest", null, true, "servers/artemisServer.groovy", ARTEMIS_2_44_0, ARTEMIS_2_44_0, ARTEMIS_2_44_0);
         evaluate(senderLoader, "selectorWildcard/sendMessages.groovy");
         stopServer(senderLoader);
      } catch (Throwable t) {
         try {
            stopServer(senderLoader);
         } catch (Throwable ignored) {
         }
         throw t;
      }

      // Restart with the current server directly.
      // The journal contains a durable subscription with a selector that has too many wildcards.
      // The server should fail to start and log the appropriate warning.
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         ConfigurationImpl configuration = new ConfigurationImpl();
         configuration.setJournalType(JournalType.NIO);
         configuration.setBrokerInstance(new java.io.File(serverFolder, "wildcardTest"));
         configuration.addAcceptorConfiguration("artemis", "tcp://0.0.0.0:61616");
         configuration.setSecurityEnabled(false);
         configuration.setPersistenceEnabled(true);
         configuration.addAddressesSetting("#", new AddressSettings().setAutoCreateAddresses(true));

         EmbeddedActiveMQ server = new EmbeddedActiveMQ();
         server.setConfiguration(configuration);

         server.start();
         assertTrue(loggerHandler.findText("AMQ224169"));
         assertFalse(server.getActiveMQServer().isStarted());

         try {
            server.stop();
         } catch (Throwable ignored) {
         }


         logger.info("Retrying now with a more permissive setting");

         ComparisonExpressionTestAccessor.setMaxWildcards(10);
         try {
            server.start();

            try (ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("tcp://localhost:61616");
                 Connection connection = cf.createConnection()) {
               connection.start();
               Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

               Queue queue = session.createQueue("selectorWildcardClient.my-wildcard-sub");
               MessageConsumer consumer = session.createConsumer(queue);
               for (int i = 0; i < 10; i++) {
                  TextMessage m = (TextMessage) consumer.receive(5000);
                  assertNotNull(m);
               }
               consumer.close();

               Queue consecutiveQueue = session.createQueue("selectorWildcardClient.my-consecutive-wildcard-sub");
               MessageConsumer consecutiveConsumer = session.createConsumer(consecutiveQueue);
               for (int i = 0; i < 10; i++) {
                  TextMessage m = (TextMessage) consecutiveConsumer.receive(5000);
                  assertNotNull(m);
               }
               consecutiveConsumer.close();
            }
         } finally {
            ComparisonExpressionTestAccessor.setMaxWildcards(5);
            server.stop();
         }
      }
   }
}
