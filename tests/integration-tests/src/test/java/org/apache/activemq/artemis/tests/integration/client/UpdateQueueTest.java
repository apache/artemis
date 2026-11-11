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
package org.apache.activemq.artemis.tests.integration.client;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UpdateQueueTest extends ActiveMQTestBase {

   @Test
   public void testUpdateQueueWithNullUser() throws Exception {
      ActiveMQServer server = createServer(true, false);

      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("vm://0");

      server.start();

      SimpleString ADDRESS = SimpleString.of("queue.0");

      final SimpleString user = SimpleString.of("newUser");

      Queue queue = server.createQueue(QueueConfiguration.of(ADDRESS).setRoutingType(RoutingType.ANYCAST).setUser(user));

      Long originalID = queue.getID();

      assertEquals(user, queue.getUser());

      Connection conn = factory.createConnection();
      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer prod = session.createProducer(session.createQueue(ADDRESS.toString()));

      for (int i = 0; i < 100; i++) {
         prod.send(session.createTextMessage("message " + i));
      }

      server.updateQueue(QueueConfiguration.of(ADDRESS).setRoutingType(RoutingType.ANYCAST).setMaxConsumers(1).setExclusive(false));

      conn.close();
      factory.close();

      server.stop();
      server.start();

      validateBindingRecords(server, JournalRecordIds.QUEUE_BINDING_RECORD, 2);

      queue = server.locateQueue(ADDRESS);

      assertNotNull(queue, "queue not found");

      assertEquals(user, queue.getUser(), "newUser");

      factory = new ActiveMQConnectionFactory("vm://0");

      conn = factory.createConnection();
      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer consumer = session.createConsumer(session.createQueue(ADDRESS.toString()));

      conn.start();
      for (int i = 0; i < 100; i++) {
         assertNotNull(consumer.receive(5000));
      }

      assertNull(consumer.receiveNoWait());

      assertEquals(1, queue.getMaxConsumers());

      conn.close();

      assertEquals(originalID, server.locateQueue(ADDRESS).getID());

      // stopping, restarting to make sure the system will not create an extra record without an update
      server.stop();
      server.start();
      validateBindingRecords(server, JournalRecordIds.QUEUE_BINDING_RECORD, 2);
      server.stop();

   }

   @Test
   public void testUpdateQueue() throws Exception {
      ActiveMQServer server = createServer(true, false);

      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("vm://0");

      server.start();

      SimpleString ADDRESS = SimpleString.of("queue.0");

      Queue queue = server.createQueue(QueueConfiguration.of(ADDRESS).setRoutingType(RoutingType.ANYCAST));

      Long originalID = queue.getID();

      assertNull(queue.getUser());

      Connection conn = factory.createConnection();
      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer prod = session.createProducer(session.createQueue(ADDRESS.toString()));

      for (int i = 0; i < 100; i++) {
         prod.send(session.createTextMessage("message " + i));
      }

      server.updateQueue(QueueConfiguration.of(ADDRESS.toString())
                            .setRoutingType(RoutingType.ANYCAST)
                            .setMaxConsumers(1)
                            .setPurgeOnNoConsumers(false)
                            .setExclusive(true)
                            .setGroupRebalance(true)
                            .setGroupBuckets(5)
                            .setGroupFirstKey("gfk")
                            .setNonDestructive(true)
                            .setConsumersBeforeDispatch(1)
                            .setDelayBeforeDispatch(10L)
                            .setUser("newUser")
                            .setRingSize(180L));

      conn.close();
      factory.close();

      server.stop();
      server.start();

      validateBindingRecords(server, JournalRecordIds.QUEUE_BINDING_RECORD, 2);

      queue = server.locateQueue(ADDRESS);

      assertNotNull(queue, "queue not found");
      assertEquals(1, queue.getMaxConsumers());
      assertFalse(queue.isPurgeOnNoConsumers());
      assertTrue(queue.isExclusive());
      assertTrue(queue.isGroupRebalance());
      assertEquals(5, queue.getGroupBuckets());
      assertEquals("gfk", queue.getGroupFirstKey().toString());
      assertTrue(queue.isNonDestructive());
      assertEquals(1, queue.getConsumersBeforeDispatch());
      assertEquals(10L, queue.getDelayBeforeDispatch());
      assertEquals("newUser", queue.getUser().toString());
      assertEquals(180L, queue.getRingSize());
      assertEquals(8192, queue.getInitialQueueBufferSize());

      factory = new ActiveMQConnectionFactory("vm://0");

      conn = factory.createConnection();
      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer consumer = session.createConsumer(session.createQueue(ADDRESS.toString()));

      conn.start();
      for (int i = 0; i < 100; i++) {
         assertNotNull(consumer.receive(5000));
      }

      assertNull(consumer.receiveNoWait());

      conn.close();

      assertEquals(originalID, server.locateQueue(ADDRESS).getID());

      // stopping, restarting to make sure the system will not create an extra record without an update
      server.stop();
      server.start();
      validateBindingRecords(server, JournalRecordIds.QUEUE_BINDING_RECORD, 2);
      server.stop();

   }

   @Test
   public void testUpdateQueueFilter() throws Exception {
      ActiveMQServer server = createServer(true, false);

      server.start();

      SimpleString ADDRESS = SimpleString.of("queue.0");

      server.createQueue(QueueConfiguration.of(ADDRESS).setRoutingType(RoutingType.ANYCAST).setFilterString("color='red'"));

      server.updateQueue(QueueConfiguration.of(ADDRESS).setRoutingType(RoutingType.ANYCAST).setFilterString("color='blue'"));

      assertEquals("color='blue'", server.locateQueue(ADDRESS).getFilter().getFilterString().toString());
      assertEquals("color='blue'", server.locateQueue(ADDRESS).getQueueConfiguration().getFilterString().toString());
      assertEquals("color='blue'", server.queueQuery(ADDRESS).getFilterString().toString());

      server.stop();
      server.start();

      assertEquals("color='blue'", server.locateQueue(ADDRESS).getFilter().getFilterString().toString());
      assertEquals("color='blue'", server.queueQuery(ADDRESS).getFilterString().toString());

      // removing the filter
      server.updateQueue(QueueConfiguration.of(ADDRESS).setRoutingType(RoutingType.ANYCAST).setFilterString(""));

      assertNull(server.locateQueue(ADDRESS).getFilter());
      assertNull(server.queueQuery(ADDRESS).getFilterString());

      server.stop();
      server.start();

      assertNull(server.locateQueue(ADDRESS).getFilter());
      assertNull(server.queueQuery(ADDRESS).getFilterString());

      server.stop();
   }

   private void validateBindingRecords(ActiveMQServer server, byte type, int expected) throws Exception {
      Map<Integer, AtomicInteger> counts = countBindingJournal(server.getConfiguration());
      // if this fails, don't ignore it.. it means something is sending a new record on the journal
      // something is creating new records upon restart of the server.
      // I really meant to have this fix, so don't ignore it if it fails
      assertEquals(expected, counts.get((int) type).intValue());
   }

   @Test
   public void testUpdateAddress() throws Exception {
      ActiveMQServer server = createServer(true, true);

      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();

      server.start();

      SimpleString ADDRESS = SimpleString.of("queue.0");

      AddressInfo infoAdded = new AddressInfo(ADDRESS, RoutingType.ANYCAST);

      server.addAddressInfo(infoAdded);

      server.updateAddressInfo(ADDRESS, infoAdded.getRoutingTypes());

      server.stop();
      server.start();

      AddressInfo infoAfterRestart = server.getPostOffice().getAddressInfo(ADDRESS);

      assertEquals(infoAdded.getId(), infoAfterRestart.getId());

      EnumSet<RoutingType> completeSet = EnumSet.allOf(RoutingType.class);

      server.updateAddressInfo(ADDRESS, completeSet);

      server.stop();
      server.start();

      infoAfterRestart = server.getPostOffice().getAddressInfo(ADDRESS);

      // it was changed.. so new ID
      assertNotEquals(infoAdded.getId(), infoAfterRestart.getId());
   }
}
