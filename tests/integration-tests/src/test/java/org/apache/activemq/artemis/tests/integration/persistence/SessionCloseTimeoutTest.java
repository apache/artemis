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
package org.apache.activemq.artemis.tests.integration.persistence;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQMessageConsumer;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzerPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A simple test-case used for documentation purposes.
 */
public class SessionCloseTimeoutTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   ActiveMQServer server;

   @BeforeEach
   protected void createServer() throws Exception {

      ConfigurationImpl configuration = createBasicConfig(0).setJMXManagementEnabled(false).addAcceptorConfiguration(new TransportConfiguration(INVM_ACCEPTOR_FACTORY, generateInVMParams(0), "invm"));
      configuration.setCriticalAnalyzer(true).setCriticalAnalyzerPolicy(CriticalAnalyzerPolicy.SHUTDOWN).setCriticalAnalyzerTimeout(1000);

      HashMap<String, Object> extraConfig = new HashMap<>();
      HashMap<String, Object> regularConfig = new HashMap<>();

      configuration.addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, regularConfig, "netty", extraConfig));
      server = createServer(true, configuration);
      server.start();
   }


   /**
    * This is simulating a context that will never finish, the timeout should take care of it.
    */
   @Test
   public void testSessionCloseTimeout() throws Exception {

      int numberOfMessages = 100;

      String queueName = getName();

      final String tag = RandomUtil.randomAlphaNumericString(20);
      AtomicInteger frozenSessions = new AtomicInteger(0);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {

         org.apache.activemq.artemis.core.server.Queue serverQueue = server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
         ConnectionFactory connectionFactory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
         try (Connection connection = connectionFactory.createConnection()) {
            connection.setClientID(tag);
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue queue = session.createQueue(queueName);

            ActiveMQMessageConsumer consumer = (ActiveMQMessageConsumer) session.createConsumer(queue);

            MessageProducer producer = session.createProducer(queue);
            for (int i = 0; i < numberOfMessages; i++) {
               TextMessage message = session.createTextMessage("hello " + i);
               message.setIntProperty("i", i);
               producer.send(message);
            }
            session.commit();

            Wait.assertEquals(numberOfMessages, serverQueue::getMessageCount, 5000);

            Wait.waitFor(() -> serverQueue.getDeliveringCount() > 1);
            assertNotNull(consumer.receive(5000));

            server.getRemotingService().getConnections().stream().filter(r -> String.valueOf(r.getClientID()).equals(tag)).forEach(r -> {
               server.getSessions().stream().filter(s -> s.getRemotingConnection() == r).forEach(s -> {
                  // this will make the context to never finish
                  s.getSessionContext().storeLineUp();
                  frozenSessions.incrementAndGet();
               });
               r.fail(new ActiveMQException("fail"));
               r.getTransportConnection().disconnect();
            });
         }

         assertTrue(frozenSessions.get() > 0);

         Wait.assertFalse(server::isStarted);

         assertTrue(loggerHandler.findText("AMQ224107"), "Critical Analyzer supposed to happen");

         createServer();

         connectionFactory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

         try (Connection connection = connectionFactory.createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            MessageConsumer consumer = session.createConsumer(session.createQueue(queueName));
            for (int i = 0; i < numberOfMessages; i++) {
               TextMessage message = (TextMessage) consumer.receive(5000);
               assertNotNull(message);
               assertEquals(i, message.getIntProperty("i"));
            }
         }
      }
   }
}