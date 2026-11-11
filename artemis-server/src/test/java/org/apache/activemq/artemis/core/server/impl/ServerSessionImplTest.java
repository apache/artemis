/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.impl;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.ResourceManager;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServerSessionImplTest {

   @Test
   public void testCheckAutoCreateModifyExistingAddressInfo() throws Exception {
      ActiveMQServer server = setupMockServerForServerSession();
      final String NAME = RandomUtil.randomAlphaNumericString(10);

      // the routing-types for this AddressInfo object should not be modified by the checkAutoCreate method
      AddressInfo addressInfo = new AddressInfo(NAME).addRoutingType(RoutingType.ANYCAST);

      setupMocksForAddressStuff(server, addressInfo);

      ServerSessionImpl session = new ServerSessionImpl(null, null, null, null, 0, false, false, false, false, true, mock(RemotingConnection.class), server, null, null, null, null, null, false);

      QueueConfiguration queueConfiguration = QueueConfiguration.of(NAME).setRoutingType(RoutingType.MULTICAST);

      session.checkAutoCreate(queueConfiguration);

      ArgumentCaptor<AddressInfo> addressInfoCaptor = ArgumentCaptor.forClass(AddressInfo.class);
      verify(server).addOrUpdateAddressInfo(addressInfoCaptor.capture());
      assertTrue(addressInfoCaptor.getValue().getRoutingTypes().contains(RoutingType.MULTICAST));
      assertTrue(addressInfoCaptor.getValue().getRoutingTypes().contains(RoutingType.ANYCAST));

      // confirm that the routing-types were not modified
      assertFalse(addressInfo.getRoutingTypes().contains(RoutingType.MULTICAST));
      assertTrue(addressInfo.getRoutingTypes().contains(RoutingType.ANYCAST));
   }

   /*
    * Create mocks to return the desired AddressInfo and AddressSettings from the ActiveMQServer.
    */
   private void setupMocksForAddressStuff(ActiveMQServer server, AddressInfo addressInfo) {
      // return the desired AddressSettings
      HierarchicalRepository<AddressSettings> addressSettingsRepository = mock(HierarchicalRepository.class);
      when(server.getAddressSettingsRepository()).thenReturn(addressSettingsRepository);
      when(addressSettingsRepository.getMatch(addressInfo.getName().toString())).thenReturn(new AddressSettings().setAutoCreateAddresses(true));

      // return the desired AddressInfo
      when(server.getAddressInfo(addressInfo.getName())).thenReturn(addressInfo);
   }

   /*
    * Create a mock ActiveMQServer instance specifically to pass to the constructor of ServerSessionImpl.
    */
   private ActiveMQServer setupMockServerForServerSession() {
      ActiveMQServer server = mock(ActiveMQServer.class);

      when(server.getManagementService()).thenReturn(mock(ManagementService.class));
      when(server.getConfiguration()).thenReturn(mock(Configuration.class));
      when(server.getResourceManager()).thenReturn(mock(ResourceManager.class));
      when(server.getSecurityStore()).thenReturn(mock(SecurityStore.class));
      ExecutorFactory executorFactory = mock(ExecutorFactory.class);
      when(executorFactory.getExecutor()).thenReturn(mock(ArtemisExecutor.class));
      when(server.getExecutorFactory()).thenReturn(executorFactory);

      return server;
   }
}