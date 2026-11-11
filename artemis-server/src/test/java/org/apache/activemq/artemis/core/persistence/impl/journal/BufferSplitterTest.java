/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.persistence.impl.journal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.utils.DataConstants;
import org.junit.jupiter.api.Test;

public class BufferSplitterTest {

   @Test
   public void testSplitting() {
      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(1000 * DataConstants.SIZE_INT);

      for (int i = 0; i < 1000; i++) {
         buffer.writeInt(i);
      }

      ActiveMQBuffer outputBuffer = ActiveMQBuffers.fixedBuffer(1000 * DataConstants.SIZE_INT);

      final int rdx = buffer.readerIndex();
      final int readableBytes = buffer.readableBytes();

      BufferSplitter.split(buffer, 77, (c) -> {
         assertTrue(c.getEncodeSize() <= 77);
         c.encode(outputBuffer);
      });

      assertEquals(rdx, buffer.readerIndex());
      assertEquals(readableBytes, buffer.readableBytes());

      outputBuffer.resetReaderIndex();
      buffer.resetReaderIndex();

      byte[] sourceBytes = new byte[1000 * DataConstants.SIZE_INT];
      buffer.readBytes(sourceBytes);
      byte[] targetBytes = new byte[1000 * DataConstants.SIZE_INT];
      outputBuffer.readBytes(targetBytes);

      assertArrayEquals(sourceBytes, targetBytes);
   }

}
