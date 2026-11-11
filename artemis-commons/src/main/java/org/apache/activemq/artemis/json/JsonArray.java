/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.apache.activemq.artemis.json;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A JsonArray e.g.
 * <pre>
 * [1,5,8]
 * </pre>
 * or
 * <pre>
 *  [
 *   {"name":"karl", "age": 38},
 *   {"name":"sue", "age": 42},
 *  ]
 * </pre>
 */
public interface JsonArray extends JsonValue, List<JsonValue> {

   /**
    * {@return the JsonObject at the given position}
    *
    * @throws IndexOutOfBoundsException if the index is out of range
    * @throws ClassCastException        if the value at the specified position is not assignable to the JsonObject
    */
   JsonObject getJsonObject(int index);

   /**
    * {@return the JsonArray at the given position}
    *
    * @throws IndexOutOfBoundsException if the index is out of range
    * @throws ClassCastException        if the value at the specified position is not assignable to the JsonArray
    */
   JsonArray getJsonArray(int index);

   /**
    * {@return the JsonNumber at the given position}
    *
    * @throws IndexOutOfBoundsException if the index is out of range
    * @throws ClassCastException        if the value at the specified position is not assignable to the JsonNumber
    */
   JsonNumber getJsonNumber(int index);

   /**
    * {@return the JsonString at the given position}
    *
    * @throws IndexOutOfBoundsException if the index is out of range
    * @throws ClassCastException        if the value at the specified position is not assignable to the JsonString
    */
   JsonString getJsonString(int index);

   /**
    * {@return the respective JsonValue at the given position}
    *
    * @throws IndexOutOfBoundsException if the index is out of range
    * @throws ClassCastException        if the value at the specified position is not assignable to the given slazz
    */
   <T extends JsonValue> List<T> getValuesAs(Class<T> clazz);

   /**
    * Returns a {@code List} for the array. The value and the type of the elements in the list is specified by the
    * {@code func} argument.
    * <p>
    * This method can be used to obtain a list of the unwrapped types, such as
    * <pre>
    * {@code
    *     List<String> strings = ary1.getValuesAs(JsonString::getString);
    *     List<Integer> ints = ary2.getValuesAs(JsonNumber::intValue);
    * }
    * </pre>
    * It can also be used to obtain a list of simple projections, such as
    * <pre>
    * {@code
    *     Lsit<Integer> stringsizes = arr.getValueAs((JsonString v) -> v.getString().length();
    * }
    * </pre>
    *
    * @param <K>  The element type (must be a subtype of JsonValue) of this JsonArray.
    * @param <T>  The element type of the returned List
    * @param func The function that maps the elements of this JsonArray to the target elements.
    * @return A List of the specified values and type
    * @throws ClassCastException if the {@code JsonArray} contains a value of wrong type
    */
   default <T, K extends JsonValue> List<T> getValuesAs(Function<K, T> func) {
      Stream<K> stream = (Stream<K>) stream();
      return stream.map(func).collect(Collectors.toList());
   }

   /**
    * {@return the native String at the given position}
    *
    * @throws IndexOutOfBoundsException if the index is out of range
    * @throws ClassCastException        if the value at the specified position is not assignable to a String
    */
   String getString(int index);

   /**
    * {@return the native String at the given position or the defaultValue if null}
    *
    * @throws IndexOutOfBoundsException if the index is out of range
    * @throws ClassCastException        if the value at the specified position is not assignable to a String
    */
   String getString(int index, String defaultValue);

   /**
    * {@return the native int value at the given position}
    *
    * @throws IndexOutOfBoundsException if the index is out of range
    * @throws ClassCastException        if the value at the specified position is not assignable to an int
    * @throws NullPointerException      if an object with the given name doesn't exist
    */
   int getInt(int index);

   /**
    * {@return the native int value at the given position or the defaultValue if null}
    *
    * @throws IndexOutOfBoundsException if the index is out of range
    * @throws ClassCastException        if the value at the specified position is not assignable to an int
    */
   int getInt(int index, int defaultValue);

   /**
    * {@return the native boolean value at the given position}
    *
    * @throws IndexOutOfBoundsException if the index is out of range
    * @throws ClassCastException        if the value at the specified position is not assignable to a boolean
    * @throws NullPointerException      if an object with the given name doesn't exist
    */
   boolean getBoolean(int index);

   /**
    * {@return the native boolean value at the given position or the defaultValue if null}
    *
    * @throws IndexOutOfBoundsException if the index is out of range
    * @throws ClassCastException        if the value at the specified position is not assignable to a boolean
    */
   boolean getBoolean(int index, boolean defaultValue);

   /**
    * {@return whether the value at the given position is {@link JsonValue#NULL}}
    *
    * @throws IndexOutOfBoundsException if the index is out of range
    */
   boolean isNull(int index);
}
