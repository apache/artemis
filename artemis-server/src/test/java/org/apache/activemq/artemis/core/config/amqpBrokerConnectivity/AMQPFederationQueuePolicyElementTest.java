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

public class AMQPFederationQueuePolicyElementTest {

   @Test
   public void testEqualsAndHashCode() {
      AMQPFederationQueuePolicyElement config1 = new AMQPFederationQueuePolicyElement();
      AMQPFederationQueuePolicyElement config2 = new AMQPFederationQueuePolicyElement();

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
      config1.addToIncludes("address1", "queue1");
      config1.addToIncludes("address2", "queue2");
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.addToIncludes("address1", "queue1");
      config2.addToIncludes("address2", "queue2");
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Excludes
      config1.addToExcludes("address10", "queue10");
      config1.addToExcludes("address20", "queue20");
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.addToExcludes("address10", "queue10");
      config2.addToExcludes("address20", "queue20");
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Name
      config1.setName("test");
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setName("test");
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Priority Adjustment
      config1.setPriorityAdjustment(-2);
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setPriorityAdjustment(-2);
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Include Federated
      config1.setIncludeFederated(true);
      assertNotEquals(config1, config2);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setIncludeFederated(true);
      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());
   }
}
