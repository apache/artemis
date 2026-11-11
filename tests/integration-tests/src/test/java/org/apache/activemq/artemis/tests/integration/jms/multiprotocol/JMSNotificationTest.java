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
package org.apache.activemq.artemis.tests.integration.jms.multiprotocol;

import static org.apache.activemq.artemis.api.core.management.ManagementHelper.HDR_CLIENT_ID;
import static org.apache.activemq.artemis.api.core.management.ManagementHelper.HDR_CONSUMER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class JMSNotificationTest extends MultiprotocolJMSClientTestSupport {

   private ClientConsumer notificationConsumer;
   private String clientID;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      clientID = RandomUtil.randomUUIDString();

      ServerLocator locator = addServerLocator(createInVMNonHALocator());
      ClientSessionFactory sf = addSessionFactory(locator.createSessionFactory());
      ClientSession session = addClientSession(sf.createSession(false, true, true));
      session.start();
      SimpleString notificationQueue = RandomUtil.randomUUIDSimpleString();
      session.createQueue(QueueConfiguration.of(notificationQueue).setAddress(ActiveMQDefaultConfiguration.getDefaultManagementNotificationAddress()).setDurable(false));
      notificationConsumer = addClientConsumer(session.createConsumer(notificationQueue));
   }

   private void flush() throws ActiveMQException {
      ClientMessage message;
      do {
         message = notificationConsumer.receiveImmediate();
      }
      while (message != null);
   }

   @Override
   protected void addConfiguration(ActiveMQServer server) throws Exception {
      server.getConfiguration().addAcceptorConfiguration("invm", "vm://0");
   }

   @Test
   @Timeout(30)
   public void testConsumerNotificationAMQP() throws Exception {
      testConsumerNotifications(createConnection(getBrokerQpidJMSConnectionURI(), null, null, clientID, true));
   }

   @Test
   @Timeout(30)
   public void testConsumerNotificationCore() throws Exception {
      testConsumerNotifications(createCoreConnection(getBrokerCoreJMSConnectionString(), null, null, clientID, true));
   }

   @Test
   @Timeout(30)
   public void testConsumerNotificationOpenWire() throws Exception {
      testConsumerNotifications(createOpenWireConnection(getBrokerOpenWireJMSConnectionString(), null, null, clientID, true));
   }

   private void testConsumerNotifications(Connection connection) throws Exception {
      final String subscriptionName = "mySub";
      try {
         flush();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(getTopicName());
         flush();
         MessageConsumer consumer = session.createDurableSubscriber(topic, subscriptionName);
         Message m = receiveNotification(CoreNotificationType.CONSUMER_CREATED, notificationConsumer);
         validateClientIdOnNotification(m, CoreNotificationType.CONSUMER_CREATED);
         String consumerID = validatePropertyOnNotification(m, CoreNotificationType.CONSUMER_CREATED, HDR_CONSUMER_NAME, null, false);
         consumer.close();
         m = receiveNotification(CoreNotificationType.CONSUMER_CLOSED, notificationConsumer);
         validateClientIdOnNotification(m, CoreNotificationType.CONSUMER_CLOSED);
         validatePropertyOnNotification(m, CoreNotificationType.CONSUMER_CLOSED, HDR_CONSUMER_NAME, consumerID, true);
         session.unsubscribe(subscriptionName);
      } finally {
         connection.close();
      }
   }

   ClientMessage receiveNotification(CoreNotificationType notificationType, ClientConsumer consumer) throws Exception {
      for (;;) {
         ClientMessage message = consumer.receive(1000);
         if (message == null) {
            return null;
         }
         String receivedType = message.getStringProperty(ManagementHelper.HDR_NOTIFICATION_TYPE);
         if (String.valueOf(receivedType).equals(notificationType.toString())) {
            return message;
         }
      }
   }

   @Test
   @Timeout(30)
   public void testSessionNotificationAMQP() throws Exception {
      testSessionNotification(createConnection(getBrokerQpidJMSConnectionURI(), null, null, clientID, true));
   }

   @Test
   @Timeout(30)
   public void testSessionNotificationCore() throws Exception {
      testSessionNotification(createCoreConnection(getBrokerCoreJMSConnectionString(), null, null, clientID, true));
   }

   @Test
   @Timeout(30)
   public void testSessionNotificationOpenWire() throws Exception {
      testSessionNotification(createOpenWireConnection(getBrokerOpenWireJMSConnectionString(), null, null, clientID, true));
   }

   private void testSessionNotification(Connection connection) throws Exception {
      try {
         flush();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         validateClientIdOnNotification(notificationConsumer.receive(1000), CoreNotificationType.SESSION_CREATED);
         session.close();
         validateClientIdOnNotification(notificationConsumer.receive(1000), CoreNotificationType.SESSION_CLOSED);
      } finally {
         connection.close();
      }
   }

   private void validateClientIdOnNotification(Message m, CoreNotificationType notificationType) {
      validatePropertyOnNotification(m, notificationType, HDR_CLIENT_ID, clientID, true);
   }

   private String validatePropertyOnNotification(Message m, CoreNotificationType notificationType, SimpleString propertyName, String propertyValue, boolean checkValue) {
      assertNotNull(m);
      assertEquals(notificationType.toString(), m.getStringProperty(ManagementHelper.HDR_NOTIFICATION_TYPE));
      assertTrue(m.getPropertyNames().contains(propertyName));
      if (checkValue) {
         assertEquals(propertyValue, m.getStringProperty(propertyName));
      }
      return m.getStringProperty(propertyName);
   }
}
