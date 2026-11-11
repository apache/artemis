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
 * Test basic API functionality of the AMQPBrokerConnectionElement type
 */
public class AMQPBrokerConnectionElementTest {

   @Test
   public void testEquals() {
      AMQPBrokerConnectionElement element1 = new AMQPBrokerConnectionElement();
      AMQPBrokerConnectionElement element2 = new AMQPBrokerConnectionElement();

      assertEquals(element1, element2);

      // Name
      element1.setName("test");
      assertNotEquals(element1, element2);
      element2.setName("test");
      assertEquals(element1, element2);

      // Match Address
      element1.setMatchAddress("test");
      assertNotEquals(element1, element2);
      element2.setMatchAddress("test");
      assertEquals(element1, element2);

      // Queue Name
      element1.setQueueName("test");
      assertNotEquals(element1, element2);
      element2.setQueueName("test");
      assertEquals(element1, element2);

      // Type
      element1.setType(AMQPBrokerConnectionAddressType.MIRROR);
      assertNotEquals(element1, element2);
      element2.setType(AMQPBrokerConnectionAddressType.MIRROR);
      assertEquals(element1, element2);
   }

   @Test
   public void testHashCode() {
      AMQPBrokerConnectionElement element1 = new AMQPBrokerConnectionElement();
      AMQPBrokerConnectionElement element2 = new AMQPBrokerConnectionElement();

      assertEquals(element1.hashCode(), element2.hashCode());

      // Name
      element1.setName("test");
      assertNotEquals(element1.hashCode(), element2.hashCode());
      element2.setName("test");
      assertEquals(element1.hashCode(), element2.hashCode());

      // Match Address
      element1.setMatchAddress("test");
      assertNotEquals(element1.hashCode(), element2.hashCode());
      element2.setMatchAddress("test");
      assertEquals(element1.hashCode(), element2.hashCode());

      // Queue Name
      element1.setQueueName("test");
      assertNotEquals(element1.hashCode(), element2.hashCode());
      element2.setQueueName("test");
      assertEquals(element1.hashCode(), element2.hashCode());

      // Type
      element1.setType(AMQPBrokerConnectionAddressType.MIRROR);
      assertNotEquals(element1.hashCode(), element2.hashCode());
      element2.setType(AMQPBrokerConnectionAddressType.MIRROR);
      assertEquals(element1.hashCode(), element2.hashCode());

      // Parent is not considered when checking equals or configurations would
      // be unequal when loaded or reloaded.
      final AMQPBrokerConnectConfiguration parent = new AMQPBrokerConnectConfiguration();

      element1.setParent(parent);
      assertEquals(element1.hashCode(), element2.hashCode());
      assertEquals(element1, element2);
      element2.setParent(parent);
      assertEquals(element1.hashCode(), element2.hashCode());
      assertEquals(element1, element2);
   }
}
