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
package org.apache.activemq.artemis.protocol.amqp.broker;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.utils.DataConstants;

import static org.apache.activemq.artemis.core.persistence.PersisterIDs.AMQPLargeMessagePersisterV2_ID;

public class AMQPLargeMessagePersisterV2 extends AMQPLargeMessagePersister {

   public static final byte ID = AMQPLargeMessagePersisterV2_ID;

   public static AMQPLargeMessagePersisterV2 theInstance;

   public static AMQPLargeMessagePersisterV2 getInstance() {
      if (theInstance == null) {
         theInstance = new AMQPLargeMessagePersisterV2();
      }
      return theInstance;
   }

   @Override
   public byte getID() {
      return ID;
   }

   public AMQPLargeMessagePersisterV2() {
      super();
   }

   int size;

   @Override
   public int getEncodeSize(Message record) {
      AMQPLargeMessage msgEncode = (AMQPLargeMessage) record;
      ByteBuf buf = msgEncode.getSavedEncodeBuffer();

      try {
         size = buf.writerIndex() + getMapCodecSize(record) + DataConstants.SIZE_BYTE;
         return size;
      } finally {
         msgEncode.releaseEncodedBuffer();
      }
   }

   /** Write persister data using Map like boundaries from MapPersister */
   protected void writeMapCodecData(ActiveMQBuffer buffer, Message record) {
      AMQPMessageMapCodec.getInstance().encode(buffer, record);
   }

   protected int getMapCodecSize(Message record) {
      return AMQPMessageMapCodec.getInstance().getEncodeSize(record);
   }

   @Override
   public Message decode(ActiveMQBuffer buffer, Message record, CoreMessageObjectPools pool) {
      AMQPMessageMapCodec mapCodec = AMQPMessageMapCodec.getInstance().reset();

      mapCodec.decode(buffer);

      assert mapCodec.memoryEstimate != 0;

      AMQPLargeMessage largeMessage = new AMQPLargeMessage(mapCodec.messageID, mapCodec.messageFormat, mapCodec.extraProperties, null, null);

      largeMessage.reloadSetDurable(mapCodec.isDurable);
      largeMessage.setFileDurable(mapCodec.isDurable);
      if (mapCodec.address != null) {
         largeMessage.setAddress(mapCodec.address);
      }

      largeMessage.readSavedEncoding(buffer.byteBuf());

      largeMessage.reloadExpiration(mapCodec.messageExpiration);
      largeMessage.setReencoded(mapCodec.isReencoded);
      largeMessage.setMemoryEstimate(mapCodec.memoryEstimate);
      largeMessage.reloadPriority(mapCodec.priority);

      mapCodec.reset();

      return largeMessage;
   }

   @Override
   public void encode(ActiveMQBuffer buffer, Message record) {
      AMQPLargeMessage msgEncode = (AMQPLargeMessage)  record;
      writePersisterID(buffer);
      writeMapCodecData(buffer, record);

      ByteBuf savedEncodeBuffer = msgEncode.getSavedEncodeBuffer();
      buffer.writeBytes(savedEncodeBuffer, 0, savedEncodeBuffer.writerIndex());
      msgEncode.releaseEncodedBufferAfterWrite();
   }

}
