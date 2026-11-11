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

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ServerLocator;

/**
 * Base class to embed an Apache Artemis ClientProducer bound to a specific address into a test.
 */
public class ActiveMQProducerDelegate extends AbstractActiveMQClientDelegate implements ActiveMQProducerOperations {

   boolean useDurableMessage = true;
   SimpleString address = null;
   ClientProducer producer;

   protected ActiveMQProducerDelegate(String url, String username, String password) {
      super(url, username, password);
   }

   protected ActiveMQProducerDelegate(String url) {
      super(url);
   }

   protected ActiveMQProducerDelegate(ServerLocator serverLocator, String username, String password) {
      super(serverLocator, username, password);
   }

   protected ActiveMQProducerDelegate(ServerLocator serverLocator) {
      super(serverLocator);
   }

   protected ActiveMQProducerDelegate(String url, String address, String username, String password) {
      this(url, SimpleString.of(address), username, password);
   }

   protected ActiveMQProducerDelegate(String url, String address) {
      this(url, address, null, null);
   }

   protected ActiveMQProducerDelegate(String url, SimpleString address, String username, String password) {
      super(url, username, password);
      if (address == null) {
         throw new IllegalArgumentException(String.format("%s construction error - address cannot be null",
                                                          this.getClass().getSimpleName()));
      }
      this.address = address;
   }

   protected ActiveMQProducerDelegate(String url, SimpleString address) {
      this(url, address, null, null);
   }

   protected ActiveMQProducerDelegate(ServerLocator serverLocator, String address, String username, String password) {
      this(serverLocator, SimpleString.of(address), username, password);
   }

   protected ActiveMQProducerDelegate(ServerLocator serverLocator, String address) {
      this(serverLocator, SimpleString.of(address));
   }

   protected ActiveMQProducerDelegate(ServerLocator serverLocator, SimpleString address, String username,
                                      String password) {
      super(serverLocator, username, password);
      if (address == null) {
         throw new IllegalArgumentException(String.format("%s construction error - address cannot be null",
                                                          this.getClass().getSimpleName()));
      }
      this.address = address;
   }

   protected ActiveMQProducerDelegate(ServerLocator serverLocator, SimpleString address) {
      this(serverLocator, address, null, null);
   }

   @Override
   public boolean isUseDurableMessage() {
      return useDurableMessage;
   }

   @Override
   public void setUseDurableMessage(boolean useDurableMessage) {
      this.useDurableMessage = useDurableMessage;
   }

   @Override
   protected void createClient() {
      try {
         if (!session.addressQuery(address).isExists() && autoCreateQueue) {
            log.warn("{}: queue does not exist - creating queue: address = {}, name = {}",
                     this.getClass().getSimpleName(), address.toString(), address.toString());
            session.createQueue(QueueConfiguration.of(address));
         }
         producer = session.createProducer(address);
      } catch (ActiveMQException amqEx) {
         throw new ActiveMQClientResourceException(String.format("Error creating producer for address %s",
                                                                 address.toString()),
                                                   amqEx);
      }
   }

   @Override
   protected void stopClient() {
      if (producer != null) {
         try {
            producer.close();
         } catch (ActiveMQException amqEx) {
            log.warn("ActiveMQException encountered closing InternalClient ClientProducer - ignoring", amqEx);
         } finally {
            producer = null;
         }
      }
   }

   @Override
   public ClientMessage createMessage() {
      if (session == null) {
         throw new IllegalStateException("ClientSession is null");
      }
      return session.createMessage(isUseDurableMessage());
   }

   @Override
   public ClientMessage createMessage(byte[] body) {
      ClientMessage message = createMessage();

      if (body != null) {
         message.writeBodyBufferBytes(body);
      }

      return message;
   }

   @Override
   public ClientMessage createMessage(String body) {
      ClientMessage message = createMessage();

      if (body != null) {
         message.writeBodyBufferString(body);
      }

      return message;
   }

   @Override
   public ClientMessage createMessage(Map<String, Object> properties) {
      ClientMessage message = createMessage();

      addMessageProperties(message, properties);

      return message;
   }

   @Override
   public ClientMessage createMessage(byte[] body, Map<String, Object> properties) {
      ClientMessage message = createMessage(body);

      addMessageProperties(message, properties);

      return message;
   }

   @Override
   public ClientMessage createMessage(String body, Map<String, Object> properties) {
      ClientMessage message = createMessage(body);

      addMessageProperties(message, properties);

      return message;
   }

   @Override
   public void sendMessage(ClientMessage message) {
      try {
         producer.send(message);
      } catch (ActiveMQException amqEx) {
         throw new ActiveMQClientResourceException(String.format("Failed to send message to %s",
                                                                 producer.getAddress().toString()),
                                                   amqEx);
      }
   }

   @Override
   public ClientMessage sendMessage(byte[] body) {
      ClientMessage message = createMessage(body);
      sendMessage(message);
      return message;
   }

   @Override
   public ClientMessage sendMessage(String body) {
      ClientMessage message = createMessage(body);
      sendMessage(message);
      return message;
   }

   @Override
   public ClientMessage sendMessage(Map<String, Object> properties) {
      ClientMessage message = createMessage(properties);
      sendMessage(message);
      return message;
   }

   @Override
   public ClientMessage sendMessage(byte[] body, Map<String, Object> properties) {
      ClientMessage message = createMessage(body, properties);
      sendMessage(message);
      return message;
   }

   @Override
   public ClientMessage sendMessage(String body, Map<String, Object> properties) {
      ClientMessage message = createMessage(body, properties);
      sendMessage(message);
      return message;
   }
}
