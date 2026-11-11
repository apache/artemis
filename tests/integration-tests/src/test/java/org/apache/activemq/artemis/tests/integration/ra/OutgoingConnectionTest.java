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

import javax.jms.Connection;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSSecurityException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.TopicConnection;
import javax.jms.XAConnection;
import javax.jms.XAQueueConnection;
import javax.jms.XASession;
import javax.resource.spi.ManagedConnection;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.ra.ActiveMQRAConnectionFactory;
import org.apache.activemq.artemis.ra.ActiveMQRAConnectionFactoryImpl;
import org.apache.activemq.artemis.ra.ActiveMQRAConnectionManager;
import org.apache.activemq.artemis.ra.ActiveMQRAManagedConnection;
import org.apache.activemq.artemis.ra.ActiveMQRAManagedConnectionFactory;
import org.apache.activemq.artemis.ra.ActiveMQRASession;
import org.apache.activemq.artemis.ra.ActiveMQResourceAdapter;
import org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapper;
import org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapperImpl;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.VersionLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

public class OutgoingConnectionTest extends ActiveMQRATestBase {

   private ActiveMQResourceAdapter resourceAdapter;
   private ActiveMQRAConnectionFactory qraConnectionFactory;
   private ActiveMQRAManagedConnectionFactory mcf;

   @Override
   public boolean useSecurity() {
      return true;
   }

   ActiveMQRAConnectionManager qraConnectionManager = new ActiveMQRAConnectionManager();

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("testuser", "testpassword");
      securityManager.getConfiguration().addUser("guest", "guest");
      securityManager.getConfiguration().setDefaultUser("guest");
      securityManager.getConfiguration().addRole("testuser", "arole");
      securityManager.getConfiguration().addRole("guest", "arole");
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
   public void testReference() throws Exception {
      assertNotNull(qraConnectionFactory.getReference());
   }

   @Test
   public void testSimpleMessageSendAndReceiveXA() throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      XAQueueConnection queueConnection = qraConnectionFactory.createXAQueueConnection();
      XASession s = queueConnection.createXASession();

      XAResource resource = s.getXAResource();
      resource.start(xid, XAResource.TMNOFLAGS);
      Queue q = ActiveMQJMSClient.createQueue(MDBQUEUE);
      MessageProducer mp = s.createProducer(q);
      MessageConsumer consumer = s.createConsumer(q);
      Message message = s.createTextMessage("test");
      mp.send(message);
      queueConnection.start();
      TextMessage textMessage = (TextMessage) consumer.receiveNoWait();
      assertNull(textMessage);
      resource.end(xid, XAResource.TMSUCCESS);
      resource.commit(xid, true);
      resource.start(xid, XAResource.TMNOFLAGS);
      textMessage = (TextMessage) consumer.receiveNoWait();
      resource.end(xid, XAResource.TMSUCCESS);
      resource.commit(xid, true);
      assertNotNull(textMessage);
      assertEquals("test", textMessage.getText());

      // When I wrote this call, this method was doing an infinite loop.
      // this is just to avoid such thing again
      textMessage.getJMSDeliveryTime();

   }

   @Test
   public void testInexistentUserOnCreateConnection() throws Exception {
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      ActiveMQRAManagedConnectionFactory mcf = new ActiveMQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      ActiveMQRAConnectionFactory qraConnectionFactory = new ActiveMQRAConnectionFactoryImpl(mcf, qraConnectionManager);

      Connection conn = null;
      try {
         conn = qraConnectionFactory.createConnection("IDont", "Exist");
         fail("Exception was expected");
      } catch (JMSSecurityException expected) {
      }

      conn = qraConnectionFactory.createConnection("testuser", "testpassword");
      conn.close();

      try {
         XAConnection xaconn = qraConnectionFactory.createXAConnection("IDont", "Exist");
         fail("Exception was expected");
      } catch (JMSSecurityException expected) {
      }

      XAConnection xaconn = qraConnectionFactory.createXAConnection("testuser", "testpassword");
      xaconn.close();

      try {
         TopicConnection topicconn = qraConnectionFactory.createTopicConnection("IDont", "Exist");
         fail("Exception was expected");
      } catch (JMSSecurityException expected) {
      }

      TopicConnection topicconn = qraConnectionFactory.createTopicConnection("testuser", "testpassword");
      topicconn.close();

      try {
         QueueConnection queueconn = qraConnectionFactory.createQueueConnection("IDont", "Exist");
         fail("Exception was expected");
      } catch (JMSSecurityException expected) {
      }

      QueueConnection queueconn = qraConnectionFactory.createQueueConnection("testuser", "testpassword");
      queueconn.close();

      mcf.stop();

   }

   @Test
   public void testMultipleSessionsThrowsException() throws Exception {
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      ActiveMQRAManagedConnectionFactory mcf = new ActiveMQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      ActiveMQRAConnectionFactory qraConnectionFactory = new ActiveMQRAConnectionFactoryImpl(mcf, qraConnectionManager);
      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();
      Session s = queueConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      try {
         Session s2 = queueConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         fail("should throw javax,jms.IllegalStateException: Only allowed one session per connection. See the J2EE spec, e.g. J2EE1.4 Section 6.6");
      } catch (JMSException e) {
      }
   }

   @Test
   public void testConnectionCredentials() throws Exception {
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      ActiveMQRAManagedConnectionFactory mcf = new ActiveMQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      ActiveMQRAConnectionFactory qraConnectionFactory = new ActiveMQRAConnectionFactoryImpl(mcf, qraConnectionManager);
      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();
      QueueSession session = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

      queueConnection = qraConnectionFactory.createQueueConnection("testuser", "testpassword");
      session = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

   }

   @Test
   public void testConnectionCredentialsFail() throws Exception {
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      ActiveMQRAManagedConnectionFactory mcf = new ActiveMQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      ActiveMQRAConnectionFactory qraConnectionFactory = new ActiveMQRAConnectionFactoryImpl(mcf, qraConnectionManager);
      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();
      QueueSession session = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

      ManagedConnection mc = ((ActiveMQRASession) session).getManagedConnection();
      queueConnection.close();
      mc.destroy();

      try {
         queueConnection = qraConnectionFactory.createQueueConnection("testuser", "testwrongpassword");
         queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE).close();
         fail("should throw esxception");
      } catch (JMSException e) {
         //pass
      }
   }

   @Test
   public void testConnectionCredentialsFailRecovery() throws Exception {
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      ActiveMQRAManagedConnectionFactory mcf = new ActiveMQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      ActiveMQRAConnectionFactory qraConnectionFactory = new ActiveMQRAConnectionFactoryImpl(mcf, qraConnectionManager);
      try {
         QueueConnection queueConnection = qraConnectionFactory.createQueueConnection("testuser", "testwrongpassword");
         queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE).close();
         fail("should throw esxception");
      } catch (JMSException e) {
         //make sure the recovery is null
         assertNull(mcf.getResourceRecovery());
      }
   }

   @Test
   public void testConnectionCredentialsOKRecovery() throws Exception {
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      ActiveMQRAManagedConnectionFactory mcf = new ActiveMQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      ActiveMQRAConnectionFactory qraConnectionFactory = new ActiveMQRAConnectionFactoryImpl(mcf, qraConnectionManager);
      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();
      QueueSession session = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

      assertNotNull(mcf.getResourceRecovery());
   }

   @Test
   public void testJMSContext() throws Exception {
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      ActiveMQRAManagedConnectionFactory mcf = new ActiveMQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      ActiveMQRAConnectionFactory qraConnectionFactory = new ActiveMQRAConnectionFactoryImpl(mcf, qraConnectionManager);

      JMSContext jmsctx = qraConnectionFactory.createContext(JMSContext.DUPS_OK_ACKNOWLEDGE);
      assertEquals(JMSContext.DUPS_OK_ACKNOWLEDGE, jmsctx.getSessionMode());

   }

   @Test
   public void testOutgoingXAResourceWrapper() throws Exception {
      XAQueueConnection queueConnection = qraConnectionFactory.createXAQueueConnection();
      XASession s = queueConnection.createXASession();

      XAResource resource = s.getXAResource();
      assertInstanceOf(ActiveMQXAResourceWrapper.class, resource);

      ActiveMQXAResourceWrapperImpl xaResourceWrapper = (ActiveMQXAResourceWrapperImpl) resource;
      assertEquals("java://jmsXA NodeId:" + server.getNodeID(), xaResourceWrapper.getJndiName());
      assertEquals(VersionLoader.getVersion().getFullVersion(), xaResourceWrapper.getProductVersion());
      assertEquals(ActiveMQResourceAdapter.PRODUCT_NAME, xaResourceWrapper.getProductName());
   }

   @Test
   public void testSharedActiveMQConnectionFactory() throws Exception {
      Session s = null;
      Session s2 = null;
      ActiveMQRAManagedConnection mc = null;
      ActiveMQRAManagedConnection mc2 = null;

      try {
         resourceAdapter = new ActiveMQResourceAdapter();

         resourceAdapter.setConnectorClassName(InVMConnectorFactory.class.getName());
         MyBootstrapContext ctx = new MyBootstrapContext();
         resourceAdapter.start(ctx);
         ActiveMQRAConnectionManager qraConnectionManager = new ActiveMQRAConnectionManager();
         ActiveMQRAManagedConnectionFactory mcf = new ActiveMQRAManagedConnectionFactory();
         mcf.setResourceAdapter(resourceAdapter);
         ActiveMQRAConnectionFactory qraConnectionFactory = new ActiveMQRAConnectionFactoryImpl(mcf, qraConnectionManager);

         QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();
         s = queueConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         mc = (ActiveMQRAManagedConnection) ((ActiveMQRASession) s).getManagedConnection();
         ActiveMQConnectionFactory cf1 = mc.getConnectionFactory();

         QueueConnection queueConnection2 = qraConnectionFactory.createQueueConnection();
         s2 = queueConnection2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         mc2 = (ActiveMQRAManagedConnection) ((ActiveMQRASession) s2).getManagedConnection();
         ActiveMQConnectionFactory cf2 = mc2.getConnectionFactory();

         // we're not testing equality so don't use equals(); we're testing if they are actually the *same* object
         assertSame(cf1, cf2);
      } finally {
         if (s != null) {
            s.close();
         }

         if (mc != null) {
            mc.destroy();
         }

         if (s2 != null) {
            s2.close();
         }

         if (mc2 != null) {
            mc2.destroy();
         }
      }
   }

   @Test
   public void testSharedActiveMQConnectionFactoryWithClose() throws Exception {
      Session s = null;
      Session s2 = null;
      ActiveMQRAManagedConnection mc = null;
      ActiveMQRAManagedConnection mc2 = null;

      try {
         server.getConfiguration().setSecurityEnabled(false);
         resourceAdapter = new ActiveMQResourceAdapter();

         resourceAdapter.setConnectorClassName(InVMConnectorFactory.class.getName());
         MyBootstrapContext ctx = new MyBootstrapContext();
         resourceAdapter.start(ctx);
         ActiveMQRAConnectionManager qraConnectionManager = new ActiveMQRAConnectionManager();
         ActiveMQRAManagedConnectionFactory mcf = new ActiveMQRAManagedConnectionFactory();
         mcf.setResourceAdapter(resourceAdapter);
         ActiveMQRAConnectionFactory qraConnectionFactory = new ActiveMQRAConnectionFactoryImpl(mcf, qraConnectionManager);

         QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();
         s = queueConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         mc = (ActiveMQRAManagedConnection) ((ActiveMQRASession) s).getManagedConnection();

         QueueConnection queueConnection2 = qraConnectionFactory.createQueueConnection();
         s2 = queueConnection2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         mc2 = (ActiveMQRAManagedConnection) ((ActiveMQRASession) s2).getManagedConnection();

         mc.destroy();

         MessageProducer producer = s2.createProducer(ActiveMQJMSClient.createQueue(MDBQUEUE));
         producer.send(s2.createTextMessage("x"));
      } finally {
         if (s != null) {
            s.close();
         }

         if (mc != null) {
            mc.destroy();
         }

         if (s2 != null) {
            s2.close();
         }

         if (mc2 != null) {
            mc2.destroy();
         }
      }
   }
}
