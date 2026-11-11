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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AutoDeleteCreatedQueueTest extends ActiveMQTestBase {

   public final SimpleString addressA = SimpleString.of("addressA");
   public final SimpleString queueA = SimpleString.of("queueA");
   public final SimpleString queueConfigurationManaged = SimpleString.of("queueConfigurationManaged");

   private ServerLocator locator;
   private ActiveMQServer server;
   private ClientSessionFactory cf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = createInVMNonHALocator();
      server = createServer(false);
      server.getConfiguration().setAddressQueueScanPeriod(500);
      server.getConfiguration().setMessageExpiryScanPeriod(500);

      server.start();
      cf = createSessionFactory(locator);
   }

   @Test
   public void testAutoDeleteCreatedQueueOnLastConsumerClose() throws Exception {
      server.getAddressSettingsRepository().addMatch(addressA.toString(), new AddressSettings().setAutoDeleteCreatedQueues(true));
      server.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setRoutingType(RoutingType.ANYCAST).setAutoCreated(false));
      assertNotNull(server.locateQueue(queueA));
      assertTrue(server.locateQueue(queueA).isAutoDelete());
      cf.createSession().createConsumer(queueA).close();
      Wait.assertTrue(() -> server.locateQueue(queueA) == null);
   }


   @Test
   public void testAutoDeleteCreatedQueueDoesNOTDeleteConfigurationManagedQueuesOnLastConsumerClose() throws Exception {
      server.getAddressSettingsRepository().addMatch(addressA.toString(), new AddressSettings().setAutoDeleteCreatedQueues(true));

      server.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setRoutingType(RoutingType.MULTICAST).setAutoCreated(false));
      server.createQueue(QueueConfiguration.of(queueConfigurationManaged).setAddress(addressA).setRoutingType(RoutingType.MULTICAST).setAutoCreated(false).setConfigurationManaged(true));
      assertNotNull(server.locateQueue(queueA));
      assertNotNull(server.locateQueue(queueConfigurationManaged));
      assertTrue(server.locateQueue(queueA).isAutoDelete());
      assertFalse(server.locateQueue(queueConfigurationManaged).isAutoDelete());
      cf.createSession().createConsumer(queueA).close();
      cf.createSession().createConsumer(queueConfigurationManaged).close();
      //Make sure the reaper has run by checking the queueA should be removed.
      Wait.assertTrue(() -> server.locateQueue(queueA) == null);
      //Check that our configuration managed queue is not removed.
      assertNotNull(server.locateQueue(queueConfigurationManaged));
   }
}
