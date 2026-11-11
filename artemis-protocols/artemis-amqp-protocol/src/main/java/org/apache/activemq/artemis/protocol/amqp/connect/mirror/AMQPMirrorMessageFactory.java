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
package org.apache.activemq.artemis.protocol.amqp.connect.mirror;

import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Header;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.codec.EncoderImpl;
import org.apache.qpid.proton.codec.WritableBuffer;

import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.ACK_REASON;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.ADDRESS;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.BROKER_ID;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.EVENT_TYPE;
import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.QUEUE;

/**
 * This class is responsible for creating the internal message types used on controlling the mirror on the AMQP module.
 */
public class AMQPMirrorMessageFactory {

   /**
    * This method is open to make it testable. Do not use on your applications.
    */
   public static Message createMessage(String to, SimpleString address, SimpleString queue, Object event, String brokerID, Object body, AckReason ackReason) {
      Header header = new Header();
      header.setDurable(true);

      Map<Symbol, Object> annotations = new HashMap<>();
      annotations.put(EVENT_TYPE, event);

      if (ackReason != null && ackReason != AckReason.NORMAL) {
         // if the ack reason is normal, we just send it null as the target will assume it as normal
         // just to save some bits
         annotations.put(ACK_REASON, ackReason.getVal());
      }

      if (address != null) {
         annotations.put(ADDRESS, address.toString());
      }
      if (brokerID != null) {
         annotations.put(BROKER_ID, brokerID);
      }
      if (queue != null) {
         annotations.put(QUEUE, queue.toString());
      }
      MessageAnnotations messageAnnotations = new MessageAnnotations(annotations);

      Section sectionBody = body != null ? new AmqpValue(body) : null;

      ByteBuf buffer = PooledByteBufAllocator.DEFAULT.heapBuffer(1024);

      try {
         EncoderImpl encoder = TLSEncode.getEncoder();
         encoder.setByteBuffer(new NettyWritable(buffer));
         encoder.writeObject(header);
         encoder.writeObject(messageAnnotations);
         if (sectionBody != null) {
            encoder.writeObject(sectionBody);
         }

         byte[] data = new byte[buffer.writerIndex()];
         buffer.readBytes(data);

         AMQPMessage amqpMessage = new AMQPStandardMessage(0, data, null);
         amqpMessage.setAddress(to);
         return amqpMessage;

      } finally {
         TLSEncode.getEncoder().setByteBuffer((WritableBuffer) null);
         buffer.release();
      }
   }

}
