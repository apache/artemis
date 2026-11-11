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

import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.command.ActiveMQDestination;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * adapted from: org.apache.activemq.JmsCreateConsumerInOnMessageTest
 */
public class JmsCreateConsumerInOnMessageTest extends BasicOpenWireTest implements MessageListener {

   private Session publisherSession;
   private Session consumerSession;
   private MessageConsumer consumer;
   private MessageConsumer testConsumer;
   private MessageProducer producer;
   private Topic topic;
   private final Object lock = new Object();
   private final AtomicBoolean failed = new AtomicBoolean(false);

   /**
    * Tests if a consumer can be created asynchronusly
    */
   @Test
   public void testCreateConsumer() throws Exception {
      connection.setClientID("connection:" + "JmsCreateConsumerInOnMessageTest");
      publisherSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      consumerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      topic = (Topic) super.createDestination(consumerSession, ActiveMQDestination.TOPIC_TYPE);
      consumer = consumerSession.createConsumer(topic);
      consumer.setMessageListener(this);
      producer = publisherSession.createProducer(topic);
      connection.start();
      Message msg = publisherSession.createMessage();
      producer.send(msg);

      synchronized (lock) {
         long timeout = System.currentTimeMillis() + 3000;
         while (testConsumer == null && timeout > System.currentTimeMillis()) {
            lock.wait(1000);
         }
      }
      assertNotNull(testConsumer);
      assertFalse(failed.get());
   }

   /**
    * Use the asynchronous subscription mechanism
    */
   @Override
   public void onMessage(Message message) {
      try {
         synchronized (lock) {
            testConsumer = consumerSession.createConsumer(topic);
            consumerSession.createProducer(topic);
            lock.notify();
         }
      } catch (Exception ex) {
         ex.printStackTrace();
         failed.set(true);
         lock.notify();
      }
   }
}
