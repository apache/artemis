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

import javax.jms.BytesMessage;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;

/**
 * Deprecated in favor of EmbeddedActiveMQDelegate. Since Artemis 2.0 all JMS specific broker management classes,
 * interfaces, and methods have been deprecated in favor of their more general counter-parts.
 *
 * @param <I> implementing type
 * @see EmbeddedActiveMQDelegate
 * @deprecated use {@link EmbeddedActiveMQDelegate} instead
 */
@Deprecated
public interface EmbeddedJMSOperations<I> {

   /**
    * The acceptor used
    */
   I addAcceptor(String name, String uri) throws Exception;

   /**
    * Start the embedded EmbeddedJMSResource.
    * <p>
    * The server will normally be started by JUnit using the before() method. This method allows the server to be
    * started manually to support advanced testing scenarios.
    */
   void start();

   /**
    * Stop the embedded ActiveMQ broker, blocking until the broker has stopped.
    * <p>
    * The broker will normally be stopped by JUnit using the after() method. This method allows the broker to be stopped
    * manually to support advanced testing scenarios.
    */
   void stop();

   /**
    * Get the EmbeddedJMS server.
    * <p>
    * This may be required for advanced configuration of the EmbeddedJMS server.
    */
   EmbeddedJMS getJmsServer();

   /**
    * Get the name of the EmbeddedJMS server
    *
    * @return name of the server
    */
   String getServerName();

   /**
    * Get the VM URL for the embedded EmbeddedActiveMQ server
    *
    * @return the VM URL for the embedded server
    */
   String getVmURL();

   /**
    * Get the Queue corresponding to a JMS Destination.
    * <p>
    * The full name of the JMS destination including the prefix should be provided - i.e. queue://myQueue or
    * topic://myTopic. If the destination type prefix is not included in the destination name, a prefix of "queue://" is
    * assumed.
    *
    * @param destinationName the full name of the JMS Destination
    * @return the number of messages in the JMS Destination
    */
   Queue getDestinationQueue(String destinationName);

   /**
    * Get the Queues corresponding to a JMS Topic.
    * <p>
    * The full name of the JMS Topic including the prefix should be provided - i.e. topic://myTopic. If the destination
    * type prefix is not included in the destination name, a prefix of "topic://" is assumed.
    *
    * @param topicName the full name of the JMS Destination
    * @return the number of messages in the JMS Destination
    */
   List<Queue> getTopicQueues(String topicName);

   /**
    * Get the number of messages in a specific JMS Destination.
    * <p>
    * The full name of the JMS destination including the prefix should be provided - i.e. queue://myQueue or
    * topic://myTopic. If the destination type prefix is not included in the destination name, a prefix of "queue://" is
    * assumed. NOTE: For JMS Topics, this returned count will be the total number of messages for all subscribers. For
    * example, if there are two subscribers on the topic and a single message is published, the returned count will be
    * two (one message for each subscriber).
    *
    * @param destinationName the full name of the JMS Destination
    * @return the number of messages in the JMS Destination
    */
   long getMessageCount(String destinationName);

   BytesMessage createBytesMessage();

   TextMessage createTextMessage();

   MapMessage createMapMessage();

   ObjectMessage createObjectMessage();

   StreamMessage createStreamMessage();

   BytesMessage createMessage(byte[] body);

   TextMessage createMessage(String body);

   MapMessage createMessage(Map<String, Object> body);

   ObjectMessage createMessage(Serializable body);

   BytesMessage createMessage(byte[] body, Map<String, Object> properties);

   TextMessage createMessage(String body, Map<String, Object> properties);

   MapMessage createMessage(Map<String, Object> body, Map<String, Object> properties);

   ObjectMessage createMessage(Serializable body, Map<String, Object> properties);

   void pushMessage(String destinationName, Message message);

   BytesMessage pushMessage(String destinationName, byte[] body);

   TextMessage pushMessage(String destinationName, String body);

   MapMessage pushMessage(String destinationName, Map<String, Object> body);

   ObjectMessage pushMessage(String destinationName, Serializable body);

   BytesMessage pushMessageWithProperties(String destinationName, byte[] body, Map<String, Object> properties);

   TextMessage pushMessageWithProperties(String destinationName, String body, Map<String, Object> properties);

   MapMessage pushMessageWithProperties(String destinationName, Map<String, Object> body,
                                        Map<String, Object> properties);

   ObjectMessage pushMessageWithProperties(String destinationName, Serializable body, Map<String, Object> properties);

   Message peekMessage(String destinationName);

   BytesMessage peekBytesMessage(String destinationName);

   TextMessage peekTextMessage(String destinationName);

   MapMessage peekMapMessage(String destinationName);

   ObjectMessage peekObjectMessage(String destinationName);

   StreamMessage peekStreamMessage(String destinationName);

}