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
package org.apache.activemq.artemis.tests.integration.openwire;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.command.ActiveMQQueue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is useful to debug connection ordering. There's only one connection being made from these tests
 */
public class VerySimpleOenwireTest extends OpenWireTestBase {

   /**
    * This is the example shipped with the distribution
    */
   @Test
   public void testOpenWireExample() throws Exception {
      Connection exConn = null;

      this.server.createQueue(QueueConfiguration.of("exampleQueue").setRoutingType(RoutingType.ANYCAST));

      try {
         ActiveMQConnectionFactory exFact = new ActiveMQConnectionFactory();

         Queue queue = new ActiveMQQueue("exampleQueue");

         exConn = exFact.createConnection();

         exConn.start();

         Session session = exConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer producer = session.createProducer(queue);

         TextMessage message = session.createTextMessage("This is a text message");

         producer.send(message);

         MessageConsumer messageConsumer = session.createConsumer(queue);

         TextMessage messageReceived = (TextMessage) messageConsumer.receive(5000);

         assertEquals("This is a text message", messageReceived.getText());
      } finally {
         if (exConn != null) {
            exConn.close();
         }
      }

   }

   @Test
   public void testMixedOpenWireExample() throws Exception {
      Connection openConn = null;

      this.server.createQueue(QueueConfiguration.of("exampleQueue").setRoutingType(RoutingType.ANYCAST));

      ActiveMQConnectionFactory openCF = new ActiveMQConnectionFactory();

      Queue queue = new ActiveMQQueue("exampleQueue");

      openConn = openCF.createConnection();

      openConn.start();

      Session openSession = openConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer producer = openSession.createProducer(queue);

      TextMessage message = openSession.createTextMessage("This is a text message");

      producer.send(message);

      org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory artemisCF = new org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory();

      Connection artemisConn = artemisCF.createConnection();
      Session artemisSession = artemisConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      artemisConn.start();
      MessageConsumer messageConsumer = artemisSession.createConsumer(artemisSession.createQueue("exampleQueue"));

      TextMessage messageReceived = (TextMessage) messageConsumer.receive(5000);

      assertEquals("This is a text message", messageReceived.getText());

      openConn.close();
      artemisConn.close();

   }

}
