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
package org.apache.activemq.artemis.tests.unit.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ActiveMQBufferInputStream;
import org.junit.jupiter.api.Test;

public class ActiveMQBufferInputStreamTest extends ActiveMQTestBase {

   @Test
   public void testReadBytes() throws Exception {
      byte[] bytes = new byte[10 * 1024];
      for (int i = 0; i < bytes.length; i++) {
         bytes[i] = getSamplebyte(i);
      }

      ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(bytes);
      ActiveMQBufferInputStream is = new ActiveMQBufferInputStream(buffer);

      // First read byte per byte
      for (int i = 0; i < 1024; i++) {
         assertEquals(getSamplebyte(i), is.read());
      }

      // Second, read in chunks
      for (int i = 1; i < 10; i++) {
         bytes = new byte[1024];
         is.read(bytes);
         for (int j = 0; j < bytes.length; j++) {
            assertEquals(getSamplebyte(i * 1024 + j), bytes[j]);
         }

      }

      assertEquals(-1, is.read());

      bytes = new byte[1024];

      int sizeRead = is.read(bytes);

      assertEquals(-1, sizeRead);
      is.close();
   }
}
