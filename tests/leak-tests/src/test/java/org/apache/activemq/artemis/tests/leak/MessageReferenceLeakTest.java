/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.tests.leak;

import io.github.checkleak.core.CheckLeak;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class MessageReferenceLeakTest extends AbstractLeakTest {

   ActiveMQServer server;
   ClientSession session;

   public void startServer() throws Exception {
      server = createServer(false, false);
      server.start();
   }

   @BeforeAll
   public static void beforeClass() throws Exception {
      assumeTrue(CheckLeak.isLoaded());
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      startServer();
      ServerLocator locator = addServerLocator(createInVMNonHALocator().setBlockOnNonDurableSend(true).setConsumerWindowSize(0));
      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));
      session = addClientSession(sf.createSession(false, true, false));
      session.start();
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      super.tearDown();
      server = null;
   }

   @Test
   public void testScheduledMessageReferenceLeak() throws Exception {

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
      Queue serverQueue = server.locateQueue(queue);

      try (ClientProducer producer = session.createProducer(address)) {
         ClientMessage message = createTextMessage(session, "Hello world")
               .putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, System.currentTimeMillis() + 5000);
         producer.send(message);
      }

      assertNull(serverQueue.peekFirstMessage());

      MessageReference ref = serverQueue.peekFirstScheduledMessage();
      assertNotNull(ref);

      // Store this for later to check for leaks
      String refClassName = ref.getClass().getCanonicalName();
      long messageId = ref.getMessageID();
      // Get rid of the message reference.
      ref = null;
      assertNull(ref);

      // Override Message.HDR_SCHEDULED_DELIVERY_TIME
      serverQueue.deliverScheduledMessage(messageId);
      serverQueue.flushExecutor();

      try (ClientConsumer consumer = session.createConsumer(queue)) {
         ClientMessage message = consumer.receive(1000);
         assertNotNull(message);
         message.individualAcknowledge();
         session.commit(true);
         assertEquals(messageId, message.getMessageID());
      }

      // Now that I've consumed the message there should be no reference left.
      // I cannot just assert that there's no org.apache.activemq.artemis.core.server.MessageReference because there's
      // a static instance of it: org.apache.activemq.artemis.core.paging.cursor.impl.PageSubscriptionImpl.RETRY_MARK
      MemoryAssertions.assertMemory(new CheckLeak(), 0, refClassName);

      session.deleteQueue(queue);
   }
}
