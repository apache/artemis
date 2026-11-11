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
package org.apache.activemq.artemis.tests.integration.paging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PagingLimitTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   ActiveMQServer server;

   @Test
   public void testPageLimitMessageCoreFail() throws Exception {
      testPageLimitMessage("CORE", false);
   }

   @Test
   public void testPageLimitAMQPFail() throws Exception {
      testPageLimitMessage("AMQP", false);
   }

   @Test
   public void testPageLimitMessagesOpenWireFail() throws Exception {
      testPageLimitMessage("OPENWIRE", false);
   }

   @Test
   public void testPageLimitMessageCoreDrop() throws Exception {
      testPageLimitMessage("CORE", false);
   }

   @Test
   public void testPageLimitAMQPDrop() throws Exception {
      testPageLimitMessage("AMQP", false);
   }

   @Test
   public void testPageLimitMessagesOpenWireDrop() throws Exception {
      testPageLimitMessage("OPENWIRE", false);
   }

   public void testPageLimitMessage(String protocol, boolean drop) throws Exception {

      String queueNameTX = getName() + "_TX";
      String queueNameNonTX = getName() + "_NONTX";

      Configuration config = createDefaultConfig(true);
      config.setJournalSyncTransactional(false).setJournalSyncTransactional(false);

      final int PAGE_MAX = 20 * 1024;

      final int PAGE_SIZE = 10 * 1024;

      server = createServer(true, config, PAGE_SIZE, PAGE_MAX, -1, -1, null, 300L, drop ? "DROP" : "FAIL", null);
      server.start();

      server.addAddressInfo(new AddressInfo(queueNameTX).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queueNameTX).setRoutingType(RoutingType.ANYCAST));
      server.addAddressInfo(new AddressInfo(queueNameNonTX).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queueNameNonTX).setRoutingType(RoutingType.ANYCAST));

      Wait.assertTrue(() -> server.locateQueue(queueNameNonTX) != null);
      Wait.assertTrue(() -> server.locateQueue(queueNameTX) != null);

      testPageLimitMessageFailInternal(queueNameTX, protocol, true, drop);
      testPageLimitMessageFailInternal(queueNameNonTX, protocol, false, drop);

   }

   private void testPageLimitMessageFailInternal(String queueName,
                                                 String protocol,
                                                 boolean transacted,
                                                 boolean drop) throws Exception {
      org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(queueName);
      assertNotNull(serverQueue);

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(transacted, transacted ? Session.SESSION_TRANSACTED : Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);
         MessageProducer producer = session.createProducer(queue);
         connection.start();

         for (int i = 0; i < 100; i++) {
            TextMessage message = session.createTextMessage("initial " + i);
            message.setIntProperty("i", i);
            producer.send(message);
         }
         if (transacted) {
            session.commit();
            assertTrue(serverQueue.getPagingStore().isPaging());
         }

         for (int i = 0; i < 300; i++) {
            if (i == 200) {
               // the initial sent has to be consumed on transaction as we need a sync on the consumer for AMQP
               try (MessageConsumer consumer = session.createConsumer(queue)) {
                  for (int initI = 0; initI < 100; initI++) {
                     TextMessage recMessage = (TextMessage) consumer.receive(1000);
                     assertEquals("initial " + initI, recMessage.getText());
                  }
               }
               if (transacted) {
                  session.commit();
               }
               Wait.assertEquals(200L, serverQueue::getMessageCount);
            }

            try {
               TextMessage message = session.createTextMessage("hello world " + i);
               message.setIntProperty("i", i);
               producer.send(message);
               if (i % 100 == 0) {
                  logger.info("sent " + i);
               }
               if (transacted) {
                  if (i % 100 == 0 && i > 0) {
                     session.commit();
                  }
               }
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
               fail("Exception happened at " + i);
            }
         }
         if (transacted) {
            session.commit();
         }

         try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
            producer.send(session.createTextMessage("should not complete"));
            if (transacted) {
               session.commit();
            }
            if (!drop) {
               fail("an Exception was expected");
            }
            assertTrue(loggerHandler.findText("AMQ224120"));
         } catch (JMSException e) {
            logger.debug("Expected exception, ok!", e);
         }


         assertTrue(serverQueue.getPagingStore().isPaging());

         MessageConsumer consumer = session.createConsumer(queue);
         for (int i = 0; i < 150; i++) { // we will consume half of the messages
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals("hello world " + i, message.getText());
            assertEquals(i, message.getIntProperty("i"));
            if (transacted) {
               if (i % 100 == 0 && i > 0) {
                  session.commit();
               }
            }
         }
         if (transacted) {
            session.commit();
         }
         Future<Boolean> cleanupDone = serverQueue.getPagingStore().getCursorProvider().scheduleCleanup();

         assertTrue(cleanupDone.get(30, TimeUnit.SECONDS));



         for (int i = 300; i < 450; i++) {
            try {
               TextMessage message = session.createTextMessage("hello world " + i);
               message.setIntProperty("i", i);
               producer.send(message);
               if (i % 100 == 0) {
                  logger.info("sent " + i);
               }
               if (transacted) {
                  if (i % 10 == 0 && i > 0) {
                     session.commit();
                  }
               }
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
               fail("Exception happened at " + i);
            }
         }
         if (transacted) {
            session.commit();
         }


         try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
            producer.send(session.createTextMessage("should not complete"));
            if (transacted) {
               session.commit();
            }
            if (!drop) {
               fail("an Exception was expected");
            } else {
               assertFalse(loggerHandler.findText("AMQ224120"));
            }
         } catch (JMSException e) {
            logger.debug("Expected exception, ok!", e);
         }

         for (int i = 150; i < 450; i++) { // we will consume half of the messages
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals("hello world " + i, message.getText());
            assertEquals(i, message.getIntProperty("i"));
            if (transacted) {
               if (i % 100 == 0 && i > 0) {
                  session.commit();
               }
            }
         }

         assertNull(consumer.receiveNoWait());
      }

   }


   @Test
   public void testPageLimitBytesAMQP() throws Exception {
      testPageLimitBytes("AMQP");
   }

   @Test
   public void testPageLimitBytesCore() throws Exception {
      testPageLimitBytes("CORE");
   }

   @Test
   public void testPageLimitBytesOpenWire() throws Exception {
      testPageLimitBytes("OPENWIRE");
   }

   public void testPageLimitBytes(String protocol) throws Exception {

      String queueNameTX = getName() + "_TX";
      String queueNameNonTX = getName() + "_NONTX";

      Configuration config = createDefaultConfig(true);
      config.setJournalSyncTransactional(false).setJournalSyncTransactional(false);

      final int PAGE_MAX = 20 * 1024;

      final int PAGE_SIZE = 10 * 1024;

      server = createServer(true, config, PAGE_SIZE, PAGE_MAX, -1, -1, (long)(PAGE_MAX * 10), null, "FAIL", null);
      server.start();

      server.addAddressInfo(new AddressInfo(queueNameTX).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queueNameTX).setRoutingType(RoutingType.ANYCAST));
      server.addAddressInfo(new AddressInfo(queueNameNonTX).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queueNameNonTX).setRoutingType(RoutingType.ANYCAST));

      Wait.assertTrue(() -> server.locateQueue(queueNameNonTX) != null);
      Wait.assertTrue(() -> server.locateQueue(queueNameTX) != null);

      testPageLimitBytesFailInternal(queueNameTX, protocol, true);
      testPageLimitBytesFailInternal(queueNameNonTX, protocol, false);

   }



   private void testPageLimitBytesFailInternal(String queueName,
                                                 String protocol,
                                                 boolean transacted) throws Exception {
      org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(queueName);
      assertNotNull(serverQueue);

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(transacted, transacted ? Session.SESSION_TRANSACTED : Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);
         MessageProducer producer = session.createProducer(queue);
         connection.start();

         int successfullSends = 0;
         boolean failed = false;

         for (int i = 0; i < 1000; i++) {
            try {
               TextMessage message = session.createTextMessage("hello world " + i);
               message.setIntProperty("i", i);
               producer.send(message);
               if (transacted) {
                  session.commit();
               }
            } catch (Exception e) {
               logger.debug(e.getMessage(), e);
               failed = true;
               break;
            }
            successfullSends++;
         }

         Wait.assertEquals(successfullSends, serverQueue::getMessageCount);
         assertTrue(failed);

         int reads = successfullSends / 2;

         connection.start();
         try (MessageConsumer consumer = session.createConsumer(queue)) {
            for (int i = 0; i < reads; i++) { // we will consume half of the messages
               TextMessage message = (TextMessage) consumer.receive(5000);
               assertNotNull(message);
               assertEquals("hello world " + i, message.getText());
               assertEquals(i, message.getIntProperty("i"));
               if (transacted) {
                  if (i % 100 == 0 && i > 0) {
                     session.commit();
                  }
               }
            }
            if (transacted) {
               session.commit();
            }
         }

         failed = false;

         int originalSuccess = successfullSends;

         Future<Boolean> result = serverQueue.getPagingStore().getCursorProvider().scheduleCleanup();
         assertTrue(result.get(10, TimeUnit.SECONDS));

         for (int i = successfullSends; i < 1000; i++) {
            try {
               TextMessage message = session.createTextMessage("hello world " + i);
               message.setIntProperty("i", i);
               producer.send(message);
               if (transacted) {
                  session.commit();
               }
            } catch (Exception e) {
               logger.debug(e.getMessage(), e);
               failed = true;
               break;
            }
            successfullSends++;
         }

         assertTrue(failed);
         assertTrue(successfullSends > originalSuccess);

         try (MessageConsumer consumer = session.createConsumer(queue)) {
            for (int i = reads; i < successfullSends; i++) {
               TextMessage message = (TextMessage) consumer.receive(5000);
               assertNotNull(message);
               assertEquals("hello world " + i, message.getText());
               assertEquals(i, message.getIntProperty("i"));
               if (transacted) {
                  if (i % 100 == 0 && i > 0) {
                     session.commit();
                  }
               }
            }
            if (transacted) {
               session.commit();
            }
            assertNull(consumer.receiveNoWait());
         }


      }

   }



}