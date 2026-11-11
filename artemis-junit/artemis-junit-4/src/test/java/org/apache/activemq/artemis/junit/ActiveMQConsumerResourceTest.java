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
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ActiveMQConsumerResourceTest {

   static final SimpleString TEST_QUEUE = SimpleString.of("test.queue");
   static final SimpleString TEST_ADDRESS = SimpleString.of("test.queue");
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

   EmbeddedActiveMQResource server = new EmbeddedActiveMQResource();

   ActiveMQConsumerResource consumer = new ActiveMQConsumerResource(server.getVmURL(), TEST_QUEUE);

   @Rule
   public RuleChain ruleChain = RuleChain.outerRule(server).around(consumer);

   @After
   public void tearDown() throws Exception {
      server.stop();
   }

   @Test
   public void testSendBytes() throws Exception {
      final ClientMessage sent = server.sendMessage(TEST_ADDRESS, TEST_BODY.getBytes());
      assertNotNull(String.format(ASSERT_SENT_FORMAT, TEST_ADDRESS), sent);

      final ClientMessage received = consumer.receiveMessage();
      assertNotNull(String.format(ASSERT_RECEIVED_FORMAT, TEST_ADDRESS), received);
      final ActiveMQBuffer body = received.getReadOnlyBodyBuffer();
      final byte[] receivedBody = new byte[body.readableBytes()];
      body.readBytes(receivedBody);
      assertArrayEquals(TEST_BODY.getBytes(), receivedBody);
   }

   @Test
   public void testSendString() throws Exception {
      final ClientMessage sent = server.sendMessage(TEST_ADDRESS, TEST_BODY);
      assertNotNull(String.format(ASSERT_SENT_FORMAT, TEST_ADDRESS), sent);

      final ClientMessage received = consumer.receiveMessage();
      assertNotNull(String.format(ASSERT_RECEIVED_FORMAT, TEST_ADDRESS), received);
      assertEquals(TEST_BODY, received.getReadOnlyBodyBuffer().readString());
   }

   @Test
   public void testSendBytesAndProperties() throws Exception {
      final ClientMessage sent = server.sendMessageWithProperties(TEST_ADDRESS, TEST_BODY.getBytes(), TEST_PROPERTIES);
      assertNotNull(String.format(ASSERT_SENT_FORMAT, TEST_ADDRESS), sent);

      final ClientMessage received = consumer.receiveMessage();
      assertNotNull(String.format(ASSERT_RECEIVED_FORMAT, TEST_ADDRESS), received);
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
   public void testSendStringAndProperties() throws Exception {
      final ClientMessage sent = server.sendMessageWithProperties(TEST_ADDRESS, TEST_BODY, TEST_PROPERTIES);
      assertNotNull(String.format(ASSERT_SENT_FORMAT, TEST_ADDRESS), sent);

      final ClientMessage received = consumer.receiveMessage();
      assertNotNull(String.format(ASSERT_RECEIVED_FORMAT, TEST_ADDRESS), received);
      assertEquals(TEST_BODY, received.getReadOnlyBodyBuffer().readString());

      TEST_PROPERTIES.forEach((k, v) -> {
         assertTrue(received.containsProperty(k));
         assertEquals(v, received.getStringProperty(k));
      });
   }
}
