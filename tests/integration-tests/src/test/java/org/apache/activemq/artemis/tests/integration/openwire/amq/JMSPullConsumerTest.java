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

import javax.jms.DeliveryMode;
import javax.jms.Session;
import java.util.Arrays;
import java.util.Collection;
import org.apache.activemq.ActiveMQMessageConsumer;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.command.ActiveMQDestination;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(ParameterizedTestExtension.class)
public class JMSPullConsumerTest extends BasicOpenWireTest {

   @Parameters(name = "deliveryMode={0} destinationType={1}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][]{{DeliveryMode.NON_PERSISTENT, ActiveMQDestination.QUEUE_TYPE},
                                          {DeliveryMode.NON_PERSISTENT, ActiveMQDestination.TEMP_QUEUE_TYPE},
                                          {DeliveryMode.PERSISTENT, ActiveMQDestination.QUEUE_TYPE},
                                          {DeliveryMode.PERSISTENT, ActiveMQDestination.TEMP_QUEUE_TYPE}});
   }

   public ActiveMQDestination destination;
   public int deliveryMode;
   public byte destinationType;

   public JMSPullConsumerTest(int deliveryMode, byte destinationType) {
      this.deliveryMode = deliveryMode;
      this.destinationType = destinationType;
   }

   @TestTemplate
   public void testReceiveMessageWithZeroPrefetchDoesNotOverConsumeWhenMessagesAdded() throws Exception {
      // Receive a message with the JMS API using pull consumers
      connection.getPrefetchPolicy().setAll(0);
      connection.start();

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      destination = createDestination(session, destinationType);
      ActiveMQMessageConsumer consumer = (ActiveMQMessageConsumer) session.createConsumer(destination);

      assertNull(consumer.receive(10));
      assertNull(consumer.receive(10));
      assertNull(consumer.receive(10));

      sendMessages(session, destination, 3);

      assertNotNull(consumer.receive(10));

      ActiveMQMessageConsumer consumer2 = (ActiveMQMessageConsumer) session.createConsumer(destination);
      ActiveMQMessageConsumer consumer3 = (ActiveMQMessageConsumer) session.createConsumer(destination);

      assertNotNull(consumer2.receive(10));
      assertNotNull(consumer3.receive(10));
   }

   @TestTemplate
   public void testReceiveMessageWithZeroPrefetchDoesNotRetainCreditAfterTimedPulls() throws Exception {
      // Receive a message with the JMS API using pull consumers
      connection.getPrefetchPolicy().setAll(0);
      connection.start();

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      destination = createDestination(session, destinationType);
      ActiveMQMessageConsumer consumer = (ActiveMQMessageConsumer) session.createConsumer(destination);

      assertNull(consumer.receive(10));
      assertNull(consumer.receive(10));
      assertNull(consumer.receive(10));

      sendMessages(session, destination, 3);

      ActiveMQMessageConsumer consumer2 = (ActiveMQMessageConsumer) session.createConsumer(destination);

      assertNotNull(consumer2.receive(1000));
      assertNotNull(consumer2.receive(1000));
      assertNotNull(consumer2.receive(1000));

      assertNull(consumer.receiveNoWait());
   }
}
