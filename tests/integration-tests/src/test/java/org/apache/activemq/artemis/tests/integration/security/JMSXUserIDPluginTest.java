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
package org.apache.activemq.artemis.tests.integration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.Map;

public class JMSXUserIDPluginTest extends ActiveMQTestBase {

   private ActiveMQServer server;
   private SimpleString ADDRESS = SimpleString.of("TestQueue");

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultNettyConfig(), true));

      JMSXUserIDPlugin plugin = new JMSXUserIDPlugin();
      plugin.setPopulateValidatedUser("testuser");

      server.registerBrokerPlugin(plugin);
      server.start();
      server.createQueue(QueueConfiguration.of(ADDRESS).setRoutingType(RoutingType.ANYCAST));
   }

   @Test
   public void testAddValidatedUserCore() throws Exception {
      ServerLocator locator = createNettyNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);
      ClientProducer producer = session.createProducer(ADDRESS.toString());
      producer.send(session.createMessage(true));
      ClientConsumer consumer = session.createConsumer(ADDRESS.toString());
      session.start();
      ClientMessage clientMessage = consumer.receiveImmediate();
      assertNotNull(clientMessage);
      assertEquals("testuser", clientMessage.getValidatedUserID());
   }

   @Test
   public void testAddValidatedUserAMQP() throws Exception {
      JmsConnectionFactory factory = new JmsConnectionFactory("amqp://127.0.0.1:61616");
      Connection connection = factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      javax.jms.Queue queue = session.createQueue(ADDRESS.toString());
      MessageProducer producer = session.createProducer(queue);
      producer.send(session.createMessage());
      connection.close();

      server.stop();
      server.start();

      connection = factory.createConnection();
      session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      connection.start();
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(5000);
      assertNotNull(message);
      assertEquals("testuser", message.getStringProperty("_AMQ_VALIDATED_USER"));
      connection.close();
   }


   private static class JMSXUserIDPlugin implements ActiveMQServerPlugin {

      private static String POPULATE_VALIDATED_USER = "POPULATE_VALIDATED_USER";

      private String populateValidatedUser;

      /**
       * used to pass configured properties to Plugin
       */
      @Override
      public void init(Map<String, String> properties) {
         populateValidatedUser = properties.getOrDefault(POPULATE_VALIDATED_USER, null);
      }

      @Override
      public void beforeSend(ServerSession session, Transaction tx, org.apache.activemq.artemis.api.core.Message message, boolean direct, boolean noAutoCreateQueue) throws ActiveMQException {
         if (populateValidatedUser != null && !message.containsProperty(org.apache.activemq.artemis.api.core.Message.HDR_VALIDATED_USER)) {
            message.messageChanged();
            message.putStringProperty(org.apache.activemq.artemis.api.core.Message.HDR_VALIDATED_USER, populateValidatedUser);
         }
      }

      public String getPopulateValidatedUser() {
         return populateValidatedUser;
      }

      public void setPopulateValidatedUser(String populateValidatedUser) {
         this.populateValidatedUser = populateValidatedUser;
      }
   }

}
