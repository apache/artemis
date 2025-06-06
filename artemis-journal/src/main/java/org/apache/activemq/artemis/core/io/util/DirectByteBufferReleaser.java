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

import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;

import io.netty.util.internal.PlatformDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Best-effort helper to eagerly free direct {@link ByteBuffer}s that were allocated through the JDK, e.g. via
 * {@link ByteBuffer#allocateDirect(int)} or {@link java.nio.channels.FileChannel#map}.
 * <p>
 * Freeing such a buffer eagerly is only an optimization to release native memory promptly rather than waiting for the
 * buffer's {@link java.lang.ref.Cleaner} to run during GC. On JDK 24+ running without {@code sun.misc.Unsafe}, Netty is
 * unable to free "arbitrary" (JDK-allocated) direct buffers and {@link PlatformDependent#freeDirectBuffer(ByteBuffer)}
 * throws {@link UnsupportedOperationException}. In that case the eager free is skipped and the GC-triggered
 * {@link java.lang.ref.Cleaner} associated with the buffer reclaims the native memory instead.
 */
public final class DirectByteBufferReleaser {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final boolean CAN_FREE_DIRECT_BUFFER = probeCanFreeDirectBuffer();

   private DirectByteBufferReleaser() {
   }

   /**
    * Eagerly frees the given direct {@code buffer} if the platform supports it. Does nothing for {@code null},
    * non-direct buffers, or when the platform is unable to free JDK-allocated direct buffers (in which case the memory
    * is reclaimed by the GC-triggered {@link java.lang.ref.Cleaner} associated with the buffer).
    * <p>
    * {@link PlatformDependent#freeDirectBuffer} is deprecated in favor of
    * {@link io.netty.util.internal.CleanableDirectBuffer#clean()}, but that replacement can only free buffers Netty
    * itself allocated via {@link PlatformDependent#allocateDirect(int)}. Artemis passes buffers it allocated through
    * the JDK ({@link ByteBuffer#allocateDirect} / {@link java.nio.channels.FileChannel#map}), so freeDirectBuffer
    * remains the only Netty API able to free them; hence the suppression.
    */
   @SuppressWarnings("deprecation")
   public static void freeDirectBuffer(ByteBuffer buffer) {
      if (CAN_FREE_DIRECT_BUFFER && buffer != null && buffer.isDirect()) {
         PlatformDependent.freeDirectBuffer(buffer);
      }
   }

   /**
    * @return whether native (eager) freeing of direct buffers is available on the current platform
    */
   public static boolean canFreeDirectBuffer() {
      return CAN_FREE_DIRECT_BUFFER;
   }

   @SuppressWarnings("deprecation")
   private static boolean probeCanFreeDirectBuffer() {
      // Probe with a JDK-allocated direct buffer, matching how Artemis allocates the buffers passed to this class.
      final ByteBuffer probe = ByteBuffer.allocateDirect(1);
      try {
         PlatformDependent.freeDirectBuffer(probe);
         return true;
      } catch (Throwable t) {
         logger.debug("Unable to eagerly free direct ByteBuffers; native memory will be reclaimed by the GC-triggered Cleaner instead. On JDK 24+ enabling sun.misc.Unsafe (e.g. --sun-misc-unsafe-memory-access=allow) restores eager freeing.", t);
         return false;
      }
   }
}
