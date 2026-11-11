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
 * A JUnit Extension that embeds an Apache Artemis ClientConsumer into a test.
 * <p>
 * This JUnit Extension is designed to simplify using Apache Artemis clients in unit tests.  Adding the extension to a
 * test will startup a ClientConsumer, which can then be used to consume messages from an Apache Artemis server.
 *
 * <pre>{@code
 * public class SimpleTest {
 *     @RegisterExtension
 *     private ActiveMQConsumerExtension client = new ActiveMQConsumerExtension( "vm://0", "test.queue" );
 *
 *     @Test
 *     public void testSomething() throws Exception {
 *         // Use the embedded client here
 *         ClientMessage message = client.receiveMessage();
 *     }
 * }
 * }</pre>
 */
public class ActiveMQConsumerExtension implements BeforeAllCallback, AfterAllCallback, ActiveMQConsumerOperations {

   private final ActiveMQConsumerDelegate activeMQConsumerDelegate;

   public ActiveMQConsumerExtension(String url, String queueName) {
      this.activeMQConsumerDelegate = new ActiveMQConsumerDelegate(url, queueName);
   }

   public ActiveMQConsumerExtension(String url, String queueName, String username, String password) {
      this.activeMQConsumerDelegate = new ActiveMQConsumerDelegate(url, queueName, username, password);
   }

   public ActiveMQConsumerExtension(String url, SimpleString queueName, String username, String password) {
      this.activeMQConsumerDelegate = new ActiveMQConsumerDelegate(url, queueName, username, password);
   }

   public ActiveMQConsumerExtension(String url, SimpleString queueName) {
      this.activeMQConsumerDelegate = new ActiveMQConsumerDelegate(url, queueName);
   }

   public ActiveMQConsumerExtension(ServerLocator serverLocator, String queueName, String username, String password) {
      this.activeMQConsumerDelegate = new ActiveMQConsumerDelegate(serverLocator, queueName, username, password);
   }

   public ActiveMQConsumerExtension(ServerLocator serverLocator, String queueName) {
      this.activeMQConsumerDelegate = new ActiveMQConsumerDelegate(serverLocator, queueName);
   }

   public ActiveMQConsumerExtension(ServerLocator serverLocator,
                                    SimpleString queueName,
                                    String username,
                                    String password) {
      this.activeMQConsumerDelegate = new ActiveMQConsumerDelegate(serverLocator, queueName, username, password);
   }

   public ActiveMQConsumerExtension(ServerLocator serverLocator, SimpleString queueName) {
      this.activeMQConsumerDelegate = new ActiveMQConsumerDelegate(serverLocator, queueName);
   }

   @Override
   public void beforeAll(ExtensionContext context) throws Exception {
      activeMQConsumerDelegate.start();
   }

   @Override
   public void afterAll(ExtensionContext context) throws Exception {
      activeMQConsumerDelegate.stop();
   }

   @Override
   public long getDefaultReceiveTimeout() {
      return activeMQConsumerDelegate.getDefaultReceiveTimeout();
   }

   @Override
   public void setDefaultReceiveTimeout(long defaultReceiveTimeout) {
      activeMQConsumerDelegate.setDefaultReceiveTimeout(defaultReceiveTimeout);
   }

   protected void createClient() {
      activeMQConsumerDelegate.createClient();
   }

   protected void stopClient() {
      activeMQConsumerDelegate.stopClient();
   }

   @Override
   public boolean isAutoCreateQueue() {
      return activeMQConsumerDelegate.isAutoCreateQueue();
   }

   @Override
   public void setAutoCreateQueue(boolean autoCreateQueue) {
      activeMQConsumerDelegate.setAutoCreateQueue(autoCreateQueue);
   }

   @Override
   public ClientMessage receiveMessage() {
      return activeMQConsumerDelegate.receiveMessage();
   }

   @Override
   public ClientMessage receiveMessage(long timeout) {
      return activeMQConsumerDelegate.receiveMessage(timeout);
   }

   public static void addMessageProperties(ClientMessage message, Map<String, Object> properties) {
      AbstractActiveMQClientDelegate.addMessageProperties(message, properties);
   }
}
