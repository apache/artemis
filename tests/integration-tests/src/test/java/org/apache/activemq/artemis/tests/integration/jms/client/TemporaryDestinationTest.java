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
package org.apache.activemq.artemis.tests.integration.jms.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.impl.ServerSessionImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnection;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TemporaryDestinationTest extends JMSTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }

   @Override
   protected boolean usePersistence() {
      return true;
   }

   @Test
   public void testTemporaryQueueLeak() throws Exception {
      ActiveMQConnection conn = null;

      try {
         conn = (ActiveMQConnection) createConnection();

         Session producerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         TemporaryQueue tempQueue = producerSession.createTemporaryQueue();

         MessageProducer producer = producerSession.createProducer(tempQueue);

         MessageConsumer consumer = consumerSession.createConsumer(tempQueue);

         conn.start();

         final String messageText = "This is a message";

         javax.jms.Message m = producerSession.createTextMessage(messageText);

         producer.send(m);

         TextMessage m2 = (TextMessage) consumer.receive(2000);

         assertNotNull(m2);

         assertEquals(messageText, m2.getText());

         consumer.close();

         assertTrue(((ActiveMQDestination) tempQueue).isCreated());

         tempQueue.delete();

         assertFalse(((ActiveMQDestination) tempQueue).isCreated());

         assertFalse(conn.containsTemporaryQueue(SimpleString.of(tempQueue.getQueueName())));
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }
   @Test
   public void testTemporaryQueueDeletedAfterSessionClosed() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateAddresses(false).setAutoCreateQueues(false));

      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         // Make sure temporary queue cannot be used after it has been deleted

         TemporaryQueue tempQueue = producerSession.createTemporaryQueue();

         MessageProducer producer = producerSession.createProducer(tempQueue);

         MessageConsumer consumer = consumerSession.createConsumer(tempQueue);

         conn.start();

         final String messageText = "This is a message";

         javax.jms.Message m = producerSession.createTextMessage(messageText);

         producer.send(m);

         TextMessage m2 = (TextMessage) consumer.receive(2000);

         assertNotNull(m2);

         assertEquals(messageText, m2.getText());

         consumer.close();

         consumerSession.close();

         producer.close();

         producerSession.close();

         tempQueue.delete();

         producerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         try {
            producer = producerSession.createProducer(tempQueue);
            producer.send(m);
            fail();
         } catch (JMSException e) {
         }
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testTemporaryTopicDeletedAfterSessionClosed() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateAddresses(false).setAutoCreateQueues(false));

      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session consumerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         // Make sure temporary topic cannot be used after it has been deleted

         TemporaryTopic tempTopic = producerSession.createTemporaryTopic();

         MessageProducer producer = producerSession.createProducer(tempTopic);

         MessageConsumer consumer = consumerSession.createConsumer(tempTopic);

         conn.start();

         final String messageText = "This is a message";

         javax.jms.Message m = producerSession.createTextMessage(messageText);

         producer.send(m);

         TextMessage m2 = (TextMessage) consumer.receive(2000);

         assertNotNull(m2);

         assertEquals(messageText, m2.getText());

         consumer.close();

         consumerSession.close();

         producer.close();

         producerSession.close();

         tempTopic.delete();

         producerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         try {
            producer = producerSession.createProducer(tempTopic);
            producer.send(m);
            fail();
         } catch (JMSException e) {
         }
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testTemporaryResourcesDeletedAfterServerRestart() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateAddresses(false).setAutoCreateQueues(false));

      Connection conn = null;

      try {
         conn = createConnection();

         Session producerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         TemporaryQueue tempQueue = producerSession.createTemporaryQueue();

         assertNotNull(server.getAddressInfo(SimpleString.of(tempQueue.getQueueName())));

         server.stop();

         conn.close();

         server.start();

         waitForServerToStart(server);

         assertNull(server.getAddressInfo(SimpleString.of(tempQueue.getQueueName())));
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testForTempQueueCleanerUpperLeak() throws Exception {
      try {
         conn = createConnection();
         Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         TemporaryQueue temporaryQueue = s.createTemporaryQueue();
         temporaryQueue.delete();
         for (ServerSession serverSession : server.getSessions()) {
            assertEquals(0, ((ServerSessionImpl)serverSession).getTempQueueCleanUppers().size());
         }
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testForTempQueueTargetInfosLeak() throws Exception {
      try {
         conn = createConnection();
         Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         TemporaryQueue temporaryQueue = s.createTemporaryQueue();
         MessageProducer producer = s.createProducer(temporaryQueue);
         producer.send(s.createMessage());
         temporaryQueue.delete();
         for (ServerSession serverSession : server.getSessions()) {
            assertFalse(((ServerSessionImpl)serverSession).cloneProducers().containsKey(temporaryQueue.getQueueName()));
         }
         Wait.assertTrue(() -> server.locateQueue(temporaryQueue.getQueueName()) == null, 1000, 100);
         Wait.assertTrue(() -> server.getAddressInfo(SimpleString.of(temporaryQueue.getQueueName())) == null, 1000, 100);
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testTemporaryQueueConnectionClosedRemovedAMQP() throws Exception {
      testTemporaryQueueConnectionClosedRemoved("AMQP");
   }

   @Test
   public void testTemporaryQueueConnectionClosedRemovedCORE() throws Exception {
      testTemporaryQueueConnectionClosedRemoved("CORE");
   }

   @Test
   public void testTemporaryQueueConnectionClosedRemovedOpenWire() throws Exception {
      testTemporaryQueueConnectionClosedRemoved("OPENWIRE");
   }

   private void testTemporaryQueueConnectionClosedRemoved(String protocol) throws Exception {
      ConnectionFactory factory = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61616");
      final TemporaryQueue temporaryQueue;
      try (Connection conn = factory.createConnection()) {
         Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         temporaryQueue = s.createTemporaryQueue();
         MessageProducer producer = s.createProducer(temporaryQueue);
         producer.send(s.createMessage());
         // These next two assertions are here to validate the test itself
         // The queue and address should be found on the server while they still exist on the connection
         Wait.assertFalse(() -> server.locateQueue(temporaryQueue.getQueueName()) == null, 1000, 100);
         Wait.assertFalse(() -> server.getAddressInfo(SimpleString.of(temporaryQueue.getQueueName())) == null, 1000, 100);
      }

      Wait.assertTrue(() -> server.locateQueue(temporaryQueue.getQueueName()) == null, 1000, 100);
      Wait.assertTrue(() -> server.getAddressInfo(SimpleString.of(temporaryQueue.getQueueName())) == null, 1000, 100);
   }

   @Test
   public void testForSecurityCacheLeak() throws Exception {
      server.getSecurityStore().setSecurityEnabled(true);
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("IDo", "Exist");
      securityManager.getConfiguration().addRole("IDo", "myrole");
      Role myRole = new Role("myrole", true, true, true, true, true, true, true, true, true, true, false, false);
      Set<Role> anySet = new HashSet<>();
      anySet.add(myRole);
      server.getSecurityRepository().addMatch("#", anySet);

      try {
         conn = addConnection(cf.createConnection("IDo", "Exist"));
         Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         for (int i = 0; i < 10; i++) {
            TemporaryQueue temporaryQueue = s.createTemporaryQueue();
            temporaryQueue.delete();
         }
         assertEquals(0, server.getSecurityRepository().getCacheSize());
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }
}
