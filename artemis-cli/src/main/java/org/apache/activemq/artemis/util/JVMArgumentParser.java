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

package org.apache.activemq.artemis.util;

import java.util.Map;

public class JVMArgumentParser {

   public static void parseOriginalArgs(String prefix, String endOfLine, String originalLine, Map<String, String> keepPrefixAlternates, String[] keepingPrefixes, Map<String, String> originalArgs) {
      originalLine = originalLine.trim();
      String line = originalLine.substring(prefix.length(), originalLine.length() - endOfLine.length());
      String[] split = line.split(" ");
      for (String s : split) {
         for (String k : keepingPrefixes) {
            if (s.startsWith(k)) {
               originalArgs.put(k, s);
            } else if (keepPrefixAlternates.containsKey(k)) {
               String oldKey = keepPrefixAlternates.get(k);
               if (s.startsWith(oldKey)) {
                  String renamed = s.replaceFirst(oldKey, k);
                  originalArgs.put(k, renamed);
               }
            }
         }
      }
   }

   public static String parseNewLine(String prefix, String endOfLine, String newLine, String[] keepingPrefixes, Map<String, String> originalArgs) {
      String spacesBeginning = newLine.substring(0, newLine.indexOf(prefix));
      newLine = newLine.trim();
      StringBuilder output = new StringBuilder();
      String line = newLine.substring(prefix.length(), newLine.length() - endOfLine.length());
      String[] split = line.split(" ");
      for (String s : split) {
         for (String k : keepingPrefixes) {
            if (s.startsWith(k)) {
               String value = originalArgs.get(k);
               if (value != null) {
                  s = value;
               }
               break;
            }
         }
         output.append(s);
         output.append(" ");
      }

      return spacesBeginning + prefix + output + endOfLine;
   }

}
