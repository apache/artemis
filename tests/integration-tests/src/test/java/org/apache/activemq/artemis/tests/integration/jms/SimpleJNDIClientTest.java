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
package org.apache.activemq.artemis.tests.integration.jms;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSSecurityException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.BroadcastEndpoint;
import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.JGroupsFileBroadcastEndpoint;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SimpleJNDIClientTest extends ActiveMQTestBase {

   private final String groupAddress = getUDPDiscoveryAddress();

   private final int groupPort = getUDPDiscoveryPort();

   private ActiveMQServer liveService;

   private TransportConfiguration liveTC;

   private List<ActiveMQConnectionFactory> factories = new LinkedList<>();

   // adding connection factories that need to be closed
   // this is because UDP connection factories could hold a UDP thread running if they are not closed
   private void addCF(ConnectionFactory factory) {
      if (factory instanceof ActiveMQConnectionFactory connectionFactory) {
         factories.add(connectionFactory);
      }
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      factories.forEach(cf -> {
         cf.close();
      });
      super.tearDown();
   }

   @Test
   public void testMultipleConnectionFactories() throws Exception {
      Hashtable<String, Object> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.VmConnectionFactory", "vm://0");
      props.put("connectionFactory.TCPConnectionFactory", "tcp://localhost:61616");
      props.put("connectionFactory.UDPConnectionFactory", "udp://" + getUDPDiscoveryAddress() + ":" + getUDPDiscoveryPort());
      props.put("connectionFactory.JGroupsConnectionFactory", "jgroups://mychannelid?file=test-jgroups-file_ping.xml");
      Context ctx = new InitialContext(props);
      ctx.lookup("VmConnectionFactory");
      ctx.lookup("TCPConnectionFactory");
      ctx.lookup("UDPConnectionFactory");
      ctx.lookup("JGroupsConnectionFactory");
      ctx.close();
   }

   @Test
   public void testVMCF0() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.ConnectionFactory", "vm://0");
      Context ctx = new InitialContext(props);

      ConnectionFactory connectionFactory = (ConnectionFactory) ctx.lookup("ConnectionFactory");

      connectionFactory.createConnection().close();
      ctx.close();
   }

   @Test
   public void testEmptyConnectionFactoryString() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.ConnectionFactory", "vm://0");

      //IIB v10 assumes this property is mandatory and sets it to an empty string when not specified
      props.put("java.naming.provider.url", "");
      Context ctx = new InitialContext(props);//Must not throw an exception
      ctx.close();
   }

   @Test
   public void testVMCF1() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.ConnectionFactory", "vm://1");
      Context ctx = new InitialContext(props);

      ConnectionFactory connectionFactory = (ConnectionFactory) ctx.lookup("ConnectionFactory");

      connectionFactory.createConnection().close();
   }

   @Test
   public void testXACF() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.myConnectionFactory", "vm://0?type=XA_CF");
      Context ctx = new InitialContext(props);

      ActiveMQConnectionFactory connectionFactory = (ActiveMQConnectionFactory) ctx.lookup("myConnectionFactory");

      assertEquals(JMSFactoryType.XA_CF.intValue(), connectionFactory.getFactoryType());
   }

   @Test
   public void testQueueCF() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.myConnectionFactory", "vm://0?type=QUEUE_CF");
      Context ctx = new InitialContext(props);

      ActiveMQConnectionFactory connectionFactory = (ActiveMQConnectionFactory) ctx.lookup("myConnectionFactory");

      assertEquals(JMSFactoryType.QUEUE_CF.intValue(), connectionFactory.getFactoryType());
   }

   @Test
   public void testQueueXACF() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.myConnectionFactory", "vm://0?type=QUEUE_XA_CF");
      Context ctx = new InitialContext(props);

      ActiveMQConnectionFactory connectionFactory = (ActiveMQConnectionFactory) ctx.lookup("myConnectionFactory");

      assertEquals(JMSFactoryType.QUEUE_XA_CF.intValue(), connectionFactory.getFactoryType());
   }

   @Test
   public void testTopicCF() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.myConnectionFactory", "vm://0?type=TOPIC_CF");
      Context ctx = new InitialContext(props);

      ActiveMQConnectionFactory connectionFactory = (ActiveMQConnectionFactory) ctx.lookup("myConnectionFactory");

      assertEquals(JMSFactoryType.TOPIC_CF.intValue(), connectionFactory.getFactoryType());
   }

   @Test
   public void testTopicXACF() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.myConnectionFactory", "vm://0?type=TOPIC_XA_CF");
      Context ctx = new InitialContext(props);

      ActiveMQConnectionFactory connectionFactory = (ActiveMQConnectionFactory) ctx.lookup("myConnectionFactory");

      assertEquals(JMSFactoryType.TOPIC_XA_CF.intValue(), connectionFactory.getFactoryType());
   }

   @Test
   public void testRemoteCFWithTCP() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.myConnectionFactory", "tcp://127.0.0.1:61616");
      Context ctx = new InitialContext(props);

      ConnectionFactory connectionFactory = (ConnectionFactory) ctx.lookup("myConnectionFactory");

      connectionFactory.createConnection().close();
   }

   @Test
   public void testRemoteCFWithTCPandHA() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.myConnectionFactory", "tcp://127.0.0.1:61616?ha=true");
      Context ctx = new InitialContext(props);

      ActiveMQConnectionFactory cf = (ActiveMQConnectionFactory) ctx.lookup("myConnectionFactory");

      assertTrue(cf.isHA());
   }

   @Test
   public void testRemoteCFWithJGroups() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.myConnectionFactory", "jgroups://mychannelid?file=test-jgroups-file_ping.xml");
      Context ctx = new InitialContext(props);

      ActiveMQConnectionFactory connectionFactory = (ActiveMQConnectionFactory) ctx.lookup("myConnectionFactory");
      connectionFactory.getDiscoveryGroupConfiguration().getBroadcastEndpointFactory().createBroadcastEndpoint().close(false);
   }

   @Test
   public void testRemoteCFWithJgroupsWithTransportConfigFile() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory.class.getCanonicalName());
      props.put("connectionFactory.myConnectionFactory", "jgroups://testChannelName?file=test-jgroups-file_ping.xml&" +
         ActiveMQInitialContextFactory.REFRESH_TIMEOUT + "=5000&" +
         ActiveMQInitialContextFactory.DISCOVERY_INITIAL_WAIT_TIMEOUT + "=6000");
      Context ctx = new InitialContext(props);

      ActiveMQConnectionFactory cf = (ActiveMQConnectionFactory) ctx.lookup("myConnectionFactory");

      DiscoveryGroupConfiguration discoveryGroupConfiguration = cf.getDiscoveryGroupConfiguration();
      assertEquals(5000, discoveryGroupConfiguration.getRefreshTimeout());
      assertEquals(6000, discoveryGroupConfiguration.getDiscoveryInitialWaitTimeout());

      BroadcastEndpoint broadcastEndpoint = cf.getDiscoveryGroupConfiguration().getBroadcastEndpointFactory().createBroadcastEndpoint();
      assertInstanceOf(JGroupsFileBroadcastEndpoint.class, broadcastEndpoint);
      broadcastEndpoint.close(false);
   }

   @Test
   public void testRemoteCFWithUDP() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.myConnectionFactory", "udp://" + getUDPDiscoveryAddress() + ":" + getUDPDiscoveryPort());
      Context ctx = new InitialContext(props);

      ConnectionFactory connectionFactory = (ConnectionFactory) ctx.lookup("myConnectionFactory");
      addCF(connectionFactory);

      connectionFactory.createConnection().close();
      ctx.close();
   }

   @Test
   public void testRemoteCFWithUDPWithTransportConfig() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, ActiveMQInitialContextFactory.class.getCanonicalName());
      props.put("connectionFactory.myConnectionFactory", "udp://" + getUDPDiscoveryAddress() + ":" + getUDPDiscoveryPort() + "?" +
         TransportConstants.LOCAL_ADDRESS_PROP_NAME + "=Server1&" +
         TransportConstants.LOCAL_PORT_PROP_NAME + "=1198&" +
         ActiveMQInitialContextFactory.REFRESH_TIMEOUT + "=5000&" +
         ActiveMQInitialContextFactory.DISCOVERY_INITIAL_WAIT_TIMEOUT + "=6000");
      Context ctx = new InitialContext(props);

      ActiveMQConnectionFactory cf = (ActiveMQConnectionFactory) ctx.lookup("myConnectionFactory");
      addCF(cf);

      DiscoveryGroupConfiguration discoveryGroupConfiguration = cf.getDiscoveryGroupConfiguration();
      assertEquals(5000, discoveryGroupConfiguration.getRefreshTimeout());
      assertEquals(6000, discoveryGroupConfiguration.getDiscoveryInitialWaitTimeout());

      UDPBroadcastEndpointFactory udpBroadcastEndpointFactory = (UDPBroadcastEndpointFactory) discoveryGroupConfiguration.getBroadcastEndpointFactory();
      //these 2 are transient so are ignored
      assertNotEquals("Server1", udpBroadcastEndpointFactory.getLocalBindAddress());
      assertNotEquals(1198, udpBroadcastEndpointFactory.getLocalBindPort());
      assertEquals(getUDPDiscoveryAddress(), udpBroadcastEndpointFactory.getGroupAddress());
      assertEquals(getUDPDiscoveryPort(), udpBroadcastEndpointFactory.getGroupPort());
      ctx.close();
   }

   @Test
   public void testRemoteCFWithMultipleHosts() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.myConnectionFactory", "tcp://127.0.0.1:61616/httpEnabled=true&foo=bar,tcp://127.0.0.2:61617?httpEnabled=false?clientID=myClientID");
      Context ctx = new InitialContext(props);

      ConnectionFactory connectionFactory = (ConnectionFactory) ctx.lookup("myConnectionFactory");

      connectionFactory.createConnection().close();
      ctx.close();
   }

   @Test
   public void testRemoteCFWithTransportConfig() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.myConnectionFactory", "tcp://127.0.0.1:61616?" +
         TransportConstants.SSL_ENABLED_PROP_NAME + "=mySSLEnabledPropValue&" +
         TransportConstants.HTTP_ENABLED_PROP_NAME + "=myHTTPEnabledPropValue&" +
         TransportConstants.HTTP_CLIENT_IDLE_PROP_NAME + "=myHTTPClientIdlePropValue&" +
         TransportConstants.HTTP_CLIENT_IDLE_SCAN_PERIOD + "=myHTTPClientIdleScanPeriodValue&" +
         TransportConstants.HTTP_REQUIRES_SESSION_ID + "=myHTTPRequiresSessionIDValue&" +
         TransportConstants.HTTP_UPGRADE_ENABLED_PROP_NAME + "=myHTTPUpgradeEnabledPropValue&" +
         TransportConstants.HTTP_UPGRADE_ENDPOINT_PROP_NAME + "=myHTTPUpgradeEndpointPropValue&" +
         TransportConstants.USE_SERVLET_PROP_NAME + "=myUseServletPropValue&" +
         TransportConstants.SERVLET_PATH + "=myServletPathValue&" +
         TransportConstants.USE_NIO_PROP_NAME + "=myUseNIOPropValue&" +
         TransportConstants.USE_NIO_GLOBAL_WORKER_POOL_PROP_NAME + "=myUseNIOGlobalWorkerPoolPropValue&" +
         TransportConstants.LOCAL_ADDRESS_PROP_NAME + "=myLocalAddressPropValue&" +
         TransportConstants.LOCAL_PORT_PROP_NAME + "=myLocalPortPropValue&" +
         TransportConstants.KEYSTORE_PROVIDER_PROP_NAME + "=myKeystoreProviderPropValue&" +
         TransportConstants.KEYSTORE_TYPE_PROP_NAME + "=myKeystoreTypePropValue&" +
         TransportConstants.KEYSTORE_PATH_PROP_NAME + "=myKeystorePathPropValue&" +
         TransportConstants.KEYSTORE_PASSWORD_PROP_NAME + "=myKeystorePasswordPropValue&" +
         TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME + "=myTruststoreProviderPropValue&" +
         TransportConstants.TRUSTSTORE_TYPE_PROP_NAME + "=myTruststoreTypePropValue&" +
         TransportConstants.TRUSTSTORE_PATH_PROP_NAME + "=myTruststorePathPropValue&" +
         TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME + "=myTruststorePasswordPropValue&" +
         TransportConstants.ENABLED_CIPHER_SUITES_PROP_NAME + "=myEnabledCipherSuitesPropValue&" +
         TransportConstants.ENABLED_PROTOCOLS_PROP_NAME + "=myEnabledProtocolsPropValue&" +
         TransportConstants.TCP_NODELAY_PROPNAME + "=myTCPNoDelayPropValue&" +
         TransportConstants.TCP_SENDBUFFER_SIZE_PROPNAME + "=myTCPSendbufferSizePropValue&" +
         TransportConstants.TCP_RECEIVEBUFFER_SIZE_PROPNAME + "=myTCPReceivebufferSizePropValue&" +
         TransportConstants.NIO_REMOTING_THREADS_PROPNAME + "=myNIORemotingThreadsPropValue&" +
         TransportConstants.BATCH_DELAY + "=myBatchDelay&" +
         ActiveMQDefaultConfiguration.getPropMaskPassword() + "=myPropMaskPassword&" +
         ActiveMQDefaultConfiguration.getPropPasswordCodec() + "=myPropPasswordCodec&" +
         TransportConstants.NETTY_CONNECT_TIMEOUT + "=myNettyConnectTimeout&");
      Context ctx = new InitialContext(props);

      ActiveMQConnectionFactory cf = (ActiveMQConnectionFactory) ctx.lookup("myConnectionFactory");

      Map<String, Object> parametersFromJNDI = cf.getServerLocator().getStaticTransportConfigurations()[0].getParams();

      assertEquals("mySSLEnabledPropValue", parametersFromJNDI.get(TransportConstants.SSL_ENABLED_PROP_NAME));
      assertEquals("myHTTPEnabledPropValue", parametersFromJNDI.get(TransportConstants.HTTP_ENABLED_PROP_NAME));
      assertEquals("myHTTPClientIdlePropValue", parametersFromJNDI.get(TransportConstants.HTTP_CLIENT_IDLE_PROP_NAME));
      assertEquals("myHTTPClientIdleScanPeriodValue", parametersFromJNDI.get(TransportConstants.HTTP_CLIENT_IDLE_SCAN_PERIOD));
      assertEquals("myHTTPRequiresSessionIDValue", parametersFromJNDI.get(TransportConstants.HTTP_REQUIRES_SESSION_ID));
      assertEquals("myHTTPUpgradeEnabledPropValue", parametersFromJNDI.get(TransportConstants.HTTP_UPGRADE_ENABLED_PROP_NAME));
      assertEquals("myHTTPUpgradeEndpointPropValue", parametersFromJNDI.get(TransportConstants.HTTP_UPGRADE_ENDPOINT_PROP_NAME));
      assertEquals("myUseServletPropValue", parametersFromJNDI.get(TransportConstants.USE_SERVLET_PROP_NAME));
      assertEquals("myServletPathValue", parametersFromJNDI.get(TransportConstants.SERVLET_PATH));
      assertEquals("myUseNIOPropValue", parametersFromJNDI.get(TransportConstants.USE_NIO_PROP_NAME));
      assertEquals("myUseNIOGlobalWorkerPoolPropValue", parametersFromJNDI.get(TransportConstants.USE_NIO_GLOBAL_WORKER_POOL_PROP_NAME));
      assertEquals("myLocalAddressPropValue", parametersFromJNDI.get(TransportConstants.LOCAL_ADDRESS_PROP_NAME));
      assertEquals("myLocalPortPropValue", parametersFromJNDI.get(TransportConstants.LOCAL_PORT_PROP_NAME));
      assertEquals("myKeystoreProviderPropValue", parametersFromJNDI.get(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME));
      assertEquals("myKeystoreTypePropValue", parametersFromJNDI.get(TransportConstants.KEYSTORE_TYPE_PROP_NAME));
      assertEquals("myKeystorePathPropValue", parametersFromJNDI.get(TransportConstants.KEYSTORE_PATH_PROP_NAME));
      assertEquals("myKeystorePasswordPropValue", parametersFromJNDI.get(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME));
      assertEquals("myTruststoreProviderPropValue", parametersFromJNDI.get(TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME));
      assertEquals("myTruststoreTypePropValue", parametersFromJNDI.get(TransportConstants.TRUSTSTORE_TYPE_PROP_NAME));
      assertEquals("myTruststorePathPropValue", parametersFromJNDI.get(TransportConstants.TRUSTSTORE_PATH_PROP_NAME));
      assertEquals("myTruststorePasswordPropValue", parametersFromJNDI.get(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME));
      assertEquals("myEnabledCipherSuitesPropValue", parametersFromJNDI.get(TransportConstants.ENABLED_CIPHER_SUITES_PROP_NAME));
      assertEquals("myEnabledProtocolsPropValue", parametersFromJNDI.get(TransportConstants.ENABLED_PROTOCOLS_PROP_NAME));
      assertEquals("myTCPNoDelayPropValue", parametersFromJNDI.get(TransportConstants.TCP_NODELAY_PROPNAME));
      assertEquals("myTCPSendbufferSizePropValue", parametersFromJNDI.get(TransportConstants.TCP_SENDBUFFER_SIZE_PROPNAME));
      assertEquals("myTCPReceivebufferSizePropValue", parametersFromJNDI.get(TransportConstants.TCP_RECEIVEBUFFER_SIZE_PROPNAME));
      assertEquals("myNIORemotingThreadsPropValue", parametersFromJNDI.get(TransportConstants.NIO_REMOTING_THREADS_PROPNAME));
      assertEquals("myBatchDelay", parametersFromJNDI.get(TransportConstants.BATCH_DELAY));
      assertEquals("myPropMaskPassword", parametersFromJNDI.get(ActiveMQDefaultConfiguration.getPropMaskPassword()));
      assertEquals("myPropPasswordCodec", parametersFromJNDI.get(ActiveMQDefaultConfiguration.getPropPasswordCodec()));
      assertEquals("myNettyConnectTimeout", parametersFromJNDI.get(TransportConstants.NETTY_CONNECT_TIMEOUT));
      ctx.close();
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      enableDiscoveryForTest();
      super.setUp();

      startServer();
   }

   private void startServer() throws Exception {
      liveTC = new TransportConfiguration(INVM_CONNECTOR_FACTORY);
      Map<String, TransportConfiguration> connectors = new HashMap<>();
      connectors.put(liveTC.getName(), liveTC);
      List<String> connectorNames = new ArrayList<>();
      connectorNames.add(liveTC.getName());

      Map<String, Object> params = new HashMap<>();
      params.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, 1);

      Configuration primaryConf = createBasicConfig().addAcceptorConfiguration(new TransportConfiguration(INVM_ACCEPTOR_FACTORY)).addAcceptorConfiguration(new TransportConfiguration(INVM_ACCEPTOR_FACTORY, params)).addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY)).setConnectorConfigurations(connectors).setHAPolicyConfiguration(new SharedStorePrimaryPolicyConfiguration()).setSecurityEnabled(true);

      final long broadcastPeriod = 250;

      final String bcGroupName = "bc1";

      final int localBindPort = 5432;

      BroadcastGroupConfiguration bcConfig1 = new BroadcastGroupConfiguration().setName(bcGroupName).setBroadcastPeriod(broadcastPeriod).setConnectorInfos(connectorNames).setEndpointFactory(new UDPBroadcastEndpointFactory().setGroupAddress(groupAddress).setGroupPort(groupPort).setLocalBindPort(localBindPort));

      List<BroadcastGroupConfiguration> bcConfigs1 = new ArrayList<>();
      bcConfigs1.add(bcConfig1);
      primaryConf.setBroadcastGroupConfigurations(bcConfigs1);

      liveService = addServer(ActiveMQServers.newActiveMQServer(primaryConf, false));
      ((ActiveMQJAASSecurityManager) liveService.getSecurityManager()).getConfiguration().addUser("guest", "guest");
      ((ActiveMQJAASSecurityManager) liveService.getSecurityManager()).getConfiguration().setDefaultUser("guest");
      liveService.start();
   }

   @Test
   public void testQueue() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("queue.myQueue", "myQueue");
      props.put("queue.queues/myQueue", "myQueue");
      Context ctx = new InitialContext(props);

      Destination destination = (Destination) ctx.lookup("myQueue");
      assertInstanceOf(Queue.class, destination);

      destination = (Destination) ctx.lookup("queues/myQueue");
      assertInstanceOf(Queue.class, destination);
   }

   @Test
   public void testQueueFQQN() throws Exception {
      final String QUEUE = "myQueue";
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("queue.myQueue", "myAddress::" + QUEUE);
      Context ctx = new InitialContext(props);
      liveService.getSecurityStore().setSecurityEnabled(false);

      Destination destination = (Destination) ctx.lookup(QUEUE);
      assertInstanceOf(Queue.class, destination);
      ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
      try (Connection connection = connectionFactory.createConnection()) {
         Session session = connection.createSession();
         MessageProducer producer = session.createProducer(destination);
         producer.send(session.createMessage());
         Wait.assertTrue(() -> liveService.locateQueue(QUEUE).getMessageCount() == 1, 2000, 100);
         MessageConsumer consumer = session.createConsumer(destination);
         connection.start();
         assertNotNull(consumer.receiveNoWait());
      }
   }

   @Test
   public void testDynamicQueue() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      Context ctx = new InitialContext(props);

      Destination destination = (Destination) ctx.lookup("dynamicQueues/myQueue");
      assertInstanceOf(Queue.class, destination);
   }

   @Test
   public void testDynamicQueueFQQN() throws Exception {
      final String QUEUE = "myQueue";
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      Context ctx = new InitialContext(props);
      liveService.getSecurityStore().setSecurityEnabled(false);

      Destination destination = (Destination) ctx.lookup("dynamicQueues/myAddress::" + QUEUE);
      assertInstanceOf(Queue.class, destination);
      ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
      try (Connection connection = connectionFactory.createConnection()) {
         Session session = connection.createSession();
         MessageProducer producer = session.createProducer(destination);
         producer.send(session.createMessage());
         Wait.assertTrue(() -> liveService.locateQueue(QUEUE).getMessageCount() == 1, 2000, 100);
         MessageConsumer consumer = session.createConsumer(destination);
         connection.start();
         assertNotNull(consumer.receiveNoWait());
      }
   }

   @Test
   public void testTopic() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("topic.myTopic", "myTopic");
      props.put("topic.topics/myTopic", "myTopic");
      Context ctx = new InitialContext(props);

      Destination destination = (Destination) ctx.lookup("myTopic");
      assertInstanceOf(Topic.class, destination);

      destination = (Destination) ctx.lookup("topics/myTopic");
      assertInstanceOf(Topic.class, destination);
   }

   @Test
   public void testTopicFQQN() throws Exception {
      final String SUBSCRIPTION = "mySubsription";
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("topic.myTopic", "myTopic::" + SUBSCRIPTION);
      Context ctx = new InitialContext(props);
      liveService.getSecurityStore().setSecurityEnabled(false);

      Destination destination = (Destination) ctx.lookup("myTopic");
      assertInstanceOf(Topic.class, destination);
      ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
      Connection connection = connectionFactory.createConnection();
      Session session = connection.createSession();
      MessageProducer producer = session.createProducer(destination);
      producer.send(session.createMessage());
      Wait.assertTrue(() -> liveService.locateQueue(SUBSCRIPTION).getMessageCount() == 1, 2000, 100);
      MessageConsumer consumer = session.createConsumer(destination);
      connection.start();
      assertNotNull(consumer.receiveNoWait());
   }

   @Test
   public void testDynamicTopic() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      Context ctx = new InitialContext(props);

      Destination destination = (Destination) ctx.lookup("dynamicTopics/myTopic");
      assertInstanceOf(Topic.class, destination);
   }

   @Test
   public void testDynamicTopicFQQN() throws Exception {
      final String SUBSCRIPTION = "mySubsription";
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("topic.myTopic", "myTopic::" + SUBSCRIPTION);
      Context ctx = new InitialContext(props);
      liveService.getSecurityStore().setSecurityEnabled(false);

      Destination destination = (Destination) ctx.lookup("dynamicTopics/myTopic::" + SUBSCRIPTION);
      assertInstanceOf(Topic.class, destination);
      ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
      Connection connection = connectionFactory.createConnection();
      Session session = connection.createSession();
      MessageProducer producer = session.createProducer(destination);
      producer.send(session.createMessage());
      Wait.assertTrue(() -> liveService.locateQueue(SUBSCRIPTION).getMessageCount() == 1, 2000, 100);
      MessageConsumer consumer = session.createConsumer(destination);
      connection.start();
      assertNotNull(consumer.receiveNoWait());
   }

   @Test
   public void testRemoteCFWithTCPUserPassword() throws Exception {

      //setup user and role on broker
      ((ActiveMQJAASSecurityManager) liveService.getSecurityManager()).getConfiguration().addUser("myUser", "myPassword");
      ((ActiveMQJAASSecurityManager) liveService.getSecurityManager()).getConfiguration().addRole("myUser", "consumeCreateRole");
      Role consumeCreateRole = new Role("consumeCreateRole", false, true, true, true, true, true, true, true, true, true, false, false);
      Set<Role> consumerCreateRoles = new HashSet<>();
      consumerCreateRoles.add(consumeCreateRole);
      liveService.getSecurityRepository().addMatch("test.queue", consumerCreateRoles);

      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");

      //user and password set on URL
      props.put("connectionFactory.myConnectionFactory", "tcp://127.0.0.1:61616?user=myUser&password=myPassword");
      Context ctx = new InitialContext(props);

      //create a connection factory
      ActiveMQConnectionFactory connectionFactory = (ActiveMQConnectionFactory) ctx.lookup("myConnectionFactory");
      assertEquals("myUser", connectionFactory.getUser(), "ensure user is set");
      assertEquals("myPassword", connectionFactory.getPassword(), "ensure password is set");

      //Connect to broker to verify credentials are used with connection
      Connection connection = connectionFactory.createConnection();
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      javax.jms.Queue queue = session.createQueue("test.queue");

      try {

         try {
            MessageProducer producer = session.createProducer(queue);
            producer.send(session.createTextMessage("test Msg"));
            fail("Sending message should throw a JMSSecurityException");
         } catch (JMSSecurityException e) {
            //expected
         }

         MessageConsumer consumer = session.createConsumer(queue);
      } finally {
         connection.close();
      }

   }

   public void testContext(Context ctx, String jndiName, JMSFactoryType expectedFactoryType) {
      try {
         ActiveMQConnectionFactory connectionFactory = (ActiveMQConnectionFactory) ctx.lookup(jndiName);
         if (expectedFactoryType == null) {
            fail("expected no factory, should have thrown NamingException");
         } else {
            assertEquals(expectedFactoryType.intValue(), connectionFactory.getFactoryType());
         }
      } catch (NamingException namingException) {
         assertNull(expectedFactoryType, "NamingException should only occur when no ExpectedFactoryType, but one existed");
      }
   }


   @Test
   public void testProviderUrlDefault() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put(Context.PROVIDER_URL, "vm://0");
      Context ctx = new InitialContext(props);

      testContext(ctx, "ConnectionFactory", JMSFactoryType.CF);
      testContext(ctx, "QueueConnectionFactory", JMSFactoryType.QUEUE_CF);
      testContext(ctx, "TopicConnectionFactory", JMSFactoryType.TOPIC_CF);
      testContext(ctx, "XAConnectionFactory", JMSFactoryType.XA_CF);
      testContext(ctx, "XAQueueConnectionFactory", JMSFactoryType.QUEUE_XA_CF);
      testContext(ctx, "XATopicConnectionFactory", JMSFactoryType.TOPIC_XA_CF);
   }

   @Test
   public void testProviderUrlCF() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put(Context.PROVIDER_URL, "vm://0?type=CF");
      Context ctx = new InitialContext(props);

      testContext(ctx, "ConnectionFactory", JMSFactoryType.CF);
      testContext(ctx, "QueueConnectionFactory", null);
      testContext(ctx, "TopicConnectionFactory", null);
      testContext(ctx, "XAConnectionFactory", null);
      testContext(ctx, "XAQueueConnectionFactory", null);
      testContext(ctx, "XATopicConnectionFactory", null);
   }

   @Test
   public void testProviderUrlXACF() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put(Context.PROVIDER_URL, "vm://0?type=XA_CF");
      Context ctx = new InitialContext(props);

      testContext(ctx, "ConnectionFactory", null);
      testContext(ctx, "QueueConnectionFactory", null);
      testContext(ctx, "TopicConnectionFactory", null);
      testContext(ctx, "XAConnectionFactory", JMSFactoryType.XA_CF);
      testContext(ctx, "XAQueueConnectionFactory", null);
      testContext(ctx, "XATopicConnectionFactory", null);
   }

   @Test
   public void testProviderUrlQueueCF() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put(Context.PROVIDER_URL, "vm://0?type=QUEUE_CF");
      Context ctx = new InitialContext(props);

      testContext(ctx, "ConnectionFactory", null);
      testContext(ctx, "QueueConnectionFactory", JMSFactoryType.QUEUE_CF);
      testContext(ctx, "TopicConnectionFactory", null);
      testContext(ctx, "XAConnectionFactory", null);
      testContext(ctx, "XAQueueConnectionFactory", null);
      testContext(ctx, "XATopicConnectionFactory", null);
   }

   @Test
   public void testProviderUrlQueueXACF() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put(Context.PROVIDER_URL, "vm://0?type=QUEUE_XA_CF");
      Context ctx = new InitialContext(props);

      testContext(ctx, "ConnectionFactory", null);
      testContext(ctx, "QueueConnectionFactory", null);
      testContext(ctx, "TopicConnectionFactory", null);
      testContext(ctx, "XAConnectionFactory", null);
      testContext(ctx, "XAQueueConnectionFactory", JMSFactoryType.QUEUE_XA_CF);
      testContext(ctx, "XATopicConnectionFactory", null);
   }

   @Test
   public void testProviderUrlTopicCF() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put(Context.PROVIDER_URL, "vm://0?type=TOPIC_CF");
      Context ctx = new InitialContext(props);

      testContext(ctx, "ConnectionFactory", null);
      testContext(ctx, "QueueConnectionFactory", null);
      testContext(ctx, "TopicConnectionFactory", JMSFactoryType.TOPIC_CF);
      testContext(ctx, "XAConnectionFactory", null);
      testContext(ctx, "XAQueueConnectionFactory", null);
      testContext(ctx, "XATopicConnectionFactory", null);
   }

   @Test
   public void testProviderUrlTopicXACF() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put(Context.PROVIDER_URL, "vm://0?type=TOPIC_XA_CF");
      Context ctx = new InitialContext(props);

      testContext(ctx, "ConnectionFactory", null);
      testContext(ctx, "QueueConnectionFactory", null);
      testContext(ctx, "TopicConnectionFactory", null);
      testContext(ctx, "XAConnectionFactory", null);
      testContext(ctx, "XAQueueConnectionFactory", null);
      testContext(ctx, "XATopicConnectionFactory", JMSFactoryType.TOPIC_XA_CF);
   }

   @Test
   public void testProviderUrlDefaultAndCustom() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put(Context.PROVIDER_URL, "vm://0");
      props.put("connectionFactory.myConnectionFactory", "vm://0");
      Context ctx = new InitialContext(props);

      testContext(ctx, "ConnectionFactory", JMSFactoryType.CF);
      testContext(ctx, "QueueConnectionFactory", JMSFactoryType.QUEUE_CF);
      testContext(ctx, "TopicConnectionFactory", JMSFactoryType.TOPIC_CF);
      testContext(ctx, "XAConnectionFactory", JMSFactoryType.XA_CF);
      testContext(ctx, "XAQueueConnectionFactory", JMSFactoryType.QUEUE_XA_CF);
      testContext(ctx, "XATopicConnectionFactory", JMSFactoryType.TOPIC_XA_CF);

      testContext(ctx, "myConnectionFactory", JMSFactoryType.CF);

   }

   @Test
   public void test1xNaming() throws Exception {
      liveService.getSecurityStore().setSecurityEnabled(false);
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.ConnectionFactory", "vm://0?enable1xPrefixes=true");
      props.put("connectionFactory.ConnectionFactory2", "vm://0");
      Context ctx = new InitialContext(props);

      ConnectionFactory connectionFactory = (ConnectionFactory) ctx.lookup("ConnectionFactory");
      Connection connection = connectionFactory.createConnection();
      Session session = connection.createSession();

      assertTrue(((ActiveMQDestination)session.createQueue("testQueue")).getSimpleAddress().startsWith(PacketImpl.OLD_QUEUE_PREFIX));
      assertTrue(((ActiveMQDestination)session.createTemporaryQueue()).getSimpleAddress().startsWith(PacketImpl.OLD_TEMP_QUEUE_PREFIX));
      assertTrue(((ActiveMQDestination)session.createTopic("testTopic")).getSimpleAddress().startsWith(PacketImpl.OLD_TOPIC_PREFIX));
      assertTrue(((ActiveMQDestination)session.createTemporaryTopic()).getSimpleAddress().startsWith(PacketImpl.OLD_TEMP_TOPIC_PREFIX));

      // The name should not include the prefix
      assertFalse(session.createQueue("testQueue").getQueueName().startsWith(PacketImpl.OLD_QUEUE_PREFIX.toString()));
      assertFalse(session.createTemporaryQueue().getQueueName().startsWith(PacketImpl.OLD_TEMP_QUEUE_PREFIX.toString()));
      assertFalse(session.createTopic("testTopic").getTopicName().startsWith(PacketImpl.OLD_TOPIC_PREFIX.toString()));
      assertFalse(session.createTemporaryTopic().getTopicName().startsWith(PacketImpl.OLD_TEMP_TOPIC_PREFIX.toString()));


      connection.close();

      // test setting programmatically
      connectionFactory = (ConnectionFactory) ctx.lookup("ConnectionFactory2");
      ((ActiveMQConnectionFactory)connectionFactory).setEnable1xPrefixes(true);
      connection = connectionFactory.createConnection();
      session = connection.createSession();

      assertTrue(((ActiveMQDestination)session.createQueue("testQueue")).getSimpleAddress().startsWith(PacketImpl.OLD_QUEUE_PREFIX));
      assertTrue(((ActiveMQDestination)session.createTemporaryQueue()).getSimpleAddress().startsWith(PacketImpl.OLD_TEMP_QUEUE_PREFIX));
      assertTrue(((ActiveMQDestination)session.createTopic("testTopic")).getSimpleAddress().startsWith(PacketImpl.OLD_TOPIC_PREFIX));
      assertTrue(((ActiveMQDestination)session.createTemporaryTopic()).getSimpleAddress().startsWith(PacketImpl.OLD_TEMP_TOPIC_PREFIX));

      // The name should not include the prefix
      assertFalse(session.createQueue("testQueue").getQueueName().startsWith(PacketImpl.OLD_QUEUE_PREFIX.toString()));
      assertFalse(session.createTemporaryQueue().getQueueName().startsWith(PacketImpl.OLD_TEMP_QUEUE_PREFIX.toString()));
      assertFalse(session.createTopic("testTopic").getTopicName().startsWith(PacketImpl.OLD_TOPIC_PREFIX.toString()));
      assertFalse(session.createTemporaryTopic().getTopicName().startsWith(PacketImpl.OLD_TEMP_TOPIC_PREFIX.toString()));

      connection.close();
   }

   @Test
   public void test1xNamingNegative() throws Exception {
      liveService.getSecurityStore().setSecurityEnabled(false);
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.ConnectionFactory", "vm://0");
      Context ctx = new InitialContext(props);

      ConnectionFactory connectionFactory = (ConnectionFactory) ctx.lookup("ConnectionFactory");
      Connection connection = connectionFactory.createConnection();
      Session session = connection.createSession();

      assertFalse(((ActiveMQDestination)session.createQueue("testQueue")).getSimpleAddress().startsWith(PacketImpl.OLD_QUEUE_PREFIX));
      assertFalse(((ActiveMQDestination)session.createTemporaryQueue()).getSimpleAddress().startsWith(PacketImpl.OLD_TEMP_QUEUE_PREFIX));
      assertFalse(((ActiveMQDestination)session.createTopic("testTopic")).getSimpleAddress().startsWith(PacketImpl.OLD_TOPIC_PREFIX));
      assertFalse(((ActiveMQDestination)session.createTemporaryTopic()).getSimpleAddress().startsWith(PacketImpl.OLD_TEMP_TOPIC_PREFIX));

      connection.close();
   }

   @Test
   public void testUseTopologyForLoadBalancing() throws Exception {
      Hashtable<String, String> props = new Hashtable<>();
      props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
      props.put("connectionFactory.ConnectionFactory", "vm://0?useTopologyForLoadBalancing=false");
      Context ctx = new InitialContext(props);

      ConnectionFactory connectionFactory = (ConnectionFactory) ctx.lookup("ConnectionFactory");
      assertFalse(((ActiveMQConnectionFactory)connectionFactory).getServerLocator().getUseTopologyForLoadBalancing());
   }
}