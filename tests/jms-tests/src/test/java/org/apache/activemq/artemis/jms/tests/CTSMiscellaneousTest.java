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
package org.apache.activemq.artemis.jms.tests;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Safeguards for previously detected TCK failures.
 */
public class CTSMiscellaneousTest extends JMSTest {

   protected static ActiveMQConnectionFactory cf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      try {
         super.setUp();
         CTSMiscellaneousTest.cf = ActiveMQJMSClient.createConnectionFactory("tcp://127.0.0.1:61616?blockOnAcknowledge=true&blockOnDurableSend=true&blockOnNonDurableSend=true", "StrictTCKConnectionFactory");
      } catch (Exception e) {
         e.printStackTrace();
      }

   }


   /*
    * By default we send non persistent messages asynchronously for performance reasons
    * when running with strictTCK we send them synchronously
    */
   @Test
   public void testNonPersistentMessagesSentSynchronously() throws Exception {
      Connection c = null;

      try {
         c = CTSMiscellaneousTest.cf.createConnection();
         Session s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer p = s.createProducer(queue1);

         p.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

         final int numMessages = 100;

         assertRemainingMessages(0);

         for (int i = 0; i < numMessages; i++) {
            p.send(s.createMessage());
         }

         assertRemainingMessages(numMessages);
      } finally {
         if (c != null) {
            c.close();
         }

         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      super.tearDown();
   }


}
