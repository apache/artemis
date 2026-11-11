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

import static org.apache.activemq.artemis.protocol.amqp.proton.AMQPTunneledMessageConstants.AMQP_TUNNELED_CORE_MESSAGE_FORMAT;

import java.lang.invoke.MethodHandles;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.core.server.MessageReference;
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
import io.netty.buffer.Unpooled;

/**
 * Writer of tunneled Core messages that will be written as the body of an AMQP delivery with a custom message format
 * that indicates this payload. The writer will encode the bytes from the Core large message file and write them into an
 * AMQP Delivery that will be sent across to the remote peer where it can be processed and a Core message recreated for
 * dispatch as if it had been sent from a Core connection.
 */
public class AMQPTunneledCoreMessageWriter implements MessageWriter {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final byte DATA_DESCRIPTOR = 0x75;
   private static final int DATA_SECTION_ENCODING_BYTES = Long.BYTES;

   private final ProtonServerSenderContext serverSender;
   private final Sender protonSender;

   public AMQPTunneledCoreMessageWriter(ProtonServerSenderContext serverSender) {
      this.serverSender = serverSender;
      this.protonSender = serverSender.getSender();
   }

   @Override
   public void writeBytes(MessageReference messageReference) {
      if (protonSender.getLocalState() == EndpointState.CLOSED) {
         logger.debug("Not delivering message {} as the sender is closed and credits were available, if you see too many of these it means clients are issuing credits and closing the connection with pending credits a lot of times", messageReference);
         return;
      }

      try {
         final ICoreMessage message = (ICoreMessage) messageReference.getMessage();
         final int encodedSize = message.getPersistSize();
         final ByteBuf buffer = Unpooled.buffer(encodedSize + DATA_SECTION_ENCODING_BYTES); // Account for the data section
         final Delivery delivery = serverSender.createDelivery(messageReference, AMQP_TUNNELED_CORE_MESSAGE_FORMAT);

         final DeliveryAnnotations annotations = messageReference.getProtocolData(DeliveryAnnotations.class);
         if (annotations != null && annotations.getValue() != null && !annotations.getValue().isEmpty()) {
            final EncoderImpl encoder = TLSEncode.getEncoder();

            try {
               encoder.setByteBuffer(new NettyWritable(buffer));
               encoder.writeObject(annotations);
            } finally {
               encoder.setByteBuffer((WritableBuffer) null);
            }
         }

         // This encoding would work up to a Core message that encodes to but does not exceed
         // 2 GB in which case we'd need to send multiple data sections but this would be unlikely
         // to succeed and Large message handling should have been in place for such messages.

         buffer.writeByte(EncodingCodes.DESCRIBED_TYPE_INDICATOR);
         buffer.writeByte(EncodingCodes.SMALLULONG);
         buffer.writeByte(DATA_DESCRIPTOR);
         buffer.writeByte(EncodingCodes.VBIN32);
         buffer.writeInt(encodedSize); // Core message will encode into this size.

         final ActiveMQBuffer bufferWrapper = ActiveMQBuffers.wrappedBuffer(buffer);

         message.persist(bufferWrapper);

         // Update the buffer that was allocated with the bytes that were written using the wrapper
         // since the wrapper doesn't update the wrapper buffer.
         buffer.writerIndex(buffer.writerIndex() + encodedSize);

         // Don't have pooled content, no need to release or copy.
         protonSender.sendNoCopy(new ReadableBuffer.ByteBufferReader(buffer.nioBuffer()));

         serverSender.reportDeliveryComplete(this, messageReference, delivery, false);
      } catch (Exception deliveryError) {
         serverSender.reportDeliveryError(this, messageReference, deliveryError);
      }
   }
}
