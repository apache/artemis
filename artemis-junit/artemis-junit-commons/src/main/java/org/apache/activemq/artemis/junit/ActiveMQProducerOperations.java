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
package org.apache.activemq.artemis.junit;

import java.util.Map;

import org.apache.activemq.artemis.api.core.client.ClientMessage;

public interface ActiveMQProducerOperations {

   boolean isUseDurableMessage();

   /**
    * Disables/Enables creating durable messages. By default, durable messages are created.
    *
    * @param useDurableMessage if {@code true}, durable messages will be created
    */
   void setUseDurableMessage(boolean useDurableMessage);

   /**
    * Create a ClientMessage.
    * <p>
    * If useDurableMessage is false, a non-durable message is created. Otherwise, a durable message is created.
    *
    * @return a new ClientMessage
    */
   ClientMessage createMessage();

   /**
    * Create a ClientMessage with the specified body.
    * <p>
    * If useDurableMessage is false, a non-durable message is created. Otherwise, a durable message is created.
    *
    * @param body the body for the new message
    * @return a new ClientMessage with the specified body
    */
   ClientMessage createMessage(byte[] body);

   /**
    * Create a ClientMessage with the specified body.
    * <p>
    * If useDurableMessage is false, a non-durable message is created. Otherwise, a durable message is created.
    *
    * @param body the body for the new message
    * @return a new ClientMessage with the specified body
    */
   ClientMessage createMessage(String body);

   /**
    * Create a ClientMessage with the specified message properties.
    * <p>
    * If useDurableMessage is false, a non-durable message is created. Otherwise, a durable message is created.
    *
    * @param properties message properties for the new message
    * @return a new ClientMessage with the specified message properties
    */
   ClientMessage createMessage(Map<String, Object> properties);

   /**
    * Create a ClientMessage with the specified body and message properties.
    * <p>
    * If useDurableMessage is false, a non-durable message is created. Otherwise, a durable message is created.
    *
    * @param body       the body for the new message
    * @param properties message properties for the new message
    * @return a new ClientMessage with the specified body and message properties
    */
   ClientMessage createMessage(byte[] body, Map<String, Object> properties);

   /**
    * Create a ClientMessage with the specified body and message properties.
    * <p>
    * If useDurableMessage is false, a non-durable message is created. Otherwise, a durable message is created.
    *
    * @param body       the body for the new message
    * @param properties message properties for the new message
    * @return a new ClientMessage with the specified body and message properties
    */
   ClientMessage createMessage(String body, Map<String, Object> properties);

   /**
    * Send a ClientMessage to the server.
    *
    * @param message the message to send
    */
   void sendMessage(ClientMessage message);

   /**
    * Create a new ClientMessage with the specified body and send to the server.
    *
    * @param body the body for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessage(byte[] body);

   /**
    * Create a new ClientMessage with the specified body and send to the server.
    *
    * @param body the body for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessage(String body);

   /**
    * Create a new ClientMessage with the specified properties and send to the server
    *
    * @param properties the properties for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessage(Map<String, Object> properties);

   /**
    * Create a new ClientMessage with the specified body and and properties and send to the server
    *
    * @param properties the properties for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessage(byte[] body, Map<String, Object> properties);

   /**
    * Create a new ClientMessage with the specified body and and properties and send to the server
    *
    * @param properties the properties for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessage(String body, Map<String, Object> properties);

}