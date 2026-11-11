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
package org.apache.activemq.artemis.api.core;

import java.nio.ByteBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;

/**
 * Factory class to create instances of {@link ActiveMQBuffer}.
 */
public final class ActiveMQBuffers {

   private static final PooledByteBufAllocator ALLOCATOR = PooledByteBufAllocator.DEFAULT;

   /**
    * Creates a <em>self-expanding</em> ActiveMQBuffer with the given initial size
    *
    * @param size the initial size of the created ActiveMQBuffer
    * @return a self-expanding ActiveMQBuffer starting with the given size
    */
   public static ActiveMQBuffer dynamicBuffer(final int size) {
      return new ChannelBufferWrapper(Unpooled.buffer(size));
   }

   public static ActiveMQBuffer pooledBuffer(final int size) {
      return new ChannelBufferWrapper(ALLOCATOR.heapBuffer(size), true, true);
   }

   /**
    * Creates a <em>self-expanding</em> ActiveMQBuffer filled with the given byte array
    *
    * @param bytes the created buffer will be initially filled with this byte array
    * @return a self-expanding ActiveMQBuffer filled with the given byte array
    */
   public static ActiveMQBuffer dynamicBuffer(final byte[] bytes) {
      ActiveMQBuffer buff = dynamicBuffer(bytes.length);

      buff.writeBytes(bytes);

      return buff;
   }

   /**
    * Creates an ActiveMQBuffer wrapping an underlying NIO ByteBuffer
    * <p>
    * The position on this buffer won't affect the position on the inner buffer
    *
    * @param underlying the underlying NIO ByteBuffer
    * @return an ActiveMQBuffer wrapping the underlying NIO ByteBuffer
    */
   public static ActiveMQBuffer wrappedBuffer(final ByteBuffer underlying) {
      ActiveMQBuffer buff = new ChannelBufferWrapper(Unpooled.wrappedBuffer(underlying));

      buff.clear();

      return buff;
   }

   /**
    * Creates an ActiveMQBuffer wrapping an underlying ByteBuf
    * <p>
    * The position on this buffer won't affect the position on the inner buffer
    *
    * @param underlying the underlying NIO ByteBuffer
    * @return an ActiveMQBuffer wrapping the underlying NIO ByteBuffer
    */
   public static ActiveMQBuffer wrappedBuffer(final ByteBuf underlying) {
      ActiveMQBuffer buff = new ChannelBufferWrapper(underlying.duplicate());

      return buff;
   }

   /**
    * Creates an ActiveMQBuffer wrapping an underlying byte array
    *
    * @param underlying the underlying byte array
    * @return an ActiveMQBuffer wrapping the underlying byte array
    */
   public static ActiveMQBuffer wrappedBuffer(final byte[] underlying) {
      return new ChannelBufferWrapper(Unpooled.wrappedBuffer(underlying));
   }

   /**
    * Creates a <em>fixed</em> ActiveMQBuffer of the given size
    *
    * @param size the size of the created ActiveMQBuffer
    * @return a fixed ActiveMQBuffer with the given size
    */
   public static ActiveMQBuffer fixedBuffer(final int size) {
      return new ChannelBufferWrapper(Unpooled.buffer(size, size));
   }

   private ActiveMQBuffers() {
      // Utility class
   }
}
