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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelPipeline;
import java.lang.reflect.Method;
import java.util.Map;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NettyConnectorTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(false, createDefaultNettyConfig());
      server.start();
   }

   //make sure the 'connect-timeout' passed to netty.
   @Test
   public void testConnectionTimeoutConfig() throws Exception {
      final int timeout = 23456;
      TransportConfiguration transport = new TransportConfiguration(NETTY_CONNECTOR_FACTORY);
      transport.getParams().put(TransportConstants.NETTY_CONNECT_TIMEOUT, timeout);

      ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(transport);

      ClientSessionFactoryImpl factory = (ClientSessionFactoryImpl) locator.createSessionFactory();
      NettyConnector connector = (NettyConnector) factory.getConnector();

      Bootstrap bootstrap = connector.getBootStrap();

      assertEquals(timeout, bootstrap.register().channel().config().getConnectTimeoutMillis());

      factory.close();
      locator.close();
   }

   @Test
   public void testConnectionHttpHeaders() throws Exception {
      TransportConfiguration transport = new TransportConfiguration(NETTY_CONNECTOR_FACTORY);
      transport.getParams().put(TransportConstants.HTTP_ENABLED_PROP_NAME, true);
      transport.getParams().put("nettyHttpHeader.accept", "text/html,application/xhtml+xml,application/xml");
      transport.getParams().put("nettyHttpHeader.Accept-Encoding", "gzip,deflate");
      transport.getParams().put("nettyHttpHeader.Accept-Language", "en-us,en;q=0.5");

      try (ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(transport)) {
         ClientSessionFactoryImpl factory = (ClientSessionFactoryImpl) locator.createSessionFactory();
         NettyConnector connector = (NettyConnector) factory.getConnector();

         Bootstrap bootstrap = connector.getBootStrap();
         ChannelPipeline pipeline = bootstrap.register().channel().pipeline();
         pipeline.flush();
         Wait.assertTrue("HttpHandler is null!", () -> pipeline.get(NettyConnector.HttpHandler.class) != null, 500, 25);
         Object httpHandler = pipeline.get(NettyConnector.HttpHandler.class);
         Method getHeadersMethod = httpHandler.getClass().getMethod("getHeaders", (Class<?>[]) null);
         getHeadersMethod.setAccessible(true);
         Map<String, String> headers = (Map<String, String>) getHeadersMethod.invoke(httpHandler, (Object[]) null);
         assertEquals(3, headers.size());
         assertTrue(headers.containsKey("accept"));
         assertEquals("text/html,application/xhtml+xml,application/xml", headers.get("accept"));
         assertTrue(headers.containsKey("Accept-Encoding"));
         assertEquals("gzip,deflate", headers.get("Accept-Encoding"));
         assertTrue(headers.containsKey("Accept-Language"));
         assertEquals("en-us,en;q=0.5", headers.get("Accept-Language"));
         assertFalse(headers.containsKey("test"));
         factory.close();
      }
   }
}
