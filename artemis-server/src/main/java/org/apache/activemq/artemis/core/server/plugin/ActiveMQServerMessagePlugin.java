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
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.transaction.Transaction;

public interface ActiveMQServerMessagePlugin extends ActiveMQServerBasePlugin {

   /**
    * Before a message is sent
    *
    * @param session the session that sends the message
    */
   default void beforeSend(ServerSession session, Transaction tx, Message message, boolean direct, boolean noAutoCreateQueue) throws ActiveMQException {
      //by default call the old method for backwards compatibility
      this.beforeSend(tx, message, direct, noAutoCreateQueue);
   }

   /**
    * After a message is sent
    *
    * @param session the session that sends the message
    */
   default void afterSend(ServerSession session, Transaction tx, Message message, boolean direct, boolean noAutoCreateQueue,
                          RoutingStatus result) throws ActiveMQException {
      //by default call the old method for backwards compatibility
      this.afterSend(tx, message, direct, noAutoCreateQueue, result);
   }

   /**
    * When there was an exception sending the message
    *
    * @param e the exception that occurred when sending the message
    */
   default void onSendException(ServerSession session, Transaction tx, Message message, boolean direct, boolean noAutoCreateQueue,
                                Exception e) throws ActiveMQException {

   }

   /**
    * Before a message is sent
    *
    * @deprecated use {@link #beforeSend(ServerSession, Transaction, Message, boolean, boolean)}
    */
   @Deprecated
   default void beforeSend(Transaction tx, Message message, boolean direct, boolean noAutoCreateQueue) throws ActiveMQException {

   }

   /**
    * After a message is sent
    *
    * @deprecated use {@link #afterSend(ServerSession, Transaction, Message, boolean, boolean, RoutingStatus)}
    */
   @Deprecated
   default void afterSend(Transaction tx, Message message, boolean direct, boolean noAutoCreateQueue,
                          RoutingStatus result) throws ActiveMQException {

   }

   /**
    * Before a message is routed
    */
   default void beforeMessageRoute(Message message, RoutingContext context, boolean direct, boolean rejectDuplicates) throws ActiveMQException {

   }

   /**
    * After a message is routed
    */
   default void afterMessageRoute(Message message, RoutingContext context, boolean direct, boolean rejectDuplicates,
                                  RoutingStatus result) throws ActiveMQException {

   }

   /**
    * When there was an error routing the message
    *
    * @param e the exception that occurred during message routing
    */
   default void onMessageRouteException(Message message, RoutingContext context, boolean direct, boolean rejectDuplicates,
                                        Exception e) throws ActiveMQException {

   }

   /**
    * Before a message is delivered to a client consumer
    *
    * @param consumer the consumer the message will be delivered to
    * @param reference message reference
    */
   default boolean canAccept(ServerConsumer consumer, MessageReference reference) throws ActiveMQException {
      return true;
   }

   /**
    * Before a message is delivered to a client consumer
    *
    * @param consumer the consumer the message will be delivered to
    * @param reference message reference
    */
   default void beforeDeliver(ServerConsumer consumer, MessageReference reference) throws ActiveMQException {
      //by default call the old method for backwards compatibility
      this.beforeDeliver(reference);
   }

   /**
    * After a message is delivered to a client consumer
    *
    * @param consumer the consumer the message was delivered to
    * @param reference message reference
    */
   default void afterDeliver(ServerConsumer consumer, MessageReference reference) throws ActiveMQException {
      //by default call the old method for backwards compatibility
      this.afterDeliver(reference);
   }

   /**
    * Before a message is delivered to a client consumer
    *
    * @deprecated use throws ActiveMQException {@link #beforeDeliver(ServerConsumer, MessageReference)}
    */
   @Deprecated
   default void beforeDeliver(MessageReference reference) throws ActiveMQException {

   }

   /**
    * After a message is delivered to a client consumer
    *
    * @deprecated use {@link #afterDeliver(ServerConsumer, MessageReference)}
    */
   @Deprecated
   default void afterDeliver(MessageReference reference) throws ActiveMQException {

   }

   /**
    * A message has been expired
    *
    * @param message              The expired message
    * @param messageExpiryAddress The message expiry address if exists
    * @deprecated use {@link #messageExpired(MessageReference, SimpleString, ServerConsumer)}
    */
   @Deprecated
   default void messageExpired(MessageReference message, SimpleString messageExpiryAddress) throws ActiveMQException {

   }

   /**
    * A message has been expired
    *
    * @param message              The expired message
    * @param messageExpiryAddress The message expiry address if exists
    * @param consumer             the Consumer that acknowledged the message - this field is optional and can be null
    */
   default void messageExpired(MessageReference message, SimpleString messageExpiryAddress, ServerConsumer consumer) throws ActiveMQException {
      messageExpired(message, messageExpiryAddress);
   }

   /**
    * A message has been acknowledged
    *
    * @param ref    The acked message
    * @param reason The ack reason
    * @deprecated use {@link #messageAcknowledged(MessageReference, AckReason, ServerConsumer)}
    */
   @Deprecated
   default void messageAcknowledged(MessageReference ref, AckReason reason) throws ActiveMQException {

   }

   /**
    * A message has been acknowledged
    *
    * @param ref      The acked message
    * @param reason   The ack reason
    * @param consumer the Consumer that acknowledged the message - this field is optional and can be null
    */
   @Deprecated
   default void messageAcknowledged(MessageReference ref, AckReason reason, ServerConsumer consumer) throws ActiveMQException {
      //by default call the old method for backwards compatibility
      this.messageAcknowledged(ref, reason);
   }

   /**
    * A message has been acknowledged
    *
    * @param tx       The transaction associated with the ack
    * @param ref      The acked message
    * @param reason   The ack reason
    * @param consumer the Consumer that acknowledged the message - this field is optional and can be null
    */
   default void messageAcknowledged(Transaction tx, MessageReference ref, AckReason reason, ServerConsumer consumer) throws ActiveMQException {
      //by default call the old method for backwards compatibility
      this.messageAcknowledged(ref, reason, consumer);
   }

   /**
    * A message has been moved
    *
    * @param tx          The transaction associated with the move
    * @param ref         The ref of the message moved
    * @param reason      The move reason
    * @param destAddress the destination address for the move operation
    * @param destQueueID the destination queueID for the move operation - this field is optional and can be null
    * @param consumer    the consumer that moved the message - this field is optional and can be null
    * @param newMessage  the new message created by the move operation
    * @param result      routing status of the move operation
    */
   default void messageMoved(final Transaction tx,
                             final MessageReference ref,
                             final AckReason reason,
                             final SimpleString destAddress,
                             final Long destQueueID,
                             final ServerConsumer consumer,
                             final Message newMessage,
                             final RoutingStatus result) throws ActiveMQException {

   }
}
