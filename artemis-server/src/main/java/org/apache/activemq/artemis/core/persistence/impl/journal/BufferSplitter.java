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
package org.apache.activemq.artemis.core.persistence.impl.journal;


import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;

/**
 * this class will split a big buffer into smaller buffers
 */
public class BufferSplitter {


   public static void split(ActiveMQBuffer buffer, int splitSize, Consumer<EncodingSupport> target) {
      byte[] bytesBuffer = new byte[buffer.readableBytes()];
      buffer.getBytes(buffer.readerIndex(), bytesBuffer);
      split(bytesBuffer, splitSize, target);
   }

   public static void split(byte[] buffer, int splitSize, Consumer<EncodingSupport> target) {

      int location = 0;
      while (location < buffer.length) {
         int maxSize = Math.min(splitSize, buffer.length - location);
         target.accept(new PartialEncoding(buffer, location, maxSize));
         location += maxSize;
      }

   }

   protected static class PartialEncoding implements EncodingSupport {

      final byte[] data;
      final int begin;
      final int length;

      public PartialEncoding(final byte[] data, final int begin, final int length) {
         this.data = data;
         this.begin = begin;
         this.length = length;
      }


      @Override
      public void decode(final ActiveMQBuffer buffer) {
         throw new IllegalStateException("operation not supported");
      }

      @Override
      public void encode(final ActiveMQBuffer buffer) {
         buffer.writeBytes(data, begin, length);
      }

      @Override
      public int getEncodeSize() {
         return length;
      }
   }


}
