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

public class AMQPFederationAddressPolicyElementTest {

   @Test
   public void testEqualsAndHashCode() {
      AMQPFederationAddressPolicyElement config1 = new AMQPFederationAddressPolicyElement();
      AMQPFederationAddressPolicyElement config2 = new AMQPFederationAddressPolicyElement();

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

      // Auto Delete
      config1.setAutoDelete(true);
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setAutoDelete(true);
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Auto Delete Delay
      config1.setAutoDeleteDelay(10L);
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setAutoDeleteDelay(10L);
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Auto Delete Message Count
      config1.setAutoDeleteMessageCount(20L);
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setAutoDeleteMessageCount(20L);
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Divert Bindings
      config1.setEnableDivertBindings(true);
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setEnableDivertBindings(true);
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Max Hops
      config1.setMaxHops(10);
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setMaxHops(10);
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Allow receivers to nest under remote wildcard addresses
      config1.setAllowWildcardGroupings(true);
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setAllowWildcardGroupings(true);
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());
   }
}
