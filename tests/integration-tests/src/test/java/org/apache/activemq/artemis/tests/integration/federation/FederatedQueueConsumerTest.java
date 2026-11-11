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
package org.apache.activemq.artemis.tests.integration.federation;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.ScheduledFuture;

import org.apache.activemq.artemis.core.config.FederationConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.federation.FederatedQueueConsumerImpl;
import org.apache.activemq.artemis.core.server.federation.Federation;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;

public class FederatedQueueConsumerTest extends ActiveMQTestBase {

   @Test
   public void testClose() throws Exception {
      ActiveMQServer server = createServer(false, createDefaultInVMConfig());
      server.start();
      Federation federation = new Federation(server, new FederationConfiguration().setName(RandomUtil.randomUUIDString()));
      federation.start();
      FederatedQueueConsumerImpl consumer = new FederatedQueueConsumerImpl(federation, server, null, null, null, null);
      assertNull(consumer.getCurrentConnectTask());
      consumer.start();
      assertNotNull(consumer.getCurrentConnectTask());
      consumer.close();
      Wait.assertTrue(() -> {
         ScheduledFuture task = consumer.getCurrentConnectTask();
         return (task.isDone() || task.isCancelled()) && task == consumer.getCurrentConnectTask();
      }, 2000, 50);
   }
}
