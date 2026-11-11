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

package org.apache.activemq.artemis.core.config.amqpBrokerConnectivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Map;

import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.junit.Test;

public class AMQPBridgeAddressPolicyElementTest {

   @Test
   public void testEqualsAndHashCode() {
      AMQPBridgeAddressPolicyElement config1 = new AMQPBridgeAddressPolicyElement();
      AMQPBridgeAddressPolicyElement config2 = new AMQPBridgeAddressPolicyElement();

      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Transformer
      final TransformerConfiguration transformer = new TransformerConfiguration("foo");
      config1.setTransformerConfiguration(transformer);
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setTransformerConfiguration(transformer);
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Properties
      final Map<String, Object> properties = Map.of("property", "value");
      config1.setProperties(properties);
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setProperties(properties);
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Includes
      config1.addToIncludes("address1");
      config1.addToIncludes("address2");
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.addToIncludes("address1");
      config2.addToIncludes("address2");
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Excludes
      config1.addToExcludes("address10");
      config1.addToExcludes("address20");
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.addToExcludes("address10");
      config2.addToExcludes("address20");
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Name
      config1.setName("test");
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setName("test");
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Remote Address
      config1.setRemoteAddress("address");
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setRemoteAddress("address");
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Remote Address Prefix
      config1.setRemoteAddressPrefix("address-prefix");
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setRemoteAddressPrefix("address-prefix");
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Remote Address Suffix
      config1.setRemoteAddressSuffix("address-suffix");
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setRemoteAddressSuffix("address-suffix");
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Remote Terminus Capabilities
      final String[] capabilities = {"test"};
      config1.setRemoteTerminusCapabilities(capabilities);
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setRemoteTerminusCapabilities(capabilities);
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Divert Bindings
      config1.setIncludeDivertBindings(true);
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setIncludeDivertBindings(true);
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Use durable subscriptions
      config1.setUseDurableSubscriptions(true);
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setUseDurableSubscriptions(true);
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Priority
      config1.setPriority(2);
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setPriority(2);
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Filter
      config1.setFilter("color='red'");
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setFilter("color='red'");
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());
   }
}
