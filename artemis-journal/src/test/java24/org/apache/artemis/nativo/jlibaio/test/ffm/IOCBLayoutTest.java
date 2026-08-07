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
package org.apache.artemis.nativo.jlibaio.test.ffm;

import org.apache.artemis.nativo.jlibaio.ffm.IOCBInit;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.apache.artemis.nativo.jlibaio.ffm.IOCBInit.IOCB_LAYOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class IOCBLayoutTest {

   @Test
   public void iocbLayoutSizeTest() {
      assertEquals(64, (int) IOCB_LAYOUT.byteSize(), "Expected 64-byte iocb");
   }

   @Test
   public void iocbLayoutValueTest() {
      try (Arena arena = Arena.ofConfined()) {
         MemorySegment iocb = arena.allocate(IOCB_LAYOUT);
         IOCBInit.setAioKey(iocb, 123);
         IOCBInit.setAioFildes(iocb, 42);
         IOCBInit.setAioBuf(iocb, 0x7f1234567890L);
         IOCBInit.setAioNbytes(iocb, 4096);
         IOCBInit.setAioFlags(iocb, 0);

         assertEquals(123, IOCBInit.getAioKey(iocb));
         assertEquals(42, IOCBInit.getAioFildes(iocb));
         assertEquals(0x7f1234567890L, IOCBInit.getAioBuf(iocb));
         assertEquals(4096, IOCBInit.getAioNbytes(iocb));
         assertEquals(0, IOCBInit.getAioFlags(iocb));
      }
   }
}
