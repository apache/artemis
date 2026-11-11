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

import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonNumber;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonString;
import org.apache.activemq.artemis.json.JsonValue;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class JsonObjectImpl extends JsonValueImpl implements JsonObject {

   private final javax.json.JsonObject rawObject;

   public javax.json.JsonObject getRawObject() {
      return rawObject;
   }

   public JsonObjectImpl(javax.json.JsonObject rawObject) {
      super(rawObject);
      this.rawObject = rawObject;
   }

   @Override
   public JsonArray getJsonArray(String name) {
      return (JsonArray)wrap(rawObject.getJsonArray(name));
   }

   @Override
   public JsonObject getJsonObject(String name) {
      return (JsonObject)wrap(rawObject.getJsonObject(name));
   }

   @Override
   public JsonNumber getJsonNumber(String name) {
      return (JsonNumber)wrap(rawObject.getJsonNumber(name));
   }

   @Override
   public JsonString getJsonString(String name) {
      return (JsonString)wrap(rawObject.getJsonString(name));
   }

   @Override
   public String getString(String name) {
      return rawObject.getString(name);
   }

   @Override
   public String getString(String name, String defaultValue) {
      return rawObject.getString(name, defaultValue);
   }

   @Override
   public int getInt(String name) {
      return rawObject.getInt(name);
   }

   @Override
   public int getInt(String name, int defaultValue) {
      return rawObject.getInt(name, defaultValue);
   }

   @Override
   public boolean getBoolean(String name) {
      return rawObject.getBoolean(name);
   }

   @Override
   public boolean getBoolean(String name, boolean defaultValue) {
      return rawObject.getBoolean(name, defaultValue);
   }

   @Override
   public boolean isNull(String name) {
      return rawObject.isNull(name);
   }

   @Override
   public int size() {
      return rawObject.size();
   }


   @Override
   public boolean isEmpty() {
      return rawObject.isEmpty();
   }


   @Override
   public boolean containsKey(Object key) {
      return rawObject.containsKey(key);
   }

   @Override
   public boolean containsValue(Object value) {
      return rawObject.containsValue(value);
   }

   @Override
   public JsonValue get(Object key) {
      return wrap(rawObject.get(key));
   }

   @Override
   public JsonValue put(String key, JsonValue value) {
      if (!(value instanceof JsonValueImpl)) {
         throw new UnsupportedOperationException();
      }

      javax.json.JsonValue rawValue = rawObject.put(key, ((JsonValueImpl)value).getRawValue());

      return rawValue != null ? wrap(rawValue) : null;
   }

   @Override
   public JsonValue remove(Object key) {
      javax.json.JsonValue rawValue = rawObject.remove(key);

      return rawValue != null ? wrap(rawValue) : null;
   }

   @Override
   public void putAll(Map<? extends String, ? extends JsonValue> m) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void clear() {
      rawObject.clear();
   }

   @Override
   public Set<String> keySet() {
      return rawObject.keySet();
   }

   @Override
   public Collection<JsonValue> values() {
      return new AbstractCollection<>() {
         @Override
         public Iterator<JsonValue> iterator() {
            return new Iterator<>() {
               private Iterator<javax.json.JsonValue> rawIterator = rawObject.values().iterator();

               @Override
               public boolean hasNext() {
                  return rawIterator.hasNext();
               }

               @Override
               public JsonValue next() {
                  return wrap(rawIterator.next());
               }
            };
         }

         @Override
         public int size() {
            return rawObject.size();
         }
      };
   }

   @Override
   public Set<Map.Entry<String, JsonValue>> entrySet() {
      return new AbstractSet<>() {
         @Override
         public Iterator<Map.Entry<String, JsonValue>> iterator() {
            return new Iterator<>() {
               private Iterator<Map.Entry<String, javax.json.JsonValue>> rawIterator = rawObject.entrySet().iterator();

               @Override
               public boolean hasNext() {
                  return rawIterator.hasNext();
               }

               @Override
               public Map.Entry<String, JsonValue> next() {
                  Map.Entry<String, javax.json.JsonValue> rawEntry = rawIterator.next();

                  return rawEntry != null ? new AbstractMap.SimpleEntry<>(rawEntry.getKey(), wrap(rawEntry.getValue())) : null;
               }
            };
         }

         @Override
         public int size() {
            return rawObject.size();
         }
      };
   }
}
