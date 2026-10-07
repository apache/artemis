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

package org.apache.activemq.artemis.tests.util;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This Proxy is based in one of the Netty Examples:
 * https://github.com/netty/netty/tree/ccc5e01f0444301561f055b02cd7c1f3e875bca7/example/src/main/java/io/netty/example/proxy
 */
public final class TcpProxy implements Runnable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   List<OutboundHandler> outbound = new ArrayList<>();
   List<InboundHandler> inbound = new ArrayList();

   public List<OutboundHandler> getOutbounddHandlers() {
      return outbound;
   }

   public List<InboundHandler> getInboundHandlers() {
      return inbound;
   }

   public void stopAllHandlers() {
      inbound.forEach(i -> i.setReadable(false));
      outbound.forEach(i -> i.setReadable(false));
   }

   /**
    * Closes the specified channel after all queued write requests are flushed.
    */
   public static void closeOnFlush(Channel ch) {
      if (ch.isActive()) {
         ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
      }
   }

   int localPort;
   String remoteHost;
   int remotePort;
   boolean logging;

   public TcpProxy(String remoteHost, int remotePort, int localPort, boolean logging) {
      this.remoteHost = remoteHost;
      this.remotePort = remotePort;
      this.localPort = localPort;
      this.logging = logging;
   }

   /**
    * Try a Core Protocol connection until successful
    */
   public void tryCore(String user, String password) {
      ConnectionFactory cf = CFUtil.createConnectionFactory("CORE", "tcp://" + remoteHost + ":" + localPort);
      // try to connect a few time, to make sure the proxy is up
      boolean succeeded = false;
      for (int i = 0; i < 10; i++) {
         try (Connection connection = cf.createConnection(user, password)) {
            succeeded = true;
            break;
         } catch (Exception e) {
            try {
               Thread.sleep(100);
            } catch (Exception ignored) {
            }
         }
      }

      if (!succeeded) {
         throw new IllegalStateException("Proxy did not work as expected");
      }

      inbound.clear();
      outbound.clear();
   }



   Thread thread;

   public void startProxy() {
      thread = new Thread(this);
      thread.start();
   }

   public void stopProxy() throws Exception {
      stopProxy(5000);
   }

   public void stopProxy(int timeoutMillis) throws Exception {
      channelFuture.cancel(true);
      thread.join(timeoutMillis);
      if (thread.isAlive()) {
         throw new RuntimeException("Proxy thread still alive");
      }
   }

   ChannelFuture channelFuture;

   @Override
   public void run() {
      logger.info("Proxying {} to {}", localPort, remotePort);

      // Configure the bootstrap.
      EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
      EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
      try {
         ServerBootstrap b = new ServerBootstrap();
         b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class);

         if (logging) {
            b.handler(new LoggingHandler(LogLevel.INFO));
         }

         channelFuture = b.childHandler(new ProxyInitializer(remoteHost, remotePort))
            .childOption(ChannelOption.AUTO_READ, false)
            .bind(localPort).sync().channel().closeFuture();
         channelFuture.sync();
         logger.info("done");
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);

      } finally {
         bossGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS);
         workerGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS);
      }
   }


   class ProxyInitializer extends ChannelInitializer<SocketChannel> {

      private final String remoteHost;
      private final int remotePort;

      ProxyInitializer(String remoteHost, int remotePort) {
         this.remoteHost = remoteHost;
         this.remotePort = remotePort;
      }

      @Override
      public void initChannel(SocketChannel ch) {
         ChannelPipeline pipeline = ch.pipeline();
         if (logging) {
            pipeline.addLast(new LoggingHandler(LogLevel.INFO));
         }
         OutboundHandler outboundHandler = new OutboundHandler();
         TcpProxy.this.outbound.add(outboundHandler);
         pipeline.addLast(outboundHandler);
      }
   }

   public class OutboundHandler extends ChannelInboundHandlerAdapter {
      // As we use inboundChannel.eventLoop() when building the Bootstrap this does not need to be volatile as
      // the outboundChannel will use the same EventLoop (and therefore Thread) as the inboundChannel.
      private Channel outboundChannel;

      volatile boolean readable = true;

      public OutboundHandler setReadable(boolean readable) {
         this.readable = readable;
         if (readable) {
            outboundChannel.read();
         }
         return this;
      }

      @Override
      public void channelActive(ChannelHandlerContext ctx) {
         final Channel inboundChannel = ctx.channel();

         InboundHandler inboundHandler = new InboundHandler(inboundChannel);
         TcpProxy.this.inbound.add(inboundHandler);

         // Start the connection attempt.
         Bootstrap b = new Bootstrap();
         b.group(inboundChannel.eventLoop())
            .channel(ctx.channel().getClass())
            .handler(inboundHandler)
            .option(ChannelOption.AUTO_READ, false);

         ChannelFuture f = b.connect(remoteHost, remotePort);

         outboundChannel = f.channel();

         f.addListener(future -> {
            if (future.isSuccess()) {
               // connection complete start to read first data
               inboundChannel.read();
            } else {
               // Close the connection if the connection attempt has failed.
               inboundChannel.close();
            }
         });
      }

      @Override
      public void channelRead(final ChannelHandlerContext ctx, Object msg) {
         if (outboundChannel.isActive()) {
            outboundChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
               if (future.isSuccess()) {
                  // was able to flush out data, start to read the next chunk
                  if (readable) {
                     ctx.channel().read();
                  }
               } else {
                  new Exception("Closing").printStackTrace();
                  future.channel().close();
               }
            });
         }
      }

      @Override
      public void channelInactive(ChannelHandlerContext ctx) {
         if (outboundChannel != null) {
            TcpProxy.closeOnFlush(outboundChannel);
         }
      }

      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
         cause.printStackTrace();
         TcpProxy.closeOnFlush(ctx.channel());
      }
   }


   public class InboundHandler extends ChannelInboundHandlerAdapter {

      private final Channel inboundChannel;

      public InboundHandler(Channel inboundChannel) {
         this.inboundChannel = inboundChannel;
      }

      volatile boolean readable = true;

      public InboundHandler setReadable(boolean readable) {
         this.readable = readable;
         if (readable) {
            inboundChannel.read();
         }
         return this;
      }

      @Override
      public void channelActive(ChannelHandlerContext ctx) {
         ctx.read();
      }

      @Override
      public void channelRead(final ChannelHandlerContext ctx, Object msg) {
         inboundChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
               if (readable) {
                  ctx.channel().read();
               }
            } else {
               new Exception("Closing").printStackTrace();
               future.channel().close();
            }
         });
      }

      @Override
      public void channelInactive(ChannelHandlerContext ctx) {
         TcpProxy.closeOnFlush(inboundChannel);
      }

      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
         cause.printStackTrace();
         TcpProxy.closeOnFlush(ctx.channel());
      }
   }
}


