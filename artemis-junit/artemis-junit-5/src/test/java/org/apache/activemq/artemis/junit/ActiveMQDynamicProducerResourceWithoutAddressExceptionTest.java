/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.junit;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class ActiveMQDynamicProducerResourceWithoutAddressExceptionTest {

   static final SimpleString TEST_QUEUE_ONE = SimpleString.of("test.queue.one");
   static final String TEST_BODY = "Test Message";
   static final Map<String, Object> TEST_PROPERTIES;

   static {
      TEST_PROPERTIES = new HashMap<String, Object>(2);
      TEST_PROPERTIES.put("PropertyOne", "Property Value 1");
      TEST_PROPERTIES.put("PropertyTwo", "Property Value 2");
   }

   @RegisterExtension
   @Order(1)
   public static EmbeddedActiveMQExtension server = new EmbeddedActiveMQExtension();

   @RegisterExtension
   @Order(2)
   public static ActiveMQDynamicProducerExtension producer = new ActiveMQDynamicProducerExtension(server.getVmURL());

   @BeforeAll
   public void setUp() throws Exception {
      producer.setAutoCreateQueue(false);
      server.createQueue(TEST_QUEUE_ONE, TEST_QUEUE_ONE);
   }

   @Test
   public void testSendBytesToDefaultAddress() {
      assertThrows(IllegalArgumentException.class, () -> {
         producer.sendMessage(TEST_BODY.getBytes());
      });
   }

   @Test
   public void testSendStringToDefaultAddress() {
      assertThrows(IllegalArgumentException.class, () -> {
         producer.sendMessage(TEST_BODY);
      });
   }

   @Test
   public void testSendBytesAndPropertiesToDefaultAddress() {
      assertThrows(IllegalArgumentException.class, () -> {
         producer.sendMessage(TEST_BODY.getBytes(), TEST_PROPERTIES);
      });
   }

   @Test
   public void testSendStringAndPropertiesToDefaultAddress() {
      assertThrows(IllegalArgumentException.class, () -> {
         producer.sendMessage(TEST_BODY, TEST_PROPERTIES);
      });
   }
}
