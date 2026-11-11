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
import org.apache.activemq.artemis.api.core.client.ServerLocator;

/**
 * Base class to embed an dynamic (i.e. unbound) Apache Artemis ClientProducer into a test.
 */
public class ActiveMQDynamicProducerDelegate extends ActiveMQProducerDelegate
         implements ActiveMQDynamicProducerOperations {

   public ActiveMQDynamicProducerDelegate(String url, String username, String password) {
      super(url, username, password);
   }

   public ActiveMQDynamicProducerDelegate(String url) {
      super(url);
   }

   public ActiveMQDynamicProducerDelegate(ServerLocator serverLocator, String username, String password) {
      super(serverLocator, username, password);
   }

   public ActiveMQDynamicProducerDelegate(ServerLocator serverLocator) {
      super(serverLocator);
   }

   public ActiveMQDynamicProducerDelegate(String url, SimpleString address, String username, String password) {
      super(url, address, username, password);
   }

   public ActiveMQDynamicProducerDelegate(String url, SimpleString address) {
      super(url, address);
   }

   public ActiveMQDynamicProducerDelegate(ServerLocator serverLocator, SimpleString address, String username,
                                          String password) {
      super(serverLocator, address, username, password);
   }

   public ActiveMQDynamicProducerDelegate(ServerLocator serverLocator, SimpleString address) {
      super(serverLocator, address);
   }

   @Override
   protected void createClient() {
      try {
         if (address != null && !session.addressQuery(address).isExists() && autoCreateQueue) {
            log.warn("queue does not exist - creating queue: address = {}, name = {}", address.toString(),
                     address.toString());
            session.createQueue(QueueConfiguration.of(address));
         }
         producer = session.createProducer((SimpleString) null);
      } catch (ActiveMQException amqEx) {
         if (address == null) {
            throw new ActiveMQClientResourceException(String.format("Error creating producer for address %s",
                                                                    address.toString()),
                                                      amqEx);
         } else {
            throw new ActiveMQClientResourceException("Error creating producer", amqEx);
         }
      }
   }

   @Override
   public void sendMessage(ClientMessage message) {
      sendMessage(address, message);
   }

   @Override
   public void sendMessage(SimpleString targetAddress, ClientMessage message) {
      if (targetAddress == null) {
         throw new IllegalArgumentException(String.format("%s error - address cannot be null",
                                                          this.getClass().getSimpleName()));
      }
      try {
         if (autoCreateQueue && !session.addressQuery(targetAddress).isExists()) {
            log.warn("queue does not exist - creating queue: address = {}, name = {}", address.toString(),
                     address.toString());
            session.createQueue(QueueConfiguration.of(targetAddress));
         }
      } catch (ActiveMQException amqEx) {
         throw new ActiveMQClientResourceException(String.format("Queue creation failed for queue: address = %s, name = %s",
                                                                 address.toString(), address.toString()));
      }

      try {
         producer.send(targetAddress, message);
      } catch (ActiveMQException amqEx) {
         throw new ActiveMQClientResourceException(String.format("Failed to send message to %s",
                                                                 targetAddress.toString()),
                                                   amqEx);
      }
   }

   @Override
   public ClientMessage sendMessage(SimpleString targetAddress, byte[] body) {
      ClientMessage message = createMessage(body);
      sendMessage(targetAddress, message);
      return message;
   }

   @Override
   public ClientMessage sendMessage(SimpleString targetAddress, String body) {
      ClientMessage message = createMessage(body);
      sendMessage(targetAddress, message);
      return message;
   }

   @Override
   public ClientMessage sendMessage(SimpleString targetAddress, Map<String, Object> properties) {
      ClientMessage message = createMessage(properties);
      sendMessage(targetAddress, message);
      return message;
   }

   @Override
   public ClientMessage sendMessage(SimpleString targetAddress, byte[] body, Map<String, Object> properties) {
      ClientMessage message = createMessage(body, properties);
      sendMessage(targetAddress, message);
      return message;
   }

   @Override
   public ClientMessage sendMessage(SimpleString targetAddress, String body, Map<String, Object> properties) {
      ClientMessage message = createMessage(body, properties);
      sendMessage(targetAddress, message);
      return message;
   }
}
