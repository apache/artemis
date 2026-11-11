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
package org.apache.activemq.artemis.tests.integration.jms.multiprotocol;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.management.MBeanServer;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MultiprotocolJMSClientTestSupport extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected static LinkedList<Connection> jmsConnections = new LinkedList<>();

   protected static final int PORT = 5672;

   protected static final String BROKER_NAME = "localhost";
   protected static final String NETTY_ACCEPTOR = "netty-acceptor";

   protected String noprivUser = "noprivs";
   protected String noprivPass = "noprivs";

   protected String browseUser = "browser";
   protected String browsePass = "browser";

   protected String guestUser = "guest";
   protected String guestPass = "guest";

   protected String fullUser = "user";
   protected String fullPass = "pass";

   protected ActiveMQServer server;

   protected MBeanServer mBeanServer = createMBeanServer();

   protected ConnectionSupplier AMQPConnection = () -> createConnection();
   protected ConnectionSupplier CoreConnection = () -> createCoreConnection();
   protected ConnectionSupplier OpenWireConnection = () -> createOpenWireConnection();

   enum Protocol {
      AMQP, CORE, OPENWIRE
   }

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      server = createServer();
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      try {
         for (Connection connection : jmsConnections) {
            try {
               connection.close();
            } catch (Throwable ignored) {
               ignored.printStackTrace();
            }
         }
      } catch (Exception e) {
         logger.warn("Exception during tearDown", e);
      }
      jmsConnections.clear();

      try {
         if (server != null) {
            server.stop();
         }
      } finally {
         super.tearDown();
      }
   }

   protected boolean isAutoCreateQueues() {
      return true;
   }

   protected boolean isAutoCreateAddresses() {
      return true;
   }

   protected boolean isSecurityEnabled() {
      return false;
   }

   protected String getDeadLetterAddress() {
      return "ActiveMQ.DLQ";
   }

   protected ActiveMQServer createServer() throws Exception {
      return createServer(PORT);
   }

   protected ActiveMQServer createServer(int port) throws Exception {
      final ActiveMQServer server = this.createServer(true, true);

      server.getConfiguration().getAcceptorConfigurations().clear();
      server.getConfiguration().getAcceptorConfigurations().add(addAcceptorConfiguration(server, port));
      server.getConfiguration().setName(BROKER_NAME);
      server.getConfiguration().setJournalDirectory(server.getConfiguration().getJournalDirectory() + port);
      server.getConfiguration().setBindingsDirectory(server.getConfiguration().getBindingsDirectory() + port);
      server.getConfiguration().setPagingDirectory(server.getConfiguration().getPagingDirectory() + port);
      if (port == PORT) {
         // we use the default large directory if the default port
         // as some tests will assert number of files
         server.getConfiguration().setLargeMessagesDirectory(server.getConfiguration().getLargeMessagesDirectory());
      } else {
         server.getConfiguration().setLargeMessagesDirectory(server.getConfiguration().getLargeMessagesDirectory() + port);
      }
      server.getConfiguration().setJMXManagementEnabled(true);
      server.getConfiguration().setMessageExpiryScanPeriod(100);
      server.setMBeanServer(mBeanServer);

      // Add any additional Acceptors needed for tests
      addAdditionalAcceptors(server);

      // Address configuration
      configureAddressPolicy(server);

      // Add optional security for tests that need it
      configureBrokerSecurity(server);

      // Add extra configuration
      addConfiguration(server);

      server.start();

      // Prepare all addresses and queues for client tests.
      createAddressAndQueues(server);

      return server;
   }

   protected void addConfiguration(ActiveMQServer server) throws Exception {

   }

   protected TransportConfiguration addAcceptorConfiguration(ActiveMQServer server, int port) {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.PORT_PROP_NAME, String.valueOf(port));
      params.put(TransportConstants.PROTOCOLS_PROP_NAME, getConfiguredProtocols());
      Map<String, Object> amqpParams = new HashMap<>();
      configureAMQPAcceptorParameters(amqpParams);
      TransportConfiguration tc = new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params, NETTY_ACCEPTOR, amqpParams);
      return tc;
   }

   protected String getConfiguredProtocols() {
      return Arrays.stream(Protocol.values()).map(Enum::name).collect(Collectors.joining(","));
   }

   protected void configureAddressPolicy(ActiveMQServer server) {
      // Address configuration
      AddressSettings addressSettings = new AddressSettings();

      addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      addressSettings.setAutoCreateQueues(isAutoCreateQueues());
      addressSettings.setAutoCreateAddresses(isAutoCreateAddresses());
      addressSettings.setDeadLetterAddress(SimpleString.of(getDeadLetterAddress()));
      addressSettings.setExpiryAddress(SimpleString.of(getDeadLetterAddress()));

      server.getConfiguration().getAddressSettings().put("#", addressSettings);
      Set<TransportConfiguration> acceptors = server.getConfiguration().getAcceptorConfigurations();
      for (TransportConfiguration tc : acceptors) {
         if (tc.getName().equals(NETTY_ACCEPTOR)) {
            tc.getExtraParams().put("anycastPrefix", "anycast://");
            tc.getExtraParams().put("multicastPrefix", "multicast://");
         }
      }
   }

   protected void createAddressAndQueues(ActiveMQServer server) throws Exception {
      // None by default
   }

   protected void addAdditionalAcceptors(ActiveMQServer server) throws Exception {
      // None by default
   }

   protected void configureBrokerSecurity(ActiveMQServer server) {
      if (isSecurityEnabled()) {
         enableSecurity(server, "#");
      } else {
         server.getConfiguration().setSecurityEnabled(false);
      }
   }

   protected void enableSecurity(ActiveMQServer server, String... securityMatches) {
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

      for (String match : securityMatches) {
         securityRepository.addMatch(match, value);
      }

      server.getConfiguration().setSecurityEnabled(true);
   }

   protected void configureAMQPAcceptorParameters(Map<String, Object> params) {
      // None by default
   }

   public AddressInfo getProxyToAddress(String addressName) {
      return server.getAddressInfo(SimpleString.of(addressName));
   }

   public String getTopicName() {
      return getName() + "-Topic";
   }

   public String getQueueName() {
      return getName();
   }

   public Queue getProxyToQueue(String queueName) {
      return server.locateQueue(SimpleString.of(queueName));
   }

   private Connection trackJMSConnection(Connection connection) {
      jmsConnections.add(connection);

      return connection;
   }

   protected String getJmsConnectionURIOptions() {
      return "";
   }

   protected String getBrokerQpidJMSConnectionString() {
      return getBrokerQpidJMSConnectionString(PORT);
   }

   protected String getBrokerQpidJMSConnectionString(int port) {
      try {
         String uri = "amqp://127.0.0.1:" + port;

         if (!getJmsConnectionURIOptions().isEmpty()) {
            uri = uri + "?" + getJmsConnectionURIOptions();
         }

         return uri;
      } catch (Exception e) {
         throw new RuntimeException();
      }
   }

   protected URI getBrokerQpidJMSConnectionURI() {
      return getBrokerQpidJMSConnectionURI(PORT);
   }

   protected URI getBrokerQpidJMSConnectionURI(int port) {
      try {
         return new URI(getBrokerQpidJMSConnectionString(port));
      } catch (Exception e) {
         throw new RuntimeException();
      }
   }

   protected URI getBrokerQpidJMSFailoverConnectionURI() {
      try {
         return new URI("failover:(" + getBrokerQpidJMSConnectionString() + ")");
      } catch (Exception e) {
         throw new RuntimeException();
      }
   }

   protected Connection createConnection() throws JMSException {
      return createConnection(getBrokerQpidJMSConnectionURI(), null, null, null, true);
   }

   protected Connection createConnection(int port) throws JMSException {
      return createConnection(getBrokerQpidJMSConnectionURI(port), null, null, null, true);
   }

   protected Connection createFailoverConnection() throws JMSException {
      return createConnection(getBrokerQpidJMSFailoverConnectionURI(), null, null, null, true);
   }

   protected Connection createConnection(boolean start) throws JMSException {
      return createConnection(getBrokerQpidJMSConnectionURI(), null, null, null, start);
   }

   protected Connection createConnection(String clientId) throws JMSException {
      return createConnection(getBrokerQpidJMSConnectionURI(), null, null, clientId, true);
   }

   protected Connection createConnection(String username, String password) throws JMSException {
      return createConnection(getBrokerQpidJMSConnectionURI(), username, password, null, true);
   }

   protected Connection createConnection(URI remoteURI,
                                                String username,
                                                String password,
                                                String clientId,
                                                boolean start) throws JMSException {
      JmsConnectionFactory factory = new JmsConnectionFactory(remoteURI);

      Connection connection = trackJMSConnection(factory.createConnection(username, password));

      connection.setExceptionListener(exception -> exception.printStackTrace());

      if (clientId != null && !clientId.isEmpty()) {
         connection.setClientID(clientId);
      }

      if (start) {
         connection.start();
      }

      return connection;
   }

   protected String getBrokerCoreJMSConnectionString() {
      return getBrokerCoreJMSConnectionString(PORT);
   }

   protected String getBrokerCoreJMSConnectionString(int port) {
      try {
         String uri = "tcp://127.0.0.1:" + port;

         if (!getJmsConnectionURIOptions().isEmpty()) {
            uri = uri + "?" + getJmsConnectionURIOptions();
         }

         return uri;
      } catch (Exception e) {
         throw new RuntimeException();
      }
   }

   protected Connection createCoreConnection() throws JMSException {
      return createCoreConnection(PORT);
   }

   protected Connection createCoreConnection(int port) throws JMSException {
      return createCoreConnection(getBrokerCoreJMSConnectionString(port), null, null, null, true);
   }

   protected Connection createCoreConnection(boolean start) throws JMSException {
      return createCoreConnection(getBrokerCoreJMSConnectionString(), null, null, null, start);
   }
   protected Connection createCoreConnection(String clientId) throws JMSException {
      return createCoreConnection(getBrokerCoreJMSConnectionString(), null, null, clientId, true);
   }

   protected Connection createCoreConnection(String username, String password) throws JMSException {
      return createCoreConnection(getBrokerCoreJMSConnectionString(), username, password, null, true);
   }

   protected Connection createCoreConnection(String connectionString,
                                             String username,
                                             String password,
                                             String clientId,
                                             boolean start) throws JMSException {
      ActiveMQJMSConnectionFactory factory = new ActiveMQJMSConnectionFactory(connectionString);

      Connection connection = trackJMSConnection(factory.createConnection(username, password));

      connection.setExceptionListener(exception -> exception.printStackTrace());

      if (clientId != null && !clientId.isEmpty()) {
         connection.setClientID(clientId);
      }

      if (start) {
         connection.start();
      }

      return connection;
   }

   protected String getBrokerOpenWireJMSConnectionString() {
      return getBrokerOpenWireJMSConnectionString(PORT);
   }

   protected String getBrokerOpenWireJMSConnectionString(int port) {
      try {
         String uri = "tcp://127.0.0.1:" + port;

         if (!getJmsConnectionURIOptions().isEmpty()) {
            uri = uri + "?" + getJmsConnectionURIOptions();
         } else {
            uri = uri + "?wireFormat.cacheEnabled=true";
         }

         return uri;
      } catch (Exception e) {
         throw new RuntimeException();
      }
   }

   protected Connection createOpenWireConnection() throws JMSException {
      return createOpenWireConnection(getBrokerOpenWireJMSConnectionString(), null, null, null, true);
   }

   protected Connection createOpenWireConnection(int port) throws JMSException {
      return createOpenWireConnection(getBrokerOpenWireJMSConnectionString(port), null, null, null, true);
   }

   protected Connection createOpenWireConnection(boolean start) throws JMSException {
      return createOpenWireConnection(getBrokerOpenWireJMSConnectionString(), null, null, null, start);
   }

   protected Connection createOpenWireConnection(String clientId) throws JMSException {
      return createOpenWireConnection(getBrokerOpenWireJMSConnectionString(), null, null, clientId, true);
   }

   protected Connection createOpenWireConnection(String username, String password) throws JMSException {
      return createOpenWireConnection(getBrokerOpenWireJMSConnectionString(), username, password, null, true);
   }

   protected Connection createOpenWireConnection(String connectionString,
                                                 String username,
                                                 String password,
                                                 String clientId,
                                                 boolean start) throws JMSException {
      return createOpenWireConnection(connectionString, username, password, clientId, start, true);
   }

   protected Connection createOpenWireConnection(String connectionString,
                                                 String username,
                                                 String password,
                                                 String clientId,
                                                 boolean start,
                                                 boolean watchAdvisories) throws JMSException {
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(connectionString);
      if (!watchAdvisories) {
         factory.setWatchTopicAdvisories(false);
      }
      Connection connection = trackJMSConnection(factory.createConnection(username, password));

      connection.setExceptionListener(exception -> exception.printStackTrace());

      if (clientId != null && !clientId.isEmpty()) {
         connection.setClientID(clientId);
      }

      if (start) {
         connection.start();
      }

      return connection;
   }

   public interface ConnectionSupplier {
      Connection createConnection() throws JMSException;
   }

   public interface SecureConnectionSupplier {
      Connection createConnection(String username, String Password) throws JMSException;
   }
}
