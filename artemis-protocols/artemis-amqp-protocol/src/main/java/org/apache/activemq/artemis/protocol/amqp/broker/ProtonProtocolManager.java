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
package org.apache.activemq.artemis.protocol.amqp.broker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyServerConnection;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.core.server.management.NotificationListener;
import org.apache.activemq.artemis.protocol.amqp.client.ProtonClientProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.ReferenceIDSupplier;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConstants;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPRoutingHandler;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.protocol.amqp.sasl.ClientSASLFactory;
import org.apache.activemq.artemis.protocol.amqp.sasl.MechanismFinder;
import org.apache.activemq.artemis.spi.core.protocol.AbstractProtocolManager;
import org.apache.activemq.artemis.spi.core.protocol.ConnectionEntry;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManagerFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.spi.core.remoting.Connection;

import io.netty.channel.ChannelPipeline;
import org.apache.activemq.artemis.utils.DestinationUtil;
import org.apache.qpid.proton.amqp.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A proton protocol manager, basically reads the Proton Input and maps proton resources to Apache Artemis resources
 */
public class ProtonProtocolManager extends AbstractProtocolManager<AMQPMessage, AmqpInterceptor, ActiveMQProtonRemotingConnection, AMQPRoutingHandler> implements NotificationListener {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final List<String> websocketRegistryNames = Arrays.asList("amqp");

   public static final String MIRROR_ADDRESS = QueueConfiguration.MIRROR_ADDRESS;

   private final List<AmqpInterceptor> incomingInterceptors = new ArrayList<>();
   private final List<AmqpInterceptor> outgoingInterceptors = new ArrayList<>();

   public static String getMirrorAddress(String connectionName) {
      return MIRROR_ADDRESS + "_" + connectionName;
   }

   private final ActiveMQServer server;

   private ReferenceIDSupplier referenceIDSupplier;

   private final ProtonProtocolManagerFactory factory;

   private final Map<SimpleString, RoutingType> prefixes = new HashMap<>();

   private final Map<SimpleString, RoutingType> temporaryPrefixes = new HashMap<>();

   /**
    * minLargeMessageSize determines when a message should be considered as large. minLargeMessageSize = -1 basically
    * disables large message control over AMQP.
    */
   private int amqpMinLargeMessageSize = 100 * 1024;

   private int amqpCredits = AmqpSupport.AMQP_CREDITS_DEFAULT;

   private int amqpLowCredits = AmqpSupport.AMQP_LOW_CREDITS_DEFAULT;

   private boolean amqpDuplicateDetection = true;

   private boolean amqpUseModifiedForTransientDeliveryErrors = AmqpSupport.AMQP_USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS;

   private boolean amqpDrainOnTransientDeliveryErrors = AmqpSupport.AMQP_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS;

   // If set true, a reject disposition will be treated as if it were an unmodified disposition with the
   // delivery-failed flag set true.
   private boolean amqpTreatRejectAsUnmodifiedDeliveryFailed = AmqpSupport.AMQP_TREAT_REJECT_AS_UNMODIFIED_DELIVERY_FAILURE;

   private int initialRemoteMaxFrameSize = 4 * 1024;

   private String[] saslMechanisms = MechanismFinder.getDefaultMechanisms();

   private String saslLoginConfigScope = "amqp-sasl-gssapi";

   private int amqpLinkQuiesceTimeout = AmqpSupport.AMQP_LINK_QUIESCE_TIMEOUT;

   private Long amqpIdleTimeout;

   private long ackManagerFlushTimeout = 10_000;

   private boolean directDeliver = true;

   private int mirrorMaxPendingAcks = 10_000;

   private final AMQPRoutingHandler routingHandler;

   /*
    * Used when you want to treat senders as a subscription on an address rather than consuming from the actual queue
    * for the address. This can be changed on the acceptor.
    */
   private String pubSubPrefix = DestinationUtil.TOPIC_QUALIFIED_PREFIX;

   private int maxFrameSize = AmqpSupport.MAX_FRAME_SIZE_DEFAULT;

   public ProtonProtocolManager(ProtonProtocolManagerFactory factory, ActiveMQServer server, List<BaseInterceptor> incomingInterceptors, List<BaseInterceptor> outgoingInterceptors) {
      this.factory = factory;
      this.server = server;
      this.updateInterceptors(incomingInterceptors, outgoingInterceptors);
      routingHandler = new AMQPRoutingHandler(server);
   }

   public synchronized ReferenceIDSupplier getReferenceIDSupplier() {
      if (referenceIDSupplier == null) {
         // we lazy start the instance.
         // only create it when needed
         referenceIDSupplier = new ReferenceIDSupplier(server);
      }
      return referenceIDSupplier;
   }

   public ActiveMQServer getServer() {
      return server;
   }

   @Override
   public void onNotification(Notification notification) {

   }

   /**
    * Before the ackManager retries acks, it must flush the OperationContext on the MirrorTargets. This is the timeout
    * is in milliseconds
    */
   public long getAckManagerFlushTimeout() {
      return ackManagerFlushTimeout;
   }

   public ProtonProtocolManager setAckManagerFlushTimeout(long ackManagerFlushTimeout) {
      this.ackManagerFlushTimeout = ackManagerFlushTimeout;
      return this;
   }

   public int getAmqpMinLargeMessageSize() {
      return amqpMinLargeMessageSize;
   }

   public int getMirrorMaxPendingAcks() {
      return mirrorMaxPendingAcks;
   }

   public ProtonProtocolManager setMirrorMaxPendingAcks(int maxPendingAcks) {
      this.mirrorMaxPendingAcks = maxPendingAcks;
      return this;
   }

   public ProtonProtocolManager setAmqpMinLargeMessageSize(int amqpMinLargeMessageSize) {
      this.amqpMinLargeMessageSize = amqpMinLargeMessageSize;
      return this;
   }

   public boolean isAmqpDuplicateDetection() {
      return amqpDuplicateDetection;
   }

   public ProtonProtocolManager setAmqpDuplicateDetection(boolean duplicateDetection) {
      this.amqpDuplicateDetection = duplicateDetection;
      return this;
   }

   @Override
   public ProtocolManagerFactory<AmqpInterceptor> getFactory() {
      return factory;
   }

   @Override
   public void updateInterceptors(List incoming, List outgoing) {
      this.incomingInterceptors.clear();
      this.incomingInterceptors.addAll(getFactory().filterInterceptors(incoming));

      this.outgoingInterceptors.clear();
      this.outgoingInterceptors.addAll(getFactory().filterInterceptors(outgoing));
   }

   @Override
   public boolean acceptsNoHandshake() {
      return false;
   }

   public Long getAmqpIdleTimeout() {
      return amqpIdleTimeout;
   }

   public ProtonProtocolManager setAmqpIdleTimeout(Long ttl) {
      logger.debug("Setting up {} as the connectionTtl", ttl);
      this.amqpIdleTimeout = ttl;
      return this;
   }

   public boolean isDirectDeliver() {
      return directDeliver;
   }

   public ProtonProtocolManager setDirectDeliver(boolean directDeliver) {
      this.directDeliver = directDeliver;
      return this;
   }

   /**
    * for outgoing
    */
   public ProtonClientProtocolManager createClientManager() {
      ProtonClientProtocolManager clientOutgoing = new ProtonClientProtocolManager(factory, server);
      return clientOutgoing;
   }

   @Override
   public ConnectionEntry createConnectionEntry(Acceptor acceptorUsed, Connection remotingConnection) {
      return internalConnectionEntry(remotingConnection, false, null, null, null, null);
   }

   /**
    * This method is not part of the ProtocolManager interface because it only makes sense on AMQP. More specifically on
    * AMQP Bridges
    */
   public ConnectionEntry createOutgoingConnectionEntry(Connection remotingConnection) {
      return internalConnectionEntry(remotingConnection, true, null, null, null, null);
   }

   public ConnectionEntry createOutgoingConnectionEntry(Connection remotingConnection, ClientSASLFactory saslFactory) {
      return internalConnectionEntry(remotingConnection, true, saslFactory, null, null, null);
   }

   public ConnectionEntry createOutgoingConnectionEntry(Connection remotingConnection, ClientSASLFactory saslFactory, Map<Symbol, Object> connectionProperties) {
      return internalConnectionEntry(remotingConnection, true, saslFactory, connectionProperties, null, null);
   }

   public ConnectionEntry createOutgoingConnectionEntry(Connection remotingConnection, ClientSASLFactory saslFactory, Map<Symbol, Object> connectionProperties, Symbol[] offeredCapabilities, Symbol[] desiredCapabilities) {
      return internalConnectionEntry(remotingConnection, true, saslFactory, connectionProperties, offeredCapabilities, desiredCapabilities);
   }

   private ConnectionEntry internalConnectionEntry(Connection remotingConnection, boolean outgoing, ClientSASLFactory saslFactory, Map<Symbol, Object> connectionProperties, Symbol[] offeredCapabilities, Symbol[] desiredCapabilities) {
      AMQPConnectionCallback connectionCallback = new AMQPConnectionCallback(this, remotingConnection, server.getExecutorFactory().getExecutor(), server);
      long ttl = ActiveMQClient.DEFAULT_CONNECTION_TTL;

      if (server.getConfiguration().getConnectionTTLOverride() != -1) {
         ttl = server.getConfiguration().getConnectionTTLOverride();
      }

      if (getAmqpIdleTimeout() != null) {
         ttl = getAmqpIdleTimeout().longValue();
      }

      if (ttl < 0) {
         ttl = 0;
      }

      String id = server.getNodeID().toString();
      boolean useCoreSubscriptionNaming = server.getConfiguration().isAmqpUseCoreSubscriptionNaming();
      AMQPConnectionContext amqpConnection = new AMQPConnectionContext(
         this, connectionCallback, id, (int) ttl, getMaxFrameSize(), AMQPConstants.Connection.DEFAULT_CHANNEL_MAX,
         useCoreSubscriptionNaming, server.getScheduledPool(), true, saslFactory, connectionProperties,
         offeredCapabilities, desiredCapabilities, outgoing);

      Executor executor = server.getExecutorFactory().getExecutor();

      ActiveMQProtonRemotingConnection protonRemotingConnection = new ActiveMQProtonRemotingConnection(this, amqpConnection, remotingConnection, executor);
      protonRemotingConnection.addFailureListener(connectionCallback);
      protonRemotingConnection.addCloseListener(connectionCallback);

      connectionCallback.setProtonConnectionDelegate(protonRemotingConnection);

      // connection entry only understands -1 otherwise we would see disconnects for no reason
      ConnectionEntry entry = new ConnectionEntry(protonRemotingConnection, executor, System.currentTimeMillis(), ttl <= 0 ? -1 : ttl);

      return entry;
   }

   @Override
   public void handleBuffer(RemotingConnection connection, ActiveMQBuffer buffer) {
      ActiveMQProtonRemotingConnection protonConnection = (ActiveMQProtonRemotingConnection) connection;

      protonConnection.bufferReceived(protonConnection.getID(), buffer);
   }

   @Override
   public void addChannelHandlers(ChannelPipeline pipeline) {

   }

   public int getAmqpCredits() {
      return amqpCredits;
   }

   public ProtonProtocolManager setAmqpCredits(int amqpCredits) {
      this.amqpCredits = amqpCredits;
      return this;
   }

   public int getAmqpLowCredits() {
      return amqpLowCredits;
   }

   public ProtonProtocolManager setAmqpLowCredits(int amqpLowCredits) {
      this.amqpLowCredits = amqpLowCredits;
      return this;
   }

   @Override
   public boolean isProtocol(byte[] array) {
      return array.length >= 4 && array[0] == (byte) 'A' && array[1] == (byte) 'M' && array[2] == (byte) 'Q' && array[3] == (byte) 'P';
   }

   @Override
   public void handshake(NettyServerConnection connection, ActiveMQBuffer buffer) {
   }

   @Override
   public List<String> websocketSubprotocolIdentifiers() {
      return websocketRegistryNames;
   }

   public String getPubSubPrefix() {
      return pubSubPrefix;
   }

   public void setPubSubPrefix(String pubSubPrefix) {
      this.pubSubPrefix = pubSubPrefix;
   }

   public int getMaxFrameSize() {
      return maxFrameSize;
   }

   public void setMaxFrameSize(int maxFrameSize) {
      this.maxFrameSize = maxFrameSize;
   }

   public String[] getSaslMechanisms() {
      return saslMechanisms;
   }

   public void setSaslMechanisms(String[] saslMechanisms) {
      this.saslMechanisms = saslMechanisms;
   }

   public String getSaslLoginConfigScope() {
      return saslLoginConfigScope;
   }

   public void setSaslLoginConfigScope(String saslLoginConfigScope) {
      this.saslLoginConfigScope = saslLoginConfigScope;
   }

   @Override
   public void setAnycastPrefix(String anycastPrefix) {
      for (String prefix : anycastPrefix.split(",")) {
         prefixes.put(SimpleString.of(prefix), RoutingType.ANYCAST);
      }
   }

   @Override
   public void setMulticastPrefix(String multicastPrefix) {
      for (String prefix : multicastPrefix.split(",")) {
         prefixes.put(SimpleString.of(prefix), RoutingType.MULTICAST);
      }
   }

   @Override
   public void setTemporaryAnycastPrefix(String temporaryAnycastPrefix) {
      for (String prefix : temporaryAnycastPrefix.split(",")) {
         prefixes.put(SimpleString.of(prefix), RoutingType.ANYCAST);
         temporaryPrefixes.put(SimpleString.of(prefix), RoutingType.ANYCAST);
      }
   }

   @Override
   public void setTemporaryMulticastPrefix(String temporaryMulticastPrefix) {
      for (String prefix : temporaryMulticastPrefix.split(",")) {
         prefixes.put(SimpleString.of(prefix), RoutingType.MULTICAST);
         temporaryPrefixes.put(SimpleString.of(prefix), RoutingType.MULTICAST);
      }
   }

   @Override
   public Map<SimpleString, RoutingType> getPrefixes() {
      return prefixes;
   }

   @Override
   public Map<SimpleString, RoutingType> getTemporaryPrefixes() {
      return temporaryPrefixes;
   }

   @Override
   public AMQPRoutingHandler getRoutingHandler() {
      return routingHandler;
   }

   public String invokeIncoming(Message message, ActiveMQProtonRemotingConnection connection) {
      // For tunneled messages we need to check the type as our interceptor only cares about
      // AMQP message right now so there's not notification point for other types that cross
      if (incomingInterceptors != null && !incomingInterceptors.isEmpty()) {
         if (message instanceof AMQPMessage amqpMessage) {
            return super.invokeInterceptors(this.incomingInterceptors, amqpMessage, connection);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public String invokeOutgoing(Message message, ActiveMQProtonRemotingConnection connection) {
      // For tunneled messages we need to check the type as our interceptor only cares about
      // AMQP message right now so there's not notification point for other types that cross
      if (outgoingInterceptors != null && !outgoingInterceptors.isEmpty()) {
         if (message instanceof AMQPMessage amqpMessage) {
            return super.invokeInterceptors(this.outgoingInterceptors, amqpMessage, connection);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public int getInitialRemoteMaxFrameSize() {
      return initialRemoteMaxFrameSize;
   }

   public void setInitialRemoteMaxFrameSize(int initialRemoteMaxFrameSize) {
      this.initialRemoteMaxFrameSize = initialRemoteMaxFrameSize;
   }

   /**
    * {@return true if transient delivery errors should be handled with a Modified disposition
    * (if permitted by link)}
    */
   public boolean isUseModifiedForTransientDeliveryErrors() {
      return this.amqpUseModifiedForTransientDeliveryErrors;
   }

   /**
    * Sets if transient delivery errors should be handled with a Modified disposition (if permitted by link)
    */
   public ProtonProtocolManager setAmqpUseModifiedForTransientDeliveryErrors(boolean amqpUseModifiedForTransientDeliveryErrors) {
      this.amqpUseModifiedForTransientDeliveryErrors = amqpUseModifiedForTransientDeliveryErrors;
      return this;
   }

   public void setAmqpTreatRejectAsUnmodifiedDeliveryFailed(final boolean amqpTreatRejectAsUnmodifiedDeliveryFailed) {
      this.amqpTreatRejectAsUnmodifiedDeliveryFailed = amqpTreatRejectAsUnmodifiedDeliveryFailed;
   }

   public boolean isAmqpTreatRejectAsUnmodifiedDeliveryFailed() {
      return this.amqpTreatRejectAsUnmodifiedDeliveryFailed;
   }

   /**
    * {@return true if transient delivery errors should be handled by draining link credit from the remote sender}
    */
   public boolean isDrainOnTransientDeliveryErrors() {
      return this.amqpDrainOnTransientDeliveryErrors;
   }

   /**
    * Sets if transient delivery errors should be handled by draining link credit from the remote sender
    *
    * @param amqpDrainOnTransientDeliveryErrors
    *    Set to <code>true</code> if senders should be drained on transient delivery errors.
    *
    * @return this protocol manager instance.
    */
   public ProtonProtocolManager setAmqpDrainOnTransientDeliveryErrors(boolean amqpDrainOnTransientDeliveryErrors) {
      this.amqpDrainOnTransientDeliveryErrors = amqpDrainOnTransientDeliveryErrors;
      return this;
   }

   /**
    * {@return the time in milliseconds to wait for remote sender once a link quiesce is initiated by this peer}
    */
   public int getLinkQuiesceTimeout() {
      return amqpLinkQuiesceTimeout;
   }

   /**
    * Sets the time in milliseconds to wait before closing a remote sender link if the server has requested
    * that the link drain all outstanding credit an complete pending settlements. A value less than or equal
    * to zero disables drain timeouts.
    *
    * @param amqpLinkQuiesceTimeout
    *    The time in milliseconds to wait for quiesce to complete.
    *
    * @return this protocol manager instance.
    */
   public ProtonProtocolManager setAmqpLinkQuiesceTimeout(int amqpLinkQuiesceTimeout) {
      this.amqpLinkQuiesceTimeout = amqpLinkQuiesceTimeout;
      return this;
   }
}
