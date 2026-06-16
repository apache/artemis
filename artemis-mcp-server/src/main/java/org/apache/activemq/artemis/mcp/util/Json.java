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
package org.apache.activemq.artemis.mcp.util;

import java.util.Map;

public final class Json {

   private Json() {
   }

   public static String write(Object value) {
      StringBuilder sb = new StringBuilder();
      writeValue(sb, value);
      return sb.toString();
   }

   private static void writeValue(StringBuilder sb, Object value) {
      if (value == null) {
         sb.append("null");
      } else if (value instanceof String s) {
         writeString(sb, s);
      } else if (value instanceof Boolean || value instanceof Number) {
         sb.append(value);
      } else if (value instanceof Map<?, ?> map) {
         writeObject(sb, map);
      } else if (value instanceof Iterable<?> iterable) {
         writeArray(sb, iterable);
      } else if (value instanceof Object[] array) {
         writeArray(sb, java.util.Arrays.asList(array));
      } else {
         writeString(sb, value.toString());
      }
   }

   private static void writeObject(StringBuilder sb, Map<?, ?> map) {
      sb.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
         if (!first) {
            sb.append(',');
         }
         first = false;
         writeString(sb, String.valueOf(entry.getKey()));
         sb.append(':');
         writeValue(sb, entry.getValue());
      }
      sb.append('}');
   }

   private static void writeArray(StringBuilder sb, Iterable<?> iterable) {
      sb.append('[');
      boolean first = true;
      for (Object element : iterable) {
         if (!first) {
            sb.append(',');
         }
         first = false;
         writeValue(sb, element);
      }
      sb.append(']');
   }

   private static void writeString(StringBuilder sb, String s) {
      sb.append('"');
      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         switch (c) {
            case '"' -> sb.append("\\\"");
            case '\\' -> sb.append("\\\\");
            case '\n' -> sb.append("\\n");
            case '\r' -> sb.append("\\r");
            case '\t' -> sb.append("\\t");
            case '\b' -> sb.append("\\b");
            case '\f' -> sb.append("\\f");
            default -> {
               if (c < 0x20) {
                  sb.append(String.format("\\u%04x", (int) c));
               } else {
                  sb.append(c);
               }
            }
         }
      }
      sb.append('"');
   }
}
