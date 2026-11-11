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
package org.apache.activemq.artemis.jms.tests.message;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.jms.tests.ActiveMQServerTestCase;
import org.junit.jupiter.api.BeforeEach;

public abstract class MessageBodyTestCase extends ActiveMQServerTestCase {

   protected Connection producerConnection, consumerConnection;
   protected Session queueProducerSession, queueConsumerSession;
   protected MessageProducer queueProducer;
   protected MessageConsumer queueConsumer;

   enum JmsMessageType {
      TEXT, MAP, OBJECT, BYTE, STREAM;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      producerConnection = addConnection(getConnectionFactory().createConnection());
      consumerConnection = addConnection(getConnectionFactory().createConnection());

      queueProducerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      queueConsumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      queueProducer = queueProducerSession.createProducer(queue1);
      queueConsumer = queueConsumerSession.createConsumer(queue1);

      consumerConnection.start();
   }
}
