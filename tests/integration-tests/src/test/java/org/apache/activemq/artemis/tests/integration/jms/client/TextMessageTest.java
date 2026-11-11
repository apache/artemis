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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TextMessageTest extends JMSTestBase {


   private Queue queue;



   @Test
   public void testSendReceiveNullBody() throws Exception {
      conn = cf.createConnection();

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = sess.createProducer(queue);

      conn.start();

      MessageConsumer cons = sess.createConsumer(queue);

      TextMessage msg1 = sess.createTextMessage(null);
      prod.send(msg1);
      TextMessage received1 = (TextMessage) cons.receive(1000);
      assertNotNull(received1);
      assertNull(received1.getText());

      TextMessage msg2 = sess.createTextMessage();
      msg2.setText(null);
      prod.send(msg2);
      TextMessage received2 = (TextMessage) cons.receive(1000);
      assertNotNull(received2);
      assertNull(received2.getText());

      TextMessage msg3 = sess.createTextMessage();
      prod.send(msg3);
      TextMessage received3 = (TextMessage) cons.receive(1000);
      assertNotNull(received3);
      assertNull(received3.getText());
   }

   @Test
   public void testSendReceiveWithBody0() throws Exception {
      testSendReceiveWithBody(0);
   }

   @Test
   public void testSendReceiveWithBody1() throws Exception {
      testSendReceiveWithBody(1);
   }

   @Test
   public void testSendReceiveWithBody9() throws Exception {
      testSendReceiveWithBody(9);
   }

   @Test
   public void testSendReceiveWithBody20() throws Exception {
      testSendReceiveWithBody(20);
   }

   @Test
   public void testSendReceiveWithBody10000() throws Exception {
      testSendReceiveWithBody(10000);
   }

   @Test
   public void testSendReceiveWithBody0xffff() throws Exception {
      testSendReceiveWithBody(0xffff);
   }

   @Test
   public void testSendReceiveWithBody0xffffplus1() throws Exception {
      testSendReceiveWithBody(0xffff + 1);
   }

   @Test
   public void testSendReceiveWithBody0xfffftimes2() throws Exception {
      testSendReceiveWithBody(2 * 0xffff);
   }

   private void testSendReceiveWithBody(final int bodyLength) throws Exception {
      conn = cf.createConnection();
      char[] chrs = new char[bodyLength];

      for (int i = 0; i < bodyLength; i++) {
         chrs[i] = RandomUtil.randomChar();
      }
      String str = new String(chrs);

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = sess.createProducer(queue);

      conn.start();

      MessageConsumer cons = sess.createConsumer(queue);

      TextMessage msg1 = sess.createTextMessage(str);
      prod.send(msg1);
      TextMessage received1 = (TextMessage) cons.receive(1000);
      assertNotNull(received1);
      assertEquals(str, received1.getText());

      TextMessage msg2 = sess.createTextMessage();
      msg2.setText(str);
      prod.send(msg2);
      TextMessage received2 = (TextMessage) cons.receive(1000);
      assertNotNull(received2);
      assertEquals(str, received2.getText());

      assertEquals(str, msg2.getText());

      // Now resend it
      prod.send(received2);
      assertEquals(str, received2.getText());
      TextMessage received3 = (TextMessage) cons.receive(1000);
      assertNotNull(received3);
      assertEquals(str, received3.getText());

      // And resend again

      prod.send(received3);
      assertEquals(str, received3.getText());
      TextMessage received4 = (TextMessage) cons.receive(1000);
      assertNotNull(received4);
      assertEquals(str, received4.getText());
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

      jmsServer.createConnectionFactory("ManualReconnectionToSingleServerTest", false, JMSFactoryType.CF, registerConnectors(server, connectorConfigs), null, ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD, ActiveMQClient.DEFAULT_CONNECTION_TTL, callTimeout, ActiveMQClient.DEFAULT_CALL_FAILOVER_TIMEOUT, true, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, ActiveMQClient.DEFAULT_COMPRESS_LARGE_MESSAGES, ActiveMQClient.DEFAULT_COMPRESSION_LEVEL, ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE, ActiveMQClient.DEFAULT_CONSUMER_MAX_RATE, ActiveMQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_MAX_RATE, ActiveMQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE, ActiveMQClient.DEFAULT_BLOCK_ON_DURABLE_SEND, ActiveMQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND, ActiveMQClient.DEFAULT_AUTO_GROUP, ActiveMQClient.DEFAULT_PRE_ACKNOWLEDGE, ActiveMQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_USE_GLOBAL_POOLS, ActiveMQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_THREAD_POOL_MAX_SIZE, retryInterval, retryIntervalMultiplier, ActiveMQClient.DEFAULT_MAX_RETRY_INTERVAL, reconnectAttempts, ActiveMQClient.DEFAULT_FAILOVER_ON_INITIAL_CONNECTION, null, jndiBindings);
   }
}
