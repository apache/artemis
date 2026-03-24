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
package org.apache.activemq.artemis.jms.client;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import java.io.Serializable;
import java.util.Properties;
import java.util.UUID;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.ParameterisedAddress;
import org.apache.activemq.artemis.api.core.QueueAttributes;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.jndi.JNDIStorable;
import org.apache.activemq.artemis.utils.DestinationUtil;

/**
 * ActiveMQ Artemis implementation of a JMS Destination.
 */
public class ActiveMQDestination extends JNDIStorable implements Destination, Serializable {


   private static final long serialVersionUID = 5027962425462382883L;

   public static final String QUEUE_QUALIFIED_PREFIX = DestinationUtil.QUEUE_QUALIFIED_PREFIX;
   public static final String TOPIC_QUALIFIED_PREFIX = DestinationUtil.TOPIC_QUALIFIED_PREFIX;
   public static final String TEMP_QUEUE_QUALIFED_PREFIX = DestinationUtil.TEMP_QUEUE_QUALIFED_PREFIX;
   public static final String TEMP_TOPIC_QUALIFED_PREFIX = DestinationUtil.TEMP_TOPIC_QUALIFED_PREFIX;

   /** createQueue and createTopic from {@link ActiveMQSession} may change the name
    *  in case Prefix usage */
   void setName(String name) {
      this.name = name;
   }

   public static ActiveMQDestination createDestination(RoutingType routingType, SimpleString address) {
      if (address == null) {
         return null;
      } else if (RoutingType.ANYCAST.equals(routingType)) {
         return ActiveMQDestination.createQueue(address);
      } else if (RoutingType.MULTICAST.equals(routingType)) {
         return ActiveMQDestination.createTopic(address);
      } else {
         return ActiveMQDestination.fromPrefixedName(address.toString());
      }
   }

   /**
    * Static helper method for working with destinations.
    */
   public static ActiveMQDestination createDestination(String name, TYPE defaultType) {
      if (name.startsWith(QUEUE_QUALIFIED_PREFIX)) {
         return new ActiveMQQueue(name.substring(QUEUE_QUALIFIED_PREFIX.length()));
      } else if (name.startsWith(TOPIC_QUALIFIED_PREFIX)) {
         return new ActiveMQTopic(name.substring(TOPIC_QUALIFIED_PREFIX.length()));
      } else if (name.startsWith(TEMP_QUEUE_QUALIFED_PREFIX)) {
         return new ActiveMQQueue(name.substring(TEMP_QUEUE_QUALIFED_PREFIX.length()), true);
      } else if (name.startsWith(TEMP_TOPIC_QUALIFED_PREFIX)) {
         return new ActiveMQTopic(name.substring(TEMP_TOPIC_QUALIFED_PREFIX.length()), true);
      }

      switch (defaultType) {
         case QUEUE:
            return new ActiveMQQueue(name);
         case TOPIC:
            return new ActiveMQTopic(name);
         case TEMP_QUEUE:
            return new ActiveMQQueue(name, true);
         case TEMP_TOPIC:
            return new ActiveMQTopic(name, true);
         case DESTINATION:
            return new ActiveMQDestination(name, TYPE.DESTINATION, null);
         default:
            throw new IllegalArgumentException("Invalid default destination type: " + defaultType);
      }
   }

   public static ActiveMQDestination fromPrefixedName(final String name) {
      return fromPrefixedName(name, name);
   }

   public static ActiveMQDestination fromPrefixedName(final String addr, final String name) {

      ActiveMQDestination destination;
      if (addr.startsWith(ActiveMQDestination.QUEUE_QUALIFIED_PREFIX)) {
         String address = addr.substring(ActiveMQDestination.QUEUE_QUALIFIED_PREFIX.length());
         destination = createQueue(address);
      } else if (addr.startsWith(ActiveMQDestination.TOPIC_QUALIFIED_PREFIX)) {
         String address = addr.substring(ActiveMQDestination.TOPIC_QUALIFIED_PREFIX.length());
         destination = createTopic(address);
      } else if (addr.startsWith(ActiveMQDestination.TEMP_QUEUE_QUALIFED_PREFIX)) {
         String address = addr.substring(ActiveMQDestination.TEMP_QUEUE_QUALIFED_PREFIX.length());
         destination = new ActiveMQTemporaryQueue(address, null);
      } else if (addr.startsWith(ActiveMQDestination.TEMP_TOPIC_QUALIFED_PREFIX)) {
         String address = addr.substring(ActiveMQDestination.TEMP_TOPIC_QUALIFED_PREFIX.length());
         destination = new ActiveMQTemporaryTopic(address, null);
      } else {
         destination = new ActiveMQDestination(addr, TYPE.DESTINATION, null);
      }

      return destination;
   }

   public static Destination fromPrefixed1XName(final String addr, final String name) {
      ActiveMQDestination destination;
      if (addr.startsWith(PacketImpl.OLD_QUEUE_PREFIX.toString())) {
         destination = createQueue(addr);
      } else if (addr.startsWith(PacketImpl.OLD_TOPIC_PREFIX.toString())) {
         destination = createTopic(addr);
      } else if (addr.startsWith(PacketImpl.OLD_TEMP_QUEUE_PREFIX.toString())) {
         destination = new ActiveMQTemporaryQueue(addr, null);
      } else if (addr.startsWith(PacketImpl.OLD_TEMP_TOPIC_PREFIX.toString())) {
         destination = new ActiveMQTemporaryTopic(addr, null);
      } else {
         destination = new ActiveMQDestination(addr, TYPE.DESTINATION, null);
      }

      String unprefixedName = name;

      if (name.startsWith(PacketImpl.OLD_QUEUE_PREFIX.toString())) {
         unprefixedName = name.substring(PacketImpl.OLD_QUEUE_PREFIX.length());
      } else if (name.startsWith(PacketImpl.OLD_TOPIC_PREFIX.toString())) {
         unprefixedName = name.substring(PacketImpl.OLD_TOPIC_PREFIX.length());
      } else if (name.startsWith(PacketImpl.OLD_TEMP_QUEUE_PREFIX.toString())) {
         unprefixedName = name.substring(PacketImpl.OLD_TEMP_QUEUE_PREFIX.length());
      } else if (name.startsWith(PacketImpl.OLD_TEMP_TOPIC_PREFIX.toString())) {
         unprefixedName = name.substring(PacketImpl.OLD_TEMP_TOPIC_PREFIX.length());
      }

      destination.setName(unprefixedName);

      return destination;
   }




   public static SimpleString createQueueNameForSubscription(final boolean isDurable,
                                                       final String clientID,
                                                       final String subscriptionName) {
      return DestinationUtil.createQueueNameForSubscription(isDurable, clientID, subscriptionName);
   }

   public static String createQueueNameForSharedSubscription(final boolean isDurable,
                                                             final String clientID,
                                                             final String subscriptionName) {
      return DestinationUtil.createQueueNameForSharedSubscription(isDurable, clientID, subscriptionName);
   }

   public static Pair<String, String> decomposeQueueNameForDurableSubscription(final String queueName) {
      StringBuffer[] parts = new StringBuffer[2];
      int currentPart = 0;

      parts[0] = new StringBuffer();
      parts[1] = new StringBuffer();

      int pos = 0;
      while (pos < queueName.length()) {
         char ch = queueName.charAt(pos);
         pos++;

         if (ch == DestinationUtil.SEPARATOR) {
            currentPart++;
            if (currentPart >= parts.length) {
               throw new JMSRuntimeException("Invalid message queue name: " + queueName);
            }

            continue;
         }

         if (ch == '\\') {
            if (pos >= queueName.length()) {
               throw new JMSRuntimeException("Invalid message queue name: " + queueName);
            }
            ch = queueName.charAt(pos);
            pos++;
         }

         parts[currentPart].append(ch);
      }

      if (currentPart != 1) {
         /* JMS 2.0 introduced the ability to create "shared" subscriptions which do not require a clientID.
          * In this case the subscription name will be the same as the queue name, but the above algorithm will put that
          * in the wrong position in the array so we need to move it.
          */
         parts[1] = parts[0];
         parts[0] = new StringBuffer();
      }

      Pair<String, String> pair = new Pair<>(parts[0].toString(), parts[1].toString());

      return pair;
   }

   public static SimpleString createQueueAddressFromName(final String name) {
      return new SimpleString(QUEUE_QUALIFIED_PREFIX + name);
   }

   public static SimpleString createTopicAddressFromName(final String name) {
      return new SimpleString(TOPIC_QUALIFIED_PREFIX + name);
   }

   public static ActiveMQQueue createQueue(final String address) {
      return new ActiveMQQueue(address);
   }

   public static ActiveMQQueue createQueue(final SimpleString address) {
      return new ActiveMQQueue(address);
   }

   public static ActiveMQQueue createQueue(final String address, final String name) {
      return new ActiveMQQueue(address, name);
   }

   public static ActiveMQTopic createTopic(final String address) {
      return new ActiveMQTopic(address);
   }

   public static ActiveMQTopic createTopic(final SimpleString address) {
      return new ActiveMQTopic(address);
   }

   public static ActiveMQTopic createTopic(final String address, final String name) {
      return new ActiveMQTopic(address, name);
   }

   public static ActiveMQTemporaryQueue createTemporaryQueue(final String address, final ActiveMQSession session) {
      return new ActiveMQTemporaryQueue(address, session);
   }

   public static ActiveMQTemporaryQueue createTemporaryQueue(final String address) {
      return createTemporaryQueue(address, null);
   }

   public static ActiveMQTemporaryQueue createTemporaryQueue(final ActiveMQSession session) {
      String address = UUID.randomUUID().toString();

      return createTemporaryQueue(address, session);
   }

   public static ActiveMQTemporaryTopic createTemporaryTopic(final ActiveMQSession session) {
      String address = UUID.randomUUID().toString();

      return createTemporaryTopic(address, session);
   }

   public static ActiveMQTemporaryQueue createTemporaryQueue(final ActiveMQSession session, final String prefix) {
      String name = UUID.randomUUID().toString();
      String address = prefix + name;

      ActiveMQTemporaryQueue queue = createTemporaryQueue(address, session);
      queue.setName(name);
      return queue;
   }

   public static ActiveMQTemporaryTopic createTemporaryTopic(final ActiveMQSession session, final String prefix) {
      String name = UUID.randomUUID().toString();
      String address = prefix + name;

      ActiveMQTemporaryTopic topic  = createTemporaryTopic(address, session);
      topic.setName(name);
      return topic;
   }

   public static ActiveMQTemporaryTopic createTemporaryTopic(String address, final ActiveMQSession session) {
      return new ActiveMQTemporaryTopic(address, session);
   }

   public static ActiveMQTemporaryTopic createTemporaryTopic(String address) {
      return createTemporaryTopic(address, null);
   }


   /**
    * The core address
    */
   private SimpleString simpleAddress;

   private QueueConfiguration queueConfiguration;

   /**
    * Needed for serialization backwards compatibility.
    */
   @Deprecated
   private String address;

   private String name;

   private final boolean temporary;

   private final boolean queue;

   private transient TYPE thetype;

   private final transient ActiveMQSession session;

   private transient boolean created;


   protected ActiveMQDestination(final String address,
                                 final TYPE type,
                                 final ActiveMQSession session) {
      this(SimpleString.toSimpleString(address), type, session);
   }

   protected ActiveMQDestination(final SimpleString address,
                                 final TYPE type,
                                 final ActiveMQSession session) {

      if (address != null) {
         setSimpleAddress(address);
      }

      this.thetype = type;

      this.session = session;

      this.temporary = TYPE.isTemporary(type);

      this.queue = TYPE.isQueue(type);
   }

   protected ActiveMQDestination(final String address,
                                 final String name,
                                 final TYPE type,
                                 final ActiveMQSession session) {
      this(SimpleString.toSimpleString(address), name, type, session);
   }

   @Deprecated
   protected ActiveMQDestination(final SimpleString address,
                                 final String name,
                                 final TYPE type,
                                 final ActiveMQSession session) {
      this(address, type, session);

      this.name = name;

      this.address = simpleAddress != null ? simpleAddress.toString() : null;
   }

   public void setAddress(String address) {
      setSimpleAddress(SimpleString.toSimpleString(address));
   }

   @Override
   public String toString() {
      return "ActiveMQDestination [address=" + simpleAddress.toString() +
         ", name=" +
         name +
         ", type =" +
         thetype +
         "]";
   }

   public void setSimpleAddress(SimpleString address) {
      if (address == null) {
         throw new IllegalArgumentException("address cannot be null");
      }
      if (ParameterisedAddress.isParameterised(address)) {
         ParameterisedAddress parameteredAddress = new ParameterisedAddress(address);
         this.simpleAddress = parameteredAddress.getAddress();
         this.address = parameteredAddress.getAddress().toString();
         this.queueConfiguration = parameteredAddress.getQueueConfiguration();
      } else {
         this.simpleAddress = address;
         this.address = address.toString();
         this.queueConfiguration = null;
      }
   }

   public void delete() throws JMSException {
      if (session != null) {
         boolean openedHere = false;
         ActiveMQSession sessionToUse = session;

         if (session.getCoreSession().isClosed()) {
            sessionToUse = (ActiveMQSession)session.getConnection().createSession();
            openedHere = true;
         }

         try {
            /**
             * The status of the session used to create the temporary destination is uncertain, but the JMS spec states
             * that the lifetime of the temporary destination is tied to the connection so even if the originating
             * session is closed the temporary destination should still be deleted. Therefore, just create a new one
             * and close it after the temporary destination is deleted. This is necessary because the Core API is
             * predicated on having a Core ClientSession which is encapsulated by the JMS session implementation.
             */
            if (isQueue()) {
               sessionToUse.deleteTemporaryQueue(this);
            } else {
               sessionToUse.deleteTemporaryTopic(this);
            }
            setCreated(false);
         } finally {
            if (openedHere) {
               sessionToUse.close();
            }
         }
      }
   }

   public boolean isQueue() {
      return queue;
   }


   public String getAddress() {
      return simpleAddress != null ? simpleAddress.toString() : null;
   }

   public SimpleString getSimpleAddress() {
      return simpleAddress;
   }

   @Deprecated
   public QueueAttributes getQueueAttributes() {
      return QueueAttributes.fromQueueConfiguration(queueConfiguration);
   }

   public QueueConfiguration getQueueConfiguration() {
      return queueConfiguration;
   }

   public String getName() {
      return name != null ? name : getAddress();
   }

   public boolean isTemporary() {
      return temporary;
   }

   public boolean isCreated() {
      return created;
   }

   public void setCreated(boolean created) {
      this.created = created;
   }

   public TYPE getType() {
      if (thetype == null) {
         if (temporary) {
            if (isQueue()) {
               thetype = TYPE.TEMP_QUEUE;
            } else {
               thetype = TYPE.TEMP_TOPIC;
            }
         } else {
            if (isQueue()) {
               thetype = TYPE.QUEUE;
            } else {
               thetype = TYPE.TOPIC;
            }
         }
      }
      return thetype;
   }

   @Override
   public boolean equals(final Object o) {
      if (this == o) {
         return true;
      }

      if (!(o instanceof ActiveMQDestination)) {
         return false;
      }

      ActiveMQDestination that = (ActiveMQDestination) o;

      return simpleAddress.equals(that.simpleAddress);
   }

   @Override
   public int hashCode() {
      return simpleAddress.hashCode();
   }

   @Override
   protected void buildFromProperties(Properties props) {
      setAddress(props.getProperty("address"));
   }

   @Override
   protected void populateProperties(Properties props) {
      props.put("address", getAddress());
   }

   public enum TYPE {
      QUEUE,
      TOPIC,
      TEMP_QUEUE,
      TEMP_TOPIC,
      DESTINATION; // unknown

      public byte getType() {
         switch (this) {
            case QUEUE:
               return 0;
            case TOPIC:
               return 1;
            case TEMP_QUEUE:
               return 2;
            case TEMP_TOPIC:
               return 3;
            case DESTINATION:
               return 4;
            default:
               return -1;
         }
      }

      public static TYPE getType(byte type) {
         switch (type) {
            case 0:
               return QUEUE;
            case 1:
               return TOPIC;
            case 2:
               return TEMP_QUEUE;
            case 3:
               return TEMP_TOPIC;
            case 4:
               return DESTINATION;
            default:
               return null;
         }
      }

      public static boolean isQueue(TYPE type) {
         boolean result = false;

         if (type != null && (type.equals(QUEUE) || type.equals(TEMP_QUEUE))) {
            result = true;
         }

         return result;
      }

      public static boolean isTemporary(TYPE type) {
         boolean result = false;

         if (type != null && (type.equals(TEMP_TOPIC) || type.equals(TEMP_QUEUE))) {
            result = true;
         }

         return result;
      }
   }
}
