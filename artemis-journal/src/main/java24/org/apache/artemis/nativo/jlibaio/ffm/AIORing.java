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
package org.apache.artemis.nativo.jlibaio.ffm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

import static org.apache.artemis.nativo.jlibaio.ffm.Constants.AIO_RING_INCOMPAT_FEATURES;
import static org.apache.artemis.nativo.jlibaio.ffm.Constants.AIO_RING_MAGIC;
import static org.apache.artemis.nativo.jlibaio.ffm.IOEvent.IO_EVENT_LAYOUT;

public class AIORing {

   private static final Logger logger = LoggerFactory.getLogger(AIORing.class);

   /**
    * There is no defined aio_ring anywhere in an include,
    * This is an implementation detail, that is a binary contract.
    * it is safe to use the feature though.
    */
   static final StructLayout AIO_RING_LAYOUT = MemoryLayout.structLayout(
      // Fixed header (32 bytes)
      ValueLayout.JAVA_INT.withName("id"),    /* kernel internal index number */
      ValueLayout.JAVA_INT.withName("nr"),    /* number of io_events */
      ValueLayout.JAVA_INT.withName("head"), ValueLayout.JAVA_INT.withName("tail"), ValueLayout.JAVA_INT.withName("magic"), ValueLayout.JAVA_INT.withName("compat_features"), ValueLayout.JAVA_INT.withName("incompat_features"), ValueLayout.JAVA_INT.withName("header_length")  /* size of aio_ring */).withName("aio_ring");

   public static final long AIO_RING_HEADER_SIZE = AIO_RING_LAYOUT.byteSize();

   public static final VarHandle AIO_RING_NR_VH = AIO_RING_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("nr"));
   public static final VarHandle AIO_RING_HEAD_VH = AIO_RING_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("head"));
   public static final VarHandle AIO_RING_TAIL_VH = AIO_RING_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("tail"));
   public static final VarHandle AIO_RING_MAGIC_VH = AIO_RING_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("magic"));
   public static final VarHandle AIO_RING_INCOMPAT_FEATURES_VH = AIO_RING_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("incompat_features"));

   // Check if the implementation supports AIO_RING by checking this number directly.
   public static boolean hasUsableRing(MemorySegment ring) {
      if (ring == null || ring.address() == 0L || ring.byteSize() < AIO_RING_HEADER_SIZE) {
         return false;
      }

      MemorySegment header = ring.asSlice(0, AIO_RING_HEADER_SIZE);
      int magic = (int) AIO_RING_MAGIC_VH.getAcquire(header, 0L);
      int incompat = (int) AIO_RING_INCOMPAT_FEATURES_VH.getAcquire(header, 0L);
      int nr = (int) AIO_RING_NR_VH.getAcquire(header, 0L);
      if (logger.isTraceEnabled()) {
         logger.trace("nr={}, magic={}, incompat={}", nr, magic, incompat);
      }

      return magic == AIO_RING_MAGIC && incompat == AIO_RING_INCOMPAT_FEATURES && nr > 0;
   }

   // Newer versions of the kernel (newer here being a relative word, a couple years already at the time
   // I am writing this), will have io_context_t as an opaque type, and the real type being the aio_ring.
   public static MemorySegment toAioRing(MemorySegment aioCtx) {
      if (aioCtx == null || aioCtx.address() == 0L) {
         return MemorySegment.NULL;
      }

      MemorySegment header = aioCtx.reinterpret(AIO_RING_HEADER_SIZE);

      if (!hasUsableRing(header)) {
         return MemorySegment.NULL;
      }

      int nr = (int) AIO_RING_NR_VH.getAcquire(header, 0L);
      long eventBytesize = IO_EVENT_LAYOUT.byteSize();
      long fullSize;

      try {
         fullSize = Math.addExact(AIO_RING_HEADER_SIZE, Math.multiplyExact((long) nr, eventBytesize));
      } catch (ArithmeticException e) {
         logger.warn("toAioRing: overflow computing ring size (nr={}, eventBytes={})", nr, eventBytesize);
         return MemorySegment.NULL;
      }

      if (fullSize <= AIO_RING_HEADER_SIZE) {
         return MemorySegment.NULL;
      }

      return aioCtx.reinterpret(fullSize);
   }
}
