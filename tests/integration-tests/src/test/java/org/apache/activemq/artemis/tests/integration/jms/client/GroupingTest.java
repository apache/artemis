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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.UUID;

import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GroupingTest extends JMSTestBase {

   private Queue queue;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      queue = createQueue("TestQueue");
   }

   protected void setProperty(Message message) {
      ((ActiveMQMessage) message).getCoreMessage().putStringProperty(org.apache.activemq.artemis.api.core.Message.HDR_GROUP_ID, SimpleString.of("foo"));
   }

   protected ConnectionFactory getCF() throws Exception {
      return cf;
   }

   @Test
   public void testGrouping() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();

      Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      MessageProducer producer = session.createProducer(queue);

      MessageConsumer consumer1 = session.createConsumer(queue);
      MessageConsumer consumer2 = session.createConsumer(queue);
      MessageConsumer consumer3 = session.createConsumer(queue);

      connection.start();

      String jmsxgroupID = null;

      for (int j = 0; j < 100; j++) {
         TextMessage message = session.createTextMessage();

         message.setText("Message" + j);

         setProperty(message);

         producer.send(message);

         String prop = message.getStringProperty("JMSXGroupID");

         assertNotNull(prop);

         if (jmsxgroupID != null) {
            assertEquals(jmsxgroupID, prop);
         } else {
            jmsxgroupID = prop;
         }
      }

      //All msgs should go to the first consumer
      for (int j = 0; j < 100; j++) {
         TextMessage tm = (TextMessage) consumer1.receive(10000);

         assertNotNull(tm);

         assertEquals("Message" + j, tm.getText());

         assertEquals(tm.getStringProperty("JMSXGroupID"), jmsxgroupID);
      }

      connection.close();

   }

   @Test
   public void testGroupingWithJMS2Producer() throws Exception {
      ConnectionFactory fact = getCF();
      assumeFalse(((ActiveMQConnectionFactory) fact).isAutoGroup(), "only makes sense withOUT auto-group");
      assumeTrue(((ActiveMQConnectionFactory) fact).getGroupID() == null, "only makes sense withOUT explicit group-id");
      final String groupID = UUID.randomUUID().toString();
      JMSContext ctx = addContext(getCF().createContext(JMSContext.SESSION_TRANSACTED));

      JMSProducer producer = ctx.createProducer().setProperty("JMSXGroupID", groupID);

      JMSConsumer consumer1 = ctx.createConsumer(queue);
      JMSConsumer consumer2 = ctx.createConsumer(queue);
      JMSConsumer consumer3 = ctx.createConsumer(queue);

      ctx.start();

      for (int j = 0; j < 100; j++) {
         TextMessage message = ctx.createTextMessage("Message" + j);

         producer.send(queue, message);

         String prop = message.getStringProperty("JMSXGroupID");

         assertNotNull(prop);
         assertEquals(groupID, prop);
      }

      ctx.commit();

      //All msgs should go to the first consumer
      for (int j = 0; j < 100; j++) {
         TextMessage tm = (TextMessage) consumer1.receive(10000);

         assertNotNull(tm);

         tm.acknowledge();

         assertEquals("Message" + j, tm.getText());

         assertEquals(tm.getStringProperty("JMSXGroupID"), groupID);

         tm = (TextMessage) consumer2.receiveNoWait();
         assertNull(tm);
         tm = (TextMessage) consumer3.receiveNoWait();
         assertNull(tm);
      }

      ctx.commit();

      ctx.close();
   }

   @Test
   public void testManyGroups() throws Exception {
      ConnectionFactory fact = getCF();
      assumeFalse(((ActiveMQConnectionFactory) fact).isAutoGroup(), "only makes sense withOUT auto-group");

      Connection connection = fact.createConnection();

      Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      MessageProducer producer = session.createProducer(queue);

      MessageConsumer consumer1 = session.createConsumer(queue);
      MessageConsumer consumer2 = session.createConsumer(queue);
      MessageConsumer consumer3 = session.createConsumer(queue);

      connection.start();

      for (int j = 0; j < 1000; j++) {
         TextMessage message = session.createTextMessage();

         message.setText("Message" + j);

         message.setStringProperty("_AMQ_GROUP_ID", "" + (j % 10));

         producer.send(message);

         String prop = message.getStringProperty("JMSXGroupID");

         assertNotNull(prop);

      }

      int msg1 = flushMessages(consumer1);
      int msg2 = flushMessages(consumer2);
      int msg3 = flushMessages(consumer3);

      assertNotSame(0, msg1);
      assertNotSame(0, msg2);
      assertNotSame(0, msg2);

      consumer1.close();
      consumer2.close();
      consumer3.close();

      connection.close();

   }

   @Test
   public void testGroupingRollbackOnClose() throws Exception {
      ActiveMQConnectionFactory fact = (ActiveMQConnectionFactory) getCF();
      fact.setConsumerWindowSize(1000);
      fact.setTransactionBatchSize(0);
      Connection connection = fact.createConnection();
      RemotingConnection rc = server.getRemotingService().getConnections().iterator().next();
      Connection connection2 = fact.createConnection();

      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      Session session2 = connection2.createSession(true, Session.SESSION_TRANSACTED);

      MessageProducer producer = session.createProducer(queue);

      MessageConsumer consumer1 = session.createConsumer(queue);
      MessageConsumer consumer2 = session2.createConsumer(queue);

      connection.start();
      connection2.start();

      String jmsxgroupID = null;

      for (int j = 0; j < 100; j++) {
         TextMessage message = session.createTextMessage();

         message.setText("Message" + j);

         setProperty(message);

         producer.send(message);

         String prop = message.getStringProperty("JMSXGroupID");

         assertNotNull(prop);

         if (jmsxgroupID != null) {
            assertEquals(jmsxgroupID, prop);
         } else {
            jmsxgroupID = prop;
         }
      }
      session.commit();
      //consume 5 msgs from 1st first consumer
      for (int j = 0; j < 1; j++) {
         TextMessage tm = (TextMessage) consumer1.receive(10000);

         assertNotNull(tm);

         assertEquals("Message" + j, tm.getText());

         assertEquals(tm.getStringProperty("JMSXGroupID"), jmsxgroupID);
      }
      //session.rollback();
      //session.close();
      //consume all msgs from 2nd first consumer
      // ClientSession amqs = ((ActiveMQSession) session).getCoreSession();
      //  ((DelegatingSession) amqs).getChannel().close();
      rc.fail(new ActiveMQNotConnectedException());
      for (int j = 0; j < 10; j++) {
         TextMessage tm = (TextMessage) consumer2.receive(10000);

         assertNotNull(tm);

         long text = ((ActiveMQTextMessage) tm).getCoreMessage().getMessageID();
         //assertEquals("Message" + j, text);

         assertEquals(tm.getStringProperty("JMSXGroupID"), jmsxgroupID);
      }

      connection.close();
      connection2.close();
   }

   private int flushMessages(MessageConsumer consumer) throws JMSException {
      int received = 0;
      while (true) {
         TextMessage msg = (TextMessage) consumer.receiveNoWait();
         if (msg == null) {
            break;
         }
         msg.acknowledge();
         received++;
      }
      return received;
   }

   @Test
   public void testGroupBuckets() throws Exception {
      ConnectionFactory fact = getCF();
      assumeFalse(((ActiveMQConnectionFactory) fact).isAutoGroup(), "only makes sense withOUT auto-group");
      assumeTrue(((ActiveMQConnectionFactory) fact).getGroupID() == null, "only makes sense withOUT explicit group-id");

      String testQueueName = getName() + "_bucket_group";

      server.createQueue(QueueConfiguration.of(testQueueName).setRoutingType(RoutingType.ANYCAST).setGroupBuckets(2));

      JMSContext ctx = addContext(getCF().createContext(JMSContext.SESSION_TRANSACTED));

      Queue testQueue = ctx.createQueue(testQueueName);


      final String groupID1 = "groupA";
      final String groupID2 = "groupB";
      final String groupID3 = "groupC";

      //Ensure the groups bucket as we expect.
      assertEquals((groupID1.hashCode() & Integer.MAX_VALUE) % 2, 0);
      assertEquals((groupID2.hashCode() & Integer.MAX_VALUE) % 2, 1);
      assertEquals((groupID3.hashCode() & Integer.MAX_VALUE) % 2, 0);

      JMSProducer producer1 = ctx.createProducer().setProperty("JMSXGroupID", groupID1);
      JMSProducer producer2 = ctx.createProducer().setProperty("JMSXGroupID", groupID2);
      JMSProducer producer3 = ctx.createProducer().setProperty("JMSXGroupID", groupID3);

      JMSConsumer consumer1 = ctx.createConsumer(testQueue);
      JMSConsumer consumer2 = ctx.createConsumer(testQueue);
      JMSConsumer consumer3 = ctx.createConsumer(testQueue);

      ctx.start();

      for (int j = 0; j < 10; j++) {
         send(ctx, testQueue, groupID1, producer1, j);
      }
      for (int j = 10; j < 20; j++) {
         send(ctx, testQueue, groupID2, producer2, j);
      }
      for (int j = 20; j < 30; j++) {
         send(ctx, testQueue, groupID3, producer3, j);
      }

      ctx.commit();

      //First two set of msgs should go to the first two consumers only (buckets is 2)
      for (int j = 0; j < 10; j++) {
         TextMessage tm = (TextMessage) consumer1.receive(10000);
         assertNotNull(tm);
         tm.acknowledge();
         assertEquals("Message" + j, tm.getText());
         assertEquals(groupID1, tm.getStringProperty("JMSXGroupID"));

         tm = (TextMessage) consumer2.receive(10000);
         assertNotNull(tm);
         tm.acknowledge();
         assertEquals("Message" + (j + 10), tm.getText());
         assertEquals(groupID2, tm.getStringProperty("JMSXGroupID"));

         assertNull(consumer3.receiveNoWait());
      }

      //Last set of msgs should go to the first consumer as bucketed queue with two bucket groups only
      for (int j = 20; j < 30; j++) {
         TextMessage tm = (TextMessage) consumer1.receive(10000);
         assertNotNull(tm);
         tm.acknowledge();
         assertEquals("Message" + j, tm.getText());
         assertEquals(groupID3, tm.getStringProperty("JMSXGroupID"));

         assertNull(consumer2.receiveNoWait());
         assertNull(consumer3.receiveNoWait());

      }


      ctx.commit();

      ctx.close();
   }

   @Test
   public void testGroupRebalance() throws Exception {
      ConnectionFactory fact = getCF();
      assumeFalse(((ActiveMQConnectionFactory) fact).isAutoGroup(), "only makes sense withOUT auto-group");
      assumeTrue(((ActiveMQConnectionFactory) fact).getGroupID() == null, "only makes sense withOUT explicit group-id");
      String testQueueName = getName() + "_group_rebalance";

      server.createQueue(QueueConfiguration.of(testQueueName).setRoutingType(RoutingType.ANYCAST).setGroupRebalance(true));

      JMSContext ctx = addContext(getCF().createContext(JMSContext.SESSION_TRANSACTED));

      Queue testQueue = ctx.createQueue(testQueueName);


      final String groupID1 = "groupA";
      final String groupID2 = "groupB";
      final String groupID3 = "groupC";


      JMSProducer producer1 = ctx.createProducer().setProperty("JMSXGroupID", groupID1);
      JMSProducer producer2 = ctx.createProducer().setProperty("JMSXGroupID", groupID2);
      JMSProducer producer3 = ctx.createProducer().setProperty("JMSXGroupID", groupID3);

      JMSConsumer consumer1 = ctx.createConsumer(testQueue);
      JMSConsumer consumer2 = ctx.createConsumer(testQueue);

      ctx.start();

      for (int j = 0; j < 10; j++) {
         send(ctx, testQueue, groupID1, producer1, j);
      }
      for (int j = 10; j < 20; j++) {
         send(ctx, testQueue, groupID2, producer2, j);
      }
      for (int j = 20; j < 30; j++) {
         send(ctx, testQueue, groupID3, producer3, j);
      }

      ctx.commit();

      //First set of msgs should go to the first consumer only
      for (int j = 0; j < 10; j++) {
         TextMessage tm = (TextMessage) consumer1.receive(10000);
         assertNotNull(tm);
         tm.acknowledge();
         assertEquals("Message" + j, tm.getText());
         assertEquals(groupID1, tm.getStringProperty("JMSXGroupID"));
      }

      //Second set of msgs should go to the second consumers only
      for (int j = 10; j < 20; j++) {
         TextMessage tm = (TextMessage) consumer2.receive(10000);

         assertNotNull(tm);

         tm.acknowledge();

         assertEquals("Message" + j, tm.getText());

         assertEquals(groupID2, tm.getStringProperty("JMSXGroupID"));
      }

      //Third set of msgs should go to the first consumer only
      for (int j = 20; j < 30; j++) {
         TextMessage tm = (TextMessage) consumer1.receive(10000);

         assertNotNull(tm);

         tm.acknowledge();

         assertEquals("Message" + j, tm.getText());

         assertEquals(groupID3, tm.getStringProperty("JMSXGroupID"));
      }
      ctx.commit();

      //Add new consumer, that should cause rebalance
      JMSConsumer consumer3 = ctx.createConsumer(testQueue);

      for (int j = 0; j < 10; j++) {
         send(ctx, testQueue, groupID1, producer1, j);
      }
      for (int j = 10; j < 20; j++) {
         send(ctx, testQueue, groupID2, producer2, j);
      }
      for (int j = 20; j < 30; j++) {
         send(ctx, testQueue, groupID3, producer3, j);
      }
      ctx.commit();

      //First set of msgs should go to the first consumer only
      for (int j = 0; j < 10; j++) {
         TextMessage tm = (TextMessage) consumer1.receive(10000);
         assertNotNull(tm);
         tm.acknowledge();
         assertEquals("Message" + j, tm.getText());
         assertEquals(groupID1, tm.getStringProperty("JMSXGroupID"));
      }

      //Second set of msgs should go to the second consumers only
      for (int j = 10; j < 20; j++) {
         TextMessage tm = (TextMessage) consumer2.receive(10000);

         assertNotNull(tm);

         tm.acknowledge();

         assertEquals("Message" + j, tm.getText());

         assertEquals(groupID2, tm.getStringProperty("JMSXGroupID"));
      }

      //Third set of msgs should now go to the third consumer now
      for (int j = 20; j < 30; j++) {
         TextMessage tm = (TextMessage) consumer3.receive(10000);

         assertNotNull(tm);

         tm.acknowledge();

         assertEquals("Message" + j, tm.getText());

         assertEquals(groupID3, tm.getStringProperty("JMSXGroupID"));
      }

      ctx.commit();

      ctx.close();
   }

   /**
    * This tests ensures that when we have group rebalance and pause dispatch, the broker pauses dispatch of new
    * messages to consumers whilst rebalance and awaits existing inflight messages to be handled before restarting
    * dispatch with new reblanced group allocations, this allows us to provide a guarantee of message ordering even with
    * rebalance, at the expense that during rebalance dispatch will pause till all consumers with inflight messages are
    * handled.
    */
   @Test
   public void testGroupRebalancePauseDispatch() throws Exception {
      ConnectionFactory fact = getCF();
      assumeFalse(((ActiveMQConnectionFactory) fact).isAutoGroup(), "only makes sense withOUT auto-group");
      assumeTrue(((ActiveMQConnectionFactory) fact).getGroupID() == null, "only makes sense withOUT explicit group-id");
      String testQueueName = getName() + "_group_rebalance";

      server.createQueue(QueueConfiguration.of(testQueueName).setRoutingType(RoutingType.ANYCAST).setGroupRebalance(true).setGroupRebalancePauseDispatch(true));

      JMSContext ctx = addContext(getCF().createContext(JMSContext.SESSION_TRANSACTED));

      Queue testQueue = ctx.createQueue(testQueueName);


      final String groupID1 = "groupA";
      final String groupID2 = "groupB";
      final String groupID3 = "groupC";


      JMSProducer producer1 = ctx.createProducer().setProperty("JMSXGroupID", groupID1);
      JMSProducer producer2 = ctx.createProducer().setProperty("JMSXGroupID", groupID2);
      JMSProducer producer3 = ctx.createProducer().setProperty("JMSXGroupID", groupID3);

      JMSConsumer consumer1 = ctx.createConsumer(testQueue);
      JMSConsumer consumer2 = ctx.createConsumer(testQueue);

      ctx.start();

      for (int j = 0; j < 10; j++) {
         send(ctx, testQueue, groupID1, producer1, j);
      }
      for (int j = 10; j < 20; j++) {
         send(ctx, testQueue, groupID2, producer2, j);
      }
      for (int j = 20; j < 30; j++) {
         send(ctx, testQueue, groupID3, producer3, j);
      }

      ctx.commit();

      //First set of msgs should go to the first consumer only
      for (int j = 0; j < 10; j++) {
         TextMessage tm = (TextMessage) consumer1.receive(10000);
         assertNotNull(tm);
         tm.acknowledge();
         assertEquals("Message" + j, tm.getText());
         assertEquals(groupID1, tm.getStringProperty("JMSXGroupID"));
      }
      ctx.commit();


      //Second set of msgs should go to the second consumers only
      for (int j = 10; j < 20; j++) {
         TextMessage tm = (TextMessage) consumer2.receive(10000);

         assertNotNull(tm);

         tm.acknowledge();

         assertEquals("Message" + j, tm.getText());

         assertEquals(groupID2, tm.getStringProperty("JMSXGroupID"));
      }
      ctx.commit();


      //Add new consumer but where third set we have not consumed so should inflight, that should cause rebalance
      JMSConsumer consumer3 = ctx.createConsumer(testQueue);

      //Send next set of messages
      for (int j = 0; j < 10; j++) {
         send(ctx, testQueue, groupID1, producer1, j);
      }
      for (int j = 10; j < 20; j++) {
         send(ctx, testQueue, groupID2, producer2, j);
      }
      for (int j = 20; j < 30; j++) {
         send(ctx, testQueue, groupID3, producer3, j);
      }
      ctx.commit();

      //Ensure we dont get anything on the other consumers, whilst we rebalance and there is inflight messages. - e.g. ensure ordering guarentee.
      assertNull(consumer2.receiveNoWait());
      assertNull(consumer3.receiveNoWait());

      //Ensure the inflight set of msgs should go to the first consumer only
      for (int j = 20; j < 30; j++) {
         TextMessage tm = (TextMessage) consumer1.receive(10000);

         assertNotNull(tm);

         tm.acknowledge();

         assertEquals("Message" + j, tm.getText());

         assertEquals(groupID3, tm.getStringProperty("JMSXGroupID"));
      }
      ctx.commit();

      //Now we cleared the "inflightm messages" expect that consumers 1,2 and 3 are rebalanced and the messages sent earlier are received.

      //First set of msgs should go to the first consumer only
      for (int j = 0; j < 10; j++) {
         TextMessage tm = (TextMessage) consumer1.receive(10000);
         assertNotNull(tm);
         tm.acknowledge();
         assertEquals("Message" + j, tm.getText());
         assertEquals(groupID1, tm.getStringProperty("JMSXGroupID"));
      }

      //Second set of msgs should go to the second consumers only
      for (int j = 10; j < 20; j++) {
         TextMessage tm = (TextMessage) consumer2.receive(10000);

         assertNotNull(tm);

         tm.acknowledge();

         assertEquals("Message" + j, tm.getText());

         assertEquals(groupID2, tm.getStringProperty("JMSXGroupID"));
      }

      //Third set of msgs should now go to the third consumer now
      for (int j = 20; j < 30; j++) {
         TextMessage tm = (TextMessage) consumer3.receive(10000);

         assertNotNull(tm);

         tm.acknowledge();

         assertEquals("Message" + j, tm.getText());

         assertEquals(groupID3, tm.getStringProperty("JMSXGroupID"));
      }

      ctx.commit();

      ctx.close();
   }



   @Test
   public void testGroupFirstKey() throws Exception {
      String customFirstGroupKey = "my-custom-key";
      ConnectionFactory fact = getCF();
      assumeFalse(((ActiveMQConnectionFactory) fact).isAutoGroup(), "only makes sense withOUT auto-group");
      assumeTrue(((ActiveMQConnectionFactory) fact).getGroupID() == null, "only makes sense withOUT explicit group-id");
      String testQueueName = getName() + "_group_first_key";

      server.createQueue(QueueConfiguration.of(testQueueName).setRoutingType(RoutingType.ANYCAST).setGroupRebalance(true).setGroupFirstKey(customFirstGroupKey));

      JMSContext ctx = addContext(getCF().createContext(JMSContext.SESSION_TRANSACTED));

      Queue testQueue = ctx.createQueue(testQueueName);


      final String groupID1 = "groupA";
      final String groupID2 = "groupB";
      final String groupID3 = "groupC";


      JMSProducer producer1 = ctx.createProducer().setProperty("JMSXGroupID", groupID1);
      JMSProducer producer2 = ctx.createProducer().setProperty("JMSXGroupID", groupID2);
      JMSProducer producer3 = ctx.createProducer().setProperty("JMSXGroupID", groupID3);

      JMSConsumer consumer1 = ctx.createConsumer(testQueue);
      JMSConsumer consumer2 = ctx.createConsumer(testQueue);

      ctx.start();

      for (int j = 0; j < 10; j++) {
         send(ctx, testQueue, groupID1, producer1, j);
      }
      for (int j = 10; j < 20; j++) {
         send(ctx, testQueue, groupID2, producer2, j);
      }
      for (int j = 20; j < 30; j++) {
         send(ctx, testQueue, groupID3, producer3, j);
      }

      ctx.commit();

      //First set of msgs should go to the first consumer only
      for (int j = 0; j < 10; j++) {
         TextMessage tm = (TextMessage) consumer1.receive(10000);
         assertNotNull(tm);
         tm.acknowledge();
         assertEquals("Message" + j, tm.getText());
         assertEquals(groupID1, tm.getStringProperty("JMSXGroupID"));
         if (j == 0) {
            assertTrue(tm.getBooleanProperty(customFirstGroupKey));
         } else {
            assertFalse(tm.propertyExists(customFirstGroupKey));
         }
      }

      //Second set of msgs should go to the second consumers only
      for (int j = 10; j < 20; j++) {
         TextMessage tm = (TextMessage) consumer2.receive(10000);

         assertNotNull(tm);

         tm.acknowledge();

         assertEquals("Message" + j, tm.getText());

         assertEquals(groupID2, tm.getStringProperty("JMSXGroupID"));

         if (j == 10) {
            assertTrue(tm.getBooleanProperty(customFirstGroupKey));
         } else {
            assertFalse(tm.propertyExists(customFirstGroupKey));
         }
      }

      //Third set of msgs should go to the first consumer only
      for (int j = 20; j < 30; j++) {
         TextMessage tm = (TextMessage) consumer1.receive(10000);

         assertNotNull(tm);

         tm.acknowledge();

         assertEquals("Message" + j, tm.getText());

         assertEquals(groupID3, tm.getStringProperty("JMSXGroupID"));

         if (j == 20) {
            assertTrue(tm.getBooleanProperty(customFirstGroupKey));
         } else {
            assertFalse(tm.propertyExists(customFirstGroupKey));
         }
      }
      ctx.commit();

      ctx.close();
   }

   @Test
   public void testGroupDisable() throws Exception {
      ConnectionFactory fact = getCF();
      assumeFalse(((ActiveMQConnectionFactory) fact).isAutoGroup(), "only makes sense withOUT auto-group");
      assumeTrue(((ActiveMQConnectionFactory) fact).getGroupID() == null, "only makes sense withOUT explicit group-id");
      String testQueueName = getName() + "_group_disable";

      server.createQueue(QueueConfiguration.of(testQueueName).setRoutingType(RoutingType.ANYCAST).setGroupBuckets(0));

      JMSContext ctx = addContext(getCF().createContext(JMSContext.SESSION_TRANSACTED));

      Queue testQueue = ctx.createQueue(testQueueName);


      final String groupID1 = "groupA";
      final String groupID2 = "groupB";
      final String groupID3 = "groupC";


      JMSProducer producer1 = ctx.createProducer().setProperty("JMSXGroupID", groupID1);
      JMSProducer producer2 = ctx.createProducer().setProperty("JMSXGroupID", groupID2);
      JMSProducer producer3 = ctx.createProducer().setProperty("JMSXGroupID", groupID3);

      JMSConsumer consumer1 = ctx.createConsumer(testQueue);
      JMSConsumer consumer2 = ctx.createConsumer(testQueue);
      JMSConsumer consumer3 = ctx.createConsumer(testQueue);

      ctx.start();

      for (int j = 0; j < 10; j++) {
         send(ctx, testQueue, groupID1, producer1, j);
      }
      for (int j = 10; j < 20; j++) {
         send(ctx, testQueue, groupID2, producer2, j);
      }
      for (int j = 20; j < 30; j++) {
         send(ctx, testQueue, groupID3, producer3, j);
      }

      ctx.commit();

      //Msgs should just round robin and ignore grouping semantics
      int j = 0;
      while (j < 30) {
         TextMessage tm = (TextMessage) consumer1.receive(10000);
         assertNotNull(tm);
         tm.acknowledge();
         assertEquals("Message" + j, tm.getText());
         assertEquals(tm.getStringProperty("JMSXGroupID"), j < 10 ? groupID1 : j < 20 ? groupID2 : groupID3);
         j++;

         tm = (TextMessage) consumer2.receive(10000);
         assertNotNull(tm);
         tm.acknowledge();
         assertEquals("Message" + j, tm.getText());
         assertEquals(tm.getStringProperty("JMSXGroupID"), j < 10 ? groupID1 : j < 20 ? groupID2 : groupID3);
         j++;

         tm = (TextMessage) consumer3.receive(10000);
         assertNotNull(tm);
         tm.acknowledge();
         assertEquals("Message" + j, tm.getText());
         assertEquals(tm.getStringProperty("JMSXGroupID"), j < 10 ? groupID1 : j < 20 ? groupID2 : groupID3);
         j++;
      }

      ctx.commit();

      ctx.close();
   }


   private void send(JMSContext ctx, Queue testQueue, String groupID1, JMSProducer producer1, int j) throws JMSException {
      TextMessage message = ctx.createTextMessage("Message" + j);
      producer1.send(testQueue, message);
      String prop = message.getStringProperty("JMSXGroupID");
      assertNotNull(prop);
      assertEquals(groupID1, prop);
   }

   @Test
   public void testGroupBucketUsingAddressQueueParameters() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         String testQueueName = getName() + "group_bucket_param";

         Queue queue = session.createQueue(testQueueName + "?group-buckets=4");
         assertEquals(testQueueName, queue.getQueueName());


         ActiveMQDestination a = (ActiveMQDestination) queue;
         assertEquals(Integer.valueOf(4), a.getQueueAttributes().getGroupBuckets());
         assertEquals(Integer.valueOf(4), a.getQueueConfiguration().getGroupBuckets());

         MessageProducer producer = session.createProducer(queue);


         QueueBinding queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(testQueueName));
         assertEquals(4, queueBinding.getQueue().getGroupBuckets());
      } finally {
         connection.close();
      }
   }

   @Test
   public void testGroupRebalanceUsingAddressQueueParameters() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         String testQueueName = getName() + "group_rebalance_param";

         Queue queue = session.createQueue(testQueueName + "?group-rebalance=true");
         assertEquals(testQueueName, queue.getQueueName());


         ActiveMQDestination a = (ActiveMQDestination) queue;
         assertTrue(a.getQueueAttributes().getGroupRebalance());
         assertTrue(a.getQueueConfiguration().isGroupRebalance());

         MessageProducer producer = session.createProducer(queue);


         QueueBinding queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(testQueueName));
         assertTrue(queueBinding.getQueue().isGroupRebalance());
      } finally {
         connection.close();
      }
   }


   @Test
   public void testGroupFirstKeyUsingAddressQueueParameters() throws Exception {
      ConnectionFactory fact = getCF();
      Connection connection = fact.createConnection();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         String testQueueName = getName() + "group_first_key_param";

         Queue queue = session.createQueue(testQueueName + "?group-first-key=my-custom-key");
         assertEquals(testQueueName, queue.getQueueName());


         ActiveMQDestination a = (ActiveMQDestination) queue;
         assertEquals("my-custom-key", a.getQueueAttributes().getGroupFirstKey().toString());

         MessageProducer producer = session.createProducer(queue);


         QueueBinding queueBinding = (QueueBinding) server.getPostOffice().getBinding(SimpleString.of(testQueueName));
         assertEquals("my-custom-key", queueBinding.getQueue().getGroupFirstKey().toString());
      } finally {
         connection.close();
      }
   }

}
