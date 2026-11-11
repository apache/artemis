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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TopicClusteredOffTest extends ClusterTestBase {

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
      return true;
   }

   @Test
   public void testTopicRedistributionOff() throws Exception {
      internalTest(false);
   }

   @Test
   public void testTopicRedistributionOn() throws Exception {
      internalTest(true);
   }

   private void internalTest(boolean redisitribute) throws Exception {
      if (redisitribute) {
         setupCluster(MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION);
      } else {
         setupCluster(MessageLoadBalancingType.OFF);
      }

      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, "queues.dist", "queue0", null, false);
      createQueue(1, "queues.dist", "queue1", null, false);

      waitForBindings(0, "queues.dist", 1, 0, true);
      waitForBindings(0, "queues.dist", 1, 0, false);

      waitForBindings(1, "queues.dist", 1, 0, true);
      waitForBindings(1, "queues.dist", 1, 0, false);

      ConnectionFactory factory0 = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      ConnectionFactory factory1 = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61617");

      try (Connection connection = factory0.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Topic topic = session.createTopic("queues.dist");
         MessageProducer producer = session.createProducer(topic);
         for (int i = 0; i < 10; i++) {
            producer.send(session.createTextMessage("message" + i));
         }
         session.commit();

         connection.start();

         MessageConsumer consumer = session.createConsumer(session.createTopic("queues.dist::queue0"));
         for (int i = 0; i < 10; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals("message" + i, message.getText());
         }
         session.rollback();
      }

      try (Connection connection = factory1.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         connection.start();

         MessageConsumer consumer = session.createConsumer(session.createTopic("queues.dist::queue1"));
         for (int i = 0; i < 10; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals("message" + i, message.getText());
         }
         session.rollback();
      }

      createQueue(1, "queues.dist", "queue0", null, false); // trying to force redistribution

      waitForBindings(0, "queues.dist", 1, 0, true);
      waitForBindings(0, "queues.dist", 2, 0, false);

      waitForBindings(1, "queues.dist", 2, 0, true);
      waitForBindings(1, "queues.dist", 1, 0, false);


      try (Connection connection = factory1.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         connection.start();

         MessageConsumer consumer = session.createConsumer(session.createTopic("queues.dist::queue0"));
         if (redisitribute) {
            for (int i = 0; i < 10; i++) {
               TextMessage message = (TextMessage) consumer.receive(5_000);
               assertNotNull(message);
               assertEquals("message" + i, message.getText());
            }
         } else {
            TextMessage message = (TextMessage) consumer.receive(100);
            assertNull(message, "Messages are being redistributed");
         }
      }

   }

   protected void setupCluster(final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      setupClusterConnection("cluster0", "queues", messageLoadBalancingType, 1, isNetty(), 0, 1);

      setupClusterConnection("cluster1", "queues", messageLoadBalancingType, 1, isNetty(), 1, 0);
   }

   protected void setRedistributionDelay(final long delay) {
      AddressSettings as = new AddressSettings().setRedistributionDelay(delay);

      getServer(0).getAddressSettingsRepository().addMatch("queues.*", as);
      getServer(1).getAddressSettingsRepository().addMatch("queues.*", as);
   }

   protected void setupServers() throws Exception {
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());
   }

   protected void stopServers() throws Exception {
      closeAllConsumers();

      closeAllSessionFactories();

      closeAllServerLocatorsFactories();

      stopServers(0, 1);

      clearServer(0, 1);
   }

}
