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
package org.apache.activemq.artemis.tests.integration.ra;

import javax.jms.Connection;
import javax.resource.ResourceException;
import javax.resource.spi.endpoint.MessageEndpoint;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.ra.ActiveMQResourceAdapter;
import org.apache.activemq.artemis.ra.inflow.ActiveMQActivation;
import org.apache.activemq.artemis.ra.inflow.ActiveMQActivationSpec;
import org.apache.activemq.artemis.service.extensions.xa.recovery.XARecoveryConfig;
import org.apache.activemq.artemis.tests.unit.ra.BootstrapContext;
import org.apache.activemq.artemis.tests.unit.ra.MessageEndpointFactory;
import org.apache.activemq.artemis.utils.DefaultSensitiveStringCodec;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ResourceAdapterTest extends ActiveMQRATestBase {

   @Test
   public void testStartStopActivationManyTimes() throws Exception {
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = locator.createSessionFactory();
      ClientSession session = factory.createSession(false, false, false);
      ActiveMQDestination queue = (ActiveMQDestination) ActiveMQJMSClient.createQueue("test");
      session.createQueue(QueueConfiguration.of(queue.getSimpleAddress()));
      session.close();

      ActiveMQResourceAdapter ra = new ActiveMQResourceAdapter();

      ra.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      ra.setUserName("userGlobal");
      ra.setPassword("passwordGlobal");
      ra.start(new BootstrapContext());

      Connection conn = ra.getDefaultActiveMQConnectionFactory().createConnection();

      conn.close();

      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();

      spec.setResourceAdapter(ra);

      spec.setUseJNDI(false);

      spec.setUser("user");
      spec.setPassword("password");

      spec.setDestinationType("javax.jms.Topic");
      spec.setDestination("test");

      spec.setMinSession(1);
      spec.setMaxSession(15);

      ActiveMQActivation activation = new ActiveMQActivation(ra, new MessageEndpointFactory(), spec);

      ServerLocatorImpl serverLocator = (ServerLocatorImpl) ra.getDefaultActiveMQConnectionFactory().getServerLocator();

      Set<XARecoveryConfig> resources = ra.getRecoveryManager().getResources();

      for (int i = 0; i < 10; i++) {
         activation.start();
         assertEquals(1, resources.size());
         activation.stop();
      }

      ra.stop();
      assertEquals(0, resources.size());
      locator.close();

   }

   @Test
   public void testQueuePrefixWhenUseJndiIsFalse() throws Exception {
      final String prefix = "jms.queue.";
      final String destinationName = "test";
      final SimpleString prefixedDestinationName = SimpleString.of(prefix + destinationName);
      server.createQueue(QueueConfiguration.of(prefixedDestinationName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      ActiveMQResourceAdapter ra = new ActiveMQResourceAdapter();
      ra.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      ra.start(new BootstrapContext());
      Connection conn = ra.getDefaultActiveMQConnectionFactory().createConnection();
      conn.close();

      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(ra);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(destinationName);
      spec.setQueuePrefix(prefix);
      spec.setMaxSession(1);
      spec.setSetupAttempts(1);

      ActiveMQActivation activation = new ActiveMQActivation(ra, new MessageEndpointFactory(), spec);

      activation.start();

      assertEquals(1, server.locateQueue(prefixedDestinationName).getConsumerCount());

      activation.stop();
   }

   @Test
   public void testAutoCreateQueuePrefixWhenUseJndiIsFalse() throws Exception {
      final String prefix = "jms.queue.";
      final String destinationName = "autocreatedtest";
      final SimpleString prefixedDestinationName = SimpleString.of(prefix + destinationName);
      ActiveMQResourceAdapter ra = new ActiveMQResourceAdapter();
      ra.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      ra.start(new BootstrapContext());
      Connection conn = ra.getDefaultActiveMQConnectionFactory().createConnection();
      conn.close();

      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(ra);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(destinationName);
      spec.setQueuePrefix(prefix);
      spec.setMaxSession(1);
      spec.setSetupAttempts(1);

      ActiveMQActivation activation = new ActiveMQActivation(ra, new MessageEndpointFactory(), spec);

      activation.start();

      assertEquals(1, server.locateQueue(prefixedDestinationName).getConsumerCount());

      activation.stop();
   }

   @Test
   public void testTopicPrefixWhenUseJndiIsFalse() throws Exception {
      final String prefix = "jms.topic.";
      final String destinationName = "test";
      final String prefixedDestinationName = prefix + destinationName;
      server.addAddressInfo(new AddressInfo(prefixedDestinationName).addRoutingType(RoutingType.MULTICAST));
      ActiveMQResourceAdapter ra = new ActiveMQResourceAdapter();
      ra.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      ra.start(new BootstrapContext());
      Connection conn = ra.getDefaultActiveMQConnectionFactory().createConnection();
      conn.close();

      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(ra);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Topic");
      spec.setDestination(destinationName);
      spec.setTopicPrefix(prefix);
      spec.setMaxSession(1);
      spec.setSetupAttempts(1);

      ActiveMQActivation activation = new ActiveMQActivation(ra, new MessageEndpointFactory(), spec);

      activation.start();

      assertEquals(1, (server.getManagementService().getAddressControl(prefixedDestinationName)).getQueueNames().length);

      activation.stop();
   }

   @Test
   public void testAutoCreatedQueueNotFiltered() throws Exception {
      final String destinationName = "test";
      ActiveMQResourceAdapter ra = new ActiveMQResourceAdapter();
      ra.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      ra.start(new BootstrapContext());
      Connection conn = ra.getDefaultActiveMQConnectionFactory().createConnection();
      conn.close();

      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(ra);
      spec.setJndiParams("java.naming.factory.initial=org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory;");
      spec.setMessageSelector("HeaderField = 'foo'");
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestinationLookup(destinationName);

      ActiveMQActivation activation = new ActiveMQActivation(ra, new MessageEndpointFactory(), spec);

      activation.start();

      Queue queue = server.locateQueue(destinationName);
      assertNotNull(queue);
      assertNull(queue.getFilter());

      activation.stop();
   }

   @Test
   public void testStartStop() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      ActiveMQRATestBase.MyBootstrapContext ctx = new ActiveMQRATestBase.MyBootstrapContext();

      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      qResourceAdapter.stop();
      assertTrue(endpoint.released);
   }

   @Test
   public void testSetters() throws Exception {
      Boolean b = Boolean.TRUE;
      Long l = (long) 1000;
      Integer i = 1000;
      Double d = (double) 1000;
      String className = "testConnector";
      String backupConn = "testBackupConnector";
      String testConfig = "key=val";
      String testid = "testid";
      String testBalancer = "testBalancer";
      String testParams = "key=val";
      String testaddress = "testaddress";
      String testpass = "testpass";
      String testuser = "testuser";
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      testParams(b, l, i, d, className, backupConn, testConfig, testid, testBalancer, testParams, testaddress, testpass, testuser, qResourceAdapter);
   }

   @Test
   public void testSetters2() throws Exception {
      Boolean b = Boolean.FALSE;
      Long l = (long) 2000;
      Integer i = 2000;
      Double d = (double) 2000;
      String className = "testConnector2";
      String backupConn = "testBackupConnector2";
      String testConfig = "key2=val2";
      String testid = "testid2";
      String testBalancer = "testBalancer2";
      String testParams = "key=val2";
      String testaddress = "testaddress2";
      String testpass = "testpass2";
      String testuser = "testuser2";
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      testParams(b, l, i, d, className, backupConn, testConfig, testid, testBalancer, testParams, testaddress, testpass, testuser, qResourceAdapter);
   }

   private void testParams(Boolean b,
                           Long aLong,
                           Integer i,
                           Double d,
                           String className,
                           String backupConn,
                           String testConfig,
                           String testid,
                           String testBalancer,
                           String testParams,
                           String testaddress,
                           String testpass,
                           String testuser,
                           ActiveMQResourceAdapter qResourceAdapter) {
      qResourceAdapter.setUseLocalTx(b);
      qResourceAdapter.setConnectorClassName(className);
      qResourceAdapter.setAutoGroup(b);
      qResourceAdapter.setBlockOnAcknowledge(b);
      qResourceAdapter.setBlockOnDurableSend(b);
      qResourceAdapter.setBlockOnNonDurableSend(b);
      qResourceAdapter.setCallTimeout(aLong);
      qResourceAdapter.setClientFailureCheckPeriod(aLong);
      qResourceAdapter.setClientID(testid);
      qResourceAdapter.setConfirmationWindowSize(i);
      qResourceAdapter.setConnectionLoadBalancingPolicyClassName(testBalancer);
      qResourceAdapter.setConnectionParameters(testParams);
      qResourceAdapter.setConnectionTTL(aLong);
      qResourceAdapter.setConsumerMaxRate(i);
      qResourceAdapter.setConsumerWindowSize(i);
      qResourceAdapter.setDiscoveryAddress(testaddress);
      qResourceAdapter.setDiscoveryInitialWaitTimeout(aLong);
      qResourceAdapter.setDiscoveryPort(i);
      qResourceAdapter.setDiscoveryRefreshTimeout(aLong);
      qResourceAdapter.setDupsOKBatchSize(i);
      qResourceAdapter.setMinLargeMessageSize(i);
      qResourceAdapter.setPassword(testpass);
      qResourceAdapter.setPreAcknowledge(b);
      qResourceAdapter.setProducerMaxRate(i);
      qResourceAdapter.setReconnectAttempts(i);
      qResourceAdapter.setRetryInterval(aLong);
      qResourceAdapter.setRetryIntervalMultiplier(d);
      qResourceAdapter.setScheduledThreadPoolMaxSize(i);
      qResourceAdapter.setThreadPoolMaxSize(i);
      qResourceAdapter.setTransactionBatchSize(i);
      qResourceAdapter.setUseGlobalPools(b);
      qResourceAdapter.setUseLocalTx(b);
      qResourceAdapter.setUserName(testuser);

      assertEquals(qResourceAdapter.getUseLocalTx(), b);
      assertEquals(qResourceAdapter.getConnectorClassName(), className);
      assertEquals(qResourceAdapter.getAutoGroup(), b);
      // assertEquals(qResourceAdapter.getBackupTransportConfiguration(),"testConfig");
      assertEquals(qResourceAdapter.getBlockOnAcknowledge(), b);
      assertEquals(qResourceAdapter.getBlockOnDurableSend(), b);
      assertEquals(qResourceAdapter.getBlockOnNonDurableSend(), b);
      assertEquals(qResourceAdapter.getCallTimeout(), aLong);
      assertEquals(qResourceAdapter.getClientFailureCheckPeriod(), aLong);
      assertEquals(qResourceAdapter.getClientID(), testid);
      assertEquals(qResourceAdapter.getConfirmationWindowSize(), i);
      assertEquals(qResourceAdapter.getConnectionLoadBalancingPolicyClassName(), testBalancer);
      assertEquals(qResourceAdapter.getConnectionParameters(), testParams);
      assertEquals(qResourceAdapter.getConnectionTTL(), aLong);
      assertEquals(qResourceAdapter.getConsumerMaxRate(), i);
      assertEquals(qResourceAdapter.getConsumerWindowSize(), i);
      assertEquals(qResourceAdapter.getDiscoveryAddress(), testaddress);
      assertEquals(qResourceAdapter.getDiscoveryInitialWaitTimeout(), aLong);
      assertEquals(qResourceAdapter.getDiscoveryPort(), i);
      assertEquals(qResourceAdapter.getDiscoveryRefreshTimeout(), aLong);
      assertEquals(qResourceAdapter.getDupsOKBatchSize(), i);
      assertEquals(qResourceAdapter.getMinLargeMessageSize(), i);
      assertEquals(qResourceAdapter.getPassword(), testpass);
      assertEquals(qResourceAdapter.getPreAcknowledge(), b);
      assertEquals(qResourceAdapter.getProducerMaxRate(), i);
      assertEquals(qResourceAdapter.getReconnectAttempts(), i);
      assertEquals(qResourceAdapter.getRetryInterval(), aLong);
      assertEquals(qResourceAdapter.getRetryIntervalMultiplier(), d);
      assertEquals(qResourceAdapter.getScheduledThreadPoolMaxSize(), i);
      assertEquals(qResourceAdapter.getThreadPoolMaxSize(), i);
      assertEquals(qResourceAdapter.getTransactionBatchSize(), i);
      assertEquals(qResourceAdapter.getUseGlobalPools(), b);
      assertEquals(qResourceAdapter.getUseLocalTx(), b);
      assertEquals(qResourceAdapter.getUserName(), testuser);
   }

   @Test
   public void testResourceAdapterSetup() throws Exception {
      ActiveMQResourceAdapter adapter = new ActiveMQResourceAdapter();
      adapter.setDiscoveryAddress("231.1.1.1");
      ActiveMQConnectionFactory factory = adapter.getDefaultActiveMQConnectionFactory();
      long initWait = factory.getDiscoveryGroupConfiguration().getDiscoveryInitialWaitTimeout();
      long refresh = factory.getDiscoveryGroupConfiguration().getRefreshTimeout();
      int port = ((UDPBroadcastEndpointFactory) factory.getDiscoveryGroupConfiguration().getBroadcastEndpointFactory()).getGroupPort();

      // defaults
      assertEquals(10000L, refresh);
      assertEquals(10000L, initWait);
      assertEquals(9876, port);

      adapter = new ActiveMQResourceAdapter();
      adapter.setDiscoveryAddress("231.1.1.1");
      adapter.setDiscoveryPort(9876);
      adapter.setDiscoveryRefreshTimeout(1234L);
      factory = adapter.getDefaultActiveMQConnectionFactory();
      initWait = factory.getDiscoveryGroupConfiguration().getDiscoveryInitialWaitTimeout();
      refresh = factory.getDiscoveryGroupConfiguration().getRefreshTimeout();

      // override refresh timeout
      assertEquals(1234L, refresh);
      assertEquals(10000L, initWait);

      adapter = new ActiveMQResourceAdapter();
      adapter.setDiscoveryAddress("231.1.1.1");
      adapter.setDiscoveryPort(9876);
      adapter.setDiscoveryInitialWaitTimeout(9999L);
      factory = adapter.getDefaultActiveMQConnectionFactory();
      initWait = factory.getDiscoveryGroupConfiguration().getDiscoveryInitialWaitTimeout();
      refresh = factory.getDiscoveryGroupConfiguration().getRefreshTimeout();

      // override initial wait
      assertEquals(10000L, refresh);
      assertEquals(9999L, initWait);

      adapter = new ActiveMQResourceAdapter();
      adapter.setDiscoveryAddress("231.1.1.1");
      adapter.setDiscoveryPort(9876);
      adapter.setDiscoveryInitialWaitTimeout(9999L);
      factory = adapter.getDefaultActiveMQConnectionFactory();
      initWait = factory.getDiscoveryGroupConfiguration().getDiscoveryInitialWaitTimeout();
      refresh = factory.getDiscoveryGroupConfiguration().getRefreshTimeout();

      // override initial wait
      assertEquals(10000L, refresh);
      assertEquals(9999L, initWait);

   }

   @Test
   public void testResourceAdapterSetupOverrideCFParams() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      qResourceAdapter.setConnectionParameters("server-id=0");
      ActiveMQRATestBase.MyBootstrapContext ctx = new ActiveMQRATestBase.MyBootstrapContext();

      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      // now override the connector class
      spec.setConnectorClassName(NETTY_CONNECTOR_FACTORY);
      spec.setConnectionParameters("port=61616");
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(new CountDownLatch(1));
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      //make sure 2 recovery resources, one is default, one is in activation.
      assertEquals(2, qResourceAdapter.getRecoveryManager().getResources().size());
      qResourceAdapter.stop();
      assertTrue(endpoint.released);
   }

   @Test
   public void testRecoveryRegistrationOnFailure() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      qResourceAdapter.setConnectionParameters("server-id=0");
      ActiveMQRATestBase.MyBootstrapContext ctx = new ActiveMQRATestBase.MyBootstrapContext();

      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      // now override the connector class
      spec.setConnectorClassName(NETTY_CONNECTOR_FACTORY);
      spec.setSetupAttempts(2);
      // using a wrong port number
      spec.setConnectionParameters("port=6776");
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(new CountDownLatch(1));
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);

      assertEquals(1, qResourceAdapter.getRecoveryManager().getResources().size());
      qResourceAdapter.stop();
      assertFalse(endpoint.released);
   }

   @Test
   public void testResourceAdapterSetupOverrideNoCFParams() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      qResourceAdapter.setConnectionParameters("server-id=0");
      ActiveMQRATestBase.MyBootstrapContext ctx = new ActiveMQRATestBase.MyBootstrapContext();

      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);

      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      qResourceAdapter.stop();
      assertFalse(spec.isHasBeenUpdated());
      assertTrue(endpoint.released);
   }

   @Test
   public void testResourceAdapterSetupNoOverrideDiscovery() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setDiscoveryAddress("231.6.6.6");
      qResourceAdapter.setDiscoveryPort(1234);
      qResourceAdapter.setDiscoveryRefreshTimeout(1L);
      qResourceAdapter.setDiscoveryInitialWaitTimeout(1L);
      ActiveMQRATestBase.MyBootstrapContext ctx = new ActiveMQRATestBase.MyBootstrapContext();

      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      ActiveMQConnectionFactory fac = qResourceAdapter.getConnectionFactory(spec);
      DiscoveryGroupConfiguration dc = fac.getServerLocator().getDiscoveryGroupConfiguration();
      UDPBroadcastEndpointFactory udpDg = (UDPBroadcastEndpointFactory) dc.getBroadcastEndpointFactory();
      assertEquals("231.6.6.6", udpDg.getGroupAddress());
      assertEquals(1234, udpDg.getGroupPort());
      assertEquals(1L, dc.getRefreshTimeout());
      assertEquals(1L, dc.getDiscoveryInitialWaitTimeout());
      qResourceAdapter.stop();
   }

   @Test
   public void testResourceAdapterSetupOverrideDiscovery() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setDiscoveryAddress("231.7.7.7");

      ActiveMQRATestBase.MyBootstrapContext ctx = new ActiveMQRATestBase.MyBootstrapContext();

      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      spec.setSetupAttempts(0);
      spec.setDiscoveryAddress("231.6.6.6");
      spec.setDiscoveryPort(1234);
      spec.setDiscoveryInitialWaitTimeout(1L);
      spec.setDiscoveryRefreshTimeout(1L);
      ActiveMQConnectionFactory fac = qResourceAdapter.getConnectionFactory(spec);
      DiscoveryGroupConfiguration dc = fac.getServerLocator().getDiscoveryGroupConfiguration();
      UDPBroadcastEndpointFactory udpDg = (UDPBroadcastEndpointFactory) dc.getBroadcastEndpointFactory();
      assertEquals("231.6.6.6", udpDg.getGroupAddress());
      assertEquals(1234, udpDg.getGroupPort());
      assertEquals(1L, dc.getRefreshTimeout());
      assertEquals(1L, dc.getDiscoveryInitialWaitTimeout());
      qResourceAdapter.stop();
   }

   @Test
   public void testResourceAdapterSetupNoHAOverride() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      qResourceAdapter.setConnectionParameters("server-id=0");
      qResourceAdapter.setHA(true);
      ActiveMQRATestBase.MyBootstrapContext ctx = new ActiveMQRATestBase.MyBootstrapContext();

      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);

      ActiveMQConnectionFactory fac = qResourceAdapter.getConnectionFactory(spec);

      assertTrue(fac.isHA());

      qResourceAdapter.stop();
      assertFalse(spec.isHasBeenUpdated());
   }

   @Test
   public void testResourceAdapterSetupNoHADefault() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      qResourceAdapter.setConnectionParameters("server-id=0");
      ActiveMQRATestBase.MyBootstrapContext ctx = new ActiveMQRATestBase.MyBootstrapContext();

      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);

      ActiveMQConnectionFactory fac = qResourceAdapter.getConnectionFactory(spec);

      assertFalse(fac.isHA());

      qResourceAdapter.stop();
      assertFalse(spec.isHasBeenUpdated());
   }

   @Test
   public void testResourceAdapterSetupHAOverride() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      qResourceAdapter.setConnectionParameters("server-id=0");
      ActiveMQRATestBase.MyBootstrapContext ctx = new ActiveMQRATestBase.MyBootstrapContext();

      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      spec.setHA(true);
      ActiveMQConnectionFactory fac = qResourceAdapter.getConnectionFactory(spec);

      assertTrue(fac.isHA());

      qResourceAdapter.stop();
      assertTrue(spec.isHasBeenUpdated());
   }

   @Test
   public void testResourceAdapterSetupNoReconnectAttemptsOverride() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      qResourceAdapter.setConnectionParameters("server-id=0");
      qResourceAdapter.setReconnectAttempts(100);
      ActiveMQRATestBase.MyBootstrapContext ctx = new ActiveMQRATestBase.MyBootstrapContext();

      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);

      ActiveMQConnectionFactory fac = qResourceAdapter.getConnectionFactory(spec);

      assertEquals(100, fac.getReconnectAttempts());

      qResourceAdapter.stop();
      assertFalse(spec.isHasBeenUpdated());
   }

   @Test
   public void testResourceAdapterSetupReconnectAttemptDefault() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      qResourceAdapter.setConnectionParameters("server-id=0");
      ActiveMQRATestBase.MyBootstrapContext ctx = new ActiveMQRATestBase.MyBootstrapContext();

      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);

      ActiveMQConnectionFactory fac = qResourceAdapter.getConnectionFactory(spec);

      assertEquals(-1, fac.getReconnectAttempts());

      qResourceAdapter.stop();
      assertFalse(spec.isHasBeenUpdated());
   }

   @Test
   public void testResourceAdapterSetupReconnectAttemptsOverride() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      qResourceAdapter.setConnectionParameters("server-id=0");
      ActiveMQRATestBase.MyBootstrapContext ctx = new ActiveMQRATestBase.MyBootstrapContext();

      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      spec.setReconnectAttempts(100);
      ActiveMQConnectionFactory fac = qResourceAdapter.getConnectionFactory(spec);

      assertEquals(100, fac.getReconnectAttempts());

      qResourceAdapter.stop();
      assertTrue(spec.isHasBeenUpdated());
   }

   @Test
   public void testMaskPassword() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      ActiveMQRATestBase.MyBootstrapContext ctx = new ActiveMQRATestBase.MyBootstrapContext();

      DefaultSensitiveStringCodec codec = PasswordMaskingUtil.getDefaultCodec();
      String mask = codec.encode("helloworld");

      qResourceAdapter.setUseMaskedPassword(true);
      qResourceAdapter.setPassword(mask);

      qResourceAdapter.start(ctx);

      assertEquals("helloworld", qResourceAdapter.getPassword());

      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);

      mask = (String) codec.encode("mdbpassword");
      spec.setPassword(mask);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);

      assertEquals("mdbpassword", spec.getPassword());

      qResourceAdapter.stop();
      assertTrue(endpoint.released);
   }

   @Test
   public void testMaskPasswordENC() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      ActiveMQRATestBase.MyBootstrapContext ctx = new ActiveMQRATestBase.MyBootstrapContext();

      DefaultSensitiveStringCodec codec = PasswordMaskingUtil.getDefaultCodec();
      String mask = codec.encode("helloworld");

      qResourceAdapter.setPassword(PasswordMaskingUtil.wrap(mask));

      qResourceAdapter.start(ctx);

      assertEquals("helloworld", qResourceAdapter.getPassword());

      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);

      mask = codec.encode("mdbpassword");
      spec.setPassword(PasswordMaskingUtil.wrap(mask));
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);

      assertEquals("mdbpassword", spec.getPassword());

      qResourceAdapter.stop();
      assertTrue(endpoint.released);
   }

   @Test
   public void testMaskPassword2() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      ActiveMQRATestBase.MyBootstrapContext ctx = new ActiveMQRATestBase.MyBootstrapContext();

      qResourceAdapter.setUseMaskedPassword(true);
      qResourceAdapter.setPasswordCodec(DefaultSensitiveStringCodec.class.getName() + ";key=anotherkey");

      DefaultSensitiveStringCodec codec = new DefaultSensitiveStringCodec();
      Map<String, String> prop = new HashMap<>();

      prop.put("key", "anotherkey");
      codec.init(prop);

      String mask = codec.encode("helloworld");

      qResourceAdapter.setPassword(mask);

      qResourceAdapter.start(ctx);

      assertEquals("helloworld", qResourceAdapter.getPassword());

      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);

      mask = codec.encode("mdbpassword");
      spec.setPassword(mask);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);

      assertEquals("mdbpassword", spec.getPassword());

      qResourceAdapter.stop();
      assertTrue(endpoint.released);
   }

   @Test
   public void testMaskPassword2ENC() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      ActiveMQRATestBase.MyBootstrapContext ctx = new ActiveMQRATestBase.MyBootstrapContext();

      qResourceAdapter.setPasswordCodec(DefaultSensitiveStringCodec.class.getName() + ";key=anotherkey");

      DefaultSensitiveStringCodec codec = new DefaultSensitiveStringCodec();
      Map<String, String> prop = new HashMap<>();

      prop.put("key", "anotherkey");
      codec.init(prop);

      String mask = codec.encode("helloworld");

      qResourceAdapter.setPassword(PasswordMaskingUtil.wrap(mask));

      qResourceAdapter.start(ctx);

      assertEquals("helloworld", qResourceAdapter.getPassword());

      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);

      mask = codec.encode("mdbpassword");
      spec.setPassword(PasswordMaskingUtil.wrap(mask));
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);

      assertEquals("mdbpassword", spec.getPassword());

      qResourceAdapter.stop();
      assertTrue(endpoint.released);
   }

   @Test
   public void testConnectionParameterStringParsing() throws Exception {
      ActiveMQResourceAdapter resourceAdapter = new ActiveMQResourceAdapter();
      resourceAdapter.setConnectionParameters("enabledProtocols=TLS1\\,TLS1.2;sslEnabled=true");
      assertEquals("TLS1,TLS1.2", resourceAdapter.getProperties().getParsedConnectionParameters().get(0).get("enabledProtocols"));
      resourceAdapter.setConnectionParameters("enabledProtocols=TLS1\\,TLS1.2;sslEnabled=true,enabledProtocols=TLS1.3\\,TLS1.4\\,TLS1.5;sslEnabled=true");
      assertEquals("TLS1,TLS1.2", resourceAdapter.getProperties().getParsedConnectionParameters().get(0).get("enabledProtocols"));
      assertEquals("TLS1.3,TLS1.4,TLS1.5", resourceAdapter.getProperties().getParsedConnectionParameters().get(1).get("enabledProtocols"));

      try {
         resourceAdapter.setConnectionParameters("enabledProtocols=TLS1,TLS1.2;sslEnabled=true,enabledProtocols=TLS1,TLS1.2;sslEnabled=true");
         fail("This should have failed");
      } catch (Exception e) {
         // ignore
      }
   }

   @Test
   public void testConnectionFactoryPropertiesApplyToRecoveryConfig() throws Exception {
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = locator.createSessionFactory();
      ClientSession session = factory.createSession(false, false, false);
      ActiveMQDestination queue = (ActiveMQDestination) ActiveMQJMSClient.createQueue("test");
      session.createQueue(QueueConfiguration.of(queue.getSimpleAddress()));
      session.close();

      ActiveMQResourceAdapter ra = new ActiveMQResourceAdapter();

      ra.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      ra.setUserName("userGlobal");
      ra.setPassword("passwordGlobal");
      ra.setConnectionTTL(100L);
      ra.setCallFailoverTimeout(100L);
      ra.start(new BootstrapContext());

      Set<XARecoveryConfig> resources = ra.getRecoveryManager().getResources();
      assertEquals(100L, ra.getDefaultActiveMQConnectionFactory().getServerLocator().getConnectionTTL());
      assertEquals(100L, ra.getDefaultActiveMQConnectionFactory().getServerLocator().getCallFailoverTimeout());


      for (XARecoveryConfig resource : resources) {
         assertEquals(100L, resource.createServerLocator().getConnectionTTL());
         assertEquals(100L, resource.createServerLocator().getCallFailoverTimeout());
      }

      ra.stop();
      assertEquals(0, resources.size());
      locator.close();

   }

   @Override
   public boolean useSecurity() {
      return false;
   }

   class DummyEndpoint implements MessageEndpoint {

      @Override
      public void beforeDelivery(Method method) throws NoSuchMethodException, ResourceException {
         // To change body of implemented methods use File | Settings | File Templates.
      }

      @Override
      public void afterDelivery() throws ResourceException {
         // To change body of implemented methods use File | Settings | File Templates.
      }

      @Override
      public void release() {
         // To change body of implemented methods use File | Settings | File Templates.
      }
   }
}
