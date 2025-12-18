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
package org.apache.activemq.artemis.tests.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;

import org.apache.activemq.artemis.api.core.ActiveMQDuplicateIdException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(ParameterizedTestExtension.class)
public class DuplicateDetectionTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Parameters(name = "persistentCache={0}")
   public static Collection<Object[]> parameters() {
      return Arrays.asList(new Object[][] {
         {true}, {false}
      });
   }

   @Parameter(index = 0)
   public boolean persistCache;



   private ActiveMQServer server;

   private final SimpleString propKey = SimpleString.of("propkey");

   private final int cacheSize = 10;

   @TestTemplate
   public void testSimpleDuplicateDetecion() throws Exception {
      ClientSession session = sf.createSession(false, true, true);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 0);
      producer.send(message);
      ClientMessage message2 = consumer.receive(1000);
      assertEquals(0, message2.getObjectProperty(propKey));

      message = createMessage(session, 1);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      message2 = consumer.receive(1000);
      assertEquals(1, message2.getObjectProperty(propKey));

      message = createMessage(session, 2);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      message2 = consumer.receiveImmediate();
      assertNull(message2);

      message = createMessage(session, 3);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      message2 = consumer.receiveImmediate();
      assertNull(message2);

      // Now try with a different id

      message = createMessage(session, 4);
      SimpleString dupID2 = SimpleString.of("hijklmnop");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      message2 = consumer.receive(1000);
      assertEquals(4, message2.getObjectProperty(propKey));

      message = createMessage(session, 5);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      message2 = consumer.receiveImmediate();
      assertNull(message2);

      message = createMessage(session, 6);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      message2 = consumer.receiveImmediate();
      assertNull(message2);
   }

   @TestTemplate
   public void testDisabledDuplicateDetection() throws Exception {
      server.stop();

      config = createDefaultInVMConfig().setIDCacheSize(0);

      server = createServer(config);

      server.start();

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 0);
      producer.send(message);
      ClientMessage message2 = consumer.receive(1000);
      assertEquals(0, message2.getObjectProperty(propKey));

      message = createMessage(session, 1);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      message2 = consumer.receive(1000);
      assertEquals(1, message2.getObjectProperty(propKey));

      message = createMessage(session, 2);
      SimpleString dupID1 = SimpleString.of("abcdefg1");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID1.getData());
      producer.send(message);
      message2 = consumer.receive(1000);
      assertEquals(2, message2.getObjectProperty(propKey));

      message = createMessage(session, 3);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      message2 = consumer.receiveImmediate();
      assertEquals(3, message2.getObjectProperty(propKey));

      message = createMessage(session, 4);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      message2 = consumer.receiveImmediate();
      assertEquals(4, message2.getObjectProperty(propKey));

      message = createMessage(session, 5);
      SimpleString dupID2 = SimpleString.of("hijklmnop");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      message2 = consumer.receive(1000);
      assertEquals(5, message2.getObjectProperty(propKey));

      message = createMessage(session, 6);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      message2 = consumer.receiveImmediate();
      assertEquals(6, message2.getObjectProperty(propKey));

      message = createMessage(session, 7);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      message2 = consumer.receiveImmediate();
      assertEquals(7, message2.getObjectProperty(propKey));
   }

   @TestTemplate
   public void testDuplicateIDCacheMemoryRetentionForNonTemporaryQueues() throws Exception {
      testDuplicateIDCacheMemoryRetention(false);
   }

   @TestTemplate
   public void testDuplicateIDCacheMemoryRetentionForTemporaryQueues() throws Exception {
      testDuplicateIDCacheMemoryRetention(true);
   }

   @TestTemplate
   public void testDuplicateIDCacheJournalRetentionForNonTemporaryQueues() throws Exception {
      testDuplicateIDCacheMemoryRetention(false);

      server.stop();

      waitForServerToStop(server);

      server.start();

      assertEquals(0, ((PostOfficeImpl) server.getPostOffice()).getDuplicateIDCaches().size());
   }

   @TestTemplate
   public void testDuplicateIDCacheJournalRetentionForTemporaryQueues() throws Exception {
      testDuplicateIDCacheMemoryRetention(true);

      server.stop();

      waitForServerToStop(server);

      server.start();

      assertEquals(0, ((PostOfficeImpl) server.getPostOffice()).getDuplicateIDCaches().size());
   }

   public void testDuplicateIDCacheMemoryRetention(boolean temporary) throws Exception {
      final int TEST_SIZE = 100;

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true);

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      session.start();

      assertEquals(0, ((PostOfficeImpl) server.getPostOffice()).getDuplicateIDCaches().size());

      final SimpleString addressName = SimpleString.of("DuplicateDetectionTestAddress");

      for (int i = 0; i < TEST_SIZE; i++) {
         final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue_" + i);

         session.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setDurable(!temporary).setTemporary(temporary));

         ClientProducer producer = session.createProducer(addressName);

         ClientConsumer consumer = session.createConsumer(queueName);

         ClientMessage message = createMessage(session, 1);
         SimpleString dupID = SimpleString.of("abcdefg");
         message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
         producer.send(message);
         ClientMessage message2 = consumer.receive(1000);
         assertEquals(1, message2.getObjectProperty(propKey));

         message = createMessage(session, 2);
         message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
         producer.send(message);
         message2 = consumer.receiveImmediate();
         assertNull(message2);

         message = createMessage(session, 3);
         message.putBytesProperty(Message.HDR_BRIDGE_DUPLICATE_ID, dupID.getData());
         producer.send(message);
         message2 = consumer.receive(1000);
         assertEquals(3, message2.getObjectProperty(propKey));

         message = createMessage(session, 4);
         message.putBytesProperty(Message.HDR_BRIDGE_DUPLICATE_ID, dupID.getData());
         producer.send(message);
         message2 = consumer.receiveImmediate();
         assertNull(message2);

         producer.close();
         consumer.close();

         // there will be 2 ID caches, one for messages using "_AMQ_DUPL_ID" and one for "_AMQ_BRIDGE_DUP"
         assertEquals(2, ((PostOfficeImpl) server.getPostOffice()).getDuplicateIDCaches().size());
         session.deleteQueue(queueName);
         assertEquals(0, ((PostOfficeImpl) server.getPostOffice()).getDuplicateIDCaches().size());
      }

      assertEquals(0, ((PostOfficeImpl) server.getPostOffice()).getDuplicateIDCaches().size());
   }

   // It is important to test the shrink with this rule
   // because we could have this after crashes
   // we would eventually have a higher number of caches while we couldn't have time to clear previous ones
   @TestTemplate
   public void testShrinkCache() throws Exception {
      assumeTrue(persistCache, "This test would restart the server");
      server.stop();
      server.getConfiguration().setIDCacheSize(150);
      server.start();

      final int TEST_SIZE = 200;

      ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      locator.setBlockOnNonDurableSend(true);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");
      session.createQueue(QueueConfiguration.of(queueName));

      ClientProducer producer = session.createProducer(queueName);

      for (int i = 0; i < TEST_SIZE; i++) {
         ClientMessage message = session.createMessage(true);
         message.putStringProperty(Message.HDR_DUPLICATE_DETECTION_ID, SimpleString.of("DUPL-" + i));
         producer.send(message);
      }
      session.commit();

      sf.close();
      session.close();
      locator.close();

      server.stop();

      server.getConfiguration().setIDCacheSize(100);

      server.start();

      locator = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      locator.setBlockOnNonDurableSend(true);
      sf = createSessionFactory(locator);
      session = sf.createSession(false, false, false);
      session.start();

      producer = session.createProducer(queueName);

      // will send the last 50 again
      for (int i = TEST_SIZE - 50; i < TEST_SIZE; i++) {
         ClientMessage message = session.createMessage(true);
         message.putStringProperty(Message.HDR_DUPLICATE_DETECTION_ID, SimpleString.of("DUPL-" + i));
         producer.send(message);
      }

      try {
         session.commit();
         fail("Exception expected");
      } catch (ActiveMQException expected) {

      }

   }

   @TestTemplate
   public void testSimpleDuplicateDetectionWithString() throws Exception {
      ClientSession session = sf.createSession(false, true, true);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 0);
      producer.send(message);
      ClientMessage message2 = consumer.receive(1000);
      assertEquals(0, message2.getObjectProperty(propKey));

      message = createMessage(session, 1);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putStringProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID);
      producer.send(message);
      message2 = consumer.receive(1000);
      assertEquals(1, message2.getObjectProperty(propKey));

      message = createMessage(session, 2);
      message.putStringProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID);
      producer.send(message);
      message2 = consumer.receiveImmediate();
      assertNull(message2);

      message = createMessage(session, 3);
      message.putStringProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID);
      producer.send(message);
      message2 = consumer.receiveImmediate();
      assertNull(message2);

      // Now try with a different id

      message = createMessage(session, 4);
      SimpleString dupID2 = SimpleString.of("hijklmnop");
      message.putStringProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2);
      producer.send(message);
      message2 = consumer.receive(1000);
      assertEquals(4, message2.getObjectProperty(propKey));

      message = createMessage(session, 5);
      message.putStringProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2);
      producer.send(message);
      message2 = consumer.receiveImmediate();
      assertNull(message2);

      message = createMessage(session, 6);
      message.putStringProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID);
      producer.send(message);
      message2 = consumer.receiveImmediate();
      assertNull(message2);
   }

   @TestTemplate
   public void testCacheSize() throws Exception {
      ClientSession session = sf.createSession(false, true, true);

      session.start();

      final SimpleString queueName1 = SimpleString.of("DuplicateDetectionTestQueue1");

      final SimpleString queueName2 = SimpleString.of("DuplicateDetectionTestQueue2");

      final SimpleString queueName3 = SimpleString.of("DuplicateDetectionTestQueue3");

      session.createQueue(QueueConfiguration.of(queueName1).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName2).setDurable(false));

      session.createQueue(QueueConfiguration.of(queueName3).setDurable(false));

      ClientProducer producer1 = session.createProducer(queueName1);
      ClientConsumer consumer1 = session.createConsumer(queueName1);

      ClientProducer producer2 = session.createProducer(queueName2);
      ClientConsumer consumer2 = session.createConsumer(queueName2);

      ClientProducer producer3 = session.createProducer(queueName3);
      ClientConsumer consumer3 = session.createConsumer(queueName3);

      for (int i = 0; i < cacheSize; i++) {
         SimpleString dupID = SimpleString.of("dupID" + i);

         ClientMessage message = createMessage(session, i);

         message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());

         producer1.send(message);
         producer2.send(message);
         producer3.send(message);
      }

      for (int i = 0; i < cacheSize; i++) {
         ClientMessage message = consumer1.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getObjectProperty(propKey));
         message = consumer2.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getObjectProperty(propKey));
         message = consumer3.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getObjectProperty(propKey));
      }

      logger.debug("Now sending more");
      for (int i = 0; i < cacheSize; i++) {
         SimpleString dupID = SimpleString.of("dupID" + i);

         ClientMessage message = createMessage(session, i);

         message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());

         producer1.send(message);
         producer2.send(message);
         producer3.send(message);
      }

      ClientMessage message = consumer1.receiveImmediate();
      assertNull(message);
      message = consumer2.receiveImmediate();
      assertNull(message);
      message = consumer3.receiveImmediate();
      assertNull(message);

      for (int i = 0; i < cacheSize; i++) {
         SimpleString dupID = SimpleString.of("dupID2-" + i);

         message = createMessage(session, i);

         message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());

         producer1.send(message);
         producer2.send(message);
         producer3.send(message);
      }

      for (int i = 0; i < cacheSize; i++) {
         message = consumer1.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getObjectProperty(propKey));
         message = consumer2.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getObjectProperty(propKey));
         message = consumer3.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getObjectProperty(propKey));
      }

      for (int i = 0; i < cacheSize; i++) {
         SimpleString dupID = SimpleString.of("dupID2-" + i);

         message = createMessage(session, i);

         message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());

         producer1.send(message);
         producer2.send(message);
         producer3.send(message);
      }

      message = consumer1.receiveImmediate();
      assertNull(message);
      message = consumer2.receiveImmediate();
      assertNull(message);
      message = consumer3.receiveImmediate();
      assertNull(message);

      // Should be able to send the first lot again now - since the second lot pushed the
      // first lot out of the cache
      for (int i = 0; i < cacheSize; i++) {
         SimpleString dupID = SimpleString.of("dupID" + i);

         message = createMessage(session, i);

         message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());

         producer1.send(message);
         producer2.send(message);
         producer3.send(message);
      }

      for (int i = 0; i < cacheSize; i++) {
         message = consumer1.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getObjectProperty(propKey));
         message = consumer2.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getObjectProperty(propKey));
         message = consumer3.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getObjectProperty(propKey));
      }
   }

   @TestTemplate
   public void testDuplicateIDCacheSizeForAddressSpecificSetting() throws Exception {
      server.stop();

      final int addressIdCacheSize = 1;
      final int globalIdCacheSize = 2;
      final SimpleString dupIDOne = SimpleString.of("1");
      final SimpleString dupIDTwo = SimpleString.of("2");
      final SimpleString globalSettingsQueueName = SimpleString.of("GlobalIdCacheSizeQueue");
      final SimpleString addressSettingsQueueName = SimpleString.of("AddressIdCacheSizeQueue");
      AddressSettings testAddressSettings = new AddressSettings();
      testAddressSettings.setIDCacheSize(addressIdCacheSize);

      config = createDefaultInVMConfig().setIDCacheSize(globalIdCacheSize);
      config.getAddressSettings().put(addressSettingsQueueName.toString(), testAddressSettings);

      server = createServer(config);
      server.start();
      sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);
      session.start();

      session.createQueue(QueueConfiguration.of(globalSettingsQueueName).setDurable(false));
      session.createQueue(QueueConfiguration.of(addressSettingsQueueName).setDurable(false));

      ClientProducer addressSettingsProducer = session.createProducer(addressSettingsQueueName);
      ClientConsumer addressSettingsConsumer = session.createConsumer(addressSettingsQueueName);
      ClientProducer globalSettingsProducer = session.createProducer(globalSettingsQueueName);
      ClientConsumer globalSettingsConsumer = session.createConsumer(globalSettingsQueueName);


      ClientMessage globalSettingsMessage1 = createMessage(session, 1);
      globalSettingsMessage1.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupIDOne.getData());
      globalSettingsProducer.send(globalSettingsMessage1);

      ClientMessage globalSettingsMessage2 = createMessage(session, 2);
      globalSettingsMessage2.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupIDTwo.getData());
      globalSettingsProducer.send(globalSettingsMessage2);

      // globalSettingsMessage3 will be ignored by the server - dupIDOne was only 2 messages ago
      ClientMessage globalSettingsMessage3 = createMessage(session, 3);
      globalSettingsMessage3.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupIDOne.getData());
      globalSettingsProducer.send(globalSettingsMessage3);

      globalSettingsMessage1 = globalSettingsConsumer.receive(1000);
      assertEquals(1, globalSettingsMessage1.getObjectProperty(propKey));

      globalSettingsMessage2 = globalSettingsConsumer.receive(1000);
      assertEquals(2, globalSettingsMessage2.getObjectProperty(propKey));

      // globalSettingsMessage3 will be ignored by the server because dupIDOne is duplicate
      globalSettingsMessage3 = globalSettingsConsumer.receiveImmediate();
      assertNull(globalSettingsMessage3);

      ClientMessage addressSettingsMessage1 = createMessage(session, 1);
      addressSettingsMessage1.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupIDOne.getData());
      addressSettingsProducer.send(addressSettingsMessage1);

      ClientMessage addressSettingsMessage2 = createMessage(session, 2);
      addressSettingsMessage2.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupIDTwo.getData());
      addressSettingsProducer.send(addressSettingsMessage2);

      // addressSettingsMessage3 will not be ignored because the id-cache-size is only 1
      // and dupOne was 2 messages ago
      ClientMessage addressSettingsMessage3 = createMessage(session, 3);
      addressSettingsMessage3.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupIDOne.getData());
      addressSettingsProducer.send(addressSettingsMessage3);

      addressSettingsMessage1 = addressSettingsConsumer.receive(1000);
      assertEquals(1, addressSettingsMessage1.getObjectProperty(propKey));

      addressSettingsMessage2 = addressSettingsConsumer.receive(1000);
      assertEquals(2, addressSettingsMessage2.getObjectProperty(propKey));

      // addressSettingsMessage3 will be acked successfully by addressSettingsConsumer
      // because the id-cache-size is only 1 (instead of the global size of 2)
      addressSettingsMessage3 = addressSettingsConsumer.receive(1000);
      assertEquals(3, addressSettingsMessage3.getObjectProperty(propKey));

      session.commit();
   }
   @TestTemplate
   public void testTransactedDuplicateDetection1() throws Exception {
      ClientSession session = sf.createSession(false, false, false);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      ClientProducer producer = session.createProducer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.close();

      session = sf.createSession(false, false, false);

      session.start();

      producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      // Should be able to resend it and not get rejected since transaction didn't commit

      message = createMessage(session, 1);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.commit();

      message = consumer.receive(250);
      assertEquals(1, message.getObjectProperty(propKey));

      message = consumer.receiveImmediate();
      assertNull(message);
   }

   @TestTemplate
   public void testTransactedDuplicateDetection2() throws Exception {
      ClientSession session = sf.createSession(false, false, false);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.rollback();

      // Should be able to resend it and not get rejected since transaction didn't commit

      message = createMessage(session, 1);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.commit();

      message = consumer.receive(250);
      assertEquals(1, message.getObjectProperty(propKey));

      message = consumer.receiveImmediate();
      assertNull(message);
   }

   @TestTemplate
   public void testTransactedDuplicateDetection3() throws Exception {
      ClientSession session = sf.createSession(false, false, false);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID1 = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID1.getData());
      producer.send(message);

      message = createMessage(session, 1);
      SimpleString dupID2 = SimpleString.of("hijklmno");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);

      session.commit();

      // These next two should get rejected

      message = createMessage(session, 2);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID1.getData());
      producer.send(message);

      message = createMessage(session, 3);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);

      try {
         session.commit();
      } catch (Exception e) {
         session.rollback();
      }

      message = consumer.receive(250);
      assertEquals(0, message.getObjectProperty(propKey));

      message = consumer.receive(250);
      assertEquals(1, message.getObjectProperty(propKey));

      message = consumer.receiveImmediate();
      assertNull(message);
   }

   @TestTemplate
   public void testRollbackThenSend() throws Exception {
      ClientSession session = sf.createSession(false, false, false);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID1 = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID1.getData());
      message.putStringProperty("key", dupID1.toString());
      producer.send(message);

      session.rollback();

      message = createMessage(session, 0);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID1.getData());
      message.putStringProperty("key", dupID1.toString());
      producer.send(message);

      session.commit();

      message = consumer.receive(5000);
      assertNotNull(message);
      assertEquals(dupID1.toString(), message.getStringProperty("key"));
   }

   /*
    * Entire transaction should be rejected on duplicate detection
    * Even if not all entries have dupl id header
    */
   @TestTemplate
   public void testEntireTransactionRejected() throws Exception {
      ClientSession session = sf.createSession(false, false, false);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      final SimpleString queue2 = SimpleString.of("queue2");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      session.createQueue(QueueConfiguration.of(queue2).setDurable(false));

      ClientProducer producer = session.createProducer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      ClientMessage message2 = createMessage(session, 0);
      ClientProducer producer2 = session.createProducer(queue2);
      producer2.send(message2);

      session.commit();

      session.close();

      session = sf.createSession(false, false, false);

      session.start();

      ClientConsumer consumer2 = session.createConsumer(queue2);

      producer = session.createProducer(queueName);

      message = createMessage(session, 1);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      message = createMessage(session, 2);
      producer.send(message);

      message = createMessage(session, 3);
      producer.send(message);

      message = createMessage(session, 4);
      producer.send(message);

      message = consumer2.receive(5000);
      assertNotNull(message);
      message.acknowledge();

      try {
         session.commit();
      } catch (ActiveMQDuplicateIdException die) {
         session.rollback();
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      ClientConsumer consumer = session.createConsumer(queueName);

      message = consumer.receive(250);
      assertEquals(0, message.getObjectProperty(propKey));

      message = consumer.receiveImmediate();
      assertNull(message);

      message = consumer2.receive(5000);
      assertNotNull(message);

      message.acknowledge();

      session.commit();
   }

   @TestTemplate
   public void testXADuplicateDetection1() throws Exception {
      ClientSession session = sf.createSession(true, false, false);

      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid, XAResource.TMNOFLAGS);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      ClientProducer producer = session.createProducer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.end(xid, XAResource.TMSUCCESS);

      session.close();

      Xid xid2 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session = sf.createSession(true, false, false);

      session.start(xid2, XAResource.TMNOFLAGS);

      session.start();

      producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      // Should be able to resend it and not get rejected since transaction didn't commit

      message = createMessage(session, 1);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.end(xid2, XAResource.TMSUCCESS);

      session.prepare(xid2);

      session.commit(xid2, false);

      Xid xid3 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid3, XAResource.TMNOFLAGS);

      message = consumer.receive(250);
      assertEquals(1, message.getObjectProperty(propKey));

      message = consumer.receiveImmediate();
      assertNull(message);

      logger.debug("ending session");
      session.end(xid3, XAResource.TMSUCCESS);

      logger.debug("preparing session");
      session.prepare(xid3);

      logger.debug("committing session");
      session.commit(xid3, false);
   }

   @TestTemplate
   public void testXADuplicateDetection2() throws Exception {
      ClientSession session = sf.createSession(true, false, false);

      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid, XAResource.TMNOFLAGS);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      ClientProducer producer = session.createProducer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.end(xid, XAResource.TMSUCCESS);

      session.rollback(xid);

      session.close();

      Xid xid2 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session = sf.createSession(true, false, false);

      session.start(xid2, XAResource.TMNOFLAGS);

      session.start();

      producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      // Should be able to resend it and not get rejected since transaction didn't commit

      message = createMessage(session, 1);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.end(xid2, XAResource.TMSUCCESS);

      session.prepare(xid2);

      session.commit(xid2, false);

      Xid xid3 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid3, XAResource.TMNOFLAGS);

      message = consumer.receive(250);
      assertEquals(1, message.getObjectProperty(propKey));

      message = consumer.receiveImmediate();
      assertNull(message);

      logger.debug("ending session");
      session.end(xid3, XAResource.TMSUCCESS);

      logger.debug("preparing session");
      session.prepare(xid3);

      logger.debug("committing session");
      session.commit(xid3, false);
   }

   @TestTemplate
   public void testXADuplicateDetection3() throws Exception {
      ClientSession session = sf.createSession(true, false, false);

      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid, XAResource.TMNOFLAGS);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      ClientProducer producer = session.createProducer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.end(xid, XAResource.TMSUCCESS);

      session.prepare(xid);

      session.close();

      Xid xid2 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session = sf.createSession(true, false, false);

      session.start(xid2, XAResource.TMNOFLAGS);

      session.start();

      producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      // Should NOT be able to resend it

      message = createMessage(session, 1);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.end(xid2, XAResource.TMSUCCESS);

      session.prepare(xid2);

      session.commit(xid2, false);

      Xid xid3 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid3, XAResource.TMNOFLAGS);

      consumer.receive(250);

      message = consumer.receiveImmediate();
      assertNull(message);

      logger.debug("ending session");
      session.end(xid3, XAResource.TMSUCCESS);

      logger.debug("preparing session");
      session.prepare(xid3);

      logger.debug("committing session");
      session.commit(xid3, false);
   }

   @TestTemplate
   public void testXADuplicateDetectionPrepareAndRollback() throws Exception {
      ClientSession session = sf.createSession(true, false, false);

      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid, XAResource.TMNOFLAGS);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      ClientProducer producer = session.createProducer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.end(xid, XAResource.TMSUCCESS);

      session.prepare(xid);

      session.rollback(xid);

      session.close();

      Xid xid2 = new XidImpl("xa2".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session = sf.createSession(true, false, false);

      session.start(xid2, XAResource.TMNOFLAGS);

      session.start();

      producer = session.createProducer(queueName);

      producer.send(message);

      session.end(xid2, XAResource.TMSUCCESS);

      session.prepare(xid2);

      session.commit(xid2, false);

      session.close();

      session = sf.createSession(false, false, false);

      session.start();

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage msgRec = consumer.receive(5000);
      assertNotNull(msgRec);
      msgRec.acknowledge();

      session.commit();
   }

   @TestTemplate
   public void testXADuplicateDetectionPrepareAndRollbackStopServer() throws Exception {
      ClientSession session = sf.createSession(true, false, false);

      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid, XAResource.TMNOFLAGS);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName));

      ClientProducer producer = session.createProducer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.end(xid, XAResource.TMSUCCESS);

      session.prepare(xid);

      session.close();

      server.stop();

      waitForServerToStop(server);

      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(true, false, false);

      session.start(xid, XAResource.TMJOIN);

      session.end(xid, XAResource.TMSUCCESS);

      session.rollback(xid);

      session.close();

      Xid xid2 = new XidImpl("xa2".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session = sf.createSession(true, false, false);

      session.start(xid2, XAResource.TMNOFLAGS);

      session.start();

      producer = session.createProducer(queueName);

      producer.send(message);

      session.end(xid2, XAResource.TMSUCCESS);

      session.prepare(xid2);

      session.commit(xid2, false);

      session.close();

      session = sf.createSession(false, false, false);

      session.start();

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage msgRec = consumer.receive(5000);
      assertNotNull(msgRec);
      msgRec.acknowledge();

      session.commit();
   }

   @TestTemplate
   public void testXADuplicateDetection4() throws Exception {
      ClientSession session = sf.createSession(true, false, false);

      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid, XAResource.TMNOFLAGS);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      ClientProducer producer = session.createProducer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.end(xid, XAResource.TMSUCCESS);

      session.prepare(xid);

      session.commit(xid, false);

      session.close();

      Xid xid2 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session = sf.createSession(true, false, false);

      session.start(xid2, XAResource.TMNOFLAGS);

      session.start();

      producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      // Should NOT be able to resend it

      message = createMessage(session, 1);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.end(xid2, XAResource.TMSUCCESS);

      try {
         session.prepare(xid2);
         fail("Should throw an exception here!");
      } catch (XAException expected) {
         assertTrue(expected.getCause().toString().contains("DUPLICATE_ID_REJECTED"));
      }

      session.rollback(xid2);

      Xid xid3 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid3, XAResource.TMNOFLAGS);

      consumer.receive(250);

      message = consumer.receiveImmediate();
      assertNull(message);

      logger.debug("ending session");
      session.end(xid3, XAResource.TMSUCCESS);

      logger.debug("preparing session");
      session.prepare(xid3);

      logger.debug("committing session");
      session.commit(xid3, false);
   }

   private ClientMessage createMessage(final ClientSession session, final int i) {
      ClientMessage message = session.createMessage(false);

      message.putIntProperty(propKey, i);

      return message;
   }

   @TestTemplate
   public void testDuplicateCacheCleanupWhenAddressRemoved() throws Exception {
      final SimpleString addressName = SimpleString.of("DuplicateDetectionTestQueue");

      // send a message to create a cache
      sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);
      session.createAddress(addressName, RoutingType.MULTICAST, false);
      ClientProducer producer = session.createProducer(addressName);
      ClientMessage message = createMessage(session, 1);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, RandomUtil.randomBytes(10));
      producer.send(message);
      session.close();
      sf.close();

      assertNotNull(((PostOfficeImpl) server.getPostOffice()).getDuplicateIDCaches().get(addressName));

      server.removeAddressInfo(addressName, null);

      assertNull(((PostOfficeImpl) server.getPostOffice()).getDuplicateIDCaches().get(addressName));

      server.stop();

      waitForServerToStop(server);

      server.start();

      assertNull(((PostOfficeImpl) server.getPostOffice()).getDuplicateIDCaches().get(addressName));
   }

   @TestTemplate
   public void testDuplicateCachePersisted() throws Exception {
      server.stop();

      config = createDefaultInVMConfig().setIDCacheSize(cacheSize);

      server = createServer(config);

      server.start();

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(true));

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 1);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      ClientMessage message2 = consumer.receive(1000);
      assertEquals(1, message2.getObjectProperty(propKey));

      message = createMessage(session, 2);
      SimpleString dupID2 = SimpleString.of("hijklmnopqr");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      message2 = consumer.receive(1000);
      assertEquals(2, message2.getObjectProperty(propKey));

      session.close();

      sf.close();

      server.stop();

      waitForServerToStop(server);

      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(false, true, true);

      session.start();

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      message = createMessage(session, 1);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      message2 = consumer.receiveImmediate();
      assertNull(message2);

      message = createMessage(session, 2);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      message2 = consumer.receiveImmediate();
      assertNull(message2);
   }

   @TestTemplate
   public void testDuplicateCachePersisted2() throws Exception {
      server.stop();

      final int theCacheSize = 5;

      config = createDefaultInVMConfig().setIDCacheSize(theCacheSize);

      server = createServer(config);

      server.start();

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(true));

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      for (int i = 0; i < theCacheSize; i++) {
         ClientMessage message = createMessage(session, i);
         SimpleString dupID = SimpleString.of("abcdefg" + i);
         message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
         producer.send(message);
         ClientMessage message2 = consumer.receive(1000);
         assertEquals(i, message2.getObjectProperty(propKey));
      }

      session.close();

      sf.close();

      server.stop();

      waitForServerToStop(server);

      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(false, true, true);

      session.start();

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      for (int i = 0; i < theCacheSize; i++) {
         ClientMessage message = createMessage(session, i);
         SimpleString dupID = SimpleString.of("abcdefg" + i);
         message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
         producer.send(message);
         ClientMessage message2 = consumer.receiveImmediate();
         assertNull(message2);
      }
   }

   @TestTemplate
   public void testNoPersist() throws Exception {
      server.stop();

      config = createDefaultInVMConfig().setIDCacheSize(cacheSize).setPersistIDCache(false);

      server = createServer(config);

      server.start();

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 1);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      ClientMessage message2 = consumer.receive(1000);
      assertEquals(1, message2.getObjectProperty(propKey));

      message = createMessage(session, 2);
      SimpleString dupID2 = SimpleString.of("hijklmnopqr");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      message2 = consumer.receive(1000);
      assertEquals(2, message2.getObjectProperty(propKey));

      session.close();

      sf.close();

      server.stop();

      waitForServerToStop(server);

      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(false, true, true);

      session.start();

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      message = createMessage(session, 1);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      message2 = consumer.receive(200);
      assertEquals(1, message2.getObjectProperty(propKey));

      message = createMessage(session, 2);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      message2 = consumer.receive(200);
      assertEquals(2, message2.getObjectProperty(propKey));
   }

   @TestTemplate
   public void testNoPersistTransactional() throws Exception {
      server.stop();

      config = createDefaultInVMConfig().setIDCacheSize(cacheSize).setPersistIDCache(false);

      server = createServer(config);

      server.start();

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 1);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      session.commit();
      ClientMessage message2 = consumer.receive(1000);
      assertEquals(1, message2.getObjectProperty(propKey));

      message = createMessage(session, 2);
      SimpleString dupID2 = SimpleString.of("hijklmnopqr");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      session.commit();
      message2 = consumer.receive(1000);
      assertEquals(2, message2.getObjectProperty(propKey));

      session.close();

      sf.close();

      server.stop();

      waitForServerToStop(server);

      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(false, false, false);

      session.start();

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      message = createMessage(session, 1);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      session.commit();
      message2 = consumer.receive(200);
      assertEquals(1, message2.getObjectProperty(propKey));

      message = createMessage(session, 2);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      session.commit();
      message2 = consumer.receive(200);
      assertEquals(2, message2.getObjectProperty(propKey));
   }

   @TestTemplate
   public void testPersistTransactional() throws Exception {
      assumeTrue(persistCache, "This test would restart the server");
      ClientSession session = sf.createSession(false, false, false);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(true));

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 1);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      session.commit();
      ClientMessage message2 = consumer.receive(1000);
      message2.acknowledge();
      session.commit();
      assertEquals(1, message2.getObjectProperty(propKey));

      message = createMessage(session, 2);
      SimpleString dupID2 = SimpleString.of("hijklmnopqr");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      session.commit();
      message2 = consumer.receive(1000);
      message2.acknowledge();
      session.commit();
      assertEquals(2, message2.getObjectProperty(propKey));

      session.close();

      sf.close();

      server.stop();

      waitForServerToStop(server);

      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(false, false, false);

      session.start();

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      message = createMessage(session, 1);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      try {
         session.commit();
      } catch (ActiveMQDuplicateIdException die) {
         session.rollback();
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      message2 = consumer.receiveImmediate();
      assertNull(message2);

      message = createMessage(session, 2);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);

      try {
         session.commit();
      } catch (ActiveMQDuplicateIdException die) {
         session.rollback();
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      message2 = consumer.receiveImmediate();
      assertNull(message2);
   }

   @TestTemplate
   public void testNoPersistXA1() throws Exception {
      server.stop();

      config = createDefaultInVMConfig().setIDCacheSize(cacheSize).setPersistIDCache(false);

      server = createServer(config);

      server.start();

      sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(true, false, false);

      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid, XAResource.TMNOFLAGS);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 1);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      message = createMessage(session, 2);
      SimpleString dupID2 = SimpleString.of("hijklmnopqr");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);

      session.end(xid, XAResource.TMSUCCESS);
      session.prepare(xid);
      session.commit(xid, false);

      session.close();

      sf.close();

      server.stop();

      waitForServerToStop(server);

      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(true, false, false);

      Xid xid2 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid2, XAResource.TMNOFLAGS);

      session.start();

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      message = createMessage(session, 1);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      message = createMessage(session, 2);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);

      session.end(xid2, XAResource.TMSUCCESS);
      session.prepare(xid2);
      session.commit(xid2, false);

      Xid xid3 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid3, XAResource.TMNOFLAGS);

      ClientMessage message2 = consumer.receive(200);
      assertEquals(1, message2.getObjectProperty(propKey));

      message2 = consumer.receive(200);
      assertEquals(2, message2.getObjectProperty(propKey));
   }

   @TestTemplate
   public void testNoPersistXA2() throws Exception {
      ClientSession session = sf.createSession(true, false, false);

      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid, XAResource.TMNOFLAGS);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      ClientProducer producer = session.createProducer(queueName);

      ClientMessage message = createMessage(session, 1);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      message = createMessage(session, 2);
      SimpleString dupID2 = SimpleString.of("hijklmnopqr");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);

      session.end(xid, XAResource.TMSUCCESS);

      session.close();

      sf.close();

      server.stop();

      waitForServerToStop(server);

      server.start();

      sf = createSessionFactory(locator);

      session = addClientSession(sf.createSession(true, false, false));

      Xid xid2 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid2, XAResource.TMNOFLAGS);

      session.start();

      session.createQueue(QueueConfiguration.of(queueName).setDurable(false));

      producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      message = createMessage(session, 1);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      message = createMessage(session, 2);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);

      session.end(xid2, XAResource.TMSUCCESS);
      session.prepare(xid2);
      session.commit(xid2, false);

      Xid xid3 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid3, XAResource.TMNOFLAGS);

      ClientMessage message2 = consumer.receive(200);
      assertEquals(1, message2.getObjectProperty(propKey));

      message2 = consumer.receive(200);
      assertEquals(2, message2.getObjectProperty(propKey));
   }

   @TestTemplate
   public void testPersistXA1() throws Exception {
      assumeTrue(persistCache, "This test would restart the server");

      ClientSession session = addClientSession(sf.createSession(true, false, false));

      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid, XAResource.TMNOFLAGS);

      session.start();

      final SimpleString queueName = SimpleString.of("DuplicateDetectionTestQueue");

      session.createQueue(QueueConfiguration.of(queueName).setDurable(true));

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 1);
      SimpleString dupID = SimpleString.of("abcdefg");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      message = createMessage(session, 2);
      SimpleString dupID2 = SimpleString.of("hijklmnopqr");
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);

      session.end(xid, XAResource.TMSUCCESS);
      session.prepare(xid);
      session.commit(xid, false);

      session.close();

      sf.close();

      server.stop();

      waitForServerToStop(server);

      server.start();

      sf = createSessionFactory(locator);

      session = addClientSession(sf.createSession(true, false, false));

      Xid xid2 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid2, XAResource.TMNOFLAGS);

      session.start();

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      message = createMessage(session, 1);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      message = createMessage(session, 2);
      message.putBytesProperty(Message.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);

      session.end(xid2, XAResource.TMSUCCESS);
      try {
         session.prepare(xid2);
         fail("Should throw an exception here!");
      } catch (XAException expected) {
      }

      session.rollback(xid2);

      Xid xid3 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid3, XAResource.TMNOFLAGS);

      ClientMessage message2 = consumer.receiveImmediate();
      assertNull(message2);

      message2 = consumer.receiveImmediate();
      assertNull(message2);
   }

   private Configuration config;
   ServerLocator locator;
   ClientSessionFactory sf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      config = createDefaultInVMConfig().setIDCacheSize(cacheSize).setPersistIDCache(persistCache);

      server = createServer(true, config);

      server.start();

      locator = createInVMNonHALocator();

      sf = createSessionFactory(locator);
   }
}
