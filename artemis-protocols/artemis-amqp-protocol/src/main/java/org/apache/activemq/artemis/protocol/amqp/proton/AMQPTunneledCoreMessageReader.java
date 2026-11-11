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

import java.nio.ByteBuffer;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.qpid.proton.amqp.Binary;
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
import org.apache.qpid.proton.codec.ReadableBuffer;
import org.apache.qpid.proton.codec.TypeConstructor;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Receiver;

/**
 * Reader of tunneled Core message that have been written as the body of an AMQP delivery with a custom message format
 * that indicates this payload. The reader will extract bytes from the delivery and decode from them a standard Core
 * message which is then routed into the broker as if received from a Core connection.
 */
public class AMQPTunneledCoreMessageReader implements MessageReader {

   private final ProtonAbstractReceiver serverReceiver;

   private boolean closed = true;
   private DeliveryAnnotations deliveryAnnotations;

   public AMQPTunneledCoreMessageReader(ProtonAbstractReceiver serverReceiver) {
      this.serverReceiver = serverReceiver;
   }

   @Override
   public DeliveryAnnotations getDeliveryAnnotations() {
      return deliveryAnnotations;
   }

   @Override
   public void close() {
      closed = true;
      deliveryAnnotations = null;
   }

   @Override
   public MessageReader open() {
      if (!closed) {
         throw new IllegalStateException("Message reader must be properly closed before open call");
      }

      return this;
   }

   @Override
   public Message readBytes(Delivery delivery) {
      if (delivery.isPartial()) {
         return null; // Only receive payload when complete
      }

      final AMQPSessionCallback sessionSPI = serverReceiver.getSessionContext().getSessionSPI();
      final Receiver receiver = ((Receiver) delivery.getLink());
      final ReadableBuffer recievedBuffer = receiver.recv();

      if (recievedBuffer.remaining() == 0) {
         throw new IllegalArgumentException("Received empty delivery when expecting a core message encoding");
      }

      final DecoderImpl decoder = TLSEncode.getDecoder();

      decoder.setBuffer(recievedBuffer);

      Data payloadData = null;

      try {
         while (recievedBuffer.hasRemaining()) {
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
               if (payloadData != null) {
                  throw new IllegalArgumentException("Received an unexpected additional Data section in core tunneled AMQP message");
               }

               payloadData = (Data) constructor.readValue();
            } else if (AmqpValue.class.equals(constructor.getTypeClass())) {
               throw new IllegalArgumentException("Received an AmqpValue payload in core tunneled AMQP message");
            } else if (AmqpSequence.class.equals(constructor.getTypeClass())) {
               throw new IllegalArgumentException("Received an AmqpSequence payload in core tunneled AMQP message");
            } else if (Footer.class.equals(constructor.getTypeClass())) {
               if (payloadData == null) {
                  throw new IllegalArgumentException("Received an Footer but no actual message payload in core tunneled AMQP message");
               }

               constructor.skipValue(); // Ignore for forward compatibility
            }
         }
      } finally {
         decoder.setBuffer(null);
      }

      if (payloadData == null) {
         throw new IllegalArgumentException("Did not receive a Data section payload in core tunneled AMQP message");
      }

      final Binary payloadBinary = payloadData.getValue();

      if (payloadBinary == null || payloadBinary.getLength() <= 0) {
         throw new IllegalArgumentException("Received an unexpected empty message payload in core tunneled AMQP message");
      }

      final ByteBuffer payload = payloadBinary.asByteBuffer();
      final ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(payload);

      // Ensure the wrapped buffer readable bytes reflects the data section payload read.
      buffer.writerIndex(payload.remaining());

      final CoreMessage coreMessage = new CoreMessage(sessionSPI.getCoreMessageObjectPools());

      coreMessage.reloadPersistence(buffer, sessionSPI.getCoreMessageObjectPools());
      coreMessage.setMessageID(sessionSPI.getStorageManager().generateID());

      return coreMessage;
   }
}
