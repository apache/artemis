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

import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.broker.ActiveMQProtonRemotingConnection;
import org.apache.activemq.artemis.protocol.amqp.converter.CoreAmqpConverter;
import org.apache.activemq.artemis.protocol.amqp.util.NettyReadable;
import org.apache.qpid.proton.codec.ReadableBuffer;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An writer of AMQP (non-large) messages or messages which will convert any non-AMQP message to AMQP before writing the
 * encoded bytes into the AMQP sender.
 */
public class AMQPMessageWriter implements MessageWriter {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final ProtonServerSenderContext serverSender;
   private final AMQPSessionCallback sessionSPI;
   private final Sender protonSender;

   public AMQPMessageWriter(ProtonServerSenderContext serverSender) {
      this.serverSender = serverSender;
      this.sessionSPI = serverSender.getSessionContext().getSessionSPI();
      this.protonSender = serverSender.getSender();
   }

   @Override
   public void writeBytes(MessageReference messageReference) {
      if (protonSender.getLocalState() == EndpointState.CLOSED) {
         logger.debug("Not delivering message {} as the sender is closed and credits were available, if you see too many of these it means clients are issuing credits and closing the connection with pending credits a lot of times", messageReference);
         return;
      }

      try {
         final AMQPMessage amqpMessage = CoreAmqpConverter.checkAMQP(messageReference.getMessage(), null);

         if (sessionSPI.invokeOutgoing(amqpMessage, (ActiveMQProtonRemotingConnection) sessionSPI.getTransportConnection().getProtocolConnection()) != null) {
            return;
         }

         final Delivery delivery = serverSender.createDelivery(messageReference, (int) amqpMessage.getMessageFormat());
         final ReadableBuffer sendBuffer = amqpMessage.getSendBuffer(messageReference.getDeliveryCount(), messageReference);

         boolean releaseRequired = sendBuffer instanceof NettyReadable;

         try {
            if (releaseRequired) {
               protonSender.send(sendBuffer);
               // Above send copied, so release now if needed
               releaseRequired = false;
               ((NettyReadable) sendBuffer).getByteBuf().release();
            } else {
               // Don't have pooled content, no need to release or copy.
               protonSender.sendNoCopy(sendBuffer);
            }

            serverSender.reportDeliveryComplete(this, messageReference, delivery, false);
         } finally {
            if (releaseRequired) {
               ((NettyReadable) sendBuffer).getByteBuf().release();
            }
         }
      } catch (Exception deliveryError) {
         serverSender.reportDeliveryError(this, messageReference, deliveryError);
      }
   }
}
