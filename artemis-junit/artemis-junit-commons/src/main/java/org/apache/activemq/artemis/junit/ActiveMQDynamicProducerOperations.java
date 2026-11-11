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

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;

public interface ActiveMQDynamicProducerOperations {

   /**
    * Send a ClientMessage to the default address on the server
    *
    * @param message the message to send
    */
   void sendMessage(ClientMessage message);

   /**
    * Send a ClientMessage to the specified address on the server
    *
    * @param targetAddress the target address
    * @param message       the message to send
    */
   void sendMessage(SimpleString targetAddress, ClientMessage message);

   /**
    * Create a new ClientMessage with the specified body and send to the specified address on the server
    *
    * @param targetAddress the target address
    * @param body          the body for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessage(SimpleString targetAddress, byte[] body);

   /**
    * Create a new ClientMessage with the specified body and send to the server
    *
    * @param targetAddress the target address
    * @param body          the body for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessage(SimpleString targetAddress, String body);

   /**
    * Create a new ClientMessage with the specified properties and send to the server
    *
    * @param targetAddress the target address
    * @param properties    the properties for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessage(SimpleString targetAddress, Map<String, Object> properties);

   /**
    * Create a new ClientMessage with the specified body and and properties and send to the server
    *
    * @param targetAddress the target address
    * @param properties    the properties for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessage(SimpleString targetAddress, byte[] body, Map<String, Object> properties);

   /**
    * Create a new ClientMessage with the specified body and and properties and send to the server
    *
    * @param targetAddress the target address
    * @param properties    the properties for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessage(SimpleString targetAddress, String body, Map<String, Object> properties);

}