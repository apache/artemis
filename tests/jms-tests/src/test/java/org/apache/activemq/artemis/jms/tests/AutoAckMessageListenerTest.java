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
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class AutoAckMessageListenerTest extends JMSTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


   @Test
   public void testAutoAckMsgListenerQueue() throws Exception {
      Connection conn = null;

      try {
         CountDownLatch latch = new CountDownLatch(1);

         conn = createConnection();
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(queue1);
         MessageConsumer consumer = session.createConsumer(queue1);
         AutoAckMsgListener listener = new AutoAckMsgListener(latch, session);
         consumer.setMessageListener(listener);

         // create and send messages
         logger.debug("Send and receive two message");
         Message messageSent = session.createMessage();
         messageSent.setBooleanProperty("last", false);
         producer.send(messageSent);
         messageSent.setBooleanProperty("last", true);
         producer.send(messageSent);

         conn.start();

         // wait until message is received
         logger.debug("waiting until message has been received by message listener...");
         latch.await(10, TimeUnit.SECONDS);

         // check message listener status
         if (listener.getPassed() == false) {
            throw new Exception("failed");
         }
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   private static class AutoAckMsgListener implements MessageListener {

      private boolean passed;

      private final Session session;

      private final CountDownLatch monitor;

      private AutoAckMsgListener(CountDownLatch latch, Session session) {
         this.monitor = latch;
         this.session = session;
      }

      // get state of test
      public boolean getPassed() {
         return passed;
      }

      // will receive two messages
      @Override
      public void onMessage(Message message) {
         try {
            if (message.getBooleanProperty("last") == false) {
               logger.debug("Received first message.");
               if (message.getJMSRedelivered() == true) {
                  // should not re-receive this one
                  logger.debug("Error: received first message twice");
                  passed = false;
               }
            } else {
               if (message.getJMSRedelivered() == false) {
                  // received second message for first time
                  logger.debug("Received second message. Calling recover()");
                  session.recover();
               } else {
                  // should be redelivered after recover
                  logger.debug("Received second message again as expected");
                  passed = true;
                  monitor.countDown();
               }
            }
         } catch (JMSException e) {
            logger.warn("Exception caught in message listener:", e);
            passed = false;
            monitor.countDown();
         }

      }
   }
}
