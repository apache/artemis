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
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;

/**
 * Registers a given backup-server as the replicating backup of a primary server (i.e. a regular ActiveMQ).
 * <p>
 * If it succeeds the backup will start synchronization of its state with the new backup node, and replicating any new
 * data. If it fails the backup server will receive a message indicating failure, and should shutdown.
 *
 * @see BackupReplicationStartFailedMessage
 */
public final class BackupRegistrationMessage extends PacketImpl {

   private TransportConfiguration connector;
   private String clusterUser;
   private String clusterPassword;
   private boolean backupWantsFailBack;

   public BackupRegistrationMessage(TransportConfiguration tc,
                                    String user,
                                    String password,
                                    boolean backupWantsFailBack) {
      this();
      connector = tc;
      clusterUser = user;
      clusterPassword = password;
      this.backupWantsFailBack = backupWantsFailBack;
   }

   public BackupRegistrationMessage() {
      super(BACKUP_REGISTRATION);
   }

   public TransportConfiguration getConnector() {
      return connector;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeString(clusterUser);
      buffer.writeString(clusterPassword);
      buffer.writeBoolean(backupWantsFailBack);
      connector.encode(buffer);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      clusterUser = buffer.readString();
      clusterPassword = buffer.readString();
      backupWantsFailBack = buffer.readBoolean();
      connector = new TransportConfiguration();
      connector.decode(buffer);
   }

   public String getClusterUser() {
      return clusterUser;
   }

   public String getClusterPassword() {
      return clusterPassword;
   }

   public boolean isFailBackRequest() {
      return backupWantsFailBack;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), backupWantsFailBack, clusterPassword, clusterUser, connector);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof BackupRegistrationMessage other)) {
         return false;
      }
      return backupWantsFailBack == other.backupWantsFailBack &&
             Objects.equals(clusterPassword, other.clusterPassword) &&
             Objects.equals(clusterUser, other.clusterUser) &&
             Objects.equals(connector, other.connector);
   }
}
