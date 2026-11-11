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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.tests.util.JMSClusteredTestBase;
import org.junit.jupiter.api.Test;

import java.util.Enumeration;
import java.util.Set;

public class TopicClusterTest extends JMSClusteredTestBase {

   // TODO: required to match cluster-connection
   public static final String TOPIC = "jms.t1";




   @Test
   public void testDeleteTopicAfterClusteredSend() throws Exception {
      Connection conn1 = cf1.createConnection();

      conn1.setClientID("someClient1");

      Connection conn2 = cf2.createConnection();

      conn2.setClientID("someClient2");

      conn1.start();

      conn2.start();

      try {

         Topic topic1 = createTopic(TOPIC);

         Topic topic2 = (Topic) context1.lookup("topic/" + TOPIC);

         Session session1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session session2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         // topic1 and 2 should be the same.
         // Using a different instance here just to make sure it is implemented correctly
         MessageConsumer cons2 = session2.createDurableSubscriber(topic2, "sub2");
         Thread.sleep(500);
         MessageProducer prod1 = session1.createProducer(topic1);

         prod1.setDeliveryMode(DeliveryMode.PERSISTENT);

         for (int i = 0; i < 2; i++) {
            prod1.send(session1.createTextMessage("someMessage"));
         }

         TextMessage received = (TextMessage) cons2.receive(5000);

         assertNotNull(received);

         assertEquals("someMessage", received.getText());

         cons2.close();
      } finally {
         conn1.close();
         conn2.close();
      }

      jmsServer1.destroyTopic(TOPIC);
      jmsServer2.destroyTopic(TOPIC);

   }

   @Test
   public void testInternalPropertyNotExposed() throws Exception {
      Connection conn1 = cf1.createConnection();

      conn1.setClientID("someClient1");

      Connection conn2 = cf2.createConnection();

      conn2.setClientID("someClient2");

      conn1.start();

      conn2.start();

      try {

         Topic topic1 = createTopic(TOPIC, true);

         Session session1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session session2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod1 = session1.createProducer(topic1);
         prod1.setDeliveryMode(DeliveryMode.PERSISTENT);

         MessageConsumer cons1 = session1.createDurableSubscriber(topic1, "sub1");
         MessageConsumer cons2 = session2.createDurableSubscriber(topic1, "sub2");

         waitForBindings(server1, TOPIC, true, 1, 1, 2000);
         waitForBindings(server2, TOPIC, true, 1, 1, 2000);
         waitForBindings(server1, TOPIC, false, 1, 1, 2000);
         waitForBindings(server2, TOPIC, false, 1, 1, 2000);
         final int num = 1;
         for (int i = 0; i < num; i++) {
            prod1.send(session1.createTextMessage("someMessage" + i));
         }

         for (int i = 0; i < num; i++) {
            TextMessage m2 = (TextMessage)cons2.receive(5000);
            assertNotNull(m2);
            TextMessage m1 = (TextMessage)cons1.receive(5000);
            assertNotNull(m1);
            checkInternalProperty(m1, m2);
         }

      } finally {
         conn1.close();
         conn2.close();

         jmsServer1.destroyTopic(TOPIC);
         jmsServer2.destroyTopic(TOPIC);
      }
   }

   //check that the internal property is in the core
   //but didn't exposed to jms
   private void checkInternalProperty(Message... msgs) throws Exception {
      boolean checked = false;
      for (Message m : msgs) {
         ActiveMQMessage hqMessage = (ActiveMQMessage) m;
         ClientMessage coreMessage = hqMessage.getCoreMessage();
         Set<SimpleString> coreProps = coreMessage.getPropertyNames();
         boolean exist = false;
         for (SimpleString prop : coreProps) {
            if (prop.startsWith(org.apache.activemq.artemis.api.core.Message.HDR_ROUTE_TO_IDS)) {
               exist = true;
               break;
            }
         }

         if (exist) {
            Enumeration enumProps = m.getPropertyNames();
            while (enumProps.hasMoreElements()) {
               String propName = (String) enumProps.nextElement();
               assertFalse(propName.startsWith(org.apache.activemq.artemis.api.core.Message.HDR_ROUTE_TO_IDS.toString()), "Shouldn't be in jms property: " + propName);
            }
            checked = true;
         }
      }
      assertTrue(checked);
   }

}
