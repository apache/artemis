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
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * A JUnit Extension that embeds an dynamic (i.e. unbound) Apache Artemis ClientProducer into a test.
 * <p>
 * This JUnit Extension is designed to simplify using Apache Artemis clients in unit tests.  Adding the extension to a
 * test will startup an unbound ClientProducer, which can then be used to feed messages to any address on the ActiveMQ
 * Artemis server.
 *
 * <pre>{@code
 * public class SimpleTest {
 *     @RegisterExtension
 *     private ActiveMQDynamicProducerExtension producer = new ActiveMQDynamicProducerExtension("vm://0");
 *
 *     @Test
 *     public void testSomething() throws Exception {
 *         // Use the embedded ClientProducer here
 *         producer.sendMessage( "test.address", "String Body" );
 *     }
 * }
 * }</pre>
 */
public class ActiveMQDynamicProducerExtension implements BeforeAllCallback, AfterAllCallback, ActiveMQDynamicProducerOperations, ActiveMQProducerOperations {

   private ActiveMQDynamicProducerDelegate activeMQDynamicProducer;

   public ActiveMQDynamicProducerExtension(String url, String username, String password) {
      this.activeMQDynamicProducer = new ActiveMQDynamicProducerDelegate(url, username, password);
   }

   public ActiveMQDynamicProducerExtension(String url) {
      this.activeMQDynamicProducer = new ActiveMQDynamicProducerDelegate(url);
   }

   public ActiveMQDynamicProducerExtension(ServerLocator serverLocator, String username, String password) {
      this.activeMQDynamicProducer = new ActiveMQDynamicProducerDelegate(serverLocator, username, password);
   }

   public ActiveMQDynamicProducerExtension(ServerLocator serverLocator) {
      this.activeMQDynamicProducer = new ActiveMQDynamicProducerDelegate(serverLocator);
   }

   public ActiveMQDynamicProducerExtension(String url, SimpleString address, String username, String password) {
      this.activeMQDynamicProducer = new ActiveMQDynamicProducerDelegate(url, address, username, password);
   }

   public ActiveMQDynamicProducerExtension(String url, SimpleString address) {
      this.activeMQDynamicProducer = new ActiveMQDynamicProducerDelegate(url, address);
   }

   public ActiveMQDynamicProducerExtension(ServerLocator serverLocator,
                                           SimpleString address,
                                           String username,
                                           String password) {
      this.activeMQDynamicProducer = new ActiveMQDynamicProducerDelegate(serverLocator, address, username, password);
   }

   public ActiveMQDynamicProducerExtension(ServerLocator serverLocator, SimpleString address) {
      this.activeMQDynamicProducer = new ActiveMQDynamicProducerDelegate(serverLocator, address);
   }

   protected void createClient() {
      activeMQDynamicProducer.createClient();
   }

   @Override
   public boolean isUseDurableMessage() {
      return activeMQDynamicProducer.isUseDurableMessage();
   }

   @Override
   public void setUseDurableMessage(boolean useDurableMessage) {
      activeMQDynamicProducer.setUseDurableMessage(useDurableMessage);
   }

   public void stopClient() {
      activeMQDynamicProducer.stopClient();
   }

   @Override
   public ClientMessage createMessage() {
      return activeMQDynamicProducer.createMessage();
   }

   @Override
   public ClientMessage createMessage(byte[] body) {
      return activeMQDynamicProducer.createMessage(body);
   }

   @Override
   public ClientMessage createMessage(String body) {
      return activeMQDynamicProducer.createMessage(body);
   }

   @Override
   public ClientMessage createMessage(Map<String, Object> properties) {
      return activeMQDynamicProducer.createMessage(properties);
   }

   @Override
   public ClientMessage createMessage(byte[] body, Map<String, Object> properties) {
      return activeMQDynamicProducer.createMessage(body, properties);
   }

   @Override
   public ClientMessage createMessage(String body, Map<String, Object> properties) {
      return activeMQDynamicProducer.createMessage(body, properties);
   }

   @Override
   public void sendMessage(ClientMessage message) {
      activeMQDynamicProducer.sendMessage(message);
   }

   @Override
   public ClientMessage sendMessage(byte[] body) {
      return activeMQDynamicProducer.sendMessage(body);
   }

   @Override
   public ClientMessage sendMessage(String body) {
      return activeMQDynamicProducer.sendMessage(body);
   }

   @Override
   public ClientMessage sendMessage(Map<String, Object> properties) {
      return activeMQDynamicProducer.sendMessage(properties);
   }

   @Override
   public ClientMessage sendMessage(byte[] body, Map<String, Object> properties) {
      return activeMQDynamicProducer.sendMessage(body, properties);
   }

   @Override
   public ClientMessage sendMessage(String body, Map<String, Object> properties) {
      return activeMQDynamicProducer.sendMessage(body, properties);
   }

   @Override
   public void sendMessage(SimpleString targetAddress, ClientMessage message) {
      activeMQDynamicProducer.sendMessage(targetAddress, message);
   }

   @Override
   public ClientMessage sendMessage(SimpleString targetAddress, byte[] body) {
      return activeMQDynamicProducer.sendMessage(targetAddress, body);
   }

   @Override
   public ClientMessage sendMessage(SimpleString targetAddress, String body) {
      return activeMQDynamicProducer.sendMessage(targetAddress, body);
   }

   @Override
   public ClientMessage sendMessage(SimpleString targetAddress, Map<String, Object> properties) {
      return activeMQDynamicProducer.sendMessage(targetAddress, properties);
   }

   @Override
   public ClientMessage sendMessage(SimpleString targetAddress, byte[] body, Map<String, Object> properties) {
      return activeMQDynamicProducer.sendMessage(targetAddress, body, properties);
   }

   @Override
   public ClientMessage sendMessage(SimpleString targetAddress, String body, Map<String, Object> properties) {
      return activeMQDynamicProducer.sendMessage(targetAddress, body, properties);
   }

   public static void addMessageProperties(ClientMessage message, Map<String, Object> properties) {
      AbstractActiveMQClientDelegate.addMessageProperties(message, properties);
   }

   public boolean isAutoCreateQueue() {
      return activeMQDynamicProducer.isAutoCreateQueue();
   }

   public void setAutoCreateQueue(boolean autoCreateQueue) {
      activeMQDynamicProducer.setAutoCreateQueue(autoCreateQueue);
   }

   @Override
   public void beforeAll(ExtensionContext context) throws Exception {
      activeMQDynamicProducer.start();
   }

   @Override
   public void afterAll(ExtensionContext context) throws Exception {
      activeMQDynamicProducer.stop();
   }
}
