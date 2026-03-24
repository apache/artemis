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
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;

public class DisconnectMessage extends PacketImpl {


   protected SimpleString nodeID;


   public DisconnectMessage(final SimpleString nodeID) {
      super(DISCONNECT);

      this.nodeID = nodeID;
   }

   public DisconnectMessage() {
      super(DISCONNECT);
   }

   public DisconnectMessage(byte disconnectV2) {
      super(disconnectV2);
   }

   public SimpleString getNodeID() {
      return nodeID;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeNullableSimpleString(nodeID);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      nodeID = buffer.readNullableSimpleString();
   }

   @Override
   protected String getPacketString() {
      StringBuffer buff = new StringBuffer(super.getPacketString());
      buff.append(", nodeID=" + nodeID);
      return buff.toString();
   }

   @Override
   public final boolean isRequiresConfirmations() {
      return false;
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = super.hashCode();
      result = prime * result + ((nodeID == null) ? 0 : nodeID.hashCode());
      return result;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof DisconnectMessage)) {
         return false;
      }
      DisconnectMessage other = (DisconnectMessage) obj;
      if (nodeID == null) {
         if (other.nodeID != null) {
            return false;
         }
      } else if (!nodeID.equals(other.nodeID)) {
         return false;
      }
      return true;
   }
}
