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
package org.apache.activemq.artemis.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class StringUtil {

   /**
    * Convert a list of Strings into a single String
    *
    * @param strList the string list
    * @param delimit the delimiter used to separate each string entry in the list
    * @return the converted string
    */
   public static String joinStringList(Collection<String> strList, String delimit) {
      Iterator<String> entries = strList.iterator();
      StringBuilder builder = new StringBuilder();

      while (entries.hasNext()) {
         builder.append(entries.next());
         if (entries.hasNext()) {
            builder.append(delimit);
         }
      }
      return builder.toString();
   }

   /**
    * Convert a String into a list of String
    *
    * @param strList the String
    * @param delimit used to separate items within the string.
    * @return the string list
    */
   public static List<String> splitStringList(String strList, String delimit) {
      List<String> list = new ArrayList<>();
      if (strList != null && !strList.isEmpty()) {
         for (String string : strList.split(delimit)) {
            list.add(string.trim());
         }
      }
      return list;
   }

   public static String convertPascalCaseToKebabCase(String input) {
      if (input == null || input.isEmpty()) {
         return input;
      }
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < input.length(); i++) {
         char c = input.charAt(i);
         if (Character.isUpperCase(c)) {
            if (i > 0) {
               builder.append('-');
            }
            builder.append(Character.toLowerCase(c));
         } else {
            builder.append(c);
         }
      }
      return builder.toString();
   }
}
