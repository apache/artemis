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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;

public class SessionBindingQueryResponseMessage extends PacketImpl {

   protected boolean exists;

   protected List<SimpleString> queueNames;

   public SessionBindingQueryResponseMessage(final boolean exists, final List<SimpleString> queueNames) {
      super(SESS_BINDINGQUERY_RESP);

      this.exists = exists;

      this.queueNames = queueNames;
   }

   public SessionBindingQueryResponseMessage() {
      super(SESS_BINDINGQUERY_RESP);
   }

   public SessionBindingQueryResponseMessage(byte v) {
      super(v);
   }

   @Override
   public boolean isResponse() {
      return true;
   }

   public boolean isExists() {
      return exists;
   }

   public List<SimpleString> getQueueNames() {
      return queueNames;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeBoolean(exists);
      buffer.writeInt(queueNames.size());
      for (SimpleString queueName : queueNames) {
         buffer.writeSimpleString(queueName);
      }
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      exists = buffer.readBoolean();
      int numQueues = buffer.readInt();
      queueNames = new ArrayList<>(numQueues);
      for (int i = 0; i < numQueues; i++) {
         queueNames.add(buffer.readSimpleString());
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), exists, queueNames);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", exists=" + exists);
      sb.append(", queueNames=" + queueNames);
      return sb.toString();
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof SessionBindingQueryResponseMessage other)) {
         return false;
      }
      return exists == other.exists &&
             Objects.equals(queueNames, other.queueNames);
   }
}
