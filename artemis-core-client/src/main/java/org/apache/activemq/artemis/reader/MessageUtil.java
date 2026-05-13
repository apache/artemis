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
package org.apache.activemq.artemis.reader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQPropertyConversionException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;

/**
 * static methods intended for import static on JMS like messages.
 *
 * This provides a helper for core message to act some of the JMS functions used by the JMS wrapper
 */
public class MessageUtil {

   public static final String CORRELATIONID_HEADER_NAME_STRING = "JMSCorrelationID";

   public static final SimpleString CORRELATIONID_HEADER_NAME = SimpleString.of(CORRELATIONID_HEADER_NAME_STRING);

   public static final SimpleString REPLYTO_HEADER_NAME = SimpleString.of("JMSReplyTo");

   public static final String TYPE_HEADER_NAME_STRING = "JMSType";

   public static final SimpleString TYPE_HEADER_NAME = SimpleString.of(TYPE_HEADER_NAME_STRING);

   public static final SimpleString JMS = SimpleString.of("JMS");

   public static final SimpleString JMSX = SimpleString.of("JMSX");

   public static final SimpleString JMS_ = SimpleString.of("JMS_");

   public static final String JMSXDELIVERYCOUNT = "JMSXDeliveryCount";

   public static final String JMSXGROUPID = "JMSXGroupID";

   public static final String JMSXGROUPSEQ = "JMSXGroupSeq";

   public static final String JMSXUSERID = "JMSXUserID";

   public static final String CONNECTION_ID_PROPERTY_NAME_STRING = "__AMQ_CID";

   public static final SimpleString CONNECTION_ID_PROPERTY_NAME = SimpleString.of(CONNECTION_ID_PROPERTY_NAME_STRING);

   //   public static ActiveMQBuffer getBodyBuffer(Message message) {
   //      return message.getBodyBuffer();
   //   }

   public static byte[] getJMSCorrelationIDAsBytes(Message message) {
      Object obj = message.getObjectProperty(CORRELATIONID_HEADER_NAME);

      if (obj instanceof byte[] bytes) {
         return bytes;
      } else {
         return null;
      }
   }

   public static void setJMSType(Message message, String type) {
      message.putStringProperty(TYPE_HEADER_NAME, SimpleString.of(type));
   }

   public static String getJMSType(Message message) {
      SimpleString ss = message.getSimpleStringProperty(TYPE_HEADER_NAME);

      if (ss != null) {
         return ss.toString();
      } else {
         return null;
      }
   }

   public static final void setJMSCorrelationIDAsBytes(Message message,
                                                       final byte[] correlationID) throws ActiveMQException {
      if (correlationID == null || correlationID.length == 0) {
         throw new ActiveMQException("Please specify a non-zero length byte[]");
      }
      message.putBytesProperty(CORRELATIONID_HEADER_NAME, correlationID);
   }

   public static void setJMSCorrelationID(Message message, final String correlationID) {
      if (correlationID == null) {
         message.removeProperty(CORRELATIONID_HEADER_NAME);
      } else {
         message.putStringProperty(CORRELATIONID_HEADER_NAME, SimpleString.of(correlationID));
      }
   }

   public static String getJMSCorrelationID(Message message) {
      try {
         return message.getStringProperty(CORRELATIONID_HEADER_NAME);
      } catch (ActiveMQPropertyConversionException e) {
         return null;
      }
   }

   public static SimpleString getJMSReplyTo(Message message) {
      return message.getSimpleStringProperty(REPLYTO_HEADER_NAME);
   }

   public static void setJMSReplyTo(Message message, final String dest) {
      if (dest == null) {
         message.removeProperty(REPLYTO_HEADER_NAME);
      } else {
         message.putStringProperty(REPLYTO_HEADER_NAME, dest);
      }
   }

   public static void setJMSReplyTo(Message message, final SimpleString dest) {
      if (dest == null) {
         message.removeProperty(REPLYTO_HEADER_NAME);
      } else {
         message.putStringProperty(REPLYTO_HEADER_NAME, dest);
      }
   }

   public static void clearProperties(Message message) {
      /*
       * JavaDoc for this method states:
       *    Clears a message's properties.
       *    The message's header fields and body are not cleared.
       *
       * Since the {@code Message.HDR_ROUTING_TYPE} is used for the JMSDestination header it isn't cleared
       */

      List<SimpleString> toRemove = new ArrayList<>();

      for (SimpleString propName : message.getPropertyNames()) {
         if ((!propName.startsWith(JMS) || propName.startsWith(JMSX) ||
            propName.startsWith(JMS_)) && !propName.equals(Message.HDR_ROUTING_TYPE)) {
            toRemove.add(propName);
         }
      }

      for (SimpleString propName : toRemove) {
         message.removeProperty(propName);
      }
   }

   public static Set<String> getPropertyNames(Message message) {
      Set<String> set = new HashSet<>();

      for (SimpleString propName : message.getPropertyNames()) {
         if (propName.equals(Message.HDR_GROUP_ID)) {
            set.add(MessageUtil.JMSXGROUPID);
         } else if (propName.equals(Message.HDR_GROUP_SEQUENCE)) {
            set.add(MessageUtil.JMSXGROUPSEQ);
         } else if (propName.equals(Message.HDR_VALIDATED_USER)) {
            set.add(MessageUtil.JMSXUSERID);
         } else if ((!propName.startsWith(JMS) || propName.startsWith(JMSX) || propName.startsWith(JMS_)) && !propName.startsWith(CONNECTION_ID_PROPERTY_NAME) && !propName.equals(Message.HDR_ROUTING_TYPE) && !propName.startsWith(Message.HDR_ROUTE_TO_IDS)) {
            set.add(propName.toString());
         }
      }

      set.add(JMSXDELIVERYCOUNT);

      return set;
   }

   public static boolean propertyExists(Message message, String name) {
      return message.containsProperty(SimpleString.of(name)) || name.equals(MessageUtil.JMSXDELIVERYCOUNT) ||
         (MessageUtil.JMSXGROUPID.equals(name) && message.containsProperty(Message.HDR_GROUP_ID)) ||
         (MessageUtil.JMSXGROUPSEQ.equals(name) && message.containsProperty(Message.HDR_GROUP_SEQUENCE)) ||
         (MessageUtil.JMSXUSERID.equals(name) && message.containsProperty(Message.HDR_VALIDATED_USER));
   }


   public static String getStringProperty(final Message message, final String name) {
      if (MessageUtil.JMSXGROUPID.equals(name)) {
         return Objects.toString(message.getGroupID(), null);
      } else if (MessageUtil.JMSXGROUPSEQ.equals(name)) {
         return Integer.toString(message.getGroupSequence());
      } else if (MessageUtil.JMSXUSERID.equals(name)) {
         return message.getValidatedUserID();
      } else {
         return message.getStringProperty(name);
      }
   }

   public static Object getObjectProperty(final Message message, final String name) {
      final Object val;
      if (MessageUtil.JMSXGROUPID.equals(name)) {
         val = message.getGroupID();
      } else if (MessageUtil.JMSXGROUPSEQ.equals(name)) {
         val = message.getGroupSequence();
      } else if (MessageUtil.JMSXUSERID.equals(name)) {
         val = message.getValidatedUserID();
      } else {
         val = message.getObjectProperty(name);
      }
      if (val instanceof SimpleString) {
         return val.toString();
      }
      return val;
   }

   public static long getLongProperty(final Message message, final String name) {
      if (MessageUtil.JMSXGROUPSEQ.equals(name)) {
         return message.getGroupSequence();
      } else {
         return message.getLongProperty(name);
      }
   }

   public static int getIntProperty(final Message message, final String name) {
      if (MessageUtil.JMSXGROUPSEQ.equals(name)) {
         return message.getGroupSequence();
      } else {
         return message.getIntProperty(name);
      }
   }

   public static void setIntProperty(final Message message, final String name, final int value) {
      if (MessageUtil.JMSXGROUPSEQ.equals(name)) {
         message.setGroupSequence(value);
      } else {
         message.putIntProperty(name, value);
      }
   }

   public static void setLongProperty(final Message message, final String name, final long value) {
      if (MessageUtil.JMSXGROUPSEQ.equals(name)) {
         message.setGroupSequence((int) value);
      } else {
         message.putLongProperty(name, value);
      }
   }

   public static void setStringProperty(final Message message, final String name, final String value) {
      if (MessageUtil.JMSXGROUPID.equals(name)) {
         message.setGroupID(value);
      } else if (MessageUtil.JMSXGROUPSEQ.equals(name)) {
         message.setGroupSequence(getInteger(value));
      } else if (MessageUtil.JMSXUSERID.equals(name)) {
         message.setValidatedUserID(value);
      } else {
         message.putStringProperty(name, value);
      }
   }

   public static void setObjectProperty(final Message message, final String name, final Object value) {
      if (MessageUtil.JMSXGROUPID.equals(name)) {
         message.setGroupID(Objects.toString(value, null));
      } else if (MessageUtil.JMSXGROUPSEQ.equals(name)) {
         message.setGroupSequence(getInteger(value));
      } else if (MessageUtil.JMSXUSERID.equals(name)) {
         message.setValidatedUserID(Objects.toString(value, null));
      } else {
         message.putObjectProperty(name, value);
      }
   }

   private static int getInteger(final Object value) {
      Objects.requireNonNull(value);
      final int integer;
      if (value instanceof Integer integerValue) {
         integer = integerValue;
      } else if (value instanceof Number number) {
         integer = number.intValue();
      } else {
         integer = Integer.parseInt(value.toString());
      }
      return integer;
   }
}
