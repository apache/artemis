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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockingSendTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testSinglePersistentBlockingNonSync() throws Exception {
      ActiveMQServer server = createServer(true);
      ClientSession session = null;
      ClientSessionFactory factory = null;

      ServerLocator locator = null;

      server.getConfiguration().setJournalSyncNonTransactional(false);
      server.getConfiguration().setJournalBufferTimeout_AIO(15000);

      server.start();

      logger.debug("sync = {}", server.getConfiguration().isJournalSyncNonTransactional());
      locator = createInVMNonHALocator().setBlockOnDurableSend(true);
      factory = createSessionFactory(locator);

      session = factory.createSession();

      session.createQueue(QueueConfiguration.of("queue").setAddress("address").setRoutingType(RoutingType.ANYCAST));

      ClientProducer prod = session.createProducer("address");

      ClientMessage message = session.createMessage(true);

      prod.send(message);

      ClientConsumer consumer = session.createConsumer("queue");

      session.start();

      ClientMessage msg = consumer.receive(5000);

      assertNotNull(msg);

      msg.acknowledge();
   }
}
