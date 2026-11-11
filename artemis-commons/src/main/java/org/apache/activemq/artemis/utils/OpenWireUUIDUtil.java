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

public class OpenWireUUIDUtil {

   /**
    * Validate an ID generated as in {@code org.apache.activemq.command.ActiveMQTempDestination}.
    *
    * An OpenWire UUID is a variable length string that follows the pattern {@code X-X-X-X:X:X} where:
    * <ul>
    * <li>The first group is a prefix of arbitrary content and length. The default prefix is {@code ID:} plus hostname.
    * <li>The second, third, and fourth groups consist of digits separated from each other by a {@code -} character.
    * <li>The fifth and sixth group consists of digits separated from each previous group by a {@code :} character.
    * </ul>
    * e.g.: {@code ID:localhost-45187-1745501083698-10000:1:10001}
    *
    * This algorithm moves backwards through the input {@code String} because there are no stable syntax elements at the
    * start due to the potentially arbitrary prefix.
    *
    * @see org.apache.activemq.command.ActiveMQTempDestination#ActiveMQTempDestination(String, long)
    */
   public static boolean isUUID(final String input) {
      if (input == null || input.isBlank()) {
         return false;
      }

      int lastDash = input.lastIndexOf('-');
      if (lastDash == -1 || input.lastIndexOf(':') == -1) {
         return false; // No dashes or colons found
      }

      // inspect the last three groups, i.e. X-X-X-(X:X:X), and ensure they're digits
      String[] end = input.substring(lastDash + 1).split(":");
      if (end.length != 3) {
         return false;
      }
      if (!isDigits(end[0]) || !isDigits(end[1]) || !isDigits(end[2])) {
         return false;
      }

      // inspect the preceeding two groups, i.e. X-(X-X)-X:X:X, and ensure they're digits
      String looper = input;
      String remainder;
      for (int i = 0; i < 2; i++) {
         remainder = looper.substring(0, lastDash);
         lastDash = remainder.lastIndexOf('-');
         if (!isDigits(remainder.substring(lastDash + 1))) {
            return false;
         }
         looper = remainder;
      }

      // ignore the first group, i.e. (X)-X-X-X:X:X, since it contains arbitrary content and length

      return true;
   }

   private static boolean isDigits(String string) {
      for (char c : string.toCharArray()) {
         if (c < '0' || c > '9') {
            return false;
         }
      }
      return true;
   }
}
