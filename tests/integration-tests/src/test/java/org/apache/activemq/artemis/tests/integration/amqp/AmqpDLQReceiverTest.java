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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for broker side support of the Durable Subscription mapping for JMS.
 */
public class AmqpDLQReceiverTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Timeout(60)
   public void testCreateDurableReceiver() throws Exception {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(getQueueName());
      sendMessages(getQueueName(), 1);
      Queue queue = getProxyToQueue(getQueueName());
      assertNotNull(queue);
      receiver.flow(100);
      for (int i = 0; i < 10; i++) {
         logger.debug("i = {}", i);
         AmqpMessage receive = receiver.receive(5000, TimeUnit.MILLISECONDS);
         receive.modified(true, false);
         Queue queueView = getProxyToQueue(getQueueName());
         logger.debug("receive = {}", receive.getWrappedMessage().getDeliveryCount());
         logger.debug("queueView.getMessageCount() = {}", queueView.getMessageCount());
         logger.debug("queueView.getDeliveringCount() = {}", queueView.getDeliveringCount());
         logger.debug("queueView.getPersistentSize() = {}", queueView.getPersistentSize());
      }

      receiver.close();
      connection.close();
      Queue queueView = getProxyToQueue(getQueueName());
      logger.debug("queueView.getMessageCount() = {}", queueView.getMessageCount());
      logger.debug("queueView.getDeliveringCount() = {}", queueView.getDeliveringCount());
      logger.debug("queueView.getPersistentSize() = {}", queueView.getPersistentSize());
      Wait.assertEquals(0, queueView::getMessageCount);
   }

}
