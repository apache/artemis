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
package org.apache.activemq.artemis.tests.integration.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.routing.policies.FirstElementPolicy;
import org.apache.activemq.artemis.core.server.routing.policies.PolicyFactoryResolver;
import org.apache.activemq.artemis.core.server.routing.targets.Target;
import org.apache.activemq.artemis.core.server.routing.KeyType;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.security.SecurityTest;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ExtendWith(ParameterizedTestExtension.class)
public class KeyTypeTest extends RoutingTestBase {

   private static final String MOCK_POLICY_NAME = "MOCK_POLICY";

   @Parameters(name = "protocol: {0}")
   public static Collection<Object[]> data() {
      Collection<Object[]> data = new ArrayList<>();

      for (String protocol : Arrays.asList(AMQP_PROTOCOL, CORE_PROTOCOL, MQTT_PROTOCOL, OPENWIRE_PROTOCOL)) {
         data.add(new Object[] {protocol});
      }

      return data;
   }


   static {
      String path = System.getProperty("java.security.auth.login.config");
      if (path == null) {
         URL resource = SecurityTest.class.getClassLoader().getResource("login.config");
         if (resource != null) {
            path = resource.getFile();
            System.setProperty("java.security.auth.login.config", path);
         }
      }
   }

   private final String protocol;

   private final List<String> keys = new ArrayList<>();

   public KeyTypeTest(String protocol) {
      this.protocol = protocol;
   }

   @BeforeEach
   public void setup() throws Exception {
      PolicyFactoryResolver.getInstance().registerPolicyFactory(MOCK_POLICY_NAME, () -> new FirstElementPolicy(MOCK_POLICY_NAME) {
         @Override
         public Target selectTarget(List<Target> targets, String key) {
            keys.add(key);
            return super.selectTarget(targets, key);
         }
      });
   }

   @TestTemplate
   public void testClientIDKey() throws Exception {
      setupPrimaryServerWithDiscovery(0, GROUP_ADDRESS, GROUP_PORT, true, true, false);
      setupRouterServerWithDiscovery(0, KeyType.CLIENT_ID, MOCK_POLICY_NAME, null, true, null, 1);
      startServers(0);

      testConnection(TransportConstants.DEFAULT_HOST, TransportConstants.DEFAULT_PORT + 0,
              "test", null, null, false, false, -1);

      assertEquals(1, keys.size());
      assertEquals("test", keys.get(0));
   }

   @Override
   protected boolean isForceUniqueStorageManagerIds() {
      return false;
   }

   @TestTemplate
   public void testClientIDKeyOnBackup() throws Exception {
      setupPrimaryServerWithDiscovery(0, GROUP_ADDRESS, GROUP_PORT, true, true, false);
      setupDiscoveryClusterConnection("cluster0", 0, "dg1", "queues", MessageLoadBalancingType.OFF, 1, true);
      setupRouterServerWithCluster(0, KeyType.CLIENT_ID, FirstElementPolicy.NAME, null, true, null, 1, "cluster0");
      setupBackupServer(1, 0, false, HAType.SharedNothingReplication, true);
      UDPBroadcastEndpointFactory endpoint = new UDPBroadcastEndpointFactory().setGroupAddress(GROUP_ADDRESS).setGroupPort(GROUP_PORT);
      List<String> connectorInfos = getServer(1).getConfiguration().getConnectorConfigurations().keySet().stream().collect(Collectors.toList());
      BroadcastGroupConfiguration bcConfig = new BroadcastGroupConfiguration().setName("bg1").setBroadcastPeriod(1000).setConnectorInfos(connectorInfos).setEndpointFactory(endpoint);
      DiscoveryGroupConfiguration dcConfig = new DiscoveryGroupConfiguration().setName("dg1").setRefreshTimeout(5000).setDiscoveryInitialWaitTimeout(5000).setBroadcastEndpointFactory(endpoint);
      getServer(1).getConfiguration().addBroadcastGroupConfiguration(bcConfig).addDiscoveryGroupConfiguration(dcConfig.getName(), dcConfig);
      setupDiscoveryClusterConnection("cluster0", 1, "dg1", "queues", MessageLoadBalancingType.OFF, 1, true);
      setupRouterServerWithCluster(1, KeyType.CLIENT_ID, MOCK_POLICY_NAME, null, true, null, 1, "cluster0");
      startServers(0, 1);

      waitForTopology(getServer(0), 1, 1);

      getServer(0).fail(true);

      waitForFailoverTopology(1);

      testConnection(TransportConstants.DEFAULT_HOST, TransportConstants.DEFAULT_PORT + 1,
              "test", null, null, false, false, -1);

      assertEquals(1, keys.size());
      assertEquals("test", keys.get(0));
   }

   @TestTemplate
   public void testSNIHostKey() throws Exception {
      String localHostname = "localhost.localdomain";

      if (!checkLocalHostname(localHostname)) {
         localHostname = "artemis.localtest.me";

         if (!checkLocalHostname(localHostname)) {
            localHostname = "localhost";

            assumeTrue(CORE_PROTOCOL.equals(protocol) && checkLocalHostname(localHostname));
         }
      }

      setupPrimaryServerWithDiscovery(0, GROUP_ADDRESS, GROUP_PORT, true, true, false);
      getDefaultServerAcceptor(0).getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      getDefaultServerAcceptor(0).getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "server-keystore.jks");
      getDefaultServerAcceptor(0).getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");

      setupRouterServerWithDiscovery(0, KeyType.SNI_HOST, MOCK_POLICY_NAME, null, true, null, 1);
      startServers(0);

      testConnection(localHostname, TransportConstants.DEFAULT_PORT + 0,
              null, null, null, true, false, -1);

      assertEquals(1, keys.size());
      assertEquals(localHostname, keys.get(0));
   }

   @TestTemplate
   public void testSourceIPKey() throws Exception {
      setupPrimaryServerWithDiscovery(0, GROUP_ADDRESS, GROUP_PORT, true, true, false);
      setupRouterServerWithDiscovery(0, KeyType.SOURCE_IP, MOCK_POLICY_NAME, null, true, null, 1);
      startServers(0);

      testConnection(TransportConstants.DEFAULT_HOST, TransportConstants.DEFAULT_PORT + 0,
              null, null, null, false, false, -1);

      assertEquals(1, keys.size());
      assertEquals(InetAddress.getLoopbackAddress().getHostAddress(), keys.get(0));
   }

   @TestTemplate
   public void testUserNameKey() throws Exception {
      setupPrimaryServerWithDiscovery(0, GROUP_ADDRESS, GROUP_PORT, true, true, false);
      setupRouterServerWithDiscovery(0, KeyType.USER_NAME, MOCK_POLICY_NAME, null, true, null, 1);
      startServers(0);

      testConnection(TransportConstants.DEFAULT_HOST, TransportConstants.DEFAULT_PORT + 0,
              null, "admin", "admin", false, false, -1);

      assertEquals(1, keys.size());
      assertEquals("admin", keys.get(0));
   }

   @TestTemplate
   public void testUserNameKeyFromCertificate() throws Exception {
      setupPrimaryServerWithDiscovery(0, GROUP_ADDRESS, GROUP_PORT, true, true, false);
      getDefaultServerAcceptor(0).getParams().put("securityDomain", "CertLogin");
      getDefaultServerAcceptor(0).getParams().put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      getDefaultServerAcceptor(0).getParams().put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "server-keystore.jks");
      getDefaultServerAcceptor(0).getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      getDefaultServerAcceptor(0).getParams().put(TransportConstants.WANT_CLIENT_AUTH_PROP_NAME, "true");
      getDefaultServerAcceptor(0).getParams().put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, "true");
      getDefaultServerAcceptor(0).getParams().put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "client-ca-truststore.jks");
      getDefaultServerAcceptor(0).getParams().put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");

      getServer(0).getConfiguration().setSecurityEnabled(true);
      getServer(0).getSecurityRepository().addMatch("#", Collections.singleton(
              new Role("programmers", true, true, true, true, true, true, true, true, true, true, false, false)));

      setupRouterServerWithDiscovery(0, KeyType.USER_NAME, MOCK_POLICY_NAME, null, true, null, 1);

      startServers(0);

      testConnection(TransportConstants.DEFAULT_HOST, TransportConstants.DEFAULT_PORT + 0,
              null, null, null, true, true, -1);

      assertEquals(1, keys.size());
      assertEquals("first", keys.get(0));
   }

   @TestTemplate
   public void testRoleNameKeyLocalTarget() throws Exception {
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLogin");
      servers[0] = addServer(ActiveMQServers.newActiveMQServer(createDefaultConfig(true).setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
      setupRouterServerWithLocalTarget(0, KeyType.ROLE_NAME, "b", "b");

      // ensure advisory permission is present for openwire connection creation by 'b'
      HierarchicalRepository<Set<Role>> securityRepository = servers[0].getSecurityRepository();
      Role role = new Role("b", true, true, true, true, true, true, false, false, true, true, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      securityRepository.addMatch("ActiveMQ.Advisory.#", roles);

      startServers(0);

      // expect disconnect/reject as not role b
      try {
         final int noRetriesSuchThatWeGetAnErrorOnRejection = 0;
         testConnection(TransportConstants.DEFAULT_HOST, TransportConstants.DEFAULT_PORT + 0,
                 null, "a", "a", false, false, noRetriesSuchThatWeGetAnErrorOnRejection);
         fail("Expect to be rejected as not in role b");
      } catch (Exception expectedButNotSpecificDueToDifferentProtocolsInPlay) {
      }

      testConnection(TransportConstants.DEFAULT_HOST, TransportConstants.DEFAULT_PORT + 0,
              null, "b", "b", false, false, -1);
   }

   private void testConnection(String host, int port, String clientID, String user, String password, boolean sslEnabled, boolean needClientAuth, int retries) throws Exception {
      if (MQTT_PROTOCOL.equals(protocol)) {
         for (int i = 0; retries == -1 || i <= retries; i++) {
            try {
               testMQTTConnection(host, port, clientID, user, password, sslEnabled, needClientAuth);
               break;
            } catch (Throwable t) {
               if (i == retries) {
                  throw t;
               }
            }
         }
      } else {
         ConnectionFactory connectionFactory = createFactory(protocol, sslEnabled, host,
                 port, clientID, user, password, needClientAuth, retries);

         try (Connection connection = connectionFactory.createConnection()) {
            connection.start();
         }
      }
   }

   private void testMQTTConnection(String host, int port, String clientID, String user, String password, boolean sslEnabled, boolean needClientAuth) throws Exception {
      MqttConnectOptions connOpts = new MqttConnectOptions();
      connOpts.setCleanSession(true);
      connOpts.setUserName(user);
      if (password != null) {
         connOpts.setPassword(password.toCharArray());
      }

      String serverURIScheme = "tcp";

      if (sslEnabled) {
         serverURIScheme = "ssl";

         SSLContext sslContext = SSLContext.getInstance("TLS");

         KeyManager[] kms = null;
         if (needClientAuth) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(getClass().getClassLoader().getResourceAsStream("client-keystore.jks"), "securepass".toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, "securepass".toCharArray());
            kms = kmf.getKeyManagers();
         }

         KeyStore trustStore = KeyStore.getInstance("JKS");
         trustStore.load(getClass().getClassLoader().getResourceAsStream("server-ca-truststore.jks"), "securepass".toCharArray());
         TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
         tmf.init(trustStore);

         sslContext.init(kms, tmf.getTrustManagers(), null); // null KeyManagers, null TrustManagers, null SecureRandom

         SSLSocketFactory socketFactory = sslContext.getSocketFactory();
         connOpts.setSocketFactory(socketFactory);
      }

      if (clientID == null) {
         clientID = UUID.randomUUID().toString();
      }

      try (MqttClient mqttClient = new MqttClient(serverURIScheme + "://" + host + ":" + port, clientID, new MemoryPersistence())) {
         mqttClient.connect(connOpts);
         mqttClient.disconnect();
      }
   }

   private boolean checkLocalHostname(String host) {
      try {
         return InetAddress.getByName(host).isLoopbackAddress();
      } catch (UnknownHostException ignore) {
         return false;
      }
   }
}
