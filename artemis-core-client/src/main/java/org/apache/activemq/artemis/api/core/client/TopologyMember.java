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
package org.apache.activemq.artemis.api.core.client;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

/**
 * A member of the topology.
 * <p>
 * Each TopologyMember represents a single server and possibly any backup server that may take over its duties (using
 * the nodeId of the original server).
 */
public interface TopologyMember {

   /**
    * Returns the {@code backup-group-name} of the primary server and backup servers associated with Topology entry.
    * <p>
    * This is a server configuration value. A (remote) backup will only work with primary servers that have a matching
    * {@code backup-group-name}.
    * <p>
    * This value does not apply to "shared-storage" backup and primary pairs.
    *
    * @return the {@code backup-group-name}
    */
   String getBackupGroupName();

   /**
    * Returns the {@code scale-down-group-name} of the server with this Topology entry.
    * <p>
    * This is a server configuration value. An active server will only send its messages to another active server with
    * matching {@code scale-down-group-name}.
    *
    * @return the {@code scale-down-group-name}
    */
   String getScaleDownGroupName();

   /**
    * {@return configuration relative to the live server}
    */
   @Deprecated(forRemoval = true)
   TransportConfiguration getLive();

   /**
    * {@return configuration relative to the primary server}
    */
   TransportConfiguration getPrimary();

   /**
    * {@return a {@link TransportConfiguration} for the backup, or null if the primary server has no backup server.}
    */
   TransportConfiguration getBackup();

   /**
    * {@return the nodeId of the server}
    */
   String getNodeId();

   /**
    * {@return long value representing a unique event ID}
    */
   long getUniqueEventID();

   /**
    * {@return {@code true} if this {@code TopologyMember} is the target of this remoting connection}
    */
   boolean isMember(RemotingConnection connection);

   /**
    * {@return {@code true} if this configuration is the target of this remoting connection}
    */
   boolean isMember(TransportConfiguration configuration);

   String toURI();
}
