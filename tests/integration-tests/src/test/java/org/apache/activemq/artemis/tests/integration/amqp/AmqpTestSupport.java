/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.amqp;

import javax.management.MBeanServer;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.junit.jupiter.api.AfterEach;

/**
 * Base test support class providing client support methods to aid in creating and configuration the AMQP test client.
 */
public class AmqpTestSupport extends ActiveMQTestBase {

   protected static final String BROKER_NAME = "localhost";
   protected static final String NETTY_ACCEPTOR = "netty-acceptor";

   protected static final String MULTICAST_PREFIX = "multicast://";
   protected static final String ANYCAST_PREFIX = "anycast://";

   protected String noprivUser = "noprivs";
   protected String noprivPass = "noprivs";

   protected String browseUser = "browser";
   protected String browsePass = "browser";

   protected String guestUser = "guest";
   protected String guestPass = "guest";

   protected String fullUser = "user";
   protected String fullPass = "pass";

   protected static final int AMQP_PORT = 5672;

   protected LinkedList<AmqpConnection> connections = new LinkedList<>();

   protected boolean useSSL;
   protected boolean useWebSockets;

   protected AmqpConnection addConnection(AmqpConnection connection) {
      connections.add(connection);
      return connection;
   }

   protected MBeanServer mBeanServer = createMBeanServer();

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      for (AmqpConnection conn : connections) {
         try {
            conn.close();
         } catch (Throwable ignored) {
            ignored.printStackTrace();
         }
      }

      super.tearDown();
   }

   public boolean isUseSSL() {
      return useSSL;
   }

   public boolean isUseWebSockets() {
      return useWebSockets;
   }

   public String getAmqpConnectionURIOptions() {
      return "";
   }

   public URI getBrokerAmqpConnectionURI() {
      boolean webSocket = isUseWebSockets();

      try {
         int port = AMQP_PORT;

         String uri = null;

         if (isUseSSL()) {
            if (webSocket) {
               uri = "wss://127.0.0.1:" + port;
            } else {
               uri = "ssl://127.0.0.1:" + port;
            }
         } else {
            if (webSocket) {
               uri = "ws://127.0.0.1:" + port;
            } else {
               uri = "tcp://127.0.0.1:" + port;
            }
         }

         if (!getAmqpConnectionURIOptions().isEmpty()) {
            uri = uri + "?" + getAmqpConnectionURIOptions();
         }

         return new URI(uri);
      } catch (Exception e) {
         throw new RuntimeException();
      }
   }

   public AmqpConnection createAmqpConnection() throws Exception {
      return createAmqpConnection(getBrokerAmqpConnectionURI());
   }

   public AmqpConnection createAmqpConnection(String username, String password) throws Exception {
      return createAmqpConnection(getBrokerAmqpConnectionURI(), username, password);
   }

   public AmqpConnection createAmqpConnection(URI brokerURI) throws Exception {
      return createAmqpConnection(brokerURI, null, null);
   }

   public AmqpConnection createAmqpConnection(URI brokerURI, String username, String password) throws Exception {
      return createAmqpClient(brokerURI, username, password).connect();
   }

   public AmqpClient createAmqpClient() throws Exception {
      return createAmqpClient(getBrokerAmqpConnectionURI(), null, null);
   }

   public AmqpClient createAmqpClient(URI brokerURI) throws Exception {
      return createAmqpClient(brokerURI, null, null);
   }

   public AmqpClient createAmqpClient(String username, String password) throws Exception {
      return createAmqpClient(getBrokerAmqpConnectionURI(), username, password);
   }

   public AmqpClient createAmqpClient(URI brokerURI, String username, String password) throws Exception {
      return new AmqpClient(brokerURI, username, password);
   }

   protected ActiveMQServer createServer(int port, boolean start) throws Exception {

      final ActiveMQServer server = this.createServer(true, true);

      server.getConfiguration().getAcceptorConfigurations().clear();
      server.getConfiguration().getAcceptorConfigurations().add(addAcceptorConfiguration(server, port));
      server.getConfiguration().setName(BROKER_NAME);
      server.getConfiguration().setJournalDirectory(server.getConfiguration().getJournalDirectory() + port);
      server.getConfiguration().setBindingsDirectory(server.getConfiguration().getBindingsDirectory() + port);
      server.getConfiguration().setPagingDirectory(server.getConfiguration().getPagingDirectory() + port);
      if (port == AMQP_PORT) {
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

      if (start) {
         server.start();

         // Prepare all addresses and queues for client tests.
         createAddressAndQueues(server);
      }

      return server;
   }
   protected void createAddressAndQueues(ActiveMQServer server) throws Exception {
   }

   protected void addConfiguration(ActiveMQServer server) {
   }

   protected void addAdditionalAcceptors(ActiveMQServer server) throws Exception {
   }

   protected void configureAddressPolicy(ActiveMQServer server) {
   }

   protected TransportConfiguration addAcceptorConfiguration(ActiveMQServer server, int port) {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.PORT_PROP_NAME, String.valueOf(port));
      params.put(TransportConstants.PROTOCOLS_PROP_NAME, getConfiguredProtocols());
      Map<String, Object> amqpParams = new HashMap<>();
      configureAMQPAcceptorParameters(amqpParams);
      TransportConfiguration tc = new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params, NETTY_ACCEPTOR, amqpParams);
      configureAMQPAcceptorParameters(tc);
      return tc;
   }

   protected String getConfiguredProtocols() {
      return "AMQP,OPENWIRE,CORE";
   }


   protected void configureAMQPAcceptorParameters(Map<String, Object> params) {
   }

   protected void configureAMQPAcceptorParameters(TransportConfiguration tc) {
   }

   protected void configureBrokerSecurity(ActiveMQServer server) {
   }

}
