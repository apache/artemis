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
  package org.apache.activemq.artemis.tests.integration.paging;

  import static org.junit.jupiter.api.Assertions.assertEquals;
  import static org.junit.jupiter.api.Assertions.assertTrue;

  import javax.jms.Connection;
  import javax.jms.MessageProducer;
  import javax.jms.Session;

  import org.apache.activemq.artemis.api.core.QueueConfiguration;
  import org.apache.activemq.artemis.api.core.RoutingType;
  import org.apache.activemq.artemis.api.core.SimpleString;
  import org.apache.activemq.artemis.core.config.Configuration;
  import org.apache.activemq.artemis.core.server.ActiveMQServer;
  import org.apache.activemq.artemis.core.server.Queue;
  import org.apache.activemq.artemis.core.server.impl.AddressInfo;
  import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
  import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
  import org.apache.activemq.artemis.tests.util.Wait;
  import org.junit.jupiter.api.Test;

  public class NegativeMessageCountTest extends ActiveMQTestBase {

     private static final int PAGE_MAX = 100 * 1024;
     private static final int PAGE_SIZE = 10 * 1024;
     private static final int MESSAGES = 50;

     @Test
     public void testPagedQueueNeverReportsNegativeMessageCount() throws Exception {
        Configuration config = createDefaultNettyConfig().setJournalSyncNonTransactional(false);
        ActiveMQServer server = createServer(true, config, PAGE_SIZE, PAGE_MAX, -1, -1);
        server.start();

        final String queueName = "negativeCountQueue";
        final SimpleString ssQueue = SimpleString.of(queueName);
        server.addAddressInfo(new AddressInfo(ssQueue, RoutingType.ANYCAST));
        Queue queue = server.createQueue(QueueConfiguration.of(ssQueue)
                                            .setRoutingType(RoutingType.ANYCAST)
                                            .setAutoCreateAddress(false));

        queue.getPageSubscription().getPagingStore().startPaging();

        ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory();
        try (Connection connection = cf.createConnection()) {
           Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
           MessageProducer producer = session.createProducer(session.createQueue(queueName));
           for (int i = 0; i < MESSAGES; i++) {
              producer.send(session.createTextMessage("hello " + i));
           }
           session.commit();
        } finally {
           cf.close();
        }

        Wait.assertEquals((long) MESSAGES, queue::getMessageCount, 5000L, 10L);
        assertTrue(queue.getPageSubscription().getPagingStore().isPaging());

        // ARTEMIS-6154: a message accounted as removed more than once (a queue purge racing with
        // acknowledgements) drives the page subscription counter below zero
        queue.getPageSubscription().getCounter().increment(null, -(MESSAGES * 2), -1024);
        assertTrue(queue.getPageSubscription().getCounter().getValue() < 0,
                   "the raw counter must be negative for this test to be meaningful");

        assertEquals(0, queue.getMessageCount());
        assertEquals(0, queue.getDurableMessageCount());
     }
  }