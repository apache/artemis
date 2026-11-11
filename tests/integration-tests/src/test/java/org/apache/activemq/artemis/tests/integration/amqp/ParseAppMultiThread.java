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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessagePersisterV2;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

public class ParseAppMultiThread {


   @Test
   public void testMultiThreadParsing() throws Exception {

      for (int rep = 0; rep < 500; rep++) {
         String randomStr = RandomUtil.randomUUIDString();
         Map map = new HashMap();
         map.put("color", randomStr);
         for (int i = 0; i < 10; i++) {
            map.put("stuff" + i, "value" + i); // just filling stuff
         }
         AMQPStandardMessage originalMessage = AMQPStandardMessage.createMessage(1, 0, SimpleString.of("duh"), null, null, null, null, map, null, null);


         // doing a round trip that would be made through persistence
         AMQPMessagePersisterV2 persister = AMQPMessagePersisterV2.getInstance();

         ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(1024);
         persister.encode(buffer, originalMessage);
         buffer.readerIndex(1);

         AMQPStandardMessage amqpStandardMessage = (AMQPStandardMessage) persister.decode(buffer, null, null);


         if (rep == 0) {
            // it is enough to check the first time only
            // this is to make sure the message does not have application properties parsed
            Field field = AMQPMessage.class.getDeclaredField("applicationProperties");
            field.setAccessible(true);
            assertNull(field.get(amqpStandardMessage));
         }


         Thread[] threads = new Thread[0];
         CyclicBarrier barrier = threads.length > 0 ? new CyclicBarrier(threads.length) : null;

         AtomicInteger errors = new AtomicInteger(0);

         for (int i = 0; i < threads.length; i++) {
            Runnable r = () -> {
               try {
                  barrier.await();
                  assertEquals(randomStr, amqpStandardMessage.getObjectProperty(SimpleString.of("color")));
               } catch (Throwable e) {
                  e.printStackTrace();
                  errors.incrementAndGet();
               }
            };

            threads[i] = new Thread(r);
            threads[i].start();
         }

         for (Thread t : threads) {
            t.join();
         }

         assertEquals(randomStr, amqpStandardMessage.getObjectPropertyForFilter(SimpleString.of("color")));
         assertEquals(0, errors.get());
      }

   }

}
