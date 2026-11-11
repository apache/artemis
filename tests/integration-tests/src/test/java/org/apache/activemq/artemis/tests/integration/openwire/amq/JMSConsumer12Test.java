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
public class JMSConsumer12Test extends BasicOpenWireTest {

   @Parameters(name = "deliveryMode={0} destinationType={1}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][]{{DeliveryMode.NON_PERSISTENT, ActiveMQDestination.QUEUE_TYPE}, {DeliveryMode.NON_PERSISTENT, ActiveMQDestination.TOPIC_TYPE}});
   }

   public int deliveryMode;
   public byte destinationType;

   public JMSConsumer12Test(int deliveryMode, byte destinationType) {
      this.deliveryMode = deliveryMode;
      this.destinationType = destinationType;
   }

   @TestTemplate
   public void testDontStart() throws Exception {

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ActiveMQDestination destination = createDestination(session, destinationType);
      MessageConsumer consumer = session.createConsumer(destination);

      // Send the messages
      sendMessages(session, destination, 1);

      // Make sure no messages were delivered.
      assertNull(consumer.receive(1000));
   }

   @TestTemplate
   public void testStartAfterSend() throws Exception {
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ActiveMQDestination destination = createDestination(session, destinationType);
      MessageConsumer consumer = session.createConsumer(destination);

      // Send the messages
      sendMessages(session, destination, 1);

      // Start the conncection after the message was sent.
      connection.start();

      // Make sure only 1 message was delivered.
      assertNotNull(consumer.receive(1000));
      assertNull(consumer.receiveNoWait());
   }

}
