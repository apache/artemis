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
package org.apache.activemq.artemis.tests.integration.cluster.warnings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterCleanNodeShutdownTest extends ClusterTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public long shutdownTimeout = 0;


   @Override
   protected final TransportConfiguration createTransportConfiguration(String name,
                                                                       boolean netty,
                                                                       boolean acceptor,
                                                                       Map<String, Object> params) {

      assertTrue(netty);
      TransportConfiguration transportConfiguration = super.createTransportConfiguration(name, netty, acceptor, params);

      transportConfiguration.getParams().put(TransportConstants.SHUTDOWN_TIMEOUT, String.valueOf(shutdownTimeout));

      return transportConfiguration;
   }

   @Test
   public void testNoWarningErrorsDuringRestartingNodesInClusterTimeoutNoTimeout() throws Exception {
      internalTest(0);
   }

   @Test
   public void testNoWarningErrorsDuringRestartingNodesInClusterTimeoutLargeTimeout() throws Exception {
      internalTest(TimeUnit.HOURS.toMillis(2));
   }

   public void internalTest(final long timeout) throws Exception {
      shutdownTimeout = timeout;
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);

      startServers(0, 1);
      getServer(0).getClusterManager().getClusterController().awaitConnectionToReplicationCluster();
      getServer(1).getClusterManager().getClusterController().awaitConnectionToReplicationCluster();

      logger.debug("server 0 = {}", getServer(0).getNodeID());
      logger.debug("server 1 = {}", getServer(1).getNodeID());

      setupSessionFactory(0, isNetty(), 15);
      setupSessionFactory(1, isNetty());

      // now create the 2 queues and make sure they are durable
      createQueue(0, "queues.testaddress", "queue10", null, true);
      createQueue(1, "queues.testaddress", "queue10", null, true);

      addConsumer(0, 0, "queue10", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 1, 0, false);
      waitForBindings(1, "queues.testaddress", 1, 1, false);

      sendInRange(1, "queues.testaddress", 0, 10, true, null);
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         logger.debug("*****************************************************************************");
         stopServers(0);
         // Waiting some time after stopped
         Wait.assertTrue(() -> !getServer(0).isStarted() && !getServer(0).isActive(), 2000L);
         logger.debug("*****************************************************************************");
         assertFalse(loggerHandler.findText("AMQ212037", " [code=DISCONNECTED]"), "Connection failure detected for an expected DISCONNECT event");
         assertFalse(loggerHandler.hasLevel(AssertionLoggerHandler.LogLevel.WARN), "WARN found");
      }
      startServers(0);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 1, 0, false);
      waitForBindings(1, "queues.testaddress", 1, 1, false);

      sendInRange(1, "queues.testaddress", 10, 20, false, null);

      verifyReceiveAllInRange(0, 20, 0);
      logger.debug("*****************************************************************************");
   }

   public boolean isNetty() {
      return true;
   }
}
