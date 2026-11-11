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
package org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQPropertyConversionException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedByte;
import org.apache.qpid.proton.amqp.UnsignedInteger;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.proton.amqp.UnsignedShort;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.amqp.messaging.Section;

import static org.apache.activemq.artemis.reader.MapMessageUtil.readBodyMap;
import static org.apache.activemq.artemis.reader.MapMessageUtil.writeBodyMap;

public final class CoreMapMessageWrapper extends CoreMessageWrapper {

   public static final byte TYPE = Message.MAP_TYPE;

   private final TypedProperties map = new TypedProperties();

   /**
    * This constructor is used to construct messages prior to sending
    */
   public CoreMapMessageWrapper(ICoreMessage message) {
      super(message);
   }

   private static Map<String, Object> getMapFromMessageBody(CoreMapMessageWrapper message) {
      final HashMap<String, Object> map = new LinkedHashMap<>();

      @SuppressWarnings("unchecked")
      final Enumeration<String> names = message.getMapNames();
      while (names.hasMoreElements()) {
         String key = names.nextElement();
         Object value = message.getObject(key);
         if (value instanceof byte[] bytes) {
            value = new Binary(bytes);
         }
         map.put(key, value);
      }

      return map;
   }

   @Override
   public Section createAMQPSection(Map<Symbol, Object> maMap, Properties properties) {
      maMap.put(AMQPMessageSupport.JMS_MSG_TYPE, AMQPMessageSupport.JMS_MAP_MESSAGE);
      return new AmqpValue(getMapFromMessageBody(this));
   }

   public void setBoolean(final String name, final boolean value) {
      map.putBooleanProperty(SimpleString.of(name), value);
   }

   public void setByte(final String name, final byte value) {
      map.putByteProperty(SimpleString.of(name), value);
   }

   public void setShort(final String name, final short value) {
      map.putShortProperty(SimpleString.of(name), value);
   }

   public void setChar(final String name, final char value) {
      map.putCharProperty(SimpleString.of(name), value);
   }

   public void setInt(final String name, final int value) {
      map.putIntProperty(SimpleString.of(name), value);
   }

   public void setLong(final String name, final long value) {
      map.putLongProperty(SimpleString.of(name), value);
   }

   public void setFloat(final String name, final float value) {
      map.putFloatProperty(SimpleString.of(name), value);
   }

   public void setDouble(final String name, final double value) {
      map.putDoubleProperty(SimpleString.of(name), value);
   }

   public void setString(final String name, final String value) {
      map.putSimpleStringProperty(SimpleString.of(name), SimpleString.of(value));
   }

   public void setBytes(final String name, final byte[] value) {
      map.putBytesProperty(SimpleString.of(name), value);
   }

   public void setBytes(final String name, final byte[] value, final int offset, final int length) {
      byte[] newBytes = new byte[length];
      System.arraycopy(value, offset, newBytes, 0, length);
      map.putBytesProperty(SimpleString.of(name), newBytes);
   }

   public void setObject(final String name, final Object value) throws ActiveMQPropertyConversionException {
      // primitives and String
      Object val = value;
      if (value instanceof UnsignedInteger unsignedInteger) {
         val = unsignedInteger.intValue();
      } else if (value instanceof UnsignedShort unsignedShort) {
         val = unsignedShort.shortValue();
      } else if (value instanceof UnsignedByte unsignedByte) {
         val = unsignedByte.byteValue();
      } else if (value instanceof UnsignedLong unsignedLong) {
         val = unsignedLong.longValue();
      }
      TypedProperties.setObjectProperty(SimpleString.of(name), val, map);
   }

   public boolean getBoolean(final String name) throws ActiveMQPropertyConversionException {
      return map.getBooleanProperty(SimpleString.of(name));
   }

   public byte getByte(final String name) throws ActiveMQPropertyConversionException {
      return map.getByteProperty(SimpleString.of(name));
   }

   public short getShort(final String name) throws ActiveMQPropertyConversionException {
      return map.getShortProperty(SimpleString.of(name));
   }

   public char getChar(final String name) throws ActiveMQPropertyConversionException {
      return map.getCharProperty(SimpleString.of(name));
   }

   public int getInt(final String name) throws ActiveMQPropertyConversionException {
      return map.getIntProperty(SimpleString.of(name));
   }

   public long getLong(final String name) throws ActiveMQPropertyConversionException {
      return map.getLongProperty(SimpleString.of(name));
   }

   public float getFloat(final String name) throws ActiveMQPropertyConversionException {
      return map.getFloatProperty(SimpleString.of(name));
   }

   public double getDouble(final String name) throws ActiveMQPropertyConversionException {
      return map.getDoubleProperty(SimpleString.of(name));
   }

   public String getString(final String name) throws ActiveMQPropertyConversionException {
      SimpleString str = map.getSimpleStringProperty(SimpleString.of(name));
      if (str == null) {
         return null;
      } else {
         return str.toString();
      }
   }

   public byte[] getBytes(final String name) throws ActiveMQPropertyConversionException {
      return map.getBytesProperty(SimpleString.of(name));
   }

   public Object getObject(final String name) {
      Object val = map.getProperty(SimpleString.of(name));

      if (val instanceof SimpleString simpleString) {
         val = simpleString.toString();
      }

      return val;
   }

   public Enumeration getMapNames() {
      return Collections.enumeration(map.getMapNames());
   }

   public boolean itemExists(final String name) {
      return map.containsProperty(SimpleString.of(name));
   }

   @Override
   public void clearBody() {
      super.clearBody();

      map.clear();
   }

   @Override
   public void encode() {
      super.encode();
      writeBodyMap(getWriteBodyBuffer(), map);
   }

   @Override
   public void decode() {
      super.decode();
      readBodyMap(getReadBodyBuffer(), map);
   }

}
