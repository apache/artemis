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

import java.util.List;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;

public class SessionBindingQueryResponseMessage_V2 extends SessionBindingQueryResponseMessage {

   protected boolean autoCreateQueues;

   public SessionBindingQueryResponseMessage_V2(final boolean exists,
                                                final List<SimpleString> queueNames,
                                                final boolean autoCreateQueues) {
      super(SESS_BINDINGQUERY_RESP_V2);

      this.exists = exists;

      this.queueNames = queueNames;

      this.autoCreateQueues = autoCreateQueues;
   }

   public SessionBindingQueryResponseMessage_V2() {
      super(SESS_BINDINGQUERY_RESP_V2);
   }

   public SessionBindingQueryResponseMessage_V2(byte v) {
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
      sb.append(", autoCreateQueues=" + autoCreateQueues);
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
      if (!(obj instanceof SessionBindingQueryResponseMessage_V2 other)) {
         return false;
      }

      return autoCreateQueues == other.autoCreateQueues;
   }
}
