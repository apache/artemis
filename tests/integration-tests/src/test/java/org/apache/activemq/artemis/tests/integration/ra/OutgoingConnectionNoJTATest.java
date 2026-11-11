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

package org.apache.activemq.artemis.tests.integration.ra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.ra.ActiveMQRAConnectionFactory;
import org.apache.activemq.artemis.ra.ActiveMQRAConnectionFactoryImpl;
import org.apache.activemq.artemis.ra.ActiveMQRAConnectionManager;
import org.apache.activemq.artemis.ra.ActiveMQRAManagedConnectionFactory;
import org.apache.activemq.artemis.ra.ActiveMQResourceAdapter;
import org.apache.activemq.artemis.service.extensions.ServiceUtils;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.HashSet;
import java.util.Set;

public class OutgoingConnectionNoJTATest extends ActiveMQRATestBase {

   protected ActiveMQResourceAdapter resourceAdapter;
   protected ActiveMQRAConnectionFactory qraConnectionFactory;
   protected ActiveMQRAManagedConnectionFactory mcf;
   ActiveMQRAConnectionManager qraConnectionManager = new ActiveMQRAConnectionManager();

   @Override
   public boolean useSecurity() {
      return true;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      useDummyTransactionManager();
      super.setUp();
      ((ActiveMQJAASSecurityManager) server.getSecurityManager()).getConfiguration().addUser("testuser", "testpassword");
      ((ActiveMQJAASSecurityManager) server.getSecurityManager()).getConfiguration().addUser("guest", "guest");
      ((ActiveMQJAASSecurityManager) server.getSecurityManager()).getConfiguration().setDefaultUser("guest");
      ((ActiveMQJAASSecurityManager) server.getSecurityManager()).getConfiguration().addRole("testuser", "arole");
      ((ActiveMQJAASSecurityManager) server.getSecurityManager()).getConfiguration().addRole("guest", "arole");
      Role role = new Role("arole", true, true, true, true, true, true, true, true, true, true, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      server.getSecurityRepository().addMatch(MDBQUEUEPREFIXED, roles);

      resourceAdapter = new ActiveMQResourceAdapter();
      resourceAdapter.setEntries("[\"java://jmsXA\"]");
      resourceAdapter.setConnectorClassName(InVMConnectorFactory.class.getName());
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      mcf = new ActiveMQRAManagedConnectionFactory();
      mcf.setAllowLocalTransactions(true);
      mcf.setResourceAdapter(resourceAdapter);
      qraConnectionFactory = new ActiveMQRAConnectionFactoryImpl(mcf, qraConnectionManager);
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      ((DummyTransactionManager) ServiceUtils.getTransactionManager()).tx = null;
      if (resourceAdapter != null) {
         resourceAdapter.stop();
      }

      qraConnectionManager.stop();
      super.tearDown();
   }

   @Test
   public void testSimpleMessageSendAndReceiveSessionTransacted() throws Exception {
      setupDLQ(10);
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      ActiveMQRAManagedConnectionFactory mcf = new ActiveMQRAManagedConnectionFactory();
      mcf.setAllowLocalTransactions(true);
      mcf.setResourceAdapter(resourceAdapter);
      ActiveMQRAConnectionFactory qraConnectionFactory = new ActiveMQRAConnectionFactoryImpl(mcf, qraConnectionManager);
      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();
      Session s = queueConnection.createSession(true, Session.SESSION_TRANSACTED);
      Queue q = ActiveMQJMSClient.createQueue(MDBQUEUE);
      MessageProducer mp = s.createProducer(q);
      MessageConsumer consumer = s.createConsumer(q);
      Message message = s.createTextMessage("test");
      mp.send(message);
      s.commit();
      queueConnection.start();
      TextMessage textMessage = (TextMessage) consumer.receive(1000);
      assertNotNull(textMessage);
      assertEquals("test", textMessage.getText());
      s.rollback();
      textMessage = (TextMessage) consumer.receive(1000);
      assertNotNull(textMessage);
      assertEquals("test", textMessage.getText());
      s.commit();
      textMessage = (TextMessage) consumer.receiveNoWait();
      assertNull(textMessage);
   }

   @Test
   public void testSimpleMessageSendAndReceiveNotTransacted() throws Exception {
      setupDLQ(10);
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      ActiveMQRAManagedConnectionFactory mcf = new ActiveMQRAManagedConnectionFactory();
      mcf.setAllowLocalTransactions(true);
      mcf.setResourceAdapter(resourceAdapter);
      ActiveMQRAConnectionFactory qraConnectionFactory = new ActiveMQRAConnectionFactoryImpl(mcf, qraConnectionManager);
      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();
      Session s = queueConnection.createSession(false, Session.SESSION_TRANSACTED);
      Queue q = ActiveMQJMSClient.createQueue(MDBQUEUE);
      MessageProducer mp = s.createProducer(q);
      MessageConsumer consumer = s.createConsumer(q);
      Message message = s.createTextMessage("test");
      mp.send(message);
      s.commit();
      queueConnection.start();
      TextMessage textMessage = (TextMessage) consumer.receive(1000);
      assertNotNull(textMessage);
      assertEquals("test", textMessage.getText());
      s.rollback();
      textMessage = (TextMessage) consumer.receive(1000);
      assertNotNull(textMessage);
      assertEquals("test", textMessage.getText());
      s.commit();
      textMessage = (TextMessage) consumer.receiveNoWait();
      assertNull(textMessage);
   }

   @Test
   public void testSimpleMessageSendAndReceiveSessionTransacted2() throws Exception {
      setupDLQ(10);
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      ActiveMQRAManagedConnectionFactory mcf = new ActiveMQRAManagedConnectionFactory();
      mcf.setAllowLocalTransactions(true);
      mcf.setResourceAdapter(resourceAdapter);
      ActiveMQRAConnectionFactory qraConnectionFactory = new ActiveMQRAConnectionFactoryImpl(mcf, qraConnectionManager);
      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();
      Session s = queueConnection.createSession(Session.SESSION_TRANSACTED);
      Queue q = ActiveMQJMSClient.createQueue(MDBQUEUE);
      MessageProducer mp = s.createProducer(q);
      MessageConsumer consumer = s.createConsumer(q);
      Message message = s.createTextMessage("test");
      mp.send(message);
      s.commit();
      queueConnection.start();
      TextMessage textMessage = (TextMessage) consumer.receive(1000);
      assertNotNull(textMessage);
      assertEquals("test", textMessage.getText());
      s.rollback();
      textMessage = (TextMessage) consumer.receive(1000);
      assertNotNull(textMessage);
      assertEquals("test", textMessage.getText());
      s.commit();
      textMessage = (TextMessage) consumer.receiveNoWait();
      assertNull(textMessage);
   }

   @Test
   public void sessionNotTransactedTestNoActiveJTATx() throws Exception {
      JMSContext context = qraConnectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE);
      assertEquals(JMSContext.AUTO_ACKNOWLEDGE, context.getSessionMode());
   }

   @Test
   public void sessionTransactedTestNoActiveJTATx() throws Exception {
      try {
         qraConnectionFactory.createContext(JMSContext.SESSION_TRANSACTED);
         fail("Exception expected");
      } catch (JMSRuntimeException ignored) {
      }
   }

   @Test
   public void testQueueSessionAckMode() throws Exception {

      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();

      Session s = queueConnection.createSession(false, Session.SESSION_TRANSACTED);

      s.close();
   }



   @Test
   public void testSimpleSendNoXAJMSContext() throws Exception {
      Queue q = ActiveMQJMSClient.createQueue(MDBQUEUE);

      try (ClientSessionFactory sf = locator.createSessionFactory();
           ClientSession session = sf.createSession();
           ClientConsumer consVerify = session.createConsumer(MDBQUEUE);
           JMSContext jmsctx = qraConnectionFactory.createContext();
      ) {
         session.start();
         // These next 4 lines could be written in a single line however it makes difficult for debugging
         JMSProducer producer = jmsctx.createProducer();
         producer.setProperty("strvalue", "hello");
         TextMessage msgsend = jmsctx.createTextMessage("hello");
         producer.send(q, msgsend);

         ClientMessage msg = consVerify.receive(1000);
         assertNotNull(msg);
         assertEquals("hello", msg.getStringProperty("strvalue"));
      }
   }

   @Test
   public void testSimpleMessageSendAndReceive() throws Exception {
      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();
      Session s = queueConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue q = ActiveMQJMSClient.createQueue(MDBQUEUE);
      MessageProducer mp = s.createProducer(q);
      MessageConsumer consumer = s.createConsumer(q);
      Message message = s.createTextMessage("test");
      mp.send(message);
      queueConnection.start();
      TextMessage textMessage = (TextMessage) consumer.receive(1000);
      assertNotNull(textMessage);
      assertEquals("test", textMessage.getText());
   }

   @Test
   public void testSimpleSendNoXAJMS1() throws Exception {
      Queue q = ActiveMQJMSClient.createQueue(MDBQUEUE);
      try (ClientSessionFactory sf = locator.createSessionFactory();
           ClientSession session = sf.createSession();
           ClientConsumer consVerify = session.createConsumer(MDBQUEUE);
           Connection conn = qraConnectionFactory.createConnection();
      ) {
         Session jmsSess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         session.start();
         MessageProducer producer = jmsSess.createProducer(q);
         // These next 4 lines could be written in a single line however it makes difficult for debugging
         TextMessage msgsend = jmsSess.createTextMessage("hello");
         msgsend.setStringProperty("strvalue", "hello");
         producer.send(msgsend);

         ClientMessage msg = consVerify.receive(1000);
         assertNotNull(msg);
         assertEquals("hello", msg.getStringProperty("strvalue"));
      }
   }
}
