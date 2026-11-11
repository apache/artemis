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
package org.apache.activemq.artemis.tests.integration.management;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class ManagementTestBase extends ActiveMQTestBase {


   protected MBeanServer mbeanServer = createMBeanServer();



   protected static void consumeMessages(final int expected,
                                         final ClientSession session,
                                         final SimpleString queue) throws Exception {
      ClientConsumer consumer = null;
      try {
         consumer = session.createConsumer(queue);
         ClientMessage m = null;
         for (int i = 0; i < expected; i++) {
            m = consumer.receive(500);
            assertNotNull(m, "expected to received " + expected + " messages, got only " + i);
            m.acknowledge();
         }
         session.commit();
         m = consumer.receiveImmediate();
         assertNull(m, "received one more message than expected (" + expected + ")");
      } finally {
         if (consumer != null) {
            consumer.close();
         }
      }
   }






   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      createMBeanServer();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      super.tearDown();
   }

   protected void checkNoResource(final ObjectName on) {
      Wait.assertFalse("unexpected resource for " + on, () -> mbeanServer.isRegistered(on));
   }

   protected void checkResource(final ObjectName on) {
      Wait.assertTrue("no resource for " + on, () -> mbeanServer.isRegistered(on));
   }

   protected QueueControl createManagementControl(final String address, final String queue) throws Exception {
      return createManagementControl(SimpleString.of(address), SimpleString.of(queue));
   }

   protected QueueControl createManagementControl(final SimpleString address,
                                                  final SimpleString queue) throws Exception {
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, queue, mbeanServer);

      return queueControl;
   }

   protected QueueControl createManagementControl(final SimpleString address,
                                                  final SimpleString queue,
                                                  final RoutingType routingType) throws Exception {
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, queue, routingType, mbeanServer);

      return queueControl;
   }

   protected long getMessageCount(QueueControl control) throws Exception {
      control.flushExecutor();
      return control.getMessageCount();
   }

   protected int getGroupCount(QueueControl control) throws Exception {
      control.flushExecutor();
      return control.getGroupCount();
   }


   protected long getDurableMessageCount(QueueControl control) throws Exception {
      control.flushExecutor();
      return control.getDurableMessageCount();
   }

   protected long getMessageSize(QueueControl control) throws Exception {
      control.flushExecutor();
      return control.getPersistentSize();
   }

   protected long getDurableMessageSize(QueueControl control) throws Exception {
      control.flushExecutor();
      return control.getDurablePersistentSize();
   }

   protected long getMessagesAdded(QueueControl control) throws Exception {
      control.flushExecutor();
      return control.getMessagesAdded();
   }



}
