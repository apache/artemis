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
package org.apache.activemq.artemis.tests.integration.client;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AddressPauseTest extends JMSTestBase {


   @Override
   protected boolean usePersistence() {
      return true;
   }

   @Test
   public void testPauseAddress() throws Exception {
      try (Connection connection = cf.createConnection()) {
         connection.setClientID("myClientID");
         connection.start();
         try (Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE)) {
            Topic topic = session.createTopic("jms.topic.MyTopic");
            TopicSubscriber subscriber1 = session.createDurableSubscriber(topic, "my-subscription1");
            AddressControl addressControl = server.getManagementService().getAddressControl("jms.topic.MyTopic");
            MessageProducer producer = session.createProducer(topic);
            final int numMessages = 100;
            for (int i = 0; i < numMessages; i++) {
               TextMessage mess = session.createTextMessage("msg" + i);
               producer.send(mess);
            }
            session.commit();
            for (int i = 0; i < numMessages; i++) {
               TextMessage m = (TextMessage) subscriber1.receive(5000);
               assertNotNull(m);
            }
            session.commit();
            //Pausing the subscriptions
            addressControl.pause();
            assertTrue(addressControl.isPaused());
            //subscriber2 should be paused too
            TopicSubscriber subscriber2 = session.createDurableSubscriber(topic, "my-subscription2");
            for (int i = 0; i < numMessages; i++) {
               TextMessage mess = session.createTextMessage("msg" + i);
               producer.send(mess);
            }
            session.commit();
            TextMessage message = (TextMessage) subscriber1.receiveNoWait();
            assertNull(message);
            message = (TextMessage) subscriber2.receiveNoWait();
            assertNull(message);
            //Resuming the subscriptions
            addressControl.resume();
            for (int i = 0; i < numMessages; i++) {
               TextMessage m = (TextMessage) subscriber1.receive(5000);
               assertNotNull(m);
            }
            session.commit();
            for (int i = 0; i < numMessages; i++) {
               TextMessage m = (TextMessage) subscriber2.receive(5000);
               assertNotNull(m);
            }
            session.commit();
         }
      }
   }


   @Test
   public void testPauseAddressServerRestart() throws Exception {
      final int numMessages = 100;

      try (Connection connection = cf.createConnection()) {
         connection.setClientID("myClientID");
         connection.start();
         try (Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE)) {
            Topic topic = session.createTopic("jms.topic.MyTopic");
            TopicSubscriber subscriber1 = session.createDurableSubscriber(topic, "my-subscription1");
            AddressControl addressControl = server.getManagementService().getAddressControl("jms.topic.MyTopic");
            MessageProducer producer = session.createProducer(topic);
            for (int i = 0; i < numMessages; i++) {
               TextMessage mess = session.createTextMessage("msg" + i);
               producer.send(mess);
            }
            session.commit();
            for (int i = 0; i < numMessages; i++) {
               TextMessage m = (TextMessage) subscriber1.receive(5000);
               assertNotNull(m);
            }
            session.commit();
            //Pausing the subscriptions
            addressControl.pause(true);
         }
      }

      server.stop();

      server.start();

      try (Connection connection = cf.createConnection()) {
         connection.setClientID("myClientID");
         connection.start();
         try (Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE)) {
            Topic topic = session.createTopic("jms.topic.MyTopic");
            TopicSubscriber subscriber1 = session.createDurableSubscriber(topic, "my-subscription1");
            AddressControl addressControl = server.getManagementService().getAddressControl("jms.topic.MyTopic");
            MessageProducer producer = session.createProducer(topic);
            assertTrue(addressControl.isPaused());
            //subscriber2 should be paused too
            TopicSubscriber subscriber2 = session.createDurableSubscriber(topic, "my-subscription2");
            for (int i = 0; i < numMessages; i++) {
               TextMessage mess = session.createTextMessage("msg" + i);
               producer.send(mess);
            }
            session.commit();
            TextMessage message = (TextMessage) subscriber1.receiveNoWait();
            assertNull(message);
            message = (TextMessage) subscriber2.receiveNoWait();
            assertNull(message);
            //Resuming the subscriptions
            addressControl.resume();
            for (int i = 0; i < numMessages; i++) {
               TextMessage m = (TextMessage) subscriber1.receive(5000);
               assertNotNull(m);
            }
            session.commit();
            for (int i = 0; i < numMessages; i++) {
               TextMessage m = (TextMessage) subscriber2.receive(5000);
               assertNotNull(m);
            }
            session.commit();
         }
      }
   }
}
