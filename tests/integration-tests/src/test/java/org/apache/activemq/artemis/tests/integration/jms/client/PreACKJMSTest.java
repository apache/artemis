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

import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.List;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PreACKJMSTest extends JMSTestBase {


   private Queue queue;



   @Test
   public void testPreACKAuto() throws Exception {
      internalTestPreACK(Session.AUTO_ACKNOWLEDGE);
   }

   @Test
   public void testPreACKClientACK() throws Exception {
      internalTestPreACK(Session.CLIENT_ACKNOWLEDGE);
   }

   @Test
   public void testPreACKDupsOK() throws Exception {
      internalTestPreACK(Session.DUPS_OK_ACKNOWLEDGE);
   }

   public void internalTestPreACK(final int sessionType) throws Exception {
      conn = cf.createConnection();
      Session sess = conn.createSession(false, sessionType);

      MessageProducer prod = sess.createProducer(queue);

      TextMessage msg1 = sess.createTextMessage("hello");

      prod.send(msg1);

      conn.start();

      MessageConsumer cons = sess.createConsumer(queue);

      TextMessage msg2 = (TextMessage) cons.receive(1000);

      assertNotNull(msg2);

      assertEquals(msg1.getText(), msg2.getText());

      conn.close();

      conn = cf.createConnection();

      conn.start();

      sess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      cons = sess.createConsumer(queue);

      msg2 = (TextMessage) cons.receiveNoWait();

      assertNull(msg2, "ConnectionFactory is on PreACK mode, the message shouldn't be received");
   }

   @Test
   @Disabled
   public void testPreACKTransactional() throws Exception {
      conn = cf.createConnection();
      Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);

      MessageProducer prod = sess.createProducer(queue);

      TextMessage msg1 = sess.createTextMessage("hello");

      prod.send(msg1);

      sess.commit();

      conn.start();

      MessageConsumer cons = sess.createConsumer(queue);

      TextMessage msg2 = (TextMessage) cons.receive(1000);

      assertNotNull(msg2);

      assertEquals(msg1.getText(), msg2.getText());

      sess.rollback();

      conn.close();

      conn = cf.createConnection();

      conn.start();

      sess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      cons = sess.createConsumer(queue);

      msg2 = (TextMessage) cons.receive(10);

      assertNotNull(msg2, "ConnectionFactory is on PreACK mode but it is transacted");
   }



   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      queue = createQueue("queue1");
   }

   @Override
   protected void createCF(final List<TransportConfiguration> connectorConfigs,
                           final String... jndiBindings) throws Exception {
      int retryInterval = 1000;
      double retryIntervalMultiplier = 1.0;
      int reconnectAttempts = -1;
      int callTimeout = 30000;

      List<String> connectors = registerConnectors(server, connectorConfigs);

      jmsServer.createConnectionFactory("ManualReconnectionToSingleServerTest", false, JMSFactoryType.CF, connectors, null, ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD, ActiveMQClient.DEFAULT_CONNECTION_TTL, callTimeout, ActiveMQClient.DEFAULT_CALL_FAILOVER_TIMEOUT, ActiveMQClient.DEFAULT_CACHE_LARGE_MESSAGE_CLIENT, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, ActiveMQClient.DEFAULT_COMPRESS_LARGE_MESSAGES, ActiveMQClient.DEFAULT_COMPRESSION_LEVEL, ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE, ActiveMQClient.DEFAULT_CONSUMER_MAX_RATE, ActiveMQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_MAX_RATE, ActiveMQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE, ActiveMQClient.DEFAULT_BLOCK_ON_DURABLE_SEND, ActiveMQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND, ActiveMQClient.DEFAULT_AUTO_GROUP, true, ActiveMQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_USE_GLOBAL_POOLS, ActiveMQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_THREAD_POOL_MAX_SIZE, retryInterval, retryIntervalMultiplier, ActiveMQClient.DEFAULT_MAX_RETRY_INTERVAL, reconnectAttempts, ActiveMQClient.DEFAULT_FAILOVER_ON_INITIAL_CONNECTION, null, jndiBindings);
   }



}
