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
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;

public class ClusterTopologyChangeMessage extends PacketImpl {

   protected boolean exit;

   protected String nodeID;

   protected Pair<TransportConfiguration, TransportConfiguration> pair;

   protected boolean last;


   public ClusterTopologyChangeMessage(final String nodeID,
                                       final Pair<TransportConfiguration, TransportConfiguration> pair,
                                       final boolean last) {
      super(CLUSTER_TOPOLOGY);

      this.nodeID = nodeID;

      this.pair = pair;

      this.last = last;

      this.exit = false;
   }

   public ClusterTopologyChangeMessage(final String nodeID) {
      super(CLUSTER_TOPOLOGY);

      this.exit = true;

      this.nodeID = nodeID;
   }

   public ClusterTopologyChangeMessage() {
      super(CLUSTER_TOPOLOGY);
   }

   /**
    * @param clusterTopologyV2
    */
   public ClusterTopologyChangeMessage(byte clusterTopologyV2) {
      super(clusterTopologyV2);
   }

   public String getNodeID() {
      return nodeID;
   }

   public Pair<TransportConfiguration, TransportConfiguration> getPair() {
      return pair;
   }

   public boolean isLast() {
      return last;
   }

   public boolean isExit() {
      return exit;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeBoolean(exit);
      buffer.writeString(nodeID);
      if (!exit) {
         if (pair.getA() != null) {
            buffer.writeBoolean(true);
            pair.getA().encode(buffer);
         } else {
            buffer.writeBoolean(false);
         }
         if (pair.getB() != null) {
            buffer.writeBoolean(true);
            pair.getB().encode(buffer);
         } else {
            buffer.writeBoolean(false);
         }
         buffer.writeBoolean(last);
      }
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      exit = buffer.readBoolean();
      nodeID = buffer.readString();
      if (!exit) {
         boolean hasLive = buffer.readBoolean();
         TransportConfiguration a;
         if (hasLive) {
            a = new TransportConfiguration();
            a.decode(buffer);
         } else {
            a = null;
         }
         boolean hasBackup = buffer.readBoolean();
         TransportConfiguration b;
         if (hasBackup) {
            b = new TransportConfiguration();
            b.decode(buffer);
         } else {
            b = null;
         }
         pair = new Pair<>(a, b);
         last = buffer.readBoolean();
      }
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = super.hashCode();
      result = prime * result + (exit ? 1231 : 1237);
      result = prime * result + (last ? 1231 : 1237);
      result = prime * result + ((nodeID == null) ? 0 : nodeID.hashCode());
      result = prime * result + ((pair == null) ? 0 : pair.hashCode());
      return result;
   }

   @Override
   protected String getPacketString() {
      StringBuffer buff = new StringBuffer(super.getPacketString());
      buff.append(", exit=" + exit);
      buff.append(", last=" + last);
      buff.append(", nodeID=" + nodeID);
      buff.append(", pair=" + pair);
      return buff.toString();
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof ClusterTopologyChangeMessage)) {
         return false;
      }
      ClusterTopologyChangeMessage other = (ClusterTopologyChangeMessage) obj;
      if (exit != other.exit) {
         return false;
      }
      if (last != other.last) {
         return false;
      }
      if (nodeID == null) {
         if (other.nodeID != null) {
            return false;
         }
      } else if (!nodeID.equals(other.nodeID)) {
         return false;
      }
      if (pair == null) {
         if (other.pair != null) {
            return false;
         }
      } else if (!pair.equals(other.pair)) {
         return false;
      }
      return true;
   }
}
