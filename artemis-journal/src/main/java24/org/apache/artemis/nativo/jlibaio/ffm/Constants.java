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

public final class Constants {

   private Constants() {
   }

   static final long ONE_MEGA = 1048576L;

   //These should be used to check if the user-space io_getevents is supported:
   //Linux ABI for the ring buffer: https://elixir.bootlin.com/linux/v4.20.13/source/fs/aio.c#L54
   //aio_read_events_ring: https://elixir.bootlin.com/linux/v4.20.13/source/fs/aio.c#L1148

   // NOTE: if the kernel ever updates the structure, the RING-MAGIC will change and the code will switch back to normal IO calls
   static final int AIO_RING_MAGIC = 0xa10a10a1;
   static final int AIO_RING_INCOMPAT_FEATURES = 0;

   // set this to false if you want to stop using ring reaping
   static final boolean RING_REAPER = true;

   static final int PERMISSION_MODE = 0666;
   static final int O_RDWR = 0x0002;
   static final int O_CREAT = 0x0040;
   static final int O_DIRECT;

   static final int LOCK_EX = 2;    // Exclusive lock
   static final int LOCK_NB = 4;    // Non-blocking lock

   static {
      O_DIRECT = detectODirectFlag();
   }

   /*
    * Detecting OS Architecture and setting O_DIRECT
    *
    * */
   private static int detectODirectFlag() {
      String arch = System.getProperty("os.arch");
      if ("aarch64".equals(arch) || "arm64".equals(arch) || "arm".equals(arch)) {
         return 0x10000;
      } else if ("ppc64le".equals(arch) || "ppc64".equals(arch) || "ppc".equals(arch)) {
         return 0x8000;
      }
      // amd64, x86_64
      return 0x4000;
   }
}
