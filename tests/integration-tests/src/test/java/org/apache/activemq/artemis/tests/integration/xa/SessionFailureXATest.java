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
package org.apache.activemq.artemis.tests.integration.xa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class SessionFailureXATest extends ActiveMQTestBase {

   private final Map<String, AddressSettings> addressSettings = new HashMap<>();

   private ActiveMQServer messagingService;

   private ClientSession clientSession;

   private ClientSessionFactory sessionFactory;

   private Configuration configuration;

   private final SimpleString atestq = SimpleString.of("BasicXaTestq");

   private ServerLocator locator;

   private StoreConfiguration.StoreType storeType;

   public SessionFailureXATest(StoreConfiguration.StoreType storeType) {
      this.storeType = storeType;
   }

   @Parameters(name = "storeType={0}")
   public static Collection<Object[]> data() {
      Object[][] params = new Object[][]{{StoreConfiguration.StoreType.FILE}};
      return Arrays.asList(params);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      addressSettings.clear();

      if (storeType == StoreConfiguration.StoreType.DATABASE) {
         configuration = createDefaultJDBCConfig(true);
      } else {
         configuration = createDefaultNettyConfig();
      }

      messagingService = createServer(true, configuration, -1, -1, addressSettings);

      // start the server
      messagingService.start();

      locator = createInVMNonHALocator();
      locator.setAckBatchSize(0);
      sessionFactory = createSessionFactory(locator);

      clientSession = addClientSession(sessionFactory.createSession(true, false, false));

      clientSession.createQueue(QueueConfiguration.of(atestq));
   }

   @TestTemplate
   public void testFailureWithXAEnd() throws Exception {
      testFailure(true, false);
   }

   @TestTemplate
   public void testFailureWithoutXAEnd() throws Exception {
      testFailure(false, false);
   }

   @TestTemplate
   public void testFailureWithXAPrepare() throws Exception {
      testFailure(true, true);
   }

   public void testFailure(boolean xaEnd, boolean xaPrepare) throws Exception {

      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      try {
         ClientProducer clientProducer = clientSession2.createProducer(atestq);
         ClientMessage m1 = createTextMessage(clientSession2, "m1");
         ClientMessage m2 = createTextMessage(clientSession2, "m2");
         ClientMessage m3 = createTextMessage(clientSession2, "m3");
         ClientMessage m4 = createTextMessage(clientSession2, "m4");
         clientProducer.send(m1);
         clientProducer.send(m2);
         clientProducer.send(m3);
         clientProducer.send(m4);
      } finally {
         clientSession2.close();
      }

      Xid xid = newXID();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.setTransactionTimeout((int) TimeUnit.MINUTES.toMillis(10));
      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(atestq);
      ClientMessage m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m4", m.getBodyBuffer().readString());
      if (xaEnd) {
         // We are validating both cases, where xaEnd succeeded and didn't succeed
         // so this tests is parameterized to validate both cases.
         clientSession.end(xid, XAResource.TMSUCCESS);

         if (xaPrepare) {
            clientSession.prepare(xid);
         }
      }

      Wait.assertEquals(1, () -> messagingService.getSessions().size());

      for (ServerSession serverSession : messagingService.getSessions()) {
         serverSession.getRemotingConnection().fail(new ActiveMQException("fail this"));
         serverSession.getRemotingConnection().disconnect(false);
      }

      Wait.assertEquals(0, () -> messagingService.getSessions().size());

      if (xaPrepare) {
         Wait.assertEquals(1, messagingService.getResourceManager()::size);
      } else {
         Wait.assertEquals(0, messagingService.getResourceManager()::size);
      }

      locator = createInVMNonHALocator();
      sessionFactory = createSessionFactory(locator);
      clientSession = addClientSession(sessionFactory.createSession(true, false, false));

      Wait.assertEquals(1, () -> messagingService.getSessions().size());

      xid = newXID();

      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.setTransactionTimeout((int) TimeUnit.MINUTES.toMillis(10));
      clientSession.start();
      clientConsumer = clientSession.createConsumer(atestq);

      Set<String> bodies = new HashSet<>();
      m = clientConsumer.receive(1000);
      if (xaPrepare) {
         assertNull(m);
      } else {
         assertNotNull(m);
         m.acknowledge();
         assertOrTrack(xaEnd, m, bodies, "m1");
         m = clientConsumer.receive(1000);
         assertNotNull(m);
         m.acknowledge();
         assertOrTrack(xaEnd, m, bodies, "m2");
         m = clientConsumer.receive(1000);
         assertNotNull(m);
         m.acknowledge();
         assertOrTrack(xaEnd, m, bodies, "m3");
         m = clientConsumer.receive(1000);
         assertNotNull(m);
         m.acknowledge();
         assertOrTrack(xaEnd, m, bodies, "m4");

         if (!xaEnd) {
            // order is not guaranteed b/c the m4 async ack may not have been processed when there is no sync end call
            assertEquals(4, bodies.size(), "got all bodies");
         }
      }
   }

   private void assertOrTrack(boolean xaEnd, ClientMessage m, Set<String> bodies, String expected) {
      final String body = m.getBodyBuffer().readString();
      if (xaEnd) {
         assertEquals(expected, body);
      } else {
         bodies.add(body);
      }
   }
}
