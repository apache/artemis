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
package org.apache.activemq.artemis.tests.integration.management;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.io.File;
import java.io.FileReader;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.ActiveMQObjectClosedException;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.ActiveMQTimeoutException;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.AddressSettingsInfo;
import org.apache.activemq.artemis.api.core.management.BridgeControl;
import org.apache.activemq.artemis.api.core.management.DivertControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.RoleInfo;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryImpl;
import org.apache.activemq.artemis.core.client.impl.ClientSessionImpl;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.brokerConnectivity.BrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.management.impl.ActiveMQServerControlImpl;
import org.apache.activemq.artemis.core.management.impl.view.ConnectionField;
import org.apache.activemq.artemis.core.management.impl.view.ConsumerField;
import org.apache.activemq.artemis.core.management.impl.view.ProducerField;
import org.apache.activemq.artemis.core.management.impl.view.SessionField;
import org.apache.activemq.artemis.core.management.impl.view.predicate.ActiveMQFilterPredicate;
import org.apache.activemq.artemis.core.messagecounter.impl.MessageCounterManagerImpl;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.config.PersistedDivertConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.BrokerConnection;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerProducer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.ServiceComponent;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.ServerLegacyProducersImpl;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerSessionPlugin;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.DeletionPolicy;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerPolicy;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnection;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQSession;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonValue;
import org.apache.activemq.artemis.marker.WebServerComponentMarker;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.unit.core.config.impl.fakes.FakeConnectorServiceFactory;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.SecurityFormatter;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.core.management.impl.ActiveMQServerControlImpl.TMP_DIR_SYSTEM_PROPERTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@ExtendWith(ParameterizedTestExtension.class)
public class ActiveMQServerControlTest extends ManagementTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected boolean legacyCreateQueue;

   // For any extended tests that may add extra prodcuers as part of the test
   protected int extraProducers = 0;

   private ActiveMQServer server;

   private Configuration conf;

   private TransportConfiguration connectorConfig;

   @Parameters(name = "legacyCreateQueue={0}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][]{{true}, {false}});
   }

   public ActiveMQServerControlTest(boolean legacyCreateQueue) {
      this.legacyCreateQueue = legacyCreateQueue;
   }


   private static boolean contains(final String name, final String[] strings) {
      boolean found = false;
      for (String str : strings) {
         if (name.equals(str)) {
            found = true;
            break;
         }
      }
      return found;
   }



   public boolean usingCore() {
      return false;
   }

   @TestTemplate
   public void testGetAttributes() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      assertEquals(server.getConfiguration().getName(), serverControl.getName());

      assertEquals(server.getVersion().getFullVersion(), serverControl.getVersion());

      assertEquals(conf.isClustered(), serverControl.isClustered());
      assertEquals(conf.isPersistDeliveryCountBeforeDelivery(), serverControl.isPersistDeliveryCountBeforeDelivery());
      assertEquals(conf.getScheduledThreadPoolMaxSize(), serverControl.getScheduledThreadPoolMaxSize());
      assertEquals(conf.getThreadPoolMaxSize(), serverControl.getThreadPoolMaxSize());
      assertEquals(conf.getSecurityInvalidationInterval(), serverControl.getSecurityInvalidationInterval());
      assertEquals(conf.isSecurityEnabled(), serverControl.isSecurityEnabled());
      assertEquals(conf.isAsyncConnectionExecutionEnabled(), serverControl.isAsyncConnectionExecutionEnabled());
      assertEquals(conf.getIncomingInterceptorClassNames().size(), serverControl.getIncomingInterceptorClassNames().length);
      assertEquals(conf.getIncomingInterceptorClassNames().size(), serverControl.getIncomingInterceptorClassNames().length);
      assertEquals(conf.getOutgoingInterceptorClassNames().size(), serverControl.getOutgoingInterceptorClassNames().length);
      assertEquals(conf.getConnectionTTLOverride(), serverControl.getConnectionTTLOverride());
      //assertEquals(conf.getBackupConnectorName(), serverControl.getBackupConnectorName());
      assertEquals(conf.getManagementAddress().toString(), serverControl.getManagementAddress());
      assertEquals(conf.getManagementNotificationAddress().toString(), serverControl.getManagementNotificationAddress());
      assertEquals(conf.getIDCacheSize(), serverControl.getIDCacheSize());
      assertEquals(conf.isPersistIDCache(), serverControl.isPersistIDCache());
      assertEquals(conf.getBindingsDirectory(), serverControl.getBindingsDirectory());
      assertEquals(conf.getJournalDirectory(), serverControl.getJournalDirectory());
      assertEquals(conf.getJournalType().toString(), serverControl.getJournalType());
      assertEquals(conf.isJournalSyncTransactional(), serverControl.isJournalSyncTransactional());
      assertEquals(conf.isJournalSyncNonTransactional(), serverControl.isJournalSyncNonTransactional());
      assertEquals(conf.getJournalFileSize(), serverControl.getJournalFileSize());
      assertEquals(conf.getJournalMinFiles(), serverControl.getJournalMinFiles());
      if (LibaioContext.isLoaded()) {
         assertEquals(conf.getJournalMaxIO_AIO(), serverControl.getJournalMaxIO());
         assertEquals(conf.getJournalBufferSize_AIO(), serverControl.getJournalBufferSize());
         assertEquals(conf.getJournalBufferTimeout_AIO(), serverControl.getJournalBufferTimeout());
      }
      assertEquals(conf.isCreateBindingsDir(), serverControl.isCreateBindingsDir());
      assertEquals(conf.isCreateJournalDir(), serverControl.isCreateJournalDir());
      assertEquals(conf.getPagingDirectory(), serverControl.getPagingDirectory());
      assertEquals(conf.getLargeMessagesDirectory(), serverControl.getLargeMessagesDirectory());
      assertEquals(conf.isWildcardRoutingEnabled(), serverControl.isWildcardRoutingEnabled());
      assertEquals(conf.getTransactionTimeout(), serverControl.getTransactionTimeout());
      assertEquals(conf.isMessageCounterEnabled(), serverControl.isMessageCounterEnabled());
      assertEquals(conf.getTransactionTimeoutScanPeriod(), serverControl.getTransactionTimeoutScanPeriod());
      assertEquals(conf.getMessageExpiryScanPeriod(), serverControl.getMessageExpiryScanPeriod());
      assertEquals(conf.getJournalCompactMinFiles(), serverControl.getJournalCompactMinFiles());
      assertEquals(conf.getJournalCompactPercentage(), serverControl.getJournalCompactPercentage());
      assertEquals(conf.isPersistenceEnabled(), serverControl.isPersistenceEnabled());
      assertEquals(conf.getJournalPoolFiles(), serverControl.getJournalPoolFiles());
      assertNull(conf.getHAPolicyConfiguration());
      assertEquals(conf.getHAPolicyConfiguration(), serverControl.getHAPolicy());
      assertTrue(serverControl.isActive());
   }

   @TestTemplate
   public void testPendingMirrorAcks() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      // faking some data, to make sure we are not just returning a default value
      for (int i = 0; i < 7; i++) {
         server.getMirrorRegistry().incrementMirrorAckSize();
      }

      assertEquals(7, serverControl.getPendingMirrorAcks());
      assertEquals(7, server.getPendingMirrorAcks());
   }

   @TestTemplate
   public void testBrokerPluginClassNames() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      conf.registerBrokerPlugin(new TestBrokerPlugin());
      conf.registerBrokerPlugin(new ActiveMQServerPlugin() {
         @Override
         public void registered(ActiveMQServer server) {
         }
      });

      assertEquals(2, conf.getBrokerPlugins().size());
      assertEquals(2, serverControl.getBrokerPluginClassNames().length);
      assertEquals(
         "org.apache.activemq.artemis.tests.integration.management.ActiveMQServerControlTest.TestBrokerPlugin",
         serverControl.getBrokerPluginClassNames()[0]);
      assertTrue(Pattern.matches(
         "org.apache.activemq.artemis.tests.integration.management.ActiveMQServerControlTest\\$\\d+$",
         serverControl.getBrokerPluginClassNames()[1]
      ));
   }

   @TestTemplate
   public void testSecurityCacheSizes() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      Wait.assertEquals(usingCore() ? 1 : 0, serverControl::getAuthenticationCacheSize);
      Wait.assertEquals(0, serverControl::getAuthorizationCacheSize);

      ServerLocator loc = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(loc);
      ClientSession session = csf.createSession("myUser", "myPass", false, true, false, false, 0);
      session.start();

      final String address = "ADDRESS";

      serverControl.createAddress(address, "MULTICAST");

      ClientProducer producer = session.createProducer(address);

      ClientMessage m = session.createMessage(true);
      m.putStringProperty("hello", "world");
      producer.send(m);

      assertEquals(usingCore() ? 2 : 1, serverControl.getAuthenticationCacheSize());
      Wait.assertEquals(1, () -> serverControl.getAuthorizationCacheSize());
   }

   @TestTemplate
   public void testCurrentTime() throws Exception {
      long time = System.currentTimeMillis();
      ActiveMQServerControl serverControl = createManagementControl();
      assertTrue(serverControl.getCurrentTimeMillis() >= time, "serverControl returned an invalid time.");
   }

   @TestTemplate
   public void testClearingSecurityCaches() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator loc = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(loc);
      ClientSession session = csf.createSession("myUser", "myPass", false, true, false, false, 0);
      session.start();

      final String address = "ADDRESS";
      serverControl.createAddress(address, "MULTICAST");
      ClientProducer producer = session.createProducer(address);
      ClientMessage m = session.createMessage(true);
      m.putStringProperty("hello", "world");
      producer.send(m);

      assertTrue(serverControl.getAuthenticationCacheSize() > 0);
      Wait.assertTrue(() -> serverControl.getAuthorizationCacheSize() > 0);

      serverControl.clearAuthenticationCache();
      serverControl.clearAuthorizationCache();

      assertEquals(usingCore() ? 1 : 0, serverControl.getAuthenticationCacheSize());
      assertEquals(0, serverControl.getAuthorizationCacheSize());
   }

   @TestTemplate
   public void testAuthCounts() throws Exception {
      // don't test this with management messages as it completely throws off the auth counts
      assumeFalse(usingCore());

      ActiveMQServerControl serverControl = createManagementControl();

      assertEquals(0, serverControl.getAuthenticationSuccessCount());
      assertEquals(0, serverControl.getAuthenticationFailureCount());
      assertEquals(0, serverControl.getAuthorizationSuccessCount());
      assertEquals(0, serverControl.getAuthorizationFailureCount());

      ServerLocator loc = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(loc);
      ClientSession successSession = csf.createSession("myUser", "myPass", false, true, false, false, 0);
      successSession.start();

      final String address = "ADDRESS";
      serverControl.createAddress(address, "MULTICAST");
      ClientProducer producer = successSession.createProducer(address);
      ClientMessage m = successSession.createMessage(true);
      m.putStringProperty("hello", "world");
      producer.send(m);

      assertEquals(1, serverControl.getAuthenticationSuccessCount());
      assertEquals(0, serverControl.getAuthenticationFailureCount());
      assertEquals(1, serverControl.getAuthorizationSuccessCount());
      assertEquals(0, serverControl.getAuthorizationFailureCount());

      final String queue = "QUEUE";
      server.createQueue(QueueConfiguration.of(queue).setAddress(address).setRoutingType(RoutingType.MULTICAST));
      ClientSession failedAuthzSession = csf.createSession("none", "none", false, true, false, false, 0);
      try {
         failedAuthzSession.createConsumer(queue);
      } catch (ActiveMQSecurityException e) {
         // expected
      }

      assertEquals(2, serverControl.getAuthenticationSuccessCount());
      assertEquals(0, serverControl.getAuthenticationFailureCount());
      assertEquals(1, serverControl.getAuthorizationSuccessCount());
      assertEquals(1, serverControl.getAuthorizationFailureCount());

      try {
         csf.createSession("none", "badpassword", false, true, false, false, 0);
      } catch (ActiveMQSecurityException e) {
         // expected
      }
      assertEquals(2, serverControl.getAuthenticationSuccessCount());
      assertEquals(1, serverControl.getAuthenticationFailureCount());
      assertEquals(1, serverControl.getAuthorizationSuccessCount());
      assertEquals(1, serverControl.getAuthorizationFailureCount());
   }

   @TestTemplate
   public void testExportAsProperties() throws Exception {

      ActiveMQServerControl serverControl = createManagementControl();

      serverControl.exportConfigAsProperties();

      File out = new File(System.getProperty(TMP_DIR_SYSTEM_PROPERTY), ActiveMQServerControlImpl.CONFIG_AS_PROPERTIES_FILE);
      assertTrue(out.exists());
      try (FileReader reader = new FileReader(out)) {
         Properties properties = new Properties();
         properties.load(reader);
         assertEquals(properties.getProperty("name"), serverControl.getName());
      }
   }

   @TestTemplate
   public void testGetConnectors() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      Object[] connectorData = serverControl.getConnectors();
      assertNotNull(connectorData);
      assertEquals(1, connectorData.length);

      Object[] config = (Object[]) connectorData[0];

      assertEquals(connectorConfig.getName(), config[0]);
   }

   @TestTemplate
   public void testGetAcceptors() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      Object[] acceptors = serverControl.getAcceptors();
      assertNotNull(acceptors);
      assertEquals(2, acceptors.length);

      for (int i = 0; i < acceptors.length; i++) {
         Object[] acceptor = (Object[]) acceptors[i];
         String name = (String) acceptor[0];
         assertTrue(name.equals("netty") || name.equals("invm"));
      }
   }

   @TestTemplate
   public void testIsReplicaSync() throws Exception {
      assertFalse(createManagementControl().isReplicaSync());
   }

   @TestTemplate
   public void testGetConnectorsAsJSON() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      String jsonString = serverControl.getConnectorsAsJSON();
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      assertEquals(1, array.size());
      JsonObject data = array.getJsonObject(0);
      assertEquals(connectorConfig.getName(), data.getString("name"));
      assertEquals(connectorConfig.getFactoryClassName(), data.getString("factoryClassName"));
      assertEquals(connectorConfig.getParams().size(), data.getJsonObject("params").size());
   }

   @TestTemplate
   public void testGetAcceptorsAsJSON() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      String jsonString = serverControl.getAcceptorsAsJSON();
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      assertEquals(2, array.size());
      for (int i = 0; i < array.size(); i++) {
         String name = ((JsonObject)array.get(i)).getString("name");
         assertTrue(name.equals("netty") || name.equals("invm"));
      }
   }

   @TestTemplate
   public void testCreateAndDestroyQueue() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, true, -1, false, false);
      } else {
         serverControl.createQueue(QueueConfiguration.of(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setAutoCreateAddress(false).toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      assertEquals(address.toString(), queueControl.getAddress());
      assertEquals(name.toString(), queueControl.getName());
      assertNull(queueControl.getFilter());
      assertTrue(queueControl.isDurable());
      assertFalse(queueControl.isTemporary());

      serverControl.destroyQueue(name.toString());

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
   }

   @TestTemplate
   public void testCreateQueueWithNullAddress() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = address;

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         serverControl.createQueue(null, name.toString(), "ANYCAST");
      } else {
         serverControl.createQueue(QueueConfiguration.of(name).setRoutingType(RoutingType.ANYCAST).toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(name, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      assertEquals(address.toString(), queueControl.getAddress());
      assertEquals(name.toString(), queueControl.getName());
      assertNull(queueControl.getFilter());
      assertTrue(queueControl.isDurable());
      assertFalse(queueControl.isTemporary());

      serverControl.destroyQueue(name.toString());

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
   }

   @TestTemplate
   public void testCreateAndDestroyQueue_2() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();
      String filter = "color = 'green'";
      boolean durable = true;

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), filter, durable, -1, false, false);
      } else {
         serverControl.createQueue(QueueConfiguration.of(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setFilterString(filter).setAutoCreateAddress(false).toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      assertEquals(address.toString(), queueControl.getAddress());
      assertEquals(name.toString(), queueControl.getName());
      assertEquals(filter, queueControl.getFilter());
      assertEquals(durable, queueControl.isDurable());
      assertFalse(queueControl.isTemporary());

      serverControl.destroyQueue(name.toString());

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
   }

   @TestTemplate
   public void testCreateAndDestroyQueue_3() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();
      boolean durable = true;

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, durable, -1, false, false);
      } else {
         serverControl.createQueue(QueueConfiguration.of(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(durable).setAutoCreateAddress(false).toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      assertEquals(address.toString(), queueControl.getAddress());
      assertEquals(name.toString(), queueControl.getName());
      assertNull(queueControl.getFilter());
      assertEquals(durable, queueControl.isDurable());
      assertFalse(queueControl.isTemporary());

      serverControl.destroyQueue(name.toString());

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
   }

   @TestTemplate
   public void testCreateAndDestroyQueue_4() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();
      boolean durable = RandomUtil.randomBoolean();
      boolean purgeOnNoConsumers = RandomUtil.randomBoolean();
      boolean autoCreateAddress = true;
      int maxConsumers = RandomUtil.randomInt();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), RoutingType.ANYCAST.toString(), name.toString(), null, durable, maxConsumers, purgeOnNoConsumers, autoCreateAddress);
      } else {
         serverControl.createQueue(QueueConfiguration.of(name)
                                      .setAddress(address)
                                      .setRoutingType(RoutingType.ANYCAST)
                                      .setDurable(durable)
                                      .setMaxConsumers(maxConsumers)
                                      .setPurgeOnNoConsumers(purgeOnNoConsumers)
                                      .setAutoCreateAddress(autoCreateAddress)
                                      .toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      assertEquals(address.toString(), queueControl.getAddress());
      assertEquals(name.toString(), queueControl.getName());
      assertNull(queueControl.getFilter());
      assertEquals(durable, queueControl.isDurable());
      assertEquals(purgeOnNoConsumers, queueControl.isPurgeOnNoConsumers());
      assertEquals(maxConsumers, queueControl.getMaxConsumers());
      assertFalse(queueControl.isTemporary());

      checkResource(ObjectNameBuilder.DEFAULT.getAddressObjectName(address));
      AddressControl addressControl = ManagementControlHelper.createAddressControl(address, mbeanServer);
      assertEquals(address.toString(), addressControl.getAddress());

      serverControl.destroyQueue(name.toString(), true, true);

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      checkNoResource(ObjectNameBuilder.DEFAULT.getAddressObjectName(address));
   }

   @TestTemplate
   public void testCreateAndDestroyQueue_5() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();
      boolean durable = RandomUtil.randomBoolean();
      int maxConsumers = RandomUtil.randomInt();
      boolean purgeOnNoConsumers = RandomUtil.randomBoolean();
      boolean exclusive = RandomUtil.randomBoolean();
      boolean groupRebalance = RandomUtil.randomBoolean();
      int groupBuckets = 1;
      String groupFirstKey = RandomUtil.randomUUIDSimpleString().toString();
      boolean lastValue = false;
      String lastValueKey = null;
      boolean nonDestructive = RandomUtil.randomBoolean();
      int consumersBeforeDispatch = RandomUtil.randomInt();
      long delayBeforeDispatch = RandomUtil.randomLong();
      boolean autoDelete = RandomUtil.randomBoolean();
      long autoDeleteDelay = RandomUtil.randomLong();
      long autoDeleteMessageCount = RandomUtil.randomLong();
      boolean autoCreateAddress = true;
      long ringSize = RandomUtil.randomLong();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), RoutingType.ANYCAST.toString(), name.toString(), null, durable, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, groupFirstKey, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreateAddress, ringSize);
      } else {
         serverControl.createQueue(QueueConfiguration.of(name)
                                      .setAddress(address)
                                      .setRoutingType(RoutingType.ANYCAST)
                                      .setDurable(durable)
                                      .setMaxConsumers(maxConsumers)
                                      .setPurgeOnNoConsumers(purgeOnNoConsumers)
                                      .setExclusive(exclusive)
                                      .setGroupRebalance(groupRebalance)
                                      .setGroupBuckets(groupBuckets)
                                      .setGroupFirstKey(groupFirstKey)
                                      .setLastValue(lastValue)
                                      .setLastValueKey(lastValueKey)
                                      .setNonDestructive(nonDestructive)
                                      .setConsumersBeforeDispatch(consumersBeforeDispatch)
                                      .setDelayBeforeDispatch(delayBeforeDispatch)
                                      .setAutoDelete(autoDelete)
                                      .setAutoDeleteDelay(autoDeleteDelay)
                                      .setAutoDeleteMessageCount(autoDeleteMessageCount)
                                      .setRingSize(ringSize)
                                      .setAutoCreateAddress(autoCreateAddress)
                                      .toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      assertEquals(address.toString(), queueControl.getAddress());
      assertEquals(name.toString(), queueControl.getName());
      assertNull(queueControl.getFilter());
      assertEquals(durable, queueControl.isDurable());
      assertEquals(maxConsumers, queueControl.getMaxConsumers());
      assertEquals(purgeOnNoConsumers, queueControl.isPurgeOnNoConsumers());
      assertFalse(queueControl.isTemporary());
      assertEquals(exclusive, queueControl.isExclusive());
      assertEquals(groupRebalance, queueControl.isGroupRebalance());
      assertEquals(groupBuckets, queueControl.getGroupBuckets());
      assertEquals(groupFirstKey, queueControl.getGroupFirstKey());
      assertEquals(lastValue, queueControl.isLastValue());
//      assertEquals(lastValueKey, queueControl.getLastValueKey());
//      assertEquals(nonDestructive, queueControl.isNonDestructive());
//      assertEquals(consumersBeforeDispatch, queueControl.getConsumersBeforeDispatch());
//      assertEquals(delayBeforeDispatch, queueControl.getDelayBeforeDispatch());
//      assertEquals(autoDelete, queueControl.isAutoDelete());
//      assertEquals(autoDeleteDelay, queueControl.getAutoDeleteDelay());
//      assertEquals(autoDeleteMessageCount, queueControl.autoDeleteMessageCount());
      assertEquals(ringSize, queueControl.getRingSize());

      checkResource(ObjectNameBuilder.DEFAULT.getAddressObjectName(address));
      AddressControl addressControl = ManagementControlHelper.createAddressControl(address, mbeanServer);
      assertEquals(address.toString(), addressControl.getAddress());

      serverControl.destroyQueue(name.toString(), true, true);

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      checkNoResource(ObjectNameBuilder.DEFAULT.getAddressObjectName(address));
   }

   @TestTemplate
   public void testCreateAndDestroyQueueWithAutoDeleteAddress() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoDeleteAddresses(false));
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, true, -1, false, true);
      } else {
         serverControl.createQueue(QueueConfiguration.of(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setAutoCreateAddress(true).toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      assertEquals(address.toString(), queueControl.getAddress());
      assertEquals(name.toString(), queueControl.getName());
      assertNull(queueControl.getFilter());
      assertTrue(queueControl.isDurable());
      assertFalse(queueControl.isTemporary());

      serverControl.destroyQueue(name.toString(), false, true);

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      checkNoResource(ObjectNameBuilder.DEFAULT.getAddressObjectName(address));
   }

   @TestTemplate
   public void testRemoveQueueFilter() throws Exception {

      String address = RandomUtil.randomUUIDString();
      QueueConfiguration queue1 = QueueConfiguration.of("q1")
              .setAddress(address)
              .setFilterString("hello='world'");

      QueueConfiguration queue2 = QueueConfiguration.of("q2")
              .setAddress(address)
              .setFilterString("hello='darling'");

      ActiveMQServerControl serverControl = createManagementControl();
      serverControl.createAddress(address, "MULTICAST");

      if (legacyCreateQueue) {
         serverControl.createQueue(address, queue1.getName().toString(), queue1.getFilterString().toString(), queue1.isDurable());
         serverControl.createQueue(address, queue2.getName().toString(), queue2.getFilterString().toString(), queue2.isDurable());
      } else {
         serverControl.createQueue(queue1.toJSON());
         serverControl.createQueue(queue2.toJSON());
      }

      ServerLocator loc = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(loc);
      ClientSession session = csf.createSession();
      session.start();

      ClientProducer producer = session.createProducer(address);
      ClientConsumer consumer1 = session.createConsumer("q1");
      ClientConsumer consumer2 = session.createConsumer("q2");

      ClientMessage m = session.createMessage(true);
      m.putStringProperty("hello", "world");
      producer.send(m);

      assertNotNull(consumer1.receiveImmediate());
      assertNull(consumer2.receiveImmediate());

      serverControl.updateQueue(queue2.setFilterString((String) null).toJSON());

      producer.send(m);

      assertNotNull(consumer1.receive(1000));
      assertNotNull(consumer2.receive(1000));
   }

   @TestTemplate
   public void testCreateAndDestroyQueueClosingConsumers() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();
      boolean durable = true;

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, durable, -1, false, false);
      } else {
         serverControl.createQueue(QueueConfiguration.of(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(durable).setAutoCreateAddress(false).toJSON());
      }

      ServerLocator receiveLocator = createInVMNonHALocator();
      ClientSessionFactory receiveCsf = createSessionFactory(receiveLocator);
      ClientSession receiveClientSession = receiveCsf.createSession(true, false, false);
      final ClientConsumer consumer = receiveClientSession.createConsumer(name);

      assertFalse(consumer.isClosed());

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.destroyQueue(name.toString(), true);
      Wait.waitFor(() -> consumer.isClosed(), 1000, 100);
      assertTrue(consumer.isClosed());

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
   }

   @TestTemplate
   public void testCreateAndDestroyQueueWithNullFilter() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();
      String filter = null;
      boolean durable = true;

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), filter, durable, -1, false, false);
      } else {
         serverControl.createQueue(QueueConfiguration.of(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(durable).setAutoCreateAddress(false).toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      assertEquals(address.toString(), queueControl.getAddress());
      assertEquals(name.toString(), queueControl.getName());
      assertNull(queueControl.getFilter());
      assertEquals(durable, queueControl.isDurable());
      assertFalse(queueControl.isTemporary());

      serverControl.destroyQueue(name.toString());

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
   }

   @TestTemplate
   public void testCreateAndDestroyQueueWithEmptyStringForFilter() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();
      String filter = "";
      boolean durable = true;

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), filter, durable, -1, false, false);
      } else {
         serverControl.createQueue(QueueConfiguration.of(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setFilterString(filter).setDurable(durable).setAutoCreateAddress(false).toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      assertEquals(address.toString(), queueControl.getAddress());
      assertEquals(name.toString(), queueControl.getName());
      assertNull(queueControl.getFilter());
      assertEquals(durable, queueControl.isDurable());
      assertFalse(queueControl.isTemporary());

      serverControl.destroyQueue(name.toString());

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
   }

   @TestTemplate
   public void testCreateAndUpdateQueueWithoutFilter() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      serverControl.createQueue(QueueConfiguration.of(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setAutoCreateAddress(true).setFilterString((String) null).toJSON());
      serverControl.updateQueue(QueueConfiguration.of(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setMaxConsumers(1).toJSON());

      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      assertEquals(address.toString(), queueControl.getAddress());
      assertEquals(name.toString(), queueControl.getName());
      assertNull(queueControl.getFilter());
      assertEquals(1, queueControl.getMaxConsumers());

      serverControl.destroyQueue(name.toString());
   }

   @TestTemplate
   public void testCreateAndLegacyUpdateQueueRingSize() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      serverControl.createQueue(QueueConfiguration.of(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setAutoCreateAddress(true).toJSON());
      serverControl.updateQueue(name.toString(),
                                RoutingType.ANYCAST.toString(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                101L);

      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      assertEquals(address.toString(), queueControl.getAddress());
      assertEquals(name.toString(), queueControl.getName());
      assertEquals(101, queueControl.getRingSize());

      serverControl.destroyQueue(name.toString());
   }

   @TestTemplate
   public void testGetQueueCount() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      // due to replication, there can be another queue created for replicating
      // management operations
      assertFalse(ActiveMQServerControlTest.contains(name.toString(), serverControl.getQueueNames()));

      int countBeforeCreate = serverControl.getQueueCount();

      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, true, -1, false, false);
      } else {
         serverControl.createQueue(QueueConfiguration.of(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(true).setAutoCreateAddress(false).toJSON());
      }

      assertTrue(countBeforeCreate < serverControl.getQueueCount());

      serverControl.destroyQueue(name.toString());
      assertFalse(ActiveMQServerControlTest.contains(name.toString(), serverControl.getQueueNames()));
   }

   @TestTemplate
   public void testGetQueueNames() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      // due to replication, there can be another queue created for replicating
      // management operations

      assertFalse(ActiveMQServerControlTest.contains(name.toString(), serverControl.getQueueNames()));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, true, -1, false, false);
      } else {
         serverControl.createQueue(QueueConfiguration.of(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(true).setAutoCreateAddress(false).toJSON());
      }
      assertTrue(ActiveMQServerControlTest.contains(name.toString(), serverControl.getQueueNames()));

      serverControl.destroyQueue(name.toString());
      assertFalse(ActiveMQServerControlTest.contains(name.toString(), serverControl.getQueueNames()));
   }

   @TestTemplate
   public void testGetQueueNamesWithRoutingType() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString anycastName = RandomUtil.randomUUIDSimpleString();
      SimpleString multicastName = RandomUtil.randomUUIDSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      // due to replication, there can be another queue created for replicating
      // management operations

      assertFalse(ActiveMQServerControlTest.contains(anycastName.toString(), serverControl.getQueueNames()));
      assertFalse(ActiveMQServerControlTest.contains(multicastName.toString(), serverControl.getQueueNames()));

      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), RoutingType.ANYCAST.toString(), anycastName.toString(), null, true, -1, false, true);
      } else {
         serverControl.createQueue(QueueConfiguration.of(anycastName).setAddress(address).setRoutingType(RoutingType.ANYCAST).toJSON());
      }
      assertTrue(ActiveMQServerControlTest.contains(anycastName.toString(), serverControl.getQueueNames(RoutingType.ANYCAST.toString())));
      assertFalse(ActiveMQServerControlTest.contains(anycastName.toString(), serverControl.getQueueNames(RoutingType.MULTICAST.toString())));

      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), RoutingType.MULTICAST.toString(), multicastName.toString(), null, true, -1, false, true);
      } else {
         serverControl.createQueue(QueueConfiguration.of(multicastName).setAddress(address).setRoutingType(RoutingType.MULTICAST).toJSON());
      }
      assertTrue(ActiveMQServerControlTest.contains(multicastName.toString(), serverControl.getQueueNames(RoutingType.MULTICAST.toString())));
      assertFalse(ActiveMQServerControlTest.contains(multicastName.toString(), serverControl.getQueueNames(RoutingType.ANYCAST.toString())));

      serverControl.destroyQueue(anycastName.toString());
      serverControl.destroyQueue(multicastName.toString());
      assertFalse(ActiveMQServerControlTest.contains(anycastName.toString(), serverControl.getQueueNames()));
      assertFalse(ActiveMQServerControlTest.contains(multicastName.toString(), serverControl.getQueueNames()));
   }

   @TestTemplate
   public void testGetClusterConnectionNames() throws Exception {
      String clusterConnection1 = RandomUtil.randomUUIDString();
      String clusterConnection2 = RandomUtil.randomUUIDString();

      ActiveMQServerControl serverControl = createManagementControl();

      assertFalse(ActiveMQServerControlTest.contains(clusterConnection1, serverControl.getClusterConnectionNames()));
      assertFalse(ActiveMQServerControlTest.contains(clusterConnection2, serverControl.getClusterConnectionNames()));

      server.stop();
      server
         .getConfiguration()
         .addClusterConfiguration(new ClusterConnectionConfiguration().setName(clusterConnection1).setConnectorName(connectorConfig.getName()))
         .addClusterConfiguration(new ClusterConnectionConfiguration().setName(clusterConnection2).setConnectorName(connectorConfig.getName()));
      server.start();

      assertTrue(ActiveMQServerControlTest.contains(clusterConnection1, serverControl.getClusterConnectionNames()));
      assertTrue(ActiveMQServerControlTest.contains(clusterConnection2, serverControl.getClusterConnectionNames()));
   }

   @TestTemplate
   public void testGetAddressCount() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      // due to replication, there can be another queue created for replicating
      // management operations
      assertFalse(ActiveMQServerControlTest.contains(address.toString(), serverControl.getAddressNames()));

      int countBeforeCreate = serverControl.getAddressCount();

      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, true, -1, false, true);
      } else {
         serverControl.createQueue(QueueConfiguration.of(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).toJSON());
      }

      assertTrue(countBeforeCreate < serverControl.getAddressCount());

      serverControl.destroyQueue(name.toString(), true, true);
      Wait.assertFalse(() -> ActiveMQServerControlTest.contains(address.toString(), serverControl.getAddressNames()));
   }

   @TestTemplate
   public void testGetAddressNames() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      // due to replication, there can be another queue created for replicating
      // management operations

      assertFalse(ActiveMQServerControlTest.contains(address.toString(), serverControl.getAddressNames()));
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, true, -1, false, true);
      } else {
         serverControl.createQueue(QueueConfiguration.of(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).toJSON());
      }
      assertTrue(ActiveMQServerControlTest.contains(address.toString(), serverControl.getAddressNames()));

      serverControl.destroyQueue(name.toString(), true, true);
      Wait.assertFalse(() -> ActiveMQServerControlTest.contains(address.toString(), serverControl.getAddressNames()));
   }

   @TestTemplate
   public void testGetAddressDeletedFromJournal() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      // due to replication, there can be another queue created for replicating
      // management operations

      assertFalse(ActiveMQServerControlTest.contains(address.toString(), serverControl.getAddressNames()));
      serverControl.createAddress(address.toString(), "ANYCAST");
      assertTrue(ActiveMQServerControlTest.contains(address.toString(), serverControl.getAddressNames()));

      restartServer();

      serverControl.deleteAddress(address.toString());

      restartServer();

      assertFalse(ActiveMQServerControlTest.contains(address.toString(), serverControl.getAddressNames()));
   }

   @TestTemplate
   public void testMessageCounterMaxDayCount() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      assertEquals(MessageCounterManagerImpl.DEFAULT_MAX_DAY_COUNT, serverControl.getMessageCounterMaxDayCount());

      int newCount = 100;
      serverControl.setMessageCounterMaxDayCount(newCount);

      assertEquals(newCount, serverControl.getMessageCounterMaxDayCount());

      try {
         serverControl.setMessageCounterMaxDayCount(-1);
         fail();
      } catch (Exception e) {
      }

      try {
         serverControl.setMessageCounterMaxDayCount(0);
         fail();
      } catch (Exception e) {
      }

      assertEquals(newCount, serverControl.getMessageCounterMaxDayCount());
   }

   @TestTemplate
   public void testGetMessageCounterSamplePeriod() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      assertEquals(MessageCounterManagerImpl.DEFAULT_SAMPLE_PERIOD, serverControl.getMessageCounterSamplePeriod());

      long newSample = 20000;
      serverControl.setMessageCounterSamplePeriod(newSample);

      assertEquals(newSample, serverControl.getMessageCounterSamplePeriod());

      try {
         serverControl.setMessageCounterSamplePeriod(-1);
         fail();
      } catch (Exception e) {
      }

      try {
         serverControl.setMessageCounterSamplePeriod(0);
         fail();
      } catch (Exception e) {
      }

      //this only gets warning now and won't cause exception.
      serverControl.setMessageCounterSamplePeriod(MessageCounterManagerImpl.MIN_SAMPLE_PERIOD - 1);

      assertEquals(MessageCounterManagerImpl.MIN_SAMPLE_PERIOD - 1, serverControl.getMessageCounterSamplePeriod());
   }

   protected void restartServer() throws Exception {
      server.stop();
      server.start();
   }

   @TestTemplate
   public void testSecuritySettings() throws Exception {
      testSecuritySettings(false);
   }


   @TestTemplate
   public void testSecuritySettingsJson() throws Exception {
      testSecuritySettings(true);
   }

   private void testSecuritySettings(boolean json) throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      String addressMatch = "test.#";
      String exactAddress = "test.whatever";

      assertEquals(2, serverControl.getRoles(addressMatch).length);
      final String sendRoles = "foo";
      final String consumeRoles = "foo, bar";
      final String createDurableQueueRoles = null;
      final String deleteDurableQueueRoles = "bar";
      final String createNonDurableQueueRoles = "foo, bar";
      final String deleteNonDurableQueueRoles = "";
      final String manageRoles = "";
      final String browseRoles = "bar";
      final String createAddressRoles = "foo";
      final String deleteAddressRoles = "foo";
      final String viewRoles = "";
      final String editRoles = "";

      if (json) {
         serverControl.addSecuritySettings(addressMatch,
                                           SecurityFormatter.toJSON(sendRoles,
                                                                    consumeRoles,
                                                                    createDurableQueueRoles,
                                                                    deleteDurableQueueRoles,
                                                                    createNonDurableQueueRoles,
                                                                    deleteNonDurableQueueRoles,
                                                                    manageRoles,
                                                                    browseRoles,
                                                                    createAddressRoles,
                                                                    deleteAddressRoles,
                                                                    viewRoles,
                                                                    editRoles));
      } else {
         serverControl.addSecuritySettings(addressMatch,
                                           sendRoles,
                                           consumeRoles,
                                           createDurableQueueRoles,
                                           deleteDurableQueueRoles,
                                           createNonDurableQueueRoles,
                                           deleteNonDurableQueueRoles,
                                           manageRoles,
                                           browseRoles,
                                           createAddressRoles,
                                           deleteAddressRoles,
                                           viewRoles,
                                           editRoles);
      }

      // Restart the server. Those settings should be persisted

      restartServer();

      serverControl = createManagementControl();

      String rolesAsJSON = serverControl.getRolesAsJSON(exactAddress);
      RoleInfo[] roleInfos = RoleInfo.from(rolesAsJSON);
      assertEquals(2, roleInfos.length);
      RoleInfo fooRoleInfo = null;
      RoleInfo barRoleInfo = null;
      if ("foo".equals(roleInfos[0].getName())) {
         fooRoleInfo = roleInfos[0];
         barRoleInfo = roleInfos[1];
      } else {
         fooRoleInfo = roleInfos[1];
         barRoleInfo = roleInfos[0];
      }
      assertTrue(fooRoleInfo.isSend());
      assertTrue(fooRoleInfo.isConsume());
      assertFalse(fooRoleInfo.isCreateDurableQueue());
      assertFalse(fooRoleInfo.isDeleteDurableQueue());
      assertTrue(fooRoleInfo.isCreateNonDurableQueue());
      assertFalse(fooRoleInfo.isDeleteNonDurableQueue());
      assertFalse(fooRoleInfo.isManage());
      assertFalse(fooRoleInfo.isBrowse());
      assertTrue(fooRoleInfo.isCreateAddress());
      assertTrue(fooRoleInfo.isDeleteAddress());

      assertFalse(barRoleInfo.isSend());
      assertTrue(barRoleInfo.isConsume());
      assertFalse(barRoleInfo.isCreateDurableQueue());
      assertTrue(barRoleInfo.isDeleteDurableQueue());
      assertTrue(barRoleInfo.isCreateNonDurableQueue());
      assertFalse(barRoleInfo.isDeleteNonDurableQueue());
      assertFalse(barRoleInfo.isManage());
      assertTrue(barRoleInfo.isBrowse());
      assertFalse(barRoleInfo.isCreateAddress());
      assertFalse(barRoleInfo.isDeleteAddress());

      Object[] roles = serverControl.getRoles(exactAddress);
      assertEquals(2, roles.length);
      Object[] fooRole = null;
      Object[] barRole = null;
      if ("foo".equals(((Object[])roles[0])[0])) {
         fooRole = (Object[]) roles[0];
         barRole = (Object[]) roles[1];
      } else {
         fooRole = (Object[]) roles[1];
         barRole = (Object[]) roles[0];
      }
      assertEquals(CheckType.values().length + 1, fooRole.length);
      assertEquals(CheckType.values().length + 1, barRole.length);

      assertTrue((boolean)fooRole[1]);
      assertTrue((boolean)fooRole[2]);
      assertFalse((boolean)fooRole[3]);
      assertFalse((boolean)fooRole[4]);
      assertTrue((boolean)fooRole[5]);
      assertFalse((boolean)fooRole[6]);
      assertFalse((boolean)fooRole[7]);
      assertFalse((boolean)fooRole[8]);
      assertTrue((boolean)fooRole[9]);
      assertTrue((boolean)fooRole[10]);

      assertFalse((boolean)barRole[1]);
      assertTrue((boolean)barRole[2]);
      assertFalse((boolean)barRole[3]);
      assertTrue((boolean)barRole[4]);
      assertTrue((boolean)barRole[5]);
      assertFalse((boolean)barRole[6]);
      assertFalse((boolean)barRole[7]);
      assertTrue((boolean)barRole[8]);
      assertFalse((boolean)barRole[9]);
      assertFalse((boolean)barRole[10]);

      serverControl.removeSecuritySettings(addressMatch);
      assertEquals(2, serverControl.getRoles(exactAddress).length);
   }

   @TestTemplate
   public void mergeAddressSettings() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setDeadLetterAddress(SimpleString.of("DLA"));
      String returnedSettings = serverControl.addAddressSettings("foo", addressSettings.toJSON());
      AddressSettings info = AddressSettings.fromJSON(returnedSettings);
      assertEquals(addressSettings.getDeadLetterAddress(), info.getDeadLetterAddress());
      assertNull(info.getExpiryAddress());
      assertEquals(0, addressSettings.getRedeliveryDelay());

      addressSettings.setExpiryAddress(SimpleString.of("EA"));
      returnedSettings = serverControl.addAddressSettings("foo", addressSettings.toJSON());
      info = AddressSettings.fromJSON(returnedSettings);
      assertEquals(addressSettings.getDeadLetterAddress(), info.getDeadLetterAddress());
      assertEquals(addressSettings.getExpiryAddress(), info.getExpiryAddress());

      addressSettings.setRedeliveryDelay(1000);
      returnedSettings = serverControl.addAddressSettings("foo", addressSettings.toJSON());
      info = AddressSettings.fromJSON(returnedSettings);
      assertEquals(addressSettings.getDeadLetterAddress(), info.getDeadLetterAddress());
      assertEquals(addressSettings.getExpiryAddress(), info.getExpiryAddress());
      assertEquals(addressSettings.getRedeliveryDelay(), info.getRedeliveryDelay());

      addressSettings.setInitialQueueBufferSize(64);
      returnedSettings = serverControl.addAddressSettings("foo", addressSettings.toJSON());
      info = AddressSettings.fromJSON(returnedSettings);
      assertEquals(addressSettings.getDeadLetterAddress(), info.getDeadLetterAddress());
      assertEquals(addressSettings.getExpiryAddress(), info.getExpiryAddress());
      assertEquals(addressSettings.getRedeliveryDelay(), info.getRedeliveryDelay());
      assertEquals(addressSettings.getInitialQueueBufferSize(), info.getInitialQueueBufferSize());
   }
   @TestTemplate
   public void emptyAddressSettings() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      AddressSettings addressSettings = new AddressSettings();
      String returnedSettings = serverControl.addAddressSettings("foo", addressSettings.toJSON());
      AddressSettings info = AddressSettings.fromJSON(returnedSettings);

      assertEquals(addressSettings.getDeadLetterAddress(), info.getDeadLetterAddress());
      assertEquals(addressSettings.getExpiryAddress(), info.getExpiryAddress());
      assertEquals(addressSettings.getExpiryDelay(), info.getExpiryDelay());
      assertEquals(addressSettings.getMinExpiryDelay(), info.getMinExpiryDelay());
      assertEquals(addressSettings.getMaxExpiryDelay(), info.getMaxExpiryDelay());
      assertEquals(addressSettings.isDefaultLastValueQueue(), info.isDefaultLastValueQueue());
      assertEquals(addressSettings.getMaxDeliveryAttempts(), info.getMaxDeliveryAttempts());
      assertEquals(addressSettings.getMaxSizeBytes(), info.getMaxSizeBytes());
      assertEquals(addressSettings.getPageCacheMaxSize(), info.getPageCacheMaxSize());
      assertEquals(addressSettings.getPageSizeBytes(), info.getPageSizeBytes());
      assertEquals(addressSettings.getRedeliveryDelay(), info.getRedeliveryDelay());
      assertEquals(addressSettings.getRedeliveryMultiplier(), info.getRedeliveryMultiplier(), 0.000001);
      assertEquals(addressSettings.getMaxRedeliveryDelay(), info.getMaxRedeliveryDelay());
      assertEquals(addressSettings.getRedistributionDelay(), info.getRedistributionDelay());
      assertEquals(addressSettings.isSendToDLAOnNoRoute(), info.isSendToDLAOnNoRoute());
      assertEquals(addressSettings.getAddressFullMessagePolicy(), info.getAddressFullMessagePolicy());
      assertEquals(addressSettings.getSlowConsumerThreshold(), info.getSlowConsumerThreshold());
      assertEquals(addressSettings.getSlowConsumerCheckPeriod(), info.getSlowConsumerCheckPeriod());
      assertEquals(addressSettings.getSlowConsumerPolicy(), info.getSlowConsumerPolicy());
      assertEquals(addressSettings.isAutoCreateQueues(), info.isAutoCreateQueues());
      assertEquals(addressSettings.isAutoDeleteQueues(), info.isAutoDeleteQueues());
      assertEquals(addressSettings.isAutoCreateAddresses(), info.isAutoCreateAddresses());
      assertEquals(addressSettings.isAutoDeleteAddresses(), info.isAutoDeleteAddresses());
      assertEquals(addressSettings.getConfigDeleteQueues(), info.getConfigDeleteQueues());
      assertEquals(addressSettings.getConfigDeleteAddresses(), info.getConfigDeleteAddresses());
      assertEquals(addressSettings.getMaxSizeBytesRejectThreshold(), info.getMaxSizeBytesRejectThreshold());
      assertEquals(addressSettings.getDefaultLastValueKey(), info.getDefaultLastValueKey());
      assertEquals(addressSettings.isDefaultNonDestructive(), info.isDefaultNonDestructive());
      assertEquals(addressSettings.isDefaultExclusiveQueue(), info.isDefaultExclusiveQueue());
      assertEquals(addressSettings.isDefaultGroupRebalance(), info.isDefaultGroupRebalance());
      assertEquals(addressSettings.getDefaultGroupBuckets(), info.getDefaultGroupBuckets());
      assertEquals(addressSettings.getDefaultGroupFirstKey(), info.getDefaultGroupFirstKey());
      assertEquals(addressSettings.getDefaultMaxConsumers(), info.getDefaultMaxConsumers());
      assertEquals(addressSettings.isDefaultPurgeOnNoConsumers(), info.isDefaultPurgeOnNoConsumers());
      assertEquals(addressSettings.getDefaultConsumersBeforeDispatch(), info.getDefaultConsumersBeforeDispatch());
      assertEquals(addressSettings.getDefaultDelayBeforeDispatch(), info.getDefaultDelayBeforeDispatch());
      assertEquals(addressSettings.getDefaultQueueRoutingType(), info.getDefaultQueueRoutingType());
      assertEquals(addressSettings.getDefaultAddressRoutingType(), info.getDefaultAddressRoutingType());
      assertEquals(addressSettings.getDefaultConsumerWindowSize(), info.getDefaultConsumerWindowSize());
      assertEquals(addressSettings.getDefaultRingSize(), info.getDefaultRingSize());
      assertEquals(addressSettings.isAutoDeleteCreatedQueues(), info.isAutoDeleteCreatedQueues());
      assertEquals(addressSettings.getAutoDeleteQueuesDelay(), info.getAutoDeleteQueuesDelay());
      assertEquals(addressSettings.getAutoDeleteQueuesMessageCount(), info.getAutoDeleteQueuesMessageCount());
      assertEquals(addressSettings.getAutoDeleteAddressesDelay(), info.getAutoDeleteAddressesDelay());
      assertEquals(addressSettings.getRedeliveryCollisionAvoidanceFactor(), info.getRedeliveryCollisionAvoidanceFactor(), 0);
      assertEquals(addressSettings.getRetroactiveMessageCount(), info.getRetroactiveMessageCount());
      assertEquals(addressSettings.isAutoCreateDeadLetterResources(), info.isAutoCreateDeadLetterResources());
      assertEquals(addressSettings.getDeadLetterQueuePrefix(), info.getDeadLetterQueuePrefix());
      assertEquals(addressSettings.getDeadLetterQueueSuffix(), info.getDeadLetterQueueSuffix());
      assertEquals(addressSettings.isAutoCreateExpiryResources(), info.isAutoCreateExpiryResources());
      assertEquals(addressSettings.getExpiryQueuePrefix(), info.getExpiryQueuePrefix());
      assertEquals(addressSettings.getExpiryQueueSuffix(), info.getExpiryQueueSuffix());
      assertEquals(addressSettings.isEnableMetrics(), info.isEnableMetrics());
      assertEquals(addressSettings.getInitialQueueBufferSize(), info.getInitialQueueBufferSize());
   }
   @TestTemplate
   public void testAddressSettings() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      String addressMatch = "test.#";
      String exactAddress = "test.whatever";

      String DLA = "someDLA";
      String expiryAddress = "someExpiry";
      long expiryDelay = RandomUtil.randomPositiveLong();
      long minExpiryDelay = RandomUtil.randomPositiveLong();
      long maxExpiryDelay = RandomUtil.randomPositiveLong();
      boolean lastValueQueue = true;
      int deliveryAttempts = 1;
      long maxSizeBytes = 20;
      int pageSizeBytes = 10;
      int pageMaxCacheSize = 7;
      long redeliveryDelay = 4;
      double redeliveryMultiplier = 1;
      long maxRedeliveryDelay = 1000;
      long redistributionDelay = 5;
      boolean sendToDLAOnNoRoute = true;
      String addressFullMessagePolicy = "PAGE";
      long slowConsumerThreshold = 5;
      long slowConsumerCheckPeriod = 10;
      String slowConsumerPolicy = SlowConsumerPolicy.getType(RandomUtil.randomPositiveInt() % 2).toString();
      boolean autoCreateQueues = RandomUtil.randomBoolean();
      boolean autoDeleteQueues = RandomUtil.randomBoolean();
      boolean autoCreateAddresses = RandomUtil.randomBoolean();
      boolean autoDeleteAddresses = RandomUtil.randomBoolean();
      String configDeleteQueues = DeletionPolicy.getType(RandomUtil.randomPositiveInt() % 2).toString();
      String configDeleteAddresses = DeletionPolicy.getType(RandomUtil.randomPositiveInt() % 2).toString();
      long maxSizeBytesRejectThreshold = RandomUtil.randomPositiveLong();
      String defaultLastValueKey = RandomUtil.randomUUIDString();
      boolean defaultNonDestructive = RandomUtil.randomBoolean();
      boolean defaultExclusiveQueue = RandomUtil.randomBoolean();
      boolean defaultGroupRebalance = RandomUtil.randomBoolean();
      int defaultGroupBuckets = RandomUtil.randomPositiveInt();
      String defaultGroupFirstKey = RandomUtil.randomUUIDString();
      int defaultMaxConsumers = RandomUtil.randomPositiveInt();
      boolean defaultPurgeOnNoConsumers = RandomUtil.randomBoolean();
      int defaultConsumersBeforeDispatch = RandomUtil.randomPositiveInt();
      long defaultDelayBeforeDispatch = RandomUtil.randomPositiveLong();
      String defaultQueueRoutingType = RoutingType.getType((byte) (RandomUtil.randomPositiveInt() % 2)).toString();
      String defaultAddressRoutingType = RoutingType.getType((byte) (RandomUtil.randomPositiveInt() % 2)).toString();
      int defaultConsumerWindowSize = RandomUtil.randomPositiveInt();
      long defaultRingSize = RandomUtil.randomPositiveLong();
      boolean autoDeleteCreatedQueues = RandomUtil.randomBoolean();
      long autoDeleteQueuesDelay = RandomUtil.randomPositiveLong();
      long autoDeleteQueuesMessageCount = RandomUtil.randomPositiveLong();
      long autoDeleteAddressesDelay = RandomUtil.randomPositiveLong();
      double redeliveryCollisionAvoidanceFactor = RandomUtil.randomDouble();
      long retroactiveMessageCount = RandomUtil.randomPositiveLong();
      boolean autoCreateDeadLetterResources = RandomUtil.randomBoolean();
      String deadLetterQueuePrefix = RandomUtil.randomUUIDString();
      String deadLetterQueueSuffix = RandomUtil.randomUUIDString();
      boolean autoCreateExpiryResources = RandomUtil.randomBoolean();
      String expiryQueuePrefix = RandomUtil.randomUUIDString();
      String expiryQueueSuffix = RandomUtil.randomUUIDString();
      boolean enableMetrics = RandomUtil.randomBoolean();
      int initialQueueBufferSize = (int) Math.pow(2, 14);

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setDeadLetterAddress(SimpleString.of(DLA))
              .setExpiryAddress(SimpleString.of(expiryAddress))
              .setExpiryDelay(expiryDelay)
              .setDefaultLastValueQueue(lastValueQueue)
              .setMaxDeliveryAttempts(deliveryAttempts)
              .setMaxSizeBytes(maxSizeBytes)
              .setPageSizeBytes(pageSizeBytes)
              .setPageCacheMaxSize(pageMaxCacheSize)
              .setRedeliveryDelay(redeliveryDelay)
              .setRedeliveryMultiplier(redeliveryMultiplier)
              .setMaxRedeliveryDelay(maxRedeliveryDelay)
              .setRedistributionDelay(redistributionDelay)
              .setSendToDLAOnNoRoute(sendToDLAOnNoRoute)
              .setAddressFullMessagePolicy(AddressFullMessagePolicy.valueOf(addressFullMessagePolicy))
              .setSlowConsumerThreshold(slowConsumerThreshold)
              .setSlowConsumerCheckPeriod(slowConsumerCheckPeriod)
              .setSlowConsumerPolicy(SlowConsumerPolicy.valueOf(slowConsumerPolicy))
              .setAutoCreateQueues(autoCreateQueues)
              .setAutoDeleteQueues(autoDeleteQueues)
              .setAutoCreateAddresses(autoCreateAddresses)
              .setAutoDeleteAddresses(autoDeleteAddresses)
              .setConfigDeleteQueues(DeletionPolicy.valueOf(configDeleteQueues))
              .setConfigDeleteAddresses(DeletionPolicy.valueOf(configDeleteAddresses))
              .setMaxSizeBytesRejectThreshold(maxSizeBytesRejectThreshold)
              .setDefaultLastValueKey(SimpleString.of(defaultLastValueKey))
              .setDefaultNonDestructive(defaultNonDestructive)
              .setDefaultExclusiveQueue(defaultExclusiveQueue)
              .setDefaultGroupRebalance(defaultGroupRebalance)
              .setDefaultGroupBuckets(defaultGroupBuckets)
              .setDefaultGroupFirstKey(SimpleString.of(defaultGroupFirstKey))
              .setDefaultMaxConsumers(defaultMaxConsumers)
              .setDefaultPurgeOnNoConsumers(defaultPurgeOnNoConsumers)
              .setDefaultConsumersBeforeDispatch(defaultConsumersBeforeDispatch)
              .setDefaultDelayBeforeDispatch(defaultDelayBeforeDispatch)
              .setDefaultQueueRoutingType(RoutingType.valueOf(defaultQueueRoutingType))
              .setDefaultAddressRoutingType(RoutingType.valueOf(defaultAddressRoutingType))
              .setDefaultConsumerWindowSize(defaultConsumerWindowSize)
              .setDefaultRingSize(defaultRingSize)
              .setAutoDeleteCreatedQueues(autoDeleteCreatedQueues)
              .setAutoDeleteQueuesDelay(autoDeleteQueuesDelay)
              .setAutoDeleteQueuesMessageCount(autoDeleteQueuesMessageCount)
              .setAutoDeleteAddressesDelay(autoDeleteAddressesDelay)
              .setRedeliveryCollisionAvoidanceFactor(redeliveryCollisionAvoidanceFactor)
              .setRetroactiveMessageCount(retroactiveMessageCount)
              .setAutoCreateDeadLetterResources(autoCreateDeadLetterResources)
              .setDeadLetterQueuePrefix(SimpleString.of(deadLetterQueuePrefix))
              .setDeadLetterQueueSuffix(SimpleString.of(deadLetterQueueSuffix))
              .setAutoCreateExpiryResources(autoCreateExpiryResources)
              .setExpiryQueuePrefix(SimpleString.of(expiryQueuePrefix))
              .setExpiryQueueSuffix(SimpleString.of(expiryQueueSuffix))
              .setMinExpiryDelay(minExpiryDelay)
              .setMaxExpiryDelay(maxExpiryDelay)
              .setEnableMetrics(enableMetrics)
              .setInitialQueueBufferSize(initialQueueBufferSize);


      serverControl.addAddressSettings(addressMatch, addressSettings.toJSON());

      addressSettings.setMaxSizeBytes(100).setPageSizeBytes(1000);

      boolean ex = false;
      try {
         serverControl.addAddressSettings(addressMatch, addressSettings.toJSON());
      } catch (Exception expected) {
         ex = true;
      }

      assertTrue(ex, "Exception expected");
      restartServer();
      serverControl = createManagementControl();

      String jsonString = serverControl.getAddressSettingsAsJSON(exactAddress);
      AddressSettingsInfo info = AddressSettingsInfo.fromJSON(jsonString);

      assertEquals(DLA, info.getDeadLetterAddress());
      assertEquals(expiryAddress, info.getExpiryAddress());
      assertEquals(expiryDelay, info.getExpiryDelay());
      assertEquals(minExpiryDelay, info.getMinExpiryDelay());
      assertEquals(maxExpiryDelay, info.getMaxExpiryDelay());
      assertEquals(lastValueQueue, info.isDefaultLastValueQueue());
      assertEquals(deliveryAttempts, info.getMaxDeliveryAttempts());
      assertEquals(maxSizeBytes, info.getMaxSizeBytes());
      assertEquals(pageMaxCacheSize, info.getPageCacheMaxSize());
      assertEquals(pageSizeBytes, info.getPageSizeBytes());
      assertEquals(redeliveryDelay, info.getRedeliveryDelay());
      assertEquals(redeliveryMultiplier, info.getRedeliveryMultiplier(), 0.000001);
      assertEquals(maxRedeliveryDelay, info.getMaxRedeliveryDelay());
      assertEquals(redistributionDelay, info.getRedistributionDelay());
      assertEquals(sendToDLAOnNoRoute, info.isSendToDLAOnNoRoute());
      assertEquals(addressFullMessagePolicy, info.getAddressFullMessagePolicy());
      assertEquals(slowConsumerThreshold, info.getSlowConsumerThreshold());
      assertEquals(slowConsumerCheckPeriod, info.getSlowConsumerCheckPeriod());
      assertEquals(slowConsumerPolicy, info.getSlowConsumerPolicy());
      assertEquals(autoCreateQueues, info.isAutoCreateQueues());
      assertEquals(autoDeleteQueues, info.isAutoDeleteQueues());
      assertEquals(autoCreateAddresses, info.isAutoCreateAddresses());
      assertEquals(autoDeleteAddresses, info.isAutoDeleteAddresses());
      assertEquals(configDeleteQueues, info.getConfigDeleteQueues());
      assertEquals(configDeleteAddresses, info.getConfigDeleteAddresses());
      assertEquals(maxSizeBytesRejectThreshold, info.getMaxSizeBytesRejectThreshold());
      assertEquals(defaultLastValueKey, info.getDefaultLastValueKey());
      assertEquals(defaultNonDestructive, info.isDefaultNonDestructive());
      assertEquals(defaultExclusiveQueue, info.isDefaultExclusiveQueue());
      assertEquals(defaultGroupRebalance, info.isDefaultGroupRebalance());
      assertEquals(defaultGroupBuckets, info.getDefaultGroupBuckets());
      assertEquals(defaultGroupFirstKey, info.getDefaultGroupFirstKey());
      assertEquals(defaultMaxConsumers, info.getDefaultMaxConsumers());
      assertEquals(defaultPurgeOnNoConsumers, info.isDefaultPurgeOnNoConsumers());
      assertEquals(defaultConsumersBeforeDispatch, info.getDefaultConsumersBeforeDispatch());
      assertEquals(defaultDelayBeforeDispatch, info.getDefaultDelayBeforeDispatch());
      assertEquals(defaultQueueRoutingType, info.getDefaultQueueRoutingType());
      assertEquals(defaultAddressRoutingType, info.getDefaultAddressRoutingType());
      assertEquals(defaultConsumerWindowSize, info.getDefaultConsumerWindowSize());
      assertEquals(defaultRingSize, info.getDefaultRingSize());
      assertEquals(autoDeleteCreatedQueues, info.isAutoDeleteCreatedQueues());
      assertEquals(autoDeleteQueuesDelay, info.getAutoDeleteQueuesDelay());
      assertEquals(autoDeleteQueuesMessageCount, info.getAutoDeleteQueuesMessageCount());
      assertEquals(autoDeleteAddressesDelay, info.getAutoDeleteAddressesDelay());
      assertEquals(redeliveryCollisionAvoidanceFactor, info.getRedeliveryCollisionAvoidanceFactor(), 0);
      assertEquals(retroactiveMessageCount, info.getRetroactiveMessageCount());
      assertEquals(autoCreateDeadLetterResources, info.isAutoCreateDeadLetterResources());
      assertEquals(deadLetterQueuePrefix, info.getDeadLetterQueuePrefix());
      assertEquals(deadLetterQueueSuffix, info.getDeadLetterQueueSuffix());
      assertEquals(autoCreateExpiryResources, info.isAutoCreateExpiryResources());
      assertEquals(expiryQueuePrefix, info.getExpiryQueuePrefix());
      assertEquals(expiryQueueSuffix, info.getExpiryQueueSuffix());
      assertEquals(enableMetrics, info.isEnableMetrics());
      assertEquals(initialQueueBufferSize, info.getInitialQueueBufferSize());


      addressSettings.setMaxSizeBytes(-1).setPageSizeBytes(1000);
      serverControl.addAddressSettings(addressMatch, addressSettings.toJSON());

      jsonString = serverControl.getAddressSettingsAsJSON(exactAddress);
      info = AddressSettingsInfo.fromJSON(jsonString);

      assertEquals(DLA, info.getDeadLetterAddress());
      assertEquals(expiryAddress, info.getExpiryAddress());
      assertEquals(expiryDelay, info.getExpiryDelay());
      assertEquals(minExpiryDelay, info.getMinExpiryDelay());
      assertEquals(maxExpiryDelay, info.getMaxExpiryDelay());
      assertEquals(lastValueQueue, info.isDefaultLastValueQueue());
      assertEquals(deliveryAttempts, info.getMaxDeliveryAttempts());
      assertEquals(-1, info.getMaxSizeBytes());
      assertEquals(pageMaxCacheSize, info.getPageCacheMaxSize());
      assertEquals(1000, info.getPageSizeBytes());
      assertEquals(redeliveryDelay, info.getRedeliveryDelay());
      assertEquals(redeliveryMultiplier, info.getRedeliveryMultiplier(), 0.000001);
      assertEquals(maxRedeliveryDelay, info.getMaxRedeliveryDelay());
      assertEquals(redistributionDelay, info.getRedistributionDelay());
      assertEquals(sendToDLAOnNoRoute, info.isSendToDLAOnNoRoute());
      assertEquals(addressFullMessagePolicy, info.getAddressFullMessagePolicy());
      assertEquals(slowConsumerThreshold, info.getSlowConsumerThreshold());
      assertEquals(slowConsumerCheckPeriod, info.getSlowConsumerCheckPeriod());
      assertEquals(slowConsumerPolicy, info.getSlowConsumerPolicy());
      assertEquals(autoCreateQueues, info.isAutoCreateQueues());
      assertEquals(autoDeleteQueues, info.isAutoDeleteQueues());
      assertEquals(autoCreateAddresses, info.isAutoCreateAddresses());
      assertEquals(autoDeleteAddresses, info.isAutoDeleteAddresses());
      assertEquals(configDeleteQueues, info.getConfigDeleteQueues());
      assertEquals(configDeleteAddresses, info.getConfigDeleteAddresses());
      assertEquals(maxSizeBytesRejectThreshold, info.getMaxSizeBytesRejectThreshold());
      assertEquals(defaultLastValueKey, info.getDefaultLastValueKey());
      assertEquals(defaultNonDestructive, info.isDefaultNonDestructive());
      assertEquals(defaultExclusiveQueue, info.isDefaultExclusiveQueue());
      assertEquals(defaultGroupRebalance, info.isDefaultGroupRebalance());
      assertEquals(defaultGroupBuckets, info.getDefaultGroupBuckets());
      assertEquals(defaultGroupFirstKey, info.getDefaultGroupFirstKey());
      assertEquals(defaultMaxConsumers, info.getDefaultMaxConsumers());
      assertEquals(defaultPurgeOnNoConsumers, info.isDefaultPurgeOnNoConsumers());
      assertEquals(defaultConsumersBeforeDispatch, info.getDefaultConsumersBeforeDispatch());
      assertEquals(defaultDelayBeforeDispatch, info.getDefaultDelayBeforeDispatch());
      assertEquals(defaultQueueRoutingType, info.getDefaultQueueRoutingType());
      assertEquals(defaultAddressRoutingType, info.getDefaultAddressRoutingType());
      assertEquals(defaultConsumerWindowSize, info.getDefaultConsumerWindowSize());
      assertEquals(defaultRingSize, info.getDefaultRingSize());
      assertEquals(autoDeleteCreatedQueues, info.isAutoDeleteCreatedQueues());
      assertEquals(autoDeleteQueuesDelay, info.getAutoDeleteQueuesDelay());
      assertEquals(autoDeleteQueuesMessageCount, info.getAutoDeleteQueuesMessageCount());
      assertEquals(autoDeleteAddressesDelay, info.getAutoDeleteAddressesDelay());
      assertEquals(redeliveryCollisionAvoidanceFactor, info.getRedeliveryCollisionAvoidanceFactor(), 0);
      assertEquals(retroactiveMessageCount, info.getRetroactiveMessageCount());
      assertEquals(autoCreateDeadLetterResources, info.isAutoCreateDeadLetterResources());
      assertEquals(deadLetterQueuePrefix, info.getDeadLetterQueuePrefix());
      assertEquals(deadLetterQueueSuffix, info.getDeadLetterQueueSuffix());
      assertEquals(autoCreateExpiryResources, info.isAutoCreateExpiryResources());
      assertEquals(expiryQueuePrefix, info.getExpiryQueuePrefix());
      assertEquals(expiryQueueSuffix, info.getExpiryQueueSuffix());
      assertEquals(enableMetrics, info.isEnableMetrics());
      assertEquals(initialQueueBufferSize, info.getInitialQueueBufferSize());


      addressSettings.setMaxSizeBytes(-2).setPageSizeBytes(1000);
      ex = false;
      try {
         serverControl.addAddressSettings(addressMatch, addressSettings.toJSON());
      } catch (Exception e) {
         ex = true;
      }

      assertTrue(ex, "Supposed to have an exception called");

   }

   @TestTemplate
   public void testRemoveAddressSettingsEffective() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      String addr = "test";
      String root = "test.#";

      String DLA = "someDLA";
      String expiryAddress = "someExpiry";
      long expiryDelay = RandomUtil.randomPositiveLong();
      long minExpiryDelay = 10000;
      long maxExpiryDelay = 20000;
      boolean lastValueQueue = true;
      int deliveryAttempts = 1;
      long maxSizeBytes = 10 * 1024 * 1024;
      int pageSizeBytes = 1024 * 1024;
      int pageMaxCacheSize = 7;
      long redeliveryDelay = 4;
      double redeliveryMultiplier = 1;
      long maxRedeliveryDelay = 1000;
      long redistributionDelay = 5;
      boolean sendToDLAOnNoRoute = true;
      String addressFullMessagePolicy = "PAGE";
      long slowConsumerThreshold = 5;
      long slowConsumerCheckPeriod = 10;
      String slowConsumerPolicy = SlowConsumerPolicy.getType(RandomUtil.randomPositiveInt() % 2).toString();
      boolean autoCreateJmsQueues = RandomUtil.randomBoolean();
      boolean autoDeleteJmsQueues = RandomUtil.randomBoolean();
      boolean autoCreateJmsTopics = RandomUtil.randomBoolean();
      boolean autoDeleteJmsTopics = RandomUtil.randomBoolean();
      boolean autoCreateQueues = RandomUtil.randomBoolean();
      boolean autoDeleteQueues = RandomUtil.randomBoolean();
      boolean autoCreateAddresses = RandomUtil.randomBoolean();
      boolean autoDeleteAddresses = RandomUtil.randomBoolean();
      String configDeleteQueues = DeletionPolicy.getType(RandomUtil.randomPositiveInt() % 2).toString();
      String configDeleteAddresses = DeletionPolicy.getType(RandomUtil.randomPositiveInt() % 2).toString();
      long maxSizeBytesRejectThreshold = RandomUtil.randomPositiveLong();
      String defaultLastValueKey = RandomUtil.randomUUIDString();
      boolean defaultNonDestructive = RandomUtil.randomBoolean();
      boolean defaultExclusiveQueue = RandomUtil.randomBoolean();
      boolean defaultGroupRebalance = RandomUtil.randomBoolean();
      int defaultGroupBuckets = RandomUtil.randomPositiveInt();
      String defaultGroupFirstKey = RandomUtil.randomUUIDString();
      int defaultMaxConsumers = RandomUtil.randomPositiveInt();
      boolean defaultPurgeOnNoConsumers = RandomUtil.randomBoolean();
      int defaultConsumersBeforeDispatch = RandomUtil.randomPositiveInt();
      long defaultDelayBeforeDispatch = RandomUtil.randomPositiveLong();
      String defaultQueueRoutingType = RoutingType.getType((byte) (RandomUtil.randomPositiveInt() % 2)).toString();
      String defaultAddressRoutingType = RoutingType.getType((byte) (RandomUtil.randomPositiveInt() % 2)).toString();
      int defaultConsumerWindowSize = RandomUtil.randomPositiveInt();
      long defaultRingSize = RandomUtil.randomPositiveLong();
      boolean autoDeleteCreatedQueues = RandomUtil.randomBoolean();
      long autoDeleteQueuesDelay = RandomUtil.randomPositiveLong();
      long autoDeleteQueuesMessageCount = RandomUtil.randomPositiveLong();
      long autoDeleteAddressesDelay = RandomUtil.randomPositiveLong();
      double redeliveryCollisionAvoidanceFactor = RandomUtil.randomDouble();
      long retroactiveMessageCount = RandomUtil.randomPositiveLong();
      boolean autoCreateDeadLetterResources = RandomUtil.randomBoolean();
      String deadLetterQueuePrefix = RandomUtil.randomUUIDString();
      String deadLetterQueueSuffix = RandomUtil.randomUUIDString();
      boolean autoCreateExpiryResources = RandomUtil.randomBoolean();
      String expiryQueuePrefix = RandomUtil.randomUUIDString();
      String expiryQueueSuffix = RandomUtil.randomUUIDString();
      boolean enableMetrics = RandomUtil.randomBoolean();

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setDeadLetterAddress(SimpleString.of(DLA))
              .setExpiryAddress(SimpleString.of(expiryAddress))
              .setExpiryDelay(expiryDelay)
              .setDefaultLastValueQueue(lastValueQueue)
              .setMaxDeliveryAttempts(deliveryAttempts)
              .setMaxSizeBytes(maxSizeBytes)
              .setPageSizeBytes(pageSizeBytes)
              .setPageCacheMaxSize(pageMaxCacheSize)
              .setRedeliveryDelay(redeliveryDelay)
              .setRedeliveryMultiplier(redeliveryMultiplier)
              .setMaxRedeliveryDelay(maxRedeliveryDelay)
              .setRedistributionDelay(redistributionDelay)
              .setSendToDLAOnNoRoute(sendToDLAOnNoRoute)
              .setAddressFullMessagePolicy(AddressFullMessagePolicy.valueOf(addressFullMessagePolicy))
              .setSlowConsumerThreshold(slowConsumerThreshold)
              .setSlowConsumerCheckPeriod(slowConsumerCheckPeriod)
              .setSlowConsumerPolicy(SlowConsumerPolicy.valueOf(slowConsumerPolicy))
              .setAutoCreateQueues(autoCreateQueues)
              .setAutoDeleteQueues(autoDeleteQueues)
              .setAutoCreateAddresses(autoCreateAddresses)
              .setAutoDeleteAddresses(autoDeleteAddresses)
              .setConfigDeleteQueues(DeletionPolicy.valueOf(configDeleteQueues))
              .setConfigDeleteAddresses(DeletionPolicy.valueOf(configDeleteAddresses))
              .setMaxSizeBytesRejectThreshold(maxSizeBytesRejectThreshold)
              .setDefaultLastValueKey(SimpleString.of(defaultLastValueKey))
              .setDefaultNonDestructive(defaultNonDestructive)
              .setDefaultExclusiveQueue(defaultExclusiveQueue)
              .setDefaultGroupRebalance(defaultGroupRebalance)
              .setDefaultGroupBuckets(defaultGroupBuckets)
              .setDefaultGroupFirstKey(SimpleString.of(defaultGroupFirstKey))
              .setDefaultMaxConsumers(defaultMaxConsumers)
              .setDefaultPurgeOnNoConsumers(defaultPurgeOnNoConsumers)
              .setDefaultConsumersBeforeDispatch(defaultConsumersBeforeDispatch)
              .setDefaultDelayBeforeDispatch(defaultDelayBeforeDispatch)
              .setDefaultQueueRoutingType(RoutingType.valueOf(defaultQueueRoutingType))
              .setDefaultAddressRoutingType(RoutingType.valueOf(defaultAddressRoutingType))
              .setDefaultConsumerWindowSize(defaultConsumerWindowSize)
              .setDefaultRingSize(defaultRingSize)
              .setAutoDeleteCreatedQueues(autoDeleteCreatedQueues)
              .setAutoDeleteQueuesDelay(autoDeleteQueuesDelay)
              .setAutoDeleteQueuesMessageCount(autoDeleteQueuesMessageCount)
              .setAutoDeleteAddressesDelay(autoDeleteAddressesDelay)
              .setRedeliveryCollisionAvoidanceFactor(redeliveryCollisionAvoidanceFactor)
              .setRetroactiveMessageCount(retroactiveMessageCount)
              .setAutoCreateDeadLetterResources(autoCreateDeadLetterResources)
              .setDeadLetterQueuePrefix(SimpleString.of(deadLetterQueuePrefix))
              .setDeadLetterQueueSuffix(SimpleString.of(deadLetterQueueSuffix))
              .setAutoCreateExpiryResources(autoCreateExpiryResources)
              .setExpiryQueuePrefix(SimpleString.of(expiryQueuePrefix))
              .setExpiryQueueSuffix(SimpleString.of(expiryQueueSuffix))
              .setMinExpiryDelay(minExpiryDelay)
              .setMaxExpiryDelay(maxExpiryDelay)
              .setEnableMetrics(enableMetrics);

      serverControl.addAddressSettings(root, addressSettings.toJSON());

      AddressSettingsInfo rootInfo = AddressSettingsInfo.fromJSON(serverControl.getAddressSettingsAsJSON(root));

      // Give settings for addr different values to the root
      final long addrMinExpiryDelay = rootInfo.getMinExpiryDelay() + 1;
      final long addrMaxExpiryDelay = rootInfo.getMaxExpiryDelay() - 1;

      addressSettings.setMinExpiryDelay(addrMinExpiryDelay).setMaxExpiryDelay(addrMaxExpiryDelay);
      serverControl.addAddressSettings(addr, addressSettings.toJSON());
      AddressSettingsInfo addrInfo = AddressSettingsInfo.fromJSON(serverControl.getAddressSettingsAsJSON(addr));

      assertEquals(addrMinExpiryDelay, addrInfo.getMinExpiryDelay(), "settings for addr should carry update");
      assertEquals(addrMaxExpiryDelay, addrInfo.getMaxExpiryDelay(), "settings for addr should carry update");

      serverControl.removeAddressSettings(addr);

      AddressSettingsInfo rereadAddrInfo = AddressSettingsInfo.fromJSON(serverControl.getAddressSettingsAsJSON(addr));

      assertEquals(rootInfo.getMinExpiryDelay(), rereadAddrInfo.getMinExpiryDelay(), "settings for addr should have reverted to original value after removal");
      assertEquals(rootInfo.getMaxExpiryDelay(), rereadAddrInfo.getMaxExpiryDelay(), "settings for addr should have reverted to original value after removal");
   }

   @TestTemplate
   public void testNullRouteNameOnDivert() throws Exception {
      testNullRouteNameOnDivert(false);
   }

   @TestTemplate
   public void testNullRouteNameOnDivertJSON() throws Exception {
      testNullRouteNameOnDivert(true);
   }

   private void testNullRouteNameOnDivert(boolean json) throws Exception {
      String address = RandomUtil.randomUUIDString();
      String name = RandomUtil.randomUUIDString();
      String forwardingAddress = RandomUtil.randomUUIDString();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      assertEquals(0, serverControl.getDivertNames().length);

      if (json) {
         serverControl.createDivert(new DivertConfiguration().setName(name).setAddress(address).setForwardingAddress(forwardingAddress).toJSON());
      } else {
         serverControl.createDivert(name, null, address, forwardingAddress, true, null, null);
      }

      checkResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
   }

   @TestTemplate
   public void testCreateAndDestroyDivert() throws Exception {
      testCreateAndDestroyDivert(false);
   }

   @TestTemplate
   public void testCreateAndDestroyDivertJSON() throws Exception {
      testCreateAndDestroyDivert(true);
   }

   private void testCreateAndDestroyDivert(boolean json) throws Exception {
      String address = RandomUtil.randomUUIDString();
      String name = RandomUtil.randomUUIDString();
      String routingName = RandomUtil.randomUUIDString();
      String forwardingAddress = RandomUtil.randomUUIDString();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      assertEquals(0, serverControl.getDivertNames().length);

      if (json) {
         serverControl.createDivert(new DivertConfiguration().setName(name).setRoutingName(routingName).setAddress(address).setForwardingAddress(forwardingAddress).setExclusive(true).toJSON());
      } else {
         serverControl.createDivert(name, routingName, address, forwardingAddress, true, null, null);
      }

      checkResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      DivertControl divertControl = ManagementControlHelper.createDivertControl(name, address, mbeanServer);
      assertEquals(name, divertControl.getUniqueName());
      assertEquals(address, divertControl.getAddress());
      assertEquals(forwardingAddress, divertControl.getForwardingAddress());
      assertEquals(routingName, divertControl.getRoutingName());
      assertTrue(divertControl.isExclusive());
      assertNull(divertControl.getFilter());
      assertNull(divertControl.getTransformerClassName());
      String[] divertNames = serverControl.getDivertNames();
      assertEquals(1, divertNames.length);
      assertEquals(name, divertNames[0]);

      // check that a message sent to the address is diverted exclusively
      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();

      String divertQueue = RandomUtil.randomUUIDString();
      String queue = RandomUtil.randomUUIDString();
      if (legacyCreateQueue) {
         session.createQueue(forwardingAddress, RoutingType.ANYCAST, divertQueue);
         session.createQueue(address, RoutingType.ANYCAST, queue);
      } else {
         session.createQueue(QueueConfiguration.of(divertQueue).setAddress(forwardingAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(QueueConfiguration.of(queue).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(false);
      String text = RandomUtil.randomUUIDString();
      message.putStringProperty("prop", text);
      producer.send(message);

      ClientConsumer consumer = session.createConsumer(queue);
      ClientConsumer divertedConsumer = session.createConsumer(divertQueue);

      session.start();

      assertNull(consumer.receiveImmediate());
      message = divertedConsumer.receive(5000);
      assertNotNull(message);
      assertEquals(text, message.getStringProperty("prop"));

      serverControl.destroyDivert(name);

      checkNoResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      assertEquals(0, serverControl.getDivertNames().length);

      // check that a message is no longer diverted
      message = session.createMessage(false);
      String text2 = RandomUtil.randomUUIDString();
      message.putStringProperty("prop", text2);
      producer.send(message);

      assertNull(divertedConsumer.receiveImmediate());
      message = consumer.receive(5000);
      assertNotNull(message);
      assertEquals(text2, message.getStringProperty("prop"));

      consumer.close();
      divertedConsumer.close();
      session.deleteQueue(queue);
      session.deleteQueue(divertQueue);
      session.close();

      locator.close();

   }

   @TestTemplate
   public void testCreateAndDestroyDivertServerRestart() throws Exception {
      testCreateAndDestroyDivertServerRestart(false);
   }

   @TestTemplate
   public void testCreateAndDestroyDivertServerRestartJSON() throws Exception {
      testCreateAndDestroyDivertServerRestart(true);
   }

   private void testCreateAndDestroyDivertServerRestart(boolean json) throws Exception {
      String address = RandomUtil.randomUUIDString();
      String name = RandomUtil.randomUUIDString();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      assertEquals(0, serverControl.getDivertNames().length);

      if (json) {
         serverControl.createDivert(new DivertConfiguration().setName(name).setRoutingName(RandomUtil.randomUUIDString()).setAddress(address).setForwardingAddress(RandomUtil.randomUUIDString()).setExclusive(true).toJSON());
      } else {
         serverControl.createDivert(name, RandomUtil.randomUUIDString(), address, RandomUtil.randomUUIDString(), true, null, null);
      }

      checkResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      assertEquals(1, serverControl.getDivertNames().length);

      serverControl.destroyDivert(name);

      checkNoResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      assertEquals(0, serverControl.getDivertNames().length);

      server.stop();

      server.start();

      checkNoResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      assertEquals(0, serverControl.getDivertNames().length);
   }

   @TestTemplate
   public void testCreateAndUpdateDivert() throws Exception {
      testCreateAndUpdateDivert(false);
   }

   @TestTemplate
   public void testCreateAndUpdateDivertJSON() throws Exception {
      testCreateAndUpdateDivert(true);
   }

   private void testCreateAndUpdateDivert(boolean json) throws Exception {
      String address = RandomUtil.randomUUIDString();
      String name = RandomUtil.randomUUIDString();
      String routingName = RandomUtil.randomUUIDString();
      String forwardingAddress = RandomUtil.randomUUIDString();
      String updatedForwardingAddress = RandomUtil.randomUUIDString();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      assertEquals(0, serverControl.getDivertNames().length);

      if (json) {
         serverControl.createDivert(new DivertConfiguration().setName(name).setRoutingName(routingName).setAddress(address).setForwardingAddress(forwardingAddress).setExclusive(true).toJSON());
      } else {
         serverControl.createDivert(name, routingName, address, forwardingAddress, true, null, null);
      }

      checkResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      DivertControl divertControl = ManagementControlHelper.createDivertControl(name, address, mbeanServer);
      assertEquals(name, divertControl.getUniqueName());
      assertEquals(address, divertControl.getAddress());
      assertEquals(forwardingAddress, divertControl.getForwardingAddress());
      assertEquals(routingName, divertControl.getRoutingName());
      assertTrue(divertControl.isExclusive());
      assertNull(divertControl.getFilter());
      assertNull(divertControl.getTransformerClassName());
      String[] divertNames = serverControl.getDivertNames();
      assertEquals(1, divertNames.length);
      assertEquals(name, divertNames[0]);

      // check that a message sent to the address is diverted exclusively
      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();

      String updatedDivertQueue = RandomUtil.randomUUIDString();
      String divertQueue = RandomUtil.randomUUIDString();
      String queue = RandomUtil.randomUUIDString();
      if (legacyCreateQueue) {
         session.createQueue(updatedForwardingAddress, RoutingType.ANYCAST, updatedDivertQueue);
         session.createQueue(forwardingAddress, RoutingType.ANYCAST, divertQueue);
         session.createQueue(address, RoutingType.ANYCAST, queue);
      } else {
         session.createQueue(QueueConfiguration.of(updatedDivertQueue).setAddress(updatedForwardingAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(QueueConfiguration.of(divertQueue).setAddress(forwardingAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(QueueConfiguration.of(queue).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(false);
      String text = RandomUtil.randomUUIDString();
      message.putStringProperty("prop", text);
      producer.send(message);

      ClientConsumer consumer = session.createConsumer(queue);
      ClientConsumer divertedConsumer = session.createConsumer(divertQueue);
      ClientConsumer updatedDivertedConsumer = session.createConsumer(updatedDivertQueue);

      session.start();

      assertNull(consumer.receiveImmediate());
      message = divertedConsumer.receive(5000);
      assertNotNull(message);
      assertEquals(text, message.getStringProperty("prop"));
      assertNull(updatedDivertedConsumer.receiveImmediate());

      if (json) {
         serverControl.updateDivert(new DivertConfiguration().setName(name).setForwardingAddress(updatedForwardingAddress).toJSON());
      } else {
         serverControl.updateDivert(name, updatedForwardingAddress, null, null, null, ActiveMQDefaultConfiguration.getDefaultDivertRoutingType());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      divertControl = ManagementControlHelper.createDivertControl(name, address, mbeanServer);
      assertEquals(name, divertControl.getUniqueName());
      assertEquals(address, divertControl.getAddress());
      assertEquals(updatedForwardingAddress, divertControl.getForwardingAddress());
      assertEquals(routingName, divertControl.getRoutingName());
      assertTrue(divertControl.isExclusive());
      assertNull(divertControl.getFilter());
      assertNull(divertControl.getTransformerClassName());
      divertNames = serverControl.getDivertNames();
      assertEquals(1, divertNames.length);
      assertEquals(name, divertNames[0]);
      //now check its been persisted
      PersistedDivertConfiguration pdc = server.getStorageManager().recoverDivertConfigurations().get(0);
      assertEquals(pdc.getDivertConfiguration().getForwardingAddress(), updatedForwardingAddress);

      // check that a message is no longer exclusively diverted
      message = session.createMessage(false);
      String text2 = RandomUtil.randomUUIDString();
      message.putStringProperty("prop", text2);
      producer.send(message);

      assertNull(consumer.receiveImmediate());
      assertNull(divertedConsumer.receiveImmediate());
      message = updatedDivertedConsumer.receive(5000);
      assertNotNull(message);
      assertEquals(text2, message.getStringProperty("prop"));

      consumer.close();
      divertedConsumer.close();
      updatedDivertedConsumer.close();
      session.deleteQueue(queue);
      session.deleteQueue(divertQueue);
      session.deleteQueue(updatedDivertQueue);
      session.close();

      locator.close();

   }

   @TestTemplate
   public void testCreateAndDestroyBridge() throws Exception {
      String name = RandomUtil.randomUUIDString();
      String sourceAddress = RandomUtil.randomUUIDString();
      String sourceQueue = RandomUtil.randomUUIDString();
      String targetAddress = RandomUtil.randomUUIDString();
      String targetQueue = RandomUtil.randomUUIDString();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getBridgeObjectName(name));
      assertEquals(0, serverControl.getBridgeNames().length);

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();

      if (legacyCreateQueue) {
         session.createQueue(sourceAddress, RoutingType.ANYCAST, sourceQueue);
         session.createQueue(targetAddress, RoutingType.ANYCAST, targetQueue);
      } else {
         session.createQueue(QueueConfiguration.of(sourceQueue).setAddress(sourceAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(QueueConfiguration.of(targetQueue).setAddress(targetAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      serverControl.createBridge(name, sourceQueue, targetAddress, null, // forwardingAddress
                                 null, // filterString
                                 ActiveMQClient.DEFAULT_RETRY_INTERVAL, ActiveMQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER, ActiveMQClient.INITIAL_CONNECT_ATTEMPTS, ActiveMQClient.DEFAULT_RECONNECT_ATTEMPTS, false, // duplicateDetection
                                 1, // confirmationWindowSize
                                 -1, // producerWindowSize
                                 ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD, connectorConfig.getName(), // primaryConnector
                                 false, false, null, null);

      checkResource(ObjectNameBuilder.DEFAULT.getBridgeObjectName(name));
      String[] bridgeNames = serverControl.getBridgeNames();
      assertEquals(1, bridgeNames.length);
      assertEquals(name, bridgeNames[0]);

      BridgeControl bridgeControl = ManagementControlHelper.createBridgeControl(name, mbeanServer);
      assertEquals(name, bridgeControl.getName());
      assertTrue(bridgeControl.isStarted());

      // check that a message sent to the sourceAddress is put in the tagetQueue
      ClientProducer producer = session.createProducer(sourceAddress);
      ClientMessage message = session.createMessage(false);
      String text = RandomUtil.randomUUIDString();
      message.putStringProperty("prop", text);
      producer.send(message);

      session.start();

      ClientConsumer targetConsumer = session.createConsumer(targetQueue);
      message = targetConsumer.receive(5000);
      assertNotNull(message);
      assertEquals(text, message.getStringProperty("prop"));

      ClientConsumer sourceConsumer = session.createConsumer(sourceQueue);
      assertNull(sourceConsumer.receiveImmediate());

      serverControl.destroyBridge(name);

      checkNoResource(ObjectNameBuilder.DEFAULT.getBridgeObjectName(name));
      assertEquals(0, serverControl.getBridgeNames().length);

      // check that a message is no longer diverted
      message = session.createMessage(false);
      String text2 = RandomUtil.randomUUIDString();
      message.putStringProperty("prop", text2);
      producer.send(message);

      assertNull(targetConsumer.receiveImmediate());
      message = sourceConsumer.receive(5000);
      assertNotNull(message);
      assertEquals(text2, message.getStringProperty("prop"));

      sourceConsumer.close();
      targetConsumer.close();

      session.deleteQueue(sourceQueue);
      session.deleteQueue(targetQueue);

      session.close();

      locator.close();
   }

   @TestTemplate
   public void testCreateAndDestroyBridgeFromJson() throws Exception {
      internalTestCreateAndDestroyBridgeFromJson(false);
   }

   @TestTemplate
   public void testCreateAndDestroyBridgeFromJsonDynamicConnector() throws Exception {
      internalTestCreateAndDestroyBridgeFromJson(true);
   }

   private void internalTestCreateAndDestroyBridgeFromJson(boolean dynamicConnector) throws Exception {
      String name = RandomUtil.randomUUIDString();
      String sourceAddress = RandomUtil.randomUUIDString();
      String sourceQueue = RandomUtil.randomUUIDString();
      String targetAddress = RandomUtil.randomUUIDString();
      String targetQueue = RandomUtil.randomUUIDString();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getBridgeObjectName(name));
      assertEquals(0, serverControl.getBridgeNames().length);

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();

      if (legacyCreateQueue) {
         session.createQueue(sourceAddress, RoutingType.ANYCAST, sourceQueue);
         session.createQueue(targetAddress, RoutingType.ANYCAST, targetQueue);
      } else {
         session.createQueue(QueueConfiguration.of(sourceQueue).setAddress(sourceAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(QueueConfiguration.of(targetQueue).setAddress(targetAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      String connectorName = connectorConfig.getName();
      if (dynamicConnector) {
         connectorName = RandomUtil.randomUUIDString();
         serverControl.addConnector(connectorName, "vm://0");
      }

      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration(name)
         .setQueueName(sourceQueue)
         .setForwardingAddress(targetAddress)
         .setUseDuplicateDetection(false)
         .setConfirmationWindowSize(1)
         .setProducerWindowSize(-1)
         .setStaticConnectors(Collections.singletonList(connectorName))
         .setHA(false)
         .setUser(null)
         .setPassword(null);
      serverControl.createBridge(bridgeConfiguration.toJSON());

      checkResource(ObjectNameBuilder.DEFAULT.getBridgeObjectName(name));
      String[] bridgeNames = serverControl.getBridgeNames();
      assertEquals(1, bridgeNames.length);
      assertEquals(name, bridgeNames[0]);

      BridgeControl bridgeControl = ManagementControlHelper.createBridgeControl(name, mbeanServer);
      assertEquals(name, bridgeControl.getName());
      assertTrue(bridgeControl.isStarted());

      // check that a message sent to the sourceAddress is put in the tagetQueue
      ClientProducer producer = session.createProducer(sourceAddress);
      ClientMessage message = session.createMessage(false);
      String text = RandomUtil.randomUUIDString();
      message.putStringProperty("prop", text);
      producer.send(message);

      session.start();

      ClientConsumer targetConsumer = session.createConsumer(targetQueue);
      message = targetConsumer.receive(5000);
      assertNotNull(message);
      assertEquals(text, message.getStringProperty("prop"));

      ClientConsumer sourceConsumer = session.createConsumer(sourceQueue);
      assertNull(sourceConsumer.receiveImmediate());

      serverControl.destroyBridge(name);

      checkNoResource(ObjectNameBuilder.DEFAULT.getBridgeObjectName(name));
      assertEquals(0, serverControl.getBridgeNames().length);

      // check that a message is no longer diverted
      message = session.createMessage(false);
      String text2 = RandomUtil.randomUUIDString();
      message.putStringProperty("prop", text2);
      producer.send(message);

      assertNull(targetConsumer.receiveImmediate());
      message = sourceConsumer.receive(5000);
      assertNotNull(message);
      assertEquals(text2, message.getStringProperty("prop"));

      sourceConsumer.close();
      targetConsumer.close();

      session.deleteQueue(sourceQueue);
      session.deleteQueue(targetQueue);

      session.close();

      locator.close();
   }

   @TestTemplate
   public void testAddAndRemoveConnector() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      String connectorName = RandomUtil.randomUUIDString();
      serverControl.addConnector(connectorName, "vm://0");
      assertEquals(connectorName, server.getConfiguration().getConnectorConfigurations().get(connectorName).getName());
      serverControl.removeConnector(connectorName);
      assertNull(server.getConfiguration().getConnectorConfigurations().get(connectorName));
   }

   @TestTemplate
   public void testListPreparedTransactionDetails() throws Exception {
      SimpleString atestq = SimpleString.of("BasicXaTestq");
      Xid xid = newXID();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession clientSession = csf.createSession(true, false, false);
      if (legacyCreateQueue) {
         clientSession.createQueue(atestq, atestq, null, true);
      } else {
         clientSession.createQueue(QueueConfiguration.of(atestq).setDurable(true));
      }

      ClientMessage m1 = createTextMessage(clientSession, "");
      ClientMessage m2 = createTextMessage(clientSession, "");
      ClientMessage m3 = createTextMessage(clientSession, "");
      ClientMessage m4 = createTextMessage(clientSession, "");
      m1.putStringProperty("m1", "m1");
      m2.putStringProperty("m2", "m2");
      m3.putStringProperty("m3", "m3");
      m4.putStringProperty("m4", "m4");
      ClientProducer clientProducer = clientSession.createProducer(atestq);
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      // add another TX, but don't prepare it
      ClientMessage m5 = createTextMessage(clientSession, "");
      ClientMessage m6 = createTextMessage(clientSession, "");
      ClientMessage m7 = createTextMessage(clientSession, "");
      ClientMessage m8 = createTextMessage(clientSession, "");
      m5.putStringProperty("m5", "m5");
      m6.putStringProperty("m6", "m6");
      m7.putStringProperty("m7", "m7");
      m8.putStringProperty("m8", "m8");
      Xid xid2 = newXID();
      clientSession.start(xid2, XAResource.TMNOFLAGS);
      clientProducer.send(m5);
      clientProducer.send(m6);
      clientProducer.send(m7);
      clientProducer.send(m8);
      clientSession.end(xid2, XAResource.TMSUCCESS);

      ActiveMQServerControl serverControl = createManagementControl();

      JsonArray jsonArray = JsonUtil.readJsonArray(serverControl.listProducersInfoAsJSON());

      assertEquals(1 + extraProducers, jsonArray.size());
      JsonObject first = (JsonObject) jsonArray.get(0);
      if (!first.getString(ProducerField.ADDRESS.getAlternativeName()).equalsIgnoreCase(atestq.toString())) {
         first = (JsonObject) jsonArray.get(1);
      }
      assertEquals(8, ((JsonObject) first).getInt("msgSent"));
      assertTrue(((JsonObject) first).getInt("msgSizeSent") > 0);

      clientSession.close();
      locator.close();

      String txDetails = serverControl.listPreparedTransactionDetailsAsJSON();

      assertTrue(txDetails.matches(".*m1.*"));
      assertTrue(txDetails.matches(".*m2.*"));
      assertTrue(txDetails.matches(".*m3.*"));
      assertTrue(txDetails.matches(".*m4.*"));
      assertFalse(txDetails.matches(".*m5.*"));
      assertFalse(txDetails.matches(".*m6.*"));
      assertFalse(txDetails.matches(".*m7.*"));
      assertFalse(txDetails.matches(".*m8.*"));
   }

   @TestTemplate
   public void testListPreparedTransactionDetailsOnConsumer() throws Exception {
      SimpleString atestq = SimpleString.of("BasicXaTestq");
      Xid xid = newXID();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession clientSession = csf.createSession(true, false, false);
      if (legacyCreateQueue) {
         clientSession.createQueue(atestq, atestq, null, true);
      } else {
         clientSession.createQueue(QueueConfiguration.of(atestq));
      }

      ClientMessage m1 = createTextMessage(clientSession, "");
      ClientMessage m2 = createTextMessage(clientSession, "");
      ClientMessage m3 = createTextMessage(clientSession, "");
      ClientMessage m4 = createTextMessage(clientSession, "");
      m1.putStringProperty("m1", "valuem1");
      m2.putStringProperty("m2", "valuem2");
      m3.putStringProperty("m3", "valuem3");
      m4.putStringProperty("m4", "valuem4");
      ClientProducer clientProducer = clientSession.createProducer(atestq);
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);
      clientSession.commit(xid, false);

      ClientConsumer consumer = clientSession.createConsumer(atestq);
      clientSession.start();
      xid = newXID();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      m1 = consumer.receive(1000);
      assertNotNull(m1);
      m1.acknowledge();
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);
      ActiveMQServerControl serverControl = createManagementControl();
      String jsonOutput = serverControl.listPreparedTransactionDetailsAsJSON();

      // just one message is pending, and it should be listed on the output
      assertTrue(jsonOutput.lastIndexOf("valuem1") > 0);
      assertTrue(jsonOutput.lastIndexOf("valuem2") < 0);
      assertTrue(jsonOutput.lastIndexOf("valuem3") < 0);
      assertTrue(jsonOutput.lastIndexOf("valuem4") < 0);
      clientSession.close();
      locator.close();
   }

   @TestTemplate
   public void testListPreparedTransactionDetailsAsHTML() throws Exception {
      SimpleString atestq = SimpleString.of("BasicXaTestq");
      Xid xid = newXID();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession clientSession = csf.createSession(true, false, false);
      if (legacyCreateQueue) {
         clientSession.createQueue(atestq, atestq, null, true);
      } else {
         clientSession.createQueue(QueueConfiguration.of(atestq));
      }

      ClientMessage m1 = createTextMessage(clientSession, "");
      ClientMessage m2 = createTextMessage(clientSession, "");
      ClientMessage m3 = createTextMessage(clientSession, "");
      ClientMessage m4 = createTextMessage(clientSession, "");
      m1.putStringProperty("m1", "m1");
      m2.putStringProperty("m2", "m2");
      m3.putStringProperty("m3", "m3");
      m4.putStringProperty("m4", "m4");
      ClientProducer clientProducer = clientSession.createProducer(atestq);
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      clientSession.close();
      locator.close();

      ActiveMQServerControl serverControl = createManagementControl();
      String html = serverControl.listPreparedTransactionDetailsAsHTML();

      assertTrue(html.matches(".*m1.*"));
      assertTrue(html.matches(".*m2.*"));
      assertTrue(html.matches(".*m3.*"));
      assertTrue(html.matches(".*m4.*"));
   }

   @TestTemplate
   public void testCommitPreparedTransactions() throws Exception {
      SimpleString recQueue = SimpleString.of("BasicXaTestqRec");
      SimpleString sendQueue = SimpleString.of("BasicXaTestqSend");

      byte[] globalTransactionId = UUIDGenerator.getInstance().generateStringUUID().getBytes();
      Xid xid = new XidImpl("xa1".getBytes(), 1, globalTransactionId);
      Xid xid2 = new XidImpl("xa2".getBytes(), 1, globalTransactionId);
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession clientSession = csf.createSession(true, false, false);
      if (legacyCreateQueue) {
         clientSession.createQueue(recQueue, recQueue, null, true);
         clientSession.createQueue(sendQueue, sendQueue, null, true);
      } else {
         clientSession.createQueue(QueueConfiguration.of(recQueue));
         clientSession.createQueue(QueueConfiguration.of(sendQueue));
      }
      ClientMessage m1 = createTextMessage(clientSession, "");
      m1.putStringProperty("m1", "m1");
      ClientProducer clientProducer = clientSession.createProducer(recQueue);
      clientProducer.send(m1);
      locator.close();

      ServerLocator receiveLocator = createInVMNonHALocator();
      ClientSessionFactory receiveCsf = createSessionFactory(receiveLocator);
      ClientSession receiveClientSession = receiveCsf.createSession(true, false, false);
      ClientConsumer consumer = receiveClientSession.createConsumer(recQueue);

      ServerLocator sendLocator = createInVMNonHALocator();
      ClientSessionFactory sendCsf = createSessionFactory(sendLocator);
      ClientSession sendClientSession = sendCsf.createSession(true, false, false);
      ClientProducer producer = sendClientSession.createProducer(sendQueue);

      receiveClientSession.start(xid, XAResource.TMNOFLAGS);
      receiveClientSession.start();
      sendClientSession.start(xid2, XAResource.TMNOFLAGS);

      ClientMessage m = consumer.receive(5000);
      assertNotNull(m);

      producer.send(m);

      receiveClientSession.end(xid, XAResource.TMSUCCESS);
      sendClientSession.end(xid2, XAResource.TMSUCCESS);

      receiveClientSession.prepare(xid);
      sendClientSession.prepare(xid2);

      ActiveMQServerControl serverControl = createManagementControl();

      sendLocator.close();
      receiveLocator.close();

      boolean success = serverControl.commitPreparedTransaction(XidImpl.toBase64String(xid));

      success = serverControl.commitPreparedTransaction(XidImpl.toBase64String(xid));
   }

   @TestTemplate
   public void testScaleDownWithConnector() throws Exception {
      scaleDown(control -> control.scaleDown("server2-connector"));
   }

   @TestTemplate
   public void testScaleDownWithOutConnector() throws Exception {
      scaleDown(control -> control.scaleDown(null));
   }

   @TestTemplate
   public void testForceFailover() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      try {
         serverControl.forceFailover();
      } catch (Exception e) {
         if (!usingCore()) {
            fail(e.getMessage());
         }
      }
      Wait.waitFor(() -> !server.isStarted());
      assertFalse(server.isStarted());
   }

   @TestTemplate
   public void testTotalMessageCount() throws Exception {
      String random1 = RandomUtil.randomUUIDString();
      String random2 = RandomUtil.randomUUIDString();

      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();

      if (legacyCreateQueue) {
         session.createQueue(random1, RoutingType.ANYCAST, random1);
         session.createQueue(random2, RoutingType.ANYCAST, random2);
      } else {
         session.createQueue(QueueConfiguration.of(random1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(QueueConfiguration.of(random2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientProducer producer1 = session.createProducer(random1);
      ClientProducer producer2 = session.createProducer(random2);
      ClientMessage message = session.createMessage(true);
      producer1.send(message);
      producer2.send(message);

      session.commit();

      // flush executors on queues so we can get precise number of messages
      Queue queue1 = server.locateQueue(SimpleString.of(random1));
      queue1.flushExecutor();
      Queue queue2 = server.locateQueue(SimpleString.of(random1));
      queue2.flushExecutor();

      assertEquals(2, serverControl.getTotalMessageCount());

      session.deleteQueue(random1);
      session.deleteQueue(random2);

      session.close();

      locator.close();
   }

   @TestTemplate
   public void testConnectionCounts() throws Exception {
      final int CONNECTION_COUNT = 100;

      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      for (int i = 0; i < CONNECTION_COUNT; i++) {
         createSessionFactory(locator).close();
      }

      assertEquals(CONNECTION_COUNT + (usingCore() ? 1 : 0), serverControl.getTotalConnectionCount());
      assertEquals((usingCore() ? 1 : 0), serverControl.getConnectionCount());

      locator.close();
   }

   @TestTemplate
   public void testSessionCounts() throws Exception {
      // don't test this with management messages as it completely throws off the session counts
      assumeFalse(usingCore());
      final int SESSION_COUNT = 100;

      ActiveMQServerControl serverControl = createManagementControl();
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      List<ClientSession> sessions = new ArrayList<>(SESSION_COUNT);

      assertEquals(0, serverControl.getSessionCount());

      for (int i = 1; i <= SESSION_COUNT; i++) {
         sessions.add(csf.createSession());
         assertEquals(i, serverControl.getSessionCount());
      }

      assertEquals(SESSION_COUNT, serverControl.getTotalSessionCount());

      for (int i = 1; i <= SESSION_COUNT; i++) {
         sessions.get(i - 1).close();
         assertEquals(SESSION_COUNT - i, serverControl.getSessionCount());
      }

      assertEquals(SESSION_COUNT, serverControl.getTotalSessionCount());
      assertEquals(0, serverControl.getSessionCount());

      locator.close();
   }

   @TestTemplate
   public void testTotalMessagesAdded() throws Exception {
      String random1 = RandomUtil.randomUUIDString();
      String random2 = RandomUtil.randomUUIDString();

      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();

      if (legacyCreateQueue) {
         session.createQueue(random1, RoutingType.ANYCAST, random1);
         session.createQueue(random2, RoutingType.ANYCAST, random2);
      } else {
         session.createQueue(QueueConfiguration.of(random1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(QueueConfiguration.of(random2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientProducer producer1 = session.createProducer(random1);
      ClientProducer producer2 = session.createProducer(random2);
      ClientMessage message = session.createMessage(false);
      producer1.send(message);
      producer2.send(message);

      session.commit();

      ClientConsumer consumer1 = session.createConsumer(random1);
      ClientConsumer consumer2 = session.createConsumer(random2);

      session.start();

      assertNotNull(consumer1.receive().acknowledge());
      assertNotNull(consumer2.receive().acknowledge());

      session.commit();

      assertEquals(2, serverControl.getTotalMessagesAdded());
      assertEquals(0, serverControl.getTotalMessageCount());

      consumer1.close();
      consumer2.close();

      session.deleteQueue(random1);
      session.deleteQueue(random2);

      session.close();

      locator.close();
   }


   @TestTemplate
   public void testTotalMessagesAcknowledged() throws Exception {
      String random1 = RandomUtil.randomUUIDString();
      String random2 = RandomUtil.randomUUIDString();

      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();

      if (legacyCreateQueue) {
         session.createQueue(random1, RoutingType.ANYCAST, random1);
         session.createQueue(random2, RoutingType.ANYCAST, random2);
      } else {
         session.createQueue(QueueConfiguration.of(random1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(QueueConfiguration.of(random2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientProducer producer1 = session.createProducer(random1);
      ClientProducer producer2 = session.createProducer(random2);
      ClientMessage message = session.createMessage(false);
      producer1.send(message);
      producer2.send(message);

      session.commit();

      ClientConsumer consumer1 = session.createConsumer(random1);
      ClientConsumer consumer2 = session.createConsumer(random2);

      session.start();

      assertNotNull(consumer1.receive().acknowledge());
      assertNotNull(consumer2.receive().acknowledge());

      session.commit();

      assertEquals(2, serverControl.getTotalMessagesAcknowledged());
      assertEquals(0, serverControl.getTotalMessageCount());

      consumer1.close();
      consumer2.close();

      session.deleteQueue(random1);
      session.deleteQueue(random2);

      session.close();

      locator.close();
   }

   @TestTemplate
   public void testTotalConsumerCount() throws Exception {
      String random1 = RandomUtil.randomUUIDString();
      String random2 = RandomUtil.randomUUIDString();

      ActiveMQServerControl serverControl = createManagementControl();
      QueueControl queueControl1 = ManagementControlHelper.createQueueControl(SimpleString.of(random1), SimpleString.of(random1), RoutingType.ANYCAST, mbeanServer);
      QueueControl queueControl2 = ManagementControlHelper.createQueueControl(SimpleString.of(random2), SimpleString.of(random2), RoutingType.ANYCAST, mbeanServer);

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();

      if (legacyCreateQueue) {
         session.createQueue(random1, RoutingType.ANYCAST, random1);
         session.createQueue(random2, RoutingType.ANYCAST, random2);
      } else {
         session.createQueue(QueueConfiguration.of(random1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(QueueConfiguration.of(random2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientConsumer consumer1 = session.createConsumer(random1);
      ClientConsumer consumer2 = session.createConsumer(random2);

      assertEquals(usingCore() ? 3 : 2, serverControl.getTotalConsumerCount());
      assertEquals(1, queueControl1.getConsumerCount());
      assertEquals(1, queueControl2.getConsumerCount());

      consumer1.close();
      consumer2.close();

      session.deleteQueue(random1);
      session.deleteQueue(random2);

      session.close();

      locator.close();
   }

   @TestTemplate
   public void testListConnectionsAsJSON() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      List<ClientSessionFactory> factories = new ArrayList<>();

      ServerLocator locator = createInVMNonHALocator();
      factories.add(createSessionFactory(locator));
      factories.add(createSessionFactory(locator));
      addClientSession(factories.get(1).createSession());

      String jsonString = serverControl.listConnectionsAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      assertEquals(usingCore() ? 3 : 2, array.size());

      JsonObject[] sorted = new JsonObject[array.size()];
      for (int i = 0; i < array.size(); i++) {
         sorted[i] = array.getJsonObject(i);
      }

      JsonObject first = null;
      JsonObject second = null;

      for (int i = 0; i < array.size(); i++) {
         JsonObject obj = array.getJsonObject(i);
         if (obj.getString("connectionID").equals(factories.get(0).getConnection().getID().toString())) {
            first = obj;
         }
         if (obj.getString("connectionID").equals(factories.get(1).getConnection().getID().toString())) {
            second = obj;
         }
      }

      assertNotNull(first);
      assertNotNull(second);

      assertFalse(first.getString("connectionID").isEmpty());
      assertFalse(first.getString("clientAddress").isEmpty());
      assertTrue(first.getJsonNumber("creationTime").longValue() > 0);
      assertEquals(0, first.getJsonNumber("sessionCount").longValue());

      assertFalse(second.getString("connectionID").isEmpty());
      assertFalse(second.getString("clientAddress").isEmpty());
      assertTrue(second.getJsonNumber("creationTime").longValue() > 0);
      assertEquals(1, second.getJsonNumber("sessionCount").longValue());
   }

   @TestTemplate
   public void testListConsumersAsJSON() throws Exception {
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      final String filter = "x = 1";
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = addClientSession(factory.createSession());
      server.addAddressInfo(new AddressInfo(queueName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      addClientConsumer(session.createConsumer(queueName));
      Thread.sleep(100); // We check the timestamp for the creation time. We need to make sure it's different
      addClientConsumer(session.createConsumer(queueName, SimpleString.of(filter), true));

      String jsonString = serverControl.listConsumersAsJSON(factory.getConnection().getID().toString());
      logger.debug(jsonString);
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      assertEquals(2, array.size());
      JsonObject first;
      JsonObject second;
      if (array.getJsonObject(0).getJsonNumber("creationTime").longValue() < array.getJsonObject(1).getJsonNumber("creationTime").longValue()) {
         first = array.getJsonObject(0);
         second = array.getJsonObject(1);
      } else {
         first = array.getJsonObject(1);
         second = array.getJsonObject(0);
      }

      assertNotNull(first.getJsonNumber(ConsumerField.ID.getAlternativeName()));
      assertFalse(first.getString(ConsumerField.CONNECTION.getAlternativeName()).isEmpty());
      assertEquals(factory.getConnection().getID().toString(), first.getString(ConsumerField.CONNECTION.getAlternativeName()));
      assertFalse(first.getString(ConsumerField.SESSION.getAlternativeName()).isEmpty());
      assertEquals(((ClientSessionImpl) session).getName(), first.getString(ConsumerField.SESSION.getAlternativeName()));
      assertFalse(first.getString(ConsumerField.QUEUE.getAlternativeName()).isEmpty());
      assertEquals(queueName.toString(), first.getString(ConsumerField.QUEUE.getAlternativeName()));
      assertFalse(first.getBoolean(ConsumerField.BROWSE_ONLY.getName()));
      assertTrue(first.getJsonNumber(ConsumerField.CREATION_TIME.getName()).longValue() > 0);
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      // test the old version that has been replaced for backward compatibility
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getAlternativeName()).longValue());

      assertNotNull(second.getJsonNumber(ConsumerField.ID.getAlternativeName()));
      assertFalse(second.getString(ConsumerField.CONNECTION.getAlternativeName()).isEmpty());
      assertEquals(factory.getConnection().getID().toString(), second.getString(ConsumerField.CONNECTION.getAlternativeName()));
      assertFalse(second.getString(ConsumerField.SESSION.getAlternativeName()).isEmpty());
      assertEquals(((ClientSessionImpl) session).getName(), second.getString(ConsumerField.SESSION.getAlternativeName()));
      assertFalse(second.getString(ConsumerField.QUEUE.getAlternativeName()).isEmpty());
      assertEquals(queueName.toString(), second.getString(ConsumerField.QUEUE.getAlternativeName()));
      assertTrue(second.getBoolean(ConsumerField.BROWSE_ONLY.getName()));
      assertTrue(second.getJsonNumber(ConsumerField.CREATION_TIME.getName()).longValue() > 0);
      assertEquals(0, second.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertFalse(second.getString(ConsumerField.FILTER.getName()).isEmpty());
      assertEquals(filter, second.getString(ConsumerField.FILTER.getName()));
   }

   @TestTemplate
   public void testListAllConsumersAsJSON() throws Exception {
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = addClientSession(factory.createSession());

      ServerLocator locator2 = createInVMNonHALocator();
      ClientSessionFactory factory2 = createSessionFactory(locator2);
      ClientSession session2 = addClientSession(factory2.createSession());
      serverControl.createAddress(queueName.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      addClientConsumer(session.createConsumer(queueName));
      Thread.sleep(200);
      addClientConsumer(session2.createConsumer(queueName));

      String jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      assertEquals(usingCore() ? 3 : 2, array.size());

      String key = "creationTime";
      JsonObject[] sorted = new JsonObject[array.size()];
      for (int i = 0; i < array.size(); i++) {
         sorted[i] = array.getJsonObject(i);
      }

      if (sorted[0].getJsonNumber(key).longValue() > sorted[1].getJsonNumber(key).longValue()) {
         JsonObject o = sorted[1];
         sorted[1] = sorted[0];
         sorted[0] = o;
      }
      if (usingCore()) {
         if (sorted[1].getJsonNumber(key).longValue() > sorted[2].getJsonNumber(key).longValue()) {
            JsonObject o = sorted[2];
            sorted[2] = sorted[1];
            sorted[1] = o;
         }
         if (sorted[0].getJsonNumber(key).longValue() > sorted[1].getJsonNumber(key).longValue()) {
            JsonObject o = sorted[1];
            sorted[1] = sorted[0];
            sorted[0] = o;
         }
      }

      JsonObject first = sorted[0];
      JsonObject second = sorted[1];

      assertTrue(first.getJsonNumber(ConsumerField.CREATION_TIME.getName()).longValue() > 0);
      assertNotNull(first.getJsonNumber(ConsumerField.ID.getAlternativeName()));
      assertFalse(first.getString(ConsumerField.CONNECTION.getAlternativeName()).isEmpty());
      assertEquals(factory.getConnection().getID().toString(), first.getString(ConsumerField.CONNECTION.getAlternativeName()));
      assertFalse(first.getString(ConsumerField.SESSION.getAlternativeName()).isEmpty());
      assertEquals(((ClientSessionImpl) session).getName(), first.getString(ConsumerField.SESSION.getAlternativeName()));
      assertFalse(first.getString(ConsumerField.QUEUE.getAlternativeName()).isEmpty());
      assertEquals(queueName.toString(), first.getString(ConsumerField.QUEUE.getAlternativeName()));
      assertFalse(first.getBoolean(ConsumerField.BROWSE_ONLY.getName()));
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.LAST_DELIVERED_TIME.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.LAST_ACKNOWLEDGED_TIME.getName()).longValue());

      assertTrue(second.getJsonNumber(ConsumerField.CREATION_TIME.getName()).longValue() > 0);
      assertNotNull(second.getJsonNumber(ConsumerField.ID.getAlternativeName()));
      assertFalse(second.getString(ConsumerField.CONNECTION.getAlternativeName()).isEmpty());
      assertEquals(factory2.getConnection().getID().toString(), second.getString(ConsumerField.CONNECTION.getAlternativeName()));
      assertFalse(second.getString(ConsumerField.SESSION.getAlternativeName()).isEmpty());
      assertEquals(((ClientSessionImpl) session2).getName(), second.getString(ConsumerField.SESSION.getAlternativeName()));
      assertFalse(second.getString(ConsumerField.QUEUE.getAlternativeName()).isEmpty());
      assertEquals(queueName.toString(), second.getString(ConsumerField.QUEUE.getAlternativeName()));
      assertFalse(second.getBoolean(ConsumerField.BROWSE_ONLY.getName()));
      assertEquals(0, second.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(0, second.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(0, second.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(0, second.getJsonNumber(ConsumerField.LAST_DELIVERED_TIME.getName()).longValue());
      assertEquals(0, second.getJsonNumber(ConsumerField.LAST_ACKNOWLEDGED_TIME.getName()).longValue());

   }

   @TestTemplate
   public void testListAllConsumersAsJSONTXCommit() throws Exception {
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = factory.createSession(true, false, 1);
      addClientSession(session);

      serverControl.createAddress(queueName.toString(), RoutingType.ANYCAST.name());
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientConsumer consumer = session.createConsumer(queueName, null, 100, -1, false);
      addClientConsumer(consumer);
      session.start();

      ClientProducer producer = session.createProducer(queueName);
      int size = 0;
      ClientMessage receive = null;
      for (int i = 0; i < 100; i++) {
         ClientMessage message = session.createMessage(true);

         producer.send(message);
         size += message.getEncodeSize();
         receive = consumer.receive();
      }

      String jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      JsonObject first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
      receive.acknowledge();
      session.commit();

      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());

      int allSize = size;
      for (int i = 0; i < 100; i++) {
         ClientMessage message = session.createMessage(true);

         producer.send(message);
         allSize += message.getEncodeSize();
         receive = consumer.receive();
      }

      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(200, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(allSize, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
   }

   @TestTemplate
   public void testListAllConsumersAsJSONTXCommitAck() throws Exception {
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = factory.createSession(true, false, 1);
      addClientSession(session);

      serverControl.createAddress(queueName.toString(), RoutingType.ANYCAST.name());
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientConsumer consumer = session.createConsumer(queueName, null, 100, -1, false);
      addClientConsumer(consumer);
      session.start();

      ClientProducer producer = session.createProducer(queueName);
      int size = 0;
      ClientMessage receive = null;
      for (int i = 0; i < 100; i++) {
         ClientMessage message = session.createMessage(true);

         producer.send(message);
         size += message.getEncodeSize();
         receive = consumer.receive();
         receive.acknowledge();
      }

      Wait.assertEquals(0, () -> JsonUtil.readJsonArray(serverControl.listAllConsumersAsJSON()).get(0).asJsonObject().getInt(ConsumerField.MESSAGES_IN_TRANSIT.getName()));

      String jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      JsonObject first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
      session.commit();

      Wait.assertEquals(0, () -> JsonUtil.readJsonArray(serverControl.listAllConsumersAsJSON()).get(0).asJsonObject().getInt(ConsumerField.MESSAGES_IN_TRANSIT.getName()));

      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
   }

   @TestTemplate
   public void testListAllConsumersAsJSONTXRollback() throws Exception {
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = factory.createSession(true, false, 1);
      addClientSession(session);

      serverControl.createAddress(queueName.toString(), RoutingType.ANYCAST.name());
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientConsumer consumer = session.createConsumer(queueName, null, 100, -1, false);
      addClientConsumer(consumer);
      session.start();

      ClientProducer producer = session.createProducer(queueName);
      int size = 0;
      ClientMessage receive = null;
      for (int i = 0; i < 100; i++) {
         ClientMessage message = session.createMessage(true);

         producer.send(message);
         size += message.getEncodeSize();
         receive = consumer.receive();
      }

      String jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      JsonObject first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
      receive.acknowledge();   //stop the session so we dont receive the same messages
      session.stop();
      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());

      session.rollback();
      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());

      int allSize = size * 2;
      session.start();
      for (int i = 0; i < 100; i++) {
         receive = consumer.receive();
      }

      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(200, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(allSize, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());

      receive.acknowledge();
      //stop the session so we dont receive the same messages
      session.stop();
      session.rollback();
      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(200, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(allSize, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(200, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
   }

   @TestTemplate
   public void testListAllConsumersAsJSONCancel() throws Exception {
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = factory.createSession(true, false, 1);
      addClientSession(session);

      serverControl.createAddress(queueName.toString(), RoutingType.ANYCAST.name());
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientConsumer consumer = session.createConsumer(queueName, null, 100, -1, false);
      addClientConsumer(consumer);
      session.start();

      ClientProducer producer = session.createProducer(queueName);
      int size = 0;
      ClientMessage receive = null;
      for (int i = 0; i < 100; i++) {
         ClientMessage message = session.createMessage(true);

         producer.send(message);
         size += message.getEncodeSize();
         receive = consumer.receive();
      }

      String jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      JsonObject first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
      receive.acknowledge();
      //close the consumer to cancel messages and recreate
      consumer.close();
      session.close();
      session = factory.createSession(true, false, 1);
      addClientSession(session);

      consumer = session.createConsumer(queueName, null, 100, -1, false);
      addClientConsumer(consumer);
      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
   }

   @TestTemplate
   public void testListAllConsumersAsJSONNoTX() throws Exception {
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = factory.createSession(true, true, 1);
      addClientSession(session);

      serverControl.createAddress(queueName.toString(), RoutingType.ANYCAST.name());
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientConsumer consumer = session.createConsumer(queueName, null, 100, -1, false);
      addClientConsumer(consumer);
      session.start();

      ClientProducer producer = session.createProducer(queueName);
      int size = 0;
      ClientMessage receive = null;
      for (int i = 0; i < 100; i++) {
         ClientMessage message = session.createMessage(true);

         producer.send(message);
         size += message.getEncodeSize();
         receive = consumer.receive();
         receive.acknowledge();
      }

      Wait.assertEquals(0, () -> JsonUtil.readJsonArray(serverControl.listAllConsumersAsJSON()).get(0).asJsonObject().getInt(ConsumerField.MESSAGES_IN_TRANSIT.getName()));

      String jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      JsonObject first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
   }

   @TestTemplate
   public void testListAllConsumersAsJSONXACommit() throws Exception {
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession sendSession = factory.createSession();
      addClientSession(sendSession);
      ClientSession session = factory.createSession(true, false, false);
      addClientSession(session);

      serverControl.createAddress(queueName.toString(), RoutingType.ANYCAST.name());
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientConsumer consumer = session.createConsumer(queueName, null, 100, -1, false);
      addClientConsumer(consumer);
      session.start();
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      session.start(xid, XAResource.TMNOFLAGS);

      ClientProducer producer = sendSession.createProducer(queueName);
      int size = 0;
      ClientMessage receive = null;
      for (int i = 0; i < 100; i++) {
         ClientMessage message = sendSession.createMessage(true);

         producer.send(message);
         size += message.getEncodeSize();
         receive = consumer.receive();
         receive.acknowledge();
      }

      String jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      JsonObject first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());

      session.end(xid, XAResource.TMSUCCESS);
      session.prepare(xid);
      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
      session.commit(xid, false);
      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
   }

   @TestTemplate
   public void testListAllConsumersAsJSONXARollback() throws Exception {
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession sendSession = factory.createSession();
      addClientSession(sendSession);
      ClientSession session = factory.createSession(true, false, false);
      addClientSession(session);

      serverControl.createAddress(queueName.toString(), RoutingType.ANYCAST.name());
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientConsumer consumer = session.createConsumer(queueName, null, 100, -1, false);
      addClientConsumer(consumer);
      session.start();
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      session.start(xid, XAResource.TMNOFLAGS);

      ClientProducer producer = sendSession.createProducer(queueName);
      int size = 0;
      ClientMessage receive = null;
      for (int i = 0; i < 100; i++) {
         ClientMessage message = sendSession.createMessage(true);

         producer.send(message);
         size += message.getEncodeSize();
         receive = consumer.receive();
         receive.acknowledge();
      }

      String jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      JsonObject first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());

      session.end(xid, XAResource.TMSUCCESS);
      session.prepare(xid);
      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
      session.stop();
      session.rollback(xid);
      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
   }

   @TestTemplate
   public void testListSessionsAsJSON() throws Exception {
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      server.addAddressInfo(new AddressInfo(queueName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session1 = addClientSession(factory.createSession());
      ClientSession session2 = addClientSession(factory.createSession("myUser", "myPass", false, false, false, false, 0));
      session2.createConsumer(queueName);

      String jsonString = serverControl.listSessionsAsJSON(factory.getConnection().getID().toString());
      logger.debug(jsonString);
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      assertEquals(2, array.size());
      JsonObject first = lookupSession(array, session1);
      JsonObject second = lookupSession(array, session2);

      assertFalse(first.getString("sessionID").isEmpty());
      assertEquals(((ClientSessionImpl) session1).getName(), first.getString("sessionID"));
      assertFalse(first.getString("principal").isEmpty());
      assertEquals("guest", first.getString("principal"));
      assertTrue(first.getJsonNumber("creationTime").longValue() > 0);
      assertEquals(0, first.getJsonNumber("consumerCount").longValue());

      assertFalse(second.getString("sessionID").isEmpty());
      assertEquals(((ClientSessionImpl) session2).getName(), second.getString("sessionID"));
      assertFalse(second.getString("principal").isEmpty());
      assertEquals("myUser", second.getString("principal"));
      assertTrue(second.getJsonNumber("creationTime").longValue() > 0);
      assertEquals(1, second.getJsonNumber("consumerCount").longValue());
   }

   private JsonObject lookupSession(JsonArray jsonArray, ClientSession session) throws Exception {
      String name = ((ClientSessionImpl)session).getName();

      for (int i = 0; i < jsonArray.size(); i++) {
         JsonObject obj = jsonArray.getJsonObject(i);
         String sessionID = obj.getString("sessionID");
         assertNotNull(sessionID);

         if (sessionID.equals(name)) {
            return obj;
         }
      }

      fail("Sesison not found for session id " + name);

      // not going to happen, fail will throw an exception but it won't compile without this
      return null;
   }

   @TestTemplate
   public void testListAllSessionsAsJSON() throws Exception {
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      server.addAddressInfo(new AddressInfo(queueName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ServerLocator locator2 = createInVMNonHALocator();
      ClientSessionFactory factory2 = createSessionFactory(locator2);
      ClientSession session1 = addClientSession(factory.createSession());
      Thread.sleep(5);
      ClientSession session2 = addClientSession(factory2.createSession("myUser", "myPass", false, false, false, false, 0));
      session2.addMetaData("foo", "bar");
      session2.addMetaData("bar", "baz");
      session2.createConsumer(queueName);

      String jsonString = serverControl.listAllSessionsAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      assertEquals(2 + (usingCore() ? 1 : 0), array.size());
      JsonObject first = lookupSession(array, session1);
      JsonObject second = lookupSession(array, session2);

      assertFalse(first.getString("sessionID").isEmpty());
      assertEquals(((ClientSessionImpl) session1).getName(), first.getString("sessionID"));
      assertFalse(first.getString("principal").isEmpty());
      assertEquals("guest", first.getString("principal"));
      assertTrue(first.getJsonNumber("creationTime").longValue() > 0);
      assertEquals(0, first.getJsonNumber("consumerCount").longValue());

      assertFalse(second.getString("sessionID").isEmpty());
      assertEquals(((ClientSessionImpl) session2).getName(), second.getString("sessionID"));
      assertFalse(second.getString("principal").isEmpty());
      assertEquals("myUser", second.getString("principal"));
      assertTrue(second.getJsonNumber("creationTime").longValue() > 0);
      assertEquals(1, second.getJsonNumber("consumerCount").longValue());
      assertEquals("bar", second.getJsonObject("metadata").getJsonString("foo").getString());
      assertEquals("baz", second.getJsonObject("metadata").getJsonString("bar").getString());
   }

   @TestTemplate
   public void testListAllSessionsAsJSONWithJMS() throws Exception {
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      server.addAddressInfo(new AddressInfo(queueName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      ActiveMQServerControl serverControl = createManagementControl();

      ActiveMQConnectionFactory cf = ActiveMQJMSClient.createConnectionFactory("vm://0", "cf");
      Connection con = cf.createConnection();
      String clientID = UUID.randomUUID().toString();
      con.setClientID(clientID);

      String jsonString = serverControl.listAllSessionsAsJSON();
      logger.debug(jsonString);
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      assertEquals(1 + (usingCore() ? 1 : 0), array.size());
      JsonObject obj = lookupSession(array, ((ActiveMQConnection)con).getInitialSession());
      assertEquals(obj.getJsonObject("metadata").getJsonString(ClientSession.JMS_SESSION_CLIENT_ID_PROPERTY).getString(), clientID);
      assertNotNull(obj.getJsonObject("metadata").getJsonString(ClientSession.JMS_SESSION_IDENTIFIER_PROPERTY));
   }

   @TestTemplate
   public void testListQueues() throws Exception {
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString queueName2 = SimpleString.of("my_queue_two");
      SimpleString queueName3 = SimpleString.of("other_queue_three");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(queueName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      server.addAddressInfo(new AddressInfo(queueName2, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName2, RoutingType.ANYCAST, queueName2, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      server.addAddressInfo(new AddressInfo(queueName3, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName3, RoutingType.ANYCAST, queueName3, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName3).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      //test with filter that matches 2 queues
      String filterString = createJsonFilter("name", "CONTAINS", "my_queue");

      String queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

      JsonObject queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      JsonArray array = (JsonArray) queuesAsJsonObject.get("data");

      assertEquals(2, array.size(), "number of queues returned from query");
      assertTrue(array.getJsonObject(0).getString("name").contains("my_queue"));
      assertTrue(array.getJsonObject(1).getString("name").contains("my_queue"));

      //test with an empty filter
      filterString = createJsonFilter("internalQueue", "NOT_CONTAINS", "true");

      queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");

      // at least 3 queues or more
      assertTrue(3 <= array.size(), "number of queues returned from query");

      //test with small page size
      queuesAsJsonString = serverControl.listQueues(filterString, 1, 1);

      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");

      assertEquals(1, array.size(), "number of queues returned from query");
      //check all field names are available
      assertNotEquals("", array.getJsonObject(0).getString("name"), "name");
      assertNotEquals("", array.getJsonObject(0).getString("id"), "id");
      assertNotEquals("", array.getJsonObject(0).getString("address"), "address");
      assertEquals("", array.getJsonObject(0).getString("filter"), "filter");
      assertEquals("false", array.getJsonObject(0).getString("durable"), "durable");
      assertEquals("false", array.getJsonObject(0).getString("paused"), "paused");
      assertNotEquals("", array.getJsonObject(0).getString("temporary"), "temporary");
      assertEquals("false", array.getJsonObject(0).getString("purgeOnNoConsumers"), "purgeOnNoConsumers");
      assertNotEquals("", array.getJsonObject(0).getString("consumerCount"), "consumerCount");
      assertEquals("-1", array.getJsonObject(0).getString("maxConsumers"), "maxConsumers");
      assertEquals("false", array.getJsonObject(0).getString("autoCreated"), "autoCreated");
      assertEquals(usingCore() ? "guest" : "", array.getJsonObject(0).getString("user"), "user");
      assertNotEquals("", array.getJsonObject(0).getString("routingType"), "routingType");
      assertEquals("0", array.getJsonObject(0).getString("messagesAdded"), "messagesAdded");
      assertEquals("0", array.getJsonObject(0).getString("messageCount"), "messageCount");
      assertEquals("0", array.getJsonObject(0).getString("messagesAcked"), "messagesAcked");
      assertEquals("0", array.getJsonObject(0).getString("deliveringCount"), "deliveringCount");
      assertEquals("0", array.getJsonObject(0).getString("messagesKilled"), "messagesKilled");
      assertEquals("false", array.getJsonObject(0).getString("exclusive"), "exclusive");
      assertEquals("false", array.getJsonObject(0).getString("lastValue"), "lastValue");
      assertEquals("0", array.getJsonObject(0).getString("scheduledCount"), "scheduledCount");
      assertEquals("false", array.getJsonObject(0).getString("groupRebalance"), "groupRebalance");
      assertEquals("-1", array.getJsonObject(0).getString("groupBuckets"), "groupBuckets");
      assertEquals("", array.getJsonObject(0).getString("groupFirstKey"), "groupFirstKey");
      assertEquals("false", array.getJsonObject(0).getString("autoDelete"), "autoDelete");
   }

   @TestTemplate
   public void testListQueuesOrder() throws Exception {
      SimpleString queueName1 = SimpleString.of("my_queue_1");
      SimpleString queueName2 = SimpleString.of("my_queue_2");
      SimpleString queueName3 = SimpleString.of("my_queue_3");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(queueName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName1, RoutingType.ANYCAST, queueName1, SimpleString.of("filter1"), null, true,
                            false, false, 20, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setRoutingType(RoutingType.ANYCAST).setFilterString("filter1").setMaxConsumers(20).setAutoCreateAddress(false));
      }
      Thread.sleep(500);
      server.addAddressInfo(new AddressInfo(queueName2, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName2, RoutingType.ANYCAST, queueName2, SimpleString.of("filter3"), null, true,
                            false, true, 40, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName2).setRoutingType(RoutingType.ANYCAST).setFilterString("filter3").setAutoCreated(true).setMaxConsumers(40).setAutoCreateAddress(false));
      }
      Thread.sleep(500);
      server.addAddressInfo(new AddressInfo(queueName3, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName3, RoutingType.ANYCAST, queueName3,  SimpleString.of("filter0"), null, true,
                            false, false, 10, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName3).setRoutingType(RoutingType.ANYCAST).setFilterString("filter0").setMaxConsumers(10).setAutoCreateAddress(false));
      }

      //test default order
      String filterString = createJsonFilter("name", "CONTAINS", "my_queue");
      String queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);
      JsonObject queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      JsonArray array = (JsonArray) queuesAsJsonObject.get("data");

      assertEquals(3, array.size(), "number of queues returned from query");
      assertEquals(queueName1.toString(), array.getJsonObject(0).getString("name"), "queue1 default Order");
      assertEquals(queueName2.toString(), array.getJsonObject(1).getString("name"), "queue2 default Order");
      assertEquals(queueName3.toString(), array.getJsonObject(2).getString("name"), "queue3 default Order");

      //test ordered by id desc
      filterString = createJsonFilter("name", "CONTAINS", "my_queue", "id", "desc");
      queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);
      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");

      assertEquals(3, array.size(), "number of queues returned from query");
      assertEquals(queueName3.toString(), array.getJsonObject(0).getString("name"), "queue3 ordered by id");
      assertEquals(queueName2.toString(), array.getJsonObject(1).getString("name"), "queue2 ordered by id");
      assertEquals(queueName1.toString(), array.getJsonObject(2).getString("name"), "queue1 ordered by id");

      //ordered by address desc
      filterString = createJsonFilter("name", "CONTAINS", "my_queue", "address", "desc");
      queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);
      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");

      assertEquals(3, array.size(), "number of queues returned from query");
      assertEquals(queueName3.toString(), array.getJsonObject(0).getString("name"), "queue3 ordered by address");
      assertEquals(queueName2.toString(), array.getJsonObject(1).getString("name"), "queue2 ordered by address");
      assertEquals(queueName1.toString(), array.getJsonObject(2).getString("name"), "queue1 ordered by address");

      //ordered by auto create desc
      filterString = createJsonFilter("name", "CONTAINS", "my_queue", "autoCreated", "asc");
      queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);
      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");

      assertEquals(3, array.size(), "number of queues returned from query");
      assertEquals("false", array.getJsonObject(0).getString("autoCreated"), "pos1 ordered by autocreate");
      assertEquals("false", array.getJsonObject(1).getString("autoCreated"), "pos2 ordered by autocreate");
      assertEquals("true", array.getJsonObject(2).getString("autoCreated"), "pos3 ordered by autocreate");

      //ordered by filter desc
      filterString = createJsonFilter("name", "CONTAINS", "my_queue", "filter", "desc");
      queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);
      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");

      assertEquals(3, array.size(), "number of queues returned from query");
      assertEquals(queueName2.toString(), array.getJsonObject(0).getString("name"), "queue2 ordered by filter");
      assertEquals(queueName1.toString(), array.getJsonObject(1).getString("name"), "queue1 ordered by filter");
      assertEquals(queueName3.toString(), array.getJsonObject(2).getString("name"), "queue3 ordered by filter");

      //ordered by max consumers asc
      filterString = createJsonFilter("name", "CONTAINS", "my_queue", "maxConsumers", "asc");
      queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);
      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");

      assertEquals(3, array.size(), "number of queues returned from query");
      assertEquals(queueName3.toString(), array.getJsonObject(0).getString("name"), "queue3 ordered by filter");
      assertEquals(queueName1.toString(), array.getJsonObject(1).getString("name"), "queue1 ordered by filter");
      assertEquals(queueName2.toString(), array.getJsonObject(2).getString("name"), "queue2 ordered by filter");

      //ordering between the pages
      //page 1
      filterString = createJsonFilter("name", "CONTAINS", "my_queue", "address", "desc");
      queuesAsJsonString = serverControl.listQueues(filterString, 1, 1);
      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");
      assertEquals(1, array.size(), "number of queues returned from query");
      assertEquals(queueName3.toString(), array.getJsonObject(0).getString("name"), "queue3 ordered by page");

      //page 2
      filterString = createJsonFilter("name", "CONTAINS", "my_queue", "address", "desc");
      queuesAsJsonString = serverControl.listQueues(filterString, 2, 1);
      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");
      assertEquals(1, array.size(), "number of queues returned from query");
      assertEquals(queueName2.toString(), array.getJsonObject(0).getString("name"), "queue2 ordered by page");

      //page 3
      filterString = createJsonFilter("name", "CONTAINS", "my_queue", "address", "desc");
      queuesAsJsonString = serverControl.listQueues(filterString, 3, 1);
      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");
      assertEquals(1, array.size(), "number of queues returned from query");
      assertEquals(queueName1.toString(), array.getJsonObject(0).getString("name"), "queue1 ordered by page");

   }

   @TestTemplate
   public void testListQueuesNumericFilter() throws Exception {
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString queueName2 = SimpleString.of("my_queue_two");
      SimpleString queueName3 = SimpleString.of("one_consumer_queue_three");
      SimpleString queueName4 = SimpleString.of("my_queue_four");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(queueName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      server.addAddressInfo(new AddressInfo(queueName2, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName2, RoutingType.ANYCAST, queueName2, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      server.addAddressInfo(new AddressInfo(queueName3, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName3, RoutingType.ANYCAST, queueName3, null, false, false, 10, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName3).setRoutingType(RoutingType.ANYCAST).setDurable(false).setMaxConsumers(10));
      }

      server.addAddressInfo(new AddressInfo(queueName4, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName4, RoutingType.ANYCAST, queueName4, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName4).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator);
           ClientSession session = csf.createSession()) {

         session.start();
         ClientConsumer consumer1_q1 = session.createConsumer(queueName1);
         ClientConsumer consumer2_q1 = session.createConsumer(queueName1);
         ClientConsumer consumer1_q2 = session.createConsumer(queueName2);
         ClientConsumer consumer2_q2 = session.createConsumer(queueName2);
         ClientConsumer consumer3_q2 = session.createConsumer(queueName2);
         ClientConsumer consumer1_q3 = session.createConsumer(queueName3);

         ClientProducer clientProducer = session.createProducer(queueName1);
         ClientMessage message = session.createMessage(false);
         for (int i = 0; i < 10; i++) {
            clientProducer.send(message);
         }

         //consume one message
         ClientMessage messageReceived = consumer1_q1.receive(100);
         if (messageReceived == null) {
            fail("should have received a message");
         }
         messageReceived.acknowledge();
         session.commit();

         //test with CONTAINS returns nothing for numeric field
         String filterString = createJsonFilter("CONSUMER_COUNT", "CONTAINS", "0");
         String queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         JsonObject queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         JsonArray array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(0, array.size(), "number of queues returned from query");

         //test with LESS_THAN returns 1 queue
         filterString = createJsonFilter("CONSUMER_COUNT", "LESS_THAN", "1");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(2, array.size(), "number of queues returned from LESS_THAN query");
         assertEquals(queueName4.toString(), array.getJsonObject(1).getString("name"), "correct queue returned from query");

         //test with GREATER_THAN returns 2 queue
         filterString = createJsonFilter("CONSUMER_COUNT", "GREATER_THAN", "2");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(1, array.size(), "number of queues returned from GREATER_THAN query");
         assertEquals(queueName2.toString(), array.getJsonObject(0).getString("name"), "correct queue returned from query");

         //test with GREATER_THAN returns 2 queue
         filterString = createJsonFilter("CONSUMER_COUNT", "EQUALS", "3");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(1, array.size(), "number of queues returned from EQUALS query");
         assertEquals(queueName2.toString(), array.getJsonObject(0).getString("name"), "correct queue returned from query");

         //test with MESSAGE_COUNT returns 2 queue
         filterString = createJsonFilter("MESSAGE_COUNT", "GREATER_THAN", "5");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(1, array.size(), "number of queues returned from MESSAGE_COUNT query");
         assertEquals(queueName1.toString(), array.getJsonObject(0).getString("name"), "correct queue returned from query");

         //test with MESSAGE_ADDED returns 1 queue
         filterString = createJsonFilter("MESSAGES_ADDED", "GREATER_THAN", "5");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(1, array.size(), "number of queues returned from MESSAGE_COUNT query");
         assertEquals(queueName1.toString(), array.getJsonObject(0).getString("name"), "correct queue returned from query");

         //test with DELIVERING_COUNT returns 1 queue
         filterString = createJsonFilter("DELIVERING_COUNT", "GREATER_THAN", "5");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(1, array.size(), "number of queues returned from DELIVERING_COUNT query");
         assertEquals(queueName1.toString(), array.getJsonObject(0).getString("name"), "correct queue returned from query");

         //test with MESSAGE_ACKED returns 1 queue
         filterString = createJsonFilter("MESSAGES_ACKED", "GREATER_THAN", "0");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(1, array.size(), "number of queues returned from MESSAGES_ACKED query");
         assertEquals(queueName1.toString(), array.getJsonObject(0).getString("name"), "correct queue returned from query");

         //test with MAX_CONSUMERS returns 1 queue
         filterString = createJsonFilter("MAX_CONSUMERS", "GREATER_THAN", "9");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(1, array.size(), "number of queues returned from MAX_CONSUMERS query");
         assertEquals(queueName3.toString(), array.getJsonObject(0).getString("name"), "correct queue returned from query");

         //test with MESSAGES_KILLED returns 0 queue
         filterString = createJsonFilter("MESSAGES_KILLED", "GREATER_THAN", "0");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(0, array.size(), "number of queues returned from MESSAGES_KILLED query");

      }

   }

   @TestTemplate
   public void testListQueuesNumericFilterInvalid() throws Exception {
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString queueName2 = SimpleString.of("one_consumer_queue_two");
      SimpleString queueName3 = SimpleString.of("one_consumer_queue_three");
      SimpleString queueName4 = SimpleString.of("my_queue_four");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(queueName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      server.addAddressInfo(new AddressInfo(queueName2, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName2, RoutingType.ANYCAST, queueName2, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      server.addAddressInfo(new AddressInfo(queueName3, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName3, RoutingType.ANYCAST, queueName3, null, false, false, 10, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName3).setRoutingType(RoutingType.ANYCAST).setDurable(false).setMaxConsumers(10));
      }

      server.addAddressInfo(new AddressInfo(queueName4, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName4, RoutingType.ANYCAST, queueName4, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName4).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator);
           ClientSession session = csf.createSession()) {

         session.start();
         ClientConsumer consumer1_q1 = session.createConsumer(queueName1);
         ClientConsumer consumer2_q1 = session.createConsumer(queueName1);

         //test with CONTAINS returns nothing for numeric field
         String filterString = createJsonFilter("CONSUMER_COUNT", "CONTAINS", "NOT_NUMBER");
         String queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         JsonObject queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         JsonArray array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(0, array.size(), "number of queues returned from query");

         //test with LESS_THAN and not a number
         filterString = createJsonFilter("CONSUMER_COUNT", "LESS_THAN", "NOT_NUMBER");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(0, array.size(), "number of queues returned from LESS_THAN query");

         //test with GREATER_THAN and not a number
         filterString = createJsonFilter("CONSUMER_COUNT", "GREATER_THAN", "NOT_NUMBER");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(0, array.size(), "number of queues returned from GREATER_THAN query");

         //test with EQUALS and not number
         filterString = createJsonFilter("CONSUMER_COUNT", "EQUALS", "NOT_NUMBER");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(0, array.size(), "number of queues returned from EQUALS query");

         //test with LESS_THAN on string value returns no queue
         filterString = createJsonFilter("name", "LESS_THAN", "my_queue");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(0, array.size(), "number of queues returned from LESS_THAN on non numeric field");

         //test with GREATER_THAN on string value returns no queue
         filterString = createJsonFilter("name", "GREATER_THAN", "my_queue");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(0, array.size(), "number of queues returned from GREATER_THAN on non numeric field");

         //test with GREATER_THAN and empty string
         filterString = createJsonFilter("CONSUMER_COUNT", "GREATER_THAN", " ");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(0, array.size(), "number of queues returned from GREATER_THAN query");

         //test with CONSUMER_COUNT against a float value
         filterString = createJsonFilter("CONSUMER_COUNT", "GREATER_THAN", "0.12");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         assertEquals(0, array.size(), "number of queues returned from GREATER_THAN query");

      }

   }

   @TestTemplate
   public void testListAddresses() throws Exception {
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString queueName2 = SimpleString.of("my_queue_two");
      SimpleString queueName3 = SimpleString.of("other_queue_three");
      SimpleString queueName4 = SimpleString.of("other_queue_four");

      SimpleString addressName1 = SimpleString.of("my_address_one");
      SimpleString addressName2 = SimpleString.of("my_address_two");
      SimpleString addressName3 = SimpleString.of("other_address_three");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      server.addAddressInfo(new AddressInfo(addressName2, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName2, RoutingType.ANYCAST, queueName2, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName2).setAddress(addressName2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      server.addAddressInfo(new AddressInfo(addressName3, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName3, RoutingType.ANYCAST, queueName3, null, false, false);
         server.createQueue(addressName3, RoutingType.ANYCAST, queueName4, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName3).setAddress(addressName3).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         server.createQueue(QueueConfiguration.of(queueName4).setAddress(addressName3).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      //test with CONTAINS filter
      String filterString = createJsonFilter("name", "CONTAINS", "my_address");
      String addressesAsJsonString = serverControl.listAddresses(filterString, 1, 50);
      JsonObject addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      JsonArray array = (JsonArray) addressesAsJsonObject.get("data");

      assertEquals(2, array.size(), "number of addresses returned from query");
      assertTrue(array.getJsonObject(0).getString("name").contains("my_address"), "address name check");
      assertTrue(array.getJsonObject(1).getString("name").contains("my_address"), "address name check");

      //test with EQUALS filter
      filterString = createJsonFilter("name", "EQUALS", addressName1.toString());
      addressesAsJsonString = serverControl.listAddresses(filterString, 1, 50);
      addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      array = (JsonArray) addressesAsJsonObject.get("data");
      System.out.println(array);

      assertEquals(1, array.size(), "number of addresses returned from query");
      //check all field names
      assertEquals(addressName1.toString(), array.getJsonObject(0).getString("name"), "address name check");
      assertNotEquals("", array.getJsonObject(0).getString("id"), "id");
      assertTrue(array.getJsonObject(0).getString("routingTypes").contains(RoutingType.ANYCAST.name()), "routingTypes");
      assertEquals("1", array.getJsonObject(0).getString("queueCount"), "queueCount");

      //test with empty filter - all addresses should be returned
      filterString = createJsonFilter("", "", "");
      addressesAsJsonString = serverControl.listAddresses(filterString, 1, 50);
      addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      array = (JsonArray) addressesAsJsonObject.get("data");

      assertTrue(3 <= array.size(), "number of addresses returned from query");

      //test with small page size
      addressesAsJsonString = serverControl.listAddresses(filterString, 1, 1);
      addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      array = (JsonArray) addressesAsJsonObject.get("data");

      assertEquals(1, array.size(), "number of queues returned from query");

      //test with QUEUE_COUNT with GREATER_THAN filter
      filterString = createJsonFilter("QUEUE_COUNT", "GREATER_THAN", "1");
      addressesAsJsonString = serverControl.listAddresses(filterString, 1, 50);
      addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      array = (JsonArray) addressesAsJsonObject.get("data");

      assertEquals(1, array.size(), "number of addresses returned from query");
      assertEquals(addressName3.toString(), array.getJsonObject(0).getString("name"), "address name check");

      //test with QUEUE_COUNT with LESS_THAN filter
      filterString = createJsonFilter("QUEUE_COUNT", "LESS_THAN", "0");
      addressesAsJsonString = serverControl.listAddresses(filterString, 1, 50);
      addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      array = (JsonArray) addressesAsJsonObject.get("data");

      assertEquals(0, array.size(), "number of addresses returned from query");

   }

   @TestTemplate
   public void testListAddressOrder() throws Exception {

      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString queueName2 = SimpleString.of("my_queue_two");
      SimpleString queueName3 = SimpleString.of("other_queue_three");
      SimpleString queueName4 = SimpleString.of("other_queue_four");

      SimpleString addressName1 = SimpleString.of("my_address_1");
      SimpleString addressName2 = SimpleString.of("my_address_2");
      SimpleString addressName3 = SimpleString.of("my_address_3");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      server.addAddressInfo(new AddressInfo(addressName2, RoutingType.ANYCAST));
      server.addAddressInfo(new AddressInfo(addressName3, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName3, RoutingType.ANYCAST, queueName3, null, false, false);
         server.createQueue(addressName3, RoutingType.ANYCAST, queueName4, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName3).setAddress(addressName3).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         server.createQueue(QueueConfiguration.of(queueName4).setAddress(addressName3).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      //test default order
      String filterString = createJsonFilter("name", "CONTAINS", "my");
      String addressesAsJsonString = serverControl.listAddresses(filterString, 1, 50);
      JsonObject addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      JsonArray array = (JsonArray) addressesAsJsonObject.get("data");

      assertEquals(3, array.size(), "number addresses returned");
      assertEquals(addressName1.toString(), array.getJsonObject(0).getString("name"), "address1 default order");
      assertEquals(addressName2.toString(), array.getJsonObject(1).getString("name"), "address2 default order");
      assertEquals(addressName3.toString(), array.getJsonObject(2).getString("name"), "address3 default order");

      //test  ordered by name desc
      filterString = createJsonFilter("name", "CONTAINS", "my", "name", "desc");
      addressesAsJsonString = serverControl.listAddresses(filterString, 1, 50);
      addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      array = (JsonArray) addressesAsJsonObject.get("data");

      assertEquals(3, array.size(), "number addresses returned");
      assertEquals(addressName3.toString(), array.getJsonObject(0).getString("name"), "address3 ordered by name");
      assertEquals(addressName2.toString(), array.getJsonObject(1).getString("name"), "address2 ordered by name");
      assertEquals(addressName1.toString(), array.getJsonObject(2).getString("name"), "address1 ordered by name");

      //test  ordered by queue count asc
      filterString = createJsonFilter("name", "CONTAINS", "my", "queueCount", "asc");
      addressesAsJsonString = serverControl.listAddresses(filterString, 1, 50);
      addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      array = (JsonArray) addressesAsJsonObject.get("data");

      assertEquals(3, array.size(), "number addresses returned");
      assertEquals(addressName2.toString(), array.getJsonObject(0).getString("name"), "address2 ordered by queue count");
      assertEquals(addressName1.toString(), array.getJsonObject(1).getString("name"), "address1 ordered by queue count");
      assertEquals(addressName3.toString(), array.getJsonObject(2).getString("name"), "address3 ordered by queue count");
   }

   @TestTemplate
   public void testListConsumers() throws Exception {
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString queueName2 = SimpleString.of("my_queue_two");
      SimpleString queueName3 = SimpleString.of("other_queue_three");
      SimpleString addressName1 = SimpleString.of("my_address_one");
      SimpleString addressName2 = SimpleString.of("my_address_two");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      server.addAddressInfo(new AddressInfo(addressName2, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName2, RoutingType.ANYCAST, queueName2, null, false, false);
         server.createQueue(addressName2, RoutingType.ANYCAST, queueName3, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName2).setAddress(addressName2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         server.createQueue(QueueConfiguration.of(queueName3).setAddress(addressName2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator);
           ClientSession session = csf.createSession()) {

         ClientConsumer consumer1_q1 = session.createConsumer(queueName1);
         ClientConsumer consumer2_q1 = session.createConsumer(queueName1);
         ClientConsumer consumer1_q2 = session.createConsumer(queueName2);
         ClientConsumer consumer1_q3 = session.createConsumer(queueName3);

         //test with filter  EQUALS
         String filterString = createJsonFilter("queue", "EQUALS", queueName1.toString());
         String consumersAsJsonString = serverControl.listConsumers(filterString, 1, 50);
         JsonObject consumersAsJsonObject = JsonUtil.readJsonObject(consumersAsJsonString);
         JsonArray array = (JsonArray) consumersAsJsonObject.get("data");

         assertEquals(2, array.size(), "number of consumers returned from query");
         assertEquals(queueName1.toString(), array.getJsonObject(0).getString(ConsumerField.QUEUE.getName()), "check consumer's queue");
         assertEquals(queueName1.toString(), array.getJsonObject(0).getString(ConsumerField.QUEUE.getName()), "check consumer's queue");

         // test with a CONTAINS operation
         filterString = createJsonFilter(ConsumerField.QUEUE.getName(), "CONTAINS", "my_queue");
         consumersAsJsonString = serverControl.listConsumers(filterString, 1, 50);
         consumersAsJsonObject = JsonUtil.readJsonObject(consumersAsJsonString);
         array = (JsonArray) consumersAsJsonObject.get("data");

         assertEquals(3, array.size(), "number of consumers returned from query");

         // filter by address
         filterString = createJsonFilter(ConsumerField.ADDRESS.getName(),  "EQUALS", addressName1.toString());
         consumersAsJsonString = serverControl.listConsumers(filterString, 1, 50);
         consumersAsJsonObject = JsonUtil.readJsonObject(consumersAsJsonString);
         array = (JsonArray) consumersAsJsonObject.get("data");

         assertEquals(2, array.size(), "number of consumers returned from query");
         assertEquals(addressName1.toString(), array.getJsonObject(0).getString(ConsumerField.ADDRESS.getName()), "check consumers address");
         assertEquals(addressName1.toString(), array.getJsonObject(1).getString(ConsumerField.ADDRESS.getName()), "check consumers address");

         //test with empty filter - all consumers should be returned
         filterString = createJsonFilter("", "", "");
         consumersAsJsonString = serverControl.listConsumers(filterString, 1, 50);
         consumersAsJsonObject = JsonUtil.readJsonObject(consumersAsJsonString);
         array = (JsonArray) consumersAsJsonObject.get("data");

         assertTrue(4 <= array.size(), "at least 4 consumers returned from query");

         //test with small page size
         consumersAsJsonString = serverControl.listConsumers(filterString, 1, 1);
         consumersAsJsonObject = JsonUtil.readJsonObject(consumersAsJsonString);
         array = (JsonArray) consumersAsJsonObject.get("data");

         assertEquals(1, array.size(), "number of consumers returned from query");

         //test contents of returned consumer
         filterString = createJsonFilter(ConsumerField.QUEUE.getName(), "EQUALS", queueName3.toString());
         consumersAsJsonString = serverControl.listConsumers(filterString, 1, 50);
         consumersAsJsonObject = JsonUtil.readJsonObject(consumersAsJsonString);
         array = (JsonArray) consumersAsJsonObject.get("data");

         assertEquals(1, array.size(), "number of consumers returned from query");
         JsonObject jsonConsumer = array.getJsonObject(0);
         assertEquals(queueName3.toString(), jsonConsumer.getString(ConsumerField.QUEUE.getName()), "queue name in consumer");
         assertEquals(addressName2.toString(), jsonConsumer.getString(ConsumerField.ADDRESS.getName()), "address name in consumer");
         assertEquals("CORE", jsonConsumer.getString(ConsumerField.PROTOCOL.getName()), "consumer protocol ");
         assertEquals("anycast", jsonConsumer.getString(ConsumerField.QUEUE_TYPE.getName()), "queue type");
         assertNotEquals("", jsonConsumer.getString(ConsumerField.ID.getName()), "id");
         assertNotEquals("", jsonConsumer.getString(ConsumerField.SESSION.getName()), "session");
         assertEquals("", jsonConsumer.getString(ConsumerField.CLIENT_ID.getName()), "clientID");
         assertEquals("", jsonConsumer.getString(ConsumerField.USER.getName()), "user");
         assertNotEquals("", jsonConsumer.getString(ConsumerField.LOCAL_ADDRESS.getName()), "localAddress");
         assertNotEquals("", jsonConsumer.getString(ConsumerField.REMOTE_ADDRESS.getName()), "remoteAddress");
         assertNotEquals("", jsonConsumer.getString(ConsumerField.CREATION_TIME.getName()), "creationTime");
         assertNotEquals("", jsonConsumer.getString(ConsumerField.MESSAGES_IN_TRANSIT.getName()), "messagesInTransit");
         assertNotEquals("", jsonConsumer.getString(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()), "messagesInTransitSize");
         assertNotEquals("", jsonConsumer.getString(ConsumerField.MESSAGES_DELIVERED.getName()), "messagesDelivered");
         assertNotEquals("", jsonConsumer.getString(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()), "messagesDeliveredSize");
         assertNotEquals("", jsonConsumer.getString(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()), "messagesAcknowledged");
         assertEquals(0, jsonConsumer.getInt(ConsumerField.LAST_DELIVERED_TIME.getName()), "lastDeliveredTime");
         assertEquals(0, jsonConsumer.getInt(ConsumerField.LAST_ACKNOWLEDGED_TIME.getName()), "lastAcknowledgedTime");
      }

   }

   @TestTemplate
   public void testListConsumersOrder() throws Exception {
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString addressName1 = SimpleString.of("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSessionImpl session1 = (ClientSessionImpl) csf.createSession();
         ClientSessionImpl session2 = (ClientSessionImpl) csf.createSession();
         ClientSessionImpl session3 = (ClientSessionImpl) csf.createSession();

         //sleep - test compares creationTimes
         ClientConsumer consumer_s1 = session1.createConsumer(queueName1);
         Thread.sleep(500);
         ClientConsumer consumer_s2 = session2.createConsumer(queueName1);
         Thread.sleep(500);
         ClientConsumer consumer_s3 = session3.createConsumer(queueName1);

         //test default Order
         String filterString = createJsonFilter(ConsumerField.QUEUE.getName(), "EQUALS", queueName1.toString());
         String consumersAsJsonString = serverControl.listConsumers(filterString, 1, 50);
         JsonObject consumersAsJsonObject = JsonUtil.readJsonObject(consumersAsJsonString);
         JsonArray array = (JsonArray) consumersAsJsonObject.get("data");
         assertEquals(3, array.size(), "number of consumers returned from query");

         assertEquals(session1.getName(), array.getJsonObject(0).getString(ConsumerField.SESSION.getName()), "Consumer1 default order");
         assertEquals(session2.getName(), array.getJsonObject(1).getString(ConsumerField.SESSION.getName()), "Consumer2 default order");
         assertEquals(session3.getName(), array.getJsonObject(2).getString(ConsumerField.SESSION.getName()), "Consumer3 default order");

         //test ordered by creationTime
         filterString = createJsonFilter(ConsumerField.QUEUE.getName(), "EQUALS", queueName1.toString(), "creationTime", "desc");
         consumersAsJsonString = serverControl.listConsumers(filterString, 1, 50);
         consumersAsJsonObject = JsonUtil.readJsonObject(consumersAsJsonString);
         array = (JsonArray) consumersAsJsonObject.get("data");
         assertEquals(3, array.size(), "number of consumers returned from query");

         assertEquals(session3.getName(), array.getJsonObject(0).getString(ConsumerField.SESSION.getName()), "Consumer3 creation time");
         assertEquals(session2.getName(), array.getJsonObject(1).getString(ConsumerField.SESSION.getName()), "Consumer2 creation time");
         assertEquals(session1.getName(), array.getJsonObject(2).getString(ConsumerField.SESSION.getName()), "Consumer1 creation time");

      }
   }

   @TestTemplate
   public void testListSessions() throws Exception {
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString addressName1 = SimpleString.of("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSessionImpl session1 = (ClientSessionImpl) csf.createSession();
         Thread.sleep(500);
         ClientSessionImpl session2 = (ClientSessionImpl) csf.createSession();
         Thread.sleep(500);
         ClientSessionImpl session3 = (ClientSessionImpl) csf.createSession();
         ClientConsumer consumer1_s1 = session1.createConsumer(queueName1);
         ClientConsumer consumer2_s1 = session1.createConsumer(queueName1);

         ClientConsumer consumer1_s2 = session2.createConsumer(queueName1);
         ClientConsumer consumer2_s2 = session2.createConsumer(queueName1);
         ClientConsumer consumer3_s2 = session2.createConsumer(queueName1);
         ClientConsumer consumer4_s2 = session2.createConsumer(queueName1);

         ClientConsumer consumer1_s3 = session3.createConsumer(queueName1);
         ClientConsumer consumer2_s3 = session3.createConsumer(queueName1);
         ClientConsumer consumer3_s3 = session3.createConsumer(queueName1);

         String filterString = createJsonFilter("CONSUMER_COUNT", "GREATER_THAN", "1");
         String sessionsAsJsonString = serverControl.listSessions(filterString, 1, 50);
         JsonObject sessionsAsJsonObject = JsonUtil.readJsonObject(sessionsAsJsonString);
         JsonArray array = (JsonArray) sessionsAsJsonObject.get("data");


         assertEquals(3, array.size(), "number of sessions returned from query");
         JsonObject jsonSession = array.getJsonObject(0);

         //check all fields
         assertNotEquals("", jsonSession.getString("id"), "id");
         assertEquals("", jsonSession.getString("user"), "user");
         assertNotEquals("", jsonSession.getString("creationTime"), "creationTime");
         assertEquals(2, jsonSession.getInt("consumerCount"), "consumerCount");
         assertTrue(0 <= jsonSession.getInt("producerCount"), "producerCount");
         assertNotEquals("", jsonSession.getString("connectionID"), "connectionID");

         //check default order
         assertEquals(session1.getName(), array.getJsonObject(0).getString("id"), "session1 location");
         assertEquals(session2.getName(), array.getJsonObject(1).getString("id"), "session2 location");
         assertEquals(session3.getName(), array.getJsonObject(2).getString("id"), "session3 location");

         //bring back session ordered by consumer count
         filterString = createJsonFilter("CONSUMER_COUNT", "GREATER_THAN", "1", "consumerCount", "asc");
         sessionsAsJsonString = serverControl.listSessions(filterString, 1, 50);
         sessionsAsJsonObject = JsonUtil.readJsonObject(sessionsAsJsonString);
         array = (JsonArray) sessionsAsJsonObject.get("data");

         assertEquals(3, array.size(), "number of sessions returned from query");
         assertEquals(session1.getName(), array.getJsonObject(0).getString("id"), "session1 ordered by consumer");
         assertEquals(session3.getName(), array.getJsonObject(1).getString("id"), "session3 ordered by consumer");
         assertEquals(session2.getName(), array.getJsonObject(2).getString("id"), "session2 ordered by consumer");

         //bring back session ordered by consumer Count
         filterString = createJsonFilter("CONSUMER_COUNT", "GREATER_THAN", "1", "consumerCount", "asc");
         sessionsAsJsonString = serverControl.listSessions(filterString, 1, 50);
         sessionsAsJsonObject = JsonUtil.readJsonObject(sessionsAsJsonString);
         array = (JsonArray) sessionsAsJsonObject.get("data");

         assertEquals(3, array.size(), "number of sessions returned from query");
         assertEquals(session1.getName(), array.getJsonObject(0).getString("id"), "session1 ordered by consumer");
         assertEquals(session3.getName(), array.getJsonObject(1).getString("id"), "session3 ordered by consumer");
         assertEquals(session2.getName(), array.getJsonObject(2).getString("id"), "session2 ordered by consumer");

         //bring back session ordered by creation time (desc)
         filterString = createJsonFilter("CONSUMER_COUNT", "GREATER_THAN", "1", "creationTime", "desc");
         sessionsAsJsonString = serverControl.listSessions(filterString, 1, 50);
         sessionsAsJsonObject = JsonUtil.readJsonObject(sessionsAsJsonString);
         array = (JsonArray) sessionsAsJsonObject.get("data");

         assertEquals(3, array.size(), "number of sessions returned from query");
         assertEquals(session3.getName(), array.getJsonObject(0).getString("id"), "session3 ordered by creationTime");
         assertEquals(session2.getName(), array.getJsonObject(1).getString("id"), "session2 ordered by creationTime");
         assertEquals(session1.getName(), array.getJsonObject(2).getString("id"), "session1 ordered by creationTime");

      }
   }

   @TestTemplate
   public void testListSessionsJmsClientID() throws Exception {
      final String clientId = RandomUtil.randomUUIDString();

      ActiveMQServerControl serverControl = createManagementControl();

      ConnectionFactory cf = new ActiveMQConnectionFactory("vm://0");
      try (Connection c = cf.createConnection()) {
         c.setClientID(clientId);
         c.createSession();
         String filter = createJsonFilter(SessionField.CLIENT_ID.getName(), "EQUALS", clientId);
         String json = serverControl.listSessions(filter, 1, 50);
         System.out.println(json);
         JsonObject sessions = JsonUtil.readJsonObject(json);
         JsonArray array = (JsonArray) sessions.get("data");

         assertEquals(2, array.size(), "number of sessions returned from query");
         JsonObject jsonSession = array.getJsonObject(0);

         assertEquals(clientId, jsonSession.getString(SessionField.CLIENT_ID.getName()), "wrong client ID returned");
      }
   }

   @TestTemplate
   public void testListConnections() throws Exception {
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString addressName1 = SimpleString.of("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientSessionFactoryImpl csf = null;
      ClientSessionFactoryImpl csf2 = null;
      ClientSessionFactoryImpl csf3 = null;

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator()) {

         //sleep as test compares creationTime
         csf = (ClientSessionFactoryImpl) createSessionFactory(locator);
         Thread.sleep(50);
         csf2 = (ClientSessionFactoryImpl) createSessionFactory(locator);
         Thread.sleep(50);
         csf3 = (ClientSessionFactoryImpl) createSessionFactory(locator);

         ClientSession session1_c1 = csf.createSession("guest", "guest", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());
         ClientSession session2_c1 = csf.createSession("guest", "guest", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());

         ClientSession session1_c2 = csf2.createSession("myUser", "myPass", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());
         ClientSession session2_c2 = csf2.createSession("myUser", "myPass", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());
         ClientSession session3_c2 = csf2.createSession("myUser", "myPass", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());
         ClientSession session4_c2 = csf2.createSession("myUser", "myPass", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());

         ClientSession session1_c4 = csf3.createSession("myUser", "myPass", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());
         ClientSession session2_c4 = csf3.createSession("guest", "guest", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());
         ClientSession session3_c4 = csf3.createSession("guest", "guest", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());

         String filterString = createJsonFilter("SESSION_COUNT", "GREATER_THAN", "1");
         String connectionsAsJsonString = serverControl.listConnections(filterString, 1, 50);
         System.err.println(connectionsAsJsonString);
         JsonObject connectionsAsJsonObject = JsonUtil.readJsonObject(connectionsAsJsonString);
         JsonArray array = (JsonArray) connectionsAsJsonObject.get("data");

         assertEquals(3, array.size(), "number of connections returned from query");
         JsonObject jsonConnection = array.getJsonObject(0);

         //check all fields
         assertNotEquals("", jsonConnection.getString("connectionID"), "connectionID");
         assertNotEquals("", jsonConnection.getString("remoteAddress"), "remoteAddress");
         assertEquals("guest", jsonConnection.getString("users"), "users");
         assertNotEquals("", jsonConnection.getString("creationTime"), "creationTime");
         assertNotEquals("", jsonConnection.getString("implementation"), "implementation");
         assertNotEquals("", jsonConnection.getString("protocol"), "protocol");
         assertEquals("", jsonConnection.getString("clientID"), "clientID");
         assertNotEquals("", jsonConnection.getString("localAddress"), "localAddress");
         assertEquals(2, jsonConnection.getInt("sessionCount"), "sessionCount");

         //check default order
         assertEquals(csf.getConnection().getID(), array.getJsonObject(0).getString("connectionID"), "connection1 default Order");
         assertEquals(csf2.getConnection().getID(), array.getJsonObject(1).getString("connectionID"), "connection2 default Order");
         assertEquals(csf3.getConnection().getID(), array.getJsonObject(2).getString("connectionID"), "connection3 session Order");

         //check order by users asc
         filterString = createJsonFilter("SESSION_COUNT", "GREATER_THAN", "1", "users", "asc");
         connectionsAsJsonString = serverControl.listConnections(filterString, 1, 50);
         connectionsAsJsonObject = JsonUtil.readJsonObject(connectionsAsJsonString);
         array = (JsonArray) connectionsAsJsonObject.get("data");

         assertEquals(3, array.size(), "number of connections returned from query");
         assertEquals("guest", array.getJsonObject(0).getString("users"), "connection1 users Order");
         assertEquals("guest,myUser", array.getJsonObject(1).getString("users"), "connection3 users Order");
         assertEquals("myUser", array.getJsonObject(2).getString("users"), "connection2 users Order");

         //check order by users desc
         filterString = createJsonFilter("SESSION_COUNT", "GREATER_THAN", "1", "users", "desc");
         connectionsAsJsonString = serverControl.listConnections(filterString, 1, 50);
         connectionsAsJsonObject = JsonUtil.readJsonObject(connectionsAsJsonString);
         array = (JsonArray) connectionsAsJsonObject.get("data");

         assertEquals(3, array.size(), "number of connections returned from query");
         assertEquals("myUser", array.getJsonObject(0).getString("users"), "connection2 users Order");
         assertEquals("guest,myUser", array.getJsonObject(1).getString("users"), "connection3 users Order");
         assertEquals("guest", array.getJsonObject(2).getString("users"), "connection1 users Order");

         //check order by session count desc
         filterString = createJsonFilter("SESSION_COUNT", "GREATER_THAN", "1", "sessionCount", "desc");
         connectionsAsJsonString = serverControl.listConnections(filterString, 1, 50);
         connectionsAsJsonObject = JsonUtil.readJsonObject(connectionsAsJsonString);
         array = (JsonArray) connectionsAsJsonObject.get("data");

         assertEquals(3, array.size(), "number of connections returned from query");
         assertEquals(csf2.getConnection().getID(), array.getJsonObject(0).getString("connectionID"), "connection2 session Order");
         assertEquals(csf3.getConnection().getID(), array.getJsonObject(1).getString("connectionID"), "connection3 session Order");
         assertEquals(csf.getConnection().getID(), array.getJsonObject(2).getString("connectionID"), "connection1 session Order");

         //check order by creationTime desc
         filterString = createJsonFilter("SESSION_COUNT", "GREATER_THAN", "1", "creationTime", "desc");
         connectionsAsJsonString = serverControl.listConnections(filterString, 1, 50);
         connectionsAsJsonObject = JsonUtil.readJsonObject(connectionsAsJsonString);
         array = (JsonArray) connectionsAsJsonObject.get("data");

         assertEquals(3, array.size(), "number of connections returned from query");
         assertEquals(csf3.getConnection().getID(), array.getJsonObject(0).getString("connectionID"), "connection3 creationTime Order");
         assertEquals(csf2.getConnection().getID(), array.getJsonObject(1).getString("connectionID"), "connection2 creationTime Order");
         assertEquals(csf.getConnection().getID(), array.getJsonObject(2).getString("connectionID"), "connection1 creationTime Order");
      } finally {
         if (csf != null) {
            csf.close();
         }
         if (csf2 != null) {
            csf.close();
         }
         if (csf3 != null) {
            csf.close();
         }
      }
   }

   @TestTemplate
   public void testListConnectionsCoreClientID() throws Exception {
      final String clientId = RandomUtil.randomUUIDString();
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString addressName1 = SimpleString.of("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientSessionFactoryImpl csf = null;

      try (ServerLocator locator = createInVMNonHALocator()) {
         csf = (ClientSessionFactoryImpl) createSessionFactory(locator);
         csf.createSession(null, null, false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize(), clientId);

         String filterString = createJsonFilter(ConnectionField.CLIENT_ID.getName(), ActiveMQFilterPredicate.Operation.EQUALS.toString(), clientId);

         String connectionsAsJsonString = serverControl.listConnections(filterString, 1, 50);
         JsonObject connectionsAsJsonObject = JsonUtil.readJsonObject(connectionsAsJsonString);
         JsonArray array = (JsonArray) connectionsAsJsonObject.get("data");

         assertEquals(1, array.size(), "number of connections returned from query");
         JsonObject jsonConnection = array.getJsonObject(0);

         assertEquals(clientId, jsonConnection.getString(ConnectionField.CLIENT_ID.getName()));
      } finally {
         if (csf != null) {
            csf.close();
         }
      }
   }

   @TestTemplate
   public void testListConnectionsCoreClientIDLegacy() throws Exception {
      final String clientId = RandomUtil.randomUUIDString();
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString addressName1 = SimpleString.of("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientSessionFactoryImpl csf = null;

      try (ServerLocator locator = createInVMNonHALocator()) {
         csf = (ClientSessionFactoryImpl) createSessionFactory(locator);
         ClientSession session = csf.createSession();
         session.addUniqueMetaData(ClientSession.JMS_SESSION_IDENTIFIER_PROPERTY, "");
         session.addUniqueMetaData(ClientSession.JMS_SESSION_CLIENT_ID_PROPERTY, clientId);

         String filterString = createJsonFilter(ConnectionField.CLIENT_ID.getName(), ActiveMQFilterPredicate.Operation.EQUALS.toString(), clientId);

         String connectionsAsJsonString = serverControl.listConnections(filterString, 1, 50);
         JsonObject connectionsAsJsonObject = JsonUtil.readJsonObject(connectionsAsJsonString);
         JsonArray array = (JsonArray) connectionsAsJsonObject.get("data");

         assertEquals(1, array.size(), "number of connections returned from query");
         JsonObject jsonConnection = array.getJsonObject(0);

         assertEquals(clientId, jsonConnection.getString(ConnectionField.CLIENT_ID.getName()));
      } finally {
         if (csf != null) {
            csf.close();
         }
      }
   }

   @TestTemplate
   public void testListConnectionsJmsClientID() throws Exception {
      final String clientId = RandomUtil.randomUUIDString();

      ActiveMQServerControl serverControl = createManagementControl();

      ConnectionFactory cf = new ActiveMQConnectionFactory("vm://0");
      try (Connection c = cf.createConnection()) {
         c.setClientID(clientId);
         String filterString = createJsonFilter(ConnectionField.CLIENT_ID.getName(), ActiveMQFilterPredicate.Operation.EQUALS.toString(), clientId);

         String connectionsAsJsonString = serverControl.listConnections(filterString, 1, 50);
         JsonObject connectionsAsJsonObject = JsonUtil.readJsonObject(connectionsAsJsonString);
         JsonArray array = (JsonArray) connectionsAsJsonObject.get("data");

         assertEquals(1, array.size(), "number of connections returned from query");
         JsonObject jsonConnection = array.getJsonObject(0);

         assertEquals(clientId, jsonConnection.getString(ConnectionField.CLIENT_ID.getName()), "wrong client ID returned");
      }
   }

   @TestTemplate
   public void testListProducers() throws Exception {
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString addressName1 = SimpleString.of("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSession session1 = csf.createSession();
         ClientSession session2 = csf.createSession();

         ClientProducer producer1 = session1.createProducer(addressName1);
         producer1.send(session1.createMessage(true));
         ClientProducer producer2 = session2.createProducer(addressName1);
         producer2.send(session2.createMessage(true));

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         assertEquals(2 + extraProducers, array.size(), "number of producers returned from query");

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            assertNotEquals("", jsonSession.getString(ProducerField.ID.getName()), ProducerField.ID.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.SESSION.getName()), ProducerField.SESSION.getName());
            assertEquals("", jsonSession.getString(ProducerField.CLIENT_ID.getName()), ProducerField.CLIENT_ID.getName());
            assertEquals("", jsonSession.getString(ProducerField.USER.getName()), ProducerField.USER.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.PROTOCOL.getName()), ProducerField.PROTOCOL.getName());
            assertEquals(addressName1.toString(), jsonSession.getString(ProducerField.ADDRESS.getName()), ProducerField.ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()), ProducerField.LOCAL_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()), ProducerField.REMOTE_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.CREATION_TIME.getName()), ProducerField.CREATION_TIME.getName());
         }


         assertTrue(foundElement, "Valid session not found");
      }
   }

   @TestTemplate
   public void testListProducersLargeMessages() throws Exception {
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString addressName1 = SimpleString.of("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSession session1 = csf.createSession();
         ClientSession session2 = csf.createSession();

         ClientProducer producer1 = session1.createProducer(addressName1);
         producer1.send(session1.createMessage(true));
         ClientProducer producer2 = session2.createProducer(addressName1);
         producer2.send(session2.createMessage(true));

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         assertEquals(2 + extraProducers, array.size(), "number of producers returned from query");

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            assertNotEquals("", jsonSession.getString(ProducerField.ID.getName()), ProducerField.ID.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.SESSION.getName()), ProducerField.SESSION.getName());
            assertEquals("", jsonSession.getString(ProducerField.CLIENT_ID.getName()), ProducerField.CLIENT_ID.getName());
            assertEquals("", jsonSession.getString(ProducerField.USER.getName()), ProducerField.USER.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.PROTOCOL.getName()), ProducerField.PROTOCOL.getName());
            assertEquals(addressName1.toString(), jsonSession.getString(ProducerField.ADDRESS.getName()), ProducerField.ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()), ProducerField.LOCAL_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()), ProducerField.REMOTE_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.CREATION_TIME.getName()), ProducerField.CREATION_TIME.getName());
         }


         assertTrue(foundElement, "Valid session not found");
      }
   }

   @TestTemplate
   public void testListProducersAsJSONAgainstServer() throws Exception {
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString addressName1 = SimpleString.of("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSession session1 = csf.createSession();

         ClientProducer producer1 = session1.createProducer(addressName1);
         producer1.send(session1.createMessage(true));

         //bring back all producers
         String producersAsJsonString = serverControl.listProducersInfoAsJSON();
         JsonArray array = JsonUtil.readJsonArray(producersAsJsonString);

         assertEquals(1 + extraProducers, array.size(), "number of producers returned from query");

         JsonObject jsonSession = array.getJsonObject(0);
         if (!jsonSession.getString(ProducerField.ADDRESS.getAlternativeName()).equalsIgnoreCase(addressName1.toString())) {
            jsonSession = array.getJsonObject(1);
         }

         //get the only server producer
         ServerProducer producer = server.getSessionByID(jsonSession.getString(ProducerField.SESSION.getAlternativeName())).getServerProducers().iterator().next();
         //check all fields
         assertEquals(String.valueOf(producer.getID()), jsonSession.getString(ProducerField.ID.getName()), ProducerField.ID.getName());
         assertEquals(producer.getName(), jsonSession.getString(ProducerField.NAME.getName()), ProducerField.NAME.getName());
         assertEquals(producer.getSessionID(), jsonSession.getString(ProducerField.SESSION.getAlternativeName()), ProducerField.SESSION.getName());
         assertEquals(producer.getAddress(), jsonSession.getString(ProducerField.ADDRESS.getAlternativeName()), ProducerField.ADDRESS.getName());
         assertEquals(producer.getMessagesSent(), jsonSession.getJsonNumber(ProducerField.MESSAGE_SENT.getName()).longValue(), ProducerField.MESSAGE_SENT.getName());
         assertEquals(producer.getMessagesSentSize(), jsonSession.getJsonNumber(ProducerField.MESSAGE_SENT_SIZE.getName()).longValue(), ProducerField.MESSAGE_SENT_SIZE.getName());
         assertEquals(String.valueOf(producer.getCreationTime()), jsonSession.getString(ProducerField.CREATION_TIME.getName()), ProducerField.CREATION_TIME.getName());
      }
   }

   private int getNumberOfProducers(ActiveMQServer server) {
      AtomicInteger producers = new AtomicInteger();
      server.getSessions().forEach(session -> {
         producers.addAndGet(session.getProducerCount());
      });
      return producers.get();
   }

   @TestTemplate
   public void testListProducersAgainstServer() throws Exception {
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString addressName1 = SimpleString.of("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSession session1 = csf.createSession();

         ClientProducer producer1 = session1.createProducer(addressName1);
         producer1.send(session1.createMessage(true));

         Wait.assertEquals(1, () -> getNumberOfProducers(server));

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         assertEquals(1 + extraProducers, array.size(), "number of producers returned from query");

         JsonObject jsonSession = array.getJsonObject(0);

         Wait.assertTrue(() -> server.getSessionByID(jsonSession.getString(ProducerField.SESSION.getName())) != null);
         //get the only server producer
         ServerProducer producer = server.getSessionByID(jsonSession.getString(ProducerField.SESSION.getName())).getServerProducers().iterator().next();
         //check all fields
         assertEquals(String.valueOf(producer.getID()), jsonSession.getString(ProducerField.ID.getName()), ProducerField.ID.getName());
         assertEquals(producer.getSessionID(), jsonSession.getString(ProducerField.SESSION.getName()), ProducerField.SESSION.getName());
         assertEquals(producer.getProtocol(), jsonSession.getString(ProducerField.PROTOCOL.getName()), ProducerField.PROTOCOL.getName());
         assertEquals(producer.getAddress(), jsonSession.getString(ProducerField.ADDRESS.getName()), ProducerField.ADDRESS.getName());
         assertEquals(producer.getMessagesSent(), jsonSession.getJsonNumber(ProducerField.MESSAGE_SENT.getName()).longValue(), ProducerField.MESSAGE_SENT.getName());
         assertEquals(producer.getMessagesSentSize(), jsonSession.getJsonNumber(ProducerField.MESSAGE_SENT_SIZE.getName()).longValue(), ProducerField.MESSAGE_SENT_SIZE.getName());
         assertEquals(String.valueOf(producer.getCreationTime()), jsonSession.getString(ProducerField.CREATION_TIME.getName()), ProducerField.CREATION_TIME.getName());
      }
   }
   @TestTemplate
   public void testListProducersJMSCore() throws Exception {
      testListProducersJMS(ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY)), "CORE");
   }

   @TestTemplate
   public void testListProducersJMSAMQP() throws Exception {
      testListProducersJMS(new JmsConnectionFactory("amqp://localhost:61616"), "AMQP");
   }

   @TestTemplate
   public void testListProducersJMSOW() throws Exception {
      testListProducersJMS(new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616"), "CORE");
   }

   public void testListProducersJMS(ConnectionFactory connectionFactory, String protocol) throws Exception {
      String queueName = "my_queue_one";
      SimpleString ssQueueName = SimpleString.of(queueName);
      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(ssQueueName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(ssQueueName, RoutingType.ANYCAST, ssQueueName, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName).setAddress(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      // create some producers
      try (Connection conn = connectionFactory.createConnection()) {

         Session session1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session session2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer producer1 = session1.createProducer(session1.createQueue(queueName));
         producer1.send(session1.createMessage());
         MessageProducer producer2 = session2.createProducer(session2.createQueue(queueName));
         producer2.send(session2.createMessage());

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         assertEquals(2 + extraProducers, array.size(), "number of producers returned from query");

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            assertNotEquals("", jsonSession.getString(ProducerField.ID.getName()), ProducerField.ID.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.SESSION.getName()), ProducerField.SESSION.getName());
            assertEquals("", jsonSession.getString(ProducerField.USER.getName()), ProducerField.USER.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.PROTOCOL.getName()), ProducerField.PROTOCOL.getName());
            assertEquals(queueName, jsonSession.getString(ProducerField.ADDRESS.getName()), ProducerField.ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()), ProducerField.LOCAL_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()), ProducerField.REMOTE_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.CREATION_TIME.getName()), ProducerField.CREATION_TIME.getName());
         }

         assertTrue(foundElement, "Valid session not found");
      }
   }

   @TestTemplate
   public void testListProducersJMSLegacy() throws Exception {
      String queueName = "my_queue_one";
      SimpleString ssQueueName = SimpleString.of(queueName);
      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(ssQueueName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(ssQueueName, RoutingType.ANYCAST, ssQueueName, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName).setAddress(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      //pretend we are an olde client by removing the producer id and changing the session to use lagacy producers
      server.getBrokerSessionPlugins().add(new ActiveMQServerSessionPlugin() {
         @Override
         public void beforeCreateSession(String name, String username, int minLargeMessageSize, RemotingConnection connection, boolean autoCommitSends, boolean autoCommitAcks, boolean preAcknowledge, boolean xa, String defaultAddress, SessionCallback callback, boolean autoCreateQueues, OperationContext context, Map<SimpleString, RoutingType> prefixes) throws ActiveMQException {
            ActiveMQServerSessionPlugin.super.beforeCreateSession(name, username, minLargeMessageSize, connection, autoCommitSends, autoCommitAcks, preAcknowledge, xa, defaultAddress, callback, autoCreateQueues, context, prefixes);
         }

         @Override
         public void afterCreateSession(ServerSession session) throws ActiveMQException {
            try {
               Field serverProducers = session.getClass().getDeclaredField("serverProducers");
               serverProducers.setAccessible(true);
               serverProducers.set(session, new ServerLegacyProducersImpl(session));
            } catch (NoSuchFieldException e) {
               throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
               throw new RuntimeException(e);
            }
         }
      });

      ActiveMQConnectionFactory connectionFactory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      // create some producers
      try (Connection conn = connectionFactory.createConnection()) {

         Session session1 = conn.createSession();
         Session session2 = conn.createSession();

         MessageProducer producer1 = session1.createProducer(session1.createQueue(queueName));
         producer1.send(session1.createMessage());
         MessageProducer producer2 = session2.createProducer(session2.createQueue(queueName));
         producer2.send(session2.createMessage());

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         assertEquals(2, array.size(), "number of producers returned from query");

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            assertNotEquals("", jsonSession.getString(ProducerField.ID.getName()), ProducerField.ID.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.SESSION.getName()), ProducerField.SESSION.getName());
            assertEquals("", jsonSession.getString(ProducerField.USER.getName()), ProducerField.USER.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.PROTOCOL.getName()), ProducerField.PROTOCOL.getName());
            assertEquals(queueName, jsonSession.getString(ProducerField.ADDRESS.getName()), ProducerField.ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()), ProducerField.LOCAL_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()), ProducerField.REMOTE_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.CREATION_TIME.getName()), ProducerField.CREATION_TIME.getName());
         }

         assertTrue(foundElement, "Valid session not found");

         session1.close();

         filterString = createJsonFilter("", "", "");
         producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         array = (JsonArray) producersAsJsonObject.get("data");

         assertEquals(1, array.size(), "number of producers returned from query");

         session2.close();

         filterString = createJsonFilter("", "", "");
         producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         array = (JsonArray) producersAsJsonObject.get("data");

         assertEquals(0, array.size(), "number of producers returned from query");
      }
   }

   @TestTemplate
   public void testListFqqnProducersJMSLegacy() throws Exception {
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString addressName1 = SimpleString.of("my_address_one");
      SimpleString FQQNName = SimpleString.of(addressName1 + "::" + queueName1);
      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(FQQNName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(FQQNName, RoutingType.ANYCAST, FQQNName, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(FQQNName).setAddress(FQQNName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      //pretend we are an olde client by removing the producer id and changing the session to use lagacy producers
      server.getBrokerSessionPlugins().add(new ActiveMQServerSessionPlugin() {
         @Override
         public void beforeCreateSession(String name, String username, int minLargeMessageSize, RemotingConnection connection, boolean autoCommitSends, boolean autoCommitAcks, boolean preAcknowledge, boolean xa, String defaultAddress, SessionCallback callback, boolean autoCreateQueues, OperationContext context, Map<SimpleString, RoutingType> prefixes) throws ActiveMQException {
            ActiveMQServerSessionPlugin.super.beforeCreateSession(name, username, minLargeMessageSize, connection, autoCommitSends, autoCommitAcks, preAcknowledge, xa, defaultAddress, callback, autoCreateQueues, context, prefixes);
         }

         @Override
         public void afterCreateSession(ServerSession session) throws ActiveMQException {
            try {
               Field serverProducers = session.getClass().getDeclaredField("serverProducers");
               serverProducers.setAccessible(true);
               serverProducers.set(session, new ServerLegacyProducersImpl(session));
            } catch (NoSuchFieldException e) {
               throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
               throw new RuntimeException(e);
            }
         }
      });
      ActiveMQConnectionFactory connectionFactory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      // create some producers
      try (Connection conn = connectionFactory.createConnection()) {

         Session session1 = conn.createSession();

         MessageProducer producer1 = session1.createProducer(session1.createQueue(FQQNName.toString()));
         producer1.send(session1.createMessage());

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 200);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         //this is testing that the producers tracked hasnt exceededd the max of 100
         assertEquals(1, array.size(), "number of producers returned from query");

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            assertNotEquals("", jsonSession.getString(ProducerField.ID.getName()), ProducerField.ID.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.SESSION.getName()), ProducerField.SESSION.getName());
            assertEquals("", jsonSession.getString(ProducerField.USER.getName()), ProducerField.USER.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.PROTOCOL.getName()), ProducerField.PROTOCOL.getName());
            assertEquals(FQQNName.toString(), jsonSession.getString(ProducerField.ADDRESS.getName()), ProducerField.ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()), ProducerField.LOCAL_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()), ProducerField.REMOTE_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.CREATION_TIME.getName()), ProducerField.CREATION_TIME.getName());
         }

         assertTrue(foundElement, "Valid session not found");
      }
   }

   @TestTemplate
   public void testListAnonProducersJMSLegacy() throws Exception {
      String queueName = "my_queue_one";
      SimpleString ssQueueName = SimpleString.of(queueName);
      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(ssQueueName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(ssQueueName, RoutingType.ANYCAST, ssQueueName, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName).setAddress(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      //pretend we are an olde client by removing the producer id and changing the session to use lagacy producers
      server.getBrokerSessionPlugins().add(new ActiveMQServerSessionPlugin() {
         @Override
         public void beforeCreateSession(String name, String username, int minLargeMessageSize, RemotingConnection connection, boolean autoCommitSends, boolean autoCommitAcks, boolean preAcknowledge, boolean xa, String defaultAddress, SessionCallback callback, boolean autoCreateQueues, OperationContext context, Map<SimpleString, RoutingType> prefixes) throws ActiveMQException {
            ActiveMQServerSessionPlugin.super.beforeCreateSession(name, username, minLargeMessageSize, connection, autoCommitSends, autoCommitAcks, preAcknowledge, xa, defaultAddress, callback, autoCreateQueues, context, prefixes);
         }

         @Override
         public void afterCreateSession(ServerSession session) throws ActiveMQException {
            try {
               Field serverProducers = session.getClass().getDeclaredField("serverProducers");
               serverProducers.setAccessible(true);
               serverProducers.set(session, new ServerLegacyProducersImpl(session));
            } catch (NoSuchFieldException e) {
               throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
               throw new RuntimeException(e);
            }
         }
      });

      ActiveMQConnectionFactory connectionFactory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      // create some producers
      try (Connection conn = connectionFactory.createConnection()) {

         Session session1 = conn.createSession();

         MessageProducer producer1 = session1.createProducer(null);
         for (int i = 0; i < 110; i++) {
            javax.jms.Queue queue = session1.createQueue(queueName + i);
            producer1.send(queue, session1.createMessage());
         }

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 200);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         //this is testing that the producers tracked hasnt exceededd the max of 100
         assertEquals(100, array.size(), "number of producers returned from query");

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            assertNotEquals("", jsonSession.getString(ProducerField.ID.getName()), ProducerField.ID.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.SESSION.getName()), ProducerField.SESSION.getName());
            assertEquals("", jsonSession.getString(ProducerField.USER.getName()), ProducerField.USER.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.PROTOCOL.getName()), ProducerField.PROTOCOL.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()), ProducerField.LOCAL_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()), ProducerField.REMOTE_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.CREATION_TIME.getName()), ProducerField.CREATION_TIME.getName());
         }

         assertTrue(foundElement, "Valid session not found");

         session1.close();

         filterString = createJsonFilter("", "", "");
         producersAsJsonString = serverControl.listProducers(filterString, 1, 110);
         producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         array = (JsonArray) producersAsJsonObject.get("data");

         assertEquals(0, array.size(), "number of producers returned from query");
      }
   }

   @TestTemplate
   public void testListProducersJMSCoreMultipleProducers() throws Exception {
      testListProducersJMSMultipleProducers(ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY)), "CORE");
   }

   @TestTemplate
   public void testListProducersJMSAMQPMultipleProducers() throws Exception {
      testListProducersJMSMultipleProducers(new JmsConnectionFactory("amqp://localhost:61616"), "AMQP");
   }

   @TestTemplate
   public void testListProducersJMSOpenWireMultipleProducers() throws Exception {
      testListProducersJMSMultipleProducers(new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616"), "OPENWIRE");
   }
   public void testListProducersJMSMultipleProducers(ConnectionFactory connectionFactory, String protocol) throws Exception {
      String queueName = "my_queue_one";
      SimpleString ssQueueName = SimpleString.of(queueName);
      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(ssQueueName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(ssQueueName, RoutingType.ANYCAST, ssQueueName, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName).setAddress(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      // create some producers
      try (Connection conn = connectionFactory.createConnection()) {

         Session session1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer producer1 = session1.createProducer(session1.createQueue(queueName));
         producer1.send(session1.createMessage());

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         assertEquals(1 + extraProducers, array.size(), "number of producers returned from query");

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            assertNotEquals("", jsonSession.getString(ProducerField.ID.getName()), ProducerField.ID.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.SESSION.getName()), ProducerField.SESSION.getName());
            assertEquals("", jsonSession.getString(ProducerField.USER.getName()), ProducerField.USER.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.PROTOCOL.getName()), ProducerField.PROTOCOL.getName());
            assertEquals(queueName, jsonSession.getString(ProducerField.ADDRESS.getName()), ProducerField.ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()), ProducerField.LOCAL_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()), ProducerField.REMOTE_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.CREATION_TIME.getName()), ProducerField.CREATION_TIME.getName());
         }

         MessageProducer producer2 = session1.createProducer(session1.createQueue(queueName));
         producer2.send(session1.createMessage());

         producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         array = (JsonArray) producersAsJsonObject.get("data");

         assertEquals(2 + extraProducers, array.size(), "number of producers returned from query");

         producer1.close();

         Wait.assertEquals(1 + extraProducers, () -> ((JsonArray) JsonUtil.readJsonObject(serverControl.listProducers(filterString, 1, 50)).get("data")).size());

         producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         array = (JsonArray) producersAsJsonObject.get("data");

         assertEquals(1 + extraProducers, array.size(), "number of producers returned from query");

         assertTrue(foundElement, "Valid session not found");
      }
   }

   @TestTemplate
   public void testListProducersJMSAnonCore() throws Exception {
      testListProducersJMSAnon(ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY)), "CORE");
   }

   @TestTemplate
   public void testListProducersJMSAnonAMQP() throws Exception {
      testListProducersJMSAnon(new JmsConnectionFactory("amqp://localhost:61616"), "AMQP");
   }

   @TestTemplate
   public void testListProducersJMSAnonOW() throws Exception {
      testListProducersJMSAnon(new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616"), "OPENWIRE");
   }

   public void testListProducersJMSAnon(ConnectionFactory connectionFactory, String protocol) throws Exception {
      String queueName1 = "my_queue_one";
      String queueName2 = "my_queue_two";
      String queueName3 = "my_queue_three";
      SimpleString ssQueueName1 = SimpleString.of(queueName1);
      SimpleString ssQueueName2 = SimpleString.of(queueName2);
      SimpleString ssQueueName3 = SimpleString.of(queueName3);
      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(ssQueueName1, RoutingType.ANYCAST));
      server.addAddressInfo(new AddressInfo(ssQueueName2, RoutingType.ANYCAST));
      server.addAddressInfo(new AddressInfo(ssQueueName3, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(ssQueueName1, RoutingType.ANYCAST, ssQueueName1, null, false, false);
         server.createQueue(ssQueueName2, RoutingType.ANYCAST, ssQueueName2, null, false, false);
         server.createQueue(ssQueueName3, RoutingType.ANYCAST, ssQueueName3, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setAddress(queueName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         server.createQueue(QueueConfiguration.of(queueName2).setAddress(queueName2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         server.createQueue(QueueConfiguration.of(queueName3).setAddress(queueName3).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      // create some producers
      try (Connection conn = connectionFactory.createConnection()) {

         Session session1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session session2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue1 = session1.createQueue(queueName1);
         javax.jms.Queue queue2 = session1.createQueue(queueName2);
         javax.jms.Queue queue3 = session1.createQueue(queueName3);
         MessageProducer producer1 = session1.createProducer(null);
         producer1.send(queue1, session1.createMessage());
         producer1.send(queue2, session1.createMessage());
         producer1.send(queue3, session1.createMessage());
         MessageProducer producer2 = session2.createProducer(null);
         producer2.send(queue1, session2.createMessage());
         producer2.send(queue2, session2.createMessage());
         producer2.send(queue3, session2.createMessage());

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         assertEquals(2 + extraProducers, array.size(), "number of producers returned from query");

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            assertNotEquals("", jsonSession.getString(ProducerField.ID.getName()), ProducerField.ID.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.SESSION.getName()), ProducerField.SESSION.getName());
            assertEquals("", jsonSession.getString(ProducerField.USER.getName()), ProducerField.USER.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.PROTOCOL.getName()), ProducerField.PROTOCOL.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()), ProducerField.LOCAL_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()), ProducerField.REMOTE_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.CREATION_TIME.getName()), ProducerField.CREATION_TIME.getName());
         }

         assertTrue(foundElement, "Valid session not found");
      }
   }

   @TestTemplate
   public void testListProducersJMSTopicCore() throws Exception {
      testListProducersJMSTopic(ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY)), "CORE");
   }

   @TestTemplate
   public void testListProducersJMSTopicAMQP() throws Exception {
      testListProducersJMSTopic(new JmsConnectionFactory("amqp://localhost:61616"), "AMQP");
   }

   @TestTemplate
   public void testListProducersJMSTopicOW() throws Exception {
      testListProducersJMSTopic(new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616"), "OPENWIRE");
   }
   public void testListProducersJMSTopic(ConnectionFactory connectionFactory, String protocol) throws Exception {
      SimpleString topicName = SimpleString.of("my-topic");
      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(topicName, RoutingType.MULTICAST));
      if (legacyCreateQueue) {
         return;
      }

      server.addAddressInfo(new AddressInfo(""));

      try (Connection conn = connectionFactory.createConnection()) {
         conn.setClientID("clientID");
         Session session1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session session2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Topic topic = session1.createTopic(topicName.toString());
         TopicSubscriber mysub1 = session1.createDurableSubscriber(topic, "mysub1");
         TopicSubscriber mysub2 = session2.createDurableSubscriber(topic, "mysub2");
         MessageProducer producer1 = session1.createProducer(topic);
         producer1.send(session1.createMessage());
         MessageProducer producer2 = session2.createProducer(session2.createTopic(topicName.toString()));
         producer2.send(session2.createMessage());

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         assertEquals(2 + extraProducers, array.size(), "number of producers returned from query");

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            assertNotEquals("", jsonSession.getString(ProducerField.ID.getName()), ProducerField.ID.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.SESSION.getName()), ProducerField.SESSION.getName());
            assertEquals("", jsonSession.getString(ProducerField.USER.getName()), ProducerField.USER.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.PROTOCOL.getName()), ProducerField.PROTOCOL.getName());
            assertEquals(topicName.toString(), jsonSession.getString(ProducerField.ADDRESS.getName()), ProducerField.ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()), ProducerField.LOCAL_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()), ProducerField.REMOTE_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.CREATION_TIME.getName()), ProducerField.CREATION_TIME.getName());
         }

         //remove the subs and make sure producers still exist
         mysub1.close();
         mysub2.close();
         session1.unsubscribe("mysub1");
         session2.unsubscribe("mysub2");
         producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         array = (JsonArray) producersAsJsonObject.get("data");
         assertEquals(2 + extraProducers, array.size());
         assertTrue(foundElement, "Valid session not found");
      }
   }

   @TestTemplate
   public void testListProducersFQQN() throws Exception {
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString addressName1 = SimpleString.of("my_address_one");
      SimpleString FQQNName = SimpleString.of(addressName1 + "::" + queueName1);
      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSession session1 = csf.createSession();
         ClientSession session2 = csf.createSession();

         ClientProducer producer1 = session1.createProducer(FQQNName);
         producer1.send(session1.createMessage(true));
         ClientProducer producer2 = session2.createProducer(FQQNName);
         producer2.send(session2.createMessage(true));

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         assertEquals(2 + extraProducers, array.size(), "number of producers returned from query");

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            assertNotEquals("", jsonSession.getString(ProducerField.ID.getName()), ProducerField.ID.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.SESSION.getName()), ProducerField.SESSION.getName());
            assertEquals("", jsonSession.getString(ProducerField.USER.getName()), ProducerField.USER.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.PROTOCOL.getName()), ProducerField.PROTOCOL.getName());
            assertEquals(FQQNName.toString(), jsonSession.getString(ProducerField.ADDRESS.getName()), ProducerField.ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()), ProducerField.LOCAL_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()), ProducerField.REMOTE_ADDRESS.getName());
            assertNotEquals("", jsonSession.getString(ProducerField.CREATION_TIME.getName()), ProducerField.CREATION_TIME.getName());
         }

         session1.deleteQueue(queueName1);

         producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         array = (JsonArray) producersAsJsonObject.get("data");

         //make sure both producers still exist as we dont tie them to an address which would only be recreated on another send
         assertEquals(2 + extraProducers, array.size());

         assertTrue(foundElement, "Valid session not found");
      }
   }

   @TestTemplate
   public void testListProducersMessageCountsJMSCore() throws Exception {
      testListProducersMessageCountsJMS(ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY)), "CORE");
   }

   @TestTemplate
   public void testListProducersMessageCountsAMQPJMS() throws Exception {
      testListProducersMessageCountsJMS(new JmsConnectionFactory("amqp://localhost:61616"), "AMQP");
   }

   @TestTemplate
   public void testListProducersMessageCountsOWJMS() throws Exception {
      testListProducersMessageCountsJMS(new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616"), "OPENWIRE");
   }

   public void testListProducersMessageCountsJMS(ConnectionFactory connectionFactory, String protocol) throws Exception {
      String myQueue = "my_queue";
      SimpleString queueName1 = SimpleString.of(myQueue);
      SimpleString addressName1 = SimpleString.of(myQueue);


      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      int numMessages = 10;

      try (Connection connection = connectionFactory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(session.createQueue(myQueue));
         for (int i = 0; i < numMessages; i++) {
            javax.jms.Message message = session.createMessage();
            producer.send(message);
         }
         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         assertEquals(1 + extraProducers, array.size(), "number of producers returned from query");

         JsonObject jsonSession = array.getJsonObject(0);

         //check all fields
         assertNotEquals("", jsonSession.getString(ProducerField.ID.getName()), ProducerField.ID.getName());
         assertNotEquals("", jsonSession.getString(ProducerField.SESSION.getName()), ProducerField.SESSION.getName());
         assertEquals("", jsonSession.getString(ProducerField.USER.getName()), ProducerField.USER.getName());
         assertEquals(protocol, jsonSession.getString(ProducerField.PROTOCOL.getName()), ProducerField.PROTOCOL.getAlternativeName());
         assertEquals(addressName1.toString(), jsonSession.getString(ProducerField.ADDRESS.getName()), ProducerField.ADDRESS.getName());
         assertNotEquals("", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()), ProducerField.LOCAL_ADDRESS.getName());
         assertNotEquals("", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()), ProducerField.REMOTE_ADDRESS.getName());
         assertNotEquals("", jsonSession.getString(ProducerField.CREATION_TIME.getName()), ProducerField.CREATION_TIME.getName());
         assertEquals(numMessages, jsonSession.getInt(ProducerField.MESSAGE_SENT.getName()), ProducerField.MESSAGE_SENT.getName());
         assertNotEquals("", jsonSession.getString(ProducerField.LAST_PRODUCED_MESSAGE_ID.getName()), ProducerField.LAST_PRODUCED_MESSAGE_ID.getName());
      }
   }


   @TestTemplate
   public void testListProducersMessageCounts() throws Exception {
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString addressName1 = SimpleString.of("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      int numMessages = 10;


      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSession session1 = csf.createSession();

         ClientProducer producer1 = session1.createProducer(addressName1);
         int messagesSize = 0;
         for (int i = 0; i < numMessages; i++) {
            ClientMessage message = session1.createMessage(true);
            producer1.send(message);
            messagesSize += message.getEncodeSize();
         }
         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         assertEquals(1 + extraProducers, array.size(), "number of producers returned from query");

         JsonObject jsonSession = array.getJsonObject(0);

         //check all fields
         assertNotEquals("", jsonSession.getString(ProducerField.ID.getName()), ProducerField.ID.getName());
         assertNotEquals("", jsonSession.getString(ProducerField.SESSION.getName()), ProducerField.SESSION.getName());
         assertEquals("", jsonSession.getString(ProducerField.CLIENT_ID.getName()), ProducerField.CLIENT_ID.getName());
         assertEquals("", jsonSession.getString(ProducerField.USER.getName()), ProducerField.USER.getName());
         assertNotEquals("", jsonSession.getString(ProducerField.PROTOCOL.getName()), ProducerField.PROTOCOL.getAlternativeName());
         assertEquals(addressName1.toString(), jsonSession.getString(ProducerField.ADDRESS.getName()), ProducerField.ADDRESS.getName());
         assertNotEquals("", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()), ProducerField.LOCAL_ADDRESS.getName());
         assertNotEquals("", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()), ProducerField.REMOTE_ADDRESS.getName());
         assertNotEquals("", jsonSession.getString(ProducerField.CREATION_TIME.getName()), ProducerField.CREATION_TIME.getName());
         assertEquals(numMessages, jsonSession.getInt(ProducerField.MESSAGE_SENT.getName()), ProducerField.MESSAGE_SENT.getName());
         assertEquals(messagesSize, jsonSession.getInt(ProducerField.MESSAGE_SENT_SIZE.getName()), ProducerField.MESSAGE_SENT_SIZE.getName());
         assertEquals("", jsonSession.getString(ProducerField.LAST_PRODUCED_MESSAGE_ID.getName()), ProducerField.LAST_PRODUCED_MESSAGE_ID.getName());
      }
   }

   @TestTemplate
   public void testListProducersMessageCounts2() throws Exception {
      SimpleString queueName1 = SimpleString.of("my_queue_one");
      SimpleString addressName1 = SimpleString.of("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      int numMessages = 10;

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSession session1 = csf.createSession();

         ClientProducer producer1 = session1.createProducer(addressName1);
         int messagesSize = 0;
         for (int i = 0; i < numMessages; i++) {
            ClientMessage message = session1.createMessage(true);
            producer1.send(message);
            messagesSize += message.getEncodeSize();
         }
         //bring back all producers
         String producersAsJsonString = serverControl.listProducersInfoAsJSON();
         JsonArray jsonArray = JsonUtil.readJsonArray(producersAsJsonString);

         JsonObject jsonSession = jsonArray.getJsonObject(0);

         if (!jsonSession.getString(ProducerField.ADDRESS.getAlternativeName()).equalsIgnoreCase(addressName1.toString())) {
            jsonSession = jsonArray.getJsonObject(1);
         }

         //check all fields
         assertNotEquals("", jsonSession.getString(ProducerField.ID.getName()), ProducerField.ID.getName());
         assertNotEquals("", jsonSession.getString(ProducerField.NAME.getName()), ProducerField.NAME.getName());
         assertNotEquals("", jsonSession.getString(ProducerField.SESSION.getAlternativeName()), ProducerField.SESSION.getAlternativeName());
         assertNotEquals("", jsonSession.getString(ProducerField.CREATION_TIME.getName()), ProducerField.CREATION_TIME.getName());
         assertEquals(numMessages, jsonSession.getInt(ProducerField.MESSAGE_SENT.getName()), ProducerField.MESSAGE_SENT.getName());
         assertEquals(messagesSize, jsonSession.getInt(ProducerField.MESSAGE_SENT_SIZE.getName()), ProducerField.MESSAGE_SENT_SIZE.getName());
         assertEquals(JsonValue.NULL, jsonSession.get(ProducerField.LAST_PRODUCED_MESSAGE_ID.getName()), ProducerField.LAST_PRODUCED_MESSAGE_ID.getName());
      }
   }

   @TestTemplate
   public void testMemoryUsagePercentage() throws Exception {
      //messages size 100K
      final int MESSAGE_SIZE = 100000;
      String name1 = "messageUsagePercentage.test.1";

      server.stop();
      //no globalMaxSize set
      server.getConfiguration().setGlobalMaxSize(-1);
      server.start();

      ActiveMQServerControl serverControl = createManagementControl();
      // check before adding messages
      assertEquals(0, serverControl.getAddressMemoryUsage(), "Memory Usage before adding messages");
      assertEquals(0, serverControl.getAddressMemoryUsagePercentage(), "MemoryUsagePercentage");

      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator);
           ClientSession session = csf.createSession()) {
         if (legacyCreateQueue) {
            session.createQueue(name1, RoutingType.ANYCAST, name1);
         } else {
            session.createQueue(QueueConfiguration.of(name1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         }

         Queue serverQueue = server.locateQueue(SimpleString.of(name1));
         assertFalse(serverQueue.isDurable());
         ClientProducer producer1 = session.createProducer(name1);
         sendMessagesWithPredefinedSize(30, session, producer1, MESSAGE_SIZE);

         //it is hard to predict an exact number so checking if it falls in a certain range: totalSizeOfMessageSent < X > totalSizeofMessageSent + 100k
         assertTrue(((30 * MESSAGE_SIZE) < serverControl.getAddressMemoryUsage()) && (serverControl.getAddressMemoryUsage() < ((30 * MESSAGE_SIZE) + 100000)), "Memory Usage within range ");
         // no globalMaxSize set so it should return zero
         assertEquals(0, serverControl.getAddressMemoryUsagePercentage(), "MemoryUsagePercentage");
      }
   }

   @TestTemplate
   public void testMemoryUsage() throws Exception {
      //messages size 100K
      final int MESSAGE_SIZE = 100000;
      String name1 = "messageUsage.test.1";
      String name2 = "messageUsage.test.2";

      server.stop();
      // set to 5 MB
      server.getConfiguration().setGlobalMaxSize(5000000);
      server.start();

      ActiveMQServerControl serverControl = createManagementControl();
      // check before adding messages
      assertEquals(0, serverControl.getAddressMemoryUsage(), "Memory Usage before adding messages");
      assertEquals(0, serverControl.getAddressMemoryUsagePercentage(), "MemoryUsagePercentage");

      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator);
           ClientSession session = csf.createSession()) {
         if (legacyCreateQueue) {
            session.createQueue(name1, RoutingType.ANYCAST, name1);
            session.createQueue(name2, RoutingType.ANYCAST, name2);
         } else {
            session.createQueue(QueueConfiguration.of(name1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
            session.createQueue(QueueConfiguration.of(name2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         }

         Queue serverQueue = server.locateQueue(SimpleString.of(name1));
         assertFalse(serverQueue.isDurable());

         ClientProducer producer1 = session.createProducer(name1);
         ClientProducer producer2 = session.createProducer(name2);
         sendMessagesWithPredefinedSize(10, session, producer1, MESSAGE_SIZE);
         sendMessagesWithPredefinedSize(10, session, producer2, MESSAGE_SIZE);

         //it is hard to predict an exact number so checking if it falls in a certain range: totalSizeOfMessageSent < X > totalSizeofMessageSent + 100k
         assertTrue(((20 * MESSAGE_SIZE) < serverControl.getAddressMemoryUsage()) && (serverControl.getAddressMemoryUsage() < ((20 * MESSAGE_SIZE) + 100000)), "Memory Usage within range ");
         assertTrue((40 <= serverControl.getAddressMemoryUsagePercentage()) && (42 >= serverControl.getAddressMemoryUsagePercentage()), "MemoryUsagePercentage");
      }
   }

   @TestTemplate
   public void testConnectorServiceManagement() throws Exception {
      ActiveMQServerControl managementControl = createManagementControl();
      managementControl.createConnectorService("myconn", FakeConnectorServiceFactory.class.getCanonicalName(), new HashMap<>());

      assertEquals(1, server.getConnectorsService().getConnectors().size());

      managementControl.createConnectorService("myconn2", FakeConnectorServiceFactory.class.getCanonicalName(), new HashMap<>());
      assertEquals(2, server.getConnectorsService().getConnectors().size());

      managementControl.destroyConnectorService("myconn");
      assertEquals(1, server.getConnectorsService().getConnectors().size());
      assertEquals("myconn2", managementControl.getConnectorServices()[0]);
   }

   @TestTemplate
   public void testCloseCOREclient() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();
      boolean durable = true;

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, durable, -1, false, false);
      } else {
         serverControl.createQueue(QueueConfiguration.of(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(durable).setAutoCreateAddress(false).toJSON());
      }

      ServerLocator receiveLocator = createInVMNonHALocator();
      ClientSessionFactory receiveCsf = createSessionFactory(receiveLocator);
      ClientSession receiveClientSession = receiveCsf.createSession(true, false, false);
      final ClientConsumer COREclient = receiveClientSession.createConsumer(name);

      ServerSession ss = server.getSessions().iterator().next();
      ServerConsumer sc = ss.getServerConsumers().iterator().next();

      assertFalse(COREclient.isClosed());
      serverControl.closeConsumerWithID(((ClientSessionImpl)receiveClientSession).getName(), Long.toString(sc.sequentialID()));
      Wait.waitFor(() -> COREclient.isClosed());
      assertTrue(COREclient.isClosed());
   }

   @TestTemplate
   public void testCloseJMSclient() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      ConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      Connection conn = cf.createConnection();
      conn.start();
      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      javax.jms.Topic topic = ActiveMQJMSClient.createTopic("ConsumerTestTopic");

      MessageConsumer JMSclient = session.createConsumer(topic, "test1");


      long clientID = -1;
      String sessionID = ((ClientSessionImpl)(((ActiveMQSession)session).getCoreSession())).getName();

      Set<ServerSession> sessions = server.getSessions();
      for (ServerSession sess : sessions) {
         if (sess.getName().equals(sessionID)) {
            Set<ServerConsumer> serverConsumers = sess.getServerConsumers();
            for (ServerConsumer serverConsumer : serverConsumers) {
               clientID = serverConsumer.sequentialID();
            }
         }
      }

      assertFalse(((org.apache.activemq.artemis.jms.client.ActiveMQMessageConsumer)JMSclient).isClosed());
      serverControl.closeConsumerWithID(sessionID, Long.toString(clientID));
      Wait.waitFor(() -> ((org.apache.activemq.artemis.jms.client.ActiveMQMessageConsumer)JMSclient).isClosed());
      assertTrue(((org.apache.activemq.artemis.jms.client.ActiveMQMessageConsumer)JMSclient).isClosed());
   }

   @Timeout(30)
   @TestTemplate
   public void testForceCloseSession() throws Exception {
      testForceCloseSession(false, false);
   }

   @Timeout(30)
   @TestTemplate
   public void testForceCloseSessionWithError() throws Exception {
      testForceCloseSession(true, false);
   }

   @Timeout(30)
   @TestTemplate
   public void testForceCloseSessionWithPendingStoreOperation() throws Exception {
      testForceCloseSession(false, true);
   }

   private void testForceCloseSession(boolean error, boolean pendingStoreOperation) throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString name = RandomUtil.randomUUIDSimpleString();
      boolean durable = true;

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, durable, -1, false, false);
      } else {
         serverControl.createQueue(QueueConfiguration.of(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(durable).setAutoCreateAddress(false).toJSON());
      }

      ServerLocator receiveLocator = createInVMNonHALocator().setCallTimeout(500);
      ClientSessionFactory receiveCsf = createSessionFactory(receiveLocator);
      ClientSession receiveClientSession = receiveCsf.createSession(true, false, false);
      final ClientConsumer clientConsumer = receiveClientSession.createConsumer(name);

      assertEquals(1, server.getSessions().size());

      ServerSession serverSession = server.getSessions().iterator().next();
      assertEquals(((ClientSessionImpl)receiveClientSession).getName(), serverSession.getName());

      if (error) {
         serverSession.getSessionContext().onError(0, "error");
      }

      if (pendingStoreOperation) {
         serverSession.getSessionContext().storeLineUp();
      }

      serverControl.closeSessionWithID(serverSession.getConnectionID().toString(), serverSession.getName(), true);

      Wait.assertTrue(() -> serverSession.getServerConsumers().isEmpty(), 500);
      Wait.assertTrue(() -> server.getSessions().isEmpty(), 500);
      Wait.assertThrows(ActiveMQObjectClosedException.class, () -> clientConsumer.receive(10), 2000, 100, () -> "Using the consumer should throw an exception here since its session was closed administratively");
   }

   @TestTemplate
   public void testAddUser() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      try {
         serverControl.addUser("x", "x", "x", true);
         fail();
      } catch (Exception expected) {
      }
   }

   @TestTemplate
   public void testRemoveUser() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      try {
         serverControl.removeUser("x");
         fail();
      } catch (Exception expected) {
      }
   }

   @TestTemplate
   public void testListUser() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      try {
         serverControl.listUser("x");
         fail();
      } catch (Exception expected) {
      }
   }

   @TestTemplate
   public void testResetUser() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      try {
         serverControl.resetUser("x", "x", "x");
         fail();
      } catch (Exception expected) {
      }
   }

   @TestTemplate
   public void testReplayWithoutDate() throws Exception {
      testReplaySimple(false);
   }

   @TestTemplate
   public void testReplayWithDate() throws Exception {
      testReplaySimple(true);
   }

   private void testReplaySimple(boolean useDate) throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      String queue = "testQueue" + RandomUtil.randomUUIDString();
      server.addAddressInfo(new AddressInfo(queue).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queue).setRoutingType(RoutingType.ANYCAST).setAddress(queue));

      ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue jmsQueue = session.createQueue(queue);
         MessageProducer producer = session.createProducer(jmsQueue);
         producer.send(session.createTextMessage("before"));

         connection.start();
         MessageConsumer consumer = session.createConsumer(jmsQueue);
         assertNotNull(consumer.receive(5000));
         assertNull(consumer.receiveNoWait());

         serverControl.replay(queue, queue, null);
         assertNotNull(consumer.receive(5000));
         assertNull(consumer.receiveNoWait());

         if (useDate) {
            serverControl.replay("dontexist", "dontexist", null); // just to force a move next file, and copy stuff into place
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
            Thread.sleep(1000); // waiting a second just to have the timestamp change
            String dateEnd = format.format(new Date());
            Thread.sleep(1000); // waiting a second just to have the timestamp change
            String dateStart = "19800101000000";


            for (int i = 0; i < 100; i++) {
               producer.send(session.createTextMessage("after receiving"));
            }
            for (int i = 0; i < 100; i++) {
               assertNotNull(consumer.receive());
            }
            assertNull(consumer.receiveNoWait());
            serverControl.replay(dateStart, dateEnd, queue, queue, null);
            for (int i = 0; i < 2; i++) { // replay of the replay will contain two messages
               TextMessage message = (TextMessage) consumer.receive(5000);
               assertNotNull(message);
               assertEquals("before", message.getText());
            }
            assertNull(consumer.receiveNoWait());
         } else {
            serverControl.replay(queue, queue, null);

            // replay of the replay, there will be two messages
            for (int i = 0; i < 2; i++) {
               assertNotNull(consumer.receive(5000));
            }
            assertNull(consumer.receiveNoWait());
         }
      }
   }


   @TestTemplate
   public void testReplayFilter() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      String queue = "testQueue" + RandomUtil.randomUUIDString();
      server.addAddressInfo(new AddressInfo(queue).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queue).setRoutingType(RoutingType.ANYCAST).setAddress(queue));

      ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue jmsQueue = session.createQueue(queue);
         MessageProducer producer = session.createProducer(jmsQueue);
         for (int i = 0; i < 10; i++) {
            TextMessage message = session.createTextMessage("message " + i);
            message.setIntProperty("i", i);
            producer.send(message);
         }

         connection.start();
         MessageConsumer consumer = session.createConsumer(jmsQueue);
         for (int i = 0; i < 10; i++) {
            assertNotNull(consumer.receive(5000));
         }
         assertNull(consumer.receiveNoWait());

         serverControl.replay(queue, queue, "i=5");
         TextMessage message = (TextMessage)consumer.receive(5000);
         assertNotNull(message);
         assertEquals(5, message.getIntProperty("i"));
         assertEquals("message 5", message.getText());
         assertNull(consumer.receiveNoWait());
      }
   }


   @TestTemplate
   public void testBrokerConnections() throws Exception {
      class Fake implements BrokerConnection {
         String name;
         boolean started = false;

         Fake(String name) {
            this.name = name;
         }

         @Override
         public String getName() {
            return name;
         }

         @Override
         public String getProtocol() {
            return "fake";
         }

         @Override
         public void start() throws Exception {
            started = true;
         }

         @Override
         public void stop() throws Exception {
            started = false;

         }

         @Override
         public boolean isStarted() {
            return started;
         }

         @Override
         public BrokerConnectConfiguration getConfiguration() {
            return null;
         }

         @Override
         public boolean isConnected() {
            return false;
         }

      }
      Fake fake = new Fake("fake" + UUIDGenerator.getInstance().generateStringUUID());
      server.registerBrokerConnection(fake);

      ActiveMQServerControl serverControl = createManagementControl();
      try {
         String result = serverControl.listBrokerConnections();
         assertTrue(result.contains(fake.getName()));
         serverControl.startBrokerConnection(fake.getName());
         assertTrue(fake.isStarted());
         serverControl.stopBrokerConnection(fake.getName());
         assertFalse(fake.isStarted());
      } catch (Exception expected) {
      }
   }

   @TestTemplate
   public void testManualStopStartEmbeddedWebServer() throws Exception {
      FakeWebServerComponent fake = new FakeWebServerComponent();
      server.addExternalComponent(fake, true);
      assertTrue(fake.isStarted());

      ActiveMQServerControl serverControl = createManagementControl();
      serverControl.stopEmbeddedWebServer();
      assertFalse(fake.isStarted());
      serverControl.startEmbeddedWebServer();
      assertTrue(fake.isStarted());
   }

   @TestTemplate
   public void testRestartEmbeddedWebServer() throws Exception {
      FakeWebServerComponent fake = new FakeWebServerComponent();
      server.addExternalComponent(fake, true);
      assertTrue(fake.isStarted());

      ActiveMQServerControl serverControl = createManagementControl();
      long time = System.currentTimeMillis();
      assertTrue(time >= fake.getStartTime());
      assertTrue(time > fake.getStopTime());
      Thread.sleep(5);
      serverControl.restartEmbeddedWebServer();
      assertTrue(serverControl.isEmbeddedWebServerStarted());
      assertTrue(time < fake.getStartTime());
      assertTrue(time < fake.getStopTime());
   }

   @TestTemplate
   public void testRestartEmbeddedWebServerTimeout() throws Exception {
      final CountDownLatch startDelay = new CountDownLatch(1);
      FakeWebServerComponent fake = new FakeWebServerComponent(startDelay);
      server.addExternalComponent(fake, false);

      ActiveMQServerControl serverControl = createManagementControl();
      try {
         serverControl.restartEmbeddedWebServer(1);
         fail();
      } catch (ActiveMQTimeoutException e) {
         // expected
      } finally {
         startDelay.countDown();
      }
      Wait.waitFor(() -> fake.isStarted());
   }

   @TestTemplate
   public void testRestartEmbeddedWebServerException() throws Exception {
      final String message = RandomUtil.randomUUIDString();
      final Exception startException = new ActiveMQIllegalStateException(message);
      FakeWebServerComponent fake = new FakeWebServerComponent(startException);
      server.addExternalComponent(fake, false);

      ActiveMQServerControl serverControl = createManagementControl();
      try {
         serverControl.restartEmbeddedWebServer(1000);
         fail();
      } catch (ActiveMQException e) {
         assertSame(startException, e.getCause(), "Unexpected cause");
      }
   }

   class FakeWebServerComponent implements ServiceComponent, WebServerComponentMarker {
      AtomicBoolean started = new AtomicBoolean(false);
      AtomicLong startTime = new AtomicLong(0);
      AtomicLong stopTime = new AtomicLong(0);
      CountDownLatch startDelay;
      Exception startException;

      FakeWebServerComponent(CountDownLatch startDelay) {
         this.startDelay = startDelay;
      }

      FakeWebServerComponent(Exception startException) {
         this.startException = startException;
      }

      FakeWebServerComponent() {
      }

      @Override
      public void start() throws Exception {
         if (started.get()) {
            return;
         }
         if (startDelay != null) {
            startDelay.await();
         }
         if (startException != null) {
            throw startException;
         }
         startTime.set(System.currentTimeMillis());
         started.set(true);
      }

      @Override
      public void stop() throws Exception {
         stop(false);
      }

      @Override
      public void stop(boolean shutdown) throws Exception {
         if (!shutdown) {
            throw new RuntimeException("shutdown flag must be true");
         }
         stopTime.set(System.currentTimeMillis());
         started.set(false);
      }

      @Override
      public boolean isStarted() {
         return started.get();
      }

      public long getStartTime() {
         return startTime.get();
      }

      public long getStopTime() {
         return stopTime.get();
      }
   }

   protected void scaleDown(ScaleDownHandler handler) throws Exception {
      SimpleString address = SimpleString.of("testQueue");
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SERVER_ID_PROP_NAME, "2");
      Configuration config = createDefaultInVMConfig(2).clearAcceptorConfigurations().addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName(), params)).setSecurityEnabled(false);
      ActiveMQServer server2 = addServer(ActiveMQServers.newActiveMQServer(config, null, true));

      this.conf.clearConnectorConfigurations().addConnectorConfiguration("server2-connector", new TransportConfiguration(INVM_CONNECTOR_FACTORY, params));

      server2.start();
      server.addAddressInfo(new AddressInfo(address, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(address, RoutingType.ANYCAST, address, null, true, false, -1, false, false);
      } else {
         server.createQueue(QueueConfiguration.of(address).setRoutingType(RoutingType.ANYCAST).setAutoCreateAddress(false));
      }
      server2.addAddressInfo(new AddressInfo(address, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server2.createQueue(address, RoutingType.ANYCAST, address, null, true, false, -1, false, false);
      } else {
         server2.createQueue(QueueConfiguration.of(address).setRoutingType(RoutingType.ANYCAST).setAutoCreateAddress(false));
      }
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();
      ClientProducer producer = session.createProducer(address);
      for (int i = 0; i < 100; i++) {
         ClientMessage message = session.createMessage(true);
         message.getBodyBuffer().writeString("m" + i);
         producer.send(message);
      }

      ActiveMQServerControl managementControl = createManagementControl();
      handler.scaleDown(managementControl);
      locator.close();
      locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(INVM_CONNECTOR_FACTORY, params)));
      csf = createSessionFactory(locator);
      session = csf.createSession();
      session.start();
      ClientConsumer consumer = session.createConsumer(address);
      for (int i = 0; i < 100; i++) {
         ClientMessage m = consumer.receive(5000);
         assertNotNull(m);
      }
   }

   interface ScaleDownHandler {

      void scaleDown(ActiveMQServerControl control) throws Exception;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      connectorConfig = new TransportConfiguration(INVM_CONNECTOR_FACTORY);

      conf = createDefaultNettyConfig().setJMXManagementEnabled(true).addConnectorConfiguration(connectorConfig.getName(), connectorConfig);
      conf.setSecurityEnabled(true);
      SecurityConfiguration securityConfiguration = new SecurityConfiguration();
      securityConfiguration.addUser("guest", "guest");
      securityConfiguration.addUser("myUser", "myPass");
      securityConfiguration.addUser("none", "none");
      securityConfiguration.addRole("guest", "guest");
      securityConfiguration.addRole("myUser", "guest");
      securityConfiguration.addRole("none", "none");
      securityConfiguration.setDefaultUser("guest");
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), securityConfiguration);
      conf.setJournalRetentionDirectory(conf.getJournalDirectory() + "_ret"); // needed for replay tests
      server = addServer(ActiveMQServers.newActiveMQServer(conf, mbeanServer, securityManager, true));
      server.getConfiguration().setAddressQueueScanPeriod(100);
      server.start();

      Set<Role> role = new HashSet<>();
      role.add(new Role("guest", true, true, true, true, true, true, true, true, true, true, false, false));
      role.add(new Role("none", false, false, false, false, false, false, false, false, false, false, false, false));
      server.getSecurityRepository().addMatch("#", role);
   }

   protected ActiveMQServerControl createManagementControl() throws Exception {
      return ManagementControlHelper.createActiveMQServerControl(mbeanServer);
   }

   private String createJsonFilter(String fieldName, String operationName, String value, String sortField, String sortOrder) {
      Map<String, Object> filterMap = new HashMap<>();
      filterMap.put("field", fieldName);
      filterMap.put("operation", operationName);
      filterMap.put("value", value);
      filterMap.put("sortField", sortField);
      filterMap.put("sortOrder", sortOrder);
      JsonObject jsonFilterObject = JsonUtil.toJsonObject(filterMap);
      return jsonFilterObject.toString();
   }

   private void sendMessagesWithPredefinedSize(int numberOfMessages,
                                               ClientSession session,
                                               ClientProducer producer,
                                               int messageSize) throws Exception {
      ClientMessage message;
      final byte[] body = new byte[messageSize];
      ByteBuffer bb = ByteBuffer.wrap(body);
      for (int i = 1; i <= messageSize; i++) {
         bb.put(getSamplebyte(i));
      }

      for (int i = 0; i < numberOfMessages; i++) {
         message = session.createMessage(true);
         ActiveMQBuffer bodyLocal = message.getBodyBuffer();
         bodyLocal.writeBytes(body);

         producer.send(message);
         if (i % 1000 == 0) {
            session.commit();
         }
      }
      session.commit();
   }

   private static class TestBrokerPlugin implements ActiveMQServerPlugin {
      @Override
      public void registered(ActiveMQServer server) {
      }
   }
}

