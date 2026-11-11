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
package org.apache.activemq.artemis.tests.integration.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.plugin.impl.BrokerMessageAuthorizationPlugin;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.integration.security.SecurityTest;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageAuthorizationTest extends ActiveMQTestBase {

   static {
      String path = System.getProperty("java.security.auth.login.config");
      if (path == null) {
         URL resource = SecurityTest.class.getClassLoader().getResource("login.config");
         if (resource != null) {
            path = resource.getFile();
            System.setProperty("java.security.auth.login.config", path);
         }
      }
   }

   private ActiveMQServer server;
   private SimpleString QUEUE = SimpleString.of("TestQueue");
   private SimpleString TOPIC = SimpleString.of("TestTopic");

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLogin");
      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultNettyConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, true));
      server.getConfiguration().setPopulateValidatedUser(true);
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("programmers", true, true, true, true, true, true, true, true, true, true, false, false));
      roles.add(new Role("a", false, true, true, true, true, false, false, false, true, true, false, false));
      roles.add(new Role("b", false, true, true, true, true, false, false, false, true, true, false, false));
      server.getConfiguration().putSecurityRoles("#", roles);

      BrokerMessageAuthorizationPlugin plugin = new BrokerMessageAuthorizationPlugin();
      plugin.init(Collections.emptyMap());
      server.registerBrokerPlugin(plugin);
      server.start();
      server.createQueue(QueueConfiguration.of(QUEUE).setRoutingType(RoutingType.ANYCAST).setDurable(true));
      server.createQueue(QueueConfiguration.of(TOPIC).setRoutingType(RoutingType.MULTICAST).setDurable(true));
   }

   @Test
   public void testMessageAuthorizationQueue() throws Exception {
      JmsConnectionFactory factory = new JmsConnectionFactory("amqp://127.0.0.1:61616");
      Connection connection = factory.createConnection("first", "secret");
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      javax.jms.Queue queue = session.createQueue(QUEUE.toString());
      MessageProducer producer = session.createProducer(queue);

      TextMessage aMessage = session.createTextMessage();
      aMessage.setStringProperty("requiredRole", "a");
      TextMessage bMessage = session.createTextMessage();
      bMessage.setStringProperty("requiredRole", "b");
      Connection aConnection = factory.createConnection("a", "a");
      Session aSession = aConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      aConnection.start();
      Connection bConnection = factory.createConnection("b", "b");
      Session bSession = bConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      bConnection.start();
      MessageConsumer aConsumer = aSession.createConsumer(queue);
      MessageConsumer bConsumer = bSession.createConsumer(queue);

      producer.send(aMessage);
      producer.send(bMessage);
      connection.close();

      Message aMsg = aConsumer.receiveNoWait();
      assertNotNull(aMsg);
      assertEquals("a", aMsg.getStringProperty("requiredRole"));

      Message bMsg = bConsumer.receiveNoWait();
      assertNotNull(bMsg);
      assertEquals("b", bMsg.getStringProperty("requiredRole"));

      aConnection.close();
      bConnection.close();
   }

   @Test
   public void testMessageAuthorizationQueueNotAuthorized() throws Exception {
      JmsConnectionFactory factory = new JmsConnectionFactory("amqp://127.0.0.1:61616");
      Connection connection = factory.createConnection("first", "secret");
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      javax.jms.Queue queue = session.createQueue("TestQueueNotAuth");
      MessageProducer producer = session.createProducer(queue);

      TextMessage bMessage = session.createTextMessage();
      bMessage.setStringProperty("requiredRole", "b");
      Connection aConnection = factory.createConnection("a", "a");
      Session aSession = aConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      aConnection.start();
      MessageConsumer aConsumer = aSession.createConsumer(queue);

      producer.send(bMessage);
      connection.close();

      assertNull(aConsumer.receiveNoWait());

      aConnection.close();
   }

   @Test
   public void testMessageAuthorizationTopic() throws Exception {
      JmsConnectionFactory factory = new JmsConnectionFactory("amqp://127.0.0.1:61616");
      Connection connection = factory.createConnection("first", "secret");
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      javax.jms.Topic topic = session.createTopic(TOPIC.toString());
      MessageProducer producer = session.createProducer(topic);
      TextMessage aMessage = session.createTextMessage();
      aMessage.setStringProperty("requiredRole", "a");
      TextMessage bMessage = session.createTextMessage();
      bMessage.setStringProperty("requiredRole", "b");

      Connection aConnection = factory.createConnection("a", "a");
      Session aSession = aConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      aConnection.start();
      Connection bConnection = factory.createConnection("b", "b");
      Session bSession = bConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      bConnection.start();
      MessageConsumer aConsumer = aSession.createConsumer(topic);
      MessageConsumer bConsumer = bSession.createConsumer(topic);

      producer.send(aMessage);
      producer.send(bMessage);
      connection.close();

      Message bMsg = bConsumer.receiveNoWait();
      assertNotNull(bMsg);
      assertEquals("b", bMsg.getStringProperty("requiredRole"));
      assertNull(bConsumer.receiveNoWait());

      Message aMsg = aConsumer.receiveNoWait();
      assertNotNull(aMsg);
      assertEquals("a", aMsg.getStringProperty("requiredRole"));
      assertNull(aConsumer.receiveNoWait());

      aConnection.close();
      bConnection.close();
   }


   @Test
   public void testMessageAuthorizationTopicNotAuthorized() throws Exception {
      JmsConnectionFactory factory = new JmsConnectionFactory("amqp://127.0.0.1:61616");
      Connection connection = factory.createConnection("first", "secret");
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      javax.jms.Topic topic = session.createTopic("TestTopicNotAuth");
      MessageProducer producer = session.createProducer(topic);
      TextMessage bMessage = session.createTextMessage();
      bMessage.setStringProperty("requiredRole", "b");

      Connection aConnection = factory.createConnection("a", "a");
      Session aSession = aConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      aConnection.start();
      MessageConsumer aConsumer = aSession.createConsumer(topic);

      producer.send(bMessage);
      connection.close();

      assertNull(aConsumer.receiveNoWait());

      aConnection.close();
   }

}
