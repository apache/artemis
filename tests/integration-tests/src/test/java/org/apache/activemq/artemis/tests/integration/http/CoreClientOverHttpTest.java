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
package org.apache.activemq.artemis.tests.integration.http;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoreClientOverHttpTest extends ActiveMQTestBase {

   private static final SimpleString QUEUE = SimpleString.of("CoreClientOverHttpTestQueue");
   private static final String CONNECTOR_NAME = "http-connector";
   private Configuration conf;
   private ActiveMQServer server;
   private ServerLocator locator;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.HTTP_ENABLED_PROP_NAME, true);
      // Keep idle GET scans aggressive so HTTP response slots can be replenished quickly.
      params.put(TransportConstants.HTTP_CLIENT_IDLE_PROP_NAME, 100L);
      params.put(TransportConstants.HTTP_CLIENT_IDLE_SCAN_PERIOD, 50L);

      TransportConfiguration connectorConfig = new TransportConfiguration(NETTY_CONNECTOR_FACTORY, params, CONNECTOR_NAME);
      conf = createDefaultInVMConfig()
         .clearAcceptorConfigurations()
         .addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params))
         .addConnectorConfiguration(CONNECTOR_NAME, connectorConfig)
         .addClusterConfiguration(basicClusterConnectionConfig(CONNECTOR_NAME));

      server = addServer(ActiveMQServers.newActiveMQServer(conf, false));
      server.start();
      locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(connectorConfig));
      locator.setCallTimeout(5000);
   }

   @Test
   public void testCoreHttpClient() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
         message.getBodyBuffer().writeString("CoreClientOverHttpTest");
         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);

      session.start();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message2 = consumer.receive();

         assertEquals("CoreClientOverHttpTest", message2.getBodyBuffer().readString());

         message2.acknowledge();
      }

      session.close();
   }

   @Test
   public void testCoreHttpClientIdle() throws Exception {
      locator.setConnectionTTL(500);
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      ClientProducer producer = session.createProducer(QUEUE);

      Thread.sleep(500 * 5);

      session.close();
   }

   @Test
   public void testCoreHttpClient8kPlus() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 100;

      String[] content = new String[numMessages];

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
         content[i] = RandomUtil.randomAlphaNumericString(((i % 5) + 1) * 1024 * 8);
         message.getBodyBuffer().writeString(content[i]);
         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);

      session.start();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message2 = consumer.receive();

         assertEquals(content[i], message2.getBodyBuffer().readString());

         message2.acknowledge();
      }

      session.close();
   }

   /**
    * ARTEMIS-6172: server-initiated ClusterTopology packets consume HTTP response slots.
    * Idle GET polling must replenish slots so request/reply traffic is not starved.
    */
   @Test
   public void testClusterTopologyDoesNotStarveHttpRequestReplies() throws Exception {
      AtomicInteger topologyEvents = new AtomicInteger();
      locator.addClusterTopologyListener(new ClusterTopologyListener() {
         @Override
         public void nodeUP(TopologyMember topologyMember, boolean last) {
            topologyEvents.incrementAndGet();
         }

         @Override
         public void nodeDown(long uniqueEventID, String nodeID) {
            topologyEvents.incrementAndGet();
         }
      });

      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);

      ClusterConnection clusterConnection = server.getClusterManager().getClusterConnection("cluster1");
      assertNotNull(clusterConnection);
      Topology topology = clusterConnection.getTopology();

      final int topologyUpdates = 50;
      final int topologyEventsBeforeFlood = topologyEvents.get();
      final long uniqueEventIDBase = System.currentTimeMillis();
      for (int i = 0; i < topologyUpdates; i++) {
         Map<String, Object> memberParams = new HashMap<>();
         memberParams.put(TransportConstants.HOST_PROP_NAME, "127.0.0.1");
         memberParams.put(TransportConstants.PORT_PROP_NAME, 17000 + i);
         TransportConfiguration memberConnector = new TransportConfiguration(NETTY_CONNECTOR_FACTORY, memberParams);
         // Use backup-only members so ClusterConnectionImpl does not attempt bridges to fake nodes,
         // while still emitting ClusterTopology packets on the HTTP client connection.
         String nodeId = "http-topo-" + i;
         TopologyMemberImpl member = new TopologyMemberImpl(nodeId, null, null, null, memberConnector);
         assertTrue(topology.updateMember(uniqueEventIDBase + i, nodeId, member));
      }

      Wait.assertTrue(() -> topologyEvents.get() >= topologyEventsBeforeFlood + topologyUpdates, 5_000, 50);

      // Synchronous Core requests must still complete after async topology consumed HTTP slots.
      SimpleString starvationQueue = SimpleString.of("HttpTopologyStarvationQueue");
      session.createQueue(QueueConfiguration.of(starvationQueue).setDurable(false));

      ClientProducer producer = session.createProducer(starvationQueue);
      ClientMessage message = session.createMessage(false);
      message.getBodyBuffer().writeString("topology-http");
      producer.send(message);

      ClientConsumer consumer = session.createConsumer(starvationQueue);
      session.start();
      ClientMessage received = consumer.receive(5_000);
      assertNotNull(received);
      assertEquals("topology-http", received.getBodyBuffer().readString());
      received.acknowledge();

      session.close();
   }
}
