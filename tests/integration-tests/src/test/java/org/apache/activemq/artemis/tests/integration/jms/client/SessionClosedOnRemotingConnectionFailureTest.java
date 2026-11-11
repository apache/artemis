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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.jms.client.ActiveMQSession;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.Test;

public class SessionClosedOnRemotingConnectionFailureTest extends JMSTestBase {

   @Test
   public void testSessionClosedOnRemotingConnectionFailure() throws Exception {
      List<TransportConfiguration> connectorConfigs = new ArrayList<>();
      connectorConfigs.add(new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      jmsServer.createConnectionFactory("cffoo", false, JMSFactoryType.CF, registerConnectors(server, connectorConfigs), null, ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD, ActiveMQClient.DEFAULT_CONNECTION_TTL, ActiveMQClient.DEFAULT_CALL_TIMEOUT, ActiveMQClient.DEFAULT_CALL_FAILOVER_TIMEOUT, ActiveMQClient.DEFAULT_CACHE_LARGE_MESSAGE_CLIENT, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, ActiveMQClient.DEFAULT_COMPRESS_LARGE_MESSAGES, ActiveMQClient.DEFAULT_COMPRESSION_LEVEL, ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE, ActiveMQClient.DEFAULT_CONSUMER_MAX_RATE, ActiveMQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_MAX_RATE, ActiveMQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE, ActiveMQClient.DEFAULT_BLOCK_ON_DURABLE_SEND, ActiveMQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND, ActiveMQClient.DEFAULT_AUTO_GROUP, ActiveMQClient.DEFAULT_PRE_ACKNOWLEDGE, ActiveMQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_USE_GLOBAL_POOLS, ActiveMQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_RETRY_INTERVAL, ActiveMQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER, ActiveMQClient.DEFAULT_MAX_RETRY_INTERVAL, 0, ActiveMQClient.DEFAULT_FAILOVER_ON_INITIAL_CONNECTION, null, "/cffoo");

      cf = (ConnectionFactory) namingContext.lookup("/cffoo");

      Connection conn = cf.createConnection();

      Queue queue = createQueue("testQueue");

      try {
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = session.createProducer(queue);

         MessageConsumer cons = session.createConsumer(queue);

         conn.start();

         prod.send(session.createMessage());

         assertNotNull(cons.receive());

         // Now fail the underlying connection

         RemotingConnection connection = ((ClientSessionInternal) ((ActiveMQSession) session).getCoreSession()).getConnection();

         connection.fail(new ActiveMQNotConnectedException());

         // Now try and use the producer

         try {
            prod.send(session.createMessage());

            fail("Should throw exception");
         } catch (JMSException e) {
            // assertEquals(ActiveMQException.OBJECT_CLOSED, e.getCode());
         }

         try {
            cons.receive();

            fail("Should throw exception");
         } catch (JMSException e) {
            // assertEquals(ActiveMQException.OBJECT_CLOSED, e.getCode());
         }

         session.close();

         conn.close();
      } finally {
         try {
            conn.close();
         } catch (Throwable igonred) {
         }
      }
   }
}
