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
package org.apache.activemq.artemis.protocol.amqp.connect.federation;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationBrokerPlugin;
import org.apache.activemq.artemis.core.server.Divert;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.protocol.amqp.federation.Federation;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumer;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumerInfo;

/**
 * Broker plugin which allows users to intercept federation related events when AMQP federation is configured on the
 * broker.
 */
public interface ActiveMQServerAMQPFederationPlugin extends AMQPFederationBrokerPlugin {

   /**
    * After a federation instance has been started
    *
    * @param federation The {@link Federation} instance that is being started.
    * @throws ActiveMQException if an error occurs during the call.
    */
   default void federationStarted(final Federation federation) throws ActiveMQException {

   }

   /**
    * After a federation instance has been stopped
    *
    * @param federation The {@link Federation} instance that is being stopped.
    * @throws ActiveMQException if an error occurs during the call.
    */
   default void federationStopped(final Federation federation) throws ActiveMQException {

   }

   /**
    * Before a consumer for a federated resource is created
    *
    * @param consumerInfo The information that will be used when creating the federation consumer.
    * @throws ActiveMQException if an error occurs during the call.
    */
   default void beforeCreateFederationConsumer(final FederationConsumerInfo consumerInfo) throws ActiveMQException {

   }

   /**
    * After a consumer for a federated resource is created
    *
    * @param consumer The consumer that was created after a matching federated resource is detected.
    * @throws ActiveMQException if an error occurs during the call.
    */
   default void afterCreateFederationConsumer(final FederationConsumer consumer) throws ActiveMQException {

   }

   /**
    * Before a consumer for a federated resource is closed
    *
    * @param consumer The federation consumer that is going to be closed.
    * @throws ActiveMQException if an error occurs during the call.
    */
   default void beforeCloseFederationConsumer(final FederationConsumer consumer) throws ActiveMQException {

   }

   /**
    * After a consumer for a federated resource is closed
    *
    * @param consumer The federation consumer that has been closed.
    * @throws ActiveMQException if an error occurs during the call.
    */
   default void afterCloseFederationConsumer(final FederationConsumer consumer) throws ActiveMQException {

   }

   /**
    * Before a federation consumer handles a message
    *
    * @param consumer The {@link Federation} consumer that is handling a new incoming message.
    * @param message  The {@link Message} that is being handled
    * @throws ActiveMQException if an error occurs during the call.
    */
   default void beforeFederationConsumerMessageHandled(final FederationConsumer consumer,
                                                       Message message) throws ActiveMQException {

   }

   /**
    * After a federation consumer handles a message
    *
    * @param consumer The {@link Federation} consumer that is handling a new incoming message.
    * @param message  The {@link Message} that is being handled
    * @throws ActiveMQException if an error occurs during the call.
    */
   default void afterFederationConsumerMessageHandled(final FederationConsumer consumer, Message message) throws ActiveMQException {

   }

   /**
    * Conditionally create a federation consumer for an address that matches the configuration of this server
    * federation. This allows custom logic to be inserted to decide when to create federation consumers
    *
    * @param address The address that matched the federation configuration
    * @return if {@code true}, create the consumer, else if false don't create
    * @throws ActiveMQException if an error occurs during the call.
    */
   default boolean shouldCreateFederationConsumerForAddress(final AddressInfo address) throws ActiveMQException {
      return true;
   }

   /**
    * Conditionally create a federation consumer for an address that matches the configuration of this server
    * federation. This allows custom logic to be inserted to decide when to create federation consumers
    *
    * @param queue The queue that matched the federation configuration
    * @return if {@code true}, create the consumer, else if false don't create
    * @throws ActiveMQException if an error occurs during the call.
    */
   default boolean shouldCreateFederationConsumerForQueue(final Queue queue) throws ActiveMQException {
      return true;
   }

   /**
    * Conditionally create a federation consumer for an divert binding that matches the configuration of this server
    * federation. This allows custom logic to be inserted to decide when to create federation consumers
    *
    * @param divert The {@link Divert} that matched the federation configuration
    * @param queue  The {@link Queue} that was attached for a divert forwarding address.
    * @return if {@code true}, create the consumer, else if false don't create
    * @throws ActiveMQException if an error occurs during the call.
    */
   default boolean shouldCreateFederationConsumerForDivert(Divert divert, Queue queue) throws ActiveMQException {
      return true;
   }
}
