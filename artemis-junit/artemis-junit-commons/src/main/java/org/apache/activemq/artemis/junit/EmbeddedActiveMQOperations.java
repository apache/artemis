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

import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;

public interface EmbeddedActiveMQOperations {

   /**
    * Start the embedded Apache Artemis server. The server will normally be started by JUnit using the before()
    * method. This method allows the server to be started manually to support advanced testing scenarios.
    */
   void start();

   /**
    * Stop the embedded Apache Artemis server The server will normally be stopped by JUnit using the after() method.
    * This method allows the server to be stopped manually to support advanced testing scenarios.
    */
   void stop();

   boolean isUseDurableMessage();

   /**
    * Disables/Enables creating durable messages. By default, durable messages are created
    *
    * @param useDurableMessage if true, durable messages will be created
    */
   void setUseDurableMessage(boolean useDurableMessage);

   boolean isUseDurableQueue();

   /**
    * Disables/Enables creating durable queues. By default, durable queues are created
    *
    * @param useDurableQueue if true, durable messages will be created
    */
   void setUseDurableQueue(boolean useDurableQueue);

   long getDefaultReceiveTimeout();

   /**
    * Sets the default timeout in milliseconds used when receiving messages. Defaults to 50 milliseconds
    *
    * @param defaultReceiveTimeout received timeout in milliseconds
    */
   void setDefaultReceiveTimeout(long defaultReceiveTimeout);

   /**
    * Get the EmbeddedActiveMQ server. This may be required for advanced configuration of the EmbeddedActiveMQ server.
    *
    * @return the embedded ActiveMQ broker
    */
   EmbeddedActiveMQ getServer();

   /**
    * Get the name of the EmbeddedActiveMQ server
    *
    * @return name of the embedded server
    */
   String getServerName();

   /**
    * Get the VM URL for the embedded EmbeddedActiveMQ server
    *
    * @return the VM URL for the embedded server
    */
   String getVmURL();

   /**
    * Get the number of messages in a specific queue.
    *
    * @param queueName the name of the queue
    * @return the number of messages in the queue; -1 if queue is not found
    */
   long getMessageCount(String queueName);

   /**
    * Get the number of messages in a specific queue.
    *
    * @param queueName the name of the queue
    * @return the number of messages in the queue; -1 if queue is not found
    */
   long getMessageCount(SimpleString queueName);

   Queue locateQueue(String queueName);

   Queue locateQueue(SimpleString queueName);

   List<Queue> getBoundQueues(String address);

   List<Queue> getBoundQueues(SimpleString address);

   Queue createQueue(String name);

   Queue createQueue(String address, String name);

   Queue createQueue(SimpleString address, SimpleString name);

   void createSharedQueue(String name, String user);

   void createSharedQueue(String address, String name, String user);

   void createSharedQueue(SimpleString address, SimpleString name, SimpleString user);

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
   ClientMessage createMessageWithProperties(Map<String, Object> properties);

   /**
    * Create a ClientMessage with the specified body and message properties.
    * <p>
    * If useDurableMessage is false, a non-durable message is created. Otherwise, a durable message is created.
    *
    * @param body       the body for the new message
    * @param properties message properties for the new message
    * @return a new ClientMessage with the specified body and message properties
    */
   ClientMessage createMessageWithProperties(byte[] body, Map<String, Object> properties);

   /**
    * Create a ClientMessage with the specified body and message properties.
    * <p>
    * If useDurableMessage is false, a non-durable message is created. Otherwise, a durable message is created.
    *
    * @param body       the body for the new message
    * @param properties message properties for the new message
    * @return a new ClientMessage with the specified body and message properties
    */
   ClientMessage createMessageWithProperties(String body, Map<String, Object> properties);

   /**
    * Send a message to an address
    *
    * @param address the target queueName for the message
    * @param message the message to send
    */
   void sendMessage(String address, ClientMessage message);

   /**
    * Create a new message with the specified body, and send the message to an address
    *
    * @param address the target queueName for the message
    * @param body    the body for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessage(String address, byte[] body);

   /**
    * Create a new message with the specified body, and send the message to an address
    *
    * @param address the target queueName for the message
    * @param body    the body for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessage(String address, String body);

   /**
    * Create a new message with the specified properties, and send the message to an address
    *
    * @param address    the target queueName for the message
    * @param properties message properties for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessageWithProperties(String address, Map<String, Object> properties);

   /**
    * Create a new message with the specified body and properties, and send the message to an address
    *
    * @param address    the target queueName for the message
    * @param body       the body for the new message
    * @param properties message properties for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessageWithProperties(String address, byte[] body, Map<String, Object> properties);

   /**
    * Create a new message with the specified body and properties, and send the message to an address
    *
    * @param address    the target queueName for the message
    * @param body       the body for the new message
    * @param properties message properties for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessageWithProperties(String address, String body, Map<String, Object> properties);

   /**
    * Send a message to an queueName
    *
    * @param address the target queueName for the message
    * @param message the message to send
    */
   void sendMessage(SimpleString address, ClientMessage message);

   /**
    * Create a new message with the specified body, and send the message to an queueName
    *
    * @param address the target queueName for the message
    * @param body    the body for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessage(SimpleString address, byte[] body);

   /**
    * Create a new message with the specified body, and send the message to an queueName
    *
    * @param address the target queueName for the message
    * @param body    the body for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessage(SimpleString address, String body);

   /**
    * Create a new message with the specified properties, and send the message to an queueName
    *
    * @param address    the target queueName for the message
    * @param properties message properties for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessageWithProperties(SimpleString address, Map<String, Object> properties);

   /**
    * Create a new message with the specified body and properties, and send the message to an queueName
    *
    * @param address    the target queueName for the message
    * @param body       the body for the new message
    * @param properties message properties for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessageWithProperties(SimpleString address, byte[] body, Map<String, Object> properties);

   /**
    * Create a new message with the specified body and properties, and send the message to an queueName
    *
    * @param address    the target queueName for the message
    * @param body       the body for the new message
    * @param properties message properties for the new message
    * @return the message that was sent
    */
   ClientMessage sendMessageWithProperties(SimpleString address, String body, Map<String, Object> properties);

   /**
    * Receive a message from the specified queue using the default receive timeout
    *
    * @param queueName name of the source queue
    * @return the received ClientMessage, null if the receive timed-out
    */
   ClientMessage receiveMessage(String queueName);

   /**
    * Receive a message from the specified queue using the specified receive timeout
    *
    * @param queueName name of the source queue
    * @param timeout   receive timeout in milliseconds
    * @return the received ClientMessage, null if the receive timed-out
    */
   ClientMessage receiveMessage(String queueName, long timeout);

   /**
    * Receive a message from the specified queue using the default receive timeout
    *
    * @param queueName name of the source queue
    * @return the received ClientMessage, null if the receive timed-out
    */
   ClientMessage receiveMessage(SimpleString queueName);

   /**
    * Receive a message from the specified queue using the specified receive timeout
    *
    * @param queueName name of the source queue
    * @param timeout   receive timeout in milliseconds
    * @return the received ClientMessage, null if the receive timed-out
    */
   ClientMessage receiveMessage(SimpleString queueName, long timeout);

   /**
    * Browse a message (receive but do not consume) from the specified queue using the default receive timeout
    *
    * @param queueName name of the source queue
    * @return the received ClientMessage, null if the receive timed-out
    */
   ClientMessage browseMessage(String queueName);

   /**
    * Browse a message (receive but do not consume) a message from the specified queue using the specified receive
    * timeout
    *
    * @param queueName name of the source queue
    * @param timeout   receive timeout in milliseconds
    * @return the received ClientMessage, null if the receive timed-out
    */
   ClientMessage browseMessage(String queueName, long timeout);

   /**
    * Browse a message (receive but do not consume) from the specified queue using the default receive timeout
    *
    * @param queueName name of the source queue
    * @return the received ClientMessage, null if the receive timed-out
    */
   ClientMessage browseMessage(SimpleString queueName);

   /**
    * Browse a message (receive but do not consume) a message from the specified queue using the specified receive
    * timeout
    *
    * @param queueName name of the source queue
    * @param timeout   receive timeout in milliseconds
    * @return the received ClientMessage, null if the receive timed-out
    */
   ClientMessage browseMessage(SimpleString queueName, long timeout);

}