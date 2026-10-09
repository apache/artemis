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

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.security.Principal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.apache.activemq.transport.amqp.client.util.IOExceptionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.function.Function.identity;

/**
 * TCP based transport that uses Netty as the underlying IO layer.
 */
public class NettyTcpTransport implements NettyTransport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int SHUTDOWN_TIMEOUT = 100;
   public static final int DEFAULT_MAX_FRAME_SIZE = 65535;

   protected Bootstrap bootstrap;
   protected EventLoopGroup group;
   protected Channel channel;
   protected NettyTransportListener listener;
   protected final NettyTransportOptions options;
   protected final URI remote;
   protected int maxFrameSize = DEFAULT_MAX_FRAME_SIZE;

   private final AtomicBoolean connected = new AtomicBoolean();
   private final AtomicBoolean closed = new AtomicBoolean();
   private final CountDownLatch connectLatch = new CountDownLatch(1);
   private volatile IOException failureCause;

   /**
    * Create a new transport instance
    *
    * @param remoteLocation the URI that defines the remote resource to connect to.
    * @param options        the transport options used to configure the socket connection.
    */
   public NettyTcpTransport(URI remoteLocation, NettyTransportOptions options) {
      this(null, remoteLocation, options);
   }

   /**
    * Create a new transport instance
    *
    * @param listener       the TransportListener that will receive events from this Transport.
    * @param remoteLocation the URI that defines the remote resource to connect to.
    * @param options        the transport options used to configure the socket connection.
    */
   public NettyTcpTransport(NettyTransportListener listener, URI remoteLocation, NettyTransportOptions options) {
      if (options == null) {
         throw new IllegalArgumentException("Transport Options cannot be null");
      }

      if (remoteLocation == null) {
         throw new IllegalArgumentException("Transport remote location cannot be null");
      }

      this.options = options;
      this.listener = listener;
      this.remote = remoteLocation;
   }

   @Override
   public void connect() throws IOException {

      if (listener == null) {
         throw new IllegalStateException("A transport listener must be set before connection attempts.");
      }

      final SslHandler sslHandler;
      if (isSSL()) {
         try {
            sslHandler = NettyTransportSupport.createSslHandler(getRemoteLocation(), getSslOptions());
         } catch (Exception ex) {
            // TODO: can we stop it throwing Exception?
            throw IOExceptionSupport.create(ex);
         }
      } else {
         sslHandler = null;
      }

      group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());

      bootstrap = new Bootstrap();
      bootstrap.group(group);
      bootstrap.channel(NioSocketChannel.class);
      bootstrap.handler(new ChannelInitializer<>() {
         @Override
         public void initChannel(Channel connectedChannel) throws Exception {
            configureChannel(connectedChannel, sslHandler);
         }
      });

      configureNetty(bootstrap, getTransportOptions());

      ChannelFuture future = bootstrap.connect(getRemoteHost(), getRemotePort());
      future.addListener((ChannelFutureListener) future1 -> {
         if (!future1.isSuccess()) {
            handleException(future1.channel(), IOExceptionSupport.create(future1.cause()));
         }
      });

      try {
         connectLatch.await();
      } catch (InterruptedException ex) {
         logger.debug("Transport connection was interrupted.");
         Thread.interrupted();
         failureCause = IOExceptionSupport.create(ex);
      }

      if (failureCause != null) {
         // Close out any Netty resources now as they are no longer needed.
         if (channel != null) {
            channel.close().syncUninterruptibly();
            channel = null;
         }
         if (group != null) {
            Future<?> fut = group.shutdownGracefully(0, SHUTDOWN_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!fut.awaitUninterruptibly(2 * SHUTDOWN_TIMEOUT)) {
               logger.trace("Channel group shutdown failed to complete in allotted time");
            }
            group = null;
         }

         throw failureCause;
      } else {
         // Connected, allow any held async error to fire now and close the transport.
         channel.eventLoop().execute(() -> {
            if (failureCause != null) {
               channel.pipeline().fireExceptionCaught(failureCause);
            }
         });
      }
   }

   @Override
   public boolean isConnected() {
      return connected.get();
   }

   @Override
   public boolean isSSL() {
      return options.isSSL();
   }

   @Override
   public void close() throws IOException {
      if (closed.compareAndSet(false, true)) {
         connected.set(false);
         try {
            if (channel != null) {
               channel.close().syncUninterruptibly();
            }
         } finally {
            if (group != null) {
               Future<?> fut = group.shutdownGracefully(0, SHUTDOWN_TIMEOUT, TimeUnit.MILLISECONDS);
               if (!fut.awaitUninterruptibly(2 * SHUTDOWN_TIMEOUT)) {
                  logger.trace("Channel group shutdown failed to complete in allotted time");
               }
            }
         }
      }
   }

   @Override
   public ByteBuf allocateSendBuffer(int size) throws IOException {
      checkConnected();
      return channel.alloc().ioBuffer(size, size);
   }

   protected final ChannelFuture writeAndFlush(ByteBuf output,
                                               ChannelPromise promise,
                                               Function<? super ByteBuf, ? extends ReferenceCounted> bufferTransformer) throws IOException {
      try {
         checkConnected();
      } catch (IOException ioEx) {
         output.release();
         throw ioEx;
      }
      int length = output.readableBytes();
      if (length == 0) {
         output.release();
         return null;
      }

      logger.trace("Attempted write of: {} bytes", length);

      return channel.writeAndFlush(bufferTransformer.apply(output), promise);
   }

   @Override
   public ChannelFuture send(ByteBuf output) throws IOException {
      return writeAndFlush(output, channel.newPromise(), identity());
   }

   @Override
   public void sendVoidPromise(ByteBuf output) throws IOException {
      writeAndFlush(output, channel.voidPromise(), identity());
   }

   @Override
   public NettyTransportListener getTransportListener() {
      return listener;
   }

   @Override
   public void setTransportListener(NettyTransportListener listener) {
      this.listener = listener;
   }

   @Override
   public NettyTransportOptions getTransportOptions() {
      return options;
   }

   @Override
   public URI getRemoteLocation() {
      return remote;
   }

   @Override
   public Principal getLocalPrincipal() {
      Principal result = null;

      if (isSSL()) {
         SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
         result = sslHandler.engine().getSession().getLocalPrincipal();
      }

      return result;
   }

   @Override
   public void setMaxFrameSize(int maxFrameSize) {
      if (connected.get()) {
         throw new IllegalStateException("Cannot change Max Frame Size while connected.");
      }

      this.maxFrameSize = maxFrameSize;
   }

   @Override
   public int getMaxFrameSize() {
      return maxFrameSize;
   }

   // ----- Internal implementation details, can be overridden as needed -----//

   protected String getRemoteHost() {
      return remote.getHost();
   }

   protected int getRemotePort() {
      if (remote.getPort() != -1) {
         return remote.getPort();
      } else {
         return isSSL() ? getSslOptions().getDefaultSslPort() : getTransportOptions().getDefaultTcpPort();
      }
   }

   protected void addAdditionalHandlers(ChannelPipeline pipeline) {

   }

   protected ChannelInboundHandlerAdapter createChannelHandler() {
      return new NettyTcpTransportHandler();
   }

   // ----- Event Handlers which can be overridden in subclasses -------------//

   protected void handleConnected(Channel channel) throws Exception {
      logger.trace("Channel has become active! Channel is {}", channel);
      connectionEstablished(channel);
   }

   protected void handleChannelInactive(Channel channel) throws Exception {
      logger.trace("Channel has gone inactive! Channel is {}", channel);
      if (connected.compareAndSet(true, false) && !closed.get()) {
         logger.trace("Firing onTransportClosed listener");
         listener.onTransportClosed();
      }
   }

   protected void handleException(Channel channel, Throwable cause) throws Exception {
      logger.trace("Exception on channel! Channel is {}", channel);
      if (connected.compareAndSet(true, false) && !closed.get()) {
         logger.trace("Firing onTransportError listener");
         if (failureCause != null) {
            listener.onTransportError(failureCause);
         } else {
            listener.onTransportError(cause);
         }
      } else {
         // Hold the first failure for later dispatch if connect succeeds.
         // This will then trigger disconnect using the first error reported.
         if (failureCause == null) {
            logger.trace("Holding error until connect succeeds: {}", cause.getMessage());
            failureCause = IOExceptionSupport.create(cause);
         }

         connectionFailed(channel, failureCause);
      }
   }

   // ----- State change handlers and checks ---------------------------------//

   protected final void checkConnected() throws IOException {
      if (!connected.get()) {
         throw new IOException("Cannot send to a non-connected transport.");
      }
   }

   // Called when the transport has successfully connected and is ready for use.
   private void connectionEstablished(Channel connectedChannel) {
      channel = connectedChannel;
      connected.set(true);
      connectLatch.countDown();
   }

   // Called when the transport connection failed and an error should be returned.
   private void connectionFailed(Channel failedChannel, IOException cause) {
      failureCause = cause;
      channel = failedChannel;
      connected.set(false);
      connectLatch.countDown();
   }

   private NettyTransportSslOptions getSslOptions() {
      return (NettyTransportSslOptions) getTransportOptions();
   }

   private void configureNetty(Bootstrap bootstrap, NettyTransportOptions options) {
      bootstrap.option(ChannelOption.TCP_NODELAY, options.isTcpNoDelay());
      bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, options.getConnectTimeout());
      bootstrap.option(ChannelOption.SO_KEEPALIVE, options.isTcpKeepAlive());
      bootstrap.option(ChannelOption.SO_LINGER, options.getSoLinger());

      if (options.getSendBufferSize() != -1) {
         bootstrap.option(ChannelOption.SO_SNDBUF, options.getSendBufferSize());
      }

      if (options.getReceiveBufferSize() != -1) {
         bootstrap.option(ChannelOption.SO_RCVBUF, options.getReceiveBufferSize());
         bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
      }

      if (options.getTrafficClass() != -1) {
         bootstrap.option(ChannelOption.IP_TOS, options.getTrafficClass());
      }
   }

   private void configureChannel(final Channel channel, final SslHandler sslHandler) throws Exception {
      if (isSSL()) {
         channel.pipeline().addLast(sslHandler);
      }

      if (getTransportOptions().isTraceBytes()) {
         channel.pipeline().addLast("logger", new LoggingHandler(getClass()));
      }

      addAdditionalHandlers(channel.pipeline());

      channel.pipeline().addLast(createChannelHandler());
   }

   // ----- Handle connection events -----------------------------------------//

   protected abstract class NettyDefaultHandler<E> extends SimpleChannelInboundHandler<E> {

      @Override
      public void channelRegistered(ChannelHandlerContext context) throws Exception {
         channel = context.channel();
      }

      @Override
      public void channelActive(ChannelHandlerContext context) throws Exception {
         // In the Secure case we need to let the handshake complete before we
         // trigger the connected event.
         if (!isSSL()) {
            handleConnected(context.channel());
         } else {
            SslHandler sslHandler = context.pipeline().get(SslHandler.class);
            sslHandler.handshakeFuture().addListener((GenericFutureListener<Future<Channel>>) future -> {
               if (future.isSuccess()) {
                  logger.trace("SSL Handshake has completed: {}", channel);
                  handleConnected(channel);
               } else {
                  logger.trace("SSL Handshake has failed: {}", channel);
                  handleException(channel, future.cause());
               }
            });
         }
      }

      @Override
      public void channelInactive(ChannelHandlerContext context) throws Exception {
         handleChannelInactive(context.channel());
      }

      @Override
      public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
         handleException(context.channel(), cause);
      }
   }

   // ----- Handle Binary data from connection -------------------------------//

   protected class NettyTcpTransportHandler extends NettyDefaultHandler<ByteBuf> {

      @Override
      protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
         logger.trace("New data read: {} bytes incoming: {}", buffer.readableBytes(), buffer);
         listener.onData(buffer);
      }
   }
}
