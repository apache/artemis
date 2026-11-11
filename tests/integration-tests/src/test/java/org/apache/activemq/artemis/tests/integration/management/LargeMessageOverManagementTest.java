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
package org.apache.activemq.artemis.tests.integration.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.utils.Base64;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

public class LargeMessageOverManagementTest extends ManagementTestBase {

   private ClientSession session;
   private ServerLocator locator;
   private ClientSessionFactory sf;
   private ActiveMQServer server;

   protected AddressControl createManagementControl(final SimpleString address) throws Exception {
      return ManagementControlHelper.createAddressControl(address, mbeanServer);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      Configuration config = createBasicConfig();


      TransportConfiguration acceptorConfig = createTransportConfiguration(false, true, generateParams(0, false));
      config.addAcceptorConfiguration(acceptorConfig);
      server = createServer(true, config);
      server.setMBeanServer(mbeanServer);
      server.start();

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true);
      sf = createSessionFactory(locator);
      session = sf.createSession(false, true, false);
      session.start();
      addClientSession(session);
   }

   @Test
   public void testSendOverSizeMessageOverQueueControl() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString emptyqueue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address));
      session.createQueue(QueueConfiguration.of(emptyqueue).setAddress(address));

      QueueControl queueControl = createManagementControl(address, queue);

      int bodySize = (int) server.getStorageManager().getMaxRecordSize() + 100;
      byte[] bigData = createBytesData(bodySize);

      queueControl.sendMessage(new HashMap<>(), Message.BYTES_TYPE, Base64.encodeBytes(bigData), true, "myUser", "myPassword");


      ClientConsumer consumer = session.createConsumer(queue);
      ClientMessage message = consumer.receive(1000);
      assertNotNull(message);
      assertEquals(bigData.length, message.getBodySize());
      assertTrue(message.isLargeMessage());



      byte[] bytesRead = new byte[bigData.length];
      message.getBodyBuffer().readBytes(bytesRead);

      for (int i = 0; i < bytesRead.length; i++) {
         assertEquals(bytesRead[i], bigData[i]);
      }


      consumer.close();

      // this is an extra check,
      consumer = session.createConsumer(emptyqueue);
      assertNull(consumer.receiveImmediate());

   }

   @Test
   public void testSendOverSizeMessageOverAddressControl() throws Exception {

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      session.createAddress(address, RoutingType.ANYCAST, false);

      AddressControl addressControl = createManagementControl(address);
      session.createQueue(QueueConfiguration.of(address).setRoutingType(RoutingType.ANYCAST));

      int bodySize = server.getConfiguration().getJournalBufferSize_AIO();
      byte[] bigData = createBytesData(bodySize);
      addressControl.sendMessage(null, Message.BYTES_TYPE, Base64.encodeBytes(bigData), false, null, null);

      ClientConsumer consumer = session.createConsumer(address);
      ClientMessage message = consumer.receive(1000);
      assertNotNull(message);
      assertEquals(bigData.length, message.getBodySize());
      assertTrue(message.isLargeMessage());

      byte[] bytesRead = new byte[bigData.length];
      message.getBodyBuffer().readBytes(bytesRead);

      for (int i = 0; i < bytesRead.length; i++) {
         assertEquals(bytesRead[i], bigData[i]);
      }

   }

   byte[] createBytesData(int nbytes) {
      byte[] result = new byte[nbytes];
      for (int i = 0; i < nbytes; i++) {
         result[i] = RandomUtil.randomByte();
      }
      return result;
   }
}
