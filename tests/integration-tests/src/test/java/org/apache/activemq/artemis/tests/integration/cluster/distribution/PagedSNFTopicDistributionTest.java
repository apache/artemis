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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PagedSNFTopicDistributionTest extends ClusterTestBase {

   public boolean isNetty() {
      return true;
   }

   @Test
   public void testTopicWhileSNFPaged() throws Exception {
      final int nmessages = 100;
      setupServer(0, true, isNetty());
      setupServer(1, true, isNetty());

      setupClusterConnection("cluster0", "topics", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster1", "topics", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);

      startServers(0, 1);

      waitForTopology(servers[0], 2);
      waitForTopology(servers[1], 2);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      servers[0].addAddressInfo(new AddressInfo("topics.A").addRoutingType(RoutingType.MULTICAST));
      servers[1].addAddressInfo(new AddressInfo("topics.A").addRoutingType(RoutingType.MULTICAST));

      ConnectionFactory factoryServer0 = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      ConnectionFactory factoryServer1 = CFUtil.createConnectionFactory("core", "tcp://localhost:61617");

      // creating the subscription on server0
      try (Connection connection = factoryServer0.createConnection()) {
         connection.setClientID("server0");
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic("topics.A");
         session.createDurableSubscriber(topic, "topic-server0");
      }

      // creating the subscription on server1
      try (Connection connection = factoryServer1.createConnection()) {
         connection.setClientID("server1");
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic("topics.A");
         session.createDurableSubscriber(topic, "topic-server1");
      }

      waitForBindings(0, "topics.A", 1, 0, false);
      waitForBindings(1, "topics.A", 1, 0, false);

      // making everything to page
      servers[0].getPostOffice().getAllBindings().forEach(b -> {
         if (b instanceof LocalQueueBinding localQueueBinding) {
            try {
               localQueueBinding.getQueue().getPagingStore().startPaging();
            } catch (Exception e) {
            }
         }
      });

      try (Connection connection = factoryServer0.createConnection()) {
         Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic("topics.A");
         MessageProducer producer = session.createProducer(topic);
         for (int i = 0; i < nmessages; i++) {
            producer.send(session.createTextMessage("msg " + i));
         }
         session.commit();
      }

      // verifying if everything is actually paged, nothing should be routed on the journal
      Map<Integer, AtomicInteger> counters = countJournal(servers[0].getConfiguration());
      assertEquals(0, getCounter(JournalRecordIds.ADD_REF, counters), "There are routed messages on the journal");
      assertEquals(0, getCounter(JournalRecordIds.ADD_MESSAGE, counters), "There are routed messages on the journal");
      assertEquals(0, getCounter(JournalRecordIds.ADD_MESSAGE_PROTOCOL, counters), "There are routed messages on the journal");

      // consume remotely on server1
      try (Connection connection = factoryServer1.createConnection()) {
         connection.setClientID("server1");
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic("topics.A");
         MessageConsumer consumer = session.createDurableSubscriber(topic, "topic-server1");
         connection.start();
         for (int i = 0; i < nmessages; i++) {
            TextMessage message = (TextMessage) consumer.receive(1000);
            assertNotNull(message);
            assertEquals("msg " + i, message.getText());
         }
      }

      // consume locally (to where the message was sent) on server0
      try (Connection connection = factoryServer0.createConnection()) {
         connection.setClientID("server0");
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = session.createTopic("topics.A");
         MessageConsumer consumer = session.createDurableSubscriber(topic, "topic-server0");
         connection.start();
         for (int i = 0; i < nmessages; i++) {
            TextMessage message = (TextMessage) consumer.receive(1000);
            assertNotNull(message);
            assertEquals("msg " + i, message.getText());
         }
      }

   }

   private int getCounter(byte typeRecord, Map<Integer, AtomicInteger> values) {
      AtomicInteger value = values.get((int) typeRecord);
      if (value == null) {
         return 0;
      } else {
         return value.get();
      }
   }

}
