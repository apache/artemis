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
package org.apache.activemq.artemis.utils.collections;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.api.core.ActiveMQPropertyConversionException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.logs.ActiveMQUtilBundle;
import org.apache.activemq.artemis.utils.AbstractByteBufPool;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.DataConstants;

import static org.apache.activemq.artemis.utils.ByteUtil.ensureExactWritable;
import static org.apache.activemq.artemis.utils.DataConstants.BOOLEAN;
import static org.apache.activemq.artemis.utils.DataConstants.BYTE;
import static org.apache.activemq.artemis.utils.DataConstants.BYTES;
import static org.apache.activemq.artemis.utils.DataConstants.CHAR;
import static org.apache.activemq.artemis.utils.DataConstants.DOUBLE;
import static org.apache.activemq.artemis.utils.DataConstants.FLOAT;
import static org.apache.activemq.artemis.utils.DataConstants.INT;
import static org.apache.activemq.artemis.utils.DataConstants.LONG;
import static org.apache.activemq.artemis.utils.DataConstants.NULL;
import static org.apache.activemq.artemis.utils.DataConstants.SHORT;
import static org.apache.activemq.artemis.utils.DataConstants.STRING;

/**
 * Property Value Conversion.
 * <p>
 * This implementation follows section 3.5.4 of the <i>Java Message Service</i> specification (Version 1.1 April 12,
 * 2002).
 */
public class TypedProperties {

   private Map<SimpleString, PropertyValue> properties;

   private int size;

   private final Predicate<SimpleString> internalPropertyPredicate;
   private final ReadWriteLock lock = new ReentrantReadWriteLock();
   private boolean internalProperties;
   private final Predicate<SimpleString> amqpPropertyPredicate;
   private boolean amqpProperties;

   public TypedProperties() {
      this.internalPropertyPredicate = null;
      this.amqpPropertyPredicate = null;
   }

   public TypedProperties(Predicate<SimpleString> internalPropertyPredicate) {
      this(internalPropertyPredicate, null);
   }

   public TypedProperties(Predicate<SimpleString> internalPropertyPredicate, Predicate<SimpleString> amqpPropertyPredicate) {
      this.internalPropertyPredicate = internalPropertyPredicate;
      this.amqpPropertyPredicate = amqpPropertyPredicate;
   }

   /**
    * Return the number of properties
    */
   public int size() {
      lock.readLock().lock();
      try {
         return properties == null ? 0 : properties.size();
      } finally {
         lock.readLock().unlock();
      }
   }

   public int getMemoryOffset() {
      // The estimate is basically the encode size + 2 object references for each entry in the map
      // Note we don't include the attributes or anything else since they already included in the memory estimate
      // of the ServerMessage
      lock.readLock().lock();
      try {
         return properties == null ? 0 : size + 2 * DataConstants.SIZE_INT * properties.size();
      } finally {
         lock.readLock().unlock();
      }
   }

   public TypedProperties(final TypedProperties other) {
      other.lock.readLock().lock();
      try {
         properties = other.properties == null ? null : new HashMap<>(other.properties);
         size = other.size;
         internalPropertyPredicate = other.internalPropertyPredicate;
         internalProperties = other.internalProperties;
         amqpPropertyPredicate = other.amqpPropertyPredicate;
         amqpProperties = other.amqpProperties;
      } finally {
         other.lock.readLock().unlock();
      }
   }

   public void putBooleanProperty(final SimpleString key, final boolean value) {
      doPutValue(key, BooleanValue.of(value));
   }

   public void putByteProperty(final SimpleString key, final byte value) {
      doPutValue(key, ByteValue.valueOf(value));
   }

   public void putBytesProperty(final SimpleString key, final byte[] value) {
      doPutValue(key, value == null ? NullValue.INSTANCE : new BytesValue(value));
   }

   public void putShortProperty(final SimpleString key, final short value) {
      doPutValue(key, new ShortValue(value));
   }

   public void putIntProperty(final SimpleString key, final int value) {
      doPutValue(key, new IntValue(value));
   }

   public void putLongProperty(final SimpleString key, final long value) {
      doPutValue(key, new LongValue(value));
   }

   public void putFloatProperty(final SimpleString key, final float value) {
      doPutValue(key, new FloatValue(value));
   }

   public void putDoubleProperty(final SimpleString key, final double value) {
      doPutValue(key, new DoubleValue(value));
   }

   public void putSimpleStringProperty(final SimpleString key, final SimpleString value) {
      doPutValue(key, value == null ? NullValue.INSTANCE : new StringValue(value));
   }

   public void putNullValue(final SimpleString key) {
      doPutValue(key, NullValue.INSTANCE);
   }

   public void putCharProperty(final SimpleString key, final char value) {
      doPutValue(key, new CharValue(value));
   }

   public void putTypedProperties(final TypedProperties otherProps) {
      if (otherProps == null || otherProps == this || otherProps.properties == null) {
         return;
      }

      otherProps.forEachInternal(this::doPutValue);
   }

   public TypedProperties putProperty(final SimpleString key, final Object value) {
      setObjectProperty(key, value, this);
      return this;
   }

   public Object getProperty(final SimpleString key) {
      return doGetProperty(key);
   }

   public Boolean getBooleanProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      Object value = doGetProperty(key);
      if (value == null) {
         return Boolean.valueOf(null);
      } else if (value instanceof Boolean booleanValue) {
         return booleanValue;
      } else if (value instanceof SimpleString simpleString) {
         return Boolean.valueOf(simpleString.toString());
      }
      throw new ActiveMQPropertyConversionException("Invalid conversion: " + key);
   }

   public Byte getByteProperty(final SimpleString key,
                               final Supplier<Byte> defaultValue) throws ActiveMQPropertyConversionException {
      Objects.requireNonNull(defaultValue);
      Object value = doGetProperty(key);
      if (value == null) {
         return defaultValue.get();
      } else if (value instanceof Byte byteValue) {
         return byteValue;
      } else if (value instanceof SimpleString simpleString) {
         return Byte.parseByte(simpleString.toString());
      }
      throw new ActiveMQPropertyConversionException("Invalid conversion: " + key);
   }

   public Byte getByteProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      return getByteProperty(key, () -> Byte.valueOf(null));
   }

   public Character getCharProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      Object value = Objects.requireNonNull(doGetProperty(key), "Invalid conversion: " + key);

      if (value instanceof Character character) {
         return character;
      }
      throw new ActiveMQPropertyConversionException("Invalid conversion: " + key);
   }

   public byte[] getBytesProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      Object value = doGetProperty(key);
      if (value == null) {
         return null;
      } else if (value instanceof byte[] bytes) {
         return bytes;
      }
      throw new ActiveMQPropertyConversionException("Invalid conversion: " + key);
   }

   public Double getDoubleProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      Object value = doGetProperty(key);
      if (value == null) {
         return Double.valueOf(null);
      } else if (value instanceof Float floatValue) {
         return floatValue.doubleValue();
      } else if (value instanceof Double doubleValue) {
         return doubleValue;
      } else if (value instanceof SimpleString simpleString) {
         return Double.parseDouble(simpleString.toString());
      }
      throw new ActiveMQPropertyConversionException("Invalid conversion: " + key);
   }

   public Integer getIntProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      Object value = doGetProperty(key);
      if (value == null) {
         return Integer.valueOf(null);
      } else if (value instanceof Integer integer) {
         return integer;
      } else if (value instanceof Byte byteValue) {
         return byteValue.intValue();
      } else if (value instanceof Short shortValue) {
         return shortValue.intValue();
      } else if (value instanceof SimpleString simpleString) {
         return Integer.parseInt(simpleString.toString());
      }
      throw new ActiveMQPropertyConversionException("Invalid conversion: " + key);
   }

   public Long getLongProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      Object value = doGetProperty(key);
      if (value == null) {
         return Long.valueOf(null);
      } else if (value instanceof Long longValue) {
         return longValue;
      } else if (value instanceof Byte byteValue) {
         return byteValue.longValue();
      } else if (value instanceof Short shortValue) {
         return shortValue.longValue();
      } else if (value instanceof Integer integer) {
         return integer.longValue();
      } else if (value instanceof SimpleString simpleString) {
         return Long.parseLong(simpleString.toString());
      }
      throw new ActiveMQPropertyConversionException("Invalid conversion: " + key);
   }

   public Short getShortProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      Object value = doGetProperty(key);
      if (value == null) {
         return Short.valueOf(null);
      } else if (value instanceof Byte byteValue) {
         return byteValue.shortValue();
      } else if (value instanceof Short shortValue) {
         return shortValue;
      } else if (value instanceof SimpleString simpleString) {
         return Short.parseShort(simpleString.toString());
      }
      throw new ActiveMQPropertyConversionException("Invalid conversion: " + key);
   }

   public Float getFloatProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      Object value = doGetProperty(key);
      if (value == null) {
         return Float.valueOf(null);
      }
      if (value instanceof Float floatValue) {
         return floatValue;
      }
      if (value instanceof SimpleString string) {
         return Float.parseFloat(string.toString());
      }
      throw new ActiveMQPropertyConversionException("Invalid conversion: " + key);
   }

   public SimpleString getSimpleStringProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      Object value = doGetProperty(key);

      if (value == null) {
         return null;
      }

      if (value instanceof SimpleString string) {
         return string;
      } else if (value instanceof Boolean) {
         return SimpleString.of(value.toString());
      } else if (value instanceof Character) {
         return SimpleString.of(value.toString());
      } else if (value instanceof Byte) {
         return SimpleString.of(value.toString());
      } else if (value instanceof Short) {
         return SimpleString.of(value.toString());
      } else if (value instanceof Integer) {
         return SimpleString.of(value.toString());
      } else if (value instanceof Long) {
         return SimpleString.of(value.toString());
      } else if (value instanceof Float) {
         return SimpleString.of(value.toString());
      } else if (value instanceof Double) {
         return SimpleString.of(value.toString());
      }
      throw new ActiveMQPropertyConversionException("Invalid conversion: " + key);
   }

   public Object removeProperty(final SimpleString key) {
      return doRemoveProperty(key);
   }

   public boolean containsProperty(final SimpleString key) {
      lock.readLock().lock();
      try {
         if (properties == null) {
            return false;

         } else {
            return properties.containsKey(key);
         }
      } finally {
         lock.readLock().unlock();
      }
   }

   public Set<SimpleString> getPropertyNames() {
      lock.readLock().lock();
      try {
         if (properties == null) {
            return Collections.emptySet();
         } else {
            return new HashSet<>(properties.keySet());
         }
      } finally {
         lock.readLock().unlock();
      }
   }

   public boolean clearInternalProperties() {
      lock.writeLock().lock();
      try {
         return internalProperties && removeInternalProperties();
      } finally {
         lock.writeLock().unlock();
      }
   }

   public boolean clearAMQPProperties() {
      lock.writeLock().lock();
      try {
         return amqpProperties && removeAMQPProperties();
      } finally {
         lock.writeLock().unlock();
      }
   }

   private boolean removeInternalProperties() {
      lock.writeLock().lock();
      try {
         if (internalPropertyPredicate == null) {
            return false;
         }
         if (properties == null) {
            return false;
         }
         if (properties.isEmpty()) {
            return false;
         }
         boolean removed = removePredicate(internalPropertyPredicate);
         internalProperties = false;
         return removed;
      } finally {
         lock.writeLock().unlock();
      }
   }

   private boolean removeAMQPProperties() {
      lock.writeLock().lock();
      try {
         if (amqpPropertyPredicate == null) {
            return false;
         }
         if (properties == null) {
            return false;
         }
         if (properties.isEmpty()) {
            return false;
         }
         boolean removed = removePredicate(amqpPropertyPredicate);
         amqpProperties = false;
         return removed;
      } finally {
         lock.writeLock().unlock();
      }
   }

   private boolean removePredicate(Predicate<SimpleString> predicate) {
      int removedBytes = 0;
      boolean removed = false;
      final Iterator<Entry<SimpleString, PropertyValue>> keyNameIterator = properties.entrySet().iterator();
      while (keyNameIterator.hasNext()) {
         final Entry<SimpleString, PropertyValue> entry = keyNameIterator.next();
         final SimpleString propertyName = entry.getKey();
         if (predicate.test(propertyName)) {
            final PropertyValue propertyValue = entry.getValue();
            removedBytes += propertyName.sizeof() + propertyValue.encodeSize();
            keyNameIterator.remove();
            removed = true;
         }
      }
      size -= removedBytes;
      return removed;
   }

   /**
    * This method is read-only. Do not modify the TypedProperties using the Consumer.
    *
    * @param action Consumer implementation that should not modify the TypedProperties
    */
   public void forEachKey(Consumer<SimpleString> action) {
      lock.readLock().lock();
      try {
         if (properties != null) {
            properties.keySet().forEach(action::accept);
         }
      } finally {
         lock.readLock().unlock();
      }
   }

   /**
    * This method is read-only. Do not modify the TypedProperties using the BiConsumer.
    *
    * @param action BiConsumer implementation that should not modify the TypedProperties
    */
   public void forEach(BiConsumer<SimpleString, Object> action) {
      lock.readLock().lock();
      try {
         if (properties != null) {
            properties.forEach((k, v) -> action.accept(k, v.getValue()));
         }
      } finally {
         lock.readLock().unlock();
      }
   }

   /**
    * This method is read-only. Do not modify the TypedProperties using the BiConsumer.
    *
    * @param action BiConsumer implementation that should not modify the TypedProperties
    */
   private void forEachInternal(BiConsumer<SimpleString, PropertyValue> action) {
      lock.readLock().lock();
      try {
         if (properties != null) {
            properties.forEach(action::accept);
         }
      } finally {
         lock.readLock().unlock();
      }
   }

   /**
    * Performs a search among the valid key properties contained in {@code buffer}, starting from {@code from} assuming
    * it to be a valid encoded {@link TypedProperties} content.
    *
    * @throws IllegalStateException if any not-valid property is found while searching the {@code key} property
    */
   public static boolean searchProperty(SimpleString key, ByteBuf buffer, int startIndex) {
      // It won't implement a straight linear search for key
      // because it would risk to find a SimpleString encoded property value
      // equals to the key we're searching for!
      int index = startIndex;
      byte b = buffer.getByte(index);
      index++;
      if (b == DataConstants.NULL) {
         return false;
      }
      final int numHeaders = buffer.getInt(index);
      index += Integer.BYTES;
      for (int i = 0; i < numHeaders; i++) {
         final int keyLength = buffer.getInt(index);
         index += Integer.BYTES;
         if (key.equals(buffer, index, keyLength)) {
            return true;
         }
         if (i == numHeaders - 1) {
            return false;
         }
         index += keyLength;
         byte type = buffer.getByte(index);
         index++;
         switch (type) {
            case NULL: {
               break;
            }
            case CHAR:
            case SHORT: {
               index += Short.BYTES;
               break;
            }
            case BOOLEAN:
            case BYTE: {
               index += Byte.BYTES;
               break;
            }
            case BYTES:
            case STRING: {
               index += (Integer.BYTES + buffer.getInt(index));
               break;
            }
            case INT: {
               index += Integer.BYTES;
               break;
            }
            case LONG: {
               index += Long.BYTES;
               break;
            }
            case FLOAT: {
               index += Float.BYTES;
               break;
            }
            case DOUBLE: {
               index += Double.BYTES;
               break;
            }
            default: {
               throw ActiveMQUtilBundle.BUNDLE.invalidType(type);
            }
         }
      }
      return false;
   }

   public void decode(final ByteBuf buffer, final TypedPropertiesDecoderPools keyValuePools) {
      lock.writeLock().lock();
      try {
         byte b = buffer.readByte();
         if (b == DataConstants.NULL) {
            properties = null;
            size = 0;
         } else {
            int numHeaders = buffer.readInt();

            //optimize the case of no collisions to avoid any resize (it doubles the map size!!!) when load factor is reached
            properties = new HashMap<>(numHeaders, 1.0f);
            size = 0;

            for (int i = 0; i < numHeaders; i++) {
               final SimpleString key = SimpleString.readSimpleString(buffer, keyValuePools == null ? null : keyValuePools.getPropertyKeysPool());

               byte type = buffer.readByte();

               PropertyValue val;

               switch (type) {
                  case NULL: {
                     val = NullValue.INSTANCE;
                     doPutValue(key, val);
                     break;
                  }
                  case CHAR: {
                     val = new CharValue(buffer);
                     doPutValue(key, val);
                     break;
                  }
                  case BOOLEAN: {
                     val = BooleanValue.of(buffer.readBoolean());
                     doPutValue(key, val);
                     break;
                  }
                  case BYTE: {
                     val = ByteValue.valueOf(buffer.readByte());
                     doPutValue(key, val);
                     break;
                  }
                  case BYTES: {
                     val = new BytesValue(buffer);
                     doPutValue(key, val);
                     break;
                  }
                  case SHORT: {
                     val = new ShortValue(buffer);
                     doPutValue(key, val);
                     break;
                  }
                  case INT: {
                     val = new IntValue(buffer);
                     doPutValue(key, val);
                     break;
                  }
                  case LONG: {
                     val = new LongValue(buffer);
                     doPutValue(key, val);
                     break;
                  }
                  case FLOAT: {
                     val = new FloatValue(buffer);
                     doPutValue(key, val);
                     break;
                  }
                  case DOUBLE: {
                     val = new DoubleValue(buffer);
                     doPutValue(key, val);
                     break;
                  }
                  case STRING: {
                     val = StringValue.readStringValue(buffer, keyValuePools == null ? null : keyValuePools.getPropertyValuesPool());
                     doPutValue(key, val);
                     break;
                  }
                  default: {
                     throw ActiveMQUtilBundle.BUNDLE.invalidType(type);
                  }
               }
            }
         }
      } finally {
         lock.writeLock().unlock();
      }
   }

   public void decode(final ByteBuf buffer) {
      decode(buffer, null);
   }

   public int encode(final ByteBuf buffer) {
      lock.readLock().lock();
      try {
         final int encodedSize;
         // it's a trick to not pay the cost of buffer.writeIndex without assertions enabled
         int writerIndex = 0;
         assert (writerIndex = buffer.writerIndex()) >= 0 : "Always true";
         if (properties == null || size == 0) {
            encodedSize = DataConstants.SIZE_BYTE;
            ensureExactWritable(buffer, encodedSize);
            buffer.writeByte(DataConstants.NULL);
         } else {
            encodedSize = DataConstants.SIZE_BYTE + DataConstants.SIZE_INT + size;
            ensureExactWritable(buffer, encodedSize);
            buffer.writeByte(DataConstants.NOT_NULL);

            buffer.writeInt(properties.size());

            //uses internal iteration to allow inlining/loop unrolling
            properties.forEach((key, value) -> {
               final byte[] data = key.getData();
               buffer.writeInt(data.length);
               buffer.writeBytes(data);
               value.write(buffer);
            });
         }
         assert buffer.writerIndex() == (writerIndex + encodedSize) : "Bad encode size estimation";
         return encodedSize;
      } finally {
         lock.readLock().unlock();
      }
   }

   public int getEncodeSize() {
      lock.readLock().lock();
      try {
         if (properties == null || size == 0) {
            return DataConstants.SIZE_BYTE;
         } else {
            return DataConstants.SIZE_BYTE + DataConstants.SIZE_INT + size;
         }
      } finally {
         lock.readLock().unlock();
      }
   }

   public void clear() {
      lock.writeLock().lock();
      try {
         if (properties != null) {
            properties.clear();
         }
         size = 0;
      } finally {
         lock.writeLock().unlock();
      }
   }

   @Override
   public String toString() {
      lock.readLock().lock();
      try {
         StringBuilder sb = new StringBuilder("TypedProperties[");

         if (properties != null) {
            Iterator<Entry<SimpleString, PropertyValue>> iter = properties.entrySet().iterator();

            while (iter.hasNext()) {
               Entry<SimpleString, PropertyValue> iterItem = iter.next();
               sb.append(iterItem.getKey() + "=");

               // it seems weird but it's right!!
               // The first getValue is from the EntrySet
               // The second is to convert the PropertyValue into the actual value
               Object theValue = iterItem.getValue().getValue();

               if (theValue == null) {
                  sb.append("NULL-value");
               } else if (theValue instanceof byte[] bytes) {
                  sb.append("[" + ByteUtil.maxString(ByteUtil.bytesToHex(bytes, 2), 150) + "]");

                  if (iterItem.getKey().toString().startsWith("_AMQ_ROUTE_TO")) {
                     sb.append(", bytesAsLongs[");
                     try {
                        ByteBuffer buff = ByteBuffer.wrap(bytes);
                        while (buff.hasRemaining()) {
                           long bindingID = buff.getLong();
                           sb.append(bindingID);
                           if (buff.hasRemaining()) {
                              sb.append(", ");
                           }
                        }
                     } catch (Throwable e) {
                        sb.append("error-converting-longs=" + e.getMessage());
                     }
                     sb.append("]");
                  }
               } else {
                  sb.append(theValue.toString());
               }

               if (iter.hasNext()) {
                  sb.append(", ");
               }
            }
         }

         return sb.append("]").toString();
      } finally {
         lock.readLock().unlock();
      }
   }

   private void doPutValue(final SimpleString key, final PropertyValue value) {
      lock.writeLock().lock();
      try {
         if (!internalProperties && internalPropertyPredicate != null && internalPropertyPredicate.test(key)) {
            internalProperties = true;
         }

         if (!amqpProperties && amqpPropertyPredicate != null && amqpPropertyPredicate.test(key)) {
            amqpProperties = true;
         }

         if (properties == null) {
            properties = new HashMap<>();
         }

         PropertyValue oldValue = properties.put(key, value);
         if (oldValue != null) {
            size += value.encodeSize() - oldValue.encodeSize();
         } else {
            size += SimpleString.sizeofString(key) + value.encodeSize();
         }
      } finally {
         lock.writeLock().unlock();
      }
   }

   private Object doRemoveProperty(final SimpleString key) {
      lock.writeLock().lock();
      try {
         if (properties == null) {
            return null;
         }

         PropertyValue val = properties.remove(key);
         if (val == null) {
            return null;
         } else {
            size -= SimpleString.sizeofString(key) + val.encodeSize();
            return val.getValue();
         }
      } finally {
         lock.writeLock().unlock();
      }
   }

   private Object doGetProperty(final SimpleString key) {
      lock.readLock().lock();
      try {
         if (properties == null) {
            return null;
         }

         PropertyValue val = properties.get(key);
         if (val == null) {
            return null;
         } else {
            return val.getValue();
         }
      } finally {
         lock.readLock().unlock();
      }
   }

   private abstract static class PropertyValue {

      abstract Object getValue();

      abstract void write(ByteBuf buffer);

      abstract int encodeSize();

      @Override
      public String toString() {
         return "" + getValue();
      }
   }

   private static final class NullValue extends PropertyValue {

      private static final NullValue INSTANCE = new NullValue();

      private NullValue() {
      }

      @Override
      public Object getValue() {
         return null;
      }

      @Override
      public void write(final ByteBuf buffer) {
         buffer.writeByte(DataConstants.NULL);
      }

      @Override
      public int encodeSize() {
         return DataConstants.SIZE_BYTE;
      }

   }

   private static final class BooleanValue extends PropertyValue {

      private static final int ENCODE_SIZE = DataConstants.SIZE_BYTE + DataConstants.SIZE_BOOLEAN;
      private static final BooleanValue TRUE = new BooleanValue(true);
      private static final BooleanValue FALSE = new BooleanValue(false);

      private final boolean val;
      private final Boolean objVal;

      private BooleanValue(final boolean val) {
         this.val = val;
         this.objVal = val;
      }

      private static BooleanValue of(final boolean val) {
         if (val) {
            return TRUE;
         } else {
            return FALSE;
         }
      }

      @Override
      public Object getValue() {
         return objVal;
      }

      @Override
      public void write(final ByteBuf buffer) {
         buffer.writeByte(DataConstants.BOOLEAN);
         buffer.writeBoolean(val);
      }

      @Override
      public int encodeSize() {
         return ENCODE_SIZE;
      }

   }

   private static final class ByteValue extends PropertyValue {

      private static final int ENCODE_SIZE = DataConstants.SIZE_BYTE + DataConstants.SIZE_BYTE;

      //LAZY CACHE that uses a benign race condition to avoid too many allocations of ByteValue if contended and to allocate upfront unneeded instances.
      //the Java spec doesn't allow tearing while reading/writing from arrays of references
      private static final ByteValue[] VALUES = new ByteValue[-(-128) + 127 + 1];

      private static ByteValue valueOf(byte b) {
         final int offset = 128;
         final int index = (int) b + offset;
         ByteValue value = VALUES[index];
         if (value == null) {
            return VALUES[index] = new ByteValue(b);
         } else {
            return value;
         }
      }

      private final byte val;
      private final Byte objectVal;

      private ByteValue(final byte val) {
         this.val = val;
         this.objectVal = val;
      }

      @Override
      public Object getValue() {
         return objectVal;
      }

      @Override
      public void write(final ByteBuf buffer) {
         buffer.writeByte(DataConstants.BYTE);
         buffer.writeByte(val);
      }

      @Override
      public int encodeSize() {
         return ENCODE_SIZE;
      }
   }

   private static final class BytesValue extends PropertyValue {

      final byte[] val;

      private BytesValue(final byte[] val) {
         this.val = val;
      }

      private BytesValue(final ByteBuf buffer) {
         int len = buffer.readInt();
         val = new byte[len];
         buffer.readBytes(val);
      }

      @Override
      public Object getValue() {
         return val;
      }

      @Override
      public void write(final ByteBuf buffer) {
         buffer.writeByte(DataConstants.BYTES);
         buffer.writeInt(val.length);
         buffer.writeBytes(val);
      }

      @Override
      public int encodeSize() {
         return DataConstants.SIZE_BYTE + DataConstants.SIZE_INT + val.length;
      }

   }

   private static final class ShortValue extends PropertyValue {

      final short val;

      private ShortValue(final short val) {
         this.val = val;
      }

      private ShortValue(final ByteBuf buffer) {
         val = buffer.readShort();
      }

      @Override
      public Object getValue() {
         return val;
      }

      @Override
      public void write(final ByteBuf buffer) {
         buffer.writeByte(DataConstants.SHORT);
         buffer.writeShort(val);
      }

      @Override
      public int encodeSize() {
         return DataConstants.SIZE_BYTE + DataConstants.SIZE_SHORT;
      }
   }

   private static final class IntValue extends PropertyValue {

      final int val;

      private IntValue(final int val) {
         this.val = val;
      }

      private IntValue(final ByteBuf buffer) {
         val = buffer.readInt();
      }

      @Override
      public Object getValue() {
         return val;
      }

      @Override
      public void write(final ByteBuf buffer) {
         buffer.writeByte(DataConstants.INT);
         buffer.writeInt(val);
      }

      @Override
      public int encodeSize() {
         return DataConstants.SIZE_BYTE + DataConstants.SIZE_INT;
      }
   }

   private static final class LongValue extends PropertyValue {

      final long val;

      private LongValue(final long val) {
         this.val = val;
      }

      private LongValue(final ByteBuf buffer) {
         val = buffer.readLong();
      }

      @Override
      public Object getValue() {
         return val;
      }

      @Override
      public void write(final ByteBuf buffer) {
         buffer.writeByte(DataConstants.LONG);
         buffer.writeLong(val);
      }

      @Override
      public int encodeSize() {
         return DataConstants.SIZE_BYTE + DataConstants.SIZE_LONG;
      }
   }

   private static final class FloatValue extends PropertyValue {

      final float val;

      private FloatValue(final float val) {
         this.val = val;
      }

      private FloatValue(final ByteBuf buffer) {
         val = Float.intBitsToFloat(buffer.readInt());
      }

      @Override
      public Object getValue() {
         return val;
      }

      @Override
      public void write(final ByteBuf buffer) {
         buffer.writeByte(DataConstants.FLOAT);
         buffer.writeInt(Float.floatToIntBits(val));
      }

      @Override
      public int encodeSize() {
         return DataConstants.SIZE_BYTE + DataConstants.SIZE_FLOAT;
      }

   }

   private static final class DoubleValue extends PropertyValue {

      final double val;

      private DoubleValue(final double val) {
         this.val = val;
      }

      private DoubleValue(final ByteBuf buffer) {
         val = Double.longBitsToDouble(buffer.readLong());
      }

      @Override
      public Object getValue() {
         return val;
      }

      @Override
      public void write(final ByteBuf buffer) {
         buffer.writeByte(DataConstants.DOUBLE);
         buffer.writeLong(Double.doubleToLongBits(val));
      }

      @Override
      public int encodeSize() {
         return DataConstants.SIZE_BYTE + DataConstants.SIZE_DOUBLE;
      }
   }

   private static final class CharValue extends PropertyValue {

      final char val;

      private CharValue(final char val) {
         this.val = val;
      }

      private CharValue(final ByteBuf buffer) {
         val = (char) buffer.readShort();
      }

      @Override
      public Object getValue() {
         return val;
      }

      @Override
      public void write(final ByteBuf buffer) {
         buffer.writeByte(DataConstants.CHAR);
         buffer.writeShort((short) val);
      }

      @Override
      public int encodeSize() {
         return DataConstants.SIZE_BYTE + DataConstants.SIZE_CHAR;
      }
   }

   public static final class StringValue extends PropertyValue {

      final SimpleString val;

      private StringValue(final SimpleString val) {
         this.val = val;
      }

      static StringValue readStringValue(final ByteBuf byteBuf, ByteBufStringValuePool pool) {
         if (pool == null) {
            return new StringValue(SimpleString.readSimpleString(byteBuf));
         } else {
            return pool.getOrCreate(byteBuf);
         }
      }

      @Override
      public Object getValue() {
         return val;
      }

      @Override
      public void write(final ByteBuf buffer) {
         buffer.writeByte(DataConstants.STRING);
         SimpleString.writeSimpleString(buffer, val);
      }

      @Override
      public int encodeSize() {
         return DataConstants.SIZE_BYTE + SimpleString.sizeofString(val);
      }

      public static final class ByteBufStringValuePool extends AbstractByteBufPool<StringValue> {

         public static final int DEFAULT_MAX_LENGTH = 36;

         private final int maxLength;

         public ByteBufStringValuePool() {
            this.maxLength = DEFAULT_MAX_LENGTH;
         }

         public ByteBufStringValuePool(final int capacity) {
            this(capacity, DEFAULT_MAX_LENGTH);
         }

         public ByteBufStringValuePool(final int capacity, final int maxCharsLength) {
            super(capacity);
            this.maxLength = maxCharsLength;
         }

         @Override
         protected boolean isEqual(final StringValue entry, final ByteBuf byteBuf, final int offset, final int length) {
            if (entry == null || entry.val == null) {
               return false;
            }
            return entry.val.equals(byteBuf, offset, length);
         }

         @Override
         protected boolean canPool(final ByteBuf byteBuf, final int length) {
            assert length % 2 == 0 : "length must be a multiple of 2";
            final int expectedStringLength = length >> 1;
            return expectedStringLength <= maxLength;
         }

         @Override
         protected StringValue create(final ByteBuf byteBuf, final int length) {
            return new StringValue(SimpleString.readSimpleString(byteBuf, length));
         }
      }
   }

   public static class TypedPropertiesDecoderPools {

      private SimpleString.ByteBufSimpleStringPool propertyKeysPool;
      private TypedProperties.StringValue.ByteBufStringValuePool propertyValuesPool;

      public TypedPropertiesDecoderPools() {
         this.propertyKeysPool = new SimpleString.ByteBufSimpleStringPool();
         this.propertyValuesPool = new TypedProperties.StringValue.ByteBufStringValuePool();
      }

      public TypedPropertiesDecoderPools(int keyPoolCapacity, int valuePoolCapacity) {
         this.propertyKeysPool = new SimpleString.ByteBufSimpleStringPool(keyPoolCapacity);
         this.propertyValuesPool = new TypedProperties.StringValue.ByteBufStringValuePool(valuePoolCapacity);
      }

      public SimpleString.ByteBufSimpleStringPool getPropertyKeysPool() {
         return propertyKeysPool;
      }

      public TypedProperties.StringValue.ByteBufStringValuePool getPropertyValuesPool() {
         return propertyValuesPool;
      }
   }

   public static class TypedPropertiesStringSimpleStringPools {

      private SimpleString.StringSimpleStringPool propertyKeysPool;
      private SimpleString.StringSimpleStringPool propertyValuesPool;

      public TypedPropertiesStringSimpleStringPools() {
         this.propertyKeysPool = new SimpleString.StringSimpleStringPool();
         this.propertyValuesPool = new SimpleString.StringSimpleStringPool();
      }

      public TypedPropertiesStringSimpleStringPools(int keyPoolCapacity, int valuePoolCapacity) {
         this.propertyKeysPool = new SimpleString.StringSimpleStringPool(keyPoolCapacity);
         this.propertyValuesPool = new SimpleString.StringSimpleStringPool(valuePoolCapacity);
      }

      public SimpleString.StringSimpleStringPool getPropertyKeysPool() {
         return propertyKeysPool;
      }

      public SimpleString.StringSimpleStringPool getPropertyValuesPool() {
         return propertyValuesPool;
      }
   }

   public boolean isEmpty() {
      lock.readLock().lock();
      try {
         if (properties == null) {
            return true;
         } else {
            return properties.isEmpty();
         }
      } finally {
         lock.readLock().unlock();
      }
   }

   public Set<String> getMapNames() {
      lock.readLock().lock();
      try {
         if (properties == null) {
            return Collections.emptySet();
         } else {
            Set<String> names = new HashSet<>(properties.size());
            for (SimpleString name : properties.keySet()) {
               names.add(name.toString());
            }
            return names;
         }
      } finally {
         lock.readLock().unlock();
      }
   }

   public Map<String, Object> getMap() {
      lock.readLock().lock();
      try {
         if (properties == null) {
            return Collections.emptyMap();
         } else {
            Map<String, Object> m = new HashMap<>(properties.size());
            for (Entry<SimpleString, PropertyValue> entry : properties.entrySet()) {
               Object val = entry.getValue().getValue();
               if (val instanceof SimpleString simpleString) {
                  m.put(entry.getKey().toString(), simpleString.toString());
               } else {
                  m.put(entry.getKey().toString(), val);
               }
            }
            return m;
         }
      } finally {
         lock.readLock().unlock();
      }
   }

   public Lock getReadLock() {
      return lock.readLock();
   }

   public Lock getWriteLock() {
      return lock.writeLock();
   }

   /**
    * Helper for MapMessage#setObjectProperty(String, Object)
    *
    * @param key        The SimpleString key
    * @param value      The Object value
    * @param properties The typed properties
    */
   public static void setObjectProperty(final SimpleString key, final Object value, final TypedProperties properties) {
      if (value == null) {
         properties.putNullValue(key);
      } else if (value instanceof Boolean booleanValue) {
         properties.putBooleanProperty(key, booleanValue);
      } else if (value instanceof Byte byteValue) {
         properties.putByteProperty(key, byteValue);
      } else if (value instanceof Character character) {
         properties.putCharProperty(key, character);
      } else if (value instanceof Short shortValue) {
         properties.putShortProperty(key, shortValue);
      } else if (value instanceof Integer integer) {
         properties.putIntProperty(key, integer);
      } else if (value instanceof Long longValue) {
         properties.putLongProperty(key, longValue);
      } else if (value instanceof Float floatValue) {
         properties.putFloatProperty(key, floatValue);
      } else if (value instanceof Double doubleValue) {
         properties.putDoubleProperty(key, doubleValue);
      } else if (value instanceof String string) {
         properties.putSimpleStringProperty(key, SimpleString.of(string));
      } else if (value instanceof SimpleString simpleString) {
         properties.putSimpleStringProperty(key, simpleString);
      } else if (value instanceof byte[] bytes) {
         properties.putBytesProperty(key, bytes);
      } else {
         throw new ActiveMQPropertyConversionException(value.getClass() + " is not a valid property type");
      }
   }
}
