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
package org.apache.activemq.artemis.tests.integration.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ReplayTest extends ActiveMQTestBase {

   ActiveMQServer server;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      server = addServer(createServer(true, true));
      server.getConfiguration().setJournalRetentionDirectory(getJournalDir() + "retention");
      server.getConfiguration().setJournalFileSize(100 * 1024);

      server.start();

      server.addAddressInfo(new AddressInfo("t1").addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of("t1").setAddress("t1").setRoutingType(RoutingType.ANYCAST));

      server.addAddressInfo(new AddressInfo("t2").addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of("t2").setAddress("t2").setRoutingType(RoutingType.ANYCAST));
   }

   @Test
   public void testReplayAMQP() throws Exception {
      testReplay("AMQP", 10, false);
   }

   @Test
   public void testReplayCore() throws Exception {
      testReplay("CORE", 10, false);
   }

   protected void testReplay(String protocol, int size, boolean paging) throws Exception {
      String buffer = RandomUtil.randomUUIDString() + "*".repeat(size);

      if (paging) {
         org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue("t1");
         serverQueue.getPagingStore().startPaging();
      }

      ConnectionFactory cf = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Queue queue = session.createQueue("t1");

         MessageProducer producer = session.createProducer(null);

         producer.send(queue, session.createTextMessage(buffer));

         connection.start();

         MessageConsumer consumer = session.createConsumer(queue);

         assertNotNull(consumer.receive(5000));

         assertNull(consumer.receiveNoWait());

         server.replay(null, null, "t1", "t2", null);

         Queue t2 = session.createQueue("t2");

         MessageConsumer consumert2 = session.createConsumer(t2);

         TextMessage receivedMessage = (TextMessage) consumert2.receive(5000);

         assertNotNull(receivedMessage);

         assertEquals(buffer, receivedMessage.getText());

         assertNull(consumert2.receiveNoWait());

         server.replay(null, null, "t2", "t1", null);

         receivedMessage = (TextMessage) consumer.receive(5000);

         assertNotNull(receivedMessage);

         assertNull(consumer.receiveNoWait());

         // invalid filter.. nothing should be re played
         server.replay(null, null, "t1", "t1", "foo='foo'");

         assertNull(consumer.receiveNoWait());
      }

   }

   @Test
   public void testReplayLargeAMQP() throws Exception {
      testReplay("AMQP", 500 * 1024, false);
   }

   @Test
   public void testReplayLargeCore() throws Exception {
      testReplay("CORE", 500 * 1024, false);
   }
   @Test
   public void testReplayCorePaging() throws Exception {
      testReplay("CORE", 10, true);
   }
}
