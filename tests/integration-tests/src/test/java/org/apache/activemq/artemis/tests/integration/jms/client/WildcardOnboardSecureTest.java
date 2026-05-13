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
package org.apache.activemq.artemis.tests.integration.jms.client;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSSecurityException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.Topic;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WildcardOnboardSecureTest extends JMSTestBase {

   private final String publishTo = "test.topic.A";
   private final String subscribeToWildcard = "test.topic.*";
   private final String clientId = "id1";
   private final String subscriberName = "sub1";

   @Test
   public void testConsumeFromExistingWildcardWithJustConsumePermissionViaFqqnAsExactMatch() throws Exception {
      final String userPass = "joe";
      Session sendSession = createSession(null, userPass);
      MessageProducer producerA = createProducer(sendSession, publishTo);

      ObjectMessage received;
      try (MessageConsumer consumerWC = createConsumer(subscribeToWildcard, userPass)) {
         Message message = sendSession.createObjectMessage(1);
         producerA.send(message);
         received = (ObjectMessage) consumerWC.receive(500);
      }
      assertNotNull(received);
      assertNotNull(received.getObject());
   }

   @Test
   public void testConsumeFromExistingWildcardWithNoPermFails() {
      assertThrows(JMSSecurityException.class, () -> createConsumer(subscribeToWildcard, "deny"));
   }

   @Test
   public void testBrowserPermissionDeniedOnFqqn() {
      assertThrows(JMSSecurityException.class, () -> {
         Session session = createSession(clientId, "joe");
         Queue queue = session.createQueue(CompositeAddress.toFullyQualified(subscribeToWildcard, clientId + "." + subscriberName));
         QueueBrowser browser = session.createBrowser(queue);
         final Enumeration enumeration = browser.getEnumeration();
      });
   }

   @Override
   protected boolean useSecurity() {
      return true;
   }

   @Override
   protected void extraServerConfig(ActiveMQServer server) {
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("deny", "deny");
      securityManager.getConfiguration().addUser("joe", "joe");
      securityManager.getConfiguration().addRole("joe", "joe");
      Role joe = new Role("joe", true, true, false, false, false, false, false, false, false, false, false, false);
      Set<Role> roles = new HashSet<>();
      // no auto create permissions
      roles.add(joe);

      securityRepository.addMatch(publishTo, roles);
      // note fqqn contains a wildcard but because it is a fqqn it is treated as an exact match
      securityRepository.addMatch(CompositeAddress.toFullyQualified(subscribeToWildcard, clientId + "." + subscriberName), roles);
      securityManager.getConfiguration().addRole("joe", "joe");
      // pre create address and consumer queue as only send/consume permissions exist
      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName(publishTo).addRoutingType(RoutingType.MULTICAST));
      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(clientId + "." + subscriberName).setAddress(subscribeToWildcard).setRoutingType(RoutingType.MULTICAST));
   }

   private Session createSession(String clientId, String userPass) throws Exception {
      Connection connection = cf.createConnection(userPass, userPass);
      if (clientId != null) {
         connection.setClientID(clientId);
      }
      connection.start();
      addConnection(connection);

      return connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
   }

   private MessageProducer createProducer(Session session, String topicName) throws Exception {
      Topic topic = session.createTopic(topicName);
      MessageProducer producer = session.createProducer(topic);
      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      return producer;
   }

   private MessageConsumer createConsumer(String topicName, String userPass) throws Exception {
      Session session = createSession(clientId, userPass);
      Topic topic = session.createTopic(topicName);
      return session.createDurableConsumer(topic, subscriberName);
   }
}
