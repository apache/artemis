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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.io.File;
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This test will send large messages in page-mode, DLQ then, expiry then, and they should be received fine
 */
public class ExpiryLargeMessageTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   final SimpleString EXPIRY = SimpleString.of("my-expiry");

   final SimpleString DLQ = SimpleString.of("my-DLQ");

   final SimpleString MY_QUEUE = SimpleString.of("MY-QUEUE");

   final int messageSize = 10 * 1024;

   // it has to be an even number
   final int numberOfMessages = 50;



   @Test
   public void testExpiryMessagesThenDLQ() throws Exception {
      ActiveMQServer server = createServer(true);

      server.getConfiguration().setMessageExpiryScanPeriod(600000);

      AddressSettings setting = new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxDeliveryAttempts(5).setMaxSizeBytes(50 * 1024).setPageSizeBytes(10 * 1024).setExpiryAddress(EXPIRY).setDeadLetterAddress(DLQ).setMaxReadPageBytes(-1).setMaxReadPageMessages(-1);
      server.getAddressSettingsRepository().addMatch(MY_QUEUE.toString(), setting);
      server.getAddressSettingsRepository().addMatch(EXPIRY.toString(), setting);

      server.start();

      server.createQueue(QueueConfiguration.of(EXPIRY).setRoutingType(RoutingType.ANYCAST));

      server.createQueue(QueueConfiguration.of(DLQ).setRoutingType(RoutingType.ANYCAST));

      server.createQueue(QueueConfiguration.of(MY_QUEUE).setRoutingType(RoutingType.ANYCAST));

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(true, true, 0);

      byte[] bufferSample = new byte[messageSize];

      for (int i = 0; i < bufferSample.length; i++) {
         bufferSample[i] = getSamplebyte(i);
      }

      ClientProducer producer = session.createProducer(MY_QUEUE);

      long timeToExpiry = System.currentTimeMillis() + 1000;
      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage message = session.createMessage(true);

         message.putIntProperty("count", i);

         // Send a few regular messages first, then all is just large messages
         if (i % 2 == 0) {
            message.putBooleanProperty("tst-large", false);
            message.getBodyBuffer().writeBytes(bufferSample);
         } else {
            message.putBooleanProperty("tst-large", true);
            message.setBodyInputStream(createFakeLargeStream(messageSize));
         }

         message.setExpiration(timeToExpiry);

         producer.send(message);
      }

      session.close();

      server.stop();
      server.start();

      Queue queueExpiry = server.locateQueue(EXPIRY);
      Queue myQueue = server.locateQueue(MY_QUEUE);

      sf = createSessionFactory(locator);

      Thread.sleep(1500);

      Wait.assertEquals(numberOfMessages, () -> {
         myQueue.expireReferences();
         return getMessageCount(queueExpiry);
      });

      session = sf.createSession(false, false);

      ClientConsumer cons = session.createConsumer(EXPIRY);
      session.start();

      // Consume half of the messages to make sure all the messages are paging (on the second try)
      for (int i = 0; i < numberOfMessages / 2; i++) {
         ClientMessage msg = cons.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();
      }

      session.commit();

      cons.close();

      for (int rep = 0; rep < 6; rep++) {
         cons = session.createConsumer(EXPIRY);
         session.start();

         for (int i = 0; i < numberOfMessages / 2; i++) {
            ClientMessage message = cons.receive(5000);
            assertNotNull(message);

            if (i % 10 == 0) {
               logger.debug("Received {}", i);
            }

            for (int location = 0; location < messageSize; location++) {
               assertEquals(getSamplebyte(location), message.getBodyBuffer().readByte());
            }
            message.acknowledge();
         }

         session.rollback();

         cons.close();

         session.close();
         sf.close();

         if (rep == 0) {
            // restart the server at the first try
            server.stop();
            server.start();
         }

         sf = createSessionFactory(locator);
         session = sf.createSession(false, false);
         session.start();
      }

      cons = session.createConsumer(EXPIRY);
      session.start();
      assertNull(cons.receiveImmediate());

      cons.close();

      session.close();
      sf.close();

      for (int rep = 0; rep < 2; rep++) {
         sf = createSessionFactory(locator);

         session = sf.createSession(false, false);

         cons = session.createConsumer(DLQ);

         session.start();

         for (int i = 0; i < numberOfMessages / 2; i++) {
            ClientMessage message = cons.receive(5000);
            assertNotNull(message);

            if (i % 10 == 0) {
               logger.debug("Received {}", i);
            }

            for (int location = 0; location < messageSize; location++) {
               assertEquals(getSamplebyte(location), message.getBodyBuffer().readByte());
            }
            message.acknowledge();
         }
         if (rep == 0) {
            session.rollback();
            session.close();
            sf.close();
            server.stop();
            server.start();
         }
      }

      session.commit();

      assertNull(cons.receiveImmediate());

      session.close();
      sf.close();
      locator.close();

      validateNoFilesOnLargeDir();
   }


   @Test
   public void testExpiryMessagesAMQP() throws Exception {
      testExpiryMessagesAMQP(false, 300 * 1024);
   }

   @Test
   public void testExpiryMessagesAMQPRestartBeforeExpiry() throws Exception {
      testExpiryMessagesAMQP(true, 300 * 1024);
   }

   // this is just sanity check for the test
   @Test
   public void testExpiryMessagesAMQPRegularMessageStandardMessage() throws Exception {
      testExpiryMessagesAMQP(false, 30);
   }

   // this is just sanity check for the test
   @Test
   public void testExpiryMessagesAMQPRestartBeforeExpiryStandardMessage() throws Exception {
      testExpiryMessagesAMQP(true, 30);
   }

   public void testExpiryMessagesAMQP(boolean restartBefore, int bodySize) throws Exception {
      ActiveMQServer server = createServer(true, true);

      server.getConfiguration().setMessageExpiryScanPeriod(6000);

      AddressSettings setting = new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxDeliveryAttempts(5).setMaxSizeBytes(50 * 1024).setPageSizeBytes(10 * 1024).setExpiryAddress(EXPIRY).setDeadLetterAddress(DLQ).setMaxReadPageMessages(-1).setMaxReadPageBytes(-1);
      server.getAddressSettingsRepository().addMatch(MY_QUEUE.toString(), setting);
      server.getAddressSettingsRepository().addMatch(EXPIRY.toString(), setting);

      server.start();

      server.createQueue(QueueConfiguration.of(EXPIRY).setRoutingType(RoutingType.ANYCAST));

      server.createQueue(QueueConfiguration.of(DLQ).setRoutingType(RoutingType.ANYCAST));

      server.createQueue(QueueConfiguration.of(MY_QUEUE).setRoutingType(RoutingType.ANYCAST));

      ConnectionFactory connectionFactory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:61616");
      Connection connection = connectionFactory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      byte[] bufferSample = new byte[bodySize];

      for (int i = 0; i < bufferSample.length; i++) {
         bufferSample[i] = getSamplebyte(i);
      }

      javax.jms.Queue jmsQueue = session.createQueue(MY_QUEUE.toString());

      MessageProducer producer = session.createProducer(jmsQueue);
      producer.setTimeToLive(300);

      for (int i = 0; i < numberOfMessages; i++) {
         BytesMessage message = session.createBytesMessage();
         message.writeBytes(bufferSample);

         message.setIntProperty("count", i);

         producer.send(message);
      }

      session.close();
      connection.close();

      if (restartBefore) {
         server.stop();
         server.start();
      }

      Queue queueExpiry = server.locateQueue(EXPIRY);
      Queue myQueue = server.locateQueue(MY_QUEUE);

      Wait.assertEquals(numberOfMessages, () -> {
         myQueue.expireReferences();
         return getMessageCount(queueExpiry);
      });

      if (!restartBefore) {
         server.stop();
         server.start();
      }


      // validateNoFilesOnLargeDir(getLargeMessagesDir(), numberOfMessages);

      connection = connectionFactory.createConnection();
      session = connection.createSession(true, Session.SESSION_TRANSACTED);

      MessageConsumer cons = session.createConsumer(session.createQueue(EXPIRY.toString()));
      connection.start();

      // Consume half of the messages to make sure all the messages are paging (on the second try)
      for (int i = 0; i < numberOfMessages; i++) {
         javax.jms.Message msg = cons.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();
      }

      session.commit();

      connection.close();

   }

   /**
    * Tests if the system would still couple with old data where the LargeMessage was linked to its previous copy
    */
   @Test
   public void testCompatilityWithLinks() throws Exception {
      ActiveMQServer server = createServer(true);

      server.getConfiguration().setMessageExpiryScanPeriod(600000);

      AddressSettings setting = new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxDeliveryAttempts(5).setMaxSizeBytes(-1).setPageSizeBytes(10 * 1024).setExpiryAddress(EXPIRY).setDeadLetterAddress(DLQ);
      server.getAddressSettingsRepository().addMatch(MY_QUEUE.toString(), setting);
      server.getAddressSettingsRepository().addMatch(EXPIRY.toString(), setting);

      server.start();

      server.createQueue(QueueConfiguration.of(EXPIRY).setRoutingType(RoutingType.ANYCAST));

      server.createQueue(QueueConfiguration.of(DLQ).setRoutingType(RoutingType.ANYCAST));

      server.createQueue(QueueConfiguration.of(MY_QUEUE).setRoutingType(RoutingType.ANYCAST));

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(true, true, 0);

      byte[] bufferSample = new byte[messageSize];

      for (int i = 0; i < bufferSample.length; i++) {
         bufferSample[i] = getSamplebyte(i);
      }

      ClientProducer producer = session.createProducer(MY_QUEUE);

      long timeToExpiry = System.currentTimeMillis() + 1000;
      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage message = session.createMessage(true);

         message.putIntProperty("count", i);

         // Everything is going to be a large message
         message.putBooleanProperty("tst-large", true);
         message.setBodyInputStream(createFakeLargeStream(messageSize));

         message.setExpiration(timeToExpiry);

         producer.send(message);
      }

      server.stop();
      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(true, true, 0);
      session.start();

      Thread.sleep(1500);

      ClientConsumer cons = session.createConsumer(MY_QUEUE);
      assertNull(cons.receive(1000));

      session.close();

      session = sf.createSession(false, false);

      cons = session.createConsumer(EXPIRY);
      session.start();

      ClientMessage msg = cons.receive(5000);
      assertNotNull(msg);
      msg.acknowledge();
      session.rollback();

      server.stop();

      // rename the file, simulating old behaviour
      long messageID = msg.getMessageID();
      long oldID = msg.getLongProperty(Message.HDR_ORIG_MESSAGE_ID);

      File largeMessagesFileDir = new File(getLargeMessagesDir());
      File oldFile = new File(largeMessagesFileDir, oldID + ".msg");
      File currentFile = new File(largeMessagesFileDir, messageID + ".msg");
      currentFile.renameTo(oldFile);

      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(true, true, 0);
      session.start();

      cons = session.createConsumer(EXPIRY);

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage message = cons.receive(5000);
         assertNotNull(message);

         if (i % 10 == 0) {
            logger.debug("Received {}", i);
         }

         for (int location = 0; location < messageSize; location++) {
            assertEquals(getSamplebyte(location), message.getBodyBuffer().readByte());
         }
         message.acknowledge();
      }

      session.commit();

      session.close();
   }

}
