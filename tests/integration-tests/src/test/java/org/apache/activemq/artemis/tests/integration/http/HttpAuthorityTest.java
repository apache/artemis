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
package org.apache.activemq.artemis.tests.integration.http;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpAuthorityTest extends ActiveMQTestBase {

   @Test
   public void testHttpAuthority() throws Exception {
      int port = 61616;
      CountDownLatch requestTested = new CountDownLatch(1);
      AtomicBoolean failed = new AtomicBoolean(false);
      EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
      EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
      try {
         ServerBootstrap bootstrap = new ServerBootstrap();
         bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) {
               ch.pipeline().addLast(new HttpServerCodec());
               ch.pipeline().addLast(new HttpObjectAggregator(1024 * 1024));
               ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                  @Override
                  protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
                     if (failed.get()) {
                        return;
                     }

                     try {
                        failed.set(!new URI(req.uri()).getAuthority().equals(req.headers().get("host")));
                     } catch (Exception e) {
                        failed.set(true);
                     }
                     requestTested.countDown();

                     ctx.writeAndFlush(new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.OK));
                  }
               });
            }
         });
         ChannelFuture future = bootstrap.bind(port).sync();

         try (ServerLocator locator = ActiveMQClient.createServerLocator("tcp://127.0.0.1:61616?httpEnabled=true;callTimeout=250")) {
            ClientSessionFactory sf = createSessionFactory(locator);
         } catch (Exception e) {
            // ignore - we expect this to fail because the server isn't returning a valid response
         }
         assertTrue(requestTested.await(500, TimeUnit.MILLISECONDS));
         assertFalse(failed.get());
         future.channel().close();
      } finally {
         workerGroup.shutdownGracefully();
         bossGroup.shutdownGracefully();
      }
   }
}
