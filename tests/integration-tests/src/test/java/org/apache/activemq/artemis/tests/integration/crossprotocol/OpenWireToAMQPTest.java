/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.crossprotocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageFormatException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQXAConnectionFactory;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class OpenWireToAMQPTest extends ActiveMQTestBase {

   public static final String OWHOST = "localhost";
   public static final int OWPORT = 61616;
   protected static final String urlString = "tcp://" + OWHOST + ":" + OWPORT + "?wireFormat.cacheEnabled=true";

   private ActiveMQServer server;
   protected ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(urlString);
   protected ActiveMQXAConnectionFactory xaFactory = new ActiveMQXAConnectionFactory(urlString);
   private JmsConnectionFactory qpidfactory;
   protected String queueName = "amqTestQueue1";
   private SimpleString coreQueue;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(true, true);
      Configuration serverConfig = server.getConfiguration();
      serverConfig.getAddressSettings().put("#", new AddressSettings().setAutoCreateQueues(true).setAutoCreateAddresses(true).setDeadLetterAddress(SimpleString.of("ActiveMQ.DLQ")));
      serverConfig.setSecurityEnabled(false);
      server.start();
      coreQueue = SimpleString.of(queueName);
      server.createQueue(QueueConfiguration.of(coreQueue).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      qpidfactory = new JmsConnectionFactory("amqp://localhost:61616");
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      if (server != null) {
         server.stop();
         server = null;
      }
   }

   @SuppressWarnings("unchecked")
   @Test
   @Timeout(60)
   public void testObjectMessage() throws Exception {
      Connection connection = null;
      try {
         connection = factory.createConnection();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);
         MessageProducer producer = session.createProducer(queue);
         ArrayList<String> list = new ArrayList<>();
         list.add("aString");
         ObjectMessage objectMessage = session.createObjectMessage(list);
         producer.send(objectMessage);
         connection.close();
      } catch (Exception e) {
         e.printStackTrace();
         fail("Failed to send message via OpenWire: " + e.getMessage());
      } finally {
         if (connection != null) {
            connection.close();
         }
      }

      try {
         connection = qpidfactory.createConnection();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
         ObjectMessage receive = (ObjectMessage) consumer.receive(5000);
         assertNotNull(receive);
         List<String> list = (ArrayList<String>) receive.getObject();
         assertEquals("aString", list.get(0));
         connection.close();
      } catch (Exception e) {
         e.printStackTrace();
         fail("Failed to receive message via AMQP: " + e.getMessage());
      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }

   @SuppressWarnings("unchecked")
   @Test
   @Timeout(60)
   public void testByteArrayProperties() throws Exception {
      Connection connection = null;
      try {
         connection = factory.createConnection();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);
         MessageProducer producer = session.createProducer(queue);
         ArrayList<String> list = new ArrayList<>();
         list.add("aString");
         ObjectMessage objectMessage = session.createObjectMessage(list);
         producer.send(objectMessage);
         connection.close();
      } catch (Exception e) {
         e.printStackTrace();
         fail("Failed to send message via OpenWire: " + e.getMessage());
      } finally {
         if (connection != null) {
            connection.close();
         }
      }

      try {
         connection = qpidfactory.createConnection();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
         ObjectMessage receive = (ObjectMessage) consumer.receive(5000);
         assertNotNull(receive);

         // As noted in section 3.5.4 of the JMS 2 specification all properties can be converted to String
         Enumeration<String> propertyNames = receive.getPropertyNames();
         while (propertyNames.hasMoreElements()) {
            receive.getStringProperty(propertyNames.nextElement());
         }
         connection.close();
      } catch (MessageFormatException e) {
         e.printStackTrace();
         fail("Failed to receive message via AMQP: " + e.getMessage());
      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }
}
