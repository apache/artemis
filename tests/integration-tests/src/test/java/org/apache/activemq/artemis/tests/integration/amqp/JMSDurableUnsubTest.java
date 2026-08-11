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

package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;

import javax.jms.Connection;
import javax.jms.JMSSecurityException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class JMSDurableUnsubTest extends JMSClientTestSupport {

   @Override
   protected boolean isSecurityEnabled() {
      return true;
   }

   @Override
   protected void createAddressAndQueues(ActiveMQServer server) throws Exception {
      // Default Queue
      server.addAddressInfo(new AddressInfo(SimpleString.of(getQueueName()), RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(getQueueName()).setRoutingType(RoutingType.ANYCAST));

      // Default DLQ
      server.addAddressInfo(new AddressInfo(SimpleString.of(getDeadLetterAddress()), RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(getDeadLetterAddress()).setRoutingType(RoutingType.ANYCAST));

      // Create Topic for durable subscriptions to use
      server.addAddressInfo(new AddressInfo(SimpleString.of(getTopicName()), RoutingType.MULTICAST));
   }

   @Override
   protected void enableSecurity(ActiveMQServer server, String... securityMatches) {
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();

      securityManager.getConfiguration().addUser(noprivUser, noprivPass);
      securityManager.getConfiguration().addRole(noprivUser, "nothing");
      securityManager.getConfiguration().addUser(browseUser, browsePass);
      securityManager.getConfiguration().addRole(browseUser, "browser");
      securityManager.getConfiguration().addUser(guestUser, guestPass);
      securityManager.getConfiguration().addRole(guestUser, "guest");
      securityManager.getConfiguration().addUser(fullUser, fullPass);
      securityManager.getConfiguration().addRole(fullUser, "full");

      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      Set<Role> value = new HashSet<>();
      value.add(new Role("nothing", false, false, false, false, false, false, false, false, false, false, false, false));
      value.add(new Role("browser", false, false, false, false, false, false, false, true, false, false, false, false));
      value.add(new Role("guest", false, true, false, false, false, false, false, true, false, false, false, false));
      value.add(new Role("full", true, true, true, true, true, true, true, true, true, true, false, false));
      securityRepository.addMatch(getTopicName(), value);

      for (String match : securityMatches) {
         securityRepository.addMatch(match, value);
      }

      server.getConfiguration().setSecurityEnabled(true);
   }

   @Test
   @Timeout(20)
   public void testUnsubscribeAllowedFromAuthorizedUserNoErrorReturned() throws Throwable {
      final String clientId = "test-" + getTestName();
      final String subscriptionName = "test-" + getTestName() + "-sub";

      // Full privilege user creates a subscription and leaves it
      try (Connection connection = createConnection(fullUser, fullPass, clientId)) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic(getTopicName());
         MessageConsumer consumer = session.createDurableSubscriber(topic, subscriptionName);

         connection.start();

         consumer.close();
      }

      Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTopicName())).isExists(), 2000, 50);
      Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTopicName()), false).getQueueNames().size() == 1, 2000, 50);

      // Then it removes it which should not return an error and bindings should be cleaned up
      try (Connection connection = createConnection(fullUser, fullPass, clientId)) {
         final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         assertDoesNotThrow(() -> session.unsubscribe(subscriptionName));
      }

      Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTopicName())).isExists(), 2000, 50);
      Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTopicName()), false).getQueueNames().size() == 0, 2000, 50);
   }

   @Test
   @Timeout(20)
   public void testUnsubscribeDisallowedFromUnauthorizedUserAndErrorReturned() throws Throwable {
      final String clientId = "test-" + getTestName();
      final String subscriptionName = "test-" + getTestName() + "-sub";

      // Full privilege user creates a subscription and leaves it
      try (Connection connection = createConnection(fullUser, fullPass, clientId)) {
         final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Topic topic = session.createTopic(getTopicName());

         session.createDurableSubscriber(topic, subscriptionName);

         connection.start();
      }

      Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTopicName())).isExists());
      Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTopicName()), false).getQueueNames().size() == 1);

      // Low privilege user tries to a remove that subscription but fails and subscription remains
      // the client should get a security exception in the response.
      try (Connection connection = createConnection(guestUser, guestPass, clientId)) {
         final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         assertThrows(JMSSecurityException.class, () -> session.unsubscribe(subscriptionName),
            "Expected JMSException when unsubscribing without deleteDurableQueue permission");
      }

      Wait.assertTrue(() -> server.addressQuery(SimpleString.of(getTopicName())).isExists());
      Wait.assertTrue(() -> server.bindingQuery(SimpleString.of(getTopicName()), false).getQueueNames().size() == 1);
   }
}
