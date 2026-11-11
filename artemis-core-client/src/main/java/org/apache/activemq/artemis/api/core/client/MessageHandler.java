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

/**
 * A MessageHandler is used to receive message <em>asynchronously</em>.
 * <p>
 * To receive messages asynchronously, a MessageHandler is set on a ClientConsumer. Every time the consumer will receive
 * a message, it will call the handler's {@code onMessage()} method.
 *
 * @see ClientConsumer#setMessageHandler(MessageHandler)
 */
public interface MessageHandler {

   /**
    * Notifies the MessageHandler that a message has been received.
    *
    * @param message a message
    */
   void onMessage(ClientMessage message);

   /**
    * Notifies the MessageHandler that an expired message has been received.
    *
    * @param message a message
    */
   default void onMessageExpired(ClientMessage message) {
      // Do nothing by default
   }
}
