/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.jms.multiprotocol;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JMSMessageExpiryTest extends MultiprotocolJMSClientTestSupport {

   protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final long EXPIRY_DELAY = 10_000_000L;

   @Test
   @Timeout(30)
   public void testCoreMessageExpiryDelay() throws Exception {
      testExpiry(CoreConnection, DelayType.NORMAL, false);
   }

   @Test
   @Timeout(30)
   public void testAmqpMessageExpiryDelay() throws Exception {
      testExpiry(AMQPConnection, DelayType.NORMAL, false);
   }

   @Test
   @Timeout(30)
   public void testOpenWireMessageExpiryDelay() throws Exception {
      testExpiry(OpenWireConnection, DelayType.NORMAL, false);
   }

   @Test
   @Timeout(30)
   public void testCoreLargeMessageExpiryDelay() throws Exception {
      testExpiry(CoreConnection, DelayType.NORMAL, false, true, false);
   }

   @Test
   @Timeout(30)
   public void testAmqpLargeMessageExpiryDelay() throws Exception {
      testExpiry(AMQPConnection, DelayType.NORMAL, false, true, false);
   }

   @Test
   @Timeout(30)
   public void testOpenWireLargeMessageExpiryDelay() throws Exception {
      testExpiry(OpenWireConnection, DelayType.NORMAL, false, true, false);
   }

   @Test
   @Timeout(30)
   public void testCoreLargeMessageExpiryDelayWithBrokerRestart() throws Exception {
      testExpiry(CoreConnection, DelayType.NORMAL, false, true, true);
   }

   @Test
   @Timeout(30)
   public void testAmqpLargeMessageExpiryDelayWithBrokerRestart() throws Exception {
      testExpiry(AMQPConnection, DelayType.NORMAL, false, true, true);
   }

   @Test
   @Timeout(30)
   public void testOpenWireLargeMessageExpiryDelayWithBrokerRestart() throws Exception {
      testExpiry(OpenWireConnection, DelayType.NORMAL, false, true, true);
   }

   @Test
   @Timeout(30)
   public void testCoreMessageExpiryDelayWithBrokerRestart() throws Exception {
      testExpiry(CoreConnection, DelayType.NORMAL, false, false, true);
   }

   @Test
   @Timeout(30)
   public void testAmqpMessageExpiryDelayWithBrokerRestart() throws Exception {
      testExpiry(AMQPConnection, DelayType.NORMAL, false, false, true);
   }

   @Test
   @Timeout(30)
   public void testOpenWireMessageExpiryDelayWithBrokerRestart() throws Exception {
      testExpiry(OpenWireConnection, DelayType.NORMAL, false, false, true);
   }

   @Test
   @Timeout(30)
   public void testCoreMaxExpiryDelayNoExpiration() throws Exception {
      testExpiry(CoreConnection, DelayType.MAX, false);
   }

   @Test
   @Timeout(30)
   public void testAmqpMaxExpiryDelayNoExpiration() throws Exception {
      testExpiry(AMQPConnection, DelayType.MAX, false);
   }

   @Test
   @Timeout(30)
   public void testOpenWireMaxExpiryDelayNoExpiration() throws Exception {
      testExpiry(OpenWireConnection, DelayType.MAX, false);
   }

   @Test
   @Timeout(30)
   public void testCoreMinExpiryDelayNoExpiration() throws Exception {
      testExpiry(CoreConnection, DelayType.MIN, false);
   }

   @Test
   @Timeout(30)
   public void testAmqpMinExpiryDelayNoExpiration() throws Exception {
      testExpiry(AMQPConnection, DelayType.MIN, false);
   }

   @Test
   @Timeout(30)
   public void testOpenWireMinExpiryDelayNoExpiration() throws Exception {
      testExpiry(OpenWireConnection, DelayType.MIN, false);
   }

   @Test
   @Timeout(30)
   public void testCoreMaxExpiryDelayWithExpiration() throws Exception {
      testExpiry(CoreConnection, DelayType.MAX, true);
   }

   @Test
   @Timeout(30)
   public void testAmqpMaxExpiryDelayWithExpiration() throws Exception {
      testExpiry(AMQPConnection, DelayType.MAX, true);
   }

   @Test
   @Timeout(30)
   public void testOpenWireMaxExpiryDelayWithExpiration() throws Exception {
      testExpiry(OpenWireConnection, DelayType.MAX, true);
   }

   @Test
   @Timeout(30)
   public void testCoreMinExpiryDelayWithExpiration() throws Exception {
      testExpiry(CoreConnection, DelayType.MIN, true);
   }

   @Test
   @Timeout(30)
   public void testAmqpMinExpiryDelayWithExpiration() throws Exception {
      testExpiry(AMQPConnection, DelayType.MIN, true);
   }

   @Test
   @Timeout(30)
   public void testOpenWireMinExpiryDelayWithExpiration() throws Exception {
      testExpiry(OpenWireConnection, DelayType.MIN, true);
   }

   @Test
   @Timeout(30)
   public void testCoreMessageNoExpiry() throws Exception {
      testExpiry(CoreConnection, DelayType.NEVER, true);
   }

   @Test
   @Timeout(30)
   public void testAmqpMessageNoExpiry() throws Exception {
      testExpiry(AMQPConnection, DelayType.NEVER, true);
   }

   @Test
   @Timeout(30)
   public void testOpenWireMessageNoExpiry() throws Exception {
      testExpiry(OpenWireConnection, DelayType.NEVER, true);
   }

   private void testExpiry(ConnectionSupplier supplier, DelayType delayType, boolean setTimeToLive) throws Exception {
      testExpiry(supplier, delayType, setTimeToLive, false, false);
   }

   private void testExpiry(ConnectionSupplier supplier, DelayType delayType, boolean setTimeToLive, boolean useLargeMessage, boolean restartBroker) throws Exception {
      AddressSettings addressSettings = new AddressSettings();
      if (delayType == DelayType.NORMAL) {
         addressSettings.setExpiryDelay(EXPIRY_DELAY);
      } else if (delayType == DelayType.MIN) {
         addressSettings.setMinExpiryDelay(EXPIRY_DELAY);
      } else if (delayType == DelayType.MAX) {
         addressSettings.setMaxExpiryDelay(EXPIRY_DELAY);
      } else if (delayType == DelayType.NEVER) {
         addressSettings.setNoExpiry(true);
      }
      server.getAddressSettingsRepository().addMatch(getQueueName(), addressSettings);

      Connection producerConnection = supplier.createConnection();
      Session session = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue q = session.createQueue(getQueueName());
      MessageProducer producer = session.createProducer(q);
      if (setTimeToLive) {
         if (delayType == DelayType.MIN) {
            producer.setTimeToLive(EXPIRY_DELAY / 2);
         } else if (delayType == DelayType.MAX) {
            producer.setTimeToLive(EXPIRY_DELAY * 2);
         } else if (delayType == DelayType.NEVER) {
            producer.setTimeToLive(EXPIRY_DELAY);
         }
      }
      BytesMessage m = session.createBytesMessage();
      if (useLargeMessage) {
         m.writeBytes(RandomUtil.randomBytes(server.getConfiguration().getJournalBufferSize_NIO() * 2));
      }
      long start = System.currentTimeMillis();
      producer.send(m);
      producerConnection.close();
      if (useLargeMessage) {
         validateNoFilesOnLargeDir(getLargeMessagesDir(), 1);
      }
      if (restartBroker) {
         server.stop();
         server.start();
      }
      Connection consumerConnection = supplier.createConnection();
      session = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      q = session.createQueue(getQueueName());
      MessageConsumer consumer = session.createConsumer(q);
      consumerConnection.start();
      m = (BytesMessage) consumer.receive(1500);
      long stop = System.currentTimeMillis();
      assertNotNull(m);
      consumerConnection.close();
      if (delayType == DelayType.NEVER) {
         assertEquals(0, m.getJMSExpiration());
      } else {
         long duration = stop - start;
         long delayOnMessage = m.getJMSExpiration() - stop;
         assertTrue(delayOnMessage >= (EXPIRY_DELAY - duration));
         assertTrue(delayOnMessage <= EXPIRY_DELAY);
      }
      if (useLargeMessage) {
         validateNoFilesOnLargeDir();
      }
   }

   enum DelayType {
      NORMAL, MIN, MAX, NEVER;
   }
}
