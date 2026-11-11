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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This will perform cleanup tests on paging while using JMS topics
 */
public class JMSPagingFileDeleteTest extends JMSTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   Topic topic1;

   Connection connection;

   Session session;

   MessageConsumer subscriber1;

   MessageConsumer subscriber2;

   PagingStore pagingStore;

   private static final int MESSAGE_SIZE = 1024;

   private static final int PAGE_SIZE = 10 * 1024;

   private static final int PAGE_MAX = 20 * 1024;

   private static final int RECEIVE_TIMEOUT = 500;

   private static final int MESSAGE_NUM = 50;

   @Override
   protected boolean usePersistence() {
      return true;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      topic1 = createTopic("topic1");

      // Paging Setting
      AddressSettings setting = new AddressSettings().setPageSizeBytes(JMSPagingFileDeleteTest.PAGE_SIZE).setMaxSizeBytes(JMSPagingFileDeleteTest.PAGE_MAX);
      server.getAddressSettingsRepository().addMatch("#", setting);
   }

   @Test
   public void testTopicsWithNonDurableSubscription() throws Exception {
      connection = null;

      try {
         for (int repeat = 0; repeat < 2; repeat++) {
            connection = cf.createConnection();
            connection.setClientID("cid");

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageProducer producer = session.createProducer(topic1);

            subscriber1 = session.createConsumer(topic1);

            // -----------------(Step1) Publish Messages to make Paging Files. --------------------
            logger.debug("---------- Send messages. ----------");
            BytesMessage bytesMessage = session.createBytesMessage();
            bytesMessage.writeBytes(new byte[JMSPagingFileDeleteTest.MESSAGE_SIZE]);
            for (int i = 0; i < JMSPagingFileDeleteTest.MESSAGE_NUM; i++) {
               producer.send(bytesMessage);
            }
            logger.debug("Sent {} messages.", JMSPagingFileDeleteTest.MESSAGE_NUM);

            pagingStore = server.getPagingManager().getPageStore(SimpleString.of("topic1"));
            printPageStoreInfo(pagingStore);

            assertTrue(pagingStore.isPaging());

            // -----------------(Step2) Closing the connection alone should cleanup pages -------
            connection.close();

            // note that if we closed subscriber or session the bug wouldn't happen
            // as they were already deleting the page-subscription properly
            // So, you can't close subscriber1 or session as that would change the test
            //subscriber1.close(); // << you can't call this on this test
            //session.close(); // << can't call this on this test

            Wait.assertFalse(pagingStore::isPaging);
            printPageStoreInfo(pagingStore);
         }

      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }

   @Test
   public void testTopics() throws Exception {
      connection = null;

      try {
         connection = cf.createConnection();
         connection.setClientID("cid");

         session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer producer = session.createProducer(topic1);
         subscriber1 = session.createDurableSubscriber(topic1, "subscriber-1");
         subscriber2 = session.createDurableSubscriber(topic1, "subscriber-2");

         // -----------------(Step1) Publish Messages to make Paging Files. --------------------
         logger.debug("---------- Send messages. ----------");
         BytesMessage bytesMessage = session.createBytesMessage();
         bytesMessage.writeBytes(new byte[JMSPagingFileDeleteTest.MESSAGE_SIZE]);
         for (int i = 0; i < JMSPagingFileDeleteTest.MESSAGE_NUM; i++) {
            producer.send(bytesMessage);
         }
         logger.debug("Sent {} messages.", JMSPagingFileDeleteTest.MESSAGE_NUM);

         pagingStore = server.getPagingManager().getPageStore(SimpleString.of("topic1"));
         printPageStoreInfo(pagingStore);

         assertTrue(pagingStore.isPaging());

         connection.start();

         // -----------------(Step2) Restart the server. --------------------------------------
         stopAndStartServer(); // If try this test without restarting server, please comment out this line;

         // -----------------(Step3) Subscribe to all the messages from the topic.--------------
         logger.debug("---------- Receive all messages. ----------");
         for (int i = 0; i < JMSPagingFileDeleteTest.MESSAGE_NUM; i++) {
            Message message1 = subscriber1.receive(JMSPagingFileDeleteTest.RECEIVE_TIMEOUT);
            assertNotNull(message1);
            Message message2 = subscriber2.receive(JMSPagingFileDeleteTest.RECEIVE_TIMEOUT);
            assertNotNull(message2);
         }

         pagingStore = server.getPagingManager().getPageStore(SimpleString.of("topic1"));
         long timeout = System.currentTimeMillis() + 5000;
         while (timeout > System.currentTimeMillis() && pagingStore.isPaging()) {
            Thread.sleep(100);
         }
         assertFalse(pagingStore.isPaging());

         printPageStoreInfo(pagingStore);

         assertEquals(0, pagingStore.getAddressSize());
         // assertEquals(1, pagingStore.getNumberOfPages()); //I expected number of the page is 1, but It was not.
         assertFalse(pagingStore.isPaging()); // I expected IsPaging is false, but It was true.
         // If the server is not restart, this test pass.

         // -----------------(Step4) Publish a message. the message is stored in the paging file.
         producer = session.createProducer(topic1);
         bytesMessage = session.createBytesMessage();
         bytesMessage.writeBytes(new byte[JMSPagingFileDeleteTest.MESSAGE_SIZE]);
         producer.send(bytesMessage);

         printPageStoreInfo(pagingStore);

         timeout = System.currentTimeMillis() + 10000;

         Wait.assertEquals(1, pagingStore::getNumberOfPages);
      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }

   private void stopAndStartServer() throws Exception {
      logger.debug("---------- Restart server. ----------");
      connection.close();

      jmsServer.stop();

      jmsServer.start();
      jmsServer.activated();
      registerConnectionFactory();

      printPageStoreInfo(pagingStore);
      reconnect();
   }

   private void reconnect() throws Exception {
      connection = cf.createConnection();
      connection.setClientID("cid");
      session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      subscriber1 = session.createDurableSubscriber(topic1, "subscriber-1");
      subscriber2 = session.createDurableSubscriber(topic1, "subscriber-2");
      connection.start();
   }

   private void printPageStoreInfo(PagingStore pagingStore) throws Exception {
      logger.debug("---------- Paging Store Info ----------");
      logger.debug(" CurrentPage = {}", pagingStore.getCurrentPage());
      logger.debug(" FirstPage = {}", pagingStore.getFirstPage());
      logger.debug(" Number of Pages = {}", pagingStore.getNumberOfPages());
      logger.debug(" Address Size = {}", pagingStore.getAddressSize());
      logger.debug(" Is Paging = {}", pagingStore.isPaging());
   }
}
