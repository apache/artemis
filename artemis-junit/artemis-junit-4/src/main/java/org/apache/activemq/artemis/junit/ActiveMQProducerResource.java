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
import org.junit.rules.ExternalResource;

/**
 * A JUnit Rule that embeds an Apache Artemis ClientProducer bound to a specific address into a
 * test.
 * <p>
 * This JUnit Rule is designed to simplify using Apache Artemis clients in unit tests. Adding the
 * rule to a test will startup a ClientProducer, which can then be used to feed messages to the
 * bound address on an Apache Artemis server.
 *
 * <pre>{@code
 * public class SimpleTest {
 *     @Rule
 *     public ActiveMQProducerResource producer = new ActiveMQProducerResource( "vm://0", "test.queue");
 *
 *     @Test
 *     public void testSomething() throws Exception {
 *         // Use the embedded ClientProducer here
 *         producer.sendMessage( "String Body" );
 *     }
 * }
 * }</pre>
 */
public class ActiveMQProducerResource extends ExternalResource implements ActiveMQProducerOperations {

   private ActiveMQProducerDelegate activeMQProducer;

   public ActiveMQProducerResource(String url, String username, String password) {
      activeMQProducer = new ActiveMQProducerDelegate(url, username, password);
   }

   public ActiveMQProducerResource(String url) {
      activeMQProducer = new ActiveMQProducerDelegate(url);
   }

   public ActiveMQProducerResource(ServerLocator serverLocator, String username, String password) {
      activeMQProducer = new ActiveMQProducerDelegate(serverLocator, username, password);
   }

   public ActiveMQProducerResource(ServerLocator serverLocator) {
      activeMQProducer = new ActiveMQProducerDelegate(serverLocator);
   }

   public ActiveMQProducerResource(String url, String address, String username, String password) {
      activeMQProducer = new ActiveMQProducerDelegate(url, SimpleString.of(address), username, password);
   }

   public ActiveMQProducerResource(String url, String address) {
      activeMQProducer = new ActiveMQProducerDelegate(url, address, null, null);
   }

   public ActiveMQProducerResource(String url, SimpleString address, String username, String password) {
      activeMQProducer = new ActiveMQProducerDelegate(url, address, username, password);
   }

   public ActiveMQProducerResource(String url, SimpleString address) {
      activeMQProducer = new ActiveMQProducerDelegate(url, address, null, null);
   }

   public ActiveMQProducerResource(ServerLocator serverLocator, String address, String username, String password) {
      activeMQProducer =
               new ActiveMQProducerDelegate(serverLocator, SimpleString.of(address), username, password);
   }

   public ActiveMQProducerResource(ServerLocator serverLocator, String address) {
      activeMQProducer = new ActiveMQProducerDelegate(serverLocator, SimpleString.of(address));
   }

   public ActiveMQProducerResource(ServerLocator serverLocator, SimpleString address, String username,
                                   String password) {
      activeMQProducer = new ActiveMQProducerDelegate(serverLocator, address, username, password);
   }

   public ActiveMQProducerResource(ServerLocator serverLocator, SimpleString address) {
      activeMQProducer = new ActiveMQProducerDelegate(serverLocator, address, null, null);
   }

   @Override
   protected void before() throws Throwable {
      super.before();
      activeMQProducer.start();
   }

   @Override
   protected void after() {
      activeMQProducer.stop();
      super.after();
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
