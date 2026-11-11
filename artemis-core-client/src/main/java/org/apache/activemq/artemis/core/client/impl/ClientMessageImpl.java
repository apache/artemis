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
package org.apache.activemq.artemis.core.client.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQPropertyConversionException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.client.ActiveMQClientMessageBundle;
import org.apache.activemq.artemis.core.message.LargeBodyReader;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.apache.activemq.artemis.utils.UUID;

public class ClientMessageImpl extends CoreMessage implements ClientMessageInternal {

   // added this constant here so that the client package have no dependency on JMS
   public static final SimpleString REPLYTO_HEADER_NAME = MessageUtil.REPLYTO_HEADER_NAME;

   private int deliveryCount;

   private ClientConsumerInternal consumer;

   private int flowControlSize = -1;

   private boolean confirmed;

   /**
    * Used on LargeMessages only
    */
   private InputStream bodyInputStream;

   public ClientMessageImpl() {
   }

   public ClientMessageImpl(CoreMessageObjectPools coreMessageObjectPools) {
      super(coreMessageObjectPools);
   }

   protected ClientMessageImpl(ClientMessageImpl other) {
      super(other);
   }

   @Override
   public ClientMessageImpl setDurable(boolean durable) {
      super.setDurable(durable);
      return this;
   }

   @Override
   public ClientMessageImpl setExpiration(long expiration) {
      super.setExpiration(expiration);
      return this;
   }

   @Override
   public ClientMessageImpl setPriority(byte priority) {
      super.setPriority(priority);
      return this;
   }

   @Override
   public ClientMessageImpl setUserID(UUID userID) {

      return this;
   }


   // Construct messages before sending
   public ClientMessageImpl(final byte type,
                            final boolean durable,
                            final long expiration,
                            final long timestamp,
                            final byte priority,
                            final int initialMessageBufferSize,
                            final CoreMessageObjectPools coreMessageObjectPools) {
      super(coreMessageObjectPools);
      this.setType(type).setExpiration(expiration).setTimestamp(timestamp).setDurable(durable).
           setPriority(priority).initBuffer(initialMessageBufferSize);
   }

   public ClientMessageImpl(final byte type,
                            final boolean durable,
                            final long expiration,
                            final long timestamp,
                            final byte priority,
                            final int initialMessageBufferSize) {
      this(type, durable, expiration, timestamp, priority, initialMessageBufferSize, null);
   }

   @Override
   public void onReceipt(final ClientConsumerInternal consumer) {
      this.consumer = consumer;
   }

   @Override
   public ClientMessageImpl setDeliveryCount(final int deliveryCount) {
      this.deliveryCount = deliveryCount;
      return this;
   }

   @Override
   public int getDeliveryCount() {
      return deliveryCount;
   }

   @Override
   public ClientMessageImpl acknowledge() throws ActiveMQException {
      if (consumer != null) {
         consumer.acknowledge(this);
      }

      return this;
   }

   @Override
   public ClientMessageImpl individualAcknowledge() throws ActiveMQException {
      if (consumer != null) {
         consumer.individualAcknowledge(this);
      }

      return this;
   }


   @Override
   public void checkCompletion() throws ActiveMQException {
   }

   @Override
   public int getFlowControlSize() {
      if (flowControlSize < 0) {
         throw new IllegalStateException("Flow Control hasn't been set");
      }
      return flowControlSize;
   }

   @Override
   public void setFlowControlSize(final int flowControlSize) {
      this.flowControlSize = flowControlSize;
   }

   @Override
   public boolean isLargeMessage() {
      return false;
   }

   @Override
   public boolean isCompressed() {
      return properties.getBooleanProperty(Message.HDR_LARGE_COMPRESSED);
   }

   @Override
   public int getBodySize() {
      checkEncode();
      return endOfBodyPosition - BUFFER_HEADER_SPACE;
   }

   @Override
   public String toString() {
      return getClass().getSimpleName() + "[messageID=" + messageID + ", durable=" + durable + ", address=" + getAddress() + ",userID=" + Objects.requireNonNullElse(getUserID(), "null") + ", properties=" + getProperties().toString() + "]";
   }

   @Override
   public void saveToOutputStream(final OutputStream out) throws ActiveMQException {
      try {
         byte[] readBuffer = new byte[getBodySize()];
         getBodyBuffer().readBytes(readBuffer);
         out.write(readBuffer);
         out.flush();
      } catch (IOException e) {
         throw ActiveMQClientMessageBundle.BUNDLE.errorSavingBody(e);
      }
   }

   @Override
   public ClientMessageImpl setOutputStream(final OutputStream out) throws ActiveMQException {
      saveToOutputStream(out);
      return this;
   }

   @Override
   public boolean waitOutputStreamCompletion(final long timeMilliseconds) throws ActiveMQException {
      return true;
   }

   @Override
   public void discardBody() {
   }

   @Override
   public InputStream getBodyInputStream() {
      return bodyInputStream;
   }

   @Override
   public ClientMessageImpl setBodyInputStream(final InputStream bodyInputStream) {
      this.bodyInputStream = bodyInputStream;
      return this;
   }

   @Override
   public LargeBodyReader getLargeBodyReader() throws ActiveMQException {
      return new DecodingContext();
   }

   @Override
   public ClientMessageImpl putBooleanProperty(final SimpleString key, final boolean value) {
      return (ClientMessageImpl) super.putBooleanProperty(key, value);
   }

   @Override
   public ClientMessageImpl putByteProperty(final SimpleString key, final byte value) {
      return (ClientMessageImpl) super.putByteProperty(key, value);
   }

   @Override
   public ClientMessageImpl putBytesProperty(final SimpleString key, final byte[] value) {
      return (ClientMessageImpl) super.putBytesProperty(key, value);
   }

   @Override
   public ClientMessageImpl putCharProperty(SimpleString key, char value) {
      return (ClientMessageImpl) super.putCharProperty(key, value);
   }

   @Override
   public ClientMessageImpl putCharProperty(String key, char value) {
      return (ClientMessageImpl) super.putCharProperty(key, value);
   }

   @Override
   public ClientMessageImpl putShortProperty(final SimpleString key, final short value) {
      return (ClientMessageImpl) super.putShortProperty(key, value);
   }

   @Override
   public ClientMessageImpl putIntProperty(final SimpleString key, final int value) {
      return (ClientMessageImpl) super.putIntProperty(key, value);
   }

   @Override
   public ClientMessageImpl putLongProperty(final SimpleString key, final long value) {
      return (ClientMessageImpl) super.putLongProperty(key, value);
   }

   @Override
   public ClientMessageImpl putFloatProperty(final SimpleString key, final float value) {
      return (ClientMessageImpl) super.putFloatProperty(key, value);
   }

   @Override
   public ClientMessageImpl putDoubleProperty(final SimpleString key, final double value) {
      return (ClientMessageImpl) super.putDoubleProperty(key, value);
   }

   @Override
   public ClientMessageImpl putStringProperty(final SimpleString key, final SimpleString value) {
      return (ClientMessageImpl) super.putStringProperty(key, value);
   }

   @Override
   public ClientMessageImpl putStringProperty(final SimpleString key, final String value) {
      return (ClientMessageImpl) super.putStringProperty(key, value);
   }

   @Override
   public ClientMessageImpl putObjectProperty(final SimpleString key,
                                              final Object value) throws ActiveMQPropertyConversionException {
      return (ClientMessageImpl) super.putObjectProperty(key, value);
   }

   @Override
   public ClientMessageImpl putObjectProperty(final String key,
                                              final Object value) throws ActiveMQPropertyConversionException {
      return (ClientMessageImpl) super.putObjectProperty(key, value);
   }

   @Override
   public ClientMessageImpl putBooleanProperty(final String key, final boolean value) {
      return (ClientMessageImpl) super.putBooleanProperty(key, value);
   }

   @Override
   public ClientMessageImpl putByteProperty(final String key, final byte value) {
      return (ClientMessageImpl) super.putByteProperty(key, value);
   }

   @Override
   public ClientMessageImpl putBytesProperty(final String key, final byte[] value) {
      return (ClientMessageImpl) super.putBytesProperty(key, value);
   }

   @Override
   public ClientMessageImpl putShortProperty(final String key, final short value) {
      return (ClientMessageImpl) super.putShortProperty(key, value);
   }

   @Override
   public ClientMessageImpl putIntProperty(final String key, final int value) {
      return (ClientMessageImpl) super.putIntProperty(key, value);
   }

   @Override
   public ClientMessageImpl putLongProperty(final String key, final long value) {
      return (ClientMessageImpl) super.putLongProperty(key, value);
   }

   @Override
   public ClientMessageImpl putFloatProperty(final String key, final float value) {
      return (ClientMessageImpl) super.putFloatProperty(key, value);
   }

   @Override
   public ClientMessageImpl putDoubleProperty(final String key, final double value) {
      return (ClientMessageImpl) super.putDoubleProperty(key, value);
   }

   @Override
   public ClientMessageImpl putStringProperty(final String key, final String value) {
      return (ClientMessageImpl) super.putStringProperty(key, value);
   }

   @Override
   public ClientMessageImpl writeBodyBufferBytes(byte[] bytes) {
      getBodyBuffer().writeBytes(bytes);
      return this;
   }

   @Override
   public ClientMessageImpl writeBodyBufferString(String string) {
      getBodyBuffer().writeString(string);
      return this;
   }

   private final class DecodingContext implements LargeBodyReader {

      private DecodingContext() {
      }

      @Override
      public void open() {
         getBodyBuffer().readerIndex(0);
      }

      @Override
      public void close() {
      }

      @Override
      public long getSize() {
         if (isLargeMessage()) {
            return getBodyBuffer().writerIndex();
         } else {
            return (long) getBodyBuffer().writerIndex() - BODY_OFFSET;
         }
      }

      @Override
      public void position(long position) {
         buffer.readerIndex((int)position);
      }

      @Override
      public long position() {
         return buffer.readerIndex();
      }

      @Override
      public int readInto(final ByteBuffer bufferRead) {
         final int remaining = bufferRead.remaining();
         buffer.readBytes(bufferRead);
         return remaining;
      }
   }

   @Override
   public ICoreMessage copy() {
      return new ClientMessageImpl(this);
   }

   @Override
   public boolean isConfirmed() {
      return confirmed;
   }

   @Override
   public void setConfirmed(boolean confirmed) {
      this.confirmed = confirmed;
   }
}
