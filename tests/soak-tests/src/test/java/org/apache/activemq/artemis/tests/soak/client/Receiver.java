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
package org.apache.activemq.artemis.tests.soak.client;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class Receiver extends ClientAbstract {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // We should leave some messages on paging. We don't want to consume all for this test
   private final Semaphore minConsume = new Semaphore(0);

   private final ReusableLatch latchMax = new ReusableLatch(0);

   private static final int MAX_DIFF = 10000;

   // The difference between producer and consuming
   private final AtomicInteger currentDiff = new AtomicInteger(0);

   private final String queue;

   protected long msgs = 0;

   protected int pendingMsgs = 0;

   protected int pendingSemaphores = 0;

   protected ClientConsumer cons;

   public Receiver(ClientSessionFactory sf, String queue) {
      super(sf);
      this.queue = queue;
   }


   @Override
   public void run() {
      super.run();

      while (running) {
         try {
            beginTX();

            for (int i = 0; i < 1000; i++) {
               ClientMessage msg = cons.receive(5000);
               if (msg == null) {
                  break;
               }

               msg.acknowledge();

               if (msg.getLongProperty("count") != msgs + pendingMsgs) {
                  errors++;
                  logger.info("count should be {} when it was {} on {}", (msgs + pendingMsgs), msg.getLongProperty("count"), queue);
               }

               pendingMsgs++;
               if (!minConsume.tryAcquire(1, 5, TimeUnit.SECONDS)) {
                  break;
               }

            }

            endTX();
         } catch (Exception e) {
            connect();
         }

      }
   }

   @Override
   protected void connectClients() throws Exception {

      cons = session.createConsumer(queue);

      session.start();
   }

   @Override
   protected void onCommit() {
      msgs += pendingMsgs;
      this.currentDiff.addAndGet(-pendingMsgs);
      latchMax.countDown(pendingMsgs);
      pendingMsgs = 0;
   }

   @Override
   protected void onRollback() {
      minConsume.release(pendingMsgs);
      pendingMsgs = 0;
   }

   @Override
   public String toString() {
      return "Receiver::" + this.queue + ", msgs=" + msgs + ", pending=" + pendingMsgs;
   }

   public void messageProduced(int producedMessages) {
      minConsume.release(producedMessages);
      currentDiff.addAndGet(producedMessages);
      if (currentDiff.get() > MAX_DIFF) {
         latchMax.setCount(currentDiff.get() - MAX_DIFF);
         try {
            latchMax.await(5, TimeUnit.SECONDS);
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
      }
   }

}
