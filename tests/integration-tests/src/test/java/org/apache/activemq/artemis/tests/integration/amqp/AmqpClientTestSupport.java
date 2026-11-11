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

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.DeleteOnClose;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.messaging.TerminusDurability;
import org.apache.qpid.proton.amqp.messaging.TerminusExpiryPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.apache.activemq.transport.amqp.AmqpSupport.LIFETIME_POLICY;
import static org.apache.activemq.transport.amqp.AmqpSupport.TEMP_QUEUE_CAPABILITY;
import static org.apache.activemq.transport.amqp.AmqpSupport.TEMP_TOPIC_CAPABILITY;

/**
 * Test support class for tests that will be using the AMQP Proton wrapper client. This is to make it easier to migrate
 * tests from ActiveMQ5
 */
public class AmqpClientTestSupport extends AmqpTestSupport {

   protected static final Symbol SHARED = Symbol.getSymbol("shared");
   protected static final Symbol GLOBAL = Symbol.getSymbol("global");

   protected ActiveMQServer server;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      server = createServer();
   }

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
      connections.clear();

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

   protected int getPrecreatedQueueSize() {
      return 10;
   }

   public URI getBrokerOpenWireConnectionURI() {
      try {
         String uri = null;

         if (isUseSSL()) {
            uri = "ssl://127.0.0.1:" + AMQP_PORT;
         } else {
            uri = "tcp://127.0.0.1:" + AMQP_PORT;
         }

         return new URI(uri);
      } catch (Exception e) {
         throw new RuntimeException();
      }
   }

   protected ActiveMQServer createServer() throws Exception {
      return createServer(AMQP_PORT);
   }

   protected ActiveMQServer createServer(int port) throws Exception {
      return createServer(port, true);
   }

   @Override
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
            tc.getExtraParams().put("anycastPrefix", ANYCAST_PREFIX);
            tc.getExtraParams().put("multicastPrefix", MULTICAST_PREFIX);
         }
      }
   }

   @Override
   protected void createAddressAndQueues(ActiveMQServer server) throws Exception {
      // Default Queue
      server.addAddressInfo(new AddressInfo(SimpleString.of(getQueueName()), RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(getQueueName()).setRoutingType(RoutingType.ANYCAST));

      // Default DLQ
      server.addAddressInfo(new AddressInfo(SimpleString.of(getDeadLetterAddress()), RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(getDeadLetterAddress()).setRoutingType(RoutingType.ANYCAST));

      // Default Topic
      server.addAddressInfo(new AddressInfo(SimpleString.of(getTopicName()), RoutingType.MULTICAST));
      server.createQueue(QueueConfiguration.of(getTopicName()));

      // Additional Test Queues
      for (int i = 0; i < getPrecreatedQueueSize(); ++i) {
         server.addAddressInfo(new AddressInfo(SimpleString.of(getQueueName(i)), RoutingType.ANYCAST));
         server.createQueue(QueueConfiguration.of(getQueueName(i)).setRoutingType(RoutingType.ANYCAST));
      }
   }

   @Override
   protected void configureBrokerSecurity(ActiveMQServer server) {
      if (isSecurityEnabled()) {
         enableSecurity(server);
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
      securityRepository.addMatch(getQueueName(), value);

      for (String match : securityMatches) {
         securityRepository.addMatch(match, value);
      }

      server.getConfiguration().setSecurityEnabled(true);
   }

   public Queue getProxyToQueue(String queueName) {
      return server.locateQueue(SimpleString.of(queueName));
   }

   public AddressInfo getProxyToAddress(String addressName) {
      return server.getAddressInfo(SimpleString.of(addressName));
   }

   public String getTestName() {
      return getName();
   }

   public String getTopicName() {
      return getName() + "-Topic";
   }

   public String getQueueName() {
      return getName();
   }

   public String getQueueName(int index) {
      return getName() + "-" + index;
   }

   protected void sendMessages(String destinationName, int count) throws Exception {
      sendMessages(destinationName, count, null);
   }

   protected void sendMessages(String destinationName, int count, RoutingType routingType) throws Exception {
      sendMessages(destinationName, count, routingType, false);
   }

   protected void sendMessages(String destinationName,
                               int count,
                               RoutingType routingType,
                               boolean durable) throws Exception {
      sendMessages(destinationName, count, routingType, durable, Collections.emptyMap());
   }

   protected void setData(AmqpMessage amqpMessage) throws Exception {
   }

   protected void sendMessages(String destinationName,
                               int count,
                               RoutingType routingType,
                               boolean durable,
                               Map<String, Object> applicationProperties) throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(destinationName);

         for (int i = 0; i < count; ++i) {
            AmqpMessage message = new AmqpMessage();
            for (Map.Entry<String, Object> entry : applicationProperties.entrySet()) {
               message.setApplicationProperty(entry.getKey(), entry.getValue());
            }
            message.setMessageId("MessageID:" + i);
            message.setDurable(durable);
            if (routingType != null) {
               message.setMessageAnnotation(AMQPMessageSupport.ROUTING_TYPE.toString(), routingType.getType());
            }
            setData(message);
            sender.send(message);
         }
      } finally {
         connection.close();
      }
   }

   protected void sendMessages(String destinationName, int count, boolean durable) throws Exception {
      sendMessages(destinationName, count, durable, null, null);
   }

   protected void sendMessages(String destinationName, int count, boolean durable, byte[] payload) throws Exception {
      sendMessages(destinationName, count, durable, payload, null);
   }

   protected void sendMessages(String destinationName, int count, boolean durable, byte[] payload, Map<String, Object> properties) throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(destinationName);

         for (int i = 0; i < count; ++i) {
            AmqpMessage message = new AmqpMessage();
            message.setMessageId("MessageID:" + i);
            message.setDurable(durable);
            if (payload != null) {
               message.setBytes(payload);
            }
            if (properties != null) {
               properties.forEach((a, b) -> message.setApplicationProperty(a, b));
            }
            sender.send(message);
         }
      } finally {
         connection.close();
      }
   }

   protected void sendMessagesCore(String destinationName, int count, boolean durable) throws Exception {
      sendMessagesCore(destinationName, count, durable, null);
   }

   protected void sendMessagesCore(String destinationName, int count, boolean durable, byte[] body) throws Exception {
      ServerLocator serverLocator = ActiveMQClient.createServerLocator("tcp://127.0.0.1:5672");
      ClientSessionFactory clientSessionFactory = serverLocator.createSessionFactory();
      ClientSession session = clientSessionFactory.createSession();
      try {
         ClientProducer sender = session.createProducer(destinationName);

         for (int i = 0; i < count; ++i) {
            ClientMessage message = session.createMessage(durable);
            if (body != null) {
               message.getBodyBuffer().writeBytes(body);
            }
            sender.send(message);
         }
      } finally {
         session.close();
      }
   }

   protected void sendMessagesOpenWire(String destinationName, int count, boolean durable) throws Exception {
      sendMessagesOpenWire(destinationName, count, durable, null);
   }

   protected void sendMessagesOpenWire(String destinationName,
                                       int count,
                                       boolean durable,
                                       byte[] payload) throws Exception {
      ConnectionFactory cf = new ActiveMQConnectionFactory("tcp://127.0.0.1:5672");
      Connection connection = cf.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      try {
         MessageProducer producer = session.createProducer(session.createQueue(destinationName));
         if (durable) {
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
         } else {
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         }

         for (int i = 0; i < count; ++i) {
            BytesMessage message = session.createBytesMessage();
            if (payload != null) {
               message.writeBytes(payload);
            }
            producer.send(message);
         }
      } finally {
         connection.close();
      }
   }

   protected Source createDynamicSource(boolean topic) {

      Source source = new Source();
      source.setDynamic(true);
      source.setDurable(TerminusDurability.NONE);
      source.setExpiryPolicy(TerminusExpiryPolicy.LINK_DETACH);

      // Set the dynamic node lifetime-policy
      Map<Symbol, Object> dynamicNodeProperties = new HashMap<>();
      dynamicNodeProperties.put(LIFETIME_POLICY, DeleteOnClose.getInstance());
      source.setDynamicNodeProperties(dynamicNodeProperties);

      // Set the capability to indicate the node type being created
      if (!topic) {
         source.setCapabilities(TEMP_QUEUE_CAPABILITY);
      } else {
         source.setCapabilities(TEMP_TOPIC_CAPABILITY);
      }

      return source;
   }

   protected Target createDynamicTarget(boolean topic) {

      Target target = new Target();
      target.setDynamic(true);
      target.setDurable(TerminusDurability.NONE);
      target.setExpiryPolicy(TerminusExpiryPolicy.LINK_DETACH);

      // Set the dynamic node lifetime-policy
      Map<Symbol, Object> dynamicNodeProperties = new HashMap<>();
      dynamicNodeProperties.put(LIFETIME_POLICY, DeleteOnClose.getInstance());
      target.setDynamicNodeProperties(dynamicNodeProperties);

      // Set the capability to indicate the node type being created
      if (!topic) {
         target.setCapabilities(TEMP_QUEUE_CAPABILITY);
      } else {
         target.setCapabilities(TEMP_TOPIC_CAPABILITY);
      }

      return target;
   }
}
