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
package org.apache.activemq.artemis.tests.integration.mqtt5;

import javax.jms.ConnectionFactory;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.apache.activemq.artemis.core.protocol.mqtt.PacketIdCache;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTProtocolManager;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTSessionState;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.core.remoting.impl.AbstractAcceptor;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ClassloadingUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Collections.singletonList;
import static org.apache.activemq.artemis.core.protocol.mqtt.MQTTProtocolManagerFactory.MQTT_PROTOCOL_NAME;

public class MQTT5TestSupport extends ActiveMQTestBase {

   // The Paho MQTT client logging adds noise during QoS2 operations
   private static final java.util.logging.Logger PAHO_LOGGER;
   static {
      PAHO_LOGGER = java.util.logging.Logger.getLogger("org.eclipse.paho.mqttv5.client.internal.ClientState");
      PAHO_LOGGER.setLevel(java.util.logging.Level.WARNING);
   }

   protected static final String TCP = "tcp";
   protected static final String WS = "ws";
   protected static final String SSL = "ssl";
   protected static final String WSS = "wss";
   protected static final SimpleString DEAD_LETTER_ADDRESS = SimpleString.of("DLA");
   protected static final SimpleString EXPIRY_ADDRESS = SimpleString.of("EXPIRY");

   protected MqttClient createPahoClient(String clientId) throws MqttException {
      return createPahoClient(TCP, clientId);
   }

   protected MqttClient createPahoClient(String protocol, String clientId) throws MqttException {
      return createPahoClient(protocol, clientId, (isUseSsl() ? getSslPort() : getPort()));
   }

   protected MqttClient createPahoClient(String clientId, int port) throws MqttException {
      return createPahoClient(TCP, clientId, port);
   }

   protected MqttClient createPahoClient(String protocol, String clientId, int port) throws MqttException {
      return new MqttClient(protocol + "://localhost:" + port, clientId, new MemoryPersistence());
   }

   protected org.eclipse.paho.client.mqttv3.MqttClient createPaho3_1_1Client(String clientId) throws org.eclipse.paho.client.mqttv3.MqttException {
      return new org.eclipse.paho.client.mqttv3.MqttClient(TCP + "://localhost:" + (isUseSsl() ? getSslPort() : getPort()), clientId, new org.eclipse.paho.client.mqttv3.persist.MemoryPersistence());
   }

   protected MqttAsyncClient createAsyncPahoClient(String clientId) throws MqttException {
      return new MqttAsyncClient(TCP + "://localhost:" + (isUseSsl() ? getSslPort() : getPort()), clientId, new MemoryPersistence());
   }

   protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   protected static final long DEFAULT_TIMEOUT_SEC = 60;
   protected ActiveMQServer server;

   protected int port = 1883;
   protected int sslPort = 8883;
   protected ConnectionFactory cf;
   protected LinkedList<Throwable> exceptions = new LinkedList<>();
   protected boolean persistent;
   protected String protocolScheme;

   protected static final int NUM_MESSAGES = 250;

   public static final int AT_MOST_ONCE = 0;
   public static final int AT_LEAST_ONCE = 1;
   public static final int EXACTLY_ONCE = 2;

   protected String noprivUser = "noprivs";
   protected String noprivPass = "noprivs";

   protected String createAddressUser = "createAddress";
   protected String createAddressPass = "createAddress";

   protected String browseUser = "browser";
   protected String browsePass = "browser";

   protected String guestUser = "guest";
   protected String guestPass = "guest";

   protected String fullUser = "user";
   protected String fullPass = "pass";

   protected String noDeleteUser = "noDelete";
   protected String noDeletePass = "noDelete";

   public MQTT5TestSupport() {
      this.protocolScheme = "mqtt";
   }

   public File basedir() throws IOException {
      ProtectionDomain protectionDomain = getClass().getProtectionDomain();
      return new File(new File(protectionDomain.getCodeSource().getLocation().getPath()), "../..").getCanonicalFile();
   }

   public ActiveMQServer getServer() {
      return server;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      exceptions.clear();
      startBroker();
      createJMSConnection();
      if (isProtocolLoggingEnabled()) {
         enableProtocolLogging();
      }
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      stopBroker();
      if (isProtocolLoggingEnabled()) {
         disableProtocolLogging();
      }
      super.tearDown();
   }

   public void enableProtocolLogging() {
      LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      org.apache.logging.log4j.core.config.Configuration config = ctx.getConfiguration();
      LoggerConfig loggerConfig = new LoggerConfig(MQTTUtil.class.getName(), Level.TRACE, true);
      config.addLogger(MQTTUtil.class.getName(), loggerConfig);
      ctx.updateLoggers();
   }

   public void disableProtocolLogging() {
      LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      org.apache.logging.log4j.core.config.Configuration config = ctx.getConfiguration();
      config.removeLogger(MQTTUtil.class.getName());
      ctx.updateLoggers();
   }

   public void configureBroker() throws Exception {
      super.setUp();
      server = createServerForMQTT();
      addCoreConnector();
      addMQTTConnector();
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setMaxSizeBytes(999999999);
      addressSettings.setAutoCreateQueues(true);
      addressSettings.setAutoCreateAddresses(true);
      configureBrokerSecurity(server);

      server.getAddressSettingsRepository().addMatch("#", addressSettings);

      server.getConfiguration().setMessageExpiryScanPeriod(500);
   }

   /**
    * Copied from org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport#configureBrokerSecurity()
    */
   protected void configureBrokerSecurity(ActiveMQServer server) {
      if (isSecurityEnabled()) {
         ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();

         // User additions
         securityManager.getConfiguration().addUser(noprivUser, noprivPass);
         securityManager.getConfiguration().addRole(noprivUser, "nothing");
         securityManager.getConfiguration().addUser(createAddressUser, createAddressPass);
         securityManager.getConfiguration().addRole(createAddressUser, "createAddress");
         securityManager.getConfiguration().addUser(browseUser, browsePass);
         securityManager.getConfiguration().addRole(browseUser, "browser");
         securityManager.getConfiguration().addUser(guestUser, guestPass);
         securityManager.getConfiguration().addRole(guestUser, "guest");
         securityManager.getConfiguration().addUser(fullUser, fullPass);
         securityManager.getConfiguration().addRole(fullUser, "full");
         securityManager.getConfiguration().addUser(noDeleteUser, noDeleteUser);
         securityManager.getConfiguration().addRole(noDeleteUser, "noDelete");

         // Configure roles
         HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
         Set<Role> value = new HashSet<>();
         value.add(new Role("nothing", false, false, false, false, false, false, false, false, false, false, false, false));
         value.add(new Role("browser", false, false, false, false, false, false, false, true, false, false, false, false));
         value.add(new Role("guest", false, true, false, false, false, false, false, true, false, false, false, false));
         value.add(new Role("full", true, true, true, true, true, true, true, true, true, true, false, false));
         value.add(new Role("createAddress", false, false, false, false, false, false, false, false, true, false, false, false));
         value.add(new Role("noDelete", true, true, true, false, true, false, true, true, true, true, false, false));
         securityRepository.addMatch("#", value);

         server.getConfiguration().setSecurityEnabled(true);
      } else {
         server.getConfiguration().setSecurityEnabled(false);
      }
   }

   public void startBroker() throws Exception {
      configureBroker();
      server.start();
      server.waitForActivation(10, TimeUnit.SECONDS);
   }

   public void createJMSConnection() throws Exception {
      cf = new ActiveMQConnectionFactory(false, new TransportConfiguration(ActiveMQTestBase.NETTY_CONNECTOR_FACTORY));
   }

   private ActiveMQServer createServerForMQTT() throws Exception {
      Configuration defaultConfig = createDefaultConfig(true).setIncomingInterceptorClassNames(singletonList(MQTTIncomingInterceptor.class.getName())).setOutgoingInterceptorClassNames(singletonList(MQTTOutoingInterceptor.class.getName()));
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setDeadLetterAddress(DEAD_LETTER_ADDRESS);
      addressSettings.setExpiryAddress(EXPIRY_ADDRESS);
      defaultConfig.getAddressSettings().put("#", addressSettings);
      defaultConfig.setMqttSessionScanInterval(200);
      return createServer(true, defaultConfig);
   }

   protected void addCoreConnector() throws Exception {
      // Overrides of this method can add additional configuration options or add multiple
      // MQTT transport connectors as needed, the port variable is always supposed to be
      // assigned the primary MQTT connector's port.

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.PORT_PROP_NAME, "" + 5445);
      params.put(TransportConstants.PROTOCOLS_PROP_NAME, "CORE");
      TransportConfiguration transportConfiguration = new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params);
      server.getConfiguration().getAcceptorConfigurations().add(transportConfiguration);

      logger.debug("Added CORE connector to broker");
   }

   protected void addMQTTConnector() throws Exception {
      // Overrides of this method can add additional configuration options or add multiple
      // MQTT transport connectors as needed, the port variable is always supposed to be
      // assigned the primary MQTT connector's port.

      server.getConfiguration().addAcceptorConfiguration(MQTT_PROTOCOL_NAME, "tcp://localhost:" + (isUseSsl() ? sslPort : port) + "?protocols=MQTT;anycastPrefix=anycast:;multicastPrefix=multicast:" + (isUseSsl() ? "&sslEnabled=true&keyStorePath=server-keystore.p12&keyStorePassword=securepass" : "") + (isMutualSsl() ? "&needClientAuth=true&trustStorePath=client-ca-truststore.p12&trustStorePassword=securepass" : ""));
      server.getConfiguration().setConnectionTtlCheckInterval(100);

      logger.debug("Added MQTT connector to broker");
   }

   public void stopBroker() throws Exception {
      if (server.isStarted()) {
         server.stop();
         server = null;
      }
   }

   protected String getQueueName() {
      return getClass().getName() + "." + getTestMethodName();
   }

   protected String getTopicName() {
      return getClass().getName() + "." + getTestMethodName();
   }

   public boolean isPersistent() {
      return persistent;
   }

   public int getPort() {
      return this.port;
   }

   public int getSslPort() {
      return this.sslPort;
   }

   public boolean isSecurityEnabled() {
      return false;
   }

   public boolean isUseSsl() {
      return false;
   }

   public boolean isMutualSsl() {
      return false;
   }

   public boolean isProtocolLoggingEnabled() {
      return false;
   }

   protected interface Task {

      void run() throws Exception;
   }

   public Map<String, MQTTSessionState> getSessionStates() {
      MQTTProtocolManager protocolManager = getProtocolManager();
      if (protocolManager == null) {
         return Collections.emptyMap();
      } else {
         return protocolManager.getStateManager().getSessionStates();
      }
   }

   public void scanSessions() {
      getProtocolManager().getStateManager().scanSessions();
   }

   public MQTTProtocolManager getProtocolManager() {
      Acceptor acceptor = server.getRemotingService().getAcceptor(MQTT_PROTOCOL_NAME);
      if (acceptor instanceof AbstractAcceptor abstractAcceptor) {
         ProtocolManager protocolManager = abstractAcceptor.getProtocolMap().get(MQTT_PROTOCOL_NAME);
         if (protocolManager instanceof MQTTProtocolManager mqttProtocolManager) {
            return mqttProtocolManager;
         }
      }
      return null;
   }

   protected Queue getSharedSubscriptionQueue(String mqttTopicFilter) {
      return getSubscriptionQueue(mqttTopicFilter, null);
   }

   protected Queue getSubscriptionQueue(String mqttTopicFilter, String clientId) {
      return server.locateQueue(MQTTUtil.getCoreQueueFromMqttTopic(mqttTopicFilter, clientId, server.getConfiguration().getWildcardConfiguration()));
   }

   protected Queue getRetainedMessageQueue(String mqttTopicFilter) {
      return server.locateQueue(MQTTUtil.getCoreRetainAddressFromMqttTopic(mqttTopicFilter, server.getConfiguration().getWildcardConfiguration()));
   }

   protected void setAcceptorProperty(String property) throws Exception {
      server.getRemotingService().getAcceptor(MQTT_PROTOCOL_NAME).stop();
      server.getRemotingService().createAcceptor(MQTT_PROTOCOL_NAME, "tcp://localhost:" + port + "?protocols=MQTT;" + property).start();
   }

   protected DuplicateIDCache getPubCache(String clientId) {
      return getCache(clientId, PacketIdCache.TYPE.PUBLISH);
   }

   protected int getPubCacheSize(String clientId) {
      return getCacheSize(getPubCache(clientId));
   }

   protected DuplicateIDCache getPubRecCache(String clientId) {
      return getCache(clientId, PacketIdCache.TYPE.PUBREC);
   }

   protected int getPubRecCacheSize(String clientId) {
      return getCacheSize(getPubRecCache(clientId));
   }

   private int getCacheSize(DuplicateIDCache cache) {
      return cache == null ? 0 : cache.getMap().size();
   }

   private DuplicateIDCache getCache(String clientId, PacketIdCache.TYPE type) {
      SimpleString cacheName = PacketIdCache.getCacheName(server.getInternalNamingPrefix(), clientId, type);
      if (server.getPostOffice().duplicateIDCacheExists(cacheName)) {
         return server.getPostOffice().getDuplicateIDCache(cacheName);
      } else {
         return null;
      }
   }

   protected static void reconnectSafely(MqttClient subscriber) throws Exception {
      Wait.waitFor(() -> {
         try {
            subscriber.reconnect();
            return true;
         } catch (MqttException e) {
            return false;
         }
      });
   }

   /*
    * From the Paho MQTT client's JavaDoc:
    *
    * com.ibm.ssl.protocol - One of: SSL, SSLv3, TLS, TLSv1, SSL_TLS.
    * com.ibm.ssl.contextProvider - Underlying JSSE provider. For example "IBMJSSE2" or "SunJSSE"
    * com.ibm.ssl.keyStore - The name of the file that contains the KeyStore object that you want the KeyManager to use. For example /mydir/etc/key.p12
    * com.ibm.ssl.keyStorePassword -The password for the KeyStore object that you want the KeyManager to use. The password can either be in plain-text, or may be obfuscated using the static method: com.ibm.micro.security.Password.obfuscate(char[] password). This obfuscates the password using a simple and insecure XOR and Base64 encoding mechanism. Note that this is only a simple scrambler to obfuscate clear-text passwords.
    * com.ibm.ssl.keyStoreType - Type of key store, for example "PKCS12", "JKS", or "JCEKS".
    * com.ibm.ssl.keyStoreProvider - Key store provider, for example "IBMJCE" or "IBMJCEFIPS".
    * com.ibm.ssl.trustStore - The name of the file that contains the KeyStore object that you want the TrustManager to use.
    * com.ibm.ssl.trustStorePassword - The password for the TrustStore object that you want the TrustManager to use. The password can either be in plain-text, or may be obfuscated using the static method: com.ibm.micro.security.Password.obfuscate(char[] password). This obfuscates the password using a simple and insecure XOR and Base64 encoding mechanism. Note that this is only a simple scrambler to obfuscate clear-text passwords.
    * com.ibm.ssl.trustStoreType - The type of KeyStore object that you want the default TrustManager to use. Same possible values as "keyStoreType".
    * com.ibm.ssl.trustStoreProvider - Trust store provider, for example "IBMJCE" or "IBMJCEFIPS".
    * com.ibm.ssl.enabledCipherSuites - A list of which ciphers are enabled. Values are dependent on the provider, for example: SSL_RSA_WITH_AES_128_CBC_SHA;SSL_RSA_WITH_3DES_EDE_CBC_SHA.
    * com.ibm.ssl.keyManager - Sets the algorithm that will be used to instantiate a KeyManagerFactory object instead of using the default algorithm available in the platform. Example values: "IbmX509" or "IBMJ9X509".
    * com.ibm.ssl.trustManager - Sets the algorithm that will be used to instantiate a TrustManagerFactory object instead of using the default algorithm available in the platform. Example values: "PKIX" or "IBMJ9X509".
    */
   protected MqttConnectionOptions getSslMqttConnectOptions() {
      MqttConnectionOptions connectionOptions = new MqttConnectionOptions();
      Properties properties = new Properties();
      properties.setProperty("com.ibm.ssl.trustStore", ClassloadingUtil.findResource("server-ca-truststore.p12").getPath());
      properties.setProperty("com.ibm.ssl.trustStorePassword", "securepass");
      if (isMutualSsl()) {
         properties.setProperty("com.ibm.ssl.keyStore", ClassloadingUtil.findResource("client-keystore.p12").getPath());
         properties.setProperty("com.ibm.ssl.keyStorePassword", "securepass");
      }
      connectionOptions.setSSLProperties(properties);

      return connectionOptions;
   }

   public static class MQTTIncomingInterceptor implements MQTTInterceptor {

      private static int messageCount = 0;

      @Override
      public boolean intercept(MqttMessage packet, RemotingConnection connection) throws ActiveMQException {
         if (packet.getClass() == MqttPublishMessage.class) {
            messageCount++;
         }
         return true;
      }

      public static void clear() {
         messageCount = 0;
      }

      public static int getMessageCount() {
         return messageCount;
      }
   }

   public static class MQTTOutoingInterceptor implements MQTTInterceptor {

      private static int messageCount = 0;

      @Override
      public boolean intercept(MqttMessage packet, RemotingConnection connection) throws ActiveMQException {
         if (packet.getClass() == MqttPublishMessage.class) {
            messageCount++;
         }
         return true;
      }

      public static void clear() {
         messageCount = 0;
      }

      public static int getMessageCount() {
         return messageCount;
      }
   }

   protected interface DefaultMqttCallback extends MqttCallback {
      @Override
      default void disconnected(MqttDisconnectResponse disconnectResponse) {
      }

      @Override
      default void mqttErrorOccurred(MqttException exception) {
      }

      @Override
      default void messageArrived(String topic, org.eclipse.paho.mqttv5.common.MqttMessage message) throws Exception {
      }

      @Override
      default void deliveryComplete(IMqttToken token) {
      }

      @Override
      default void connectComplete(boolean reconnect, String serverURI) {
      }

      @Override
      default void authPacketArrived(int reasonCode, MqttProperties properties) {
      }
   }

   protected class LatchedMqttCallback implements DefaultMqttCallback {
      CountDownLatch latch;
      boolean fail;

      public LatchedMqttCallback(CountDownLatch latch) {
         this.latch = latch;
         this.fail = false;
      }

      public LatchedMqttCallback(CountDownLatch latch, boolean fail) {
         this.latch = latch;
         this.fail = fail;
      }

      @Override
      public void messageArrived(String topic, org.eclipse.paho.mqttv5.common.MqttMessage message) throws Exception {
         System.out.println("Message arrived: " + message);
         latch.countDown();
         if (fail) {
            throw new Exception();
         }
      }
   }
}
