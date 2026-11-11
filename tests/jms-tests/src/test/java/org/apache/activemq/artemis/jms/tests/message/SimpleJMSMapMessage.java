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
package org.apache.activemq.artemis.jms.tests.message;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageFormatException;
import javax.jms.MessageNotWriteableException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SimpleJMSMapMessage extends SimpleJMSMessage implements MapMessage {


   protected Map<String, Object> content;

   protected boolean bodyReadOnly = false;


   public SimpleJMSMapMessage() {
      content = new HashMap<>();
   }

   // MapMessage implementation -------------------------------------

   @Override
   public void setBoolean(final String name, final boolean value) throws JMSException {
      checkName(name);
      if (bodyReadOnly) {
         throw new MessageNotWriteableException("Message is ReadOnly !");
      }

      content.put(name, value);

   }

   @Override
   public void setByte(final String name, final byte value) throws JMSException {
      checkName(name);
      if (bodyReadOnly) {
         throw new MessageNotWriteableException("Message is ReadOnly !");
      }

      content.put(name, value);

   }

   @Override
   public void setShort(final String name, final short value) throws JMSException {
      checkName(name);
      if (bodyReadOnly) {
         throw new MessageNotWriteableException("Message is ReadOnly !");
      }

      content.put(name, value);

   }

   @Override
   public void setChar(final String name, final char value) throws JMSException {
      checkName(name);
      if (bodyReadOnly) {
         throw new MessageNotWriteableException("Message is ReadOnly !");
      }

      content.put(name, value);

   }

   @Override
   public void setInt(final String name, final int value) throws JMSException {
      checkName(name);
      if (bodyReadOnly) {
         throw new MessageNotWriteableException("Message is ReadOnly !");
      }

      content.put(name, value);

   }

   @Override
   public void setLong(final String name, final long value) throws JMSException {
      checkName(name);
      if (bodyReadOnly) {
         throw new MessageNotWriteableException("Message is ReadOnly !");
      }

      content.put(name, value);

   }

   @Override
   public void setFloat(final String name, final float value) throws JMSException {
      checkName(name);
      if (bodyReadOnly) {
         throw new MessageNotWriteableException("Message is ReadOnly !");
      }

      content.put(name, value);

   }

   @Override
   public void setDouble(final String name, final double value) throws JMSException {
      checkName(name);
      if (bodyReadOnly) {
         throw new MessageNotWriteableException("Message is ReadOnly !");
      }

      content.put(name, value);

   }

   @Override
   public void setString(final String name, final String value) throws JMSException {
      checkName(name);
      if (bodyReadOnly) {
         throw new MessageNotWriteableException("Message is ReadOnly !");
      }

      content.put(name, value);

   }

   @Override
   public void setBytes(final String name, final byte[] value) throws JMSException {
      checkName(name);
      if (bodyReadOnly) {
         throw new MessageNotWriteableException("Message is ReadOnly !");
      }

      content.put(name, value.clone());

   }

   @Override
   public void setBytes(final String name, final byte[] value, final int offset, final int length) throws JMSException {
      checkName(name);
      if (bodyReadOnly) {
         throw new MessageNotWriteableException("Message is ReadOnly !");
      }

      if (offset + length > value.length) {
         throw new JMSException("Array is too small");
      }
      byte[] temp = new byte[length];
      System.arraycopy(value, offset, temp, 0, length);

      content.put(name, temp);

   }

   @Override
   public void setObject(final String name, final Object value) throws JMSException {
      checkName(name);
      if (bodyReadOnly) {
         throw new MessageNotWriteableException("Message is ReadOnly !");
      }

      if (value instanceof Boolean) {
         content.put(name, value);
      } else if (value instanceof Byte) {
         content.put(name, value);
      } else if (value instanceof Short) {
         content.put(name, value);
      } else if (value instanceof Character) {
         content.put(name, value);
      } else if (value instanceof Integer) {
         content.put(name, value);
      } else if (value instanceof Long) {
         content.put(name, value);
      } else if (value instanceof Float) {
         content.put(name, value);
      } else if (value instanceof Double) {
         content.put(name, value);
      } else if (value instanceof String) {
         content.put(name, value);
      } else if (value instanceof byte[] bytes) {
         content.put(name, bytes.clone());
      } else {
         throw new MessageFormatException("Invalid object type.");
      }

   }

   @Override
   public boolean getBoolean(final String name) throws JMSException {
      Object value;

      value = content.get(name);

      if (value == null) {
         return Boolean.valueOf(null).booleanValue();
      }

      if (value instanceof Boolean booleanValue) {
         return booleanValue.booleanValue();
      } else if (value instanceof String string) {
         return Boolean.valueOf(string).booleanValue();
      } else {
         throw new MessageFormatException("Invalid conversion");
      }
   }

   @Override
   public byte getByte(final String name) throws JMSException {
      Object value;

      value = content.get(name);

      if (value == null) {
         return Byte.parseByte(null);
      }

      if (value instanceof Byte byteValue) {
         return byteValue.byteValue();
      } else if (value instanceof String string) {
         return Byte.parseByte(string);
      } else {
         throw new MessageFormatException("Invalid conversion");
      }
   }

   @Override
   public short getShort(final String name) throws JMSException {
      Object value;

      value = content.get(name);

      if (value == null) {
         return Short.parseShort(null);
      }

      if (value instanceof Byte byteValue) {
         return byteValue.shortValue();
      } else if (value instanceof Short shortValue) {
         return shortValue.shortValue();
      } else if (value instanceof String string) {
         return Short.parseShort(string);
      } else {
         throw new MessageFormatException("Invalid conversion");
      }
   }

   @Override
   public char getChar(final String name) throws JMSException {
      Object value = Objects.requireNonNull(content.get(name), "Invalid conversion");
      if (value instanceof Character character) {
         return character.charValue();
      } else {
         throw new MessageFormatException("Invalid conversion");
      }
   }

   @Override
   public int getInt(final String name) throws JMSException {
      Object value;

      value = content.get(name);

      if (value == null) {
         return Integer.parseInt(null);
      }

      if (value instanceof Byte byteValue) {
         return byteValue.intValue();
      } else if (value instanceof Short shortValue) {
         return shortValue.intValue();
      } else if (value instanceof Integer integer) {
         return integer.intValue();
      } else if (value instanceof String string) {
         return Integer.parseInt(string);
      } else {
         throw new MessageFormatException("Invalid conversion");
      }
   }

   @Override
   public long getLong(final String name) throws JMSException {
      Object value;

      value = content.get(name);

      if (value == null) {
         return Long.parseLong(null);
      }

      if (value instanceof Byte byteValue) {
         return byteValue.longValue();
      } else if (value instanceof Short shortValue) {
         return shortValue.longValue();
      } else if (value instanceof Integer integer) {
         return integer.longValue();
      } else if (value instanceof Long longValue) {
         return longValue.longValue();
      } else if (value instanceof String string) {
         return Long.parseLong(string);
      } else {
         throw new MessageFormatException("Invalid conversion");
      }
   }

   @Override
   public float getFloat(final String name) throws JMSException {
      Object value;

      value = content.get(name);

      if (value == null) {
         return Float.parseFloat(null);
      }

      if (value instanceof Float floatValue) {
         return floatValue.floatValue();
      } else if (value instanceof String string) {
         return Float.parseFloat(string);
      } else {
         throw new MessageFormatException("Invalid conversion");
      }
   }

   @Override
   public double getDouble(final String name) throws JMSException {
      Object value;

      value = content.get(name);

      if (value == null) {
         return Double.parseDouble(null);
      }

      if (value instanceof Float floatValue) {
         return floatValue.doubleValue();
      } else if (value instanceof Double doubleValue) {
         return doubleValue.doubleValue();
      } else if (value instanceof String string) {
         return Double.parseDouble(string);
      } else {
         throw new MessageFormatException("Invalid conversion");
      }
   }

   @Override
   public String getString(final String name) throws JMSException {
      Object value;

      value = content.get(name);

      if (value == null) {
         return null;
      }

      if (value instanceof Boolean booleanValue) {
         return booleanValue.toString();
      } else if (value instanceof Byte byteValue) {
         return byteValue.toString();
      } else if (value instanceof Short shortValue) {
         return shortValue.toString();
      } else if (value instanceof Character character) {
         return character.toString();
      } else if (value instanceof Integer integer) {
         return integer.toString();
      } else if (value instanceof Long longValue) {
         return longValue.toString();
      } else if (value instanceof Float floatValue) {
         return floatValue.toString();
      } else if (value instanceof Double doubleValue) {
         return doubleValue.toString();
      } else if (value instanceof String string) {
         return string;
      } else {
         throw new MessageFormatException("Invalid conversion");
      }
   }

   @Override
   public byte[] getBytes(final String name) throws JMSException {
      Object value;

      value = content.get(name);

      if (value == null) {
         return null;
      }
      if (value instanceof byte[] bytes) {
         return bytes;
      } else {
         throw new MessageFormatException("Invalid conversion");
      }
   }

   @Override
   public Object getObject(final String name) throws JMSException {

      return content.get(name);

   }

   @Override
   public Enumeration getMapNames() throws JMSException {

      return Collections.enumeration(new HashMap<>(content).keySet());

   }

   @Override
   public boolean itemExists(final String name) throws JMSException {

      return content.containsKey(name);

   }

   /**
    * Check the name
    *
    * @param name the name
    */
   private void checkName(final String name) {
      if (name == null) {
         throw new IllegalArgumentException("Name must not be null.");
      }

      if (name.isEmpty()) {
         throw new IllegalArgumentException("Name must not be an empty String.");
      }
   }


}
