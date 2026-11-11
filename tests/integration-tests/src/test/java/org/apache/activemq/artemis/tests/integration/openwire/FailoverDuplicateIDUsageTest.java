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
package org.apache.activemq.artemis.tests.integration.openwire;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FailoverDuplicateIDUsageTest extends ActiveMQTestBase {

   ActiveMQServer server;


   @BeforeEach
   public void setupServer() throws Exception {
      server = createServer(true, true);
   }


   @Test
   public void testTempQueue() throws Exception {
      server.getConfiguration().getAcceptorConfigurations().clear();
      server.getConfiguration().clearAcceptorConfigurations().addAcceptorConfiguration("openwire", "tcp://localhost:61616?openwireUseDuplicateDetectionOnFailover=true");
      server.start();
      server.waitForActivation(10, TimeUnit.SECONDS);

      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("failover:tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createTemporaryQueue();
         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < 100; i++) {
            producer.send(session.createTextMessage("hello"));
         }
         assertEquals(0, countDuplicateDetection(server.getConfiguration()));
      }


   }

   @Test
   public void testNoDuplicate() throws Exception {
      testDuplicate(false);
   }

   @Test
   public void testDuplicate() throws Exception {
      testDuplicate(true);
   }

   private void testDuplicate(boolean useDuplicate) throws Exception {
      String queueName = getName();
      server.getConfiguration().getAcceptorConfigurations().clear();
      server.getConfiguration().clearAcceptorConfigurations().addAcceptorConfiguration("openwire", "tcp://localhost:61616?openwireUseDuplicateDetectionOnFailover=" + useDuplicate);
      server.start();
      server.waitForActivation(10, TimeUnit.SECONDS);
      server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));

      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("failover:tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);
         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < 100; i++) {
            producer.send(session.createTextMessage("hello"));
         }
      }
      server.stop();

      assertEquals(useDuplicate ? 100 : 0, countDuplicateDetection(server.getConfiguration()));

   }

   private int countDuplicateDetection(Configuration configuration) throws Exception {
      Map<Integer, AtomicInteger> maps = countJournal(configuration);
      AtomicInteger value = maps.get((int)JournalRecordIds.DUPLICATE_ID);
      return value == null ? 0 : value.get();
   }


}
