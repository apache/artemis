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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class TransactionDurabilityTest extends ActiveMQTestBase {

   /*
    * This tests the following situation:
    *
    * (With the old implementation)
    * Currently when a new persistent message is routed to persistent queues, the message is first stored, then the message is routed.
    * Let's say it has been routed to two different queues A, B.
    * Ref R1 gets consumed and acknowledged by transacted session S1, this decrements the ref count and causes an acknowledge record to be written to storage,
    * transactionally, but it's not committed yet.
    * Ref R2 then gets consumed and acknowledged by non transacted session S2, this causes a delete record to be written to storage.
    * R1 then rolls back, and the server is restarted - unfortunately since the delete record was written R1 is not ready to be consumed again.
    *
    * It's therefore crucial the messages aren't deleted from storage until AFTER any ack records are committed to storage.
    */
   @Test
   public void testRolledBackAcknowledgeWithSameMessageAckedByOtherSession() throws Exception {
      final SimpleString testAddress = SimpleString.of("testAddress");

      final SimpleString queue1 = SimpleString.of("queue1");

      final SimpleString queue2 = SimpleString.of("queue2");

      ActiveMQServer server = createServer(true, createDefaultInVMConfig());

      server.start();

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session1 = addClientSession(sf.createSession(false, true, true));

      ClientSession session2 = addClientSession(sf.createSession(false, false, false));

      session1.createQueue(QueueConfiguration.of(queue1).setAddress(testAddress));

      session1.createQueue(QueueConfiguration.of(queue2).setAddress(testAddress));

      ClientProducer producer = session1.createProducer(testAddress);

      ClientMessage message = session1.createMessage(true);

      producer.send(message);

      session1.start();

      session2.start();

      ClientConsumer consumer1 = session1.createConsumer(queue1);

      ClientConsumer consumer2 = session2.createConsumer(queue2);

      ClientMessage m1 = consumer1.receive(1000);

      assertNotNull(m1);

      ClientMessage m2 = consumer2.receive(1000);

      assertNotNull(m2);

      m2.acknowledge();

      // Don't commit session 2

      m1.acknowledge();

      session2.rollback();

      session1.close();

      session2.close();

      server.stop();

      server.start();

      sf = createSessionFactory(locator);

      session1 = addClientSession(sf.createSession(false, true, true));

      session2 = addClientSession(sf.createSession(false, true, true));

      session1.start();

      session2.start();

      consumer1 = session1.createConsumer(queue1);

      consumer2 = session2.createConsumer(queue2);

      m1 = consumer1.receiveImmediate();

      assertNull(m1);

      m2 = consumer2.receive(1000);

      assertNotNull(m2);

      m2.acknowledge();

      session1.close();

      session2.close();

      server.stop();

      server.start();

      sf = createSessionFactory(locator);

      session1 = addClientSession(sf.createSession(false, true, true));

      session2 = addClientSession(sf.createSession(false, true, true));

      session1.start();

      session2.start();

      consumer1 = session1.createConsumer(queue1);

      consumer2 = session2.createConsumer(queue2);

      m1 = consumer1.receiveImmediate();

      assertNull(m1);

      m2 = consumer2.receiveImmediate();

      assertNull(m2);

      session1.close();

      session2.close();

      locator.close();

      server.stop();
   }
}
