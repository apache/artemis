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
package org.apache.activemq.artemis.tests.integration.divert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class PersistentDivertTest extends ActiveMQTestBase {

   final int minLargeMessageSize = ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE * 2;

   @Test
   public void testPersistentDivert() throws Exception {
      doTestPersistentDivert(false);
   }

   @Test
   public void testPersistentDiverLargeMessage() throws Exception {
      doTestPersistentDivert(true);
   }

   public void doTestPersistentDivert(final boolean largeMessage) throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress1 = "forwardAddress1";

      final String forwardAddress2 = "forwardAddress2";

      final String forwardAddress3 = "forwardAddress3";

      DivertConfiguration divertConf1 = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress1);

      DivertConfiguration divertConf2 = new DivertConfiguration().setName("divert2").setRoutingName("divert2").setAddress(testAddress).setForwardingAddress(forwardAddress2);

      DivertConfiguration divertConf3 = new DivertConfiguration().setName("divert3").setRoutingName("divert3").setAddress(testAddress).setForwardingAddress(forwardAddress3);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf1).addDivertConfiguration(divertConf2).addDivertConfiguration(divertConf3);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config));

      server.start();

      ServerLocator locator = createInVMNonHALocator().setBlockOnAcknowledge(true).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(true, true, 0);

      final SimpleString queueName1 = SimpleString.of("queue1");

      final SimpleString queueName2 = SimpleString.of("queue2");

      final SimpleString queueName3 = SimpleString.of("queue3");

      final SimpleString queueName4 = SimpleString.of("queue4");

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress1));

      session.createQueue(QueueConfiguration.of(queueName2).setAddress(forwardAddress2));

      session.createQueue(QueueConfiguration.of(queueName3).setAddress(forwardAddress3));

      session.createQueue(QueueConfiguration.of(queueName4).setAddress(testAddress));

      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      ClientConsumer consumer2 = session.createConsumer(queueName2);

      ClientConsumer consumer3 = session.createConsumer(queueName3);

      ClientConsumer consumer4 = session.createConsumer(queueName4);

      final int numMessages = 10;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(true);

         if (largeMessage) {
            message.setBodyInputStream(ActiveMQTestBase.createFakeLargeStream(minLargeMessageSize));
         }

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(5000);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         if (largeMessage) {
            checkLargeMessage(message);
         }

         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer2.receive(5000);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         if (largeMessage) {
            checkLargeMessage(message);
         }

         message.acknowledge();
      }

      assertNull(consumer2.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer3.receive(5000);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         if (largeMessage) {
            checkLargeMessage(message);
         }

         message.acknowledge();
      }

      assertNull(consumer3.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer4.receive(5000);

         assertNotNull(message);

         assertEquals(i, message.getObjectProperty(propKey));

         if (largeMessage) {
            checkLargeMessage(message);
         }

         message.acknowledge();
      }

      assertNull(consumer4.receiveImmediate());
   }

   private void checkLargeMessage(final ClientMessage message) {
      for (int j = 0; j < minLargeMessageSize; j++) {
         assertEquals(ActiveMQTestBase.getSamplebyte(j), message.getBodyBuffer().readByte());
      }
   }

   @Test
   public void testPersistentDivertRestartBeforeConsume() throws Exception {
      doTestPersistentDivertRestartBeforeConsume(false);
   }

   @Test
   public void testPersistentDivertRestartBeforeConsumeLargeMessage() throws Exception {
      doTestPersistentDivertRestartBeforeConsume(true);
   }

   public void doTestPersistentDivertRestartBeforeConsume(final boolean largeMessage) throws Exception {
      final String testAddress = "testAddress";

      final String forwardAddress1 = "forwardAddress1";

      final String forwardAddress2 = "forwardAddress2";

      final String forwardAddress3 = "forwardAddress3";

      DivertConfiguration divertConf1 = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress1);

      DivertConfiguration divertConf2 = new DivertConfiguration().setName("divert2").setRoutingName("divert2").setAddress(testAddress).setForwardingAddress(forwardAddress2);

      DivertConfiguration divertConf3 = new DivertConfiguration().setName("divert3").setRoutingName("divert3").setAddress(testAddress).setForwardingAddress(forwardAddress3);

      Configuration config = createDefaultInVMConfig().addDivertConfiguration(divertConf1).addDivertConfiguration(divertConf2).addDivertConfiguration(divertConf3);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config));

      server.start();

      ServerLocator locator = createInVMNonHALocator().setBlockOnAcknowledge(true).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(true, true, 0);

      final SimpleString queueName1 = SimpleString.of("queue1");

      final SimpleString queueName2 = SimpleString.of("queue2");

      final SimpleString queueName3 = SimpleString.of("queue3");

      final SimpleString queueName4 = SimpleString.of("queue4");

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress1));

      session.createQueue(QueueConfiguration.of(queueName2).setAddress(forwardAddress2));

      session.createQueue(QueueConfiguration.of(queueName3).setAddress(forwardAddress3));

      session.createQueue(QueueConfiguration.of(queueName4).setAddress(testAddress));

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      final int numMessages = 10;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(true);

         message.putIntProperty(propKey, i);

         if (largeMessage) {
            message.setBodyInputStream(ActiveMQTestBase.createFakeLargeStream(minLargeMessageSize));
         }

         producer.send(message);
      }

      session.close();

      sf.close();

      server.stop();

      waitForServerToStop(server);

      server.start();

      ServerLocator locator2 = createInVMNonHALocator().setBlockOnDurableSend(true);

      sf = createSessionFactory(locator2);
      session = sf.createSession(false, true, true);

      session.start();

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      ClientConsumer consumer2 = session.createConsumer(queueName2);

      ClientConsumer consumer3 = session.createConsumer(queueName3);

      ClientConsumer consumer4 = session.createConsumer(queueName4);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(5000);

         assertNotNull(message);

         if (largeMessage) {
            checkLargeMessage(message);
         }

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer2.receive(5000);

         assertNotNull(message);

         if (largeMessage) {
            checkLargeMessage(message);
         }

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer2.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer3.receive(5000);

         assertNotNull(message);

         if (largeMessage) {
            checkLargeMessage(message);
         }

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer3.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer4.receive(5000);

         assertNotNull(message);

         if (largeMessage) {
            checkLargeMessage(message);
         }

         assertEquals(i, message.getObjectProperty(propKey));

         message.acknowledge();
      }

      assertNull(consumer4.receiveImmediate());

      session.close();

      sf.close();

      server.stop();

      waitForServerToStop(server);

      server.start();

      ServerLocator locator3 = createInVMNonHALocator().setBlockOnDurableSend(true);

      sf = createSessionFactory(locator3);

      session = sf.createSession(false, true, true);

      consumer1 = session.createConsumer(queueName1);

      consumer2 = session.createConsumer(queueName2);

      consumer3 = session.createConsumer(queueName3);

      consumer4 = session.createConsumer(queueName4);

      assertNull(consumer1.receiveImmediate());

      assertNull(consumer2.receiveImmediate());

      assertNull(consumer3.receiveImmediate());

      assertNull(consumer4.receiveImmediate());
   }

}
