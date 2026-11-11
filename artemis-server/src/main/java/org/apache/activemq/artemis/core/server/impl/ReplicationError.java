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
package org.apache.activemq.artemis.core.server.impl;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQInternalErrorException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.BackupReplicationStartFailedMessage;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.NodeLocator;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Stops the backup in case of an error at the start of Replication.
 * <p>
 * Using an interceptor for the task to avoid a server reference inside of the 'basic' channel-0 handler at
 * {@link org.apache.activemq.artemis.core.protocol.core.impl.ActiveMQClientProtocolManager.Channel0Handler}. As
 * {@link org.apache.activemq.artemis.core.protocol.core.impl.ActiveMQClientProtocolManager} is also shipped in the
 * activemq-core-client JAR (which does not include {@link org.apache.activemq.artemis.core.server.ActiveMQServer}).
 */
final class ReplicationError implements Interceptor {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private NodeLocator nodeLocator;

   ReplicationError(NodeLocator nodeLocator) {
      this.nodeLocator = nodeLocator;
   }

   @Override
   public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
      if (packet.getType() != PacketImpl.BACKUP_REGISTRATION_FAILED) {
         return true;
      }

      logger.trace("Received ReplicationError::{}", packet);

      BackupReplicationStartFailedMessage message = (BackupReplicationStartFailedMessage) packet;
      switch (message.getRegistrationProblem()) {
         case ALREADY_REPLICATING:
            tryNext();
            break;
         case AUTHENTICATION:
            failed();
            break;
         case EXCEPTION:
            failed();
            break;
         default:
            failed();

      }
      return false;
   }

   private void failed() throws ActiveMQInternalErrorException {
      ActiveMQServerLogger.LOGGER.errorRegisteringBackup();
      nodeLocator.notifyRegistrationFailed(false);
   }

   private void tryNext() {
      nodeLocator.notifyRegistrationFailed(true);
   }

}
