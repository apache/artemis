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

import java.util.Map;

/**
 * A JsonObject, e.g.
 * <pre>
 * {
 *     "name":"karl",
 *     "age":38,
 *     "address": {
 *         "street":"dummystreet"
 *         "housenumber":12
 *     }
 * }
 * </pre>
 * <p>
 * A JsonObject is always also a Map which uses the attribute names as key mapping to their JsonValues.
 */
public interface JsonObject extends JsonValue, Map<String, JsonValue> {

   /**
    * {@return the JsonArray with the given name or {@code null} if there is no attribute with that name}
    *
    * @throws ClassCastException if the JsonValue cannot be correctly cast
    */
   JsonArray getJsonArray(String name);

   /**
    * {@return the JsonObject with the given name or {@code null} if there is no attribute with that name}
    *
    * @throws ClassCastException if the JsonValue cannot be correctly cast
    */
   JsonObject getJsonObject(String name);

   /**
    * {@return the JsonNumber with the given name or {@code null} if there is no attribute with that name}
    *
    * @throws ClassCastException if the JsonValue cannot be correctly cast
    */
   JsonNumber getJsonNumber(String name);

   /**
    * {@return the JsonString with the given name or {@code null} if there is no attribute with that name}
    *
    * @throws ClassCastException if the JsonValue cannot be correctly cast
    */
   JsonString getJsonString(String name);

   /**
    * {@return the native string with the given name or {@code null} if there is no attribute with that name}
    *
    * @throws ClassCastException if the JsonValue cannot be correctly cast
    */
   String getString(String name);

   /**
    * {@return the native string with the given name or the default value if there is no attribute with that name}
    *
    * @throws ClassCastException if the JsonValue cannot be correctly cast
    */
   String getString(String name, String defaultValue);

   /**
    * {@return the int with the given name or {@code null} if there is no attribute with that name}
    *
    * @throws ClassCastException   if the JsonValue cannot be correctly cast
    * @throws NullPointerException if an object with the given name doesn't exist
    */
   int getInt(String name);

   /**
    * {@return the int with the given name or the default value if there is no attribute with that name}
    *
    * @throws ClassCastException if the JsonValue cannot be correctly cast
    */
   int getInt(String name, int defaultValue);

   /**
    * {@return the boolean with the given name or {@code null} if there is no attribute with that name}
    *
    * @throws ClassCastException   if the JsonValue cannot be correctly cast
    * @throws NullPointerException if an object with the given name doesn't exist
    */
   boolean getBoolean(String name);

   /**
    * {@return the boolean with the given name or the default value if there is no attribute with that name}
    *
    * @throws ClassCastException if the JsonValue cannot be correctly cast
    */
   boolean getBoolean(String name, boolean defaultValue);

   /**
    * {@return whether the attribute with the given name is {@link JsonValue#NULL}}
    */
   boolean isNull(String name);
}