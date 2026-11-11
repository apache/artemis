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
package org.apache.activemq.artemis.tests.integration.openwire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@ExtendWith(ParameterizedTestExtension.class)
public class OpenWireGroupingTest extends BasicOpenWireTest {

   //whether to use core send grouping messages
   private boolean coreSend;
   //whether to use core receive grouping messages
   private boolean coreReceive;

   @Parameters(name = "core-send={0} core-receive={1}")
   public static Collection<Object[]> params() {
      return Arrays.asList(new Object[][]{{true, true},
                                          {true, false},
                                          {false, true},
                                          {false, false}});
   }

   public OpenWireGroupingTest(boolean coreSend, boolean coreReceive) {
      this.coreSend = coreSend;
      this.coreReceive = coreReceive;
   }

   @TestTemplate
   public void testGrouping() throws Exception {

      String jmsxgroupID = null;

      ConnectionFactory sendFact = coreSend ? coreCf : factory;
      ConnectionFactory receiveFact = coreReceive ? coreCf : factory;

      final int num = 10;
      try (Connection coreConn = sendFact.createConnection()) {

         Session session = coreConn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);
         MessageProducer producer = session.createProducer(queue);

         for (int j = 0; j < num; j++) {
            TextMessage message = session.createTextMessage();

            message.setText("Message" + j);

            setProperty(message);

            producer.send(message);

            String prop = message.getStringProperty("JMSXGroupID");

            assertNotNull(prop);

            if (jmsxgroupID != null) {
               assertEquals(jmsxgroupID, prop);
            } else {
               jmsxgroupID = prop;
            }
         }
      }
      try (Connection connection = receiveFact.createConnection()) {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);

         MessageConsumer consumer1 = session.createConsumer(queue);
         MessageConsumer consumer2 = session.createConsumer(queue);
         MessageConsumer consumer3 = session.createConsumer(queue);

         connection.start();

         List<MessageConsumer> otherConsumers = new ArrayList<>();
         otherConsumers.add(consumer1);
         otherConsumers.add(consumer2);
         otherConsumers.add(consumer3);

         //find out which one broker picks up
         MessageConsumer groupConsumer = null;
         for (MessageConsumer consumer : otherConsumers) {
            TextMessage tm = (TextMessage) consumer.receive(2000);
            if (tm != null) {
               assertEquals("Message" + 0, tm.getText());
               otherConsumers.remove(consumer);
               groupConsumer = consumer;
               break;
            }
         }
         assertNotNull(groupConsumer);

         //All msgs should go to the group consumer
         for (int j = 1; j < num; j++) {

            TextMessage tm = (TextMessage) groupConsumer.receive(2000);

            assertNotNull(tm);

            assertEquals("Message" + j, tm.getText());

            assertEquals(tm.getStringProperty("JMSXGroupID"), jmsxgroupID);
         }

         for (MessageConsumer consumer : otherConsumers) {
            assertNull(consumer.receive(100));
         }
      }

   }

   protected void setProperty(Message message) {
      if (coreSend) {
         ((ActiveMQMessage) message).getCoreMessage().putStringProperty(org.apache.activemq.artemis.api.core.Message.HDR_GROUP_ID, SimpleString.of("foo"));
      } else {
         org.apache.activemq.command.ActiveMQMessage m = (org.apache.activemq.command.ActiveMQMessage) message;
         m.setGroupID("foo");
      }
   }
}
