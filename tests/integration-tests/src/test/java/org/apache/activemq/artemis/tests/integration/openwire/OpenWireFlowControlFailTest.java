/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Map;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class OpenWireFlowControlFailTest extends OpenWireTestBase {

   public static final String OWHOST = "localhost";
   public static final int OWPORT = 61616;

   protected static final String urlString = "tcp://" + OWHOST + ":" + OWPORT + "?wireFormat.cacheEnabled=true";

   @Override
   protected void configureAddressSettings(Map<String, AddressSettings> addressSettingsMap) {
      addressSettingsMap.put("#", new AddressSettings().setAutoCreateQueues(false).setAutoCreateAddresses(false).
         setDeadLetterAddress(SimpleString.of("ActiveMQ.DLQ")).setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL).setMaxSizeBytes(10000));
   }

   @Test
   @Timeout(60)
   public void testMessagesNotSent() throws Exception {

      AddressInfo addressInfo = new AddressInfo(SimpleString.of("Test"), RoutingType.ANYCAST);
      server.addAddressInfo(addressInfo);
      server.createQueue(QueueConfiguration.of(addressInfo.getName()).setRoutingType(RoutingType.ANYCAST));

      String textBody = " ".repeat(10);
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(urlString);
      factory.setWatchTopicAdvisories(false);
      int numberOfMessage = 0;
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(addressInfo.getName().toString());
         MessageProducer producer = session.createProducer(queue);
         boolean failed = false;
         try {
            for (int i = 0; i < 1000; i++) {
               TextMessage message = session.createTextMessage(textBody);
               message.setIntProperty("i", i);

               producer.send(message);
               numberOfMessage++;
            }
         } catch (Exception e) {
            e.printStackTrace(System.out);
            failed = true;
            try {
               producer.send(session.createTextMessage(textBody));
               fail("Exception expected");
            } catch (JMSException expected) {
               expected.printStackTrace();

            }
         }
         assertTrue(failed);
      }

      factory = new ActiveMQConnectionFactory(urlString);
      factory.setWatchTopicAdvisories(false);
      try (Connection connection2 = factory.createConnection()) {
         Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session2.createQueue(addressInfo.getName().toString());

         MessageConsumer consumer = session2.createConsumer(queue);
         connection2.start();
         for (int i = 0; i < numberOfMessage; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals(textBody, message.getText());
         }

         TextMessage msg = (TextMessage)consumer.receive(500);
         assertNull(msg);
      }
   }
}
