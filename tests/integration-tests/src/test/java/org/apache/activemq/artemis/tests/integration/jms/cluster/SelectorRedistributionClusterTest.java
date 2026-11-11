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
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.tests.util.JMSClusteredTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SelectorRedistributionClusterTest extends JMSClusteredTestBase {

   private final String myQueue = "myQueue";

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

   @Test
   public void testSelectorRoutingReDistributionManyInitialLocalConsumers() throws Exception {
      server1.getAddressSettingsRepository().getMatch("#").setRedistributionDelay(0);
      server2.getAddressSettingsRepository().getMatch("#").setRedistributionDelay(0);

      Connection conn1 = cf1.createConnection();
      Connection conn2 = cf2.createConnection();
      conn1.start();
      conn2.start();

      try {
         Session session1 = conn1.createSession(Session.SESSION_TRANSACTED);
         Session session2 = conn2.createSession(Session.SESSION_TRANSACTED);

         javax.jms.Queue jmsQueue = session1.createQueue(myQueue);
         MessageProducer prod1 = session1.createProducer(jmsQueue);
         prod1.setDeliveryMode(DeliveryMode.PERSISTENT);
         TextMessage textMessage = session1.createTextMessage("m1");
         textMessage.setIntProperty("N", 10);
         prod1.send(textMessage);
         session1.commit();

         MessageConsumer cons1_1 = session1.createConsumer(jmsQueue, "N = 0");
         waitForBindings(server1, jmsQueue.getQueueName(), true, 1, 1, 4000);

         MessageConsumer cons1_2 = session1.createConsumer(jmsQueue, "N = 0");
         waitForBindings(server1, jmsQueue.getQueueName(), true, 1, 2, 4000);

         MessageConsumer cons2 = session2.createConsumer(jmsQueue, "N = 10");
         waitForBindings(server1, jmsQueue.getQueueName(), false, 1, 1, 4000);

         // message should be redistributed to server2
         TextMessage received = (TextMessage) cons2.receive(5000);
         assertNotNull(received);
         assertEquals("m1", received.getText());
         session2.commit();

         Wait.assertEquals(0, () -> server1.locateQueue(jmsQueue.getQueueName()).getMessageCount(), 5000);
         Wait.assertEquals(0, () -> server2.locateQueue(jmsQueue.getQueueName()).getMessageCount(), 5000);
      } finally {
         conn1.close();
         conn2.close();
      }
   }

   @Test
   public void testSelectorRoutingReDistributionOnNoConsumer() throws Exception {
      server1.getAddressSettingsRepository().getMatch("#").setAutoCreateQueues(true).setAutoCreateAddresses(true).setRedistributionDelay(0);
      server2.getAddressSettingsRepository().getMatch("#").setAutoCreateQueues(true).setAutoCreateAddresses(true).setRedistributionDelay(0);
      Connection conn1 = cf1.createConnection();
      Connection conn2 = cf2.createConnection();
      conn1.start();
      conn2.start();

      try {
         Session session1 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Session session2 = conn2.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         javax.jms.Queue jmsQueue = session1.createQueue(myQueue);

         MessageProducer prod1 = session1.createProducer(jmsQueue);

         prod1.setDeliveryMode(DeliveryMode.PERSISTENT);

         TextMessage textMessage = session1.createTextMessage("m1");
         textMessage.setIntProperty("N", 10);


         // remote demand with a filter in advance of send, so routing sees remote filter match and can ignore the local consumer
         MessageConsumer cons2 = session2.createConsumer(jmsQueue, "N = 10");
         // local consumer that does not match message
         MessageConsumer cons1 = session1.createConsumer(jmsQueue, "N = 0");

         // verify cluster notifications have completed before send
         waitForBindings(server1, myQueue, false, 1, 1, 4000);

         prod1.send(textMessage);

         TextMessage received = (TextMessage) cons2.receive(4000);
         assertNotNull(received);
         assertEquals("m1", received.getText());

         // lets check some redistribution back by close with no acknowledge
         session2.close();

         // consumer on server 1 does not match, redistribution not done yet, message still available to local consumer
         session2 = conn2.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         cons2 = session2.createConsumer(jmsQueue, "N = 10");
         received = (TextMessage) cons2.receive(4000);
         assertNotNull(received);

         // have to create consumer matching filter on server1 in advance such that redistribution happens fast
         MessageConsumer cons11 = session1.createConsumer(jmsQueue, "N = 10");

         // now expect redistribution
         session2.close();

         // get it from server1
         received = (TextMessage) cons11.receive(4000);
         assertNotNull(received);
         assertEquals("m1", received.getText());

         // done
         received.acknowledge();

      } finally {
         conn1.close();
         conn2.close();
      }
   }

   @Test
   public void testSelectorRoutingNoReDistributionNewMessageSkipsTillLocalClose() throws Exception {
      server1.getAddressSettingsRepository().getMatch("#").setAutoCreateQueues(true).setAutoCreateAddresses(true).setRedistributionDelay(0);
      server2.getAddressSettingsRepository().getMatch("#").setAutoCreateQueues(true).setAutoCreateAddresses(true).setRedistributionDelay(0);
      Connection conn1 = cf1.createConnection();
      Connection conn2 = cf2.createConnection();
      conn1.start();
      conn2.start();

      try {
         Session session1 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         Session session11 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Session session2 = conn2.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         javax.jms.Queue jmsQueue = session1.createQueue(myQueue);

         MessageProducer prod1 = session1.createProducer(jmsQueue);

         prod1.setDeliveryMode(DeliveryMode.PERSISTENT);

         TextMessage textMessage = session1.createTextMessage("m1");
         textMessage.setIntProperty("N", 10);


         // remote demand with a filter in advance of send
         MessageConsumer cons2 = session2.createConsumer(jmsQueue, "N = 10");
         // local consumer that does not match message
         MessageConsumer cons1 = session1.createConsumer(jmsQueue, "N = 0");
         // local consumer that matches message
         MessageConsumer cons11 = session11.createConsumer(jmsQueue, "N = 10");

         // verify cluster notifications have completed before send
         waitForBindings(server1, myQueue, false, 1, 1, 4000);

         prod1.send(textMessage);

         TextMessage received = (TextMessage) cons11.receive(4000);
         assertNotNull(received);
         assertEquals("m1", received.getText());

         // lets check some redistribution by close with no acknowledge so session rolls back delivery
         session11.close();

         // nothing for the existing remote binding
         received = (TextMessage) cons2.receiveNoWait();
         assertNull(received);

         // send a second message, it will get routed to the remote binding
         textMessage = session1.createTextMessage("m2");
         textMessage.setIntProperty("N", 10);

         prod1.send(textMessage);

         received = (TextMessage) cons2.receive(4000);
         assertNotNull(received);
         assertEquals("m2", received.getText());
         received.acknowledge();

         // release the local consumer such that redistribution kicks in
         session1.close();

         received = (TextMessage) cons2.receive(4000);
         assertNotNull(received);
         assertEquals("m1", received.getText());

         // done
         received.acknowledge();

      } finally {
         conn1.close();
         conn2.close();
      }
   }


   @Test
   public void testSelectorRoutingReDistributionDoesNotBlockLocalConsumer() throws Exception {
      server1.getAddressSettingsRepository().getMatch("#").setAutoCreateQueues(true).setAutoCreateAddresses(true).setRedistributionDelay(0);
      server2.getAddressSettingsRepository().getMatch("#").setAutoCreateQueues(true).setAutoCreateAddresses(true).setRedistributionDelay(0);
      Connection conn1 = cf1.createConnection();
      Connection conn2 = cf2.createConnection();
      conn1.start();
      conn2.start();

      try {
         Session session1 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         Session session11 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Session session2 = conn2.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         javax.jms.Queue jmsQueue = session1.createQueue(myQueue);

         MessageProducer prod1 = session1.createProducer(jmsQueue);

         prod1.setDeliveryMode(DeliveryMode.PERSISTENT);

         TextMessage textMessage = session1.createTextMessage("m1");
         textMessage.setIntProperty("N", 10);


         // local consumers that does not match message
         MessageConsumer cons1_0 = session1.createConsumer(jmsQueue, "N = 0");
         MessageConsumer cons1_1 = session1.createConsumer(jmsQueue, "N = 1");

         // local consumer that matches message
         MessageConsumer cons1_10 = session11.createConsumer(jmsQueue, "N = 10");

         prod1.send(textMessage);

         TextMessage received = (TextMessage) cons1_10.receive(4000);
         assertNotNull(received);
         assertEquals("m1", received.getText());

         // lets check some redistribution by close with no acknowledge so session rolls back delivery
         session11.close();

         // remote demand with a filter, consumer moved triggers redistribution event b/c all local consumers don't match
         MessageConsumer cons2_10 = session2.createConsumer(jmsQueue, "N = 10");

         received = (TextMessage) cons2_10.receive(4000);
         assertNotNull(received);
         assertEquals("m1", received.getText());
         received.acknowledge();

         // check local consumers can still get dispatched
         textMessage = session1.createTextMessage("m2");
         textMessage.setIntProperty("N", 0);
         prod1.send(textMessage);

         textMessage = session1.createTextMessage("m3");
         textMessage.setIntProperty("N", 1);
         prod1.send(textMessage);


         received = (TextMessage) cons1_0.receive(4000);
         assertNotNull(received);
         assertEquals("m2", received.getText());
         received.acknowledge();

         received = (TextMessage) cons1_1.receive(4000);
         assertNotNull(received);
         assertEquals("m3", received.getText());
         received.acknowledge();

         // verify redistributor still kicks in too
         textMessage = session1.createTextMessage("m4");
         textMessage.setIntProperty("N", 10);
         prod1.send(textMessage);

         received = (TextMessage) cons2_10.receive(4000);
         assertNotNull(received);
         assertEquals("m4", received.getText());
         received.acknowledge();

      } finally {
         conn1.close();
         conn2.close();
      }
   }


   @Test
   public void testSelectorRoutingReDistributionOnConsumerMove() throws Exception {
      server1.getAddressSettingsRepository().getMatch("#").setAutoCreateQueues(true).setAutoCreateAddresses(true).setRedistributionDelay(0);
      server2.getAddressSettingsRepository().getMatch("#").setAutoCreateQueues(true).setAutoCreateAddresses(true).setRedistributionDelay(0);
      Connection conn1 = cf1.createConnection();
      Connection conn2 = cf2.createConnection();
      conn1.start();
      conn2.start();

      try {
         Session session1 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         Session session11 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Session session2 = conn2.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         javax.jms.Queue jmsQueue = session1.createQueue(myQueue);

         MessageProducer prod1 = session1.createProducer(jmsQueue);

         prod1.setDeliveryMode(DeliveryMode.PERSISTENT);

         TextMessage textMessage = session1.createTextMessage("m1");
         textMessage.setIntProperty("N", 10);


         // local consumers that does not match message
         MessageConsumer cons1 = session1.createConsumer(jmsQueue, "N = 0");
         MessageConsumer cons12 = session1.createConsumer(jmsQueue, "N = 1");

         // local consumer that matches message
         MessageConsumer cons111 = session11.createConsumer(jmsQueue, "N = 10");

         prod1.send(textMessage);

         TextMessage received = (TextMessage) cons111.receive(4000);
         assertNotNull(received);
         assertEquals("m1", received.getText());

         // lets check some redistribution by close with no acknowledge so session rolls back delivery
         session11.close();

         // remote demand with a filter, consumer moved triggers redistribution event b/c all local consumers don't match
         MessageConsumer cons2 = session2.createConsumer(jmsQueue, "N = 10");

         received = (TextMessage) cons2.receive(4000);
         assertNotNull(received);
         assertEquals("m1", received.getText());

         received.acknowledge();

      } finally {
         conn1.close();
         conn2.close();
      }
   }

   @Test
   public void testSelectorRoutingReDistributionOnLocalNoMatchConsumerCloseNeedsNewRemoteDemand() throws Exception {
      server1.getAddressSettingsRepository().getMatch("#").setAutoCreateQueues(true).setAutoCreateAddresses(true).setRedistributionDelay(0);
      server2.getAddressSettingsRepository().getMatch("#").setAutoCreateQueues(true).setAutoCreateAddresses(true).setRedistributionDelay(0);
      Connection conn1 = cf1.createConnection();
      Connection conn2 = cf2.createConnection();
      conn1.start();
      conn2.start();

      try {
         Session session1 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         Session session1_n_10 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         Session session1_n_1 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Session session2 = conn2.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         javax.jms.Queue jmsQueue = session1.createQueue(myQueue);


         MessageProducer prod1 = session1.createProducer(jmsQueue);

         prod1.setDeliveryMode(DeliveryMode.PERSISTENT);

         TextMessage textMessage = session1.createTextMessage("m1");
         textMessage.setIntProperty("N", 10);


         // remote demand with a filter
         MessageConsumer consumer2_n_10 = session2.createConsumer(jmsQueue, "N = 10");

         // local consumers that does not match message
         MessageConsumer consumer1_n_0 = session1.createConsumer(jmsQueue, "N = 0");
         MessageConsumer consumer1_n_1 = session1_n_1.createConsumer(jmsQueue, "N = 1");

         // local consumer that matches message
         MessageConsumer consumer1_n_10 = session1_n_10.createConsumer(jmsQueue, "N = 10");

         // verify cluster notifications have completed before send
         waitForBindings(server1, myQueue, false, 1, 1, 4000);
         waitForBindings(server1, myQueue, true, 1, 3, 4000);

         prod1.send(textMessage);

         TextMessage received = (TextMessage) consumer1_n_10.receive(4000);
         assertNotNull(received);
         assertEquals("m1", received.getText());

         // lets prepare some non matching message for redistribution by close with no acknowledge so session rolls back delivery
         session1_n_10.close();

         // verify no redistribution event yet
         assertNull(consumer2_n_10.receiveNoWait());

         // local remove consumer event will not trigger redistribution
         session1_n_1.close();

         // verify no redistribution event yet
         assertNull(consumer2_n_10.receiveNoWait());

         // force a redistribution event on new remote consumer creation (that won't match in this case), trigger redistribution
         MessageConsumer consumer2_n_0 = session2.createConsumer(jmsQueue, "N = 0");

         // verify redistribution to remote
         received = (TextMessage) consumer2_n_10.receive(4000);
         assertNotNull(received);
         assertEquals("m1", received.getText());

         received.acknowledge();

      } finally {
         conn1.close();
         conn2.close();
      }
   }
}
