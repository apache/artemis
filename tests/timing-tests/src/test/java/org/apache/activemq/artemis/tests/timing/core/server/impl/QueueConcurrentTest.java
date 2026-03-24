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
package org.apache.activemq.artemis.tests.timing.core.server.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.HandleStatus;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.QueueConfig;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.tests.unit.core.server.impl.fakes.FakeConsumer;
import org.apache.activemq.artemis.tests.unit.core.server.impl.fakes.FakeQueueFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * A concurrent QueueTest
 *
 * All the concurrent queue tests go in here
 */
public class QueueConcurrentTest extends ActiveMQTestBase {

   private static final Logger log = Logger.getLogger(QueueConcurrentTest.class);

   private FakeQueueFactory queueFactory = new FakeQueueFactory();

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();
      queueFactory = new FakeQueueFactory();
   }

   @Override
   @After
   public void tearDown() throws Exception {
      queueFactory.stop();
      super.tearDown();
   }

   /*
    * Concurrent set consumer not busy, busy then, call deliver while messages are being added and consumed
    */
   @Test
   public void testConcurrentAddsDeliver() throws Exception {

      QueueImpl queue = (QueueImpl) queueFactory.createQueueWith(QueueConfig.builderWith(1, new SimpleString("address1"), new SimpleString("queue1")).durable(false).temporary(false).autoCreated(false).build());

      FakeConsumer consumer = new FakeConsumer();

      queue.addConsumer(consumer);

      final long testTime = 5000;

      Sender sender = new Sender(queue, testTime);

      Toggler toggler = new Toggler(queue, consumer, testTime);

      sender.start();

      toggler.start();

      sender.join();

      toggler.join();

      consumer.setStatusImmediate(HandleStatus.HANDLED);

      queue.deliverNow();

      if (sender.getException() != null) {
         throw sender.getException();
      }

      if (toggler.getException() != null) {
         throw toggler.getException();
      }

      assertRefListsIdenticalRefs(sender.getReferences(), consumer.getReferences());

      log.info("num refs: " + sender.getReferences().size());

      log.info("num toggles: " + toggler.getNumToggles());

   }

   class Sender extends Thread {

      private volatile Exception e;

      private final Queue queue;

      private final long testTime;

      private int i;

      public Exception getException() {
         return e;
      }

      private final List<MessageReference> refs = new ArrayList<>();

      public List<MessageReference> getReferences() {
         return refs;
      }

      Sender(final Queue queue, final long testTime) {
         this.testTime = testTime;

         this.queue = queue;
      }

      @Override
      public void run() {
         long start = System.currentTimeMillis();

         while (System.currentTimeMillis() - start < testTime) {
            Message message = generateMessage(i);

            MessageReference ref = MessageReference.Factory.createReference(message, queue);

            queue.addTail(ref, false);

            refs.add(ref);

            i++;
         }
      }
   }

   class Toggler extends Thread {

      private volatile Exception e;

      private final QueueImpl queue;

      private final FakeConsumer consumer;

      private final long testTime;

      private boolean toggle;

      private int numToggles;

      public int getNumToggles() {
         return numToggles;
      }

      public Exception getException() {
         return e;
      }

      Toggler(final QueueImpl queue, final FakeConsumer consumer, final long testTime) {
         this.testTime = testTime;

         this.queue = queue;

         this.consumer = consumer;
      }

      @Override
      public void run() {
         long start = System.currentTimeMillis();

         while (System.currentTimeMillis() - start < testTime) {
            if (toggle) {
               consumer.setStatusImmediate(HandleStatus.BUSY);
            } else {
               consumer.setStatusImmediate(HandleStatus.HANDLED);

               queue.deliverNow();
            }
            toggle = !toggle;

            numToggles++;
         }
      }
   }

}
