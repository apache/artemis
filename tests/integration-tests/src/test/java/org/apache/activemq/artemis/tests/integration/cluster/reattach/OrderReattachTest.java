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
package org.apache.activemq.artemis.tests.integration.cluster.reattach;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.protocol.core.impl.RemotingConnectionImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.Assert;
import org.junit.Test;

public class OrderReattachTest extends ActiveMQTestBase {

   final SimpleString ADDRESS = new SimpleString("address");

   private ActiveMQServer server;



   @Test
   public void testOrderOnSendInVM() throws Throwable {
      doTestOrderOnSend(false);
   }

   public void doTestOrderOnSend(final boolean isNetty) throws Throwable {
      server = createServer(false, isNetty);

      server.start();
      ServerLocator locator = createFactory(isNetty).setReconnectAttempts(15).setConfirmationWindowSize(1024 * 1024).setBlockOnNonDurableSend(false).setBlockOnAcknowledge(false);

      ClientSessionFactory sf = createSessionFactory(locator);

      final ClientSession session = sf.createSession(false, true, true);

      final LinkedBlockingDeque<Boolean> failureQueue = new LinkedBlockingDeque<>();

      final CountDownLatch ready = new CountDownLatch(1);

      // this test will use a queue. Whenever the test wants a failure.. it can just send TRUE to failureQueue
      // This Thread will be reading the queue
      Thread failer = new Thread() {
         @Override
         public void run() {
            ready.countDown();
            while (true) {
               try {
                  Boolean poll = false;
                  try {
                     poll = failureQueue.poll(60, TimeUnit.SECONDS);
                  } catch (InterruptedException e) {
                     e.printStackTrace();
                     break;
                  }

                  Thread.sleep(1);

                  final RemotingConnectionImpl conn = (RemotingConnectionImpl) ((ClientSessionInternal) session).getConnection();

                  // True means... fail session
                  if (poll) {
                     conn.fail(new ActiveMQNotConnectedException("poop"));
                  } else {
                     // false means... finish thread
                     break;
                  }
               } catch (Exception e) {
                  e.printStackTrace();
               }
            }
         }
      };

      failer.start();

      ready.await();

      try {
         doSend2(1, sf, failureQueue);
      } finally {
         try {
            session.close();
         } catch (Exception e) {
            e.printStackTrace();
         }

         try {
            locator.close();
         } catch (Exception e) {
            //
         }
         try {
            sf.close();
         } catch (Exception e) {
            e.printStackTrace();
         }

         failureQueue.put(false);

         failer.join();
      }

   }

   public void doSend2(final int order,
                       final ClientSessionFactory sf,
                       final LinkedBlockingDeque<Boolean> failureQueue) throws Exception {
      ClientSession s = sf.createSession(false, false, false);

      final int numMessages = 500;

      final int numSessions = 10;

      Set<ClientConsumer> consumers = new HashSet<>();
      Set<ClientSession> sessions = new HashSet<>();

      for (int i = 0; i < numSessions; i++) {
         SimpleString subName = new SimpleString("sub" + i);

         // failureQueue.push(true);

         ClientSession sessConsume = sf.createSession(false, true, true);

         sessConsume.createQueue(new QueueConfiguration(subName).setAddress(ADDRESS).setDurable(false));

         ClientConsumer consumer = sessConsume.createConsumer(subName);

         consumers.add(consumer);

         sessions.add(sessConsume);
      }

      ClientSession sessSend = sf.createSession(false, true, true);

      ClientProducer producer = sessSend.createProducer(ADDRESS);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = sessSend.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);

         if (i % 10 == 0) {
            // failureQueue.push(true);
         }
         message.putIntProperty(new SimpleString("count"), i);
         producer.send(message);
      }

      for (ClientSession session : sessions) {
         session.start();
      }

      class MyHandler implements MessageHandler {

         final CountDownLatch latch = new CountDownLatch(1);

         int count;

         Exception failure;

         @Override
         public void onMessage(final ClientMessage message) {
            if (count >= numMessages) {
               failure = new Exception("too many messages");
               latch.countDown();
            }

            if (message.getIntProperty("count") != count) {
               failure = new Exception("counter " + count + " was not as expected (" + message.getIntProperty("count") + ")");
               instanceLog.warn("Failure on receiving message ", failure);
               failure.printStackTrace();
               latch.countDown();
            }

            count++;

            if (count % 100 == 0) {
               failureQueue.push(true);
            }

            if (count == numMessages) {
               latch.countDown();
            }
         }
      }

      Set<MyHandler> handlers = new HashSet<>();

      for (ClientConsumer consumer : consumers) {
         MyHandler handler = new MyHandler();

         consumer.setMessageHandler(handler);

         handlers.add(handler);
      }

      for (MyHandler handler : handlers) {
         boolean ok = handler.latch.await(60000, TimeUnit.MILLISECONDS);

         Assert.assertTrue(ok);

         if (handler.failure != null) {
            throw handler.failure;
         }
      }

      // failureQueue.push(true);

      sessSend.close();

      for (ClientSession session : sessions) {
         // failureQueue.push(true);
         session.close();
      }

      for (int i = 0; i < numSessions; i++) {

         failureQueue.push(true);

         SimpleString subName = new SimpleString("sub" + i);

         s.deleteQueue(subName);
      }

      s.close();
   }
}
