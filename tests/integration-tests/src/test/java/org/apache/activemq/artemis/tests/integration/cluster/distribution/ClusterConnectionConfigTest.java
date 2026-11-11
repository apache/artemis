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
package org.apache.activemq.artemis.tests.integration.cluster.distribution;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Random;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.management.impl.view.ConnectionField;
import org.apache.activemq.artemis.core.management.impl.view.predicate.ActiveMQFilterPredicate;
import org.apache.activemq.artemis.core.server.cluster.MessageFlowRecord;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionImpl;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClusterConnectionConfigTest extends ClusterTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      start();
   }

   private void start() throws Exception {
      setupServers();
   }

   protected boolean isNetty() {
      return true;
   }

   protected void setupServers() throws Exception {
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());
   }

   protected void setupCluster(final MessageLoadBalancingType messageLoadBalancingType, final ClusterConfigCallback cb) throws Exception {
      setupClusterConnection("cluster0", "queues", messageLoadBalancingType, 1, isNetty(), cb, 0, 1, 2);
      setupClusterConnection("cluster1", "queues", messageLoadBalancingType, 1, isNetty(), cb, 1, 0, 2);
   }

   @Test
   public void testRedistributionFlowControl() throws Exception {
      final int producerWindow = new Random().nextInt(Integer.MAX_VALUE);
      setupCluster(MessageLoadBalancingType.ON_DEMAND, (ClusterConnectionConfiguration cfg) -> {
         cfg.setProducerWindowSize(producerWindow);
      });
      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);

      addConsumer(1, 1, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 0, true);
      waitForBindings(1, "queues.testaddress", 1, 1, true);

      waitForBindings(0, "queues.testaddress", 1, 1, false);
      waitForBindings(1, "queues.testaddress", 1, 0, false);

      send(0, "queues.testaddress", 1, false, null);

      //receiveMessages(consumers[1].consumer, 0, 1, true);
      Thread.sleep(5000);
      makeSureForwardingFlowControl(producerWindow, 0, 1);


      removeConsumer(1);
   }

   private void makeSureForwardingFlowControl(int producerWindow, int... indices) throws NoSuchFieldException, IllegalAccessException {
      for (int i : indices) {
         ClusterConnectionImpl cc = (ClusterConnectionImpl) servers[i].getClusterManager().getClusterConnection("cluster" + i);
         Map<String, MessageFlowRecord> map = cc.getRecords();
         assertEquals(1, map.size());
         MessageFlowRecord record = map.entrySet().iterator().next().getValue();

         Field f = record.getClass().getDeclaredField("targetLocator"); //NoSuchFieldException
         f.setAccessible(true);
         ServerLocatorInternal targetLocator = (ServerLocatorInternal) f.get(record);
         assertEquals(producerWindow, targetLocator.getProducerWindowSize());
      }
   }

   @Test
   public void testClusterConnectionClientId() throws Exception {
      final String clientId = RandomUtil.randomUUIDString();
      setupCluster(MessageLoadBalancingType.ON_DEMAND, (ClusterConnectionConfiguration cfg) -> {
         cfg.setClientId(clientId);
      });
      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);

      waitForBindings(0, "queues.testaddress", 1, 0, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 1, 0, false);
      waitForBindings(1, "queues.testaddress", 1, 0, false);

      String connectionsAsJsonString = servers[1].getActiveMQServerControl().listConnections(createJsonFilter(ConnectionField.CLIENT_ID.getName(), ActiveMQFilterPredicate.Operation.EQUALS.toString(), clientId), 1, 1);
      JsonObject connectionsAsJsonObject = JsonUtil.readJsonObject(connectionsAsJsonString);
      JsonArray array = (JsonArray) connectionsAsJsonObject.get("data");
      assertEquals(1, array.size(), "number of connections returned from query");
   }

}
