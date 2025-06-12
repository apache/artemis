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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

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
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.CheckDependencies;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IoUringTransportTest extends ActiveMQTestBase {

   private static final SimpleString QUEUE = SimpleString.of("IoUringTransportTestQueue");

   private Map<String, Object> ioUringParams;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      // skip before we build anything if the platform can't do io_uring
      assumeTrue(CheckDependencies.isIoUringAvailable(), "io_uring is not available on this platform");
      super.setUp();

      // force io_uring as the only native option so a working transfer unambiguously proves io_uring was used
      ioUringParams = new HashMap<>();
      ioUringParams.put(TransportConstants.USE_IOURING_PROP_NAME, true);
      ioUringParams.put(TransportConstants.USE_EPOLL_PROP_NAME, false);
      ioUringParams.put(TransportConstants.USE_KQUEUE_PROP_NAME, false);
   }

   @Test
   public void testSendReceiveOverIoUring() throws Exception {
      Configuration config = createBasicConfig().addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, ioUringParams, "netty"));
      ActiveMQServer server = addServer(createServer(false, config));
      server.start();

      // the acceptor must have genuinely selected io_uring, not silently fallen back to NIO
      assertUsingIoUring(server);

      TransportConfiguration connectorConfig = new TransportConfiguration(NETTY_CONNECTOR_FACTORY, ioUringParams);
      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(connectorConfig));
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = addClientSession(sf.createSession(false, true, true));

      session.createQueue(QueueConfiguration.of(QUEUE).setRoutingType(RoutingType.ANYCAST).setDurable(false));

      String text = RandomUtil.randomUUIDString();
      ClientProducer producer = session.createProducer(QUEUE);
      ClientMessage message = session.createMessage(false);
      message.getBodyBuffer().writeString(text);
      producer.send(message);

      ClientConsumer consumer = session.createConsumer(QUEUE);
      session.start();
      ClientMessage received = consumer.receive(5000);
      assertNotNull(received, "expected a message over the io_uring transport");
      assertEquals(text, received.getBodyBuffer().readString());
      received.acknowledge();
   }

   private void assertUsingIoUring(ActiveMQServer server) throws Exception {
      Object acceptor = server.getRemotingService().getAcceptor("netty");
      Field channelClazzField = acceptor.getClass().getDeclaredField("channelClazz");
      channelClazzField.setAccessible(true);
      Class<?> channelClazz = (Class<?>) channelClazzField.get(acceptor);
      assertEquals("io.netty.channel.uring.IoUringServerSocketChannel", channelClazz.getName(), "acceptor did not select the io_uring transport");
   }
}
