/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.transport.netty;

import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.haproxy.HAProxyCommand;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageEncoder;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;
import io.netty.handler.codec.haproxy.HAProxyProxiedProtocol;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.apache.activemq.artemis.utils.SocketAddressUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty based HA Proxy server that allows for testing some aspect of operating a broker behind an HA Proxy. Client
 * connections are accepted and their traffic is forwarded between this proxy and a running broker instance.
 */
public class NettyHAProxyServer {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final AtomicBoolean started = new AtomicBoolean();

   // values to be set on the PROXY Protocol header
   private String headerSourceHost;
   private int headerSourcePort;
   private String headerDestinationHost;
   private int headerDestinationPort;

   private EventLoopGroup bossGroup;
   private EventLoopGroup workerGroup;
   private Channel serverChannel;

   private String backendHost = "127.0.0.1";
   private int backendPort;
   private boolean sendProxyHeader = true;
   private HAProxyProtocolVersion proxyProtocolVersion = HAProxyProtocolVersion.V1;
   private boolean logBytes = false;
   private int fontendPort = 0;
   private int frontendPortInUse = -1;

   /**
    * Create a new instance that is not yet configured and requires a back-end port be set prior to calling the start
    * method otherwise an exception will be thrown.
    */
   public NettyHAProxyServer() {
   }

   /**
    * Create a new instance providing the port of the Artemis back-end server to connect to when a new client connects
    * to this proxy server.
    *
    * @param backendPort The port on the host used then a client connects to this front end proxy.
    */
   public NettyHAProxyServer(int backendPort) {
      if (backendPort <= 0) {
         throw new IllegalArgumentException("Port for backend service cannot be less than or equal to zero");
      }

      this.backendPort = backendPort;
   }

   public NettyHAProxyServer start() {
      if (started.compareAndSet(false, true)) {

         if (backendPort <= 0) {
            throw new IllegalArgumentException("The back-end server port has not been properly configured");
         }

         bossGroup = new NioEventLoopGroup();
         workerGroup = new NioEventLoopGroup();

         final ServerBootstrap server = new ServerBootstrap();

         // Create the server context and ensure the client channels are created with auto-read set to off so we can
         // first attempt a connection to the back-end before we handle any incoming data. Once connected to the backed
         // the handlers will pump data between after sending a proxy header if configured and then enabling auto read
         // for both sides.
         server.group(bossGroup, workerGroup)
               .channel(NioServerSocketChannel.class)
               .option(ChannelOption.SO_BACKLOG, 100)
               .handler(new LoggingHandler(logBytes ? LogLevel.INFO : LogLevel.TRACE))
               .childOption(ChannelOption.AUTO_READ, false)
               .childHandler(new ChannelInitializer<Channel>() {
                  @Override
                  public void initChannel(Channel clientChannel) throws Exception {
                     if (isLogBytes()) {
                        clientChannel.pipeline().addLast(new LoggingHandler(getClass(), LogLevel.INFO));
                     }

                     logger.info("New client connected to {}. Attempting to proxy to {}:{}", SocketAddressUtil.toString(serverChannel.localAddress()), backendHost, backendPort);

                     // The client channel handler will take care of connecting and wiring the exchange of bytes back
                     // and forth between the front end and the back-end.
                     clientChannel.pipeline().addLast(new NettyHAProxyFrontendHandler(backendHost,
                                                                                      backendPort,
                                                                                      sendProxyHeader,
                                                                                      proxyProtocolVersion,
                                                                                      headerSourceHost,
                                                                                      headerSourcePort,
                                                                                      headerDestinationHost,
                                                                                      headerDestinationPort));
                  }
               });

         // Start the server and then update the server port in case the configuration was such that the server chose a
         // free port.
         try {
            serverChannel = server.bind(getFrontEndPort()).sync().channel();
         } catch (InterruptedException e) {
            throw new RuntimeException(e);
         }
         frontendPortInUse = ((InetSocketAddress) serverChannel.localAddress()).getPort();
         logger.info("PROXY Protocol server listening on: {}", SocketAddressUtil.toString(serverChannel.localAddress()));
      }
      return this;
   }

   public void stop() throws Exception {
      final int timeout = 100;

      if (started.compareAndSet(true, false)) {
         frontendPortInUse = -1;

         logger.info("Syncing channel close");
         serverChannel.close().syncUninterruptibly();

         logger.trace("Shutting down boss group");
         bossGroup.shutdownGracefully(0, timeout, TimeUnit.MILLISECONDS).awaitUninterruptibly(timeout);
         logger.trace("Boss group shut down");

         logger.trace("Shutting down worker group");
         workerGroup.shutdownGracefully(0, timeout, TimeUnit.MILLISECONDS).awaitUninterruptibly(timeout);
         logger.trace("Worker group shut down");
      }
   }

   public int getFrontendPortInUse() {
      mustBeRunning("front-end port in use");
      return frontendPortInUse;
   }

   public int getFrontEndPort() {
      return fontendPort;
   }

   public NettyHAProxyServer setFrontendPort(int frontendPort) {
      mustNotBeRunning("front-end port");
      this.fontendPort = frontendPort;
      return this;
   }

   public String getBackEndHost() {
      return backendHost;
   }

   public NettyHAProxyServer setBackendHost(String backendHost) {
      mustNotBeRunning("back-end host");
      this.backendHost = backendHost;
      return this;
   }

   public int getBackEndPort() {
      return backendPort;
   }

   public NettyHAProxyServer setBackEndPort(int port) {
      mustNotBeRunning("back-end port");
      this.backendPort = port;
      return this;
   }

   public boolean isLogBytes() {
      return logBytes;
   }

   public NettyHAProxyServer setLogBytes(boolean logBytes) {
      mustNotBeRunning("log bytes");
      this.logBytes = logBytes;
      return this;
   }

   public boolean isSendProxyHeader() {
      mustBeRunning("send proxy header");
      return sendProxyHeader;
   }

   public NettyHAProxyServer setSendProxyHeader(boolean sendProxyHeader) {
      mustNotBeRunning("send proxy header");
      this.sendProxyHeader = sendProxyHeader;
      return this;
   }

   public HAProxyProtocolVersion getProxyProtocolVersion() {
      mustBeRunning("proxy protocol version");
      return proxyProtocolVersion;
   }

   public NettyHAProxyServer setProxyProtocolVersion(HAProxyProtocolVersion version) {
      mustNotBeRunning("proxy protocol version");
      this.proxyProtocolVersion = version;
      return this;
   }

   public String getHeaderSourceHost() {
      return headerSourceHost;
   }

   public NettyHAProxyServer setHeaderSourceHost(String headerSourceHost) {
      mustNotBeRunning("header source host");
      this.headerSourceHost = headerSourceHost;
      return this;
   }

   public int getHeaderSourcePort() {
      return headerSourcePort;
   }

   public NettyHAProxyServer setHeaderSourcePort(int headerSourcePort) {
      mustNotBeRunning("header source port");
      this.headerSourcePort = headerSourcePort;
      return this;
   }

   public String getHeaderDestinationHost() {
      return headerDestinationHost;
   }

   public NettyHAProxyServer setHeaderDestinationHost(String headerDestinationHost) {
      mustNotBeRunning("header destination host");
      this.headerDestinationHost = headerDestinationHost;
      return this;
   }

   public int getHeaderDestinationPort() {
      return headerDestinationPort;
   }

   public NettyHAProxyServer setHeaderDestinationPort(int headerDestinationPort) {
      mustNotBeRunning("header destination port");
      this.headerDestinationPort = headerDestinationPort;
      return this;
   }

   private void mustNotBeRunning(String configuration) {
      if (started.get()) {
         throw new IllegalStateException("Cannot configure " + configuration + " for a server that is running");
      }
   }

   private void mustBeRunning(String configuration) {
      if (!started.get()) {
         throw new IllegalStateException("Cannot access " + configuration + " for a server that is not running");
      }
   }

   private static void closeOnFlush(Channel ch) {
      // This queues an empty write which means to close won't occur until everything ahead of it is written or an error
      // occurs in which case we close on handling it.

      logger.trace("Close and flush called for channel: {}", ch);

      if (ch.isActive()) {
         ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
      } else {
         logger.trace("Did not queue a write as the channel is already inactive.");
      }
   }

   /**
    * The front end handler attempts to connect to the back-end on connect of a new client connection at the front end.
    * We will share the event loop for back-end communication with the front end so that we can simplify the thread
    * handling and confine all work to the same event-loop.
    */
   public static class NettyHAProxyFrontendHandler extends ChannelInboundHandlerAdapter {

      private final String backendHost;
      private final int backendPort;
      private final boolean sendProxyHeader;
      private final HAProxyProtocolVersion proxyProtocolVersion;
      private final String headerSourceHost;
      private final int headerSourcePort;
      private final String headerDestinationHost;
      private final int headerDestinationPort;

      private boolean proxyHeaderSent;
      private boolean autoReadEnabled;

      // As we use inboundChannel.eventLoop() when building the Bootstrap this does not need to be volatile as the
      // outboundChannel will use the same EventLoop (and therefore Thread) as the inboundChannel.
      private Channel backEndChannel;

      public NettyHAProxyFrontendHandler(String backendHost,
                                         int backendPort,
                                         boolean sendProxyHeader,
                                         HAProxyProtocolVersion proxyProtocolVersion,
                                         String headerSourceHost,
                                         int headerSourcePort,
                                         String headerDestinationHost,
                                         int headerDestinationPort) {
         this.backendHost = backendHost;
         this.backendPort = backendPort;
         this.sendProxyHeader = sendProxyHeader;
         this.proxyProtocolVersion = proxyProtocolVersion;
         this.headerSourceHost = headerSourceHost;
         this.headerSourcePort = headerSourcePort;
         this.headerDestinationHost = headerDestinationHost;
         this.headerDestinationPort = headerDestinationPort;
      }

      @Override
      public void channelActive(ChannelHandlerContext ctx) {

         final Channel clientChannel = ctx.channel();
         final Bootstrap bootstrap = new Bootstrap();

         // Start the connection attempt but ensure that the channel is created with auto-read disabled so that we can
         // handle pump of data between the front end and the back-end.
         bootstrap.group(clientChannel.eventLoop())
                  .channel(ctx.channel().getClass())
                  .option(ChannelOption.AUTO_READ, false)
                  .handler(new ChannelInitializer<>() {

                     @Override
                     public void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(new NettyHAProxyBackendHandler(clientChannel));
                        ch.pipeline().addLast(HAProxyMessageEncoder.INSTANCE);
                     }
                  });

         final ChannelFuture connectFuture = bootstrap.connect(backendHost, backendPort);

         backEndChannel = connectFuture.channel();

         connectFuture.addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future) {
               // If we succeed in connecting we can trigger a read but if we fail we should perform a clean close out
               // of the client connection via its channel.
               if (future.isSuccess()) {
                  logger.info("Connected to back-end {}:{}; triggering client read.", backendHost, backendPort);
                  clientChannel.read();
               } else {
                  logger.warn("Failed to connect to back-end {}:{}", backendHost, backendPort);
                  clientChannel.close();
               }
            }
         });
      }

      @Override
      public void channelRead(final ChannelHandlerContext ctx, Object message) {
         if (backEndChannel.isActive()) {
            // We only want to send the header once if configured so ensure we gate that
            if (sendProxyHeader && !proxyHeaderSent) {
               // Write but don't flush so that the client bytes can be sent into the pipeline before flushing the work
               // to the back-end broker instance.
               backEndChannel.write(new HAProxyMessage(proxyProtocolVersion,
                                                       HAProxyCommand.PROXY,
                                                       HAProxyProxiedProtocol.TCP4,
                                                       headerSourceHost,
                                                       headerDestinationHost,
                                                       headerSourcePort,
                                                       headerDestinationPort));

               proxyHeaderSent = true;
            }

            backEndChannel.writeAndFlush(message).addListener(new ChannelFutureListener() {

               @Override
               public void operationComplete(ChannelFuture future) {
                  if (future.isSuccess() && !autoReadEnabled) {
                     logger.trace("Switching front end channel to auto-read after initial exchange.");
                     ctx.channel().config().setAutoRead(true);
                     ctx.channel().read();
                     autoReadEnabled = true;
                  } else if (!future.isSuccess()) {
                     logger.warn("Error forwarding data from the front end to the back-end channel.");
                     future.channel().close();
                  }
               }
            });
         }
      }

      @Override
      public void channelInactive(ChannelHandlerContext ctx) {
         if (backEndChannel != null) {
            closeOnFlush(backEndChannel);
         }
      }

      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
         logger.warn("Error caught from front end handler", cause);
         closeOnFlush(ctx.channel());
      }
   }

   /**
    * Back-end handler reads from the back-end and forwards all bytes unchanged to the front end client. The back-end
    * adds an encoder for HA Proxy messages in order to encode them if the front end sends one into the pipeline for
    * write to the back-end server.
    */
   public static class NettyHAProxyBackendHandler extends ChannelInboundHandlerAdapter {

      private final Channel frontEndChannel;

      private boolean autoReadEnabled;

      public NettyHAProxyBackendHandler(Channel backEndChannel) {
         this.frontEndChannel = backEndChannel;
      }

      @Override
      public void channelActive(ChannelHandlerContext ctx) {
         // The connection to the front end could have gone down in which case ensure we close out the connection to the
         // back-end so that things get cleaned up.
         if (frontEndChannel.isActive()) {
            ctx.read();
         } else {
            closeOnFlush(ctx.channel());
         }
      }

      @Override
      public void channelInactive(ChannelHandlerContext ctx) {
         // Connection to the back-end is down so close the front end to propagate the state.
         logger.trace("Back-end connection has gone inactive");
         closeOnFlush(frontEndChannel);
      }

      @Override
      public void channelRead(final ChannelHandlerContext ctx, Object message) {
         // Read from the back-end triggers passthrough write to the front end
         frontEndChannel.writeAndFlush(message).addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future) {
               if (future.isSuccess() && !autoReadEnabled) {
                  logger.trace("Switching back-end channel to auto-read after initial exchange.");
                  ctx.channel().config().setAutoRead(true);
                  ctx.channel().read();
                  autoReadEnabled = true;
               } else if (!future.isSuccess()) {
                  logger.warn("Error forwarding data from the back-end to the front end channel");
                  future.channel().close();
               }
            }
         });
      }

      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
         logger.warn("Error caught from back-end handler", cause);
         closeOnFlush(ctx.channel());
      }
   }
}
