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
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;

public class NodeAnnounceMessage extends PacketImpl {

   protected String nodeID;

   protected String backupGroupName;

   protected boolean backup;

   protected long currentEventID;

   protected TransportConfiguration connector;

   protected TransportConfiguration backupConnector;

   private String scaleDownGroupName;



   public NodeAnnounceMessage(final long currentEventID,
                              final String nodeID,
                              final String backupGroupName,
                              final String scaleDownGroupName,
                              final boolean backup,
                              final TransportConfiguration tc,
                              final TransportConfiguration backupConnector) {
      super(NODE_ANNOUNCE);

      this.currentEventID = currentEventID;

      this.nodeID = nodeID;

      this.backupGroupName = backupGroupName;

      this.backup = backup;

      this.connector = tc;

      this.backupConnector = backupConnector;

      this.scaleDownGroupName = scaleDownGroupName;
   }

   public NodeAnnounceMessage() {
      super(NODE_ANNOUNCE);
   }

   public NodeAnnounceMessage(byte nodeAnnounceMessage_V2) {
      super(nodeAnnounceMessage_V2);
   }


   public String getNodeID() {
      return nodeID;
   }

   public String getBackupGroupName() {
      return backupGroupName;
   }

   public boolean isBackup() {
      return backup;
   }

   public TransportConfiguration getConnector() {
      return connector;
   }

   public TransportConfiguration getBackupConnector() {
      return backupConnector;
   }

   public String getScaleDownGroupName() {
      return scaleDownGroupName;
   }

   /**
    * @return the currentEventID
    */
   public long getCurrentEventID() {
      return currentEventID;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeString(nodeID);
      buffer.writeNullableString(backupGroupName);
      buffer.writeBoolean(backup);
      buffer.writeLong(currentEventID);
      if (connector != null) {
         buffer.writeBoolean(true);
         connector.encode(buffer);
      } else {
         buffer.writeBoolean(false);
      }
      if (backupConnector != null) {
         buffer.writeBoolean(true);
         backupConnector.encode(buffer);
      } else {
         buffer.writeBoolean(false);
      }
      buffer.writeNullableString(scaleDownGroupName);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      this.nodeID = buffer.readString();
      this.backupGroupName = buffer.readNullableString();
      this.backup = buffer.readBoolean();
      this.currentEventID = buffer.readLong();
      if (buffer.readBoolean()) {
         connector = new TransportConfiguration();
         connector.decode(buffer);
      }
      if (buffer.readBoolean()) {
         backupConnector = new TransportConfiguration();
         backupConnector.decode(buffer);
      }
      scaleDownGroupName = buffer.readNullableString();
   }

   @Override
   protected String getPacketString() {
      String baseString = super.getPacketString();
      return baseString +
         ", backup=" + backup +
         ", connector=" +
         connector +
         ", nodeID=" +
         nodeID;
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = super.hashCode();
      result = prime * result + (backup ? 1231 : 1237);
      result = prime * result + ((backupConnector == null) ? 0 : backupConnector.hashCode());
      result = prime * result + ((connector == null) ? 0 : connector.hashCode());
      result = prime * result + (int) (currentEventID ^ (currentEventID >>> 32));
      result = prime * result + ((nodeID == null) ? 0 : nodeID.hashCode());
      result = prime * result + ((scaleDownGroupName == null) ? 0 : scaleDownGroupName.hashCode());
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
      if (!(obj instanceof NodeAnnounceMessage)) {
         return false;
      }
      NodeAnnounceMessage other = (NodeAnnounceMessage) obj;
      if (backup != other.backup) {
         return false;
      }
      if (backupConnector == null) {
         if (other.backupConnector != null) {
            return false;
         }
      } else if (!backupConnector.equals(other.backupConnector)) {
         return false;
      }
      if (connector == null) {
         if (other.connector != null) {
            return false;
         }
      } else if (!connector.equals(other.connector)) {
         return false;
      }
      if (currentEventID != other.currentEventID) {
         return false;
      }
      if (nodeID == null) {
         if (other.nodeID != null) {
            return false;
         }
      } else if (!nodeID.equals(other.nodeID)) {
         return false;
      } else if (!scaleDownGroupName.equals(other.scaleDownGroupName)) {
         return false;
      }
      return true;
   }
}
