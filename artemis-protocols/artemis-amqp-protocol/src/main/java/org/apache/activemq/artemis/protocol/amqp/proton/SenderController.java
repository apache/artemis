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
package org.apache.activemq.artemis.protocol.amqp.proton;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPLargeMessage;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;

public interface SenderController {

   class RejectingOutgoingMessageWriter implements MessageWriter {

      @Override
      public void writeBytes(MessageReference reference) {
         throw new UnsupportedOperationException("Message was sent to rejecting writer in error");
      }
   }

   /**
    * Used as an initial state for message writers in controllers to ensure that a valid version is chosen when a
    * message is dispatched.
    */
   MessageWriter REJECTING_MESSAGE_WRITER = new RejectingOutgoingMessageWriter();

   /**
    * Initialize sender controller state and handle open of AMQP sender resources
    *
    * @param senderContext The sender context that is requesting controller initialization.
    * @return a server consumer that has been initialize by the controller
    * @throws Exception if an error occurs during initialization.
    */
   Consumer init(ProtonServerSenderContext senderContext) throws Exception;

   /**
    * Handle close of the sever sender AMQP resources either from remote link detach or local close usually due to
    * connection drop.
    *
    * @param remoteClose Indicates if the remote link detached the sender or local action closed it.
    * @throws Exception if an error occurs during close.
    */
   void close(boolean remoteClose) throws Exception;

   /**
    * Called when the sender is being locally closed due to some error or forced shutdown due to resource deletion etc.
    * The default implementation of this API does nothing in response to this call.
    *
    * @param error The error condition that triggered the close.
    */
   default void close(ErrorCondition error) {

   }

   /**
    * Controller selects a outgoing delivery writer that will handle the encoding and writing of the target
    * {@link Message} carried in the given {@link MessageReference}. The selection process should take into account how
    * the message pre-processing will mutate the outgoing message.
    * <p>
    * The default implementation performs no caching of writers and should be overridden in subclasses to reduce GC
    * churn, the default version is suitable for tests.
    *
    * @param sender    The server sender that will make use of the returned delivery context.
    * @param reference The message that must be sent using an outgoing context
    * @return an {@link MessageWriter} to use when sending the message in the reference
    */
   default MessageWriter selectOutgoingMessageWriter(ProtonServerSenderContext sender, MessageReference reference) {
      final MessageWriter selected;

      if (reference.getMessage() instanceof AMQPLargeMessage) {
         selected = new AMQPLargeMessageWriter(sender);
      } else {
         selected = new AMQPMessageWriter(sender);
      }

      return selected;
   }
}
