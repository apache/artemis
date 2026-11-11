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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InVMNonPersistentMessageBufferTest extends ActiveMQTestBase {

   public static final String address = "testaddress";

   public static final String queueName = "testqueue";

   private ActiveMQServer server;

   private ClientSession session;

   private ClientProducer producer;

   private ClientConsumer consumer;

   /*
    * Test message can be read after being sent
    * Message can be sent multiple times
    * After sending, local message can be read
    * After sending, local message body can be added to and sent
    * When reset message body it should only reset to after packet headers
    * Should not be able to read past end of body into encoded message
    */

   @Test
   public void testSimpleSendReceive() throws Exception {
      ClientMessage message = session.createMessage(false);

      final String body = RandomUtil.randomUUIDString();

      message.getBodyBuffer().writeString(body);

      ClientMessage received = sendAndReceive(message);

      assertNotNull(received);

      assertEquals(body, received.getBodyBuffer().readString());
   }

   @Test
   public void testSimpleSendReceiveWithEmptyBody() throws Exception {
      ClientMessage message = session.createMessage(false);

      ClientMessage received = sendAndReceive(message);

      assertNotNull(received);

      assertEquals(0, received.getBodySize());
   }

   @Test
   public void testSendSameMessageMultipleTimes() throws Exception {
      ClientMessage message = session.createMessage(false);

      final String body = RandomUtil.randomUUIDString();

      message.getBodyBuffer().writeString(body);

      int bodySize = message.getBodySize();

      for (int i = 0; i < 10; i++) {
         ClientMessage received = sendAndReceive(message);

         assertNotNull(received);

         assertEquals(bodySize, received.getBodySize());

         assertEquals(body, received.getBodyBuffer().readString());

         assertFalse(received.getBodyBuffer().readable());
      }
   }

   @Test
   public void testSendMessageResetSendAgainDifferentBody() throws Exception {
      ClientMessage message = session.createMessage(false);

      String body = RandomUtil.randomUUIDString();

      for (int i = 0; i < 10; i++) {
         // Make the body a bit longer each time
         body += "XX";

         message.getBodyBuffer().writeString(body);

         int bodySize = message.getBodySize();

         ClientMessage received = sendAndReceive(message);

         assertNotNull(received);

         assertEquals(bodySize, received.getBodySize());

         assertEquals(body, received.getBodyBuffer().readString());

         assertFalse(received.getBodyBuffer().readable());

         message.getBodyBuffer().clear();

         assertEquals(DataConstants.SIZE_INT, message.getBodyBuffer().writerIndex());

         assertEquals(DataConstants.SIZE_INT, message.getBodyBuffer().readerIndex());
      }
   }

   @Test
   public void testCannotReadPastEndOfMessageBody() throws Exception {
      ClientMessage message = session.createMessage(false);

      final String body = RandomUtil.randomUUIDString();

      message.getBodyBuffer().writeString(body);

      ClientMessage received = sendAndReceive(message);

      assertNotNull(received);

      ActiveMQBuffer buffer = received.getReadOnlyBodyBuffer();

      assertEquals(body, buffer.readString());

      try {
         buffer.readByte();
         fail("Should throw exception");
      } catch (IndexOutOfBoundsException e) {
         // OK
      }


      assertEquals(body, received.getBodyBuffer().readString());

      try {
         received.getBodyBuffer().readByte();

         fail("Should throw exception");
      } catch (IndexOutOfBoundsException e) {
         // OK
      }

      buffer = received.getReadOnlyBodyBuffer();

      assertEquals(body, buffer.readString());

      try {
         buffer.readByte();
         fail("Should throw exception");
      } catch (IndexOutOfBoundsException e) {
         // OK
      }

   }

   @Test
   public void testCanReReadBodyAfterReaderReset() throws Exception {
      ClientMessage message = session.createMessage(false);

      final String body = RandomUtil.randomUUIDString();

      message.getBodyBuffer().writeString(body);

      assertEquals(DataConstants.SIZE_INT, message.getBodyBuffer().readerIndex());

      String body2 = message.getBodyBuffer().readString();

      assertEquals(body, body2);

      message.getBodyBuffer().resetReaderIndex();

      assertEquals(DataConstants.SIZE_INT, message.getBodyBuffer().readerIndex());

      String body3 = message.getBodyBuffer().readString();

      assertEquals(body, body3);

      ClientMessage received = sendAndReceive(message);

      assertNotNull(received);

      assertEquals(body, received.getBodyBuffer().readString());

      received.getBodyBuffer().resetReaderIndex();

      assertEquals(DataConstants.SIZE_INT, received.getBodyBuffer().readerIndex());

      String body4 = received.getBodyBuffer().readString();

      assertEquals(body, body4);

   }

   protected ServerLocator createFactory() throws Exception {
      if (isNetty()) {
         return createNettyNonHALocator();
      } else {
         return createInVMNonHALocator();
      }
   }

   protected boolean isNetty() {
      return false;
   }

   protected boolean isPersistent() {
      return false;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(isPersistent(), isNetty());

      server.start();

      ServerLocator locator = createFactory();

      ClientSessionFactory cf = createSessionFactory(locator);

      session = cf.createSession();

      session.createQueue(QueueConfiguration.of(InVMNonPersistentMessageBufferTest.queueName).setAddress(InVMNonPersistentMessageBufferTest.address).setRoutingType(RoutingType.ANYCAST));

      producer = session.createProducer(InVMNonPersistentMessageBufferTest.address);

      consumer = session.createConsumer(InVMNonPersistentMessageBufferTest.queueName);

      session.start();
   }

   private ClientMessage sendAndReceive(final ClientMessage message) throws Exception {
      producer.send(message);

      ClientMessage received = consumer.receive(10000);

      return received;
   }

}
