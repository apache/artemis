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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Message;
import javax.resource.ResourceException;
import javax.resource.spi.ApplicationServerInternalException;
import javax.transaction.Status;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.ra.ActiveMQResourceAdapter;
import org.apache.activemq.artemis.ra.inflow.ActiveMQActivationSpec;
import org.apache.activemq.artemis.service.extensions.ServiceUtils;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.junit.jupiter.api.Test;

public class ActiveMQMessageHandlerXATest extends ActiveMQRATestBase {

   @Override
   public boolean useSecurity() {
      return false;
   }

   @Test
   public void testXACommit() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      XADummyEndpoint endpoint = new XADummyEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, true);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer(MDBQUEUEPREFIXED);
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString("teststring");
      clientProducer.send(message);
      session.close();
      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("teststring", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());
      endpoint.prepare();
      endpoint.commit();
      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();
   }

   @Test
   public void testXABeginFails() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      TransactionSynchronizationRegistry tsr = new DummyTransactionSynchronizationRegistry().setStatus(Status.STATUS_ACTIVE);
      MyBootstrapContext ctx = new MyBootstrapContext().setTransactionSynchronizationRegistry(tsr);
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      XADummyEndpointBegin endpoint = new XADummyEndpointBegin(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, true);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer(MDBQUEUEPREFIXED);
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString("teststring");
      clientProducer.send(message);
      session.close();
      latch.await(5, TimeUnit.SECONDS);

      assertTrue(tsr.getRollbackOnly());
      assertTrue(endpoint.afterDelivery);
      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();
   }

   @Test
   public void testXACommitWhenStopping() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      spec.setMaxSession(1);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      CountDownLatch beforeDeliveryLatch = new CountDownLatch(1);
      PausingXADummyEndpoint endpoint = new PausingXADummyEndpoint(latch, beforeDeliveryLatch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, true);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer(MDBQUEUEPREFIXED);
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString("teststring");
      clientProducer.send(message);
      ClientMessage message2 = session.createMessage(true);
      message2.getBodyBuffer().writeString("teststring2");
      clientProducer.send(message2);
      session.close();
      beforeDeliveryLatch.await(5, TimeUnit.SECONDS);
      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();

      assertFalse(endpoint.interrupted);
      assertNotNull(endpoint.lastMessage);
      assertEquals("teststring", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());

      Binding binding = server.getPostOffice().getBinding(MDBQUEUEPREFIXEDSIMPLE);
      long messageCount = getMessageCount((Queue) binding.getBindable());
      assertEquals(1, messageCount);
   }

   @Test
   public void testXACommitInterruptsWhenStopping() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setCallTimeout(500L);
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      spec.setMaxSession(1);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      CountDownLatch beforeDeliveryLatch = new CountDownLatch(1);
      PausingXADummyEndpoint endpoint = new PausingXADummyEndpoint(latch, beforeDeliveryLatch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, true);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer(MDBQUEUEPREFIXED);
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString("teststring");
      clientProducer.send(message);
      ClientMessage message2 = session.createMessage(true);
      message2.getBodyBuffer().writeString("teststring2");
      clientProducer.send(message2);
      session.close();
      beforeDeliveryLatch.await(5, TimeUnit.SECONDS);
      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();
      assertTrue(endpoint.interrupted);
      assertNotNull(endpoint.lastMessage);
      assertEquals("teststring", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());
   }

   @Test
   public void testXARollback() throws Exception {
      setupDLQ(10);
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setMaxSession(1);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      XADummyEndpoint endpoint = new XADummyEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, true);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer(MDBQUEUEPREFIXED);
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString("teststring");
      clientProducer.send(message);
      session.close();
      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("teststring", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());
      latch = new CountDownLatch(1);
      endpoint.reset(latch);
      endpoint.rollback();
      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("teststring", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());
      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();
   }

   class XADummyEndpoint extends DummyMessageEndpoint {

      private Xid xid;

      XADummyEndpoint(CountDownLatch latch) {
         super(latch);
         xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      }

      @Override
      public void beforeDelivery(Method method) throws NoSuchMethodException, ResourceException {
         super.beforeDelivery(method);
         try {
            xaResource.start(xid, XAResource.TMNOFLAGS);
         } catch (XAException e) {
            throw new ResourceException(e.getMessage(), e);
         }
      }

      @Override
      public void afterDelivery() throws ResourceException {
         try {
            xaResource.end(xid, XAResource.TMSUCCESS);
         } catch (XAException e) {
            throw new ResourceException(e.getMessage(), e);
         }

         super.afterDelivery();
      }

      public void rollback() throws XAException {
         xaResource.rollback(xid);
      }

      public void prepare() throws XAException {
         xaResource.prepare(xid);
      }

      public void commit() throws XAException {
         xaResource.commit(xid, false);
      }
   }

   class PausingXADummyEndpoint extends XADummyEndpoint {

      private final CountDownLatch beforeDeliveryLatch;

      boolean interrupted = false;

      PausingXADummyEndpoint(CountDownLatch latch, CountDownLatch beforeDeliveryLatch) {
         super(latch);
         this.beforeDeliveryLatch = beforeDeliveryLatch;
      }

      @Override
      public void beforeDelivery(Method method) throws NoSuchMethodException, ResourceException {
         super.beforeDelivery(method);
         beforeDeliveryLatch.countDown();
      }

      @Override
      public void onMessage(Message message) {
         super.onMessage(message);
         try {
            Thread.sleep(2000);
         } catch (InterruptedException e) {
            interrupted = true;
         }
      }

      @Override
      public void release() {
         try {
            prepare();
            commit();
         } catch (XAException e) {
            e.printStackTrace();
         }
         super.release();
      }
   }

   class XADummyEndpointBegin extends XADummyEndpoint {

      private boolean afterDelivery = false;

      XADummyEndpointBegin(CountDownLatch latch) {
         super(latch);
      }

      @Override
      public void beforeDelivery(Method method) throws NoSuchMethodException, ResourceException {
         super.beforeDelivery(method);
         DummyTransactionManager dummyTransactionManager = (DummyTransactionManager) ServiceUtils.getTransactionManager();
         DummyTransaction tx = new DummyTransaction();
         dummyTransactionManager.tx = tx;
         throw new ApplicationServerInternalException();
      }

      @Override
      public void afterDelivery() throws ResourceException {
         afterDelivery = true;
         latch.countDown();
      }
   }
}
