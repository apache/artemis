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
package org.apache.activemq.artemis.cli.commands.util;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.aio.AIOSequentialFileFactory;
import org.apache.activemq.artemis.core.io.mapped.MappedSequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.utils.ReusableLatch;

/**
 * It will perform a simple test to evaluate how many syncs a disk can make per second
 */
public class SyncCalculation {

   //uses nanoseconds to avoid any conversion cost: useful when performing operation on mapped journal without fsync or with RAM-like devices
   private static final long MAX_FLUSH_NANOS = TimeUnit.SECONDS.toNanos(5);

   /**
    * It will perform {@code tries} write tests of {@code blockSize * blocks} bytes and returning the lowest elapsed
    * time to perform a try.    *
    * <p>
    * Please configure {@code blocks >= -XX:CompileThreshold} (ie by default on most JVMs is 10000) to favour the best
    * JIT/OSR compilation (ie: Just In Time/On Stack Replacement) if the test is running on a temporary file-system (eg:
    * tmpfs on Linux) or without {@code fsync}.
    * <p>
    * NOTE: The write latencies are provided only if
    * {@code verbose && !(journalType == JournalType.ASYNCIO && !syncWrites)} (ie are used effective synchronous
    * writes).
    *
    * @param datafolder  the folder where the journal files will be stored
    * @param blockSize   the size in bytes of each write on the journal
    * @param blocks      the number of {@code blockSize} writes performed on each try
    * @param tries       the number of tests
    * @param verbose     {@code true} to make the output verbose, {@code false} otherwise
    * @param fsync       if {@code true} the test is performing full durable writes, {@code false} otherwise
    * @param syncWrites  if {@code true} each write is performed only if the previous one is completed, {@code false}
    *                    otherwise (ie each try will wait only the last write)
    * @param fileName    the name of the journal file used for the test
    * @param maxAIO      the max number of in-flight IO requests (if {@code journalType} will support it)
    * @param journalType the {@link JournalType} used for the tests
    * @return the lowest elapsed time (in {@link TimeUnit#MILLISECONDS}) to perform a try
    */
   public static long syncTest(File datafolder,
                               int blockSize,
                               int blocks,
                               int tries,
                               boolean verbose,
                               boolean fsync,
                               boolean syncWrites,
                               String fileName,
                               int maxAIO,
                               JournalType journalType,
                               ActionContext context) throws Exception {
      SequentialFileFactory factory = newFactory(datafolder, fsync, journalType, blockSize * blocks, maxAIO);

      if (factory instanceof AIOSequentialFileFactory) {
         factory.setAlignment(blockSize);
      }
      //the write latencies could be taken only when writes are effectively synchronous

      if (journalType == JournalType.ASYNCIO && syncWrites) {
         context.out.println();
         context.out.println("*******************************************************************************************");
         context.out.println("*** Notice: The recommendation for AsyncIO journal is to not use --sync-writes          ***");
         context.out.println("***         The measures here will be useful to understand your device                  ***");
         context.out.println("***         however the result here won't represent the best configuration option       ***");
         context.out.println("*******************************************************************************************");
         context.out.println();
      }

      if (verbose) {
         context.out.println("Using " + factory.getClass().getName() + " to calculate sync times, alignment=" + factory.getAlignment());
      }
      SequentialFile file = factory.createSequentialFile(fileName);
      //to be sure that a process/thread crash won't leave the dataFolder with garbage files
      file.getJavaFile().deleteOnExit();
      try {
         final ByteBuffer bufferBlock = allocateAlignedBlock(blockSize, factory);

         // making sure the blockSize matches the device
         blockSize = bufferBlock.remaining();

         file.delete();
         file.open();

         file.fill(blockSize * blocks);

         file.close();

         long[] result = new long[tries];

         final ReusableLatch latch = new ReusableLatch(0);

         IOCallback callback = new IOCallback() {
            @Override
            public void done() {
               latch.countDown();
            }

            @Override
            public void onError(int errorCode, String errorMessage) {

            }
         };

         DecimalFormat dcformat = new DecimalFormat("###.##");
         for (int ntry = 0; ntry < tries; ntry++) {

            if (verbose) {
               context.out.println("**************************************************");
               context.out.println(ntry + " of " + tries + " calculation");
            }
            file.open();
            file.position(0);
            long start = System.currentTimeMillis();
            for (int i = 0; i < blocks; i++) {
               bufferBlock.position(0);
               latch.countUp();
               file.writeDirect(bufferBlock, true, callback);

               if (syncWrites) {
                  flushLatch(latch);
               }
            }

            if (!syncWrites) {
               flushLatch(latch);
            }

            long end = System.currentTimeMillis();

            result[ntry] = (end - start);

            if (verbose) {
               double writesPerMillisecond = (double) blocks / (double) result[ntry];
               context.out.println("Time = " + result[ntry] + " milliseconds");
               context.out.println("Writes / millisecond = " + dcformat.format(writesPerMillisecond));
               context.out.println("bufferTimeout = " + toNanos(result[ntry], blocks, verbose, context));
               context.out.println("**************************************************");
            }
            file.close();

         }

         factory.releaseDirectBuffer(bufferBlock);

         long totalTime = Long.MAX_VALUE;
         for (int i = 0; i < tries; i++) {
            if (result[i] < totalTime) {
               totalTime = result[i];
            }
         }

         return totalTime;
      } finally {
         try {
            file.close();
         } catch (Exception e) {
         }
         try {
            file.delete();
         } catch (Exception e) {
         }
         try {
            factory.stop();
         } catch (Exception e) {
         }
      }
   }

   private static ByteBuffer allocateAlignedBlock(int blockSize, SequentialFileFactory factory) {
      final ByteBuffer bufferBlock = factory.newBuffer(blockSize);
      final byte[] block = new byte[bufferBlock.remaining()];
      Arrays.fill(block, (byte) 't');
      bufferBlock.put(block);
      bufferBlock.position(0);
      return bufferBlock;
   }

   private static void flushLatch(ReusableLatch latch) throws InterruptedException, IOException {
      if (!latch.await(MAX_FLUSH_NANOS, TimeUnit.NANOSECONDS)) {
         throw new IOException("Timed out on receiving IO callback");
      }
   }

   public static long toNanos(long time, long blocks, boolean verbose, ActionContext context) {

      double blocksPerMillisecond = (double) blocks / (double) (time);

      if (verbose) {
         context.out.println("Blocks per millisecond::" + blocksPerMillisecond);
      }

      long nanoSeconds = TimeUnit.NANOSECONDS.convert(1, TimeUnit.MILLISECONDS);

      long timeWait = (long) (nanoSeconds / blocksPerMillisecond);

      if (verbose) {
         context.out.println("your system could make a sync every " + timeWait + " nanoseconds, and this will be your timeout");
      }

      return timeWait;
   }

   private static SequentialFileFactory newFactory(File datafolder, boolean datasync, JournalType journalType, int fileSize, int maxAIO) {
      SequentialFileFactory factory;

      if (journalType == JournalType.ASYNCIO && !LibaioContext.isLoaded()) {
         journalType = JournalType.NIO;
      }

      switch (journalType) {

         case NIO:
            factory = new NIOSequentialFileFactory(datafolder, 1).setDatasync(datasync);
            ((NIOSequentialFileFactory) factory).disableBufferReuse();
            factory.start();
            return factory;
         case ASYNCIO:
            factory = new AIOSequentialFileFactory(datafolder, maxAIO).setDatasync(datasync);
            factory.start();
            ((AIOSequentialFileFactory) factory).disableBufferReuse();
            return factory;
         case MAPPED:
            factory = new MappedSequentialFileFactory(datafolder, fileSize, false, 0, 0, null)
               .setDatasync(datasync)
               .disableBufferReuse();
            factory.start();
            return factory;
         default:
            throw ActiveMQMessageBundle.BUNDLE.invalidJournalType2(journalType);
      }
   }
}
