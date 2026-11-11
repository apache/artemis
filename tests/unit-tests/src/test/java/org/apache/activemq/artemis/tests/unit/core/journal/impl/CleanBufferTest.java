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
package org.apache.activemq.artemis.tests.unit.core.journal.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.ByteBuffer;

import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.aio.AIOSequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.tests.unit.core.journal.impl.fakes.FakeSequentialFileFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class CleanBufferTest extends ActiveMQTestBase {



   @Test
   public void testCleanOnNIO() {
      SequentialFileFactory factory = new NIOSequentialFileFactory(new File("Whatever"), 1);

      testBuffer(factory);
   }

   @Test
   public void testCleanOnAIO() {
      if (LibaioContext.isLoaded()) {
         SequentialFileFactory factory = new AIOSequentialFileFactory(new File("./target"), 50);

         testBuffer(factory);
      }
   }

   @Test
   public void testCleanOnFake() {
      SequentialFileFactory factory = new FakeSequentialFileFactory();

      testBuffer(factory);
   }

   private void testBuffer(final SequentialFileFactory factory) {
      factory.start();
      ByteBuffer buffer = factory.newBuffer(100);

      try {
         for (byte b = 0; b < 100; b++) {
            buffer.put(b);
         }

         buffer.rewind();

         for (byte b = 0; b < 100; b++) {
            assertEquals(b, buffer.get());
         }

         buffer.limit(10);
         factory.clearBuffer(buffer);
         buffer.limit(100);

         buffer.rewind();

         for (byte b = 0; b < 100; b++) {
            if (b < 10) {
               assertEquals(0, buffer.get());
            } else {
               assertEquals(b, buffer.get());
            }
         }
      } finally {
         factory.releaseBuffer(buffer);
         factory.stop();
      }
   }

}
