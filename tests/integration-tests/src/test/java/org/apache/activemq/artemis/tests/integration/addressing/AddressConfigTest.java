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

package org.apache.activemq.artemis.tests.integration.addressing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AddressConfigTest extends ActiveMQTestBase {

   protected ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      Configuration configuration = createDefaultInVMConfig();
      server = createServer(true, configuration);
      server.start();
   }

   @Test
   public void persistAddressConfigTest() throws Exception {
      server.createQueue(QueueConfiguration.of("myQueue").setAddress("myAddress"));
      server.stop();
      server.start();
      AddressInfo addressInfo = server.getAddressInfo(SimpleString.of("myAddress"));
      assertNotNull(addressInfo);

      Set<RoutingType> routingTypeSet = new HashSet<>();
      routingTypeSet.add(RoutingType.MULTICAST);
      assertEquals(routingTypeSet, addressInfo.getRoutingTypes());
      assertFalse(addressInfo.isPaused());

      addressInfo.setPostOffice(server.getPostOffice());
      addressInfo.setStorageManager(server.getStorageManager());
      addressInfo.pause(true);
      assertTrue(addressInfo.isPaused());
      long id = addressInfo.getId();
      server.stop();
      server.start();
      addressInfo = server.getAddressInfo(SimpleString.of("myAddress"));
      assertNotNull(addressInfo);
      routingTypeSet = new HashSet<>();
      routingTypeSet.add(RoutingType.MULTICAST);
      assertEquals(routingTypeSet, addressInfo.getRoutingTypes());
      assertEquals(id, addressInfo.getId());
      assertTrue(addressInfo.isPaused());
      addressInfo.setPostOffice(server.getPostOffice());
      addressInfo.setStorageManager(server.getStorageManager());
      addressInfo.resume();
   }
}
