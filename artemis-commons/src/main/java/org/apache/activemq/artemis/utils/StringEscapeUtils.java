/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.activemq.artemis.utils;

public abstract class StringEscapeUtils {

   /**
    * Adapted from commons lang StringEscapeUtils, escapes a string
    *
    * @return an escaped version of the input string
    */
   public static String escapeString(String str) {
      if (str == null) {
         return str;
      }
      int sz = str.length();
      StringBuilder stringBuilder = new StringBuilder(str.length());
      for (int i = 0; i < sz; i++) {
         char ch = str.charAt(i);

         // handle unicode
         if (ch > 0xfff) {
            stringBuilder.append("\\u").append(hex(ch));
         } else if (ch > 0xff) {
            stringBuilder.append("\\u0").append(hex(ch));
         } else if (ch > 0x7f) {
            stringBuilder.append("\\u00").append(hex(ch));
         } else if (ch < 32) {
            switch (ch) {
               case '\b':
                  stringBuilder.append('\\').append('b');
                  break;
               case '\n':
                  stringBuilder.append('\\').append('n');
                  break;
               case '\t':
                  stringBuilder.append('\\').append('t');
                  break;
               case '\f':
                  stringBuilder.append('\\').append('f');
                  break;
               case '\r':
                  stringBuilder.append('\\').append('r');
                  break;
               default:
                  if (ch > 0xf) {
                     stringBuilder.append("\\u00").append(hex(ch));
                  } else {
                     stringBuilder.append("\\u000").append(hex(ch));
                  }
                  break;
            }
         } else {
            switch (ch) {
               case '\'':
                  stringBuilder.append('\\').append('\'');
                  break;
               case '"':
                  stringBuilder.append('\\').append('"');
                  break;
               case '\\':
                  stringBuilder.append('\\').append('\\');
                  break;
               case '/':
                  stringBuilder.append('\\').append('/');
                  break;
               default:
                  stringBuilder.append(ch);
                  break;
            }
         }
      }
      return stringBuilder.toString();
   }

   /**
    * <p>Returns an upper case hexadecimal {@code String} for the given
    * character.</p>
    *
    * @param ch The character to convert.
    * @return An upper case hexadecimal {@code String}
    */
   private static String hex(char ch) {
      return Integer.toHexString(ch).toUpperCase();
   }
}
