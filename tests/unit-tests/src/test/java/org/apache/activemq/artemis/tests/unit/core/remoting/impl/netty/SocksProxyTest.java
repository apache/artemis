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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.NoopAddressResolverGroup;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler;
import org.apache.activemq.artemis.spi.core.remoting.ClientConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SocksProxyTest extends ActiveMQTestBase {

   private static final int SOCKS_PORT = 1080;

   private ExecutorService closeExecutor;
   private ExecutorService threadPool;
   private ScheduledExecutorService scheduledThreadPool;

   private EventLoopGroup bossGroup;
   private EventLoopGroup workerGroup;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      closeExecutor       = Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      threadPool          = Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      scheduledThreadPool = Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));

      startSocksProxy();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      closeExecutor.shutdownNow();
      threadPool.shutdownNow();
      scheduledThreadPool.shutdownNow();

      stopSocksProxy();

      super.tearDown();
   }

   @Test
   public void testSocksProxyHandlerAdded() throws Exception {
      InetAddress address = getNonLoopbackAddress();
      assumeTrue(address != null, "Cannot find non-loopback address");

      BufferHandler handler = (connectionID, buffer) -> {
      };

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.HOST_PROP_NAME, address.getHostAddress());
      params.put(TransportConstants.SOCKS_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.SOCKS_HOST_PROP_NAME, "localhost");

      ClientConnectionLifeCycleListener listener = new ClientConnectionLifeCycleListener() {
         @Override
         public void connectionException(final Object connectionID, final ActiveMQException me) {
         }

         @Override
         public void connectionDestroyed(final Object connectionID, boolean failed) {
         }

         @Override
         public void connectionCreated(final ActiveMQComponent component,
                                       final Connection connection,
                                       final ClientProtocolManager protocol) {
         }

         @Override
         public void connectionReadyForWrites(Object connectionID, boolean ready) {
         }
      };

      NettyConnector connector = new NettyConnector(params, handler, listener, closeExecutor, threadPool, scheduledThreadPool);

      connector.start();
      assertTrue(connector.isStarted());

      ChannelPipeline pipeline = connector.getBootStrap().register().await().channel().pipeline();
      assertNotNull(pipeline.get(Socks5ProxyHandler.class));

      connector.close();
      assertFalse(connector.isStarted());
   }

   private InetAddress getNonLoopbackAddress() throws SocketException {
      Enumeration<NetworkInterface> n = NetworkInterface.getNetworkInterfaces();
      InetAddress addr = null;
      for (; n.hasMoreElements(); ) {
         NetworkInterface e = n.nextElement();
         Enumeration<InetAddress> a = e.getInetAddresses();
         boolean found = false;
         for (; a.hasMoreElements(); ) {
            addr = a.nextElement();
            if (!addr.isLoopbackAddress()) {
               found = true;
               break;
            }
         }
         if (found) {
            break;
         }
      }
      return addr;
   }

   @Test
   public void testSocksProxyHandlerNotAddedForLocalhost() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.HOST_PROP_NAME, "localhost");
      params.put(TransportConstants.SOCKS_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.SOCKS_HOST_PROP_NAME, "localhost");

      ClientConnectionLifeCycleListener listener = new ClientConnectionLifeCycleListener() {
         @Override
         public void connectionException(final Object connectionID, final ActiveMQException me) {
         }

         @Override
         public void connectionDestroyed(final Object connectionID, boolean failed) {
         }

         @Override
         public void connectionCreated(final ActiveMQComponent component,
                                       final Connection connection,
                                       final ClientProtocolManager protocol) {
         }

         @Override
         public void connectionReadyForWrites(Object connectionID, boolean ready) {
         }
      };

      NettyConnector connector = new NettyConnector(params, handler, listener, closeExecutor, threadPool, scheduledThreadPool);

      connector.start();
      assertTrue(connector.isStarted());

      ChannelPipeline pipeline = connector.getBootStrap().register().await().channel().pipeline();
      assertNull(pipeline.get(Socks5ProxyHandler.class));

      connector.close();
      assertFalse(connector.isStarted());
   }

   @Test
   public void testSocks5hSupport() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };
      Map<String, Object> params = new HashMap<>();

      params.put(TransportConstants.HOST_PROP_NAME, "only-resolvable-on-proxy");
      params.put(TransportConstants.SOCKS_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.SOCKS_HOST_PROP_NAME, "localhost");
      params.put(TransportConstants.SOCKS_PORT_PROP_NAME, SOCKS_PORT);
      params.put(TransportConstants.SOCKS_REMOTE_DNS_PROP_NAME, true);

      ClientConnectionLifeCycleListener listener = new ClientConnectionLifeCycleListener() {
         @Override
         public void connectionException(final Object connectionID, final ActiveMQException me) {
         }

         @Override
         public void connectionDestroyed(final Object connectionID, boolean failed) {
         }

         @Override
         public void connectionCreated(final ActiveMQComponent component,
                                       final Connection connection,
                                       final ClientProtocolManager protocol) {
         }

         @Override
         public void connectionReadyForWrites(Object connectionID, boolean ready) {
         }
      };

      NettyConnector connector = new NettyConnector(params, handler, listener, closeExecutor, threadPool, scheduledThreadPool);

      connector.start();
      assertTrue(connector.isStarted());

      connector.getBootStrap().register().await().channel().pipeline();

      AddressResolverGroup<?> resolver = connector.getBootStrap().config().resolver();
      assertSame(NoopAddressResolverGroup.INSTANCE, resolver);

      Connection connection = connector.createConnection(future -> {
         future.awaitUninterruptibly();
         assertTrue(future.isSuccess());

         Socks5ProxyHandler socks5Handler = future.channel().pipeline().get(Socks5ProxyHandler.class);
         assertNotNull(socks5Handler);

         InetSocketAddress remoteAddress = (InetSocketAddress)socks5Handler.destinationAddress();
         assertTrue(remoteAddress.isUnresolved());
      });
      assertNotNull(connection);

      assertTrue(connection.isOpen());
      connection.close();
      assertFalse(connection.isOpen());

      connector.close();
      assertFalse(connector.isStarted());
   }

   private void startSocksProxy() throws Exception {
      bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
      workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

      ServerBootstrap b = new ServerBootstrap();
      b.group(bossGroup, workerGroup);
      b.channel(NioServerSocketChannel.class);
      b.childHandler(new ChannelInitializer<SocketChannel>() {
         @Override
         protected void initChannel(SocketChannel ch) throws Exception {
            // We can further configure SOCKS, but have to assume Netty is doing the right thing,
            // we just need something listening on the port to make the initial connection
         }
      });

      b.bind(SOCKS_PORT).sync();
   }

   private void stopSocksProxy() {
      bossGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS);
      workerGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS);
   }
}
