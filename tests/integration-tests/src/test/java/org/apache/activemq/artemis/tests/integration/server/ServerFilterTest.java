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
package org.apache.activemq.artemis.tests.integration.server;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQTopic;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ServerFilterTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(false);
      server.start();
   }

   @Test
   public void concurrentXpathTest() throws Exception {
      final long threadCount = 5;
      final long messageCount = 5;
      final String address = "myAddress";
      final String xpathFilter = "XPATH '/a/b/c/d[text()=\"foo\"]'";
      final String text = "<a><b><c><d>foo</d></c></b></a>";

      server.createQueue(QueueConfiguration.of("A").setAddress(address).setFilterString(xpathFilter).setRoutingType(RoutingType.MULTICAST));
      server.createQueue(QueueConfiguration.of("B").setAddress(address).setFilterString(xpathFilter).setRoutingType(RoutingType.MULTICAST));
      ConnectionFactory cf = new ActiveMQConnectionFactory("vm://0");
      ExecutorService executor = Executors.newFixedThreadPool((int) threadCount);
      for (int i = 0; i < threadCount; i++) {
         executor.submit(() -> {
            try (Connection conn = cf.createConnection()) {
               Session session = conn.createSession();
               MessageProducer producer = session.createProducer(new ActiveMQTopic(address));
               Message msg = session.createTextMessage(text);
               int count = 0;
               while (count++ < messageCount) {
                  msg.setStringProperty("MessageId", String.valueOf(count));
                  producer.send(msg);
               }
               session.close();
            } catch (JMSException e) {
               e.printStackTrace();
            }
         });
      }
      runAfter(executor::shutdownNow);
      Wait.assertEquals(threadCount * messageCount, () -> server.getAddressInfo(SimpleString.of(address)).getRoutedMessageCount(), 2000, 100);
      Wait.assertEquals(threadCount * messageCount, () -> server.locateQueue("A").getMessageCount(), 2000, 100);
      Wait.assertEquals(threadCount * messageCount, () -> server.locateQueue("B").getMessageCount(), 2000, 100);
   }
}
