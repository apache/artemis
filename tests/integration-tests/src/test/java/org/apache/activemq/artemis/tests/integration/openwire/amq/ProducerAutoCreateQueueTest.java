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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.command.ActiveMQDestination;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import java.util.Map;

public class ProducerAutoCreateQueueTest extends BasicOpenWireTest {

   @Override
   protected void extraServerConfig(Configuration serverConfig) {
      serverConfig.setAddressQueueScanPeriod(100);
      String match = "#";
      Map<String, AddressSettings> asMap = serverConfig.getAddressSettings();
      asMap.get(match).setAutoCreateAddresses(true).setAutoCreateQueues(true);
   }

   @Test
   public void testProducerBlockWontGetTimeout() throws Exception {
      Connection connection = null;
      try {
         connection = factory.createConnection("admin", "password");
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue trash = session.createQueue("trash");
         final MessageProducer producer = session.createProducer(trash);
         producer.send(session.createTextMessage("foo"));
         assertNotNull(server.getPostOffice().getBinding(SimpleString.of("trash")));
      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }

   @Test
   public void testAutoCreateSendToTopic() throws Exception {
      Connection connection = null;
      try {
         connection = factory.createConnection("admin", "password");
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic trash = session.createTopic("trash");
         final MessageProducer producer = session.createProducer(trash);
         producer.send(session.createTextMessage("foo"));
      } finally {
         if (connection != null) {
            connection.close();
         }
      }

      Wait.assertTrue(() -> server.getAddressInfo(SimpleString.of("trash")) != null);
      Wait.assertEquals(0, server::getTotalMessageCount);
   }

   @Test
   public void testAutoCreateSendToQueue() throws Exception {
      Connection connection = null;
      try {
         connection = factory.createConnection("admin", "password");
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue trash = session.createQueue("trash");
         final MessageProducer producer = session.createProducer(trash);
         producer.send(session.createTextMessage("foo"));
      } finally {
         if (connection != null) {
            connection.close();
         }
      }

      Wait.assertTrue(() -> server.getAddressInfo(SimpleString.of("trash")) != null);
      Wait.assertTrue(() -> server.locateQueue(SimpleString.of("trash")) != null);
      Wait.assertEquals(1, server::getTotalMessageCount);
   }

   @Test
   public void testAutoDelete() throws Exception {
      Connection connection = null;
      try {
         connection = factory.createConnection("admin", "password");
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue trash = session.createQueue("trash");
         final MessageProducer producer = session.createProducer(trash);
         producer.send(session.createTextMessage("foo"));
         assertNotNull(server.locateQueue(SimpleString.of("trash")));
         MessageConsumer consumer = session.createConsumer(trash);
         connection.start();
         assertNotNull(consumer.receive(1000));
      } finally {
         if (connection != null) {
            connection.close();
         }
      }

      SimpleString queueName = SimpleString.of("trash");
      Wait.assertTrue(() -> server.locateQueue(queueName) == null);
   }

   @Test
   public void testAutoDeleteNegative() throws Exception {
      server.getAddressSettingsRepository().addMatch("trash", new AddressSettings().setAutoDeleteQueues(false).setAutoDeleteAddresses(false));
      Connection connection = null;
      try {
         connection = factory.createConnection("admin", "password");
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue trash = session.createQueue("trash");
         final MessageProducer producer = session.createProducer(trash);
         producer.send(session.createTextMessage("foo"));
         assertNotNull(server.locateQueue(SimpleString.of("trash")));
         MessageConsumer consumer = session.createConsumer(trash);
         connection.start();
         assertNotNull(consumer.receive(1000));
      } finally {
         if (connection != null) {
            connection.close();
         }
      }

      Wait.assertTrue(() -> server.locateQueue(SimpleString.of("trash")) != null);
      Wait.assertTrue(() -> server.getAddressInfo(SimpleString.of("trash")) != null);
   }

   @Test
   public void testSendFailsWithoutAutoCreate() throws Exception {
      assertThrows(javax.jms.JMSException.class, () -> {

         Connection connection = null;
         try {
            AddressSettings setting = new AddressSettings().setAutoCreateAddresses(false).setAutoCreateQueues(false);
            server.getAddressSettingsRepository().addMatch("WRONG.#", setting);

            connection = factory.createConnection("admin", "password");
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            ActiveMQDestination destination = ActiveMQDestination.createDestination("WRONG.QUEUE", ActiveMQDestination.QUEUE_TYPE);

            final MessageProducer producer = session.createProducer(null);
            producer.send(destination, session.createTextMessage("foo"));

         } finally {
            if (connection != null) {
               connection.close();
            }
         }
      });
   }

   @Test
   public void testTransactedSendFailsWithoutAutoCreate() throws Exception {
      assertThrows(javax.jms.JMSException.class, () -> {

         Connection connection = null;
         try {
            AddressSettings setting = new AddressSettings().setAutoCreateAddresses(false).setAutoCreateQueues(false);
            server.getAddressSettingsRepository().addMatch("WRONG.#", setting);

            connection = factory.createConnection("admin", "password");
            Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
            ActiveMQDestination destination = ActiveMQDestination.createDestination("WRONG.QUEUE", ActiveMQDestination.QUEUE_TYPE);

            final MessageProducer producer = session.createProducer(null);
            producer.send(destination, session.createTextMessage("foo"));
            session.commit();

         } finally {
            if (connection != null) {
               connection.close();
            }
         }
      });
   }
}
