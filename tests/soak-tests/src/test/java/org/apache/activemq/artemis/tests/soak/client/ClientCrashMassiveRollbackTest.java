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
package org.apache.activemq.artemis.tests.soak.client;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzerPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClientCrashMassiveRollbackTest extends ActiveMQTestBase {
   protected ActiveMQServer server;
   protected ClientSession session;
   protected ClientSessionFactory sf;
   protected ServerLocator locator;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      Configuration config = createDefaultNettyConfig();
      config.setCriticalAnalyzer(true);
      config.setCriticalAnalyzerTimeout(10000);
      config.setCriticalAnalyzerCheckPeriod(5000);
      config.setConnectionTTLOverride(5000);
      config.setCriticalAnalyzerPolicy(CriticalAnalyzerPolicy.SHUTDOWN);
      server = createServer(false, config);
      server.start();
   }

   @Test
   public void clientCrashMassiveRollbackTest() throws Exception {
      final String queueName = "queueName";
      final int messageCount = 1000000;

      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("(tcp://localhost:61616)");
      factory.setConsumerWindowSize(-1);
      factory.setConfirmationWindowSize(10240000);
      Connection connection = factory.createConnection();
      connection.start();

      Thread thread = new Thread(() -> {
         try {
            Session consumerSession = connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue destination = consumerSession.createQueue(queueName);
            MessageConsumer consumer = consumerSession.createConsumer(destination);
            MessageConsumer consumer2 = consumerSession.createConsumer(destination);
            MessageConsumer consumer3 = consumerSession.createConsumer(destination);
            for (;;) {
               consumer.receive();
               consumer2.receive();
               consumer3.receive();
            }
         } catch (Exception e) {
         }
      });

      locator = createNettyNonHALocator();
      locator.setConfirmationWindowSize(10240000);
      sf = createSessionFactory(locator);
      session = addClientSession(sf.createSession(false, true, true));
      SendAcknowledgementHandler sendHandler = message -> {
      };
      session.setSendAcknowledgementHandler(sendHandler);
      session.createQueue(QueueConfiguration.of(queueName).setAddress(queueName).setRoutingType(RoutingType.ANYCAST));
      ClientProducer producer = session.createProducer(queueName);
      QueueControl queueControl = server.getManagementService().getQueueControl(queueName);

      thread.start();

      for (int i = 0; i < messageCount; i++) {
         producer.send(session.createMessage(true));
      }
      producer.close();

      while (queueControl.getDeliveringCount() < messageCount) {
         Thread.sleep(1000);
      }

      thread.interrupt();

      assertEquals(messageCount, queueControl.getMessageCount());
      assertEquals(ActiveMQServer.SERVER_STATE.STARTED, server.getState());

      server.stop();

      Wait.assertEquals(ActiveMQServer.SERVER_STATE.STOPPED, server::getState, 5000, 100);


   }

}
