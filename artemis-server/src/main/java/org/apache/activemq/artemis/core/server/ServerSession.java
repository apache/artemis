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
package org.apache.activemq.artemis.core.server;

import org.apache.activemq.artemis.api.core.AutoCreateResult;
import org.apache.activemq.artemis.json.JsonArrayBuilder;
import javax.transaction.xa.Xid;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import org.apache.activemq.artemis.Closeable;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

public interface ServerSession extends SecurityAuth {

   String getName();

   int getMinLargeMessageSize();

   Object getConnectionID();

   Executor getSessionExecutor();

   /**
    * Certain protocols may create an internal session that shouldn't go through security checks. Make sure you don't
    * expose this property through any protocol layer as that would be a security breach
    */
   void enableSecurity();

   void disableSecurity();

   @Override
   RemotingConnection getRemotingConnection();

   void transferConnection(RemotingConnection newConnection);

   Transaction newTransaction();

   boolean removeConsumer(long consumerID) throws Exception;

   List<Long> acknowledge(long consumerID, long messageID) throws Exception;

   void individualAcknowledge(long consumerID, long messageID) throws Exception;

   void individualCancel(long consumerID, long messageID, boolean failed) throws Exception;

   void expire(long consumerID, long messageID) throws Exception;

   void rollback(boolean considerLastMessageAsDelivered) throws Exception;

   void commit() throws Exception;

   void xaCommit(Xid xid, boolean onePhase) throws Exception;

   void xaEnd(Xid xid) throws Exception;

   void xaForget(Xid xid) throws Exception;

   void xaJoin(Xid xid) throws Exception;

   void xaPrepare(Xid xid) throws Exception;

   void xaResume(Xid xid) throws Exception;

   void xaRollback(Xid xid) throws Exception;

   void xaStart(Xid xid) throws Exception;

   void xaFailed(Xid xid) throws Exception;

   void xaSuspend() throws Exception;

   void markTXFailed(Throwable e);

   List<Xid> xaGetInDoubtXids();

   int xaGetTimeout();

   void xaSetTimeout(int timeout);

   void start();

   void stop();

   void addCloseable(Closeable closeable);

   AutoCreateResult checkAutoCreate(QueueConfiguration queueConfiguration) throws Exception;

   ServerConsumer createConsumer(long consumerID,
                                 SimpleString queueName,
                                 SimpleString filterString,
                                 int priority,
                                 boolean browseOnly,
                                 boolean supportLargeMessage,
                                 Integer credits) throws Exception;

   ServerConsumer createConsumer(long consumerID,
                                 SimpleString queueName,
                                 SimpleString filterString,
                                 int priority,
                                 boolean browseOnly,
                                 boolean supportLargeMessage,
                                 Integer credits,
                                 boolean enforceSecurity) throws Exception;

   /**
    * To be used by protocol heads that needs to control the transaction outside the session context.
    */
   void resetTX(Transaction transaction);

   @Deprecated
   Queue createQueue(SimpleString address,
                     SimpleString name,
                     RoutingType routingType,
                     SimpleString filterString,
                     boolean temporary,
                     boolean durable) throws Exception;

   @Deprecated
   Queue createQueue(AddressInfo address,
                     SimpleString name,
                     SimpleString filterString,
                     boolean temporary,
                     boolean durable) throws Exception;

   /**
    * Create queue with default delivery mode
    */
   @Deprecated
   Queue createQueue(SimpleString address,
                     SimpleString name,
                     SimpleString filterString,
                     boolean temporary,
                     boolean durable) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address,
                     SimpleString name,
                     RoutingType routingType,
                     SimpleString filterString,
                     boolean temporary,
                     boolean durable,
                     int maxConsumers,
                     boolean purgeOnNoConsumers,
                     boolean autoCreated) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address,
                     SimpleString name,
                     RoutingType routingType,
                     SimpleString filterString,
                     boolean temporary,
                     boolean durable,
                     int maxConsumers,
                     boolean purgeOnNoConsumers,
                     Boolean exclusive,
                     Boolean lastValue,
                     boolean autoCreated) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address,
                     SimpleString name,
                     RoutingType routingType,
                     SimpleString filterString,
                     boolean temporary,
                     boolean durable,
                     int maxConsumers,
                     boolean purgeOnNoConsumers,
                     Boolean exclusive,
                     Boolean groupRebalance,
                     Integer groupBuckets,
                     Boolean lastValue,
                     SimpleString lastValueKey,
                     Boolean nonDestructive,
                     Integer consumersBeforeDispatch,
                     Long delayBeforeDispatch,
                     Boolean autoDelete,
                     Long autoDeleteDelay,
                     Long autoDeleteMessageCount,
                     boolean autoCreated) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address,
                     SimpleString name,
                     RoutingType routingType,
                     SimpleString filterString,
                     boolean temporary,
                     boolean durable,
                     int maxConsumers,
                     boolean purgeOnNoConsumers,
                     Boolean exclusive,
                     Boolean groupRebalance,
                     Integer groupBuckets,
                     SimpleString groupFirstKey,
                     Boolean lastValue,
                     SimpleString lastValueKey,
                     Boolean nonDestructive,
                     Integer consumersBeforeDispatch,
                     Long delayBeforeDispatch,
                     Boolean autoDelete,
                     Long autoDeleteDelay,
                     Long autoDeleteMessageCount,
                     boolean autoCreated) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address,
                     SimpleString name,
                     RoutingType routingType,
                     SimpleString filterString,
                     boolean temporary,
                     boolean durable,
                     int maxConsumers,
                     boolean purgeOnNoConsumers,
                     Boolean exclusive,
                     Boolean groupRebalance,
                     Integer groupBuckets,
                     SimpleString groupFirstKey,
                     Boolean lastValue,
                     SimpleString lastValueKey,
                     Boolean nonDestructive,
                     Integer consumersBeforeDispatch,
                     Long delayBeforeDispatch,
                     Boolean autoDelete,
                     Long autoDeleteDelay,
                     Long autoDeleteMessageCount,
                     boolean autoCreated,
                     Long ringSize) throws Exception;

   @Deprecated
   Queue createQueue(SimpleString address,
                     SimpleString name,
                     RoutingType routingType,
                     SimpleString filterString,
                     boolean temporary,
                     boolean durable,
                     boolean autoCreated) throws Exception;

   @Deprecated
   Queue createQueue(AddressInfo addressInfo,
                     SimpleString name,
                     SimpleString filterString,
                     boolean temporary,
                     boolean durable,
                     boolean autoCreated) throws Exception;

   @Deprecated
   Queue createQueue(AddressInfo addressInfo,
                     SimpleString name,
                     SimpleString filterString,
                     boolean temporary,
                     boolean durable,
                     Boolean exclusive,
                     Boolean lastValue,
                     boolean autoCreated) throws Exception;

   /**
    * This method invokes {@link ActiveMQServer#createQueue(QueueConfiguration)} with a few client-specific additions:
    * <ul>
    * <li>set the routing type based on the prefixes configured on the acceptor used by the client
    * <li>strip any prefixes from the address and queue names (if applicable)
    * <li>check authorization based on the client's credentials
    * <li>enforce queue creation resource limit
    * <li>set up callbacks to clean up temporary queues once the client disconnects
    * </ul>
    *
    * @param queueConfiguration the configuration to use when creating the queue
    * @return the {@code Queue} instance that was created
    */
   Queue createQueue(QueueConfiguration queueConfiguration) throws Exception;

   AddressInfo createAddress(SimpleString address,
                             EnumSet<RoutingType> routingTypes,
                             boolean autoCreated) throws Exception;

   AddressInfo createAddress(SimpleString address,
                             RoutingType routingType,
                             boolean autoCreated) throws Exception;

   AddressInfo createAddress(AddressInfo addressInfo,
                             boolean autoCreated) throws Exception;

   void deleteQueue(SimpleString name) throws Exception;

   void deleteQueue(SimpleString name, boolean enforceSecurity) throws Exception;

   void deleteAddress(SimpleString name) throws Exception;

   ServerConsumer createConsumer(long consumerID,
                                 SimpleString queueName,
                                 SimpleString filterString,
                                 boolean browseOnly) throws Exception;

   ServerConsumer createConsumer(long consumerID,
                                 SimpleString queueName,
                                 SimpleString filterString,
                                 boolean browseOnly,
                                 boolean supportLargeMessage,
                                 Integer credits) throws Exception;

   QueueQueryResult executeQueueQuery(SimpleString name) throws Exception;

   AddressQueryResult executeAddressQuery(SimpleString name) throws Exception;

   BindingQueryResult executeBindingQuery(SimpleString address) throws Exception;

   void closeConsumer(long consumerID) throws Exception;

   void receiveConsumerCredits(long consumerID, int credits) throws Exception;

   RoutingStatus send(Transaction tx,
                      Message message,
                      boolean direct,
                      String senderName,
                      boolean noAutoCreateQueue) throws Exception;

   RoutingStatus send(Transaction tx,
                      Message message,
                      boolean direct,
                      String senderName,
                      boolean noAutoCreateQueue,
                      RoutingContext routingContext) throws Exception;

   RoutingStatus send(Transaction tx,
                      SimpleString address,
                      RoutingType routingType,
                      Message message,
                      boolean direct,
                      String senderName,
                      boolean noAutoCreateQueue,
                      RoutingContext routingContext) throws Exception;

   RoutingStatus doSend(Transaction tx,
                        Message msg,
                        SimpleString originalAddress,
                        boolean direct,
                        String senderName,
                        boolean noAutoCreateQueue) throws Exception;

   RoutingStatus doSend(Transaction tx,
                        Message msg,
                        SimpleString originalAddress,
                        boolean direct,
                        String senderName,
                        boolean noAutoCreateQueue,
                        RoutingContext routingContext) throws Exception;

   RoutingStatus doSend(Transaction tx,
                        RoutingType routingType,
                        Message msg,
                        SimpleString originalAddress,
                        boolean direct,
                        String senderName,
                        boolean noAutoCreateQueue,
                        RoutingContext routingContext) throws Exception;

   RoutingStatus send(Message message, boolean direct, String senderName, boolean noAutoCreateQueue) throws Exception;

   RoutingStatus send(Message message, boolean direct, String senderName) throws Exception;

   void forceConsumerDelivery(long consumerID, long sequence) throws Exception;

   void requestProducerCredits(SimpleString address, int credits) throws Exception;

   void close(boolean failed) throws Exception;

   void close(boolean failed, boolean force) throws Exception;

   void setTransferring(boolean transferring);

   Set<ServerConsumer> getServerConsumers();

   void addMetaData(String key, String data) throws Exception;

   boolean addUniqueMetaData(String key, String data) throws Exception;

   String getMetaData(String key);

   Map<String, String> getMetaData();

   /**
    * Add all the producers detail to the JSONArray object. This is a method to be used by the management layer.
    */
   void describeProducersInfo(JsonArrayBuilder objs);

   long getCreationTime();

   OperationContext getSessionContext();

   Transaction getCurrentTransaction();

   ServerConsumer locateConsumer(long consumerID) throws Exception;

   boolean isClosed();

   @Deprecated
   void createSharedQueue(SimpleString address,
                     SimpleString name,
                     RoutingType routingType,
                     SimpleString filterString,
                     boolean durable,
                     Integer maxConsumers,
                     Boolean purgeOnNoConsumers,
                     Boolean exclusive,
                     Boolean lastValue) throws Exception;

   @Deprecated
   void createSharedQueue(SimpleString address,
                          SimpleString name,
                          RoutingType routingType,
                          SimpleString filterString,
                          boolean durable,
                          Integer maxConsumers,
                          Boolean purgeOnNoConsumers,
                          Boolean exclusive,
                          Boolean groupRebalance,
                          Integer groupBuckets,
                          Boolean lastValue,
                          SimpleString lastValueKey,
                          Boolean nonDestructive,
                          Integer consumersBeforeDispatch,
                          Long delayBeforeDispatch,
                          Boolean autoDelete,
                          Long autoDeleteDelay,
                          Long autoDeleteMessageCount) throws Exception;

   @Deprecated
   void createSharedQueue(SimpleString address,
                          SimpleString name,
                          RoutingType routingType,
                          SimpleString filterString,
                          boolean durable,
                          Integer maxConsumers,
                          Boolean purgeOnNoConsumers,
                          Boolean exclusive,
                          Boolean groupRebalance,
                          Integer groupBuckets,
                          SimpleString groupFirstKey,
                          Boolean lastValue,
                          SimpleString lastValueKey,
                          Boolean nonDestructive,
                          Integer consumersBeforeDispatch,
                          Long delayBeforeDispatch,
                          Boolean autoDelete,
                          Long autoDeleteDelay,
                          Long autoDeleteMessageCount) throws Exception;

   @Deprecated
   void createSharedQueue(SimpleString address,
                          SimpleString name,
                          RoutingType routingType,
                          boolean durable,
                          SimpleString filterString) throws Exception;

   @Deprecated
   void createSharedQueue(SimpleString address,
                          SimpleString name,
                          boolean durable,
                          SimpleString filterString) throws Exception;

   void createSharedQueue(QueueConfiguration queueConfiguration) throws Exception;

   List<MessageReference> getInTXMessagesForConsumer(long consumerId);

   List<MessageReference> getInTxLingerMessages();

   void addLingerConsumer(ServerConsumer consumer);

   String getValidatedUser();

   SimpleString getMatchingQueue(SimpleString address, RoutingType routingType) throws Exception;

   SimpleString getMatchingQueue(SimpleString address,
                                 SimpleString queueName,
                                 RoutingType routingType) throws Exception;

   AddressInfo getAddress(SimpleString address);

   /**
    * Strip the prefix (if it exists) from the address based on the prefixes provided to the ServerSession constructor.
    *
    * @param address the address to inspect
    * @return the canonical (i.e. non-prefixed) address name
    */
   SimpleString removePrefix(SimpleString address);

   /**
    * Get the prefix (if it exists) from the address based on the prefixes provided to the ServerSession constructor.
    *
    * @param address the address to inspect
    * @return the canonical (i.e. non-prefixed) address name
    */
   SimpleString getPrefix(SimpleString address);

   /**
    * Get the canonical (i.e. non-prefixed) address and the corresponding routing-type.
    *
    * @param addressInfo the address to inspect
    * @return a {@code org.apache.activemq.artemis.api.core.Pair} representing the canonical (i.e. non-prefixed) address
    * name and the {@code org.apache.activemq.artemis.api.core.RoutingType} corresponding to the that prefix.
    */
   AddressInfo getAddressAndRoutingType(AddressInfo addressInfo);

   RoutingType getRoutingTypeFromPrefix(SimpleString address, RoutingType defaultRoutingType);

   /**
    * Get the canonical (i.e. non-prefixed) address and the corresponding routing-type.
    *
    * @param address             the address to inspect
    * @param defaultRoutingTypes a the {@code java.util.Set} of {@code org.apache.activemq.artemis.api.core.RoutingType}
    *                            objects to return if no prefix match is found.
    * @return a {@code org.apache.activemq.artemis.api.core.Pair} representing the canonical (i.e. non-prefixed) address
    * name and the {@code java.util.Set} of {@code org.apache.activemq.artemis.api.core.RoutingType} objects
    * corresponding to the that prefix.
    */
   Pair<SimpleString, EnumSet<RoutingType>> getAddressAndRoutingTypes(SimpleString address,
                                                                      EnumSet<RoutingType> defaultRoutingTypes);

   void addProducer(String name, String protocol, String address);

   void removeProducer(String ID);

   Collection<ServerProducer> getServerProducers();

   String getDefaultAddress();

   int getConsumerCount();

   int getProducerCount();

   int getDefaultConsumerWindowSize(SimpleString address);

   String toManagementString();
}