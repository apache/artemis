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
package org.apache.activemq.artemis.jms.tests.tools.container;

import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.naming.InitialContext;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.registry.JndiBindingRegistry;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.server.JMSServerManager;
import org.apache.activemq.artemis.jms.server.impl.JMSServerManagerImpl;
import org.apache.activemq.artemis.jms.tests.JmsTestLogger;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;

public class LocalTestServer implements Server, Runnable {

   private boolean started = false;

   private final HashMap<String, List<String>> allBindings = new HashMap<>();
   private JMSServerManagerImpl jmsServerManager;



   public static void setEnvironmentServerIndex(final int serverIndex) {
      System.setProperty(Constants.SERVER_INDEX_PROPERTY_NAME, Integer.toString(serverIndex));
   }

   public static void clearEnvironmentServerIndex() {
      System.getProperty(Constants.SERVER_INDEX_PROPERTY_NAME, null);
   }


   private final int serverIndex;


   public LocalTestServer() {
      super();

      serverIndex = 0;
   }

   // Server implementation ------------------------------------------------------------------------

   @Override
   public int getServerID() {
      return serverIndex;
   }

   @Override
   public synchronized void start(final HashMap<String, Object> configuration,
                                  final boolean clearJournal) throws Exception {
      if (isStarted()) {
         return;
      }

      if (clearJournal) {
         // Delete the Journal environment

         File dir = new File("target/data");

         boolean deleted = LocalTestServer.deleteDirectory(dir);

         JmsTestLogger.LOGGER.info("Deleted dir: " + dir.getAbsolutePath() + " deleted: " + deleted);
      }

      javax.management.MBeanServer beanServer = java.lang.management.ManagementFactory.getPlatformMBeanServer();
      FileConfiguration fileConfiguration = new FileConfiguration();
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), new SecurityConfiguration());
      securityManager.getConfiguration().addUser("guest", "guest");
      securityManager.getConfiguration().setDefaultUser("guest");
      securityManager.getConfiguration().addRole("guest", "guest");
      ActiveMQServerImpl activeMQServer = new ActiveMQServerImpl(fileConfiguration, beanServer, securityManager);
      jmsServerManager = new JMSServerManagerImpl(activeMQServer);
      System.setProperty(Constants.SERVER_INDEX_PROPERTY_NAME, "" + getServerID());
      jmsServerManager.setRegistry(new JndiBindingRegistry(getInitialContext()));

      FileDeploymentManager deploymentManager = new FileDeploymentManager();
      deploymentManager.addDeployable(fileConfiguration).readConfiguration();
      jmsServerManager.start();
      started = true;

   }

   private static boolean deleteDirectory(final File directory) {
      if (directory.isDirectory()) {
         String[] files = directory.list();

         for (int j = 0; j < files.length; j++) {
            if (!LocalTestServer.deleteDirectory(new File(directory, files[j]))) {
               return false;
            }
         }
      }

      return directory.delete();
   }

   @Override
   public synchronized boolean stop() throws Exception {
      jmsServerManager.stop();
      started = false;
      unbindAll();
      jmsServerManager = null;
      return true;
   }

   @Override
   public void ping() throws Exception {
      if (!isStarted()) {
         throw new RuntimeException("ok");
      }
   }

   @Override
   public synchronized void kill() throws Exception {
      stop();
   }

   @Override
   public synchronized boolean isStarted() throws Exception {
      return started;
   }

   @Override
   public synchronized void startServerPeer() throws Exception {
      System.setProperty(Constants.SERVER_INDEX_PROPERTY_NAME, "" + getServerID());
      getActiveMQServer().start();
   }

   @Override
   public synchronized void stopServerPeer() throws Exception {
      System.setProperty(Constants.SERVER_INDEX_PROPERTY_NAME, "" + getServerID());
      getActiveMQServer().stop();
      // also unbind everything
      unbindAll();
   }

   private void unbindAll() throws Exception {
      Collection<List<String>> bindings = allBindings.values();
      for (List<String> binding : bindings) {
         for (String s : binding) {
            getInitialContext().unbind(s);
         }
      }
   }

   /**
    * Only for in-VM use!
    */
   @Override
   public ActiveMQServer getServerPeer() {
      return getActiveMQServer();
   }

   @Override
   public void destroyQueue(final String name, final String jndiName) throws Exception {
      getJMSServerManager().destroyQueue(name);
   }

   @Override
   public void destroyTopic(final String name, final String jndiName) throws Exception {
      getJMSServerManager().destroyTopic(name);
   }

   @Override
   public void createQueue(final String name, final String jndiName) throws Exception {
      getJMSServerManager().createQueue(true, name, null, true, "/queue/" + (jndiName != null ? jndiName : name));
   }

   @Override
   public void createTopic(final String name, final String jndiName) throws Exception {
      getJMSServerManager().createTopic(true, name, "/topic/" + (jndiName != null ? jndiName : name));
   }

   @Override
   public void deployConnectionFactory(final String clientId,
                                       final String objectName,
                                       final String... jndiBindings) throws Exception {
      deployConnectionFactory(clientId, JMSFactoryType.CF, objectName, -1, -1, -1, -1, false, false, -1, false, jndiBindings);
   }

   @Override
   public void deployConnectionFactory(final String objectName,
                                       final int consumerWindowSize,
                                       final String... jndiBindings) throws Exception {
      deployConnectionFactory(null, JMSFactoryType.CF, objectName, consumerWindowSize, -1, -1, -1, false, false, -1, false, jndiBindings);
   }

   @Override
   public void deployConnectionFactory(final String objectName, final String... jndiBindings) throws Exception {
      deployConnectionFactory(null, JMSFactoryType.CF, objectName, -1, -1, -1, -1, false, false, -1, false, jndiBindings);
   }

   @Override
   public void deployConnectionFactory(final String objectName,
                                       JMSFactoryType type,
                                       final String... jndiBindings) throws Exception {
      deployConnectionFactory(null, type, objectName, -1, -1, -1, -1, false, false, -1, false, jndiBindings);
   }

   @Override
   public void deployConnectionFactory(final String objectName,
                                       final int prefetchSize,
                                       final int defaultTempQueueFullSize,
                                       final int defaultTempQueuePageSize,
                                       final int defaultTempQueueDownCacheSize,
                                       final String... jndiBindings) throws Exception {
      this.deployConnectionFactory(null, JMSFactoryType.CF, objectName, prefetchSize, defaultTempQueueFullSize, defaultTempQueuePageSize, defaultTempQueueDownCacheSize, false, false, -1, false, jndiBindings);
   }

   @Override
   public void deployConnectionFactory(final String objectName,
                                       final boolean supportsFailover,
                                       final boolean supportsLoadBalancing,
                                       final String... jndiBindings) throws Exception {
      this.deployConnectionFactory(null, JMSFactoryType.CF, objectName, -1, -1, -1, -1, supportsFailover, supportsLoadBalancing, -1, false, jndiBindings);
   }

   @Override
   public void deployConnectionFactory(final String clientId,
                                       final JMSFactoryType type,
                                       final String objectName,
                                       final int prefetchSize,
                                       final int defaultTempQueueFullSize,
                                       final int defaultTempQueuePageSize,
                                       final int defaultTempQueueDownCacheSize,
                                       final boolean supportsFailover,
                                       final boolean supportsLoadBalancing,
                                       final int dupsOkBatchSize,
                                       final boolean blockOnAcknowledge,
                                       final String... jndiBindings) throws Exception {
      List<TransportConfiguration> connectorConfigs = new ArrayList<>();
      connectorConfigs.add(new TransportConfiguration(NettyConnectorFactory.class.getName()));

      ArrayList<String> connectors = new ArrayList<>();
      connectors.add("netty");

      getJMSServerManager().createConnectionFactory(objectName, false, type, connectors, clientId, ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD, ActiveMQClient.DEFAULT_CONNECTION_TTL, ActiveMQClient.DEFAULT_CALL_TIMEOUT, ActiveMQClient.DEFAULT_CALL_FAILOVER_TIMEOUT, ActiveMQClient.DEFAULT_CACHE_LARGE_MESSAGE_CLIENT, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, ActiveMQClient.DEFAULT_COMPRESS_LARGE_MESSAGES, prefetchSize, ActiveMQClient.DEFAULT_CONSUMER_MAX_RATE, ActiveMQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_MAX_RATE, blockOnAcknowledge, true, true, ActiveMQClient.DEFAULT_AUTO_GROUP, ActiveMQClient.DEFAULT_PRE_ACKNOWLEDGE, ActiveMQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, dupsOkBatchSize, ActiveMQClient.DEFAULT_USE_GLOBAL_POOLS, ActiveMQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_RETRY_INTERVAL, ActiveMQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER, ActiveMQClient.DEFAULT_MAX_RETRY_INTERVAL, ActiveMQClient.DEFAULT_RECONNECT_ATTEMPTS, ActiveMQClient.DEFAULT_FAILOVER_ON_INITIAL_CONNECTION, null, jndiBindings);
   }

   @Override
   public void undeployConnectionFactory(final String objectName) throws Exception {
      getJMSServerManager().destroyConnectionFactory(objectName);
   }

   @Override
   public void configureSecurityForDestination(final String destName,
                                               final boolean isQueue,
                                               final Set<Role> roles) throws Exception {
      if (roles != null) {
         getActiveMQServer().getSecurityRepository().addMatch(destName, roles);
      } else {
         getActiveMQServer().getSecurityRepository().removeMatch(destName);
      }
   }

   @Override
   public ActiveMQServer getActiveMQServer() {
      return jmsServerManager.getActiveMQServer();
   }

   @Override
   public JMSServerManager getJMSServerManager() {
      return jmsServerManager;
   }

   @Override
   public InitialContext getInitialContext() throws Exception {
      Properties props = new Properties();
      props.setProperty("java.naming.factory.initial", "org.apache.activemq.artemis.jms.tests.tools.container.InVMInitialContextFactory");
      props.setProperty(Constants.SERVER_INDEX_PROPERTY_NAME, "" + getServerID());
      return new InitialContext(props);
   }

   @Override
   public void run() {
      //  bootstrap.run();

      started = true;
   }

   @Override
   public Long getMessageCountForQueue(final String queueName) throws Exception {
      QueueControl queue = (QueueControl) getActiveMQServer().getManagementService().getResource("queue." + queueName);
      if (queue != null) {
         queue.flushExecutor();
         return queue.getMessageCount();
      } else {
         return -1L;
      }
   }

   @Override
   public void removeAllMessages(final String queueName) throws Exception {
      QueueControl queue = (QueueControl) getActiveMQServer().getManagementService().getResource(ResourceNames.QUEUE + queueName);
      queue.removeMessages(null);
   }

   @Override
   public List<String> listAllSubscribersForTopic(final String s) throws Exception {
      ObjectName objectName = ObjectNameBuilder.DEFAULT.getAddressObjectName(new SimpleString(s));
      AddressControl topic = MBeanServerInvocationHandler.newProxyInstance(ManagementFactory.getPlatformMBeanServer(), objectName, AddressControl.class, false);
      Object[] subInfos = topic.getQueueNames();
      List<String> subs = new ArrayList<>();
      for (Object o : subInfos) {
         subs.add( ((String) o).split("\\.")[1]);
      }
      return subs;
   }

   @Override
   public Set<Role> getSecurityConfig() throws Exception {
      return getActiveMQServer().getSecurityRepository().getMatch("*");
   }

   @Override
   public void setSecurityConfig(final Set<Role> defConfig) throws Exception {
      getActiveMQServer().getSecurityRepository().removeMatch("#");
      getActiveMQServer().getSecurityRepository().addMatch("#", defConfig);
   }

}
