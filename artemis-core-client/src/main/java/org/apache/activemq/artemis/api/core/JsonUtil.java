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
package org.apache.activemq.artemis.api.core;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonArrayBuilder;
import org.apache.activemq.artemis.json.JsonNumber;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.json.JsonString;
import org.apache.activemq.artemis.json.JsonValue;
import org.apache.activemq.artemis.utils.Base64;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.ObjectInputStreamWithClassLoader;
import org.apache.activemq.artemis.utils.StringEscapeUtils;

public final class JsonUtil {

   public static JsonArray toJSONArray(final Object[] array) throws Exception {
      JsonArrayBuilder jsonArray = JsonLoader.createArrayBuilder();

      for (Object parameter : array) {
         addToArray(parameter, jsonArray);
      }
      return jsonArray.build();
   }

   public static Object[] fromJsonArray(final JsonArray jsonArray) throws Exception {
      Object[] array = new Object[jsonArray.size()];

      for (int i = 0; i < jsonArray.size(); i++) {
         Object val = jsonArray.get(i);

         if (val instanceof JsonArray jsonArrayValue) {
            Object[] inner = fromJsonArray(jsonArrayValue);

            array[i] = inner;
         } else if (val instanceof JsonObject jsonObject) {

            Map<String, Object> map = new HashMap<>();

            Set<String> keys = jsonObject.keySet();

            for (String key : keys) {
               Object innerVal = jsonObject.get(key);

               if (innerVal instanceof JsonArray jsonArrayValue) {
                  innerVal = fromJsonArray(jsonArrayValue);
               } else if (innerVal instanceof JsonString jsonString) {
                  innerVal = jsonString.getString();
               } else if (innerVal == JsonValue.FALSE) {
                  innerVal = Boolean.FALSE;
               } else if (innerVal == JsonValue.TRUE) {
                  innerVal = Boolean.TRUE;
               } else if (innerVal instanceof JsonNumber jsonNumber) {
                  if (jsonNumber.isIntegral()) {
                     innerVal = jsonNumber.longValue();
                  } else {
                     innerVal = jsonNumber.doubleValue();
                  }
               } else if (innerVal instanceof JsonObject innerJsonObject) {
                  Map<String, Object> innerMap = new HashMap<>();
                  Set<String> innerKeys = innerJsonObject.keySet();
                  for (String k : innerKeys) {
                     innerMap.put(k, innerJsonObject.get(k));
                  }
                  innerVal = innerMap;
               }
               if (CompositeData.class.getName().equals(key)) {
                  Object[] data = (Object[]) innerVal;
                  CompositeData[] cds = new CompositeData[data.length];
                  for (int i1 = 0; i1 < data.length; i1++) {
                     String dataConverted = convertJsonValue(data[i1], String.class).toString();
                     try (ObjectInputStreamWithClassLoader ois = new ObjectInputStreamWithClassLoader(new ByteArrayInputStream(Base64.decode(dataConverted)))) {
                        ois.setAllowList("java.util,java.lang,javax.management");
                        cds[i1] = (CompositeDataSupport) ois.readObject();
                     }
                  }
                  innerVal = cds;
               }

               map.put(key, innerVal);
            }

            array[i] = map;
         } else if (val instanceof JsonString jsonString) {
            array[i] = jsonString.getString();
         } else if (val == JsonValue.FALSE) {
            array[i] = Boolean.FALSE;
         } else if (val == JsonValue.TRUE) {
            array[i] = Boolean.TRUE;
         } else if (val instanceof JsonNumber jsonNumber) {
            if (jsonNumber.isIntegral()) {
               array[i] = jsonNumber.longValue();
            } else {
               array[i] = jsonNumber.doubleValue();
            }
         } else {
            if (val == JsonValue.NULL) {
               array[i] = null;
            } else {
               array[i] = val;
            }
         }
      }

      return array;
   }

   public static JsonValue nullSafe(String input) {
      return new NullableJsonString(input);
   }

   public static void addToObject(final String key, final Object param, final JsonObjectBuilder jsonObjectBuilder) {
      if (param instanceof Integer integer) {
         jsonObjectBuilder.add(key, integer);
      } else if (param instanceof Long longValue) {
         jsonObjectBuilder.add(key, longValue);
      } else if (param instanceof Double doubleValue) {
         jsonObjectBuilder.add(key, doubleValue);
      } else if (param instanceof String string) {
         jsonObjectBuilder.add(key, string);
      } else if (param instanceof Boolean booleanValue) {
         jsonObjectBuilder.add(key, booleanValue);
      } else if (param instanceof Map map) {
         jsonObjectBuilder.add(key, toJsonObject(map));
      } else if (param instanceof Short shortValue) {
         jsonObjectBuilder.add(key, shortValue);
      } else if (param instanceof Byte byteValue) {
         jsonObjectBuilder.add(key, byteValue.shortValue());
      } else if (param instanceof Number number) {
         jsonObjectBuilder.add(key, number.doubleValue());
      } else if (param instanceof SimpleString) {
         jsonObjectBuilder.add(key, param.toString());
      } else if (param == null) {
         jsonObjectBuilder.addNull(key);
      } else if (param instanceof byte[] bytes) {
         JsonArrayBuilder byteArrayObject = toJsonArrayBuilder(bytes);
         jsonObjectBuilder.add(key, byteArrayObject);
      } else if (param instanceof Object[] objects) {
         final JsonArrayBuilder objectArrayBuilder = JsonLoader.createArrayBuilder();
         for (Object parameter : objects) {
            addToArray(parameter, objectArrayBuilder);
         }
         jsonObjectBuilder.add(key, objectArrayBuilder);
      } else if (param instanceof JsonValue jsonValue) {
         jsonObjectBuilder.add(key, jsonValue);
      } else {
         jsonObjectBuilder.add(key, param.toString());
      }
   }

   public static void addToArray(final Object param, final JsonArrayBuilder jsonArrayBuilder) {
      if (param instanceof Integer integer) {
         jsonArrayBuilder.add(integer);
      } else if (param instanceof Long longValue) {
         jsonArrayBuilder.add(longValue);
      } else if (param instanceof Double doubleValue) {
         jsonArrayBuilder.add(doubleValue);
      } else if (param instanceof String string) {
         jsonArrayBuilder.add(string);
      } else if (param instanceof Boolean booleanValue) {
         jsonArrayBuilder.add(booleanValue);
      } else if (param instanceof Map map) {
         jsonArrayBuilder.add(toJsonObject(map));
      } else if (param instanceof Short shortValue) {
         jsonArrayBuilder.add(shortValue);
      } else if (param instanceof Byte byteValue) {
         jsonArrayBuilder.add(byteValue.shortValue());
      } else if (param instanceof Number number) {
         jsonArrayBuilder.add(number.doubleValue());
      } else if (param == null) {
         jsonArrayBuilder.addNull();
      } else if (param instanceof byte[] bytes) {
         JsonArrayBuilder byteArrayObject = toJsonArrayBuilder(bytes);
         jsonArrayBuilder.add(byteArrayObject);
      } else if (param instanceof CompositeData[] compositeData) {
         JsonArrayBuilder innerJsonArray = JsonLoader.createArrayBuilder();
         for (Object data : compositeData) {
            String s = Base64.encodeObject((CompositeDataSupport) data);
            innerJsonArray.add(s);
         }
         JsonObjectBuilder jsonObject = JsonLoader.createObjectBuilder();
         jsonObject.add(CompositeData.class.getName(), innerJsonArray);
         jsonArrayBuilder.add(jsonObject);
      } else if (param instanceof Object[] objects) {
         JsonArrayBuilder objectArrayBuilder = JsonLoader.createArrayBuilder();
         for (Object parameter : objects) {
            addToArray(parameter, objectArrayBuilder);
         }
         jsonArrayBuilder.add(objectArrayBuilder);
      } else if (param instanceof JsonValue jsonValue) {
         jsonArrayBuilder.add(jsonValue);
      } else {
         jsonArrayBuilder.add(param.toString());
      }
   }

   public static JsonArray toJsonArray(List<String> strings) {
      JsonArrayBuilder array = JsonLoader.createArrayBuilder();
      if (strings != null) {
         for (String connector : strings) {
            array.add(connector);
         }
      }
      return array.build();
   }

   public static JsonObject toJsonObject(Map<String, ?> map) {
      JsonObjectBuilder jsonObjectBuilder = JsonLoader.createObjectBuilder();
      if (map != null) {
         for (Map.Entry<String, ?> entry : map.entrySet()) {
            addToObject(String.valueOf(entry.getKey()), entry.getValue(), jsonObjectBuilder);
         }
      }
      return jsonObjectBuilder.build();
   }

   public static JsonArrayBuilder toJsonArrayBuilder(byte[] byteArray) {
      JsonArrayBuilder jsonArrayBuilder = JsonLoader.createArrayBuilder();
      if (byteArray != null) {
         for (int i = 0; i < byteArray.length; i++) {
            jsonArrayBuilder.add(((Byte) byteArray[i]).shortValue());
         }
      }
      return jsonArrayBuilder;
   }

   public static JsonArray readJsonArray(String jsonString) {
      return JsonLoader.readArray(new StringReader(jsonString));
   }

   public static JsonObject readJsonObject(String jsonString) {
      return JsonLoader.readObject(new StringReader(jsonString));
   }

   public static Map<String, String> readJsonProperties(String jsonString) {
      Map<String, String> properties = new HashMap<>();
      if (jsonString != null) {
         JsonUtil.readJsonObject(jsonString).entrySet().forEach(e -> properties.put(e.getKey(), e.getValue().toString()));
      }
      return properties;
   }

   public static Object convertJsonValue(Object jsonValue, Class desiredType) {
      if (jsonValue instanceof JsonNumber number) {

         if (desiredType == null || desiredType == Long.class || desiredType == Long.TYPE) {
            return number.longValue();
         } else if (desiredType == Integer.class || desiredType == Integer.TYPE) {
            return number.intValue();
         } else if (desiredType == Double.class || desiredType == Double.TYPE) {
            return number.doubleValue();
         } else {
            return number.longValue();
         }
      } else if (jsonValue instanceof JsonString jsonString) {
         return jsonString.getString();
      } else if (jsonValue instanceof JsonValue) {
         if (jsonValue == JsonValue.TRUE) {
            return true;
         } else if (jsonValue == JsonValue.FALSE) {
            return false;
         } else {
            return jsonValue.toString();
         }
      } else if (jsonValue instanceof Number jsonNumber) {
         if (desiredType == Integer.TYPE || desiredType == Integer.class) {
            return jsonNumber.intValue();
         } else if (desiredType == Long.TYPE || desiredType == Long.class) {
            return jsonNumber.longValue();
         } else if (desiredType == Double.TYPE || desiredType == Double.class) {
            return jsonNumber.doubleValue();
         } else if (desiredType == Short.TYPE || desiredType == Short.class) {
            return jsonNumber.shortValue();
         } else {
            return jsonValue;
         }
      } else if (jsonValue instanceof Object[] objects) {
         Object[] result;
         if (desiredType != null) {
            result = (Object[]) Array.newInstance(desiredType, objects.length);
         } else {
            result = objects;
         }
         for (int i = 0; i < objects.length; i++) {
            result[i] = convertJsonValue(objects[i], desiredType);
         }
         return result;
      } else {
         return jsonValue;
      }
   }

   private JsonUtil() {
   }

   public static String truncateString(final String str, final int valueSizeLimit) {
      if (valueSizeLimit >= 0 && str.length() > valueSizeLimit) {
         return new StringBuilder(valueSizeLimit + 32).append(str.substring(0, valueSizeLimit)).append(", + ").append(str.length() - valueSizeLimit).append(" more").toString();
      } else {
         return str;
      }
   }

   public static Object truncate(final Object value, final int valueSizeLimit) {
      if (value == null) {
         return "";
      }
      Object result = value;
      if (valueSizeLimit >= 0) {
         if (String.class.equals(value.getClass())) {
            result = truncateString((String)value, valueSizeLimit);
         } else if (value.getClass().isArray()) {
            if (byte[].class.equals(value.getClass())) {
               if (((byte[]) value).length > valueSizeLimit) {
                  result = Arrays.copyOfRange((byte[]) value, 0, valueSizeLimit);
               }
            } else if (char[].class.equals(value.getClass())) {
               if (((char[]) value).length > valueSizeLimit) {
                  result = Arrays.copyOfRange((char[]) value, 0, valueSizeLimit);
               }
            }
         }
      }
      return result;
   }

   public static JsonObject mergeAndUpdate(JsonObject source, JsonObject update) {
      // all immutable so we need to create new merged instance
      JsonObjectBuilder jsonObjectBuilder = JsonLoader.createObjectBuilder();
      for (Map.Entry<String, JsonValue> entry : source.entrySet()) {
         jsonObjectBuilder.add(entry.getKey(), entry.getValue());
      }
      // apply any updates
      for (String updateKey : update.keySet()) {
         JsonValue updatedValue = update.get(updateKey);
         if (updatedValue != null) {
            if (!source.containsKey(updateKey)) {
               jsonObjectBuilder.add(updateKey, updatedValue);
            } else {
               // recursively merge into new value
               if (updatedValue.getValueType() == JsonValue.ValueType.OBJECT) {
                  jsonObjectBuilder.add(updateKey, mergeAndUpdate(source.getJsonObject(updateKey), updatedValue.asJsonObject()));

               } else if (updatedValue.getValueType() == JsonValue.ValueType.ARRAY) {
                  JsonArrayBuilder jsonArrayBuilder = JsonLoader.createArrayBuilder();

                  // update wins
                  JsonArray updatedArrayValue = update.getJsonArray(updateKey);
                  JsonArray sourceArrayValue = source.getJsonArray(updateKey);

                  for (int i = 0; i < updatedArrayValue.size(); i++) {
                     if (i < sourceArrayValue.size()) {
                        JsonValue element = updatedArrayValue.get(i);
                        if (element.getValueType() == JsonValue.ValueType.OBJECT) {
                           jsonArrayBuilder.add(mergeAndUpdate(sourceArrayValue.getJsonObject(i), updatedArrayValue.getJsonObject(i)));
                        } else {
                           // take the update
                           jsonArrayBuilder.add(element);
                        }
                     } else {
                        jsonArrayBuilder.add(updatedArrayValue.get(i));
                     }
                  }
                  jsonObjectBuilder.add(updateKey, jsonArrayBuilder.build());
               } else {
                  // update wins!
                  jsonObjectBuilder.add(updateKey, updatedValue);
               }
            }
         }
      }

      return jsonObjectBuilder.build();
   }

   // component path, may contain x/y/z as a pointer to a nested object
   public static JsonObjectBuilder objectBuilderWithValueAtPath(String componentPath, JsonValue componentStatus) {
      JsonObjectBuilder jsonObjectBuilder = JsonLoader.createObjectBuilder();
      String[] nestedComponents = componentPath.split("/", 0);
      // may need to nest this status in objects
      for (int i = nestedComponents.length - 1; i > 0; i--) {
         JsonObjectBuilder nestedBuilder = JsonLoader.createObjectBuilder();
         nestedBuilder.add(nestedComponents[i], componentStatus);
         componentStatus = nestedBuilder.build();
      }
      jsonObjectBuilder.add(nestedComponents[0], componentStatus);
      return jsonObjectBuilder;
   }


   private static class NullableJsonString implements JsonValue, JsonString {

      private final String value;
      private String escape;

      NullableJsonString(String value) {
         if (value == null || value.isEmpty()) {
            this.value = null;
         } else {
            this.value = value;
         }
      }

      @Override
      public ValueType getValueType() {
         return value == null ? ValueType.NULL : ValueType.STRING;
      }

      @Override
      public String getString() {
         return this.value;
      }

      @Override
      public CharSequence getChars() {
         return getString();
      }

      @Override
      public String toString() {
         if (this.value == null) {
            return null;
         }
         String s = this.escape;
         if (s == null) {
            s = '\"' + StringEscapeUtils.escapeString(this.value) + '\"';
            this.escape = s;
         }
         return s;
      }
   }

   /**
    * Converts a JSON array from the specified key in a JsonObject into a comma-separated string. If the key does not
    * exist or if its value is not a JSON array, then an empty string is returned.
    *
    * @param jsonObject the JsonObject from which to retrieve the array
    * @param key        the key associated with the JSON array in the JsonObject
    * @return a comma-separated string representation of the array's elements, or an empty string if the key does not
    * exist or the value is not an array
    */
   public static String arrayToString(JsonObject jsonObject, String key) {
      JsonValue value = jsonObject.get(key);
      if (value == null || value.getValueType() != JsonValue.ValueType.ARRAY) {
         return "";
      }
      return jsonObject.getJsonArray(key).stream().map((s) -> ((JsonString) s).getString()).collect(Collectors.joining(", "));
   }
}
