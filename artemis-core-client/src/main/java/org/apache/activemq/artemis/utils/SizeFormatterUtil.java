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

public class SizeFormatterUtil {


   private static long oneKiB = 1024;

   private static long oneMiB = SizeFormatterUtil.oneKiB * 1024;

   private static long oneGiB = SizeFormatterUtil.oneMiB * 1024;

   public static String sizeof(final long size) {
      double s = Long.valueOf(size).doubleValue();
      String suffix = "B";
      if (s > SizeFormatterUtil.oneGiB) {
         s /= SizeFormatterUtil.oneGiB;
         suffix = "GiB";
      } else if (s > SizeFormatterUtil.oneMiB) {
         s /= SizeFormatterUtil.oneMiB;
         suffix = "MiB";
      } else if (s > SizeFormatterUtil.oneKiB) {
         s /= SizeFormatterUtil.oneKiB;
         suffix = "KiB";
      }
      return String.format("%.2f %s", s, suffix);
   }
}
