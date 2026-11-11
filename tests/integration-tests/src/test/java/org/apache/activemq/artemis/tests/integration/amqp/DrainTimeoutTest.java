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

import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Session;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class DrainTimeoutTest extends AmqpClientTestSupport {

   final int NUMBER_OF_MESSAGES = 1000;

   @Test
   @Timeout(60)
   public void testFlowControl() throws Exception {
      final AtomicInteger errors = new AtomicInteger(0);
      final String queueName = getQueueName();
      JmsConnectionFactory connectionFactory =
         new JmsConnectionFactory(
            "amqp://localhost:5672?jms.prefetchPolicy.all=1&jms.connectTimeout=60000&amqp.drainTimeout=1000");
      LongAdder sendCount = new LongAdder();
      LongAdder consumeCount = new LongAdder();
      Thread consumerThread =
         new Thread(
            () -> {
               try (JMSContext listenerContext =
                       connectionFactory.createContext(Session.AUTO_ACKNOWLEDGE)) {
                  try (JMSConsumer consumer =
                          listenerContext.createConsumer(
                             listenerContext.createQueue(queueName))) {
                     while (!Thread.interrupted()) {
                        while (true) {
                           if (consumer.receiveNoWait() == null) {
                              break;
                           }
                           consumeCount.increment();
                           if (consumeCount.sum() == NUMBER_OF_MESSAGES) {
                              return;
                           }
                        }
                     }
                  }
               } catch (Exception e) {
                  e.printStackTrace(System.out);
                  errors.incrementAndGet();
               }
            });
      consumerThread.start();
      try (JMSContext context = connectionFactory.createContext(Session.AUTO_ACKNOWLEDGE)) {
         final Message message = context.createMessage();
         message.setStringProperty("selector", "dude");
         JMSProducer producer = context.createProducer();
         Queue queue = context.createQueue(queueName);
         while (sendCount.sum() < NUMBER_OF_MESSAGES && !Thread.interrupted()) {
            producer.send(queue, message);
            sendCount.increment();
            long sent = sendCount.sum();
         }
      }

      consumerThread.join();

      assertEquals(0, errors.get());
   }
}
