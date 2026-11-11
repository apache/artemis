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
package org.apache.activemq.artemis.tests.integration.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.RemoteQueueBinding;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the store-and-forward (SnF) disable mechanism used during scale-down drain: when a cluster connection
 * disables a record, the remote queue bindings for that node are marked disabled so that no further messages are routed
 * (egress) or redistributed into the draining SnF queue.
 *
 * @see org.apache.activemq.artemis.core.server.cluster.ClusterConnection#disableRecord(String)
 * @see org.apache.activemq.artemis.core.postoffice.Binding#isEnabled()
 */
public class ScaleDownDisableBindingTest extends ClusterTestBase {

   private static final String CLUSTER_NAME = "cluster0";
   private static final String ADDRESS = "testAddress";
   private static final String QUEUE = "testQueue";

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      setupPrimaryServer(0, isFileStorage(), isNetty(), true);
      setupPrimaryServer(1, isFileStorage(), isNetty(), true);
      setupClusterConnection(CLUSTER_NAME, ADDRESS, MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection(CLUSTER_NAME, ADDRESS, MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);
      servers[0].getConfiguration().getAddressSettings().put("#", new AddressSettings().setRedistributionDelay(0));
      servers[1].getConfiguration().getAddressSettings().put("#", new AddressSettings().setRedistributionDelay(0));
      startServers(0, 1);
      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
   }

   protected boolean isNetty() {
      return true;
   }

   /**
    * Once the record is disabled, no message must enter node 0's store-and-forward queue bound for node 1, neither by
    * fresh routing nor by redistribution of already-queued messages. This is the "ingress" side of the drain: the SnF
    * queue stops filling so the bridge can drain it to a stable target. The complementary "egress" side (the bridge
    * forwarding the SnF queue out to the remote node) is driven by the bridge lifecycle and covered by
    * {@link ScaleDownTest}.
    */
   @Test
   public void testDisabledRemoteBindingBlocksSnFIngress() throws Exception {
      final int messageCount = 10;

      createQueue(0, ADDRESS, QUEUE, null, true);
      createQueue(1, ADDRESS, QUEUE, null, true);

      // a consumer only on node 1 so ON_DEMAND load balancing routes messages from node 0 to node 1
      addConsumer(0, 1, QUEUE, null);

      waitForBindings(0, ADDRESS, 1, 0, true);
      waitForBindings(0, ADDRESS, 1, 1, false);

      // sanity check: while enabled, messages sent to node 0 flow through the SnF queue and reach node 1
      send(0, ADDRESS, messageCount, true, null);
      for (int i = 0; i < messageCount; i++) {
         ClientMessage message = consumers[0].getConsumer().receive(2000);
         assertNotNull(message, "message " + i + " should be delivered via node 1 while the binding is enabled");
         message.acknowledge();
      }

      // disable the record, mirroring what the scale-down drain does before moving the SnF references
      ClusterConnection clusterConnection = servers[0].getClusterManager().getClusterConnection(CLUSTER_NAME);
      clusterConnection.disableRecord(servers[1].getNodeID().toString());

      RemoteQueueBinding remoteBinding = getRemoteQueueBinding(servers[0], QUEUE);
      assertFalse(remoteBinding.isEnabled(), "remote binding should be disabled right after disableRecord returns");

      // messages sent now must stay on node 0 rather than being routed/redistributed to the disabled node 1
      send(0, ADDRESS, messageCount, true, null);

      LocalQueueBinding localBinding = (LocalQueueBinding) servers[0].getPostOffice().getBinding(SimpleString.of(QUEUE));
      Wait.assertEquals(messageCount, () -> getMessageCount(localBinding.getQueue()), 2000, 50);

      // the SnF queue bound for node 1 must have received nothing: ingress is blocked
      assertEquals(0, getMessageCount(getSnFQueue(servers[0], servers[1])), "no message should enter the SnF queue of the draining node");

      // and the consumer on node 1 must not receive any of them
      assertNull(consumers[0].getConsumer().receiveImmediate(), "no message should reach the disabled node");
   }

   private RemoteQueueBinding getRemoteQueueBinding(ActiveMQServer server, String queueName) {
      SimpleString name = SimpleString.of(queueName);
      return server.getPostOffice().getAllBindings()
         .filter(RemoteQueueBinding.class::isInstance)
         .map(RemoteQueueBinding.class::cast)
         .filter(binding -> name.equals(binding.getRoutingName()))
         .findFirst()
         .orElseThrow(() -> new IllegalStateException("No remote queue binding for " + queueName));
   }

   private Queue getSnFQueue(ActiveMQServer server, ActiveMQServer targetServer) {
      String snfPrefix = server.getInternalNamingPrefix() + "sf.";
      String targetNodeID = targetServer.getNodeID().toString();
      return server.getPostOffice().getAllBindings()
         .filter(LocalQueueBinding.class::isInstance)
         .map(LocalQueueBinding.class::cast)
         .map(LocalQueueBinding::getQueue)
         .filter(queue -> queue.getAddress().toString().startsWith(snfPrefix) && queue.getAddress().toString().endsWith(targetNodeID))
         .findFirst()
         .orElseThrow(() -> new IllegalStateException("No SnF queue toward " + targetNodeID));
   }
}
