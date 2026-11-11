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
import javax.jms.MessageEOFException;
import javax.jms.MessageFormatException;
import javax.jms.MessageNotReadableException;
import javax.jms.MessageNotWriteableException;
import javax.jms.StreamMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SimpleJMSStreamMessage extends SimpleJMSMessage implements StreamMessage {


   protected List<Object> content;

   protected int position;

   protected int offset;

   protected int size;

   protected boolean bodyWriteOnly = true;

   // protected transient boolean deserialised;


   public SimpleJMSStreamMessage() {
      content = new ArrayList<>();
      position = 0;
      size = 0;
      offset = 0;
   }


   // StreamMessage implementation ----------------------------------

   @Override
   public boolean readBoolean() throws JMSException {
      if (bodyWriteOnly) {
         throw new MessageNotReadableException("The message body is writeonly");
      }

      try {
         Object value = content.get(position);
         offset = 0;

         Objects.requireNonNull(value, "Value is null");
         if (value instanceof Boolean booleanValue) {
            position++;
            return booleanValue.booleanValue();
         } else if (value instanceof String string) {
            boolean result = Boolean.valueOf(string).booleanValue();
            position++;
            return result;
         } else {
            throw new MessageFormatException("Invalid conversion");
         }
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }

   }

   @Override
   public byte readByte() throws JMSException {
      if (bodyWriteOnly) {
         throw new MessageNotReadableException("The message body is writeonly");
      }

      try {
         Object value = content.get(position);
         offset = 0;
         Objects.requireNonNull(value, "Value is null");
         if (value instanceof Byte byteValue) {
            position++;
            return byteValue.byteValue();
         } else if (value instanceof String string) {
            byte result = Byte.parseByte(string);
            position++;
            return result;
         } else {
            throw new MessageFormatException("Invalid conversion");
         }
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public short readShort() throws JMSException {
      if (bodyWriteOnly) {
         throw new MessageNotReadableException("The message body is writeonly");
      }
      try {
         Object value = content.get(position);
         offset = 0;

         Objects.requireNonNull(value, "Value is null");
         if (value instanceof Byte byteValue) {
            position++;
            return byteValue.shortValue();
         } else if (value instanceof Short shortValue) {
            position++;
            return shortValue.shortValue();
         } else if (value instanceof String string) {
            short result = Short.parseShort(string);
            position++;
            return result;
         } else {
            throw new MessageFormatException("Invalid conversion");
         }
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public char readChar() throws JMSException {
      if (bodyWriteOnly) {
         throw new MessageNotReadableException("The message body is writeonly");
      }
      try {
         Object value = content.get(position);
         offset = 0;

         Objects.requireNonNull(value, "Value is null");
         if (value instanceof Character character) {
            position++;
            return character.charValue();
         } else {
            throw new MessageFormatException("Invalid conversion");
         }
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public int readInt() throws JMSException {
      if (bodyWriteOnly) {
         throw new MessageNotReadableException("The message body is writeonly");
      }
      try {
         Object value = content.get(position);
         offset = 0;

         Objects.requireNonNull(value, "Value is null");
         if (value instanceof Byte byteValue) {
            position++;
            return byteValue.intValue();
         } else if (value instanceof Short shortValue) {
            position++;
            return shortValue.intValue();
         } else if (value instanceof Integer integer) {
            position++;
            return integer.intValue();
         } else if (value instanceof String string) {
            int result = Integer.parseInt(string);
            position++;
            return result;
         } else {
            throw new MessageFormatException("Invalid conversion");
         }
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public long readLong() throws JMSException {
      if (bodyWriteOnly) {
         throw new MessageNotReadableException("The message body is writeonly");
      }
      try {
         Object value = content.get(position);
         offset = 0;

         Objects.requireNonNull(value, "Value is null");
         if (value instanceof Byte byteValue) {
            position++;
            return byteValue.longValue();
         } else if (value instanceof Short shortValue) {
            position++;
            return shortValue.longValue();
         } else if (value instanceof Integer integer) {
            position++;
            return integer.longValue();
         } else if (value instanceof Long longValue) {
            position++;
            return longValue.longValue();
         } else if (value instanceof String string) {
            long result = Long.parseLong(string);
            position++;
            return result;
         } else {
            throw new MessageFormatException("Invalid conversion");
         }
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public float readFloat() throws JMSException {
      if (bodyWriteOnly) {
         throw new MessageNotReadableException("The message body is writeonly");
      }
      try {
         Object value = content.get(position);
         offset = 0;

         Objects.requireNonNull(value, "Value is null");
         if (value instanceof Float floatValue) {
            position++;
            return floatValue.floatValue();
         } else if (value instanceof String string) {
            float result = Float.parseFloat(string);
            position++;
            return result;
         } else {
            throw new MessageFormatException("Invalid conversion");
         }
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public double readDouble() throws JMSException {
      if (bodyWriteOnly) {
         throw new MessageNotReadableException("The message body is writeonly");
      }
      try {
         Object value = content.get(position);
         offset = 0;

         Objects.requireNonNull(value, "Value is null");
         if (value instanceof Float floatValue) {
            position++;
            return floatValue.doubleValue();
         } else if (value instanceof Double doubleValue) {
            position++;
            return doubleValue.doubleValue();
         } else if (value instanceof String string) {
            double result = Double.parseDouble(string);
            position++;
            return result;
         } else {
            throw new MessageFormatException("Invalid conversion");
         }
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public String readString() throws JMSException {
      if (bodyWriteOnly) {
         throw new MessageNotReadableException("The message body is writeonly");
      }
      try {
         Object value = content.get(position);
         offset = 0;

         if (value == null) {
            position++;
            return null;
         } else if (value instanceof Boolean booleanValue) {
            position++;
            return booleanValue.toString();
         } else if (value instanceof Byte byteValue) {
            position++;
            return byteValue.toString();
         } else if (value instanceof Short shortValue) {
            position++;
            return shortValue.toString();
         } else if (value instanceof Character character) {
            position++;
            return character.toString();
         } else if (value instanceof Integer integer) {
            position++;
            return integer.toString();
         } else if (value instanceof Long longValue) {
            position++;
            return longValue.toString();
         } else if (value instanceof Float floatValue) {
            position++;
            return floatValue.toString();
         } else if (value instanceof Double doubleValue) {
            position++;
            return doubleValue.toString();
         } else if (value instanceof String string) {
            position++;
            return string;
         } else {
            throw new MessageFormatException("Invalid conversion");
         }
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public int readBytes(final byte[] value) throws JMSException {
      if (bodyWriteOnly) {
         throw new MessageNotReadableException("The message body is writeonly");
      }
      try {
         Object myObj = content.get(position);
         Objects.requireNonNull(myObj, "Value is null");
         if (!(myObj instanceof byte[])) {
            throw new MessageFormatException("Invalid conversion");
         }
         byte[] obj = (byte[]) myObj;

         if (obj.length == 0) {
            position++;
            offset = 0;
            return 0;
         }

         if (offset >= obj.length) {
            position++;
            offset = 0;
            return -1;
         }

         if (obj.length - offset < value.length) {
            System.arraycopy(obj, offset, value, 0, obj.length);

            position++;
            offset = 0;

            return obj.length - offset;
         } else {
            System.arraycopy(obj, offset, value, 0, value.length);
            offset += value.length;

            return value.length;
         }

      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public Object readObject() throws JMSException {
      if (bodyWriteOnly) {
         throw new MessageNotReadableException("The message body is writeonly");
      }
      try {
         Object value = content.get(position);
         position++;
         offset = 0;

         return value;
      } catch (IndexOutOfBoundsException e) {
         throw new MessageEOFException("");
      }
   }

   @Override
   public void writeBoolean(final boolean value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("The message body is readonly");
      }
      content.add(value);
   }

   @Override
   public void writeByte(final byte value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("The message body is readonly");
      }
      content.add(value);
   }

   @Override
   public void writeShort(final short value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("The message body is readonly");
      }
      content.add(value);
   }

   @Override
   public void writeChar(final char value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("The message body is readonly");
      }
      content.add(value);
   }

   @Override
   public void writeInt(final int value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("The message body is readonly");
      }
      content.add(value);
   }

   @Override
   public void writeLong(final long value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("The message body is readonly");
      }
      content.add(value);
   }

   @Override
   public void writeFloat(final float value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("The message body is readonly");
      }
      content.add(value);
   }

   @Override
   public void writeDouble(final double value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("The message body is readonly");
      }
      content.add(value);
   }

   @Override
   public void writeString(final String value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("The message body is readonly");
      }
      if (value == null) {
         content.add(null);
      } else {
         content.add(value);
      }
   }

   @Override
   public void writeBytes(final byte[] value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("The message body is readonly");
      }
      content.add(value.clone());
   }

   @Override
   public void writeBytes(final byte[] value, final int offset, final int length) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("The message body is readonly");
      }

      if (offset + length > value.length) {
         throw new JMSException("Array is too small");
      }
      byte[] temp = new byte[length];
      System.arraycopy(value, offset, temp, 0, length);

      content.add(temp);
   }

   @Override
   public void writeObject(final Object value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("The message body is readonly");
      }
      if (value == null) {
         content.add(null);
      } else if (value instanceof Boolean) {
         content.add(value);
      } else if (value instanceof Byte) {
         content.add(value);
      } else if (value instanceof Short) {
         content.add(value);
      } else if (value instanceof Character) {
         content.add(value);
      } else if (value instanceof Integer) {
         content.add(value);
      } else if (value instanceof Long) {
         content.add(value);
      } else if (value instanceof Float) {
         content.add(value);
      } else if (value instanceof Double) {
         content.add(value);
      } else if (value instanceof String) {
         content.add(value);
      } else if (value instanceof byte[] bytes) {
         content.add(bytes.clone());
      } else {
         throw new MessageFormatException("Invalid object type");
      }
   }

   @Override
   public void reset() throws JMSException {
      bodyWriteOnly = false;
      position = 0;
      size = content.size();
      offset = 0;
   }


}
