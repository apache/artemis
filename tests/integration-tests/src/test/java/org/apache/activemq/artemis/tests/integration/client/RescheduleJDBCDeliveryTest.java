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

package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.cli.commands.tools.PrintData;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RescheduleJDBCDeliveryTest extends ActiveMQTestBase {

   // Set this to true if you're debugging what happened in the journal
   private final boolean PRINT_DATA = false;

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


   @Test
   public void testRescheduledRedeliveryCORE() throws Exception {
      testRescheduledRedelivery("CORE", 0);
   }

   @Test
   public void testRescheduledRedeliveryCORE_1() throws Exception {
      testRescheduledRedelivery("CORE", 1);
   }

   @Test
   public void testRescheduledRedeliveryAMQP_1() throws Exception {
      testRescheduledRedelivery("AMQP", 1);
   }

   @Test
   public void testRescheduledRedeliveryAMQP() throws Exception {
      testRescheduledRedelivery("AMQP", 0);
   }

   @Test
   public void testRescheduledRedeliveryCOREInfinite() throws Exception {
      testRescheduledRedelivery("CORE", -1);
   }

   @Test
   public void testRescheduledRedeliveryAMQPInfinite() throws Exception {
      testRescheduledRedelivery("AMQP", -1);
   }

   private void testRescheduledRedelivery(String protocol, int maxRecords) throws Exception {
      int maxRedeliveries = 100;
      String testQueue = getName();
      Configuration configuration = createDefaultJDBCConfig(true);
      configuration.setMaxRedeliveryRecords(maxRecords);
      configuration.addAddressSetting("#", new AddressSettings().setRedeliveryDelay(1).setMaxDeliveryAttempts(-1).setDeadLetterAddress(SimpleString.of("DLQ")));
      configuration.addAddressConfiguration(new CoreAddressConfiguration().setName("DLQ").addRoutingType(RoutingType.ANYCAST));
      configuration.addQueueConfiguration(QueueConfiguration.of("DLQ").setAddress("DLQ").setRoutingType(RoutingType.ANYCAST));
      configuration.addAddressConfiguration(new CoreAddressConfiguration().setName(testQueue).addRoutingType(RoutingType.ANYCAST));
      configuration.addQueueConfiguration(QueueConfiguration.of(testQueue).setAddress(testQueue).setRoutingType(RoutingType.ANYCAST));
      ActiveMQServer server = createServer(true, configuration, AddressSettings.DEFAULT_PAGE_SIZE, AddressSettings.DEFAULT_MAX_SIZE_BYTES);
      server.start();

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(testQueue);
         MessageProducer producer = session.createProducer(queue);
         producer.send(session.createTextMessage("hello"));
         session.commit();

         connection.start();

         MessageConsumer consumer = session.createConsumer(queue);

         for (int i = 0; i < maxRedeliveries; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            logger.debug("received {}", message);
            assertNotNull(message);
            session.rollback();
         }
      }

      server.stop();

      java.sql.Connection jdbcConnection = DriverManager.getConnection(getTestJDBCConnectionUrl());
      runAfter(jdbcConnection::close);

      int records = executeQuery(jdbcConnection, "SELECT * FROM MESSAGE WHERE USERRECORDTYPE=36 OR USERRECORDTYPE=34");

      // manually set this value to true if you need to understand what's in the journal
      if (PRINT_DATA) {
         PrintData printData = new PrintData();
         printData.printDataJDBC(configuration, System.out);
      }

      if (maxRecords < 0) {
         assertEquals(maxRedeliveries * 2, records);
      } else {

         assertEquals(maxRecords * 2, records);
      }

      server.start();

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(testQueue);
         connection.start();

         MessageConsumer consumer = session.createConsumer(queue);

         TextMessage message = (TextMessage) consumer.receive(5000);
         logger.debug("received {}", message);
         assertNotNull(message);
         session.commit();
         assertNull(consumer.receiveNoWait());
      }
   }

   protected int executeQuery(java.sql.Connection connection, String sql) throws Exception {
      PreparedStatement statement = connection.prepareStatement(sql);
      ResultSet result = statement.executeQuery();
      ResultSetMetaData metaData = result.getMetaData();
      int columnCount = metaData.getColumnCount();
      int records = 0;

      while (result.next()) {
         if (logger.isDebugEnabled()) {
            StringBuilder line = new StringBuilder();
            for (int i = 1; i <= columnCount; i++) {
               Object value = result.getObject(i);
               line.append(metaData.getColumnLabel(i) + " = " + value);
               if (i + 1 <= columnCount) {
                  line.append(", ");
               }
            }
            logger.info(line.toString());
         }
         records++;
      }

      return records;

   }
}
