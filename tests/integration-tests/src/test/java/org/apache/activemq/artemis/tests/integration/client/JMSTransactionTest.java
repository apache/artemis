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

import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.jms.CompletionListener;
import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class JMSTransactionTest extends JMSTestBase {

   @Test
   @Timeout(60)
   public void testAsyncProduceMessageAndCommit() throws Throwable {
      final String queueName = "TEST";
      final int messages = 10;

      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory();
      cf.setConfirmationWindowSize(1000000);
      cf.setBlockOnDurableSend(true);
      cf.setBlockOnNonDurableSend(true);

      CountDownLatch commitLatch = new CountDownLatch(1);
      AtomicInteger sentMessages = new AtomicInteger(0);

      server.getRemotingService().addIncomingInterceptor((Interceptor) (packet, connection1) -> {
         if (packet.getType() == PacketImpl.SESS_COMMIT) {
            commitLatch.countDown();
         }
         return true;
      });

      try (Connection connection = cf.createConnection();
           Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {

         javax.jms.Queue queue = session.createQueue(queueName);
         MessageProducer p = session.createProducer(queue);

         for (int i = 0; i < messages; i++) {
            TextMessage message = session.createTextMessage();
            message.setText("Message:" + i);
            p.send(message, new CompletionListener() {
               @Override
               public void onCompletion(Message message) {
                  try {
                     commitLatch.await();
                     sentMessages.incrementAndGet();
                  } catch (Exception e) {
                     e.printStackTrace();
                  }
               }

               @Override
               public void onException(Message message, Exception exception) {

               }
            });
         }

         session.commit();
         Wait.assertEquals(messages, sentMessages::get);

         org.apache.activemq.artemis.core.server.Queue queueView = server.locateQueue(SimpleString.of(queueName));
         Wait.assertEquals(messages, queueView::getMessageCount);
      }
   }
}
