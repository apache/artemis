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
package org.apache.activemq.artemis.tests.integration.jms.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.tests.util.JMSClusteredTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AutoCreateQueueClusterTest extends JMSClusteredTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      //todo fix if needed
      super.setUp();
      jmsServer1.getActiveMQServer().setIdentity("Server 1");
      jmsServer2.getActiveMQServer().setIdentity("Server 2");
   }

   @Override
   protected boolean enablePersistence() {
      return true;
   }

   @Test
   public void testAutoCreate() throws Exception {
      server1.getAddressSettingsRepository().getMatch("#").setAutoCreateQueues(true).setAutoCreateAddresses(true).setRedistributionDelay(0);
      server2.getAddressSettingsRepository().getMatch("#").setAutoCreateQueues(true).setAutoCreateAddresses(true).setRedistributionDelay(0);
      Connection conn1 = cf1.createConnection();
      Connection conn2 = cf2.createConnection();
      conn1.start();
      conn2.start();

      try {
         Session session1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session session2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod1 = session1.createProducer(ActiveMQJMSClient.createQueue("myQueue"));

         prod1.setDeliveryMode(DeliveryMode.PERSISTENT);

         prod1.send(session1.createTextMessage("m1"));

         MessageConsumer cons2 = session2.createConsumer(ActiveMQJMSClient.createQueue("myQueue"));

         TextMessage received = (TextMessage) cons2.receive(5000);

         assertNotNull(received);

         assertEquals("m1", received.getText());

         cons2.close();
      } finally {
         conn1.close();
         conn2.close();
      }
   }
}
