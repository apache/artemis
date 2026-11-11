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
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.naming.InitialContext;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class ConsumerClosedTest extends JMSTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


   public static final int NUMBER_OF_MESSAGES = 10;


   InitialContext ic;


   @Test
   public void testMessagesSentDuringClose() throws Exception {
      Connection c = null;

      try {
         c = createConnection();
         c.start();

         Session s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = s.createProducer(queue1);

         for (int i = 0; i < ConsumerClosedTest.NUMBER_OF_MESSAGES; i++) {
            p.send(s.createTextMessage("message" + i));
         }

         logger.debug("all messages sent");

         MessageConsumer cons = s.createConsumer(queue1);
         cons.close();

         logger.debug("consumer closed");

         // make sure that all messages are in queue

         assertRemainingMessages(ConsumerClosedTest.NUMBER_OF_MESSAGES);
      } finally {
         if (c != null) {
            c.close();
         }

         removeAllMessages(queue1.getQueueName(), true);
      }
   }


}
