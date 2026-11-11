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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQObjectClosedException;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SessionCreateProducerTest extends ActiveMQTestBase {

   private ServerLocator locator;
   private ClientSessionInternal clientSession;
   private ClientSessionFactory cf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = createInVMNonHALocator();
      ActiveMQServer service = createServer(false);
      service.start();
      locator.setProducerMaxRate(99).setBlockOnNonDurableSend(true).setBlockOnNonDurableSend(true);
      cf = createSessionFactory(locator);
      clientSession = (ClientSessionInternal) addClientSession(cf.createSession(false, true, true));
   }

   @Test
   public void testCreateAnonProducer() throws Exception {
      ClientProducer producer = clientSession.createProducer();
      assertNull(producer.getAddress());
      assertEquals(cf.getServerLocator().getProducerMaxRate(), producer.getMaxRate());
      assertEquals(cf.getServerLocator().isBlockOnNonDurableSend(), producer.isBlockOnNonDurableSend());
      assertEquals(cf.getServerLocator().isBlockOnDurableSend(), producer.isBlockOnDurableSend());
      assertFalse(producer.isClosed());
   }

   @Test
   public void testCreateProducer1() throws Exception {
      ClientProducer producer = clientSession.createProducer("testAddress");
      assertNotNull(producer.getAddress());
      assertEquals(cf.getServerLocator().getProducerMaxRate(), producer.getMaxRate());
      assertEquals(cf.getServerLocator().isBlockOnNonDurableSend(), producer.isBlockOnNonDurableSend());
      assertEquals(cf.getServerLocator().isBlockOnDurableSend(), producer.isBlockOnDurableSend());
      assertFalse(producer.isClosed());
   }

   @Test
   public void testProducerOnClosedSession() throws Exception {
      clientSession.close();
      try {
         clientSession.createProducer();
         fail("should throw exception");
      } catch (ActiveMQObjectClosedException oce) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
   }

}
