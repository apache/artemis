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
package org.apache.activemq.artemis.tests.integration.openwire.amq;

import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.command.ActiveMQQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class QueueConsumerPriorityTest extends BasicOpenWireTest {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      this.makeSureCoreQueueExist("QUEUE.A");
   }
   @Test
   public void testQueueConsumerPriority() throws Exception {
      connection.start();
      Session consumerLowPriority = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Session consumerHighPriority = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      assertNotNull(consumerHighPriority);
      Session senderSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      String queueName = "QUEUE.A";
      ActiveMQQueue low = new ActiveMQQueue(queueName + "?consumer.priority=1");
      MessageConsumer lowConsumer = consumerLowPriority.createConsumer(low);

      ActiveMQQueue high = new ActiveMQQueue(queueName + "?consumer.priority=2");
      MessageConsumer highConsumer = consumerLowPriority.createConsumer(high);

      ActiveMQQueue senderQueue = new ActiveMQQueue(queueName);

      MessageProducer producer = senderSession.createProducer(senderQueue);

      Message msg = senderSession.createTextMessage("test");
      for (int i = 0; i < 1000; i++) {
         producer.send(msg);
         assertNotNull(highConsumer.receive(1000), "null on iteration: " + i);
      }
      assertNull(lowConsumer.receive(250));
   }
}
