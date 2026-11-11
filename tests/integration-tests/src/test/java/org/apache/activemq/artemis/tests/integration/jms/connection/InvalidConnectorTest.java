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
package org.apache.activemq.artemis.tests.integration.jms.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.Test;

public class InvalidConnectorTest extends JMSTestBase {

   @Test
   public void testInvalidConnector() throws Exception {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.HOST_PROP_NAME, "0.0.0.0");

      List<TransportConfiguration> connectorConfigs = new ArrayList<>();
      connectorConfigs.add(new TransportConfiguration(NETTY_CONNECTOR_FACTORY, params));

      int retryInterval = 1000;
      double retryIntervalMultiplier = 1.0;
      int reconnectAttempts = -1;
      int callTimeout = 30000;

      jmsServer.createConnectionFactory("invalid-cf", false, JMSFactoryType.CF, registerConnectors(server, connectorConfigs), null, ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD, ActiveMQClient.DEFAULT_CONNECTION_TTL, callTimeout, ActiveMQClient.DEFAULT_CALL_FAILOVER_TIMEOUT, ActiveMQClient.DEFAULT_CACHE_LARGE_MESSAGE_CLIENT, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, ActiveMQClient.DEFAULT_COMPRESS_LARGE_MESSAGES, ActiveMQClient.DEFAULT_COMPRESSION_LEVEL, ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE, ActiveMQClient.DEFAULT_CONSUMER_MAX_RATE, ActiveMQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_MAX_RATE, ActiveMQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE, ActiveMQClient.DEFAULT_BLOCK_ON_DURABLE_SEND, ActiveMQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND, ActiveMQClient.DEFAULT_AUTO_GROUP, ActiveMQClient.DEFAULT_PRE_ACKNOWLEDGE, ActiveMQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_USE_GLOBAL_POOLS, ActiveMQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_THREAD_POOL_MAX_SIZE, retryInterval, retryIntervalMultiplier, ActiveMQClient.DEFAULT_MAX_RETRY_INTERVAL, reconnectAttempts, ActiveMQClient.DEFAULT_FAILOVER_ON_INITIAL_CONNECTION, null, "/invalid-cf");

      ActiveMQConnectionFactory invalidCf = (ActiveMQConnectionFactory) namingContext.lookup("/invalid-cf");

      TransportConfiguration[] tcs = invalidCf.getServerLocator().getStaticTransportConfigurations();

      TransportConfiguration tc = tcs[0];

      assertNotSame("0.0.0.0", tc.getParams().get(TransportConstants.HOST_PROP_NAME));
      assertEquals(tc.getParams().get(TransportConstants.HOST_PROP_NAME), InetAddress.getLocalHost().getHostName());
   }
}
