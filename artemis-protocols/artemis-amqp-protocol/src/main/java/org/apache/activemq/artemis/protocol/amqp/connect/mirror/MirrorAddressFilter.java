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
package org.apache.activemq.artemis.protocol.amqp.connect.mirror;

import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.SimpleString;

public class MirrorAddressFilter {

   private final SimpleString[] allowList;

   private final SimpleString[] denyList;

   public MirrorAddressFilter(String filter) {
      Set<SimpleString> allowList = new HashSet<>();
      Set<SimpleString> denyList = new HashSet<>();

      if (filter != null && !filter.isEmpty()) {
         String[] parts = filter.split(",");
         for (String part : parts) {
            if (!"".equals(part) && !"!".equals(part)) {
               if (part.startsWith("!")) {
                  denyList.add(SimpleString.of(part.substring(1)));
               } else {
                  allowList.add(SimpleString.of(part));
               }
            }
         }
      }

      this.allowList = allowList.toArray(new SimpleString[]{});
      this.denyList = denyList.toArray(new SimpleString[]{});
   }

   public boolean match(SimpleString checkAddress) {
      if (denyList.length > 0) {
         for (SimpleString pattern : denyList) {
            if (checkAddress.startsWith(pattern)) {
               return false;
            }
         }
      }

      if (allowList.length > 0) {
         for (SimpleString pattern : allowList) {
            if (checkAddress.startsWith(pattern)) {
               return true;
            }
         }
         return false;
      }
      return true;
   }
}
