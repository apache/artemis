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
package org.apache.activemq.artemis.core.server.impl;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.utils.UUID;
import org.apache.activemq.artemis.utils.UUIDGenerator;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

public abstract class FileBasedNodeManager extends NodeManager {

   protected static final byte FIRST_TIME_START = '0';
   public static final String SERVER_LOCK_NAME = "server.lock";
   public static final String SERVER_ACTIVATION_SEQUENCE_NAME = "server.activation.sequence";
   private static final String ACCESS_MODE = "rw";
   private final File directory;
   protected File serverLockFile;
   private final Path activationSequencePath;
   protected FileChannel channel;
   protected FileChannel activationSequenceChannel;

   public FileBasedNodeManager(boolean replicatedBackup, File directory) {
      super(replicatedBackup);
      this.directory = directory;
      if (directory != null) {
         directory.mkdirs();
      }
      activationSequencePath = new File(directory, SERVER_ACTIVATION_SEQUENCE_NAME).toPath();
   }

   /**
    * If {@code createIfNotExists} and activation sequence file doesn't exist yet, it returns {@code null}, otherwise it
    * opens it.
    * <p>
    * If {@code !createIfNotExists} it just open to create it.
    */
   private FileChannel useActivationSequenceChannel(final boolean createIfNotExists) throws IOException {
      FileChannel channel = this.activationSequenceChannel;
      if (channel != null) {
         return channel;
      }
      final OpenOption[] openOptions;
      if (!createIfNotExists) {
         if (!Files.exists(activationSequencePath)) {
            return null;
         }
         openOptions = new OpenOption[]{READ, WRITE};
      } else {
         openOptions = new OpenOption[]{READ, WRITE, CREATE};
      }
      channel = FileChannel.open(activationSequencePath, openOptions);
      activationSequenceChannel = channel;
      return channel;
   }

   @Override
   public long readNodeActivationSequence() throws NodeManagerException {
      if (!isStarted()) {
         throw new NodeManagerException(new IllegalStateException("node manager must be started first"));
      }
      try {
         final FileChannel channel = useActivationSequenceChannel(false);
         if (channel == null) {
            setNodeActivationSequence(NULL_NODE_ACTIVATION_SEQUENCE);
            return NULL_NODE_ACTIVATION_SEQUENCE;
         }
         final ByteBuffer tmpBuffer = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN);
         if (channel.read(tmpBuffer, 0) != Long.BYTES) {
            setNodeActivationSequence(NULL_NODE_ACTIVATION_SEQUENCE);
            return NULL_NODE_ACTIVATION_SEQUENCE;
         }
         tmpBuffer.flip();
         final long activationSequence = tmpBuffer.getLong(0);
         setNodeActivationSequence(activationSequence);
         return activationSequence;
      } catch (IOException ie) {
         throw new NodeManagerException(ie);
      }
   }

   @Override
   public void writeNodeActivationSequence(long sequence) throws NodeManagerException {
      if (!isStarted()) {
         throw new NodeManagerException(new IllegalStateException("node manager must be started first"));
      }
      try {
         final FileChannel channel = useActivationSequenceChannel(true);
         final ByteBuffer tmpBuffer = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN);
         tmpBuffer.putLong(0, sequence);
         channel.write(tmpBuffer, 0);
         channel.force(false);
         setNodeActivationSequence(sequence);
      } catch (IOException ie) {
         throw new NodeManagerException(ie);
      }
   }

   /**
    * Ensures existence of persistent information about the server's nodeID.
    * <p>
    * Roughly the different use cases are:
    * <ol>
    * <li>old server restarts: a server.lock file already exists and contains a nodeID.
    * <li>new server starting for the first time: no file exists, and we just *create* a new
    * UUID to use as nodeID
    * <li>replicated backup received its nodeID from its live: no file exists, we need to persist
    * the *current* nodeID
    * </ol>
    */
   protected synchronized void setUpServerLockFile() throws IOException {
      serverLockFile = newFile(SERVER_LOCK_NAME);

      boolean fileCreated = false;

      int count = 0;
      while (!serverLockFile.exists()) {
         try {
            fileCreated = serverLockFile.createNewFile();
         } catch (RuntimeException e) {
            ActiveMQServerLogger.LOGGER.nodeManagerCantOpenFile(serverLockFile, e);
            throw e;
         } catch (IOException e) {
            // on some OS's this may fail weirdly even tho the parent dir exists, retrying will work, some weird timing issue i think
            if (count < 5) {
               try {
                  Thread.sleep(100);
               } catch (InterruptedException e1) {
               }
               count++;
               continue;
            }
            ActiveMQServerLogger.LOGGER.nodeManagerCantOpenFile(serverLockFile, e);
            throw e;
         }
      }

      if (channel != null) {
         try {
            channel.close();
         } catch (IOException ignored) {
            // can ignore it: going to open a new file and that's the I/O to care about
         } finally {
            channel = null;
         }
      }

      @SuppressWarnings("resource")
      RandomAccessFile raFile = new RandomAccessFile(serverLockFile, ACCESS_MODE);

      channel = raFile.getChannel();

      if (fileCreated) {
         ByteBuffer id = ByteBuffer.allocateDirect(3);
         byte[] bytes = new byte[3];
         bytes[0] = FIRST_TIME_START;
         bytes[1] = FIRST_TIME_START;
         bytes[2] = FIRST_TIME_START;
         id.put(bytes, 0, 3);
         id.position(0);
         channel.write(id, 0);
         channel.force(true);
      }

      createNodeId();
   }

   protected final File newFile(final String fileName) {
      return new File(directory, fileName);
   }

   protected final synchronized void createNodeId() throws IOException {
      synchronized (nodeIDGuard) {
         ByteBuffer id = ByteBuffer.allocateDirect(16);
         int read = channel.read(id, 3);
         if (replicatedBackup) {
            id.position(0);
            id.put(getUUID().asBytes(), 0, 16);
            id.position(0);
            channel.write(id, 3);
            channel.force(true);
         } else if (read != 16) {
            setUUID(UUIDGenerator.getInstance().generateUUID());
            id.put(getUUID().asBytes(), 0, 16);
            id.position(0);
            channel.write(id, 3);
            channel.force(true);
         } else {
            byte[] bytes = new byte[16];
            id.position(0);
            id.get(bytes);
            setUUID(new UUID(UUID.TYPE_TIME_BASED, bytes));
         }
      }
   }

   @Override
   public synchronized void stop() throws Exception {
      FileChannel channelCopy = channel;
      try {
         if (channelCopy != null) {
            channelCopy.close();
         }
      } finally {
         try {
            setNodeActivationSequence(NULL_NODE_ACTIVATION_SEQUENCE);
            FileChannel dataVersionChannel = this.activationSequenceChannel;
            this.activationSequenceChannel = null;
            if (dataVersionChannel != null) {
               dataVersionChannel.close();
            }
         } finally {
            super.stop();
         }
      }
   }

   @Override
   public void stopBackup() throws NodeManagerException {
      synchronized (nodeIDGuard) {
         if (replicatedBackup && getNodeId() != null) {
            try {
               setUpServerLockFile();
               final long nodeActivationSequence = this.nodeActivationSequence;
               if (nodeActivationSequence != NULL_NODE_ACTIVATION_SEQUENCE) {
                  writeNodeActivationSequence(nodeActivationSequence);
               }
            } catch (IOException e) {
               throw new NodeManagerException(e);
            }
         }
         super.stopBackup();
      }
   }
}
