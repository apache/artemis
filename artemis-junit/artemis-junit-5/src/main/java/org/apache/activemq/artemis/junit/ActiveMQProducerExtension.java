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
 * A JUnit Extension that embeds an Apache Artemis ClientProducer bound to a specific address into a test.
 * <p>
 * This JUnit Extension is designed to simplify using Apache Artemis clients in unit tests. Adding the extension to a
 * test will startup a ClientProducer, which can then be used to feed messages to the bound address on an ActiveMQ
 * Artemis server.
 *
 * <pre>{@code
 * public class SimpleTest {
 *     @RegisterExtension
 *     private ActiveMQProducerExtension producer = new ActiveMQProducerExtension( "vm://0", "test.queue");
 *
 *     @Test
 *     public void testSomething() throws Exception {
 *         // Use the embedded ClientProducer here
 *         producer.sendMessage( "String Body" );
 *     }
 * }
 * }</pre>
 */
public class ActiveMQProducerExtension implements BeforeAllCallback, AfterAllCallback, ActiveMQProducerOperations {

   private ActiveMQProducerDelegate activeMQProducer;

   protected ActiveMQProducerExtension(String url, String username, String password) {
      this.activeMQProducer = new ActiveMQProducerDelegate(url, username, password);
   }

   protected ActiveMQProducerExtension(String url) {
      this.activeMQProducer = new ActiveMQProducerDelegate(url);
   }

   protected ActiveMQProducerExtension(ServerLocator serverLocator, String username, String password) {
      this.activeMQProducer = new ActiveMQProducerDelegate(serverLocator, username, password);
   }

   protected ActiveMQProducerExtension(ServerLocator serverLocator) {
      this.activeMQProducer = new ActiveMQProducerDelegate(serverLocator);
   }

   public ActiveMQProducerExtension(String url, String address, String username, String password) {
      this.activeMQProducer = new ActiveMQProducerDelegate(url, address, username, password);
   }

   public ActiveMQProducerExtension(String url, String address) {
      this.activeMQProducer = new ActiveMQProducerDelegate(url, address);
   }

   public ActiveMQProducerExtension(String url, SimpleString address, String username, String password) {
      this.activeMQProducer = new ActiveMQProducerDelegate(url, address, username, password);
   }

   public ActiveMQProducerExtension(String url, SimpleString address) {
      this.activeMQProducer = new ActiveMQProducerDelegate(url, address);
   }

   public ActiveMQProducerExtension(ServerLocator serverLocator, String address, String username, String password) {
      this.activeMQProducer = new ActiveMQProducerDelegate(serverLocator, address, username, password);
   }

   public ActiveMQProducerExtension(ServerLocator serverLocator, String address) {
      this.activeMQProducer = new ActiveMQProducerDelegate(serverLocator, address);
   }

   public ActiveMQProducerExtension(ServerLocator serverLocator,
                                    SimpleString address,
                                    String username,
                                    String password) {
      this.activeMQProducer = new ActiveMQProducerDelegate(serverLocator, address, username, password);
   }

   public ActiveMQProducerExtension(ServerLocator serverLocator, SimpleString address) {
      this.activeMQProducer = new ActiveMQProducerDelegate(serverLocator, address);
   }

   @Override
   public void beforeAll(ExtensionContext context) throws Exception {
      activeMQProducer.start();
   }

   @Override
   public void afterAll(ExtensionContext context) throws Exception {
      activeMQProducer.stop();
   }

   @Override
   public boolean isUseDurableMessage() {
      return activeMQProducer.isUseDurableMessage();
   }

   @Override
   public void setUseDurableMessage(boolean useDurableMessage) {
      activeMQProducer.setUseDurableMessage(useDurableMessage);
   }

   protected void createClient() {
      activeMQProducer.createClient();
   }

   protected void stopClient() {
      activeMQProducer.stopClient();
   }

   @Override
   public ClientMessage createMessage() {
      return activeMQProducer.createMessage();
   }

   @Override
   public ClientMessage createMessage(byte[] body) {
      return activeMQProducer.createMessage(body);
   }

   @Override
   public ClientMessage createMessage(String body) {
      return activeMQProducer.createMessage(body);
   }

   @Override
   public ClientMessage createMessage(Map<String, Object> properties) {
      return activeMQProducer.createMessage(properties);
   }

   @Override
   public ClientMessage createMessage(byte[] body, Map<String, Object> properties) {
      return activeMQProducer.createMessage(body, properties);
   }

   @Override
   public ClientMessage createMessage(String body, Map<String, Object> properties) {
      return activeMQProducer.createMessage(body, properties);
   }

   @Override
   public void sendMessage(ClientMessage message) {
      activeMQProducer.sendMessage(message);
   }

   @Override
   public ClientMessage sendMessage(byte[] body) {
      return activeMQProducer.sendMessage(body);
   }

   @Override
   public ClientMessage sendMessage(String body) {
      return activeMQProducer.sendMessage(body);
   }

   @Override
   public ClientMessage sendMessage(Map<String, Object> properties) {
      return activeMQProducer.sendMessage(properties);
   }

   @Override
   public ClientMessage sendMessage(byte[] body, Map<String, Object> properties) {
      return activeMQProducer.sendMessage(body, properties);
   }

   @Override
   public ClientMessage sendMessage(String body, Map<String, Object> properties) {
      return activeMQProducer.sendMessage(body, properties);
   }

   public static void addMessageProperties(ClientMessage message, Map<String, Object> properties) {
      AbstractActiveMQClientDelegate.addMessageProperties(message, properties);
   }

   public boolean isAutoCreateQueue() {
      return activeMQProducer.isAutoCreateQueue();
   }

   public void setAutoCreateQueue(boolean autoCreateQueue) {
      activeMQProducer.setAutoCreateQueue(autoCreateQueue);
   }
}
