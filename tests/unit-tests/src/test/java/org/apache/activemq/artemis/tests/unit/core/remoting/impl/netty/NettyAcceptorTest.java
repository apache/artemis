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
package org.apache.activemq.artemis.tests.unit.core.remoting.impl.netty;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptor;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.ServerConnectionLifeCycleListener;
import org.apache.activemq.artemis.tests.extensions.PortCheckExtension;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class NettyAcceptorTest extends ActiveMQTestBase {

   private ScheduledExecutorService pool2;
   private ExecutorService pool3;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      if (pool3 != null) {
         pool3.shutdown();
      }

      if (pool2 != null) {
         pool2.shutdownNow();
      }

      super.tearDown();
   }

   @Test
   public void testStartStop() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };

      Map<String, Object> params = new HashMap<>();
      ServerConnectionLifeCycleListener listener = new ServerConnectionLifeCycleListener() {

         @Override
         public void connectionException(final Object connectionID, final ActiveMQException me) {
         }

         @Override
         public void connectionDestroyed(final Object connectionID, boolean failed) {
         }

         @Override
         public void connectionCreated(final ActiveMQComponent component,
                                       final Connection connection,
                                       final ProtocolManager protocol) {
         }

         @Override
         public void connectionReadyForWrites(Object connectionID, boolean ready) {
         }
      };
      pool2 = Executors.newScheduledThreadPool(ActiveMQDefaultConfiguration.getDefaultScheduledThreadPoolMaxSize(), ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      pool3 = Executors.newSingleThreadExecutor(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      NettyAcceptor acceptor = new NettyAcceptor("netty", null, params, handler, listener, pool2, pool3, new HashMap<>(), null, null);

      addActiveMQComponent(acceptor);
      acceptor.start();
      assertTrue(acceptor.isStarted());
      acceptor.stop();
      assertFalse(acceptor.isStarted());
      assertTrue(PortCheckExtension.checkAvailable(TransportConstants.DEFAULT_PORT));

      acceptor.start();
      assertTrue(acceptor.isStarted());
      acceptor.stop();
      assertFalse(acceptor.isStarted());
      assertTrue(PortCheckExtension.checkAvailable(TransportConstants.DEFAULT_PORT));
   }

   @Test
   public void testAutoStart() throws Exception {
      ActiveMQServer server = createServer(false, createDefaultInVMConfig());
      server.getConfiguration().addAcceptorConfiguration("default", "tcp://127.0.0.1:61617");
      server.getConfiguration().addAcceptorConfiguration("start", "tcp://127.0.0.1:61618?autoStart=true");
      server.getConfiguration().addAcceptorConfiguration("noStart", "tcp://127.0.0.1:61619?autoStart=false");
      server.start();
      assertTrue(server.getRemotingService().getAcceptor("default").isStarted());
      assertTrue(server.getRemotingService().getAcceptor("start").isStarted());
      assertFalse(server.getRemotingService().getAcceptor("noStart").isStarted());
   }

   @Test
   public void testActualPort() throws Exception {
      String firstPort0 = RandomUtil.randomUUIDString();
      String secondPort0 = RandomUtil.randomUUIDString();
      String normal = RandomUtil.randomUUIDString();
      String invm = RandomUtil.randomUUIDString();
      ActiveMQServer server = createServer(false, createDefaultInVMConfig());
      server.getConfiguration().addAcceptorConfiguration(firstPort0, "tcp://127.0.0.1:0");
      server.getConfiguration().addAcceptorConfiguration(secondPort0, "tcp://127.0.0.1:0");
      server.getConfiguration().addAcceptorConfiguration(normal, "tcp://127.0.0.1:61616");
      server.getConfiguration().addAcceptorConfiguration(invm, "vm://1");
      server.start();
      Wait.assertTrue(() -> server.getRemotingService().getAcceptor(firstPort0).getActualPort() > 0);
      Wait.assertTrue(() -> server.getRemotingService().getAcceptor(secondPort0).getActualPort() > 0);
      Wait.assertTrue(() -> server.getRemotingService().getAcceptor(firstPort0).getActualPort() != server.getRemotingService().getAcceptor(secondPort0).getActualPort());
      Wait.assertEquals(61616, () -> server.getRemotingService().getAcceptor(normal).getActualPort());
      Wait.assertEquals(-1, () -> server.getRemotingService().getAcceptor(invm).getActualPort());
   }

   @Test
   public void testInvalidSSLConfig() {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");

      try {
         new NettyAcceptor("netty", null, params, null, null, null, null, Map.of(), null, null);
         fail("This should have failed with an IllegalArgumentException");
      } catch (IllegalArgumentException e) {
         // expected
      }
   }

   @Test
   public void testValidSSLConfig1() {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
      params.put(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME, RandomUtil.randomUUIDString());
      new NettyAcceptor("netty", null, params, null, null, null, null, Map.of(), null, null);
   }

   @Test
   public void testValidSSLConfig2() {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
      params.put(TransportConstants.SSL_CONTEXT_PROP_NAME, RandomUtil.randomUUIDString());
      new NettyAcceptor("netty", null, params, null, null, null, null, Map.of(), null, null);
   }

   @Test
   public void testValidSSLConfigWithCrcOptions() {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
      params.put(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME, RandomUtil.randomUUIDString());
      params.put(TransportConstants.CRC_OPTIONS_PROP_NAME, "SOFT_FAIL");
      NettyAcceptor acceptor = new NettyAcceptor("netty", null, params, null, null, null, null, Map.of(), null, null);

      assertEquals("SOFT_FAIL", acceptor.getSSLContextConfig().getCrcOptions());
   }

   @Test
   public void testValidSSLConfigWithOcspResponderURL() {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
      params.put(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME, RandomUtil.randomUUIDString());
      params.put(TransportConstants.OCSP_RESPONDER_URL_PROP_NAME, "http://localhost:8080");
      NettyAcceptor acceptor = new NettyAcceptor("netty", null, params, null, null, null, null, Map.of(), null, null);

      assertEquals("http://localhost:8080", acceptor.getSSLContextConfig().getOcspResponderURL());
   }
}
