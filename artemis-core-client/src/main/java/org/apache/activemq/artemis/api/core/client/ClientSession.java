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

import javax.transaction.xa.XAResource;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueAttributes;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;

/**
 * A ClientSession is a single-threaded parent object required for producing and consuming messages.
 * <p>
 * Only a single thread may be used to operate on the session and its child producers and consumers, other than close()
 * methods which may be called from another thread. Setting a MessageHandler on a consumer renders the session, and all
 * its child producers and consumers, to be dedicated to the session-wide handler delivery thread of control.
 */
public interface ClientSession extends XAResource, AutoCloseable {

   /**
    * This is used to identify a ClientSession as used by the JMS Layer The JMS Layer will add this through Meta-data,
    * so the server or management layers can identify session created over core API purely or through the JMS Layer
    */
   String JMS_SESSION_IDENTIFIER_PROPERTY = "jms-session";

   /**
    * Just like {@link #JMS_SESSION_IDENTIFIER_PROPERTY} this is used to identify the ClientID
    * over JMS Session. However this is only used when the JMS Session.clientID is set (which is optional). With this
    * property management tools and the server can identify the jms-client-id used over JMS
    */
   String JMS_SESSION_CLIENT_ID_PROPERTY = "jms-client-id";

   /**
    * Information returned by a binding query
    *
    * @see ClientSession#addressQuery(SimpleString)
    */
   interface AddressQuery {

      /**
       * {@return {@code true} if the binding exists, {@code false} else}
       */
      boolean isExists();

      /**
       * {@return the names of the queues bound to the binding}
       */
      List<SimpleString> getQueueNames();

      /**
       * {@return {@code true} if auto-queue-creation for this address is enabled, {@code false} else}
       */
      boolean isAutoCreateQueues();

      /**
       * {@return {@code true} if auto-address-creation for this address is enabled, {@code false} else}
       */
      boolean isAutoCreateAddresses();

      boolean isDefaultPurgeOnNoConsumers();

      int getDefaultMaxConsumers();

      Boolean isDefaultLastValueQueue();

      Boolean isDefaultExclusive();

      SimpleString getDefaultLastValueKey();

      Boolean isDefaultNonDestructive();

      Integer getDefaultConsumersBeforeDispatch();

      Long getDefaultDelayBeforeDispatch();

      boolean isSupportsMulticast();

      boolean isSupportsAnycast();
   }

   /**
    * Information returned by a queue query
    *
    * @see ClientSession#queueQuery(SimpleString)
    */
   interface QueueQuery {

      /**
       * {@return {@code true} if the queue exists, {@code false} else}
       */
      boolean isExists();

      /**
       * {@return {@code true} if the queue is temporary, {@code false} else}
       */
      boolean isTemporary();

      /**
       * {@return {@code true} if the queue is durable, {@code false} else}
       */
      boolean isDurable();

      /**
       * {@return {@code true} if auto-creation for this queue is enabled and if the queue queried is a JMS queue,
       * {@code false} else}
       */
      boolean isAutoCreateQueues();

      /**
       * {@return the number of consumers attached to the queue}
       */
      int getConsumerCount();

      /**
       * {@return the number of messages in the queue}
       */
      long getMessageCount();

      /**
       * {@return the queue's filter string (or {@code null} if the queue has no filter)}
       */
      SimpleString getFilterString();

      /**
       * {@return the address that the queue is bound to}
       */
      SimpleString getAddress();

      /**
       * Return the name of the queue
       */
      SimpleString getName();

      RoutingType getRoutingType();

      int getMaxConsumers();

      boolean isPurgeOnNoConsumers();

      boolean isAutoCreated();

      Boolean isExclusive();

      Boolean isLastValue();

      SimpleString getLastValueKey();

      Boolean isNonDestructive();

      Integer getConsumersBeforeDispatch();

      Long getDelayBeforeDispatch();

      Integer getDefaultConsumerWindowSize();

      Boolean isGroupRebalance();

      Boolean isGroupRebalancePauseDispatch();

      Integer getGroupBuckets();

      SimpleString getGroupFirstKey();

      Boolean isAutoDelete();

      Long getAutoDeleteDelay();

      Long getAutoDeleteMessageCount();

      Long getRingSize();

      Boolean isEnabled();

      Boolean isConfigurationManaged();
   }

   // Lifecycle operations ------------------------------------------

   /**
    * Starts the session. The session must be started before ClientConsumers created by the session can consume messages
    * from the queue.
    *
    * @throws ActiveMQException if an exception occurs while starting the session
    */
   ClientSession start() throws ActiveMQException;

   /**
    * Stops the session. ClientConsumers created by the session can not consume messages when the session is stopped.
    *
    * @throws ActiveMQException if an exception occurs while stopping the session
    */
   void stop() throws ActiveMQException;

   /**
    * Closes the session.
    *
    * @throws ActiveMQException if an exception occurs while closing the session
    */
   @Override
   void close() throws ActiveMQException;

   /**
    * {@return {@code true} if the session is closed, {@code false} else}
    */
   boolean isClosed();

   /**
    * Adds a FailureListener to the session which is notified if a failure occurs on the session.
    *
    * @param listener the listener to add
    */
   void addFailureListener(SessionFailureListener listener);

   /**
    * Removes a FailureListener to the session.
    *
    * @param listener the listener to remove
    * @return {@code true} if the listener was removed, {@code false} else
    */
   boolean removeFailureListener(SessionFailureListener listener);

   /**
    * Adds a FailoverEventListener to the session which is notified if a failover event  occurs on the session.
    *
    * @param listener the listener to add
    */
   void addFailoverListener(FailoverEventListener listener);

   /**
    * Removes a FailoverEventListener to the session.
    *
    * @param listener the listener to remove
    * @return {@code true} if the listener was removed, {@code false} else
    */
   boolean removeFailoverListener(FailoverEventListener listener);

   /**
    * {@return the server's {@code incrementingVersion}}
    */
   int getVersion();

   /**
    * Create Address with a single initial routing type
    */
   void createAddress(SimpleString address, EnumSet<RoutingType> routingTypes, boolean autoCreated) throws ActiveMQException;

   /**
    * Create Address with a single initial routing type
    */
   @Deprecated
   void createAddress(SimpleString address, Set<RoutingType> routingTypes, boolean autoCreated) throws ActiveMQException;

   /**
    * Create Address with a single initial routing type
    */
   void createAddress(SimpleString address, RoutingType routingType, boolean autoCreated) throws ActiveMQException;

   // Queue Operations ----------------------------------------------

   /**
    * This method creates a queue based on the {@link QueueConfiguration} input. See {@link QueueConfiguration} for more
    * details on configuration specifics.
    * <p>
    * Some static defaults will be enforced for properties which are not set on the {@code QueueConfiguration}:
    * <ul>
    * <li>{@code transient} : {@code false}
    * <li>{@code temporary} : {@code false}
    * <li>{@code durable} : {@code true}
    * <li>{@code autoCreated} : {@code false}
    * <li>{@code internal} : {@code false}
    * <li>{@code configurationManaged} : {@code false}
    * <li>{@code maxConsumers} : {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#getDefaultMaxQueueConsumers()}
    * <li>{@code purgeOnNoConsumers} : {@link org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration#getDefaultPurgeOnNoConsumers()}
    * </ul>
    * Some dynamic defaults will be enforced via address-settings for the corresponding unset properties:
    * <ul>
    * <li>{@code exclusive}
    * <li>{@code groupRebalance}
    * <li>{@code groupBuckets}
    * <li>{@code groupFirstKey}
    * <li>{@code lastValue}
    * <li>{@code lastValueKey}
    * <li>{@code nonDestructive}
    * <li>{@code consumersBeforeDispatch}
    * <li>{@code delayBeforeDispatch}
    * <li>{@code ringSize}
    * <li>{@code routingType}
    * <li>{@code autoCreateAddress}
    * <li>{@code autoDelete} (only set if queue was auto-created)
    * <li>{@code autoDeleteDelay}
    * <li>{@code autoDeleteMessageCount}
    * </ul>
    *
    * @param queueConfiguration the configuration to use when creating the queue
    */
   void createQueue(QueueConfiguration queueConfiguration) throws ActiveMQException;

   /**
    * This method is essentially the same as {@link #createQueue(QueueConfiguration)} with a few key exceptions.
    * <p>
    * If {@code durable} is {@code true} then:
    * <ul>
    * <li>{@code transient} will be forced to {@code false}
    * <li>{@code temporary} will be forced to {@code false}
    * </ul>
    * If {@code durable} is {@code false} then:
    * <ul>
    * <li>{@code transient} will be forced to {@code true}
    * <li>{@code temporary} will be forced to {@code true}
    * </ul>
    * In all instances {@code autoCreated} will be forced to {@code false} and {@code autoCreatedAddress} will be forced
    * to {@code true}.
    *
    * @see #createQueue(QueueConfiguration)
    */
   void createSharedQueue(QueueConfiguration queueConfiguration) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em> queue.
    *
    * @param address   the queue will be bound to this address
    * @param queueName the name of the queue
    * @param durable   whether the queue is durable or not
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(SimpleString address, SimpleString queueName, boolean durable) throws ActiveMQException;

   /**
    * Creates a transient queue. A queue that will exist as long as there are consumers. When the last consumer is
    * closed the queue will be deleted
    * <p>
    * Notice: you will get an exception if the address or the filter doesn't match to an already existent queue
    *
    * @param address   the queue will be bound to this address
    * @param queueName the name of the queue
    * @param durable   if the queue is durable
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createSharedQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createSharedQueue(SimpleString address, SimpleString queueName, boolean durable) throws ActiveMQException;

   /**
    * Creates a transient queue. A queue that will exist as long as there are consumers. When the last consumer is
    * closed the queue will be deleted
    * <p>
    * Notice: you will get an exception if the address or the filter doesn't match to an already existent queue
    *
    * @param address   the queue will be bound to this address
    * @param queueName the name of the queue
    * @param filter    whether the queue is durable or not
    * @param durable   if the queue is durable
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createSharedQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createSharedQueue(SimpleString address,
                          SimpleString queueName,
                          SimpleString filter,
                          boolean durable) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em> queue.
    *
    * @param address   the queue will be bound to this address
    * @param queueName the name of the queue
    * @param durable   whether the queue is durable or not
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(String address, String queueName, boolean durable) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em> queue <em>non-durable</em> queue.
    *
    * @param address   the queue will be bound to this address
    * @param queueName the name of the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(String address, String queueName) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em> queue <em>non-durable</em> queue.
    *
    * @param address   the queue will be bound to this address
    * @param queueName the name of the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(SimpleString address, SimpleString queueName) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em> queue.
    *
    * @param address   the queue will be bound to this address
    * @param queueName the name of the queue
    * @param filter    only messages which match this filter will be put in the queue
    * @param durable   whether the queue is durable or not
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(SimpleString address,
                    SimpleString queueName,
                    SimpleString filter,
                    boolean durable) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em>queue.
    *
    * @param address   the queue will be bound to this address
    * @param queueName the name of the queue
    * @param durable   whether the queue is durable or not
    * @param filter    only messages which match this filter will be put in the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(String address, String queueName, String filter, boolean durable) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em> queue.
    *
    * @param address     the queue will be bound to this address
    * @param queueName   the name of the queue
    * @param filter      only messages which match this filter will be put in the queue
    * @param durable     whether the queue is durable or not
    * @param autoCreated whether to mark this queue as autoCreated or not
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(SimpleString address,
                    SimpleString queueName,
                    SimpleString filter,
                    boolean durable,
                    boolean autoCreated) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em>queue.
    *
    * @param address     the queue will be bound to this address
    * @param queueName   the name of the queue
    * @param filter      only messages which match this filter will be put in the queue
    * @param durable     whether the queue is durable or not
    * @param autoCreated whether to mark this queue as autoCreated or not
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(String address, String queueName, String filter, boolean durable, boolean autoCreated) throws ActiveMQException;

   /**
    * Creates a <em>temporary</em> queue.
    *
    * @param address   the queue will be bound to this address
    * @param queueName the name of the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createTemporaryQueue(SimpleString address, SimpleString queueName) throws ActiveMQException;

   /**
    * Creates a <em>temporary</em> queue.
    *
    * @param address   the queue will be bound to this address
    * @param queueName the name of the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createTemporaryQueue(String address, String queueName) throws ActiveMQException;

   /**
    * Creates a <em>temporary</em> queue with a filter.
    *
    * @param address   the queue will be bound to this address
    * @param queueName the name of the queue
    * @param filter    only messages which match this filter will be put in the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createTemporaryQueue(SimpleString address,
                             SimpleString queueName,
                             SimpleString filter) throws ActiveMQException;

   /**
    * Creates a <em>temporary</em> queue with a filter.
    *
    * @param address   the queue will be bound to this address
    * @param queueName the name of the queue
    * @param filter    only messages which match this filter will be put in the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createTemporaryQueue(String address, String queueName, String filter) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em> queue.
    *
    * @param address     the queue will be bound to this address
    * @param routingType the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName   the name of the queue
    * @param durable     whether the queue is durable or not
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, boolean durable) throws ActiveMQException;

   /**
    * Creates a transient queue. A queue that will exist as long as there are consumers. When the last consumer is
    * closed the queue will be deleted
    * <p>
    * Notice: you will get an exception if the address or the filter doesn't match to an already existent queue
    *
    * @param address     the queue will be bound to this address
    * @param routingType the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName   the name of the queue
    * @param durable     if the queue is durable
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createSharedQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createSharedQueue(SimpleString address, RoutingType routingType, SimpleString queueName, boolean durable) throws ActiveMQException;

   /**
    * Creates a transient queue. A queue that will exist as long as there are consumers. When the last consumer is
    * closed the queue will be deleted
    * <p>
    * Notice: you will get an exception if the address or the filter doesn't match to an already existent queue
    *
    * @param address     the queue will be bound to this address
    * @param routingType the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName   the name of the queue
    * @param filter      whether the queue is durable or not
    * @param durable     if the queue is durable
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createSharedQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createSharedQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                          boolean durable) throws ActiveMQException;

   /**
    * Creates Shared queue. A queue that will exist as long as there are consumers or is durable.
    *
    * @param address            the queue will be bound to this address
    * @param routingType        the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName          the name of the queue
    * @param filter             whether the queue is durable or not
    * @param durable            if the queue is durable
    * @param maxConsumers       how many concurrent consumers will be allowed on this queue
    * @param purgeOnNoConsumers whether to delete the contents of the queue when the last consumer disconnects
    * @param exclusive          if the queue is exclusive queue
    * @param lastValue          if the queue is last value queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createSharedQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createSharedQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                          boolean durable, Integer maxConsumers, Boolean purgeOnNoConsumers, Boolean exclusive, Boolean lastValue) throws ActiveMQException;

   /**
    * Creates Shared queue. A queue that will exist as long as there are consumers or is durable.
    *
    * @param address         the queue will be bound to this address
    * @param queueName       the name of the queue
    * @param queueAttributes attributes for the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createSharedQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createSharedQueue(SimpleString address, SimpleString queueName, QueueAttributes queueAttributes) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em> queue.
    *
    * @param address     the queue will be bound to this address
    * @param routingType the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName   the name of the queue
    * @param durable     whether the queue is durable or not
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(String address, RoutingType routingType, String queueName, boolean durable) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em> queue <em>non-durable</em> queue.
    *
    * @param address     the queue will be bound to this address
    * @param routingType the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName   the name of the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(String address, RoutingType routingType, String queueName) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em> queue <em>non-durable</em> queue.
    *
    * @param address     the queue will be bound to this address
    * @param routingType the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName   the name of the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(SimpleString address, RoutingType routingType, SimpleString queueName) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em> queue.
    *
    * @param address     the queue will be bound to this address
    * @param routingType the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName   the name of the queue
    * @param filter      only messages which match this filter will be put in the queue
    * @param durable     whether the queue is durable or not
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                    boolean durable) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em>queue.
    *
    * @param address     the queue will be bound to this address
    * @param routingType the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName   the name of the queue
    * @param filter      only messages which match this filter will be put in the queue
    * @param durable     whether the queue is durable or not
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(String address, RoutingType routingType, String queueName, String filter, boolean durable) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em> queue.
    *
    * @param address     the queue will be bound to this address
    * @param routingType the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName   the name of the queue
    * @param filter      only messages which match this filter will be put in the queue
    * @param durable     whether the queue is durable or not
    * @param autoCreated whether to mark this queue as autoCreated or not
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                    boolean durable, boolean autoCreated) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em> queue.
    *
    * @param address            the queue will be bound to this address
    * @param routingType        the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName          the name of the queue
    * @param filter             only messages which match this filter will be put in the queue
    * @param durable            whether the queue is durable or not
    * @param autoCreated        whether to mark this queue as autoCreated or not
    * @param maxConsumers       how many concurrent consumers will be allowed on this queue
    * @param purgeOnNoConsumers whether to delete the contents of the queue when the last consumer disconnects
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                    boolean durable, boolean autoCreated, int maxConsumers, boolean purgeOnNoConsumers) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em> queue.
    *
    * @param address            the queue will be bound to this address
    * @param routingType        the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName          the name of the queue
    * @param filter             only messages which match this filter will be put in the queue
    * @param durable            whether the queue is durable or not
    * @param autoCreated        whether to mark this queue as autoCreated or not
    * @param maxConsumers       how many concurrent consumers will be allowed on this queue
    * @param purgeOnNoConsumers whether to delete the contents of the queue when the last consumer disconnects
    * @param exclusive          whether the queue should be exclusive
    * @param lastValue          whether the queue should be lastValue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                    boolean durable, boolean autoCreated, int maxConsumers, boolean purgeOnNoConsumers, Boolean exclusive, Boolean lastValue) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em> queue.
    *
    * @param address         the queue will be bound to this address
    * @param queueName       the name of the queue
    * @param autoCreated     whether to mark this queue as autoCreated or not
    * @param queueAttributes attributes for the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(SimpleString address, SimpleString queueName, boolean autoCreated, QueueAttributes queueAttributes) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em>queue.
    *
    * @param address     the queue will be bound to this address
    * @param routingType the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName   the name of the queue
    * @param filter      only messages which match this filter will be put in the queue
    * @param durable     whether the queue is durable or not
    * @param autoCreated whether to mark this queue as autoCreated or not
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(String address, RoutingType routingType, String queueName, String filter, boolean durable, boolean autoCreated) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em>queue.
    *
    * @param address            the queue will be bound to this address
    * @param routingType        the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName          the name of the queue
    * @param filter             only messages which match this filter will be put in the queue
    * @param durable            whether the queue is durable or not
    * @param autoCreated        whether to mark this queue as autoCreated or not
    * @param maxConsumers       how many concurrent consumers will be allowed on this queue
    * @param purgeOnNoConsumers whether to delete the contents of the queue when the last consumer disconnects
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(String address, RoutingType routingType, String queueName, String filter, boolean durable, boolean autoCreated,
                           int maxConsumers, boolean purgeOnNoConsumers) throws ActiveMQException;

   /**
    * Creates a <em>non-temporary</em>queue.
    *
    * @param address            the queue will be bound to this address
    * @param routingType        the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName          the name of the queue
    * @param filter             only messages which match this filter will be put in the queue
    * @param durable            whether the queue is durable or not
    * @param autoCreated        whether to mark this queue as autoCreated or not
    * @param maxConsumers       how many concurrent consumers will be allowed on this queue
    * @param purgeOnNoConsumers whether to delete the contents of the queue when the last consumer disconnects
    * @param exclusive          whether the queue should be exclusive
    * @param lastValue          whether the queue should be lastValue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createQueue(String address, RoutingType routingType, String queueName, String filter, boolean durable, boolean autoCreated,
                    int maxConsumers, boolean purgeOnNoConsumers, Boolean exclusive, Boolean lastValue) throws ActiveMQException;

   /**
    * Creates a <em>temporary</em> queue.
    *
    * @param address     the queue will be bound to this address
    * @param routingType the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName   the name of the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createTemporaryQueue(SimpleString address, RoutingType routingType, SimpleString queueName) throws ActiveMQException;

   /**
    * Creates a <em>temporary</em> queue.
    *
    * @param address     the queue will be bound to this address
    * @param routingType the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName   the name of the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createTemporaryQueue(String address, RoutingType routingType, String queueName) throws ActiveMQException;

   /**
    * Creates a <em>temporary</em> queue with a filter.
    *
    * @param address            the queue will be bound to this address
    * @param routingType        the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName          the name of the queue
    * @param filter             only messages which match this filter will be put in the queue
    * @param maxConsumers       how many concurrent consumers will be allowed on this queue
    * @param purgeOnNoConsumers whether to delete the contents of the queue when the last consumer disconnects
    * @param exclusive          if the queue is exclusive queue
    * @param lastValue          if the queue is last value queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createTemporaryQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter, int maxConsumers,
                             boolean purgeOnNoConsumers, Boolean exclusive, Boolean lastValue) throws ActiveMQException;

   /**
    * Creates a <em>temporary</em> queue with a filter.
    *
    * @param address         the queue will be bound to this address
    * @param queueName       the name of the queue
    * @param queueAttributes attributes for the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createTemporaryQueue(SimpleString address, SimpleString queueName, QueueAttributes queueAttributes) throws ActiveMQException;

   /**
    * Creates a <em>temporary</em> queue with a filter.
    *
    * @param address     the queue will be bound to this address
    * @param routingType the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName   the name of the queue
    * @param filter      only messages which match this filter will be put in the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createTemporaryQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter) throws ActiveMQException;

   /**
    * Creates a <em>temporary</em> queue with a filter.
    *
    * @param address     the queue will be bound to this address
    * @param routingType the routing type for this queue, MULTICAST or ANYCAST
    * @param queueName   the name of the queue
    * @param filter      only messages which match this filter will be put in the queue
    * @throws ActiveMQException in an exception occurs while creating the queue
    * @deprecated use {@link #createQueue(QueueConfiguration)} instead
    */
   @Deprecated
   void createTemporaryQueue(String address, RoutingType routingType, String queueName, String filter) throws ActiveMQException;

   /**
    * Deletes the queue.
    *
    * @param queueName the name of the queue to delete
    * @throws ActiveMQException if there is no queue for the given name or if the queue has consumers
    */
   void deleteQueue(SimpleString queueName) throws ActiveMQException;

   /**
    * Deletes the queue.
    *
    * @param queueName the name of the queue to delete
    * @throws ActiveMQException if there is no queue for the given name or if the queue has consumers
    */
   void deleteQueue(String queueName) throws ActiveMQException;

   // Consumer Operations -------------------------------------------

   /**
    * Creates a ClientConsumer to consume message from the queue with the given name.
    *
    * @param queueName name of the queue to consume messages from
    * @return a ClientConsumer
    * @throws ActiveMQException if an exception occurs while creating the ClientConsumer
    */
   ClientConsumer createConsumer(SimpleString queueName) throws ActiveMQException;

   /**
    * Creates a ClientConsumer to consume messages from the queue with the given name.
    *
    * @param queueName name of the queue to consume messages from
    * @return a ClientConsumer
    * @throws ActiveMQException if an exception occurs while creating the ClientConsumer
    */
   ClientConsumer createConsumer(String queueName) throws ActiveMQException;

   /**
    * Creates a ClientConsumer to consume messages matching the filter from the queue with the given name.
    *
    * @param queueName name of the queue to consume messages from
    * @param filter    only messages which match this filter will be consumed
    * @return a ClientConsumer
    * @throws ActiveMQException if an exception occurs while creating the ClientConsumer
    */
   ClientConsumer createConsumer(SimpleString queueName, SimpleString filter) throws ActiveMQException;

   /**
    * Creates a ClientConsumer to consume messages matching the filter from the queue with the given name.
    *
    * @param queueName name of the queue to consume messages from
    * @param filter    only messages which match this filter will be consumed
    * @return a ClientConsumer
    * @throws ActiveMQException if an exception occurs while creating the ClientConsumer
    */
   ClientConsumer createConsumer(String queueName, String filter) throws ActiveMQException;

   /**
    * Creates a ClientConsumer to consume or browse messages from the queue with the given name.
    * <p>
    * If {@code browseOnly} is {@code true}, the ClientConsumer will receive the messages from the queue but they will
    * not be consumed (the messages will remain in the queue). Note that paged messages will not be in the queue, and
    * will therefore not be visible if {@code browseOnly} is {@code true}.
    * <p>
    * If {@code browseOnly} is {@code false}, the ClientConsumer will behave like consume the messages from the queue
    * and the messages will effectively be removed from the queue.
    *
    * @param queueName  name of the queue to consume messages from
    * @param browseOnly whether the ClientConsumer will only browse the queue or consume messages.
    * @return a ClientConsumer
    * @throws ActiveMQException if an exception occurs while creating the ClientConsumer
    */
   ClientConsumer createConsumer(SimpleString queueName, boolean browseOnly) throws ActiveMQException;

   /**
    * Creates a ClientConsumer to consume or browse messages from the queue with the given name.
    * <p>
    * If {@code browseOnly} is {@code true}, the ClientConsumer will receive the messages from the queue but they will
    * not be consumed (the messages will remain in the queue). Note that paged messages will not be in the queue, and
    * will therefore not be visible if {@code browseOnly} is {@code true}.
    * <p>
    * If {@code browseOnly} is {@code false}, the ClientConsumer will behave like consume the messages from the queue
    * and the messages will effectively be removed from the queue.
    *
    * @param queueName  name of the queue to consume messages from
    * @param browseOnly whether the ClientConsumer will only browse the queue or consume messages.
    * @return a ClientConsumer
    * @throws ActiveMQException if an exception occurs while creating the ClientConsumer
    */
   ClientConsumer createConsumer(String queueName, boolean browseOnly) throws ActiveMQException;

   /**
    * Creates a ClientConsumer to consume or browse messages matching the filter from the queue with the given name.
    * <p>
    * If {@code browseOnly} is {@code true}, the ClientConsumer will receive the messages from the queue but they will
    * not be consumed (the messages will remain in the queue). Note that paged messages will not be in the queue, and
    * will therefore not be visible if {@code browseOnly} is {@code true}.
    * <p>
    * If {@code browseOnly} is {@code false}, the ClientConsumer will behave like consume the messages from the queue
    * and the messages will effectively be removed from the queue.
    *
    * @param queueName  name of the queue to consume messages from
    * @param filter     only messages which match this filter will be consumed
    * @param browseOnly whether the ClientConsumer will only browse the queue or consume messages.
    * @return a ClientConsumer
    * @throws ActiveMQException if an exception occurs while creating the ClientConsumer
    */
   ClientConsumer createConsumer(String queueName, String filter, boolean browseOnly) throws ActiveMQException;

   /**
    * Creates a ClientConsumer to consume or browse messages matching the filter from the queue with the given name.
    * <p>
    * If {@code browseOnly} is {@code true}, the ClientConsumer will receive the messages from the queue but they will
    * not be consumed (the messages will remain in the queue). Note that paged messages will not be in the queue, and
    * will therefore not be visible if {@code browseOnly} is {@code true}.
    * <p>
    * If {@code browseOnly} is {@code false}, the ClientConsumer will behave like consume the messages from the queue
    * and the messages will effectively be removed from the queue.
    *
    * @param queueName  name of the queue to consume messages from
    * @param filter     only messages which match this filter will be consumed
    * @param browseOnly whether the ClientConsumer will only browse the queue or consume messages.
    * @return a ClientConsumer
    * @throws ActiveMQException if an exception occurs while creating the ClientConsumer
    */
   ClientConsumer createConsumer(SimpleString queueName, SimpleString filter, boolean browseOnly) throws ActiveMQException;

   /**
    * Creates a ClientConsumer to consume or browse messages matching the filter from the queue with the given name.
    * <p>
    * If {@code browseOnly} is {@code true}, the ClientConsumer will receive the messages from the queue but they will
    * not be consumed (the messages will remain in the queue). Note that paged messages will not be in the queue, and
    * will therefore not be visible if {@code browseOnly} is {@code true}.
    * <p>
    * If {@code browseOnly} is {@code false}, the ClientConsumer will behave like consume the messages from the queue
    * and the messages will effectively be removed from the queue.
    *
    * @param queueName  name of the queue to consume messages from
    * @param filter     only messages which match this filter will be consumed
    * @param priority   the consumer priority
    * @param browseOnly whether the ClientConsumer will only browse the queue or consume messages.
    * @return a ClientConsumer
    * @throws ActiveMQException if an exception occurs while creating the ClientConsumer
    */
   ClientConsumer createConsumer(SimpleString queueName, SimpleString filter, int priority, boolean browseOnly) throws ActiveMQException;

   /**
    * Creates a ClientConsumer to consume or browse messages matching the filter from the queue with the given name.
    * <p>
    * If {@code browseOnly} is {@code true}, the ClientConsumer will receive the messages from the queue but they will
    * not be consumed (the messages will remain in the queue). Note that paged messages will not be in the queue, and
    * will therefore not be visible if {@code browseOnly} is {@code true}.
    * <p>
    * If {@code browseOnly} is {@code false}, the ClientConsumer will behave like consume the messages from the queue
    * and the messages will effectively be removed from the queue.
    *
    * @param queueName  name of the queue to consume messages from
    * @param filter     only messages which match this filter will be consumed
    * @param windowSize the consumer window size
    * @param maxRate    the maximum rate to consume messages
    * @param browseOnly whether the ClientConsumer will only browse the queue or consume messages.
    * @return a ClientConsumer
    * @throws ActiveMQException if an exception occurs while creating the ClientConsumer
    */
   ClientConsumer createConsumer(SimpleString queueName, SimpleString filter, int windowSize, int maxRate, boolean browseOnly) throws ActiveMQException;

   /**
    * Creates a ClientConsumer to consume or browse messages matching the filter from the queue with the given name.
    * <p>
    * If {@code browseOnly} is {@code true}, the ClientConsumer will receive the messages from the queue but they will
    * not be consumed (the messages will remain in the queue). Note that paged messages will not be in the queue, and
    * will therefore not be visible if {@code browseOnly} is {@code true}.
    * <p>
    * If {@code browseOnly} is {@code false}, the ClientConsumer will behave like consume the messages from the queue
    * and the messages will effectively be removed from the queue.
    *
    * @param queueName  name of the queue to consume messages from
    * @param filter     only messages which match this filter will be consumed
    * @param priority   the consumer priority
    * @param windowSize the consumer window size
    * @param maxRate    the maximum rate to consume messages
    * @param browseOnly whether the ClientConsumer will only browse the queue or consume messages.
    * @return a ClientConsumer
    * @throws ActiveMQException if an exception occurs while creating the ClientConsumer
    */
   ClientConsumer createConsumer(SimpleString queueName,
                                 SimpleString filter,
                                 int priority,
                                 int windowSize,
                                 int maxRate,
                                 boolean browseOnly) throws ActiveMQException;

   /**
    * Creates a ClientConsumer to consume or browse messages matching the filter from the queue with the given name.
    * <p>
    * If {@code browseOnly} is {@code true}, the ClientConsumer will receive the messages from the queue but they will
    * not be consumed (the messages will remain in the queue). Note that paged messages will not be in the queue, and
    * will therefore not be visible if {@code browseOnly} is {@code true}.
    * <p>
    * If {@code browseOnly} is {@code false}, the ClientConsumer will behave like consume the messages from the queue
    * and the messages will effectively be removed from the queue.
    *
    * @param queueName  name of the queue to consume messages from
    * @param filter     only messages which match this filter will be consumed
    * @param windowSize the consumer window size
    * @param maxRate    the maximum rate to consume messages
    * @param browseOnly whether the ClientConsumer will only browse the queue or consume messages.
    * @return a ClientConsumer
    * @throws ActiveMQException if an exception occurs while creating the ClientConsumer
    */
   ClientConsumer createConsumer(String queueName,
                                 String filter,
                                 int windowSize,
                                 int maxRate,
                                 boolean browseOnly) throws ActiveMQException;

   // Producer Operations -------------------------------------------

   /**
    * Creates a producer with no default address. Address must be specified every time a message is sent
    *
    * @return a ClientProducer
    * @see ClientProducer#send(SimpleString, org.apache.activemq.artemis.api.core.Message)
    */
   ClientProducer createProducer() throws ActiveMQException;

   /**
    * Creates a producer which sends messages to the given address
    *
    * @param address the address to send messages to
    * @return a ClientProducer
    * @throws ActiveMQException if an exception occurs while creating the ClientProducer
    */
   ClientProducer createProducer(SimpleString address) throws ActiveMQException;

   /**
    * Creates a producer which sends messages to the given address
    *
    * @param address the address to send messages to
    * @return a ClientProducer
    * @throws ActiveMQException if an exception occurs while creating the ClientProducer
    */
   ClientProducer createProducer(String address) throws ActiveMQException;

   /**
    * Creates a producer which sends messages to the given address
    *
    * @param address the address to send messages to
    * @param rate    the producer rate
    * @return a ClientProducer
    * @throws ActiveMQException if an exception occurs while creating the ClientProducer
    */
   ClientProducer createProducer(SimpleString address, int rate) throws ActiveMQException;

   // Message operations --------------------------------------------

   /**
    * Creates a ClientMessage.
    *
    * @param durable whether the created message is durable or not
    * @return a ClientMessage
    */
   ClientMessage createMessage(boolean durable);

   /**
    * Creates a ClientMessage.
    *
    * @param type    type of the message
    * @param durable whether the created message is durable or not
    * @return a ClientMessage
    */
   ClientMessage createMessage(byte type, boolean durable);

   /**
    * Creates a ClientMessage.
    *
    * @param type       type of the message
    * @param durable    whether the created message is durable or not
    * @param expiration the message expiration
    * @param timestamp  the message timestamp
    * @param priority   the message priority (between 0 and 9 inclusive)
    * @return a ClientMessage
    */
   ClientMessage createMessage(byte type, boolean durable, long expiration, long timestamp, byte priority);

   // Query operations ----------------------------------------------

   /**
    * Queries information on a queue.
    *
    * @param queueName the name of the queue to query
    * @return a QueueQuery containing information on the given queue
    * @throws ActiveMQException if an exception occurs while querying the queue
    */
   QueueQuery queueQuery(SimpleString queueName) throws ActiveMQException;

   /**
    * Queries information on a binding.
    *
    * @param address the address of the biding to query
    * @return an AddressQuery containing information on the binding attached to the given address
    * @throws ActiveMQException if an exception occurs while querying the binding
    */
   AddressQuery addressQuery(SimpleString address) throws ActiveMQException;

   // Transaction operations ----------------------------------------

   /**
    * {@return the XAResource associated to the session}
    */
   XAResource getXAResource();

   /**
    * {@return {@code true} if the session supports XA, {@code false} else}
    */
   boolean isXA();

   /**
    * Commits the current transaction, blocking.
    *
    * @throws ActiveMQException if an exception occurs while committing the transaction
    */
   void commit() throws ActiveMQException;

   /**
    * Commits the current transaction.
    *
    * @param block if the commit will be blocking or not.
    * @throws ActiveMQException if an exception occurs while committing the transaction
    */
   void commit(boolean block) throws ActiveMQException;

   /**
    * Rolls back the current transaction.
    *
    * @throws ActiveMQException if an exception occurs while rolling back the transaction
    */
   void rollback() throws ActiveMQException;

   /**
    * Rolls back the current transaction.
    *
    * @param considerLastMessageAsDelivered the first message on deliveringMessage Buffer is considered as delivered
    * @throws ActiveMQException if an exception occurs while rolling back the transaction
    */
   void rollback(boolean considerLastMessageAsDelivered) throws ActiveMQException;

   /**
    * {@return {@code true} if the current transaction has been flagged to rollback, {@code false} else}
    */
   boolean isRollbackOnly();

   /**
    * {@return whether the session will <em>automatically</em> commit its transaction every time a message is sent by a
    * ClientProducer created by this session, {@code false} else}
    */
   boolean isAutoCommitSends();

   /**
    * {@return {@code true} if the session <em>automatically</em> commit its transaction every time a message is
    * acknowledged by a ClientConsumer created by this session, {@code false} else}
    */
   boolean isAutoCommitAcks();

   /**
    * {@return {@code true} if the session's ClientConsumer block when they acknowledge a message, {@code false} else}
    */
   boolean isBlockOnAcknowledge();

   /**
    * Sets a {@code SendAcknowledgementHandler} for this session.
    *
    * @param handler a SendAcknowledgementHandler
    * @return this ClientSession
    */
   ClientSession setSendAcknowledgementHandler(SendAcknowledgementHandler handler);

   /**
    * Attach any metadata to the session.
    */
   void addMetaData(String key, String data) throws ActiveMQException;

   /**
    * Attach any metadata to the session. Throws an exception if there's already a metadata available. You can use this
    * metadata to ensure that there is no other session with the same meta-data you are passing as an argument. This is
    * useful to simulate unique client-ids, where you may want to avoid multiple instances of your client application
    * connected.
    */
   void addUniqueMetaData(String key, String data) throws ActiveMQException;

   /**
    * {@return the {@code ClientSessionFactory} used to created this {@code ClientSession}}
    */
   ClientSessionFactory getSessionFactory();
}
