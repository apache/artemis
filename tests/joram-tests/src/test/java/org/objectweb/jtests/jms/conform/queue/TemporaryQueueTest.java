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
package org.objectweb.jtests.jms.conform.queue;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.QueueReceiver;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.jtests.jms.framework.PTPTestCase;
import org.objectweb.jtests.jms.framework.TestConfig;

public class TemporaryQueueTest extends PTPTestCase {

   private TemporaryQueue tempQueue;

   private QueueReceiver tempReceiver;

   @Test
   public void testTemporaryQueue() {
      try {
         // we stop both sender and receiver connections
         senderConnection.stop();
         receiverConnection.stop();
         // we create a temporary queue to receive messages
         tempQueue = receiverSession.createTemporaryQueue();
         // we recreate the sender because it has been
         // already created with a Destination as parameter
         sender = senderSession.createSender(null);
         // we create a receiver on the temporary queue
         tempReceiver = receiverSession.createReceiver(tempQueue);
         receiverConnection.start();
         senderConnection.start();

         TextMessage message = senderSession.createTextMessage();
         message.setText("testTemporaryQueue");
         sender.send(tempQueue, message);

         Message m = tempReceiver.receive(TestConfig.TIMEOUT);
         Assert.assertTrue(m instanceof TextMessage);
         TextMessage msg = (TextMessage) m;
         Assert.assertEquals("testTemporaryQueue", msg.getText());
      } catch (JMSException e) {
         fail(e);
      }
   }
}
