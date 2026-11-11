/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageBufferTest extends ActiveMQTestBase {

   protected ActiveMQServer server;
   protected ClientSession session;
   protected ClientSessionFactory sf;
   protected ServerLocator locator;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(false, createDefaultInVMConfig());
      server.start();
      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
      session = addClientSession(sf.createSession(false, true, true));
   }

   @Test
   public void simpleTest() throws Exception {
      final String data = "Simple Text " + UUID.randomUUID().toString();
      final String queueName = "simpleQueue";
      final String addressName = "simpleAddress";

      session.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST));
      ClientProducer producer = session.createProducer(addressName);

      ClientMessageImpl message = (ClientMessageImpl) session.createMessage(true);
      message.getBodyBuffer().writeString(data);

      for (int i = 0; i < 100; i++) {
         message.putStringProperty("key", "int" + i);
         // JMS layer will always call this before sending
         message.getBodyBuffer().resetReaderIndex();
         producer.send(message);
         session.commit();
         assertTrue(message.getBodySize() < 1000, "Message body growing indefinitely and unexpectedly");

      }

      producer.send(message);
      producer.close();
      ClientConsumer consumer = session.createConsumer(queueName);
      session.start();

      assertNotNull(message);
      message.acknowledge();
      assertEquals(data, message.getBodyBuffer().readString());
   }

   @Test
   public void simpleTestBytes() throws Exception {
      ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.buffer(1500);
      for (int i = 0; i < 1024; i++) {
         buf.writeByte(getSamplebyte(i));
      }
      final String queueName = "simpleQueue";
      final String addressName = "simpleAddress";

      session.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST));
      ClientProducer producer = session.createProducer(addressName);

      {
         ClientMessage message = session.createMessage(true);
         assertEquals(1024, buf.readableBytes());
         message.getBodyBuffer().writeBytes(buf, 0, buf.readableBytes());
         producer.send(message);
      }

      session.commit();

      producer.close();

      ClientConsumer consumer = session.createConsumer(queueName);
      session.start();

      {
         ClientMessage message = consumer.receive(5000);
         assertNotNull(message);
         assertEquals(1024, message.getBodySize());
         ActiveMQBuffer buffer = message.getDataBuffer();
         assertEquals(1024, message.getBodySize());
         ActiveMQBuffer bodyBuffer = message.getBodyBuffer();
         assertEquals(1024, message.getBodySize());

         ActiveMQBuffer result = ActiveMQBuffers.fixedBuffer(message.getBodyBufferSize());
         buffer.readBytes(result);
         assertEquals(1024, result.readableBytes());
         for (int i = 0; i < 1024; i++) {
            assertEquals(getSamplebyte(i), result.readByte());
         }
         assertNotNull(message);
         message.acknowledge();
      }
   }
}
