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
package org.apache.activemq.artemis.tests.db.paging;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.Collection;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.db.common.Database;
import org.apache.activemq.artemis.tests.db.common.ParameterDBTestBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(ParameterizedTestExtension.class)
public class GlobalPagingTest extends ParameterDBTestBase {

   @Parameters(name = "db={0}")
   public static Collection<Object[]> parameters() {
      return convertParameters(Database.randomList());
   }

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
      dropDatabase();
   }

   @Override
   protected final String getJDBCClassName() {
      return database.getDriverClass();
   }

   @TestTemplate
   public void testMaxMessages() throws Exception {
      ActiveMQServer server = createServer(createDefaultConfig(0, true));
      server.getConfiguration().getAddressSettings().clear();
      server.getConfiguration().getAddressSettings().put("#", new AddressSettings().setMaxSizeMessages(10));
      server.getConfiguration().setGlobalMaxMessages(5);
      server.start();
      String addressName = "Q" + RandomUtil.randomUUIDString();
      server.addAddressInfo(new AddressInfo(addressName).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(addressName).setRoutingType(RoutingType.ANYCAST).setDurable(true));

      ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(addressName));
         for (int i = 0; i < 6; i++) {
            producer.send(session.createTextMessage());
         }
         session.commit();

         Queue queue = server.locateQueue(addressName);
         Wait.assertTrue(queue.getPagingStore()::isPaging, 1000, 100);

         connection.start();

         MessageConsumer consumer = session.createConsumer(session.createQueue(addressName));
         for (int i = 0; i < 6; i++) {
            Message message = consumer.receive(5000);
            assertNotNull(message);
         }
         session.commit();

         Wait.assertFalse(queue.getPagingStore()::isPaging, 1000, 100);
      }

   }

   @TestTemplate
   public void testMaxMessagesOpposite() throws Exception {
      ActiveMQServer server = createServer(createDefaultConfig(0, true));
      server.getConfiguration().getAddressSettings().clear();
      server.getConfiguration().getAddressSettings().put("#", new AddressSettings().setMaxSizeMessages(5));
      server.getConfiguration().setGlobalMaxMessages(500);
      server.start();
      String addressName = "Q" + RandomUtil.randomUUIDString();
      server.addAddressInfo(new AddressInfo(addressName).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(addressName).setRoutingType(RoutingType.ANYCAST).setDurable(true));

      ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(addressName));
         for (int i = 0; i < 6; i++) {
            producer.send(session.createTextMessage());
         }
         session.commit();

         Queue queue = server.locateQueue(addressName);
         Wait.assertTrue(queue.getPagingStore()::isPaging, 1000, 100);

         connection.start();

         MessageConsumer consumer = session.createConsumer(session.createQueue(addressName));
         for (int i = 0; i < 6; i++) {
            Message message = consumer.receive(5000);
            assertNotNull(message);
         }
         session.commit();

         Wait.assertFalse(queue.getPagingStore()::isPaging, 1000, 100);
      }

   }

   @TestTemplate
   public void testMaxMessagesBytes() throws Exception {
      ActiveMQServer server = createServer(createDefaultConfig(0, true));
      server.getConfiguration().getAddressSettings().clear();
      server.getConfiguration().getAddressSettings().put("#", new AddressSettings().setMaxSizeMessages(10000).setMaxSizeBytes(100 * 1024 * 1024));
      server.getConfiguration().setGlobalMaxSize(50 * 1024);
      server.start();
      String addressName = "Q" + RandomUtil.randomUUIDString();
      server.addAddressInfo(new AddressInfo(addressName).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(addressName).setRoutingType(RoutingType.ANYCAST).setDurable(true));

      ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(addressName));
         for (int i = 0; i < 6; i++) {
            BytesMessage message = session.createBytesMessage();
            message.writeBytes(new byte[20 * 1024]);
            producer.send(message);
         }
         session.commit();

         Queue queue = server.locateQueue(addressName);
         Wait.assertTrue(queue.getPagingStore()::isPaging, 1000, 100);

         connection.start();

         MessageConsumer consumer = session.createConsumer(session.createQueue(addressName));
         for (int i = 0; i < 6; i++) {
            Message message = consumer.receive(5000);
            assertNotNull(message);
         }
         session.commit();

         Wait.assertFalse(queue.getPagingStore()::isPaging, 1000, 100);
      }

   }

   @TestTemplate
   public void testMaxMessagesBytesOpposite() throws Exception {
      ActiveMQServer server = createServer(createDefaultConfig(0, true));
      server.getConfiguration().getAddressSettings().clear();
      server.getConfiguration().getAddressSettings().put("#", new AddressSettings().setMaxSizeMessages(10000).setMaxSizeBytes(50 * 1024));
      server.getConfiguration().setGlobalMaxSize(10 * 1024 * 1024);
      server.start();
      String addressName = "Q" + RandomUtil.randomUUIDString();
      server.addAddressInfo(new AddressInfo(addressName).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(addressName).setRoutingType(RoutingType.ANYCAST).setDurable(true));

      ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(addressName));
         for (int i = 0; i < 6; i++) {
            BytesMessage message = session.createBytesMessage();
            message.writeBytes(new byte[20 * 1024]);
            producer.send(message);
         }
         session.commit();

         Queue queue = server.locateQueue(addressName);
         Wait.assertTrue(queue.getPagingStore()::isPaging, 1000, 100);

         connection.start();

         MessageConsumer consumer = session.createConsumer(session.createQueue(addressName));
         for (int i = 0; i < 6; i++) {
            Message message = consumer.receive(5000);
            assertNotNull(message);
         }
         session.commit();

         Wait.assertFalse(queue.getPagingStore()::isPaging, 1000, 100);
      }

   }
}