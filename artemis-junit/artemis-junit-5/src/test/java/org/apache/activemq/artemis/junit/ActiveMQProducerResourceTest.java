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

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class ActiveMQProducerResourceTest {

   static final SimpleString TEST_QUEUE = SimpleString.of("test.queue");
   static final SimpleString TEST_ADDRESS = SimpleString.of("test.queue");
   static final String TEST_BODY = "Test Message";
   static final Map<String, Object> TEST_PROPERTIES;

   static final String ASSERT_SENT_FORMAT = "Message should have been sent to %s";
   static final String ASSERT_RECEIVED_FORMAT = "Message should have been received from %s";

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
   public static ActiveMQDynamicProducerExtension producer = new ActiveMQDynamicProducerExtension(server.getVmURL(), TEST_ADDRESS);

   @Test
   public void testSendBytes() {
      final ClientMessage sent = producer.sendMessage(TEST_BODY.getBytes());
      assertNotNull(sent, String.format(ASSERT_SENT_FORMAT, TEST_ADDRESS));

      final ClientMessage received = server.receiveMessage(TEST_QUEUE);
      assertNotNull(received, String.format(ASSERT_RECEIVED_FORMAT, TEST_QUEUE));
      final ActiveMQBuffer body = received.getReadOnlyBodyBuffer();
      final byte[] receivedBody = new byte[body.readableBytes()];
      body.readBytes(receivedBody);
      assertArrayEquals(TEST_BODY.getBytes(), receivedBody);
   }

   @Test
   public void testSendString() {
      final ClientMessage sent = producer.sendMessage(TEST_BODY);
      assertNotNull(sent, String.format(ASSERT_SENT_FORMAT, TEST_ADDRESS));

      final ClientMessage received = server.receiveMessage(TEST_QUEUE);
      assertNotNull(received, String.format(ASSERT_RECEIVED_FORMAT, TEST_QUEUE));
      assertEquals(TEST_BODY, received.getReadOnlyBodyBuffer().readString());
   }

   @Test
   public void testSendBytesAndProperties() {
      final ClientMessage sent = producer.sendMessage(TEST_BODY.getBytes(), TEST_PROPERTIES);
      assertNotNull(sent, String.format(ASSERT_SENT_FORMAT, TEST_ADDRESS));

      final ClientMessage received = server.receiveMessage(TEST_QUEUE);
      assertNotNull(received, String.format(ASSERT_RECEIVED_FORMAT, TEST_QUEUE));
      final ActiveMQBuffer body = received.getReadOnlyBodyBuffer();
      final byte[] receivedBody = new byte[body.readableBytes()];
      body.readBytes(receivedBody);
      assertArrayEquals(TEST_BODY.getBytes(), receivedBody);

      TEST_PROPERTIES.forEach((k, v) -> {
         assertTrue(received.containsProperty(k));
         assertEquals(v, received.getStringProperty(k));
      });
   }

   @Test
   public void testSendStringAndProperties() {
      final ClientMessage sent = producer.sendMessage(TEST_BODY, TEST_PROPERTIES);
      assertNotNull(sent, String.format(ASSERT_SENT_FORMAT, TEST_ADDRESS));

      final ClientMessage received = server.receiveMessage(TEST_QUEUE);
      assertNotNull(received, String.format(ASSERT_RECEIVED_FORMAT, TEST_QUEUE));
      assertEquals(TEST_BODY, received.getReadOnlyBodyBuffer().readString());

      TEST_PROPERTIES.forEach((k, v) -> {
         assertTrue(received.containsProperty(k));
         assertEquals(v, received.getStringProperty(k));
      });
   }
}
