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
import java.lang.foreign.ValueLayout;

public class IOCBInit {

   public static final int IOCB_LAYOUT_SIZE = 64;
   public static final MemoryLayout IOCB_LAYOUT = MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("aio_data"), ValueLayout.JAVA_INT.withName("aio_key"), ValueLayout.JAVA_INT.withName("aio_rw_flags"), ValueLayout.JAVA_SHORT.withName("aio_lio_opcode"), ValueLayout.JAVA_SHORT.withName("aio_reqprio"), ValueLayout.JAVA_INT.withName("aio_fildes"), ValueLayout.JAVA_LONG.withName("aio_buf"), ValueLayout.JAVA_LONG.withName("aio_nbytes"), ValueLayout.JAVA_LONG.withName("aio_offset"), ValueLayout.JAVA_LONG.withName("aio_reserved2"), ValueLayout.JAVA_INT.withName("aio_flags"), ValueLayout.JAVA_INT.withName("aio_resfd")).withByteAlignment(8).withName("iocb");

   public static final long AIO_DATA = 0;
   public static final long AIO_KEY = 8;
   public static final long AIO_RW_FLAGS = 12;
   public static final long AIO_LIO_OPCODE = 16;
   public static final long AIO_REQPRIO = 18;
   public static final long AIO_FILDES = 20;
   public static final long AIO_BUF = 24;
   public static final long AIO_NBYTES = 32;
   public static final long AIO_OFFSET = 40;
   public static final long AIO_RESERVED2 = 48;
   public static final long AIO_FLAGS = 56;
   public static final long AIO_RESFD = 60;

   public static long getAioData(MemorySegment iocb) {
      return iocb.get(ValueLayout.JAVA_LONG, AIO_DATA);
   }

   public static void setAioData(MemorySegment iocb, long value) {
      iocb.set(ValueLayout.JAVA_LONG, AIO_DATA, value);
   }

   public static int getAioKey(MemorySegment iocb) {
      return iocb.get(ValueLayout.JAVA_INT, AIO_KEY);
   }

   public static void setAioKey(MemorySegment iocb, int value) {
      iocb.set(ValueLayout.JAVA_INT, AIO_KEY, value);
   }

   public static int getAioRwFlags(MemorySegment iocb) {
      return iocb.get(ValueLayout.JAVA_INT, AIO_RW_FLAGS);
   }

   public static void setAioRwFlags(MemorySegment iocb, int value) {
      iocb.set(ValueLayout.JAVA_INT, AIO_RW_FLAGS, value);
   }

   public static short getAioLioOpcode(MemorySegment iocb) {
      return iocb.get(ValueLayout.JAVA_SHORT, AIO_LIO_OPCODE);
   }

   public static void setAioLioOpcode(MemorySegment iocb, short value) {
      iocb.set(ValueLayout.JAVA_SHORT, AIO_LIO_OPCODE, value);
   }

   public static short getAioReqprio(MemorySegment iocb) {
      return iocb.get(ValueLayout.JAVA_SHORT, AIO_REQPRIO);
   }

   public static void setAioReqprio(MemorySegment iocb, short value) {
      iocb.set(ValueLayout.JAVA_SHORT, AIO_REQPRIO, value);
   }

   public static int getAioFildes(MemorySegment iocb) {
      return iocb.get(ValueLayout.JAVA_INT, AIO_FILDES);
   }

   public static void setAioFildes(MemorySegment iocb, int value) {
      iocb.set(ValueLayout.JAVA_INT, AIO_FILDES, value);
   }

   public static long getAioBuf(MemorySegment iocb) {
      return iocb.get(ValueLayout.JAVA_LONG, AIO_BUF);
   }

   public static void setAioBuf(MemorySegment iocb, long value) {
      iocb.set(ValueLayout.JAVA_LONG, AIO_BUF, value);
   }

   public static long getAioNbytes(MemorySegment iocb) {
      return iocb.get(ValueLayout.JAVA_LONG, AIO_NBYTES);
   }

   public static void setAioNbytes(MemorySegment iocb, long value) {
      iocb.set(ValueLayout.JAVA_LONG, AIO_NBYTES, value);
   }

   public static long getAioOffset(MemorySegment iocb) {
      return iocb.get(ValueLayout.JAVA_LONG, AIO_OFFSET);
   }

   public static void setAioOffset(MemorySegment iocb, long value) {
      iocb.set(ValueLayout.JAVA_LONG, AIO_OFFSET, value);
   }

   public static int getAioFlags(MemorySegment iocb) {
      return iocb.get(ValueLayout.JAVA_INT, AIO_FLAGS);
   }

   public static void setAioFlags(MemorySegment iocb, int value) {
      iocb.set(ValueLayout.JAVA_INT, AIO_FLAGS, value);
   }

   public static int getAioResfd(MemorySegment iocb) {
      return iocb.get(ValueLayout.JAVA_INT, AIO_RESFD);
   }

   public static void setAioResfd(MemorySegment iocb, int value) {
      iocb.set(ValueLayout.JAVA_INT, AIO_RESFD, value);
   }
}
