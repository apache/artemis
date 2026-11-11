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
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.Map;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ScheduledMessage;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

public class OpenWireScheduledDelayTest extends OpenWireTestBase {

   @Override
   protected void configureAddressSettings(Map<String, AddressSettings> addressSettingsMap) {
      addressSettingsMap.put("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("ActiveMQ.DLQ")));
   }

   @Test
   public void testScheduledDelay() throws Exception {
      final String QUEUE_NAME = RandomUtil.randomUUIDString();
      final long DELAY = 2000;
      final String PROP_NAME = RandomUtil.randomUUIDString();
      final String FIRST = RandomUtil.randomUUIDString();
      final String SECOND = RandomUtil.randomUUIDString();

      ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://127.0.0.1:61616");
      Connection connection = connectionFactory.createConnection();
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Destination destination = session.createQueue(QUEUE_NAME);
      MessageProducer producer = session.createProducer(destination);
      Message firstMessage = session.createMessage();
      firstMessage.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY, DELAY);
      firstMessage.setStringProperty(PROP_NAME, FIRST);
      final long ETA = System.currentTimeMillis() + DELAY;
      producer.send(firstMessage);
      Message secondMessage = session.createMessage();
      secondMessage.setStringProperty(PROP_NAME, SECOND);
      producer.send(secondMessage);
      producer.close();

      MessageConsumer consumer = session.createConsumer(destination);

      Message received = consumer.receive(250);
      assertNotNull(received);
      assertEquals(SECOND, received.getStringProperty(PROP_NAME));

      received = consumer.receive(DELAY + 250);
      assertNotNull(received);
      assertEquals(FIRST, received.getStringProperty(PROP_NAME));
      assertTrue(System.currentTimeMillis() >= ETA);

      connection.close();
   }
}


