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
package org.apache.activemq.artemis.core.server.management.impl;

import java.lang.reflect.Field;
import javax.management.MBeanServer;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.management.GuardInvocationHandler;
import org.apache.activemq.artemis.core.server.management.HawtioSecurityControl;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ManagementServiceImplTest {

   MBeanServer mBeanServer = Mockito.mock(MBeanServer.class);
   SecurityStore securityStore = Mockito.mock(SecurityStore.class);
   ActiveMQServer messagingServer = Mockito.mock(ActiveMQServer.class);
   ExecutorFactory executorFactory = Mockito.mock(ExecutorFactory.class);
   ArtemisExecutor artemisExecutor = Mockito.mock(ArtemisExecutor.class);
   PostOffice postOffice = Mockito.mock(PostOffice.class);
   SecurityAuth auth = Mockito.mock(SecurityAuth.class);
   PagingManager pagingManager = Mockito.mock(PagingManager.class);
   private PagingStore pageStore = Mockito.mock(PagingStore.class);

   @Test
   public void testGetAttributeSecurityCheck() throws Exception {

      Configuration configuration = new FileConfiguration();
      configuration.setManagementMessageRbac(true);
      configuration.setManagementRbacPrefix("mm");
      configuration.setViewPermissionMethodMatchPattern("^get.*$"); // no match for isPaging
      ManagementServiceImpl managementService = new ManagementServiceImpl(mBeanServer, configuration);

      Mockito.when(executorFactory.getExecutor()).thenReturn(artemisExecutor);
      Mockito.when(messagingServer.getExecutorFactory()).thenReturn(executorFactory);
      Mockito.when(messagingServer.getManagementService()).thenReturn(managementService);
      Mockito.when(postOffice.isStarted()).thenReturn(true);
      Mockito.when(messagingServer.getPostOffice()).thenReturn(postOffice);
      Mockito.when(pagingManager.getPageStore(Mockito.any(SimpleString.class))).thenReturn(pageStore);
      Mockito.when(pageStore.isPaging()).thenReturn(true);
      Mockito.when(pageStore.isStorePaging()).thenReturn(true);


      managementService.registerServer(null, securityStore, null, configuration, null, null, null, null, messagingServer, null, null, pagingManager, false);

      // acceptor
      Mockito.clearInvocations(securityStore);
      Acceptor acceptor = Mockito.mock(Acceptor.class);
      TransportConfiguration transportConfig = Mockito.mock(TransportConfiguration.class);
      Mockito.when(transportConfig.getName()).thenReturn("a1");

      managementService.registerAcceptor(acceptor, transportConfig);
      managementService.getAttribute(ResourceNames.ACCEPTOR + transportConfig.getName(), "name", auth);

      SimpleString expected = SimpleString.of("mm.acceptor.a1.getName");
      Mockito.verify(securityStore).check(Mockito.eq(expected), Mockito.eq(CheckType.VIEW), Mockito.any(SecurityAuth.class));

      // address
      final String addressName = "addr1";
      Mockito.clearInvocations(securityStore);

      managementService.registerAddress(new AddressInfo(addressName));
      managementService.getAttribute(ResourceNames.ADDRESS + addressName, "address", auth);

      expected = SimpleString.of("mm.address." + addressName + ".getAddress");
      Mockito.verify(securityStore).check(Mockito.eq(expected), Mockito.eq(CheckType.VIEW), Mockito.any(SecurityAuth.class));

      // isX needs UPDATE with view regexp above
      Mockito.clearInvocations(securityStore);
      managementService.getAttribute(ResourceNames.ADDRESS + addressName, "paging", auth);

      expected = SimpleString.of("mm.address." + addressName + ".isPaging");
      Mockito.verify(securityStore).check(Mockito.eq(expected), Mockito.eq(CheckType.EDIT), Mockito.any(SecurityAuth.class));


      // queue
      final SimpleString queueName = SimpleString.of("queueName");
      Mockito.clearInvocations(securityStore);

      Queue queue = Mockito.mock(Queue.class);
      Mockito.when(queue.getName()).thenReturn(queueName);
      Mockito.when(queue.getRoutingType()).thenReturn(RoutingType.ANYCAST);

      StorageManager storageManager = Mockito.mock(StorageManager.class);
      managementService.registerQueue(queue, queueName, storageManager);
      managementService.getAttribute(ResourceNames.QUEUE + queueName, "ringSize", auth);

      expected = SimpleString.of("mm.queue." + queueName + ".getRingSize");
      Mockito.verify(securityStore).check(Mockito.eq(expected), Mockito.eq(CheckType.VIEW), Mockito.any(SecurityAuth.class));

      Mockito.clearInvocations(securityStore);
      managementService.getAttribute(ResourceNames.QUEUE + queueName, "ID", auth);

      expected = SimpleString.of("mm.queue." + queueName + ".getID");
      Mockito.verify(securityStore).check(Mockito.eq(expected), Mockito.eq(CheckType.VIEW), Mockito.any(SecurityAuth.class));

   }

   @Test
   public void testInvokeSecurityCheck() throws Exception {

      Configuration configuration = new FileConfiguration();
      configuration.setManagementMessageRbac(true);
      configuration.setManagementRbacPrefix("$mm");

      ManagementServiceImpl managementService = new ManagementServiceImpl(mBeanServer, configuration);

      Mockito.when(executorFactory.getExecutor()).thenReturn(artemisExecutor);
      Mockito.when(messagingServer.getExecutorFactory()).thenReturn(executorFactory);
      Mockito.when(messagingServer.getManagementService()).thenReturn(managementService);
      Mockito.when(postOffice.isStarted()).thenReturn(true);
      Mockito.when(messagingServer.getPostOffice()).thenReturn(postOffice);

      managementService.registerServer(null, securityStore, null, configuration, null, null, null, null, messagingServer, null, null, null, false);

      // acceptor
      Mockito.clearInvocations(securityStore);

      Acceptor acceptor = Mockito.mock(Acceptor.class);
      TransportConfiguration transportConfig = Mockito.mock(TransportConfiguration.class);
      Mockito.when(transportConfig.getName()).thenReturn("a1");

      managementService.registerAcceptor(acceptor, transportConfig);
      managementService.invokeOperation(ResourceNames.ACCEPTOR + transportConfig.getName(), "getName", new Object[]{}, auth);

      SimpleString expected = SimpleString.of("$mm.acceptor.a1.getName");
      Mockito.verify(securityStore).check(Mockito.eq(expected), Mockito.eq(CheckType.VIEW), Mockito.any(SecurityAuth.class));

      // address
      final String addressName = "addr1";
      Mockito.clearInvocations(securityStore);

      managementService.registerAddress(new AddressInfo(addressName));
      managementService.invokeOperation(ResourceNames.ADDRESS + addressName, "getAddress", new Object[]{}, auth);

      expected = SimpleString.of("$mm.address." + addressName + ".getAddress");
      Mockito.verify(securityStore).check(Mockito.eq(expected), Mockito.eq(CheckType.VIEW), Mockito.any(SecurityAuth.class));

      // queue
      final SimpleString queueName = SimpleString.of("queueName");
      Mockito.clearInvocations(securityStore);

      Queue queue = Mockito.mock(Queue.class);
      Mockito.when(queue.getName()).thenReturn(queueName);
      Mockito.when(queue.getRoutingType()).thenReturn(RoutingType.ANYCAST);

      StorageManager storageManager = Mockito.mock(StorageManager.class);
      managementService.registerQueue(queue, queueName, storageManager);
      managementService.invokeOperation(ResourceNames.QUEUE + queueName, "getRingSize", new Object[]{}, auth);

      expected = SimpleString.of("$mm.queue." + queueName + ".getRingSize");

      Mockito.verify(securityStore).check(Mockito.eq(expected), Mockito.eq(CheckType.VIEW), Mockito.any(SecurityAuth.class));

      // update permission required on pause operation
      Mockito.clearInvocations(securityStore);
      managementService.invokeOperation(ResourceNames.QUEUE + queueName, "pause", new Object[]{}, auth);
      expected = SimpleString.of("$mm.queue." + queueName + ".pause");
      Mockito.verify(securityStore).check(Mockito.eq(expected), Mockito.eq(CheckType.EDIT), Mockito.any(SecurityAuth.class));

   }

   @Test
   public void testBroadcasterNullWhenJMXNotificationDisabled() throws Exception {
      Field broadcasterField = ManagementServiceImpl.class.getDeclaredField("broadcaster");
      broadcasterField.setAccessible(true);

      FileConfiguration enabledConfig = new FileConfiguration();
      enabledConfig.setJMXNotificationEnabled(true);
      ManagementServiceImpl enabledService = new ManagementServiceImpl(mBeanServer, enabledConfig);
      assertNotNull(broadcasterField.get(enabledService));

      FileConfiguration disabledConfig = new FileConfiguration();
      disabledConfig.setJMXNotificationEnabled(false);
      ManagementServiceImpl disabledService = new ManagementServiceImpl(mBeanServer, disabledConfig);
      assertNull(broadcasterField.get(disabledService));
   }

   @Test
   public void testGetAttributeNoSecurityCheck() throws Exception {

      Configuration configuration = new FileConfiguration();
      ManagementServiceImpl managementService = new ManagementServiceImpl(mBeanServer, configuration);

      Mockito.when(executorFactory.getExecutor()).thenReturn(artemisExecutor);
      Mockito.when(messagingServer.getExecutorFactory()).thenReturn(executorFactory);
      Mockito.when(messagingServer.getManagementService()).thenReturn(managementService);
      Mockito.when(postOffice.isStarted()).thenReturn(true);
      Mockito.when(messagingServer.getPostOffice()).thenReturn(postOffice);

      managementService.registerServer(null, securityStore, null, configuration, null, null, null, null, messagingServer, null, null, null, false);

      // acceptor
      Mockito.clearInvocations(securityStore);
      Acceptor acceptor = Mockito.mock(Acceptor.class);
      TransportConfiguration transportConfig = Mockito.mock(TransportConfiguration.class);
      Mockito.when(transportConfig.getName()).thenReturn("a1");

      managementService.registerAcceptor(acceptor, transportConfig);
      managementService.getAttribute(ResourceNames.ACCEPTOR + transportConfig.getName(), "name", auth);

      Mockito.verifyNoInteractions(securityStore);
   }

   @Test
   public void testGetResourcesWithNullHawtioSecurity() throws Exception {
      Configuration configuration = new FileConfiguration();
      ManagementServiceImpl managementService = new ManagementServiceImpl(mBeanServer, configuration);

      Mockito.when(executorFactory.getExecutor()).thenReturn(artemisExecutor);
      Mockito.when(messagingServer.getExecutorFactory()).thenReturn(executorFactory);
      Mockito.when(messagingServer.getManagementService()).thenReturn(managementService);
      Mockito.when(postOffice.isStarted()).thenReturn(true);
      Mockito.when(messagingServer.getPostOffice()).thenReturn(postOffice);

      managementService.registerServer(null, securityStore, null, configuration, null, null, null, null, messagingServer, null, null, null, false);

      // HawtioSecurity is not registered - getResources must not throw NPE
      Object[] resources = managementService.getResources(HawtioSecurityControl.class);
      assertEquals(0, resources.length);
   }

   @Test
   public void testGetResourcesWithHawtioSecurity() throws Exception {
      Configuration configuration = new FileConfiguration();
      ManagementServiceImpl managementService = new ManagementServiceImpl(mBeanServer, configuration);

      Mockito.when(executorFactory.getExecutor()).thenReturn(artemisExecutor);
      Mockito.when(messagingServer.getExecutorFactory()).thenReturn(executorFactory);
      Mockito.when(messagingServer.getManagementService()).thenReturn(managementService);
      Mockito.when(postOffice.isStarted()).thenReturn(true);
      Mockito.when(messagingServer.getPostOffice()).thenReturn(postOffice);

      managementService.registerServer(null, securityStore, null, configuration, null, null, null, null, messagingServer, null, null, null, false);

      GuardInvocationHandler guard = Mockito.mock(GuardInvocationHandler.class);
      managementService.registerHawtioSecurity(guard);

      Object[] resources = managementService.getResources(HawtioSecurityControl.class);
      assertEquals(1, resources.length);
      assertInstanceOf(HawtioSecurityControl.class, resources[0]);
   }
}