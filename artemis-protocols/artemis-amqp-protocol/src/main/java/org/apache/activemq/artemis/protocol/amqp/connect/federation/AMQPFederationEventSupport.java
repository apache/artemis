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

package org.apache.activemq.artemis.protocol.amqp.connect.federation;

import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.EVENT_TYPE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.REQUESTED_ADDRESS_ADDED;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.REQUESTED_ADDRESS_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.REQUESTED_QUEUE_ADDED;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.REQUESTED_QUEUE_NAME;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolMessageBundle;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.codec.EncoderImpl;
import org.apache.qpid.proton.codec.WritableBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

/**
 * Tools used for sending and receiving events inside AMQP message instance.
 */
public final class AMQPFederationEventSupport {

   /**
    * Encode an event that indicates that a Queue that belongs to a federation request which was not present at the time
    * of the request or was later removed is now present and the remote should check for demand and attempt to federate
    * the resource once again.
    *
    * @param address The address that the queue is currently bound to.
    * @param queue   The queue that was part of a previous federation request.
    * @return the AMQP message with the encoded event data
    */
   public static AMQPMessage encodeQueueAddedEvent(String address, String queue) {
      final Map<Symbol, Object> annotations = new LinkedHashMap<>();
      final MessageAnnotations messageAnnotations = new MessageAnnotations(annotations);
      final Map<String, Object> eventMap = new LinkedHashMap<>();
      final Section sectionBody = new AmqpValue(eventMap);
      final ByteBuf buffer = PooledByteBufAllocator.DEFAULT.heapBuffer(1024);

      annotations.put(EVENT_TYPE, REQUESTED_QUEUE_ADDED);

      eventMap.put(REQUESTED_ADDRESS_NAME, address);
      eventMap.put(REQUESTED_QUEUE_NAME, queue);

      try {
         final EncoderImpl encoder = TLSEncode.getEncoder();
         encoder.setByteBuffer(new NettyWritable(buffer));
         encoder.writeObject(messageAnnotations);
         encoder.writeObject(sectionBody);

         final byte[] data = new byte[buffer.writerIndex()];
         buffer.readBytes(data);

         return new AMQPStandardMessage(0, data, null);
      } finally {
         TLSEncode.getEncoder().setByteBuffer((WritableBuffer) null);
         buffer.release();
      }
   }

   /**
    * Encode an event that indicates that an Address that belongs to a federation request which was not present at the
    * time of the request or was later removed is now present and the remote should check for demand and attempt to
    * federate the resource once again.
    *
    * @param address The address portion of the previously failed federation request
    * @return the AMQP message with the encoded event data
    */
   public static AMQPMessage encodeAddressAddedEvent(String address) {
      final Map<Symbol, Object> annotations = new LinkedHashMap<>();
      final MessageAnnotations messageAnnotations = new MessageAnnotations(annotations);
      final Map<String, Object> eventMap = new LinkedHashMap<>();
      final Section sectionBody = new AmqpValue(eventMap);
      final ByteBuf buffer = PooledByteBufAllocator.DEFAULT.heapBuffer(1024);

      annotations.put(EVENT_TYPE, REQUESTED_ADDRESS_ADDED);

      eventMap.put(REQUESTED_ADDRESS_NAME, address);

      try {
         final EncoderImpl encoder = TLSEncode.getEncoder();
         encoder.setByteBuffer(new NettyWritable(buffer));
         encoder.writeObject(messageAnnotations);
         encoder.writeObject(sectionBody);

         final byte[] data = new byte[buffer.writerIndex()];
         buffer.readBytes(data);

         return new AMQPStandardMessage(0, data, null);
      } finally {
         TLSEncode.getEncoder().setByteBuffer((WritableBuffer) null);
         buffer.release();
      }
   }

   /**
    * Decode and return the Map containing the event data for a Queue that was the target of a previous federation
    * request which was not present on the remote server or was later removed has now been (re)added.
    *
    * @param message The event message that carries the event data in its body.
    * @return a {@link Map} containing the payload of the incoming event
    * @throws ActiveMQException if an error occurs while decoding the event data.
    */
   @SuppressWarnings("unchecked")
   public static Map<String, Object> decodeQueueAddedEvent(AMQPMessage message) throws ActiveMQException {
      final Section body = message.getBody();

      if (!(body instanceof AmqpValue bodyValue)) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
            "Message body was not an AmqpValue type");
      }

      if (!(bodyValue.getValue() instanceof Map)) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
            "Message body AmqpValue did not carry an encoded Map");
      }

      try {
         final Map<String, Object> eventMap = (Map<String, Object>) bodyValue.getValue();

         if (!eventMap.containsKey(REQUESTED_ADDRESS_NAME)) {
            throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationEventMessage(
               "Message body did not carry the required address name");
         }

         if (!eventMap.containsKey(REQUESTED_QUEUE_NAME)) {
            throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationEventMessage(
               "Message body did not carry the required queue name");
         }

         return eventMap;
      } catch (ActiveMQException amqEx) {
         throw amqEx;
      } catch (Exception e) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
            "Invalid encoded queue added event entry: " + e.getMessage());
      }
   }

   /**
    * Decode and return the Map containing the event data for an Address that was the target of a previous federation
    * request which was not present on the remote server or was later removed has now been (re)added.
    *
    * @param message The event message that carries the event data in its body.
    * @return a {@link Map} containing the payload of the incoming event
    * @throws ActiveMQException if an error occurs while decoding the event data.
    */
   @SuppressWarnings("unchecked")
   public static Map<String, Object> decodeAddressAddedEvent(AMQPMessage message) throws ActiveMQException {
      final Section body = message.getBody();

      if (!(body instanceof AmqpValue bodyValue)) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
            "Message body was not an AmqpValue type");
      }

      if (!(bodyValue.getValue() instanceof Map)) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
            "Message body AmqpValue did not carry an encoded Map");
      }

      try {
         final Map<String, Object> eventMap = (Map<String, Object>) bodyValue.getValue();

         if (!eventMap.containsKey(REQUESTED_ADDRESS_NAME)) {
            throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationEventMessage(
               "Message body did not carry the required address name");
         }

         return eventMap;
      } catch (ActiveMQException amqEx) {
         throw amqEx;
      } catch (Exception e) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
            "Invalid encoded address added event entry: " + e.getMessage());
      }
   }
}
