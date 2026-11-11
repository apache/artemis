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
import org.apache.activemq.artemis.core.server.QueueQueryResult;

public class SessionQueueQueryResponseMessage_V2 extends SessionQueueQueryResponseMessage {

   protected boolean autoCreateQueues;

   public SessionQueueQueryResponseMessage_V2(final QueueQueryResult result) {
      this(result.getName(), result.getAddress(), result.isDurable(), result.isTemporary(), result.getFilterString(), result.getConsumerCount(), result.getMessageCount(), result.isExists(), result.isAutoCreateQueues());
   }

   public SessionQueueQueryResponseMessage_V2() {
      this(null, null, false, false, null, 0, 0, false, false);
   }

   private SessionQueueQueryResponseMessage_V2(final SimpleString name,
                                               final SimpleString address,
                                               final boolean durable,
                                               final boolean temporary,
                                               final SimpleString filterString,
                                               final int consumerCount,
                                               final long messageCount,
                                               final boolean exists,
                                               final boolean autoCreateQueues) {
      super(SESS_QUEUEQUERY_RESP_V2);

      this.durable = durable;

      this.temporary = temporary;

      this.consumerCount = consumerCount;

      this.messageCount = messageCount;

      this.filterString = filterString;

      this.address = address;

      this.name = name;

      this.exists = exists;

      this.autoCreateQueues = autoCreateQueues;
   }
   public SessionQueueQueryResponseMessage_V2(byte v) {
      super(v);
   }

   public boolean isAutoCreateQueues() {
      return autoCreateQueues;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      super.encodeRest(buffer);
      buffer.writeBoolean(autoCreateQueues);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      super.decodeRest(buffer);
      autoCreateQueues = buffer.readBoolean();
   }

   @Override
   public int hashCode() {
      return super.hashCode() + Objects.hashCode(autoCreateQueues);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", autoCreationEnabled=" + autoCreateQueues);
      return sb.toString();
   }

   @Override
   public ClientSession.QueueQuery toQueueQuery() {
      return new QueueQueryImpl(isDurable(), isTemporary(), getConsumerCount(), getMessageCount(), getFilterString(), getAddress(), getName(), isExists(), isAutoCreateQueues());
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof SessionQueueQueryResponseMessage_V2 other)) {
         return false;
      }

      return autoCreateQueues == other.autoCreateQueues;
   }
}
