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
package org.apache.activemq.artemis.tests.integration.openwire.amq;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.activemq.command.ActiveMQDestination;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * adapted from: JmsTopicSendReceiveWithTwoConnectionsTest
 */
public class JmsTopicSendReceiveWithTwoConnectionsTest extends JmsSendReceiveTestSupport {

   protected Connection sendConnection;
   protected Connection receiveConnection;
   protected Session receiveSession;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      sendConnection = createSendConnection();
      sendConnection.start();

      receiveConnection = createReceiveConnection();
      receiveConnection.start();

      session = createSendSession(sendConnection);
      receiveSession = createReceiveSession(receiveConnection);

      producer = session.createProducer(null);
      producer.setDeliveryMode(deliveryMode);
      if (topic) {
         consumerDestination = createDestination(session, ActiveMQDestination.TOPIC_TYPE);
         producerDestination = createDestination(session, ActiveMQDestination.TOPIC_TYPE);
      } else {
         consumerDestination = createDestination(session, ActiveMQDestination.QUEUE_TYPE);
         producerDestination = createDestination(session, ActiveMQDestination.QUEUE_TYPE);
      }

      consumer = createConsumer(receiveSession, consumerDestination);
      consumer.setMessageListener(this);
   }

   protected Session createReceiveSession(Connection receiveConnection) throws Exception {
      return receiveConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
   }

   protected Session createSendSession(Connection sendConnection) throws Exception {
      return sendConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
   }

   protected Connection createReceiveConnection() throws Exception {
      return createConnection();
   }

   protected Connection createSendConnection() throws Exception {
      return createConnection();
   }

   protected MessageConsumer createConsumer(Session session, Destination dest) throws JMSException {
      return session.createConsumer(dest);
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      session.close();
      receiveSession.close();
      sendConnection.close();
      receiveConnection.close();

      super.tearDown();
   }
}
