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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ScheduledMessageRestartTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(true, false);
      server.start();
   }

   @Test
   public void testSchedulePropertyExistsAfterRestart() throws Exception {
      final String queueName = RandomUtil.randomUUIDString();
      final long scheduledTime = System.currentTimeMillis() * 2;
      server.createQueue(QueueConfiguration.of(queueName).setAddress(queueName));
      ServerLocator locator = createInVMLocator(0);
      ClientSessionFactory factory = locator.createSessionFactory();
      ClientSession session = factory.createSession();
      ClientProducer producer = session.createProducer(queueName);
      ClientMessage m = session.createMessage(true);
      m.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, scheduledTime);
      producer.send(m);
      locator.close();
      server.stop();
      server.start();
      List<MessageReference> scheduledMessages = server.locateQueue(queueName).getScheduledMessages();
      assertEquals(1, scheduledMessages.size());
      Message serverMessage = scheduledMessages.get(0).getMessage();
      assertTrue(serverMessage.containsProperty(Message.HDR_SCHEDULED_DELIVERY_TIME));
      assertEquals(scheduledTime, serverMessage.getLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME).longValue());
   }
}
