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

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPInternalErrorException;
import org.apache.activemq.artemis.protocol.amqp.util.NettyReadable;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.qpid.proton.amqp.messaging.AmqpSequence;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.amqp.messaging.Footer;
import org.apache.qpid.proton.amqp.messaging.Header;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.codec.DecoderImpl;
import org.apache.qpid.proton.codec.EncodingCodes;
import org.apache.qpid.proton.codec.ReadableBuffer;
import org.apache.qpid.proton.codec.TypeConstructor;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Receiver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Reader of tunneled large Core message that have been written as the body of an AMQP delivery with a custom message
 * format that indicates this payload. The reader will extract bytes from the delivery and write them into a Core large
 * message file which is then routed into the broker as if received from a Core connection.
 */
public class AMQPTunneledCoreLargeMessageReader implements MessageReader {

   private final ProtonAbstractReceiver serverReceiver;

   private enum State {
      /**
       * Awaiting initial decode of first section in delivery which could be delivery annotations or could be the first
       * data section which will be the core message headers and properties.
       */
      INITIALIZING,
      /**
       * Accumulating the bytes from the remote that comprise the message headers and properties that must be decoded
       * before creating the large message instance.
       */
      CORE_HEADER_BUFFERING,
      /**
       * Awaiting a Data section that contains some or all of the bytes of the Core large message body, there can be
       * multiple Data sections if the size is greater than 2GB or the peer encodes them in smaller chunks
       */
      BODY_SECTION_PENDING,
      /**
       * Accumulating the actual large message payload into the large message file
       */
      BODY_BUFFERING,
      /**
       * The full message has been read and no more incoming bytes are accepted.
       */
      DONE,
      /**
       * Indicates the reader is closed and cannot be used until opened.
       */
      CLOSED
   }

   private final CompositeByteBuf pendingRecvBuffer = Unpooled.compositeBuffer();
   private final NettyReadable pendingReadable = new NettyReadable(pendingRecvBuffer);

   private DeliveryAnnotations deliveryAnnotations;
   private ByteBuf coreHeadersBuffer;
   private LargeServerMessage coreLargeMessage;
   private int largeMessageSectionRemaining;
   private State state = State.CLOSED;

   public AMQPTunneledCoreLargeMessageReader(ProtonAbstractReceiver serverReceiver) {
      this.serverReceiver = serverReceiver;
   }

   @Override
   public DeliveryAnnotations getDeliveryAnnotations() {
      return deliveryAnnotations;
   }

   @Override
   public void close() {
      if (state != State.CLOSED) {
         if (coreLargeMessage != null) {
            try {
               coreLargeMessage.deleteFile();
            } catch (Throwable error) {
               ActiveMQServerLogger.LOGGER.errorDeletingLargeMessageFile(error);
            } finally {
               coreLargeMessage = null;
            }
         }

         pendingRecvBuffer.clear();
         deliveryAnnotations = null;
         coreHeadersBuffer = null;
         largeMessageSectionRemaining = 0;

         state = State.CLOSED;
      }
   }

   @Override
   public AMQPTunneledCoreLargeMessageReader open() {
      if (state != State.CLOSED) {
         throw new IllegalStateException("Reader the reader was not closed before call to open.");
      }

      state = State.INITIALIZING;

      return this;
   }

   @Override
   public Message readBytes(Delivery delivery) throws Exception {
      if (state == State.CLOSED) {
         throw new IllegalStateException("Core over AMQP Large Message Reader is closed and read cannot proceed");
      }

      if (state == State.DONE) {
         throw new IllegalStateException("The reader already read a message and was not reset");
      }

      final Receiver receiver = ((Receiver) delivery.getLink());
      final ReadableBuffer recieved = receiver.recv();

      // Store what we read into a composite as we may need to hold onto some or all of
      // the received data until a complete type is available.
      pendingRecvBuffer.addComponent(true, Unpooled.wrappedBuffer(recieved.byteBuffer()));

      final DecoderImpl decoder = TLSEncode.getDecoder();
      decoder.setBuffer(pendingReadable);

      try {
         while (pendingRecvBuffer.isReadable()) {
            pendingRecvBuffer.markReaderIndex();

            try {
               if (state == State.CORE_HEADER_BUFFERING) {
                  tryReadHeadersAndProperties(pendingRecvBuffer);
               } else if (state == State.BODY_BUFFERING) {
                  tryReadMessageBody(delivery, pendingRecvBuffer);
               } else {
                  scanForNextMessageSection(decoder);
               }

               // Advance mark so read bytes can be discarded and we can start from this
               // location next time.
               pendingRecvBuffer.markReaderIndex();
            } catch (ActiveMQException ex) {
               throw ex;
            } catch (Exception e) {
               // We expect exceptions from proton when only partial section are received within
               // a frame so we will continue trying to decode until either we read a complete
               // section or we consume everything to the point of completing the delivery.
               if (delivery.isPartial()) {
                  pendingRecvBuffer.resetReaderIndex();
                  break; // Not enough data to decode yet.
               } else {
                  throw new ActiveMQAMQPInternalErrorException(
                     "Decoding error encounted in tunneled core large message.", e);
               }
            } finally {
               pendingRecvBuffer.discardReadComponents();
            }
         }

         if (!delivery.isPartial()) {
            if (coreLargeMessage == null) {
               throw new ActiveMQAMQPInternalErrorException(
                  "Tunneled Core large message delivery contained no large message body.");
            }

            final Message result = coreLargeMessage.toMessage();

            // We don't want a close to delete the file now, so we release these resources.
            coreLargeMessage.releaseResources(serverReceiver.getConnection().isLargeMessageSync(), true);
            coreLargeMessage = null;

            state = State.DONE;

            return result;
         }

         return null;
      } finally {
         decoder.setBuffer(null);
      }
   }

   private void scanForNextMessageSection(DecoderImpl decoder) throws ActiveMQException {
      final TypeConstructor<?> constructor = decoder.readConstructor();

      if (Header.class.equals(constructor.getTypeClass())) {
         constructor.skipValue(); // Ignore for forward compatibility
      } else if (DeliveryAnnotations.class.equals(constructor.getTypeClass())) {
         deliveryAnnotations = (DeliveryAnnotations) constructor.readValue();
      } else if (MessageAnnotations.class.equals(constructor.getTypeClass())) {
         constructor.skipValue(); // Ignore for forward compatibility
      } else if (Properties.class.equals(constructor.getTypeClass())) {
         constructor.skipValue(); // Ignore for forward compatibility
      } else if (ApplicationProperties.class.equals(constructor.getTypeClass())) {
         constructor.skipValue(); // Ignore for forward compatibility
      } else if (Data.class.equals(constructor.getTypeClass())) {
         // Store how much we need to read before the Data section payload is consumed.
         final int dataSectionRemaining = readNextDataSectionSize(pendingReadable);

         if (state.ordinal() < State.CORE_HEADER_BUFFERING.ordinal()) {
            coreHeadersBuffer = Unpooled.buffer(dataSectionRemaining, dataSectionRemaining);
            state = State.CORE_HEADER_BUFFERING;
         } else if (state.ordinal() < State.BODY_BUFFERING.ordinal()) {
            largeMessageSectionRemaining = dataSectionRemaining;
            state = State.BODY_BUFFERING;
         } else {
            throw new IllegalStateException("Data section found when not expecting any more input.");
         }
      } else if (AmqpValue.class.equals(constructor.getTypeClass())) {
         throw new IllegalArgumentException("Received an AmqpValue payload in core tunneled AMQP message");
      } else if (AmqpSequence.class.equals(constructor.getTypeClass())) {
         throw new IllegalArgumentException("Received an AmqpSequence payload in core tunneled AMQP message");
      } else if (Footer.class.equals(constructor.getTypeClass())) {
         if (coreLargeMessage == null) {
            throw new IllegalArgumentException("Received an Footer but no actual message paylod in core tunneled AMQP message");
         }

         constructor.skipValue(); // Ignore for forward compatibility
      }
   }

   // This reads the size for the encoded Binary that should follow a detected Data section and
   // leaves the buffer at the head of the payload of the Binary, readers from here should just
   // use the returned size to determine when all the binary payload is consumed.
   private static int readNextDataSectionSize(ReadableBuffer buffer) throws ActiveMQException {
      final byte encodingCode = buffer.get();

      return switch (encodingCode) {
         case EncodingCodes.VBIN8 -> buffer.get() & 0xFF;
         case EncodingCodes.VBIN32 -> buffer.getInt();
         case EncodingCodes.NULL -> 0;
         default -> throw new ActiveMQException("Expected Binary type but found encoding: " + encodingCode);
      };
   }

   private boolean tryReadHeadersAndProperties(ByteBuf buffer) throws Exception {
      final int writeSize = Math.min(coreHeadersBuffer.writableBytes(), buffer.readableBytes());

      if (writeSize > 0) {
         coreHeadersBuffer.writeBytes(buffer, writeSize);
      }

      // Have we read all the message headers and properties or is there more to come
      // we can't create the actual Core message until we get everything.
      if (coreHeadersBuffer.isWritable()) {
         return false;
      }

      try {
         final AMQPSessionCallback sessionSPI = serverReceiver.getSessionContext().getSessionSPI();
         final long id = sessionSPI.getStorageManager().generateID();
         final CoreMessage coreMessage = new CoreMessage();

         coreMessage.decodeHeadersAndProperties(coreHeadersBuffer);

         coreLargeMessage = sessionSPI.getStorageManager().createCoreLargeMessage(id, coreMessage);
         coreHeadersBuffer = null; // Buffer can be discarded once the decode is done
         state = State.BODY_SECTION_PENDING;
      } catch (ActiveMQException ex) {
         throw ex;
      } catch (Exception ex) {
         throw new ActiveMQAMQPInternalErrorException(
            "Encountered error while attempting to create a Core Large message instance", ex);
      }

      return true;
   }

   private void tryReadMessageBody(Delivery delivery, ByteBuf buffer) throws Exception {
      // Account for multiple Data section in the body, don't read full contents unless
      // the current readable is the full section contents or what's left of it.
      final int writeSize = Math.min(largeMessageSectionRemaining, buffer.readableBytes());

      try {
         final ActiveMQBuffer bodyBuffer = ActiveMQBuffers.wrappedBuffer(buffer.slice(buffer.readerIndex(), writeSize));

         coreLargeMessage.addBytes(bodyBuffer);
      } catch (ActiveMQException ex) {
         throw ex;
      } catch (Exception ex) {
         throw new ActiveMQAMQPInternalErrorException("Error while adding body bytes to Core Large message", ex);
      }

      largeMessageSectionRemaining -= writeSize;
      buffer.readerIndex(buffer.readerIndex() + writeSize);

      if (largeMessageSectionRemaining == 0) {
         state = State.BODY_SECTION_PENDING;
      }
   }
}
