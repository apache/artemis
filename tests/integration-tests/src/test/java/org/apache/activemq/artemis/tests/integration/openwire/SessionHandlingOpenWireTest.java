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
package org.apache.activemq.artemis.tests.integration.openwire;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.state.ConnectionState;
import org.apache.activemq.state.SessionState;
import org.junit.jupiter.api.Test;

public class SessionHandlingOpenWireTest extends BasicOpenWireTest {

   @Test
   public void testInternalSessionHandling() throws Exception {
      try (Connection conn = factory.createConnection();
         AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         conn.start();
         try (Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            Destination dest = createDestination(session, ActiveMQDestination.QUEUE_TYPE);
            sendMessages(session, dest, 1);
            MessageConsumer consumer = session.createConsumer(dest);
            Message m = consumer.receive(2000);
            assertNotNull(m);
         }
         assertFalse(loggerHandler.findText("Client connection failed, clearing up resources for session"));
         assertFalse(loggerHandler.findText("Cleared up resources for session"));
      }
   }

   @Test
   public void testInternalSessionHandlingNoSessionClose() throws Exception {
      try (Connection conn = factory.createConnection();
         AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         conn.start();
         for (int i = 0; i < 100; i++) {
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination dest = createDestination(session, ActiveMQDestination.QUEUE_TYPE);
            sendMessages(session, dest, 1);
            MessageConsumer consumer = session.createConsumer(dest);
            Message m = consumer.receive(2000);
            consumer.close();
            assertNotNull(m);

            if (i % 2 == 1) {
               // it will close only half of the sessions
               // just to introduce error conditions
               session.close();
            }

         }
         assertFalse(loggerHandler.findText("Client connection failed, clearing up resources for session"));
         assertFalse(loggerHandler.findText("Cleared up resources for session"));
      }
   }


   @Test
   public void testProducerState() throws Exception {
      try (Connection conn = factory.createConnection()) {
         conn.start();
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination dest = createDestination(session, ActiveMQDestination.QUEUE_TYPE);

         for (int i = 0; i < 10; i++) {
            MessageProducer messageProducer = session.createProducer(dest);
            messageProducer.close();
         }

         // verify no trace of producer on the broker
         for (RemotingConnection remotingConnection : server.getRemotingService().getConnections()) {
            if (remotingConnection instanceof OpenWireConnection openWireConnection) {
               ConnectionState connectionState = openWireConnection.getState();
               if (connectionState != null) {
                  for (SessionState sessionState : connectionState.getSessionStates()) {
                     assertTrue(Wait.waitFor(() -> sessionState.getProducerIds().isEmpty()), "no producer states leaked");
                  }
               }
            }
         }
      }
   }
}
