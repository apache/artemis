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

import static org.apache.activemq.artemis.utils.TestParameters.testProperty;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.tests.db.common.Database;
import org.apache.activemq.artemis.tests.db.common.ParameterDBTestBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(ParameterizedTestExtension.class)
public class RealServerDatabasePagingTest extends ParameterDBTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String TEST_NAME = "PGDB";

   private static final int MAX_MESSAGES = Integer.parseInt(testProperty(TEST_NAME, "MAX_MESSAGES", "1000"));
   private static final int MAX_LARGE_MESSAGES = Integer.parseInt(testProperty(TEST_NAME, "MAX_LARGE_MESSAGES", "200"));

   private static final int SOAK_MAX_MESSAGES = Integer.parseInt(testProperty(TEST_NAME, "SOAK_MAX_MESSAGES", "100000"));

   private static final int MESSAGE_SIZE = Integer.parseInt(testProperty(TEST_NAME, "MESSAGE_SIZE", "1000"));
   private static final int LARGE_MESSAGE_SIZE = Integer.parseInt(testProperty(TEST_NAME, "LARGE_MESSAGE_SIZE", "500000"));
   private static final int SOAK_MESSAGE_SIZE = Integer.parseInt(testProperty(TEST_NAME, "SOAK_MESSAGE_SIZE", "1000"));

   private static final int COMMIT_INTERVAL = Integer.parseInt(testProperty(TEST_NAME, "COMMIT_INTERVAL", "1000"));

   Process serverProcess;

   @Parameters(name = "db={0}")
   public static Collection<Object[]> parameters() {
      return convertParameters(Database.selectedList());
   }


   @BeforeEach
   public void before() throws Exception {
      serverProcess = startServer(database.getName(), 0, 60_000);
   }


   @TestTemplate
   public void testPaging() throws Exception {
      testPaging("CORE", MAX_MESSAGES, MESSAGE_SIZE);
      testPaging("AMQP", MAX_MESSAGES, MESSAGE_SIZE);
      testPaging("OPENWIRE", MAX_MESSAGES, MESSAGE_SIZE);
   }


   @TestTemplate
   public void testPagingWithLargeMessages() throws Exception {
      testPaging("CORE", MAX_LARGE_MESSAGES, LARGE_MESSAGE_SIZE);
      testPaging("AMQP", MAX_LARGE_MESSAGES, LARGE_MESSAGE_SIZE);
   }

   @TestTemplate
   public void testSoakPaging() throws Exception {
      testPaging("AMQP", SOAK_MAX_MESSAGES, SOAK_MESSAGE_SIZE);
   }

   private void testPaging(String protocol, int messages, int messageSize) throws Exception {
      logger.info("performing paging test on protocol={} and db={}", protocol, database);

      if (database == Database.POSTGRES) {
         prepareCheckPostgres();
      }

      final String queueName = "QUEUE_" + RandomUtil.randomUUIDString() + "_" + protocol + "_" + database;

      ConnectionFactory connectionFactory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      byte[] messageLoad = RandomUtil.randomBytes(messageSize);

      try (Connection connection = connectionFactory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(queueName);
         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < messages; i++) {
            BytesMessage message = session.createBytesMessage();
            message.writeBytes(messageLoad);
            message.setIntProperty("i", i);
            producer.send(message);
            if (i % COMMIT_INTERVAL == 0) {
               logger.info("Sent {} messages", i);
               session.commit();
            }
         }
         session.commit();

      }

      stopServerWithFile(getServerLocation(database.getName()), serverProcess, 1, TimeUnit.MINUTES);
      assertFalse(serverProcess.isAlive());

      serverProcess = startServer(database.getName(), 0, 60_000);

      try (Connection connection = connectionFactory.createConnection()) {
         connection.start();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(queueName);
         MessageConsumer consumer = session.createConsumer(queue);
         for (int i = 0; i < messages; i++) {
            BytesMessage message = (BytesMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals(i, message.getIntProperty("i"));
            assertEquals(messageSize, message.getBodyLength());
            byte[] bytesOutput = new byte[(int)message.getBodyLength()];
            message.readBytes(bytesOutput);
            assertArrayEquals(messageLoad, bytesOutput);
            if (i % COMMIT_INTERVAL == 0) {
               logger.info("Received {}", i);
               session.commit();
            }
         }
         session.commit();
         assertNull(consumer.receiveNoWait());
      }

      if (database == Database.POSTGRES) {
         checkPostgres();
      }


   }

}
