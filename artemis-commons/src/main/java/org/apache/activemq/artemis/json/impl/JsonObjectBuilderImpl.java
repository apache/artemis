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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

import org.apache.activemq.artemis.json.JsonArrayBuilder;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.json.JsonValue;

public class JsonObjectBuilderImpl implements JsonObjectBuilder {

   private final javax.json.JsonObjectBuilder rawObjectBuilder;

   public javax.json.JsonObjectBuilder getRawObjectBuilder() {
      return rawObjectBuilder;
   }

   public JsonObjectBuilderImpl(javax.json.JsonObjectBuilder rawObjectBuilder) {
      this.rawObjectBuilder = Objects.requireNonNull(rawObjectBuilder);
   }

   @Override
   public JsonObjectBuilder add(String name, JsonValue value) {
      if (!(value instanceof JsonValueImpl)) {
         throw new UnsupportedOperationException();
      }
      rawObjectBuilder.add(name, ((JsonValueImpl)value).getRawValue());
      return this;
   }

   @Override
   public JsonObjectBuilder add(String name, String value) {
      rawObjectBuilder.add(name, value);
      return this;
   }

   @Override
   public JsonObjectBuilder add(String name, String value, JsonValue defaultValue) {
      if (value != null) {
         rawObjectBuilder.add(name, value);
      } else {
         add(name, defaultValue);
      }
      return this;
   }

   @Override
   public JsonObjectBuilder add(String name, BigInteger value) {
      rawObjectBuilder.add(name, value);
      return this;
   }

   @Override
   public JsonObjectBuilder add(String name, BigInteger value, JsonValue defaultValue) {
      if (value != null) {
         rawObjectBuilder.add(name, value);
      } else {
         add(name, defaultValue);
      }
      return this;
   }

   @Override
   public JsonObjectBuilder add(String name, BigDecimal value) {
      rawObjectBuilder.add(name, value);
      return this;
   }

   @Override
   public JsonObjectBuilder add(String name, BigDecimal value, JsonValue defaultValue) {
      if (value != null) {
         rawObjectBuilder.add(name, value);
      } else {
         add(name, defaultValue);
      }
      return this;
   }

   @Override
   public JsonObjectBuilder add(String name, int value) {
      rawObjectBuilder.add(name, value);
      return this;
   }

   @Override
   public JsonObjectBuilder add(String name, long value) {
      rawObjectBuilder.add(name, value);
      return this;
   }

   @Override
   public JsonObjectBuilder add(String name, double value) {
      rawObjectBuilder.add(name, value);
      return this;
   }

   @Override
   public JsonObjectBuilder add(String name, boolean value) {
      rawObjectBuilder.add(name, value);
      return this;
   }

   @Override
   public JsonObjectBuilder addNull(String name) {
      rawObjectBuilder.addNull(name);
      return this;
   }

   @Override
   public JsonObjectBuilder add(String name, JsonObjectBuilder builder) {
      if (!(builder instanceof JsonObjectBuilderImpl)) {
         throw new UnsupportedOperationException();
      }
      rawObjectBuilder.add(name, ((JsonObjectBuilderImpl)builder).getRawObjectBuilder());
      return this;
   }

   @Override
   public JsonObjectBuilder add(String name, JsonArrayBuilder builder) {
      if (!(builder instanceof JsonArrayBuilderImpl)) {
         throw new UnsupportedOperationException();
      }
      rawObjectBuilder.add(name, ((JsonArrayBuilderImpl)builder).getRawArrayBuilder());
      return this;
   }

   @Override
   public JsonObjectBuilder remove(String name) {
      rawObjectBuilder.remove(name);
      return this;
   }

   @Override
   public JsonObject build() {
      return new JsonObjectImpl(rawObjectBuilder.build());
   }
}
