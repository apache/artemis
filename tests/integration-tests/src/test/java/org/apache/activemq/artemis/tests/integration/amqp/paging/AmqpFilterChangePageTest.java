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
package org.apache.activemq.artemis.tests.integration.amqp.paging;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.reflect.Field;
import java.util.Map;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.paging.cursor.impl.PageSubscriptionImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.selector.filter.Filterable;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.junit.jupiter.api.Test;

public class AmqpFilterChangePageTest extends ActiveMQTestBase {

   ActiveMQServer server;

   @Test
   public void testChangingMatching() throws Exception {
      Configuration config = createDefaultConfig(true);

      int NUMBER_OF_MESSAGES = 2000;

      server = createServer(true, config, 100 * 1024, 1024 * 1024, -1, -1);
      server.start();

      server.addAddressInfo(new AddressInfo("AD1").addRoutingType(RoutingType.MULTICAST));
      server.createQueue(QueueConfiguration.of("Q1").setAddress("AD1").setDurable(true).setFilterString("color='red'"));

      ConnectionFactory cf = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:61616");
      Connection connection = cf.createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      MessageProducer producer = session.createProducer(session.createTopic("AD1"));

      Queue queue = server.locateQueue("Q1");

      queue.getPagingStore().startPaging();

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         TextMessage message = session.createTextMessage("hello " + i);
         message.setStringProperty("color", "red");
         producer.send(message);
         if (i % 100 == 0 && i > 0) {
            session.commit();
            queue.getPagingStore().forceAnotherPage(true);
         }
      }
      session.commit();

      PageSubscriptionImpl subscription = (PageSubscriptionImpl) queue.getPageSubscription();
      Field subscriptionField = PageSubscriptionImpl.class.getDeclaredField("filter");
      subscriptionField.setAccessible(true);

      // Replacing the filter for that won't work
      // The system should still respect the original routing
      // This is because if something happened to the message in which parsing did not work
      // the routing should still be respected
      subscriptionField.set(subscription, new Filter() {
         @Override
         public boolean match(org.apache.activemq.artemis.api.core.Message message) {
            return false;
         }

         @Override
         public boolean match(Map<String, String> map) {
            return false;
         }

         @Override
         public boolean match(Filterable filterable) {
            return false;
         }

         @Override
         public SimpleString getFilterString() {
            return null;
         }
      });

      connection.start();
      MessageConsumer consumer = session.createConsumer(session.createQueue("AD1::Q1"));

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         Message message = consumer.receive(5000);
         assertNotNull(message);
      }

      session.commit();

      assertNull(consumer.receiveNoWait());

      connection.close();

   }

}
