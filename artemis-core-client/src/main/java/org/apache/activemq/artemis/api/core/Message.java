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
package org.apache.activemq.artemis.api.core;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.OpenDataException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.persistence.Persister;

/**
 * A Message is a routable instance that has a payload.
 * <p>
 * The payload (the "body") is opaque to the messaging system. A Message also has a fixed set of
 * headers (required by the messaging system) and properties (defined by the users) that can be used
 * by the messaging system to route the message (e.g. to ensure it matches a queue filter).
 * <h2>Message Properties</h2>
 * <p>
 * Message can contain properties specified by the users. It is possible to convert from some types
 * to other types as specified by the following table:
 * <pre>
 * |        | boolean byte short int long float double String byte[]
 * |----------------------------------------------------------------
 * |boolean |    X                                      X
 * |byte    |          X    X    X   X                  X
 * |short   |               X    X   X                  X
 * |int     |                    X   X                  X
 * |long    |                        X                  X
 * |float   |                              X     X      X
 * |double  |                                    X      X
 * |String  |    X     X    X    X   X     X     X      X
 * |byte[]  |                                                   X
 * |-----------------------------------------------------------------
 * </pre>
 * <p>
 * If conversion is not allowed (for example calling {@code getFloatProperty} on a property set a
 * {@code boolean}), a {@link ActiveMQPropertyConversionException} will be thrown.
 *
 *
 * User cases that will be covered by Message
 *
 * Receiving a buffer:
 *
 * Message encode = new CoreMessage(); // or any other implementation
 * encode.receiveBuffer(buffer);
 *
 *
 * Sending to a buffer:
 *
 * Message encode;
 * size = encode.getEncodeSize();
 * encode.encodeDirectly(bufferOutput);
 *
 */
public interface Message {

   // This is an estimate of how much memory a Message takes up, excluding body and properties
   // Note, it is only an estimate, it's not possible to be entirely sure with Java
   // This figure is calculated using the test utilities in org.apache.activemq.tests.unit.util.sizeof
   // The value is somewhat higher on 64 bit architectures, probably due to different alignment
   int memoryOffset = 352;

   // We use properties to establish routing context on clustering.
   // However if the client resends the message after receiving, it needs to be removed, so we mark these internal
   Predicate<SimpleString> INTERNAL_PROPERTY_NAMES_PREDICATE =
      name -> (name.startsWith(Message.HDR_ROUTE_TO_IDS) && !name.equals(Message.HDR_ROUTE_TO_IDS)) ||
      (name.startsWith(Message.HDR_ROUTE_TO_ACK_IDS) && !name.equals(Message.HDR_ROUTE_TO_ACK_IDS));

   SimpleString HDR_ROUTE_TO_IDS = new SimpleString("_AMQ_ROUTE_TO");

   SimpleString HDR_SCALEDOWN_TO_IDS = new SimpleString("_AMQ_SCALEDOWN_TO");

   SimpleString HDR_ROUTE_TO_ACK_IDS = new SimpleString("_AMQ_ACK_ROUTE_TO");

   // used by the bridges to set duplicates
   SimpleString HDR_BRIDGE_DUPLICATE_ID = new SimpleString("_AMQ_BRIDGE_DUP");

   /**
    * the actual time the message was expired.
    * * *
    */
   SimpleString HDR_ACTUAL_EXPIRY_TIME = new SimpleString("_AMQ_ACTUAL_EXPIRY");

   /**
    * The original address of a message when a message is diverted or transferred through DLQ or expiry
    */
   SimpleString HDR_ORIGINAL_ADDRESS = new SimpleString("_AMQ_ORIG_ADDRESS");

   /**
    * The original address of a message when a message is transferred through DLQ or expiry
    */
   SimpleString HDR_ORIGINAL_QUEUE = new SimpleString("_AMQ_ORIG_QUEUE");

   /**
    * The original message ID before the message was transferred.
    */
   SimpleString HDR_ORIG_MESSAGE_ID = new SimpleString("_AMQ_ORIG_MESSAGE_ID");

   /**
    * For the Message Grouping feature.
    */
   SimpleString HDR_GROUP_ID = new SimpleString("_AMQ_GROUP_ID");

   SimpleString HDR_GROUP_SEQUENCE = new SimpleString("_AMQ_GROUP_SEQUENCE");

   /**
    * to determine if the Large Message was compressed.
    */
   SimpleString HDR_LARGE_COMPRESSED = new SimpleString("_AMQ_LARGE_COMPRESSED");

   /**
    * The body size of a large message before it was compressed.
    */
   SimpleString HDR_LARGE_BODY_SIZE = new SimpleString("_AMQ_LARGE_SIZE");

   /**
    * To be used with Scheduled Delivery.
    */
   SimpleString HDR_SCHEDULED_DELIVERY_TIME = new SimpleString("_AMQ_SCHED_DELIVERY");

   /**
    * To be used with duplicate detection.
    */
   SimpleString HDR_DUPLICATE_DETECTION_ID = new SimpleString("_AMQ_DUPL_ID");

   /**
    * To be used with Last value queues.
    */
   SimpleString HDR_LAST_VALUE_NAME = new SimpleString("_AMQ_LVQ_NAME");

   /**
    * To define the mime-type of body messages. Mainly for stomp but it could be informed on any message for user purposes.
    */
   SimpleString HDR_CONTENT_TYPE = new SimpleString("_AMQ_CONTENT_TYPE");

   /**
    * The name of the validated user who sent the message. Useful for auditing.
    */
   SimpleString HDR_VALIDATED_USER = new SimpleString("_AMQ_VALIDATED_USER");

   /**
    * The Routing Type for this message.  Ensures that this message is only routed to queues with matching routing type.
    */
   SimpleString HDR_ROUTING_TYPE = new SimpleString("_AMQ_ROUTING_TYPE");

   /**
    * The original routing type of a message before getting transferred through DLQ or expiry
    */
   SimpleString HDR_ORIG_ROUTING_TYPE = new SimpleString("_AMQ_ORIG_ROUTING_TYPE");

   /**
    * The time at which the message arrived at the broker.
    */
   SimpleString HDR_INGRESS_TIMESTAMP = new SimpleString("_AMQ_INGRESS_TIMESTAMP");

   /**
    * The prefix used (if any) when sending this message.  For protocols (e.g. STOMP) that need to track this and restore
    * the prefix when the message is consumed.
    */
   SimpleString HDR_PREFIX = new SimpleString("_AMQ_PREFIX");

   byte DEFAULT_TYPE = 0;

   byte OBJECT_TYPE = 2;

   byte TEXT_TYPE = 3;

   byte BYTES_TYPE = 4;

   byte MAP_TYPE = 5;

   byte STREAM_TYPE = 6;

   /** The message will contain another message persisted through {@literal org.apache.activemq.artemis.spi.core.protocol.EmbedMessageUtil}*/
   byte EMBEDDED_TYPE = 7;

   /** This is to embedd Large Messages from other protocol */
   byte LARGE_EMBEDDED_TYPE = 8;

   default void clearInternalProperties() {
      // only on core
   }

   /**
    * Search for the existence of the property: an implementor can save
    * the message to be decoded, if possible.
    */
   default boolean hasScheduledDeliveryTime() {
      return getScheduledDeliveryTime() != null;
   }

   default RoutingType getRoutingType() {
      return null;
   }

   default Message setRoutingType(RoutingType routingType) {
      return this;
   }

   default SimpleString getLastValueProperty() {
      return null;
   }

   default Message setLastValueProperty(SimpleString lastValueName) {
      return this;
   }

   /**
    * @deprecated do not use this, use through ICoreMessage or ClientMessage
    */
   @Deprecated
   default InputStream getBodyInputStream() {
      return null;
   }

   /**
    * @deprecated do not use this, use through ICoreMessage or ClientMessage
    * Warning: if you need to read the content of a message use getDataBuffer(). This method is intended for when you
    *          want to make changes.
    */
   @Deprecated
   default ActiveMQBuffer getBodyBuffer() {
      return null;
   }

      /**
       * @deprecated do not use this, use through ICoreMessage or ClientMessage
       */
   @Deprecated
   default byte getType() {
      return (byte)0;
   }

   /**
    * @deprecated do not use this, use through ICoreMessage or ClientMessage
    */
   @Deprecated
   default Message setType(byte type) {
      return this;
   }

   /**
    * WARNING: Calling this method on a AMQPMessage will allow the non mutable part of the message to be modified.
    */
   void messageChanged();

   /** Used to calculate what is the delivery time.
    *  Return null if not scheduled. */
   Long getScheduledDeliveryTime();

   default Message setScheduledDeliveryTime(Long time) {
      return this;
   }

   default SimpleString getGroupID() {
      return null;
   }

   default Message setGroupID(SimpleString groupID) {
      return this;
   }

   default Message setGroupID(String groupID) {
      return this;
   }

   default int getGroupSequence() {
      return 0;
   }

   default Message setGroupSequence(int sequence) {
      return this;
   }

   default Object getCorrelationID() {
      return null;
   }

   default Message setCorrelationID(Object correlationID) {

      return this;
   }

   SimpleString getReplyTo();

   Message setReplyTo(SimpleString address);

   /** It will generate a new instance of the message encode, being a deep copy, new properties, new everything */
   Message copy();

   /** It will generate a new instance of the message encode, being a deep copy, new properties, new everything */
   Message copy(long newID);

   /** It will generate a new instance of the message encode, being a deep copy, new properties, new everything */
   default Message copy(long newID, boolean isExpiryOrDLQ) {
      return copy(newID);
   }

   default boolean acceptsConsumer(long uniqueConsumerID) {
      return true;
   }

   default void rejectConsumer(long uniqueConsumerID) {
   }

   /**
    * Returns the messageID.
    * <br>
    * The messageID is set when the message is handled by the server.
    */
   long getMessageID();

   // used for NO-LOCAL: mainly for AMQP
   default Message setConnectionID(String connectionID) {
      return this;
   }

   default String getConnectionID() {
      return null;
   }

   Message setMessageID(long id);

   default boolean isLargeMessage() {
      return false;
   }

   /**
    * Returns the expiration time of this message.
    */
   long getExpiration();

   /**
    * Sets the expiration of this message.
    *
    * @param expiration expiration time
    */
   Message setExpiration(long expiration);

   /**
    * Returns whether this message is expired or not.
    */
   default boolean isExpired() {
      if (getExpiration() == 0) {
         return false;
      }

      return System.currentTimeMillis() - getExpiration() >= 0;
   }


   /**
    *
    * This represents historically the JMSMessageID.
    * We had in the past used this for the MessageID that was sent on core messages...
    *
    * later on when we added AMQP this name clashed with AMQPMessage.getUserID();
    *
    * @return the user id
    */
   Object getUserID();

   Message setUserID(Object userID);

   default  String getValidatedUserID() {
      return null;
   }

   default Message setValidatedUserID(String validatedUserID) {
      return this;
   }

   /**
    * Returns whether this message is durable or not.
    */
   boolean isDurable();

   /**
    * Sets whether this message is durable or not.
    *
    * @param durable {@code true} to flag this message as durable, {@code false} else
    */
   Message setDurable(boolean durable);

   Persister<Message> getPersister();

   String getAddress();

   /**
    * Look at {@link #setAddress(SimpleString)} for the doc.
    * @param address
    * @return
    */
   Message setAddress(String address);

   SimpleString getAddressSimpleString();

   /**
    * This will set the address on CoreMessage.
    *
    * Note for AMQPMessages:
    * in AMQPMessages this will not really change the address on the message. Instead it will add a property
    * on extraProperties which only transverse internally at the broker.
    * Whatever you change here it won't affect anything towards the received message.
    *
    * If you wish to change AMQPMessages address you will have to do it directly at the AMQP Message, however beware
    * that AMQPMessages are not supposed to be changed at the broker, so only do it if you know what you are doing.
    * @param address
    * @return
    */
   Message setAddress(SimpleString address);

   long getTimestamp();

   Message setTimestamp(long timestamp);

   /**
    * Returns the message priority.
    * <p>
    * Values range from 0 (less priority) to 9 (more priority) inclusive.
    */
   byte getPriority();

   /**
    * Sets the message priority.
    * <p>
    * Value must be between 0 and 9 inclusive.
    *
    * @param priority the new message priority
    */
   Message setPriority(byte priority);

   /** Used to receive this message from an encoded medium buffer */
   void receiveBuffer(ByteBuf buffer);

   /** Used to send this message to an encoded medium buffer.
    * @param buffer the buffer used.
    * @param deliveryCount Some protocols (AMQP) will have this as part of the message. */
   void sendBuffer(ByteBuf buffer, int deliveryCount);

   int getPersistSize();

   void persist(ActiveMQBuffer targetRecord);

   void reloadPersistence(ActiveMQBuffer record, CoreMessageObjectPools pools);

   default void reencode() {
      // only valid probably on AMQP
   }

   default void referenceOriginalMessage(final Message original, final SimpleString originalQueue) {
      setBrokerProperty(Message.HDR_ORIGINAL_QUEUE, originalQueue);
      setBrokerProperty(Message.HDR_ORIGINAL_ADDRESS, original.getAddressSimpleString());
      setBrokerProperty(Message.HDR_ORIG_MESSAGE_ID, original.getMessageID());
      final RoutingType routingType = original.getRoutingType();
      if (routingType != null) {
         setBrokerProperty(Message.HDR_ORIG_ROUTING_TYPE, routingType.getType());
      }

      // reset expiry
      setExpiration(0);
   }

   /**
    * it will translate a property named HDR_DUPLICATE_DETECTION_ID.
    * @return
    */
   default byte[] getDuplicateIDBytes() {
      Object duplicateID = getDuplicateProperty();

      if (duplicateID == null) {
         return null;
      } else {
         if (duplicateID instanceof SimpleString) {
            return ((SimpleString) duplicateID).getData();
         } else if (duplicateID instanceof String) {
            return new SimpleString(duplicateID.toString()).getData();
         } else {
            return (byte[]) duplicateID;
         }
      }
   }


   default org.apache.activemq.artemis.api.core.Message putExtraBytesProperty(SimpleString key, byte[] value) {
      return putBytesProperty(key, value);
   }

   default byte[] getExtraBytesProperty(SimpleString key) throws ActiveMQPropertyConversionException {
      return getBytesProperty(key);
   }

   default byte[] removeExtraBytesProperty(SimpleString key) throws ActiveMQPropertyConversionException {
      return (byte[])removeProperty(key);
   }

   default Object getDuplicateProperty() {
      return null;
   }

   Message putBooleanProperty(String key, boolean value);

   Message putByteProperty(String key, byte value);

   Message putBytesProperty(String key, byte[] value);

   Message putShortProperty(String key, short value);

   Message putCharProperty(String key, char value);

   Message putIntProperty(String key, int value);

   Message putLongProperty(String key, long value);

   Message putFloatProperty(String key, float value);

   Message putDoubleProperty(String key, double value);



   Message putBooleanProperty(SimpleString key, boolean value);

   Message putByteProperty(SimpleString key, byte value);

   Message putBytesProperty(SimpleString key, byte[] value);

   Message putShortProperty(SimpleString key, short value);

   Message putCharProperty(SimpleString key, char value);

   Message putIntProperty(SimpleString key, int value);

   Message putLongProperty(SimpleString key, long value);

   Message putFloatProperty(SimpleString key, float value);

   Message putDoubleProperty(SimpleString key, double value);

   /**
    * Puts a String property in this message.
    *
    * @param key   property name
    * @param value property value
    */
   Message putStringProperty(String key, String value);

   Message putObjectProperty(String key, Object value) throws ActiveMQPropertyConversionException;

   Message putObjectProperty(SimpleString key, Object value) throws ActiveMQPropertyConversionException;

   Object removeProperty(String key);

   boolean containsProperty(String key);

   Boolean getBooleanProperty(String key) throws ActiveMQPropertyConversionException;

   Byte getByteProperty(String key) throws ActiveMQPropertyConversionException;

   Double getDoubleProperty(String key) throws ActiveMQPropertyConversionException;

   Integer getIntProperty(String key) throws ActiveMQPropertyConversionException;

   Long getLongProperty(String key) throws ActiveMQPropertyConversionException;

   Object getObjectProperty(String key);

   Short getShortProperty(String key) throws ActiveMQPropertyConversionException;

   Float getFloatProperty(String key) throws ActiveMQPropertyConversionException;

   String getStringProperty(String key) throws ActiveMQPropertyConversionException;

   SimpleString getSimpleStringProperty(String key) throws ActiveMQPropertyConversionException;

   byte[] getBytesProperty(String key) throws ActiveMQPropertyConversionException;

   Object removeProperty(SimpleString key);

   boolean containsProperty(SimpleString key);

   Boolean getBooleanProperty(SimpleString key) throws ActiveMQPropertyConversionException;

   Byte getByteProperty(SimpleString key) throws ActiveMQPropertyConversionException;

   Double getDoubleProperty(SimpleString key) throws ActiveMQPropertyConversionException;

   Integer getIntProperty(SimpleString key) throws ActiveMQPropertyConversionException;

   Long getLongProperty(SimpleString key) throws ActiveMQPropertyConversionException;

   default Object getObjectPropertyForFilter(SimpleString key) {
      return getObjectProperty(key);
   }

   Object getObjectProperty(SimpleString key);

   default Object removeAnnotation(SimpleString key) {
      return removeProperty(key);
   }

   default String getAnnotationString(SimpleString key) {
      Object value = getAnnotation(key);
      if (value != null) {
         return value.toString();
      } else {
         return null;
      }
   }

   Object getAnnotation(SimpleString key);

   /** Callers must call {@link #reencode()} in order to be sent to clients */
   default Message setAnnotation(SimpleString key, Object value) {
      putObjectProperty(key, value);
      return this;
   }

   /** To be called by the broker on ocasions such as DLQ and expiry.
    * When the broker is adding additional properties. */
   default Message setBrokerProperty(SimpleString key, Object value) {
      putObjectProperty(key, value);
      return this;
   }

   default Object getBrokerProperty(SimpleString key) {
      return getObjectProperty(key);
   }

   default Message setIngressTimestamp() {
      setBrokerProperty(HDR_INGRESS_TIMESTAMP, System.currentTimeMillis());
      return this;
   }

   default Long getIngressTimestamp() {
      return (Long) getBrokerProperty(HDR_INGRESS_TIMESTAMP);
   }

   Short getShortProperty(SimpleString key) throws ActiveMQPropertyConversionException;

   Float getFloatProperty(SimpleString key) throws ActiveMQPropertyConversionException;

   String getStringProperty(SimpleString key) throws ActiveMQPropertyConversionException;

   SimpleString getSimpleStringProperty(SimpleString key) throws ActiveMQPropertyConversionException;

   byte[] getBytesProperty(SimpleString key) throws ActiveMQPropertyConversionException;

   Message putStringProperty(SimpleString key, SimpleString value);

   Message putStringProperty(SimpleString key, String value);

   /**
    * Returns the size of the <em>encoded</em> message.
    */
   int getEncodeSize();

   /**
    * Return an estimate of the size of the message on the wire.
    * for LargeMessages this will contain whatever is needed to encode properties and the body size of large messages.
    * For AMQP this will return the whole body size of the message as the body will contain all the data including properties.
    * @return
    */
   default long getWholeMessageSize() {
      return getEncodeSize();
   }

   /**
    * Returns all the names of the properties for this message.
    */
   Set<SimpleString> getPropertyNames();



   int getRefCount();

   int getUsage();

   int getDurableCount();

   /** this method indicates usage by components such as large message or page cache.
    *  This method will cause large messages to be held longer after the ack happened for instance.
    */
   int usageUp();

   /**
    * @see #usageUp()
    * @return
    */
   int usageDown();

   int refUp();

   int refDown();

   int durableUp();

   int durableDown();

   /**
    * @return Returns the message in Map form, useful when encoding to JSON
    */
   default Map<String, Object> toMap() {
      return toMap(-1);
   }

   /**
    * @return Returns the message in Map form, useful when encoding to JSON
    * @param valueSizeLimit that limits [] map values
    */
   default Map<String, Object> toMap(int valueSizeLimit) {
      Map map = toPropertyMap(valueSizeLimit);
      map.put("messageID", getMessageID());
      Object userID = getUserID();
      if (getUserID() != null) {
         map.put("userID", "ID:" + userID.toString());
      }

      map.put("address", getAddress() == null ? "" : getAddress());
      map.put("durable", isDurable());
      map.put("expiration", getExpiration());
      map.put("timestamp", getTimestamp());
      map.put("priority", (int)getPriority());

      return map;
   }

   /**
    * @return Returns the message properties in Map form, useful when encoding to JSON
    */
   default Map<String, Object> toPropertyMap() {
      return toPropertyMap(-1);
   }

   /**
    * @return Returns the message properties in Map form, useful when encoding to JSON
    * @param valueSizeLimit that limits [] map values
    */
   default Map<String, Object> toPropertyMap(int valueSizeLimit) {
      Map map = new HashMap<>();
      for (SimpleString name : getPropertyNames()) {
         Object value = getObjectProperty(name.toString());
         //some property is SimpleString, which is not available for management console
         if (value instanceof SimpleString) {
            value = value.toString();
         }
         value = JsonUtil.truncate(value, valueSizeLimit);
         map.put(name.toString(), value);
      }
      return map;
   }

   /** This should make you convert your message into Core format. */
   ICoreMessage toCore();

   default CompositeData toCompositeData(int fieldsLimit, int deliveryCount) throws OpenDataException {
      return null;
   }

   /** This should make you convert your message into Core format. */
   ICoreMessage toCore(CoreMessageObjectPools coreMessageObjectPools);

   int getMemoryEstimate();

   /**
    * This is the size of the message when persisted on disk which is used for metrics tracking
    * Note that even if the message itself is not persisted on disk (ie non-durable) this value is
    * still used for metrics tracking
    * If a normal message it will be the encoded message size
    * If a large message it will be encoded message size + large message body size
    * @return
    * @throws ActiveMQException
    */
   long getPersistentSize() throws ActiveMQException;

   Object getOwner();

   void setOwner(Object object);

   default String getStringBody() {
      return null;
   }
}
