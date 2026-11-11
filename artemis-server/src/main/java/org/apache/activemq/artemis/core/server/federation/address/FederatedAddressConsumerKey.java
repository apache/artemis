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
package org.apache.activemq.artemis.core.server.federation.address;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.federation.FederatedConsumerKey;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.commons.lang3.text.StrSubstitutor;

public class FederatedAddressConsumerKey implements FederatedConsumerKey {

   private final SimpleString federation;
   private final SimpleString upstream;
   private final SimpleString address;
   private final SimpleString queueNameFormat;
   private final RoutingType routingType;
   private final SimpleString queueFilterString;


   private SimpleString fqqn;
   private SimpleString queueName;

   FederatedAddressConsumerKey(SimpleString federation, SimpleString upstream, SimpleString address, RoutingType routingType, SimpleString queueNameFormat, SimpleString queueFilterString) {
      this.federation = federation;
      this.upstream = upstream;
      this.address = address;
      this.routingType = routingType;
      this.queueNameFormat = queueNameFormat;
      this.queueFilterString = queueFilterString;
   }

   @Override
   public SimpleString getQueueName() {
      if (queueName == null) {
         Map<String, String> data = new HashMap<>();
         data.put("address", address.toString());
         data.put("routeType", routingType.name().toLowerCase());
         data.put("upstream", upstream.toString());
         data.put("federation", federation.toString());
         queueName = SimpleString.of(StrSubstitutor.replace(queueNameFormat, data));
      }
      return queueName;
   }

   @Override
   public SimpleString getQueueFilterString() {
      return queueFilterString;
   }

   @Override
   public int getPriority() {
      return 0;
   }

   @Override
   public SimpleString getAddress() {
      return address;
   }

   @Override
   public SimpleString getFqqn() {
      if (fqqn == null) {
         fqqn = CompositeAddress.toFullyQualified(getAddress(), getQueueName());
      }
      return fqqn;
   }

   @Override
   public RoutingType getRoutingType() {
      return routingType;
   }

   @Override
   public SimpleString getFilterString() {
      return null;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof FederatedAddressConsumerKey other)) {
         return false;
      }

      return Objects.equals(address, other.address) &&
             Objects.equals(queueNameFormat, other.queueNameFormat) &&
             Objects.equals(routingType, other.routingType) &&
             Objects.equals(queueFilterString, other.queueFilterString);
   }

   @Override
   public int hashCode() {
      return Objects.hash(address, queueNameFormat, routingType, queueFilterString);
   }
}
