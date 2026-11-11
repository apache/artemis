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
package org.apache.activemq.artemis.core.remoting.impl.invm;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQInterruptedException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.security.ActiveMQPrincipal;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.BaseConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class InVMConnection implements Connection {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final BufferHandler handler;

   private final BaseConnectionLifeCycleListener listener;

   private final String id;

   private boolean closed;

   // Used on tests
   private static boolean flushEnabled = true;

   private final int serverID;

   private final ArtemisExecutor executor;

   private volatile boolean closing;

   private final ActiveMQPrincipal defaultActiveMQPrincipal;

   private RemotingConnection protocolConnection;

   private boolean bufferPoolingEnabled = TransportConstants.DEFAULT_BUFFER_POOLING;

   private boolean directDeliver = TransportConstants.DEFAULT_DIRECT_DELIVER;

   public InVMConnection(final int serverID,
                         final BufferHandler handler,
                         final BaseConnectionLifeCycleListener listener,
                         final ArtemisExecutor executor) {
      this(serverID, UUIDGenerator.getInstance().generateSimpleStringUUID().toString(), handler, listener, executor);
   }

   public InVMConnection(final int serverID,
                         final String id,
                         final BufferHandler handler,
                         final BaseConnectionLifeCycleListener listener,
                         final ArtemisExecutor executor) {
      this(serverID, id, handler, listener, executor, null);
   }

   public InVMConnection(final int serverID,
                         final String id,
                         final BufferHandler handler,
                         final BaseConnectionLifeCycleListener listener,
                         final ArtemisExecutor executor,
                         final ActiveMQPrincipal defaultActiveMQPrincipal) {

      this.serverID = serverID;

      this.handler = handler;

      this.listener = listener;

      this.id = id;

      this.executor = executor;

      this.defaultActiveMQPrincipal = defaultActiveMQPrincipal;
   }

   public void setEnableBufferPooling(boolean enableBufferPooling) {
      this.bufferPoolingEnabled = enableBufferPooling;
   }

   @Override
   public void forceClose() {
      // no op
   }

   @Override
   public boolean isWritable(ReadyListener listener) {
      return true;
   }

   @Override
   public boolean isOpen() {
      return true;
   }

   @Override
   public void fireReady(boolean ready) {
   }

   @Override
   public RemotingConnection getProtocolConnection() {
      return this.protocolConnection;
   }

   @Override
   public void setProtocolConnection(RemotingConnection connection) {
      this.protocolConnection = connection;
   }

   @Override
   public void close() {
      internalClose(false);
   }

   private void internalClose(boolean failed) {
      if (closing) {
         return;
      }

      closing = true;

      synchronized (this) {
         if (!closed) {
            listener.connectionDestroyed(id, failed);

            closed = true;
         }
      }
   }

   @Override
   public void disconnect() {
      internalClose(true);
   }

   @Override
   public void setAutoRead(boolean autoRead) {
      // nothing to be done on the INVM.
      // maybe we could eventually implement something, but not needed now
   }

   @Override
   public ActiveMQBuffer createTransportBuffer(final int size) {
      if (bufferPoolingEnabled) {
         return ActiveMQBuffers.pooledBuffer(size);
      }
      return ActiveMQBuffers.dynamicBuffer(size);
   }

   @Override
   public Object getID() {
      return id;
   }

   @Override
   public void checkFlushBatchBuffer() {
   }

   @Override
   public void write(ActiveMQBuffer buffer, boolean requestFlush) {
      write(buffer, false, false, null);
   }

   @Override
   public void write(final ActiveMQBuffer buffer) {
      write(buffer, false, false, null);
   }

   @Override
   public void write(final ActiveMQBuffer buffer, final boolean flush, final boolean batch) {
      write(buffer, flush, batch, null);
   }

   @Override
   public void write(final ActiveMQBuffer buffer,
                     final boolean flush,
                     final boolean batch,
                     final ChannelFutureListener futureListener) {

      try {
         executor.execute(new Runnable() {
            @Override
            public void run() {
               try {
                  if (!closed) {
                     buffer.readInt(); // read and discard

                     logger.trace("{}::Sending inVM packet", InVMConnection.this);

                     handler.bufferReceived(id, buffer);
                     if (futureListener != null) {
                        futureListener.operationComplete(null);
                     }
                  }
               } catch (Exception e) {
                  final String msg = "Failed to write to handler on connector " + this;
                  ActiveMQServerLogger.LOGGER.errorWritingToInvmConnector(this, e);
                  throw new IllegalStateException(msg, e);
               } finally {
                  buffer.release();

                  logger.trace("{}::packet sent done", InVMConnection.this);
               }
            }
         });

         if (flush && flushEnabled) {
            final CountDownLatch latch = new CountDownLatch(1);
            executor.execute(latch::countDown);

            try {
               if (!latch.await(10, TimeUnit.SECONDS)) {
                  ActiveMQServerLogger.LOGGER.timedOutFlushingInvmChannel();
               }
            } catch (InterruptedException e) {
               throw new ActiveMQInterruptedException(e);
            }
         }
      } catch (RejectedExecutionException e) {
         // Ignore - this can happen if server/client is shutdown and another request comes in
      }

   }

   @Override
   public EventLoop getEventLoop() {
      throw new NotImplementedException("Event Loop and Netty is not supported on the inVM Connection.");
   }

   @Override
   public String getRemoteAddress() {
      return "invm:" + serverID;
   }

   @Override
   public String getLocalAddress() {
      return "invm:" + serverID;
   }

   public int getBatchingBufferSize() {
      return -1;
   }

   @Override
   public boolean isUsingProtocolHandling() {
      return false;
   }

   @Override
   public ActiveMQPrincipal getDefaultActiveMQPrincipal() {
      return defaultActiveMQPrincipal;
   }

   public static void setFlushEnabled(boolean enable) {
      flushEnabled = enable;
   }

   public Executor getExecutor() {
      return executor;
   }

   @Override
   public TransportConfiguration getConnectorConfig() {
      Map<String, Object> params = new HashMap<>();

      params.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, serverID);

      return new TransportConfiguration(InVMConnectorFactory.class.getName(), params);
   }

   @Override
   public boolean isDirectDeliver() {
      return directDeliver;
   }

   public void setDirectDeliver(boolean directDeliver) {
      this.directDeliver = directDeliver;
   }

   @Override
   public String toString() {
      return "InVMConnection [serverID=" + serverID + ", id=" + id + "]";
   }

   @Override
   public boolean isSameTarget(TransportConfiguration... configs) {
      boolean result = false;
      for (TransportConfiguration cfg : configs) {
         if (cfg == null) {
            continue;
         }
         if (InVMConnectorFactory.class.getName().equals(cfg.getFactoryClassName())) {
            //factory same, get id
            if (serverID == (int) cfg.getParams().get(TransportConstants.SERVER_ID_PROP_NAME)) {
               result = true;
               break;
            }
         }
      }
      return result;
   }
}
