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

public class IOEvent {

   public static final int IO_EVENT_LAYOUT_SIZE = 32;

   static final StructLayout IO_EVENT_LAYOUT = MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("data"), ValueLayout.ADDRESS.withName("obj"), ValueLayout.JAVA_LONG.withName("res"), ValueLayout.JAVA_LONG.withName("res2")).withName("io_event");

   public static final long DATA = 0;
   public static final long OBJ = 8;
   public static final long RES = 16;
   public static final long RES2 = 24;

   public static long getData(MemorySegment ioEvent) {
      return ioEvent.get(ValueLayout.JAVA_LONG, DATA);
   }

   public static void setData(MemorySegment ioEvent, long value) {
      ioEvent.set(ValueLayout.JAVA_LONG, DATA, value);
   }

   public static MemorySegment getObj(MemorySegment ioEvent) {
      return ioEvent.get(ValueLayout.ADDRESS, OBJ);
   }

   public static void setObj(MemorySegment ioEvent, MemorySegment value) {
      ioEvent.set(ValueLayout.ADDRESS, OBJ, value);
   }
}
