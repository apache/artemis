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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FFMHandles {

   private static final Logger logger = LoggerFactory.getLogger(FFMHandles.class);
   static final Linker LINKER = Linker.nativeLinker();
   static final SymbolLookup STDLIB = setStdLib();
   public static final SymbolLookup LIBAIO = setLibaio();

   static final ReentrantLock oneMegaMutex = new ReentrantLock();

   static final StructLayout CAPTURE_STATE_LAYOUT = Linker.Option.captureStateLayout();
   static final VarHandle ERRNO_VH = CAPTURE_STATE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("errno"));

   private static final Linker.Option captureCallState = Linker.Option.captureCallState("errno");

   static final MethodHandle WRITE_HANDLE = LINKER.downcallHandle(STDLIB.find("write").orElseThrow(() -> new UnsatisfiedLinkError("write not found in STDLIB")), FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG), captureCallState);

   static final MethodHandle OPEN_HANDLE = LINKER.downcallHandle(STDLIB.find("open").orElseThrow(() -> new UnsatisfiedLinkError("open not found in STDLIB")), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,    // pathName
                                                                                                                                                                                    ValueLayout.JAVA_INT,   // flags
                                                                                                                                                                                    ValueLayout.JAVA_INT), // mode
                                                                 captureCallState);

   static final MethodHandle CLOSE_HANDLE = LINKER.downcallHandle(STDLIB.find("close").orElseThrow(() -> new UnsatisfiedLinkError("close not found in STDLIB")), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), captureCallState);

   static final MethodHandle FALLOCATE_HANDLE = LINKER.downcallHandle(STDLIB.find("fallocate").orElseThrow(() -> new UnsatisfiedLinkError("fallocate not found in STDLIB")), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG), captureCallState);

   static final MethodHandle FSYNC_HANDLE = LINKER.downcallHandle(STDLIB.find("fsync").orElseThrow(() -> new UnsatisfiedLinkError("fsync not found in STDLIB")), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), captureCallState);

   static final MethodHandle LSEEK_HANDLE = LINKER.downcallHandle(STDLIB.find("lseek").orElseThrow(() -> new UnsatisfiedLinkError("lseek not found in STDLIB")), FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT), captureCallState);

   static final MethodHandle FSTAT_HANDLE = LINKER.downcallHandle(STDLIB.find("fstat").orElseThrow(() -> new UnsatisfiedLinkError("fstat not found in STDLIB")), FunctionDescriptor.of(ValueLayout.JAVA_INT,   // return
                                                                                                                                                                                       ValueLayout.JAVA_INT,    // fd
                                                                                                                                                                                       ValueLayout.ADDRESS),   // struct stat
                                                                  captureCallState);

   // for x86_64 - stat
   static final MethodHandle STAT_HANDLE = LINKER.downcallHandle(STDLIB.find("stat").orElseThrow(() -> new UnsatisfiedLinkError("stat not found in STDLIB")), FunctionDescriptor.of(ValueLayout.JAVA_INT,   // return
                                                                                                                                                                                    ValueLayout.ADDRESS,    // pathname
                                                                                                                                                                                    ValueLayout.ADDRESS),   // struct stat
                                                                 captureCallState);

   static final MethodHandle IO_GETEVENTS_HANDLE = LINKER.downcallHandle(LIBAIO.find("io_getevents").orElseThrow(() -> new UnsatisfiedLinkError("io_getevents not found in LIBAIO")), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS), captureCallState);

   static final MethodHandle IO_SUBMIT_HANDLE = LINKER.downcallHandle(LIBAIO.find("io_submit").orElseThrow(() -> new UnsatisfiedLinkError("io_submit not found in LIBAIO")), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)).asFixedArity();

   static final MethodHandle FREE_BUF_HANDLE = LINKER.downcallHandle(STDLIB.find("free").orElseThrow(() -> new UnsatisfiedLinkError("free not found in STDLIB")), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

   static final MethodHandle FLOCK_HANDLE = LINKER.downcallHandle(STDLIB.find("flock").orElseThrow(() -> new UnsatisfiedLinkError("flock not found in STDLIB")), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), captureCallState);

   static final MethodHandle IO_QUEUE_INIT_HANDLE = LINKER.downcallHandle(LIBAIO.find("io_queue_init").orElseThrow(() -> new UnsatisfiedLinkError("io_queue_init not found in LIBAIO")), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS), captureCallState);

   static final MethodHandle IO_QUEUE_RELEASE_HANDLE = LINKER.downcallHandle(LIBAIO.find("io_queue_release").orElseThrow(() -> new UnsatisfiedLinkError("io_queue_release not found in LIBAIO")), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS), captureCallState);

   static final MethodHandle FDATASYNC_HANDLE = LINKER.downcallHandle(STDLIB.find("fdatasync").orElseThrow(() -> new UnsatisfiedLinkError("fdatasync not found in STDLIB")), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), captureCallState);

   static final MethodHandle MEMSET_HANDLE = LINKER.downcallHandle(STDLIB.find("memset").orElseThrow(() -> new UnsatisfiedLinkError("memset not found in STDLIB")), FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));

   static final MethodHandle POSIX_MEMALIGN_HANDLE = LINKER.downcallHandle(STDLIB.find("posix_memalign").orElseThrow(() -> new UnsatisfiedLinkError("posix_memalign not found in STDLIB")), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG), Linker.Option.captureCallState("errno"));

   private static SymbolLookup setStdLib() {
      String[] libcPaths = {"/lib64/libc.so.6", "/usr/lib64/libc.so.6", "/lib/x86_64-linux-gnu/libc.so.6", "libc.so.6"};
      for (String path : libcPaths) {
         try {
            SymbolLookup loopup = SymbolLookup.libraryLookup(path, Arena.global());
            if (loopup != null) {
               logger.info("libc.so.6 found at {}", path);
               return loopup;
            }
         } catch (IllegalArgumentException | SecurityException e) {
            logger.warn("libc.so.6 not found", e);
         }
      }
      logger.warn("libc.so.6 not found");
      throw new RuntimeException("libc.so.6 not found");
   }

   private static SymbolLookup setLibaio() {
      String[] paths = {System.getProperty("libaio.path"), "/usr/lib64/libaio.so.1", "/usr/lib/x86_64-linux-gnu/libaio.so.1", "/lib64/libaio.so.1", "/usr/lib/libaio.so.1", "libaio.so.1"};
      for (String path : paths) {
         if (path != null && !path.isEmpty()) {
            try {
               SymbolLookup lookup = SymbolLookup.libraryLookup(path, Arena.global());
               if (lookup != null) {
                  logger.info("libaio.so.1 found at {}", path);
                  return lookup;
               }
            } catch (IllegalArgumentException | SecurityException e) {
               logger.warn("libaio.so.1 not found", e);
            }
         }
      }
      logger.warn("libaio.so.1 not found");
      throw new RuntimeException("libaio.so.1 not found");
   }
}
