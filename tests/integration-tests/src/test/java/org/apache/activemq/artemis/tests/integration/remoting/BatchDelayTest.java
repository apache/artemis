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
package org.apache.activemq.artemis.tests.integration.remoting;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BatchDelayTest extends ActiveMQTestBase {

   private static final int N = 1000;
   private static final long DELAY = 500;
   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.BATCH_DELAY, DELAY);

      TransportConfiguration tc = new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params);

      Configuration config = createBasicConfig().addAcceptorConfiguration(tc);
      server = createServer(false, config);
      server.start();
   }

   protected ClientSessionFactory createSessionFactory() throws Exception {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.BATCH_DELAY, DELAY);
      ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(createTransportConfiguration(true, false, params));
      addServerLocator(locator);
      ClientSessionFactory sf = createSessionFactory(locator);
      return addSessionFactory(sf);
   }

   @Test
   public void testSendReceiveMany() throws Exception {
      ClientSessionFactory sf = createSessionFactory();

      ClientSession session = sf.createSession();

      final String foo = "foo";

      session.createQueue(QueueConfiguration.of(foo).setRoutingType(RoutingType.ANYCAST));

      ClientProducer prod = session.createProducer(foo);

      ClientConsumer cons = session.createConsumer(foo);

      session.start();

      sendMessages(session, prod, N);
      receiveMessages(cons, 0, N, true);
   }

   @Test
   public void testSendReceiveOne() throws Exception {
      ClientSessionFactory sf = createSessionFactory();

      ClientSession session = sf.createSession();

      final String foo = "foo";

      session.createQueue(QueueConfiguration.of(foo).setRoutingType(RoutingType.ANYCAST));

      ClientProducer prod = session.createProducer(foo);

      ClientConsumer cons = session.createConsumer(foo);

      session.start();

      ClientMessage msg = session.createMessage(false);

      prod.send(msg);

      msg = cons.receive(10000);

      assertNotNull(msg);

      msg.acknowledge();
   }
}
