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
package org.apache.activemq.artemis.tests.integration.jms;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class FloodServerTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      Configuration config = createDefaultNettyConfig();
      server = addServer(ActiveMQServers.newActiveMQServer(config, false));
      server.start();
   }

   @Test
   public void testFlood() throws Exception {
      ConnectionFactory cf = ActiveMQJMSClient.createConnectionFactory("tcp://127.0.0.1:61616?retryInterval=1000&retryIntervalMultiplier=1.0&reconnectAttempts=-1&callTimeout=30000&clientFailureCheckPeriod=1000&maxRetryInterval=1000&blockOnDurableSend=false&blockOnAcknowledge=false", "cf");

      final int numProducers = 20;

      final int numConsumers = 20;

      final int numMessages = 10000;

      ProducerThread[] producers = new ProducerThread[numProducers];

      for (int i = 0; i < numProducers; i++) {
         producers[i] = new ProducerThread(cf, numMessages);
      }

      ConsumerThread[] consumers = new ConsumerThread[numConsumers];

      for (int i = 0; i < numConsumers; i++) {
         consumers[i] = new ConsumerThread(cf, numMessages);
      }

      for (int i = 0; i < numConsumers; i++) {
         consumers[i].start();
      }

      for (int i = 0; i < numProducers; i++) {
         producers[i].start();
      }

      for (int i = 0; i < numConsumers; i++) {
         consumers[i].join();
      }

      for (int i = 0; i < numProducers; i++) {
         producers[i].join();
      }

   }

   class ProducerThread extends Thread {

      private final Connection connection;

      private final Session session;

      private final MessageProducer producer;

      private final int numMessages;

      ProducerThread(final ConnectionFactory cf, final int numMessages) throws Exception {
         connection = cf.createConnection();

         session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         producer = session.createProducer(ActiveMQJMSClient.createTopic("my-topic"));

         producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

         this.numMessages = numMessages;
      }

      @Override
      public void run() {
         try {
            byte[] bytes = new byte[1000];

            BytesMessage message = session.createBytesMessage();

            message.writeBytes(bytes);

            for (int i = 0; i < numMessages; i++) {
               producer.send(message);
            }

            connection.close();
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   class ConsumerThread extends Thread {

      private final Connection connection;

      private final Session session;

      private final MessageConsumer consumer;

      private final int numMessages;

      ConsumerThread(final ConnectionFactory cf, final int numMessages) throws Exception {
         connection = cf.createConnection();

         connection.start();

         session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         consumer = session.createConsumer(ActiveMQJMSClient.createTopic("my-topic"));

         this.numMessages = numMessages;
      }

      @Override
      public void run() {
         try {
            for (int i = 0; i < numMessages; i++) {
               Message msg = consumer.receive();

               if (msg == null) {
                  FloodServerTest.logger.error("message is null");
                  break;
               }
            }

            connection.close();
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

}
