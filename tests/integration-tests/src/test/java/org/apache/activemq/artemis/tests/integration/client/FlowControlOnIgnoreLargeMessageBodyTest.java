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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowControlOnIgnoreLargeMessageBodyTest extends JMSTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private Topic topic;

   private static int TOTAL_MESSAGES_COUNT = 20000;

   private static int MSG_SIZE = 150 * 1024;

   private final int CONSUMERS_COUNT = 5;

   private static final String ATTR_MSG_COUNTER = "msgIdex";

   protected int receiveTimeout = 10000;

   private volatile boolean error = false;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      jmsServer.createTopic(true, "topicIn", "/topic/topicIn");
      topic = (Topic) namingContext.lookup("/topic/topicIn");
   }

   @Override
   protected boolean usePersistence() {
      return false;
   }

   class LoadProducer extends Thread {

      private final ConnectionFactory cf;

      private final Topic topic;

      private final int messagesCount;

      private volatile boolean requestForStop = false;

      private volatile boolean stopped = false;

      private int sentMessages = 0;

      LoadProducer(final String name,
                   final Topic topic,
                   final ConnectionFactory cf,
                   final int messagesCount) throws Exception {
         super(name);
         this.cf = cf;
         this.topic = topic;
         this.messagesCount = messagesCount;
      }

      public void sendStopRequest() {
         stopped = false;
         requestForStop = true;
      }

      public boolean isStopped() {
         return stopped;
      }

      @Override
      public void run() {
         stopped = false;
         Connection connection = null;
         Session session = null;
         MessageProducer prod;
         logger.debug("Starting producer for {} - {}", topic, getName());
         try {
            connection = cf.createConnection();
            session = connection.createSession(true, Session.SESSION_TRANSACTED);
            prod = session.createProducer(topic);

            prod.setDeliveryMode(DeliveryMode.PERSISTENT);

            for (int i = 1; i <= messagesCount && !requestForStop; i++) {
               if (error) {
                  break;
               }
               sentMessages++;
               BytesMessage msg = session.createBytesMessage();
               msg.setIntProperty(FlowControlOnIgnoreLargeMessageBodyTest.ATTR_MSG_COUNTER, i);
               msg.writeBytes(new byte[FlowControlOnIgnoreLargeMessageBodyTest.MSG_SIZE]);
               prod.send(msg);
               if (i % 10 == 0) {
                  session.commit();
               }
               if (i % 100 == 0) {
                  logger.debug("Address {} sent {} messages", topic, i);
               }
            }
            logger.debug("Ending producer for {} - {} messages {}", topic, getName(), sentMessages);
         } catch (Exception e) {
            error = true;
            e.printStackTrace();
         } finally {
            try {
               session.commit();
            } catch (Exception e) {
               e.printStackTrace();
            }
            try {
               connection.close();
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
         stopped = true;
      }

      public int getSentMessages() {
         return sentMessages;
      }
   }

   /**
    * LoadConsumer
    */
   class LoadConsumer extends Thread {

      private final ConnectionFactory cf;

      private final Topic topic;

      private volatile boolean requestForStop = false;

      private volatile boolean stopped = false;

      private volatile int receivedMessages = 0;

      private final int numberOfMessages;

      private int receiveTimeout = 0;

      private final CountDownLatch consumerCreated;

      LoadConsumer(final CountDownLatch consumerCreated,
                   final String name,
                   final Topic topic,
                   final ConnectionFactory cf,
                   final int receiveTimeout,
                   final int numberOfMessages) {
         super(name);
         this.cf = cf;
         this.topic = topic;
         this.receiveTimeout = receiveTimeout;
         this.numberOfMessages = numberOfMessages;
         this.consumerCreated = consumerCreated;
      }

      public void sendStopRequest() {
         stopped = false;
         requestForStop = true;
      }

      public boolean isStopped() {
         return stopped;
      }

      @Override
      public void run() {
         Connection connection = null;
         Session session = null;
         stopped = false;
         requestForStop = false;
         logger.debug("Starting consumer for {} - {}", topic, getName());
         try {
            connection = cf.createConnection();

            connection.setClientID(getName());

            connection.start();

            session = connection.createSession(true, Session.SESSION_TRANSACTED);

            TopicSubscriber subscriber = session.createDurableSubscriber(topic, getName());

            consumerCreated.countDown();

            int counter = 0;

            while (counter < numberOfMessages && !requestForStop && !error) {
               if (counter == 0) {
                  logger.debug("Starting to consume for {} - {}", topic, getName());
               }
               BytesMessage msg = (BytesMessage) subscriber.receive(receiveTimeout);
               if (msg == null) {
                  logger.debug("Cannot get message in specified timeout: {} - {}", topic, getName());
                  error = true;
               } else {
                  counter++;
                  if (msg.getIntProperty(FlowControlOnIgnoreLargeMessageBodyTest.ATTR_MSG_COUNTER) != counter) {
                     error = true;
                  }
               }
               if (counter % 10 == 0) {
                  session.commit();
               }
               if (counter % 100 == 0) {
                  logger.debug("## {} {} received {}", getName(), topic, counter);
               }
               receivedMessages = counter;
            }
            session.commit();
         } catch (Exception e) {
            logger.debug("Exception in consumer {} : {}", getName(), e.getMessage());
            e.printStackTrace();
         } finally {
            if (session != null) {
               try {
                  session.close();
               } catch (JMSException e) {
                  System.err.println("Cannot close session " + e.getMessage());
               }
            }
            if (connection != null) {
               try {
                  connection.close();
               } catch (JMSException e) {
                  System.err.println("Cannot close connection " + e.getMessage());
               }
            }
         }
         stopped = true;
         logger.debug("Stopping consumer for {} - {}, received {}", topic, getName(), getReceivedMessages());
      }

      public int getReceivedMessages() {
         return receivedMessages;
      }
   }

   @Test
   public void testFlowControl() {
      try {
         LoadProducer producer = new LoadProducer("producer", topic, cf, FlowControlOnIgnoreLargeMessageBodyTest.TOTAL_MESSAGES_COUNT);

         LoadConsumer[] consumers = new LoadConsumer[CONSUMERS_COUNT];

         CountDownLatch latch = new CountDownLatch(CONSUMERS_COUNT);

         for (int i = 0; i < consumers.length; i++) {
            consumers[i] = new LoadConsumer(latch, "consumer " + i, topic, cf, receiveTimeout, FlowControlOnIgnoreLargeMessageBodyTest.TOTAL_MESSAGES_COUNT);
         }

         for (LoadConsumer consumer : consumers) {
            consumer.start();
         }

         waitForLatch(latch);

         producer.start();
         producer.join();
         for (LoadConsumer consumer : consumers) {
            consumer.join();
         }

         String errorMessage = null;
         if (producer.getSentMessages() != FlowControlOnIgnoreLargeMessageBodyTest.TOTAL_MESSAGES_COUNT) {
            errorMessage = "Producer did not send defined count of messages";
         } else {
            for (LoadConsumer consumer : consumers) {
               if (consumer.getReceivedMessages() != FlowControlOnIgnoreLargeMessageBodyTest.TOTAL_MESSAGES_COUNT) {
                  errorMessage = "Consumer did not send defined count of messages";
                  break;
               }
            }
         }

         if (errorMessage != null) {
            System.err.println(errorMessage);
         }

         assertFalse(error);
         if (errorMessage != null) {
            fail(errorMessage);
         }
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
      }
   }

}
