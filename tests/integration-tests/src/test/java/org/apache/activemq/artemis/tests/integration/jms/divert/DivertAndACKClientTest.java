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
package org.apache.activemq.artemis.tests.integration.jms.divert;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.List;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.Test;

public class DivertAndACKClientTest extends JMSTestBase {

   @Test
   public void testAutoACK() throws Exception {
      Queue queueSource = createQueue("Source");
      Queue queueTarget = createQueue("Dest");

      Connection connection = cf.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      final MessageProducer producer = session.createProducer(queueSource);

      final TextMessage message = session.createTextMessage("message text");
      producer.send(message);

      connection.start();

      final MessageConsumer consumer = session.createConsumer(queueTarget);
      TextMessage receivedMessage = (TextMessage) consumer.receive(1000);

      assertNotNull(receivedMessage);

      connection.close();
   }

   @Test
   public void testClientACK() throws Exception {
      Queue queueSource = createQueue("Source");
      Queue queueTarget = createQueue("Dest");

      Connection connection = cf.createConnection();
      Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      final MessageProducer producer = session.createProducer(queueSource);

      final TextMessage message = session.createTextMessage("message text");
      producer.send(message);

      connection.start();

      final MessageConsumer consumer = session.createConsumer(queueTarget);
      TextMessage receivedMessage = (TextMessage) consumer.receive(1000);
      assertNotNull(receivedMessage);
      receivedMessage.acknowledge();

      connection.close();
   }

   @Override
   protected boolean usePersistence() {
      return true;
   }

   @Override
   protected Configuration createDefaultConfig(final boolean netty) throws Exception {
      DivertConfiguration divert = new DivertConfiguration().setName("local-divert").setRoutingName("some-name").setAddress("Source").setForwardingAddress("Dest").setExclusive(true);

      Configuration config = super.createDefaultConfig(netty).addDivertConfiguration(divert);

      return config;
   }

   @Override
   protected void createCF(final List<TransportConfiguration> connectorConfigs,
                           final String... jndiBindings) throws Exception {
      int retryInterval = 1000;
      double retryIntervalMultiplier = 1.0;
      int reconnectAttempts = -1;
      int callTimeout = 30000;

      jmsServer.createConnectionFactory("ManualReconnectionToSingleServerTest", false, JMSFactoryType.CF, registerConnectors(server, connectorConfigs), null, ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD, ActiveMQClient.DEFAULT_CONNECTION_TTL, callTimeout, ActiveMQClient.DEFAULT_CALL_FAILOVER_TIMEOUT, ActiveMQClient.DEFAULT_CACHE_LARGE_MESSAGE_CLIENT, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, ActiveMQClient.DEFAULT_COMPRESS_LARGE_MESSAGES, ActiveMQClient.DEFAULT_COMPRESSION_LEVEL, ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE, ActiveMQClient.DEFAULT_CONSUMER_MAX_RATE, ActiveMQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_MAX_RATE, true, // this test needs to block on ACK
                                        ActiveMQClient.DEFAULT_BLOCK_ON_DURABLE_SEND, ActiveMQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND, ActiveMQClient.DEFAULT_AUTO_GROUP, ActiveMQClient.DEFAULT_PRE_ACKNOWLEDGE, ActiveMQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_USE_GLOBAL_POOLS, ActiveMQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_THREAD_POOL_MAX_SIZE, retryInterval, retryIntervalMultiplier, ActiveMQClient.DEFAULT_MAX_RETRY_INTERVAL, reconnectAttempts, ActiveMQClient.DEFAULT_FAILOVER_ON_INITIAL_CONNECTION, null, jndiBindings);
   }

}
