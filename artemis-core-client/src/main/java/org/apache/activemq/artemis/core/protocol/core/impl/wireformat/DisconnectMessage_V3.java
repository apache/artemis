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
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", nodeID=" + nodeID);
      sb.append(", reason=" + reason);
      sb.append(", targetNodeID=" + targetNodeID);
      sb.append(", targetConnector=" + targetConnector);
      return sb.toString();
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), reason.getType(), targetNodeID, targetConnector);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof DisconnectMessage_V3 other)) {
         return false;
      }

      return Objects.equals(reason, other.reason) &&
             Objects.equals(targetNodeID, other.targetNodeID);
   }
}