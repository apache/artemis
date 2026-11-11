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
package org.apache.activemq.artemis.tests.integration.cluster.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Bindable;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.cluster.impl.Redistributor;
import org.apache.activemq.artemis.core.server.group.impl.GroupingHandlerConfiguration;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class MessageRedistributionTest extends ClusterTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      start();
   }

   private void start() throws Exception {
      setupServers();

      setRedistributionDelay(0);
   }

   protected boolean isNetty() {
      return false;
   }

   @Override
   protected void setSessionFactoryCreateLocator(int node, boolean ha, TransportConfiguration serverTotc) {
      super.setSessionFactoryCreateLocator(node, ha, serverTotc);

      locators[node].setConsumerWindowSize(0);

   }

   @Test
   public void testRedistributionWithMessageGroups() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      logger.debug("Doing test");

      getServer(0).getConfiguration().setGroupingHandlerConfiguration(new GroupingHandlerConfiguration().setName(SimpleString.of("handler")).setType(GroupingHandlerConfiguration.TYPE.LOCAL).setAddress(SimpleString.of("queues")));
      getServer(1).getConfiguration().setGroupingHandlerConfiguration(new GroupingHandlerConfiguration().setName(SimpleString.of("handler")).setType(GroupingHandlerConfiguration.TYPE.REMOTE).setAddress(SimpleString.of("queues")));

      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);

      addConsumer(1, 1, "queue0", null);
      waitForBindings(0, "queues.testaddress", 1, 0, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 1, 1, false);
      waitForBindings(1, "queues.testaddress", 1, 0, false);

      //send some grouped messages before we add the consumer to node 0 so we guarantee its pinned to node 1
      sendWithProperty(0, "queues.testaddress", 10, false, Message.HDR_GROUP_ID, SimpleString.of("grp1"));
      addConsumer(0, 0, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 1, 1, false);
      waitForBindings(1, "queues.testaddress", 1, 1, false);
      //now send some non grouped messages
      send(0, "queues.testaddress", 10, false, null);

      //consume half of the grouped messages from node 1
      for (int i = 0; i < 5; i++) {
         ClientMessage message = getConsumer(1).receive(1000);
         assertNotNull(message);
         message.acknowledge();
         assertNotNull(message.getSimpleStringProperty(Message.HDR_GROUP_ID));
      }

      //now consume the non grouped messages from node 1 where they are pinned
      for (int i = 0; i < 5; i++) {
         ClientMessage message = getConsumer(0).receive(5000);
         assertNotNull(message, "" + i);
         message.acknowledge();
         assertNull(message.getSimpleStringProperty(Message.HDR_GROUP_ID));
      }

      ClientMessage clientMessage = getConsumer(0).receiveImmediate();
      assertNull(clientMessage);

      // i know the last 5 messages consumed won't be acked yet so i wait for 15
      waitForMessages(1, "queues.testaddress", 15);

      //now removing it will start redistribution but only for non grouped messages
      removeConsumer(1);

      //consume the non grouped messages
      for (int i = 0; i < 5; i++) {
         ClientMessage message = getConsumer(0).receive(5000);
         assertNotNull(message, "" + i);
         message.acknowledge();
         assertNull(message.getSimpleStringProperty(Message.HDR_GROUP_ID));
      }

      clientMessage = getConsumer(0).receiveImmediate();
      assertNull(clientMessage);

      removeConsumer(0);

      addConsumer(1, 1, "queue0", null);

      //now we see the grouped messages are still on the same node
      for (int i = 0; i < 5; i++) {
         ClientMessage message = getConsumer(1).receive(1000);
         assertNotNull(message);
         message.acknowledge();
         assertNotNull(message.getSimpleStringProperty(Message.HDR_GROUP_ID));
      }
      logger.debug("Test done");
   }

   @Test
   public void testRedistributionWithMultipleQueuesOnTheSameAddress() throws Exception {
      final int MESSAGE_COUNT = 10;
      final String ADDRESS = "myAddress";
      final String QUEUE0 = "queue0";
      final String QUEUE1 = "queue1";

      getServer(0).getConfiguration().addAddressSetting(ADDRESS, new AddressSettings().setRedistributionDelay(0));
      getServer(1).getConfiguration().addAddressSetting(ADDRESS, new AddressSettings().setRedistributionDelay(0));

      setupClusterConnection("cluster0", ADDRESS, MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster1", ADDRESS, MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);

      startServers(0, 1);

      waitForTopology(servers[0], 2);
      waitForTopology(servers[1], 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, ADDRESS, QUEUE0, null, false);
      createQueue(0, ADDRESS, QUEUE1, null, false);

      createQueue(1, ADDRESS, QUEUE0, null, false);
      createQueue(1, ADDRESS, QUEUE1, null, false);

      addConsumer(0, 0, QUEUE0, null, true, null, null, 0);
      addConsumer(1, 1, QUEUE0, null, true, null, null, 0);

      waitForBindings(0, ADDRESS, 2, 1, true);
      waitForBindings(0, ADDRESS, 2, 1, false);

      waitForBindings(1, ADDRESS, 2, 1, true);
      waitForBindings(1, ADDRESS, 2, 1, false);

      send(0, ADDRESS, MESSAGE_COUNT, true, null, RoutingType.MULTICAST, null);

      { // make sure all the messages were delivered to the proper queues & nodes
         Wait.assertEquals(5L, () -> servers[0].locateQueue(QUEUE0).getMessagesAdded(), 2000, 100);
         Wait.assertEquals(5L, () -> servers[1].locateQueue(QUEUE0).getMessagesAdded(), 2000, 100);

         Wait.assertEquals(10L, () -> servers[0].locateQueue(QUEUE1).getMessageCount(), 2000, 100);
         Wait.assertEquals(0L, () -> servers[1].locateQueue(QUEUE1).getMessageCount(), 2000, 100);
      }

      for (int i = 0; i < MESSAGE_COUNT / 2; i++) {
         {
            ClientMessage m = consumers[0].getConsumer().receive(1000);
            assertNotNull(m);
            m.acknowledge();
         }
         {
            ClientMessage m = consumers[1].getConsumer().receive(1000);
            assertNotNull(m);
            m.acknowledge();
         }
      }

      { // make sure all the messages were consumed propertly
         Wait.assertEquals(5L, () -> servers[0].locateQueue(QUEUE0).getMessagesAcknowledged(), 2000, 100);
         Wait.assertEquals(0L, () -> servers[0].locateQueue(QUEUE0).getMessageCount(), 2000, 100);

         Wait.assertEquals(5L, () -> servers[1].locateQueue(QUEUE0).getMessagesAcknowledged(), 2000, 100);
         Wait.assertEquals(0L, () -> servers[1].locateQueue(QUEUE0).getMessageCount(), 2000, 100);
      }

      // add consumer to force redistribution of messages to node 1
      addConsumer(2, 1, QUEUE1, null);
      waitForBindings(1, ADDRESS, 2, 2, true);
      waitForBindings(0, ADDRESS, 2, 2, false);

      Wait.assertEquals(10L, () -> servers[1].locateQueue(QUEUE1).getMessageCount(), 2000, 100);
      Wait.assertEquals(0L, () -> servers[0].locateQueue(QUEUE1).getMessageCount(), 2000, 100);

      // ensure no messages were inadvertently redistributed to the wrong queue (i.e. the main point of this test)
      Wait.assertEquals(0L, () -> servers[1].locateQueue(QUEUE0).getMessageCount(), 2000, 100);
   }

   @Test
   public void testRedistributionStopsWhenConsumerAdded() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      logger.debug("Doing test");

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(2, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);
      waitForBindings(2, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 2, 0, false);
      waitForBindings(1, "queues.testaddress", 2, 1, false);
      waitForBindings(2, "queues.testaddress", 2, 1, false);

      send(0, "queues.testaddress", 100, false, null);

      removeConsumer(0);
      addConsumer(0, 0, "queue0", null);

      Bindable bindable = servers[0].getPostOffice().getBinding(SimpleString.of("queue0")).getBindable();
      String debug = ((QueueImpl) bindable).debug();
      assertFalse(debug.contains(Redistributor.class.getName()));
      logger.debug("Test done");
   }

   @Test
   public void testRedistributionWhenConsumerIsClosed() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      logger.debug("Doing test");

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(2, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 1, "queue0", null);
      addConsumer(2, 2, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 2, 2, false);
      waitForBindings(1, "queues.testaddress", 2, 2, false);
      waitForBindings(2, "queues.testaddress", 2, 2, false);

      send(0, "queues.testaddress", 20, false, null);

      getReceivedOrder(0);
      int[] ids1 = getReceivedOrder(1);
      getReceivedOrder(2);

      removeConsumer(1);

      verifyReceiveRoundRobinInSomeOrderWithCounts(false, ids1, 0, 2);

      logger.debug("Test done");
   }

   protected void verifyReceiveRoundRobinInSomeOrderWithCounts(final boolean ack,
                                                               final int[] messageCounts,
                                                               final int... consumerIDs) throws Exception {
      List<LinkedList<Integer>> receivedCounts = new ArrayList<>();

      Set<Integer> counts = new HashSet<>();

      for (int consumerID : consumerIDs) {
         ConsumerHolder holder = consumers[consumerID];

         if (holder == null) {
            throw new IllegalArgumentException("No consumer at " + consumerID);
         }

         LinkedList<Integer> list = new LinkedList<>();

         receivedCounts.add(list);

         for (long timeout = System.currentTimeMillis() + 5000; !matchCounters(messageCounts, counts) && timeout > System.currentTimeMillis();) {
            ClientMessage message = holder.consumer.receiveImmediate();

            if (message != null) {
               int count = (Integer) message.getObjectProperty(ClusterTestBase.COUNT_PROP);

               checkMessageBody(message);

               assertFalse(counts.contains(count));

               counts.add(count);

               list.add(count);

               if (ack) {
                  message.acknowledge();
               }
            }
         }
      }

      assertTrue(matchCounters(messageCounts, counts));

      @SuppressWarnings("unchecked")
      LinkedList<Integer>[] lists = new LinkedList[consumerIDs.length];

      for (int i = 0; i < messageCounts.length; i++) {
         for (LinkedList<Integer> list : receivedCounts) {
            int elem = list.get(0);

            if (elem == messageCounts[i]) {
               lists[i] = list;

               break;
            }
         }
      }
      int index = 0;

      for (int messageCount : messageCounts) {
         LinkedList<Integer> list = lists[index];

         assertNotNull(list);

         int elem = list.poll();

         assertEquals(messageCount, elem);

         index++;

         if (index == consumerIDs.length) {
            index = 0;
         }
      }

   }

   private static boolean matchCounters(int[] messageCounts, Set<Integer> counts) {
      for (int messageCount : messageCounts) {
         if (!counts.contains(messageCount)) {
            return false;
         }
      }
      return true;
   }

   @Test
   public void testRedistributionWhenConsumerIsClosedDifferentQueues() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, true);
      createQueue(1, "queues.testaddress", "queue1", null, true);
      createQueue(2, "queues.testaddress", "queue2", null, true);

      ClientSession sess0 = sfs[0].createSession();
      ClientConsumer consumer0 = sess0.createConsumer("queue0");

      ClientSession sess1 = sfs[1].createSession();
      ClientConsumer consumer1 = sess1.createConsumer("queue1");

      ClientSession sess2 = sfs[2].createSession();
      ClientConsumer consumer2 = sess2.createConsumer("queue2");

      ClientProducer producer0 = sess0.createProducer("queues.testaddress");

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 2, 2, false);
      waitForBindings(1, "queues.testaddress", 2, 2, false);
      waitForBindings(2, "queues.testaddress", 2, 2, false);

      final int NUMBER_OF_MESSAGES = 100;

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         producer0.send(sess0.createMessage(true).putIntProperty("count", i));
      }

      sess0.start();
      sess1.start();
      sess2.start();

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         ClientMessage msg = consumer0.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();
         assertEquals(i, msg.getIntProperty("count").intValue());
      }

      assertNull(consumer0.receiveImmediate());

      consumer1.close();

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         ClientMessage msg = consumer2.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();
         assertEquals(i, msg.getIntProperty("count").intValue());
      }

      assertNull(consumer2.receiveImmediate());
      assertNull(consumer0.receiveImmediate());

      consumer1 = sess1.createConsumer("queue1");
      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         ClientMessage msg = consumer1.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();
         assertEquals(i, msg.getIntProperty("count").intValue());
      }

      assertNull(consumer0.receiveImmediate());
      assertNull(consumer1.receiveImmediate());
      assertNull(consumer2.receiveImmediate());

      logger.debug("Test done");
   }

   @Test
   public void testRedistributionWhenConsumerIsClosedNotConsumersOnAllNodes() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(2, "queues.testaddress", "queue0", null, false);

      addConsumer(1, 1, "queue0", null);
      addConsumer(2, 2, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 0, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 2, 2, false);
      waitForBindings(1, "queues.testaddress", 2, 1, false);
      waitForBindings(2, "queues.testaddress", 2, 1, false);

      send(0, "queues.testaddress", 20, false, null);

      int[] ids1 = getReceivedOrder(1);
      getReceivedOrder(2);

      removeConsumer(1);

      verifyReceiveRoundRobinInSomeOrderWithCounts(false, ids1, 2);
   }

   @Test
   public void testNoRedistributionWhenConsumerIsClosedForwardWhenNoConsumersTrue() throws Exception {
      // x
      setupCluster(MessageLoadBalancingType.STRICT);

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(2, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 1, "queue0", null);
      addConsumer(2, 2, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 2, 2, false);
      waitForBindings(1, "queues.testaddress", 2, 2, false);
      waitForBindings(2, "queues.testaddress", 2, 2, false);

      send(0, "queues.testaddress", 20, false, null);

      removeConsumer(1);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);
      waitForBindings(2, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 2, 1, false);
      waitForBindings(1, "queues.testaddress", 2, 2, false);
      waitForBindings(2, "queues.testaddress", 2, 1, false);

      addConsumer(1, 1, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 2, 2, false);
      waitForBindings(1, "queues.testaddress", 2, 2, false);
      waitForBindings(2, "queues.testaddress", 2, 2, false);

      verifyReceiveRoundRobinInSomeOrder(20, 0, 1, 2);
   }

   @Test
   public void testNoRedistributionWhenConsumerIsClosedNoConsumersOnOtherNodes() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(2, "queues.testaddress", "queue0", null, false);

      addConsumer(1, 1, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 0, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 2, 1, false);
      waitForBindings(1, "queues.testaddress", 2, 0, false);
      waitForBindings(2, "queues.testaddress", 2, 1, false);

      send(0, "queues.testaddress", 20, false, null);

      removeConsumer(1);

      waitForBindings(0, "queues.testaddress", 1, 0, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);
      waitForBindings(2, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 2, 0, false);
      waitForBindings(1, "queues.testaddress", 2, 0, false);
      waitForBindings(2, "queues.testaddress", 2, 0, false);

      addConsumer(1, 1, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 0, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 2, 1, false);
      waitForBindings(1, "queues.testaddress", 2, 0, false);
      waitForBindings(2, "queues.testaddress", 2, 1, false);

      verifyReceiveAll(20, 1);
   }

   @Test
   public void testRedistributeWithScheduling() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      AddressSettings setting = new AddressSettings().setRedeliveryDelay(100);
      servers[0].getAddressSettingsRepository().addMatch("queues.testaddress", setting);
      servers[0].getAddressSettingsRepository().addMatch("queue0", setting);
      servers[1].getAddressSettingsRepository().addMatch("queue0", setting);
      servers[1].getAddressSettingsRepository().addMatch("queues.testaddress", setting);

      startServers(0);

      setupSessionFactory(0, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);

      ClientSession session0 = sfs[0].createSession(false, false, false);

      ClientProducer prod0 = session0.createProducer("queues.testaddress");

      final int numberOfMessages = 10;

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage msg = session0.createMessage(true);
         msg.putIntProperty("key", i);

         byte[] bytes = new byte[24];

         ByteBuffer bb = ByteBuffer.wrap(bytes);

         bb.putLong(i);

         msg.putBytesProperty(Message.HDR_BRIDGE_DUPLICATE_ID, bytes);

         prod0.send(msg);

         session0.commit();
      }

      session0.close();

      session0 = sfs[0].createSession(true, false, false);

      ClientConsumer consumer0 = session0.createConsumer("queue0");

      session0.start();

      List<Xid> xids = new ArrayList<>();

      for (int i = 0; i < numberOfMessages; i++) {
         Xid xid = newXID();

         session0.start(xid, XAResource.TMNOFLAGS);

         ClientMessage msg = consumer0.receive(5000);

         msg.acknowledge();

         session0.end(xid, XAResource.TMSUCCESS);

         session0.prepare(xid);

         xids.add(xid);
      }

      session0.close();

      sfs[0].close();
      sfs[0] = null;

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(2, "queues.testaddress", "queue0", null, false);

      ClientSession session1 = sfs[1].createSession(false, false);
      session1.start();
      ClientConsumer consumer1 = session1.createConsumer("queue0");

      waitForBindings(0, "queues.testaddress", 1, 0, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 2, 1, false);
      waitForBindings(1, "queues.testaddress", 2, 0, false);
      waitForBindings(2, "queues.testaddress", 2, 1, false);

      session0 = sfs[0].createSession(true, false, false);

      for (Xid xid : xids) {
         session0.rollback(xid);
      }

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage msg = consumer1.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();
      }

      session1.commit();

   }

   @Test
   public void testRedistributionWhenConsumerIsClosedQueuesWithFilters() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      String filter1 = "giraffe";
      String filter2 = "platypus";

      createQueue(0, "queues.testaddress", "queue0", filter1, false);
      createQueue(1, "queues.testaddress", "queue0", filter2, false);
      createQueue(2, "queues.testaddress", "queue0", filter1, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 1, "queue0", null);
      addConsumer(2, 2, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 2, 2, false);
      waitForBindings(1, "queues.testaddress", 2, 2, false);
      waitForBindings(2, "queues.testaddress", 2, 2, false);

      send(0, "queues.testaddress", 20, false, filter1);

      int[] ids0 = getReceivedOrder(0);
      getReceivedOrder(1);
      getReceivedOrder(2);

      removeConsumer(0);

      verifyReceiveRoundRobinInSomeOrderWithCounts(false, ids0, 2);
   }

   @Test
   public void testRedistributionWhenConsumerIsClosedConsumersWithFilters() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      String filter1 = "giraffe";
      String filter2 = "platypus";

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(2, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", filter1);
      addConsumer(1, 1, "queue0", filter2);
      addConsumer(2, 2, "queue0", filter1);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);
      waitForBindings(2, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 2, 2, false);
      waitForBindings(1, "queues.testaddress", 2, 2, false);
      waitForBindings(2, "queues.testaddress", 2, 2, false);

      send(0, "queues.testaddress", 20, false, filter1);

      int[] ids0 = getReceivedOrder(0);
      getReceivedOrder(1);
      getReceivedOrder(2);

      removeConsumer(0);

      verifyReceiveRoundRobinInSomeOrderWithCounts(false, ids0, 2);
   }

   @Test
   public void testRedistributionWithPrefixesWhenRemoteConsumerIsAdded() throws Exception {

      for (int i = 0; i <= 2; i++) {
         ActiveMQServer server = getServer(i);
         for (TransportConfiguration c : server.getConfiguration().getAcceptorConfigurations()) {
            c.getExtraParams().putIfAbsent("anycastPrefix", "jms.queue.");
         }
      }

      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      String name = "queues.queue";

      createQueue(0, name, name, null, false, RoutingType.ANYCAST);
      createQueue(1, name, name, null, false, RoutingType.ANYCAST);
      createQueue(2, name, name, null, false, RoutingType.ANYCAST);

      addConsumer(0, 0, name, null);

      waitForBindings(0, name, 1, 1, true);
      waitForBindings(1, name, 1, 0, true);
      waitForBindings(2, name, 1, 0, true);

      waitForBindings(0, name, 2, 0, false);
      waitForBindings(1, name, 2, 1, false);
      waitForBindings(2, name, 2, 1, false);

      removeConsumer(0);

      send(0, "jms.queue." + name, 20, false, null);

      addConsumer(1, 1, name, null);

      verifyReceiveAll(20, 1);
      verifyNotReceive(1);
   }

   @Test
   public void testRedistributionWhenRemoteConsumerIsAdded() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(2, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);
      waitForBindings(2, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 2, 0, false);
      waitForBindings(1, "queues.testaddress", 2, 1, false);
      waitForBindings(2, "queues.testaddress", 2, 1, false);

      send(0, "queues.testaddress", 20, false, null);

      removeConsumer(0);

      addConsumer(1, 1, "queue0", null);

      verifyReceiveAll(20, 1);
      verifyNotReceive(1);
   }

   @Test
   public void testRedistributionWithFqqnAnycast() throws Exception {
      internalTestRedistributionWithFqqn(RoutingType.ANYCAST);
   }

   @Test
   public void testRedistributionWithFqqnMulticast() throws Exception {
      internalTestRedistributionWithFqqn(RoutingType.MULTICAST);
   }

   private void internalTestRedistributionWithFqqn(RoutingType routingType) throws Exception {
      final String ADDRESS = "myAddress";
      final String QUEUE = "myQueue";
      final String FQQN = CompositeAddress.toFullyQualified(ADDRESS, QUEUE);
      AddressSettings as = new AddressSettings().setRedistributionDelay(0);
      getServer(0).getAddressSettingsRepository().addMatch(ADDRESS, as);
      getServer(1).getAddressSettingsRepository().addMatch(ADDRESS, as);
      setupCluster(ADDRESS, MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      createQueue(0, ADDRESS, QUEUE, null, false, routingType);
      createQueue(0, ADDRESS, "extra", null, false, routingType);
      waitForBindings(0, ADDRESS, 2, 0, true);
      waitForBindings(1, ADDRESS, 2, 0, false);

      send(0, FQQN, 20, false, null, routingType, null);

      setupSessionFactory(1, isNetty());
      createQueue(1, ADDRESS, QUEUE, null, false, routingType);
      waitForBindings(0, ADDRESS, 1, 0, false);
      waitForBindings(1, ADDRESS, 1, 0, true);

      addConsumer(1, 1, FQQN, null);
      waitForBindings(1, ADDRESS, 1, 1, true);

      verifyReceiveAll(20, 1);
      verifyNotReceive(1);
   }

   @Test
   public void testRedistributionWithFqqnJmsQueue() throws Exception {
      final String ADDRESS = "myAddress";
      final String QUEUE = "myQueue";
      final String FQQN = CompositeAddress.toFullyQualified(ADDRESS, QUEUE);

      AddressSettings as = new AddressSettings().setRedistributionDelay(0);
      getServer(0).getAddressSettingsRepository().addMatch(ADDRESS, as);
      getServer(1).getAddressSettingsRepository().addMatch(ADDRESS, as);
      setupCluster(ADDRESS, MessageLoadBalancingType.ON_DEMAND);
      getServer(0).getConfiguration().setName("0");
      getServer(1).getConfiguration().setName("1");
      startServers(0, 1);

      ConnectionFactory cf0 = new ActiveMQConnectionFactory("vm://0");
      ConnectionFactory cf1 = new ActiveMQConnectionFactory("vm://1");

      try (Connection connection0 = cf0.createConnection();
           Connection connection1 = cf1.createConnection()) {
         javax.jms.Queue sendTo = ActiveMQJMSClient.createQueue(FQQN);
         javax.jms.Queue consumeFrom = ActiveMQJMSClient.createQueue(FQQN);

         setupSessionFactory(0, isNetty());
         createQueue(0, ADDRESS, QUEUE, null, false, RoutingType.ANYCAST);
         waitForBindings(0, ADDRESS, 1, 0, true);
         waitForBindings(1, ADDRESS, 1, 0, false);

         Session session0 = connection0.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session0.createProducer(sendTo);

         final int numMessages = 10;
         for (int i = 0; i < numMessages; i++) {
            TextMessage message = session0.createTextMessage("This is text message " + i);
            producer.send(message);
         }
         producer.close();
         assertEquals(numMessages, servers[0].locateQueue(QUEUE).getMessageCount());

         setupSessionFactory(1, isNetty());
         createQueue(1, ADDRESS, QUEUE, null, false, RoutingType.ANYCAST);
         waitForBindings(1, ADDRESS, 1, 0, true);
         waitForBindings(0, ADDRESS, 1, 0, false);

         Session session1 = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         connection1.start();
         MessageConsumer consumer1 = session1.createConsumer(consumeFrom);
         waitForBindings(1, ADDRESS, 1, 1, true);
         javax.jms.Message message1;
         for (int i = 0; i < numMessages; i++) {
            message1 = consumer1.receive(5000);
            assertNotNull(message1);
         }
      }
   }

   @Test
   public void testRedistributionWhenRemoteConsumerIsAddedLbOffWithRedistribution() throws Exception {
      setupCluster(MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION);

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(2, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);
      waitForBindings(2, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 2, 0, false);
      waitForBindings(1, "queues.testaddress", 2, 1, false);
      waitForBindings(2, "queues.testaddress", 2, 1, false);

      send(0, "queues.testaddress", 20, false, null);

      removeConsumer(0);

      addConsumer(1, 1, "queue0", null);

      verifyReceiveAll(20, 1);
      verifyNotReceive(1);
   }

   @Test
   public void testRedistributionOnlyWhenLocalRemovedLbOffWithRedistribution() throws Exception {
      setupCluster(MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION);

      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 1, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 1, 1, false);
      waitForBindings(1, "queues.testaddress", 1, 1, false);

      send(0, "queues.testaddress", 2, false, null);

      verifyNotReceive(1);

      removeConsumer(0);

      verifyReceiveAll(2, 1);
   }

   @Test
   public void testRedistributionToRemoteConsumerFromNewQueueLbOffWithRedistribution() throws Exception {

      String address = "test.address";
      String queue = "test.address";
      String clusterAddress = "test";
      AddressSettings settings = new AddressSettings().setRedistributionDelay(0).setAutoCreateAddresses(true).setAutoCreateQueues(true);
      RoutingType routingType = RoutingType.ANYCAST;

      getServer(0).getAddressSettingsRepository().addMatch(address, settings);
      getServer(1).getAddressSettingsRepository().addMatch(address, settings);

      setupClusterConnection("cluster0", clusterAddress, MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster0", clusterAddress, MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION, 1, isNetty(), 1, 0);

      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, address, queue, null, true, routingType);
      addConsumer(0, 0, queue, null);
      waitForBindings(0, address, 1, 1, true);
      waitForBindings(1, address, 1, 1, false);

      createQueue(1, address, queue, null, true, routingType);
      waitForBindings(1, address, 1, 0, true);

      waitForBindings(0, address, 1, 0, false);
      waitForBindings(1, address, 1, 1, false);

      final int noMessages = 10;
      send(1, address, noMessages, true, null, null);
      verifyReceiveAll(noMessages, 0);

   }

   @Test
   public void testRedistributionToRemoteMulticastConsumerLbOffWithRedistribution() throws Exception {

      String address = "test.address";
      String queue = "queue";
      String clusterAddress = "test";
      AddressSettings settings = new AddressSettings().setRedistributionDelay(0).setAutoCreateAddresses(true).setAutoCreateQueues(true);
      RoutingType routingType = RoutingType.MULTICAST;

      getServer(0).getAddressSettingsRepository().addMatch(address, settings);
      getServer(1).getAddressSettingsRepository().addMatch(address, settings);

      setupClusterConnection("cluster0", clusterAddress, MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster0", clusterAddress, MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION, 1, isNetty(), 1, 0);

      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, address, queue, null, false, routingType);
      addConsumer(0, 0, queue, null);
      waitForBindings(0, address, 1, 1, true);
      waitForBindings(1, address, 1, 1, false);

      createAddressInfo(1, address, routingType, 0, false);

      final int noMessages = 10;
      send(1, address, noMessages, false, null, routingType, null);
      verifyReceiveAll(noMessages, 0);

   }

   @Test
   public void testBackAndForth() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      final String ADDRESS = "queues.testaddress";
      final String QUEUE = "queue0";

      createQueue(0, ADDRESS, QUEUE, null, false);
      createQueue(1, ADDRESS, QUEUE, null, false);
      createQueue(2, ADDRESS, QUEUE, null, false);

      addConsumer(0, 0, QUEUE, null);

      waitForBindings(0, ADDRESS, 1, 1, true);
      waitForBindings(1, ADDRESS, 1, 0, true);
      waitForBindings(2, ADDRESS, 1, 0, true);

      waitForBindings(0, ADDRESS, 2, 0, false);
      waitForBindings(1, ADDRESS, 2, 1, false);
      waitForBindings(2, ADDRESS, 2, 1, false);

      send(0, ADDRESS, 20, false, null);

      waitForMessages(0, ADDRESS, 20);

      removeConsumer(0);

      waitForBindings(0, ADDRESS, 1, 0, true);
      waitForBindings(1, ADDRESS, 1, 0, true);
      waitForBindings(2, ADDRESS, 1, 0, true);

      waitForBindings(0, ADDRESS, 2, 0, false);
      waitForBindings(1, ADDRESS, 2, 0, false);
      waitForBindings(2, ADDRESS, 2, 0, false);

      addConsumer(1, 1, QUEUE, null);

      waitForBindings(0, ADDRESS, 1, 0, true);
      waitForBindings(1, ADDRESS, 1, 1, true);
      waitForBindings(2, ADDRESS, 1, 0, true);

      waitForMessages(1, ADDRESS, 20);
      waitForMessages(0, ADDRESS, 0);

      waitForBindings(0, ADDRESS, 2, 1, false);
      waitForBindings(1, ADDRESS, 2, 0, false);
      waitForBindings(2, ADDRESS, 2, 1, false);

      removeConsumer(1);

      waitForBindings(0, ADDRESS, 1, 0, true);
      waitForBindings(1, ADDRESS, 1, 0, true);
      waitForBindings(2, ADDRESS, 1, 0, true);

      waitForBindings(0, ADDRESS, 2, 0, false);
      waitForBindings(1, ADDRESS, 2, 0, false);
      waitForBindings(2, ADDRESS, 2, 0, false);

      addConsumer(0, 0, QUEUE, null);

      waitForBindings(0, ADDRESS, 1, 1, true);
      waitForBindings(1, ADDRESS, 1, 0, true);
      waitForBindings(2, ADDRESS, 1, 0, true);

      waitForBindings(0, ADDRESS, 2, 0, false);
      waitForBindings(1, ADDRESS, 2, 1, false);
      waitForBindings(2, ADDRESS, 2, 1, false);

      waitForMessages(0, ADDRESS, 20);

      verifyReceiveAll(20, 0);
      verifyNotReceive(0);

      addConsumer(1, 1, QUEUE, null);
      verifyNotReceive(1);
      removeConsumer(1);

   }

   @Test
   public void testBackAndForth2WithDuplicDetection() throws Exception {
      internalTestBackAndForth2(true);
   }

   @Test
   public void testBackAndForth2() throws Exception {
      internalTestBackAndForth2(false);
   }

   public void internalTestBackAndForth2(final boolean useDuplicateDetection) throws Exception {
      AtomicInteger duplDetection = null;

      if (useDuplicateDetection) {
         duplDetection = new AtomicInteger(0);
      }
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      final String ADDRESS = "queues.testaddress";
      final String QUEUE = "queue0";

      createQueue(0, ADDRESS, QUEUE, null, false);
      createQueue(1, ADDRESS, QUEUE, null, false);

      addConsumer(0, 0, QUEUE, null);

      waitForBindings(0, ADDRESS, 1, 1, true);
      waitForBindings(1, ADDRESS, 1, 0, true);

      waitForBindings(0, ADDRESS, 1, 0, false);
      waitForBindings(1, ADDRESS, 1, 1, false);

      send(1, ADDRESS, 20, false, null, duplDetection);

      waitForMessages(0, ADDRESS, 20);

      removeConsumer(0);

      waitForBindings(0, ADDRESS, 1, 0, true);
      waitForBindings(1, ADDRESS, 1, 0, true);

      waitForBindings(0, ADDRESS, 1, 0, false);
      waitForBindings(1, ADDRESS, 1, 0, false);

      addConsumer(1, 1, QUEUE, null);

      waitForMessages(1, ADDRESS, 20);
      waitForMessages(0, ADDRESS, 0);

      waitForBindings(0, ADDRESS, 1, 1, false);
      waitForBindings(1, ADDRESS, 1, 0, false);

      removeConsumer(1);

      addConsumer(0, 0, QUEUE, null);

      waitForMessages(1, ADDRESS, 0);
      waitForMessages(0, ADDRESS, 20);

      removeConsumer(0);
      addConsumer(1, 1, QUEUE, null);

      waitForMessages(1, ADDRESS, 20);
      waitForMessages(0, ADDRESS, 0);

      verifyReceiveAll(20, 1);
   }

   @Test
   public void testRedistributionToQueuesWhereNotAllMessagesMatch() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      String filter1 = "giraffe";
      String filter2 = "platypus";

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(2, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);
      waitForBindings(2, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 2, 0, false);
      waitForBindings(1, "queues.testaddress", 2, 1, false);
      waitForBindings(2, "queues.testaddress", 2, 1, false);

      sendInRange(0, "queues.testaddress", 0, 10, false, filter1);
      sendInRange(0, "queues.testaddress", 10, 20, false, filter2);

      removeConsumer(0);
      addConsumer(1, 1, "queue0", filter1);
      addConsumer(2, 2, "queue0", filter2);

      verifyReceiveAllInRange(0, 10, 1);
      verifyReceiveAllInRange(10, 20, 2);
   }

   @Test
   public void testDelayedRedistribution() throws Exception {
      final long delay = 200;
      setRedistributionDelay(delay);

      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(2, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);
      waitForBindings(2, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 2, 0, false);
      waitForBindings(1, "queues.testaddress", 2, 1, false);
      waitForBindings(2, "queues.testaddress", 2, 1, false);

      send(0, "queues.testaddress", 20, false, null);

      long start = System.currentTimeMillis();

      removeConsumer(0);
      addConsumer(1, 1, "queue0", null);

      long minReceiveTime = start + delay;

      verifyReceiveAllNotBefore(minReceiveTime, 20, 1);
   }

   @Test
   public void testDelayedRedistributionCancelled() throws Exception {
      final long delay = 200;
      setRedistributionDelay(delay);

      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(2, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);
      waitForBindings(2, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 2, 0, false);
      waitForBindings(1, "queues.testaddress", 2, 1, false);
      waitForBindings(2, "queues.testaddress", 2, 1, false);

      send(0, "queues.testaddress", 20, false, null);

      removeConsumer(0);
      addConsumer(1, 1, "queue0", null);

      Thread.sleep(delay / 2);

      // Add it back on the local queue - this should stop any redistributionm
      addConsumer(0, 0, "queue0", null);

      Thread.sleep(delay);

      verifyReceiveAll(20, 0);
   }

   @Test
   public void testRedistributionNumberOfMessagesGreaterThanBatchSize() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1, 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      setupSessionFactory(2, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);
      createQueue(2, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);
      waitForBindings(2, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 2, 0, false);
      waitForBindings(1, "queues.testaddress", 2, 1, false);
      waitForBindings(2, "queues.testaddress", 2, 1, false);

      send(0, "queues.testaddress", 10, false, null);

      removeConsumer(0);
      addConsumer(1, 1, "queue0", null);

      Queue queue = servers[1].locateQueue(SimpleString.of("queue0"));
      assertNotNull(queue);
      Wait.waitFor(() -> queue.getMessageCount() == 10);

      for (int i = 0; i < 10; i++) {
         ClientMessage message = consumers[1].getConsumer().receive(5000);
         assertNotNull(message);
         message.acknowledge();
      }

      assertNull(consumers[1].getConsumer().receiveImmediate());
   }

   /*
    * Start one node with no consumers and send some messages. Start another node add a consumer and verify all messages
    * are redistributed.
    */
   @Test
   public void testRedistributionWhenNewNodeIsAddedWithConsumer() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0);

      setupSessionFactory(0, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);

      waitForBindings(0, "queues.testaddress", 1, 0, true);

      send(0, "queues.testaddress", 20, false, null);

      // Now bring up node 1

      startServers(1);

      setupSessionFactory(1, isNetty());

      createQueue(1, "queues.testaddress", "queue0", null, false);

      waitForBindings(1, "queues.testaddress", 1, 0, true);
      waitForBindings(0, "queues.testaddress", 1, 0, false);

      addConsumer(0, 1, "queue0", null);

      verifyReceiveAll(20, 0);
      verifyNotReceive(0);
   }

   @Test
   public void testRedistributionWithPagingOnTarget() throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      AddressSettings as = new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setPageSizeBytes(10000).setMaxSizeBytes(20000);

      getServer(0).getAddressSettingsRepository().addMatch("queues.*", as);
      getServer(1).getAddressSettingsRepository().addMatch("queues.*", as);
      getServer(2).getAddressSettingsRepository().addMatch("queues.*", as);

      startServers(0);

      startServers(1);

      waitForTopology(getServer(0), 2);
      waitForTopology(getServer(1), 2);

      setupSessionFactory(0, isNetty());

      setupSessionFactory(1, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, true);

      createQueue(1, "queues.testaddress", "queue0", null, true);

      waitForBindings(1, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 1, 0, false);

      getServer(0).getPagingManager().getPageStore(SimpleString.of("queues.testaddress")).startPaging();

      ClientSession session0 = sfs[0].createSession(true, true, 0);
      ClientProducer producer0 = session0.createProducer("queues.testaddress");

      ClientConsumer consumer0 = session0.createConsumer("queue0");
      session0.start();

      ClientSession session1 = sfs[1].createSession(true, true, 0);
      ClientConsumer consumer1 = session1.createConsumer("queue0");
      session1.start();

      for (int i = 0; i < 10; i++) {
         ClientMessage msg = session0.createMessage(true);
         msg.putIntProperty("i", i);
         // send two identical messages so they are routed on the cluster
         producer0.send(msg);
         producer0.send(msg);

         msg = consumer0.receive(5000);
         assertNotNull(msg);
         assertEquals(i, msg.getIntProperty("i").intValue());
         // msg.acknowledge(); // -- do not ack message on consumer0, to make sure the messages will be paged

         msg = consumer1.receive(5000);
         assertNotNull(msg);
         assertEquals(i, msg.getIntProperty("i").intValue());
         msg.acknowledge();
      }

      session0.close();
      session1.close();
   }

   protected void setupCluster(final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      setupCluster("queues", messageLoadBalancingType);
   }

   protected void setupCluster(final String address, final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      setupClusterConnection("cluster0", address, messageLoadBalancingType, 1, isNetty(), 0, 1, 2);

      setupClusterConnection("cluster1", address, messageLoadBalancingType, 1, isNetty(), 1, 0, 2);

      setupClusterConnection("cluster2", address, messageLoadBalancingType, 1, isNetty(), 2, 0, 1);
   }

   protected void setRedistributionDelay(final long delay) {
      AddressSettings as = new AddressSettings().setRedistributionDelay(delay);

      getServer(0).getAddressSettingsRepository().addMatch("queues.*", as);
      getServer(1).getAddressSettingsRepository().addMatch("queues.*", as);
      getServer(2).getAddressSettingsRepository().addMatch("queues.*", as);
   }

   protected void setupServers() throws Exception {
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());
      setupServer(2, isFileStorage(), isNetty());
   }

   protected void stopServers() throws Exception {
      closeAllConsumers();

      closeAllSessionFactories();

      closeAllServerLocatorsFactories();

      stopServers(0, 1, 2);

      clearServer(0, 1, 2);
   }

}
