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
package org.apache.activemq.artemis.core.remoting.impl.netty;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.FastThreadLocal;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQInterruptedException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.core.security.ActiveMQPrincipal;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.BaseConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.activemq.artemis.utils.ConfigurationHelper;
import org.apache.activemq.artemis.utils.Env;
import org.apache.activemq.artemis.utils.IPV6Util;
import org.apache.activemq.artemis.utils.SocketAddressUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyConnection implements Connection {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int DEFAULT_WAIT_MILLIS = 10_000;

   protected final Channel channel;
   private final BaseConnectionLifeCycleListener<?> listener;
   private final boolean directDeliver;
   private final Map<String, Object> configuration;
   /**
    * if {@link #isWritable(ReadyListener)} returns false, we add a callback here for when the connection (or Netty
    * Channel) becomes available again.
    */
   private final List<ReadyListener> readyListeners = new ArrayList<>();
   private static final FastThreadLocal<ArrayList<ReadyListener>> localListenersPool = new FastThreadLocal<>();

   private final boolean batchingEnabled;

   private boolean closed;
   private RemotingConnection protocolConnection;

   private boolean ready = true;

   public NettyConnection(final Map<String, Object> configuration,
                          final Channel channel,
                          final BaseConnectionLifeCycleListener<?> listener,
                          boolean batchingEnabled,
                          boolean directDeliver) {
      this.configuration = configuration;

      this.channel = Objects.requireNonNull(channel);

      this.listener = listener;

      this.directDeliver = directDeliver;

      this.batchingEnabled = batchingEnabled;
   }

   @Override
   public EventLoop getEventLoop() {
      return channel.eventLoop();
   }

   private static void waitFor(ChannelPromise promise, long millis) {
      try {
         final boolean completed = promise.await(millis);
         if (!completed) {
            ActiveMQClientLogger.LOGGER.timeoutFlushingPacket();
         }
      } catch (InterruptedException e) {
         throw new ActiveMQInterruptedException(e);
      }
   }

   /**
    * {@return an estimation of the current size of the write buffer in the channel}
    */
   private static long batchBufferSize(Channel channel) {
      final ChannelOutboundBuffer outboundBuffer = channel.unsafe().outboundBuffer();
      if (outboundBuffer == null) {
         return 0;
      }
      return outboundBuffer.totalPendingWriteBytes();
   }

   public final Channel getNettyChannel() {
      return channel;
   }

   @Override
   public final void setAutoRead(boolean autoRead) {
      channel.config().setAutoRead(autoRead);
   }

   @Override
   public final boolean isWritable(ReadyListener callback) {
      synchronized (readyListeners) {
         if (!ready) {
            readyListeners.add(callback);
         }

         return ready;
      }
   }

   @Override
   public boolean isOpen() {
      return channel.isOpen();
   }

   @Override
   public final void fireReady(final boolean ready) {
      ArrayList<ReadyListener> readyToCall = localListenersPool.get();
      if (readyToCall != null) {
         localListenersPool.set(null);
      }
      synchronized (readyListeners) {
         this.ready = ready;

         if (ready) {
            final int size = this.readyListeners.size();
            if (readyToCall != null) {
               readyToCall.ensureCapacity(size);
            }
            try {
               for (int i = 0; i < size; i++) {
                  final ReadyListener readyListener = readyListeners.get(i);
                  if (readyListener == null) {
                     break;
                  }
                  if (readyToCall == null) {
                     readyToCall = new ArrayList<>(size);
                  }
                  readyToCall.add(readyListener);
               }
            } finally {
               readyListeners.clear();
            }
         }
      }
      if (readyToCall != null) {
         try {
            readyToCall.forEach(readyListener -> {
               try {
                  readyListener.readyForWriting();
               } catch (Throwable logOnly) {
                  ActiveMQClientLogger.LOGGER.failedToSetChannelReadyForWriting(logOnly);
               }
            });
         } catch (Throwable t) {
            ActiveMQClientLogger.LOGGER.failedToSetChannelReadyForWriting(t);
         } finally {
            readyToCall.clear();
            if (localListenersPool.get() != null) {
               localListenersPool.set(readyToCall);
            }
         }
      }
   }

   @Override
   public final void forceClose() {
      if (channel != null) {
         try {
            channel.close();
         } catch (Throwable e) {
            ActiveMQClientLogger.LOGGER.failedForceClose(e);
         }
      }
   }

   /**
    * This is exposed so users would have the option to look at any data through interceptors
    */
   public final Channel getChannel() {
      return channel;
   }

   @Override
   public final RemotingConnection getProtocolConnection() {
      return protocolConnection;
   }

   @Override
   public final void setProtocolConnection(RemotingConnection protocolConnection) {
      this.protocolConnection = protocolConnection;
   }

   @Override
   public final void close() {
      if (closed) {
         return;
      }
      EventLoop eventLoop = channel.eventLoop();
      boolean inEventLoop = eventLoop.inEventLoop();
      //if we are in an event loop we need to close the channel after the writes have finished
      if (!inEventLoop) {
         closeChannel(channel, false);
      } else {
         eventLoop.execute(() -> {
            closeChannel(channel, true);
         });
      }

      closed = true;

      listener.connectionDestroyed(getID(), false);
   }

   @Override
   public ActiveMQBuffer createTransportBuffer(final int size) {
      try {
         return new ChannelBufferWrapper(channel.alloc().directBuffer(size), true);
      } catch (OutOfMemoryError oom) {
         final long totalPendingWriteBytes = batchBufferSize(this.channel);
         // I'm not using the ActiveMQLogger framework here, as I wanted the class name to be very specific here
         logger.warn("Trying to allocate {} bytes, System is throwing OutOfMemoryError on NettyConnection {}, there are currently pendingWrites: [NETTY] -> {} causes: {}",
                     size, this, totalPendingWriteBytes, oom.getMessage(), oom);
         throw oom;
      }
   }

   @Override
   public final Object getID() {
      return channel.id();
   }

   // This is called periodically to flush the batch buffer
   @Override
   public final void checkFlushBatchBuffer() {
      if (batchingEnabled) {
         // perform the flush only if necessary
         if (batchBufferSize(channel) > 0) {
            channel.flush();
         }
      }
   }

   @Override
   public final void write(final ActiveMQBuffer buffer) {
      write(buffer, false, false);
   }

   @Override
   public void write(ActiveMQBuffer buffer, boolean requestFlush) {
      final Channel channel = this.channel;
      final ByteBuf bytes = buffer.byteBuf();
      if (requestFlush) {
         channel.writeAndFlush(bytes, channel.voidPromise());
      } else {
         channel.write(bytes, channel.voidPromise());
      }
   }

   @Override
   public void flush() {
      checkConnectionState();
      this.channel.flush();
   }

   @Override
   public final void write(ActiveMQBuffer buffer, final boolean flush, final boolean batched) {
      write(buffer, flush, batched, null);
   }

   private void checkConnectionState() {
      if (this.closed || !this.channel.isActive()) {
         throw new IllegalStateException("Connection " + getID() + " closed or disconnected");
      }
   }

   @Override
   public final boolean blockUntilWritable(final long timeout, final TimeUnit timeUnit) {
      checkConnectionState();
      final boolean isAllowedToBlock = isAllowedToBlock();
      if (!isAllowedToBlock) {
         if (timeout > 0) {
            if (Env.isTestEnv()) {
               // this will only show when inside the testsuite.
               // we may great the log for FAILURE
               logger.warn("FAILURE! The code is using blockUntilWritable inside a Netty worker, which would block. The code will probably need fixing!", new Exception("trace"));
            }

            logger.debug("Calling blockUntilWritable using a thread where it's not allowed");
         }
         return channel.isWritable();
      } else {
         final long timeoutNanos = timeUnit.toNanos(timeout);
         final long deadline = System.nanoTime() + timeoutNanos;
         //choose wait time unit size
         final long parkNanos;
         //if is requested to wait more than a millisecond than we could use
         if (timeoutNanos >= 1_000_000L) {
            parkNanos = 100_000L;
         } else {
            //reduce it doesn't make sense, only a spin loop could be enough precise with the most OS
            parkNanos = 1000L;
         }
         boolean canWrite;
         while (!(canWrite = channel.isWritable()) && (System.nanoTime() - deadline) < 0) {
            //periodically check the connection state
            checkConnectionState();
            LockSupport.parkNanos(parkNanos);
         }
         return canWrite;
      }
   }

   private boolean isAllowedToBlock() {
      final EventLoop eventLoop = channel.eventLoop();
      final boolean inEventLoop = eventLoop.inEventLoop();
      return !inEventLoop;
   }

   @Override
   public final void write(ActiveMQBuffer buffer,
                           final boolean flush,
                           final boolean batched,
                           final ChannelFutureListener futureListener) {
      final int readableBytes = buffer.readableBytes();
      //no need to lock because the Netty's channel is thread-safe
      //and the order of write is ensured by the order of the write calls
      final Channel channel = this.channel;
      final ChannelPromise promise;
      if (flush || (futureListener != null)) {
         promise = channel.newPromise();
      } else {
         promise = channel.voidPromise();
      }
      final ChannelFuture future;
      final ByteBuf bytes = buffer.byteBuf();
      assert readableBytes >= 0;
      final boolean batchingEnabled = this.batchingEnabled;
      if (batchingEnabled && batched && !flush && channel.isWritable()) {
         future = channel.write(bytes, promise);
      } else {
         future = channel.writeAndFlush(bytes, promise);
      }
      if (futureListener != null) {
         future.addListener(futureListener);
      }
      if (flush) {
         //NOTE: this code path seems used only on RemotingConnection::disconnect
         flushAndWait(channel, promise);
      }
   }

   private static void flushAndWait(final Channel channel, final ChannelPromise promise) {
      if (!channel.eventLoop().inEventLoop()) {
         waitFor(promise, DEFAULT_WAIT_MILLIS);
      } else {
         logger.debug("Calling write with flush from a thread where it's not allowed");
      }
   }

   @Override
   public String getRemoteAddress() {
      return SocketAddressUtil.toString(channel.remoteAddress());
   }

   @Override
   public final String getLocalAddress() {
      String address = SocketAddressUtil.toString(channel.localAddress());
      if (address == null) {
         return null;
      }
      return IPV6Util.encloseHost(address);
   }

   @Override
   public final boolean isDirectDeliver() {
      return directDeliver;
   }

   //never allow this
   @Override
   public final ActiveMQPrincipal getDefaultActiveMQPrincipal() {
      return null;
   }

   @Override
   public final TransportConfiguration getConnectorConfig() {
      if (configuration != null) {
         return new TransportConfiguration(NettyConnectorFactory.class.getName(), this.configuration);
      } else {
         return null;
      }
   }

   @Override
   public final boolean isUsingProtocolHandling() {
      return true;
   }

   @Override
   public boolean isSameTarget(TransportConfiguration... configs) {
      boolean result = false;
      for (TransportConfiguration cfg : configs) {
         if (cfg == null) {
            continue;
         }
         if (NettyConnectorFactory.class.getName().equals(cfg.getFactoryClassName())) {
            int port1 = ConfigurationHelper.getIntProperty(TransportConstants.PORT_PROP_NAME, TransportConstants.DEFAULT_PORT, configuration);
            int port2 = ConfigurationHelper.getIntProperty(TransportConstants.PORT_PROP_NAME, TransportConstants.DEFAULT_PORT, cfg.getParams());
            if (port1 == port2) {
               //port same, check host
               Object hostParam = configuration.get(TransportConstants.HOST_PROP_NAME);
               if (hostParam != null) {
                  if (hostParam.equals(cfg.getParams().get(TransportConstants.HOST_PROP_NAME))) {
                     result = true;
                     break;
                  } else {
                     //check special 'localhost' case
                     if (isLocalhost((String) configuration.get(TransportConstants.HOST_PROP_NAME)) && isLocalhost((String) cfg.getParams().get(TransportConstants.HOST_PROP_NAME))) {
                        result = true;
                        break;
                     }
                  }
               } else if (cfg.getParams().get(TransportConstants.HOST_PROP_NAME) == null) {
                  result = true;
                  break;
               }
            }
         }
      }
      return result;
   }

   //here we consider 'localhost' is equivalent to '127.0.0.1'
   //other values of 127.0.0.x is not and the user makes sure
   //not to mix use of 'localhost' and '127.0.0.x'
   private boolean isLocalhost(String hostname) {
      return "127.0.0.1".equals(hostname) || "localhost".equals(hostname);
   }

   @Override
   public final String toString() {
      return super.toString() + "[ID=" + getID() + ", local= " + getLocalAddress() + ", remote=" + getRemoteAddress() + "]";
   }

   private void closeChannel(final Channel channel, boolean inEventLoop) {
      checkFlushBatchBuffer();
      // closing the channel results in closing any sslHandler first; SslHandler#close() was deprecated by netty
      ChannelFuture closeFuture = channel.close();
      if (!inEventLoop && !closeFuture.awaitUninterruptibly(DEFAULT_WAIT_MILLIS)) {
         ActiveMQClientLogger.LOGGER.timeoutClosingNettyChannel();
      }
   }

}