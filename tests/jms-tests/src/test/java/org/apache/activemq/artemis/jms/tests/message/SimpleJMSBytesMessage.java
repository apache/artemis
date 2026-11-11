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

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MessageEOFException;
import javax.jms.MessageFormatException;
import javax.jms.MessageNotReadableException;
import javax.jms.MessageNotWriteableException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Objects;

public class SimpleJMSBytesMessage extends SimpleJMSMessage implements BytesMessage {


   protected byte[] internalArray;

   protected boolean bodyWriteOnly = true;

   private transient ByteArrayOutputStream ostream;

   private transient DataOutputStream p;

   private transient ByteArrayInputStream istream;

   private transient DataInputStream m;


   public SimpleJMSBytesMessage() {
      ostream = new ByteArrayOutputStream();
      p = new DataOutputStream(ostream);
   }

   // BytesMessage implementation -----------------------------------

   @Override
   public boolean readBoolean() throws JMSException {
      checkRead();
      try {
         return m.readBoolean();
      } catch (EOFException e) {
         throw new MessageEOFException("");
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public byte readByte() throws JMSException {
      checkRead();
      try {
         return m.readByte();
      } catch (EOFException e) {
         throw new MessageEOFException("");
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public int readUnsignedByte() throws JMSException {
      checkRead();
      try {
         return m.readUnsignedByte();
      } catch (EOFException e) {
         throw new MessageEOFException("");
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public short readShort() throws JMSException {
      checkRead();
      try {
         return m.readShort();
      } catch (EOFException e) {
         throw new MessageEOFException("");
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public int readUnsignedShort() throws JMSException {
      checkRead();
      try {
         return m.readUnsignedShort();
      } catch (EOFException e) {
         throw new MessageEOFException("");
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public char readChar() throws JMSException {
      checkRead();
      try {
         return m.readChar();
      } catch (EOFException e) {
         throw new MessageEOFException("");
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public int readInt() throws JMSException {
      checkRead();
      try {
         return m.readInt();
      } catch (EOFException e) {
         throw new MessageEOFException("");
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public long readLong() throws JMSException {
      checkRead();
      try {
         return m.readLong();
      } catch (EOFException e) {
         throw new MessageEOFException("");
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public float readFloat() throws JMSException {
      checkRead();
      try {
         return m.readFloat();
      } catch (EOFException e) {
         throw new MessageEOFException("");
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public double readDouble() throws JMSException {
      checkRead();
      try {
         return m.readDouble();
      } catch (EOFException e) {
         throw new MessageEOFException("");
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public String readUTF() throws JMSException {
      checkRead();
      try {
         return m.readUTF();
      } catch (EOFException e) {
         throw new MessageEOFException("");
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public int readBytes(final byte[] value) throws JMSException {
      checkRead();
      try {
         return m.read(value);
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public int readBytes(final byte[] value, final int length) throws JMSException {
      checkRead();
      try {
         return m.read(value, 0, length);
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public void writeBoolean(final boolean value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("the message body is read-only");
      }
      try {
         p.writeBoolean(value);
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public void writeByte(final byte value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("the message body is read-only");
      }
      try {
         p.writeByte(value);
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public void writeShort(final short value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("the message body is read-only");
      }
      try {
         p.writeShort(value);
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public void writeChar(final char value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("the message body is read-only");
      }
      try {
         p.writeChar(value);
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public void writeInt(final int value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("the message body is read-only");
      }
      try {
         p.writeInt(value);
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public void writeLong(final long value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("the message body is read-only");
      }
      try {
         p.writeLong(value);
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public void writeFloat(final float value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("the message body is read-only");
      }
      try {
         p.writeFloat(value);
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public void writeDouble(final double value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("the message body is read-only");
      }
      try {
         p.writeDouble(value);
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public void writeUTF(final String value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("the message body is read-only");
      }
      try {
         p.writeUTF(value);
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public void writeBytes(final byte[] value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("the message body is read-only");
      }
      try {
         p.write(value, 0, value.length);
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public void writeBytes(final byte[] value, final int offset, final int length) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("the message body is read-only");
      }
      try {
         p.write(value, offset, length);
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public void writeObject(final Object value) throws JMSException {
      if (!bodyWriteOnly) {
         throw new MessageNotWriteableException("the message body is read-only");
      }
      try {
         Objects.requireNonNull(value, "Attempt to write a null value");
         if (value instanceof String string) {
            p.writeUTF(string);
         } else if (value instanceof Boolean booleanValue) {
            p.writeBoolean(booleanValue.booleanValue());
         } else if (value instanceof Byte byteValue) {
            p.writeByte(byteValue.byteValue());
         } else if (value instanceof Short shortValue) {
            p.writeShort(shortValue.shortValue());
         } else if (value instanceof Integer integer) {
            p.writeInt(integer.intValue());
         } else if (value instanceof Long longValue) {
            p.writeLong(longValue.longValue());
         } else if (value instanceof Float floatValue) {
            p.writeFloat(floatValue.floatValue());
         } else if (value instanceof Double doubleValue) {
            p.writeDouble(doubleValue.doubleValue());
         } else if (value instanceof byte[] bytes) {
            p.write(bytes, 0, bytes.length);
         } else {
            throw new MessageFormatException("Invalid object for properties");
         }
      } catch (IOException e) {
         throw new JMSException("IOException");
      }

   }

   @Override
   public void reset() throws JMSException {
      try {
         if (bodyWriteOnly) {
            p.flush();
            internalArray = ostream.toByteArray();
            ostream.close();
         }
         ostream = null;
         istream = null;
         m = null;
         p = null;
         bodyWriteOnly = false;
      } catch (IOException e) {
         throw new JMSException("IOException");
      }
   }

   @Override
   public long getBodyLength() throws JMSException {
      checkRead();
      return internalArray.length;
   }

   /**
    * Check the message is readable
    *
    * @throws javax.jms.JMSException when not readable
    */
   private void checkRead() throws JMSException {
      if (bodyWriteOnly) {
         throw new MessageNotReadableException("readByte while the buffer is writeonly");
      }

      // We have just received/reset() the message, and the client is trying to
      // read it
      if (istream == null || m == null) {
         istream = new ByteArrayInputStream(internalArray);
         m = new DataInputStream(istream);
      }
   }

}
