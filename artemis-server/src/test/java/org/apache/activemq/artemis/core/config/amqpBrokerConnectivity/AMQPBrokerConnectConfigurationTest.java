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
 * Tests for the AMQPBrokerConnectConfiguration type
 */
public class AMQPBrokerConnectConfigurationTest {

   @Test
   public void testEquals() {
      AMQPBrokerConnectConfiguration config1 = new AMQPBrokerConnectConfiguration();
      AMQPBrokerConnectConfiguration config2 = new AMQPBrokerConnectConfiguration();

      assertEquals(config1, config2);

      // Name
      config1.setName("test");
      assertNotEquals(config1, config2);
      config2.setName("test");
      assertEquals(config1, config2);

      // User
      config1.setUser("test");
      assertNotEquals(config1, config2);
      config2.setUser("test");
      assertEquals(config1, config2);

      // Password
      config1.setPassword("test");
      assertNotEquals(config1, config2);
      config2.setPassword("test");
      assertEquals(config1, config2);

      // Uri
      config1.setUri("test");
      assertNotEquals(config1, config2);
      config2.setUri("test");
      assertEquals(config1, config2);

      // Reconnect Attempts
      config1.setReconnectAttempts(1);
      assertNotEquals(config1, config2);
      config2.setReconnectAttempts(1);
      assertEquals(config1, config2);

      // Retry Interval
      config1.setRetryInterval(1);
      assertNotEquals(config1, config2);
      config2.setRetryInterval(1);
      assertEquals(config1, config2);

      // Auto start
      config1.setAutostart(false);
      assertNotEquals(config1, config2);
      config2.setAutostart(false);
      assertEquals(config1, config2);

      // Broker connection elements
      AMQPBrokerConnectionElement element = new AMQPBrokerConnectionElement();

      element.setName("test");

      config1.addElement(element);
      assertNotEquals(config1, config2);
      config2.addElement(element);
      assertEquals(config1, config2);
   }

   @Test
   public void testHashCode() {
      AMQPBrokerConnectConfiguration config1 = new AMQPBrokerConnectConfiguration();
      AMQPBrokerConnectConfiguration config2 = new AMQPBrokerConnectConfiguration();

      assertEquals(config1, config2);

      // Name
      config1.setName("test");
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setName("test");
      assertEquals(config1.hashCode(), config2.hashCode());

      // User
      config1.setUser("test");
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setUser("test");
      assertEquals(config1.hashCode(), config2.hashCode());

      // Password
      config1.setPassword("test");
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setPassword("test");
      assertEquals(config1.hashCode(), config2.hashCode());

      // Uri
      config1.setUri("test");
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setUri("test");
      assertEquals(config1.hashCode(), config2.hashCode());

      // Reconnect Attempts
      config1.setReconnectAttempts(1);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setReconnectAttempts(1);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Retry Interval
      config1.setRetryInterval(1);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setRetryInterval(1);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Auto start
      config1.setAutostart(false);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.setAutostart(false);
      assertEquals(config1.hashCode(), config2.hashCode());

      // Broker connection elements
      AMQPBrokerConnectionElement element = new AMQPBrokerConnectionElement();

      element.setName("test");

      config1.addElement(element);
      assertNotEquals(config1.hashCode(), config2.hashCode());
      config2.addElement(element);
      assertEquals(config1.hashCode(), config2.hashCode());
   }
}
