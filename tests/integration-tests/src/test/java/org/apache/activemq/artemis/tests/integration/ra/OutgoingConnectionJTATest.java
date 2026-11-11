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
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.transaction.Status;
import java.util.HashSet;
import java.util.Set;

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
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OutgoingConnectionJTATest extends ActiveMQRATestBase {

   protected ActiveMQResourceAdapter resourceAdapter;
   protected ActiveMQRAConnectionFactory qraConnectionFactory;
   protected ActiveMQRAManagedConnectionFactory mcf;
   private DummyTransactionSynchronizationRegistry tsr;
   ActiveMQRAConnectionManager qraConnectionManager = new ActiveMQRAConnectionManager();

   @Override
   public boolean useSecurity() {
      return true;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
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
      tsr = new DummyTransactionSynchronizationRegistry();
      MyBootstrapContext ctx = new MyBootstrapContext().setTransactionSynchronizationRegistry(tsr);
      resourceAdapter.start(ctx);
      mcf = new ActiveMQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      qraConnectionFactory = new ActiveMQRAConnectionFactoryImpl(mcf, qraConnectionManager);
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      if (resourceAdapter != null) {
         resourceAdapter.stop();
      }

      qraConnectionManager.stop();
      super.tearDown();
   }

   @Test
   public void testSimpleMessageSendAndReceiveTransacted() throws Exception {
      tsr.setStatus(Status.STATUS_ACTIVE);
      setupDLQ(10);
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext().setTransactionSynchronizationRegistry(tsr);
      resourceAdapter.start(ctx);
      ActiveMQRAManagedConnectionFactory mcf = new ActiveMQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      ActiveMQRAConnectionFactory qraConnectionFactory = new ActiveMQRAConnectionFactoryImpl(mcf, qraConnectionManager);
      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();
      Session s = queueConnection.createSession(true, Session.AUTO_ACKNOWLEDGE);
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
   }

   public void testQueuSessionAckMode(boolean inTx) throws Exception {
      if (inTx) {
         tsr.setStatus(Status.STATUS_ACTIVE);
      }
      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();

      Session s = queueConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      if (inTx) {
         assertEquals(Session.SESSION_TRANSACTED, s.getAcknowledgeMode());
      } else {
         assertEquals(Session.AUTO_ACKNOWLEDGE, s.getAcknowledgeMode());
      }
      s.close();

      s = queueConnection.createSession(false, Session.DUPS_OK_ACKNOWLEDGE);
      if (inTx) {
         assertEquals(Session.SESSION_TRANSACTED, s.getAcknowledgeMode());
      } else {
         assertEquals(Session.DUPS_OK_ACKNOWLEDGE, s.getAcknowledgeMode());
      }
      s.close();

      //exception should be thrown if ack mode is SESSION_TRANSACTED or
      //CLIENT_ACKNOWLEDGE when in a JTA else ackmode should bee ignored
      try {
         s = queueConnection.createSession(false, Session.SESSION_TRANSACTED);
         if (inTx) {
            assertEquals(Session.SESSION_TRANSACTED, s.getAcknowledgeMode());
         } else {
            fail("didn't get expected exception creating session with SESSION_TRANSACTED mode ");
         }
         s.close();
      } catch (JMSException e) {
         if (inTx) {
            fail("shouldn't throw exception " + e);
         }
      }

      try {
         s = queueConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         if (inTx) {
            assertEquals(Session.SESSION_TRANSACTED, s.getAcknowledgeMode());
         } else {
            fail("didn't get expected exception creating session with CLIENT_ACKNOWLEDGE mode");
         }
      } catch (JMSException e) {
         if (inTx) {
            fail("shouldn't throw exception " + e);
         }
      }

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
   public void testQueueSessionAckModeJTA() throws Exception {
      testQueuSessionAckMode(true);
   }

   @Test
   public void testSessionAckModeNoJTA() throws Exception {
      testQueuSessionAckMode(false);
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
