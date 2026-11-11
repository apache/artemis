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
package org.apache.activemq.artemis.core.cluster;

import org.apache.activemq.artemis.api.core.TransportConfiguration;

public class DiscoveryEntry {

   private final String nodeID;
   private final TransportConfiguration connector;
   private final long lastUpdate;

   public DiscoveryEntry(final String nodeID, final TransportConfiguration connector, final long lastUpdate) {
      this.nodeID = nodeID;
      this.connector = connector;
      this.lastUpdate = lastUpdate;
   }

   public String getNodeID() {
      return nodeID;
   }

   public TransportConfiguration getConnector() {
      return connector;
   }

   public long getLastUpdate() {
      return lastUpdate;
   }

   @Override
   public String toString() {
      return "DiscoveryEntry[nodeID=" + nodeID + ", connector=" + connector + ", lastUpdate=" + lastUpdate + "]";
   }
}
