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

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;

/**
 * A {@code ClientProducer} is used to send messages to a specific address. Messages are then routed on the server to
 * any queues that are bound to the address. A {@code ClientProducer} can either be created with a specific address in
 * mind or with none. With the latter the address must be provided using the appropriate {@code send} method.
 * <p>
 * The sending semantics can change depending on what blocking semantics are set via
 * {@link ServerLocator#setBlockOnDurableSend} and {@link ServerLocator#setBlockOnNonDurableSend}. If set to
 * {@code true} then for each message type, durable and non durable respectively, any exceptions such as the address not
 * existing or security exceptions will be thrown at the time of send. Alternatively if set to {@code false} then
 * exceptions will only be logged on the server.
 * <p>
 * The send rate can also be controlled via {@link ServerLocator#setProducerMaxRate} and the
 * {@link ServerLocator#setProducerWindowSize}.
 */
public interface ClientProducer extends AutoCloseable {

   /**
    * {@return the address where messages will be sent; the address can be {@code null} if the {@code ClientProducer}
    * was created without specifying an address, e.g. by using {@link ClientSession#createProducer()}}
    */
   SimpleString getAddress();

   /**
    * Sends a message to an address. specified in {@link ClientSession#createProducer(String)} or similar methods.
    * <p>
    * This will block until confirmation that the message has reached the server has been received if
    * {@link ServerLocator#setBlockOnDurableSend(boolean)} or {@link ServerLocator#setBlockOnNonDurableSend(boolean)}
    * are set to {@code true} for the specified message type.
    *
    * @param message the message to send
    * @throws ActiveMQException if an exception occurs while sending the message
    */
   void send(Message message) throws ActiveMQException;

   /**
    * Sends a message to the specified address instead of the ClientProducer's address.
    * <p>
    * This message will be sent asynchronously.
    * <p>
    * The handler will only get called if {@link ServerLocator#setConfirmationWindowSize(int) -1}.
    *
    * @param message the message to send
    * @param handler handler to call after receiving a SEND acknowledgement from the server
    * @throws ActiveMQException if an exception occurs while sending the message
    */
   void send(Message message, SendAcknowledgementHandler handler) throws ActiveMQException;

   /**
    * Sends a message to the specified address instead of the ClientProducer's address.
    * <p>
    * This will block until confirmation that the message has reached the server has been received if
    * {@link ServerLocator#setBlockOnDurableSend(boolean)} or {@link ServerLocator#setBlockOnNonDurableSend(boolean)}
    * are set to true for the specified message type.
    *
    * @param address the address where the message will be sent
    * @param message the message to send
    * @throws ActiveMQException if an exception occurs while sending the message
    */
   void send(SimpleString address, Message message) throws ActiveMQException;

   /**
    * Sends a message to the specified address instead of the ClientProducer's address.
    * <p>
    * This message will be sent asynchronously as long as {@link ServerLocator#setConfirmationWindowSize(int)} was set.
    * <p>
    * Notice that if no confirmationWindowsize is set
    *
    * @param address the address where the message will be sent
    * @param message the message to send
    * @param handler handler to call after receiving a SEND acknowledgement from the server
    * @throws ActiveMQException if an exception occurs while sending the message
    */
   void send(SimpleString address, Message message, SendAcknowledgementHandler handler) throws ActiveMQException;

   /**
    * Sends a message to the specified address instead of the ClientProducer's address.
    * <p>
    * This will block until confirmation that the message has reached the server has been received if
    * {@link ServerLocator#setBlockOnDurableSend(boolean)} or {@link ServerLocator#setBlockOnNonDurableSend(boolean)}
    * are set to true for the specified message type.
    *
    * @param address the address where the message will be sent
    * @param message the message to send
    * @throws ActiveMQException if an exception occurs while sending the message
    */
   void send(String address, Message message) throws ActiveMQException;

   /**
    * Closes the ClientProducer. If already closed nothing is done.
    *
    * @throws ActiveMQException if an exception occurs while closing the producer
    */
   @Override
   void close() throws ActiveMQException;

   /**
    * {@return {@code true} if the producer is closed, {@code false} else}
    */
   boolean isClosed();

   /**
    * {@return {@code true} if the producer blocks when sending <em>durable</em> messages, {@code false} else}
    */
   boolean isBlockOnDurableSend();

   /**
    * {@return {@code true} if the producer blocks when sending <em>non-durable</em> messages, {@code false} else}
    */
   boolean isBlockOnNonDurableSend();

   /**
    * {@return the maximum rate at which a ClientProducer can send messages per second}
    */
   int getMaxRate();
}
