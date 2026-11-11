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
package org.apache.activemq.artemis.tests.integration.jms.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.jms.Topic;
import javax.naming.Context;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.registry.JndiBindingRegistry;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.server.JMSServerManager;
import org.apache.activemq.artemis.jms.server.config.ConnectionFactoryConfiguration;
import org.apache.activemq.artemis.jms.server.config.JMSConfiguration;
import org.apache.activemq.artemis.jms.server.config.TopicConfiguration;
import org.apache.activemq.artemis.jms.server.config.impl.ConnectionFactoryConfigurationImpl;
import org.apache.activemq.artemis.jms.server.config.impl.JMSConfigurationImpl;
import org.apache.activemq.artemis.jms.server.config.impl.JMSQueueConfigurationImpl;
import org.apache.activemq.artemis.jms.server.config.impl.TopicConfigurationImpl;
import org.apache.activemq.artemis.jms.server.impl.JMSServerManagerImpl;
import org.apache.activemq.artemis.tests.unit.util.InVMNamingContext;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.transport.netty.NettyTransport;
import org.apache.activemq.transport.netty.NettyTransportFactory;
import org.apache.activemq.transport.netty.NettyTransportListener;
import org.junit.jupiter.api.Test;

public class JMSConfigurationTest extends ActiveMQTestBase {

   @Test
   public void testSetupJMSConfiguration() throws Exception {
      Context context = new InVMNamingContext();

      ActiveMQServer coreServer = new ActiveMQServerImpl(createDefaultInVMConfig());

      JMSConfiguration jmsConfiguration = new JMSConfigurationImpl();
      TransportConfiguration connectorConfig = new TransportConfiguration(InVMConnectorFactory.class.getName());
      List<TransportConfiguration> transportConfigs = new ArrayList<>();
      transportConfigs.add(connectorConfig);

      ConnectionFactoryConfiguration cfConfig = new ConnectionFactoryConfigurationImpl().setName(RandomUtil.randomUUIDString()).setConnectorNames(registerConnectors(coreServer, transportConfigs)).setBindings("/cf/binding1", "/cf/binding2");

      jmsConfiguration.getConnectionFactoryConfigurations().add(cfConfig);
      JMSQueueConfigurationImpl queueConfig = new JMSQueueConfigurationImpl().setName(RandomUtil.randomUUIDString()).setDurable(false).setBindings("/queue/binding1", "/queue/binding2");
      jmsConfiguration.getQueueConfigurations().add(queueConfig);
      TopicConfiguration topicConfig = new TopicConfigurationImpl().setName(RandomUtil.randomUUIDString()).setBindings("/topic/binding1", "/topic/binding2");
      jmsConfiguration.getTopicConfigurations().add(topicConfig);

      JMSServerManager server = new JMSServerManagerImpl(coreServer, jmsConfiguration);

      server.setRegistry(new JndiBindingRegistry(context));
      server.start();

      for (String binding : cfConfig.getBindings()) {
         Object o = context.lookup(binding);
         assertNotNull(o);
         assertInstanceOf(ConnectionFactory.class, o);
         ConnectionFactory cf = (ConnectionFactory) o;
         Connection connection = cf.createConnection();
         connection.close();
      }

      for (String binding : queueConfig.getBindings()) {
         Object o = context.lookup(binding);
         assertNotNull(o);
         assertInstanceOf(Queue.class, o);
         Queue queue = (Queue) o;
         assertEquals(queueConfig.getName(), queue.getQueueName());
      }

      for (String binding : topicConfig.getBindings()) {
         Object o = context.lookup(binding);
         assertNotNull(o);
         assertInstanceOf(Topic.class, o);
         Topic topic = (Topic) o;
         assertEquals(topicConfig.getName(), topic.getTopicName());
      }

      server.stop();
   }

   @Test
   public void testHandshakeTimeoutWithValueSet() throws Exception {
      final int HANDSHAKE_TIMEOUT = 5;

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultConfig(false)
                                                                             .clearAcceptorConfigurations()
                                                                             .addAcceptorConfiguration("netty", "tcp://localhost:61616?handshake-timeout=5")));
      server.start();

      TransportConfiguration tc = server.getConfiguration().getAcceptorConfigurations().iterator().next();
      String host = (String) tc.getParams().get(TransportConstants.HOST_PROP_NAME);
      String port = (String) tc.getParams().get(TransportConstants.PORT_PROP_NAME);
      Object handshakeTimeout = tc.getParams().get(TransportConstants.HANDSHAKE_TIMEOUT);
      assertNotNull(handshakeTimeout);
      assertEquals(HANDSHAKE_TIMEOUT, Integer.parseInt(handshakeTimeout.toString()));

      NettyTransport transport = NettyTransportFactory.createTransport(new URI("tcp://" + host + ":" + port));
      transport.setTransportListener(new NettyTransportListener() {
         @Override
         public void onData(ByteBuf incoming) {

         }

         @Override
         public void onTransportClosed() {
         }

         @Override
         public void onTransportError(Throwable cause) {
         }

      });

      try {
         transport.connect();
         assertTrue(Wait.waitFor(() -> !transport.isConnected(), TimeUnit.SECONDS.toMillis(HANDSHAKE_TIMEOUT + 1)), "Connection should be closed now");
      } finally {
         transport.close();
         server.stop();
      }
   }

   @Test
   public void testSslHandshakeTimeoutWithValueSet() throws Exception {
      final int SSL_HANDSHAKE_TIMEOUT = 20;

      String url = "tcp://127.0.0.1:61616?sslEnabled=true;keyStorePath=server-ca-truststore.p12;keyStorePassword=securepass;sslHandshakeTimeout=20;handshake-timeout=0";
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultConfig(false)
                                                                             .clearAcceptorConfigurations()
                                                                             .addAcceptorConfiguration("netty", url)));
      server.start();

      TransportConfiguration tc = server.getConfiguration().getAcceptorConfigurations().iterator().next();
      String host = (String) tc.getParams().get(TransportConstants.HOST_PROP_NAME);
      String port = (String) tc.getParams().get(TransportConstants.PORT_PROP_NAME);
      Object sslHandshakeTimeout = tc.getParams().get(TransportConstants.SSL_HANDSHAKE_TIMEOUT);
      assertNotNull(sslHandshakeTimeout);
      assertEquals(SSL_HANDSHAKE_TIMEOUT, Integer.parseInt(sslHandshakeTimeout.toString()));

      NettyTransport transport = NettyTransportFactory.createTransport(new URI("tcp://" + host + ":" + port));
      transport.setTransportListener(new NettyTransportListener() {
         @Override
         public void onData(ByteBuf incoming) {

         }

         @Override
         public void onTransportClosed() {
         }

         @Override
         public void onTransportError(Throwable cause) {
         }

      });

      try {
         transport.connect();
         assertTrue(Wait.waitFor(() -> !transport.isConnected(), TimeUnit.SECONDS.toMillis(SSL_HANDSHAKE_TIMEOUT + 1)), "Connection should be closed now");
      } finally {
         transport.close();
         server.stop();
      }
   }
}
