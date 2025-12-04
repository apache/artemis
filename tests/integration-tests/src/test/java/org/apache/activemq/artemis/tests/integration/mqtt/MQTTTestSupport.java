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
package org.apache.activemq.artemis.tests.integration.mqtt;

import javax.jms.ConnectionFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.security.ProtectionDomain;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTProtocolManager;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTSessionState;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.core.remoting.impl.AbstractAcceptor;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.fusesource.hawtdispatch.DispatchPriority;
import org.fusesource.hawtdispatch.internal.DispatcherConfig;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.Tracer;
import org.fusesource.mqtt.codec.MQTTFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import static java.util.Collections.singletonList;
import static org.apache.activemq.artemis.core.protocol.mqtt.MQTTProtocolManagerFactory.MQTT_PROTOCOL_NAME;

public class MQTTTestSupport extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   protected ActiveMQServer server;

   static {
      DispatcherConfig.getDefaultDispatcher().getThreadQueues(DispatchPriority.DEFAULT);
   }

   protected int port = 1883;
   protected ConnectionFactory cf;
   protected LinkedList<Throwable> exceptions = new LinkedList<>();
   protected boolean persistent;
   protected String protocolConfig;
   protected String protocolScheme;
   protected boolean useSSL;

   protected static final int NUM_MESSAGES = 250;

   public static final int AT_MOST_ONCE = 0;
   public static final int AT_LEAST_ONCE = 1;
   public static final int EXACTLY_ONCE = 2;

   protected String noprivUser = "noprivs";
   protected String noprivPass = "noprivs";

   protected String browseUser = "browser";
   protected String browsePass = "browser";

   protected String guestUser = "guest";
   protected String guestPass = "guest";

   protected String fullUser = "user";
   protected String fullPass = "pass";

   public MQTTTestSupport() {
      this.protocolScheme = "mqtt";
      this.useSSL = false;
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
      super.setUp();
      String basedir = basedir().getPath();
      System.setProperty("javax.net.ssl.trustStore", basedir + "/src/test/resources/client.keystore");
      System.setProperty("javax.net.ssl.trustStorePassword", "password");
      System.setProperty("javax.net.ssl.trustStoreType", "jks");
      System.setProperty("javax.net.ssl.keyStore", basedir + "/src/test/resources/server.keystore");
      System.setProperty("javax.net.ssl.keyStorePassword", "password");
      System.setProperty("javax.net.ssl.keyStoreType", "jks");

      exceptions.clear();
      startBroker();
      createJMSConnection();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      System.clearProperty("javax.net.ssl.trustStore");
      System.clearProperty("javax.net.ssl.trustStorePassword");
      System.clearProperty("javax.net.ssl.trustStoreType");
      System.clearProperty("javax.net.ssl.keyStore");
      System.clearProperty("javax.net.ssl.keyStorePassword");
      System.clearProperty("javax.net.ssl.keyStoreType");
      stopBroker();
      super.tearDown();
   }

   public void configureBroker() throws Exception {
      // TODO Add SSL
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
         securityManager.getConfiguration().addUser(browseUser, browsePass);
         securityManager.getConfiguration().addRole(browseUser, "browser");
         securityManager.getConfiguration().addUser(guestUser, guestPass);
         securityManager.getConfiguration().addRole(guestUser, "guest");
         securityManager.getConfiguration().addUser(fullUser, fullPass);
         securityManager.getConfiguration().addRole(fullUser, "full");

         // Configure roles
         HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
         Set<Role> value = new HashSet<>();
         value.add(new Role("nothing", false, false, false, false, false, false, false, false, false, false, false, false));
         value.add(new Role("browser", false, false, false, false, false, false, false, true, false, false, false, false));
         value.add(new Role("guest", false, true, false, false, false, false, false, true, false, false, false, false));
         value.add(new Role("full", true, true, true, true, true, true, true, true, true, true, false, false));
         securityRepository.addMatch(MQTTUtil.getCoreAddressFromMqttTopic(getQueueName(), server.getConfiguration().getWildcardConfiguration()), value);

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
      addressSettings.setDeadLetterAddress(SimpleString.of("DLA"));
      addressSettings.setExpiryAddress(SimpleString.of("EXPIRY"));
      defaultConfig.getAddressSettings().put("#", addressSettings);
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

      server.getConfiguration().addAcceptorConfiguration("MQTT", "tcp://localhost:" + port + "?protocols=MQTT;anycastPrefix=anycast:;multicastPrefix=multicast:");

      logger.debug("Added MQTT connector to broker");
   }

   public void stopBroker() throws Exception {
      if (server.isStarted()) {
         server.stop();
         server = null;
      }
   }

   protected String getQueueName() {
      return getClass().getName() + "." + name;
   }

   protected String getTopicName() {
      return getClass().getName() + "." + name;
   }

   /**
    * Initialize an MQTTClientProvider instance.  By default this method uses the port that's assigned to be the TCP
    * based port using the base version of addMQTTConnector.  A subclass can either change the value of port or override
    * this method to assign the correct port.
    *
    * @param provider the MQTTClientProvider instance to initialize.
    * @throws Exception if an error occurs during initialization.
    */
   protected void initializeConnection(MQTTClientProvider provider) throws Exception {
      if (!isUseSSL()) {
         provider.connect("tcp://localhost:" + port);
      } else {
         SSLContext ctx = SSLContext.getInstance("TLS");
         ctx.init(new KeyManager[0], new TrustManager[]{new DefaultTrustManager()}, new SecureRandom());
         provider.setSslContext(ctx);
         provider.connect("ssl://localhost:" + port);
      }
   }

   public String getProtocolScheme() {
      return protocolScheme;
   }

   public void setProtocolScheme(String scheme) {
      this.protocolScheme = scheme;
   }

   public boolean isUseSSL() {
      return this.useSSL;
   }

   public void setUseSSL(boolean useSSL) {
      this.useSSL = useSSL;
   }

   public boolean isPersistent() {
      return persistent;
   }

   public int getPort() {
      return this.port;
   }

   public boolean isSchedulerSupportEnabled() {
      return false;
   }

   public boolean isSecurityEnabled() {
      return false;
   }

   protected interface Task {

      void run() throws Exception;
   }

   protected void within(int time, TimeUnit unit, Task task) throws InterruptedException {
      long timeMS = unit.toMillis(time);
      long deadline = System.currentTimeMillis() + timeMS;
      while (true) {
         try {
            task.run();
            return;
         } catch (Throwable e) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
               if (e instanceof RuntimeException runtimeException) {
                  throw runtimeException;
               }
               if (e instanceof Error error) {
                  throw error;
               }
               throw new RuntimeException(e);
            }
            Thread.sleep(Math.min(timeMS / 10, remaining));
         }
      }
   }

   protected MQTTClientProvider getMQTTClientProvider() {
      return new FuseMQTTClientProvider();
   }

   protected MQTT createMQTTConnection() throws Exception {
      MQTT client = createMQTTConnection(null, false);
      client.setVersion("3.1.1");
      return client;
   }

   protected MQTT createMQTTConnection(String clientId, boolean clean) throws Exception {
      if (isUseSSL()) {
         return createMQTTSslConnection(clientId, clean);
      } else {
         return createMQTTTcpConnection(clientId, clean);
      }
   }

   private MQTT createMQTTTcpConnection(String clientId, boolean clean) throws Exception {
      MQTT mqtt = new MQTT();
      mqtt.setConnectAttemptsMax(1);
      mqtt.setReconnectAttemptsMax(0);
      mqtt.setTracer(createTracer());
      mqtt.setVersion("3.1.1");
      if (clientId != null) {
         mqtt.setClientId(clientId);
      }
      mqtt.setCleanSession(clean);
      mqtt.setHost("localhost", port);
      return mqtt;
   }

   protected MqttClient createPaho3_1_1Client(String clientId) throws org.eclipse.paho.client.mqttv3.MqttException {
      return new MqttClient("tcp://localhost:" + port, clientId, new MemoryPersistence());
   }

   public Map<String, MQTTSessionState> getSessions() {
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

   private MQTT createMQTTSslConnection(String clientId, boolean clean) throws Exception {
      MQTT mqtt = new MQTT();
      mqtt.setConnectAttemptsMax(1);
      mqtt.setReconnectAttemptsMax(0);
      mqtt.setTracer(createTracer());
      mqtt.setHost("ssl://localhost:" + port);
      if (clientId != null) {
         mqtt.setClientId(clientId);
      }
      mqtt.setCleanSession(clean);

      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(new KeyManager[0], new TrustManager[]{new DefaultTrustManager()}, new SecureRandom());
      mqtt.setSslContext(ctx);
      return mqtt;
   }

   protected Tracer createTracer() {
      return new Tracer() {
         @Override
         public void onReceive(MQTTFrame frame) {
            logger.debug("Client Received:\n{}", frame);
         }

         @Override
         public void onSend(MQTTFrame frame) {
            logger.debug("Client Sent:\n{}", frame);
         }

         @Override
         public void debug(String message, Object... args) {
            if (logger.isDebugEnabled()) {
               logger.debug(String.format(message, args));
            }
         }
      };
   }

   static class DefaultTrustManager implements X509TrustManager {

      @Override
      public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
         return new X509Certificate[0];
      }
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

   protected interface DefaultMqtt3Callback extends MqttCallback {
      @Override
      default void connectionLost(Throwable cause) {
      }

      @Override
      default void messageArrived(String topic, org.eclipse.paho.client.mqttv3.MqttMessage message) throws Exception {
      }

      @Override
      default void deliveryComplete(IMqttDeliveryToken token) {
      }
   }
}
