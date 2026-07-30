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
package org.apache.activemq.artemis.tests.unit.core.paging.impl;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.paging.impl.PagedMessageImpl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPLargeMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.qpid.proton.message.impl.MessageImpl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class AmqpPageTest extends PageTest {

   private static MessageImpl createProtonMessage(String address, byte[] content) {
      AmqpMessage message = new AmqpMessage();
      message.setBytes(content);
      message.setAddress(address);
      message.setDurable(true);
      MessageImpl protonMessage = (MessageImpl) message.getWrappedMessage();
      return protonMessage;
   }

   private static AMQPStandardMessage createStandardMessage(SimpleString address, long msgId, byte[] content) {
      MessageImpl protonMessage = createProtonMessage(address.toString(), content);
      AMQPStandardMessage amqpMessage = encodeAndDecodeMessage(0, protonMessage, content.length + 1000);
      amqpMessage.setMessageID(msgId);
      return amqpMessage;
   }

   private static AMQPLargeMessage createLargeMessage(StorageManager storageManager,
                                                      SimpleString address,
                                                      long msgId,
                                                      byte[] content) throws Exception {
      MessageImpl protonMessage = createProtonMessage(address.toString(), content);
      byte[] messageContent = encodeProtonMessage(protonMessage, 1024);
      final AMQPLargeMessage amqpMessage = new AMQPLargeMessage(msgId, 0, null, null, storageManager);
      amqpMessage.setAddress(address);
      amqpMessage.setFileDurable(true);
      amqpMessage.addBytes(messageContent);
      amqpMessage.reloadExpiration(0);
      return amqpMessage;
   }

   @Override
   protected void writeMessage(StorageManager storageManager,
                               boolean isLargeMessage,
                               long msgID,
                               SimpleString address,
                               byte[] content,
                               Page page) throws Exception {
      if (!isLargeMessage) {
         final Message message = createStandardMessage(address, msgID, content);
         page.write(new PagedMessageImpl(message, new long[0]), true, false);
      } else {
         final AMQPLargeMessage message = createLargeMessage(storageManager, address, msgID, content);
         page.write(new PagedMessageImpl(message, new long[0]), true, false);
         message.releaseResources(false, false);
      }
   }

   public static AMQPStandardMessage encodeAndDecodeMessage(int messageFormat, MessageImpl message, int expectedSize) {
      byte[] bytes = encodeProtonMessage(message, expectedSize);

      return new AMQPStandardMessage(messageFormat, bytes, null);
   }

   private static byte[] encodeProtonMessage(MessageImpl message, int expectedSize) {
      ByteBuf nettyBuffer = Unpooled.buffer(expectedSize);
      message.encode(new NettyWritable(nettyBuffer));
      byte[] bytes = new byte[nettyBuffer.writerIndex()];
      nettyBuffer.readBytes(bytes);
      return bytes;
   }
}
