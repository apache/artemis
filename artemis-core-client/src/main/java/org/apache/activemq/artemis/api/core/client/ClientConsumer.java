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
import org.apache.activemq.artemis.spi.core.remoting.ConsumerContext;

/**
 * A ClientConsumer receives messages from Apache Artemis queues.
 * <p>
 * Messages can be consumed synchronously by using the {@code receive()} methods which will block until a message is
 * received (or a timeout expires) or asynchronously by setting a {@link MessageHandler}.
 * <p>
 * These 2 types of consumption are exclusive: a ClientConsumer with a MessageHandler set will throw ActiveMQException
 * if its {@code receive()} methods are called.
 *
 * @see ClientSession#createConsumer(String)
 */
public interface ClientConsumer extends AutoCloseable {

   /**
    * The server's ID associated with this consumer. Apache Artemis implements this as a long but this could be
    * protocol dependent.
    */
   ConsumerContext getConsumerContext();

   /**
    * Receives a message from a queue.
    * <p>
    * This call will block indefinitely until a message is received.
    * <p>
    * Calling this method on a closed consumer will throw an ActiveMQException.
    *
    * @return a ClientMessage
    * @throws ActiveMQException if an exception occurs while waiting to receive a message
    */
   ClientMessage receive() throws ActiveMQException;

   /**
    * Receives a message from a queue.
    * <p>
    * This call will block until a message is received or the given timeout expires.
    * <p>
    * Calling this method on a closed consumer will throw an ActiveMQException.
    *
    * @param timeout time (in milliseconds) to wait to receive a message
    * @return a message or {@code null} if the time out expired
    * @throws ActiveMQException if an exception occurs while waiting to receive a message
    */
   ClientMessage receive(long timeout) throws ActiveMQException;

   /**
    * Receives a message from a queue. This call will force a network trip to Apache Artemis server to ensure that
    * there are no messages in the queue which can be delivered to this consumer.
    * <p>
    * This call will never wait indefinitely for a message, it will return {@code null} if no messages are available for
    * this consumer.
    * <p>
    * Note however that there is a performance cost as an additional network trip to the server may required to check
    * the queue status.
    * <p>
    * Calling this method on a closed consumer will throw an ActiveMQException.
    *
    * @return a message or {@code null} if there are no messages in the queue for this consumer
    * @throws ActiveMQException if an exception occurs while waiting to receive a message
    */
   ClientMessage receiveImmediate() throws ActiveMQException;

   /**
    * Calling this method on a closed consumer will throw an ActiveMQException.
    *
    * @return the MessageHandler associated to this consumer or {@code null}
    * @throws ActiveMQException if an exception occurs while getting the MessageHandler
    */
   MessageHandler getMessageHandler() throws ActiveMQException;

   /**
    * Sets the MessageHandler for this consumer to consume messages asynchronously.
    * <p>
    * Note that setting a handler dedicates the parent session, and its child producers and consumers, to the
    * session-wide handler delivery thread of control.
    * <p>
    * Calling this method on a closed consumer will throw a ActiveMQException.
    *
    * @param handler a MessageHandler
    * @throws ActiveMQException if an exception occurs while setting the MessageHandler
    */
   ClientConsumer setMessageHandler(MessageHandler handler) throws ActiveMQException;

   /**
    * Closes the consumer.
    * <p>
    * Once this consumer is closed, it can not receive messages, whether synchronously or asynchronously.
    */
   @Override
   void close() throws ActiveMQException;

   /**
    * {@return {@code true} if this consumer is closed, {@code false} else}
    */
   boolean isClosed();

   /**
    * {@return the last exception thrown by a call to this consumer's MessageHandler or {@code null}}
    */
   Exception getLastException();
}
