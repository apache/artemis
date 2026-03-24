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
package org.apache.activemq.artemis.core.filter.impl;

import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.FilterConstants;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.federation.address.FederatedAddress;
import org.apache.activemq.artemis.selector.filter.BooleanExpression;
import org.apache.activemq.artemis.selector.filter.FilterException;
import org.apache.activemq.artemis.selector.filter.Filterable;
import org.apache.activemq.artemis.selector.impl.SelectorParser;
import org.apache.activemq.artemis.utils.ByteUtil;

import static org.apache.activemq.artemis.api.core.FilterConstants.NATIVE_MESSAGE_ID;

/**
 * This class implements an ActiveMQ Artemis filter
 *
 * ActiveMQ Artemis filters have the same syntax as JMS 1.1 selectors, but the identifiers are different.
 *
 * Valid identifiers that can be used are:
 *
 * AMQPriority - the priority of the message
 * AMQTimestamp - the timestamp of the message
 * AMQDurable - "DURABLE" or "NON_DURABLE"
 * AMQExpiration - the expiration of the message
 * AMQSize - the encoded size of the full message in bytes
 * AMQUserID - the user specified ID string (if any)
 * Any other identifiers that appear in a filter expression represent header values for the message
 *
 * String values must be set as <code>SimpleString</code>, not <code>java.lang.String</code> (see JBMESSAGING-1307).
 * Derived from JBoss MQ version by
 */
public class FilterImpl implements Filter {


   private final SimpleString sfilterString;

   private final BooleanExpression booleanExpression;


   /**
    * @return null if <code>filterStr</code> is null or an empty String and a valid filter else
    * @throws ActiveMQException if the string does not correspond to a valid filter
    */
   public static Filter createFilter(final String filterStr) throws ActiveMQException {
      return FilterImpl.createFilter(SimpleString.toSimpleString(filterStr == null ? null : filterStr.trim()));
   }

   /**
    * @return null if <code>filterStr</code> is null or an empty String and a valid filter else
    * @throws ActiveMQException if the string does not correspond to a valid filter
    */
   public static Filter createFilter(final SimpleString filterStr) throws ActiveMQException {
      if (filterStr == null || filterStr.length() == 0) {
         return null;
      }

      BooleanExpression booleanExpression;
      try {
         booleanExpression = SelectorParser.parse(filterStr.toString());
      } catch (Throwable e) {
         ActiveMQServerLogger.LOGGER.invalidFilter(filterStr);
         if (ActiveMQServerLogger.LOGGER.isDebugEnabled()) {
            ActiveMQServerLogger.LOGGER.debug("Invalid filter", e);
         }
         throw ActiveMQMessageBundle.BUNDLE.invalidFilter(e, filterStr);
      }
      return new FilterImpl(filterStr, booleanExpression);
   }

   private FilterImpl(final SimpleString str, final BooleanExpression expression) {
      sfilterString = str;
      this.booleanExpression = expression;
   }

   // Filter implementation ---------------------------------------------------------------------

   @Override
   public SimpleString getFilterString() {
      return sfilterString;
   }

   @Override
   public boolean match(final Message message) {
      return match(new FilterableServerMessage(message));
   }

   @Override
   public boolean match(final Map<String, String> map) {
      return match(new FilterableMap(map));
   }


   @Override
   public synchronized boolean match(final Filterable filterable) {
      try {
         return booleanExpression.matches(filterable);
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.invalidFilter(sfilterString);
         if (ActiveMQServerLogger.LOGGER.isDebugEnabled()) {
            ActiveMQServerLogger.LOGGER.debug("Invalid filter", e);
         }
         return false;
      }
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((sfilterString == null) ? 0 : sfilterString.hashCode());
      return result;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj)
         return true;
      if (obj == null)
         return false;
      if (getClass() != obj.getClass())
         return false;
      FilterImpl other = (FilterImpl) obj;
      if (sfilterString == null) {
         if (other.sfilterString != null)
            return false;
      } else if (!sfilterString.equals(other.sfilterString))
         return false;
      return true;
   }

   @Override
   public String toString() {
      return "FilterImpl [sfilterString=" + sfilterString + "]";
   }

   private static Object getHeaderFieldValue(final Message msg, final SimpleString fieldName) {
      if (FilterConstants.ACTIVEMQ_USERID.equals(fieldName)) {
         if (msg.getUserID() == null) {
            // Proton stores JMSMessageID as NATIVE_MESSAGE_ID that is an arbitrary string
            String amqpNativeID = msg.getStringProperty(NATIVE_MESSAGE_ID);
            if (amqpNativeID != null) {
               return SimpleString.toSimpleString(amqpNativeID);
            }
         }
         // It's the stringified (hex) representation of a user id that can be used in a selector expression
         String userID = msg.getUserID().toString();
         if (userID.startsWith("ID:")) {
            return SimpleString.toSimpleString(userID);
         } else {
            return SimpleString.toSimpleString("ID:" + msg.getUserID());
         }
      } else if (FilterConstants.ACTIVEMQ_PRIORITY.equals(fieldName)) {
         return Integer.valueOf(msg.getPriority());
      } else if (FilterConstants.ACTIVEMQ_TIMESTAMP.equals(fieldName)) {
         return msg.getTimestamp();
      } else if (FilterConstants.ACTIVEMQ_DURABLE.equals(fieldName)) {
         return msg.isDurable() ? FilterConstants.DURABLE : FilterConstants.NON_DURABLE;
      } else if (FilterConstants.ACTIVEMQ_EXPIRATION.equals(fieldName)) {
         return msg.getExpiration();
      } else if (FilterConstants.ACTIVEMQ_SIZE.equals(fieldName)) {
         return msg.getEncodeSize();
      } else if (FilterConstants.ACTIVEMQ_ADDRESS.equals(fieldName)) {
         return msg.getAddress();
      } else if (FilterConstants.ACTIVEMQ_GROUP_ID.equals(fieldName)) {
         return msg.getGroupID();
      } else {
         return null;
      }
   }

   private static class FilterableMap implements Filterable {

      private final Map<String, String> map;

      private FilterableMap(Map<String, String> map) {
         this.map = map;
      }

      @Override
      public <T> T getBodyAs(Class<T> type) throws FilterException {
         return null;
      }

      @Override
      public Object getProperty(SimpleString name) {
         return map.get(name.toString());
      }

      @Override
      public Object getLocalConnectionId() {
         return null;
      }
   }

   private static class FilterableServerMessage implements Filterable {

      private final Message message;

      private FilterableServerMessage(Message message) {
         this.message = message;
      }

      @Override
      public Object getProperty(SimpleString id) {
         Object result = null;
         if (id.startsWith(FilterConstants.ACTIVEMQ_PREFIX)) {
            result = getHeaderFieldValue(message, id);
         }
         if (id.startsWith(FederatedAddress.HDR_HOPS)) {
            byte[] bytes = message.getExtraBytesProperty(FederatedAddress.HDR_HOPS);
            result = bytes == null ? null : ByteUtil.bytesToInt(bytes);
         }
         if (result == null) {
            result = message.getObjectPropertyForFilter(id);
         }
         if (result != null) {
            if (result.getClass() == SimpleString.class) {
               result = result.toString();
            }
         }
         return result;
      }

      @Override
      public <T> T getBodyAs(Class<T> type) throws FilterException {
         T body = null;
         if (!message.isLargeMessage() && type == String.class) {
            try {
               body = type.cast(message.getStringBody());
            } catch (Exception e) {
               // ignore
            }
         }
         return body;
      }

      @Override
      public Object getLocalConnectionId() {
         // Only needed if the NoLocal
         return null;
      }
   }
}
