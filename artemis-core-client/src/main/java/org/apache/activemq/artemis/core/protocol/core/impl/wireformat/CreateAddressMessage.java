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
package org.apache.activemq.artemis.core.protocol.core.impl.wireformat;

import java.util.EnumSet;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.api.core.RoutingType;

public class CreateAddressMessage extends PacketImpl {

   private SimpleString address;

   private EnumSet<RoutingType> routingTypes;

   private boolean autoCreated;

   private boolean requiresResponse;

   public CreateAddressMessage(final SimpleString address,
                               EnumSet<RoutingType> routingTypes,
                               final boolean autoCreated,
                               final boolean requiresResponse) {
      this();

      this.address = address;
      this.routingTypes = routingTypes;
      this.autoCreated = autoCreated;
      this.requiresResponse = requiresResponse;
   }

   public CreateAddressMessage() {
      super(CREATE_ADDRESS);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", address=" + address);
      sb.append(", routingTypes=" + routingTypes);
      sb.append(", autoCreated=" + autoCreated);
      return sb.toString();
   }

   public SimpleString getAddress() {
      return address;
   }

   @Override
   public boolean isRequiresResponse() {
      return requiresResponse;
   }

   public boolean isAutoCreated() {
      return autoCreated;
   }

   public void setAddress(SimpleString address) {
      this.address = address;
   }

   public EnumSet<RoutingType> getRoutingTypes() {
      return routingTypes;
   }

   public void setRoutingTypes(EnumSet<RoutingType> routingTypes) {
      this.routingTypes = routingTypes;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeSimpleString(address);
      buffer.writeInt(routingTypes.size());
      for (RoutingType routingType : routingTypes) {
         buffer.writeByte(routingType.getType());
      }
      buffer.writeBoolean(requiresResponse);
      buffer.writeBoolean(autoCreated);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      address = buffer.readSimpleString();
      int routingTypeSetSize = buffer.readInt();
      routingTypes = EnumSet.noneOf(RoutingType.class);
      for (int i = 0; i < routingTypeSetSize; i++) {
         routingTypes.add(RoutingType.getType(buffer.readByte()));
      }
      requiresResponse = buffer.readBoolean();
      autoCreated = buffer.readBoolean();
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), address, routingTypes, autoCreated, requiresResponse);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof CreateAddressMessage other)) {
         return false;
      }

      return Objects.equals(address, other.address) &&
             Objects.equals(routingTypes, other.routingTypes) &&
             autoCreated == other.autoCreated &&
             requiresResponse == other.requiresResponse;
   }
}
