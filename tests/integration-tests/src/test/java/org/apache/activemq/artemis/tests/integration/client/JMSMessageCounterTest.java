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

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JMSMessageCounterTest extends JMSTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected boolean usePersistence() {
      return true;
   }

   @Test
   public void testMessageCounter() throws Exception {
      Connection conn = cf.createConnection();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      Queue queue = createQueue(true, "Test");

      MessageProducer producer = sess.createProducer(queue);
      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++) {
         TextMessage mess = sess.createTextMessage("msg" + i);
         producer.send(mess);
      }

      conn.close();

      QueueControl control = server.getManagementService().getQueueControl(queue.getQueueName());
      assertNotNull(control);

      logger.debug(control.listMessageCounterAsHTML());

      jmsServer.stop();

      restartServer();

      control = server.getManagementService().getQueueControl(queue.getQueueName());
      assertNotNull(control);

      logger.debug(control.listMessageCounterAsHTML());
   }

}
