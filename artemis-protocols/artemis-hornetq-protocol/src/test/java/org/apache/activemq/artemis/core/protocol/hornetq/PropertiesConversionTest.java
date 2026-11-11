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

package org.apache.activemq.artemis.core.protocol.hornetq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.MessagePacket;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionReceiveMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionReceiveMessage_1X;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

public class PropertiesConversionTest {

   class FakeMessagePacket extends MessagePacket {

      FakeMessagePacket(ICoreMessage message) {
         super(PacketImpl.SESS_SEND, message);
      }

      @Override
      public int expectedEncodeSize() {
         return 0;
      }

      @Override
      public void release() {

      }
   }

   @Test
   public void testParallelHornetQConversions() throws Throwable {
      CoreMessage coreMessage = new CoreMessage(1, 1024);
      for (int i = 0; i < 10; i++) {
         coreMessage.putBooleanProperty(SimpleString.of("key1"), true);
      }
      coreMessage.putStringProperty(SimpleString.of("_HQ_ORIG_ADDRESS"), SimpleString.of("hqOne"));
      coreMessage.putStringProperty(SimpleString.of("_AMQ_ORIG_QUEUE"), SimpleString.of("amqOne"));
      coreMessage.putStringProperty(SimpleString.of("_AMQ_ORIG_MESSAGE_ID"), SimpleString.of("asdfkhaksdjfhaskfdjhas"));

      int threads = 100;
      int conversions = 100;
      Thread[] t = new Thread[threads];
      CyclicBarrier barrier = new CyclicBarrier(threads);
      HQPropertiesConversionInterceptor hq = new HQPropertiesConversionInterceptor(true);
      HQPropertiesConversionInterceptor amq = new HQPropertiesConversionInterceptor(true);
      AtomicInteger errors = new AtomicInteger(0);
      AtomicInteger counts = new AtomicInteger(0);

      for (int i = 0; i < threads; i++) {
         t[i] = new Thread(() -> {
            try {
               for (int i1 = 0; i1 < conversions; i1++) {
                  counts.incrementAndGet();
                  FakeMessagePacket packetSend = new FakeMessagePacket(coreMessage);
                  if (i1 == 0) {
                     barrier.await();
                  }
                  hq.intercept(packetSend, null);
                  FakeMessagePacket packetRec = new FakeMessagePacket(coreMessage);
                  amq.intercept(packetRec, null);

                  // heads or tails here, I need part of the messages with a big header, part of the messages with a small header
                  boolean heads = RandomUtil.randomBoolean();

                  // this is playing with a scenario where the horentq interceptor will change the size of the message
                  if (heads) {
                     packetRec.getMessage().putStringProperty("propChanges", "looooooooooooooooooooong property text");
                  } else {
                     packetRec.getMessage().putStringProperty("propChanges", "short one");
                  }

                  SessionReceiveMessage_1X receiveMessage_1X = new SessionReceiveMessage_1X(coreMessage);
                  ActiveMQBuffer buffer = receiveMessage_1X.encode(null);
                  buffer.release();

                  if (i1 > conversions / 2) {
                     // I only validate half of the messages
                     // to give it a chance of Races and Exceptions
                     // that could happen from reusing the same message on these conversions
                     assertNotSame(packetRec.getMessage(), coreMessage);
                     assertNotSame(packetSend.getMessage(), coreMessage);
                  }
               }
            } catch (Throwable e) {
               errors.incrementAndGet();
               e.printStackTrace();
            }
         });
         t[i].start();
      }

      for (Thread thread : t) {
         thread.join();
      }

      assertEquals(threads * conversions, counts.get());

      assertEquals(0, errors.get());
   }


   @Test
   public void testMultiThreadChanges() throws Throwable {
      CoreMessage coreMessage = new CoreMessage(1, 1024);
      for (int i = 0; i < 10; i++) {
         coreMessage.putBooleanProperty(SimpleString.of("key1"), true);
      }
      coreMessage.putStringProperty(SimpleString.of("_HQ_ORIG_ADDRESS"), SimpleString.of("hqOne"));
      coreMessage.putStringProperty(SimpleString.of("_AMQ_ORIG_QUEUE"), SimpleString.of("amqOne"));
      coreMessage.putStringProperty(SimpleString.of("_AMQ_ORIG_MESSAGE_ID"), SimpleString.of("asdfkhaksdjfhaskfdjhas"));

      int threads = 100;
      int conversions = 100;
      Thread[] t = new Thread[threads];
      CyclicBarrier barrier = new CyclicBarrier(threads);
      AtomicInteger errors = new AtomicInteger(0);
      AtomicInteger counts = new AtomicInteger(0);

      AtomicBoolean running = new AtomicBoolean(true);

      for (int i = 0; i < threads; i++) {
         t[i] = new Thread(() -> {
            try {
               for (int i1 = 0; i1 < conversions; i1++) {
                  counts.incrementAndGet();
                  if (i1 == 0) {
                     barrier.await();
                  }

                  // heads or tails here, I need part of the messages with a big header, part of the messages with a small header
                  boolean heads = RandomUtil.randomBoolean();

                  // this is playing with a scenario where the horentq interceptor will change the size of the message
                  if (heads) {
                     coreMessage.putStringProperty("propChanges", "looooooooooooooooooooong property text");
                  } else {
                     coreMessage.putStringProperty("propChanges", "short one");
                  }

                  heads = RandomUtil.randomBoolean();

                  if (heads) {
                     SessionReceiveMessage_1X receiveMessage_1X = new SessionReceiveMessage_1X(coreMessage);
                     ActiveMQBuffer buffer = receiveMessage_1X.encode(null);
                     buffer.release();
                  } else {
                     SessionReceiveMessage receiveMessage = new SessionReceiveMessage(coreMessage);
                     ActiveMQBuffer buffer = receiveMessage.encode(null);
                     buffer.release();
                  }
               }
            } catch (Throwable e) {
               errors.incrementAndGet();
               e.printStackTrace();
            }
         });
         t[i].start();
      }

      running.set(false);

      for (Thread thread : t) {
         thread.join();
      }

      assertEquals(threads * conversions, counts.get());

      assertEquals(0, errors.get());
   }


}
