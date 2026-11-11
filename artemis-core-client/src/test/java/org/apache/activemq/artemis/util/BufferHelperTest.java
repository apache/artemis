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
package org.apache.activemq.artemis.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.utils.BufferHelper;
import org.junit.jupiter.api.Test;

public class BufferHelperTest {
   @Test
   public void testSizeOfNullableString() {
      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(0);
      String[] tests = new String[]{"111111111", new String(new byte[4094]), new String(new byte[4095])};
      for (String s : tests) {
         buffer.resetReaderIndex();
         buffer.resetWriterIndex();
         buffer.writeNullableString(s);
         int size = BufferHelper.sizeOfNullableString(s);
         int written = buffer.writerIndex();
         assertEquals(written, size);
         String readString = buffer.readNullableString();
         assertEquals(s, readString);
      }
   }
}
