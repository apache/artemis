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

import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonArrayBuilder;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.json.JsonValue;

public class JsonArrayBuilderImpl implements JsonArrayBuilder {

   private final javax.json.JsonArrayBuilder rawArrayBuilder;

   public javax.json.JsonArrayBuilder getRawArrayBuilder() {
      return rawArrayBuilder;
   }

   public JsonArrayBuilderImpl(javax.json.JsonArrayBuilder rawArrayBuilder) {
      this.rawArrayBuilder = Objects.requireNonNull(rawArrayBuilder);
   }

   @Override
   public JsonArrayBuilder add(JsonValue value) {
      if (!(value instanceof JsonValueImpl)) {
         throw new UnsupportedOperationException();
      }
      rawArrayBuilder.add(((JsonValueImpl)value).getRawValue());
      return this;
   }

   @Override
   public JsonArrayBuilder add(String value) {
      rawArrayBuilder.add(value);
      return this;
   }

   @Override
   public JsonArrayBuilder add(BigDecimal value) {
      rawArrayBuilder.add(value);
      return this;
   }

   @Override
   public JsonArrayBuilder add(BigInteger value) {
      rawArrayBuilder.add(value);
      return this;
   }

   @Override
   public JsonArrayBuilder add(int value) {
      rawArrayBuilder.add(value);
      return this;
   }

   @Override
   public JsonArrayBuilder add(long value) {
      rawArrayBuilder.add(value);
      return this;
   }

   @Override
   public JsonArrayBuilder add(double value) {
      rawArrayBuilder.add(value);
      return this;
   }

   @Override
   public JsonArrayBuilder add(boolean value) {
      rawArrayBuilder.add(value);
      return this;
   }

   @Override
   public JsonArrayBuilder addNull() {
      rawArrayBuilder.addNull();
      return this;
   }

   @Override
   public JsonArrayBuilder add(JsonObjectBuilder builder) {
      if (!(builder instanceof JsonObjectBuilderImpl)) {
         throw new UnsupportedOperationException();
      }
      rawArrayBuilder.add(((JsonObjectBuilderImpl)builder).getRawObjectBuilder());
      return this;
   }

   @Override
   public JsonArrayBuilder add(JsonArrayBuilder builder) {
      if (!(builder instanceof JsonArrayBuilderImpl)) {
         throw new UnsupportedOperationException();
      }
      rawArrayBuilder.add(((JsonArrayBuilderImpl)builder).getRawArrayBuilder());
      return this;
   }

   @Override
   public JsonArrayBuilder remove(int index) {
      rawArrayBuilder.remove(index);
      return this;
   }

   @Override
   public JsonArray build() {
      return new JsonArrayImpl(rawArrayBuilder.build());
   }
}
