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
package org.apache.activemq.artemis.core.protocol.openwire;

import javax.jms.InvalidClientIDException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.apache.activemq.advisory.AdvisorySupport;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.protocol.openwire.amq.AMQConnectionContext;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptor;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyServerConnection;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.apache.activemq.artemis.selector.impl.LRUCache;
import org.apache.activemq.artemis.spi.core.protocol.AbstractProtocolManager;
import org.apache.activemq.artemis.spi.core.protocol.ConnectionEntry;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManagerFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.command.BrokerId;
import org.apache.activemq.command.BrokerInfo;
import org.apache.activemq.command.Command;
import org.apache.activemq.command.ConnectionControl;
import org.apache.activemq.command.ConnectionInfo;
import org.apache.activemq.command.ConsumerId;
import org.apache.activemq.command.DestinationInfo;
import org.apache.activemq.command.MessageDispatch;
import org.apache.activemq.command.MessageId;
import org.apache.activemq.command.ProducerId;
import org.apache.activemq.command.WireFormatInfo;
import org.apache.activemq.filter.DestinationFilter;
import org.apache.activemq.filter.DestinationPath;
import org.apache.activemq.openwire.OpenWireFormat;
import org.apache.activemq.openwire.OpenWireFormatFactory;
import org.apache.activemq.util.IdGenerator;
import org.apache.activemq.util.InetAddressUtil;
import org.apache.activemq.util.LongSequenceGenerator;

import static org.apache.activemq.artemis.core.protocol.openwire.util.OpenWireUtil.SELECTOR_AWARE_OPTION;

public class OpenWireProtocolManager  extends AbstractProtocolManager<Command, OpenWireInterceptor, OpenWireConnection, OpenWireRedirectHandler> implements ClusterTopologyListener {

   private static final List<String> websocketRegistryNames = Collections.EMPTY_LIST;

   private static final IdGenerator BROKER_ID_GENERATOR = new IdGenerator();
   private static final IdGenerator ID_GENERATOR = new IdGenerator();

   private final LongSequenceGenerator messageIdGenerator = new LongSequenceGenerator();
   private final ActiveMQServer server;

   private final OpenWireProtocolManagerFactory factory;

   private final OpenWireFormatFactory wireFactory;

   private boolean prefixPacketSize = true;

   private int actorThresholdBytes = -1;

   private BrokerId brokerId;
   protected final ProducerId advisoryProducerId = new ProducerId();

   private final CopyOnWriteArrayList<OpenWireConnection> connections = new CopyOnWriteArrayList<>();

   private final Map<String, AMQConnectionContext> clientIdSet = new HashMap<>();

   private String brokerName;

   private final Map<String, TopologyMember> topologyMap = new ConcurrentHashMap<>();

   private final LinkedList<TopologyMember> members = new LinkedList<>();

   private final ScheduledExecutorService scheduledPool;

   private String securityDomain;

   //bean properties
   //http://activemq.apache.org/failover-transport-reference.html
   private boolean rebalanceClusterClients = false;
   private boolean updateClusterClients = false;
   private boolean updateClusterClientsOnRemove = false;

   private boolean openwireUseDuplicateDetectionOnFailover = true;

   //http://activemq.apache.org/activemq-inactivitymonitor.html
   private long maxInactivityDuration = 30 * 1000L;
   private long maxInactivityDurationInitalDelay = 10 * 1000L;
   private boolean useKeepAlive = true;

   private boolean supportAdvisory = true;
   //prevents advisory addresses/queues to be registered
   //to management service
   private boolean suppressInternalManagementObjects = true;

   private final OpenWireFormat wireFormat;

   private final Map<SimpleString, RoutingType> prefixes = new HashMap<>();

   private final List<OpenWireInterceptor> incomingInterceptors = new ArrayList<>();
   private final List<OpenWireInterceptor> outgoingInterceptors = new ArrayList<>();

   private final OpenWireRedirectHandler redirectHandler;

   protected static class VirtualTopicConfig {
      public int filterPathTerminus;
      public boolean selectorAware;

      public VirtualTopicConfig(String[] configuration) {
         filterPathTerminus = Integer.valueOf(configuration[1]);
         // optional config
         for (int i = 2; i < configuration.length; i++) {
            String[] optionPair = configuration[i].split("=");
            consumeOption(optionPair);
         }
      }

      private void consumeOption(String[] optionPair) {
         if (optionPair.length == 2) {
            if (SELECTOR_AWARE_OPTION.equals(optionPair[0])) {
               selectorAware = Boolean.valueOf(optionPair[1]);
            }
         }
      }
   }

   private final Map<DestinationFilter, VirtualTopicConfig> vtConsumerDestinationMatchers = new HashMap<>();
   protected final LRUCache<ActiveMQDestination, ActiveMQDestination> vtDestMapCache = new LRUCache();

   public OpenWireProtocolManager(OpenWireProtocolManagerFactory factory, ActiveMQServer server,
                                  List<BaseInterceptor> incomingInterceptors,
                                  List<BaseInterceptor> outgoingInterceptors) {
      this.factory = factory;
      this.server = server;
      this.wireFactory = new OpenWireFormatFactory();
      // preferred prop, should be done via config
      wireFactory.setCacheEnabled(false);
      advisoryProducerId.setConnectionId(ID_GENERATOR.generateId());
      scheduledPool = server.getScheduledPool();
      this.wireFormat = (OpenWireFormat) wireFactory.createWireFormat();

      updateInterceptors(incomingInterceptors, outgoingInterceptors);

      final ClusterManager clusterManager = this.server.getClusterManager();

      ClusterConnection cc = clusterManager.getDefaultConnection(null);

      if (cc != null) {
         cc.addClusterTopologyListener(this);
      }

      //make sure we don't cluster advisories
      clusterManager.addProtocolIgnoredAddress(AdvisorySupport.ADVISORY_TOPIC_PREFIX);

      redirectHandler = new OpenWireRedirectHandler(server, this);
   }

   /** Is Duplicate detection enabled when used with failover clients. */
   public boolean isOpenwireUseDuplicateDetectionOnFailover() {
      return openwireUseDuplicateDetectionOnFailover;
   }

   /** should it use duplicate detection on failover clients. */
   public OpenWireProtocolManager setOpenwireUseDuplicateDetectionOnFailover(boolean openwireUseDuplicateDetectionOnFailover) {
      this.openwireUseDuplicateDetectionOnFailover = openwireUseDuplicateDetectionOnFailover;
      return this;
   }

   @Override
   public void nodeUP(TopologyMember member, boolean last) {
      if (topologyMap.put(member.getNodeId(), member) == null) {
         updateClientClusterInfo();
      }
   }

   @Override
   public void nodeDown(long eventUID, String nodeID) {
      if (topologyMap.remove(nodeID) != null) {
         updateClientClusterInfo();
      }
   }

   public void removeConnection(ConnectionInfo info, Throwable error) throws InvalidClientIDException {
      synchronized (clientIdSet) {
         String clientId = info.getClientId();
         if (clientId != null) {
            AMQConnectionContext context = this.clientIdSet.get(clientId);
            if (context != null && context.decRefCount() == 0) {
               //connection is still there and need to close
               context.getConnection().disconnect(error != null);
               this.connections.remove(context.getConnection());
               this.clientIdSet.remove(clientId);
            }
         } else {
            throw new InvalidClientIDException("No clientID specified for connection disconnect request");
         }
      }
   }

   /*** if set, the OpenWire connection will bypass the tcpReadBuferSize and use this value instead.
    *   This is by default -1, and it should not be used unless in extreme situations like on a slow storage. */
   public int getActorThresholdBytes() {
      return actorThresholdBytes;
   }

   public OpenWireProtocolManager setActorThresholdBytes(int actorThresholdBytes) {
      this.actorThresholdBytes = actorThresholdBytes;
      return this;
   }

   public ScheduledExecutorService getScheduledPool() {
      return scheduledPool;
   }

   public ActiveMQServer getServer() {
      return server;
   }

   private void updateClientClusterInfo() {

      synchronized (members) {
         members.clear();
         members.addAll(topologyMap.values());
      }

      for (OpenWireConnection c : this.connections) {
         ConnectionControl control = newConnectionControl();
         try {
            c.updateClient(control);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.warn(e.getMessage(), e);
            c.sendException(e);
         }
      }
   }

   @Override
   public boolean acceptsNoHandshake() {
      return false;
   }

   @Override
   public ProtocolManagerFactory getFactory() {
      return factory;
   }

   @Override
   public void updateInterceptors(List incoming, List outgoing) {
      this.incomingInterceptors.clear();
      if (incoming != null) {
         this.incomingInterceptors.addAll(getFactory().filterInterceptors(incoming));
      }

      this.outgoingInterceptors.clear();
      if (outgoing != null) {
         this.outgoingInterceptors.addAll(getFactory().filterInterceptors(outgoing));
      }
   }

   public String invokeIncoming(Command command, OpenWireConnection connection) {
      return super.invokeInterceptors(this.incomingInterceptors, command, connection);
   }

   public String invokeOutgoing(Command command, OpenWireConnection connection) {
      return super.invokeInterceptors(this.outgoingInterceptors, command, connection);
   }

   private int getActorThreadshold(Acceptor acceptorUsed) {
      int actorThreshold = TransportConstants.DEFAULT_TCP_RECEIVEBUFFER_SIZE;

      if (acceptorUsed instanceof NettyAcceptor) {
         actorThreshold = ((NettyAcceptor) acceptorUsed).getTcpReceiveBufferSize();
      }

      if (this.actorThresholdBytes > 0) {
         // replace any previous value
         actorThreshold = this.actorThresholdBytes;
      }

      return actorThreshold;
   }

   @Override
   public ConnectionEntry createConnectionEntry(Acceptor acceptorUsed, Connection connection) {
      OpenWireFormat wf = (OpenWireFormat) wireFactory.createWireFormat();
      OpenWireConnection owConn = new OpenWireConnection(connection, server, this, wf, server.getExecutorFactory().getExecutor(), getActorThreadshold(acceptorUsed));
      owConn.sendHandshake();

      //first we setup ttl to -1
      //then when negotiation, we handle real ttl and delay
      ConnectionEntry entry = new ConnectionEntry(owConn, null, System.currentTimeMillis(), -1);
      owConn.setConnectionEntry(entry);
      return entry;
   }

   @Override
   public void removeHandler(String name) {
   }

   @Override
   public void handleBuffer(RemotingConnection connection, ActiveMQBuffer buffer) {
   }

   @Override
   public void addChannelHandlers(ChannelPipeline pipeline) {
      // each read will have a full packet with this
      pipeline.addLast("packet-decipher", new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, DataConstants.SIZE_INT));
   }

   @Override
   public boolean isProtocol(byte[] array) {
      if (array.length < 8) {
         throw new IllegalArgumentException("Protocol header length changed " + array.length);
      }

      int start = this.prefixPacketSize ? 4 : 0;
      int j = 0;
      // type
      if (array[start] != WireFormatInfo.DATA_STRUCTURE_TYPE) {
         return false;
      }
      start++;
      WireFormatInfo info = new WireFormatInfo();
      final byte[] magic = info.getMagic();
      int remainingLen = array.length - start;
      int useLen = remainingLen > magic.length ? magic.length : remainingLen;
      useLen += start;
      // magic
      for (int i = start; i < useLen; i++) {
         if (array[i] != magic[j]) {
            return false;
         }
         j++;
      }
      return true;
   }

   @Override
   public void handshake(NettyServerConnection connection, ActiveMQBuffer buffer) {
   }

   @Override
   public List<String> websocketSubprotocolIdentifiers() {
      return websocketRegistryNames;
   }

   public void addConnection(OpenWireConnection connection, ConnectionInfo info) throws Exception {
      String username = info.getUserName();
      String password = info.getPassword();

      try {
         validateUser(username, password, connection);
      } catch (ActiveMQSecurityException e) {
         // We need to send an exception used by the openwire
         SecurityException ex = new SecurityException("User name [" + username + "] or password is invalid.");
         ex.initCause(e);
         throw ex;
      }

      String clientId = info.getClientId();
      if (clientId == null) {
         throw new InvalidClientIDException("No clientID specified for connection request");
      }

      synchronized (clientIdSet) {
         AMQConnectionContext context;
         context = clientIdSet.get(clientId);
         if (context != null) {
            if (info.isFailoverReconnect()) {
               OpenWireConnection oldConnection = context.getConnection();
               oldConnection.disconnect(true);
               connections.remove(oldConnection);
               connection.reconnect(context, info);
            } else {
               throw new InvalidClientIDException("Broker: " + getBrokerName() + " - Client: " + clientId + " already connected from " + context.getConnection().getRemoteAddress());
            }
         } else {
            //new connection
            context = connection.initContext(info);
            clientIdSet.put(clientId, context);
         }

         connections.add(connection);

         ActiveMQTopic topic = AdvisorySupport.getConnectionAdvisoryTopic();
         // do not distribute passwords in advisory messages. usernames okay
         ConnectionInfo copy = info.copy();
         copy.setPassword("");
         fireAdvisory(context, topic, copy);

         // init the conn
         context.getConnection().addSessions(context.getConnectionState().getSessionIds());
      }
   }

   public void fireAdvisory(AMQConnectionContext context, ActiveMQTopic topic, Command copy) throws Exception {
      this.fireAdvisory(context, topic, copy, null, null);
   }

   public BrokerId getBrokerId() {
      // TODO: Use the Storage ID here...
      if (brokerId == null) {
         brokerId = new BrokerId(BROKER_ID_GENERATOR.generateId());
      }
      return brokerId;
   }

   /*
    * See AdvisoryBroker.fireAdvisory()
    */
   public void fireAdvisory(AMQConnectionContext context,
                            ActiveMQTopic topic,
                            Command command,
                            ConsumerId targetConsumerId,
                            String originalConnectionId) throws Exception {
      if (!this.isSupportAdvisory()) {
         return;
      }
      ActiveMQMessage advisoryMessage = new ActiveMQMessage();

      if (originalConnectionId == null) {
         originalConnectionId = context.getConnectionId().getValue();
      }
      advisoryMessage.setStringProperty(MessageUtil.CONNECTION_ID_PROPERTY_NAME.toString(), originalConnectionId);
      advisoryMessage.setStringProperty(AdvisorySupport.MSG_PROPERTY_ORIGIN_BROKER_NAME, getBrokerName());
      String id = getBrokerId() != null ? getBrokerId().getValue() : "NOT_SET";
      advisoryMessage.setStringProperty(AdvisorySupport.MSG_PROPERTY_ORIGIN_BROKER_ID, id);
      advisoryMessage.setStringProperty(AdvisorySupport.MSG_PROPERTY_ORIGIN_BROKER_URL, context.getConnection().getLocalAddress());

      // set the data structure
      advisoryMessage.setDataStructure(command);
      advisoryMessage.setPersistent(false);
      advisoryMessage.setType(AdvisorySupport.ADIVSORY_MESSAGE_TYPE);
      advisoryMessage.setMessageId(new MessageId(advisoryProducerId, messageIdGenerator.getNextSequenceId()));
      advisoryMessage.setTargetConsumerId(targetConsumerId);
      advisoryMessage.setDestination(topic);
      advisoryMessage.setResponseRequired(false);
      advisoryMessage.setProducerId(advisoryProducerId);
      advisoryMessage.setTimestamp(System.currentTimeMillis());

      final CoreMessageObjectPools objectPools = context.getConnection().getCoreMessageObjectPools();
      final org.apache.activemq.artemis.api.core.Message coreMessage = OpenWireMessageConverter.inbound(advisoryMessage, wireFormat, objectPools);

      final SimpleString address = SimpleString.toSimpleString(topic.getPhysicalName(), objectPools.getAddressStringSimpleStringPool());
      coreMessage.setAddress(address);
      coreMessage.setRoutingType(RoutingType.MULTICAST);
      // follow pattern from management notification to route directly
      server.getPostOffice().route(coreMessage, false);
   }

   public String getBrokerName() {
      if (brokerName == null) {
         try {
            brokerName = InetAddressUtil.getLocalHostName().toLowerCase(Locale.ENGLISH);
         } catch (Exception e) {
            brokerName = server.getNodeID().toString();
         }
      }
      return brokerName;
   }

   protected ConnectionControl newConnectionControl() {
      ConnectionControl control = new ConnectionControl();

      String uri = generateMembersURI(rebalanceClusterClients);
      control.setConnectedBrokers(uri);

      control.setRebalanceConnection(rebalanceClusterClients);
      return control;
   }

   private String generateMembersURI(boolean flip) {
      String uri;
      StringBuffer connectedBrokers = new StringBuffer();
      String separator = "";

      synchronized (members) {
         if (members.size() > 0) {
            for (TopologyMember member : members) {
               connectedBrokers.append(separator).append(member.toURI());
               separator = ",";
            }

            // The flip exists to guarantee even distribution of URIs when sent to the client
            // in case of failures you won't get all the connections failing to a single server.
            if (flip && members.size() > 1) {
               members.addLast(members.removeFirst());
            }
         }
      }

      uri = connectedBrokers.toString();
      return uri;
   }

   public boolean isFaultTolerantConfiguration() {
      return false;
   }

   public void postProcessDispatch(MessageDispatch md) {
      // TODO Auto-generated method stub

   }

   public boolean isStopped() {
      // TODO Auto-generated method stub
      return false;
   }

   public void preProcessDispatch(MessageDispatch messageDispatch) {
      // TODO Auto-generated method stub

   }

   public boolean isStopping() {
      return false;
   }

   public void validateUser(String login, String passcode, OpenWireConnection connection) throws Exception {
      server.getSecurityStore().authenticate(login, passcode, connection, getSecurityDomain());
   }

   public void sendBrokerInfo(OpenWireConnection connection) throws Exception {
      BrokerInfo brokerInfo = new BrokerInfo();
      brokerInfo.setBrokerName(getBrokerName());
      brokerInfo.setBrokerId(new BrokerId("" + server.getNodeID()));
      brokerInfo.setPeerBrokerInfos(null);
      brokerInfo.setFaultTolerantConfiguration(false);
      brokerInfo.setBrokerURL(connection.getLocalAddress());

      //cluster support yet to support
      brokerInfo.setPeerBrokerInfos(null);
      connection.dispatch(brokerInfo);
   }

   public void configureInactivityParams(OpenWireConnection connection, WireFormatInfo command) throws IOException {
      long inactivityDurationToUse = command.getMaxInactivityDuration() > this.maxInactivityDuration ? this.maxInactivityDuration : command.getMaxInactivityDuration();
      long inactivityDurationInitialDelayToUse = command.getMaxInactivityDurationInitalDelay() > this.maxInactivityDurationInitalDelay ? this.maxInactivityDurationInitalDelay : command.getMaxInactivityDurationInitalDelay();
      boolean useKeepAliveToUse = inactivityDurationToUse == 0L ? false : this.useKeepAlive;
      connection.setUpTtl(inactivityDurationToUse, inactivityDurationInitialDelayToUse, useKeepAliveToUse);
   }

   /**
    * URI property
    */
   @SuppressWarnings("unused")
   public void setRebalanceClusterClients(boolean rebalance) {
      this.rebalanceClusterClients = rebalance;
   }

   /**
    * URI property
    */
   @SuppressWarnings("unused")
   public boolean isRebalanceClusterClients() {
      return this.rebalanceClusterClients;
   }

   /**
    * URI property
    */
   @SuppressWarnings("unused")
   public void setUpdateClusterClients(boolean updateClusterClients) {
      this.updateClusterClients = updateClusterClients;
   }

   public boolean isUpdateClusterClients() {
      return this.updateClusterClients;
   }

   /**
    * URI property
    */
   @SuppressWarnings("unused")
   public void setUpdateClusterClientsOnRemove(boolean updateClusterClientsOnRemove) {
      this.updateClusterClientsOnRemove = updateClusterClientsOnRemove;
   }

   /**
    * URI property
    */
   @SuppressWarnings("unused")
   public boolean isUpdateClusterClientsOnRemove() {
      return this.updateClusterClientsOnRemove;
   }

   public void setBrokerName(String name) {
      this.brokerName = name;
   }

   public boolean isUseKeepAlive() {
      return useKeepAlive;
   }

   @SuppressWarnings("unused")
   public void setUseKeepAlive(boolean useKeepAlive) {
      this.useKeepAlive = useKeepAlive;
   }

   public long getMaxInactivityDuration() {
      return maxInactivityDuration;
   }

   public void setMaxInactivityDuration(long maxInactivityDuration) {
      this.maxInactivityDuration = maxInactivityDuration;
   }

   @SuppressWarnings("unused")
   public long getMaxInactivityDurationInitalDelay() {
      return maxInactivityDurationInitalDelay;
   }

   @SuppressWarnings("unused")
   public void setMaxInactivityDurationInitalDelay(long maxInactivityDurationInitalDelay) {
      this.maxInactivityDurationInitalDelay = maxInactivityDurationInitalDelay;
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
   public OpenWireRedirectHandler getRedirectHandler() {
      return redirectHandler;
   }

   @Override
   public String getSecurityDomain() {
      return securityDomain;
   }

   public List<DestinationInfo> getTemporaryDestinations() {
      List<DestinationInfo> total = new ArrayList<>();
      for (OpenWireConnection connection : connections) {
         total.addAll(connection.getTemporaryDestinations());
      }
      return total;
   }

   public OpenWireFormat wireFormat() {
      return wireFormat;
   }

   public boolean isSupportAdvisory() {
      return supportAdvisory;
   }

   public void setSupportAdvisory(boolean supportAdvisory) {
      this.supportAdvisory = supportAdvisory;
   }

   public boolean isSuppressInternalManagementObjects() {
      return suppressInternalManagementObjects;
   }

   public void setSuppressInternalManagementObjects(boolean suppressInternalManagementObjects) {
      this.suppressInternalManagementObjects = suppressInternalManagementObjects;
   }

   public void setVirtualTopicConsumerWildcards(String virtualTopicConsumerWildcards) {
      for (String filter : virtualTopicConsumerWildcards.split(",")) {
         String[] configuration = filter.split(";");
         vtConsumerDestinationMatchers.put(DestinationFilter.parseFilter(new ActiveMQQueue(configuration[0])), new VirtualTopicConfig(configuration));
      }
   }

   public void setVirtualTopicConsumerLruCacheMax(int max) {
      vtDestMapCache.setMaxCacheSize(max);
   }

   public ActiveMQDestination virtualTopicConsumerToFQQN(final ActiveMQDestination destination) {

      if (vtConsumerDestinationMatchers.isEmpty()) {
         return destination;
      }

      ActiveMQDestination mappedDestination = null;
      synchronized (vtDestMapCache) {
         mappedDestination = vtDestMapCache.get(destination);
      }

      if (mappedDestination != null) {
         return mappedDestination;
      }

      for (Map.Entry<DestinationFilter, VirtualTopicConfig> candidate : vtConsumerDestinationMatchers.entrySet()) {
         if (candidate.getKey().matches(destination)) {
            // convert to matching FQQN
            String[] paths = DestinationPath.getDestinationPaths(destination);
            StringBuilder fqqn = new StringBuilder();
            VirtualTopicConfig virtualTopicConfig = candidate.getValue();
            // address - ie: topic
            for (int i = virtualTopicConfig.filterPathTerminus; i < paths.length; i++) {
               if (i > virtualTopicConfig.filterPathTerminus) {
                  fqqn.append(ActiveMQDestination.PATH_SEPERATOR);
               }
               fqqn.append(paths[i]);
            }
            fqqn.append(CompositeAddress.SEPARATOR);
            // consumer queue - the full vt queue
            for (int i = 0; i < paths.length; i++) {
               if (i > 0) {
                  fqqn.append(ActiveMQDestination.PATH_SEPERATOR);
               }
               fqqn.append(paths[i]);
            }
            mappedDestination = new ActiveMQQueue(fqqn.toString() + ( virtualTopicConfig.selectorAware ? "?" + SELECTOR_AWARE_OPTION + "=true" : "" ));
            break;
         }
      }
      if (mappedDestination == null) {
         // cache the identity mapping
         mappedDestination = destination;
      }
      synchronized (vtDestMapCache) {
         ActiveMQDestination existing = vtDestMapCache.put(destination, mappedDestination);
         if (existing != null) {
            // some one beat us to the put, revert
            vtDestMapCache.put(destination, existing);
            mappedDestination = existing;
         }
      }
      return mappedDestination;
   }

   public List<OpenWireConnection> getConnections() {
      return connections;
   }
}
