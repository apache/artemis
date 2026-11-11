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
package org.apache.activemq.artemis.core.server;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;

import java.util.Set;


public class AddressQueryResult {
   private final SimpleString name;
   private final Set<RoutingType> routingTypes;
   private final long id;
   private final boolean autoCreated;
   private final boolean exists;
   private final boolean autoCreateAddresses;
   private final boolean defaultPurgeOnNoConsumers;
   private final int defaultMaxConsumers;

   public AddressQueryResult(SimpleString name, Set<RoutingType> routingTypes, long id, boolean autoCreated, boolean exists, boolean autoCreateAddresses, boolean defaultPurgeOnNoConsumers, int defaultMaxConsumers) {

      this.name = name;
      this.routingTypes = routingTypes;
      this.id = id;

      this.autoCreated = autoCreated;
      this.exists = exists;
      this.autoCreateAddresses = autoCreateAddresses;
      this.defaultPurgeOnNoConsumers = defaultPurgeOnNoConsumers;
      this.defaultMaxConsumers = defaultMaxConsumers;
   }

   public SimpleString getName() {
      return name;
   }

   public Set<RoutingType> getRoutingTypes() {
      return routingTypes;
   }

   public long getId() {
      return id;
   }

   public boolean isAutoCreated() {
      return autoCreated;
   }

   public boolean isExists() {
      return exists;
   }

   public boolean isAutoCreateAddresses() {
      return autoCreateAddresses;
   }

   public boolean isDefaultPurgeOnNoConsumers() {
      return defaultPurgeOnNoConsumers;
   }

   public int getDefaultMaxConsumers() {
      return defaultMaxConsumers;
   }
}
