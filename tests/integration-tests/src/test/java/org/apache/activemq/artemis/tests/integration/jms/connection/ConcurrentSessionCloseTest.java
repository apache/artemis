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
package org.apache.activemq.artemis.tests.integration.jms.connection;

import static org.junit.jupiter.api.Assertions.assertFalse;

import javax.jms.Connection;
import javax.jms.Session;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A ConcurrentSessionCloseTest
 */
public class ConcurrentSessionCloseTest extends JMSTestBase {

   private ActiveMQConnectionFactory cf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
   }

   @Test
   public void testConcurrentClose() throws Exception {
      final Connection con = cf.createConnection();

      for (int j = 0; j < 100; j++) {
         final AtomicBoolean failed = new AtomicBoolean(false);

         int threadCount = 10;

         ThreadGroup group = new ThreadGroup("Test");

         Thread[] threads = new Thread[threadCount];

         for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(group, "thread " + i) {
               @Override
               public void run() {
                  try {
                     con.start();

                     Session session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

                     session.close();
                  } catch (Exception e) {
                     e.printStackTrace();

                     failed.set(true);
                  }

               }
            };
            threads[i].start();
         }

         for (int i = 0; i < threadCount; i++) {
            threads[i].join();
         }

         assertFalse(failed.get());
      }
   }
}
