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
package org.apache.activemq.artemis.cli.commands.address;

import org.apache.activemq.artemis.cli.commands.messages.ConnectionAbstract;
import picocli.CommandLine.Option;

public abstract class AddressAbstract extends ConnectionAbstract {

   @Option(names = "--name", description = "The address's name.")
   private String name;

   @Option(names = "--anycast", description = "Whether the address supports anycast queues.")
   private Boolean anycast;

   @Option(names = "--no-anycast", description = "Whether the address won't support anycast queues.")
   private Boolean noAnycast;

   @Option(names = "--multicast", description = "Whether the address supports multicast queues.")
   private Boolean multicast;

   @Option(names = "--no-multicast", description = "Whether the address won't support multicast queues.")
   private Boolean noMulticast;


   public AddressAbstract setName(String name) {
      this.name = name;
      return this;
   }

   public String getName(boolean requireInput) {
      if (name == null && requireInput) {
         name = input("--name", "What is the name of the address?", null);
      }
      return name;
   }

   public String getRoutingTypes(boolean useDefault) {
      StringBuilder buffer = new StringBuilder();

      if (isAnycast()) {
         buffer.append("ANYCAST");
      }

      if (isMulticast()) {
         if (isAnycast()) {
            buffer.append(",");
         }
         buffer.append("MULTICAST");
      }

      if (!isAnycast() && !isMulticast()) {
         if (useDefault) {
            return "MULTICAST"; // the default;
         } else {
            return null;
         }

      }

      return buffer.toString();
   }

   public boolean isAnycast() {
      if (noAnycast != null) {
         anycast = !noAnycast.booleanValue();
      }
      if (anycast == null) {
         anycast = inputBoolean("--anycast", "Will this address support anycast queues?", false);
      }
      return anycast;
   }

   public AddressAbstract setAnycast(boolean anycast) {
      this.anycast = anycast;
      return this;
   }

   public boolean isMulticast() {
      if (noMulticast != null) {
         multicast = !noMulticast.booleanValue();
      }
      if (multicast == null) {
         multicast = inputBoolean("--multicast", "Will this address support multicast queues?", true);
      }
      return multicast;
   }

   public AddressAbstract setMulticast(boolean multicast) {
      this.multicast = multicast;
      return this;
   }
}
