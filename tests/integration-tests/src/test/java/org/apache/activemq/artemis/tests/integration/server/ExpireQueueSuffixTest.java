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
package org.apache.activemq.artemis.tests.integration.server;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ExpireQueueSuffixTest extends ActiveMQTestBase {

   public final SimpleString queueA = SimpleString.of("queueA");
   public final SimpleString queueB = SimpleString.of("queueB");
   public final SimpleString expiryAddress = SimpleString.of("myExpiry");

   public final SimpleString expirySuffix = SimpleString.of(".expSuffix");
   public final long EXPIRY_DELAY = 10L;

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(true, true);
      server.getConfiguration().setAddressQueueScanPeriod(50L).setMessageExpiryScanPeriod(50L);

      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateExpiryResources(true).setExpiryAddress(expiryAddress).setExpiryDelay(EXPIRY_DELAY).setExpiryQueueSuffix(expirySuffix));

      server.start();

      server.createQueue(QueueConfiguration.of(queueA).setRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queueB).setRoutingType(RoutingType.ANYCAST));
   }

   @Test
   public void testAutoCreationOfExpiryResources() throws Exception {
      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

      long sendA = 7;
      long sendB = 11;

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(queueA.toString()));
         producer.setTimeToLive(100);

         for (int i = 0; i < sendA; i++) {
            producer.send(session.createTextMessage("queueA"));
         }
         session.commit();

         producer = session.createProducer(session.createQueue(queueB.toString()));
         producer.setTimeToLive(100);
         for (int i = 0; i < sendB; i++) {
            producer.send(session.createTextMessage("queueB"));
         }
         session.commit();
      }

      Wait.waitFor(() -> server.locateQueue(expiryAddress.toString(), "EXP." + queueA + expirySuffix) != null, 5000);
      Queue expA = server.locateQueue(expiryAddress.toString(), "EXP." + queueA + expirySuffix);
      assertNotNull(expA);

      Wait.waitFor(() -> server.locateQueue(expiryAddress.toString(), "EXP." + queueB + expirySuffix) != null, 5000);
      Queue expB = server.locateQueue(expiryAddress.toString(), "EXP." + queueB + expirySuffix);
      assertNotNull(expB);

      Wait.assertEquals(sendA, expA::getMessageCount, 5000, 100);
      Wait.assertEquals(sendB, expB::getMessageCount, 5000, 100);
   }
}

