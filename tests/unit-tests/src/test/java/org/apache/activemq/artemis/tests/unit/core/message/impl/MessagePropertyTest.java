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
package org.apache.activemq.artemis.tests.unit.core.message.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessagePropertyTest extends ActiveMQTestBase {

   private ActiveMQServer server;
   private ServerLocator locator;
   private ClientSessionFactory sf;
   private final int numMessages = 20;

   private static final String ADDRESS = "anAddress123";
   private static final SimpleString SIMPLE_STRING_KEY = SimpleString.of("StringToSimpleString");

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(true);
      server.start();
      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
   }

   private void sendMessages() throws Exception {
      ClientSession session = sf.createSession(true, true);

      String filter = null;
      session.createAddress(SimpleString.of(ADDRESS), RoutingType.MULTICAST, false);
      session.createQueue(QueueConfiguration.of(ADDRESS).setRoutingType(RoutingType.MULTICAST).setFilterString(filter));
      ClientProducer producer = session.createProducer(ADDRESS);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(true);
         setBody(i, message);
         message.putIntProperty("int", i);
         message.putShortProperty("short", (short) i);
         message.putByteProperty("byte", (byte) i);
         message.putFloatProperty("float", floatValue(i));
         message.putStringProperty(SIMPLE_STRING_KEY, SimpleString.of(Integer.toString(i)));
         message.putBytesProperty("byte[]", byteArray(i));
         message.putObjectProperty("null-value", null);
         producer.send(message);
      }
      session.commit();
   }

   private float floatValue(int i) {
      return (float) (i * 1.3);
   }

   private byte[] byteArray(int i) {
      return new byte[]{(byte) i, (byte) (i / 2)};
   }

   @Test
   public void testProperties() throws Exception {
      sendMessages();
      receiveMessages();
   }

   private void receiveMessages() throws Exception {
      ClientSession session = sf.createSession(true, true);
      session.start();
      try (ClientConsumer consumer = session.createConsumer(ADDRESS)) {
         for (int i = 0; i < numMessages; i++) {
            ClientMessage message = consumer.receive(100);
            assertNotNull(message, "Expecting a message " + i);
            assertMessageBody(i, message);
            assertEquals(i, message.getIntProperty("int").intValue());
            assertEquals((short) i, message.getShortProperty("short").shortValue());
            assertEquals((byte) i, message.getByteProperty("byte").byteValue());
            assertEquals(floatValue(i), message.getFloatProperty("float").floatValue(), 0.001);
            assertEquals(SimpleString.of(Integer.toString(i)), message.getSimpleStringProperty(SIMPLE_STRING_KEY.toString()));
            assertEqualsByteArrays(byteArray(i), message.getBytesProperty("byte[]"));
            assertNull(message.getIngressTimestamp());

            assertTrue(message.containsProperty("null-value"));
            assertNull(message.getObjectProperty("null-value"));

            message.acknowledge();
         }
         assertNull(consumer.receive(50), "no more messages");
      }
      session.commit();
   }

}
