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
package org.apache.activemq.artemis.api.jms;

import javax.jms.Queue;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.uri.ConnectionFactoryParser;
import org.apache.activemq.artemis.utils.ClassloadingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A utility class for creating Apache Artemis client-side JMS managed resources.
 */
public class ActiveMQJMSClient {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final boolean DEFAULT_ENABLE_1X_PREFIXES;


   static {

      String value1X = System.getProperty(ActiveMQJMSClient.class.getName() + ".enable1xPrefixes");

      if (value1X == null) {
         value1X = ClassloadingUtil.loadProperty(ActiveMQJMSClient.class.getClassLoader(), ActiveMQJMSClient.class.getName() + ".properties", "enable1xPrefixes");
      }

      boolean prefixes = false;


      if (value1X != null) {
         try {
            prefixes = Boolean.parseBoolean(value1X);
         } catch (Throwable e) {
            logger.warn("enable1xPrefixes config failure", e);
         }
      }
      DEFAULT_ENABLE_1X_PREFIXES = prefixes;
   }

   public static ActiveMQConnectionFactory createConnectionFactory(final String url, String name) throws Exception {
      ConnectionFactoryParser parser = new ConnectionFactoryParser();
      return parser.newObject(parser.expandURI(url), name);
   }

   /**
    * Creates an ActiveMQConnectionFactory that receives cluster topology updates from the cluster as servers leave or
    * join and new backups are appointed or removed.
    * <p>
    * The discoveryAddress and discoveryPort parameters in this method are used to listen for UDP broadcasts which
    * contain connection information for members of the cluster. The broadcasted connection information is simply used
    * to make an initial connection to the cluster, once that connection is made, up to date cluster topology
    * information is downloaded and automatically updated whenever the cluster topology changes. If the topology
    * includes backup servers that information is also propagated to the client so that it can know which server to
    * failover onto in case of server failure.
    *
    * @return the ActiveMQConnectionFactory
    */
   public static ActiveMQConnectionFactory createConnectionFactoryWithHA(final DiscoveryGroupConfiguration groupConfiguration,
                                                                         JMSFactoryType jmsFactoryType) {
      return jmsFactoryType.createConnectionFactoryWithHA(groupConfiguration);
   }

   /**
    * Create an ActiveMQConnectionFactory which creates session factories from a set of active servers, no HA backup
    * information is propagated to the client
    * <p>
    * The UDP address and port are used to listen for active servers in the cluster
    *
    * @return the ActiveMQConnectionFactory
    */
   public static ActiveMQConnectionFactory createConnectionFactoryWithoutHA(final DiscoveryGroupConfiguration groupConfiguration,
                                                                            JMSFactoryType jmsFactoryType) {
      return jmsFactoryType.createConnectionFactoryWithoutHA(groupConfiguration);
   }

   /**
    * Create an ActiveMQConnectionFactory which will receive cluster topology updates from the cluster as servers leave
    * or join and new backups are appointed or removed.
    * <p>
    * The initial list of servers supplied in this method is simply to make an initial connection to the cluster, once
    * that connection is made, up to date cluster topology information is downloaded and automatically updated whenever
    * the cluster topology changes. If the topology includes backup servers that information is also propagated to the
    * client so that it can know which server to failover onto in case of server failure.
    *
    * @param initialServers The initial set of servers used to make a connection to the cluster. Each one is tried in
    *                       turn until a successful connection is made. Once a connection is made, the cluster topology
    *                       is downloaded and the rest of the list is ignored.
    * @return the ActiveMQConnectionFactory
    */
   public static ActiveMQConnectionFactory createConnectionFactoryWithHA(JMSFactoryType jmsFactoryType,
                                                                         final TransportConfiguration... initialServers) {
      return jmsFactoryType.createConnectionFactoryWithHA(initialServers);
   }

   /**
    * Create an ActiveMQConnectionFactory which creates session factories using a static list of
    * transportConfigurations.
    * <p>
    * The ActiveMQConnectionFactory is not updated automatically as the cluster topology changes, and no HA backup
    * information is propagated to the client
    *
    * @return the ActiveMQConnectionFactory
    */
   public static ActiveMQConnectionFactory createConnectionFactoryWithoutHA(JMSFactoryType jmsFactoryType,
                                                                            final TransportConfiguration... transportConfigurations) {
      return jmsFactoryType.createConnectionFactoryWithoutHA(transportConfigurations);
   }

   /**
    * Creates a client-side representation of a JMS Topic.
    * <p>
    * This method is deprecated. Use {@link org.apache.activemq.artemis.jms.client.ActiveMQSession#createTopic(String)}
    * as that method will know the proper prefix used at the target server.
    *
    * @param name the name of the topic
    * @return The Topic
    */
   @Deprecated
   public static Topic createTopic(final String name) {
      if (DEFAULT_ENABLE_1X_PREFIXES) {
         return ActiveMQDestination.createTopic(PacketImpl.OLD_TOPIC_PREFIX + name, name);
      } else {
         return ActiveMQDestination.createTopic(name);
      }
   }

   /**
    * Creates a client-side representation of a JMS Queue.
    * <p>
    * This method is deprecated. Use {@link org.apache.activemq.artemis.jms.client.ActiveMQSession#createQueue(String)}
    * (String)} as that method will know the proper prefix used at the target server. *
    *
    * @param name the name of the queue
    * @return The Queue
    */
   @Deprecated
   public static Queue createQueue(final String name) {
      if (DEFAULT_ENABLE_1X_PREFIXES) {
         return ActiveMQDestination.createQueue(PacketImpl.OLD_QUEUE_PREFIX + name, name);
      } else {
         return ActiveMQDestination.createQueue(name);
      }
   }

   private ActiveMQJMSClient() {
      // Utility class
   }
}
