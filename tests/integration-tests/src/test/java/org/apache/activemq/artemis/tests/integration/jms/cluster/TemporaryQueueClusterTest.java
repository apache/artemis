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
package org.apache.activemq.artemis.tests.integration.jms.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.tests.util.JMSClusteredTestBase;
import org.junit.jupiter.api.Test;

public class TemporaryQueueClusterTest extends JMSClusteredTestBase {

   public static final String QUEUE_NAME = "target";



   @Test
   public void testClusteredQueue() throws Exception {
      jmsServer1.createQueue(false, QUEUE_NAME, null, true, "/queue/target");
      jmsServer2.createQueue(false, QUEUE_NAME, null, true, "/queue/target");

      Connection conn1 = cf1.createConnection();
      Connection conn2 = cf2.createConnection();

      conn1.start();

      conn2.start();

      try {
         Session session1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue targetQueue1 = session1.createQueue(QUEUE_NAME);

         Session session2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue targetQueue2 = session2.createQueue(QUEUE_NAME);

         this.waitForBindings(jmsServer1.getActiveMQServer(), QUEUE_NAME, true, 1, 0, 2000);
         this.waitForBindings(jmsServer1.getActiveMQServer(), QUEUE_NAME, true, 1, 0, 2000);
         this.waitForBindings(jmsServer2.getActiveMQServer(), QUEUE_NAME, false, 1, 0, 2000);
         this.waitForBindings(jmsServer1.getActiveMQServer(), QUEUE_NAME, false, 1, 0, 2000);


         MessageProducer prod1 = session1.createProducer(targetQueue1);
         MessageConsumer cons2 = session2.createConsumer(targetQueue2);

         TextMessage msg = session1.createTextMessage("hello");

         prod1.send(msg);

         prod1.send(msg);

         TextMessage msgReceived = (TextMessage) cons2.receive(5000);

         assertNotNull(msgReceived);
         assertEquals(msgReceived.getText(), msg.getText());

      } finally {
         conn1.close();
         conn2.close();
      }
   }

   @Test
   public void testTemporaryQueue() throws Exception {
      jmsServer1.createQueue(false, QUEUE_NAME, null, false, "/queue/target");
      jmsServer2.createQueue(false, QUEUE_NAME, null, false, "/queue/target");

      Connection conn1 = cf1.createConnection();
      Connection conn2 = cf2.createConnection();

      conn1.start();
      conn2.start();

      try {
         Session session1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue targetQueue1 = session1.createQueue(QUEUE_NAME);
         Queue tempQueue = session1.createTemporaryQueue();
         Session session2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue targetQueue2 = session2.createQueue(QUEUE_NAME);

         this.waitForBindings(jmsServer1.getActiveMQServer(), QUEUE_NAME, true, 1, 0, 2000);
         this.waitForBindings(jmsServer1.getActiveMQServer(), QUEUE_NAME, true, 1, 0, 2000);
         this.waitForBindings(jmsServer2.getActiveMQServer(), QUEUE_NAME, false, 1, 0, 2000);
         this.waitForBindings(jmsServer1.getActiveMQServer(), QUEUE_NAME, false, 1, 0, 2000);

         MessageConsumer tempCons1 = session1.createConsumer(tempQueue);

         this.waitForBindings(jmsServer1.getActiveMQServer(), tempQueue.getQueueName(), true, 1, 1, 2000);
         this.waitForBindings(jmsServer2.getActiveMQServer(), tempQueue.getQueueName(), false, 1, 0, 2000);

         MessageProducer prod1 = session1.createProducer(targetQueue1);
         MessageConsumer cons2 = session2.createConsumer(targetQueue2);

         for (int i = 0; i < 10; i++) {
            TextMessage message = session1.createTextMessage("" + i);
            message.setJMSReplyTo(tempQueue);
            prod1.send(message);
         }

         for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
               TextMessage received = (TextMessage) cons2.receive(5000);
               MessageProducer tempProducer = session2.createProducer(received.getJMSReplyTo());
               tempProducer.send(session2.createTextMessage(">>> " + received.getText()));
               tempProducer.close();
            }
         }

         for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
               TextMessage received = (TextMessage) tempCons1.receive(5000);
               assertNotNull(received);
            }
         }
      } finally {
         conn1.close();
         conn2.close();
      }

      jmsServer1.destroyQueue(QUEUE_NAME);
      jmsServer2.destroyQueue(QUEUE_NAME);

   }

}
