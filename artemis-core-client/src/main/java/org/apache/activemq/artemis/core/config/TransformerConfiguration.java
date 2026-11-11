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
package org.apache.activemq.artemis.core.config;

import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.utils.JsonLoader;

import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonString;
import org.apache.activemq.artemis.json.JsonValue;
import java.io.Serializable;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class TransformerConfiguration implements Serializable {

   private static final long serialVersionUID = -1057244274380572226L;

   public static final String CLASS_NAME = "class-name";
   public static final String PROPERTIES = "properties";

   private String className;
   private Map<String, String> properties = new HashMap<>();

   public TransformerConfiguration(String className) {
      this.className = className;
   }

   public TransformerConfiguration() {
   }

   public void setClassName(String className) {
      this.className = className;
   }

   public String getClassName() {
      return className;
   }

   public Map<String, String> getProperties() {
      return properties;
   }

   /**
    * This method returns a {@code TransformerConfiguration} created from the JSON-formatted input {@code String}. The
    * input should contain these entries:
    *
    * <ul>
    * <li>class-name - a string value,
    * <li>properties - an object containing string key-value pairs.
    * </ul>
    *
    * @param jsonString json string
    * @return the {@code TransformerConfiguration} created from the JSON-formatted input {@code String}
    */
   public static TransformerConfiguration fromJSON(String jsonString) {
      JsonObject json = JsonLoader.readObject(new StringReader(jsonString));

      // name is the only required value
      if (!json.containsKey(CLASS_NAME)) {
         return null;
      }

      TransformerConfiguration result = new TransformerConfiguration(json.getString(CLASS_NAME));

      if (json.containsKey(PROPERTIES)) {
         Map<String, String> properties = new HashMap<>();
         for (Map.Entry<String, JsonValue> propEntry: json.getJsonObject(PROPERTIES).entrySet()) {
            if (propEntry.getValue().getValueType() == JsonValue.ValueType.STRING) {
               properties.put(propEntry.getKey(), ((JsonString) propEntry.getValue()).getString());
            } else {
               properties.put(propEntry.getKey(), propEntry.getValue().toString());
            }
         }
         result.setProperties(properties);
      }

      return result;
   }

   public JsonObjectBuilder createJsonObjectBuilder() {
      JsonObjectBuilder tcBuilder = JsonLoader.createObjectBuilder().add(TransformerConfiguration.CLASS_NAME, getClassName());
      if (getProperties() != null && !getProperties().isEmpty()) {
         JsonObjectBuilder propBuilder = JsonLoader.createObjectBuilder();
         getProperties().forEach(propBuilder::add);
         tcBuilder.add(TransformerConfiguration.PROPERTIES, propBuilder);
      }
      return tcBuilder;
   }

   /**
    * add properties to this {@code TransportConfiguration}
    *
    * @param properties the properties to set
    */
   public TransformerConfiguration setProperties(final Map<String, String> properties) {
      if (properties != null) {
         this.properties.putAll(properties);
      }
      return this;
   }

   @Override
   public int hashCode() {
      return Objects.hash(className, properties);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof TransformerConfiguration other)) {
         return false;
      }

      return Objects.equals(className, other.className) &&
             Objects.equals(properties, other.properties);
   }

   @Override
   public String toString() {
      return "TransformerConfiguration [" +
         "className=" + className +
         ", properties=" + properties + "]";
   }
}
