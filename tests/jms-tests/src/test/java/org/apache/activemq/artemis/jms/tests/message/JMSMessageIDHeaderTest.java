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
package org.apache.activemq.artemis.jms.tests.message;

import javax.jms.Message;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Test;

public class JMSMessageIDHeaderTest extends MessageHeaderTestBase {



   @Test
   public void testJMSMessageIDPrefix() throws Exception {
      Message m = queueProducerSession.createMessage();
      queueProducer.send(m);
      String messageID = queueConsumer.receive().getJMSMessageID();

      // JMS1.1 specs 3.4.3
      ProxyAssertSupport.assertTrue(messageID.startsWith("ID:"));
   }

   @Test
   public void testJMSMessageIDChangedAfterSendingMessage() throws Exception {
      try {
         Message m = queueProducerSession.createMessage();
         m.setJMSMessageID("ID:something");

         queueProducer.send(m);

         ProxyAssertSupport.assertFalse("ID:something".equals(m.getJMSMessageID()));
      } finally {
         removeAllMessages(queue1.getQueueName(), true);
      }
   }

   @Test
   public void testJMSMessageID() throws Exception {
      try {
         Message m = queueProducerSession.createMessage();
         ProxyAssertSupport.assertNull(m.getJMSMessageID());

         queueProducer.send(m);

         ProxyAssertSupport.assertNotNull(m.getJMSMessageID());
         ProxyAssertSupport.assertTrue(m.getJMSMessageID().startsWith("ID:"));
      } finally {
         removeAllMessages(queue1.getQueueName(), true);
      }
   }


}
