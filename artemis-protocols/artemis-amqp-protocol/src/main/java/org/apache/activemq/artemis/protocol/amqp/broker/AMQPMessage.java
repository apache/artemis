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
package org.apache.activemq.artemis.protocol.amqp.broker;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.SimpleType;
import java.lang.invoke.MethodHandles;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQPropertyConversionException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.RefCountMessage;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.message.openmbean.CompositeDataConstants;
import org.apache.activemq.artemis.core.message.openmbean.MessageOpenTypeFactory;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageIdHelper;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.activemq.artemis.protocol.amqp.converter.AmqpCoreConverter;
import org.apache.activemq.artemis.protocol.amqp.util.NettyReadable;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.apache.qpid.proton.ProtonException;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedByte;
import org.apache.qpid.proton.amqp.UnsignedInteger;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.proton.amqp.UnsignedShort;
import org.apache.qpid.proton.amqp.messaging.AmqpSequence;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.amqp.messaging.Footer;
import org.apache.qpid.proton.amqp.messaging.Header;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.codec.DecoderImpl;
import org.apache.qpid.proton.codec.DroppingWritableBuffer;
import org.apache.qpid.proton.codec.ReadableBuffer;
import org.apache.qpid.proton.codec.TypeConstructor;
import org.apache.qpid.proton.codec.WritableBuffer;
import org.apache.qpid.proton.message.Message;
import org.apache.qpid.proton.message.impl.MessageImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.proton.codec.EncodingCodes;


import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.getCharsetForTextualContent;

/**
 * See <a href="https://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-messaging-v1.0-os.html#section-message-format">AMQP v1.0 message format</a>
 * <pre>
 *
 *                                                      Bare Message
 *                                                            |
 *                                      .---------------------+--------------------.
 *                                      |                                          |
 * +--------+-------------+-------------+------------+--------------+--------------+--------+
 * | header | delivery-   | message-    | properties | application- | application- | footer |
 * |        | annotations | annotations |            | properties   | data         |        |
 * +--------+-------------+-------------+------------+--------------+--------------+--------+
 * |                                                                                        |
 * '-------------------------------------------+--------------------------------------------'
 *                                             |
 *                                      Annotated Message
 * </pre>
 * <ul>
 *    <li>Zero or one header sections.
 *    <li>Zero or one delivery-annotation sections.
 *    <li>Zero or one message-annotation sections.
 *    <li>Zero or one properties sections.
 *    <li>Zero or one application-properties sections.
 *    <li>The body consists of one of the following three choices:
 *    <ul>
 *       <li>one or more data sections
 *       <li>one or more amqp-sequence sections
 *       <li>or a single amqp-value section.
 *    </ul>
 *    <li>Zero or one footer sections.
 * </ul>
 */
public abstract class AMQPMessage extends RefCountMessage implements org.apache.activemq.artemis.api.core.Message {

   // The minimum size an AMQP message uses.
   // This is an estimate, and it's based on the following test:
   // By running AMQPGlobalMaxTest::testSendUntilOME, you look at the initial memory used by the broker without any messages.
   // By the time you get the OME, you can do some basic calculations on how much each message uses and get an AVG.
   public static final int MINIMUM_ESTIMATE = 1300;

   private static final byte DURABLE = 1;
   private static final byte NON_DURABLE = 0;

   private static final SimpleString ANNOTATION_AREA_PREFIX = SimpleString.of("m.");

   protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final SimpleString ADDRESS_PROPERTY = SimpleString.of("_AMQ_AD");
   // used to perform quick search
   private static final Symbol[] SCHEDULED_DELIVERY_SYMBOLS = new Symbol[]{
      AMQPMessageSupport.SCHEDULED_DELIVERY_TIME, AMQPMessageSupport.SCHEDULED_DELIVERY_DELAY};
   private static final KMPNeedle[] SCHEDULED_DELIVERY_NEEDLES = new KMPNeedle[]{
      AMQPMessageSymbolSearch.kmpNeedleOf(AMQPMessageSupport.SCHEDULED_DELIVERY_TIME),
      AMQPMessageSymbolSearch.kmpNeedleOf(AMQPMessageSupport.SCHEDULED_DELIVERY_DELAY)};

   private static final KMPNeedle[] DUPLICATE_ID_NEEDLES = new KMPNeedle[] {
      AMQPMessageSymbolSearch.kmpNeedleOf(org.apache.activemq.artemis.api.core.Message.HDR_DUPLICATE_DETECTION_ID.toString())
   };

   public static final int DEFAULT_MESSAGE_FORMAT = 0;
   public static final int DEFAULT_MESSAGE_PRIORITY = 4;
   public static final int MAX_MESSAGE_PRIORITY = 9;

   protected static final int VALUE_NOT_PRESENT = -1;

   /**
    * This has been made public just for testing purposes: it's not stable and developers shouldn't rely on this for
    * developing purposes.
    */
   public enum MessageDataScanningStatus {
      NOT_SCANNED(0), SCANNED(1);

      private static final MessageDataScanningStatus[] STATES;

      static {
         // this prevent codes to be assigned with the wrong order
         // and ensure valueOf to work fine
         final MessageDataScanningStatus[] states = values();
         STATES = new MessageDataScanningStatus[states.length];
         for (final MessageDataScanningStatus state : states) {
            final int code = state.code;
            if (STATES[code] != null) {
               throw new AssertionError("code already in use: " + code);
            }
            STATES[code] = state;
         }
      }

      final byte code;

      MessageDataScanningStatus(int code) {
         assert code >= 0 && code <= Byte.MAX_VALUE;
         this.code = (byte) code;
      }

      static MessageDataScanningStatus valueOf(int code) {
         checkCode(code);
         return STATES[code];
      }

      private static void checkCode(int code) {
         if (code < 0 || code > (STATES.length - 1)) {
            throw new IllegalArgumentException("invalid status code: " + code);
         }
      }
   }
   // Buffer and state for the data backing this message.
   protected byte messageDataScanned = MessageDataScanningStatus.NOT_SCANNED.code;

   // Marks the message as needed to be re-encoded to update the backing buffer
   protected boolean modified;

   // Track locations of the message sections for later use and track the size
   // of the header and delivery annotations if present so we can easily exclude
   // the delivery annotations later and perform efficient encodes or copies.
   protected int headerPosition = VALUE_NOT_PRESENT;
   protected int encodedHeaderSize;
   protected int deliveryAnnotationsPosition = VALUE_NOT_PRESENT;
   protected int encodedDeliveryAnnotationsSize;
   protected int messageAnnotationsPosition = VALUE_NOT_PRESENT;
   protected int propertiesPosition = VALUE_NOT_PRESENT;
   protected int applicationPropertiesPosition = VALUE_NOT_PRESENT;
   protected int applicationPropertiesCount;
   protected int remainingBodyPosition = VALUE_NOT_PRESENT;

   // Message level meta data
   protected final long messageFormat;
   protected long messageID;
   protected SimpleString address;
   protected volatile int memoryEstimate = VALUE_NOT_PRESENT;
   protected long expiration;
   protected boolean expirationReload = false;
   protected long scheduledTime = VALUE_NOT_PRESENT;
   protected byte priority = VALUE_NOT_PRESENT;

   /**
    * 0 = false, 1 = true, or VALUE_NOT_PRESENT
    */
   protected byte durable = VALUE_NOT_PRESENT;

   protected boolean isPaged;
   protected volatile boolean routed = false;

   @Override
   public void routed() {
      this.routed = true;
   }

   // The Proton based AMQP message section that are retained in memory, these are the
   // mutable portions of the Message as the broker sees it, although AMQP defines that
   // the Properties and ApplicationProperties are immutable so care should be taken
   // here when making changes to those Sections.
   protected Header header;
   protected MessageAnnotations messageAnnotations;
   protected Properties properties;
   protected ApplicationProperties applicationProperties;

   protected String connectionID;
   protected final CoreMessageObjectPools coreMessageObjectPools;
   protected Set<Object> rejectedConsumers;
   protected DeliveryAnnotations deliveryAnnotationsForSendBuffer;
   protected DeliveryAnnotations deliveryAnnotations;

   // These are properties set at the broker level and carried only internally by broker storage.
   protected volatile TypedProperties extraProperties;

   private volatile Object owner;

   /**
    * Creates a new {@link AMQPMessage} instance from binary encoded message data.
    *
    * @param messageFormat   The Message format tag given the in Transfer that carried this message
    * @param extraProperties Broker specific extra properties that should be carried with this message
    */
   public AMQPMessage(long messageFormat, TypedProperties extraProperties, CoreMessageObjectPools coreMessageObjectPools) {
      this.messageFormat = messageFormat;
      this.coreMessageObjectPools = coreMessageObjectPools;
      this.extraProperties = extraProperties == null ? null : new TypedProperties(extraProperties);
   }

   protected AMQPMessage(AMQPMessage copy) {
      this(copy.messageFormat, copy.extraProperties, copy.coreMessageObjectPools);

      this.headerPosition = copy.headerPosition;
      this.encodedHeaderSize = copy.encodedHeaderSize;
      this.header = copy.header == null ? null : new Header(copy.header);
      this.deliveryAnnotationsPosition = copy.deliveryAnnotationsPosition;
      this.encodedDeliveryAnnotationsSize = copy.encodedDeliveryAnnotationsSize;
      this.deliveryAnnotations = copy.deliveryAnnotations == null ? null : new DeliveryAnnotations(copy.deliveryAnnotations.getValue());
      this.messageAnnotationsPosition = copy.messageAnnotationsPosition;
      this.messageAnnotations = copyAnnotations(copy.messageAnnotations);
      this.propertiesPosition = copy.propertiesPosition;
      this.properties = copy.properties == null ? null : new Properties(copy.properties);
      this.applicationPropertiesPosition = copy.applicationPropertiesPosition;
      this.applicationProperties = copy.applicationProperties == null ? null : new ApplicationProperties(copy.applicationProperties.getValue());
      this.remainingBodyPosition = copy.remainingBodyPosition;
      this.messageDataScanned = copy.messageDataScanned;
   }

   void setMemoryEstimate(int memoryEstimate) {
      this.memoryEstimate = memoryEstimate;
   }


   private static MessageAnnotations copyAnnotations(MessageAnnotations messageAnnotations) {
      if (messageAnnotations == null) {
         return null;
      }
      Map newAnnotation = new HashMap();
      messageAnnotations.getValue().forEach((a, b) -> {
         // These properties should not be copied when re-routing the messages
         if (!a.toString().startsWith("x-opt-ORIG") && !a.toString().equals("x-opt-routing-type")) {
            newAnnotation.put(a, b);
         }
      });
      return new MessageAnnotations(newAnnotation);
   }

   protected AMQPMessage(long messageFormat) {
      this.messageFormat = messageFormat;
      this.coreMessageObjectPools = null;
   }

   @Override
   public String getProtocolName() {
      return ProtonProtocolManagerFactory.AMQP_PROTOCOL_NAME;
   }

   public final MessageDataScanningStatus getDataScanningStatus() {
      return MessageDataScanningStatus.valueOf(messageDataScanned);
   }

   @Override
   public boolean isPaged() {
      return isPaged;
   }

   @Override
   public void setPaged() {
      isPaged = true;
   }

   /**
    * This will return application properties without attempting to decode it. That means, if applicationProperties were
    * never parsed before, this will return null, even if there is application properties. This was created as an
    * internal method for testing, as we need to validate if the application properties are not decoded until needed.
    */
   protected ApplicationProperties getDecodedApplicationProperties() {
      return applicationProperties;
   }

   protected MessageAnnotations getDecodedMessageAnnotations() {
      return messageAnnotations;
   }

   protected abstract ReadableBuffer getData();

   // Access to the AMQP message data using safe copies freshly decoded from the current
   // AMQP message data stored in this message wrapper.  Changes to these values cannot
   // be used to influence the underlying AMQP message data, the standard AMQPMessage API
   // must be used to make changes to mutable portions of the message.

   /**
    * Creates and returns a Proton-J MessageImpl wrapper around the message data. Changes to the returned Message are
    * not reflected in this message.
    *
    * @return a MessageImpl that wraps the AMQP message data in this {@link AMQPMessage}
    */
   public final MessageImpl getProtonMessage() {

      Objects.requireNonNull(getData(), "Data is not initialized");

      ensureScanning();

      MessageImpl protonMessage = null;
      if (getData() != null) {
         protonMessage = (MessageImpl) Message.Factory.create();
         getData().rewind();
         protonMessage.decode(getData().duplicate());
      }

      return protonMessage;
   }

   @Override
   public Object getObjectPropertyForFilter(SimpleString key) {
      if (key.startsWith(ANNOTATION_AREA_PREFIX)) {
         key = key.subSeq(ANNOTATION_AREA_PREFIX.length(), key.length());
         return getAnnotation(key);
      }

      Object value = getObjectProperty(key);
      if (value == null) {
         TypedProperties extra = getExtraProperties();
         if (extra != null) {
            value = extra.getProperty(key);
         }
      }
      return value;
   }

   /**
    * {@return a <em>copy</em> of this message's {@code Header} if one exists or {@code null} if none present; changes
    * to the returned {@code Header} instance do not affect the original message}
    */
   public final Header getHeader() {
      ensureScanning();
      return scanForMessageSection(headerPosition, Header.class);
   }


   /**
    * {@return the current, already scanned {@code Header}}
    */
   final Header getCurrentHeader() {
      return header;
   }

   /**
    * {@return the current already scanned {@coce Properties}}
    */
   final Properties getCurrentProperties() {
      return properties;
   }

   protected void ensureScanning() {
      ensureDataIsValid();
      ensureMessageDataScanned();
   }

   Object getDeliveryAnnotationProperty(Symbol symbol) {
      DeliveryAnnotations daToUse = deliveryAnnotations;
      if (daToUse == null) {
         daToUse = getDeliveryAnnotations();
      }
      if (daToUse == null) {
         return null;
      } else {
         return daToUse.getValue().get(symbol);
      }
   }

   /**
    * {@return a <em>copy</em> of the {@link DeliveryAnnotations} present in the message or {@code null} if non
    * present; changes to the returned DeliveryAnnotations instance do not affect the original message}
    */
   public final DeliveryAnnotations getDeliveryAnnotations() {
      ensureScanning();
      return scanForMessageSection(deliveryAnnotationsPosition, DeliveryAnnotations.class);
   }

   /**
    * Sets the delivery annotations to be included when encoding the message for sending it on the wire.
    * <p>
    * The broker can add additional message annotations as long as the annotations being added follow the rules from the
    * spec. If the user adds something that the remote doesn't understand and it is not prefixed with "x-opt" the remote
    * can just kill the link. See:
    * <p>
    * http://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-messaging-v1.0-os.html#type-annotations
    *
    * @param deliveryAnnotations delivery annotations used in the sendBuffer() method
    * @deprecated use {@link MessageReference#setProtocolData(Class, Object)}
    */
   @Deprecated
   public final void setDeliveryAnnotationsForSendBuffer(DeliveryAnnotations deliveryAnnotations) {
      this.deliveryAnnotationsForSendBuffer = deliveryAnnotations;
   }

   /**
    * {@return a <em>copy</em> of the message's {@link MessageAnnotations} or {@code null} if none present; changes to
    * the returned {@link MessageAnnotations} instance do not affect the original message}
    */
   public final MessageAnnotations getMessageAnnotations() {
      ensureScanning();
      return scanForMessageSection(messageAnnotationsPosition, MessageAnnotations.class);
   }

   /**
    * {@return a <em>copy</em> of the message's {@link Properties} if one exists or {@code null} if none present;
    * changes to the returned {@link Properties} instance do not affect the original message}
    */
   public final Properties getProperties() {
      ensureScanning();
      return scanForMessageSection(propertiesPosition, Properties.class);
   }

   /**
    * {@return a <em>copy</em> of the message's {@link ApplicationProperties} or {@code null} if none present; changes
    * to the returned {@link ApplicationProperties} instance do not affect the original message}
    */
   public final ApplicationProperties getApplicationProperties() {
      ensureScanning();
      return scanForMessageSection(applicationPropertiesPosition, ApplicationProperties.class);
   }

   /**
    * This is different from toString, as this will print an expanded version of the buffer in Hex and programmers's
    * readable format
    */
   public final String toDebugString() {
      return ByteUtil.debugByteArray(getData().array());
   }

   /**
    * Retrieves the AMQP Section that composes the body of this message by decoding a fresh copy from the encoded
    * message data.  Changes to the returned value are not reflected in the value encoded in the original message.
    *
    * @return the Section that makes up the body of this message
    */
   public final Section getBody() {
      ensureScanning();

      // We only handle Sections of AmqpSequence, AmqpValue and Data types so we filter on those.
      // There could also be a Footer and no body so this will prevent a faulty return type in case
      // of no body or message type we don't handle.
      return scanForMessageSection(Math.max(0, remainingBodyPosition), AmqpSequence.class, AmqpValue.class, Data.class);
   }

   /**
    * Retrieves the AMQP Footer encoded in the data of this message by decoding a fresh copy from the encoded message
    * data.  Changes to the returned value are not reflected in the value encoded in the original message.
    *
    * @return the Footer that was encoded into this AMQP Message
    */
   public final Footer getFooter() {
      ensureScanning();
      return scanForMessageSection(Math.max(0, remainingBodyPosition), Footer.class);
   }

   protected <T> T scanForMessageSection(int scanStartPosition, Class...targetTypes) {
      return scanForMessageSection(getData().duplicate().position(0), scanStartPosition, targetTypes);
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   protected <T> T scanForMessageSection(ReadableBuffer buffer, int scanStartPosition, Class...targetTypes) {
      ensureMessageDataScanned();

      // In cases where we parsed out enough to know the value is not encoded in the message
      // we can exit without doing any reads or buffer hopping.
      if (scanStartPosition == VALUE_NOT_PRESENT) {
         return null;
      }

      final DecoderImpl decoder = TLSEncode.getDecoder();

      buffer.position(scanStartPosition);

      T section = null;

      decoder.setBuffer(buffer);
      try {
         while (buffer.hasRemaining()) {
            TypeConstructor<?> constructor = decoder.readConstructor();
            for (Class<?> type : targetTypes) {
               if (type.equals(constructor.getTypeClass())) {
                  section = (T) constructor.readValue();
                  return section;
               }
            }

            constructor.skipValue();
         }
      } finally {
         decoder.setBuffer(null);
      }

      return section;
   }

   protected ApplicationProperties lazyDecodeApplicationProperties() {
      ensureMessageDataScanned();
      if (applicationProperties != null || applicationPropertiesPosition == VALUE_NOT_PRESENT) {
         return applicationProperties;
      }
      return lazyDecodeApplicationProperties(getData().duplicate().position(0));
   }

   // need to synchronize access to lazyDecodeApplicationProperties to avoid clashes with getMemoryEstimate
   protected synchronized ApplicationProperties lazyDecodeApplicationProperties(ReadableBuffer data) {
      if (applicationProperties == null && applicationPropertiesPosition != VALUE_NOT_PRESENT) {
         this.applicationProperties = readApplicationProperties(data, applicationPropertiesPosition);
      }

      return applicationProperties;
   }

   protected ApplicationProperties readApplicationProperties(ReadableBuffer data, int position) {
      return scanForMessageSection(data, position, ApplicationProperties.class);
   }

   @SuppressWarnings("unchecked")
   protected Map<String, Object> getApplicationPropertiesMap(boolean createIfAbsent) {
      ApplicationProperties appMap = lazyDecodeApplicationProperties();
      Map<String, Object> map = null;

      if (appMap != null) {
         map = appMap.getValue();
      }

      if (map == null) {
         if (createIfAbsent) {
            map = new HashMap<>();
            this.applicationProperties = new ApplicationProperties(map);
         } else {
            map = Collections.emptyMap();
         }
      }

      return map;
   }

   @SuppressWarnings("unchecked")
   protected Map<Symbol, Object> getMessageAnnotationsMap(boolean createIfAbsent) {
      Map<Symbol, Object> map = null;

      if (messageAnnotations != null) {
         map = messageAnnotations.getValue();
      }

      if (map == null) {
         if (createIfAbsent) {
            map = new HashMap<>();
            this.messageAnnotations = new MessageAnnotations(map);
         } else {
            map = Collections.emptyMap();
         }
      }

      return map;
   }

   protected Object getMessageAnnotation(String annotation) {
      Object messageAnnotation = getMessageAnnotation(Symbol.getSymbol(AMQPMessageSupport.toAnnotationName(annotation)));
      if (messageAnnotation == null) {
         messageAnnotation = getMessageAnnotation(Symbol.getSymbol(annotation));
      }
      return messageAnnotation;
   }

   protected Object getMessageAnnotation(Symbol annotation) {
      ensureMessageDataScanned();
      return getMessageAnnotationsMap(false).get(annotation);
   }

   protected Object removeMessageAnnotation(Symbol annotation) {
      return getMessageAnnotationsMap(false).remove(annotation);
   }

   protected void setMessageAnnotation(String annotation, Object value) {
      setMessageAnnotation(Symbol.getSymbol(annotation), value);
   }

   protected void setMessageAnnotation(Symbol annotation, Object value) {
      if (value instanceof SimpleString) {
         value = value.toString();
      }
      getMessageAnnotationsMap(true).put(annotation, value);
   }

   protected void setMessageAnnotations(MessageAnnotations messageAnnotations) {
      this.messageAnnotations = messageAnnotations;
   }

   // Message decoding and copying methods.  Care must be taken here to ensure the buffer and the
   // state tracking information is kept up to data.  When the message is manually changed a forced
   // re-encode should be done to update the backing data with the in memory elements.

   protected synchronized void ensureMessageDataScanned() {
      final MessageDataScanningStatus state = getDataScanningStatus();
      switch (state) {
         case NOT_SCANNED:
            scanMessageData();
            break;
         case SCANNED:
            // NO-OP
            break;
      }
   }


   protected int getEstimateSavedEncode() {
      return remainingBodyPosition > 0 ? remainingBodyPosition : 1024;
   }

   protected synchronized void resetMessageData() {
      header = null;
      applicationPropertiesCount = 0;
      messageAnnotations = null;
      properties = null;
      applicationProperties = null;
      if (!expirationReload) {
         expiration = 0;
      }
      priority = VALUE_NOT_PRESENT;
      durable = VALUE_NOT_PRESENT;
      encodedHeaderSize = 0;
      memoryEstimate = VALUE_NOT_PRESENT;
      scheduledTime = VALUE_NOT_PRESENT;
      encodedDeliveryAnnotationsSize = 0;
      headerPosition = VALUE_NOT_PRESENT;
      deliveryAnnotationsPosition = VALUE_NOT_PRESENT;
      propertiesPosition = VALUE_NOT_PRESENT;
      applicationPropertiesPosition = VALUE_NOT_PRESENT;
      remainingBodyPosition = VALUE_NOT_PRESENT;
   }

   protected synchronized void scanMessageData() {
      scanMessageData(getData());
   }

   protected synchronized void scanMessageData(ReadableBuffer data) {
      DecoderImpl decoder = TLSEncode.getDecoder();
      decoder.setBuffer(data);

      resetMessageData();

      try {
         while (data.hasRemaining()) {
            int constructorPos = data.position();
            TypeConstructor<?> constructor = decoder.readConstructor();
            if (Header.class.equals(constructor.getTypeClass())) {
               header = (Header) constructor.readValue();
               headerPosition = constructorPos;
               encodedHeaderSize = data.position() - constructorPos;
               if (header.getTtl() != null) {
                  if (!expirationReload) {
                     expiration = System.currentTimeMillis() + header.getTtl().longValue();
                  }
               }
               if (header.getPriority() != null) {
                  priority = (byte) Math.min(header.getPriority().intValue(), MAX_MESSAGE_PRIORITY);
               }
            } else if (DeliveryAnnotations.class.equals(constructor.getTypeClass())) {
               deliveryAnnotationsPosition = constructorPos;
               this.deliveryAnnotations = (DeliveryAnnotations) constructor.readValue();
               encodedDeliveryAnnotationsSize = data.position() - constructorPos;
            } else if (MessageAnnotations.class.equals(constructor.getTypeClass())) {
               messageAnnotationsPosition = constructorPos;
               messageAnnotations = (MessageAnnotations) constructor.readValue();
            } else if (Properties.class.equals(constructor.getTypeClass())) {
               propertiesPosition = constructorPos;
               properties = (Properties) constructor.readValue();

               if (properties.getAbsoluteExpiryTime() != null && properties.getAbsoluteExpiryTime().getTime() > 0) {
                  if (!expirationReload) {
                     expiration = properties.getAbsoluteExpiryTime().getTime();
                  }
               }
            } else if (ApplicationProperties.class.equals(constructor.getTypeClass())) {
               // Lazy decoding will start at the TypeConstructor of these ApplicationProperties
               // but we scan past it to grab the location of the possible body and footer section.
               applicationPropertiesPosition = constructorPos;
               this.applicationPropertiesCount = parseApCountAndSkip(data);
               remainingBodyPosition = data.hasRemaining() ? data.position() : VALUE_NOT_PRESENT;
               break;
            } else {
               // This will be either the body or a Footer section which will be treated as an immutable
               // and be copied as is when re-encoding the message.
               remainingBodyPosition = constructorPos;
               break;
            }
         }
      } finally {
         decoder.setBuffer(null);
         data.rewind();
      }
      this.messageDataScanned = MessageDataScanningStatus.SCANNED.code;
   }

   public int getApplicationPropertiesCount() {
      ensureScanning();
      return applicationPropertiesCount;
   }


   // this is "borrowed" from:
   // https://github.com/apache/qpid-proton-j/blob/6dc5587f1d1b23969a8994f1755198e638e92bc4/proton-j/src/main/java/org/apache/qpid/proton/codec/messaging/FastPathApplicationPropertiesType.java#L93-L115
   private static int parseApCountAndSkip(ReadableBuffer data) {
      byte encodingCode = data.get();
      int count;
      int size;
      int sizePosition;
      switch (encodingCode) {
         case EncodingCodes.MAP8:
            size = data.get() & 0xff;
            sizePosition = data.position();
            count = data.get() & 0xff;
            break;
         case EncodingCodes.MAP32:
            size = data.getInt();
            sizePosition = data.position();
            count = data.getInt();
            break;
         case EncodingCodes.NULL:
            sizePosition = data.position();
            size = 0;
            count = 0;
            break;
         default:
            throw new ProtonException("Expected Map type but found encoding: " + encodingCode);
      }

      data.position(sizePosition + size);

      return count / 2;
   }

   @Override
   public abstract org.apache.activemq.artemis.api.core.Message copy();

   // Core Message APIs for persisting and encoding of message data along with
   // utilities for checking memory usage and encoded size characteristics.

   /**
    * Would be called by the Artemis Core components to encode the message into the provided send buffer.  Because of
    * how Proton message data handling works this method is not currently used by the AMQP protocol head and will not be
    * called for out-bound sends.
    *
    * @see #getSendBuffer(int, MessageReference) for the actual method used for message sends.
    */
   @Override
   public final void sendBuffer(ByteBuf buffer, int deliveryCount) {
      ensureDataIsValid();
      NettyWritable writable = new NettyWritable(buffer);
      writable.put(getSendBuffer(deliveryCount, null));
   }

   /**
    * Gets a ByteBuf from the Message that contains the encoded bytes to be sent on the wire.
    * <p>
    * When possible this method will present the bytes to the caller without copying them into a new buffer copy.  If
    * copying is needed a new Netty buffer is created and returned. The caller should ensure that the reference count on
    * the returned buffer is always decremented to avoid a leak in the case of a copied buffer being returned.
    *
    * @param deliveryCount The new delivery count for this message.
    * @return a Netty ByteBuf containing the encoded bytes of this Message instance
    */
   public ReadableBuffer getSendBuffer(int deliveryCount, MessageReference reference) {
      ensureMessageDataScanned();
      ensureDataIsValid();

      DeliveryAnnotations daToWrite = reference != null ? reference.getProtocolData(DeliveryAnnotations.class) : null;

      if (reference == null) {
         // deliveryAnnotationsForSendBuffer is part of an older API, deprecated but still present
         daToWrite = deliveryAnnotationsForSendBuffer;
      }

      if (deliveryCount > 1 || daToWrite != null || deliveryAnnotationsPosition != VALUE_NOT_PRESENT) {
         return createDeliveryCopy(deliveryCount, daToWrite);
      } else {
         // Common case message has no delivery annotations, no delivery annotations for the send buffer were set
         // and this is the first delivery so no re-encoding or section skipping needed.
         return getData().duplicate();
      }
   }

   /**
    * it will create a copy with the relevant delivery annotation and its copy
    */
   protected ReadableBuffer createDeliveryCopy(int deliveryCount, DeliveryAnnotations deliveryAnnotations) {
      ReadableBuffer duplicate = getData().duplicate();

      final int amqpDeliveryCount = deliveryCount - 1;

      final ByteBuf result = PooledByteBufAllocator.DEFAULT.heapBuffer(getEncodeSize());

      // If this is re-delivering the message then the header must be re-encoded
      // otherwise we want to write the original header if present.  When a
      // Header is present we need to copy it as we are updating the re-delivered
      // message and not the stored version which we don't want to invalidate here.
      Header localHeader = this.header;
      if (localHeader == null) {
         if (deliveryCount > 1) {
            localHeader = new Header();
         }
      } else {
         localHeader = new Header(header);
      }

      if (localHeader != null) {
         localHeader.setDeliveryCount(UnsignedInteger.valueOf(amqpDeliveryCount));
         TLSEncode.getEncoder().setByteBuffer(new NettyWritable(result));
         TLSEncode.getEncoder().writeObject(localHeader);
         TLSEncode.getEncoder().setByteBuffer((WritableBuffer) null);
      }

      writeDeliveryAnnotationsForSendBuffer(result, deliveryAnnotations);

      // skip existing delivery annotations of the original message
      duplicate.position(encodedHeaderSize + encodedDeliveryAnnotationsSize);
      result.writeBytes(duplicate.byteBuffer());

      return new NettyReadable(result);
   }

   protected void writeDeliveryAnnotationsForSendBuffer(ByteBuf result, DeliveryAnnotations deliveryAnnotations) {
      if (deliveryAnnotations != null && !deliveryAnnotations.getValue().isEmpty()) {
         TLSEncode.getEncoder().setByteBuffer(new NettyWritable(result));
         TLSEncode.getEncoder().writeObject(deliveryAnnotations);
         TLSEncode.getEncoder().setByteBuffer((WritableBuffer) null);
      }
   }

   protected int getDeliveryAnnotationsForSendBufferSize() {
      if (deliveryAnnotationsForSendBuffer == null || deliveryAnnotationsForSendBuffer.getValue().isEmpty()) {
         return 0;
      }
      DroppingWritableBuffer droppingWritableBuffer = new DroppingWritableBuffer();
      TLSEncode.getEncoder().setByteBuffer(droppingWritableBuffer);
      TLSEncode.getEncoder().writeObject(deliveryAnnotationsForSendBuffer);
      TLSEncode.getEncoder().setByteBuffer((WritableBuffer) null);
      return droppingWritableBuffer.position() + 1;
   }

   @Override
   public void messageChanged() {
      modified = true;
   }

   @Override
   public abstract int getEncodeSize();

   @Override
   public final void receiveBuffer(ByteBuf buffer) {
      // Not used for AMQP messages.
   }

   @Override
   public abstract int getMemoryEstimate();

   @Override
   public Map<String, Object> toPropertyMap(int valueSizeLimit) {
      return toPropertyMap(false, valueSizeLimit);
   }

   private Map<String, Object> toPropertyMap(boolean expandPropertyType, int valueSizeLimit) {
      String extraPropertiesPrefix;
      String applicationPropertiesPrefix;
      String annotationPrefix;
      String propertiesPrefix;
      if (expandPropertyType) {
         extraPropertiesPrefix = "extraProperties.";
         applicationPropertiesPrefix = "applicationProperties.";
         annotationPrefix = "messageAnnotations.";
         propertiesPrefix = "properties.";
      } else {
         extraPropertiesPrefix = "";
         applicationPropertiesPrefix = "";
         annotationPrefix = "";
         propertiesPrefix = "";
      }
      Map map = new HashMap<>();
      for (SimpleString name : getPropertyNames()) {
         Object value = getObjectProperty(name.toString());
         //some property is Binary, which is not available for management console
         if (value instanceof Binary binary) {
            value = binary.getArray();
         }
         map.put(applicationPropertiesPrefix + name, JsonUtil.truncate(value, valueSizeLimit));
      }

      TypedProperties extraProperties = getExtraProperties();
      if (extraProperties != null) {
         extraProperties.forEach((s, o) -> {
            if (o instanceof Number) {
               // keep fields like _AMQ_ACTUAL_EXPIRY in their original type
               map.put(extraPropertiesPrefix + s.toString(), o);
            } else {
               map.put(extraPropertiesPrefix + s.toString(), JsonUtil.truncate(o != null ? o.toString() : o, valueSizeLimit));
            }
         });
      }

      addAnnotationsAsProperties(annotationPrefix, map, messageAnnotations);

      if (properties != null) {
         if (properties.getContentType() != null) {
            map.put(propertiesPrefix + "contentType", properties.getContentType().toString());
         }
         if (properties.getContentEncoding() != null) {
            map.put(propertiesPrefix + "contentEncoding", properties.getContentEncoding().toString());
         }
         if (properties.getGroupId() != null) {
            map.put(propertiesPrefix + "groupId", properties.getGroupId());
         }
         if (properties.getGroupSequence() != null) {
            map.put(propertiesPrefix + "groupSequence", properties.getGroupSequence().intValue());
         }
         if (properties.getReplyToGroupId() != null) {
            map.put(propertiesPrefix + "replyToGroupId", properties.getReplyToGroupId());
         }
         if (properties.getCreationTime() != null) {
            map.put(propertiesPrefix + "creationTime", properties.getCreationTime().getTime());
         }
         if (properties.getAbsoluteExpiryTime() != null) {
            map.put(propertiesPrefix + "absoluteExpiryTime", properties.getAbsoluteExpiryTime().getTime());
         }
         if (properties.getTo() != null) {
            map.put(propertiesPrefix + "to", properties.getTo());
         }
         if (properties.getSubject() != null) {
            map.put(propertiesPrefix + "subject", properties.getSubject());
         }
         if (properties.getReplyTo() != null) {
            map.put(propertiesPrefix + "replyTo", properties.getReplyTo());
         }
         if (properties.getCorrelationId() != null) {
            map.put(propertiesPrefix + "correlationId", properties.getCorrelationId());
         }
      }

      return map;
   }


   protected static void addAnnotationsAsProperties(String prefix, Map map, MessageAnnotations annotations) {
      if (annotations != null && annotations.getValue() != null) {
         for (Map.Entry<?, ?> entry : annotations.getValue().entrySet()) {
            String key = entry.getKey().toString();
            if ("x-opt-delivery-time".equals(key) && entry.getValue() != null) {
               long deliveryTime = ((Number) entry.getValue()).longValue();
               map.put(prefix + "x-opt-delivery-time", deliveryTime);
            } else if ("x-opt-delivery-delay".equals(key) && entry.getValue() != null) {
               long delay = ((Number) entry.getValue()).longValue();
               map.put(prefix + "x-opt-delivery-delay", delay);
            } else if (AMQPMessageSupport.X_OPT_INGRESS_TIME.equals(key) && entry.getValue() != null) {
               map.put(prefix + AMQPMessageSupport.X_OPT_INGRESS_TIME, ((Number) entry.getValue()).longValue());
            } else {
               try {
                  map.put(prefix + key, entry.getValue());
               } catch (ActiveMQPropertyConversionException e) {
                  logger.warn(e.getMessage(), e);
               }
            }
         }
      }
   }


   @Override
   public ICoreMessage toCore(CoreMessageObjectPools coreMessageObjectPools) {
      try {
         ensureScanning();
         return AmqpCoreConverter.toCore(
            this, coreMessageObjectPools, header, messageAnnotations, properties, lazyDecodeApplicationProperties(), getBody(), getFooter());
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   @Override
   public ICoreMessage toCore() {
      return toCore(coreMessageObjectPools);
   }

   @Override
   public abstract void persist(ActiveMQBuffer targetRecord);

   @Override
   public abstract int getPersistSize();

   protected int internalPersistSize() {
      return getData().remaining();
   }

   @Override
   public abstract void reloadPersistence(ActiveMQBuffer record, CoreMessageObjectPools pools);

   @Override
   public abstract long getPersistentSize() throws ActiveMQException;

   @Override
   public abstract Persister<org.apache.activemq.artemis.api.core.Message> getPersister();

   @Override
   public abstract void reencode();

   protected abstract void ensureDataIsValid();

   protected abstract void encodeMessage();

   // These methods interact with the Extra Properties that can accompany the message but
   // because these are not sent on the wire, update to these does not force a re-encode on
   // send of the message.

   public final TypedProperties createExtraProperties() {
      if (extraProperties == null) {
         extraProperties = new TypedProperties(INTERNAL_PROPERTY_NAMES_PREDICATE);
      }
      return extraProperties;
   }

   public final TypedProperties getExtraProperties() {
      return extraProperties;
   }

   public final AMQPMessage setExtraProperties(TypedProperties extraProperties) {
      this.extraProperties = extraProperties;
      return this;
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putExtraBytesProperty(SimpleString key, byte[] value) {
      createExtraProperties().putBytesProperty(key, value);
      return this;
   }

   @Override
   public final byte[] getExtraBytesProperty(SimpleString key) throws ActiveMQPropertyConversionException {
      if (extraProperties == null) {
         return null;
      } else {
         return extraProperties.getBytesProperty(key);
      }
   }

   @Override
   public void clearInternalProperties() {
      if (extraProperties != null) {
         extraProperties.clearInternalProperties();
      }
   }

   @Override
   public final byte[] removeExtraBytesProperty(SimpleString key) throws ActiveMQPropertyConversionException {
      if (extraProperties == null) {
         return null;
      } else {
         return (byte[])extraProperties.removeProperty(key);
      }
   }

   // Message meta data access for Core and AMQP usage.

   @Override
   public final org.apache.activemq.artemis.api.core.Message setConnectionID(String connectionID) {
      this.connectionID = connectionID;
      return this;
   }

   @Override
   public final String getConnectionID() {
      return connectionID;
   }

   public final long getMessageFormat() {
      return messageFormat;
   }

   @Override
   public final long getMessageID() {
      return messageID;
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message setMessageID(long id) {
      this.messageID = id;
      return this;
   }

   @Override
   public final long getExpiration() {
      if (!expirationReload) {
         ensureMessageDataScanned();
      }
      return expiration;
   }

   public void reloadExpiration(long expiration) {
      this.expiration = expiration;
      this.expirationReload = true;
   }

   @Override
   public final AMQPMessage setExpiration(long expiration) {
      if (properties != null) {
         if (expiration <= 0) {
            properties.setAbsoluteExpiryTime(null);
         } else {
            properties.setAbsoluteExpiryTime(new Date(expiration));
         }
      } else if (expiration > 0) {
         properties = new Properties();
         properties.setAbsoluteExpiryTime(new Date(expiration));
      }

      // We are overriding expiration with an Absolute expiration time so any
      // previous Header based TTL also needs to be removed.
      if (header != null) {
         header.setTtl(null);
      }

      this.expiration = Math.max(0, expiration);

      return this;
   }

   @Override
   public final Object getUserID() {
      ensureMessageDataScanned();

      // User ID in Artemis API means Message ID
      if (properties != null && properties.getMessageId() != null) {
         return properties.getMessageId();
      } else {
         return null;
      }
   }

   /**
    * Before we added AMQP into Artemis the name getUserID was already taken by JMSMessageID. We cannot simply change
    * the names now as it would break the API for existing clients.
    * <p>
    * This is to return and read the proper AMQP userID.
    *
    * @return the UserID value in the AMQP Properties if one is present
    */
   public final Object getAMQPUserID() {
      ensureMessageDataScanned();

      if (properties != null && properties.getUserId() != null) {
         Binary binary = properties.getUserId();
         return new String(binary.getArray(), binary.getArrayOffset(), binary.getLength(), StandardCharsets.UTF_8);
      } else {
         return null;
      }
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message setUserID(Object userID) {
      return this;
   }

   @Override
   public final Object getDuplicateProperty() {
      if (applicationProperties == null && messageDataScanned == MessageDataScanningStatus.SCANNED.code && applicationPropertiesPosition != VALUE_NOT_PRESENT) {
         if (!AMQPMessageSymbolSearch.anyApplicationProperties(getData(), DUPLICATE_ID_NEEDLES, applicationPropertiesPosition)) {
            // no need for duplicate-property
            return null;
         }
      }
      return getApplicationObjectProperty(org.apache.activemq.artemis.api.core.Message.HDR_DUPLICATE_DETECTION_ID.toString());
   }

   @Override
   public boolean isDurable() {
      if (this.durable == VALUE_NOT_PRESENT) {
         if (header != null && header.getDurable() != null) {
            reloadSetDurable(header.getDurable());
         } else {
            this.durable = NON_DURABLE;
         }
      }
      return this.durable == DURABLE;
   }

   void reloadSetDurable(boolean booleanDurable) {
      this.durable = booleanDurable ? DURABLE : NON_DURABLE;
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message setDurable(boolean durable) {
      if (header == null) {
         header = new Header();
      }

      header.setDurable(durable);  // Message needs to be re-encoded following this action.
      reloadSetDurable(durable);

      return this;
   }

   @Override
   public final String getAddress() {
      return Objects.toString(getAddressSimpleString(), null);
   }

   @Override
   public final AMQPMessage setAddress(String address) {
      setAddress(cachedAddressSimpleString(address));
      return this;
   }

   @Override
   public final AMQPMessage setAddress(SimpleString address) {
      this.address = address;
      createExtraProperties().putSimpleStringProperty(ADDRESS_PROPERTY, address);
      return this;
   }

   final void reloadAddress(SimpleString address) {
      this.address = address;
   }

   @Override
   public final SimpleString getAddressSimpleString() {
      if (address == null) {
         TypedProperties extraProperties = getExtraProperties();

         // we first check if extraProperties is not null, no need to create it just to check it here
         if (extraProperties != null) {
            address = extraProperties.getSimpleStringProperty(ADDRESS_PROPERTY);
         }

         if (address == null) {
            // if it still null, it will look for the address on the getTo();
            if (properties != null && properties.getTo() != null) {
               address = cachedAddressSimpleString(properties.getTo());
            }
         }
      }
      return address;
   }

   protected SimpleString cachedAddressSimpleString(String address) {
      return CoreMessageObjectPools.cachedAddressSimpleString(address, coreMessageObjectPools);
   }

   @Override
   public final long getTimestamp() {
      ensureMessageDataScanned();

      if (properties != null && properties.getCreationTime() != null) {
         return properties.getCreationTime().getTime();
      } else {
         return 0L;
      }
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message setTimestamp(long timestamp) {
      if (properties == null) {
         properties = new Properties();
      }
      properties.setCreationTime(new Date(timestamp));
      return this;
   }

   @Override
   public final byte getPriority() {
      if (priority == VALUE_NOT_PRESENT) {
         ensureScanning();
         // if still not present, it means it doesn't have it, so we use the default
         if (priority == VALUE_NOT_PRESENT) {
            priority = DEFAULT_MESSAGE_PRIORITY;
         }
      }
      return priority;
   }

   void reloadPriority(byte priority) {
      this.priority = priority;
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message setPriority(byte priority) {
      // Internally we can only deal with a limited range, but the AMQP value is allowed
      // to span the full range of the unsigned byte so we store what was actually set in
      // the AMQP Header section.
      this.priority = (byte) Math.min(priority & 0xff, MAX_MESSAGE_PRIORITY);

      if (header == null) {
         header = new Header();
      }
      header.setPriority(UnsignedByte.valueOf(priority));
      this.priority = priority;

      return this;
   }

   @Override
   public final SimpleString getReplyTo() {
      ensureMessageDataScanned();

      if (properties != null) {
         return SimpleString.of(properties.getReplyTo());
      } else {
         return null;
      }
   }

   @Override
   public final AMQPMessage setReplyTo(SimpleString address) {
      if (properties == null) {
         properties = new Properties();
      }

      properties.setReplyTo(Objects.toString(address, null));
      return this;
   }

   @Override
   public final RoutingType getRoutingType() {
      ensureMessageDataScanned();
      Object routingType = getMessageAnnotation(AMQPMessageSupport.ROUTING_TYPE);

      if (routingType != null) {
         return RoutingType.getType(((Number) routingType).byteValue());
      } else {
         routingType = getMessageAnnotation(AMQPMessageSupport.JMS_DEST_TYPE_MSG_ANNOTATION);
         if (routingType != null) {
            if (AMQPMessageSupport.QUEUE_TYPE == ((Number) routingType).byteValue() || AMQPMessageSupport.TEMP_QUEUE_TYPE == ((Number) routingType).byteValue()) {
               return RoutingType.ANYCAST;
            } else if (AMQPMessageSupport.TOPIC_TYPE == ((Number) routingType).byteValue() || AMQPMessageSupport.TEMP_TOPIC_TYPE == ((Number) routingType).byteValue()) {
               return RoutingType.MULTICAST;
            }
         } else {
            return null;
         }

         return null;
      }
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message setRoutingType(RoutingType routingType) {
      if (routingType == null) {
         removeMessageAnnotation(AMQPMessageSupport.ROUTING_TYPE);
      } else {
         setMessageAnnotation(AMQPMessageSupport.ROUTING_TYPE, routingType.getType());
      }
      return this;
   }

   @Override
   public final SimpleString getGroupID() {
      ensureMessageDataScanned();

      if (properties != null && properties.getGroupId() != null) {
         return SimpleString.of(properties.getGroupId(),
                                coreMessageObjectPools == null ? null : coreMessageObjectPools.getGroupIdStringSimpleStringPool());
      } else {
         return null;
      }
   }

   @Override
   public final int getGroupSequence() {
      ensureMessageDataScanned();

      if (properties != null && properties.getGroupSequence() != null) {
         return properties.getGroupSequence().intValue();
      } else {
         return 0;
      }
   }

   @Override
   public final Object getCorrelationID() {
      ensureMessageDataScanned();
      return properties != null ? properties.getCorrelationId() : null;
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message setCorrelationID(final Object correlationID) {
      if (properties == null) {
         properties = new Properties();
      }

      properties.setCorrelationId(correlationID);
      return this;
   }

   public final String getSubject() {
      ensureMessageDataScanned();

      if (properties != null) {
         return properties.getSubject();
      } else {
         return null;
      }
   }

   @Override
   public boolean hasScheduledDeliveryTime() {
      if (scheduledTime >= 0) {
         return scheduledTime > 0;
      }
      return anyMessageAnnotations(SCHEDULED_DELIVERY_SYMBOLS, SCHEDULED_DELIVERY_NEEDLES);
   }

   private boolean anyMessageAnnotations(Symbol[] symbols, KMPNeedle[] symbolNeedles) {
      assert symbols.length == symbolNeedles.length;
      final int count = symbols.length;
      if (messageDataScanned == MessageDataScanningStatus.SCANNED.code) {
         final MessageAnnotations messageAnnotations = this.messageAnnotations;
         if (messageAnnotations == null) {
            return false;
         }
         Map<Symbol, Object> map = messageAnnotations.getValue();
         if (map == null) {
            return false;
         }
         for (int i = 0; i < count; i++) {
            if (map.containsKey(symbols[i])) {
               return true;
            }
         }
         return false;
      }
      return AMQPMessageSymbolSearch.anyMessageAnnotations(getData(), symbolNeedles);
   }

   @Override
   public final Long getScheduledDeliveryTime() {
      ensureMessageDataScanned();
      if (scheduledTime < 0) {
         Object objscheduledTime = getMessageAnnotation(AMQPMessageSupport.SCHEDULED_DELIVERY_TIME);
         Object objdelay = getMessageAnnotation(AMQPMessageSupport.SCHEDULED_DELIVERY_DELAY);

         if (objscheduledTime != null && objscheduledTime instanceof Number number) {
            this.scheduledTime = number.longValue();
         } else if (objdelay != null && objdelay instanceof Number number) {
            this.scheduledTime = System.currentTimeMillis() + number.longValue();
         } else {
            this.scheduledTime = 0;
         }
      }

      return scheduledTime;
   }

   @Override
   public final AMQPMessage setScheduledDeliveryTime(Long time) {
      if (time != null && time.longValue() > 0) {
         setMessageAnnotation(AMQPMessageSupport.SCHEDULED_DELIVERY_TIME, time);
         removeMessageAnnotation(AMQPMessageSupport.SCHEDULED_DELIVERY_DELAY);
         scheduledTime = time;
      } else {
         removeMessageAnnotation(AMQPMessageSupport.SCHEDULED_DELIVERY_TIME);
         removeMessageAnnotation(AMQPMessageSupport.SCHEDULED_DELIVERY_DELAY);
         scheduledTime = 0;
      }

      return this;
   }

   @Override
   public final Object removeAnnotation(SimpleString key) {
      return removeMessageAnnotation(Symbol.getSymbol(key.toString()));
   }

   @Override
   public final Object getAnnotation(SimpleString key) {
      return getMessageAnnotation(key.toString());
   }

   @Override
   public final AMQPMessage setAnnotation(SimpleString key, Object value) {
      setMessageAnnotation(key.toString(), value);
      return this;
   }


   @Override
   public org.apache.activemq.artemis.api.core.Message setBrokerProperty(SimpleString key, Object value) {
      // Annotation names have to start with x-opt
      setMessageAnnotation(AMQPMessageSupport.toAnnotationName(key.toString()), value);
      createExtraProperties().putProperty(key, value);
      return this;
   }

   @Override
   public Object getBrokerProperty(SimpleString key) {
      TypedProperties extra = getExtraProperties();
      if (extra == null) {
         return null;
      }
      return extra.getProperty(key);
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message setIngressTimestamp() {
      setMessageAnnotation(AMQPMessageSupport.INGRESS_TIME_MSG_ANNOTATION, System.currentTimeMillis());
      return this;
   }

   @Override
   public Long getIngressTimestamp() {
      return (Long) getMessageAnnotation(AMQPMessageSupport.INGRESS_TIME_MSG_ANNOTATION);
   }

   // JMS Style property access methods.  These can result in additional decode of AMQP message
   // data from Application properties.  Updates to application properties puts the message in a
   // dirty state and requires a re-encode of the data to update all buffer state data otherwise
   // the next send of the Message will not include changes made here.

   @Override
   public final Object removeProperty(SimpleString key) {
      return removeProperty(key.toString());
   }

   @Override
   public final Object removeProperty(String key) {
      return getApplicationPropertiesMap(false).remove(key);
   }

   @Override
   public final boolean containsProperty(SimpleString key) {
      return containsProperty(key.toString());
   }

   @Override
   public final boolean containsProperty(String key) {
      return getApplicationPropertiesMap(false).containsKey(key);
   }

   @Override
   public final Boolean getBooleanProperty(String key) throws ActiveMQPropertyConversionException {
      return (Boolean) getApplicationPropertiesMap(false).get(key);
   }

   @Override
   public final Byte getByteProperty(String key) throws ActiveMQPropertyConversionException {
      return (Byte) getApplicationPropertiesMap(false).get(key);
   }

   @Override
   public final Double getDoubleProperty(String key) throws ActiveMQPropertyConversionException {
      return (Double) getApplicationPropertiesMap(false).get(key);
   }

   @Override
   public final Integer getIntProperty(String key) throws ActiveMQPropertyConversionException {
      return (Integer) getApplicationPropertiesMap(false).get(key);
   }

   @Override
   public final Long getLongProperty(String key) throws ActiveMQPropertyConversionException {
      return (Long) getApplicationPropertiesMap(false).get(key);
   }

   @Override
   public final Object getObjectProperty(String key) {
      switch (key) {
         case MessageUtil.TYPE_HEADER_NAME_STRING:
            return getSubject();
         case MessageUtil.CONNECTION_ID_PROPERTY_NAME_STRING:
            return getConnectionID();
         case MessageUtil.JMSXGROUPID:
            return getGroupID();
         case MessageUtil.JMSXGROUPSEQ:
            return getGroupSequence();
         case MessageUtil.JMSXUSERID:
            return getAMQPUserID();
         case MessageUtil.CORRELATIONID_HEADER_NAME_STRING:
            final Object correlationID = getCorrelationID();
            if (correlationID != null) {
               return AMQPMessageIdHelper.INSTANCE.toCorrelationIdStringOrBytes(correlationID);
            } else {
               return null;
            }
         default:
            return getApplicationObjectProperty(key);
      }
   }

   private Object getApplicationObjectProperty(String key) {
      Object value = getApplicationPropertiesMap(false).get(key);
      if (value instanceof Number number) {
         // AMQP Numeric types must be converted to a compatible value.
         if (value instanceof UnsignedInteger ||
             value instanceof UnsignedByte ||
             value instanceof UnsignedLong ||
             value instanceof UnsignedShort) {
            return number.longValue();
         }
      } else if (value instanceof Binary) {
         // Binary wrappers must be unwrapped into a byte[] form.
         return getBytesProperty(key);
      }

      return value;
   }

   @Override
   public final Short getShortProperty(String key) throws ActiveMQPropertyConversionException {
      return (Short) getApplicationPropertiesMap(false).get(key);
   }

   @Override
   public final Float getFloatProperty(String key) throws ActiveMQPropertyConversionException {
      return (Float) getApplicationPropertiesMap(false).get(key);
   }

   @Override
   public final String getStringProperty(String key) throws ActiveMQPropertyConversionException {
      return switch (key) {
         case MessageUtil.TYPE_HEADER_NAME_STRING -> properties.getSubject();
         case MessageUtil.CONNECTION_ID_PROPERTY_NAME_STRING -> getConnectionID();
         default -> (String) getApplicationPropertiesMap(false).get(key);
      };
   }

   @Override
   public final Set<SimpleString> getPropertyNames() {
      Set<SimpleString> values = new HashSet<>();
      for (Object k : getApplicationPropertiesMap(false).keySet()) {
         values.add(SimpleString.of(k.toString(), getPropertyKeysPool()));
      }
      return values;
   }

   @Override
   public final Boolean getBooleanProperty(SimpleString key) throws ActiveMQPropertyConversionException {
      return getBooleanProperty(key.toString());
   }

   @Override
   public final Byte getByteProperty(SimpleString key) throws ActiveMQPropertyConversionException {
      return getByteProperty(key.toString());
   }

   @Override
   public final byte[] getBytesProperty(String key) throws ActiveMQPropertyConversionException {
      final Object value = getApplicationPropertiesMap(false).get(key);

      if (value instanceof Binary binary) {

         if (binary.getArray() == null) {
            return null;
         } else if (binary.getArrayOffset() == 0 && binary.getLength() == binary.getArray().length) {
            return binary.getArray();
         } else {
            final byte[] payload = new byte[binary.getLength()];

            System.arraycopy(binary.getArray(), binary.getArrayOffset(), payload, 0, binary.getLength());

            return payload;
         }
      } else {
         return (byte[]) value;
      }
   }

   @Override
   public final Double getDoubleProperty(SimpleString key) throws ActiveMQPropertyConversionException {
      return getDoubleProperty(key.toString());
   }

   @Override
   public final Integer getIntProperty(SimpleString key) throws ActiveMQPropertyConversionException {
      return getIntProperty(key.toString());
   }

   @Override
   public final Long getLongProperty(SimpleString key) throws ActiveMQPropertyConversionException {
      return getLongProperty(key.toString());
   }

   @Override
   public final Object getObjectProperty(SimpleString key) {
      return getObjectProperty(key.toString());
   }

   @Override
   public final Short getShortProperty(SimpleString key) throws ActiveMQPropertyConversionException {
      return getShortProperty(key.toString());
   }

   @Override
   public final Float getFloatProperty(SimpleString key) throws ActiveMQPropertyConversionException {
      return getFloatProperty(key.toString());
   }

   @Override
   public final String getStringProperty(SimpleString key) throws ActiveMQPropertyConversionException {
      return getStringProperty(key.toString());
   }

   @Override
   public final SimpleString getSimpleStringProperty(SimpleString key) throws ActiveMQPropertyConversionException {
      return getSimpleStringProperty(key.toString());
   }

   @Override
   public final byte[] getBytesProperty(SimpleString key) throws ActiveMQPropertyConversionException {
      return getBytesProperty(key.toString());
   }
   @Override
   public final SimpleString getSimpleStringProperty(String key) throws ActiveMQPropertyConversionException {
      return SimpleString.of((String) getApplicationPropertiesMap(false).get(key), getPropertyValuesPool());
   }

   // Core Message Application Property update methods, calling these puts the message in a dirty
   // state and requires a re-encode of the data to update all buffer state data.  If no re-encode
   // is done prior to the next dispatch the old view of the message will be sent.

   @Override
   public final org.apache.activemq.artemis.api.core.Message putBooleanProperty(String key, boolean value) {
      getApplicationPropertiesMap(true).put(key, value);
      return this;
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putByteProperty(String key, byte value) {
      getApplicationPropertiesMap(true).put(key, value);
      return this;
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putBytesProperty(String key, byte[] value) {
      Binary payload = null;

      if (value != null) {
         payload = new Binary(value);
      }

      getApplicationPropertiesMap(true).put(key, payload);

      return this;
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putShortProperty(String key, short value) {
      getApplicationPropertiesMap(true).put(key, value);
      return this;
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putCharProperty(String key, char value) {
      getApplicationPropertiesMap(true).put(key, value);
      return this;
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putIntProperty(String key, int value) {
      getApplicationPropertiesMap(true).put(key, value);
      return this;
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putLongProperty(String key, long value) {
      getApplicationPropertiesMap(true).put(key, value);
      return this;
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putFloatProperty(String key, float value) {
      getApplicationPropertiesMap(true).put(key, value);
      return this;
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putDoubleProperty(String key, double value) {
      getApplicationPropertiesMap(true).put(key, value);
      return this;
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putBooleanProperty(SimpleString key, boolean value) {
      getApplicationPropertiesMap(true).put(key.toString(), value);
      return this;
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putByteProperty(SimpleString key, byte value) {
      return putByteProperty(key.toString(), value);
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putBytesProperty(SimpleString key, byte[] value) {
      return putBytesProperty(key.toString(), value);
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putShortProperty(SimpleString key, short value) {
      return putShortProperty(key.toString(), value);
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putCharProperty(SimpleString key, char value) {
      return putCharProperty(key.toString(), value);
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putIntProperty(SimpleString key, int value) {
      return putIntProperty(key.toString(), value);
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putLongProperty(SimpleString key, long value) {
      return putLongProperty(key.toString(), value);
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putFloatProperty(SimpleString key, float value) {
      return putFloatProperty(key.toString(), value);
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putDoubleProperty(SimpleString key, double value) {
      return putDoubleProperty(key.toString(), value);
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putStringProperty(String key, String value) {
      getApplicationPropertiesMap(true).put(key, value);
      return this;
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putObjectProperty(String key, Object value) throws ActiveMQPropertyConversionException {
      if (value instanceof byte[] bytes) {
         // Prevent error from proton encoding, byte array must be wrapped in a Binary type.
         putBytesProperty(key, bytes);
      } else {
         getApplicationPropertiesMap(true).put(key, value);
      }

      return this;
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putObjectProperty(SimpleString key, Object value) throws ActiveMQPropertyConversionException {
      return putObjectProperty(key.toString(), value);
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putStringProperty(SimpleString key, SimpleString value) {
      return putStringProperty(key.toString(), value.toString());
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message putStringProperty(SimpleString key, String value) {
      return putStringProperty(key.toString(), value);
   }

   @Override
   public final SimpleString getLastValueProperty() {
      return getSimpleStringProperty(HDR_LAST_VALUE_NAME);
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message setLastValueProperty(SimpleString lastValueName) {
      return putStringProperty(HDR_LAST_VALUE_NAME, lastValueName);
   }

   @Override
   public String toString() {
      MessageDataScanningStatus scanningStatus = getDataScanningStatus();
      Map<String, Object> applicationProperties = scanningStatus == MessageDataScanningStatus.SCANNED ?
         getApplicationPropertiesMap(false) : Collections.emptyMap();

      return this.getClass().getSimpleName() + "( [durable=" + isDurable() +
         ", messageID=" + getMessageID() +
         ", address=" + getAddress() +
         ", size=" + getEncodeSize() +
         ", scanningStatus=" + scanningStatus +
         ", applicationProperties=" + applicationProperties +
         ", messageAnnotations=" + getMessageAnnotationsMap(false) +
         ", properties=" + properties +
         ", extraProperties = " + getExtraProperties() +
         "]";
   }

   @Override
   public final synchronized boolean acceptsConsumer(long consumer) {
      if (rejectedConsumers == null) {
         return true;
      } else {
         return !rejectedConsumers.contains(consumer);
      }
   }

   @Override
   public final synchronized void rejectConsumer(long consumer) {
      if (rejectedConsumers == null) {
         rejectedConsumers = new HashSet<>();
      }

      rejectedConsumers.add(consumer);
   }

   protected SimpleString.StringSimpleStringPool getPropertyKeysPool() {
      return coreMessageObjectPools == null ? null : coreMessageObjectPools.getPropertiesStringSimpleStringPools().getPropertyKeysPool();
   }

   protected SimpleString.StringSimpleStringPool getPropertyValuesPool() {
      return coreMessageObjectPools == null ? null : coreMessageObjectPools.getPropertiesStringSimpleStringPools().getPropertyValuesPool();
   }

   @Override
   public Object getOwner() {
      return owner;
   }

   @Override
   public void setOwner(Object object) {
      this.owner = object;
   }


   // *******************************************************************************************************************************
   // Composite Data implementation

   private static MessageOpenTypeFactory AMQP_FACTORY = new AmqpMessageOpenTypeFactory();

   static class AmqpMessageOpenTypeFactory extends MessageOpenTypeFactory<AMQPMessage> {
      @Override
      protected void init() throws OpenDataException {
         super.init();
         addItem(CompositeDataConstants.TEXT_BODY, CompositeDataConstants.TEXT_BODY, SimpleType.STRING);
      }

      @Override
      public Map<String, Object> getFields(AMQPMessage m, int valueSizeLimit, int delivery) throws OpenDataException {
         if (!m.isLargeMessage()) {
            m.ensureScanning();
         }

         Map<String, Object> rc = super.getFields(m, valueSizeLimit, delivery);

         Properties properties = m.getCurrentProperties();

         byte type = getType(m, properties);

         rc.put(CompositeDataConstants.TYPE, type);

         if (m.isLargeMessage())  {
            rc.put(CompositeDataConstants.TEXT_BODY, "... Large message ...");
         } else {
            Object amqpValue;
            if (m.getBody() instanceof AmqpValue && (amqpValue = ((AmqpValue) m.getBody()).getValue()) != null) {
               rc.put(CompositeDataConstants.TEXT_BODY, JsonUtil.truncateString(String.valueOf(amqpValue), valueSizeLimit));
            } else {
               rc.put(CompositeDataConstants.TEXT_BODY, JsonUtil.truncateString(String.valueOf(m.getBody()), valueSizeLimit));
            }
         }

         return rc;
      }

      @Override
      protected Map<String, Object> expandProperties(AMQPMessage m, int valueSizeLimit) {
         return m.toPropertyMap(true, valueSizeLimit);
      }

      private byte getType(AMQPMessage m, Properties properties) {
         if (m.isLargeMessage()) {
            return DEFAULT_TYPE;
         }
         byte type = BYTES_TYPE;

         if (m.getBody() == null) {
            return DEFAULT_TYPE;
         }

         final Symbol contentType = properties != null ? properties.getContentType() : null;

         if (m.getBody() instanceof Data && contentType != null) {
            if (AMQPMessageSupport.SERIALIZED_JAVA_OBJECT_CONTENT_TYPE.equals(contentType)) {
               type = OBJECT_TYPE;
            } else if (AMQPMessageSupport.OCTET_STREAM_CONTENT_TYPE_SYMBOL.equals(contentType)) {
               type = BYTES_TYPE;
            } else {
               Charset charset = getCharsetForTextualContent(contentType.toString());
               if (StandardCharsets.UTF_8.equals(charset)) {
                  type = TEXT_TYPE;
               }
            }
         } else if (m.getBody() instanceof AmqpValue) {
            Object value = ((AmqpValue) m.getBody()).getValue();

            if (value instanceof String) {
               type = TEXT_TYPE;
            } else if (value instanceof Binary) {
               if (AMQPMessageSupport.SERIALIZED_JAVA_OBJECT_CONTENT_TYPE.equals(contentType)) {
                  type = OBJECT_TYPE;
               } else {
                  type = BYTES_TYPE;
               }
            } else if (value instanceof List) {
               type = STREAM_TYPE;
            } else if (value instanceof Map) {
               type = MAP_TYPE;
            }
         } else if (m.getBody() instanceof AmqpSequence) {
            type = STREAM_TYPE;
         }

         return type;
      }
   }

   @Override
   public CompositeData toCompositeData(int fieldsLimit, int deliveryCount) throws OpenDataException {
      Map<String, Object> fields;
      fields = AMQP_FACTORY.getFields(this, fieldsLimit, deliveryCount);
      return new CompositeDataSupport(AMQP_FACTORY.getCompositeType(), fields);
   }

   // Composite Data implementation
   // *******************************************************************************************************************************

}