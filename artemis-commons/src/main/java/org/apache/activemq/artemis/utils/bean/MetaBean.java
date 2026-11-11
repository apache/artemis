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

package org.apache.activemq.artemis.utils.bean;

import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives a metadata about a class with methods to read, write and certain gates. And provides a generic logic to
 * convert to and from JSON.
 * <p>
 * As a historical context the first try to make a few objects more dynamic (e.g. AddressSettings) was around BeanUtils
 * however there was some implicit logic on when certain settings were Null or default values. for that reason I decided
 * for a meta-data approach where extra semantic could be applied for each individual attributes rather than a generic
 * BeanUtils parser.
 */
public class MetaBean<T> {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private List<MetaData<T>> metaData = new CopyOnWriteArrayList<>();

   /**
    * Accepted types:
    * <ul>
    * <li>String.class
    * <li>SimpleString.class
    * <li>Integer.class
    * <li>Long.class
    * <li>Double.class
    * <li>Float.class
    * <li>Enumerations
    * </ul>
    */
   public MetaBean add(Class type,
                       String name,
                       BiConsumer<T, ? extends Object> setter,
                       Function<T, ? extends Object> getter,
                       Predicate<T> gate) {
      if (type != String.class && type != SimpleString.class && type != Integer.class && type != Long.class && type != Double.class && type != Float.class && type != Boolean.class && !Enum.class.isAssignableFrom(type)) {
         throw new IllegalArgumentException("invalid type " + type);
      }

      metaData.add(new MetaData(type, name, setter, getter, gate));
      return this;
   }

   public <Z extends Object> MetaBean add(Class<Z> type,
                                          String name,
                                          BiConsumer<T, Z> setter,
                                          Function<T, Z> getter) {
      return add(type, name, setter, getter, null);
   }

   public JsonObject toJSON(T object, boolean ignoreNullAttributes) {
      JsonObjectBuilder builder = JsonLoader.createObjectBuilder();
      parseToJSON(object, builder, ignoreNullAttributes);
      return builder.build();
   }

   public void parseToJSON(T object, JsonObjectBuilder builder, boolean ignoreNullAttributes) {
      logger.debug("Parsing object {}", object);
      this.forEach((type, name, setter, getter, gate) -> {
         logger.debug("Parsing {} {} {} {} {}", type, name, setter, getter, gate);
         Object value = getter.apply(object);
         if (ignoreNullAttributes && value == null) {
            logger.debug("Ignoring null attribute {}", name);
            return;
         }
         if (gate == null || gate.test(object)) {

            if (logger.isTraceEnabled()) {

               if (gate != null) {
                  logger.trace("Gate passed for {}", name);
               }

               if (value == null) {
                  logger.debug("Result for {} = IS NULL", name);
               } else {
                  logger.debug("Result for {} = {}, type={}", name, value, value.getClass());
               }
            }

            if (value == null) {
               logger.trace("Setting {} as null", name);
               builder.addNull(name);
            } else if (type == String.class || type == SimpleString.class) {
               logger.trace("Setting {} as String {}", name, value);
               builder.add(name, String.valueOf(value));
            } else if (Number.class.isAssignableFrom(type) && value instanceof Number number) {
               if (value instanceof Double || value instanceof Float) {
                  logger.trace("Setting {} as double {}", name, value);
                  builder.add(name, number.doubleValue());
               } else {
                  logger.trace("Setting {} as long {}", name, value);
                  builder.add(name, number.longValue());
               }
            } else if (type == Boolean.class) {
               builder.add(name, (Boolean) value);
            } else {
               builder.add(name, String.valueOf(value));
            }
         } else {
            logger.debug("Gate ignored on {}", name);
         }
      });
   }

   /**
    * Generates a random Object using the setters for testing purposes.
    */
   public void setRandom(T randomObject) {
      forEach((type, name, setter, getter, gate) -> {
         if (Enum.class.isAssignableFrom(type)) {
            Object[] enumValues = type.getEnumConstants();
            int randomInt = RandomUtil.randomInterval(0, enumValues.length - 1);
            setter.accept(randomObject, enumValues[randomInt]);
         } else if (type == String.class) {
            setter.accept(randomObject, RandomUtil.randomUUIDString());
         } else if (type == SimpleString.class) {
            setter.accept(randomObject, RandomUtil.randomUUIDSimpleString());
         } else if (type == Integer.class) {
            setter.accept(randomObject, RandomUtil.randomPositiveInt());
         } else if (type == Long.class) {
            setter.accept(randomObject, RandomUtil.randomPositiveLong());
         } else if (type == Double.class) {
            setter.accept(randomObject, RandomUtil.randomDouble());
         } else if (type == Float.class) {
            setter.accept(randomObject, RandomUtil.randomFloat());
         } else if (type == Boolean.class) {
            setter.accept(randomObject, RandomUtil.randomBoolean());
         }
      });
   }

   public void copy(T source, T target) {
      forEach((type, name, setter, getter, gate) -> {
         Object sourceAttribute = getter.apply(source);
         setter.accept(target, sourceAttribute);
      });
   }

   public void forEach(MetadataListener listener) {
      metaData.forEach(m -> {
         try {
            listener.metaItem(m.type, m.name, m.setter, m.getter, m.gate);
         } catch (Throwable e) {
            logger.warn("Error parsing {}", m, e);
            throw new RuntimeException("Error while parsing " + m, e);
         }
      });
   }

   public void fromJSON(T resultObject, String jsonString) {

      logger.debug("Parsing JSON {}", jsonString);

      JsonObject json = JsonLoader.readObject(new StringReader(jsonString));

      this.forEach((type, name, setter, getter, gate) -> {
         if (json.containsKey(name)) {
            if (json.isNull(name)) {
               setter.accept(resultObject, null);
            } else if (type == String.class) {
               setter.accept(resultObject, json.getString(name));
            } else if (type == SimpleString.class) {
               setter.accept(resultObject, SimpleString.of(json.getString(name)));
            } else if (type == Integer.class) {
               setter.accept(resultObject, json.getInt(name));
            } else if (type == Long.class) {
               setter.accept(resultObject, json.getJsonNumber(name).longValue());
            } else if (type == Double.class) {
               setter.accept(resultObject, json.getJsonNumber(name).doubleValue());
            } else if (type == Float.class) {
               setter.accept(resultObject, json.getJsonNumber(name).numberValue().floatValue());
            } else if (type == Boolean.class) {
               setter.accept(resultObject, json.getBoolean(name));
            } else if (Enum.class.isAssignableFrom(type)) {
               String value = json.getString(name);
               Object enumValue = Enum.valueOf(type, value);
               setter.accept(resultObject, enumValue);
            }
         }
      });
   }

   static class MetaData<T> {

      Class type;
      String name;
      BiConsumer<T, ?> setter;
      Function<T, ?> getter;
      Predicate<?> gate;

      <Z> MetaData(Class<Z> type,
                          String name,
                          BiConsumer<T, Object> setter,
                          Function<T, Object> getter,
                          Predicate<T> gate) {
         this.type = type;
         this.name = name;
         this.setter = setter;
         this.getter = getter;
         this.gate = gate;
      }

      @Override
      public String toString() {
         return "MetaData{" + "type=" + type + ", name='" + name + '\'' + '}';
      }
   }

   public interface MetadataListener<T> {

      void metaItem(Class type,
                    String name,
                    BiConsumer<T, Object> setter,
                    Function<T, Object> getter,
                    Predicate<Object> gate);
   }

}
