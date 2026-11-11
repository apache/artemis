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

import org.apache.activemq.artemis.api.core.Message;

/**
 * A SendAcknowledgementHandler notifies a client when a message sent asynchronously has been received by the server.
 * <p>
 * If the session is not blocking when sending durable or non-durable messages, the session can set a
 * SendAcknowledgementHandler to be notified later when the messages has been received by the server. The method
 * {@link #sendAcknowledged(Message)} will be called with the message that was sent asynchronously.
 * <p>
 * The rate of notification can be controlled through {@link ServerLocator#setConfirmationWindowSize(int)}.
 * <p>
 * Notice that this notification will only take place if {@code ConfirmationWindowSize} is set to a positive value at
 * {@link ServerLocator#setConfirmationWindowSize(int)}.
 */
public interface SendAcknowledgementHandler {

   /**
    * Notifies the client that a message sent asynchronously has been received by the server.
    *
    * @param message message sent asynchronously
    */
   void sendAcknowledged(Message message);

   default void sendFailed(Message message, Exception e) {
      /*
       * By default ignore failures to preserve compatibility with existing implementations. If the message makes it to
       * the broker and a failure occurs sendAcknowledge() will still be invoked just like it always was.
       */
   }

}
