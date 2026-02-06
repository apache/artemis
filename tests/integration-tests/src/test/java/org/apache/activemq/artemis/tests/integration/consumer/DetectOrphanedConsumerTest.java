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

package org.apache.activemq.artemis.tests.integration.consumer;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.management.impl.view.ConsumerField;
import org.apache.activemq.artemis.core.management.impl.view.ConsumerView;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.ServerConsumerImpl;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test is simulating an orphaned consumer situation that was fixed in ARTEMIS-4476. the method
 * QueueControl::listConsumersAsJSON should add a field orphaned=true in case the consumer is orphaned.
 */
public class DetectOrphanedConsumerTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testOrphanedConsumerCORE() throws Exception {
      testOrphanedConsumer("CORE");
   }

   @Test
   public void testOrphanedConsumerAMQP() throws Exception {
      testOrphanedConsumer("AMQP");
   }

   @Test
   public void testOrphanedConsumerOpenWire() throws Exception {
      testOrphanedConsumer("OPENWIRE");
   }

   private void testOrphanedConsumer(String protocol) throws Exception {

      ActiveMQServer server = createServer(false, createDefaultConfig(true));
      server.start();

      Queue queue = server.createQueue(QueueConfiguration.of(getName()).setDurable(true).setName(getName()).setRoutingType(RoutingType.ANYCAST));

      ConnectionFactory connectionFactory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      Connection connection = connectionFactory.createConnection();

      //////////////////////////////////////////////////////
      // this close is to be done after the test is done
      runAfter(connection::close);
      //////////////////////////////////////////////////////

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      // this consumer is never used here.
      MessageConsumer willBeOrphaned = session.createConsumer(session.createQueue(getName()));

      Wait.assertEquals(1, queue::getConsumerCount, 5000);

      QueueControl queueControl = server.getManagementService().getQueueControl(queue.getName().toString());
      assertNotNull(queueControl);

      String result = queueControl.listConsumersAsJSON();
      logger.debug("json: {}", result);

      JsonArray resultArray = JsonUtil.readJsonArray(result);
      assertEquals(1, resultArray.size());
      assertEquals(ConsumerView.CONSUMER_STATUS_OK, resultArray.getJsonObject(0).getString(ConsumerField.STATUS.getName()));

      queue.getConsumers().forEach(c -> {
         ServerConsumerImpl serverConsumer = (ServerConsumerImpl) c;
         logger.debug("Removing connection for {} on connectionID {}", serverConsumer, serverConsumer.getConnectionID());
         Object removed = server.getRemotingService().removeConnection(serverConsumer.getConnectionID());
         logger.debug("removed {}", removed);
      });

      result = queueControl.listConsumersAsJSON();
      logger.debug("json: {}", result);

      resultArray = JsonUtil.readJsonArray(result);
      assertEquals(1, resultArray.size());
      assertEquals(ConsumerView.CONSUMER_STATUS_ORPHANED, resultArray.getJsonObject(0).getString(ConsumerField.STATUS.getName()));

      ActiveMQServerControl serverControl = (ActiveMQServerControl) server.getManagementService().getServerControl();
      String sessionID = resultArray.getJsonObject(0).getString(ConsumerField.SESSION.getAlternativeName());
      int consumerID = resultArray.getJsonObject(0).getInt(ConsumerField.SEQUENTIAL_ID.getAlternativeName());
      logger.debug("SessionID{} ConsumerID::{}", sessionID, consumerID);
      assertTrue(serverControl.closeConsumerWithID(sessionID, String.valueOf(consumerID)));
   }
}
