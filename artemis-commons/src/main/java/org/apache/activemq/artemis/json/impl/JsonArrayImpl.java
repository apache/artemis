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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class JsonArrayImpl extends JsonValueImpl implements JsonArray {

   private final javax.json.JsonArray rawArray;

   public javax.json.JsonArray getRawArray() {
      return rawArray;
   }

   public JsonArrayImpl(javax.json.JsonArray rawArray) {
      super(rawArray);
      this.rawArray = rawArray;
   }

   @Override
   public JsonObject getJsonObject(int index) {
      return (JsonObject)this.wrap(rawArray.getJsonObject(index));
   }

   @Override
   public JsonArray getJsonArray(int index) {
      return (JsonArray)this.wrap(rawArray.getJsonArray(index));
   }

   @Override
   public JsonNumber getJsonNumber(int index) {
      return (JsonNumber)this.wrap(rawArray.getJsonNumber(index));
   }

   @Override
   public JsonString getJsonString(int index) {
      return (JsonString)this.wrap(rawArray.getJsonString(index));
   }

   @Override
   public <T extends JsonValue> List<T> getValuesAs(Class<T> clazz) {
      throw new UnsupportedOperationException();
   }

   @Override
   public String getString(int index) {
      return rawArray.getString(index);
   }

   @Override
   public String getString(int index, String defaultValue) {
      return rawArray.getString(index, defaultValue);
   }

   @Override
   public int getInt(int index) {
      return rawArray.getInt(index);
   }

   @Override
   public int getInt(int index, int defaultValue) {
      return rawArray.getInt(index, defaultValue);
   }

   @Override
   public boolean getBoolean(int index) {
      return rawArray.getBoolean(index);
   }

   @Override
   public boolean getBoolean(int index, boolean defaultValue) {
      return rawArray.getBoolean(index, defaultValue);
   }

   @Override
   public boolean isNull(int index) {
      return rawArray.isNull(index);
   }

   @Override
   public int size() {
      return rawArray.size();
   }

   @Override
   public boolean isEmpty() {
      return rawArray.isEmpty();
   }

   @Override
   public boolean contains(Object o) {
      if (o instanceof JsonValueImpl jsonValue) {
         return rawArray.contains(jsonValue.getRawValue());
      } else {
         return rawArray.contains(o);
      }
   }

   @Override
   public Iterator<JsonValue> iterator() {
      return new Iterator<>() {
         private Iterator<javax.json.JsonValue> rawIterator = rawArray.iterator();

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
   public Object[] toArray() {
      throw new UnsupportedOperationException();
   }

   @Override
   public <T> T[] toArray(T[] a) {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean add(JsonValue jsonValue) {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean remove(Object o) {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean containsAll(Collection<?> c) {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean addAll(Collection<? extends JsonValue> c) {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean addAll(int index, Collection<? extends JsonValue> c) {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean removeAll(Collection<?> c) {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean retainAll(Collection<?> c) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void clear() {
      rawArray.clear();
   }

   @Override
   public JsonValue get(int index) {
      return wrap(rawArray.get(index));
   }

   @Override
   public JsonValue set(int index, JsonValue element) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void add(int index, JsonValue element) {
      throw new UnsupportedOperationException();
   }

   @Override
   public JsonValue remove(int index) {
      throw new UnsupportedOperationException();
   }

   @Override
   public int indexOf(Object o) {
      throw new UnsupportedOperationException();
   }

   @Override
   public int lastIndexOf(Object o) {
      throw new UnsupportedOperationException();
   }

   @Override
   public ListIterator<JsonValue> listIterator() {
      throw new UnsupportedOperationException();
   }

   @Override
   public ListIterator<JsonValue> listIterator(int index) {
      throw new UnsupportedOperationException();
   }

   @Override
   public List<JsonValue> subList(int fromIndex, int toIndex) {
      throw new UnsupportedOperationException();
   }
}
