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
package org.apache.activemq.artemis.core.replication;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.EventLoop;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.util.internal.PlatformDependent;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQReplicationTimeooutException;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.journal.impl.JournalFile;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.persistence.impl.journal.AbstractJournalStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.ChannelHandler;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl.CHANNEL_ID;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationAddMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationAddTXMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationCommitMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationDeleteMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationDeleteTXMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationLargeMessageBeginMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationLargeMessageEndMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationLargeMessageWriteMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationLiveIsStoppingMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationLiveIsStoppingMessage.LiveStopping;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationPageEventMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationPageWriteMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationPrepareMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationResponseMessageV2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationStartSyncMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationSyncFileMessage;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnection;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.cluster.qourum.QuorumManager;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.jboss.logging.Logger;

/**
 * Manages replication tasks on the live server (that is the live server side of a "remote backup"
 * use case).
 * <p>
 * Its equivalent in the backup server is {@link ReplicationEndpoint}.
 *
 * @see ReplicationEndpoint
 */
public final class ReplicationManager implements ActiveMQComponent {

   private static final Logger logger = Logger.getLogger(ReplicationManager.class);

   public enum ADD_OPERATION_TYPE {
      UPDATE {
         @Override
         public byte toRecord() {
            return 0;
         }
      }, ADD {
         @Override
         public byte toRecord() {
            return 1;
         }
      }, EVENT {
         @Override
         public byte toRecord() {
            return 2;
         }
      };

      public abstract byte toRecord();

      public static ADD_OPERATION_TYPE toOperation(byte recordType) {
         switch (recordType) {
            case 0: // 0: it used to be false, we need to use 0 for compatibility reasons with writeBoolean on the channel
               return UPDATE;
            case 1: // 1: it used to be true, we need to use 1 for compatibility reasons with writeBoolean
               return ADD;
            case 2: // 2: this represents the new value
               return EVENT;
         }
         return ADD;
      }
   }

   private final ActiveMQServer server;

   private final ResponseHandler responseHandler = new ResponseHandler();

   private final Channel replicatingChannel;

   private boolean started;

   private volatile boolean enabled;

   private final Queue<OperationContext> pendingTokens = new ConcurrentLinkedQueue<>();

   private final ExecutorFactory ioExecutorFactory;

   private SessionFailureListener failureListener;

   private CoreRemotingConnection remotingConnection;

   private final long maxAllowedSlownessNanos;

   private final long initialReplicationSyncTimeout;

   private volatile boolean inSync = true;

   private final ReusableLatch synchronizationIsFinishedAcknowledgement = new ReusableLatch(0);

   private static final class ReplicatePacketRequest {

      final Packet packet;
      final OperationContext context;
      // Although this field is needed just during the initial sync,
      // the JVM field layout would likely left 4 bytes of wasted space without it
      // so it makes sense to use it instead.
      final ReusableLatch done;

      ReplicatePacketRequest(Packet packet, OperationContext context, ReusableLatch done) {
         this.packet = packet;
         this.context = context;
         this.done = done;
      }
   }

   private final Queue<ReplicatePacketRequest> replicatePacketRequests;
   private final Executor replicationStream;
   private final ScheduledExecutorService scheduledExecutorService;
   private ScheduledFuture<?> slowReplicationChecker;
   private long notWritableFrom;
   private boolean checkSlowReplication;
   private final ReadyListener onResume;
   private boolean isFlushing;
   private boolean awaitingResume;

   /**
    * @param remotingConnection
    */
   public ReplicationManager(ActiveMQServer server,
                             CoreRemotingConnection remotingConnection,
                             final long timeout,
                             final long initialReplicationSyncTimeout,
                             final ExecutorFactory ioExecutorFactory) {
      this.server = server;
      this.ioExecutorFactory = ioExecutorFactory;
      this.initialReplicationSyncTimeout = initialReplicationSyncTimeout;
      this.replicatingChannel = remotingConnection.getChannel(CHANNEL_ID.REPLICATION.id, -1);
      this.remotingConnection = remotingConnection;
      final Connection transportConnection = this.remotingConnection.getTransportConnection();
      if (transportConnection instanceof NettyConnection) {
         final EventLoop eventLoop = ((NettyConnection) transportConnection).getNettyChannel().eventLoop();
         this.replicationStream = eventLoop;
         this.scheduledExecutorService = eventLoop;
      } else {
         this.replicationStream = ioExecutorFactory.getExecutor();
         this.scheduledExecutorService = null;
      }
      this.maxAllowedSlownessNanos = timeout > 0 ? TimeUnit.MILLISECONDS.toNanos(timeout) : -1;
      this.replicatePacketRequests = PlatformDependent.newMpscQueue();
      this.slowReplicationChecker = null;
      this.notWritableFrom = Long.MAX_VALUE;
      this.awaitingResume = false;
      this.onResume = this::resume;
      this.isFlushing = false;
      this.checkSlowReplication = false;
   }

   public void appendUpdateRecord(final byte journalID,
                                  final ADD_OPERATION_TYPE operation,
                                  final long id,
                                  final byte recordType,
                                  final Persister persister,
                                  final Object record) throws Exception {
      if (enabled) {
         sendReplicatePacket(new ReplicationAddMessage(journalID, operation, id, recordType, persister, record));
      }
   }

   public void appendDeleteRecord(final byte journalID, final long id) throws Exception {
      if (enabled) {
         sendReplicatePacket(new ReplicationDeleteMessage(journalID, id));
      }
   }

   public void appendAddRecordTransactional(final byte journalID,
                                            final ADD_OPERATION_TYPE operation,
                                            final long txID,
                                            final long id,
                                            final byte recordType,
                                            final Persister persister,
                                            final Object record) throws Exception {
      if (enabled) {
         sendReplicatePacket(new ReplicationAddTXMessage(journalID, operation, txID, id, recordType, persister, record));
      }
   }

   public void appendCommitRecord(final byte journalID,
                                  final long txID,
                                  boolean sync,
                                  final boolean lineUp) throws Exception {
      if (enabled) {
         sendReplicatePacket(new ReplicationCommitMessage(journalID, false, txID), lineUp);
      }
   }

   public void appendDeleteRecordTransactional(final byte journalID,
                                               final long txID,
                                               final long id,
                                               final EncodingSupport record) throws Exception {
      if (enabled) {
         sendReplicatePacket(new ReplicationDeleteTXMessage(journalID, txID, id, record));
      }
   }

   public void appendDeleteRecordTransactional(final byte journalID, final long txID, final long id) throws Exception {
      if (enabled) {
         sendReplicatePacket(new ReplicationDeleteTXMessage(journalID, txID, id, NullEncoding.instance));
      }
   }

   public void appendPrepareRecord(final byte journalID,
                                   final long txID,
                                   final EncodingSupport transactionData) throws Exception {
      if (enabled) {
         sendReplicatePacket(new ReplicationPrepareMessage(journalID, txID, transactionData));
      }
   }

   public void appendRollbackRecord(final byte journalID, final long txID) throws Exception {
      if (enabled) {
         sendReplicatePacket(new ReplicationCommitMessage(journalID, true, txID));
      }
   }

   /**
    * @param storeName
    * @param pageNumber
    */
   public void pageClosed(final SimpleString storeName, final int pageNumber) {
      if (enabled) {
         sendReplicatePacket(new ReplicationPageEventMessage(storeName, pageNumber, false));
      }
   }

   public void pageDeleted(final SimpleString storeName, final int pageNumber) {
      if (enabled) {
         sendReplicatePacket(new ReplicationPageEventMessage(storeName, pageNumber, true));
      }
   }

   public void pageWrite(final PagedMessage message, final int pageNumber) {
      if (enabled) {
         sendReplicatePacket(new ReplicationPageWriteMessage(message, pageNumber));
      }
   }

   public void largeMessageBegin(final long messageId) {
      if (enabled) {
         sendReplicatePacket(new ReplicationLargeMessageBeginMessage(messageId));
      }
   }

   //we pass in storageManager to generate ID only if enabled
   public void largeMessageDelete(final Long messageId, JournalStorageManager storageManager) {
      if (enabled) {
         long pendingRecordID = storageManager.generateID();
         sendReplicatePacket(new ReplicationLargeMessageEndMessage(messageId, pendingRecordID, true));
      }
   }

   public void largeMessageClosed(final Long messageId, JournalStorageManager storageManager) {
      if (enabled) {
         sendReplicatePacket(new ReplicationLargeMessageEndMessage(messageId, -1, false));
      }
   }

   public void largeMessageWrite(final long messageId, final byte[] body) {
      if (enabled) {
         sendReplicatePacket(new ReplicationLargeMessageWriteMessage(messageId, body));
      }
   }

   @Override
   public synchronized boolean isStarted() {
      return started;
   }

   @Override
   public synchronized void start() throws ActiveMQException {
      if (started) {
         throw new IllegalStateException("ReplicationManager is already started");
      }

      replicatingChannel.setHandler(responseHandler);
      failureListener = new ReplicatedSessionFailureListener();
      remotingConnection.addFailureListener(failureListener);
      // only Netty connections can enable slow replication checker
      if (scheduledExecutorService != null && maxAllowedSlownessNanos >= 0) {
         long periodNanos = maxAllowedSlownessNanos / 10;
         if (periodNanos > TimeUnit.SECONDS.toNanos(1)) {
            periodNanos = TimeUnit.SECONDS.toNanos(1);
         } else if (periodNanos < TimeUnit.MILLISECONDS.toNanos(100)) {
            logger.warnf("The cluster call timeout is too low ie %d ms: consider raising it to save CPU",
                         TimeUnit.NANOSECONDS.toMillis(maxAllowedSlownessNanos));
            periodNanos = TimeUnit.MILLISECONDS.toNanos(100);
         }
         logger.debugf("Slow replication checker is running with a period of %d ms", TimeUnit.NANOSECONDS.toMillis(periodNanos));
         // The slow detection has been implemented by using an always-on timer task
         // instead of triggering one each time we detect an un-writable channel because:
         // - getting temporarily an un-writable channel is rather common under load and scheduling/cancelling a
         //   timed task is a CPU and GC intensive operation
         // - choosing a period of 100-1000 ms lead to a reasonable and constant CPU utilization while idle too
         slowReplicationChecker = scheduledExecutorService.scheduleAtFixedRate(this::checkSlowReplication,
                                                                               periodNanos, periodNanos, TimeUnit.NANOSECONDS);
      }

      started = true;

      enabled = true;
   }

   @Override
   public void stop() throws Exception {
      stop(true);
   }

   public void stop(boolean clearTokens) throws Exception {
      synchronized (this) {
         if (!started) {
            logger.trace("Stopping being ignored as it hasn't been started");
            return;
         }

         started = false;
      }

      if (logger.isTraceEnabled()) {
         logger.trace("stop(clearTokens=" + clearTokens + ")", new Exception("Trace"));
      }

      // This is to avoid the write holding a lock while we are trying to close it
      if (replicatingChannel != null) {
         replicatingChannel.close();
         replicatingChannel.getConnection().getTransportConnection().fireReady(true);
      }

      if (slowReplicationChecker != null) {
         slowReplicationChecker.cancel(false);
         slowReplicationChecker = null;
      }

      enabled = false;

      if (clearTokens) {
         clearReplicationTokens();
      }

      RemotingConnection toStop = remotingConnection;
      if (toStop != null) {
         toStop.removeFailureListener(failureListener);
         toStop.destroy();
      }
      remotingConnection = null;
   }

   /**
    * Completes any pending operations.
    * <p>
    * This can be necessary in case the live loses connection to the backup (network failure, or
    * backup crashing).
    */
   public void clearReplicationTokens() {
      logger.trace("clearReplicationTokens initiating");
      while (!pendingTokens.isEmpty()) {
         OperationContext ctx = pendingTokens.poll();
         logger.trace("Calling ctx.replicationDone()");
         try {
            ctx.replicationDone();
         } catch (Throwable e) {
            ActiveMQServerLogger.LOGGER.errorCompletingCallbackOnReplicationManager(e);
         }
      }
      logger.trace("clearReplicationTokens finished");
   }

   /**
    * A list of tokens that are still waiting for replications to be completed
    */
   public Set<OperationContext> getActiveTokens() {

      LinkedHashSet<OperationContext> activeContexts = new LinkedHashSet<>();

      // The same context will be replicated on the pending tokens...
      // as the multiple operations will be replicated on the same context

      for (OperationContext ctx : pendingTokens) {
         activeContexts.add(ctx);
      }

      return activeContexts;

   }

   private OperationContext sendReplicatePacket(final Packet packet) {
      return sendReplicatePacket(packet, true);
   }

   private OperationContext sendReplicatePacket(final Packet packet, boolean lineUp) {
      return sendReplicatePacket(packet, lineUp, null);
   }

   private OperationContext sendReplicatePacket(final Packet packet, boolean lineUp, ReusableLatch done) {
      if (!enabled) {
         packet.release();
         return null;
      }

      final OperationContext repliToken = OperationContextImpl.getContext(ioExecutorFactory);
      if (lineUp) {
         repliToken.replicationLineUp();
      }
      final ReplicatePacketRequest request = new ReplicatePacketRequest(packet, repliToken, done);
      replicatePacketRequests.add(request);
      replicationStream.execute(() -> {
         if (enabled) {
            sendReplicatedPackets(false);
         } else {
            releaseReplicatedPackets(replicatePacketRequests);
         }
      });

      return repliToken;
   }

   private void releaseReplicatedPackets(Queue<ReplicatePacketRequest> requests) {
      assert checkEventLoop();
      ReplicatePacketRequest req;
      while ((req = requests.poll()) != null) {
         req.packet.release();
         req.context.replicationDone();
         if (req.done != null) {
            req.done.countDown();
         }
      }
   }

   private void checkSlowReplication() {
      if (!enabled) {
         return;
      }
      assert checkEventLoop();
      if (!checkSlowReplication) {
         return;
      }
      final boolean isWritable = replicatingChannel.getConnection().blockUntilWritable(0);
      if (isWritable) {
         checkSlowReplication = false;
         return;
      }
      final long elapsedNanosNotWritable = System.nanoTime() - notWritableFrom;
      if (elapsedNanosNotWritable >= maxAllowedSlownessNanos) {
         checkSlowReplication = false;
         releaseReplicatedPackets(replicatePacketRequests);
         try {
            ActiveMQServerLogger.LOGGER.slowReplicationResponse();
            stop();
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
         }
      }
   }

   private void resume() {
      sendReplicatedPackets(true);
   }

   private void sendReplicatedPackets(boolean resume) {
      assert checkEventLoop();
      if (resume) {
         awaitingResume = false;
      }
      // We try to:
      // - save recursive calls of resume due to flushConnection
      // - saving flush pending writes *if* the OS hasn't notified that's writable again
      if (awaitingResume || isFlushing || !enabled) {
         return;
      }
      if (replicatePacketRequests.isEmpty()) {
         return;
      }
      isFlushing = true;
      final CoreRemotingConnection connection = replicatingChannel.getConnection();
      try {
         while (connection.blockUntilWritable(0)) {
            checkSlowReplication = false;
            final ReplicatePacketRequest request = replicatePacketRequests.poll();
            if (request == null) {
               replicatingChannel.flushConnection();
               // given that there isn't any more work to do, we're not interested
               // to check writability state to trigger the slow connection check
               return;
            }
            pendingTokens.add(request.context);
            final Packet pack = request.packet;
            final ReusableLatch done = request.done;
            if (done != null) {
               done.countDown();
            }
            replicatingChannel.send(pack, false);
         }
         replicatingChannel.flushConnection();
         assert !awaitingResume;
         // we care about writability just if there is some work to do
         if (!replicatePacketRequests.isEmpty()) {
            if (!connection.isWritable(onResume)) {
               checkSlowReplication = true;
               notWritableFrom = System.nanoTime();
               awaitingResume = true;
            } else {
               // submit itself again to continue draining:
               // we're not trying it again here to save read starvation
               // NOTE: maybe it's redundant because there are already others in-flights requests
               replicationStream.execute(() -> sendReplicatedPackets(false));
            }
         }
      } catch (Throwable t) {
         assert !(t instanceof AssertionError) : t.getMessage();
         if (!connection.getTransportConnection().isOpen()) {
            // that's an handled state: right after this cleanup is expected to be stopped/closed
            // or get the failure listener to be called!
            logger.trace("Transport connection closed: cleaning up replicate tokens", t);
            releaseReplicatedPackets(replicatePacketRequests);
            // cleanup ReadyListener without triggering any further write/flush
            connection.getTransportConnection().fireReady(true);
         } else {
            logger.warn("Unexpected error while flushing replicate packets", t);
         }
      } finally {
         isFlushing = false;
      }
   }

   private boolean checkEventLoop() {
      if (!(replicationStream instanceof SingleThreadEventLoop)) {
         return true;
      }
      final SingleThreadEventLoop eventLoop = (SingleThreadEventLoop) replicationStream;
      return eventLoop.inEventLoop();
   }

   /**
    * @throws IllegalStateException By default, all replicated packets generate a replicated
    *                               response. If your packets are triggering this exception, it may be because the
    *                               packets were not sent with {@link #sendReplicatePacket(Packet)}.
    */
   private void replicated() {
      assert checkEventLoop();
      OperationContext ctx = pendingTokens.poll();

      if (ctx == null) {
         ActiveMQServerLogger.LOGGER.missingReplicationTokenOnQueue();
         return;
      }
      ctx.replicationDone();
   }


   private final class ReplicatedSessionFailureListener implements SessionFailureListener {

      @Override
      public void connectionFailed(final ActiveMQException me, boolean failedOver) {
         if (me.getType() == ActiveMQExceptionType.DISCONNECTED) {
            // Backup has shut down - no need to log a stack trace
            ActiveMQServerLogger.LOGGER.replicationStopOnBackupShutdown();
         } else {
            ActiveMQServerLogger.LOGGER.replicationStopOnBackupFail(me);
         }

         try {
            stop();
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.errorStoppingReplication(e);
         }
      }

      @Override
      public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
         connectionFailed(me, failedOver);
      }

      @Override
      public void beforeReconnect(final ActiveMQException me) {
      }
   }

   private final class ResponseHandler implements ChannelHandler {

      @Override
      public void handlePacket(final Packet packet) {
         if (packet.getType() == PacketImpl.REPLICATION_RESPONSE || packet.getType() == PacketImpl.REPLICATION_RESPONSE_V2) {
            replicated();
            if (packet.getType() == PacketImpl.REPLICATION_RESPONSE_V2) {
               ReplicationResponseMessageV2 replicationResponseMessage = (ReplicationResponseMessageV2) packet;
               if (replicationResponseMessage.isSynchronizationIsFinishedAcknowledgement()) {
                  synchronizationIsFinishedAcknowledgement.countDown();
               }
            }
         }
      }

   }

   private static final class NullEncoding implements EncodingSupport {

      static final NullEncoding instance = new NullEncoding();

      @Override
      public void decode(final ActiveMQBuffer buffer) {
      }

      @Override
      public void encode(final ActiveMQBuffer buffer) {
      }

      @Override
      public int getEncodeSize() {
         return 0;
      }
   }

   /**
    * Sends the whole content of the file to be duplicated.
    *
    * @throws ActiveMQException
    * @throws Exception
    */
   public void syncJournalFile(JournalFile jf, AbstractJournalStorageManager.JournalContent content) throws Exception {
      if (!enabled) {
         return;
      }
      SequentialFile file = jf.getFile().cloneFile();
      try {
         ActiveMQServerLogger.LOGGER.replicaSyncFile(file, file.size());
         sendLargeFile(content, null, jf.getFileID(), file, Long.MAX_VALUE);
      } finally {
         if (file.isOpen())
            file.close();
      }
   }

   public void syncLargeMessageFile(SequentialFile file, long size, long id) throws Exception {
      if (enabled) {
         sendLargeFile(null, null, id, file, size);
      }
   }

   public void syncPages(SequentialFile file, long id, SimpleString queueName) throws Exception {
      if (enabled)
         sendLargeFile(null, queueName, id, file, Long.MAX_VALUE);
   }

   /**
    * Sends large files in reasonably sized chunks to the backup during replication synchronization.
    *
    * @param content        journal type or {@code null} for large-messages and pages
    * @param pageStore      page store name for pages, or {@code null} otherwise
    * @param id             journal file id or (large) message id
    * @param file
    * @param maxBytesToSend maximum number of bytes to read and send from the file
    * @throws Exception
    */
   private void sendLargeFile(AbstractJournalStorageManager.JournalContent content,
                              SimpleString pageStore,
                              final long id,
                              SequentialFile file,
                              long maxBytesToSend) throws Exception {
      if (!enabled)
         return;
      if (!file.isOpen()) {
         file.open();
      }
      final int size = 32 * 1024;

      int flowControlSize = 10;

      int packetsSent = 0;
      final ReusableLatch flushed = new ReusableLatch(1);

      try {
         try (FileInputStream fis = new FileInputStream(file.getJavaFile()); FileChannel channel = fis.getChannel()) {

            // We can afford having a single buffer here for this entire loop
            // because sendReplicatePacket will encode the packet as a NettyBuffer
            // through ActiveMQBuffer class leaving this buffer free to be reused on the next copy
            while (true) {
               final ByteBuf buffer = PooledByteBufAllocator.DEFAULT.directBuffer(size, size);
               buffer.clear();
               ByteBuffer byteBuffer = buffer.writerIndex(size).readerIndex(0).nioBuffer();
               final int bytesRead = channel.read(byteBuffer);
               int toSend = bytesRead;
               if (bytesRead > 0) {
                  if (bytesRead >= maxBytesToSend) {
                     toSend = (int) maxBytesToSend;
                     maxBytesToSend = 0;
                  } else {
                     maxBytesToSend = maxBytesToSend - bytesRead;
                  }
               }
               if (logger.isDebugEnabled()) {
                  logger.debugf("sending %d bytes on file %s", buffer.writerIndex(), file.getFileName());
               }
               // sending -1 or 0 bytes will close the file at the backup
               final boolean lastPacket = bytesRead == -1 || bytesRead == 0 || maxBytesToSend == 0;
               final boolean flowControlCheck = (packetsSent % flowControlSize == 0) || lastPacket;
               if (flowControlCheck) {
                  flushed.setCount(1);
                  sendReplicatePacket(new ReplicationSyncFileMessage(content, pageStore, id, toSend, buffer), true, flushed);
                  awaitFlushOfReplicationStream(flushed);
               } else {
                  sendReplicatePacket(new ReplicationSyncFileMessage(content, pageStore, id, toSend, buffer), true);
               }
               packetsSent++;

               if (lastPacket)
                  break;
            }
         }
      } finally {
         if (file.isOpen())
            file.close();
      }
   }

   private void awaitFlushOfReplicationStream(ReusableLatch flushed) throws Exception {
      if (!flushed.await(this.initialReplicationSyncTimeout, TimeUnit.MILLISECONDS)) {
         throw ActiveMQMessageBundle.BUNDLE.replicationSynchronizationTimeout(initialReplicationSyncTimeout);
      }
   }

   /**
    * Reserve the following fileIDs in the backup server.
    *
    * @param datafiles
    * @param contentType
    * @throws ActiveMQException
    */
   public void sendStartSyncMessage(JournalFile[] datafiles,
                                    AbstractJournalStorageManager.JournalContent contentType,
                                    String nodeID,
                                    boolean allowsAutoFailBack) throws ActiveMQException {
      if (enabled)
         sendReplicatePacket(new ReplicationStartSyncMessage(datafiles, contentType, nodeID, allowsAutoFailBack));
   }

   /**
    * Informs backup that data synchronization is done.
    * <p>
    * So if 'live' fails, the (up-to-date) backup now may take over its duties. To do so, it must
    * know which is the live's {@code nodeID}.
    *
    * @param nodeID
    */
   public void sendSynchronizationDone(String nodeID, long initialReplicationSyncTimeout, IOCriticalErrorListener criticalErrorListener) throws ActiveMQReplicationTimeooutException {
      if (enabled) {

         if (logger.isTraceEnabled()) {
            logger.trace("sendSynchronizationDone ::" + nodeID + ", " + initialReplicationSyncTimeout);
         }

         synchronizationIsFinishedAcknowledgement.countUp();
         sendReplicatePacket(new ReplicationStartSyncMessage(nodeID, server.getNodeManager().getNodeActivationSequence()));
         try {
            if (!synchronizationIsFinishedAcknowledgement.await(initialReplicationSyncTimeout)) {
               ActiveMQReplicationTimeooutException exception = ActiveMQMessageBundle.BUNDLE.replicationSynchronizationTimeout(initialReplicationSyncTimeout);

               if (server != null) {
                  try {
                     ClusterManager clusterManager = server.getClusterManager();
                     if (clusterManager != null) {
                        QuorumManager manager = clusterManager.getQuorumManager();
                        if (criticalErrorListener != null && manager != null && manager.getMaxClusterSize() <= 2) {
                           criticalErrorListener.onIOException(exception, exception.getMessage(), null);
                        }
                     }
                  } catch (Throwable e) {
                     // if NPE or anything else, continue as nothing changed
                     logger.warn(e.getMessage(), e);
                  }
               }

               logger.trace("sendSynchronizationDone wasn't finished in time");
               throw exception;
            }
         } catch (InterruptedException e) {
            logger.debug(e);
         }
         inSync = false;

         logger.trace("sendSynchronizationDone finished");
      }
   }

   /**
    * Reserves several LargeMessage IDs in the backup.
    * <p>
    * Doing this before hand removes the need of synchronizing large-message deletes with the
    * largeMessageSyncList.
    *
    * @param largeMessages
    */
   public void sendLargeMessageIdListMessage(Map<Long, Pair<String, Long>> largeMessages) {
      ArrayList<Long> idsToSend;
      idsToSend = new ArrayList<>(largeMessages.keySet());

      if (enabled)
         sendReplicatePacket(new ReplicationStartSyncMessage(idsToSend));
   }

   /**
    * Notifies the backup that the live server is stopping.
    * <p>
    * This notification allows the backup to skip quorum voting (or any other measure to avoid
    * 'split-brain') and do a faster fail-over.
    *
    * @return
    */
   public OperationContext sendLiveIsStopping(final LiveStopping finalMessage) {
      logger.debug("LIVE IS STOPPING?!? message=" + finalMessage + " enabled=" + enabled);
      if (enabled) {
         logger.debug("LIVE IS STOPPING?!? message=" + finalMessage + " " + enabled);
         return sendReplicatePacket(new ReplicationLiveIsStoppingMessage(finalMessage));
      }
      return null;
   }

   /**
    * Used while stopping the server to ensure that we freeze communications with the backup.
    *
    * @return remoting connection with the backup
    */
   public CoreRemotingConnection getBackupTransportConnection() {
      return remotingConnection;
   }

   /**
    * @return
    */
   public boolean isSynchronizing() {
      return inSync;
   }
}
