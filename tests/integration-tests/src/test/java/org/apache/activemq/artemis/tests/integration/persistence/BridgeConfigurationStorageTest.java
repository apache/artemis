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
package org.apache.activemq.artemis.tests.integration.persistence;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedBridgeConfiguration;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(ParameterizedTestExtension.class)
public class BridgeConfigurationStorageTest extends StorageManagerTestBase {

   @Parameters(name = "storeType={0}")
   public static Collection<Object[]> data() {
      Object[][] params = new Object[][]{{StoreConfiguration.StoreType.FILE}, {StoreConfiguration.StoreType.DATABASE}};
      return Arrays.asList(params);
   }

   public BridgeConfigurationStorageTest(StoreConfiguration.StoreType storeType) {
      super(storeType);
   }

   @TestTemplate
   public void testStoreBridgeConfiguration() throws Exception {
      TransformerConfiguration mytransformer = new TransformerConfiguration("mytransformer");
      mytransformer.getProperties().put("key1", "prop1");
      mytransformer.getProperties().put("key2", "prop2");
      mytransformer.getProperties().put("key3", "prop3");
      BridgeConfiguration configuration = new BridgeConfiguration()
         .setName("name")
         .setParentName("name")
         .setQueueName("QueueName")
         .setConcurrency(2)
         .setPendingAckTimeout(9876)
         .setForwardingAddress("forward")
         .setProducerWindowSize(123123)
         .setConfirmationWindowSize(123123)
         .setStaticConnectors(Arrays.asList("connector1", "connector2"))
         .setTransformerConfiguration(mytransformer)
         .setClientId("myClientID");

      journal.storeBridgeConfiguration(new PersistedBridgeConfiguration(configuration));

      rebootStorage();

      List<PersistedBridgeConfiguration> bridgeConfigurations = journal.recoverBridgeConfigurations();

      assertEquals(1, bridgeConfigurations.size());

      PersistedBridgeConfiguration persistedBridgeConfiguration = bridgeConfigurations.get(0);
      assertEquals(configuration.getName(), persistedBridgeConfiguration.getBridgeConfiguration().getName());
      assertEquals(configuration.getQueueName(), persistedBridgeConfiguration.getBridgeConfiguration().getQueueName());
      assertEquals(configuration.getConcurrency(), persistedBridgeConfiguration.getBridgeConfiguration().getConcurrency());
      assertEquals(configuration.getPendingAckTimeout(), persistedBridgeConfiguration.getBridgeConfiguration().getPendingAckTimeout());
      assertEquals(configuration.getForwardingAddress(), persistedBridgeConfiguration.getBridgeConfiguration().getForwardingAddress());
      assertEquals(configuration.getStaticConnectors(), persistedBridgeConfiguration.getBridgeConfiguration().getStaticConnectors());
      assertNotNull(persistedBridgeConfiguration.getBridgeConfiguration().getTransformerConfiguration());
      assertEquals("mytransformer", persistedBridgeConfiguration.getBridgeConfiguration().getTransformerConfiguration().getClassName());
      Map<String, String> properties = persistedBridgeConfiguration.getBridgeConfiguration().getTransformerConfiguration().getProperties();
      assertEquals(3, properties.size());
      assertEquals("prop1", properties.get("key1"));
      assertEquals("prop2", properties.get("key2"));
      assertEquals("prop3", properties.get("key3"));
      assertEquals(configuration.getClientId(), persistedBridgeConfiguration.getBridgeConfiguration().getClientId());
   }

   @TestTemplate
   public void testStoreBridgeConfigurationNoTransformer() throws Exception {
      BridgeConfiguration configuration = new BridgeConfiguration()
         .setName("name")
         .setParentName("name")
         .setQueueName("QueueName")
         .setConcurrency(2)
         .setForwardingAddress("forward")
         .setStaticConnectors(Arrays.asList("connector1", "connector2"));

      journal.storeBridgeConfiguration(new PersistedBridgeConfiguration(configuration));

      rebootStorage();

      List<PersistedBridgeConfiguration> bridgeConfigurations = journal.recoverBridgeConfigurations();

      assertEquals(1, bridgeConfigurations.size());

      PersistedBridgeConfiguration persistedBridgeConfiguration = bridgeConfigurations.get(0);
      assertEquals(configuration.getName(), persistedBridgeConfiguration.getBridgeConfiguration().getName());
      assertEquals(configuration.getQueueName(), persistedBridgeConfiguration.getBridgeConfiguration().getQueueName());
      assertEquals(configuration.getConcurrency(), persistedBridgeConfiguration.getBridgeConfiguration().getConcurrency());
      assertEquals(configuration.getForwardingAddress(), persistedBridgeConfiguration.getBridgeConfiguration().getForwardingAddress());
      assertEquals(configuration.getStaticConnectors(), persistedBridgeConfiguration.getBridgeConfiguration().getStaticConnectors());
      assertNull(persistedBridgeConfiguration.getBridgeConfiguration().getTransformerConfiguration());
   }
}
