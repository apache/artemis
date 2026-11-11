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
package org.apache.activemq.artemis.tests.unit.core.remoting.server.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.server.impl.RemotingServiceImpl;
import org.apache.activemq.artemis.core.server.ServiceRegistry;
import org.apache.activemq.artemis.core.server.impl.ServiceRegistryImpl;
import org.apache.activemq.artemis.tests.unit.core.remoting.server.impl.fake.FakeInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RemotingServiceImplTest {

   private ServiceRegistry serviceRegistry;

   private RemotingServiceImpl remotingService;

   private Configuration configuration;

   @BeforeEach
   public void setUp() throws Exception {
      serviceRegistry = new ServiceRegistryImpl();
      configuration = new ConfigurationImpl();
      configuration.setAcceptorConfigurations(new HashSet<>());
      remotingService = new RemotingServiceImpl(null, configuration, null, null, null, null, null, serviceRegistry);
   }

   /**
    * Tests that the service registry gets propaged into remotingService.
    */
   @Test
   public void testPropagatingInterceptors() throws Exception {
      for (int i = 0; i < 5; i++) {
         serviceRegistry.addIncomingInterceptor(new FakeInterceptor());
      }

      remotingService = new RemotingServiceImpl(null, configuration, null, null, null, null, null, serviceRegistry);

      assertEquals(5, remotingService.getIncomingInterceptors().size());
      assertInstanceOf(FakeInterceptor.class, remotingService.getIncomingInterceptors().get(0));
      assertNotSame(remotingService.getIncomingInterceptors().get(0), remotingService.getIncomingInterceptors().get(1));
   }

   /**
    * Tests ensures that setInterceptors methods adds both interceptors from the service registry and also interceptors
    * defined in the configuration.
    */
   @Test
   public void testSetInterceptorsAddsBothInterceptorsFromConfigAndServiceRegistry() throws Exception {
      Method method = RemotingServiceImpl.class.getDeclaredMethod("setInterceptors", Configuration.class);
      Field incomingInterceptors = RemotingServiceImpl.class.getDeclaredField("incomingInterceptors");
      Field outgoingInterceptors = RemotingServiceImpl.class.getDeclaredField("outgoingInterceptors");

      method.setAccessible(true);
      incomingInterceptors.setAccessible(true);
      outgoingInterceptors.setAccessible(true);

      serviceRegistry.addIncomingInterceptor(new FakeInterceptor());
      serviceRegistry.addOutgoingInterceptor(new FakeInterceptor());

      List<String> interceptorClassNames = new ArrayList<>();
      interceptorClassNames.add(FakeInterceptor.class.getCanonicalName());
      configuration.setIncomingInterceptorClassNames(interceptorClassNames);
      configuration.setOutgoingInterceptorClassNames(interceptorClassNames);

      method.invoke(remotingService, configuration);

      assertEquals(2, ((List) incomingInterceptors.get(remotingService)).size());
      assertEquals(2, ((List) outgoingInterceptors.get(remotingService)).size());
      assertTrue(((List) incomingInterceptors.get(remotingService)).contains(serviceRegistry.getIncomingInterceptors(null).get(0)));
      assertTrue(((List) outgoingInterceptors.get(remotingService)).contains(serviceRegistry.getOutgoingInterceptors(null).get(0)));
   }

   /**
    * Tests ensures that both interceptors from the service registry and also interceptors defined in the configuration
    * are added to the RemotingServiceImpl on creation
    */
   @Test
   public void testInterceptorsAreAddedOnCreationOfServiceRegistry() throws Exception {
      Field incomingInterceptors = RemotingServiceImpl.class.getDeclaredField("incomingInterceptors");
      Field outgoingInterceptors = RemotingServiceImpl.class.getDeclaredField("outgoingInterceptors");

      incomingInterceptors.setAccessible(true);
      outgoingInterceptors.setAccessible(true);

      serviceRegistry.addIncomingInterceptor(new FakeInterceptor());
      serviceRegistry.addOutgoingInterceptor(new FakeInterceptor());

      List<String> interceptorClassNames = new ArrayList<>();
      interceptorClassNames.add(FakeInterceptor.class.getCanonicalName());
      configuration.setIncomingInterceptorClassNames(interceptorClassNames);
      configuration.setOutgoingInterceptorClassNames(interceptorClassNames);

      remotingService = new RemotingServiceImpl(null, configuration, null, null, null, null, null, serviceRegistry);

      assertEquals(2, ((List) incomingInterceptors.get(remotingService)).size());
      assertEquals(2, ((List) outgoingInterceptors.get(remotingService)).size());
      assertTrue(((List) incomingInterceptors.get(remotingService)).contains(serviceRegistry.getIncomingInterceptors(null).get(0)));
      assertTrue(((List) outgoingInterceptors.get(remotingService)).contains(serviceRegistry.getOutgoingInterceptors(null).get(0)));
   }
}
