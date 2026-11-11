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
import org.junit.jupiter.api.BeforeAll;
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
public class ActiveMQDynamicProducerResourceWithoutAddressTest {

   static final SimpleString TEST_QUEUE_ONE = SimpleString.of("test.queue.one");
   static final SimpleString TEST_QUEUE_TWO = SimpleString.of("test.queue.two");
   static final String TEST_BODY = "Test Message";
   static final Map<String, Object> TEST_PROPERTIES;

   static final String ASSERT_SENT_FORMAT = "Message should have been sent to %s";
   static final String ASSERT_RECEIVED_FORMAT = "Message should have been received from %s";
   static final String ASSERT_COUNT_FORMAT = "Unexpected message count in queue %s";

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
   public void setUp() {
      producer.setAutoCreateQueue(false);
      server.createQueue(TEST_QUEUE_ONE, TEST_QUEUE_ONE);
      server.createQueue(TEST_QUEUE_TWO, TEST_QUEUE_TWO);
   }

   @Test
   public void testSendBytes() {
      final ClientMessage sentOne = producer.sendMessage(TEST_QUEUE_ONE, TEST_BODY.getBytes());
      final ClientMessage sentTwo = producer.sendMessage(TEST_QUEUE_TWO, TEST_BODY.getBytes());

      assertNotNull(sentOne, String.format(ASSERT_SENT_FORMAT, TEST_QUEUE_ONE));
      assertNotNull(sentTwo, String.format(ASSERT_SENT_FORMAT, TEST_QUEUE_TWO));

      {
         final ClientMessage received = server.receiveMessage(TEST_QUEUE_ONE);
         assertNotNull(received, String.format(ASSERT_RECEIVED_FORMAT, TEST_QUEUE_ONE));
         final ActiveMQBuffer receuvedBuffer = received.getReadOnlyBodyBuffer();
         final byte[] receivedBody = new byte[receuvedBuffer.readableBytes()];
         receuvedBuffer.readBytes(receivedBody);
         assertArrayEquals(TEST_BODY.getBytes(), receivedBody);
      }
      {
         final ClientMessage received = server.receiveMessage(TEST_QUEUE_TWO);
         assertNotNull(received, String.format(ASSERT_RECEIVED_FORMAT, TEST_QUEUE_TWO));
         final ActiveMQBuffer receivedBuffer = received.getReadOnlyBodyBuffer();
         final byte[] receivedBody = new byte[receivedBuffer.readableBytes()];
         receivedBuffer.readBytes(receivedBody);
         assertArrayEquals(TEST_BODY.getBytes(), receivedBody);
      }
   }

   @Test
   public void testSendString() {
      final ClientMessage sentOne = producer.sendMessage(TEST_QUEUE_ONE, TEST_BODY);
      final ClientMessage sentTwo = producer.sendMessage(TEST_QUEUE_TWO, TEST_BODY);

      assertNotNull(sentOne, String.format(ASSERT_SENT_FORMAT, TEST_QUEUE_ONE));
      assertNotNull(sentTwo, String.format(ASSERT_SENT_FORMAT, TEST_QUEUE_TWO));

      {
         final ClientMessage received = server.receiveMessage(TEST_QUEUE_ONE);
         assertNotNull(received, String.format(ASSERT_RECEIVED_FORMAT, TEST_QUEUE_ONE));
         assertEquals(TEST_BODY, received.getReadOnlyBodyBuffer().readString());
      }
      {
         final ClientMessage received = server.receiveMessage(TEST_QUEUE_TWO);
         assertNotNull(received, String.format(ASSERT_RECEIVED_FORMAT, TEST_QUEUE_TWO));
         assertEquals(TEST_BODY, received.getReadOnlyBodyBuffer().readString());
      }
   }

   @Test
   public void testSendBytesAndProperties() {
      final ClientMessage sentOne = producer.sendMessage(TEST_QUEUE_ONE, TEST_BODY.getBytes(), TEST_PROPERTIES);
      final ClientMessage sentTwo = producer.sendMessage(TEST_QUEUE_TWO, TEST_BODY.getBytes(), TEST_PROPERTIES);

      assertNotNull(sentOne, String.format(ASSERT_SENT_FORMAT, TEST_QUEUE_ONE));
      assertNotNull(sentTwo, String.format(ASSERT_SENT_FORMAT, TEST_QUEUE_TWO));

      {
         final ClientMessage received = server.receiveMessage(TEST_QUEUE_ONE);
         assertNotNull(received, String.format(ASSERT_RECEIVED_FORMAT, TEST_QUEUE_ONE));
         final ActiveMQBuffer receivedBuffer = received.getReadOnlyBodyBuffer();
         final byte[] receivedBody = new byte[receivedBuffer.readableBytes()];
         receivedBuffer.readBytes(receivedBody);
         assertArrayEquals(TEST_BODY.getBytes(), receivedBody);

         TEST_PROPERTIES.forEach((k, v) -> {
            assertTrue(received.containsProperty(k));
            assertEquals(v, received.getStringProperty(k));
         });
      }
      {
         final ClientMessage received = server.receiveMessage(TEST_QUEUE_TWO);
         assertNotNull(received, String.format(ASSERT_RECEIVED_FORMAT, TEST_QUEUE_TWO));
         final ActiveMQBuffer receivedBuffer = received.getReadOnlyBodyBuffer();
         final byte[] receivedBody = new byte[receivedBuffer.readableBytes()];
         receivedBuffer.readBytes(receivedBody);
         assertArrayEquals(TEST_BODY.getBytes(), receivedBody);

         TEST_PROPERTIES.forEach((k, v) -> {
            assertTrue(received.containsProperty(k));
            assertEquals(v, received.getStringProperty(k));
         });
      }
   }

   @Test
   public void testSendStringAndProperties() {
      final ClientMessage sentOne = producer.sendMessage(TEST_QUEUE_ONE, TEST_BODY, TEST_PROPERTIES);
      final ClientMessage sentTwo = producer.sendMessage(TEST_QUEUE_TWO, TEST_BODY, TEST_PROPERTIES);

      assertNotNull(sentOne, String.format(ASSERT_SENT_FORMAT, TEST_QUEUE_ONE));
      assertNotNull(sentTwo, String.format(ASSERT_SENT_FORMAT, TEST_QUEUE_TWO));

      {
         final ClientMessage received = server.receiveMessage(TEST_QUEUE_ONE);
         assertNotNull(received, String.format(ASSERT_RECEIVED_FORMAT, TEST_QUEUE_ONE));
         assertEquals(TEST_BODY, received.getReadOnlyBodyBuffer().readString());

         TEST_PROPERTIES.forEach((k, v) -> {
            assertTrue(received.containsProperty(k));
            assertEquals(v, received.getStringProperty(k));
         });
      }
      {
         final ClientMessage received = server.receiveMessage(TEST_QUEUE_TWO);
         assertNotNull(received, String.format(ASSERT_RECEIVED_FORMAT, TEST_QUEUE_TWO));
         assertEquals(TEST_BODY, received.getReadOnlyBodyBuffer().readString());

         TEST_PROPERTIES.forEach((k, v) -> {
            assertTrue(received.containsProperty(k));
            assertEquals(v, received.getStringProperty(k));
         });
      }
   }
}
