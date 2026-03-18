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
package org.apache.activemq.artemis.core.server.cluster.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQAddressDoesNotExistException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.BindingType;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.ActiveMQServerSideProtocolManagerFactory;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.cluster.MessageFlowRecord;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.UUID;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A bridge with extra functionality only available when the server is clustered.
 * <p>
 * Such as such adding extra properties and setting up notifications between the nodes.
 */
public class ClusterConnectionBridge extends BridgeImpl {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final ClusterConnection clusterConnection;

   private final ClusterManager clusterManager;

   private final MessageFlowRecord flowRecord;

   private final SimpleString managementAddress;

   private final SimpleString managementNotificationAddress;


   private ClientConsumer notifConsumer;

   private final SimpleString idsHeaderName;

   private final long targetNodeEventUID;

   private final StorageManager storageManager;

   private final ServerLocatorInternal discoveryLocator;

   private final String storeAndForwardPrefix;

   public ClusterConnectionBridge(final ClusterConnection clusterConnection,
                                  final ClusterManager clusterManager,
                                  final ServerLocatorInternal targetLocator,
                                  final ServerLocatorInternal discoveryLocator,
                                  final int initialConnectAttempts,
                                  final int reconnectAttempts,
                                  final long retryInterval,
                                  final double retryMultiplier,
                                  final long maxRetryInterval,
                                  final UUID nodeUUID,
                                  final long targetNodeEventUID,
                                  final String targetNodeID,
                                  final SimpleString name,
                                  final Queue queue,
                                  final Executor executor,
                                  final Filter filterString,
                                  final SimpleString forwardingAddress,
                                  final ScheduledExecutorService scheduledExecutor,
                                  final TransformerConfiguration transformer,
                                  final boolean useDuplicateDetection,
                                  final String user,
                                  final String password,
                                  final ActiveMQServer server,
                                  final SimpleString managementAddress,
                                  final SimpleString managementNotificationAddress,
                                  final MessageFlowRecord flowRecord,
                                  final TransportConfiguration connector,
                                  final String storeAndForwardPrefix,
                                  final StorageManager storageManager,
                                  final String clientId) throws ActiveMQException {
      super(targetLocator, new BridgeConfiguration()
         .setName(Objects.toString(name, null))
         .setInitialConnectAttempts(initialConnectAttempts)
         .setReconnectAttempts(reconnectAttempts)
         .setReconnectAttemptsOnSameNode(0) // reconnectAttemptsOnSameNode means nothing on the clustering bridge since we always try the same
         .setRetryInterval(retryInterval)
         .setRetryIntervalMultiplier(retryMultiplier)
         .setMaxRetryInterval(maxRetryInterval)
         .setFilterString(Objects.toString(filterString, null))
         .setForwardingAddress(Objects.toString(forwardingAddress, null))
         .setUseDuplicateDetection(useDuplicateDetection)
         .setUser(user)
         .setPassword(password)
         .setTransformerConfiguration(transformer)
         .setRoutingType(ComponentConfigurationRoutingType.valueOf(ActiveMQDefaultConfiguration.getDefaultBridgeRoutingType()))
         .setClientId(clientId), nodeUUID, queue, executor, scheduledExecutor, server);

      this.discoveryLocator = discoveryLocator;

      idsHeaderName = Message.HDR_ROUTE_TO_IDS.concat(name);

      this.clusterConnection = clusterConnection;

      this.clusterManager = clusterManager;

      this.targetNodeEventUID = targetNodeEventUID;
      this.targetNodeID = targetNodeID;
      this.managementAddress = managementAddress;
      this.managementNotificationAddress = managementNotificationAddress;
      this.flowRecord = flowRecord;

      if (logger.isTraceEnabled()) {
         logger.trace("Setting up bridge between {} and {}", clusterConnection.getConnector(), targetLocator, new Exception("trace"));
      }

      this.storeAndForwardPrefix = storeAndForwardPrefix;

      this.storageManager = storageManager;
   }

   @Override
   protected ClientSessionFactoryInternal createSessionFactory() throws Exception {
      serverLocator.setProtocolManagerFactory(ActiveMQServerSideProtocolManagerFactory.getInstance(serverLocator, storageManager));
      ClientSessionFactoryInternal factory = (ClientSessionFactoryInternal) serverLocator.createSessionFactory(targetNodeID);
      //if it is null then its possible the broker was removed after a disconnect so lets try the original connectors
      if (factory == null) {
         factory = reconnectOnOriginalNode();
         if (factory == null) {
            return null;
         }
      }
      setSessionFactory(factory);

      if (factory == null) {
         return null;
      }
      factory.setReconnectAttempts(0);
      factory.getConnection().addFailureListener(this);
      return factory;
   }

   @Override
   protected Message beforeForward(final Message message, final SimpleString forwardingAddress) throws ActiveMQException {
      // We make a copy of the message, then we strip out the unwanted routing id headers and leave
      // only
      // the one pertinent for the address node - this is important since different queues on different
      // nodes could have same queue ids
      // Note we must copy since same message may get routed to other nodes which require different headers
      Message messageCopy = message.copy();

      logger.trace("Clustered bridge  copied message {} as {} before delivery", message, messageCopy);

      // TODO - we can optimise this

      Set<SimpleString> propNames = new HashSet<>(messageCopy.getPropertyNames());

      byte[] queueIds = message.getExtraBytesProperty(idsHeaderName);
      if (queueIds == null) {
         throw ActiveMQMessageBundle.BUNDLE.noQueueIdsDefined(idsHeaderName);
      }

      for (SimpleString propName : propNames) {
         if (propName.startsWith(Message.HDR_ROUTE_TO_IDS)) {
            messageCopy.removeProperty(propName);
         }
      }

      messageCopy.putExtraBytesProperty(Message.HDR_ROUTE_TO_IDS, queueIds);

      messageCopy = super.beforeForwardingNoCopy(messageCopy, forwardingAddress);

      return messageCopy;
   }

   private void setupNotificationConsumer() throws Exception {
      if (flowRecord != null) {
         if (logger.isDebugEnabled()) {
            logger.debug("Setting up notificationConsumer between {} and {} clusterConnection = {} on server {}",
                          this.clusterConnection.getConnector(), flowRecord.getBridge().getForwardingConnection(),
                          this.clusterConnection.getName(), clusterConnection.getServer());
         }
         flowRecord.reset();

         if (notifConsumer != null) {
            try {
               logger.debug("Closing notification Consumer for reopening {} on bridge {}", notifConsumer, this.getName());
               notifConsumer.close();

               notifConsumer = null;
            } catch (ActiveMQException e) {
               ActiveMQServerLogger.LOGGER.errorClosingConsumer(e);
            }
         }

         // Get the queue data
         String qName = "notif." + UUIDGenerator.getInstance().generateStringUUID() +
            "." +
            clusterConnection.getServer().toString().replaceAll(CompositeAddress.SEPARATOR, "_");

         SimpleString notifQueueName = SimpleString.of(qName);

         SimpleString filter = SimpleString.of("(" + ManagementHelper.HDR_BINDING_TYPE + " <> " + BindingType.DIVERT.toInt() +
                                                   " OR "
                                                   + ManagementHelper.HDR_BINDING_TYPE + " IS NULL)" +
                                                   " AND " +
                                                   ManagementHelper.HDR_NOTIFICATION_TYPE +
                                                   " IN ('" +
                                                   CoreNotificationType.SESSION_CREATED +
                                                   "', '" +
                                                   CoreNotificationType.BINDING_ADDED +
                                                   "', '" +
                                                   CoreNotificationType.BINDING_REMOVED +
                                                   "', '" +
                                                   CoreNotificationType.BINDING_UPDATED +
                                                   "', '" +
                                                   CoreNotificationType.CONSUMER_CREATED +
                                                   "', '" +
                                                   CoreNotificationType.CONSUMER_CLOSED +
                                                   "', '" +
                                                   CoreNotificationType.PROPOSAL +
                                                   "', '" +
                                                   CoreNotificationType.PROPOSAL_RESPONSE +
                                                   "', '" +
                                                   CoreNotificationType.UNPROPOSAL +
                                                   "')" +
                                                   " AND " +
                                                   ManagementHelper.HDR_DISTANCE +
                                                   " < " +
                                                   flowRecord.getMaxHops() +
                                                   " AND (" +
                                                   createSelectorFromAddress(appendIgnoresToFilter(flowRecord.getAddress())) +
                                                   ") AND (" +
                                                   createPermissiveManagementNotificationToFilter() +
                                                   ")");

         sessionConsumer.createQueue(QueueConfiguration.of(notifQueueName)
                                        .setAddress(managementNotificationAddress)
                                        .setFilterString(filter)
                                        .setDurable(false)
                                        .setTemporary(true)
                                        .setRoutingType(RoutingType.MULTICAST));

         notifConsumer = sessionConsumer.createConsumer(notifQueueName);

         notifConsumer.setMessageHandler(flowRecord);

         sessionConsumer.start();

         ClientMessage message = sessionConsumer.createMessage(false);
         if (logger.isTraceEnabled()) {
            logger.trace("Requesting sendQueueInfoToQueue through {}", this, new Exception("trace"));
         }
         ManagementHelper.putOperationInvocation(message, ResourceNames.BROKER, "sendQueueInfoToQueue", notifQueueName.toString(), flowRecord.getAddress());

         try (ClientProducer prod = sessionConsumer.createProducer(managementAddress)) {
            logger.debug("Cluster connection bridge on {} requesting information on queues", clusterConnection);

            prod.send(message);
         }
      }
   }

   /**
    * Takes in a string of an address filter or comma separated list and generates an appropriate JMS selector for
    * filtering queues.
    */
   public static String createSelectorFromAddress(String address) {
      StringBuilder stringBuilder = new StringBuilder();

      // Support standard address (not a list) case.
      if (!address.contains(",")) {
         if (address.startsWith("!")) {
            stringBuilder.append(ManagementHelper.HDR_ADDRESS + " NOT LIKE '" + address.substring(1, address.length()) + "%'");
         } else {
            stringBuilder.append(ManagementHelper.HDR_ADDRESS + " LIKE '" + address + "%'");
         }
         return stringBuilder.toString();
      }

      // For comma separated lists build a JMS selector statement based on the list items
      return buildSelectorFromArray(address.split(","));
   }

   public static String buildSelectorFromArray(String[] list) {
      List<String> includes = new ArrayList<>();
      List<String> excludes = new ArrayList<>();

      // Split the list into addresses to match and addresses to exclude.
      for (String s : list) {
         if (s.startsWith("!")) {
            excludes.add(s.substring(1, s.length()));
         } else {
            includes.add(s);
         }
      }

      // Build the address matching part of the selector
      StringBuilder builder = new StringBuilder("(");
      if (!includes.isEmpty()) {
         if (!excludes.isEmpty()) {
            builder.append("(");
         }
         for (int i = 0; i < includes.size(); i++) {
            builder.append("(" + ManagementHelper.HDR_ADDRESS + " LIKE '" + includes.get(i) + "%')");
            if (i < includes.size() - 1) {
               builder.append(" OR ");
            }
         }
         if (!excludes.isEmpty()) {
            builder.append(")");
         }
      }

      // Build the address exclusion part of the selector
      if (!excludes.isEmpty()) {
         if (!includes.isEmpty()) {
            builder.append(" AND (");
         }
         for (int i = 0; i < excludes.size(); i++) {
            builder.append("(" + ManagementHelper.HDR_ADDRESS + " NOT LIKE '" + excludes.get(i) + "%')");
            if (i < excludes.size() - 1) {
               builder.append(" AND ");
            }
         }
         if (!includes.isEmpty()) {
            builder.append(")");
         }
      }
      builder.append(")");
      return builder.toString();
   }

   private String appendIgnoresToFilter(String filterString) {
      if (filterString != null && !filterString.isEmpty()) {
         filterString += ",";
      }
      filterString += "!" + storeAndForwardPrefix;
      filterString += ",!" + managementAddress;
      //add any protocol specific ignored addresses
      for (String ignoreAddress : clusterManager.getProtocolIgnoredAddresses()) {
         filterString += ",!" + ignoreAddress;
      }
      return filterString;
   }

   /**
    * Create a filter rule,in addition to SESSION_CREATED notifications, all other notifications using
    * managementNotificationAddress as the routing address will be filtered.
    */
   private String createPermissiveManagementNotificationToFilter() {
      StringBuilder filterBuilder = new StringBuilder(ManagementHelper.HDR_NOTIFICATION_TYPE).append(" = '")
              .append(CoreNotificationType.SESSION_CREATED).append("' OR (").append(ManagementHelper.HDR_ADDRESS)
              .append(" NOT LIKE '").append(managementNotificationAddress).append("%')");
      return filterBuilder.toString();
   }

   @Override
   protected void nodeUP(TopologyMember member, boolean last) {
      if (member != null && targetNodeID != null && !this.targetNodeID.equals(member.getNodeId())) {
         //A ClusterConnectionBridge (identified by holding an internal queue)
         //never re-connects to another node here. It only connects to its original
         //target node (from the ClusterConnection) or its backups. That's why
         //we put a return here.
         return;
      }
      super.nodeUP(member, last);
   }


   @Override
   protected void afterConnect() throws Exception {
      super.afterConnect();
      setupNotificationConsumer();
   }

   @Override
   protected void tryScheduleRetryReconnect(final ActiveMQExceptionType type) {
      if (type != ActiveMQExceptionType.DISCONNECTED) {
         scheduleRetryConnect();
      }
   }

   @Override
   protected void fail(final boolean permanently, final boolean scaleDown) {
      logger.debug("Cluster Bridge {} failed, permanently={}", this.getName(), permanently);
      super.fail(permanently, scaleDown);

      if (permanently) {
         logger.debug("cluster node for bridge {} is permanently down", this.getName());
         clusterConnection.removeRecord(targetNodeID);

         if (scaleDown) {
            executor.execute(() -> {
               logger.debug("Scaling down queue {}", queue);
               try {
                  queue.deleteQueue(true);
                  queue.removeAddress();
               } catch (ActiveMQAddressDoesNotExistException e) {
                  logger.debug("ActiveMQAddressDoesNotExistException during scale down for queue {}", queue);
               } catch (Exception e) {
                  logger.warn(e.getMessage(), e);
               }
            });
         }
      } else {
         clusterConnection.disconnectRecord(targetNodeID);
      }
   }
}
