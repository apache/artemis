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

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TransactionalSendTest extends ActiveMQTestBase {

   public final SimpleString addressA = SimpleString.of("addressA");

   public final SimpleString queueA = SimpleString.of("queueA");

   public final SimpleString queueB = SimpleString.of("queueB");

   public final SimpleString queueC = SimpleString.of("queueC");

   private ServerLocator locator;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = createInVMNonHALocator();
   }

   @Test
   public void testSendWithCommit() throws Exception {
      ActiveMQServer server = createServer(false);
      server.start();
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession(false, false, false);
      session.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      ClientProducer cp = session.createProducer(addressA);
      int numMessages = 100;
      for (int i = 0; i < numMessages; i++) {
         cp.send(session.createMessage(false));
      }
      Queue q = (Queue) server.getPostOffice().getBinding(queueA).getBindable();
      assertEquals(0, getMessageCount(q));
      session.commit();
      Wait.assertEquals(numMessages, () -> getMessageCount(q));
      // now send some more
      for (int i = 0; i < numMessages; i++) {
         cp.send(session.createMessage(false));
      }
      Wait.assertEquals(numMessages, q::getMessageCount);
      session.commit();
      Wait.assertEquals(numMessages * 2, q::getMessageCount);
      session.close();
   }

   @Test
   public void testSendWithRollback() throws Exception {
      ActiveMQServer server = createServer(false);
      server.start();
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession(false, false, false);
      session.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      ClientProducer cp = session.createProducer(addressA);
      int numMessages = 100;
      for (int i = 0; i < numMessages; i++) {
         cp.send(session.createMessage(false));
      }
      Queue q = (Queue) server.getPostOffice().getBinding(queueA).getBindable();
      assertEquals(0, getMessageCount(q));
      session.rollback();
      assertEquals(0, getMessageCount(q));
      // now send some more
      for (int i = 0; i < numMessages; i++) {
         cp.send(session.createMessage(false));
      }
      Wait.assertEquals(0, q::getMessageCount);
      session.commit();
      Wait.assertEquals(numMessages, q::getMessageCount);
      session.close();
   }

}
