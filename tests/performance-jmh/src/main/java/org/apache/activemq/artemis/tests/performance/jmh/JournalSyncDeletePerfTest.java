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
package org.apache.activemq.artemis.tests.performance.jmh;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class JournalSyncDeletePerfTest {

   private static final String STORE_DIR = System.getProperty("user.dir") + File.separator + "JournalSyncDeletePerfTest";
   private static final String FILE_PREFIX = "perf";
   private static final String FILE_EXTENSION = "amq";
   private static final byte RECORD_TYPE = 0;

   @Param({"100", "1000"})
   private int records;
   @Param({"64"})
   private int recordSize;
   @Param({"10485760"})
   private int fileSize;
   @Param({"4"})
   private int minFiles;
   @Param({"20"})
   private int poolSize;
   @Param({"10"})
   private int compactMinFiles;
   @Param({"30"})
   private int compactPercentage;

   private SequentialFileFactory factory;
   private AtomicLong ranges;
   private Journal journal;
   private byte[] recordData;

   @Setup
   public void init() throws Exception {
      ranges = new AtomicLong();
      File storeDir = new File(STORE_DIR);
      factory = new NIOSequentialFileFactory(storeDir, false, 1).setDatasync(false);
      factory.start();
      factory.createDirs();
      journal = new JournalImpl(fileSize, minFiles, poolSize, compactMinFiles, compactPercentage, factory, FILE_PREFIX, FILE_EXTENSION, factory.getMaxIO());
      journal.replaceableRecord(RECORD_TYPE);
      journal.start();
      journal.loadInternalOnly();
      recordData = new byte[recordSize];
      Arrays.fill(recordData, (byte) 1);
   }

   @State(Scope.Thread)
   public static class RecordRange {

      private long recordId;

      @Setup
      public void init(JournalSyncDeletePerfTest test) {
         recordId = test.ranges.getAndAdd(test.records);
      }
   }

   @Benchmark
   public void batchAppendUpdateDelete(final RecordRange range) throws Exception {
      final long firstRecordId = range.recordId;
      final byte[] recordData = this.recordData;
      for (int i = 0; i < records - 1; i++) {
         journal.appendAddRecord(firstRecordId + i, RECORD_TYPE, recordData, false);
         journal.appendUpdateRecord(firstRecordId + i, RECORD_TYPE, recordData, false);
         journal.appendDeleteRecord(firstRecordId + i, false);
      }
      final long lastRecordId = firstRecordId + (records - 1);
      journal.appendAddRecord(lastRecordId, RECORD_TYPE, recordData, false);
      journal.appendUpdateRecord(lastRecordId, RECORD_TYPE, recordData, false);
      journal.appendDeleteRecord(lastRecordId, true);
   }

   @TearDown
   public synchronized void stop() {
      try {
         journal.stop();
      } catch (Exception ignore) {

      }
      factory.stop();
      Stream.of(factory.getDirectory().listFiles()).forEach(File::delete);
      factory.getDirectory().delete();
   }

}
