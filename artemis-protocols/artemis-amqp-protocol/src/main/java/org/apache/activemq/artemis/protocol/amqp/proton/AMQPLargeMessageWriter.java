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

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.core.message.LargeBodyReader;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPLargeMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessageBrokerAccessor;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.broker.ActiveMQProtonRemotingConnection;
import org.apache.activemq.artemis.protocol.amqp.util.NettyReadable;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.amqp.messaging.Header;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.codec.WritableBuffer;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

/**
 * A writer of {@link AMQPLargeMessage} content that handles the read from large message file and write into the AMQP
 * sender with some respect for the AMQP frame size in use by this connection.
 */
public class AMQPLargeMessageWriter implements MessageWriter {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final ProtonServerSenderContext serverSender;
   private final AMQPConnectionContext connection;
   private final AMQPSessionCallback sessionSPI;
   private final Sender protonSender;

   private MessageReference reference;
   private AMQPLargeMessage message;

   private LargeBodyReader largeBodyReader;

   private Delivery delivery;
   private long position;
   private boolean initialPacketHandled;

   private volatile boolean closed = true;

   public AMQPLargeMessageWriter(ProtonServerSenderContext serverSender) {
      this.serverSender = serverSender;
      this.connection = serverSender.getSessionContext().getAMQPConnectionContext();
      this.sessionSPI = serverSender.getSessionContext().getSessionSPI();
      this.protonSender = serverSender.getSender();
   }

   @Override
   public boolean isWriting() {
      return !closed;
   }

   @Override
   public void close() {
      if (!closed) {
         try {
            try {
               if (largeBodyReader != null) {
                  largeBodyReader.close();
               }
            } catch (Exception e) {
               // if we get an error only at this point, there's nothing else we could do other than log.warn
               logger.warn("{}", e.getMessage(), e);
            }
            if (message != null) {
               message.usageDown();
            }
         } finally {
            resetClosed();
         }
      }
   }

   @Override
   public AMQPLargeMessageWriter open(MessageReference reference) {
      if (!closed) {
         throw new IllegalStateException("Trying to open an AMQP Large Message writer that was not closed");
      }

      this.reference = reference;
      this.message = (AMQPLargeMessage) reference.getMessage();
      this.message.usageUp();

      try {
         largeBodyReader = message.getLargeBodyReader();
         largeBodyReader.open();
      } catch (Exception e) {
         serverSender.reportDeliveryError(this, reference, e);
      }

      resetOpen();

      return this;
   }

   private void resetClosed() {
      message = null;
      reference = null;
      delivery = null;
      largeBodyReader = null;
      position = 0;
      initialPacketHandled = false;
      closed = true;
   }

   private void resetOpen() {
      position = 0;
      initialPacketHandled = false;
      closed = false;
   }

   @Override
   public void accept(MessageReference messageReference) {
      if (serverSender.isClosed()) {
         logger.trace("Server sender was closed before queued write attempt was executed");
      } else {
         writeBytes(messageReference);
      }
   }

   @Override
   public void writeBytes(MessageReference messageReference) {
      if (protonSender.getLocalState() == EndpointState.CLOSED) {
         logger.debug("Not delivering message {} as the sender is closed and credits were available, if you see too many of these it means clients are issuing credits and closing the connection with pending credits a lot of times", messageReference);
         return;
      }

      if (closed) {
         throw new IllegalStateException("Cannot write to an AMQP Large Message Writer that has been closed");
      }

      if (sessionSPI.invokeOutgoing(message, (ActiveMQProtonRemotingConnection) sessionSPI.getTransportConnection().getProtocolConnection()) != null) {
         // an interceptor rejected the delivery
         // since we opened the message as part of the queue executor we must close it now
         close();
         return;
      }

      this.delivery = serverSender.createDelivery(messageReference, (int) this.message.getMessageFormat());

      tryDelivering();
   }

   /**
    * Used to provide re-entry from the flow control executor when IO back-pressure has eased
    */
   private void resume() {
      connection.runLater(this::tryDelivering);
   }

   private void tryDelivering() {
      if (closed) {
         logger.trace("AMQP Large Message Writer was closed before queued write attempt was executed");
         return;
      }

      if (serverSender.isClosed()) {
         logger.trace("Server sender was closed before queued write attempt was executed");
         return;
      }

      // This is discounting some bytes due to Transfer payload
      final int frameSize = protonSender.getSession().getConnection().getTransport().getOutboundFrameSizeLimit() - 50 - (delivery.getTag() != null ? delivery.getTag().length : 0);

      try {
         final ByteBuf frameBuffer = PooledByteBufAllocator.DEFAULT.directBuffer(frameSize, frameSize);
         final NettyReadable frameView = new NettyReadable(frameBuffer);

         try {
            largeBodyReader.position(position);
            long bodySize = largeBodyReader.getSize();
            // materialize it so we can use its internal NIO buffer
            frameBuffer.ensureWritable(frameSize);

            if (!initialPacketHandled && protonSender.getLocalState() != EndpointState.CLOSED) {
               if (!deliverInitialPacket(largeBodyReader, frameBuffer)) {
                  return;
               }

               initialPacketHandled = true;
            }

            for (; protonSender.getLocalState() != EndpointState.CLOSED && position < bodySize; ) {
               if (!connection.flowControl(this::resume)) {
                  return;
               }
               frameBuffer.clear();

               final int readSize = largeBodyReader.readInto(frameBuffer.internalNioBuffer(0, frameSize));

               frameBuffer.writerIndex(readSize);

               protonSender.send(frameView);

               position += readSize;

               if (readSize > 0) {
                  if (position < bodySize) {
                     connection.instantFlush();
                  }
               }
            }
         } finally {
            frameBuffer.release();
         }

         serverSender.reportDeliveryComplete(this, reference, delivery, true);
      } catch (Exception deliveryError) {
         serverSender.reportDeliveryError(this, reference, deliveryError);
      }
   }

   private boolean deliverInitialPacket(final LargeBodyReader context, final ByteBuf frameBuffer) throws Exception {
      assert position == 0 && context.position() == 0 && !initialPacketHandled;

      if (!connection.flowControl(this::resume)) {
         return false;
      }

      frameBuffer.clear();

      message.checkReference(reference);

      DeliveryAnnotations deliveryAnnotationsToEncode = reference.getProtocolData(DeliveryAnnotations.class);

      try {
         replaceInitialHeader(deliveryAnnotationsToEncode, context, new NettyWritable(frameBuffer));
      } catch (IndexOutOfBoundsException indexOutOfBoundsException) {
         assert position == 0 : "this shouldn't happen unless replaceInitialHeader is updating position before modifying frameBuffer";
         logger.debug("Delivery of message failed with an overFlowException, retrying again with expandable buffer");

         // on the very first packet, if the initial header was replaced with a much bigger header
         // (re-encoding) we could recover the situation with a retry using an expandable buffer.
         // this is tested on org.apache.activemq.artemis.tests.integration.amqp.AmqpMessageDivertsTest
         sendAndFlushInitialPacket(deliveryAnnotationsToEncode, context);
         return true;
      }

      int readSize = 0;
      final int writableBytes = frameBuffer.writableBytes();
      if (writableBytes != 0) {
         final int writtenBytes = frameBuffer.writerIndex();
         readSize = context.readInto(frameBuffer.internalNioBuffer(writtenBytes, writableBytes));
         if (readSize > 0) {
            frameBuffer.writerIndex(writtenBytes + readSize);
         }
      }

      protonSender.send(new NettyReadable(frameBuffer));
      if (readSize > 0) {
         position += readSize;
      }
      connection.instantFlush();
      return true;
   }

   /**
    * This must be used when either the delivery annotations or re-encoded buffer is bigger than the frame size.
    * <p>
    * This will create one expandable buffer, send and flush it.
    */
   private void sendAndFlushInitialPacket(DeliveryAnnotations deliveryAnnotationsToEncode, LargeBodyReader context) throws Exception {
      // if the buffer overflow happened during the initial position
      // this means the replaced headers are bigger then the frame size
      // on this case we do with an expandable netty buffer
      final ByteBuf nettyBuffer = PooledByteBufAllocator.DEFAULT.directBuffer(AMQPMessageBrokerAccessor.getRemainingBodyPosition(message) * 2);
      try {
         replaceInitialHeader(deliveryAnnotationsToEncode, context, new NettyWritable(nettyBuffer));
         protonSender.send(new NettyReadable(nettyBuffer));
      } finally {
         nettyBuffer.release();
         connection.instantFlush();
      }
   }

   private int replaceInitialHeader(DeliveryAnnotations deliveryAnnotationsToEncode, LargeBodyReader context, WritableBuffer buf) throws Exception {
      TLSEncode.getEncoder().setByteBuffer(buf);
      try {
         int proposedPosition = writeHeaderAndAnnotations(deliveryAnnotationsToEncode);
         if (message.isReencoded()) {
            proposedPosition = writeMessageAnnotationsPropertiesAndApplicationProperties(context, message);
         }

         context.position(proposedPosition);
         position = proposedPosition;
         return (int) position;
      } finally {
         TLSEncode.getEncoder().setByteBuffer((WritableBuffer) null);
      }
   }

   /**
    * Write properties and application properties when the message is flagged as re-encoded.
    */
   private int writeMessageAnnotationsPropertiesAndApplicationProperties(LargeBodyReader context, AMQPLargeMessage message) throws Exception {
      int bodyPosition = AMQPMessageBrokerAccessor.getRemainingBodyPosition(message);
      assert bodyPosition > 0;
      writeMessageAnnotationsPropertiesAndApplicationPropertiesInternal(message);
      return bodyPosition;
   }

   private void writeMessageAnnotationsPropertiesAndApplicationPropertiesInternal(AMQPLargeMessage message) {
      MessageAnnotations messageAnnotations = AMQPMessageBrokerAccessor.getDecodedMessageAnnotations(message);

      if (messageAnnotations != null) {
         TLSEncode.getEncoder().writeObject(messageAnnotations);
      }

      Properties amqpProperties = AMQPMessageBrokerAccessor.getCurrentProperties(message);
      if (amqpProperties != null) {
         TLSEncode.getEncoder().writeObject(amqpProperties);
      }

      ApplicationProperties applicationProperties = AMQPMessageBrokerAccessor.getDecodedApplicationProperties(message);

      if (applicationProperties != null) {
         TLSEncode.getEncoder().writeObject(applicationProperties);
      }
   }

   private int writeHeaderAndAnnotations(DeliveryAnnotations deliveryAnnotationsToEncode) {
      Header header = AMQPMessageBrokerAccessor.getCurrentHeader(message);
      if (header != null) {
         TLSEncode.getEncoder().writeObject(header);
      }
      if (deliveryAnnotationsToEncode != null) {
         TLSEncode.getEncoder().writeObject(deliveryAnnotationsToEncode);
      }
      return message.getPositionAfterDeliveryAnnotations();
   }
}
