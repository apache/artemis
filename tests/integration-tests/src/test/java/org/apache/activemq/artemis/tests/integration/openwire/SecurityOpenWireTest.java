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
package org.apache.activemq.artemis.tests.integration.openwire;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Set;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSSecurityException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
public class SecurityOpenWireTest extends BasicOpenWireTest {

   // This set specifically tests that two FQQNs under the same address can be isolated for
   // two distinct users each being allowed only access to a given queue under the address
   private final String FQQN_USER1 = "fqqnUser1";
   private final String FQQN_USER2 = "fqqnUser2";
   private final String FQQN_ROLE1 = "fqqnRole1";
   private final String FQQN_ROLE2 = "fqqnRole2";
   private final String FQQN_ADDRESS = "fqqnAddress";
   private final String FQQN_QUEUE1 = "fqqnQueue1";
   private final String FQQN_QUEUE2 = "fqqnQueue2";
   private final String FQQN_FOR_USER1 = CompositeAddress.toFullyQualified(FQQN_ADDRESS, FQQN_QUEUE1);
   private final String FQQN_FOR_USER2 = CompositeAddress.toFullyQualified(FQQN_ADDRESS, FQQN_QUEUE2);

   private final String ALLOWED_USER = "allowedUser";
   private final String ALLOWED_ROLE = "allowedRole";
   private final String ALLOWED_USER_ALTERNATE = "allowedUserAlternate";
   private final String ALLOWED_ROLE_ALTERNATE = "allowedRoleAlternate";
   private final String DENIED_USER = "deniedUser";
   private final String DENIED_ROLE = "deniedRole";
   private final String PASS = RandomUtil.randomUUIDString();
   private final String ADDRESS = "myAddress";
   private final String ALTERNATE_ADDRESS = "myOtherAddress";
   private final String ALTERNATE_QUEUE = "myOtherQueue";
   private final String QUEUE = "myQueue";
   private final String FQQN = CompositeAddress.toFullyQualified(ADDRESS, QUEUE);
   private final String FQQN_ALTERNATE = CompositeAddress.toFullyQualified(ALTERNATE_ADDRESS, ALTERNATE_QUEUE);

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      // this system property is used to construct the executor in
      // org.apache.activemq.transport.AbstractInactivityMonitor.createExecutor()
      // and affects the pool's shutdown time. (default is 30 sec)
      // set it to 2 to make tests shutdown quicker.
      System.setProperty("org.apache.activemq.transport.AbstractInactivityMonitor.keepAliveTime", "2");

      realStore = true;
      enableSecurity = true;

      super.setUp();
   }

   @Override
   protected void extraServerConfig(Configuration configuration) {
      super.extraServerConfig(configuration);

      final Role allowed = new Role(ALLOWED_ROLE, true, false, false, false, false, false, false, false, true, false, false, false);
      final Role denied = new Role(DENIED_ROLE, false, false, false, false, false, false, false, false, false, false, false, false);
      final Role alternate = new Role(ALLOWED_ROLE_ALTERNATE, true, false, false, false, false, false, false, false, true, false, false, false);

      final ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();

      securityManager.getConfiguration().addUser(ALLOWED_USER, PASS);
      securityManager.getConfiguration().addRole(ALLOWED_USER, ALLOWED_ROLE);
      securityManager.getConfiguration().addUser(ALLOWED_USER_ALTERNATE, PASS);
      securityManager.getConfiguration().addRole(ALLOWED_USER_ALTERNATE, ALLOWED_ROLE_ALTERNATE);
      securityManager.getConfiguration().addUser(DENIED_USER, PASS);
      securityManager.getConfiguration().addRole(DENIED_USER, DENIED_ROLE);
      securityManager.getConfiguration().addRole(ALLOWED_USER, "advisoryReceiver");
      securityManager.getConfiguration().addRole(ALLOWED_USER_ALTERNATE, "advisoryReceiver");
      securityManager.getConfiguration().addRole(DENIED_USER, "advisoryReceiver");

      configuration.putSecurityRoles(FQQN, Set.of(allowed, denied));
      configuration.putSecurityRoles(ADDRESS, Set.of(allowed, denied));
      configuration.putSecurityRoles(FQQN_ALTERNATE, Set.of(alternate, denied));
      configuration.putSecurityRoles(ALTERNATE_ADDRESS, Set.of(alternate, denied));

      configuration.addQueueConfiguration(QueueConfiguration.of(QUEUE).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST));
      configuration.addQueueConfiguration(QueueConfiguration.of(ALTERNATE_QUEUE).setAddress(ALTERNATE_ADDRESS).setRoutingType(RoutingType.ANYCAST));

      // This section create a split FQQN set under a single address where each user can only write to their
      // own Queue under the base FQQN address, these roles disallow auto create to ensure neither can just
      // create their way into a working configuration.

      final Role fqqn1 = new Role(FQQN_ROLE1, true, false, false, false, false, false, false, false, false, false, false, false);
      final Role fqqn2 = new Role(FQQN_ROLE2, true, false, false, false, false, false, false, false, false, false, false, false);

      securityManager.getConfiguration().addUser(FQQN_USER1, PASS);
      securityManager.getConfiguration().addRole(FQQN_USER1, FQQN_ROLE1);
      securityManager.getConfiguration().addRole(FQQN_USER1, "advisoryReceiver");
      securityManager.getConfiguration().addUser(FQQN_USER2, PASS);
      securityManager.getConfiguration().addRole(FQQN_USER2, FQQN_ROLE2);
      securityManager.getConfiguration().addRole(FQQN_USER2, "advisoryReceiver");

      configuration.putSecurityRoles(FQQN_FOR_USER1, Set.of(fqqn1));
      configuration.putSecurityRoles(FQQN_FOR_USER2, Set.of(fqqn2));

      configuration.addQueueConfiguration(QueueConfiguration.of(FQQN_FOR_USER1).setAddress(FQQN_ADDRESS).setRoutingType(RoutingType.ANYCAST));
      configuration.addQueueConfiguration(QueueConfiguration.of(FQQN_FOR_USER2).setAddress(FQQN_ADDRESS).setRoutingType(RoutingType.ANYCAST));
   }

   @Test
   public void testAnonymousSendNoAuthToQueue() throws Exception {
      doTestAnonymousSendNoAuth(false);
   }

   @Test
   public void testAnonymousSendNoAuthToTopic() throws Exception {
      doTestAnonymousSendNoAuth(true);
   }

   private void doTestAnonymousSendNoAuth(boolean topic) throws Exception {
      try (Connection connection = factory.createConnection(DENIED_USER, PASS)) {
         final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Destination destination;

         if (topic) {
            destination = session.createTopic(ADDRESS);
         } else {
            destination = session.createQueue(ADDRESS);
         }

         final MessageProducer producer = session.createProducer(null);

         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         try {
            producer.send(destination, session.createTextMessage());
            fail("Should not be able to send to this destination");
         } catch (JMSSecurityException e) {
            // expected to fail as not authorized
         }
      }
   }

   @Test
   public void testAnonymousSendWithAuthToQueue() throws Exception {
      doTestAnonymousSendWithAuth(false);
   }

   @Test
   public void testAnonymousSendWithAuthToTopic() throws Exception {
      doTestAnonymousSendWithAuth(true);
   }

   private void doTestAnonymousSendWithAuth(boolean topic) throws Exception {
      try (Connection connection = factory.createConnection(ALLOWED_USER, PASS)) {
         final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final MessageProducer producer = session.createProducer(null);
         final Destination destination;

         if (topic) {
            destination = session.createTopic(ADDRESS);
         } else {
            destination = session.createQueue(ADDRESS);
         }

         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         try {
            producer.send(destination, session.createMessage());
            // Expected: can send to allowed destination
         } catch (JMSSecurityException e) {
            fail("unexpected error: " + e.getMessage());
         }
      }
   }

   @Test
   public void testSendWithAuthToQueue() throws Exception {
      doTestSendWithAuth(false);
   }

   @Test
   public void testSendWithAuthToTopic() throws Exception {
      doTestSendWithAuth(true);
   }

   private void doTestSendWithAuth(boolean topic) throws Exception {
      try (Connection connection = factory.createConnection(ALLOWED_USER, PASS)) {
         final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Destination destination;

         if (topic) {
            destination = session.createTopic(ADDRESS);
         } else {
            destination = session.createQueue(ADDRESS);
         }

         final MessageProducer producer = session.createProducer(destination);

         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         try {
            producer.send(session.createMessage());
            // Expected: can send to allowed destination
         } catch (JMSSecurityException e) {
            fail("unexpected error: " + e.getMessage());
         }
      }
   }

   @Test
   public void testCreateSenderNoAuthToQueue() throws Exception {
      doTestCreateSenderNoAuth(false);
   }

   @Test
   public void testCreateSenderNoAuthToTopic() throws Exception {
      doTestCreateSenderNoAuth(true);
   }

   private void doTestCreateSenderNoAuth(boolean topic) throws Exception {
      try (Connection connection = factory.createConnection(DENIED_USER, PASS)) {
         final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Destination destination;

         if (topic) {
            destination = session.createTopic(ADDRESS);
         } else {
            destination = session.createQueue(ADDRESS);
         }

         try {
            session.createProducer(destination);
            fail("Should not be able to create producer on restricted destination");
         } catch (JMSSecurityException e) {
            // expected to fail as not authorized
         }
      }
   }

   @Test
   public void testCreateSenderWithAuthToFQQNAsQueue() throws Exception {
      doTestCreateSenderWithAuthToFQQN(false);
   }

   @Test
   public void testCreateSenderWithAuthToFQQNAsTopic() throws Exception {
      doTestCreateSenderWithAuthToFQQN(true);
   }

   private void doTestCreateSenderWithAuthToFQQN(boolean topic) throws Exception {
      try (Connection connection = factory.createConnection(ALLOWED_USER, PASS)) {
         final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Destination destination;
         final Destination otherDestination;

         if (topic) {
            destination = session.createTopic(FQQN);
            otherDestination = session.createTopic(FQQN_ALTERNATE); // Does not have authorization to this FQQN
         } else {
            destination = session.createQueue(FQQN);
            otherDestination = session.createQueue(FQQN_ALTERNATE); // Does not have authorization to this FQQN
         }

         try {
            session.createProducer(destination);
            // Expected: can attach to allowed destination
         } catch (JMSSecurityException e) {
            fail("Should be able to send to given FQQN: " + e.getMessage());
         }

         try {
            session.createProducer(otherDestination);
            fail("Should not be able to send to given alternate FQQN");
         } catch (JMSSecurityException e) {
            // Expected: cannot attach to FQQN destination that is not in the security match settings
         }
      }
   }

   @Test
   public void testCreateSenderNoAuthToFQQNAsQueue() throws Exception {
      doTestCreateSenderNoAuthToFQQN(false);
   }

   @Test
   public void testCreateSenderNoAuthToFQQNAsTopic() throws Exception {
      doTestCreateSenderNoAuthToFQQN(true);
   }

   private void doTestCreateSenderNoAuthToFQQN(boolean topic) throws Exception {
      try (Connection connection = factory.createConnection(DENIED_USER, PASS)) {
         final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Destination destination;

         if (topic) {
            destination = session.createTopic(FQQN);
         } else {
            destination = session.createQueue(FQQN);
         }

         try {
            session.createProducer(destination);
            fail("Should not be able to send to given FQQN");
         } catch (JMSSecurityException e) {
            // Expected: cannot attach to denied destination
         }
      }
   }

   @Test
   public void testAnonymousSenderWithAuthToFQQNAsQueue() throws Exception {
      doTestAnonymousSenderWithAuthToFQQN(false);
   }

   @Test
   public void testAnonymousSenderWithAuthToFQQNAsTopic() throws Exception {
      doTestAnonymousSenderWithAuthToFQQN(true);
   }

   private void doTestAnonymousSenderWithAuthToFQQN(boolean topic) throws Exception {
      try (Connection connection = factory.createConnection(ALLOWED_USER, PASS)) {
         final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Destination destination;
         final Destination otherDestination;

         if (topic) {
            destination = session.createTopic(FQQN);
            otherDestination = session.createTopic(FQQN_ALTERNATE); // Does not have authorization to this FQQN
         } else {
            destination = session.createQueue(FQQN);
            otherDestination = session.createQueue(FQQN_ALTERNATE); // Does not have authorization to this FQQN
         }

         final MessageProducer producer = session.createProducer(null);

         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         try {
            producer.send(destination, session.createMessage());
            // Expected: should send to allowed destination
         } catch (JMSSecurityException e) {
            fail("Should be able to send to given FQQN: " + e.getMessage());
         }

         try {
            producer.send(otherDestination, session.createMessage());
            fail("Should not be able to send to given alternate FQQN");
         } catch (JMSSecurityException e) {
            // Expected: should fail to send to this destination
         }
      }
   }

   @Test
   public void testAnonymousSenderNoAuthToFQQNAsQueue() throws Exception {
      doTestAnonymousSenderNoAuthToFQQN(false);
   }

   @Test
   public void testAnonymousSenderNoAuthToFQQNAsTopic() throws Exception {
      doTestAnonymousSenderNoAuthToFQQN(true);
   }

   private void doTestAnonymousSenderNoAuthToFQQN(boolean topic) throws Exception {
      try (Connection connection = factory.createConnection(DENIED_USER, PASS)) {
         final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Destination destination;

         if (topic) {
            destination = session.createTopic(FQQN);
         } else {
            destination = session.createQueue(FQQN);
         }

         final MessageProducer producer = session.createProducer(null);

         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         try {
            producer.send(destination, session.createMessage());
            fail("Should not be able to send to given FQQN");
         } catch (JMSSecurityException e) {
            // Expected: cannot attach to denied destination
         }
      }
   }

   @Test
   public void testCreateSendersWithFineGrainedAuthToFQQNAsQueues() throws Exception {
      doTestCreateSenderWithFineGrainedAuthToFQQNs(false);
   }

   @Test
   public void testCreateSendersWithFineGrainedAuthToFQQNAsTopics() throws Exception {
      doTestCreateSenderWithFineGrainedAuthToFQQNs(true);
   }

   private void doTestCreateSenderWithFineGrainedAuthToFQQNs(boolean topic) throws Exception {
      try (Connection connection1 = factory.createConnection(ALLOWED_USER, PASS);
           Connection connection2 = factory.createConnection(ALLOWED_USER_ALTERNATE, PASS)) {

         final Session session1 = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         final Destination destination1;
         final Destination destination2;

         if (topic) {
            destination1 = session1.createTopic(FQQN);
            destination2 = session2.createTopic(FQQN_ALTERNATE);
         } else {
            destination1 = session1.createQueue(FQQN);
            destination2 = session2.createQueue(FQQN_ALTERNATE);
         }

         try {
            session1.createProducer(destination1);
            // Expected: should be able to create sender to allowed destination
         } catch (JMSSecurityException e) {
            fail("Should be able to create sender to given FQQN: " + e.getMessage());
         }

         try {
            session1.createProducer(destination2);
            fail("Should not be able to create sender to given FQQN");
         } catch (JMSSecurityException e) {
            // Expected: should fail to create sender to this FQQN destination
         }

         try {
            session2.createProducer(destination2);
            // Expected: should be able to create sender to allowed destination
         } catch (JMSSecurityException e) {
            fail("Should be able to create sender to given FQQN: " + e.getMessage());
         }

         try {
            session2.createProducer(destination1);
            fail("Should not be able to create sender to given FQQN");
         } catch (JMSSecurityException e) {
            // Expected: should fail to create sender to this FQQN destination
         }
      }
   }

   @Test
   public void testAnonymousSendersWithFineGrainedAuthToFQQNAsQueues() throws Exception {
      doTestAnonymousSenderWithFineGrainedAuthToFQQNs(false);
   }

   @Test
   public void testAnonymousSendersWithFineGrainedAuthToFQQNAsTopics() throws Exception {
      doTestAnonymousSenderWithFineGrainedAuthToFQQNs(true);
   }

   private void doTestAnonymousSenderWithFineGrainedAuthToFQQNs(boolean topic) throws Exception {
      try (Connection connection1 = factory.createConnection(ALLOWED_USER, PASS);
           Connection connection2 = factory.createConnection(ALLOWED_USER_ALTERNATE, PASS)) {

         final Session session1 = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         final Destination destination1;
         final Destination destination2;

         if (topic) {
            destination1 = session1.createTopic(FQQN);
            destination2 = session2.createTopic(FQQN_ALTERNATE);
         } else {
            destination1 = session1.createQueue(FQQN);
            destination2 = session2.createQueue(FQQN_ALTERNATE);
         }

         final MessageProducer producer1 = session1.createProducer(null);
         final MessageProducer producer2 = session2.createProducer(null);

         producer1.setDeliveryMode(DeliveryMode.PERSISTENT); // ALLOWED USER
         producer2.setDeliveryMode(DeliveryMode.PERSISTENT); // ALTERNATE ALLOWED USER

         try {
            producer1.send(destination1, session1.createMessage());
            // Expected: should send to allowed destination
         } catch (JMSSecurityException e) {
            fail("Should be able to send to given FQQN: " + e.getMessage());
         }

         try {
            producer1.send(destination2, session1.createMessage());
            fail("Should not be able to send to given FQQN");
         } catch (JMSSecurityException e) {
            // Expected: should fail to send to this destination
         }

         try {
            producer2.send(destination2, session2.createMessage());
            // Expected: should send to allowed destination
         } catch (JMSSecurityException e) {
            fail("Should be able to send to given FQQN: " + e.getMessage());
         }

         try {
            producer2.send(destination1, session2.createMessage());
            fail("Should not be able to send to given FQQN");
         } catch (JMSSecurityException e) {
            // Expected: should fail to send to this destination
         }
      }
   }

   @Test
   public void testCreateSenderNoAuthToCompositeAsQueue() throws Exception {
      doTestCreateSenderNoAuthToComposite(false);
   }

   @Test
   public void testCreateSenderNoAuthToCompositeAsTopic() throws Exception {
      doTestCreateSenderNoAuthToComposite(true);
   }

   private void doTestCreateSenderNoAuthToComposite(boolean topic) throws Exception {
      try (Connection connection = factory.createConnection(DENIED_USER, PASS)) {
         final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Destination destination;

         if (topic) {
            destination = session.createTopic(ADDRESS + "," + ALTERNATE_ADDRESS);
         } else {
            destination = session.createQueue(ADDRESS + "," + ALTERNATE_ADDRESS);
         }

         try {
            session.createProducer(destination);
            fail("Should not be able to create a producer to given composite destination");
         } catch (JMSSecurityException e) {
            // Expected: cannot attach to denied destination
         }
      }
   }

   @Test
   public void testAnonymousSenderNoAuthToCompositeAsQueue() throws Exception {
      doTestAnonymousSenderNoAuthToComposite(false);
   }

   @Test
   public void testAnonymousSenderNoAuthToCompositeAsTopic() throws Exception {
      doTestAnonymousSenderNoAuthToComposite(true);
   }

   private void doTestAnonymousSenderNoAuthToComposite(boolean topic) throws Exception {
      try (Connection connection = factory.createConnection(DENIED_USER, PASS)) {
         final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Destination destination;

         if (topic) {
            destination = session.createTopic(ADDRESS + "," + ALTERNATE_ADDRESS);
         } else {
            destination = session.createQueue(ADDRESS + "," + ALTERNATE_ADDRESS);
         }

         final MessageProducer producer = session.createProducer(null);

         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         try {
            producer.send(destination, session.createMessage());
            fail("Should not be able to send to given composite destination");
         } catch (JMSSecurityException e) {
            // Expected: cannot attach to denied destination
         }
      }
   }

   @Test
   public void testFQQNUserIsolationUnderSameAddressOnSenderCreate() throws Exception {
      try (Connection connection1 = factory.createConnection(FQQN_USER1, PASS);
           Connection connection2 = factory.createConnection(FQQN_USER2, PASS)) {

         final Session session1 = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         final Destination destination1 = session1.createQueue(FQQN_FOR_USER1);
         final Destination destination2 = session2.createQueue(FQQN_FOR_USER2);

         // FQQN USER 1 is isolated to the queue under address created for it

         try {
            session1.createProducer(destination1);
            // Expected: should be able to create sender to allowed destination
         } catch (JMSSecurityException e) {
            fail("Should be able to create sender to given FQQN: " + e.getMessage());
         }

         try {
            session1.createProducer(destination2);
            fail("Should not be able to create sender to given FQQN");
         } catch (JMSSecurityException e) {
            // Expected: should fail to create sender to this FQQN destination
         }

         // FQQN USER 2 is isolated to the queue under address created for it

         try {
            session2.createProducer(destination2);
            // Expected: should be able to create sender to allowed destination
         } catch (JMSSecurityException e) {
            fail("Should be able to create sender to given FQQN: " + e.getMessage());
         }

         try {
            session2.createProducer(destination1);
            fail("Should not be able to create sender to given FQQN");
         } catch (JMSSecurityException e) {
            // Expected: should fail to create sender to this FQQN destination
         }
      }
   }

   @Test
   public void testFQQNUserIsolationUnderSameAddressWithAnonymousSends() throws Exception {
      try (Connection connection1 = factory.createConnection(FQQN_USER1, PASS);
           Connection connection2 = factory.createConnection(FQQN_USER2, PASS)) {

         final Session session1 = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         final Destination destination1 = session1.createQueue(FQQN_FOR_USER1);
         final Destination destination2 = session2.createQueue(FQQN_FOR_USER2);

         final MessageProducer producer1 = session1.createProducer(null);
         final MessageProducer producer2 = session2.createProducer(null);

         // FQQN USER 1 is isolated to the queue under address created for it

         try {
            producer1.send(destination1, session1.createMessage());
            // Expected: should be able to send to allowed destination
         } catch (JMSSecurityException e) {
            fail("Should be able to send to given FQQN: " + e.getMessage());
         }

         try {
            producer1.send(destination2, session1.createMessage());
            fail("Should not be able to send to given FQQN");
         } catch (JMSSecurityException e) {
            // Expected: should fail to send to this FQQN destination
         }

         // FQQN USER 2 is isolated to the queue under address created for it

         try {
            producer2.send(destination2, session1.createMessage());
            // Expected: should be able to send to allowed destination
         } catch (JMSSecurityException e) {
            fail("Should be able to send to given FQQN: " + e.getMessage());
         }

         try {
            producer2.send(destination1, session1.createMessage());
            fail("Should not be able to send to given FQQN");
         } catch (JMSSecurityException e) {
            // Expected: should fail to send to this FQQN destination
         }
      }
   }
}
