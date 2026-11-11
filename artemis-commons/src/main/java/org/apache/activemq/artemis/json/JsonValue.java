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

import org.apache.activemq.artemis.json.impl.JsonArrayImpl;
import org.apache.activemq.artemis.json.impl.JsonObjectImpl;
import org.apache.activemq.artemis.json.impl.JsonValueImpl;

/**
 * A single value in a JSON expression.
 */
public interface JsonValue {

   /**
    * The empty JSON object.
    */
   JsonObject EMPTY_JSON_OBJECT = new JsonObjectImpl(javax.json.JsonValue.EMPTY_JSON_OBJECT);

   /**
    * The empty JSON array.
    */
   JsonArray EMPTY_JSON_ARRAY = new JsonArrayImpl(javax.json.JsonValue.EMPTY_JSON_ARRAY);

   /**
    * A constant JsonValue for null values
    */
   JsonValue NULL = new JsonValueImpl(javax.json.JsonValue.NULL);

   /**
    * A constant JsonValue for TRUE
    */
   JsonValue TRUE = new JsonValueImpl(javax.json.JsonValue.TRUE);

   /**
    * A constant JsonValue for FALSE
    */
   JsonValue FALSE = new JsonValueImpl(javax.json.JsonValue.FALSE);

   ValueType getValueType();

   @Override
   String toString();

   enum ValueType {
      ARRAY,
      OBJECT, STRING, NUMBER,
      TRUE, FALSE,
      NULL
   }

   default JsonObject asJsonObject() {
      return JsonObject.class.cast(this);
   }

   default JsonArray asJsonArray() {
      return JsonArray.class.cast(this);
   }

}