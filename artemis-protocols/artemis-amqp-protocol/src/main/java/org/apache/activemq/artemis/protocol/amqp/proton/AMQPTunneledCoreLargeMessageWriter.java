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

package org.apache.activemq.artemis.protocol.amqp.proton;

import static org.apache.activemq.artemis.protocol.amqp.proton.AMQPTunneledMessageConstants.AMQP_TUNNELED_CORE_LARGE_MESSAGE_FORMAT;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.message.LargeBodyReader;
import org.apache.activemq.artemis.core.persistence.impl.journal.LargeServerMessageImpl;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.protocol.amqp.util.NettyReadable;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.codec.EncoderImpl;
import org.apache.qpid.proton.codec.EncodingCodes;
import org.apache.qpid.proton.codec.ReadableBuffer;
import org.apache.qpid.proton.codec.WritableBuffer;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;

/**
 * Writer of tunneled large Core messages that will be written as the body of an AMQP delivery with a custom message
 * format that indicates this payload. The writer will read bytes from the Core large message file and write them into
 * an AMQP Delivery that will be sent across to the remote peer where it can be processed and a Core message recreated
 * for dispatch as if it had been sent from a Core connection.
 */
public class AMQPTunneledCoreLargeMessageWriter implements MessageWriter {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final byte DATA_DESCRIPTOR = 0x75;
   private static final int DATA_SECTION_ENCODING_BYTES = Long.BYTES;

   private enum State {
      /**
       * Writing the optional AMQP delivery annotations which can provide additional context.
       */
      STREAMING_DELIVERY_ANNOTATIONS,
      /**
       * Writing the core message headers and properties that describe the message.
       */
      STREAMING_CORE_HEADERS,
      /**
       * Writing the actual message payload from the large message file.
       */
      STREAMING_BODY,
      /**
       * Done writing, no more bytes will be written.
       */
      DONE,
      /**
       * The writer is closed and cannot be used again until open is called.
       */
      CLOSED
   }

   private final ProtonServerSenderContext serverSender;
   private final AMQPConnectionContext connection;
   private final Sender protonSender;

   private DeliveryAnnotations annotations;
   private MessageReference reference;
   private LargeServerMessageImpl message;
   private Delivery delivery;
   private int frameSize;

   // Used for storing the encoded delivery annotations or the core header and properties
   // while writing into the frame buffer which could be smaller.
   private ByteBuf encodingBuffer;

   // For writing any of the sections this stores where we are in the total writes expected
   // for that section so that on IO flow control we can resume later where we left off.
   private long position;

   // When writing the Data section(s) for the core large message body each Data section is
   // limited to 2GB to ensure receiver Codec can handle the encoding.
   private int dataSectionRemaining;

   private volatile State state = State.CLOSED;

   public AMQPTunneledCoreLargeMessageWriter(ProtonServerSenderContext serverSender) {
      this.serverSender = serverSender;
      this.connection = serverSender.getSessionContext().getAMQPConnectionContext();
      this.protonSender = serverSender.getSender();
   }

   @Override
   public boolean isWriting() {
      return state != State.CLOSED;
   }

   @Override
   public void close() {
      if (state != State.CLOSED) {
         try {
            if (message != null) {
               message.usageDown();
            }
         } finally {
            reset(State.CLOSED);
         }
      }
   }

   @Override
   public AMQPTunneledCoreLargeMessageWriter open(MessageReference reference) {
      if (state != State.CLOSED) {
         throw new IllegalStateException("Trying to open an AMQP Large Message writer that was not closed");
      }

      reset(State.STREAMING_DELIVERY_ANNOTATIONS);

      return this;
   }

   private void reset(State newState) {
      message = null;
      reference = null;
      delivery = null;
      position = 0;
      dataSectionRemaining = 0;
      state = newState;
      encodingBuffer = null;
   }

   @Override
   public void writeBytes(MessageReference messageReference) {
      if (protonSender.getLocalState() == EndpointState.CLOSED) {
         logger.debug("Not delivering message {} as the sender is closed and credits were available, if you see too many of these it means clients are issuing credits and closing the connection with pending credits a lot of times", messageReference);
         return;
      }

      if (state == State.CLOSED) {
         throw new IllegalStateException("Cannot write to an AMQP Large Message Writer that has been closed");
      }

      if (state == State.DONE) {
         throw new IllegalStateException(
            "Cannot write to an AMQP Large Message Writer that was already used to write a message and was not reset");
      }

      reference = messageReference;
      message = (LargeServerMessageImpl) messageReference.getMessage();
      annotations = reference.getProtocolData(DeliveryAnnotations.class);

      delivery = serverSender.createDelivery(messageReference, AMQP_TUNNELED_CORE_LARGE_MESSAGE_FORMAT);
      // We will deduct some bytes from the frame for encoding the Transfer payload which could exclude
      // the delivery tag on successive transfers but we aren't sure if that will happen so we assume not.
      frameSize = protonSender.getSession().getConnection().getTransport().getOutboundFrameSizeLimit() - 50 - (delivery.getTag() != null ? delivery.getTag().length : 0);

      message.usageUp();

      tryDelivering();
   }

   /**
    * Used to provide re-entry from the flow control executor when IO back-pressure has eased
    */
   private void resume() {
      connection.runNow(this::tryDelivering);
   }

   private ByteBuf getOrCreateDeliveryAnnotationsBuffer() {
      if (encodingBuffer == null) {
         encodingBuffer = Unpooled.buffer();

         final EncoderImpl encoder = TLSEncode.getEncoder();

         try {
            encoder.setByteBuffer(new NettyWritable(encodingBuffer));
            encoder.writeObject(annotations);
         } finally {
            encoder.setByteBuffer((WritableBuffer) null);
         }
      }

      return encodingBuffer;
   }

   private ByteBuf getOrCreateMessageHeaderBuffer() {
      if (encodingBuffer == null) {
         final int headersSize = message.getHeadersAndPropertiesEncodeSize();
         final int bufferSize = headersSize + DATA_SECTION_ENCODING_BYTES;
         encodingBuffer = Unpooled.buffer(bufferSize, bufferSize);
         writeDataSectionTypeInfo(encodingBuffer, headersSize);
         message.encodeHeadersAndProperties(encodingBuffer);
      }

      return encodingBuffer;
   }

   // Will return true when the optional delivery annotations are fully sent or are not present, and false
   // if not able to send due to a flow control event.
   private boolean trySendDeliveryAnnotations(ByteBuf frameBuffer, NettyReadable frameView) {
      for (; protonSender.getLocalState() != EndpointState.CLOSED && state == State.STREAMING_DELIVERY_ANNOTATIONS; ) {
         if (annotations != null && annotations.getValue() != null && !annotations.getValue().isEmpty()) {
            if (isFlowControlled(frameBuffer, frameView)) {
               break;
            }

            final ByteBuf annotationsBuffer = getOrCreateDeliveryAnnotationsBuffer();
            final int readSize = Math.min(frameBuffer.writableBytes(), annotationsBuffer.readableBytes());

            annotationsBuffer.readBytes(frameBuffer, readSize);

            // In case the Delivery Annotations encoding exceed the AMQP frame size we
            // flush and keep sending until done or until flow controlled.
            if (!frameBuffer.isWritable()) {
               protonSender.send(frameView);
               frameBuffer.clear();
               connection.instantFlush();
            }

            if (!annotationsBuffer.isReadable()) {
               encodingBuffer = null;
               state = State.STREAMING_CORE_HEADERS;
            }
         } else {
            state = State.STREAMING_CORE_HEADERS;
         }
      }

      return state == State.STREAMING_CORE_HEADERS;
   }

   // Will return true when the header was fully sent false if not all the header
   // data could be sent due to a flow control event.
   private boolean trySendHeadersAndProperties(ByteBuf frameBuffer, NettyReadable frameView) {
      for (; protonSender.getLocalState() != EndpointState.CLOSED && state == State.STREAMING_CORE_HEADERS; ) {
         if (isFlowControlled(frameBuffer, frameView)) {
            break;
         }

         final ByteBuf headerBuffer = getOrCreateMessageHeaderBuffer();
         final int readSize = Math.min(frameBuffer.writableBytes(), headerBuffer.readableBytes());

         headerBuffer.readBytes(frameBuffer, readSize);

         // In case the Core message header and properties exceed the AMQP frame size we
         // flush and keep sending until done or until flow controlled.
         if (!frameBuffer.isWritable()) {
            protonSender.send(frameView);
            frameBuffer.clear();
            connection.instantFlush();
         }

         if (!headerBuffer.isReadable()) {
            encodingBuffer = null;
            state = State.STREAMING_BODY;
         }
      }

      return state == State.STREAMING_BODY;
   }

   // Should return true whenever the message contents have been fully written and false otherwise
   // so that more writes can be attempted after flow control allows it.
   private boolean tryDeliveryMessageBody(ByteBuf frameBuffer, NettyReadable frameView) throws ActiveMQException {
      try (LargeBodyReader context = message.getLargeBodyReader()) {
         context.open();
         context.position(position);

         final long bodySize = context.getSize();

         for (; protonSender.getLocalState() != EndpointState.CLOSED && state == State.STREAMING_BODY; ) {
            if (isFlowControlled(frameBuffer, frameView)) {
               break;
            }

            if (dataSectionRemaining == 0) {
               // Cap section at 2GB or the remaining contents of the file if smaller
               dataSectionRemaining = (int) Math.min(Integer.MAX_VALUE, bodySize - position);

               // Ensure the frame buffer has room for the Data section encoding.
               if (frameBuffer.writableBytes() < DATA_SECTION_ENCODING_BYTES) {
                  protonSender.send(frameView);
                  frameBuffer.clear();
               }

               writeDataSectionTypeInfo(frameBuffer, dataSectionRemaining);
            }

            final int readSize = context.readInto(
               frameBuffer.internalNioBuffer(frameBuffer.writerIndex(), frameBuffer.writableBytes()));

            frameBuffer.writerIndex(frameBuffer.writerIndex() + readSize);
            position += readSize;
            dataSectionRemaining -= readSize;

            if (!frameBuffer.isWritable() || position == bodySize) {
               protonSender.send(frameView);
               frameBuffer.clear();

               // Only flush on partial writes, the sender will flush on completion so
               // we avoid excessive flushing in that case.
               if (position < bodySize) {
                  connection.instantFlush();
               } else {
                  state = State.DONE;
               }
            }
         }

         return state == State.DONE;
      }
   }

   private void tryDelivering() {
      final ByteBuf frameBuffer = PooledByteBufAllocator.DEFAULT.directBuffer(frameSize, frameSize);

      try {
         final NettyReadable frameView = new NettyReadable(frameBuffer);

         // materialize it so we can use its internal NIO buffer
         frameBuffer.ensureWritable(frameSize);

         switch (state) {
            case STREAMING_DELIVERY_ANNOTATIONS:
               if (!trySendDeliveryAnnotations(frameBuffer, frameView)) {
                  return;
               }
            case STREAMING_CORE_HEADERS:
               if (!trySendHeadersAndProperties(frameBuffer, frameView)) {
                  return;
               }
            case STREAMING_BODY:
               if (!tryDeliveryMessageBody(frameBuffer, frameView)) {
                  return;
               }

               serverSender.reportDeliveryComplete(this, reference, delivery, true);
               break;
            default:
               throw new IllegalStateException("The writer already wrote a message and was not reset");
         }
      } catch (Exception deliveryError) {
         serverSender.reportDeliveryError(this, reference, deliveryError);
      } finally {
         frameBuffer.release();
      }
   }

   private boolean isFlowControlled(ByteBuf frameBuffer, ReadableBuffer frameView) {
      if (!connection.flowControl(this::resume)) {
         if (frameBuffer.isReadable()) {
            protonSender.send(frameView); // Store inflight data in the sender
         }

         return true;
      } else {
         return false;
      }
   }

   private void writeDataSectionTypeInfo(ByteBuf buffer, int encodedSize) {
      buffer.writeByte(EncodingCodes.DESCRIBED_TYPE_INDICATOR);
      buffer.writeByte(EncodingCodes.SMALLULONG);
      buffer.writeByte(DATA_DESCRIPTOR);
      buffer.writeByte(EncodingCodes.VBIN32);
      buffer.writeInt(encodedSize); // Core message will encode into this size.
   }
}
