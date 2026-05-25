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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.BindingQueryResult;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Base class to embed an Apache Artemis server into a test.
 */
public class EmbeddedActiveMQDelegate implements EmbeddedActiveMQOperations {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   static final String SERVER_NAME = "embedded-server";

   boolean useDurableMessage = true;
   boolean useDurableQueue = true;
   long defaultReceiveTimeout = 50;

   Configuration configuration;

   EmbeddedActiveMQ server;

   InternalClient internalClient;

   /**
    * Create a default EmbeddedActiveMQResource
    */
   protected EmbeddedActiveMQDelegate() {
      configuration =
               new ConfigurationImpl().setName(SERVER_NAME)
                                      .setPersistenceEnabled(false)
                                      .setSecurityEnabled(false)
                                      .addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName()))
                                      .addAddressSetting("#",
                                                         new AddressSettings().setDeadLetterAddress(SimpleString.of("dla"))
                                                                              .setExpiryAddress(SimpleString.of("expiry")));
      init();
   }

   /**
    * Create a default EmbeddedActiveMQResource with the specified serverId
    *
    * @param serverId server id
    */
   protected EmbeddedActiveMQDelegate(int serverId) {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SERVER_ID_PROP_NAME, serverId);
      TransportConfiguration transportConfiguration =
               new TransportConfiguration(InVMAcceptorFactory.class.getName(), params);
      configuration = new ConfigurationImpl().setName(SERVER_NAME + "-" + serverId)
                                             .setPersistenceEnabled(false)
                                             .setSecurityEnabled(false)
                                             .addAcceptorConfiguration(transportConfiguration);
      init();
   }

   /**
    * Creates an EmbeddedActiveMQResource using the specified configuration
    *
    * @param configuration ActiveMQServer configuration
    */
   protected EmbeddedActiveMQDelegate(Configuration configuration) {
      this.configuration = configuration;
      init();
   }

   /**
    * Creates an EmbeddedActiveMQResource using the specified configuration file
    *
    * @param filename ActiveMQServer configuration file name
    */
   protected EmbeddedActiveMQDelegate(String filename) {
      if (filename == null) {
         throw new IllegalArgumentException("ActiveMQServer configuration file name cannot be null");
      }
      FileDeploymentManager deploymentManager = new FileDeploymentManager(filename);
      FileConfiguration config = new FileConfiguration();
      deploymentManager.addDeployable(config);
      try {
         deploymentManager.readConfiguration();
      } catch (Exception ex) {
         throw new EmbeddedActiveMQResourceException(String.format("Failed to read configuration file %s", filename),
                                                     ex);
      }
      this.configuration = config;
      init();
   }

   /**
    * Adds properties to a ClientMessage
    */
   public static void addMessageProperties(ClientMessage message, Map<String, Object> properties) {
      if (properties != null && !properties.isEmpty()) {
         for (Map.Entry<String, Object> property : properties.entrySet()) {
            message.putObjectProperty(property.getKey(), property.getValue());
         }
      }
   }

   private void init() {
      if (server == null) {
         server = new EmbeddedActiveMQ().setConfiguration(configuration);
      }
   }

   @Override
   public void start() {
      try {
         server.start();
      } catch (Exception ex) {
         throw new RuntimeException(String.format("Exception encountered starting %s: %s", server.getClass().getName(),
                                                  this.getServerName()),
                                    ex);
      }

      configuration = server.getActiveMQServer().getConfiguration();
   }

   @Override
   public void stop() {
      if (internalClient != null) {
         internalClient.stop();
         internalClient = null;
      }

      if (server != null) {
         try {
            server.stop();
         } catch (Exception ex) {
            logger.warn(String.format("Exception encountered stopping %s: %s", server.getClass().getSimpleName(), this.getServerName()), ex);
         }
      }
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
   public boolean isUseDurableQueue() {
      return useDurableQueue;
   }

   @Override
   public void setUseDurableQueue(boolean useDurableQueue) {
      this.useDurableQueue = useDurableQueue;
   }

   @Override
   public long getDefaultReceiveTimeout() {
      return defaultReceiveTimeout;
   }

   @Override
   public void setDefaultReceiveTimeout(long defaultReceiveTimeout) {
      this.defaultReceiveTimeout = defaultReceiveTimeout;
   }

   @Override
   public EmbeddedActiveMQ getServer() {
      return server;
   }

   @Override
   public String getServerName() {
      String name = "unknown";
      ActiveMQServer activeMQServer = server.getActiveMQServer();
      if (activeMQServer != null) {
         name = activeMQServer.getConfiguration().getName();
      } else if (configuration != null) {
         name = configuration.getName();
      }

      return name;
   }

   @Override
   public String getVmURL() {
      String vmURL = "vm://0";
      for (TransportConfiguration transportConfiguration : configuration.getAcceptorConfigurations()) {
         Map<String, Object> params = transportConfiguration.getParams();
         if (params != null && params.containsKey(TransportConstants.SERVER_ID_PROP_NAME)) {
            vmURL = "vm://" + params.get(TransportConstants.SERVER_ID_PROP_NAME);
         }
      }

      return vmURL;
   }

   @Override
   public long getMessageCount(String queueName) {
      return getMessageCount(SimpleString.of(queueName));
   }

   @Override
   public long getMessageCount(SimpleString queueName) {
      Queue queue = locateQueue(queueName);
      if (queue == null) {
         logger.warn("getMessageCount(queueName) - queue {} not found; returning -1", queueName.toString());
         return -1;
      }

      return queue.getMessageCount();
   }

   @Override
   public Queue locateQueue(String queueName) {
      return locateQueue(SimpleString.of(queueName));
   }

   @Override
   public Queue locateQueue(SimpleString queueName) {
      return server.getActiveMQServer().locateQueue(queueName);
   }

   @Override
   public List<Queue> getBoundQueues(String address) {
      return getBoundQueues(SimpleString.of(address));
   }

   @Override
   public List<Queue> getBoundQueues(SimpleString address) {
      if (address == null) {
         throw new IllegalArgumentException("getBoundQueues( address ) - address cannot be null");
      }
      List<Queue> boundQueues = new java.util.LinkedList<>();

      BindingQueryResult bindingQueryResult = null;
      try {
         bindingQueryResult = server.getActiveMQServer().bindingQuery(address);
      } catch (Exception e) {
         throw new EmbeddedActiveMQResourceException(String.format("getBoundQueues( %s ) - bindingQuery( %s ) failed",
                                                                   address.toString(), address.toString()));
      }
      if (bindingQueryResult.isExists()) {
         for (SimpleString queueName : bindingQueryResult.getQueueNames()) {
            boundQueues.add(server.getActiveMQServer().locateQueue(queueName));
         }
      }
      return boundQueues;
   }

   @Override
   public Queue createQueue(String name) {
      return createQueue(SimpleString.of(name), SimpleString.of(name));
   }

   @Override
   public Queue createQueue(String address, String name) {
      return createQueue(SimpleString.of(address), SimpleString.of(name));
   }

   @Override
   public Queue createQueue(SimpleString address, SimpleString name) {
      Queue queue = null;
      try {
         queue = server.getActiveMQServer()
                       .createQueue(QueueConfiguration.of(name).setAddress(address).setDurable(isUseDurableQueue()));
      } catch (Exception ex) {
         throw new EmbeddedActiveMQResourceException(String.format("Failed to create queue: queueName = %s, name = %s",
                                                                   address.toString(), name.toString()),
                                                     ex);
      }

      return queue;
   }

   @Override
   public void createSharedQueue(String name, String user) {
      createSharedQueue(SimpleString.of(name), SimpleString.of(name),
                        SimpleString.of(user));
   }

   @Override
   public void createSharedQueue(String address, String name, String user) {
      createSharedQueue(SimpleString.of(address), SimpleString.of(name),
                        SimpleString.of(user));
   }

   @Override
   public void createSharedQueue(SimpleString address, SimpleString name, SimpleString user) {
      try {
         server.getActiveMQServer()
               .createSharedQueue(QueueConfiguration.of(name).setAddress(address)
                                                              .setRoutingType(RoutingType.MULTICAST)
                                                              .setDurable(isUseDurableQueue())
                                                              .setUser(user));
      } catch (Exception ex) {
         throw new EmbeddedActiveMQResourceException(String.format("Failed to create shared queue: queueName = %s, name = %s, user = %s",
                                                                   address.toString(), name.toString(),
                                                                   user.toString()),
                                                     ex);
      }
   }

   @Override
   public ClientMessage createMessage() {
      getInternalClient();
      return internalClient.createMessage(isUseDurableMessage());
   }

   @Override
   public ClientMessage createMessage(byte[] body) {
      getInternalClient();
      ClientMessage message = internalClient.createMessage(isUseDurableMessage());

      if (body != null) {
         message.writeBodyBufferBytes(body);
      }

      return message;
   }

   @Override
   public ClientMessage createMessage(String body) {
      getInternalClient();
      ClientMessage message = internalClient.createMessage(isUseDurableMessage());

      if (body != null) {
         message.writeBodyBufferString(body);
      }

      return message;
   }

   @Override
   public ClientMessage createMessageWithProperties(Map<String, Object> properties) {
      getInternalClient();
      ClientMessage message = internalClient.createMessage(isUseDurableMessage());

      addMessageProperties(message, properties);

      return message;
   }

   @Override
   public ClientMessage createMessageWithProperties(byte[] body, Map<String, Object> properties) {
      ClientMessage message = createMessage(body);

      addMessageProperties(message, properties);

      return message;
   }

   @Override
   public ClientMessage createMessageWithProperties(String body, Map<String, Object> properties) {
      ClientMessage message = createMessage(body);

      addMessageProperties(message, properties);

      return message;
   }

   @Override
   public void sendMessage(String address, ClientMessage message) {
      sendMessage(SimpleString.of(address), message);
   }

   @Override
   public ClientMessage sendMessage(String address, byte[] body) {
      return sendMessage(SimpleString.of(address), body);
   }

   @Override
   public ClientMessage sendMessage(String address, String body) {
      return sendMessage(SimpleString.of(address), body);
   }

   @Override
   public ClientMessage sendMessageWithProperties(String address, Map<String, Object> properties) {
      return sendMessageWithProperties(SimpleString.of(address), properties);
   }

   @Override
   public ClientMessage sendMessageWithProperties(String address, byte[] body, Map<String, Object> properties) {
      return sendMessageWithProperties(SimpleString.of(address), body, properties);
   }

   @Override
   public ClientMessage sendMessageWithProperties(String address, String body, Map<String, Object> properties) {
      return sendMessageWithProperties(SimpleString.of(address), body, properties);
   }

   @Override
   public void sendMessage(SimpleString address, ClientMessage message) {
      if (address == null) {
         throw new IllegalArgumentException("sendMessage failure - queueName is required");
      } else if (message == null) {
         throw new IllegalArgumentException("sendMessage failure - a ClientMessage is required");
      }

      getInternalClient();
      internalClient.sendMessage(address, message);
   }

   @Override
   public ClientMessage sendMessage(SimpleString address, byte[] body) {
      ClientMessage message = createMessage(body);
      sendMessage(address, message);
      return message;
   }

   @Override
   public ClientMessage sendMessage(SimpleString address, String body) {
      ClientMessage message = createMessage(body);
      sendMessage(address, message);
      return message;
   }

   @Override
   public ClientMessage sendMessageWithProperties(SimpleString address, Map<String, Object> properties) {
      ClientMessage message = createMessageWithProperties(properties);
      sendMessage(address, message);
      return message;
   }

   @Override
   public ClientMessage sendMessageWithProperties(SimpleString address, byte[] body, Map<String, Object> properties) {
      ClientMessage message = createMessageWithProperties(body, properties);
      sendMessage(address, message);
      return message;
   }

   @Override
   public ClientMessage sendMessageWithProperties(SimpleString address, String body, Map<String, Object> properties) {
      ClientMessage message = createMessageWithProperties(body, properties);
      sendMessage(address, message);
      return message;
   }

   @Override
   public ClientMessage receiveMessage(String queueName) {
      return receiveMessage(SimpleString.of(queueName));
   }

   @Override
   public ClientMessage receiveMessage(String queueName, long timeout) {
      return receiveMessage(SimpleString.of(queueName), timeout);
   }

   @Override
   public ClientMessage receiveMessage(SimpleString queueName) {
      final boolean browseOnly = false;
      return getInternalClient().receiveMessage(queueName, defaultReceiveTimeout, browseOnly);
   }

   @Override
   public ClientMessage receiveMessage(SimpleString queueName, long timeout) {
      final boolean browseOnly = false;
      return getInternalClient().receiveMessage(queueName, timeout, browseOnly);
   }

   @Override
   public ClientMessage browseMessage(String queueName) {
      return browseMessage(SimpleString.of(queueName), defaultReceiveTimeout);
   }

   @Override
   public ClientMessage browseMessage(String queueName, long timeout) {
      return browseMessage(SimpleString.of(queueName), timeout);
   }

   @Override
   public ClientMessage browseMessage(SimpleString queueName) {
      final boolean browseOnly = true;
      return getInternalClient().receiveMessage(queueName, defaultReceiveTimeout, browseOnly);
   }

   @Override
   public ClientMessage browseMessage(SimpleString queueName, long timeout) {
      final boolean browseOnly = true;
      return getInternalClient().receiveMessage(queueName, timeout, browseOnly);
   }

   private InternalClient getInternalClient() {
      if (internalClient == null) {
         logger.info("Creating Internal Client");
         internalClient = new InternalClient();
         internalClient.start();
      }

      return internalClient;
   }

   public static class EmbeddedActiveMQResourceException extends RuntimeException {

      public EmbeddedActiveMQResourceException(String message) {
         super(message);
      }

      public EmbeddedActiveMQResourceException(String message, Exception cause) {
         super(message, cause);
      }
   }

   private class InternalClient {

      ServerLocator serverLocator;
      ClientSessionFactory sessionFactory;
      ClientSession session;
      ClientProducer producer;

      InternalClient() {
      }

      void start() {
         logger.info("Starting {}", this.getClass().getSimpleName());
         try {
            serverLocator = ActiveMQClient.createServerLocator(getVmURL());
            sessionFactory = serverLocator.createSessionFactory();
         } catch (RuntimeException runtimeEx) {
            throw runtimeEx;
         } catch (Exception ex) {
            throw new EmbeddedActiveMQResourceException("Internal Client creation failure", ex);
         }

         try {
            session = sessionFactory.createSession();
            producer = session.createProducer((String) null);
            session.start();
         } catch (ActiveMQException amqEx) {
            throw new EmbeddedActiveMQResourceException("Internal Client creation failure", amqEx);
         }
      }

      void stop() {
         if (producer != null) {
            try {
               producer.close();
            } catch (ActiveMQException amqEx) {
               logger.warn("ActiveMQException encountered closing InternalClient ClientProducer - ignoring", amqEx);
            } finally {
               producer = null;
            }
         }
         if (session != null) {
            try {
               session.close();
            } catch (ActiveMQException amqEx) {
               logger.warn("ActiveMQException encountered closing InternalClient ClientSession - ignoring", amqEx);
            } finally {
               session = null;
            }
         }
         if (sessionFactory != null) {
            sessionFactory.close();
            sessionFactory = null;
         }
         if (serverLocator != null) {
            serverLocator.close();
            serverLocator = null;
         }

      }

      public ClientMessage createMessage(boolean durable) {
         checkSession();

         return session.createMessage(durable);
      }

      public void sendMessage(SimpleString address, ClientMessage message) {
         checkSession();
         if (producer == null) {
            throw new IllegalStateException("ClientProducer is null - has the InternalClient been started?");
         }

         try {
            producer.send(address, message);
         } catch (ActiveMQException amqEx) {
            throw new EmbeddedActiveMQResourceException(String.format("Failed to send message to %s",
                                                                      address.toString()),
                                                        amqEx);
         }
      }

      public ClientMessage receiveMessage(SimpleString address, long timeout, boolean browseOnly) {
         checkSession();

         EmbeddedActiveMQResourceException failureCause = null;

         try (ClientConsumer consumer = session.createConsumer(address, browseOnly)) {
            ClientMessage message = null;
            if (timeout > 0) {
               try {
                  message = consumer.receive(timeout);
               } catch (ActiveMQException amqEx) {
                  failureCause = new EmbeddedActiveMQResourceException(String.format("ClientConsumer.receive( timeout = %d ) for %s failed",
                                                                        timeout, address.toString()), amqEx);
                  throw failureCause;
               }
            } else if (timeout == 0) {
               try {
                  message = consumer.receiveImmediate();
               } catch (ActiveMQException amqEx) {
                  failureCause = new EmbeddedActiveMQResourceException(String.format("ClientConsumer.receiveImmediate() for %s failed",
                                                                       address.toString()), amqEx);
                  throw failureCause;
               }
            } else {
               try {
                  message = consumer.receive();
               } catch (ActiveMQException amqEx) {
                  failureCause = new EmbeddedActiveMQResourceException(String.format("ClientConsumer.receive() for %s failed",
                                                                       address.toString()), amqEx);
                  throw failureCause;
               }
            }

            if (message != null) {
               try {
                  message.acknowledge();
               } catch (ActiveMQException amqEx) {
                  failureCause = new EmbeddedActiveMQResourceException(String.format("ClientMessage.acknowledge() for %s from %s failed",
                                                                       message, address.toString()), amqEx);
                  throw failureCause;
               }
            }

            return message;
         } catch (ActiveMQException amqEx) {
            if (failureCause == null) {
               failureCause = new EmbeddedActiveMQResourceException(String.format("Failed to create consumer for %s",
                                                                    address.toString()), amqEx);
            }

            throw failureCause;
         }
      }

      void checkSession() {
         getInternalClient();
         if (session == null) {
            throw new IllegalStateException("ClientSession is null - has the InternalClient been started?");
         }
      }
   }
}
