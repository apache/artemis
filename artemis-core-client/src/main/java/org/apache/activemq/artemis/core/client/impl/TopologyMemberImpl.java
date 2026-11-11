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
package org.apache.activemq.artemis.core.client.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.utils.ConfigurationHelper;

public final class TopologyMemberImpl implements TopologyMember {

   private final Pair<TransportConfiguration, TransportConfiguration> connector;

   private final String backupGroupName;

   private final String scaleDownGroupName;


   /**
    * transient to avoid serialization changes
    */
   private transient long uniqueEventID = System.currentTimeMillis();

   private final String nodeId;

   public TopologyMemberImpl(String nodeId,
                             final String backupGroupName,
                             final String scaleDownGroupName,
                             final TransportConfiguration a,
                             final TransportConfiguration b) {
      this.nodeId = nodeId;
      this.backupGroupName = backupGroupName;
      this.scaleDownGroupName = scaleDownGroupName;
      this.connector = new Pair<>(a, b);
      uniqueEventID = System.currentTimeMillis();
   }

   @Override
   @Deprecated(forRemoval = true)
   public TransportConfiguration getLive() {
      return getPrimary();
   }

   @Override
   public TransportConfiguration getPrimary() {
      return connector.getA();
   }

   @Override
   public TransportConfiguration getBackup() {
      return connector.getB();
   }

   public void setBackup(final TransportConfiguration param) {
      connector.setB(param);
   }

   public void setPrimary(final TransportConfiguration param) {
      connector.setA(param);
   }

   @Override
   public String getNodeId() {
      return nodeId;
   }

   @Override
   public long getUniqueEventID() {
      return uniqueEventID;
   }

   @Override
   public String getBackupGroupName() {
      return backupGroupName;
   }

   @Override
   public String getScaleDownGroupName() {
      return scaleDownGroupName;
   }

   public void setUniqueEventID(final long uniqueEventID) {
      this.uniqueEventID = uniqueEventID;
   }

   public Pair<TransportConfiguration, TransportConfiguration> getConnector() {
      return connector;
   }

   /**
    * We only need to check if the connection point to the same node, don't need to compare the whole params map.
    *
    * @param connection The connection to the target node
    * @return true if the connection point to the same node as this member represents.
    */
   @Override
   public boolean isMember(RemotingConnection connection) {
      return connection.isSameTarget(getConnector().getA(), getConnector().getB());
   }

   @Override
   public boolean isMember(TransportConfiguration configuration) {
      if (getConnector().getA() != null && getConnector().getA().isSameParams(configuration) || getConnector().getB() != null && getConnector().getB().isSameParams(configuration)) {
         return true;
      } else {
         return false;
      }
   }

   @Override
   public String toURI() {
      TransportConfiguration primaryConnector = getPrimary();
      Map<String, Object> props = primaryConnector.getParams();
      String host = ConfigurationHelper.getStringProperty(TransportConstants.HOST_PROP_NAME, "localhost", props);
      int port = ConfigurationHelper.getIntProperty(TransportConstants.PORT_PROP_NAME, 0, props);
      return "tcp://" + host + ":" + port;
   }

   public URI toBackupURI() {
      TransportConfiguration backupConnector = getBackup();
      if (backupConnector == null) {
         return null;
      }
      Map<String, Object> props = backupConnector.getParams();
      String host = ConfigurationHelper.getStringProperty(TransportConstants.HOST_PROP_NAME, "localhost", props);
      int port = ConfigurationHelper.getIntProperty(TransportConstants.PORT_PROP_NAME, 0, props);
      boolean sslEnabled = ConfigurationHelper.getBooleanProperty(TransportConstants.SSL_ENABLED_PROP_NAME, false, props);
      try {
         return new URI("tcp://" + host + ":" + port + "?" + TransportConstants.SSL_ENABLED_PROP_NAME + "=" + sslEnabled);
      } catch (URISyntaxException e) {
         return null;
      }
   }

   @Override
   public String toString() {
      return "TopologyMember[id=" + nodeId + ", connector=" + connector + ", backupGroupName=" + backupGroupName + ", scaleDownGroupName=" + scaleDownGroupName + "]";
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof TopologyMemberImpl other)) {
         return false;
      }

      // note the uniqueEventId is not part of the equals and hashmap key
      return Objects.equals(connector, other.connector) &&
             Objects.equals(backupGroupName, other.backupGroupName) &&
             Objects.equals(scaleDownGroupName, other.scaleDownGroupName) &&
             Objects.equals(nodeId, other.nodeId);
   }

   @Override
   public int hashCode() {
      return Objects.hash(connector, backupGroupName, scaleDownGroupName, nodeId);
   }
}
