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
         boolean hasPrimary = buffer.readBoolean();
         TransportConfiguration a;
         if (hasPrimary) {
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
      return Objects.hash(super.hashCode(), exit, last, nodeID, pair);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", exit=" + exit);
      sb.append(", last=" + last);
      sb.append(", nodeID=" + nodeID);
      sb.append(", pair=" + pair);
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
      if (!(obj instanceof ClusterTopologyChangeMessage other)) {
         return false;
      }

      return exit == other.exit &&
             last == other.last &&
             Objects.equals(nodeID, other.nodeID) &&
             Objects.equals(pair, other.pair);
   }
}
