/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.utils;

public class IPV6Util {

   /**
    * It will enclose an IPV6 host with [], that we need before building URIs
    */
   public static String encloseHost(final String host) {
      // if the host contains a ':' then we know it's not IPv4
      if (host != null && host.contains(":")) {
         String hostToCheck = host;

         /*
          * strip off zone index since com.google.common.net.InetAddresses.isInetAddress() doesn't support it
          * see https://en.wikipedia.org/wiki/IPv6_address#Link-local_addresses_and_zone_indices for more info
          */
         if (host.contains("%")) {
            hostToCheck = host.substring(0, host.indexOf("%"));
         }

         if (InetAddresses.isInetAddress(hostToCheck)) {
            return "[" + host + "]";
         }
      }

      return host;
   }

   public static String stripBracketsAndZoneID(String host) {
      // if the host contains a ':' then we know it's not IPv4
      if (host != null && host.length() > 2 && host.contains(":")) {
         // Strip opening/closing brackets
         if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
         }

         if (host.contains("%")) {
            return host.substring(0, host.indexOf("%"));
         }
      }
      return host;
   }
}
