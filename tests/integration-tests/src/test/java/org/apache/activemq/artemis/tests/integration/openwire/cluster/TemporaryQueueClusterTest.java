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
package org.apache.activemq.artemis.tests.integration.openwire.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

public class TemporaryQueueClusterTest extends OpenWireJMSClusteredTestBase {

   @Override
   public boolean isFileStorage() {
      return false;
   }

   public static final String QUEUE_NAME = "target";

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      createAddressInfo(0, QUEUE_NAME, RoutingType.ANYCAST, -1, false);
      createAddressInfo(1, QUEUE_NAME, RoutingType.ANYCAST, -1, false);
      createQueue(0, QUEUE_NAME, QUEUE_NAME, null, true, null, null, RoutingType.ANYCAST);
      createQueue(1, QUEUE_NAME, QUEUE_NAME, null, true, null, null, RoutingType.ANYCAST);

      waitForBindings(0, QUEUE_NAME, 1, 0, true);
      waitForBindings(0, QUEUE_NAME, 1, 0, false);
      waitForBindings(1, QUEUE_NAME, 1, 0, true);
      waitForBindings(1, QUEUE_NAME, 1, 0, false);
   }

   @Test
   public void testClusteredQueue() throws Exception {

      Connection conn1 = openWireCf1.createConnection();
      Connection conn2 = openWireCf2.createConnection();

      conn1.start();
      conn2.start();

      try {
         Session session1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue targetQueue1 = session1.createQueue(QUEUE_NAME);

         Session session2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue targetQueue2 = session2.createQueue(QUEUE_NAME);

         this.waitForBindings(servers[0], QUEUE_NAME, true, 1, 0, 2000);
         this.waitForBindings(servers[1], QUEUE_NAME, true, 1, 0, 2000);
         this.waitForBindings(servers[1], QUEUE_NAME, false, 1, 0, 2000);
         this.waitForBindings(servers[0], QUEUE_NAME, false, 1, 0, 2000);

         MessageProducer prod1 = session1.createProducer(targetQueue1);
         MessageConsumer cons2 = session2.createConsumer(targetQueue2);

         this.waitForBindings(servers[0], QUEUE_NAME, false, 1, 1, 2000);
         this.waitForBindings(servers[1], QUEUE_NAME, true, 1, 1, 2000);

         TextMessage msg = session1.createTextMessage("hello");

         prod1.send(msg);

         Wait.assertTrue(() -> getServer(1).locateQueue(SimpleString.of(QUEUE_NAME)).getMessageCount() == 1, 5000, 100);

         TextMessage msgReceived = (TextMessage) cons2.receive(5000);

         assertNotNull(msgReceived);
         assertEquals(msgReceived.getText(), msg.getText());

      } finally {
         conn1.close();
         conn2.close();
      }
   }

}
