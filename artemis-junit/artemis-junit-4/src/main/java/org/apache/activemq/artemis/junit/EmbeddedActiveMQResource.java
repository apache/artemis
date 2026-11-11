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

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JUnit Rule that embeds an Apache Artemis server into a test. This JUnit Rule is designed to simplify using
 * embedded servers in unit tests. Adding the rule to a test will startup an embedded server, which can then be used by
 * client applications.
 *
 * <pre>{@code
 * public class SimpleTest {
 *     @Rule
 *     public EmbeddedActiveMQResource server = new EmbeddedActiveMQResource();
 *
 *     @Test
 *     public void testSomething() throws Exception {
 *         // Use the embedded server here
 *     }
 * }
 * }</pre>
 */
public class EmbeddedActiveMQResource extends ExternalResource implements EmbeddedActiveMQOperations {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private EmbeddedActiveMQOperations embeddedActiveMQDelegate;

   /**
    * Create a default EmbeddedActiveMQResource
    */
   public EmbeddedActiveMQResource() {
      this.embeddedActiveMQDelegate = new EmbeddedActiveMQDelegate();
   }

   /**
    * Create a default EmbeddedActiveMQResource with the specified serverId
    *
    * @param serverId server id
    */
   public EmbeddedActiveMQResource(int serverId) {
      this.embeddedActiveMQDelegate = new EmbeddedActiveMQDelegate(serverId);
   }

   /**
    * Creates an EmbeddedActiveMQResource using the specified configuration
    *
    * @param configuration ActiveMQServer configuration
    */
   public EmbeddedActiveMQResource(Configuration configuration) {
      this.embeddedActiveMQDelegate = new EmbeddedActiveMQDelegate(configuration);
   }

   /**
    * Creates an EmbeddedActiveMQResource using the specified configuration file
    *
    * @param filename ActiveMQServer configuration file name
    */
   public EmbeddedActiveMQResource(String filename) {
      this.embeddedActiveMQDelegate = new EmbeddedActiveMQDelegate(filename);
   }

   /**
    * Add the specified properties to the specified message.
    *
    * @see EmbeddedActiveMQDelegate#addMessageProperties(ClientMessage, Map)
    */
   public static void addMessageProperties(ClientMessage message, Map<String, Object> properties) {
      EmbeddedActiveMQDelegate.addMessageProperties(message, properties);
   }

   @Override
   public void start() {
      embeddedActiveMQDelegate.start();
   }

   @Override
   public void stop() {
      embeddedActiveMQDelegate.stop();
   }

   /**
    * Invoked by JUnit to setup the resource - start the embedded Apache Artemis server
    */
   @Override
   protected void before() throws Throwable {
      logger.info("Starting {}: {}", this.getClass().getSimpleName(), embeddedActiveMQDelegate.getServerName());

      embeddedActiveMQDelegate.start();

      super.before();
   }

   /**
    * Invoked by JUnit to tear down the resource - stops the embedded Apache Artemis server
    */
   @Override
   protected void after() {
      logger.info("Stopping {}: {}", this.getClass().getSimpleName(), embeddedActiveMQDelegate.getServerName());

      embeddedActiveMQDelegate.stop();

      super.after();
   }

   @Override
   public boolean isUseDurableMessage() {
      return embeddedActiveMQDelegate.isUseDurableMessage();
   }

   @Override
   public void setUseDurableMessage(boolean useDurableMessage) {
      embeddedActiveMQDelegate.setUseDurableMessage(useDurableMessage);
   }

   @Override
   public boolean isUseDurableQueue() {
      return embeddedActiveMQDelegate.isUseDurableQueue();
   }

   @Override
   public void setUseDurableQueue(boolean useDurableQueue) {
      embeddedActiveMQDelegate.setUseDurableQueue(useDurableQueue);
   }

   @Override
   public long getDefaultReceiveTimeout() {
      return embeddedActiveMQDelegate.getDefaultReceiveTimeout();
   }

   @Override
   public void setDefaultReceiveTimeout(long defaultReceiveTimeout) {
      embeddedActiveMQDelegate.setDefaultReceiveTimeout(defaultReceiveTimeout);
   }

   @Override
   public EmbeddedActiveMQ getServer() {
      return embeddedActiveMQDelegate.getServer();
   }

   @Override
   public String getServerName() {
      return embeddedActiveMQDelegate.getServerName();
   }

   @Override
   public String getVmURL() {
      return embeddedActiveMQDelegate.getVmURL();
   }

   @Override
   public long getMessageCount(String queueName) {
      return embeddedActiveMQDelegate.getMessageCount(queueName);
   }

   @Override
   public long getMessageCount(SimpleString queueName) {
      return embeddedActiveMQDelegate.getMessageCount(queueName);
   }

   @Override
   public Queue locateQueue(String queueName) {
      return embeddedActiveMQDelegate.locateQueue(queueName);
   }

   @Override
   public Queue locateQueue(SimpleString queueName) {
      return embeddedActiveMQDelegate.locateQueue(queueName);
   }

   @Override
   public List<Queue> getBoundQueues(String address) {
      return embeddedActiveMQDelegate.getBoundQueues(address);
   }

   @Override
   public List<Queue> getBoundQueues(SimpleString address) {
      return embeddedActiveMQDelegate.getBoundQueues(address);
   }

   @Override
   public Queue createQueue(String name) {
      return embeddedActiveMQDelegate.createQueue(name);
   }

   @Override
   public Queue createQueue(String address, String name) {
      return embeddedActiveMQDelegate.createQueue(address, name);
   }

   @Override
   public Queue createQueue(SimpleString address, SimpleString name) {
      return embeddedActiveMQDelegate.createQueue(address, name);
   }

   @Override
   public void createSharedQueue(String name, String user) {
      embeddedActiveMQDelegate.createSharedQueue(name, user);
   }

   @Override
   public void createSharedQueue(String address, String name, String user) {
      embeddedActiveMQDelegate.createSharedQueue(address, name, user);
   }

   @Override
   public void createSharedQueue(SimpleString address, SimpleString name, SimpleString user) {
      embeddedActiveMQDelegate.createSharedQueue(address, name, user);
   }

   @Override
   public ClientMessage createMessage() {
      return embeddedActiveMQDelegate.createMessage();
   }

   @Override
   public ClientMessage createMessage(byte[] body) {
      return embeddedActiveMQDelegate.createMessage(body);
   }

   @Override
   public ClientMessage createMessage(String body) {
      return embeddedActiveMQDelegate.createMessage(body);
   }

   @Override
   public ClientMessage createMessageWithProperties(Map<String, Object> properties) {
      return embeddedActiveMQDelegate.createMessageWithProperties(properties);
   }

   @Override
   public ClientMessage createMessageWithProperties(byte[] body, Map<String, Object> properties) {
      return embeddedActiveMQDelegate.createMessageWithProperties(body, properties);
   }

   @Override
   public ClientMessage createMessageWithProperties(String body, Map<String, Object> properties) {
      return embeddedActiveMQDelegate.createMessageWithProperties(body, properties);
   }

   @Override
   public void sendMessage(String address, ClientMessage message) {
      embeddedActiveMQDelegate.sendMessage(address, message);
   }

   @Override
   public ClientMessage sendMessage(String address, byte[] body) {
      return embeddedActiveMQDelegate.sendMessage(address, body);
   }

   @Override
   public ClientMessage sendMessage(String address, String body) {
      return embeddedActiveMQDelegate.sendMessage(address, body);
   }

   @Override
   public ClientMessage sendMessageWithProperties(String address, Map<String, Object> properties) {
      return embeddedActiveMQDelegate.sendMessageWithProperties(address, properties);
   }

   @Override
   public ClientMessage sendMessageWithProperties(String address, byte[] body, Map<String, Object> properties) {
      return embeddedActiveMQDelegate.sendMessageWithProperties(address, body, properties);
   }

   @Override
   public ClientMessage sendMessageWithProperties(String address, String body, Map<String, Object> properties) {
      return embeddedActiveMQDelegate.sendMessageWithProperties(address, body, properties);
   }

   @Override
   public void sendMessage(SimpleString address, ClientMessage message) {
      embeddedActiveMQDelegate.sendMessage(address, message);
   }

   @Override
   public ClientMessage sendMessage(SimpleString address, byte[] body) {
      return embeddedActiveMQDelegate.sendMessage(address, body);
   }

   @Override
   public ClientMessage sendMessage(SimpleString address, String body) {
      return embeddedActiveMQDelegate.sendMessage(address, body);
   }

   @Override
   public ClientMessage sendMessageWithProperties(SimpleString address, Map<String, Object> properties) {
      return embeddedActiveMQDelegate.sendMessageWithProperties(address, properties);
   }

   @Override
   public ClientMessage sendMessageWithProperties(SimpleString address, byte[] body, Map<String, Object> properties) {
      return embeddedActiveMQDelegate.sendMessageWithProperties(address, body, properties);
   }

   @Override
   public ClientMessage sendMessageWithProperties(SimpleString address, String body, Map<String, Object> properties) {
      return embeddedActiveMQDelegate.sendMessageWithProperties(address, body, properties);
   }

   @Override
   public ClientMessage receiveMessage(String queueName) {
      return embeddedActiveMQDelegate.receiveMessage(queueName);
   }

   @Override
   public ClientMessage receiveMessage(String queueName, long timeout) {
      return embeddedActiveMQDelegate.receiveMessage(queueName, timeout);
   }

   @Override
   public ClientMessage receiveMessage(SimpleString queueName) {
      return embeddedActiveMQDelegate.receiveMessage(queueName);
   }

   @Override
   public ClientMessage receiveMessage(SimpleString queueName, long timeout) {
      return embeddedActiveMQDelegate.receiveMessage(queueName, timeout);
   }

   @Override
   public ClientMessage browseMessage(String queueName) {
      return embeddedActiveMQDelegate.browseMessage(queueName);
   }

   @Override
   public ClientMessage browseMessage(String queueName, long timeout) {
      return embeddedActiveMQDelegate.browseMessage(queueName, timeout);
   }

   @Override
   public ClientMessage browseMessage(SimpleString queueName) {
      return embeddedActiveMQDelegate.browseMessage(queueName);
   }

   @Override
   public ClientMessage browseMessage(SimpleString queueName, long timeout) {
      return embeddedActiveMQDelegate.browseMessage(queueName, timeout);
   }

}
