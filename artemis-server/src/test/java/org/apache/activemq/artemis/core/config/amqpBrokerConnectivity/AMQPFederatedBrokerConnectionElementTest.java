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

import org.junit.jupiter.api.Test;

/**
 * Test for the API of AMQPFederatedBrokerConnectionElement
 */
public class AMQPFederatedBrokerConnectionElementTest {

   @Test
   public void testEquals() {
      AMQPFederatedBrokerConnectionElement element1 = new AMQPFederatedBrokerConnectionElement();
      AMQPFederatedBrokerConnectionElement element2 = new AMQPFederatedBrokerConnectionElement();

      // Properties
      element1.addProperty("test", "test");
      assertNotEquals(element1, element2);
      element2.addProperty("test", "test");
      assertEquals(element1, element2);

      AMQPFederationAddressPolicyElement addressPolicy = new AMQPFederationAddressPolicyElement();
      addressPolicy.addToIncludes("test");

      // Local Address policy
      element1.addLocalAddressPolicy(addressPolicy);
      assertNotEquals(element1, element2);
      element2.addLocalAddressPolicy(addressPolicy);
      assertEquals(element1, element2);

      // Remote Address policy
      element1.addRemoteAddressPolicy(addressPolicy);
      assertNotEquals(element1, element2);
      element2.addRemoteAddressPolicy(addressPolicy);
      assertEquals(element1, element2);

      AMQPFederationQueuePolicyElement queuePolicy = new AMQPFederationQueuePolicyElement();
      queuePolicy.addToExcludes("test", "test");

      // Local Queue policy
      element1.addLocalQueuePolicy(queuePolicy);
      assertNotEquals(element1, element2);
      element2.addLocalQueuePolicy(queuePolicy);
      assertEquals(element1, element2);

      // Remote Queue policy
      element1.addRemoteQueuePolicy(queuePolicy);
      assertNotEquals(element1, element2);
      element2.addRemoteQueuePolicy(queuePolicy);
      assertEquals(element1, element2);
   }

   @Test
   public void testHashCode() {
      AMQPFederatedBrokerConnectionElement element1 = new AMQPFederatedBrokerConnectionElement();
      AMQPFederatedBrokerConnectionElement element2 = new AMQPFederatedBrokerConnectionElement();

      // Properties
      element1.addProperty("test", "value");
      assertNotEquals(element1.hashCode(), element2.hashCode());
      element2.addProperty("test", "value");
      assertEquals(element1.hashCode(), element2.hashCode());

      AMQPFederationAddressPolicyElement addressPolicy = new AMQPFederationAddressPolicyElement();
      addressPolicy.addToIncludes("test");

      // Local Address policy
      element1.addLocalAddressPolicy(addressPolicy);
      assertNotEquals(element1.hashCode(), element2.hashCode());
      element2.addLocalAddressPolicy(addressPolicy);
      assertEquals(element1.hashCode(), element2.hashCode());

      // Remote Address policy
      element1.addRemoteAddressPolicy(addressPolicy);
      assertNotEquals(element1.hashCode(), element2.hashCode());
      element2.addRemoteAddressPolicy(addressPolicy);
      assertEquals(element1.hashCode(), element2.hashCode());

      AMQPFederationQueuePolicyElement queuePolicy = new AMQPFederationQueuePolicyElement();
      queuePolicy.addToExcludes("test", "value");

      // Local Queue policy
      element1.addLocalQueuePolicy(queuePolicy);
      assertNotEquals(element1.hashCode(), element2.hashCode());
      element2.addLocalQueuePolicy(queuePolicy);
      assertEquals(element1.hashCode(), element2.hashCode());

      // Remote Queue policy
      element1.addRemoteQueuePolicy(queuePolicy);
      assertNotEquals(element1.hashCode(), element2.hashCode());
      element2.addRemoteQueuePolicy(queuePolicy);
      assertEquals(element1.hashCode(), element2.hashCode());
   }
}
