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
package org.apache.activemq.artemis.tests.integration.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.management.DivertControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.transformer.AddHeadersTransformer;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DivertControlTest extends ManagementTestBase {


   private ActiveMQServer server;

   private DivertConfiguration divertConfig;



   @Test
   public void testAttributes() throws Exception {
      checkResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(divertConfig.getName(), divertConfig.getAddress()));

      DivertControl divertControl = createDivertManagementControl(divertConfig.getName(), divertConfig.getAddress());

      assertEquals(divertConfig.getFilterString(), divertControl.getFilter());

      assertEquals(divertConfig.isExclusive(), divertControl.isExclusive());

      assertEquals(divertConfig.getName(), divertControl.getUniqueName());

      assertEquals(divertConfig.getRoutingName(), divertControl.getRoutingName());

      assertEquals(divertConfig.getAddress(), divertControl.getAddress());

      assertEquals(divertConfig.getForwardingAddress(), divertControl.getForwardingAddress());

      assertEquals(divertConfig.getTransformerConfiguration().getClassName(), divertControl.getTransformerClassName());

      assertEquals(divertConfig.getTransformerConfiguration().getProperties(), divertControl.getTransformerProperties());
   }

   @Test
   public void testRetroactiveResourceAttribute() throws Exception {
      String address = RandomUtil.randomUUIDString();
      QueueConfiguration queueConfig = QueueConfiguration.of(RandomUtil.randomUUIDString()).setDurable(false);
      QueueConfiguration forwardQueueConfig = QueueConfiguration.of(RandomUtil.randomUUIDString()).setAddress(address).setDurable(false);

      divertConfig = new DivertConfiguration()
         .setName(ResourceNames.getRetroactiveResourceDivertName(server.getInternalNamingPrefix(), server.getConfiguration().getWildcardConfiguration().getDelimiterString(), SimpleString.of(address)).toString())
         .setRoutingName(RandomUtil.randomUUIDString()).setAddress(queueConfig.getAddress().toString())
         .setForwardingAddress(forwardQueueConfig.getAddress().toString())
         .setExclusive(RandomUtil.randomBoolean())
         .setTransformerConfiguration(new TransformerConfiguration(AddHeadersTransformer.class.getName()));

      server.deployDivert(divertConfig);

      checkResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(divertConfig.getName(), divertConfig.getAddress()));

      DivertControl divertControl = createDivertManagementControl(divertConfig.getName(), divertConfig.getAddress());

      assertTrue(divertControl.isRetroactiveResource());
   }



   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      QueueConfiguration queueConfig = QueueConfiguration.of(RandomUtil.randomUUIDString()).setDurable(false);
      QueueConfiguration forwardQueueConfig = QueueConfiguration.of(RandomUtil.randomUUIDString()).setDurable(false);

      divertConfig = new DivertConfiguration()
         .setName(RandomUtil.randomUUIDString())
         .setRoutingName(RandomUtil.randomUUIDString())
         .setAddress(queueConfig.getAddress().toString())
         .setForwardingAddress(forwardQueueConfig.getAddress().toString())
         .setExclusive(RandomUtil.randomBoolean())
         .setTransformerConfiguration(new TransformerConfiguration(AddHeadersTransformer.class.getName()));

      TransportConfiguration connectorConfig = new TransportConfiguration(INVM_CONNECTOR_FACTORY);

      Configuration config = createDefaultInVMConfig()
         .setJMXManagementEnabled(true)
         .addQueueConfiguration(queueConfig)
         .addQueueConfiguration(forwardQueueConfig)
         .addDivertConfiguration(divertConfig)
         .addConnectorConfiguration(connectorConfig.getName(), connectorConfig);

      server = addServer(ActiveMQServers.newActiveMQServer(config, mbeanServer, false));
      server.start();
   }

   protected DivertControl createDivertManagementControl(final String name, final String address) throws Exception {
      return ManagementControlHelper.createDivertControl(name, address, mbeanServer);
   }
}
