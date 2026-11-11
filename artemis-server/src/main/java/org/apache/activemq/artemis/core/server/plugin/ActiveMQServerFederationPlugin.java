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
package org.apache.activemq.artemis.core.server.plugin;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.postoffice.impl.DivertBinding;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.federation.FederatedConsumerKey;
import org.apache.activemq.artemis.core.server.federation.FederatedQueueConsumer;
import org.apache.activemq.artemis.core.server.federation.FederationStream;

public interface ActiveMQServerFederationPlugin extends ActiveMQServerBasePlugin {

   /**
    * After a federation stream has been started
    */
   default void federationStreamStarted(final FederationStream stream) throws ActiveMQException {

   }

   /**
    * After a federation stream has been stopped
    */
   default void federationStreamStopped(final FederationStream stream) throws ActiveMQException {

   }

   /**
    * Before a federated queue consumer is created
    */
   default void beforeCreateFederatedQueueConsumer(final FederatedConsumerKey key) throws ActiveMQException {

   }

   /**
    * After a federated queue consumer is created
    */
   default void afterCreateFederatedQueueConsumer(final FederatedQueueConsumer consumer) throws ActiveMQException {

   }

   /**
    * Before a federated queue consumer is closed
    */
   default void beforeCloseFederatedQueueConsumer(final FederatedQueueConsumer consumer) throws ActiveMQException {

   }

   /**
    * After a federated queue consumer is closed
    */
   default void afterCloseFederatedQueueConsumer(final FederatedQueueConsumer consumer) throws ActiveMQException {

   }

   /**
    * Before a federated queue consumer handles a message
    */
   default void beforeFederatedQueueConsumerMessageHandled(final FederatedQueueConsumer consumer, Message message) throws ActiveMQException {

   }

   /**
    * After a federated queue consumer handles a message
    */
   default void afterFederatedQueueConsumerMessageHandled(final FederatedQueueConsumer consumer, Message message) throws ActiveMQException {

   }

   /**
    * Conditionally create a federated queue consumer for a federated address. This allows custom
    * logic to be inserted to decide when to create federated queue consumers
    *
    * @return if {@code true}, create the consumer, else if false don't create
    */
   default boolean federatedAddressConditionalCreateConsumer(final Queue queue) throws ActiveMQException {
      return true;
   }

   default boolean federatedAddressConditionalCreateDivertConsumer(DivertBinding divertBinding, QueueBinding queueBinding) throws ActiveMQException {
      return true;
   }

   /**
    * Conditionally create a federated queue consumer for a federated queue. This allows custom
    * logic to be inserted to decide when to create federated queue consumers
    *
    * @return {@code true}, create the consumer, else if false don't create
    */
   default boolean federatedQueueConditionalCreateConsumer(final ServerConsumer consumer) throws ActiveMQException {
      return true;
   }

}
