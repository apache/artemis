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

package org.apache.activemq.artemis.jms.client.compatible1X;

import javax.jms.Destination;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQBytesMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.jms.client.ActiveMQMapMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQObjectMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQStreamMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.jms.client.ConnectionFactoryOptions;
import org.apache.activemq.artemis.reader.MessageUtil;

public class ActiveMQCompatibleMessage extends ActiveMQMessage {

   public ActiveMQCompatibleMessage(byte type, ClientSession session) {
      super(type, session);
   }

   public ActiveMQCompatibleMessage(ClientSession session) {
      super(session);
   }

   public ActiveMQCompatibleMessage(ClientMessage message, ClientSession session) {
      super(message, session);
   }

   public ActiveMQCompatibleMessage(Message foreign, ClientSession session) throws JMSException {
      super(foreign, session);
   }

   public ActiveMQCompatibleMessage() {
   }

   public ActiveMQCompatibleMessage(Message foreign, byte type, ClientSession session) throws JMSException {
      super(foreign, type, session);
   }

   @Override
   public Destination getJMSReplyTo() throws JMSException {
      if (replyTo == null) {
         replyTo = findCompatibleReplyTo(message);
      }
      return replyTo;
   }

   @Override
   public void setJMSReplyTo(Destination dest) throws JMSException {
      replyTo = setCompatibleReplyTo(dest, message);
   }

   static Destination setCompatibleReplyTo(Destination dest, ClientMessage message) throws InvalidDestinationException {
      if (dest == null) {
         MessageUtil.setJMSReplyTo(message, (String) null);
         return null;
      } else {
         if (dest instanceof ActiveMQDestination == false) {
            throw new InvalidDestinationException("Foreign destination " + dest);
         }
         ActiveMQDestination jbd = (ActiveMQDestination) dest;
         final String address = jbd.getAddress();
         if (hasPrefix1X(address)) {
            MessageUtil.setJMSReplyTo(message, jbd.getAddress());
         } else {
            String prefix = prefixOf(dest);
            MessageUtil.setJMSReplyTo(message, prefix + jbd.getAddress());
         }
         return jbd;
      }
   }

   static Destination findCompatibleReplyTo(ClientMessage message) {
      SimpleString address = MessageUtil.getJMSReplyTo(message);
      if (address != null) {
         final SimpleString checkedAddress = checkPrefix1X(address);
         if (checkedAddress != null) {
            return ActiveMQDestination.fromPrefixed1XName(address.toString(), checkedAddress.toString());
         }
         String name = address.toString();
         // swap the old prefixes for the new ones so the proper destination type gets created
         if (address.startsWith(OLD_QUEUE_QUALIFIED_PREFIX)) {
            name = address.subSeq(OLD_QUEUE_QUALIFIED_PREFIX.length(), address.length()).toString();
         } else if (address.startsWith(OLD_TEMP_QUEUE_QUALIFED_PREFIX)) {
            name = address.subSeq(OLD_TEMP_QUEUE_QUALIFED_PREFIX.length(), address.length()).toString();
         } else if (address.startsWith(OLD_TOPIC_QUALIFIED_PREFIX)) {
            name = address.subSeq(OLD_TOPIC_QUALIFIED_PREFIX.length(), address.length()).toString();
         } else if (address.startsWith(OLD_TEMP_TOPIC_QUALIFED_PREFIX)) {
            name = address.subSeq(OLD_TEMP_TOPIC_QUALIFED_PREFIX.length(), address.length()).toString();
         }
         return ActiveMQDestination.fromPrefixedName(address.toString(), name);
      }

      return null;
   }

   @Override
   public SimpleString checkPrefix(SimpleString address) {
      return checkPrefix1X(address);
   }

   private static boolean hasPrefix1X(String address) {
      if (address != null) {
         if (address.startsWith(PacketImpl.OLD_QUEUE_PREFIX.toString())) {
            return true;
         } else if (address.startsWith(PacketImpl.OLD_TEMP_QUEUE_PREFIX.toString())) {
            return true;
         } else if (address.startsWith(PacketImpl.OLD_TOPIC_PREFIX.toString())) {
            return true;
         } else if (address.startsWith(PacketImpl.OLD_TEMP_TOPIC_PREFIX.toString())) {
            return true;
         }
      }

      return false;
   }

   protected static SimpleString checkPrefix1X(SimpleString address) {
      if (address != null) {
         if (address.startsWith(PacketImpl.OLD_QUEUE_PREFIX)) {
            return address.subSeq(PacketImpl.OLD_QUEUE_PREFIX.length(), address.length());
         } else if (address.startsWith(PacketImpl.OLD_TEMP_QUEUE_PREFIX)) {
            return address.subSeq(PacketImpl.OLD_TEMP_QUEUE_PREFIX.length(), address.length());
         } else if (address.startsWith(PacketImpl.OLD_TOPIC_PREFIX)) {
            return address.subSeq(PacketImpl.OLD_TOPIC_PREFIX.length(), address.length());
         } else if (address.startsWith(PacketImpl.OLD_TEMP_TOPIC_PREFIX)) {
            return address.subSeq(PacketImpl.OLD_TEMP_TOPIC_PREFIX.length(), address.length());
         }
      }

      return null;
   }

   public static ActiveMQMessage createMessage(final ClientMessage message,
                                               final ClientSession session,
                                               final ConnectionFactoryOptions options) {
      int type = message.getType();

      ActiveMQMessage msg;

      switch (type) {
         case ActiveMQMessage.TYPE: { // 0
            msg = new ActiveMQCompatibleMessage(message, session);
            break;
         }
         case ActiveMQBytesMessage.TYPE: { // 4
            msg = new ActiveMQBytesCompatibleMessage(message, session);
            break;
         }
         case ActiveMQMapMessage.TYPE: { // 5
            msg = new ActiveMQMapCompatibleMessage(message, session);
            break;
         }
         case ActiveMQObjectMessage.TYPE: {
            msg = new ActiveMQObjectCompatibleMessage(message, session, options);
            break;
         }
         case ActiveMQStreamMessage.TYPE: { // 6
            msg = new ActiveMQStreamCompatibleMessage(message, session);
            break;
         }
         case ActiveMQTextMessage.TYPE: { // 3
            msg = new ActiveMQTextCompatibleMessage(message, session);
            break;
         }
         default: {
            throw new JMSRuntimeException("Invalid message type " + type);
         }
      }

      return msg;
   }

}
