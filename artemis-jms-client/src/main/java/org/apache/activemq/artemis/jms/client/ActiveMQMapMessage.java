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

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageFormatException;
import java.util.Collections;
import java.util.Enumeration;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQPropertyConversionException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.utils.collections.TypedProperties;

import static org.apache.activemq.artemis.reader.MapMessageUtil.readBodyMap;
import static org.apache.activemq.artemis.reader.MapMessageUtil.writeBodyMap;

/**
 * Apache Artemis implementation of a JMS MapMessage.
 */
public class ActiveMQMapMessage extends ActiveMQMessage implements MapMessage {

   public static final byte TYPE = Message.MAP_TYPE;


   private final TypedProperties map = new TypedProperties();

   private boolean invalid;


   /**
    * This constructor is used to construct messages prior to sending
    */
   protected ActiveMQMapMessage(final ClientSession session) {
      super(ActiveMQMapMessage.TYPE, session);

      invalid = true;
   }

   /**
    * This constructor is used during reading
    */
   protected ActiveMQMapMessage(final ClientMessage message, final ClientSession session) {
      super(message, session);

      invalid = false;
   }

   public ActiveMQMapMessage() {
      invalid = false;
   }

   /**
    * Constructor for a foreign MapMessage
    */
   public ActiveMQMapMessage(final MapMessage foreign, final ClientSession session) throws JMSException {
      super(foreign, ActiveMQMapMessage.TYPE, session);
      Enumeration<?> names = foreign.getMapNames();
      while (names.hasMoreElements()) {
         String name = (String) names.nextElement();
         Object obj = foreign.getObject(name);
         setObject(name, obj);
      }

      // invalidate the message anyway. If the foreign is empty, we would otherwise not encode the message at all
      invalid = true;
   }


   @Override
   public byte getType() {
      return ActiveMQMapMessage.TYPE;
   }

   // MapMessage implementation -------------------------------------

   @Override
   public void setBoolean(final String name, final boolean value) throws JMSException {
      checkName(name);
      map.putBooleanProperty(SimpleString.of(name), value);
      invalid = true;
   }

   @Override
   public void setByte(final String name, final byte value) throws JMSException {
      checkName(name);
      map.putByteProperty(SimpleString.of(name), value);
      invalid = true;
   }

   @Override
   public void setShort(final String name, final short value) throws JMSException {
      checkName(name);
      map.putShortProperty(SimpleString.of(name), value);
      invalid = true;
   }

   @Override
   public void setChar(final String name, final char value) throws JMSException {
      checkName(name);
      map.putCharProperty(SimpleString.of(name), value);
      invalid = true;
   }

   @Override
   public void setInt(final String name, final int value) throws JMSException {
      checkName(name);
      map.putIntProperty(SimpleString.of(name), value);
      invalid = true;
   }

   @Override
   public void setLong(final String name, final long value) throws JMSException {
      checkName(name);
      map.putLongProperty(SimpleString.of(name), value);
      invalid = true;
   }

   @Override
   public void setFloat(final String name, final float value) throws JMSException {
      checkName(name);
      map.putFloatProperty(SimpleString.of(name), value);
      invalid = true;
   }

   @Override
   public void setDouble(final String name, final double value) throws JMSException {
      checkName(name);
      map.putDoubleProperty(SimpleString.of(name), value);
      invalid = true;
   }

   @Override
   public void setString(final String name, final String value) throws JMSException {
      checkName(name);
      map.putSimpleStringProperty(SimpleString.of(name), value == null ? null : SimpleString.of(value));
      invalid = true;
   }

   @Override
   public void setBytes(final String name, final byte[] value) throws JMSException {
      checkName(name);
      map.putBytesProperty(SimpleString.of(name), value);
      invalid = true;
   }

   @Override
   public void setBytes(final String name, final byte[] value, final int offset, final int length) throws JMSException {
      checkName(name);
      if (offset + length > value.length) {
         throw new JMSException("Invalid offset/length");
      }
      byte[] newBytes = new byte[length];
      System.arraycopy(value, offset, newBytes, 0, length);
      map.putBytesProperty(SimpleString.of(name), newBytes);
      invalid = true;
   }

   @Override
   public void setObject(final String name, final Object value) throws JMSException {
      checkName(name);
      try {
         TypedProperties.setObjectProperty(SimpleString.of(name), value, map);
      } catch (ActiveMQPropertyConversionException e) {
         throw new MessageFormatException(e.getMessage());
      }
      invalid = true;
   }

   @Override
   public boolean getBoolean(final String name) throws JMSException {
      try {
         return map.getBooleanProperty(SimpleString.of(name));
      } catch (ActiveMQPropertyConversionException e) {
         throw new MessageFormatException(e.getMessage());
      }
   }

   @Override
   public byte getByte(final String name) throws JMSException {
      try {
         return map.getByteProperty(SimpleString.of(name));
      } catch (ActiveMQPropertyConversionException e) {
         throw new MessageFormatException(e.getMessage());
      }
   }

   @Override
   public short getShort(final String name) throws JMSException {
      try {
         return map.getShortProperty(SimpleString.of(name));
      } catch (ActiveMQPropertyConversionException e) {
         throw new MessageFormatException(e.getMessage());
      }
   }

   @Override
   public char getChar(final String name) throws JMSException {
      try {
         return map.getCharProperty(SimpleString.of(name));
      } catch (ActiveMQPropertyConversionException e) {
         throw new MessageFormatException(e.getMessage());
      }
   }

   @Override
   public int getInt(final String name) throws JMSException {
      try {
         return map.getIntProperty(SimpleString.of(name));
      } catch (ActiveMQPropertyConversionException e) {
         throw new MessageFormatException(e.getMessage());
      }
   }

   @Override
   public long getLong(final String name) throws JMSException {
      try {
         return map.getLongProperty(SimpleString.of(name));
      } catch (ActiveMQPropertyConversionException e) {
         throw new MessageFormatException(e.getMessage());
      }
   }

   @Override
   public float getFloat(final String name) throws JMSException {
      try {
         return map.getFloatProperty(SimpleString.of(name));
      } catch (ActiveMQPropertyConversionException e) {
         throw new MessageFormatException(e.getMessage());
      }
   }

   @Override
   public double getDouble(final String name) throws JMSException {
      try {
         return map.getDoubleProperty(SimpleString.of(name));
      } catch (ActiveMQPropertyConversionException e) {
         throw new MessageFormatException(e.getMessage());
      }
   }

   @Override
   public String getString(final String name) throws JMSException {
      try {
         SimpleString str = map.getSimpleStringProperty(SimpleString.of(name));
         if (str == null) {
            return null;
         } else {
            return str.toString();
         }
      } catch (ActiveMQPropertyConversionException e) {
         throw new MessageFormatException(e.getMessage());
      }
   }

   @Override
   public byte[] getBytes(final String name) throws JMSException {
      try {
         return map.getBytesProperty(SimpleString.of(name));
      } catch (ActiveMQPropertyConversionException e) {
         throw new MessageFormatException(e.getMessage());
      }
   }

   @Override
   public Object getObject(final String name) throws JMSException {
      Object val = map.getProperty(SimpleString.of(name));

      if (val instanceof SimpleString string) {
         val = string.toString();
      }

      return val;
   }

   @Override
   public Enumeration getMapNames() throws JMSException {
      return Collections.enumeration(map.getMapNames());
   }

   @Override
   public boolean itemExists(final String name) throws JMSException {
      return map.containsProperty(SimpleString.of(name));
   }

   @Override
   public void clearBody() throws JMSException {
      super.clearBody();

      map.clear();

      invalid = true;
   }

   @Override
   public void doBeforeSend() throws Exception {
      if (invalid) {
         writeBodyMap(message.getBodyBuffer(), map);
         invalid = false;
      }

      super.doBeforeSend();
   }

   @Override
   public void doBeforeReceive() throws ActiveMQException {
      super.doBeforeReceive();

      readBodyMap(message.getBodyBuffer(), map);
   }

   private void checkName(final String name) throws JMSException {
      checkWrite();

      if (name == null) {
         throw ActiveMQJMSClientBundle.BUNDLE.nameCannotBeNull();
      }
      if (name.isEmpty()) {
         throw ActiveMQJMSClientBundle.BUNDLE.nameCannotBeEmpty();
      }
   }

   @Override
   protected boolean hasNoBody() {
      return map.isEmpty();
   }

   @Override
   @SuppressWarnings("unchecked")
   public boolean isBodyAssignableTo(Class c) {
      if (hasNoBody()) {
         return true;
      }
      return c.isAssignableFrom(java.util.Map.class);
   }

   @SuppressWarnings("unchecked")
   @Override
   protected <T> T getBodyInternal(Class<T> c) {
      return (T) map.getMap();
   }
}
