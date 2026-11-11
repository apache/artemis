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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenWireLargeMessageTest extends BasicOpenWireTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public OpenWireLargeMessageTest() {
      super();
   }

   public SimpleString lmAddress = SimpleString.of("LargeMessageAddress");
   public SimpleString lmDropAddress = SimpleString.of("LargeMessageDropAddress");

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      this.realStore = true;
      super.setUp();
      server.createQueue(QueueConfiguration.of(lmAddress).setRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(lmDropAddress).setRoutingType(RoutingType.ANYCAST));
   }

   @Override
   protected void configureAddressSettings(Map<String, AddressSettings> addressSettingsMap) {
      addressSettingsMap.put("#", new AddressSettings().setAutoCreateQueues(false).setAutoCreateAddresses(false).setDeadLetterAddress(SimpleString.of("ActiveMQ.DLQ")).setAutoCreateAddresses(true));
      addressSettingsMap.put(lmDropAddress.toString(),
                             new AddressSettings()
                                .setMaxSizeBytes(100 * 1024)
                                .setAddressFullMessagePolicy(AddressFullMessagePolicy.DROP)
                                .setMaxSizeMessages(2)
                                .setMessageCounterHistoryDayLimit(10)
                                .setRedeliveryDelay(0)
                                .setMaxDeliveryAttempts(0));
   }

   @Test
   public void testSendReceiveLargeMessageRestart() throws Exception {
      internalSendReceiveLargeMessage(factory, true);
      internalSendReceiveLargeMessage(CFUtil.createConnectionFactory("openwire", "tcp://localhost:61618"), true);
   }

   @Test
   public void testSendReceiveLargeMessage() throws Exception {
      internalSendReceiveLargeMessage(factory, false);
      internalSendReceiveLargeMessage(CFUtil.createConnectionFactory("openwire", "tcp://localhost:61618"), false);
   }

   private void internalSendReceiveLargeMessage(ConnectionFactory factory, boolean restart) throws Exception {
      // Create 1MB Message
      String largeString = RandomUtil.randomAlphaNumericString(1024 * 1024);

      try (Connection connection = factory.createConnection()) {
         connection.start();

         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(lmAddress.toString());
         MessageProducer producer = session.createProducer(queue);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         TextMessage message = session.createTextMessage(largeString);
         producer.send(message);
      }

      if (restart) {
         server.stop();
         server.start();
      }

      try (Connection connection = factory.createConnection()) {
         connection.start();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(lmAddress.toString());


         MessageConsumer consumer = session.createConsumer(queue);
         TextMessage m = (TextMessage) consumer.receive(5000);
         assertEquals(largeString, m.getText());
      }
   }

   @Test
   public void testFastLargeMessageProducerDropOnPaging() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         // Create 200K Message
         int size = 200 * 1024;

         final byte[] bytes = new byte[size];

         try (Connection connection = factory.createConnection()) {
            connection.start();

            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
               Queue queue = session.createQueue(lmDropAddress.toString());
               try (MessageProducer producer = session.createProducer(queue)) {
                  producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

                  bytes[0] = 1;

                  BytesMessage message = session.createBytesMessage();
                  message.writeBytes(bytes);

                  final PagingStore pageStore = server.getPagingManager().getPageStore(lmDropAddress);
                  while (!pageStore.isPaging()) {
                     producer.send(message);
                  }
                  for (int i = 0; i < 10; i++) {
                     producer.send(message);
                  }
                  final long messageCount = server.locateQueue(lmDropAddress).getMessageCount();
                  assertTrue(messageCount > 0, "The queue cannot be empty");
                  try (MessageConsumer messageConsumer = session.createConsumer(queue)) {
                     for (long m = 0; m < messageCount; m++) {
                        if (messageConsumer.receive(2000) == null) {
                           fail("The messages are not finished yet");
                        }
                     }
                  }
               }
            }
         }
         server.stop();
         assertFalse(loggerHandler.findTrace("NullPointerException"));
         assertFalse(loggerHandler.findText("It was not possible to delete message"));
      }
   }


   @Test
   public void testSendReceiveLargeMessageTX() throws Exception {
      int NUMBER_OF_MESSAGES = 10;
      int TX_SIZE = 5;

      ExecutorService executorService = Executors.newFixedThreadPool(1);
      runAfter(executorService::shutdownNow);

      AtomicInteger errors = new AtomicInteger(0);
      CountDownLatch latch = new CountDownLatch(1);

      // Create 1MB Message
      String largeString = RandomUtil.randomAlphaNumericString(1024 * 1024);

      executorService.execute(() -> {

         try (Connection connection = factory.createConnection()) {
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue queue = session.createQueue(lmAddress.toString());
            MessageConsumer consumer = session.createConsumer(queue);
            for (int received = 0; received < NUMBER_OF_MESSAGES; received++) {
               Message m = consumer.receive(5000);
               assertNotNull(m);
               if (m instanceof TextMessage message) {
                  assertEquals(largeString, message.getText());
               }
               if (received > 0 && received % TX_SIZE == 0) {
                  logger.info("Received {} messages", received);
                  session.commit();
               }
            }
            session.commit();
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
            errors.incrementAndGet();
         } finally {
            latch.countDown();
         }

      });


      try (Connection connection = factory.createConnection()) {
         connection.start();

         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(lmAddress.toString());
         MessageProducer producer = session.createProducer(queue);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         for (int sent = 0; sent < NUMBER_OF_MESSAGES; sent++) {
            Message message;
            if (sent % 2 == 0) {
               message = session.createTextMessage(largeString);
            } else {
               BytesMessage bytesMessage = session.createBytesMessage();
               bytesMessage.writeBytes(largeString.getBytes(StandardCharsets.UTF_8));
               message = bytesMessage;
            }
            producer.send(message);
            if (sent > 0 && sent % TX_SIZE == 0) {
               logger.info("Sent {} messages", sent);
               session.commit();
            }
         }
         session.commit();
      }

      latch.await(1, TimeUnit.MINUTES);
      assertEquals(0, errors.get());
   }


   @Override
   protected void extraServerConfig(Configuration serverConfig) {
      try {
         // to validate the server would still work without MaxPackeSize configured
         serverConfig.addAcceptorConfiguration("openwire", "tcp://0.0.0.0:61618?OPENWIRE;openwireMaxPacketSize=10 * 1024");
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

}
