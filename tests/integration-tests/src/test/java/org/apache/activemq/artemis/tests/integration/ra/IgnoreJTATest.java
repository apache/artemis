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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.ra.ActiveMQRAConnectionFactory;
import org.apache.activemq.artemis.ra.ActiveMQRAConnectionFactoryImpl;
import org.apache.activemq.artemis.ra.ActiveMQRAConnectionManager;
import org.apache.activemq.artemis.ra.ActiveMQRAManagedConnectionFactory;
import org.apache.activemq.artemis.ra.ActiveMQResourceAdapter;
import org.apache.activemq.artemis.service.extensions.ServiceUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.IllegalStateException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.transaction.Status;

public class IgnoreJTATest extends ActiveMQRATestBase {

   protected ActiveMQResourceAdapter resourceAdapter;
   protected ActiveMQRAConnectionFactory qraConnectionFactory;
   protected ActiveMQRAManagedConnectionFactory mcf;
   ActiveMQRAConnectionManager qraConnectionManager = new ActiveMQRAConnectionManager();

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      useDummyTransactionManager();
      super.setUp();

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
      ((DummyTransactionManager) ServiceUtils.getTransactionManager()).tx = null;
      if (resourceAdapter != null) {
         resourceAdapter.stop();
      }

      qraConnectionManager.stop();
      super.tearDown();
   }

   @Test
   public void testIgnoreJTA() throws Exception {
      assertThrows(IllegalStateException.class, () -> {
         testSendAndReceive(true);
      });
   }

   @Test
   public void testDontIgnoreJTA() throws Exception {
      testSendAndReceive(false);
   }

   @Test
   public void testDefaultIgnoreJTA() throws Exception {
      testSendAndReceive(null);
   }

   private void testSendAndReceive(Boolean ignoreJTA) throws Exception {
      setupDLQ(10);
      resourceAdapter = newResourceAdapter();
      if (ignoreJTA != null) {
         resourceAdapter.setIgnoreJTA(ignoreJTA);
      }
      MyBootstrapContext ctx = new MyBootstrapContext().setTransactionSynchronizationRegistry(new DummyTransactionSynchronizationRegistry().setStatus(Status.STATUS_ACTIVE));
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

   private void setDummyTX() {
      ((DummyTransactionManager) ServiceUtils.getTransactionManager()).tx = new DummyTransaction();
   }
}
