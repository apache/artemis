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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.ra.ActiveMQResourceAdapter;
import org.apache.activemq.artemis.ra.inflow.ActiveMQActivationSpec;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.junit.jupiter.api.Test;

public class ActiveMQMessageHandlerSecurityTest extends ActiveMQRATestBase {

   @Override
   public boolean useSecurity() {
      return true;
   }

   @Test
   public void testSimpleMessageReceivedOnQueueWithSecurityFails() throws Exception {
      SecurityConfiguration emptyConfiguration = new SecurityConfiguration();
      ((ActiveMQJAASSecurityManager) server.getSecurityManager()).setConfiguration(emptyConfiguration);
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      spec.setUser("dodgyuser");
      spec.setPassword("dodgypassword");
      spec.setSetupAttempts(0);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      Binding binding = server.getPostOffice().getBinding(MDBQUEUEPREFIXEDSIMPLE);
      assertEquals(0, ((LocalQueueBinding) binding).getQueue().getConsumerCount());
      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();
   }

   @Test
   public void testSimpleMessageReceivedOnQueueWithSecuritySucceeds() throws Exception {
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("testuser", "testpassword");
      securityManager.getConfiguration().addRole("testuser", "arole");
      Role role = new Role("arole", false, true, false, false, false, false, false, false, false, false, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      server.getSecurityRepository().addMatch(MDBQUEUEPREFIXED, roles);
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      spec.setUser("testuser");
      spec.setPassword("testpassword");
      spec.setSetupAttempts(0);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      Binding binding = server.getPostOffice().getBinding(MDBQUEUEPREFIXEDSIMPLE);
      assertEquals(15, ((LocalQueueBinding) binding).getQueue().getConsumerCount());
      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();
   }
}
