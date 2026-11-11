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

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;

public class ClusterConnectReplyMessage extends PacketImpl {

   private boolean authorized;

   public ClusterConnectReplyMessage() {
      super(CLUSTER_CONNECT_REPLY);
   }

   public ClusterConnectReplyMessage(boolean authorized) {
      super(CLUSTER_CONNECT_REPLY);
      this.authorized = authorized;
   }

   @Override
   public boolean isResponse() {
      return true;
   }

   @Override
   public void encodeRest(ActiveMQBuffer buffer) {
      super.encodeRest(buffer);
      buffer.writeBoolean(authorized);
   }

   @Override
   public void decodeRest(ActiveMQBuffer buffer) {
      super.decodeRest(buffer);
      authorized = buffer.readBoolean();
   }

   public boolean isAuthorized() {
      return authorized;
   }

   public void setAuthorized(boolean authorized) {
      this.authorized = authorized;
   }
}
