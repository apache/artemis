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
package org.apache.activemq.artemis.tests.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.jms.Topic;
import javax.management.MBeanServer;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.registry.JndiBindingRegistry;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.server.config.impl.JMSConfigurationImpl;
import org.apache.activemq.artemis.jms.server.impl.JMSServerManagerImpl;
import org.apache.activemq.artemis.tests.unit.util.InVMNamingContext;
import org.junit.jupiter.api.BeforeEach;

public class JMSClusteredTestBase extends ActiveMQTestBase {

   protected MBeanServer mBeanServer1;

   protected ActiveMQServer server1;

   protected JMSServerManagerImpl jmsServer1;

   protected MBeanServer mBeanServer2;

   protected ActiveMQServer server2;

   protected JMSServerManagerImpl jmsServer2;

   protected ConnectionFactory cf1;

   protected ConnectionFactory cf2;

   protected InVMNamingContext context1;

   protected InVMNamingContext context2;

   protected static final int MAX_HOPS = 1;

   protected Queue createQueue(final String name) throws Exception {
      jmsServer2.createQueue(false, name, null, true, "/queue/" + name);
      jmsServer1.createQueue(false, name, null, true, "/queue/" + name);

      assertTrue(waitForBindings(server1, name, false, 1, 0, 10000));
      assertTrue(waitForBindings(server2, name, false, 1, 0, 10000));

      return (Queue) context1.lookup("/queue/" + name);
   }

   protected Topic createTopic(final String name) throws Exception {
      return createTopic(name, false);
   }

   protected Topic createTopic(final String name, boolean durable) throws Exception {
      jmsServer2.createTopic(durable, name, "/topic/" + name);
      jmsServer1.createTopic(durable, name, "/topic/" + name);

      return (Topic) context1.lookup("/topic/" + name);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      setupServer2();
      setupServer1();

      jmsServer1.start();
      jmsServer1.activated();
      waitForServerToStart(jmsServer1.getActiveMQServer());

      jmsServer2.start();
      jmsServer2.activated();
      waitForServerToStart(jmsServer2.getActiveMQServer());

      waitForTopology(jmsServer1.getActiveMQServer(), 2);

      waitForTopology(jmsServer2.getActiveMQServer(), 2);

      cf1 = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(InVMConnectorFactory.class.getName(), generateInVMParams(1)));
      cf2 = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(InVMConnectorFactory.class.getName(), generateInVMParams(2)));
   }

   private void setupServer2() throws Exception {
      Configuration configuration = createConfigServer(2, 1);

      JMSConfigurationImpl jmsconfig = new JMSConfigurationImpl();

      mBeanServer2 = createMBeanServer();
      server2 = addServer(ActiveMQServers.newActiveMQServer(configuration, mBeanServer2, enablePersistence()));
      jmsServer2 = new JMSServerManagerImpl(server2, jmsconfig);
      context2 = new InVMNamingContext();
      jmsServer2.setRegistry(new JndiBindingRegistry(context2));
   }

   private void setupServer1() throws Exception {
      Configuration configuration = createConfigServer(1, 2);

      JMSConfigurationImpl jmsconfig = new JMSConfigurationImpl();

      mBeanServer1 = createMBeanServer();
      server1 = addServer(ActiveMQServers.newActiveMQServer(configuration, mBeanServer1, enablePersistence()));
      jmsServer1 = new JMSServerManagerImpl(server1, jmsconfig);
      context1 = new InVMNamingContext();
      jmsServer1.setRegistry(new JndiBindingRegistry(context1));
   }

   protected boolean enablePersistence() {
      return false;
   }

   protected Configuration createConfigServer(final int source, final int destination) throws Exception {
      final String destinationLabel = "toServer" + destination;
      final String sourceLabel = "server" + source;

      Configuration configuration = createDefaultInVMConfig(source).setSecurityEnabled(false)
                                                                   .setJMXManagementEnabled(true)
                                                                   .setPersistenceEnabled(false)
                                                                   .addConnectorConfiguration(destinationLabel, new TransportConfiguration(InVMConnectorFactory.class.getName(), generateInVMParams(destination)))
                                                                   .addConnectorConfiguration(sourceLabel, new TransportConfiguration(InVMConnectorFactory.class.getName(), generateInVMParams(source)))
                                                                   .addClusterConfiguration(new ClusterConnectionConfiguration().setName(destinationLabel)
                                                                                                                                .setConnectorName(sourceLabel)
                                                                                                                                .setRetryInterval(250)
                                                                                                                                .setMaxHops(MAX_HOPS)
                                                                                                                                .setConfirmationWindowSize(1024)
                                                                                                                                .setMessageLoadBalancingType(MessageLoadBalancingType.ON_DEMAND)
                                                                                                                                .setStaticConnectors(new ArrayList<String>(List.of(destinationLabel))));

      configuration.getAddressSettings().put("#", new AddressSettings().setRedistributionDelay(0));

      return configuration;
   }
}
