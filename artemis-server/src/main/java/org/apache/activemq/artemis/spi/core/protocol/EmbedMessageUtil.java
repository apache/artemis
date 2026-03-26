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

package org.apache.activemq.artemis.spi.core.protocol;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RefCountMessage;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.LargeServerMessageImpl;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class EmbedMessageUtil {


   private static final String AMQP_ENCODE_PROPERTY = "_AMQP_EMBED_LARGE";

   private static final byte[] signature = new byte[]{(byte) 'E', (byte) 'M', (byte) 'B'};

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // AMQP introduced Map Encoding on this version
   public static final int LATEST_WIRE_VERSION = 2;
   public static final int NO_MAP_WIRE_VERSION = 1;

   public static ICoreMessage embedAsCoreMessage(Message source, int wireVersion) {

      if (source instanceof ICoreMessage message) {
         return message;
      } else {

         if (source.isLargeMessage()) {
            LargeServerMessage largeSource = (LargeServerMessage) source;

            LargeServerMessageImpl largeServerMessage = new LargeServerMessageImpl(Message.LARGE_EMBEDDED_TYPE, source.getMessageID(), largeSource.getStorageManager(), largeSource.getLargeBody().createFile());
            largeServerMessage.setDurable(source.isDurable());

            Persister<Message> messagePersister = source.getWireCompatiblePersister(wireVersion);

            int size = messagePersister.getEncodeSize(source);
            byte[] arrayByte = new byte[size];
            ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(arrayByte);
            buffer.resetWriterIndex();
            messagePersister.encode(buffer, source);
            largeServerMessage.toMessage().putBytesProperty(AMQP_ENCODE_PROPERTY, arrayByte);
            largeServerMessage.setParentRef((RefCountMessage)source);
            return (ICoreMessage) largeServerMessage.toMessage();
         } else {
            Persister persister = source.getWireCompatiblePersister(wireVersion);

            CoreMessage message = new CoreMessage(source.getMessageID(), persister.getEncodeSize(source) + signature.length + CoreMessage.BODY_OFFSET).setType(Message.EMBEDDED_TYPE);
            message.setDurable(source.isDurable());

            ActiveMQBuffer buffer = message.getBodyBuffer();
            buffer.writeBytes(signature);
            persister.encode(buffer, source);
            return message;
         }
      }
   }

   public static ICoreMessage embedAsCoreMessage(Message source) {
      return embedAsCoreMessage(source, LATEST_WIRE_VERSION);
   }

   public static Message extractEmbedded(ICoreMessage message, StorageManager storageManager) {
      return switch (message.getType()) {
         case Message.EMBEDDED_TYPE -> extractRegularMessage(message, storageManager);
         case Message.LARGE_EMBEDDED_TYPE -> extractLargeMessage(message, storageManager);
         default -> message;
      };
   }

   private static Message extractRegularMessage(ICoreMessage message, StorageManager storageManager) {
      ActiveMQBuffer buffer = message.getReadOnlyBodyBuffer();

      if (buffer.readableBytes() < signature.length || !checkSignature(buffer)) {
         logger.trace("Message type {} was used for something other than embed messages, ignoring content and treating as a regular message", Message.EMBEDDED_TYPE);
         return message;
      }

      return readEncoded(message, storageManager, buffer);
   }

   private static Message readEncoded(ICoreMessage message, StorageManager storageManager, ActiveMQBuffer buffer) {
      try {
         Message returnMessage = MessagePersister.getInstance().decode(buffer, null, null, storageManager);
         if (returnMessage instanceof LargeServerMessage largeServerMessage) {
            largeServerMessage.setStorageManager(storageManager);
         }
         returnMessage.setMessageID(message.getMessageID());
         return returnMessage;
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         return message;
      }
   }

   private static Message extractLargeMessage(ICoreMessage message, StorageManager storageManager) {
      ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(message.getBytesProperty(AMQP_ENCODE_PROPERTY));

      Message largeMessageReturn = readEncoded(message, storageManager, buffer);

      return largeMessageReturn;
   }

   private static boolean checkSignature(final ActiveMQBuffer buffer) {
      return buffer.readByte() == signature[0] && buffer.readByte() == signature[1] && buffer.readByte() == signature[2];
   }
}
