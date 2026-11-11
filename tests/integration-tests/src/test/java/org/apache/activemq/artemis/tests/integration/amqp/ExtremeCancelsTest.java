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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class ExtremeCancelsTest extends JMSClientTestSupport {

   private SimpleString anycastAddress = SimpleString.of("theQueue");


   @Override
   protected String getConfiguredProtocols() {
      return "AMQP,OPENWIRE,CORE";
   }

   private boolean isAMQP;

   public ExtremeCancelsTest(boolean isAMQP) {
      this.isAMQP = isAMQP;
   }


   @Parameters(name = "{index}: isAMQP={0}")
   public static Collection<Object[]> parameters() {
      return Arrays.asList(new Object[][] {
         {true}, {false}
      });
   }


   @TestTemplate
   @Timeout(120)
   public void testLotsOfCloseOpenConsumer() throws Exception {

      server.createQueue(QueueConfiguration.of(anycastAddress).setRoutingType(RoutingType.ANYCAST));

      AtomicInteger errors = new AtomicInteger(0);
      AtomicBoolean runnning = new AtomicBoolean(true);
      Runnable runnable = () -> {
         try {
            ConnectionFactory factory = createCF();

            Connection connection = factory.createConnection();
            Session session = connection.createSession();
            connection.start();
            Queue queue = session.createQueue(anycastAddress.toString());

            while (runnning.get()) {
               MessageConsumer consumer = session.createConsumer(queue);
               TextMessage message = (TextMessage)consumer.receive(100);
               if (message != null) {
                  consumer.close();
               }
            }

            connection.close();

         } catch (Exception e) {
            e.printStackTrace();
            errors.incrementAndGet();
         }
      };

      Thread[] consumers = new Thread[10];

      for (int i = 0; i < consumers.length; i++) {
         consumers[i] = new Thread(runnable);
         consumers[i].start();
      }

      ConnectionFactory factory = createCF();

      Connection connection = factory.createConnection();
      Session session = connection.createSession();
      Queue queue = session.createQueue(anycastAddress.toString());
      MessageProducer producer = session.createProducer(queue);


      final int NUMBER_OF_MESSAGES = 500;


      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         producer.send(session.createTextMessage("Hello guys " + i));
      }

      runnning.set(false);


      for (Thread c : consumers) {
         c.join();
      }

      assertEquals(0, errors.get());
   }

   private ConnectionFactory createCF() {
      if (isAMQP) {
         return new JmsConnectionFactory(getBrokerQpidJMSConnectionURI());
      } else {
         return new ActiveMQConnectionFactory("tcp://localhost:5672");
      }
   }

}
