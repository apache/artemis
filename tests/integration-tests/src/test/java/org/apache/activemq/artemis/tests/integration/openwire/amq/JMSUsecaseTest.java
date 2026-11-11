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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.command.ActiveMQDestination;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * adapted from: org.apache.activemq.JMSUsecaseTest
 */
@ExtendWith(ParameterizedTestExtension.class)
public class JMSUsecaseTest extends BasicOpenWireTest {

   @Parameters(name = "deliveryMode={0} destinationType={1}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][]{{DeliveryMode.NON_PERSISTENT, ActiveMQDestination.QUEUE_TYPE}, {DeliveryMode.NON_PERSISTENT, ActiveMQDestination.TEMP_QUEUE_TYPE}, {DeliveryMode.PERSISTENT, ActiveMQDestination.QUEUE_TYPE}, {DeliveryMode.PERSISTENT, ActiveMQDestination.TEMP_QUEUE_TYPE}});
   }

   public int deliveryMode;
   public byte destinationType;

   public JMSUsecaseTest(int deliveryMode, byte destinationType) {
      this.deliveryMode = deliveryMode;
      this.destinationType = destinationType;
   }

   @TestTemplate
   public void testQueueBrowser() throws Exception {
      // Send a message to the broker.
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ActiveMQDestination destination = createDestination(session, destinationType);
      MessageProducer producer = session.createProducer(destination);
      producer.setDeliveryMode(this.deliveryMode);
      sendMessages(session, producer, 5);
      producer.close();

      QueueBrowser browser = session.createBrowser((Queue) destination);
      Enumeration<?> enumeration = browser.getEnumeration();
      for (int i = 0; i < 5; i++) {
         Thread.sleep(100);
         assertTrue(enumeration.hasMoreElements());
         Message m = (Message) enumeration.nextElement();
         assertNotNull(m);
         assertEquals("" + i, ((TextMessage) m).getText());
      }
      assertFalse(enumeration.hasMoreElements());
   }

}
