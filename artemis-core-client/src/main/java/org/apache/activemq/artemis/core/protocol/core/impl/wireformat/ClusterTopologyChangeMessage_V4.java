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

public class ClusterTopologyChangeMessage_V4 extends ClusterTopologyChangeMessage_V3 {

   private int serverVersion;

   public ClusterTopologyChangeMessage_V4(final long uniqueEventID,
                                          final String nodeID,
                                          final String backupGroupName,
                                          final String scaleDownGroupName,
                                          final Pair<TransportConfiguration, TransportConfiguration> pair,
                                          final boolean last,
                                          final int serverVersion) {
      super(CLUSTER_TOPOLOGY_V4, uniqueEventID, nodeID, backupGroupName, scaleDownGroupName, pair, last);

      this.serverVersion = serverVersion;
   }

   public ClusterTopologyChangeMessage_V4() {
      super(CLUSTER_TOPOLOGY_V4);
   }

   public int getServerVersion() {
      return serverVersion;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      super.encodeRest(buffer);

      buffer.writeInt(serverVersion);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      super.decodeRest(buffer);

      serverVersion = buffer.readInt();
   }

   @Override
   public int hashCode() {
      return super.hashCode() + Objects.hashCode(serverVersion);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", clientVersion=" + serverVersion);
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
      if (!(obj instanceof ClusterTopologyChangeMessage_V4 other)) {
         return false;
      }
      return serverVersion == other.serverVersion;
   }
}
