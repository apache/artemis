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
package org.apache.activemq.artemis.cli.commands.queue;

import org.apache.activemq.artemis.cli.commands.messages.ConnectionAbstract;
import picocli.CommandLine.Option;

public class QueueAbstract extends ConnectionAbstract {

   @Option(names = "--name", description = "The queue's name.")
   private String name;

   @Option(names = "--filter", description = "The queue's filter string. Default: null.")
   private String filter = null;

   @Option(names = "--address", description = "The queue's address. Default: queue's name.")
   private String address;

   @Option(names = "--durable", description = "The queue is durable. Default: input.")
   private Boolean durable;

   @Option(names = "--no-durable", description = "The queue is not durable. Default: input.")
   private Boolean noDurable;

   @Option(names = "--purge-on-no-consumers", description = "Delete the contents of this queue when its last consumer disconnects. Default: input.")
   private Boolean purgeOnNoConsumers;

   @Option(names = "--preserve-on-no-consumers", description = "Preserve the contents of this queue when its last consumer disconnects. Default: input.")
   private Boolean preserveOnNoConsumers;

   @Option(names = "--max-consumers", description = "The maximum number of concurrent consumers allowed on this queue. Default: no limit.")
   private Integer maxConsumers;

   @Option(names = "--auto-create-address", description = "Automatically create the address (if it doesn't exist) with default values. Default: input.")
   private Boolean autoCreateAddress;

   @Option(names = "--anycast", description = "Create an anycast queue. Default: input.")
   private Boolean anycast;

   @Option(names = "--multicast", description = "Create a multicast queue. Default: input.")
   private Boolean multicast;

   public void setFilter(String filter) {
      this.filter = filter;
   }

   public String getFilter() {
      return filter;
   }

   public String getAddress(boolean requireInput) {
      // just to force asking the queue name first
      String queueName = getName();

      if (requireInput && (address == null || "".equals(address.trim()))) {
         address = input("--address", "What is the name of the address? <Enter for " + queueName + ">", null, true);
      }

      if (address == null || "".equals(address.trim())) {
         // if still null, it will use the queueName
         address = queueName;
      }
      return address;
   }

   public boolean isDurable() {
      if (durable == null) {
         if (noDurable != null) {
            durable = !noDurable.booleanValue();
         }
      }

      if (durable == null) {
         durable = inputBoolean("--durable", "Is this a durable queue?", false);
      }

      return durable;
   }

   public QueueAbstract setDurable(boolean durable) {
      this.durable = durable;
      return this;
   }

   public boolean getPreserveOnNoConsumers() {
      return preserveOnNoConsumers;
   }

   public QueueAbstract setPreserveOnNoConsumers(boolean preserveOnNoConsumers) {
      this.preserveOnNoConsumers = preserveOnNoConsumers;
      return this;
   }

   public Integer getMaxConsumers(Integer defaultValue) {
      if (maxConsumers == null) {
         return defaultValue;
      }
      return maxConsumers;
   }

   public boolean isAutoCreateAddress() {
      if (autoCreateAddress == null) {
         autoCreateAddress = inputBoolean("--auto-create-address", "Automatically create the address if it doesn't exist?", false);
      }
      return autoCreateAddress;
   }

   public QueueAbstract setAutoCreateAddress(boolean autoCreateAddress) {
      this.autoCreateAddress = autoCreateAddress;
      return this;
   }

   public boolean isAnycast() {
      if (anycast == null) {
         if (multicast != null) {
            // if multicast is not null, it should be the opposite
            anycast = !multicast.booleanValue();
         }

         if (anycast == null) {
            // if it is still null
            anycast = inputBoolean("--anycast", "Is this an anycast queue?", false);
         }
      }
      return anycast;
   }

   public QueueAbstract setAnycast(boolean anycast) {
      this.anycast = anycast;
      return this;
   }

   public boolean isMulticast() {
      if (multicast == null) {
         if (anycast != null) {
            // if anycast is not null, it should be the opposite
            multicast = !anycast.booleanValue();
         }

         if (multicast == null) {
            // if it is still null
            multicast = inputBoolean("--multicast", "Is this a multicast queue?", false);
         }
      }
      return multicast;
   }

   public QueueAbstract setMulticast(boolean multicast) {
      this.multicast = multicast;
      return this;
   }

   public Boolean isPurgeOnNoConsumers() {
      return isPurgeOnNoConsumers(false);
   }

   public Boolean isPurgeOnNoConsumers(boolean useInput) {

      Boolean value = null;

      if (purgeOnNoConsumers != null) {
         value = purgeOnNoConsumers.booleanValue();
      } else if (preserveOnNoConsumers != null) {
         value = !preserveOnNoConsumers.booleanValue();
      }


      if (value == null && useInput) {
         value = inputBoolean("--purge-on-no-consumers", "Purge the contents of the queue once the last consumer disconnects?", false);
      }

      if (value == null) {
         // return null if still null
         return null;
      }

      purgeOnNoConsumers = value.booleanValue();
      preserveOnNoConsumers = !value.booleanValue();

      return value;
   }

   public void setMaxConsumers(int maxConsumers) {
      this.maxConsumers = maxConsumers;
   }

   public void setPurgeOnNoConsumers(boolean purgeOnNoConsumers) {
      this.purgeOnNoConsumers = purgeOnNoConsumers;
   }

   public QueueAbstract setAddress(String address) {
      this.address = address;
      return this;
   }

   public QueueAbstract setName(String name) {
      this.name = name;
      return this;
   }

   public String getName() {
      if (name == null) {
         name = input("--name", "What is the name of the queue?", "");
      }

      return name;
   }

   public String getRoutingType() {
      if (isAnycast() && isMulticast()) {
         throw new IllegalArgumentException("--multicast and --anycast are exclusive options for a queue");
      }

      if (isMulticast()) {
         return "MULTICAST";
      } else if (anycast) {
         return "ANYCAST";
      } else {
         return null;
      }
   }


}
