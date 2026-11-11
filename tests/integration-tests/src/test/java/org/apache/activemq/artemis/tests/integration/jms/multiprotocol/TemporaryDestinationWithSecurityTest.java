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
import javax.jms.JMSSecurityException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(ParameterizedTestExtension.class)
public class TemporaryDestinationWithSecurityTest extends MultiprotocolJMSClientTestSupport {

   private SecureConnectionSupplier connectionSupplier;
   private final String UUID_NAMESPACE = "uuid";
   private String createOnlyUser = "createonly";
   private String createOnlyPass = "createonly";
   private Protocol protocol;

   @Parameters(name = "protocol={0}")
   public static Collection getParameters() {
      return Arrays.asList(new Object[][]{
         {Protocol.AMQP},
         {Protocol.CORE},
         {Protocol.OPENWIRE}
      });
   }

   public TemporaryDestinationWithSecurityTest(Protocol protocol) {
      this.protocol = protocol;
      switch (protocol) {
         case AMQP -> this.connectionSupplier = (username, password) -> createConnection(username, password);
         case CORE -> this.connectionSupplier = (username, password) -> createCoreConnection(username, password);
         case OPENWIRE -> this.connectionSupplier = (username, password) -> createOpenWireConnection(username, password);
      }
   }

   @Override
   protected boolean isSecurityEnabled() {
      return true;
   }

   @Override
   protected void enableSecurity(ActiveMQServer server, String... securityMatches) {
      super.enableSecurity(server, UUID_NAMESPACE + ".#");

      // add a new user/role who can create but not delete
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser(createOnlyUser, createOnlyPass);
      securityManager.getConfiguration().addRole(createOnlyUser, "createonly");
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      Set<Role> value = securityRepository.getMatch(UUID_NAMESPACE + ".#");
      value.add(new Role("createonly", false, false, true, false, true, false, false, false, true, false));
   }

   @Override
   protected void configureAMQPAcceptorParameters(Map<String, Object> params) {
      params.put("supportAdvisory", "false");
   }

   @Override
   protected void addConfiguration(ActiveMQServer server) throws Exception {
      super.addConfiguration(server);
      server.getConfiguration().setUuidNamespace(UUID_NAMESPACE);
   }

   @TestTemplate
   public void testCreateTemporaryQueue() throws Throwable {
      Connection connection = connectionSupplier.createConnection(fullUser, fullPass);

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      TemporaryQueue queue = session.createTemporaryQueue();
      MessageProducer producer = session.createProducer(queue);

      TextMessage message = session.createTextMessage();
      message.setText("Message temporary");
      producer.send(message);

      MessageConsumer consumer = session.createConsumer(queue);
      connection.start();

      message = (TextMessage) consumer.receive(5000);

      assertNotNull(message);
   }

   @TestTemplate
   public void testCreateTemporaryQueueNegative() throws Throwable {
      Connection connection = connectionSupplier.createConnection(noprivUser, noprivPass);

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      try {
         session.createTemporaryQueue();
         fail();
      } catch (JMSSecurityException e) {
         // expected
      }
   }

   @TestTemplate
   public void testDeleteTemporaryQueue() throws Exception {
      Connection connection = connectionSupplier.createConnection(fullUser, fullPass);

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      final TemporaryQueue queue = session.createTemporaryQueue();
      assertNotNull(queue);

      Queue queueView = getProxyToQueue(queue.getQueueName());
      assertNotNull(queueView);

      queue.delete();

      Wait.assertTrue("Temp Queue should be deleted.", () -> getProxyToQueue(queue.getQueueName()) == null, 3000, 10);
   }

   @TestTemplate
   public void testDeleteTemporaryQueueNegative() throws Exception {
      Connection connection = connectionSupplier.createConnection(createOnlyUser, createOnlyPass);

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      final TemporaryQueue queue = session.createTemporaryQueue();
      assertNotNull(queue);

      Queue queueView = getProxyToQueue(queue.getQueueName());
      assertNotNull(queueView);

      try {
         queue.delete();
         // Qpid JMS doesn't throw an error here even though the queue is not deleted
         if (protocol != Protocol.AMQP) {
            fail();
         }
      } catch (JMSSecurityException e) {
         // expected
      }

      connection.close();

      Wait.assertTrue(() -> getProxyToAddress(queue.getQueueName()) == null, 3000, 10);
   }

   @TestTemplate
   public void testCreateTemporaryTopic() throws Throwable {
      Connection connection = connectionSupplier.createConnection(fullUser, fullPass);

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      TemporaryTopic topic = session.createTemporaryTopic();

      MessageConsumer consumer = session.createConsumer(topic);
      MessageProducer producer = session.createProducer(topic);

      TextMessage message = session.createTextMessage();
      message.setText("Message temporary");
      producer.send(message);

      connection.start();
      message = (TextMessage) consumer.receive(5000);
      assertNotNull(message);
   }

   @TestTemplate
   public void testCreateTemporaryTopicNegative() throws Throwable {
      Connection connection = connectionSupplier.createConnection(noprivUser, noprivPass);

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      try {
         session.createTemporaryTopic();
         fail();
      } catch (JMSSecurityException e) {
         // expected
      }
   }

   @TestTemplate
   public void testDeleteTemporaryTopic() throws Exception {
      Connection connection = connectionSupplier.createConnection(fullUser, fullPass);

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      final TemporaryTopic topic = session.createTemporaryTopic();
      assertNotNull(topic);

      Wait.assertTrue(() -> getProxyToAddress(topic.getTopicName()) != null, 3000, 10);

      topic.delete();

      Wait.assertTrue("Temp Queue should be deleted.", () -> getProxyToQueue(topic.getTopicName()) == null, 3000, 10);
   }

   @TestTemplate
   public void testDeleteTemporaryTopicNegative() throws Exception {
      Connection connection = connectionSupplier.createConnection(createOnlyUser, createOnlyPass);

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      final TemporaryTopic topic = session.createTemporaryTopic();
      assertNotNull(topic);

      Wait.assertTrue(() -> getProxyToAddress(topic.getTopicName()) != null, 3000, 10);

      try {
         topic.delete();
         // Qpid JMS doesn't throw an error here even though the topic is not deleted
         if (protocol != Protocol.AMQP) {
            fail();
         }
      } catch (JMSSecurityException e) {
         // expected
      }

      connection.close();

      Wait.assertTrue(() -> getProxyToAddress(topic.getTopicName()) == null, 3000, 10);
   }
}
