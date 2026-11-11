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

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.resource.ResourceException;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.BootstrapContext;
import javax.resource.spi.UnavailableException;
import javax.resource.spi.XATerminator;
import javax.resource.spi.endpoint.MessageEndpoint;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.resource.spi.work.ExecutionContext;
import javax.resource.spi.work.Work;
import javax.resource.spi.work.WorkContext;
import javax.resource.spi.work.WorkException;
import javax.resource.spi.work.WorkListener;
import javax.resource.spi.work.WorkManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.xa.XAResource;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.CountDownLatch;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.ra.ActiveMQResourceAdapter;
import org.apache.activemq.artemis.ra.inflow.ActiveMQActivation;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.BeforeEach;

public abstract class ActiveMQRATestBase extends JMSTestBase {

   protected ServerLocator locator;

   protected static final String MDBQUEUE = "mdbQueue";
   protected static final String DLQ = "dlqQueue";
   protected static final String MDBQUEUEPREFIXED = "mdbQueue";
   protected static final SimpleString MDBQUEUEPREFIXEDSIMPLE = SimpleString.of("mdbQueue");

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = createInVMNonHALocator();
      createQueue(true, MDBQUEUE);
      createQueue(DLQ);
      setupDLQ(1);
   }

   protected void setupDLQ(int maxDeliveries) {
      AddressSettings settings = new AddressSettings().setDeadLetterAddress(SimpleString.of(DLQ)).setMaxDeliveryAttempts(maxDeliveries);
      server.getAddressSettingsRepository().addMatch("#", settings);
   }

   protected ActiveMQActivation lookupActivation(ActiveMQResourceAdapter qResourceAdapter) {
      Map<ActivationSpec, ActiveMQActivation> activations = qResourceAdapter.getActivations();
      assertEquals(1, activations.size());

      return activations.values().iterator().next();
   }

   protected ActiveMQResourceAdapter newResourceAdapter() {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      return qResourceAdapter;
   }

   protected class DummyMessageEndpointFactory implements MessageEndpointFactory {

      private DummyMessageEndpoint endpoint;

      private final boolean isDeliveryTransacted;

      public DummyMessageEndpointFactory(DummyMessageEndpoint endpoint, boolean deliveryTransacted) {
         this.endpoint = endpoint;
         isDeliveryTransacted = deliveryTransacted;
      }

      @Override
      public MessageEndpoint createEndpoint(XAResource xaResource) throws UnavailableException {
         if (xaResource != null) {
            endpoint.setXAResource(xaResource);
         }
         return endpoint;
      }

      @Override
      public MessageEndpoint createEndpoint(XAResource xaResource, long l) throws UnavailableException {
         return null;
      }

      @Override
      public boolean isDeliveryTransacted(Method method) throws NoSuchMethodException {
         return isDeliveryTransacted;
      }

      @Override
      public String getActivationName() {
         return null;
      }

      @Override
      public Class<?> getEndpointClass() {
         return DummyMessageEndpoint.class;
      }
   }

   protected class DummyMessageEndpoint implements MessageEndpoint, MessageListener {

      public CountDownLatch latch;

      public ActiveMQMessage lastMessage;

      public boolean released = false;

      public XAResource xaResource;

      volatile boolean inAfterDelivery = false;

      public DummyMessageEndpoint(CountDownLatch latch) {
         this.latch = latch;
      }

      @Override
      public void beforeDelivery(Method method) throws NoSuchMethodException, ResourceException {

      }

      @Override
      public void afterDelivery() throws ResourceException {
         if (latch != null) {
            latch.countDown();
         }
      }

      @Override
      public void release() {
         released = true;
      }

      @Override
      public void onMessage(Message message) {
         lastMessage = (ActiveMQMessage) message;
      }

      public void reset(CountDownLatch latch) {
         this.latch = latch;
         lastMessage = null;
      }

      public void setXAResource(XAResource xaResource) {
         this.xaResource = xaResource;
      }
   }

   public class MyBootstrapContext implements BootstrapContext {
      TransactionSynchronizationRegistry tsr = null;

      public MyBootstrapContext setTransactionSynchronizationRegistry(TransactionSynchronizationRegistry tsr) {
         this.tsr = tsr;
         return this;
      }

      WorkManager workManager = new DummyWorkManager();

      @Override
      public Timer createTimer() throws UnavailableException {
         return null;
      }

      @Override
      public boolean isContextSupported(Class<? extends WorkContext> aClass) {
         return false;
      }

      @Override
      public TransactionSynchronizationRegistry getTransactionSynchronizationRegistry() {
         return tsr;
      }

      @Override
      public WorkManager getWorkManager() {
         return workManager;
      }

      @Override
      public XATerminator getXATerminator() {
         return null;
      }

      class DummyWorkManager implements WorkManager {

         @Override
         public void doWork(Work work) throws WorkException {
         }

         @Override
         public void doWork(Work work,
                            long l,
                            ExecutionContext executionContext,
                            WorkListener workListener) throws WorkException {
         }

         @Override
         public long startWork(Work work) throws WorkException {
            return 0;
         }

         @Override
         public long startWork(Work work,
                               long l,
                               ExecutionContext executionContext,
                               WorkListener workListener) throws WorkException {
            return 0;
         }

         @Override
         public void scheduleWork(Work work) throws WorkException {
            work.run();
         }

         @Override
         public void scheduleWork(Work work,
                                  long l,
                                  ExecutionContext executionContext,
                                  WorkListener workListener) throws WorkException {
         }
      }
   }
}
