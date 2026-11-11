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
package org.apache.activemq.artemis.tests.integration.cluster.distribution;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import java.util.Map;

public class TwoWayTwoNodeClusterTest extends ClusterTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      setupServers();
      setupClusters();
   }

   protected void setupServers() throws Exception {
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());
   }

   protected void setupClusters() {
      setupClusterConnection("cluster0", 0, 1, "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), false);
      setupClusterConnection("cluster1", 1, 0, "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), false);
   }

   protected boolean isNetty() {
      return false;
   }

   /*
    * This test starts 2 servers and send messages to
    * a queue until it enters into paging state. Then
    * it changes the max-size to -1, restarts the 2 servers
    * and consumes all the messages. If verifies that
    * even if the max-size has changed all the paged
    * messages will be depaged and consumed. No stuck
    * messages after restarting.
    */
   @Test
   @Timeout(60)
   public void testClusterRestartWithConfigChanged() throws Exception {
      Configuration config0 = servers[0].getConfiguration();
      Configuration config1 = servers[1].getConfiguration();

      configureBeforeStart(config0, config1);
      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, "queues", "queue0", null, true);
      createQueue(1, "queues", "queue0", null, true);

      waitForBindings(0, "queues", 1, 0, true);
      waitForBindings(1, "queues", 1, 0, true);

      waitForBindings(0, "queues", 1, 0, false);
      waitForBindings(1, "queues", 1, 0, false);

      ClientSessionFactory sf0 = sfs[0];
      ClientSession session0 = sf0.createSession(false, false);
      ClientProducer producer = session0.createProducer("queues");
      final int numSent = 200;
      for (int i = 0; i < numSent; i++) {
         ClientMessage msg = createTextMessage(session0, true, 5000);
         producer.send(msg);
         if (i % 50 == 0) {
            session0.commit();
         }
      }
      session0.commit();
      session0.close();

      Wait.assertTrue(() -> getMessageCount(servers[0], "queues") + getMessageCount(servers[1], "queues") >= numSent, 5000);

      Queue queue0 = servers[0].locateQueue(SimpleString.of("queue0"));
      assertTrue(queue0.getPageSubscription().isPaging());

      closeAllSessionFactories();
      stopServers(0, 1);

      AddressSettings addressSettings0 = config0.getAddressSettings().get("#");
      AddressSettings addressSettings1 = config1.getAddressSettings().get("#");

      addressSettings0.setMaxSizeBytes(-1);
      addressSettings1.setMaxSizeBytes(-1);

      startServers(0, 1);

      waitForBindings(0, "queues", 1, 0, true);
      waitForBindings(1, "queues", 1, 0, true);

      waitForBindings(0, "queues", 1, 0, false);
      waitForBindings(1, "queues", 1, 0, false);

      setupSessionFactory(0, isNetty());
      addConsumer(0, 0, "queue0", null);

      waitForBindings(0, "queues", 1, 1, true);

      for (int i = 0; i < numSent; i++) {
         ClientMessage m = consumers[0].consumer.receive(5000);
         assertNotNull(m, "failed to receive message " + i);
      }
   }

   private void configureBeforeStart(Configuration... serverConfigs) {
      for (Configuration config : serverConfigs) {
         config.setPersistenceEnabled(true);
         config.setMessageCounterEnabled(true);
         config.setJournalFileSize(20 * 1024);
         config.setJournalMinFiles(5);

         Map<String, AddressSettings> addressSettingsMap0 = config.getAddressSettings();
         AddressSettings addrSettings = addressSettingsMap0.get("#");
         if (addrSettings == null) {
            addrSettings = new AddressSettings();
            addressSettingsMap0.put("#", addrSettings);
         }
         addrSettings.setDeadLetterAddress(SimpleString.of("jms.queue.DLQ"));
         addrSettings.setExpiryAddress(SimpleString.of("jms.queue.ExpiryQueue"));
         addrSettings.setRedeliveryDelay(30);
         addrSettings.setMaxDeliveryAttempts(5);
         addrSettings.setMaxSizeBytes(1048576);
         addrSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
         addrSettings.setPageSizeBytes(524288);
         addrSettings.setMessageCounterHistoryDayLimit(10);
         addrSettings.setRedistributionDelay(1000).setMaxReadPageBytes(-1).setMaxReadPageMessages(-1);
      }
   }

   @Test
   public void testStartStop() throws Exception {

      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, "queues", "queue0", null, false);
      createQueue(1, "queues", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 1, "queue0", null);

      waitForBindings(0, "queues", 1, 1, true);
      waitForBindings(1, "queues", 1, 1, true);

      waitForBindings(0, "queues", 1, 1, false);
      waitForBindings(1, "queues", 1, 1, false);

      send(0, "queues", 10, false, null);
      verifyReceiveRoundRobin(10, 0, 1);
      verifyNotReceive(0, 1);

      stopServers(0, 1);
   }

   @Test
   public void testStartPauseStartOther() throws Exception {

      startServers(0);

      setupSessionFactory(0, isNetty());
      createQueue(0, "queues", "queue0", null, false);
      addConsumer(0, 0, "queue0", null);

      startServers(1);
      setupSessionFactory(1, isNetty());
      createQueue(1, "queues", "queue0", null, false);

      addConsumer(1, 1, "queue0", null);

      waitForBindings(0, "queues", 1, 1, true);
      waitForBindings(1, "queues", 1, 1, true);

      waitForBindings(0, "queues", 1, 1, false);
      waitForBindings(1, "queues", 1, 1, false);

      send(0, "queues", 10, false, null);
      verifyReceiveRoundRobin(10, 0, 1);
      verifyNotReceive(0, 1);

      stopServers(0, 1);
   }

   @Test
   public void testStopStart() throws Exception {
      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, "queues", "queue0", null, true);
      createQueue(1, "queues", "queue0", null, true);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 1, "queue0", null);

      waitForBindings(0, "queues", 1, 1, true);
      waitForBindings(1, "queues", 1, 1, true);

      waitForBindings(0, "queues", 1, 1, false);
      waitForBindings(1, "queues", 1, 1, false);

      send(0, "queues", 10, false, null);
      verifyReceiveRoundRobin(10, 0, 1);
      verifyNotReceive(0, 1);

      removeConsumer(1);

      closeSessionFactory(1);

      stopServers(1);

      //allow the topology to be propagated before restarting
      waitForTopology(servers[0], 1, -1, 2000);

      logger.debug(clusterDescription(servers[0]));

      startServers(1);

      logger.debug(clusterDescription(servers[0]));
      logger.debug(clusterDescription(servers[1]));

      setupSessionFactory(1, isNetty());

      // createQueue(1, "queues", "queue0", null, false);

      addConsumer(1, 1, "queue0", null);

      waitForBindings(0, "queues", 1, 1, true);
      waitForBindings(1, "queues", 1, 1, true);

      waitForBindings(1, "queues", 1, 1, false);
      waitForBindings(0, "queues", 1, 1, false);

      send(0, "queues", 10, false, null);
      verifyReceiveRoundRobin(10, 0, 1);
      verifyNotReceive(0, 1);

      stopServers(0, 1);
   }
}
