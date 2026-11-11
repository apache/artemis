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

package org.apache.activemq.artemis.tests.soak.clientFailure;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;

public class ClientFailureSoakTestClient {

   static void exitVM(int code) {
      // flipping a coin between halt and exit
      if (RandomUtil.randomBoolean()) {
         System.out.println("returning halt " + code);
         Runtime.getRuntime().halt(code);
      } else {
         System.out.println("returning exit " + code);
         System.exit(code);
      }
   }

   public static final int RETURN_OK = 1;
   public static final int RETURN_ERROR = 2;

   public static void main(String[] arg) {
      String protocol = arg[0];
      int numberOfThreads = Integer.parseInt(arg[1]);
      int numberOfConsumers = Integer.parseInt(arg[2]);
      String queueName = arg[3];

      try {
         ConnectionFactory cf = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
         if (protocol.equals("OPENWIRE")) {
            RedeliveryPolicy inifinitePolicy = new RedeliveryPolicy();
            inifinitePolicy.setMaximumRedeliveries(-1);
            ((ActiveMQConnectionFactory)cf).setRedeliveryPolicy(inifinitePolicy);
         }
         ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);

         CyclicBarrier consumersCreated = new CyclicBarrier(numberOfThreads + 1);

         for (int i = 0; i < numberOfThreads; i++) {
            service.execute(() -> {
               try {
                  boolean tx = RandomUtil.randomBoolean(); // flip a coin if we are using TX or Auto-ACK
                  Connection connection = cf.createConnection();
                  connection.start();

                  Session session = tx ? connection.createSession(true, Session.SESSION_TRANSACTED) : connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

                  for (int consI = 0; consI < numberOfConsumers; consI++) {
                     MessageConsumer consumer = session.createConsumer(session.createQueue(queueName));
                     if (consI == 0) {
                        System.out.println("Consumers created");
                        Session anotherSession = connection.createSession(true, Session.SESSION_TRANSACTED);
                        MessageConsumer anotherConsumer = anotherSession.createConsumer(session.createQueue(queueName));
                        consumersCreated.await(30, TimeUnit.SECONDS);
                     }

                     if (tx) {
                        if (consumer.receiveNoWait() == null) {
                           System.out.println("Nothing received");
                        } else {
                           System.out.println("received");
                        }
                     }

                     if (RandomUtil.randomBoolean()) {
                        connection.close();
                        break;
                     }
                  }
               } catch (Throwable e) {
                  System.out.println("FAILURE:" + e.getMessage());
                  e.printStackTrace(System.out);
                  exitVM(RETURN_ERROR);
               }
            });

         }

         System.out.println("Sleeping");
         consumersCreated.await(30, TimeUnit.SECONDS);
         System.out.println("Done");

         exitVM(RETURN_OK);
      } catch (Throwable e) {
         System.out.println("FAILURE:" + e.getMessage());
         e.printStackTrace(System.out);
         exitVM(RETURN_ERROR);
      }
   }

}
