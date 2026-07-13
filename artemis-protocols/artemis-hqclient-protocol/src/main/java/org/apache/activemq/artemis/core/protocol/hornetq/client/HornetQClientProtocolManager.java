/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.protocol.hornetq.client;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.ActiveMQClientProtocolManager;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ClusterTopologyChangeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSessionResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SubscribeClusterTopologyUpdatesMessageV2;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.SessionContext;

public class HornetQClientProtocolManager extends ActiveMQClientProtocolManager {

   private static final int VERSION_PLAYED = 123;

   @Override
   protected void sendHandshake(Connection transportConnection) {
   }

   @Override
   protected SessionContext newSessionContext(String name,
                                              int confirmationWindowSize,
                                              Channel sessionChannel,
                                              CreateSessionResponseMessage response) {
      // these objects won't be null, otherwise it would keep retrying on the previous loop
      return new HornetQClientSessionContext(name, connection, sessionChannel, response.getServerVersion(), confirmationWindowSize);
   }

   @Override
   protected Packet newCreateSessionPacket(int clientVersion,
                                           String name,
                                           String username,
                                           String password,
                                           boolean xa,
                                           boolean autoCommitSends,
                                           boolean autoCommitAcks,
                                           boolean preAcknowledge,
                                           int minLargeMessageSize,
                                           int confirmationWindowSize,
                                           long sessionChannelID,
                                           String clientID) {
      return super.newCreateSessionPacket(VERSION_PLAYED, name, username, password, xa, autoCommitSends, autoCommitAcks, preAcknowledge, minLargeMessageSize, confirmationWindowSize, sessionChannelID, clientID);
   }

   @Override
   public void sendSubscribeTopology(final boolean isServer) {
      getChannel0().send(new SubscribeClusterTopologyUpdatesMessageV2(isServer, VERSION_PLAYED));
   }

   @Override
   public boolean sendConnect(String nodeID, String connectionUser, String connectionPassword) throws ActiveMQException {
      //HornetQ doesn't support connect handshake
      return true;
   }


   @Override
   protected ClusterTopologyChangeMessage updateTransportConfiguration(final ClusterTopologyChangeMessage topMessage) {
      updateTransportConfiguration(topMessage.getPair().getA());
      updateTransportConfiguration(topMessage.getPair().getB());
      return super.updateTransportConfiguration(topMessage);
   }

   private void updateTransportConfiguration(TransportConfiguration connector) {
      if (connector != null) {
         String factoryClassName = connector.getFactoryClassName();
         if ("org.hornetq.core.remoting.impl.netty.NettyConnectorFactory".equals(factoryClassName)) {
            connector.setFactoryClassName(NettyConnectorFactory.class.getName());
         }
      }
   }

}
