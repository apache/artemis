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
package org.apache.activemq.artemis.tests.integration.jms.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.JMSClusteredTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies how the delivery count of a message behaves when the message is redistributed to another node of the
 * cluster.
 * <p>
 * The delivery count is tracked on the {@code MessageReference}, not on the message itself, so a redistributed copy
 * would normally start over at 0 on the target broker. The {@code preserve-delivery-count-on-redistribution} address
 * setting carries the count over so {@code max-delivery-attempts} is enforced cluster-wide.
 */
public class RedeliveryWithRedistributionClusterTest extends JMSClusteredTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // the "jms." prefix is required because the cluster connection of JMSClusteredTestBase matches on it
   private static final String QUEUE = "jms.Q1";

   private static final String DLQ = "jms.Q1_DLQ";

   private static final int MAX_DELIVERY_ATTEMPTS = 5;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      jmsServer1.getActiveMQServer().setIdentity("Server 1");
      jmsServer2.getActiveMQServer().setIdentity("Server 2");
   }

   @Override
   protected boolean enablePersistence() {
      return true;
   }

   /**
    * Baseline without redistribution: the message is rolled back on server1 until {@code max-delivery-attempts} is
    * reached and it is moved to the dead letter queue.
    */
   @Test
   public void testDeliveryCountWithoutRedistribution() throws Exception {
      configureRedelivery(server1, false);
      configureRedelivery(server2, false);

      Queue jmsQueue = createQueue(QUEUE);
      Queue dlq = createQueue(DLQ);

      try (Connection conn1 = cf1.createConnection()) {
         conn1.start();
         Session session1 = conn1.createSession(Session.SESSION_TRANSACTED);

         MessageProducer producer = session1.createProducer(jmsQueue);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);
         producer.send(session1.createTextMessage("m1"));
         session1.commit();
         producer.close();

         MessageConsumer cons1 = session1.createConsumer(jmsQueue);
         waitForBindings(server1, QUEUE, true, 1, 1, 4000);

         for (int expected = 1; expected <= MAX_DELIVERY_ATTEMPTS; expected++) {
            logger.debug("expecting delivery count {} on server1", expected);
            TextMessage received = (TextMessage) cons1.receive(5000);
            assertNotNull(received, "no message received on server1");
            assertEquals("m1", received.getText());
            assertEquals(expected, received.getIntProperty("JMSXDeliveryCount"));
            session1.rollback();
         }

         // max-delivery-attempts is reached, the message must be gone
         assertNull(cons1.receive(500));
         Wait.assertEquals(1, () -> server1.locateQueue(DLQ).getMessageCount(), 5000);
         Wait.assertEquals(0, () -> server1.locateQueue(QUEUE).getMessageCount(), 5000);
         Wait.assertEquals(0, () -> server2.locateQueue(QUEUE).getMessageCount(), 5000);

         MessageConsumer dlqConsumer = session1.createConsumer(dlq);
         TextMessage dlqMessage = (TextMessage) dlqConsumer.receive(5000);
         assertNotNull(dlqMessage, "no message in the dead letter queue");
         assertEquals("m1", dlqMessage.getText());
         assertEquals(1, dlqMessage.getIntProperty("JMSXDeliveryCount"));
         session1.commit();
      }
   }

   /**
    * Default behaviour: the delivery count is reset when the message is redistributed, so server2 starts counting at 1
    * again and the message is delivered far more often than {@code max-delivery-attempts} allows.
    */
   @Test
   public void testDeliveryCountIsResetOnRedistribution() throws Exception {
      configureRedelivery(server1, false);
      configureRedelivery(server2, false);

      Queue jmsQueue = createQueue(QUEUE);
      Queue dlq = createQueue(DLQ);

      try (Connection conn1 = cf1.createConnection();
           Connection conn2 = cf2.createConnection()) {
         conn1.start();
         conn2.start();

         Session session1 = conn1.createSession(Session.SESSION_TRANSACTED);
         Session session2 = conn2.createSession(Session.SESSION_TRANSACTED);

         MessageProducer producer = session1.createProducer(jmsQueue);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);
         producer.send(session1.createTextMessage("m1"));
         session1.commit();
         producer.close();

         MessageConsumer cons1 = session1.createConsumer(jmsQueue);
         waitForBindings(server1, QUEUE, true, 1, 1, 4000);

         // stop one delivery short of max-delivery-attempts so the message stays on server1
         for (int expected = 1; expected < MAX_DELIVERY_ATTEMPTS; expected++) {
            logger.debug("expecting delivery count {} on server1", expected);
            TextMessage received = (TextMessage) cons1.receive(5000);
            assertNotNull(received, "no message received on server1");
            assertEquals("m1", received.getText());
            assertEquals(expected, received.getIntProperty("JMSXDeliveryCount"));
            session1.rollback();
         }

         // closing the only local consumer triggers redistribution to server2
         cons1.close();
         waitForBindings(server1, QUEUE, true, 1, 0, 4000);

         MessageConsumer cons2 = session2.createConsumer(jmsQueue);
         waitForBindings(server1, QUEUE, false, 1, 1, 4000);
         Wait.assertEquals(1, () -> server2.locateQueue(QUEUE).getMessageCount(), 5000);

         // the 4 deliveries on server1 are forgotten, so server2 starts counting at 1 again
         for (int expected = 1; expected <= MAX_DELIVERY_ATTEMPTS; expected++) {
            logger.debug("expecting delivery count {} on server2", expected);
            TextMessage received = (TextMessage) cons2.receive(5000);
            assertNotNull(received, "no message received on server2");
            assertEquals("m1", received.getText());
            assertEquals(expected, received.getIntProperty("JMSXDeliveryCount"));
            session2.rollback();
         }

         // the message was delivered 9 times in total even though max-delivery-attempts is 5
         assertNull(cons2.receive(500));
         Wait.assertEquals(1, () -> server2.locateQueue(DLQ).getMessageCount(), 5000);
         Wait.assertEquals(0, () -> server1.locateQueue(QUEUE).getMessageCount(), 5000);
         Wait.assertEquals(0, () -> server2.locateQueue(QUEUE).getMessageCount(), 5000);

         MessageConsumer dlqConsumer = session2.createConsumer(dlq);
         TextMessage dlqMessage = (TextMessage) dlqConsumer.receive(5000);
         assertNotNull(dlqMessage, "no message in the dead letter queue");
         assertEquals("m1", dlqMessage.getText());
         assertEquals(1, dlqMessage.getIntProperty("JMSXDeliveryCount"));
         session2.commit();
      }
   }

   /**
    * With {@code preserve-delivery-count-on-redistribution} enabled the delivery count is carried over, so the very
    * first delivery on server2 continues at {@code max-delivery-attempts} and a single rollback moves the message to
    * the dead letter queue.
    */
   @Test
   public void testDeliveryCountIsPreservedOnRedistribution() throws Exception {
      configureRedelivery(server1, true);
      configureRedelivery(server2, true);

      Queue jmsQueue = createQueue(QUEUE);
      Queue dlq = createQueue(DLQ);

      try (Connection conn1 = cf1.createConnection();
           Connection conn2 = cf2.createConnection()) {
         conn1.start();
         conn2.start();

         Session session1 = conn1.createSession(Session.SESSION_TRANSACTED);
         Session session2 = conn2.createSession(Session.SESSION_TRANSACTED);

         MessageProducer producer = session1.createProducer(jmsQueue);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);
         producer.send(session1.createTextMessage("m1"));
         session1.commit();
         producer.close();

         MessageConsumer cons1 = session1.createConsumer(jmsQueue);
         waitForBindings(server1, QUEUE, true, 1, 1, 4000);

         // stop one delivery short of max-delivery-attempts so the message stays on server1
         for (int expected = 1; expected < MAX_DELIVERY_ATTEMPTS; expected++) {
            logger.debug("expecting delivery count {} on server1", expected);
            TextMessage received = (TextMessage) cons1.receive(5000);
            assertNotNull(received, "no message received on server1");
            assertEquals("m1", received.getText());
            assertEquals(expected, received.getIntProperty("JMSXDeliveryCount"));
            session1.rollback();
         }

         // closing the only local consumer triggers redistribution to server2
         cons1.close();
         waitForBindings(server1, QUEUE, true, 1, 0, 4000);

         MessageConsumer cons2 = session2.createConsumer(jmsQueue);
         waitForBindings(server1, QUEUE, false, 1, 1, 4000);
         Wait.assertEquals(1, () -> server2.locateQueue(QUEUE).getMessageCount(), 5000);

         // the 4 deliveries on server1 are carried over, so the very first delivery on server2 is number 5
         logger.debug("expecting delivery count {} on server2", MAX_DELIVERY_ATTEMPTS);
         TextMessage received = (TextMessage) cons2.receive(5000);
         assertNotNull(received, "no message received on server2");
         assertEquals("m1", received.getText());
         assertEquals(MAX_DELIVERY_ATTEMPTS, received.getIntProperty("JMSXDeliveryCount"));

         // this single rollback reaches max-delivery-attempts cluster-wide
         session2.rollback();

         assertNull(cons2.receive(500));
         Wait.assertEquals(1, () -> server2.locateQueue(DLQ).getMessageCount(), 5000);
         Wait.assertEquals(0, () -> server1.locateQueue(QUEUE).getMessageCount(), 5000);
         Wait.assertEquals(0, () -> server2.locateQueue(QUEUE).getMessageCount(), 5000);

         // the preserved count must not leak into the dead letter queue, redelivery there starts over at 1
         MessageConsumer dlqConsumer = session2.createConsumer(dlq);
         TextMessage dlqMessage = (TextMessage) dlqConsumer.receive(5000);
         assertNotNull(dlqMessage, "no message in the dead letter queue");
         assertEquals("m1", dlqMessage.getText());
         assertEquals(1, dlqMessage.getIntProperty("JMSXDeliveryCount"));
         session2.commit();
      }
   }

   private void configureRedelivery(ActiveMQServer server, boolean preserveDeliveryCount) {
      AddressSettings settings = new AddressSettings()
         .setRedeliveryDelay(0)
         .setMaxDeliveryAttempts(MAX_DELIVERY_ATTEMPTS)
         .setDeadLetterAddress(SimpleString.of(DLQ))
         .setRedistributionDelay(0)
         .setPreserveDeliveryCountOnRedistribution(preserveDeliveryCount);

      server.getAddressSettingsRepository().addMatch("#", settings);
   }
}