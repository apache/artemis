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

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.codec.ReadableBuffer;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Receiver;

/**
 * Reader of AMQP (non-large) messages which reads all bytes and decodes once a non-partial
 * delivery is read.
 */
public class AMQPMessageReader implements MessageReader {

   private final ProtonAbstractReceiver serverReceiver;

   private DeliveryAnnotations deliveryAnnotations;
   private boolean closed = true;

   public AMQPMessageReader(ProtonAbstractReceiver serverReceiver) {
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

      final Receiver receiver = ((Receiver) delivery.getLink());
      final ReadableBuffer payload = receiver.recv();

      final AMQPMessage message = serverReceiver.getSessionContext().getSessionSPI().createStandardMessage(delivery, payload);

      deliveryAnnotations = message.getDeliveryAnnotations();

      return message;
   }
}
