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
package org.apache.activemq.artemis.core.io.util;

import java.nio.ByteBuffer;
import java.util.Objects;

import io.netty.util.internal.PlatformDependent;
import org.apache.activemq.artemis.utils.PowerOf2Util;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.Env;

final class ThreadLocalByteBufferPool implements ByteBufferPool {

   private final ThreadLocal<ByteBuffer> bytesPool;
   private final boolean direct;

   ThreadLocalByteBufferPool(boolean direct) {
      this.bytesPool = new ThreadLocal<>();
      this.direct = direct;
   }

   @Override
   public ByteBuffer borrow(final int size, boolean zeroed) {
      final int requiredCapacity = PowerOf2Util.align(size, Env.osPageSize());
      ByteBuffer byteBuffer = bytesPool.get();
      if (byteBuffer == null || requiredCapacity > byteBuffer.capacity()) {
         //do not free the old one (if any) until the new one will be released into the pool!
         byteBuffer = direct ? ByteBuffer.allocateDirect(requiredCapacity) : ByteBuffer.allocate(requiredCapacity);
      } else {
         bytesPool.set(null);
         if (zeroed) {
            byteBuffer.clear();
            ByteUtil.zeros(byteBuffer, 0, size);
         }
         byteBuffer.clear();
      }
      byteBuffer.limit(size);
      return byteBuffer;
   }

   @Override
   public void release(ByteBuffer buffer) {
      Objects.requireNonNull(buffer);
      boolean directBuffer = buffer.isDirect();
      if (directBuffer == direct && !buffer.isReadOnly()) {
         final ByteBuffer byteBuffer = bytesPool.get();
         if (byteBuffer != buffer) {
            //replace with the current pooled only if greater or null
            if (byteBuffer == null || buffer.capacity() > byteBuffer.capacity()) {
               if (byteBuffer != null) {
                  //free the smaller one
                  if (directBuffer) {
                     PlatformDependent.freeDirectBuffer(byteBuffer);
                  }
               }
               bytesPool.set(buffer);
            } else {
               if (directBuffer) {
                  PlatformDependent.freeDirectBuffer(buffer);
               }
            }
         }
      }
   }

}
