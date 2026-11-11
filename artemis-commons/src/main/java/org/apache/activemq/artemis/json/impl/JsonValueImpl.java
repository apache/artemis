/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.json.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonValue;

public class JsonValueImpl implements JsonValue {

   private Map<javax.json.JsonValue, JsonValue> cache = new HashMap<>();

   public JsonValue wrap(javax.json.JsonValue rawValue) {
      if (rawValue == null) {
         return null;
      }

      JsonValue cacheValue = cache.get(rawValue);

      if (cacheValue != null) {
         return cacheValue;
      } else if (rawValue == javax.json.JsonValue.EMPTY_JSON_OBJECT) {
         return JsonValue.EMPTY_JSON_OBJECT;
      } else if (rawValue == javax.json.JsonValue.EMPTY_JSON_ARRAY) {
         return JsonValue.EMPTY_JSON_ARRAY;
      } else if (rawValue == javax.json.JsonValue.TRUE) {
         return JsonValue.TRUE;
      } else if (rawValue == javax.json.JsonValue.FALSE) {
         return JsonValue.FALSE;
      } else if (rawValue == javax.json.JsonValue.NULL) {
         return JsonValue.NULL;
      } else if (rawValue.getValueType() == javax.json.JsonValue.ValueType.ARRAY) {
         cacheValue = new JsonArrayImpl((javax.json.JsonArray) rawValue);
      } else if (rawValue.getValueType() == javax.json.JsonValue.ValueType.OBJECT) {
         cacheValue = new JsonObjectImpl((javax.json.JsonObject) rawValue);
      } else if (rawValue.getValueType() == javax.json.JsonValue.ValueType.STRING) {
         cacheValue = new JsonStringImpl((javax.json.JsonString) rawValue);
      } else if (rawValue.getValueType() == javax.json.JsonValue.ValueType.NUMBER) {
         cacheValue = new JsonNumberImpl((javax.json.JsonNumber) rawValue);
      } else if (rawValue.getValueType() == javax.json.JsonValue.ValueType.TRUE) {
         cacheValue = new JsonValueImpl(rawValue);
      } else if (rawValue.getValueType() == javax.json.JsonValue.ValueType.FALSE) {
         cacheValue = new JsonValueImpl(rawValue);
      } else if (rawValue.getValueType() == javax.json.JsonValue.ValueType.NULL) {
         cacheValue = new JsonValueImpl(rawValue);
      } else {
         throw new IllegalStateException("Unexpected value: " + rawValue.getValueType());
      }

      cache.put(rawValue, cacheValue);

      return cacheValue;
   }

   private final javax.json.JsonValue rawValue;

   public javax.json.JsonValue getRawValue() {
      return rawValue;
   }

   public JsonValueImpl(javax.json.JsonValue rawValue) {
      this.rawValue = Objects.requireNonNull(rawValue);
   }


   @Override
   public JsonValue.ValueType getValueType() {
      return ValueType.valueOf(rawValue.getValueType().name());
   }

   @Override
   public JsonObject asJsonObject() {
      return JsonValue.super.asJsonObject();
   }

   @Override
   public JsonArray asJsonArray() {
      return JsonValue.super.asJsonArray();
   }

   @Override
   public String toString() {
      return rawValue.toString();
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof JsonValueImpl other)) {
         return false;
      }

      return Objects.equals(rawValue, other.getRawValue());
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(rawValue);
   }
}
