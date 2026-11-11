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
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.tests.util.JMSClusteredTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopicClusterPageStoreSizeTest extends JMSClusteredTestBase {

   @Override
   protected boolean enablePersistence() {
      return true;
   }
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String TOPIC = "jms.t1";

   @Test
   public void testPageStoreSizeWithClusteredDurableSub() throws Exception {
      doTestPageStoreSizeWithClusteredDurableSub(false);
   }

   @Test
   public void testPageStoreSizeWithClusteredDurableSubWithPaging() throws Exception {
      doTestPageStoreSizeWithClusteredDurableSub(true);
   }

   private void doTestPageStoreSizeWithClusteredDurableSub(boolean forcePaging) throws Exception {

      Connection conn1 = cf1.createConnection();

      conn1.setClientID("someClient1");

      Connection conn2 = cf2.createConnection();

      conn2.setClientID("someClient2");

      conn1.start();

      conn2.start();

      Topic topic1 = createTopic(TOPIC, true);

      Session session1 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      Session session2 = conn2.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      MessageProducer prod1 = session1.createProducer(null);
      prod1.setDeliveryMode(DeliveryMode.PERSISTENT);

      MessageConsumer cons1 = session1.createDurableSubscriber(topic1, "sub1");
      MessageConsumer cons2 = session2.createDurableSubscriber(topic1, "sub2");

      waitForBindings(server1, TOPIC, true, 1, 1, 2000);
      waitForBindings(server2, TOPIC, true, 1, 1, 2000);
      waitForBindings(server1, TOPIC, false, 1, 1, 2000);
      waitForBindings(server2, TOPIC, false, 1, 1, 2000);

      if (forcePaging) {
         for (SimpleString psName : server1.getPagingManager().getStoreNames()) {
            logger.info("server1: force paging on:{}", psName);
            server1.getPagingManager().getPageStore(psName).startPaging();
         }
         for (SimpleString psName : server2.getPagingManager().getStoreNames()) {
            logger.info("server2: force paging on:{}", psName);
            server2.getPagingManager().getPageStore(psName).startPaging();
         }
      }

      prod1.send(topic1, session1.createTextMessage("someMessage"));

      TextMessage m2 = (TextMessage) cons2.receive(5000);
      assertNotNull(m2);
      assertTrue(m2.getJMSDestination().toString().contains(TOPIC));
      logger.info("sub2 on 2, no ack, message txt:{}", m2.getText());


      TextMessage m1 = (TextMessage) cons1.receive(5000);
      assertNotNull(m1);
      assertTrue(m1.getJMSDestination().toString().contains(TOPIC));
      logger.info("message txt: {}", m1.getText());

      m1.acknowledge();
      // leave m2 for reconnect on server1

      conn1.close();
      conn2.close();

      for (SimpleString psName : server1.getPagingManager().getStoreNames()) {
         Wait.assertTrue(() -> server1.getPagingManager().getPageStore(psName).getAddressSize() >= 0, 5000);
      }

      for (SimpleString psName : server2.getPagingManager().getStoreNames()) {
         logger.info("server2: size of pages store: {} : {}", psName, server2.getPagingManager().getPageStore(psName).getAddressSize());
         Wait.assertTrue(() -> server2.getPagingManager().getPageStore(psName).getAddressSize() >= 0, 5000);
      }

      if (forcePaging) {
         // message in the store, should have getPagedSize or is there some such thing?
         Wait.assertTrue(() -> server2.getPagingManager().getPageStore(SimpleString.of(TOPIC)).getNumberOfPages() > 0);
      } else {
         Wait.assertTrue(() -> server2.getPagingManager().getPageStore(SimpleString.of(TOPIC)).getAddressSize() > 0);
      }

      // reconnect
      // get message for someClient2 on server 1 (cf1)
      conn1 = cf1.createConnection();

      conn1.setClientID("someClient2");

      conn1.start();

      session1 = conn1.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      cons1 = session1.createDurableSubscriber(topic1, "sub2");

      m2 = (TextMessage) cons1.receive(5000);
      assertNotNull(m2);
      assertTrue(m2.getJMSDestination().toString().contains(TOPIC));
      logger.info("sub2 on 1, message txt: {}", m2.getText());

      m2.acknowledge();

      // publish another
      prod1 = session1.createProducer(null);
      prod1.setDeliveryMode(DeliveryMode.PERSISTENT);

      prod1.send(topic1, session1.createTextMessage("someOtherMessage"));

      // stays on server 1
      assertTrue(server1.getPagingManager().getPageStore(SimpleString.of(TOPIC)).getAddressSize() > 0, "some size on 1");
      assertEquals(0, server2.getPagingManager().getPageStore(SimpleString.of(TOPIC)).getAddressSize(), "no size on 2");

      // duplicate this sub on 2
      conn2 = cf2.createConnection();

      conn2.setClientID("someClient2");

      conn2.start();

      session2 = conn2.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      // the clientId unique guarantee is per broker, not per cluster
      cons2 = session2.createDurableSubscriber(topic1, "sub2");

      // should not be able to consume yet
      m2 = (TextMessage) cons2.receiveNoWait();
      assertNull(m2, "did not get message");

      // still available on cons1
      m2 = (TextMessage) cons1.receive(5000);
      assertNotNull(m2, "got message");
      assertTrue(m2.getJMSDestination().toString().contains(TOPIC));
      logger.info("sub2 on 1, message txt:{}", m2.getText());
      m2.acknowledge();

      // if we send another, lb will kick in..
      prod1.send(topic1, session1.createTextMessage("someOtherOtherMessage"));

      m2 = (TextMessage) cons2.receive(5000);
      assertNotNull(m2, "got message");
      assertTrue(m2.getJMSDestination().toString().contains(TOPIC));

      logger.info("sub2 on 2: message txt:{}", m2.getText());
      m2.acknowledge();

      // no duplicate, not available on cons1
      m2 = (TextMessage) cons1.receiveNoWait();
      assertNull(m2, "non null message");

      conn1.close();
      conn2.close();

      // pick up sub1:someClient1 messages, one from each broker
      for (ConnectionFactory cf : new ConnectionFactory[]{cf2, cf1}) {
         conn2 = cf.createConnection();
         conn2.setClientID("someClient1");
         conn2.start();

         session2 = conn2.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         cons2 = session2.createDurableSubscriber(topic1, "sub1");

         m2 = (TextMessage) cons2.receive(5000);
         assertNotNull(m2, "got message");
         assertTrue(m2.getJMSDestination().toString().contains(TOPIC));
         logger.info("sub1 on: {} - message txt: {}", cf, m2.getText());
         m2.acknowledge();

         conn2.close();
      }
   }
}
