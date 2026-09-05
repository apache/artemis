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
package org.apache.activemq.artemis.core.io.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the NIO journal direct-buffer release path when {@code sun.misc.Unsafe} is unavailable. In that
 * case Netty cannot free JDK-allocated ("arbitrary") direct buffers, so the eager free must be skipped (letting the
 * GC-triggered {@link java.lang.ref.Cleaner} reclaim the memory) rather than throwing
 * {@link UnsupportedOperationException}.
 * <p>
 * On JDK 24+ at runtime by default Netty 4.2 avoids {@code sun.misc.Unsafe} (so it runs with {@code hasUnsafe=false})
 * unless the JVM is started with {@code --sun-misc-unsafe-memory-access=allow}. The same no-Unsafe path is also reached
 * when Unsafe is explicitly disabled ({@code -Dio.netty.noUnsafe=true}) or on a future JDK where
 * {@code sun.misc.Unsafe} is gone entirely.
 */
public class DirectByteBufferReleaserTest {

   // runs in the spawned child JVM (started with -Dio.netty.noUnsafe=true to force Netty's no-Unsafe path)
   public static void main(String[] arg) {
      try {
         // exercise the exact path that failed during Create auto-tune: SyncCalculation -> NIOSequentialFile.fill
         File dir = Files.createTempDirectory("DirectByteBufferReleaserTest").toFile();
         dir.deleteOnExit();
         NIOSequentialFileFactory factory = new NIOSequentialFileFactory(dir, 1);
         factory.start();
         SequentialFile file = factory.createSequentialFile("release.dat");
         file.open();
         // allocates a direct ByteBuffer and then releases it; the release must not throw without Unsafe
         file.fill(1024 * 1024);
         file.close();
         // force a real release of a direct buffer through the factory (bypassing the pool)
         factory.releaseDirectBuffer(factory.allocateDirectBuffer(1024));
         factory.stop();
         System.exit(0);
      } catch (Throwable e) {
         e.printStackTrace();
         System.exit(100);
      }
   }

   @Test
   public void releaseDirectBufferWithoutUnsafe() throws Exception {
      final String javaPath = new File(new File(System.getProperty("java.home"), "bin"), "java").getAbsolutePath();
      final List<String> command = new ArrayList<>();
      command.add(javaPath);
      command.add("-cp");
      command.add(SpawnedVMSupport.getClassPath());
      // force Netty's no-Unsafe path (hasUnsafe=false); in that configuration Netty cannot free the journal's
      // JDK-allocated ("arbitrary") direct buffers and PlatformDependent.freeDirectBuffer throws, which is exactly
      // the case DirectByteBufferReleaser must catch and skip
      command.add("-Dio.netty.noUnsafe=true");
      command.add("-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir", "./tmp"));
      command.add(DirectByteBufferReleaserTest.class.getName());

      final ProcessBuilder builder = new ProcessBuilder(command);
      builder.inheritIO();
      final Process process = builder.start();
      assertEquals(0, process.waitFor(), "releasing a direct buffer without Unsafe/native-access must not throw");
   }
}
