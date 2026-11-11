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

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * A JsonObjectBuilder can be used to build {@link JsonObject JsonObjects}. Instances are not thread safe.
 * <p>
 * Calling any of those methods with either the {@code name} or {@code value} param as {@code null} will result in a
 * {@code NullPointerException}
 */
public interface JsonObjectBuilder {

   /**
    * Add the given JsonValue value to the JsonObject to be created. If a value with that name already exists it will be
    * replaced by the new value.
    *
    * @param name  the JSON attribute name
    * @param value the JsonValue to add
    * @return the current JsonObjectBuilder
    */
   JsonObjectBuilder add(String name, JsonValue value);

   /**
    * Add the given String value to the JsonObject to be created. If a value with that name already exists it will be
    * replaced by the new value.
    *
    * @param name  the JSON attribute name
    * @param value the String value to add
    * @return the current JsonObjectBuilder
    */
   JsonObjectBuilder add(String name, String value);

   JsonObjectBuilder add(String name, String value, JsonValue defaultValue);

   /**
    * Add the given BigInteger value to the JsonObject to be created. If a value with that name already exists it will
    * be replaced by the new value.
    *
    * @param name  the JSON attribute name
    * @param value the BigInteger value to add
    * @return the current JsonObjectBuilder
    */
   JsonObjectBuilder add(String name, BigInteger value);

   JsonObjectBuilder add(String name, BigInteger value, JsonValue defaultValue);

   /**
    * Add the given BigDecimal value to the JsonObject to be created. If a value with that name already exists it will
    * be replaced by the new value.
    *
    * @param name  the JSON attribute name
    * @param value the BigDecimal value to add
    * @return the current JsonObjectBuilder
    */
   JsonObjectBuilder add(String name, BigDecimal value);

   JsonObjectBuilder add(String name, BigDecimal value, JsonValue defaultValue);

   /**
    * Add the given int value to the JsonObject to be created. If a value with that name already exists it will be
    * replaced by the new value.
    *
    * @param name  the JSON attribute name
    * @param value to add
    * @return the current JsonObjectBuilder
    */
   JsonObjectBuilder add(String name, int value);

   /**
    * Add the given long value to the JsonObject to be created. If a value with that name already exists it will be
    * replaced by the new value.
    *
    * @param name  the JSON attribute name
    * @param value to add
    * @return the current JsonObjectBuilder
    */
   JsonObjectBuilder add(String name, long value);

   /**
    * Add the given double value to the JsonObject to be created. If a value with that name already exists it will be
    * replaced by the new value.
    *
    * @param name  the JSON attribute name
    * @param value to add
    * @return the current JsonObjectBuilder
    */
   JsonObjectBuilder add(String name, double value);

   /**
    * Add the given boolean value to the JsonObject to be created. If a value with that name already exists it will be
    * replaced by the new value.
    *
    * @param name  the JSON attribute name
    * @param value to add
    * @return the current JsonObjectBuilder
    */
   JsonObjectBuilder add(String name, boolean value);

   /**
    * Add a {@link JsonValue#NULL} value to the JsonObject to be created. If a value with that name already exists it
    * will be replaced by the null value.
    *
    * @param name the JSON attribute name
    * @return the current JsonObjectBuilder
    */
   JsonObjectBuilder addNull(String name);

   /**
    * Use the given {@link JsonObjectBuilder} to create a {@link JsonObject} which will be added to the JsonObject to be
    * created by this builder. If a value with that name already exists it will be replaced by the new value.
    *
    * @param name    the JSON attribute name
    * @param builder for creating the JsonObject to add
    * @return the current JsonObjectBuilder
    */
   JsonObjectBuilder add(String name, JsonObjectBuilder builder);

   /**
    * Use the given {@link JsonArrayBuilder} to create a {@link JsonArray} which will be added to the JsonObject to be
    * created by this builder. If a value with that name already exists it will be replaced by the new value.
    *
    * @param name    the JSON attribute name
    * @param builder for creating the JsonArray to add
    * @return the current JsonObjectBuilder
    */
   JsonObjectBuilder add(String name, JsonArrayBuilder builder);

   /**
    * {@return a {@link JsonObject} based on the added values}
    */
   JsonObject build();

   /**
    * Add all of the attributes of the given {@link JsonObjectBuilder} to the current one
    */
   default JsonObjectBuilder addAll(JsonObjectBuilder builder) {
      throw new UnsupportedOperationException();
   }

   /**
    * Remove the attribute with the given name from the builder.
    */
   default JsonObjectBuilder remove(String name) {
      throw new UnsupportedOperationException();
   }
}