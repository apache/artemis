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
package org.apache.activemq.artemis.api.core;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.core.message.LargeBodyReader;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;

/**
 * This interface is only to determine the API of methods required for Core Messages
 */
public interface ICoreMessage extends Message {

   @Override
   ICoreMessage copy();
   /**
    * The buffer will belong to this message, until release is called.
    */
   Message setBuffer(ByteBuf buffer);

   ByteBuf getBuffer();

   LargeBodyReader getLargeBodyReader() throws ActiveMQException;

   int getHeadersAndPropertiesEncodeSize();

   @Override
   InputStream getBodyInputStream();

   /**
    * {@return a new Buffer slicing the current Body}
    */
   ActiveMQBuffer getReadOnlyBodyBuffer();

   /**
    * {@return the length in bytes of the body buffer}
    */
   int getBodyBufferSize();

   /**
    * {@return a readOnlyBodyBuffer or a decompressed one if the message is compressed or the large message buffer}
    */
   ActiveMQBuffer getDataBuffer();

   @Override
   byte getType();

   @Override
   CoreMessage setType(byte type);

   /**
    * We are really interested if this is a LargeServerMessage.
    */
   boolean isServerMessage();

   /**
    * The buffer to write the body. Warning: If you just want to read the content of a message, use
    * {@link #getDataBuffer()} or {@link #getReadOnlyBodyBuffer()}
    */
   @Override
   ActiveMQBuffer getBodyBuffer();

   int getEndOfBodyPosition();

   /**
    * Used on large messages treatment. this method is used to transfer properties from a temporary CoreMessage to a
    * definitive one. This is used when before a Message was defined as a LargeMessages, its properties are then moved
    * from the Temporary message to its final LargeMessage object.
    * <p>
    * Be careful as this will not perform a copy of the Properties. For real copy, use the copy methods or copy
    * constructors.
    */
   void moveHeadersAndProperties(Message msg);

   void sendBuffer_1X(ByteBuf sendBuffer);

   /**
    * it will fix the body of incoming messages from 1.x and before versions
    */
   void receiveBuffer_1X(ByteBuf buffer);

   /**
    * {@inheritDoc}
    */
   @Override
   default Map<String, Object> toMap(int valueSizeLimit) {
      Map map = toPropertyMap(valueSizeLimit);
      map.put("messageID", getMessageID());
      Object userID = getUserID();
      if (getUserID() != null) {
         map.put("userID", "ID:" + userID.toString());
      }

      map.put("address", Objects.requireNonNullElse(getAddress(), ""));
      map.put("type", getType());
      map.put("durable", isDurable());
      map.put("expiration", getExpiration());
      map.put("timestamp", getTimestamp());
      map.put("priority", getPriority());

      return map;
   }

   default boolean isConfirmed() {
      return false;
   }

   default void setConfirmed(boolean confirmed) {
   }

}
