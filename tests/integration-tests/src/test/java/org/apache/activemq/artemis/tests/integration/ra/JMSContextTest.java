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
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.JMSRuntimeException;
import javax.jms.MessageFormatRuntimeException;
import javax.jms.Queue;
import javax.jms.TextMessage;
import javax.resource.ResourceException;
import javax.transaction.Status;
import java.util.HashSet;
import java.util.Set;

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

public class JMSContextTest extends ActiveMQRATestBase {

   private ActiveMQResourceAdapter resourceAdapter;
   private DummyTransactionSynchronizationRegistry tsr;
   ActiveMQRAConnectionManager qraConnectionManager = new ActiveMQRAConnectionManager();
   private ActiveMQRAConnectionFactory qraConnectionFactory;

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
      resourceAdapter.setConnectorClassName(InVMConnectorFactory.class.getName());
      tsr = new DummyTransactionSynchronizationRegistry();
      MyBootstrapContext ctx = new MyBootstrapContext().setTransactionSynchronizationRegistry(tsr);
      resourceAdapter.start(ctx);
      ActiveMQRAManagedConnectionFactory mcf = new ActiveMQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      qraConnectionFactory = new ActiveMQRAConnectionFactoryImpl(mcf, qraConnectionManager);
   }

   private void initRA(int status) throws ResourceException {
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
   public void testCreateContextThrowsException() throws Exception {
      JMSContext jmsctx = qraConnectionFactory.createContext();
      try {
         jmsctx.createContext(JMSContext.AUTO_ACKNOWLEDGE);
         fail("expected JMSRuntimeException");
      } catch (JMSRuntimeException e) {
         //pass
      } catch (Exception e) {
         fail("wrong exception thrown: " + e);
      }
   }

   @Test
   public void testCreateXAContextThrowsException() throws Exception {
      JMSContext jmsctx = qraConnectionFactory.createXAContext();
      try {
         jmsctx.createContext(JMSContext.AUTO_ACKNOWLEDGE);
         fail("expected JMSRuntimeException");
      } catch (JMSRuntimeException e) {
         //pass
      } catch (Exception e) {
         fail("wrong exception thrown: " + e);
      }
   }

   @Test
   public void sessionTransactedTestActiveJTATx() throws Exception {
      try {
         qraConnectionFactory.createContext(JMSContext.SESSION_TRANSACTED);
         fail();
      } catch (JMSRuntimeException e) {
         //pass
      }
   }

   @Test
   public void sessionTransactedTestNoActiveJTATx() throws Exception {
      tsr.setStatus(Status.STATUS_ACTIVE);
      JMSContext context = qraConnectionFactory.createContext(JMSContext.SESSION_TRANSACTED);
      assertEquals(JMSContext.AUTO_ACKNOWLEDGE, context.getSessionMode());
   }

   @Test
   public void clientAckTestActiveJTATx() throws Exception {
      try {
         qraConnectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE);
         fail();
      } catch (JMSRuntimeException e) {
         //pass
      }
   }

   @Test
   public void clientAckTestNoActiveJTATx() throws Exception {
      tsr.setStatus(Status.STATUS_ACTIVE);
      JMSContext context = qraConnectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE);
      assertEquals(JMSContext.AUTO_ACKNOWLEDGE, context.getSessionMode());
   }

   @Test
   public void testJMSContextConsumerThrowsMessageFormatExceptionOnMalformedBody() throws Exception {
      Queue queue = createQueue(true, "ContextMalformedBodyTestQueue");

      JMSContext context = qraConnectionFactory.createContext();
      JMSProducer producer = context.createProducer();

      TextMessage message = context.createTextMessage("TestMessage");
      producer.send(queue, message);

      JMSConsumer consumer = context.createConsumer(queue);

      try {
         consumer.receiveBody(Boolean.class);
         fail("Should thrown MessageFormatException");
      } catch (MessageFormatRuntimeException mfre) {
         // Do nothing test passed
      } catch (Exception e) {
         fail("Threw wrong exception, should be MessageFormatRuntimeException, instead got: " + e.getClass().getCanonicalName());
      }
   }
}
