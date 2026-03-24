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
import org.apache.activemq.artemis.api.core.DisconnectReason;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;

public class DisconnectMessage_V3 extends DisconnectMessage {

   private DisconnectReason reason;
   private SimpleString targetNodeID;
   private TransportConfiguration targetConnector;

   public DisconnectMessage_V3(final SimpleString nodeID,
                               final DisconnectReason reason,
                               final SimpleString targetNodeID,
                               final TransportConfiguration targetConnector) {
      super(DISCONNECT_V3);

      this.nodeID = nodeID;

      this.reason = reason;

      this.targetNodeID = targetNodeID;

      this.targetConnector = targetConnector;
   }

   public DisconnectMessage_V3() {
      super(DISCONNECT_V3);
   }

   public DisconnectReason getReason() {
      return reason;
   }

   public SimpleString getTargetNodeID() {
      return targetNodeID;
   }

   public TransportConfiguration getTargetConnector() {
      return targetConnector;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      super.encodeRest(buffer);
      buffer.writeByte(reason == null ? -1 : reason.getType());
      buffer.writeNullableSimpleString(targetNodeID);
      if (targetConnector != null) {
         buffer.writeBoolean(true);
         targetConnector.encode(buffer);
      } else {
         buffer.writeBoolean(false);
      }
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      super.decodeRest(buffer);
      reason = DisconnectReason.getType(buffer.readByte());
      targetNodeID = buffer.readNullableSimpleString();
      boolean hasTargetConnector = buffer.readBoolean();
      if (hasTargetConnector) {
         targetConnector = new TransportConfiguration();
         targetConnector.decode(buffer);
      } else {
         targetConnector = null;
      }
   }

   @Override
   protected String getPacketString() {
      StringBuffer buf = new StringBuffer(super.getPacketString());
      buf.append(", nodeID=" + nodeID);
      buf.append(", reason=" + reason);
      buf.append(", targetNodeID=" + targetNodeID);
      buf.append(", targetConnector=" + targetConnector);
      return buf.toString();
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = super.hashCode();
      result = prime * result + (reason.getType());
      result = prime * result + ((targetNodeID == null) ? 0 : targetNodeID.hashCode());
      result = prime * result + ((targetConnector == null) ? 0 : targetConnector.hashCode());
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
      if (!(obj instanceof DisconnectMessage_V3)) {
         return false;
      }
      DisconnectMessage_V3 other = (DisconnectMessage_V3) obj;
      if (reason == null) {
         if (other.reason != null)
            return false;
      } else if (!reason.equals(other.reason))
         return false;
      if (targetNodeID == null) {
         if (other.targetNodeID != null) {
            return false;
         }
      } else if (!targetNodeID.equals(other.targetNodeID)) {
         return false;
      }
      return true;
   }
}
