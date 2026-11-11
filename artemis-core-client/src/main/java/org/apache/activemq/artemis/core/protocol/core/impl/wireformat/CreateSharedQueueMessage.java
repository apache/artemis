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

import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;

public class CreateSharedQueueMessage extends PacketImpl {

   protected SimpleString address;

   protected SimpleString queueName;

   protected SimpleString filterString;

   protected boolean durable;

   protected boolean requiresResponse;

   public CreateSharedQueueMessage(final SimpleString address,
                                   final SimpleString queueName,
                                   final SimpleString filterString,
                                   final boolean durable,
                                   final boolean requiresResponse) {
      this();

      this.address = address;
      this.queueName = queueName;
      this.filterString = filterString;
      this.durable = durable;
      this.requiresResponse = requiresResponse;
   }

   public CreateSharedQueueMessage() {
      this(CREATE_SHARED_QUEUE);
   }

   public CreateSharedQueueMessage(byte packetType) {
      super(packetType);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", address=" + address);
      sb.append(", queueName=" + queueName);
      sb.append(", filterString=" + filterString);
      sb.append(", durable=" + durable);
      sb.append(", requiresResponse=" + requiresResponse);
      return sb.toString();
   }

   public SimpleString getAddress() {
      return address;
   }

   public SimpleString getQueueName() {
      return queueName;
   }

   public SimpleString getFilterString() {
      return filterString;
   }

   @Override
   public boolean isRequiresResponse() {
      return requiresResponse;
   }

   public void setAddress(SimpleString address) {
      this.address = address;
   }

   public void setQueueName(SimpleString queueName) {
      this.queueName = queueName;
   }

   public void setFilterString(SimpleString filterString) {
      this.filterString = filterString;
   }

   public boolean isDurable() {
      return durable;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeSimpleString(address);
      buffer.writeSimpleString(queueName);
      buffer.writeNullableSimpleString(filterString);
      buffer.writeBoolean(durable);
      buffer.writeBoolean(requiresResponse);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      address = buffer.readSimpleString();
      queueName = buffer.readSimpleString();
      filterString = buffer.readNullableSimpleString();
      durable = buffer.readBoolean();
      requiresResponse = buffer.readBoolean();
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), address, filterString, queueName, durable, requiresResponse);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof CreateSharedQueueMessage other)) {
         return false;
      }

      return durable == other.durable &&
             requiresResponse == other.requiresResponse &&
             Objects.equals(address, other.address) &&
             Objects.equals(filterString, other.filterString) &&
             Objects.equals(queueName, other.queueName);
   }
}