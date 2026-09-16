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

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@code temporaryAnycastPrefix} and {@code temporaryMulticastPrefix} are implemented at the Core
 * server level (in {@code ServerSessionImpl}) and therefore work for any protocol, exactly like the existing
 * {@code anycastPrefix}/{@code multicastPrefix} settings. This drives the feature directly through the Core client
 * rather than through any single protocol (e.g. STOMP) to prove the behaviour isn't protocol-specific.
 */
public class TemporaryPrefixTest extends ActiveMQTestBase {

   private static final String TEMP_QUEUE_PREFIX = "temp-queue://";
   private static final String TEMP_TOPIC_PREFIX = "temp-topic://";

   private static final String TEST_URI = "tcp://localhost:5446";

   private ActiveMQServer createAndStartServer(String acceptorParams) throws Exception {
      Configuration configuration = createBasicConfig();
      configuration.setAddressQueueScanPeriod(100);
      configuration.clearAcceptorConfigurations();
      configuration.addAcceptorConfiguration("core", TEST_URI + "?protocols=CORE" + acceptorParams);
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(configuration, false));
      server.start();
      return server;
   }

   private ClientSession createSession() throws Exception {
      ServerLocator locator = addServerLocator(ServerLocatorImpl.newLocator(TEST_URI));
      ClientSessionFactory sf = createSessionFactory(locator);
      return addClientSession(sf.createSession(false, true, true));
   }

   @Test
   public void testTemporaryAnycastPrefixForcesTemporaryQueue() throws Exception {
      ActiveMQServer server = createAndStartServer(";temporaryAnycastPrefix=" + TEMP_QUEUE_PREFIX);
      ClientSession session = createSession();
      String address = UUIDGenerator.getInstance().generateStringUUID();

      // client asks for a durable, non-temporary queue; Core must override this because of the temp prefix
      session.createQueue(QueueConfiguration.of(address).setAddress(TEMP_QUEUE_PREFIX + address).setDurable(true).setTemporary(false));

      Queue queue = server.locateQueue(SimpleString.of(address));
      assertNotNull(queue, "the temp prefix should have caused the address/queue to be auto-created");
      assertTrue(queue.isTemporary(), "queue created under temporaryAnycastPrefix must be forced temporary");
      assertFalse(queue.isDurable(), "queue created under temporaryAnycastPrefix must be forced non-durable");
      assertEquals(RoutingType.ANYCAST, queue.getRoutingType());

      session.getSessionFactory().close();

      Wait.assertTrue("temporary queue should be removed once the session/connection closes",
         () -> server.locateQueue(SimpleString.of(address)) == null);
   }

   @Test
   public void testTemporaryMulticastPrefixForcesTemporaryAddress() throws Exception {
      ActiveMQServer server = createAndStartServer(";temporaryMulticastPrefix=" + TEMP_TOPIC_PREFIX);
      ClientSession session = createSession();
      String address = UUIDGenerator.getInstance().generateStringUUID();

      session.createAddress(SimpleString.of(TEMP_TOPIC_PREFIX + address), RoutingType.MULTICAST, false);

      AddressInfo addressInfo = server.getAddressInfo(SimpleString.of(address));
      assertNotNull(addressInfo, "the temp prefix should have caused the address to be auto-created");
      assertTrue(addressInfo.isTemporary(), "address created under temporaryMulticastPrefix must be forced temporary");
      assertTrue(addressInfo.getRoutingTypes().contains(RoutingType.MULTICAST));

      session.getSessionFactory().close();

      Wait.assertTrue("temporary address should be removed once the session/connection closes",
         () -> server.getAddressInfo(SimpleString.of(address)) == null);
   }

   @Test
   public void testNonPrefixedAddressStaysNonTemporary() throws Exception {
      ActiveMQServer server = createAndStartServer(";temporaryAnycastPrefix=" + TEMP_QUEUE_PREFIX);
      ClientSession session = createSession();
      String address = UUIDGenerator.getInstance().generateStringUUID();

      // no temp prefix on this address, so the queue should be created exactly as requested
      session.createQueue(QueueConfiguration.of(address).setAddress(address).setDurable(true));

      Queue queue = server.locateQueue(SimpleString.of(address));
      assertNotNull(queue);
      assertFalse(queue.isTemporary(), "queue created without the temp prefix must not be forced temporary");
      assertTrue(queue.isDurable());

      session.close();
      assertNotNull(server.locateQueue(SimpleString.of(address)), "non-temporary queue must survive session close");
   }

   @Test
   public void testBothTemporaryPrefixesOnSameAcceptor() throws Exception {
      ActiveMQServer server = createAndStartServer(";temporaryAnycastPrefix=" + TEMP_QUEUE_PREFIX + ";temporaryMulticastPrefix=" + TEMP_TOPIC_PREFIX);
      ClientSession session = createSession();
      String queueAddress = UUIDGenerator.getInstance().generateStringUUID();
      String topicAddress = UUIDGenerator.getInstance().generateStringUUID();

      session.createQueue(QueueConfiguration.of(queueAddress).setAddress(TEMP_QUEUE_PREFIX + queueAddress));
      session.createAddress(SimpleString.of(TEMP_TOPIC_PREFIX + topicAddress), RoutingType.MULTICAST, false);

      Queue queue = server.locateQueue(SimpleString.of(queueAddress));
      assertNotNull(queue);
      assertTrue(queue.isTemporary());
      assertEquals(RoutingType.ANYCAST, queue.getRoutingType());

      AddressInfo addressInfo = server.getAddressInfo(SimpleString.of(topicAddress));
      assertNotNull(addressInfo);
      assertTrue(addressInfo.isTemporary());
      assertTrue(addressInfo.getRoutingTypes().contains(RoutingType.MULTICAST));

      session.getSessionFactory().close();

      Wait.assertTrue(() -> server.locateQueue(SimpleString.of(queueAddress)) == null);
      Wait.assertTrue(() -> server.getAddressInfo(SimpleString.of(topicAddress)) == null);
   }

   @Test
   public void testNoPrefixConfiguredMeansNoOverride() throws Exception {
      ActiveMQServer server = createAndStartServer("");
      ClientSession session = createSession();
      String address = UUIDGenerator.getInstance().generateStringUUID();

      session.createQueue(QueueConfiguration.of(address).setAddress(TEMP_QUEUE_PREFIX + address).setDurable(true));

      // with no temporaryAnycastPrefix configured, "temp-queue://" is just a normal (unrecognized) address segment
      Queue queue = server.locateQueue(SimpleString.of(address));
      assertNotNull(queue);
      assertFalse(queue.isTemporary());
      assertTrue(queue.isDurable());

      session.close();
      assertNotNull(server.locateQueue(SimpleString.of(address)));
   }
}
