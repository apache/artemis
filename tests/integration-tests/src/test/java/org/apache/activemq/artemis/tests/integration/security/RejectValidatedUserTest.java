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

import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

public class RejectValidatedUserTest extends ActiveMQTestBase {

   private static final String ADDRESS = "TestQueue";
   private ActiveMQServer server;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig(), false));
      server.getConfiguration().setRejectEmptyValidatedUser(true);
      server.start();
   }

   @Test
   public void testRejectException() throws Exception {
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sessionFactory = locator.createSessionFactory();
      ClientSession session = sessionFactory.createSession();
      ClientProducer producer = session.createProducer(ADDRESS);
      try {
         producer.send(session.createMessage(true));
         fail("Should throw exception");
      } catch (ActiveMQIllegalStateException e) {
         //pass
      }
      locator.close();
   }

   @Test
   public void testAcceptException() throws Exception {
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sessionFactory = locator.createSessionFactory();
      ClientSession session = sessionFactory.createSession();
      ClientProducer producer = session.createProducer(ADDRESS);
      ClientMessage message = session.createMessage(true);
      message.setValidatedUserID("testuser");
      producer.send(message);
      locator.close();
   }

   @Test
   public void testAcceptJMSException() throws Exception {
      ActiveMQConnectionFactory connectionFactory = ActiveMQJMSClient.createConnectionFactory("vm://0", "0");
      Connection connection = connectionFactory.createConnection();
      Session session = connection.createSession();
      Queue queue = session.createQueue(ADDRESS);
      MessageProducer producer = session.createProducer(queue);
      Message message = session.createMessage();
      message.setStringProperty("JMSXUserID", "testuser");
      producer.send(message);
   }
}
