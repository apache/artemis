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
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.client.impl.QueueQueryImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.server.QueueQueryResult;

public class SessionQueueQueryResponseMessage extends PacketImpl {

   protected SimpleString name;

   protected boolean exists;

   protected boolean durable;

   protected int consumerCount;

   protected long messageCount;

   protected SimpleString filterString;

   protected SimpleString address;

   protected boolean temporary;

   public SessionQueueQueryResponseMessage(final QueueQueryResult result) {
      this(result.getName(), result.getAddress(), result.isDurable(), result.isTemporary(), result.getFilterString(), result.getConsumerCount(), result.getMessageCount(), result.isExists());
   }

   public SessionQueueQueryResponseMessage() {
      this(null, null, false, false, null, 0, 0, false);
   }

   public SessionQueueQueryResponseMessage(byte v) {
      super(v);
   }

   private SessionQueueQueryResponseMessage(final SimpleString name,
                                            final SimpleString address,
                                            final boolean durable,
                                            final boolean temporary,
                                            final SimpleString filterString,
                                            final int consumerCount,
                                            final long messageCount,
                                            final boolean exists) {
      super(SESS_QUEUEQUERY_RESP);

      this.durable = durable;

      this.temporary = temporary;

      this.consumerCount = consumerCount;

      this.messageCount = messageCount;

      this.filterString = filterString;

      this.address = address;

      this.name = name;

      this.exists = exists;
   }

   @Override
   public boolean isResponse() {
      return true;
   }

   public boolean isExists() {
      return exists;
   }

   public boolean isDurable() {
      return durable;
   }

   public int getConsumerCount() {
      return consumerCount;
   }

   public long getMessageCount() {
      return messageCount;
   }

   public SimpleString getFilterString() {
      return filterString;
   }

   public SimpleString getAddress() {
      return address;
   }

   public SimpleString getName() {
      return name;
   }

   public boolean isTemporary() {
      return temporary;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeBoolean(exists);
      buffer.writeBoolean(durable);
      buffer.writeBoolean(temporary);
      buffer.writeInt(consumerCount);
      buffer.writeLong(messageCount);
      buffer.writeNullableSimpleString(filterString);
      buffer.writeNullableSimpleString(address);
      buffer.writeNullableSimpleString(name);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      exists = buffer.readBoolean();
      durable = buffer.readBoolean();
      temporary = buffer.readBoolean();
      consumerCount = buffer.readInt();
      messageCount = buffer.readLong();
      filterString = buffer.readNullableSimpleString();
      address = buffer.readNullableSimpleString();
      name = buffer.readNullableSimpleString();
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), address, consumerCount, durable, exists, filterString, messageCount, name,
                          temporary);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", address=" + address);
      sb.append(", name=" + name);
      sb.append(", consumerCount=" + consumerCount);
      sb.append(", filterString=" + filterString);
      sb.append(", durable=" + durable);
      sb.append(", exists=" + exists);
      sb.append(", temporary=" + temporary);
      sb.append(", messageCount=" + messageCount);
      return sb.toString();
   }

   public ClientSession.QueueQuery toQueueQuery() {
      return new QueueQueryImpl(isDurable(), isTemporary(), getConsumerCount(), getMessageCount(), getFilterString(), getAddress(), getName(), isExists());
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof SessionQueueQueryResponseMessage other)) {
         return false;
      }

      return Objects.equals(address, other.address) &&
             consumerCount == other.consumerCount &&
             durable == other.durable &&
             exists == other.exists &&
             Objects.equals(filterString, other.filterString) &&
             messageCount == other.messageCount &&
             Objects.equals(name, other.name) &&
             temporary == other.temporary;
   }

}
