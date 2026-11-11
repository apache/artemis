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
package org.apache.activemq.artemis.junit;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Session;
import java.util.List;

import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.jms.client.ActiveMQMessageConsumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EmbeddedJMSResourceSingleFileConfigurationTest {

   static final String TEST_QUEUE = "queue://test.queue";
   static final String TEST_TOPIC = "topic://test.topic";
   static final String TEST_BODY = "Test Message";

   static final String ASSERT_PUSHED_FORMAT = "Message should have been pushed a message to %s";
   static final String ASSERT_COUNT_FORMAT = "Unexpected message count in destination %s";

   public EmbeddedJMSResource jmsServer = new EmbeddedJMSResource("embedded-artemis-jms-server.xml");

   @Rule
   public RuleChain rulechain = RuleChain.outerRule(jmsServer);

   ConnectionFactory connectionFactory;
   Connection connection;
   Session session;
   ActiveMQMessageConsumer consumer;

   @Before
   public void setUp() throws Exception {
      connectionFactory = new ActiveMQConnectionFactory(jmsServer.getVmURL());
      connection = connectionFactory.createConnection();
      session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      consumer = (ActiveMQMessageConsumer) session.createConsumer(ActiveMQDestination.createDestination(TEST_TOPIC, ActiveMQDestination.TYPE.TOPIC));
      connection.start();
   }

   @After
   public void tearDown() throws Exception {
      consumer.close();
      session.close();
      connection.close();
   }

   @Test
   public void testConfiguration() throws Exception {
      assertNotNull("Queue should have been created", jmsServer.getDestinationQueue(TEST_QUEUE));
      assertNotNull("Topic should have been created", jmsServer.getDestinationQueue(TEST_TOPIC));

      List<Queue> boundQueues = jmsServer.getTopicQueues(TEST_TOPIC);
      assertNotNull("List should never be null", boundQueues);
      assertEquals("Should have two queues bound to topic " + TEST_TOPIC, 1, boundQueues.size());
   }

}