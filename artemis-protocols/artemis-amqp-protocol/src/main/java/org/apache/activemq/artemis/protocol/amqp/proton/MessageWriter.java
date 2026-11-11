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

package org.apache.activemq.artemis.protocol.amqp.proton;

import java.util.function.Consumer;

import org.apache.activemq.artemis.core.server.MessageReference;

/**
 * Message writer for outgoing message from and AMQP sender context which will handle the encode and write of message
 * payload into am AMQP sender.
 */
public interface MessageWriter extends Consumer<MessageReference> {

   /**
    * Entry point for asynchronous delivery mechanics which is equivalent to calling the
    * {@link #writeBytes(MessageReference)} method.
    *
    * @param messageReference The original message reference that triggered the delivery.
    * @see #writeBytes(MessageReference)
    */
   @Override
   default void accept(MessageReference messageReference) {
      writeBytes(messageReference);
   }

   /**
    * This should return {@code true} when a delivery is still in progress as a hint to the sender that new messages
    * can't be accepted yet. The handler can be paused during delivery of large payload data due to IO or session back
    * pressure. The context is responsible for scheduling itself for resumption when it finds that it must halt delivery
    * work.
    * <p>
    * This could be called from outside the connection thread so the state should be thread safe however the sender
    * should take care to restart deliveries in a safe way taking into account that this value might not get seen by
    * other threads in its non-busy state when the delivery completes.
    *
    * @return {@code true} if the handler is still working on delivering a message
    */
   default boolean isWriting() {
      return false;
   }

   /**
    * Begin delivery of a message providing the original message reference instance. The writer should be linked to a
    * parent sender or sender controller which it will use for obtaining services needed to send and complete sending
    * operations. This must be called from the connection thread.
    * <p>
    * Once delivery processing completes (successful or not) the handler must inform the server sender of the outcome so
    * that further deliveries can be sent or error processing can commence.
    *
    * @param messageReference The original message reference that triggered the delivery.
    */
   void writeBytes(MessageReference messageReference);

   /**
    * Mark the writer as done and release any resources that it might be holding, this call should trigger the busy
    * method to return false for any handler that has a busy state. It is expected that the sender will close each
    * handler after it reports that writing the message has completed. This must be called from the connection thread.
    */
   default void close() {
      // By default stateless writers have no reaction to closed events.
   }

   /**
    * Opens the handler and ensures the handler state is in its initial values to prepare for a new message write. This
    * is only applicable to handlers that have state data but should be called on every handler by the sender context as
    * it doesn't know which instances need opened.
    */
   default MessageWriter open(MessageReference reference) {
      // Default for stateless handlers is to do nothing here.
      return this;
   }
}
