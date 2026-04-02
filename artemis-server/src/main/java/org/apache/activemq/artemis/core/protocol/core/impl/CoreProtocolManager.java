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
package org.apache.activemq.artemis.core.protocol.core.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import io.netty.channel.ChannelPipeline;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.FederationConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationConnectionConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationDownstreamConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationPolicy;
import org.apache.activemq.artemis.core.config.federation.FederationTransformerConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationUpstreamConfiguration;
import org.apache.activemq.artemis.core.protocol.ServerPacketDecoder;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.ChannelHandler;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.ServerSessionPacketHandler;
import org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl.CHANNEL_ID;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ClusterTopologyChangeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ClusterTopologyChangeMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ClusterTopologyChangeMessage_V3;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ClusterTopologyChangeMessage_V4;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.FederationDownstreamConnectMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.Ping;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SubscribeClusterTopologyUpdatesMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SubscribeClusterTopologyUpdatesMessageV2;
import org.apache.activemq.artemis.core.remoting.CloseListener;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.core.remoting.impl.netty.ActiveMQFrameDecoder2;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyServerConnection;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.spi.core.protocol.ConnectionEntry;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManagerFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.jboss.logging.Logger;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.utils.SecurityManagerUtil;

public class CoreProtocolManager implements ProtocolManager<Interceptor, ActiveMQRedirectHandler> {

   private static final Logger logger = Logger.getLogger(CoreProtocolManager.class);

   private static final List<String> websocketRegistryNames = Collections.EMPTY_LIST;

   protected final ActiveMQServer server;

   private final List<Interceptor> incomingInterceptors;

   private final List<Interceptor> outgoingInterceptors;

   private final CoreProtocolManagerFactory protocolManagerFactory;

   private final Map<SimpleString, RoutingType> prefixes = new HashMap<>();

   private String securityDomain;

   private final ActiveMQRedirectHandler redirectHandler;

   public CoreProtocolManager(final CoreProtocolManagerFactory factory,
                              final ActiveMQServer server,
                              final List<Interceptor> incomingInterceptors,
                              List<Interceptor> outgoingInterceptors) {
      this.protocolManagerFactory = factory;

      this.server = server;

      this.incomingInterceptors = incomingInterceptors;

      this.outgoingInterceptors = outgoingInterceptors;

      this.redirectHandler = new ActiveMQRedirectHandler(server);
   }

   @Override
   public ProtocolManagerFactory<Interceptor> getFactory() {
      return protocolManagerFactory;
   }

   @Override
   public void updateInterceptors(List<BaseInterceptor> incoming, List<BaseInterceptor> outgoing) {
      this.incomingInterceptors.clear();
      this.incomingInterceptors.addAll(getFactory().filterInterceptors(incoming));

      this.outgoingInterceptors.clear();
      this.outgoingInterceptors.addAll(getFactory().filterInterceptors(outgoing));
   }

   @Override
   public boolean acceptsNoHandshake() {
      return false;
   }

   @Override
   public ConnectionEntry createConnectionEntry(final Acceptor acceptorUsed,
                                                final Connection connection) {
      final Configuration config = server.getConfiguration();

      Executor connectionExecutor = server.getExecutorFactory().getExecutor();

      final CoreRemotingConnection rc = new RemotingConnectionImpl(new ServerPacketDecoder(server.getStorageManager()),
                                                                   connection, incomingInterceptors, outgoingInterceptors, server.getNodeID(),
                                                                   connectionExecutor);

      Channel channel1 = rc.getChannel(CHANNEL_ID.SESSION.id, -1);

      ChannelHandler handler = new ActiveMQPacketHandler(this, server, channel1, rc);

      channel1.setHandler(handler);

      long ttl = ActiveMQClient.DEFAULT_CONNECTION_TTL;

      if (config.getConnectionTTLOverride() != -1) {
         ttl = config.getConnectionTTLOverride();
      }

      final ConnectionEntry entry = new ConnectionEntry(rc, connectionExecutor,
                                                        System.currentTimeMillis(), ttl);

      final Channel channel0 = rc.getChannel(ChannelImpl.CHANNEL_ID.PING.id, -1);

      channel0.setHandler(new LocalChannelHandler(config, entry, channel0, acceptorUsed, rc));

      server.getClusterManager()
         .addClusterChannelHandler(rc.getChannel(CHANNEL_ID.CLUSTER.id, -1), acceptorUsed, rc,
                                   server.getActivation());

      rc.getChannel(CHANNEL_ID.FEDERATION.id, -1).setHandler(new FederationChannelHandler(acceptorUsed, rc));

      return entry;
   }

   private final Map<String, ServerSessionPacketHandler> sessionHandlers = new ConcurrentHashMap<>();

   ServerSessionPacketHandler getSessionHandler(final String sessionName) {
      return sessionHandlers.get(sessionName);
   }

   void addSessionHandler(final String name, final ServerSessionPacketHandler handler) {
      sessionHandlers.put(name, handler);
   }

   @Override
   public void removeHandler(final String name) {
      sessionHandlers.remove(name);
   }

   @Override
   public void handleBuffer(RemotingConnection connection, ActiveMQBuffer buffer) {
   }

   @Override
   public void addChannelHandlers(ChannelPipeline pipeline) {
      pipeline.addLast("activemq-decoder", new ActiveMQFrameDecoder2());
   }

   @Override
   public boolean isProtocol(byte[] array) {
      return isArtemis(ActiveMQBuffers.wrappedBuffer(array));
   }

   @Override
   public void handshake(NettyServerConnection connection, ActiveMQBuffer buffer) {
      //if we are not an old client then handshake
      if (isArtemis(buffer)) {
         buffer.skipBytes(7);
      }
   }

   @Override
   public List<String> websocketSubprotocolIdentifiers() {
      return websocketRegistryNames;
   }

   @Override
   public void setAnycastPrefix(String anycastPrefix) {
      for (String prefix : anycastPrefix.split(",")) {
         prefixes.put(SimpleString.toSimpleString(prefix), RoutingType.ANYCAST);
      }
   }

   @Override
   public void setMulticastPrefix(String multicastPrefix) {
      for (String prefix : multicastPrefix.split(",")) {
         prefixes.put(SimpleString.toSimpleString(prefix), RoutingType.MULTICAST);
      }
   }

   @Override
   public Map<SimpleString, RoutingType> getPrefixes() {
      return prefixes;
   }

   @Override
   public void setSecurityDomain(String securityDomain) {
      this.securityDomain = securityDomain;
   }

   @Override
   public String getSecurityDomain() {
      return securityDomain;
   }

   @Override
   public ActiveMQRedirectHandler getRedirectHandler() {
      return redirectHandler;
   }

   private boolean isArtemis(ActiveMQBuffer buffer) {
      return buffer.getByte(0) == 'A' &&
         buffer.getByte(1) == 'R' &&
         buffer.getByte(2) == 'T' &&
         buffer.getByte(3) == 'E' &&
         buffer.getByte(4) == 'M' &&
         buffer.getByte(5) == 'I' &&
         buffer.getByte(6) == 'S';
   }

   @Override
   public String toString() {
      return "CoreProtocolManager(server=" + server + ")";
   }

   private class LocalChannelHandler implements ChannelHandler {

      private final Configuration config;
      private final ConnectionEntry entry;
      private final Channel channel0;
      private final Acceptor acceptorUsed;
      private final CoreRemotingConnection rc;

      private LocalChannelHandler(final Configuration config,
                                  final ConnectionEntry entry,
                                  final Channel channel0,
                                  final Acceptor acceptorUsed,
                                  final CoreRemotingConnection rc) {
         this.config = config;
         this.entry = entry;
         this.channel0 = channel0;
         this.acceptorUsed = acceptorUsed;
         this.rc = rc;
      }

      @Override
      public void handlePacket(final Packet packet) {
         if (packet.getType() == PacketImpl.PING) {
            Ping ping = (Ping) packet;

            if (config.getConnectionTTLOverride() == -1) {
               // Allow clients to specify connection ttl
               entry.ttl = ping.getConnectionTTL();
            }

            // Just send a ping back
            channel0.send(packet);
         } else if (packet.getType() == PacketImpl.SUBSCRIBE_TOPOLOGY
            || packet.getType() == PacketImpl.SUBSCRIBE_TOPOLOGY_V2) {
            SubscribeClusterTopologyUpdatesMessage msg = (SubscribeClusterTopologyUpdatesMessage) packet;

            if (packet.getType() == PacketImpl.SUBSCRIBE_TOPOLOGY_V2) {
               channel0.getConnection().setChannelVersion(
                  ((SubscribeClusterTopologyUpdatesMessageV2) msg).getClientVersion());
            }

            final ClusterTopologyListener listener = new ClusterTopologyListener() {
               @Override
               public void nodeUP(final TopologyMember topologyMember, final boolean last) {
                  try {
                     final Pair<TransportConfiguration, TransportConfiguration> connectorPair = BackwardsCompatibilityUtils
                        .checkTCPPairConversion(
                           channel0.getConnection().getChannelVersion(), topologyMember);

                     final String nodeID = topologyMember.getNodeId();
                     // Using an executor as most of the notifications on the Topology
                     // may come from a channel itself
                     // What could cause deadlocks
                     entry.connectionExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                           if (channel0.supports(PacketImpl.CLUSTER_TOPOLOGY_V4)) {
                              channel0.send(new ClusterTopologyChangeMessage_V4(
                                 topologyMember.getUniqueEventID(), nodeID,
                                 topologyMember.getBackupGroupName(),
                                 topologyMember.getScaleDownGroupName(), connectorPair,
                                 last, server.getVersion().getIncrementingVersion()));
                           } else if (channel0.supports(PacketImpl.CLUSTER_TOPOLOGY_V3)) {
                              channel0.send(new ClusterTopologyChangeMessage_V3(
                                 topologyMember.getUniqueEventID(), nodeID,
                                 topologyMember.getBackupGroupName(),
                                 topologyMember.getScaleDownGroupName(), connectorPair,
                                 last));
                           } else if (channel0.supports(PacketImpl.CLUSTER_TOPOLOGY_V2)) {
                              channel0.send(new ClusterTopologyChangeMessage_V2(
                                 topologyMember.getUniqueEventID(), nodeID,
                                 topologyMember.getBackupGroupName(), connectorPair,
                                 last));
                           } else {
                              channel0.send(
                                 new ClusterTopologyChangeMessage(nodeID, connectorPair,
                                                                  last));
                           }
                        }
                     });
                  } catch (RejectedExecutionException ignored) {
                     logger.debug(ignored.getMessage(), ignored);
                     // this could happen during a shutdown and we don't care, if we lost a nodeDown during a shutdown
                     // what can we do anyways?
                  }

               }

               @Override
               public void nodeDown(final long uniqueEventID, final String nodeID) {
                  // Using an executor as most of the notifications on the Topology
                  // may come from a channel itself
                  // What could cause deadlocks
                  try {
                     entry.connectionExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                           if (channel0.supports(PacketImpl.CLUSTER_TOPOLOGY_V2)) {
                              channel0.send(
                                 new ClusterTopologyChangeMessage_V2(uniqueEventID,
                                                                     nodeID));
                           } else {
                              channel0.send(new ClusterTopologyChangeMessage(nodeID));
                           }
                        }
                     });
                  } catch (RejectedExecutionException ignored) {
                     // this could happen during a shutdown and we don't care, if we lost a nodeDown during a shutdown
                     // what can we do anyways?
                  }
               }

               @Override
               public String toString() {
                  return "Remote Proxy on channel " + Integer
                     .toHexString(System.identityHashCode(this));
               }
            };

            if (acceptorUsed.getClusterConnection() != null) {
               acceptorUsed.getClusterConnection().addClusterTopologyListener(listener);

               rc.addCloseListener(new CloseListener() {
                  @Override
                  public void connectionClosed() {
                     acceptorUsed.getClusterConnection()
                        .removeClusterTopologyListener(listener);
                  }
               });
            } else {
               // if not clustered, we send a single notification to the client containing the node-id where the server is connected to
               // This is done this way so Recovery discovery could also use the node-id for non-clustered setups
               entry.connectionExecutor.execute(new Runnable() {
                  @Override
                  public void run() {
                     String nodeId = server.getNodeID().toString();
                     Pair<TransportConfiguration, TransportConfiguration> emptyConfig = new Pair<>(
                        null, null);
                     if (channel0.supports(PacketImpl.CLUSTER_TOPOLOGY_V4)) {
                        channel0.send(new ClusterTopologyChangeMessage_V4(System.currentTimeMillis(), nodeId,
                           null, null, emptyConfig, true, server.getVersion().getIncrementingVersion()));
                     } else if (channel0.supports(PacketImpl.CLUSTER_TOPOLOGY_V2)) {
                        channel0.send(
                           new ClusterTopologyChangeMessage_V2(System.currentTimeMillis(),
                                                               nodeId, null, emptyConfig, true));
                     } else {
                        channel0.send(
                           new ClusterTopologyChangeMessage(nodeId, emptyConfig, true));
                     }
                  }
               });
            }
         }
      }

      private Pair<TransportConfiguration, TransportConfiguration> getPair(
         TransportConfiguration conn,
         boolean isBackup) {
         if (isBackup) {
            return new Pair<>(null, conn);
         }
         return new Pair<>(conn, null);
      }
   }

   private class FederationChannelHandler implements ChannelHandler {

      private final Acceptor acceptorUsed;
      private final CoreRemotingConnection rc;

      private FederationChannelHandler(final Acceptor acceptorUsed, final CoreRemotingConnection rc) {
         this.acceptorUsed = acceptorUsed;
         this.rc = rc;
      }

      @Override
      public void handlePacket(final Packet packet) {
         if (packet.getType() == PacketImpl.FEDERATION_DOWNSTREAM_CONNECT) {
            if (server.getSecurityStore().isSecurityEnabled()) {
               if (rc.getAuditSubject() == null) {
                  ActiveMQServerLogger.LOGGER.federationDownstreamUnauthenticated(rc.getRemoteAddress());
                  rc.destroy();
                  return;
               }
               if (!server.getFederationManager().authorizeDownstreamDeployment(rc.getAuditSubject())) {
                  ActiveMQServerLogger.LOGGER.federationDownstreamUnauthorized(rc.getRemoteAddress(), SecurityManagerUtil.getUserFromSubject(rc.getAuditSubject(), UserPrincipal.class));
                  rc.destroy();
                  return;
               }
            }
            //If we receive this packet then a remote broker is requesting us to create federated upstream connection
            //back to it which simulates a downstream connection
            final FederationDownstreamConnectMessage message = (FederationDownstreamConnectMessage) packet;
            final FederationDownstreamConfiguration downstreamConfiguration = message.getStreamConfiguration();

            //Create a new Upstream Federation configuration based on the received Downstream connection message
            //from the remote broker
            //The idea here is to set all the same configuration parameters that apply to the upstream connection
            final FederationConfiguration config = new FederationConfiguration();
            config.setName(message.getName() + FederationDownstreamConnectMessage.UPSTREAM_SUFFIX);
            config.setCredentials(message.getCredentials());

            //Add the policy map configuration
            for (FederationPolicy policy : message.getFederationPolicyMap().values()) {
               config.addFederationPolicy(policy);
            }

            //Add any transformer configurations
            for (FederationTransformerConfiguration transformerConfiguration : message.getTransformerConfigurationMap().values()) {
               config.addTransformerConfiguration(transformerConfiguration);
            }

            //Create an upstream configuration with the same name but apply the upstream suffix so it is unique
            final FederationUpstreamConfiguration upstreamConfiguration = new FederationUpstreamConfiguration()
               .setName(downstreamConfiguration.getName() + FederationDownstreamConnectMessage.UPSTREAM_SUFFIX)
               .addPolicyRefs(downstreamConfiguration.getPolicyRefs());

            //Use the provided Transport Configuration information to create an upstream connection back to the broker that
            //created the downstream connection
            final TransportConfiguration upstreamConfig = downstreamConfiguration.getUpstreamConfiguration();

            //Initialize the upstream transport with the config from the acceptor as this will apply
            //relevant settings such as SSL, then override with settings from the downstream config
            final Map<String, Object> params = new HashMap<>(acceptorUsed.getConfiguration());
            params.putAll(upstreamConfig.getParams());

            //Add the new upstream configuration that was created so we can connect back to the downstream server
            final TransportConfiguration upstreamConf = new TransportConfiguration(
               upstreamConfig.getFactoryClassName(), params, upstreamConfig.getName() + FederationDownstreamConnectMessage.UPSTREAM_SUFFIX,
               new HashMap<>());
            server.getConfiguration()
               .addConnectorConfiguration(upstreamConf.getName() + FederationDownstreamConnectMessage.UPSTREAM_SUFFIX, upstreamConf);

            //Create a new upstream connection config based on the downstream configuration
            FederationConnectionConfiguration downstreamConConf = downstreamConfiguration.getConnectionConfiguration();
            FederationConnectionConfiguration upstreamConConf = upstreamConfiguration.getConnectionConfiguration();
            List<String> connectorNames = new ArrayList<>();
            connectorNames.add(upstreamConf.getName() + FederationDownstreamConnectMessage.UPSTREAM_SUFFIX);

            //Configure all of the upstream connection parameters from the downstream connection that are relevant
            //Note that HA and discoveryGroupName are skipped because the downstream connection will manage that
            //In this case we just want to create a connection back to the broker that sent the downstream packet.
            //If this broker goes down then the original broker (if configured with HA) will re-establish a new
            //connection to another broker which will then create another upstream, etc
            upstreamConConf.setStaticConnectors(connectorNames);
            upstreamConConf.setUsername(downstreamConConf.getUsername());
            upstreamConConf.setPassword(downstreamConConf.getPassword());
            upstreamConConf.setShareConnection(downstreamConConf.isShareConnection());
            upstreamConConf.setPriorityAdjustment(downstreamConConf.getPriorityAdjustment());
            upstreamConConf.setClientFailureCheckPeriod(downstreamConConf.getClientFailureCheckPeriod());
            upstreamConConf.setConnectionTTL(downstreamConConf.getConnectionTTL());
            upstreamConConf.setRetryInterval(downstreamConConf.getRetryInterval());
            upstreamConConf.setRetryIntervalMultiplier(downstreamConConf.getRetryIntervalMultiplier());
            upstreamConConf.setMaxRetryInterval(downstreamConConf.getMaxRetryInterval());
            upstreamConConf.setInitialConnectAttempts(downstreamConConf.getInitialConnectAttempts());
            upstreamConConf.setReconnectAttempts(downstreamConConf.getReconnectAttempts());
            upstreamConConf.setCallTimeout(downstreamConConf.getCallTimeout());
            upstreamConConf.setCallFailoverTimeout(downstreamConConf.getCallFailoverTimeout());
            config.addUpstreamConfiguration(upstreamConfiguration);

            //Register close and failure listeners, if the initial downstream connection goes down then we
            //want to terminate the upstream connection
            rc.addCloseListener(() -> {
               handleClose(config.getName(), rc.getRemoteAddress());
            });

            rc.addFailureListener(new FailureListener() {
               @Override
               public void connectionFailed(ActiveMQException exception, boolean failedOver) {
                  handleClose(config.getName(), rc.getRemoteAddress());
               }

               @Override
               public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {
                  handleClose(config.getName(), rc.getRemoteAddress());
               }
            });

            try {
               server.getFederationManager().deploy(config);
               String user = rc.getAuditSubject() != null ? SecurityManagerUtil.getUserFromSubject(rc.getAuditSubject(), UserPrincipal.class) : "anonymous";
               ActiveMQServerLogger.LOGGER.federationDownstreamDeployedFromRemoteUser(config.getName(), user, rc.getRemoteAddress());
            } catch (Exception e) {
               logger.error("Error deploying federation: " + e.getMessage(), e);
            }
         }
      }

      private void handleClose(String configName, String remoteAddress) {
         if (server.getFederationManager().undeploy(configName)) {
            ActiveMQServerLogger.LOGGER.federationDownstreamConnectionClosed(remoteAddress, configName);
         }
      }
   }
}
