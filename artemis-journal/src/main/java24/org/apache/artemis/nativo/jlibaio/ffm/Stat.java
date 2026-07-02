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

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

public final class Stat {

   // this will work only for 64-bit linux
   static final StructLayout STAT_LAYOUT = MemoryLayout.structLayout(MemoryLayout.paddingLayout(48), ValueLayout.JAVA_LONG.withName("st_size"),     // File size (bytes)
                                                                     ValueLayout.JAVA_INT.withName("st_blksize"),  // Block size for filesystem I/O
                                                                     ValueLayout.JAVA_INT.withName("__pad2"), ValueLayout.JAVA_LONG.withName("st_blocks"),   // Number of 512B blocks allocated
                                                                     MemoryLayout.paddingLayout(192)).withName("stat").withByteAlignment(8L);

   static final VarHandle ST_SIZE_VH = STAT_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("st_size"));
   static final VarHandle ST_BLKSIZE_VH = STAT_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("st_blksize"));
   static final VarHandle ST_BLOCKS_VH = STAT_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("st_blocks"));

   public static long getSize(MemorySegment stat) {
      return (long) ST_SIZE_VH.get(stat, 0L);
   }

   public static int getBlksize(MemorySegment stat) {
      return (int) ST_BLKSIZE_VH.get(stat, 0L);
   }

   public static int getBlocks(MemorySegment stat) {
      return (int) ST_BLOCKS_VH.get(stat, 0L);
   }
}
