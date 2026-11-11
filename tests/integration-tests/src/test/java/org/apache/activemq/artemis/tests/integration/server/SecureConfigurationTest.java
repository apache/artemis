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
package org.apache.activemq.artemis.tests.integration.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.Arrays;
import java.util.Collection;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.JMSSecurityException;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.server.config.impl.FileJMSConfiguration;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class SecureConfigurationTest extends ActiveMQTestBase {

   @Parameters(name = "{index}: protocol={0}")
   public static Collection<Object[]> parameters() {
      return Arrays.asList(new Object[][] {
            {"CORE"}, {"AMQP"}, {"OPENWIRE"}
      });
   }

   @Parameter(index = 0)
   public String protocol;

   ActiveMQServer server;

   @BeforeEach
   public void startSever() throws Exception {
      server = getActiveMQServer("multicast_topic.xml");
      server.start();
   }

   @AfterEach
   public void stopServer() throws Exception {
      try {
         if (server != null) {
            server.stop();
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   @TestTemplate
   public void testSecureSharedDurableSubscriber() throws Exception {
      //This is because OpenWire does not support JMS 2.0
      assumeFalse(protocol.equals("OPENWIRE"));
      ConnectionFactory connectionFactory = getConnectionFactory("b", "b");
      String message = "blah";

      //Expect to be able to create subscriber on pre-defined/existing queue.
      String messageRecieved = sendAndReceiveTextUsingTopic(connectionFactory, null, message, "secured_topic_shared_durable", (t, s) -> s.createSharedDurableConsumer(t, "secured_topic_shared_durable/queue"));
      assertEquals(message, messageRecieved);

      try {
         sendAndReceiveTextUsingTopic(connectionFactory, null, message, "secured_topic_shared_durable", (t, s) -> s.createSharedDurableConsumer(t, "secured_topic_shared_durable/non-existant-queue"));
         fail("Security exception expected, but did not occur, excepetion expected as not permissioned to dynamically create queue");
      } catch (JMSSecurityException j) {
         //Expected exception
      }
   }

   @TestTemplate
   public void testSecureSharedSubscriber() throws Exception {
      //This is because OpenWire does not support JMS 2.0
      assumeFalse(protocol.equals("OPENWIRE"));
      ConnectionFactory connectionFactory = getConnectionFactory("b", "b");
      String message = "blah";

      //Expect to be able to create subscriber on pre-defined/existing queue.
      String messageRecieved = sendAndReceiveTextUsingTopic(connectionFactory, null, message, "secured_topic_shared", (t, s) -> s.createSharedConsumer(t, "secured_topic_shared/queue"));
      assertEquals(message, messageRecieved);

      try {
         sendAndReceiveTextUsingTopic(connectionFactory, null, message, "secured_topic_shared", (t, s) -> s.createSharedConsumer(t, "secured_topic_shared/non-existant-queue"));
         fail("Security exception expected, but did not occur, excepetion expected as not permissioned to dynamically create queue");
      } catch (JMSSecurityException j) {
         //Expected exception
      }
   }

   @TestTemplate
   public void testCreateSecureDurableSubscriber() throws Exception {
      ConnectionFactory connectionFactory = getConnectionFactory("b", "b");
      String message = "blah";

      //Expect to be able to create subscriber on pre-defined/existing queue.
      String messageRecieved = sendAndReceiveTextUsingTopic(connectionFactory, "clientId", message, "secured_topic_durable", (t, s) -> s.createDurableSubscriber(t, "secured_topic_durable/queue"));
      assertEquals(message, messageRecieved);

      try {
         sendAndReceiveTextUsingTopic(connectionFactory, "clientId", message, "secured_topic_durable", (t, s) -> s.createDurableSubscriber(t, "secured_topic_durable/non-existant-queue"));
         fail("Security exception expected, but did not occur, excepetion expected as not permissioned to dynamically create queue");
      } catch (JMSSecurityException j) {
         //Expected exception
      }
   }

   @TestTemplate
   public void testDeleteSecureDurableSubscriber() throws Exception {
      ConnectionFactory connectionFactory = getConnectionFactory("c", "c");
      String message = "blah";

      //Expect to be able to create durable queue for subscription
      String messageRecieved = sendAndReceiveTextUsingTopic(connectionFactory, "clientId", message, "secured_topic_durable", (t, s) -> s.createDurableSubscriber(t, "secured_topic_durable/non-existant-queue"));
      assertEquals(message, messageRecieved);

      try {
         sendAndReceiveTextUsingTopic(connectionFactory, "clientId", message, "secured_topic_durable", (t, s) -> s.createDurableSubscriber(t, "secured_topic_durable/non-existant-queue", "age > 10", false));
         fail("Security exception expected, but did not occur, excepetion expected as not permissioned to dynamically delete queue");
      } catch (JMSSecurityException j) {
         //Expected exception
      }
   }

   @TestTemplate
   public void testTemporaryQueue() throws Exception {
      ConnectionFactory connectionFactory = getConnectionFactory("a", "a");
      String message = "blah";

      //Expect to be able to create subscriber on pre-defined/existing queue.
      String messageRecieved = sendAndReceiveText(connectionFactory, "clientId", message, s -> s.createTemporaryQueue(), (d, s) -> s.createConsumer(d));
      assertEquals(message, messageRecieved);

      connectionFactory = getConnectionFactory("c", "c");
      try {
         sendAndReceiveText(connectionFactory, "clientId", message, s -> s.createTemporaryQueue(), (d, s) -> s.createConsumer(d));
         fail("Security exception expected, but did not occur, excepetion expected as not permissioned to create a temporary queue");
      } catch (JMSSecurityException jmsse) {
      } catch (JMSException e) {
         e.printStackTrace();
         fail("thrown a JMSEXception instead of a JMSSEcurityException");
      }
   }

   @TestTemplate
   public void testTemporaryTopic() throws Exception {
      ConnectionFactory connectionFactory = getConnectionFactory("a", "a");
      String message = "blah";

      //Expect to be able to create subscriber on pre-defined/existing queue.
      String messageRecieved = sendAndReceiveText(connectionFactory, "clientId", message, s -> s.createTemporaryTopic(), (d, s) -> s.createConsumer(d));
      assertEquals(message, messageRecieved);

      connectionFactory = getConnectionFactory("c", "c");
      try {
         sendAndReceiveText(connectionFactory, "clientId", message, s -> s.createTemporaryTopic(), (d, s) -> s.createConsumer(d));
         fail("Security exception expected, but did not occur, excepetion expected as not permissioned to create a temporary queue");
      } catch (JMSSecurityException jmsse) {
      } catch (JMSException e) {
         e.printStackTrace();
         fail("thrown a JMSEXception instead of a JMSSEcurityException");
      }
   }

   @TestTemplate
   public void testSecureQueue() throws Exception {
      ConnectionFactory connectionFactory = getConnectionFactory("b", "b");
      String message = "blah";

      //Expect to be able to create subscriber on pre-defined/existing queue.
      String messageRecieved = sendAndReceiveTextUsingQueue(connectionFactory, "clientId", message, "secured_queue", (q, s) -> s.createConsumer(q));
      assertEquals(message, messageRecieved);

      connectionFactory = getConnectionFactory("a", "a");
      messageRecieved = sendAndReceiveTextUsingQueue(connectionFactory, "clientId", message, "new-queue-1", (q, s) -> s.createConsumer(q));
      assertEquals(message, messageRecieved);

      connectionFactory = getConnectionFactory("b", "b");
      try {
         sendAndReceiveTextUsingQueue(connectionFactory, "clientId", message, "new-queue-2", (q, s) -> s.createConsumer(q));
         fail("Security exception expected, but did not occur, excepetion expected as not permissioned to dynamically create address, or queue");
      } catch (JMSSecurityException j) {
         //Expected exception
      }

      connectionFactory = getConnectionFactory("a", "a");
      messageRecieved = sendAndReceiveTextUsingQueue(connectionFactory, "clientId", message, "new-queue-2", (q, s) -> s.createConsumer(q));
      assertEquals(message, messageRecieved);

   }


   private ConnectionFactory getConnectionFactory(String user, String password) {
      return switch (protocol) {
         case "CORE" -> getActiveMQConnectionFactory(password, user);
         case "AMQP" -> getAMQPConnectionFactory(password, user);
         case "OPENWIRE" -> getOpenWireConnectionFactory(password, user);
         default -> throw new IllegalStateException("Unsupported Protocol");
      };
   }

   private ActiveMQConnectionFactory getActiveMQConnectionFactory(String user, String password) {
      ActiveMQConnectionFactory activeMQConnection = new ActiveMQConnectionFactory("tcp://localhost:61616");
      activeMQConnection.setUser(user);
      activeMQConnection.setPassword(password);
      return activeMQConnection;
   }

   private JmsConnectionFactory getAMQPConnectionFactory(String user, String password) {
      JmsConnectionFactory jmsConnectionFactory = new JmsConnectionFactory("amqp://localhost:61616");
      jmsConnectionFactory.setUsername(user);
      jmsConnectionFactory.setPassword(password);
      return jmsConnectionFactory;
   }

   private org.apache.activemq.ActiveMQConnectionFactory getOpenWireConnectionFactory(String user, String password) {
      org.apache.activemq.ActiveMQConnectionFactory activeMQConnectionFactory = new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616");
      activeMQConnectionFactory.setUserName(user);
      activeMQConnectionFactory.setPassword(password);
      // don't listen for advisories to avoid the need for advisory permissions
      activeMQConnectionFactory.setWatchTopicAdvisories(false);
      return activeMQConnectionFactory;
   }

   private String sendAndReceiveTextUsingTopic(ConnectionFactory connectionFactory, String clientId, String message, String topicName, ConsumerSupplier<Topic> consumerSupplier) throws JMSException {
      return sendAndReceiveText(connectionFactory, clientId, message, s -> s.createTopic(topicName), consumerSupplier);
   }

   private String sendAndReceiveTextUsingQueue(ConnectionFactory connectionFactory, String clientId, String message, String queueName, ConsumerSupplier<Queue> consumerSupplier) throws JMSException {
      return sendAndReceiveText(connectionFactory, clientId, message, s -> s.createQueue(queueName), consumerSupplier);
   }

   private <D extends Destination> String sendAndReceiveText(ConnectionFactory connectionFactory, String clientId, String message, DestinationSupplier<D> destinationSupplier, ConsumerSupplier<D> consumerSupplier) throws JMSException {
      String messageRecieved;
      Connection connection = null;
      try {
         connection = connectionFactory.createConnection();
         if (clientId != null && !clientId.isEmpty()) {
            connection.setClientID(clientId);
         }
         connection.start();
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            D destination = destinationSupplier.create(session);
            MessageConsumer messageConsumer = consumerSupplier.create(destination, session);
            assertNull(messageConsumer.receiveNoWait());

            TextMessage messageToSend = session.createTextMessage(message);
            session.createProducer(destination).send(messageToSend);

            TextMessage received = (TextMessage) messageConsumer.receive(100);
            messageRecieved = received != null ? received.getText() : null;
         }
      } catch (JMSException | JMSRuntimeException e) {
         // Exception Should not be fatal
         assertNotNull(connection.createSession(false, Session.AUTO_ACKNOWLEDGE));
         throw e;
      } finally {
         connection.close();
      }
      return messageRecieved;
   }

   protected ActiveMQServer getActiveMQServer(String brokerConfig) throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileJMSConfiguration fileConfiguration = new FileJMSConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager(brokerConfig);
      deploymentManager.addDeployable(fc);
      deploymentManager.addDeployable(fileConfiguration);
      deploymentManager.readConfiguration();

      SecurityConfiguration securityConfiguration = new SecurityConfiguration();
      securityConfiguration.addUser("a", "a");
      securityConfiguration.addRole("a", "a");

      securityConfiguration.addUser("b", "b");
      securityConfiguration.addRole("b", "b");

      securityConfiguration.addUser("c", "c");
      securityConfiguration.addRole("c", "c");

      ActiveMQJAASSecurityManager sm = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), securityConfiguration);

      return addServer(new ActiveMQServerImpl(fc, sm));
   }

   private interface ConsumerSupplier<D extends Destination> {
      MessageConsumer create(D destination, Session session) throws JMSException;
   }

   private interface DestinationSupplier<D extends Destination> {
      D create(Session session) throws JMSException;
   }

}
