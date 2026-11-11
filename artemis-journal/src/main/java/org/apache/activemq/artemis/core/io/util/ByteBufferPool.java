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

/**
 * Object Pool that allows to borrow and release {@link ByteBuffer}s according to a specific type (direct/heap).
 * <p>
 * The suggested usage pattern is:
 * <pre>{@code
 *    ByteBuffer buffer = pool.borrow(size);
 *    //...using buffer...
 *    pool.release(buffer);
 * }</pre>
 */
public interface ByteBufferPool {

   /**
    * It returns a {@link ByteBuffer} with {@link ByteBuffer#capacity()} &gt;= {@code size}.
    * <p>
    * The {@code buffer} is zeroed until {@code size} if {@code zeroed=true}, with {@link ByteBuffer#position()}=0 and
    * {@link ByteBuffer#limit()}={@code size}.
    */
   ByteBuffer borrow(int size, boolean zeroed);

   /**
    * It pools or free {@code buffer} that cannot be used anymore.
    * <p>
    * If {@code buffer} is of a type different from the one that the pool can borrow, it will ignore it.
    */
   void release(ByteBuffer buffer);

   /**
    * Factory method that creates a thread-local pool of capacity 1 of {@link ByteBuffer}s of the specified type
    * (direct/heap).
    */
   static ByteBufferPool threadLocal(boolean direct) {
      return new ThreadLocalByteBufferPool(direct);
   }

}
