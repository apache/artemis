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

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.integration.largemessage.LargeMessageTestBase;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class LargeMessageAvoidLargeMessagesTest extends LargeMessageTestBase {

   public LargeMessageAvoidLargeMessagesTest(StoreConfiguration.StoreType storeType) {
      super(storeType);
      isCompressedTest = true;
   }

   @Override
   protected void validateLargeMessageComplete(ActiveMQServer server) throws Exception {
   }

   @Override
   protected boolean isNetty() {
      return false;
   }

   @Override
   protected ServerLocator createFactory(final boolean isNetty) throws Exception {
      return super.createFactory(isNetty).setMinLargeMessageSize(10240).setCompressLargeMessage(true);
   }


   @TestTemplate
   public void testSimpleSendOnAvoid() throws Exception {
      ActiveMQServer server = createServer(true, isNetty());
      server.start();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true));

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      int minLargeSize = locator.getMinLargeMessageSize();

      TestLargeMessageInputStream input = new TestLargeMessageInputStream(minLargeSize);

      ClientMessage clientFile = session.createMessage(true);
      clientFile.setBodyInputStream(input.clone());

      producer.send(clientFile);

      session.start();

      //no file should be in the dir as we send it as regular
      validateNoFilesOnLargeDir();

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      ClientMessage msg1 = consumer.receive(1000);
      assertNotNull(msg1);

      for (int i = 0; i < input.getSize(); i++) {
         byte b = msg1.getBodyBuffer().readByte();
         assertEquals(input.getChar(i), b, "incorrect char ");
      }
      msg1.acknowledge();
      consumer.close();

      session.close();
   }

   //send some messages that can be compressed into regular size.
   @TestTemplate
   public void testSendRegularAfterCompression() throws Exception {
      ActiveMQServer server = createServer(true, isNetty());
      server.start();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true));

      session.createQueue(QueueConfiguration.of(ADDRESS).setAddress(ADDRESS).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(ADDRESS);

      int minLargeSize = locator.getMinLargeMessageSize();

      TestLargeMessageInputStream input = new TestLargeMessageInputStream(minLargeSize);
      adjustLargeCompression(true, input, 1024);

      int num = 1;
      for (int i = 0; i < num; i++) {
         ClientMessage clientFile = session.createMessage(true);
         clientFile.setBodyInputStream(input.clone());

         producer.send(clientFile);
      }

      session.start();

      //no file should be in the dir as we send it as regular
      validateNoFilesOnLargeDir();

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      for (int j = 0; j < num; j++) {
         ClientMessage msg1 = consumer.receive(1000);
         assertNotNull(msg1);

         for (int i = 0; i < input.getSize(); i++) {
            byte b = msg1.getBodyBuffer().readByte();
            assertEquals(input.getChar(i), b, "incorrect char ");
         }
         msg1.acknowledge();
      }

      session.commit();
      consumer.close();

      session.close();
   }

   //send some messages that cannot be compressed into regular messages
   @TestTemplate
   public void testSendLargeAfterUnableToSendRegular() throws Exception {
      ActiveMQServer server = createServer(true, isNetty());
      server.start();

      //reduce the minLargeMessageSize to make the test faster
      locator.setMinLargeMessageSize(5 * 1024);
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(QueueConfiguration.of(ADDRESS).setAddress(ADDRESS).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(ADDRESS);

      int minLargeSize = locator.getMinLargeMessageSize();
      TestLargeMessageInputStream input = new TestLargeMessageInputStream(minLargeSize);
      input.setSize(80 * minLargeSize);
      adjustLargeCompression(false, input, 40 * minLargeSize);

      int num = 10;
      for (int i = 0; i < num; i++) {
         ClientMessage clientFile = session.createMessage(true);
         clientFile.setBodyInputStream(input.clone());

         producer.send(clientFile);
      }

      session.commit();

      session.start();

      //no file should be in the dir as we send it as regular
      validateNoFilesOnLargeDir(server.getConfiguration().getLargeMessagesDirectory(), num);

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      for (int j = 0; j < num; j++) {
         ClientMessage msg1 = consumer.receive(1000);
         assertNotNull(msg1);

         for (int i = 0; i < input.getSize(); i++) {
            byte b = msg1.getBodyBuffer().readByte();
            assertEquals(input.getChar(i), b, "incorrect char");
         }
         msg1.acknowledge();
      }

      session.commit();
      consumer.close();

      session.close();
   }

   @TestTemplate
   public void testMixedCompressionSendReceive() throws Exception {
      ActiveMQServer server = createServer(true, isNetty());
      server.start();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(QueueConfiguration.of(ADDRESS).setAddress(ADDRESS).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(ADDRESS);

      final int minLargeSize = locator.getMinLargeMessageSize();
      TestLargeMessageInputStream regularInput = new TestLargeMessageInputStream(minLargeSize);
      adjustLargeCompression(true, regularInput, 1024);

      TestLargeMessageInputStream largeInput = new TestLargeMessageInputStream(minLargeSize);
      largeInput.setSize(100 * minLargeSize);
      adjustLargeCompression(false, largeInput, 50 * minLargeSize);

      int num = 6;
      for (int i = 0; i < num; i++) {
         ClientMessage clientFile = session.createMessage(true);
         if (i % 2 == 0) {
            clientFile.setBodyInputStream(regularInput.clone());
         } else {
            clientFile.setBodyInputStream(largeInput.clone());
         }

         producer.send(clientFile);
      }

      session.commit();

      session.start();

      //half the messages are sent as large
      validateNoFilesOnLargeDir(server.getConfiguration().getLargeMessagesDirectory(), num / 2);

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      for (int j = 0; j < num; j++) {
         ClientMessage msg1 = consumer.receive(1000);
         assertNotNull(msg1);

         if (j % 2 == 0) {
            for (int i = 0; i < regularInput.getSize(); i++) {
               byte b = msg1.getBodyBuffer().readByte();
               assertEquals(regularInput.getChar(i), b, "incorrect char ");
            }
         } else {
            for (int i = 0; i < largeInput.getSize(); i++) {
               byte b = msg1.getBodyBuffer().readByte();
               assertEquals(largeInput.getChar(i), b, "incorrect char ");
            }
         }
         msg1.acknowledge();
      }

      session.commit();
      consumer.close();

      session.close();
   }

   //this test won't leave any large messages in the large-messages dir
   //because after compression, the messages are regulars at server.
   @TestTemplate
   public void testDLALargeMessage() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ClientSession session = null;

      ActiveMQServer server = createServer(true, isNetty());

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(QueueConfiguration.of(ADDRESS));
      session.createQueue(QueueConfiguration.of(ADDRESS.concat("-2")).setAddress(ADDRESS));

      SimpleString ADDRESS_DLA = ADDRESS.concat("-dla");

      AddressSettings addressSettings = new AddressSettings().setDeadLetterAddress(ADDRESS_DLA).setMaxDeliveryAttempts(1);

      server.getAddressSettingsRepository().addMatch("*", addressSettings);

      session.createQueue(QueueConfiguration.of(ADDRESS_DLA));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS_DLA);

      ClientConsumer consumerRollback = session.createConsumer(ADDRESS);
      ClientMessage msg1 = consumerRollback.receive(1000);
      assertNotNull(msg1);
      msg1.acknowledge();
      session.rollback();
      consumerRollback.close();

      msg1 = consumer.receive(10000);

      assertNotNull(msg1);

      for (int i = 0; i < messageSize; i++) {
         assertEquals(ActiveMQTestBase.getSamplebyte(i), msg1.getBodyBuffer().readByte());
      }

      session.close();
      server.stop();

      server = createServer(true, isNetty());

      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(false, false, false);

      session.start();

      consumer = session.createConsumer(ADDRESS_DLA);

      msg1 = consumer.receive(10000);

      assertNotNull(msg1);

      for (int i = 0; i < messageSize; i++) {
         assertEquals(ActiveMQTestBase.getSamplebyte(i), msg1.getBodyBuffer().readByte());
      }

      msg1.acknowledge();

      session.commit();

      //large message becomes a regular at server.
      validateNoFilesOnLargeDir(server.getConfiguration().getLargeMessagesDirectory(), 0);

      consumer = session.createConsumer(ADDRESS.concat("-2"));

      msg1 = consumer.receive(10000);

      assertNotNull(msg1);

      for (int i = 0; i < messageSize; i++) {
         assertEquals(ActiveMQTestBase.getSamplebyte(i), msg1.getBodyBuffer().readByte());
      }

      msg1.acknowledge();

      session.commit();

      session.close();

      validateNoFilesOnLargeDir();
   }

}
