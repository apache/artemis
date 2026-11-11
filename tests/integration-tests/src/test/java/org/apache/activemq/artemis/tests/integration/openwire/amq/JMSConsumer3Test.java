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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import java.util.Arrays;
import java.util.Collection;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.command.ActiveMQDestination;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * adapted from: org.apache.activemq.JMSConsumerTest
 */
@ExtendWith(ParameterizedTestExtension.class)
public class JMSConsumer3Test extends BasicOpenWireTest {

   @Parameters(name = "deliveryMode={0} ackMode={1} destinationType={2}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][]{{DeliveryMode.NON_PERSISTENT, Session.AUTO_ACKNOWLEDGE, ActiveMQDestination.QUEUE_TYPE}, {DeliveryMode.NON_PERSISTENT, Session.AUTO_ACKNOWLEDGE, ActiveMQDestination.TOPIC_TYPE}, {DeliveryMode.NON_PERSISTENT, Session.AUTO_ACKNOWLEDGE, ActiveMQDestination.TEMP_QUEUE_TYPE}, {DeliveryMode.NON_PERSISTENT, Session.AUTO_ACKNOWLEDGE, ActiveMQDestination.TEMP_TOPIC_TYPE}, {DeliveryMode.NON_PERSISTENT, Session.DUPS_OK_ACKNOWLEDGE, ActiveMQDestination.QUEUE_TYPE}, {DeliveryMode.NON_PERSISTENT, Session.DUPS_OK_ACKNOWLEDGE, ActiveMQDestination.TOPIC_TYPE}, {DeliveryMode.NON_PERSISTENT, Session.DUPS_OK_ACKNOWLEDGE, ActiveMQDestination.TEMP_QUEUE_TYPE}, {DeliveryMode.NON_PERSISTENT, Session.DUPS_OK_ACKNOWLEDGE, ActiveMQDestination.TEMP_TOPIC_TYPE}, {DeliveryMode.NON_PERSISTENT, Session.CLIENT_ACKNOWLEDGE, ActiveMQDestination.QUEUE_TYPE}, {DeliveryMode.NON_PERSISTENT, Session.CLIENT_ACKNOWLEDGE, ActiveMQDestination.TOPIC_TYPE}, {DeliveryMode.NON_PERSISTENT, Session.CLIENT_ACKNOWLEDGE, ActiveMQDestination.TEMP_QUEUE_TYPE}, {DeliveryMode.NON_PERSISTENT, Session.CLIENT_ACKNOWLEDGE, ActiveMQDestination.TEMP_TOPIC_TYPE}, {DeliveryMode.PERSISTENT, Session.AUTO_ACKNOWLEDGE, ActiveMQDestination.QUEUE_TYPE}, {DeliveryMode.PERSISTENT, Session.AUTO_ACKNOWLEDGE, ActiveMQDestination.TOPIC_TYPE}, {DeliveryMode.PERSISTENT, Session.AUTO_ACKNOWLEDGE, ActiveMQDestination.TEMP_QUEUE_TYPE}, {DeliveryMode.PERSISTENT, Session.AUTO_ACKNOWLEDGE, ActiveMQDestination.TEMP_TOPIC_TYPE}, {DeliveryMode.PERSISTENT, Session.DUPS_OK_ACKNOWLEDGE, ActiveMQDestination.QUEUE_TYPE}, {DeliveryMode.PERSISTENT, Session.DUPS_OK_ACKNOWLEDGE, ActiveMQDestination.TOPIC_TYPE}, {DeliveryMode.PERSISTENT, Session.DUPS_OK_ACKNOWLEDGE, ActiveMQDestination.TEMP_QUEUE_TYPE}, {DeliveryMode.PERSISTENT, Session.DUPS_OK_ACKNOWLEDGE, ActiveMQDestination.TEMP_TOPIC_TYPE}, {DeliveryMode.PERSISTENT, Session.CLIENT_ACKNOWLEDGE, ActiveMQDestination.QUEUE_TYPE}, {DeliveryMode.PERSISTENT, Session.CLIENT_ACKNOWLEDGE, ActiveMQDestination.TOPIC_TYPE}, {DeliveryMode.PERSISTENT, Session.CLIENT_ACKNOWLEDGE, ActiveMQDestination.TEMP_QUEUE_TYPE}, {DeliveryMode.PERSISTENT, Session.CLIENT_ACKNOWLEDGE, ActiveMQDestination.TEMP_TOPIC_TYPE}});
   }

   public ActiveMQDestination destination;
   public int deliveryMode;
   public int prefetch;
   public int ackMode;
   public byte destinationType;
   public boolean durableConsumer;

   public JMSConsumer3Test(int deliveryMode, int ackMode, byte destinationType) {
      this.deliveryMode = deliveryMode;
      this.ackMode = ackMode;
      this.destinationType = destinationType;
   }

   @TestTemplate
   public void testMutiReceiveWithPrefetch1() throws Exception {
      // Set prefetch to 1
      connection.getPrefetchPolicy().setAll(1);
      connection.start();

      // Use all the ack modes
      Session session = connection.createSession(false, ackMode);
      destination = createDestination(session, destinationType);
      MessageConsumer consumer = session.createConsumer(destination);

      // Send the messages
      sendMessages(session, destination, 4);

      // Make sure 4 messages were delivered.
      Message message = null;
      for (int i = 0; i < 4; i++) {
         message = consumer.receive(5000);
         assertNotNull(message);
      }
      assertNull(consumer.receiveNoWait());
      message.acknowledge();
   }

}

