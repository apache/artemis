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
package org.apache.activemq.artemis.tests.integration.openwire.amq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.junit.jupiter.api.Test;

/**
 * adapted from: org.apache.activemq.JmsConsumerResetActiveListenerTest
 */
public class JmsConsumerResetActiveListenerTest extends BasicOpenWireTest {

   /**
    * verify the (undefined by spec) behaviour of setting a listener while receiving a message.
    */
   @Test
   public void testSetListenerFromListener() throws Exception {
      Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      Destination dest = session.createQueue(queueName);
      final MessageConsumer consumer = session.createConsumer(dest);

      final CountDownLatch latch = new CountDownLatch(2);
      final AtomicBoolean first = new AtomicBoolean(true);
      final Vector<Object> results = new Vector<>();
      consumer.setMessageListener(new MessageListener() {

         @Override
         public void onMessage(Message message) {
            if (first.compareAndSet(true, false)) {
               try {
                  consumer.setMessageListener(this);
                  results.add(message);
               } catch (JMSException e) {
                  results.add(e);
               }
            } else {
               results.add(message);
            }
            latch.countDown();
         }
      });

      connection.start();

      MessageProducer producer = session.createProducer(dest);
      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      producer.send(session.createTextMessage("First"));
      producer.send(session.createTextMessage("Second"));

      assertTrue(latch.await(5, TimeUnit.SECONDS), "we did not timeout");

      assertEquals(2, results.size(), "we have a result");
      Object result = results.get(0);
      assertInstanceOf(TextMessage.class, result);
      assertEquals("First", ((TextMessage) result).getText(), "result is first");
      result = results.get(1);
      assertInstanceOf(TextMessage.class, result);
      assertEquals("Second", ((TextMessage) result).getText(), "result is first");
   }

   /**
    * and a listener on a new consumer, just in case.
    */
   @Test
   public void testNewConsumerSetListenerFromListener() throws Exception {
      final Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      final Destination dest = session.createQueue(queueName);
      final MessageConsumer consumer = session.createConsumer(dest);

      final CountDownLatch latch = new CountDownLatch(2);
      final AtomicBoolean first = new AtomicBoolean(true);
      final Vector<Object> results = new Vector<>();
      consumer.setMessageListener(new MessageListener() {

         @Override
         public void onMessage(Message message) {
            if (first.compareAndSet(true, false)) {
               try {
                  MessageConsumer anotherConsumer = session.createConsumer(dest);
                  anotherConsumer.setMessageListener(this);
                  results.add(message);
               } catch (JMSException e) {
                  results.add(e);
               }
            } else {
               results.add(message);
            }
            latch.countDown();
         }
      });

      connection.start();

      MessageProducer producer = session.createProducer(dest);
      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      producer.send(session.createTextMessage("First"));
      producer.send(session.createTextMessage("Second"));

      assertTrue(latch.await(5, TimeUnit.SECONDS), "we did not timeout");

      assertEquals(2, results.size(), "we have a result");
      Object result = results.get(0);
      assertInstanceOf(TextMessage.class, result);
      assertEquals("First", ((TextMessage) result).getText(), "result is first");
      result = results.get(1);
      assertInstanceOf(TextMessage.class, result);
      assertEquals("Second", ((TextMessage) result).getText(), "result is first");
   }

}
