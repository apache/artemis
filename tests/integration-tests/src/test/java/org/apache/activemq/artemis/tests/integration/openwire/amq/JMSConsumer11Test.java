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
import javax.jms.Session;
import java.util.Arrays;
import java.util.Collection;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQMessageConsumer;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.command.ActiveMQDestination;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * adapted from: org.apache.activemq.JMSConsumerTest
 */
@ExtendWith(ParameterizedTestExtension.class)
public class JMSConsumer11Test extends BasicOpenWireTest {

   @Parameters(name = "deliveryMode={0}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][]{{DeliveryMode.NON_PERSISTENT}, {DeliveryMode.PERSISTENT}});
   }

   public int deliveryMode;

   public JMSConsumer11Test(int deliveryMode) {
      this.deliveryMode = deliveryMode;
   }

   @TestTemplate
   public void testPrefetch1MessageNotDispatched() throws Exception {
      // Set prefetch to 1
      connection.getPrefetchPolicy().setAll(1);
      connection.start();

      Session session = connection.createSession(true, 0);
      ActiveMQDestination destination = createDestination(session, ActiveMQDestination.QUEUE_TYPE);
      ActiveMQMessageConsumer consumer = (ActiveMQMessageConsumer)session.createConsumer(destination);

      // Send 2 messages to the destination.
      sendMessages(session, destination, 2);
      session.commit();

      // The prefetch should fill up with 1 message.
      // Since prefetch is still full, the 2nd message should get dispatched
      // to another consumer.. lets create the 2nd consumer test that it does
      // make sure it does.
      ActiveMQConnection connection2 = (ActiveMQConnection) factory.createConnection();
      connection2.start();
      Session session2 = connection2.createSession(true, 0);
      ActiveMQMessageConsumer consumer2 = (ActiveMQMessageConsumer)session2.createConsumer(destination);

      // On a test race you could have a scenario where the message only arrived at the first consumer and
      // if the test is not fast enough the first consumer will receive the message againt
      // This will guarantee the test is correctly balanced.
      Wait.assertEquals(1, consumer::getMessageSize);
      Wait.assertEquals(1, consumer2::getMessageSize);

      // Pick up the first message.
      Message message1 = consumer.receive(1000);
      assertNotNull(message1);

      // Pick up the 2nd messages.
      Message message2 = consumer2.receive(5000);
      assertNotNull(message2);

      session.commit();
      session2.commit();

      Message m = consumer.receiveNoWait();
      assertNull(m);

      try {
         connection2.close();
      } catch (Throwable e) {
         System.err.println("exception e: " + e);
         e.printStackTrace();
      }

   }

}
