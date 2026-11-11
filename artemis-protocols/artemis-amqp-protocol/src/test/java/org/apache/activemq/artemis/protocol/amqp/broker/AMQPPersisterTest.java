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

package org.apache.activemq.artemis.protocol.amqp.broker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.artemis.spi.core.protocol.MessagePersister;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.UnsignedByte;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Header;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.message.impl.MessageImpl;
import org.junit.jupiter.api.Test;

public class AMQPPersisterTest {

   protected Message createMessage(SimpleString address, int msgId, byte[] content) {
      return createMessage(address, (byte) AMQPMessage.MAX_MESSAGE_PRIORITY, msgId, content);
   }

   protected Message createMessage(SimpleString address, byte priority, int msgId, byte[] content) {
      final MessageImpl protonMessage = createProtonMessage(address.toString(), priority, content);
      final AMQPStandardMessage msg = encodeAndDecodeMessage(protonMessage, content.length);
      msg.setAddress(address);
      msg.setMessageID(msgId);
      return msg;
   }

   private AMQPStandardMessage encodeAndDecodeMessage(MessageImpl message, int expectedSize) {
      ByteBuf nettyBuffer = Unpooled.buffer(expectedSize);

      message.encode(new NettyWritable(nettyBuffer));
      byte[] bytes = new byte[nettyBuffer.writerIndex()];
      nettyBuffer.readBytes(bytes);

      return new AMQPStandardMessage(0, bytes, null);
   }

   private MessageImpl createProtonMessage(String address, byte priority, byte[] content) {
      MessageImpl message = (MessageImpl) Proton.message();

      Header header = new Header();
      header.setDurable(true);
      header.setPriority(UnsignedByte.valueOf(priority));

      Properties properties = new Properties();
      properties.setCreationTime(new Date(System.currentTimeMillis()));
      properties.setTo(address);
      properties.setMessageId(UUID.randomUUID());

      MessageAnnotations annotations = new MessageAnnotations(new LinkedHashMap<>());
      ApplicationProperties applicationProperties = new ApplicationProperties(new LinkedHashMap<>());

      AmqpValue body = new AmqpValue(Arrays.copyOf(content, content.length));

      message.setHeader(header);
      message.setMessageAnnotations(annotations);
      message.setProperties(properties);
      message.setApplicationProperties(applicationProperties);
      message.setBody(body);

      return message;
   }

   @Test
   public void testEncodeSize() throws Exception {
      Message message = createMessage(SimpleString.of("Test"), 1, new byte[10]);

      MessagePersister persister = AMQPMessagePersisterV3.getInstance();

      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(1024);
      persister.encode(buffer, message);

      assertEquals(persister.getEncodeSize(message), buffer.writerIndex());
   }

   @Test
   public void testV1PersisterRecoversPriority() {
      doTestPersisterRecoversPriority(AMQPMessagePersister.getInstance());
   }

   @Test
   public void testV2PersisterRecoversPriority() {
      doTestPersisterRecoversPriority(AMQPMessagePersisterV2.getInstance());
   }

   @Test
   public void testV3PersisterRecoversPriority() {
      doTestPersisterRecoversPriority(AMQPMessagePersisterV3.getInstance());
   }

   private void doTestPersisterRecoversPriority(MessagePersister persister) {
      for (byte priority = 0; priority <= AMQPMessage.MAX_MESSAGE_PRIORITY; ++priority) {
         final Message message = createMessage(SimpleString.of("Test"), priority, 1, new byte[10]);

         final ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(1024);

         persister.encode(buffer, message);

         assertEquals(persister.getID(), buffer.readByte());

         final Message decoded = persister.decode(buffer, message, null);

         assertEquals(priority, decoded.getPriority());
      }
   }
}
