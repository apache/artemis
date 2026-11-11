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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AckBatchSizeTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public final SimpleString addressA = SimpleString.of("addressA");

   public final SimpleString queueA = SimpleString.of("queueA");

   public final SimpleString queueB = SimpleString.of("queueB");

   public final SimpleString queueC = SimpleString.of("queueC");

   /*ackbatchSize tests*/

   // tests that we don't acknowledge until the correct ackBatchSize is reached
   private int getMessageEncodeSize(final SimpleString address) throws Exception {
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession(false, true, true);
      ClientMessage message = session.createMessage(false);
      // we need to set the destination so we can calculate the encodesize correctly
      message.setAddress(address);
      int encodeSize = message.getEncodeSize();
      session.close();
      cf.close();
      return encodeSize;
   }

   @Test
   public void testAckBatchSize() throws Exception {
      ActiveMQServer server = createServer(false);
      server.start();
      int numMessages = 100;
      int originalSize = getMessageEncodeSize(addressA);
      ServerLocator locator = createInVMNonHALocator().setAckBatchSize(numMessages * originalSize).setBlockOnAcknowledge(true);
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession sendSession = cf.createSession(false, true, true);

      ClientSession session = cf.createSession(false, true, true);
      session.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      ClientProducer cp = sendSession.createProducer(addressA);
      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = (ClientMessage)sendSession.createMessage(false).setAddress(addressA);
         assertEquals(originalSize, message.getEncodeSize());
         cp.send(message);
         assertEquals(originalSize, message.getEncodeSize());
      }

      ClientConsumer consumer = session.createConsumer(queueA);
      session.start();
      for (int i = 0; i < numMessages - 1; i++) {
         logger.debug("Receive ");
         ClientMessage m = consumer.receive(5000);
         assertEquals(0, m.getPropertyNames().size());
         assertEquals(originalSize, m.getEncodeSize(), "expected to be " + originalSize);
         m.acknowledge();
      }

      ClientMessage m = consumer.receive(5000);
      Queue q = (Queue) server.getPostOffice().getBinding(queueA).getBindable();
      assertEquals(numMessages, q.getDeliveringCount());
      m.acknowledge();
      assertEquals(0, q.getDeliveringCount());
      sendSession.close();
      session.close();
   }

   // tests that when the ackBatchSize is 0 we ack every message directly
   @Test
   public void testAckBatchSizeZero() throws Exception {
      ActiveMQServer server = createServer(false);

      server.start();
      ServerLocator locator = createInVMNonHALocator().setAckBatchSize(0).setBlockOnAcknowledge(true);
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession sendSession = cf.createSession(false, true, true);
      int numMessages = 100;

      ClientSession session = cf.createSession(false, true, true);
      session.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      ClientProducer cp = sendSession.createProducer(addressA);
      for (int i = 0; i < numMessages; i++) {
         cp.send(sendSession.createMessage(false));
      }

      ClientConsumer consumer = session.createConsumer(queueA);
      session.start();
      Queue q = (Queue) server.getPostOffice().getBinding(queueA).getBindable();
      ClientMessage[] messages = new ClientMessage[numMessages];
      for (int i = 0; i < numMessages; i++) {
         messages[i] = consumer.receive(5000);
         assertNotNull(messages[i]);
      }
      for (int i = 0; i < numMessages; i++) {
         messages[i].acknowledge();
         assertEquals(numMessages - i - 1, q.getDeliveringCount());
      }
      sendSession.close();
      session.close();
   }
}
