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

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQRedirectedException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueAttributes;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.FailoverEventListener;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.core.client.ActiveMQClientMessageBundle;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.ConsumerContext;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.activemq.artemis.spi.core.remoting.SessionContext;
import org.apache.activemq.artemis.utils.ConfirmationWindowWarning;
import org.apache.activemq.artemis.utils.TokenBucketLimiterImpl;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.XidCodecSupport;
import org.jboss.logging.Logger;

public final class ClientSessionImpl implements ClientSessionInternal, FailureListener {

   private static final Logger logger = Logger.getLogger(ClientSessionImpl.class);

   private final Map<String, String> metadata = new HashMap<>();

   private final ClientSessionFactoryInternal sessionFactory;

   private String name;

   private final String username;

   private final String password;

   private final boolean xa;

   private final Executor executor;

   // to be sent to consumers as consumers will need a separate consumer for flow control
   private final Executor flowControlExecutor;

   /**
    * All access to producers are guarded (i.e. synchronized) on itself.
    */
   private final Set<ClientProducerInternal> producers = new HashSet<>();

   // Consumers must be an ordered map so if we fail we recreate them in the same order with the same ids
   private final Map<ConsumerContext, ClientConsumerInternal> consumers = new LinkedHashMap<>();

   private volatile boolean closed;

   private final boolean autoCommitAcks;

   private final boolean preAcknowledge;

   private final boolean autoCommitSends;

   private final boolean blockOnAcknowledge;

   private final boolean autoGroup;

   private final int ackBatchSize;

   private final int consumerWindowSize;

   private final int consumerMaxRate;

   private final int confirmationWindowSize;

   private final int producerMaxRate;

   private final boolean blockOnNonDurableSend;

   private final boolean blockOnDurableSend;

   private final int minLargeMessageSize;

   private final boolean compressLargeMessages;

   private volatile int initialMessagePacketSize;

   private final boolean cacheLargeMessageClient;

   private final SessionContext sessionContext;

   // For testing only
   private boolean forceNotSameRM;

   private final ClientProducerCreditManager producerCreditManager;

   private volatile boolean started;

   private volatile boolean rollbackOnly;

   private volatile boolean workDone;

   private final String groupID;

   private volatile boolean inClose;

   private volatile boolean mayAttemptToFailover = true;

   private volatile boolean resetCreditManager = false;

   /**
    * Current XID. this will be used in case of failover
    */
   private Xid currentXID;

   private final AtomicInteger concurrentCall = new AtomicInteger(0);

   private final ConfirmationWindowWarning confirmationWindowWarning;

   private final Executor closeExecutor;

   private final CoreMessageObjectPools coreMessageObjectPools = new CoreMessageObjectPools();

   ClientSessionImpl(final ClientSessionFactoryInternal sessionFactory,
                     final String name,
                     final String username,
                     final String password,
                     final boolean xa,
                     final boolean autoCommitSends,
                     final boolean autoCommitAcks,
                     final boolean preAcknowledge,
                     final boolean blockOnAcknowledge,
                     final boolean autoGroup,
                     final int ackBatchSize,
                     final int consumerWindowSize,
                     final int consumerMaxRate,
                     final int confirmationWindowSize,
                     final int producerWindowSize,
                     final int producerMaxRate,
                     final boolean blockOnNonDurableSend,
                     final boolean blockOnDurableSend,
                     final boolean cacheLargeMessageClient,
                     final int minLargeMessageSize,
                     final boolean compressLargeMessages,
                     final int initialMessagePacketSize,
                     final String groupID,
                     final SessionContext sessionContext,
                     final Executor executor,
                     final Executor flowControlExecutor,
                     final Executor closeExecutor) throws ActiveMQException {
      this.sessionFactory = sessionFactory;

      this.name = name;

      this.username = username;

      this.password = password;

      this.executor = executor;

      this.flowControlExecutor = flowControlExecutor;

      this.xa = xa;

      this.autoCommitAcks = autoCommitAcks;

      this.preAcknowledge = preAcknowledge;

      this.autoCommitSends = autoCommitSends;

      this.blockOnAcknowledge = blockOnAcknowledge;

      this.autoGroup = autoGroup;

      this.ackBatchSize = ackBatchSize;

      this.consumerWindowSize = consumerWindowSize;

      this.consumerMaxRate = consumerMaxRate;

      this.confirmationWindowSize = confirmationWindowSize;

      this.producerMaxRate = producerMaxRate;

      this.blockOnNonDurableSend = blockOnNonDurableSend;

      this.blockOnDurableSend = blockOnDurableSend;

      this.cacheLargeMessageClient = cacheLargeMessageClient;

      this.minLargeMessageSize = minLargeMessageSize;

      this.compressLargeMessages = compressLargeMessages;

      this.initialMessagePacketSize = initialMessagePacketSize;

      this.groupID = groupID;

      producerCreditManager = new ClientProducerCreditManagerImpl(this, producerWindowSize);

      this.sessionContext = sessionContext;

      sessionContext.setSession(this);

      confirmationWindowWarning = sessionFactory.getConfirmationWindowWarning();

      this.closeExecutor = closeExecutor;
   }

   // ClientSession implementation
   // -----------------------------------------------------------------

   @Override
   public void createQueue(final SimpleString address, final SimpleString queueName) throws ActiveMQException {
      createQueue(address, queueName, false);
   }

   @Override
   public void createQueue(final SimpleString address,
                           final SimpleString queueName,
                           final boolean durable) throws ActiveMQException {
      createQueue(address, queueName, null, durable, false);
   }

   @Override
   public void createQueue(final String address,
                           final String queueName,
                           final boolean durable) throws ActiveMQException {
      createQueue(SimpleString.toSimpleString(address), SimpleString.toSimpleString(queueName), durable);
   }

   @Override
   public void createSharedQueue(SimpleString address,
                                 SimpleString queueName,
                                 boolean durable) throws ActiveMQException {
      createSharedQueue(address, queueName, null, durable);
   }

   @Override
   public void createSharedQueue(SimpleString address,
                                 SimpleString queueName,
                                 SimpleString filterString,
                                 boolean durable) throws ActiveMQException {
      createSharedQueue(address, ActiveMQDefaultConfiguration.getDefaultRoutingType(), queueName, filterString, durable);
   }

   @Override
   public void createAddress(final SimpleString address, Set<RoutingType> routingTypes, boolean autoCreated) throws ActiveMQException {
      createAddress(address, EnumSet.copyOf(routingTypes), autoCreated);
   }

   @Override
   public void createAddress(final SimpleString address, EnumSet<RoutingType> routingTypes, boolean autoCreated) throws ActiveMQException {
      checkClosed();

      startCall();
      try {
         sessionContext.createAddress(address, routingTypes, autoCreated);
      } finally {
         endCall();
      }
   }

   @Override
   public void createAddress(final SimpleString address, RoutingType routingType, boolean autoCreated) throws ActiveMQException {
      createAddress(address, EnumSet.of(routingType), autoCreated);
   }

   @Override
   public void createQueue(QueueConfiguration queueConfiguration) throws ActiveMQException {
      internalCreateQueue(queueConfiguration);
   }

   @Override
   public void createSharedQueue(QueueConfiguration queueConfiguration) throws ActiveMQException {
      checkClosed();

      startCall();
      try {
         sessionContext.createSharedQueue(queueConfiguration);
      } finally {
         endCall();
      }
   }

   @Override
   public void createQueue(final SimpleString address,
                           final SimpleString queueName,
                           final SimpleString filterString,
                           final boolean durable) throws ActiveMQException {
      createQueue(address, queueName, filterString, durable, false);
   }

   @Override
   public void createQueue(final String address,
                           final String queueName,
                           final String filterString,
                           final boolean durable) throws ActiveMQException {
      createQueue(SimpleString.toSimpleString(address),
                  SimpleString.toSimpleString(queueName),
                  SimpleString.toSimpleString(filterString),
                  durable);
   }

   @Override
   public void createQueue(final SimpleString address,
                           final SimpleString queueName,
                           final SimpleString filterString,
                           final boolean durable,
                           final boolean autoCreated) throws ActiveMQException {
      createQueue(address, ActiveMQDefaultConfiguration.getDefaultRoutingType(), queueName, filterString, durable, autoCreated);
   }

   @Override
   public void createQueue(final String address,
                           final String queueName,
                           final String filterString,
                           final boolean durable,
                           final boolean autoCreated) throws ActiveMQException {
      createQueue(SimpleString.toSimpleString(address), SimpleString.toSimpleString(queueName), SimpleString.toSimpleString(filterString), durable, autoCreated);
   }

   @Override
   public void createTemporaryQueue(final SimpleString address, final SimpleString queueName) throws ActiveMQException {
      createTemporaryQueue(address, queueName, (SimpleString) null);
   }

   @Override
   public void createTemporaryQueue(final String address, final String queueName) throws ActiveMQException {
      createTemporaryQueue(SimpleString.toSimpleString(address), SimpleString.toSimpleString(queueName));
   }

   @Override
   public void createTemporaryQueue(final SimpleString address,
                                    final SimpleString queueName,
                                    final SimpleString filter) throws ActiveMQException {
      createTemporaryQueue(address, ActiveMQDefaultConfiguration.getDefaultRoutingType(), queueName, filter);
   }

   @Override
   public void createTemporaryQueue(final String address,
                                    final String queueName,
                                    final String filter) throws ActiveMQException {
      createTemporaryQueue(SimpleString.toSimpleString(address), SimpleString.toSimpleString(queueName), SimpleString.toSimpleString(filter));
   }


   /** New Queue API **/


   @Deprecated
   @Override
   public void createQueue(final SimpleString address,
                           final RoutingType routingType,
                           final SimpleString queueName,
                           final SimpleString filterString,
                           final boolean durable,
                           final boolean autoCreated) throws ActiveMQException {
      internalCreateQueue(address,
                          queueName,
                          false,
                          autoCreated,
                          new QueueAttributes()
                              .setRoutingType(routingType)
                              .setFilterString(filterString)
                              .setDurable(durable)
                              .setPurgeOnNoConsumers(ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers())
                              .setMaxConsumers(ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers()));
   }

   @Override
   public void createQueue(final String address, final RoutingType routingType, final String queueName, final String filterString,
                           final boolean durable, final boolean autoCreated) throws ActiveMQException {
      createQueue(SimpleString.toSimpleString(address),
                  routingType,
                  SimpleString.toSimpleString(queueName),
                  SimpleString.toSimpleString(filterString),
                  durable,
                  autoCreated);
   }


   @Deprecated
   @Override
   public void createQueue(final SimpleString address, final RoutingType routingType, final SimpleString queueName, final SimpleString filterString,
                           final boolean durable, final boolean autoCreated, final int maxConsumers, final boolean purgeOnNoConsumers) throws ActiveMQException {
      internalCreateQueue(address,
                          queueName,
                          false,
                          autoCreated,
                          new QueueAttributes()
                                  .setRoutingType(routingType)
                                  .setFilterString(filterString)
                                  .setDurable(durable)
                                  .setMaxConsumers(maxConsumers)
                                  .setPurgeOnNoConsumers(purgeOnNoConsumers));
   }

   @Deprecated
   @Override
   public void createQueue(final SimpleString address, final RoutingType routingType, final SimpleString queueName, final SimpleString filterString,
                           final boolean durable, final boolean autoCreated, final int maxConsumers, final boolean purgeOnNoConsumers, final Boolean exclusive, final Boolean lastValue) throws ActiveMQException {
      internalCreateQueue(address,
                          queueName,
                          false,
                          autoCreated,
                          new QueueAttributes()
                                  .setRoutingType(routingType)
                                  .setFilterString(filterString)
                                  .setDurable(durable)
                                  .setMaxConsumers(maxConsumers)
                                  .setPurgeOnNoConsumers(purgeOnNoConsumers)
                                  .setExclusive(exclusive)
                                  .setLastValue(lastValue));
   }

   @Deprecated
   @Override
   public void createQueue(final SimpleString address, final SimpleString queueName, final boolean autoCreated, final QueueAttributes queueAttributes) throws ActiveMQException {
      internalCreateQueue(address,
              queueName,
              false,
              autoCreated,
              queueAttributes);
   }

   @Deprecated
   @Override
   public void createQueue(final String address, final RoutingType routingType, final String queueName, final String filterString,
                           final boolean durable, final boolean autoCreated, final int maxConsumers, final boolean purgeOnNoConsumers) throws ActiveMQException {
      createQueue(address,
                  routingType,
                  queueName,
                  filterString,
                  durable,
                  autoCreated,
                  maxConsumers,
                  purgeOnNoConsumers,
                  null,
                  null);
   }

   @Override
   public void createQueue(final String address, final RoutingType routingType, final String queueName, final String filterString,
                           final boolean durable, final boolean autoCreated, final int maxConsumers, final boolean purgeOnNoConsumers, Boolean exclusive, Boolean lastValue) throws ActiveMQException {
      createQueue(SimpleString.toSimpleString(address),
                  routingType,
                  SimpleString.toSimpleString(queueName),
                  SimpleString.toSimpleString(filterString),
                  durable,
                  autoCreated,
                  maxConsumers,
                  purgeOnNoConsumers,
                  exclusive,
                  lastValue);
   }

   @Override
   public void createTemporaryQueue(final SimpleString address,
                                    final RoutingType routingType,
                                    final SimpleString queueName) throws ActiveMQException {
      createTemporaryQueue(address, routingType, queueName, null);
   }

   @Override
   public void createTemporaryQueue(final String address, final RoutingType routingType, final String queueName) throws ActiveMQException {
      createTemporaryQueue(SimpleString.toSimpleString(address), routingType, SimpleString.toSimpleString(queueName));
   }

   @Deprecated
   @Override
   public void createTemporaryQueue(final SimpleString address,
                                    final RoutingType routingType,
                                    final SimpleString queueName,
                                    final SimpleString filter,
                                    final int maxConsumers,
                                    final boolean purgeOnNoConsumers,
                                    final Boolean exclusive,
                                    final Boolean lastValue) throws ActiveMQException {
      internalCreateQueue(address,
                          queueName,
                          true,
                          false,
                          new QueueAttributes()
                                  .setRoutingType(routingType)
                                  .setFilterString(filter)
                                  .setDurable(false)
                                  .setPurgeOnNoConsumers(purgeOnNoConsumers)
                                  .setMaxConsumers(maxConsumers)
                                  .setExclusive(exclusive)
                                  .setLastValue(lastValue));
   }

   @Deprecated
   @Override
   public void createTemporaryQueue(final SimpleString address,
                                    final SimpleString queueName,
                                    final QueueAttributes queueAttributes) throws ActiveMQException {
      internalCreateQueue(address,
              queueName,
              true,
              false,
              queueAttributes);
   }

   @Deprecated
   @Override
   public void createTemporaryQueue(final SimpleString address,
                                    final RoutingType routingType,
                                    final SimpleString queueName,
                                    final SimpleString filter) throws ActiveMQException {
      createTemporaryQueue(address, routingType, queueName, filter, ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers(), ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers(), null, null);
   }

   @Override
   public void createTemporaryQueue(final String address, final RoutingType routingType, final String queueName, final String filter) throws ActiveMQException {
      createTemporaryQueue(SimpleString.toSimpleString(address), routingType, SimpleString.toSimpleString(queueName), SimpleString.toSimpleString(filter));
   }

   /**
    * Creates a <em>non-temporary</em> queue.
    *
    * @param address      the queue will be bound to this address
    * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
    * @param queueName    the name of the queue
    * @param durable      whether the queue is durable or not
    * @throws ActiveMQException in an exception occurs while creating the queue
    */
   @Deprecated
   @Override
   public void createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, boolean durable) throws ActiveMQException {
      internalCreateQueue(address,
                          queueName,
                          false,
                          false,
                          new QueueAttributes()
                                  .setRoutingType(routingType)
                                  .setFilterString(null)
                                  .setDurable(durable)
                                  .setPurgeOnNoConsumers(ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers())
                                  .setMaxConsumers(ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers()));
   }

   /**
    * Creates a transient queue. A queue that will exist as long as there are consumers. When the last consumer is closed the queue will be deleted
    * <p>
    * Notice: you will get an exception if the address or the filter doesn't match to an already existent queue
    *
    * @param address      the queue will be bound to this address
    * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
    * @param queueName    the name of the queue
    * @param durable      if the queue is durable
    * @throws ActiveMQException in an exception occurs while creating the queue
    */
   @Override
   public void createSharedQueue(SimpleString address, RoutingType routingType, SimpleString queueName, boolean durable) throws ActiveMQException {
      createSharedQueue(address, routingType, queueName, null, durable);
   }

   /**
    * Creates a transient queue. A queue that will exist as long as there are consumers. When the last consumer is closed the queue will be deleted
    * <p>
    * Notice: you will get an exception if the address or the filter doesn't match to an already existent queue
    *
    * @param address      the queue will be bound to this address
    * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
    * @param queueName    the name of the queue
    * @param filter       whether the queue is durable or not
    * @param durable      if the queue is durable
    * @throws ActiveMQException in an exception occurs while creating the queue
    */
   @Override
   public void createSharedQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                                 boolean durable) throws ActiveMQException {
      createSharedQueue(address, routingType, queueName, filter, durable, null, null, null, null);
   }

   /**
    * Creates Shared queue. A queue that will exist as long as there are consumers or is durable.
    *
    * @param address      the queue will be bound to this address
    * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
    * @param queueName    the name of the queue
    * @param filter       whether the queue is durable or not
    * @param durable      if the queue is durable
    * @param maxConsumers how many concurrent consumers will be allowed on this queue
    * @param purgeOnNoConsumers whether to delete the contents of the queue when the last consumer disconnects
    * @param exclusive    if the queue is exclusive queue
    * @param lastValue    if the queue is last value queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    */
   @Deprecated
   @Override
   public void createSharedQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                                 boolean durable, Integer maxConsumers, Boolean purgeOnNoConsumers, Boolean exclusive, Boolean lastValue) throws ActiveMQException {
      QueueAttributes queueAttributes = new QueueAttributes()
              .setRoutingType(routingType)
              .setFilterString(filter)
              .setDurable(durable)
              .setPurgeOnNoConsumers(ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers())
              .setMaxConsumers(ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers())
              .setExclusive(exclusive)
              .setLastValue(lastValue);
      createSharedQueue(address, queueName, queueAttributes);
   }

   /**
    * Creates Shared queue. A queue that will exist as long as there are consumers or is durable.
    *
    * @param address      the queue will be bound to this address
    * @param queueName    the name of the queue
    * @param queueAttributes attributes for the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    */
   @Deprecated
   @Override
   public void createSharedQueue(SimpleString address, SimpleString queueName, QueueAttributes queueAttributes) throws ActiveMQException {
      createSharedQueue(queueAttributes.toQueueConfiguration().setName(queueName).setAddress(address));
   }

   /**
    * Creates a <em>non-temporary</em> queue.
    *
    * @param address      the queue will be bound to this address
    * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
    * @param queueName    the name of the queue
    * @param durable      whether the queue is durable or not
    * @throws ActiveMQException in an exception occurs while creating the queue
    */
   @Override
   public void createQueue(String address, RoutingType routingType, String queueName, boolean durable) throws ActiveMQException {
      createQueue(SimpleString.toSimpleString(address), routingType, SimpleString.toSimpleString(queueName), durable);
   }

   /**
    * Creates a <em>non-temporary</em> queue <em>non-durable</em> queue.
    *
    * @param address      the queue will be bound to this address
    * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
    * @param queueName    the name of the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    */
   @Deprecated
   @Override
   public void createQueue(String address, RoutingType routingType, String queueName) throws ActiveMQException {
      internalCreateQueue(SimpleString.toSimpleString(address),
                          SimpleString.toSimpleString(queueName),
                          false,
                          false,
                          new QueueAttributes()
                                  .setRoutingType(routingType)
                                  .setFilterString(null)
                                  .setDurable(false)
                                  .setPurgeOnNoConsumers(ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers())
                                  .setMaxConsumers(ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers()));
   }

   /**
    * Creates a <em>non-temporary</em> queue <em>non-durable</em> queue.
    *
    * @param address      the queue will be bound to this address
    * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
    * @param queueName    the name of the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    */
   @Deprecated
   @Override
   public void createQueue(SimpleString address, RoutingType routingType, SimpleString queueName) throws ActiveMQException {
      internalCreateQueue(address,
                          queueName,
                          false,
                          false,
                          new QueueAttributes()
                                  .setRoutingType(routingType)
                                  .setFilterString(null)
                                  .setDurable(false)
                                  .setPurgeOnNoConsumers(ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers())
                                  .setMaxConsumers(ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers()));
   }

   /**
    * Creates a <em>non-temporary</em> queue.
    *
    * @param address      the queue will be bound to this address
    * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
    * @param queueName    the name of the queue
    * @param filter       only messages which match this filter will be put in the queue
    * @param durable      whether the queue is durable or not
    * @throws ActiveMQException in an exception occurs while creating the queue
    */
   @Deprecated
   @Override
   public void createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                           boolean durable) throws ActiveMQException {
      internalCreateQueue(address,
                          queueName,
                          false,
                          false,
                          new QueueAttributes()
                                  .setRoutingType(routingType)
                                  .setFilterString(filter)
                                  .setDurable(durable)
                                  .setPurgeOnNoConsumers(ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers())
                                  .setMaxConsumers(ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers()));
   }

   /**
    * Creates a <em>non-temporary</em>queue.
    *
    * @param address      the queue will be bound to this address
    * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
    * @param queueName    the name of the queue
    * @param filter       only messages which match this filter will be put in the queue
    * @param durable      whether the queue is durable or not
    * @throws ActiveMQException in an exception occurs while creating the queue
    */
   @Override
   public void createQueue(String address, RoutingType routingType, String queueName, String filter, boolean durable) throws ActiveMQException {
      createQueue(SimpleString.toSimpleString(address), routingType, SimpleString.toSimpleString(queueName), SimpleString.toSimpleString(filter),
                  durable);
   }


   @Override
   public void deleteQueue(final SimpleString queueName) throws ActiveMQException {
      checkClosed();

      startCall();
      try {
         sessionContext.deleteQueue(queueName);
      } finally {
         endCall();
      }
   }

   @Override
   public void deleteQueue(final String queueName) throws ActiveMQException {
      deleteQueue(SimpleString.toSimpleString(queueName));
   }

   @Override
   public QueueQuery queueQuery(final SimpleString queueName) throws ActiveMQException {
      checkClosed();

      startCall();
      try {
         return sessionContext.queueQuery(queueName);
      } finally {
         endCall();
      }

   }

   @Override
   public AddressQuery addressQuery(final SimpleString address) throws ActiveMQException {
      checkClosed();

      return sessionContext.addressQuery(address);
   }

   @Override
   public ClientConsumer createConsumer(final SimpleString queueName) throws ActiveMQException {
      return createConsumer(queueName, null, false);
   }

   @Override
   public ClientConsumer createConsumer(final String queueName) throws ActiveMQException {
      return createConsumer(SimpleString.toSimpleString(queueName));
   }

   @Override
   public ClientConsumer createConsumer(final SimpleString queueName,
                                        final SimpleString filterString) throws ActiveMQException {
      return createConsumer(queueName, filterString, consumerWindowSize, consumerMaxRate, false);
   }

   @Override
   public void createQueue(final String address, final String queueName) throws ActiveMQException {
      createQueue(SimpleString.toSimpleString(address), SimpleString.toSimpleString(queueName));
   }

   @Override
   public ClientConsumer createConsumer(final String queueName, final String filterString) throws ActiveMQException {
      return createConsumer(SimpleString.toSimpleString(queueName), SimpleString.toSimpleString(filterString));
   }

   @Override
   public ClientConsumer createConsumer(final SimpleString queueName,
                                        final SimpleString filterString,
                                        final boolean browseOnly) throws ActiveMQException {
      return createConsumer(queueName, filterString, consumerWindowSize, consumerMaxRate, browseOnly);
   }

   @Override
   public ClientConsumer createConsumer(final SimpleString queueName,
                                        final SimpleString filterString,
                                        final int priority,
                                        final boolean browseOnly) throws ActiveMQException {
      return createConsumer(queueName, filterString, priority, consumerWindowSize, consumerMaxRate, browseOnly);
   }

   @Override
   public ClientConsumer createConsumer(final SimpleString queueName,
                                        final boolean browseOnly) throws ActiveMQException {
      return createConsumer(queueName, null, consumerWindowSize, consumerMaxRate, browseOnly);
   }

   @Override
   public ClientConsumer createConsumer(final String queueName,
                                        final String filterString,
                                        final boolean browseOnly) throws ActiveMQException {
      return createConsumer(SimpleString.toSimpleString(queueName), SimpleString.toSimpleString(filterString), browseOnly);
   }

   @Override
   public ClientConsumer createConsumer(final String queueName, final boolean browseOnly) throws ActiveMQException {
      return createConsumer(SimpleString.toSimpleString(queueName), null, browseOnly);
   }

   @Override
   public boolean isWritable(ReadyListener callback) {
      return sessionContext.isWritable(callback);
   }

   @Override
   public ClientConsumer createConsumer(final SimpleString queueName,
                                        final SimpleString filterString,
                                        final int windowSize,
                                        final int maxRate,
                                        final boolean browseOnly) throws ActiveMQException {
      return createConsumer(queueName, filterString, ActiveMQDefaultConfiguration.getDefaultConsumerPriority(), windowSize, maxRate, browseOnly);
   }

   /**
    * Note, we DO NOT currently support direct consumers (i.e. consumers where delivery occurs on
    * the remoting thread).
    * <p>
    * Direct consumers have issues with blocking and failover. E.g. if direct then inside
    * MessageHandler call a blocking method like rollback or acknowledge (blocking) This can block
    * until failover completes, which disallows the thread to be used to deliver any responses to
    * the client during that period, so failover won't occur. If we want direct consumers we need to
    * rethink how they work.
    */
   @Override
   public ClientConsumer createConsumer(final SimpleString queueName,
                                        final SimpleString filterString,
                                        final int priority,
                                        final int windowSize,
                                        final int maxRate,
                                        final boolean browseOnly) throws ActiveMQException {
      return internalCreateConsumer(queueName, filterString, priority, windowSize, maxRate, browseOnly);
   }

   @Override
   public ClientConsumer createConsumer(final String queueName,
                                        final String filterString,
                                        final int windowSize,
                                        final int maxRate,
                                        final boolean browseOnly) throws ActiveMQException {
      return createConsumer(SimpleString.toSimpleString(queueName), SimpleString.toSimpleString(filterString), windowSize, maxRate, browseOnly);
   }

   @Override
   public ClientProducer createProducer() throws ActiveMQException {
      return createProducer((SimpleString) null);
   }

   @Override
   public ClientProducer createProducer(final SimpleString address) throws ActiveMQException {
      return createProducer(address, producerMaxRate);
   }

   @Override
   public ClientProducer createProducer(final String address) throws ActiveMQException {
      return createProducer(SimpleString.toSimpleString(address));
   }

   @Override
   public ClientProducer createProducer(final SimpleString address, final int maxRate) throws ActiveMQException {
      return internalCreateProducer(address, maxRate);
   }

   public ClientProducer createProducer(final String address, final int rate) throws ActiveMQException {
      return createProducer(SimpleString.toSimpleString(address), rate);
   }

   @Override
   public XAResource getXAResource() {
      return this;
   }

   private void rollbackOnFailover(boolean outcomeKnown) throws ActiveMQException {
      rollback(false);

      if (outcomeKnown) {
         throw ActiveMQClientMessageBundle.BUNDLE.txRolledBack();
      }

      throw ActiveMQClientMessageBundle.BUNDLE.txOutcomeUnknown();
   }

   @Override
   public void commit() throws ActiveMQException {
      commit(true);
   }

   @Override
   public void commit(boolean block) throws ActiveMQException {
      checkClosed();

      if (logger.isTraceEnabled()) {
         logger.trace("Sending commit");
      }

      /*
      * we have failed over since any work was done so we should rollback
      * */
      if (rollbackOnly) {
         rollbackOnFailover(true);
      }

      flushAcks();
      /*
      * if we have failed over whilst flushing the acks then we should rollback and throw exception before attempting to
      * commit as committing might actually commit something but we we wouldn't know and rollback after the commit
      * */
      if (rollbackOnly) {
         rollbackOnFailover(true);
      }
      try {
         sessionContext.simpleCommit(block);
      } catch (ActiveMQException e) {
         if (e.getType() == ActiveMQExceptionType.UNBLOCKED || e.getType() == ActiveMQExceptionType.CONNECTION_TIMEDOUT || rollbackOnly) {
            // The call to commit was unlocked on failover, we therefore rollback the tx,
            // and throw a transaction rolled back exception instead
            //or
            //if we have been set to rollbackonly then we have probably failed over and don't know if the tx has committed
            rollbackOnFailover(false);
         } else {
            throw e;
         }
      }

      //oops, we have failed over during the commit and don't know what happened
      if (rollbackOnly) {
         rollbackOnFailover(false);
      }

      workDone = false;
   }

   @Override
   public boolean isRollbackOnly() {
      return rollbackOnly;
   }

   @Override
   public void rollback() throws ActiveMQException {
      rollback(false);
   }

   @Override
   public void rollback(final boolean isLastMessageAsDelivered) throws ActiveMQException {
      rollback(isLastMessageAsDelivered, true);
   }

   public void rollback(final boolean isLastMessageAsDelivered, final boolean waitConsumers) throws ActiveMQException {
      if (logger.isTraceEnabled()) {
         logger.trace("calling rollback(isLastMessageAsDelivered=" + isLastMessageAsDelivered + ")");
      }
      checkClosed();

      // We do a "JMS style" rollback where the session is stopped, and the buffer is cancelled back
      // first before rolling back
      // This ensures messages are received in the same order after rollback w.r.t. to messages in the buffer
      // For core we could just do a straight rollback, it really depends if we want JMS style semantics or not...

      boolean wasStarted = started;

      if (wasStarted) {
         stop();
      }

      // We need to make sure we don't get any inflight messages
      for (ClientConsumerInternal consumer : cloneConsumers()) {
         consumer.clear(waitConsumers);
      }

      try {
         // Acks must be flushed here *after connection is stopped and all onmessages finished executing
         flushAcks();

         sessionContext.simpleRollback(isLastMessageAsDelivered);
      } finally {
         if (wasStarted) {
            // restore the original session state even if something fails
            start();
         }
      }

      rollbackOnly = false;
   }

   @Override
   public void markRollbackOnly() {
      rollbackOnly = true;
   }

   @Override
   public ClientMessage createMessage(final byte type,
                                      final boolean durable,
                                      final long expiration,
                                      final long timestamp,
                                      final byte priority) {
      return new ClientMessageImpl(type, durable, expiration, timestamp, priority, initialMessagePacketSize, coreMessageObjectPools);
   }

   @Override
   public ClientMessage createMessage(final byte type, final boolean durable) {
      return this.createMessage(type, durable, 0, System.currentTimeMillis(), (byte) 4);
   }

   @Override
   public ClientMessage createMessage(final boolean durable) {
      return this.createMessage((byte) 0, durable);
   }

   @Override
   public boolean isClosed() {
      return closed;
   }

   @Override
   public boolean isAutoCommitSends() {
      return autoCommitSends;
   }

   @Override
   public boolean isAutoCommitAcks() {
      return autoCommitAcks;
   }

   @Override
   public boolean isBlockOnAcknowledge() {
      return blockOnAcknowledge;
   }

   @Override
   public boolean isXA() {
      return xa;
   }

   @Override
   public void resetIfNeeded() throws ActiveMQException {
      if (rollbackOnly) {
         ActiveMQClientLogger.LOGGER.resettingSessionAfterFailure();
         rollback(false);
      }
   }

   @Override
   public ClientSessionImpl start() throws ActiveMQException {
      checkClosed();

      if (!started) {
         for (ClientConsumerInternal clientConsumerInternal : cloneConsumers()) {
            clientConsumerInternal.start();
         }

         sessionContext.sessionStart();

         started = true;
      }

      return this;
   }

   @Override
   public void stop() throws ActiveMQException {
      stop(true);
   }

   public void stop(final boolean waitForOnMessage) throws ActiveMQException {
      checkClosed();

      if (started) {
         for (ClientConsumerInternal clientConsumerInternal : cloneConsumers()) {
            clientConsumerInternal.stop(waitForOnMessage);
         }

         sessionContext.sessionStop();

         started = false;
      }
   }

   @Override
   public void addFailureListener(final SessionFailureListener listener) {
      sessionFactory.addFailureListener(listener);
   }

   @Override
   public boolean removeFailureListener(final SessionFailureListener listener) {
      return sessionFactory.removeFailureListener(listener);
   }

   @Override
   public void addFailoverListener(FailoverEventListener listener) {
      sessionFactory.addFailoverListener(listener);
   }

   @Override
   public boolean removeFailoverListener(FailoverEventListener listener) {
      return sessionFactory.removeFailoverListener(listener);
   }

   @Override
   public int getVersion() {
      return sessionContext.getServerVersion();
   }

   @Override
   public boolean isClosing() {
      return inClose;
   }

   @Override
   public String getNodeId() {
      return sessionFactory.getLiveNodeId();
   }

   // ClientSessionInternal implementation
   // ------------------------------------------------------------

   @Override
   public int getMinLargeMessageSize() {
      return minLargeMessageSize;
   }

   @Override
   public boolean isCompressLargeMessages() {
      return compressLargeMessages;
   }

   /**
    * @return the cacheLargeMessageClient
    */
   @Override
   public boolean isCacheLargeMessageClient() {
      return cacheLargeMessageClient;
   }

   @Override
   public String getName() {
      return name;
   }

   /**
    * Acknowledges all messages received by the consumer so far.
    */
   @Override
   public void acknowledge(final ClientConsumer consumer, final Message message) throws ActiveMQException {
      // if we're pre-acknowledging then we don't need to do anything
      if (preAcknowledge) {
         return;
      }

      checkClosed();
      if (logger.isDebugEnabled()) {
         logger.debug("client ack messageID = " + message.getMessageID());
      }

      startCall();
      try {
         sessionContext.sendACK(false, blockOnAcknowledge, consumer, message);
      } finally {
         endCall();
      }
   }

   @Override
   public void individualAcknowledge(final ClientConsumer consumer, final Message message) throws ActiveMQException {
      // if we're pre-acknowledging then we don't need to do anything
      if (preAcknowledge) {
         return;
      }

      checkClosed();

      startCall();
      try {

         sessionContext.sendACK(true, blockOnAcknowledge, consumer, message);
      } finally {
         endCall();
      }
   }

   @Override
   public void expire(final ClientConsumer consumer, final Message message) throws ActiveMQException {
      checkClosed();

      // We don't send expiries for pre-ack since message will already have been acked on server
      if (!preAcknowledge) {
         sessionContext.expireMessage(consumer, message);
      }
   }

   @Override
   public void addConsumer(final ClientConsumerInternal consumer) {
      synchronized (consumers) {
         consumers.put(consumer.getConsumerContext(), consumer);
      }
   }

   @Override
   public void addProducer(final ClientProducerInternal producer) {
      synchronized (producers) {
         producers.add(producer);
      }
   }

   @Override
   public void removeConsumer(final ClientConsumerInternal consumer) throws ActiveMQException {
      synchronized (consumers) {
         consumers.remove(consumer.getConsumerContext());
      }
   }

   @Override
   public void removeProducer(final ClientProducerInternal producer) {
      synchronized (producers) {
         producers.remove(producer);
      }
   }

   @Override
   public void handleReceiveMessage(final ConsumerContext consumerID,
                                    final ClientMessageInternal message) throws Exception {
      ClientConsumerInternal consumer = getConsumer(consumerID);

      if (consumer != null) {
         consumer.handleMessage(message);
      }
   }

   @Override
   public void handleReceiveLargeMessage(final ConsumerContext consumerID,
                                         ClientLargeMessageInternal clientLargeMessage,
                                         long largeMessageSize) throws Exception {
      ClientConsumerInternal consumer = getConsumer(consumerID);

      if (consumer != null) {
         consumer.handleLargeMessage(clientLargeMessage, largeMessageSize);
      }
   }

   @Override
   public void handleReceiveContinuation(final ConsumerContext consumerID,
                                         byte[] chunk,
                                         int flowControlSize,
                                         boolean isContinues) throws Exception {
      ClientConsumerInternal consumer = getConsumer(consumerID);

      if (consumer != null) {
         consumer.handleLargeMessageContinuation(chunk, flowControlSize, isContinues);
      }
   }

   @Override
   public void handleConsumerDisconnect(ConsumerContext context) throws ActiveMQException {
      final ClientConsumerInternal consumer = getConsumer(context);

      if (consumer != null) {
         closeExecutor.execute(new Runnable() {
            @Override
            public void run() {
               try {
                  consumer.close();
               } catch (ActiveMQException e) {
                  ActiveMQClientLogger.LOGGER.unableToCloseConsumer(e);
               }
            }
         });
      }
   }

   @Override
   public void close() throws ActiveMQException {
      if (closed) {
         logger.debug("Session was already closed, giving up now, this=" + this);
         return;
      }

      if (logger.isDebugEnabled()) {
         logger.debug("Calling close on session " + this);
      }

      try {
         closeChildren();

         synchronized (producerCreditManager) {
            producerCreditManager.close();
         }
         inClose = true;
         sessionContext.sessionClose();
      } catch (Throwable e) {
         // Session close should always return without exception

         // Note - we only log at trace
         logger.trace("Failed to close session", e);
      }

      doCleanup(false);
   }

   @Override
   public synchronized void cleanUp(boolean failingOver) throws ActiveMQException {
      if (closed) {
         return;
      }

      synchronized (producerCreditManager) {
         producerCreditManager.close();
      }

      cleanUpChildren();

      doCleanup(failingOver);
   }

   @Override
   public ClientSessionImpl setSendAcknowledgementHandler(final SendAcknowledgementHandler handler) {
      sessionContext.setSendAcknowledgementHandler(handler);
      return this;
   }

   @Override
   public void preHandleFailover(RemotingConnection connection) {
      // We lock the channel to prevent any packets to be added to the re-send
      // cache during the failover process
      //we also do this before the connection fails over to give the session a chance to block for failover
      sessionContext.lockCommunications();
   }

   // Needs to be synchronized to prevent issues with occurring concurrently with close()

   @Override
   public boolean handleFailover(final RemotingConnection backupConnection, ActiveMQException cause) {
      boolean suc = true;

      synchronized (this) {
         if (closed) {
            return true;
         }

         try {

            // TODO remove this and encapsulate it

            boolean reattached = sessionContext.reattachOnNewConnection(backupConnection);

            if (!reattached) {

               // We change the name of the Session, otherwise the server could close it while we are still sending the recreate
               // in certain failure scenarios
               // For instance the fact we didn't change the name of the session after failover or reconnect
               // was the reason allowing multiple Sessions to be closed simultaneously breaking concurrency
               this.name = UUIDGenerator.getInstance().generateStringUUID();

               sessionContext.resetName(name);

               Map<ConsumerContext, ClientConsumerInternal> clonedConsumerEntries = cloneConsumerEntries();

               for (ClientConsumerInternal consumer : clonedConsumerEntries.values()) {
                  consumer.clearAtFailover();
               }

               // The session wasn't found on the server - probably we're failing over onto a backup server where the
               // session won't exist or the target server has been restarted - in this case the session will need to be
               // recreated,
               // and we'll need to recreate any consumers

               // It could also be that the server hasn't been restarted, but the session is currently executing close,
               // and
               // that
               // has already been executed on the server, that's why we can't find the session- in this case we *don't*
               // want
               // to recreate the session, we just want to unblock the blocking call
               if (!inClose && mayAttemptToFailover) {
                  sessionContext.recreateSession(username, password, minLargeMessageSize, xa, autoCommitSends, autoCommitAcks, preAcknowledge);

                  for (Map.Entry<ConsumerContext, ClientConsumerInternal> entryx : clonedConsumerEntries.entrySet()) {

                     ClientConsumerInternal consumerInternal = entryx.getValue();
                     synchronized (consumerInternal) {
                        if (!consumerInternal.isClosed()) {
                           sessionContext.recreateConsumerOnServer(consumerInternal, entryx.getKey().getId(), started);
                        }
                     }
                  }

                  if ((!autoCommitAcks || !autoCommitSends) && workDone) {
                     // this is protected by a lock, so we can guarantee nothing will sneak here
                     // while we do our work here
                     rollbackOnly = true;
                  }
                  if (currentXID != null) {
                     sessionContext.xaFailed(currentXID);
                     rollbackOnly = true;
                  }

                  // Now start the session if it was already started
                  if (started) {
                     for (ClientConsumerInternal consumer : clonedConsumerEntries.values()) {
                        consumer.clearAtFailover();
                        consumer.start();
                     }

                     sessionContext.restartSession();
                  }

                  resetCreditManager = true;
               }

               sessionContext.returnBlocking(cause);
            }
         } catch (ActiveMQRedirectedException e) {
            logger.info("failedToHandleFailover.ActiveMQRedirectedException");
            suc = false;
         } catch (Throwable t) {
            ActiveMQClientLogger.LOGGER.failedToHandleFailover(t);
            suc = false;
         }
      }

      return suc;
   }

   @Override
   public void postHandleFailover(RemotingConnection connection, boolean successful) {
      sessionContext.releaseCommunications();

      if (successful) {
         synchronized (this) {
            if (closed) {
               return;
            }

            if (resetCreditManager) {
               synchronized (producerCreditManager) {
                  producerCreditManager.reset();
               }

               resetCreditManager = false;

               // Also need to send more credits for consumers, otherwise the system could hand with the server
               // not having any credits to send
            }
         }

         HashMap<String, String> metaDataToSend;

         synchronized (metadata) {
            metaDataToSend = new HashMap<>(metadata);
         }

         sessionContext.resetMetadata(metaDataToSend);
      }
   }

   @Override
   public void addMetaData(String key, String data) throws ActiveMQException {
      synchronized (metadata) {
         metadata.put(key, data);
      }

      sessionContext.addSessionMetadata(key, data);
   }

   @Override
   public void addUniqueMetaData(String key, String data) throws ActiveMQException {
      sessionContext.addUniqueMetaData(key, data);
   }

   @Override
   public ClientSessionFactory getSessionFactory() {
      return sessionFactory;
   }

   @Override
   public void setAddress(final Message message, final SimpleString address) {
      logger.tracef("setAddress() Setting default address as %s", address);

      message.setAddress(address);
   }

   @Override
   public void setPacketSize(final int packetSize) {
      if (packetSize > this.initialMessagePacketSize) {
         this.initialMessagePacketSize = (int) (packetSize * 1.2);
      }
   }

   @Override
   public void workDone() {
      workDone = true;
   }

   @Override
   public void sendProducerCreditsMessage(final int credits, final SimpleString address) {
      sessionContext.sendProducerCreditsMessage(credits, address);
   }

   @Override
   public ClientProducerCredits getCredits(final SimpleString address, final boolean anon) {
      synchronized (producerCreditManager) {
         return producerCreditManager.getCredits(address, anon, sessionContext);
      }
   }

   @Override
   public void returnCredits(final SimpleString address) {
      synchronized (producerCreditManager) {
         producerCreditManager.returnCredits(address);
      }
   }

   @Override
   public void handleReceiveProducerCredits(final SimpleString address, final int credits) {
      synchronized (producerCreditManager) {
         producerCreditManager.receiveCredits(address, credits);
      }
   }

   @Override
   public void handleReceiveProducerFailCredits(final SimpleString address, int credits) {
      synchronized (producerCreditManager) {
         producerCreditManager.receiveFailCredits(address, credits);
      }
   }

   @Override
   public ClientProducerCreditManager getProducerCreditManager() {
      return producerCreditManager;
   }

   @Override
   public void startCall() {
      if (concurrentCall.incrementAndGet() > 1) {
         ActiveMQClientLogger.LOGGER.invalidConcurrentSessionUsage(new Exception("trace"));
      }
   }

   @Override
   public void endCall() {
      concurrentCall.decrementAndGet();
   }

   // CommandConfirmationHandler implementation ------------------------------------

   // TODO: this will be encapsulated by the SessionContext

   // XAResource implementation
   // --------------------------------------------------------------------

   @Override
   public void commit(final Xid xid, final boolean onePhase) throws XAException {
      if (logger.isTraceEnabled()) {
         logger.trace("call commit(xid=" + convert(xid));
      }
      checkXA();

      // we should never throw rollback if we have already prepared
      if (rollbackOnly) {
         if (onePhase) {
            throw new XAException(XAException.XAER_RMFAIL);
         } else {
            ActiveMQClientLogger.LOGGER.commitAfterFailover();
         }
      }

      // Note - don't need to flush acks since the previous end would have
      // done this

      startCall();
      try {
         sessionContext.xaCommit(xid, onePhase);
         workDone = false;
      } catch (XAException xae) {
         throw xae;
      } catch (Throwable t) {
         ActiveMQClientLogger.LOGGER.failoverDuringCommit();

         XAException xaException;
         if (onePhase) {
            logger.debug("Throwing oneFase RMFAIL on xid=" + xid, t);
            //we must return XA_RMFAIL
            xaException = new XAException(XAException.XAER_RMFAIL);
         } else {
            logger.debug("Throwing twoFase Retry on xid=" + xid, t);
            // Any error on commit -> RETRY
            // We can't rollback a Prepared TX for definition
            xaException = new XAException(XAException.XA_RETRY);
         }

         xaException.initCause(t);
         throw xaException;
      } finally {
         endCall();
      }
   }

   @Override
   public void end(final Xid xid, final int flags) throws XAException {
      if (logger.isTraceEnabled()) {
         logger.trace("Calling end:: " + convert(xid) + ", flags=" + convertTXFlag(flags));
      }

      checkXA();

      try {
         if (rollbackOnly) {
            try {
               rollback(false, false);
            } catch (Throwable ignored) {
               logger.debug("Error on rollback during end call!", ignored);
            }
            throw new XAException(XAException.XAER_RMFAIL);
         }

         try {
            flushAcks();

            startCall();
            try {
               sessionContext.xaEnd(xid, flags);
            } finally {
               endCall();
            }
         } catch (XAException xae) {
            throw xae;
         } catch (Throwable t) {
            ActiveMQClientLogger.LOGGER.errorCallingEnd(t);
            // This could occur if the TM interrupts the thread
            XAException xaException = new XAException(XAException.XAER_RMFAIL);
            xaException.initCause(t);
            throw xaException;
         }
      } finally {
         currentXID = null;
      }
   }

   @Override
   public void forget(final Xid xid) throws XAException {
      checkXA();
      startCall();
      try {
         sessionContext.xaForget(xid);
      } catch (XAException xae) {
         throw xae;
      } catch (Throwable t) {
         // This could occur if the TM interrupts the thread
         XAException xaException = new XAException(XAException.XAER_RMFAIL);
         xaException.initCause(t);
         throw xaException;
      } finally {
         endCall();
      }
   }

   @Override
   public int getTransactionTimeout() throws XAException {
      checkXA();

      try {
         return sessionContext.recoverSessionTimeout();
      } catch (Throwable t) {
         // This could occur if the TM interrupts the thread
         XAException xaException = new XAException(XAException.XAER_RMFAIL);
         xaException.initCause(t);
         throw xaException;
      }
   }

   @Override
   public boolean setTransactionTimeout(final int seconds) throws XAException {
      checkXA();

      try {
         return sessionContext.configureTransactionTimeout(seconds);
      } catch (Throwable t) {
         markRollbackOnly(); // The TM will ignore any errors from here, if things are this screwed up we mark rollbackonly
         // This could occur if the TM interrupts the thread
         XAException xaException = new XAException(XAException.XAER_RMFAIL);
         xaException.initCause(t);
         throw xaException;
      }
   }

   @Override
   public boolean isSameRM(final XAResource xares) throws XAException {
      checkXA();

      if (forceNotSameRM) {
         return false;
      }

      ClientSessionInternal other = getSessionInternalFromXAResource(xares);

      if (other == null) {
         return false;
      }

      String liveNodeId = sessionFactory.getLiveNodeId();
      String otherLiveNodeId = ((ClientSessionFactoryInternal) other.getSessionFactory()).getLiveNodeId();

      if (liveNodeId != null && otherLiveNodeId != null) {
         return liveNodeId.equals(otherLiveNodeId);
      }

      //we shouldn't get here, live node id should always be set
      return sessionFactory == other.getSessionFactory();
   }

   private ClientSessionInternal getSessionInternalFromXAResource(final XAResource xares) {
      if (xares == null) {
         return null;
      }
      if (xares instanceof ClientSessionInternal) {
         return (ClientSessionInternal) xares;
      } else if (xares instanceof ActiveMQXAResource) {
         return getSessionInternalFromXAResource(((ActiveMQXAResource) xares).getResource());
      }
      return null;
   }

   @Override
   public int prepare(final Xid xid) throws XAException {
      checkXA();
      if (logger.isTraceEnabled()) {
         logger.trace("Calling prepare:: " + convert(xid));
      }

      if (rollbackOnly) {
         throw new XAException(XAException.XAER_RMFAIL);
      }

      // Note - don't need to flush acks since the previous end would have
      // done this

      startCall();
      try {
         return sessionContext.xaPrepare(xid);
      } catch (XAException xae) {
         throw xae;
      } catch (ActiveMQException e) {
         if (e.getType() == ActiveMQExceptionType.UNBLOCKED || e.getType() == ActiveMQExceptionType.CONNECTION_TIMEDOUT) {
            // Unblocked on failover
            try {
               // will retry once after failover & unblock
               return sessionContext.xaPrepare(xid);
            } catch (Throwable t) {
               // ignore and rollback
            }
            ActiveMQClientLogger.LOGGER.failoverDuringPrepareRollingBack();
            try {
               rollback(false);
            } catch (Throwable t) {
               // This could occur if the TM interrupts the thread
               XAException xaException = new XAException(XAException.XAER_RMFAIL);
               xaException.initCause(t);
               throw xaException;
            }

            ActiveMQClientLogger.LOGGER.errorDuringPrepare(e);

            throw new XAException(XAException.XAER_RMFAIL);
         }

         ActiveMQClientLogger.LOGGER.errorDuringPrepare(e);

         // This should never occur
         XAException xaException = new XAException(XAException.XAER_RMFAIL);
         xaException.initCause(e);
         throw xaException;
      } catch (Throwable t) {
         ActiveMQClientLogger.LOGGER.errorDuringPrepare(t);

         // This could occur if the TM interrupts the thread
         XAException xaException = new XAException(XAException.XAER_RMFAIL);
         xaException.initCause(t);
         throw xaException;
      } finally {
         endCall();
      }
   }

   @Override
   public Xid[] recover(final int flags) throws XAException {
      checkXA();

      if ((flags & XAResource.TMSTARTRSCAN) == XAResource.TMSTARTRSCAN) {
         try {
            return sessionContext.xaScan();
         } catch (Throwable t) {
            // This could occur if the TM interrupts the thread
            XAException xaException = new XAException(XAException.XAER_RMFAIL);
            xaException.initCause(t);
            throw xaException;
         }
      }

      return new Xid[0];
   }

   @Override
   public void rollback(final Xid xid) throws XAException {
      checkXA();

      if (logger.isTraceEnabled()) {
         logger.trace("Calling rollback:: " + convert(xid));
      }

      try {
         boolean wasStarted = started;

         if (wasStarted) {
            stop(false);
         }

         // We need to make sure we don't get any inflight messages
         for (ClientConsumerInternal consumer : cloneConsumers()) {
            consumer.clear(false);
         }

         flushAcks();

         try {
            sessionContext.xaRollback(xid, wasStarted);
         } finally {
            if (wasStarted) {
               start();
            }
         }

         workDone = false;
      } catch (XAException xae) {
         throw xae;
      } catch (ActiveMQException e) {
         if (e.getType() == ActiveMQExceptionType.UNBLOCKED || e.getType() == ActiveMQExceptionType.CONNECTION_TIMEDOUT || e.getType() == ActiveMQExceptionType.SHUTDOWN_ERROR) {
            // Unblocked on failover
            throw new XAException(XAException.XA_RETRY);
         }

         // This should never occur
         XAException xaException = new XAException(XAException.XAER_RMFAIL);
         xaException.initCause(e);
         throw xaException;
      } catch (Throwable t) {
         // This could occur if the TM interrupts the thread
         XAException xaException = new XAException(XAException.XAER_RMFAIL);
         xaException.initCause(t);
         throw xaException;
      }
   }

   @Override
   public void start(final Xid xid, final int flags) throws XAException {
      if (logger.isTraceEnabled()) {
         logger.trace("Calling start:: " + convert(xid) + " clientXID=" + xid + " flags = " + convertTXFlag(flags));
      }

      checkXA();

      try {
         sessionContext.xaStart(xid, flags);

         this.currentXID = xid;
      } catch (XAException xae) {
         throw xae;
      } catch (ActiveMQException e) {
         // we can retry this only because we know for sure that no work would have been done
         if (e.getType() == ActiveMQExceptionType.UNBLOCKED || e.getType() == ActiveMQExceptionType.CONNECTION_TIMEDOUT) {
            try {
               sessionContext.xaStart(xid, flags);
            } catch (XAException xae) {
               throw xae;
            } catch (Throwable t) {
               // This could occur if the TM interrupts the thread
               XAException xaException = new XAException(XAException.XAER_RMFAIL);
               xaException.initCause(t);
               throw xaException;
            }
         }

         // This should never occur
         XAException xaException = new XAException(XAException.XAER_RMFAIL);
         xaException.initCause(e);
         throw xaException;
      } catch (Throwable t) {
         // This could occur if the TM interrupts the thread
         XAException xaException = new XAException(XAException.XAER_RMFAIL);
         xaException.initCause(t);
         throw xaException;
      }
   }

   // FailureListener implementation --------------------------------------------

   @Override
   public void connectionFailed(final ActiveMQException me, boolean failedOver) {
      try {
         cleanUp(false);
      } catch (Exception e) {
         ActiveMQClientLogger.LOGGER.failedToCleanupSession(e);
      }
   }

   @Override
   public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
      connectionFailed(me, failedOver);
   }

   @Override
   public void setForceNotSameRM(final boolean force) {
      forceNotSameRM = force;
   }

   @Override
   public RemotingConnection getConnection() {
      return sessionContext.getRemotingConnection();
   }

   @Override
   public String toString() {
      StringBuilder buffer = new StringBuilder();
      synchronized (metadata) {
         for (Map.Entry<String, String> entry : metadata.entrySet()) {
            buffer.append(entry.getKey() + "=" + entry.getValue() + ",");
         }
      }

      return "ClientSessionImpl [name=" + name +
         ", username=" +
         username +
         ", closed=" +
         closed +
         ", factory = " + this.sessionFactory +
         ", metaData=(" +
         buffer +
         ")]@" +
         Integer.toHexString(hashCode());
   }

   /**
    * @param queueName
    * @param filterString
    * @param windowSize
    * @param browseOnly
    * @return
    * @throws ActiveMQException
    */
   private ClientConsumer internalCreateConsumer(final SimpleString queueName,
                                                 final SimpleString filterString,
                                                 final int priority,
                                                 final int windowSize,
                                                 final int maxRate,
                                                 final boolean browseOnly) throws ActiveMQException {
      checkClosed();

      ClientConsumerInternal consumer = sessionContext.createConsumer(queueName, filterString, priority, windowSize, maxRate, ackBatchSize, browseOnly, executor, flowControlExecutor);

      addConsumer(consumer);

      // Now we send window size credits to start the consumption
      // We even send it if windowSize == -1, since we need to start the
      // consumer

      // TODO: this could semantically change on other servers. I know for instance on stomp this is just an ignore
      if (consumer.getClientWindowSize() != 0) {
         sessionContext.sendConsumerCredits(consumer, consumer.getInitialWindowSize());
      }

      return consumer;
   }

   private ClientProducer internalCreateProducer(final SimpleString address,
                                                 final int maxRate) throws ActiveMQException {
      checkClosed();

      ClientProducerInternal producer = new ClientProducerImpl(this, address, maxRate == -1 ? null : new TokenBucketLimiterImpl(maxRate, false), autoCommitSends && blockOnNonDurableSend, autoCommitSends && blockOnDurableSend, autoGroup, groupID == null ? null : new SimpleString(groupID), minLargeMessageSize, sessionContext);

      addProducer(producer);

      return producer;
   }

   @Deprecated
   private void internalCreateQueue(final SimpleString address,
                                    final SimpleString queueName,
                                    final boolean temp,
                                    final boolean autoCreated,
                                    final QueueAttributes queueAttributes) throws ActiveMQException {
      internalCreateQueue(queueAttributes.toQueueConfiguration().setName(queueName).setAddress(address).setTemporary(temp).setAutoCreated(autoCreated));
   }

   private void internalCreateQueue(final QueueConfiguration queueConfiguration) throws ActiveMQException {
      checkClosed();

      if (queueConfiguration.isDurable() && queueConfiguration.isTemporary()) {
         throw ActiveMQClientMessageBundle.BUNDLE.queueMisConfigured();
      }

      startCall();
      try {
         sessionContext.createQueue(queueConfiguration);
      } finally {
         endCall();
      }
   }

   private void checkXA() throws XAException {
      if (!xa) {
         ActiveMQClientLogger.LOGGER.sessionNotXA();
         throw new XAException(XAException.XAER_RMFAIL);
      }
   }

   private void checkClosed() throws ActiveMQException {
      if (closed || inClose) {
         throw ActiveMQClientMessageBundle.BUNDLE.sessionClosed();
      }
   }

   private ClientConsumerInternal getConsumer(final ConsumerContext consumerContext) {
      synchronized (consumers) {
         return consumers.get(consumerContext);
      }
   }

   private void doCleanup(boolean failingOver) {
      if (logger.isDebugEnabled()) {
         logger.debug("calling cleanup on " + this);
      }

      synchronized (this) {
         closed = true;

         sessionContext.cleanup();
      }

      sessionFactory.removeSession(this, failingOver);
   }

   private void cleanUpChildren() throws ActiveMQException {
      Set<ClientConsumerInternal> consumersClone = cloneConsumers();

      for (ClientConsumerInternal consumer : consumersClone) {
         consumer.cleanUp();
      }

      Set<ClientProducerInternal> producersClone = cloneProducers();

      for (ClientProducerInternal producer : producersClone) {
         producer.cleanUp();
      }
   }

   /**
    * Not part of the interface, used on tests only
    *
    * @return
    */
   public Set<ClientProducerInternal> cloneProducers() {
      Set<ClientProducerInternal> producersClone;

      synchronized (producers) {
         producersClone = new HashSet<>(producers);
      }
      return producersClone;
   }

   /**
    * Not part of the interface, used on tests only
    *
    * @return
    */
   public Set<ClientConsumerInternal> cloneConsumers() {
      synchronized (consumers) {
         return new HashSet<>(consumers.values());
      }
   }

   public Map<ConsumerContext, ClientConsumerInternal> cloneConsumerEntries() {
      synchronized (consumers) {
         return new HashMap<>(consumers);
      }
   }

   private void closeChildren() throws ActiveMQException {
      Set<ClientConsumerInternal> consumersClone = cloneConsumers();

      for (ClientConsumer consumer : consumersClone) {
         consumer.close();
      }

      Set<ClientProducerInternal> producersClone = cloneProducers();

      for (ClientProducer producer : producersClone) {
         producer.close();
      }
   }

   private void flushAcks() throws ActiveMQException {
      for (ClientConsumerInternal consumer : cloneConsumers()) {
         consumer.flushAcks();
      }
   }

   /**
    * If you ever tried to debug XIDs you will know what this is about.
    * This will serialize and deserialize the XID to the same way it's going to be printed on server logs
    * or print-data.
    * <p>
    * This will convert to the same XID deserialized on the Server, hence we will be able to debug eventual stuff
    *
    * @param xid
    * @return
    */
   public static Object convert(Xid xid) {
      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(200);
      XidCodecSupport.encodeXid(xid, buffer);

      Object obj = XidCodecSupport.decodeXid(buffer);

      return "xid=" + obj + ",clientXID=" + xid;
   }

   private String convertTXFlag(final int flags) {
      if (flags == XAResource.TMSUSPEND) {
         return "SESS_XA_SUSPEND";
      } else if (flags == XAResource.TMSUCCESS) {
         return "TMSUCCESS";
      } else if (flags == XAResource.TMFAIL) {
         return "TMFAIL";
      } else if (flags == XAResource.TMJOIN) {
         return "TMJOIN";
      } else if (flags == XAResource.TMRESUME) {
         return "TMRESUME";
      } else if (flags == XAResource.TMNOFLAGS) {
         // Don't need to flush since the previous end will have done this
         return "TMNOFLAGS";
      } else {
         return "XAER_INVAL(" + flags + ")";
      }
   }

   @Override
   public void setStopSignal() {
      mayAttemptToFailover = false;
   }

   @Override
   public boolean isConfirmationWindowEnabled() {
      if (confirmationWindowWarning.disabled) {
         if (!confirmationWindowWarning.warningIssued.get()) {
            ActiveMQClientLogger.LOGGER.confirmationWindowDisabledWarning();
            confirmationWindowWarning.warningIssued.set(true);
         }
         return false;
      }
      return true;
   }

   @Override
   public void scheduleConfirmation(final SendAcknowledgementHandler handler, final Message message) {
      executor.execute(new Runnable() {
         @Override
         public void run() {
            handler.sendAcknowledged(message);
         }
      });
   }

   @Override
   public SessionContext getSessionContext() {
      return sessionContext;
   }
}
