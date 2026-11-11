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

import java.nio.ByteBuffer;
import java.util.HashMap;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

/**
 * A PagingOrderTest.
 * <p>
 * PagingTest has a lot of tests already. I decided to create a newer one more specialized on Ordering and counters
 */
public class PagingSyncTest extends ActiveMQTestBase {

   private static final int PAGE_MAX = 100 * 1024;

   private static final int PAGE_SIZE = 10 * 1024;



   static final SimpleString ADDRESS = SimpleString.of("TestQueue");

   @Test
   public void testOrder1() throws Throwable {
      boolean persistentMessages = true;

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      ActiveMQServer server = createServer(true, config, PAGE_SIZE, PAGE_MAX, new HashMap<>());

      server.start();

      final int messageSize = 1024;

      final int numberOfMessages = 500;

      ServerLocator locator = createInVMNonHALocator().setClientFailureCheckPeriod(1000).setConnectionTTL(2000).setReconnectAttempts(0).setBlockOnNonDurableSend(false).setBlockOnDurableSend(false).setBlockOnAcknowledge(false).setConsumerWindowSize(1024 * 1024);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false, false);

      server.addAddressInfo(new AddressInfo(ADDRESS, RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(ADDRESS).setRoutingType(RoutingType.ANYCAST));

      ClientProducer producer = session.createProducer(ADDRESS);

      byte[] body = new byte[messageSize];

      ByteBuffer bb = ByteBuffer.wrap(body);

      for (int j = 1; j <= messageSize; j++) {
         bb.put(getSamplebyte(j));
      }

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage message = session.createMessage(persistentMessages);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         message.putIntProperty(SimpleString.of("id"), i);

         producer.send(message);
      }

      session.commit();

      session.close();
   }

}
