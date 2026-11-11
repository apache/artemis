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

import java.io.DataInput;
import java.nio.ByteBuffer;

import io.netty.buffer.ByteBuf;

/**
 * An ActiveMQBuffer wraps a Netty's ChannelBuffer and is used throughout Apache Artemis code base.
 * <p>
 * Instances of it can be obtained from {@link ActiveMQBuffers} factory.
 * <p>
 * Much of it derived from Netty ChannelBuffer by Trustin Lee
 *
 * @see ActiveMQBuffers
 */
public interface ActiveMQBuffer extends DataInput {

   /**
    * {@return the underlying Netty's ByteBuf}
    */
   ByteBuf byteBuf();

   /**
    * {@return the number of bytes this buffer can contain}
    */
   int capacity();

   /**
    * {@return the {@code readerIndex} of this buffer.}
    */
   int readerIndex();

   /**
    * Sets the {@code readerIndex} of this buffer.
    *
    * @param readerIndex The reader's index
    * @throws IndexOutOfBoundsException if the specified {@code readerIndex} is less than {@code 0} or greater than
    *                                   {@code this.writerIndex}
    */
   void readerIndex(int readerIndex);

   /**
    * {@return the {@code writerIndex} of this buffer.}
    */
   int writerIndex();

   /**
    * Sets the {@code writerIndex} of this buffer.
    *
    * @param writerIndex The writer's index
    * @throws IndexOutOfBoundsException if the specified {@code writerIndex} is less than {@code this.readerIndex} or
    *                                   greater than {@code this.capacity}
    */
   void writerIndex(int writerIndex);

   /**
    * Sets the {@code readerIndex} and {@code writerIndex} of this buffer in one shot.  This method is useful when you
    * have to worry about the invocation order of {@link #readerIndex(int)} and {@link #writerIndex(int)} methods.  For
    * example, the following code will fail:
    *
    * <pre>
    * // Create a buffer whose readerIndex, writerIndex and capacity are
    * // 0, 0 and 8 respectively.
    * ChannelBuffer buf = ChannelBuffers.buffer(8);
    *
    * // IndexOutOfBoundsException is thrown because the specified
    * // readerIndex (2) cannot be greater than the current writerIndex (0).
    * buf.readerIndex(2);
    * buf.writerIndex(4);
    * </pre>
    * <p>
    * The following code will also fail:
    *
    * <pre>
    * // Create a buffer whose readerIndex, writerIndex and capacity are
    * // 0, 8 and 8 respectively.
    * ChannelBuffer buf = ChannelBuffers.wrappedBuffer(new byte[8]);
    *
    * // readerIndex becomes 8.
    * buf.readLong();
    *
    * // IndexOutOfBoundsException is thrown because the specified
    * // writerIndex (4) cannot be less than the current readerIndex (8).
    * buf.writerIndex(4);
    * buf.readerIndex(2);
    * </pre>
    * <p>
    * By contrast, {@link #setIndex(int, int)} guarantees that it never throws an {@link IndexOutOfBoundsException} as
    * long as the specified indexes meet basic constraints, regardless what the current index values of the buffer are:
    *
    * <pre>
    * // No matter what the current state of the buffer is, the following
    * // call always succeeds as long as the capacity of the buffer is not
    * // less than 4.
    * buf.setIndex(2, 4);
    * </pre>
    *
    * @param readerIndex The reader's index
    * @param writerIndex The writer's index
    * @throws IndexOutOfBoundsException if the specified {@code readerIndex} is less than 0, if the specified
    *                                   {@code writerIndex} is less than the specified {@code readerIndex} or if the
    *                                   specified {@code writerIndex} is greater than {@code this.capacity}
    */
   void setIndex(int readerIndex, int writerIndex);

   /**
    * {@return the number of readable bytes which is equal to {@code (this.writerIndex - this.readerIndex)}}
    */
   int readableBytes();

   /**
    * {@return the number of writable bytes which is equal to {@code (this.capacity - this.writerIndex)}}
    */
   int writableBytes();

   /**
    * {@return {@code true} if and only if {@code (this.writerIndex - this.readerIndex)} is greater than {@code 0}}
    */
   boolean readable();

   /**
    * {@return {@code true}if and only if {@code (this.capacity - this.writerIndex)} is greater than {@code 0}}
    */
   boolean writable();

   /**
    * Sets the {@code readerIndex} and {@code writerIndex} of this buffer to {@code 0}. This method is identical to
    * {@link #setIndex(int, int) setIndex(0, 0)}.
    * <p>
    * Please note that the behavior of this method is different from that of NIO buffer, which sets the {@code limit} to
    * the {@code capacity} of the buffer.
    */
   void clear();

   /**
    * Marks the current {@code readerIndex} in this buffer.  You can reposition the current {@code readerIndex} to the
    * marked {@code readerIndex} by calling {@link #resetReaderIndex()}. The initial value of the marked
    * {@code readerIndex} is {@code 0}.
    */
   void markReaderIndex();

   /**
    * Repositions the current {@code readerIndex} to the marked {@code readerIndex} in this buffer.
    *
    * @throws IndexOutOfBoundsException if the current {@code writerIndex} is less than the marked {@code readerIndex}
    */
   void resetReaderIndex();

   /**
    * Marks the current {@code writerIndex} in this buffer.  You can reposition the current {@code writerIndex} to the
    * marked {@code writerIndex} by calling {@link #resetWriterIndex()}. The initial value of the marked
    * {@code writerIndex} is {@code 0}.
    */
   void markWriterIndex();

   /**
    * Repositions the current {@code writerIndex} to the marked {@code writerIndex} in this buffer.
    *
    * @throws IndexOutOfBoundsException if the current {@code readerIndex} is greater than the marked
    *                                   {@code writerIndex}
    */
   void resetWriterIndex();

   /**
    * Discards the bytes between the 0th index and {@code readerIndex}. It moves the bytes between {@code readerIndex}
    * and {@code writerIndex} to the 0th index, and sets {@code readerIndex} and {@code writerIndex} to {@code 0} and
    * {@code oldWriterIndex - oldReaderIndex} respectively.
    * <p>
    * Please refer to the class documentation for more detailed explanation.
    */
   void discardReadBytes();

   /**
    * Gets a byte at the specified absolute {@code index} in this buffer. This method does not modify
    * {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @return The byte at the specified index
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or {@code index + 1} is
    *                                   greater than {@code this.capacity}
    */
   byte getByte(int index);

   /**
    * Gets an unsigned byte at the specified absolute {@code index} in this buffer.  This method does not modify
    * {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @return an unsigned byte at the specified absolute {@code index}
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or {@code index + 1} is
    *                                   greater than {@code this.capacity}
    */
   short getUnsignedByte(int index);

   /**
    * Gets a 16-bit short integer at the specified absolute {@code index} in this buffer.  This method does not modify
    * {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @return a 16-bit short integer at the specified absolute {@code index}
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or {@code index + 2} is
    *                                   greater than {@code this.capacity}
    */
   short getShort(int index);

   /**
    * Gets an unsigned 16-bit short integer at the specified absolute {@code index} in this buffer.  This method does
    * not modify {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @return an unsigned 16-bit short integer
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or {@code index + 2} is
    *                                   greater than {@code this.capacity}
    */
   int getUnsignedShort(int index);

   /**
    * Gets a 32-bit integer at the specified absolute {@code index} in this buffer.  This method does not modify
    * {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @return a 32-bit integer at the specified absolute {@code index}
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or {@code index + 4} is
    *                                   greater than {@code this.capacity}
    */
   int getInt(int index);

   /**
    * Gets an unsigned 32-bit integer at the specified absolute {@code index} in this buffer.  This method does not
    * modify {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index The index into this buffer
    * @return an unsigned 32-bit integer at the specified absolute {@code index}
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or {@code index + 4} is
    *                                   greater than {@code this.capacity}
    */
   long getUnsignedInt(int index);

   /**
    * Gets a 64-bit long integer at the specified absolute {@code index} in this buffer.  This method does not modify
    * {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @return a 64-bit long integer at the specified absolute {@code index}
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or {@code index + 8} is
    *                                   greater than {@code this.capacity}
    */
   long getLong(int index);

   /**
    * Transfers this buffer's data to the specified destination starting at the specified absolute {@code index} until
    * the destination becomes non-writable.  This method is basically same with
    * {@link #getBytes(int, ActiveMQBuffer, int, int)}, except that this method increases the {@code writerIndex} of the
    * destination by the number of the transferred bytes while {@link #getBytes(int, ActiveMQBuffer, int, int)} does
    * not. This method does not modify {@code readerIndex} or {@code writerIndex} of the source buffer (i.e.
    * {@code this}).
    *
    * @param index Index into the buffer
    * @param dst   The destination buffer
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or if
    *                                   {@code index + dst.writableBytes} is greater than {@code this.capacity}
    */
   void getBytes(int index, ActiveMQBuffer dst);

   /**
    * Transfers this buffer's data to the specified destination starting at the specified absolute {@code index}.  This
    * method is basically same with {@link #getBytes(int, ActiveMQBuffer, int, int)}, except that this method increases
    * the {@code writerIndex} of the destination by the number of the transferred bytes while
    * {@link #getBytes(int, ActiveMQBuffer, int, int)} does not. This method does not modify {@code readerIndex} or
    * {@code writerIndex} of the source buffer (i.e. {@code this}).
    *
    * @param length the number of bytes to transfer
    * @param index  Index into the buffer
    * @param dst    The destination buffer
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0}, if {@code index + length}
    *                                   is greater than {@code this.capacity}, or if {@code length} is greater than
    *                                   {@code dst.writableBytes}
    */
   void getBytes(int index, ActiveMQBuffer dst, int length);

   /**
    * Transfers this buffer's data to the specified destination starting at the specified absolute {@code index}. This
    * method does not modify {@code readerIndex} or {@code writerIndex} of both the source (i.e. {@code this}) and the
    * destination.
    *
    * @param dst      The destination bufferIndex the first index of the destination
    * @param length   The number of bytes to transfer
    * @param index    Index into the buffer
    * @param dstIndex The index into the destination bufferThe destination buffer
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0}, if the specified
    *                                   {@code dstIndex} is less than {@code 0}, if {@code index + length} is greater
    *                                   than {@code this.capacity}, or if {@code dstIndex + length} is greater than
    *                                   {@code dst.capacity}
    */
   void getBytes(int index, ActiveMQBuffer dst, int dstIndex, int length);

   /**
    * Transfers this buffer's data to the specified destination starting at the specified absolute {@code index}. This
    * method does not modify {@code readerIndex} or {@code writerIndex} of this buffer
    *
    * @param index Index into the buffer
    * @param dst   The destination buffer
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or if
    *                                   {@code index + dst.length} is greater than {@code this.capacity}
    */
   void getBytes(int index, byte[] dst);

   /**
    * Transfers this buffer's data to the specified destination starting at the specified absolute {@code index}. This
    * method does not modify {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param dstIndex The first index of the destination
    * @param length   The number of bytes to transfer
    * @param index    Index into the buffer
    * @param dst      The destination buffer
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0}, if the specified
    *                                   {@code dstIndex} is less than {@code 0}, if {@code index + length} is greater
    *                                   than {@code this.capacity}, or if {@code dstIndex + length} is greater than
    *                                   {@code dst.length}
    */
   void getBytes(int index, byte[] dst, int dstIndex, int length);

   /**
    * Transfers this buffer's data to the specified destination starting at the specified absolute {@code index} until
    * the destination's position reaches its limit. This method does not modify {@code readerIndex} or
    * {@code writerIndex} of this buffer while the destination's {@code position} will be increased.
    *
    * @param index Index into the buffer
    * @param dst   The destination buffer
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or if
    *                                   {@code index + dst.remaining()} is greater than {@code this.capacity}
    */
   void getBytes(int index, ByteBuffer dst);

   /**
    * Gets a char at the specified absolute {@code index} in this buffer.  This method does not modify
    * {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @return a char at the specified absolute {@code index}
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or {@code index + 2} is
    *                                   greater than {@code this.capacity}
    */
   char getChar(int index);

   /**
    * Gets a float at the specified absolute {@code index} in this buffer.  This method does not modify
    * {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @return a float at the specified absolute {@code index}
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or {@code index + 4} is
    *                                   greater than {@code this.capacity}
    */
   float getFloat(int index);

   /**
    * Gets a double at the specified absolute {@code index} in this buffer.  This method does not modify
    * {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @return a double at the specified absolute {@code index}
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or {@code index + 8} is
    *                                   greater than {@code this.capacity}
    */
   double getDouble(int index);

   /**
    * Sets the specified byte at the specified absolute {@code index} in this buffer. This method does not modify
    * {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @param value The specified byte
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or {@code index + 1} is
    *                                   greater than {@code this.capacity}
    */
   void setByte(int index, byte value);

   /**
    * Sets the specified 16-bit short integer at the specified absolute {@code index} in this buffer. This method does
    * not modify {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @param value The specified 16-bit short integer
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or {@code index + 2} is
    *                                   greater than {@code this.capacity}
    */
   void setShort(int index, short value);

   /**
    * Sets the specified 32-bit integer at the specified absolute {@code index} in this buffer. This method does not
    * modify {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @param value The specified 32-bit integer
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or {@code index + 4} is
    *                                   greater than {@code this.capacity}
    */
   void setInt(int index, int value);

   /**
    * Sets the specified 64-bit long integer at the specified absolute {@code index} in this buffer. This method does
    * not modify {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @param value The specified 64-bit long integer
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or {@code index + 8} is
    *                                   greater than {@code this.capacity}
    */
   void setLong(int index, long value);

   /**
    * Transfers the specified source buffer's data to this buffer starting at the specified absolute {@code index} until
    * the destination becomes unreadable.  This method is basically same with
    * {@link #setBytes(int, ActiveMQBuffer, int, int)}, except that this method increases the {@code readerIndex} of the
    * source buffer by the number of the transferred bytes while {@link #getBytes(int, ActiveMQBuffer, int, int)} does
    * not. This method does not modify {@code readerIndex} or {@code writerIndex} of the source buffer (i.e.
    * {@code this}).
    *
    * @param index Index into the buffer
    * @param src   The source buffer
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or if
    *                                   {@code index + src.readableBytes} is greater than {@code this.capacity}
    */
   void setBytes(int index, ActiveMQBuffer src);

   /**
    * Transfers the specified source buffer's data to this buffer starting at the specified absolute {@code index}. This
    * method is basically same with {@link #setBytes(int, ActiveMQBuffer, int, int)}, except that this method increases
    * the {@code readerIndex} of the source buffer by the number of the transferred bytes while
    * {@link #getBytes(int, ActiveMQBuffer, int, int)} does not. This method does not modify {@code readerIndex} or
    * {@code writerIndex} of the source buffer (i.e. {@code this}).
    *
    * @param length the number of bytes to transfer
    * @param index  Index into the buffer
    * @param src    The source buffer
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0}, if {@code index + length}
    *                                   is greater than {@code this.capacity}, or if {@code length} is greater than
    *                                   {@code src.readableBytes}
    */
   void setBytes(int index, ActiveMQBuffer src, int length);

   /**
    * Transfers the specified source buffer's data to this buffer starting at the specified absolute {@code index}. This
    * method does not modify {@code readerIndex} or {@code writerIndex} of both the source (i.e. {@code this}) and the
    * destination.
    *
    * @param src      The source bufferIndex the first index of the source
    * @param length   The number of bytes to transfer
    * @param index    Index into the buffer
    * @param srcIndex The source buffer index
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0}, if the specified
    *                                   {@code srcIndex} is less than {@code 0}, if {@code index + length} is greater
    *                                   than {@code this.capacity}, or if {@code srcIndex + length} is greater than
    *                                   {@code src.capacity}
    */
   void setBytes(int index, ActiveMQBuffer src, int srcIndex, int length);

   /**
    * Transfers the specified source array's data to this buffer starting at the specified absolute {@code index}. This
    * method does not modify {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @param src   The source buffer
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or if
    *                                   {@code index + src.length} is greater than {@code this.capacity}
    */
   void setBytes(int index, byte[] src);

   /**
    * Transfers the specified source array's data to this buffer starting at the specified absolute {@code index}. This
    * method does not modify {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index    Index into the buffer
    * @param src      The source buffer
    * @param srcIndex The source buffer index
    * @param length   The number of bytes to transfer
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0}, if the specified
    *                                   {@code srcIndex} is less than {@code 0}, if {@code index + length} is greater
    *                                   than {@code this.capacity}, or if {@code srcIndex + length} is greater than
    *                                   {@code src.length}
    */
   void setBytes(int index, byte[] src, int srcIndex, int length);

   /**
    * Transfers the specified source buffer's data to this buffer starting at the specified absolute {@code index} until
    * the source buffer's position reaches its limit. This method does not modify {@code readerIndex} or
    * {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @param src   The source buffer
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or if
    *                                   {@code index + src.remaining()} is greater than {@code this.capacity}
    */
   void setBytes(int index, ByteBuffer src);

   /**
    * Sets the specified char at the specified absolute {@code index} in this buffer. This method does not modify
    * {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @param value The specified char
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or {@code index + 2} is
    *                                   greater than {@code this.capacity}
    */
   void setChar(int index, char value);

   /**
    * Sets the specified float at the specified absolute {@code index} in this buffer. This method does not modify
    * {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @param value The specified float
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or {@code index + 4} is
    *                                   greater than {@code this.capacity}
    */
   void setFloat(int index, float value);

   /**
    * Sets the specified double at the specified absolute {@code index} in this buffer. This method does not modify
    * {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index Index into the buffer
    * @param value The specified double
    * @throws IndexOutOfBoundsException if the specified {@code index} is less than {@code 0} or {@code index + 8} is
    *                                   greater than {@code this.capacity}
    */
   void setDouble(int index, double value);

   /**
    * Gets a byte at the current {@code readerIndex} and increases the {@code readerIndex} by {@code 1} in this buffer.
    *
    * @return a byte at the current {@code readerIndex}
    * @throws IndexOutOfBoundsException if {@code this.readableBytes} is less than {@code 1}
    */
   @Override
   byte readByte();

   /**
    * Gets an unsigned byte at the current {@code readerIndex} and increases the {@code readerIndex} by {@code 1} in
    * this buffer.
    *
    * @return an unsigned byte at the current {@code readerIndex}
    * @throws IndexOutOfBoundsException if {@code this.readableBytes} is less than {@code 1}
    */
   @Override
   int readUnsignedByte();

   /**
    * Gets a 16-bit short integer at the current {@code readerIndex} and increases the {@code readerIndex} by {@code 2}
    * in this buffer.
    *
    * @return a 16-bit short integer at the current {@code readerIndex}
    * @throws IndexOutOfBoundsException if {@code this.readableBytes} is less than {@code 2}
    */
   @Override
   short readShort();

   /**
    * Gets an unsigned 16-bit short integer at the current {@code readerIndex} and increases the {@code readerIndex} by
    * {@code 2} in this buffer.
    *
    * @return an unsigned 16-bit short integer at the current {@code readerIndex}
    * @throws IndexOutOfBoundsException if {@code this.readableBytes} is less than {@code 2}
    */
   @Override
   int readUnsignedShort();

   /**
    * Gets a 32-bit integer at the current {@code readerIndex} and increases the {@code readerIndex} by {@code 4} in
    * this buffer.
    *
    * @return a 32-bit integer at the current {@code readerIndex}
    * @throws IndexOutOfBoundsException if {@code this.readableBytes} is less than {@code 4}
    */
   @Override
   int readInt();

   /**
    * Gets a (potentially {@code null}) 32-bit integer at the current {@code readerIndex} and increases the
    * {@code readerIndex} by {@code 4} in this buffer.
    *
    * @return a (potentially {@code null}) 32-bit integer at the current {@code readerIndex}
    * @throws IndexOutOfBoundsException if {@code this.readableBytes} is less than {@code 4}
    */
   Integer readNullableInt();

   /**
    * Gets an unsigned 32-bit integer at the current {@code readerIndex} and increases the {@code readerIndex} by
    * {@code 4} in this buffer.
    *
    * @return an unsigned 32-bit integer at the current {@code readerIndex}
    * @throws IndexOutOfBoundsException if {@code this.readableBytes} is less than {@code 4}
    */
   long readUnsignedInt();

   /**
    * Gets a 64-bit integer at the current {@code readerIndex} and increases the {@code readerIndex} by {@code 8} in
    * this buffer.
    *
    * @return a 64-bit integer at the current {@code readerIndex}
    * @throws IndexOutOfBoundsException if {@code this.readableBytes} is less than {@code 8}
    */
   @Override
   long readLong();

   /**
    * Gets a (potentially {@code null}) 64-bit integer at the current {@code readerIndex} and increases the
    * {@code readerIndex} by {@code 8} in this buffer.
    *
    * @return a (potentially {@code null}) 64-bit integer at the current {@code readerIndex}
    * @throws IndexOutOfBoundsException if {@code this.readableBytes} is less than {@code 8}
    */
   Long readNullableLong();

   /**
    * Gets a char at the current {@code readerIndex} and increases the {@code readerIndex} by {@code 2} in this buffer.
    *
    * @return a char at the current {@code readerIndex}
    * @throws IndexOutOfBoundsException if {@code this.readableBytes} is less than {@code 2}
    */
   @Override
   char readChar();

   /**
    * Gets a float at the current {@code readerIndex} and increases the {@code readerIndex} by {@code 4} in this
    * buffer.
    *
    * @return a float at the current {@code readerIndex}
    * @throws IndexOutOfBoundsException if {@code this.readableBytes} is less than {@code 4}
    */
   @Override
   float readFloat();

   /**
    * Gets a double at the current {@code readerIndex} and increases the {@code readerIndex} by {@code 8} in this
    * buffer.
    *
    * @return a double at the current {@code readerIndex}
    * @throws IndexOutOfBoundsException if {@code this.readableBytes} is less than {@code 8}
    */
   @Override
   double readDouble();

   /**
    * Gets a boolean at the current {@code readerIndex} and increases the {@code readerIndex} by {@code 1} in this
    * buffer.
    *
    * @return a boolean at the current {@code readerIndex}
    * @throws IndexOutOfBoundsException if {@code this.readableBytes} is less than {@code 1}
    */
   @Override
   boolean readBoolean();

   /**
    * Gets a (potentially {@code null}) boolean at the current {@code readerIndex} and increases the {@code readerIndex}
    * by {@code 1} in this buffer.
    *
    * @return a (potentially {@code null}) boolean at the current {@code readerIndex}
    * @throws IndexOutOfBoundsException if {@code this.readableBytes} is less than {@code 1}
    */
   Boolean readNullableBoolean();

   /**
    * Gets a SimpleString (potentially {@code null}) at the current {@code readerIndex}
    *
    * @return a SimpleString (potentially {@code null}) at the current {@code readerIndex}
    */
   SimpleString readNullableSimpleString();

   /**
    * Gets a String (potentially {@code null}) at the current {@code readerIndex}
    *
    * @return a String (potentially {@code null}) at the current {@code readerIndex}
    */
   String readNullableString();

   /**
    * Gets a non-null SimpleString at the current {@code readerIndex}
    *
    * @return a non-null SimpleString at the current {@code readerIndex}
    */
   SimpleString readSimpleString();

   /**
    * Gets a non-null String at the current {@code readerIndex}
    *
    * @return a non-null String at the current {@code readerIndex}
    */
   String readString();

   /**
    * Gets a UTF-8 String at the current {@code readerIndex}
    *
    * @return a UTF-8 String at the current {@code readerIndex}
    */
   @Override
   String readUTF();

   /**
    * Returns a new slice of this buffer's sub-region starting at the current {@code readerIndex} and increases the
    * {@code readerIndex} by the size of the new slice (= {@code length}).
    *
    * @param length the size of the new slice
    * @return the newly created slice
    * @throws IndexOutOfBoundsException if {@code length} is greater than {@code this.readableBytes}
    */
   ActiveMQBuffer readSlice(int length);

   /**
    * Transfers this buffer's data to the specified destination starting at the current {@code readerIndex} until the
    * destination becomes non-writable, and increases the {@code readerIndex} by the number of the transferred bytes.
    * This method is basically same with {@link #readBytes(ActiveMQBuffer, int, int)}, except that this method increases
    * the {@code writerIndex} of the destination by the number of the transferred bytes while
    * {@link #readBytes(ActiveMQBuffer, int, int)} does not.
    *
    * @param dst The destination buffer
    * @throws IndexOutOfBoundsException if {@code dst.writableBytes} is greater than {@code this.readableBytes}
    */
   void readBytes(ActiveMQBuffer dst);

   /**
    * Transfers this buffer's data to the specified destination starting at the current {@code readerIndex} and
    * increases the {@code readerIndex} by the number of the transferred bytes (= {@code length}).  This method is
    * basically same with {@link #readBytes(ActiveMQBuffer, int, int)}, except that this method increases the
    * {@code writerIndex} of the destination by the number of the transferred bytes (= {@code length}) while
    * {@link #readBytes(ActiveMQBuffer, int, int)} does not.
    *
    * @param dst    The destination buffer
    * @param length The number of bytes to transfer
    * @throws IndexOutOfBoundsException if {@code length} is greater than {@code this.readableBytes} or if
    *                                   {@code length} is greater than {@code dst.writableBytes}
    */
   void readBytes(ActiveMQBuffer dst, int length);

   /**
    * Transfers this buffer's data to the specified destination starting at the current {@code readerIndex} and
    * increases the {@code readerIndex} by the number of the transferred bytes (= {@code length}).
    *
    * @param dstIndex The destination buffer index
    * @param length   the number of bytes to transfer
    * @param dst      The destination buffer
    * @throws IndexOutOfBoundsException if the specified {@code dstIndex} is less than {@code 0}, if {@code length} is
    *                                   greater than {@code this.readableBytes}, or if {@code dstIndex + length} is
    *                                   greater than {@code dst.capacity}
    */
   void readBytes(ActiveMQBuffer dst, int dstIndex, int length);

   /**
    * Transfers this buffer's data to the specified destination starting at the current {@code readerIndex} and
    * increases the {@code readerIndex} by the number of the transferred bytes (= {@code dst.length}).
    *
    * @param dst The destination buffer
    * @throws IndexOutOfBoundsException if {@code dst.length} is greater than {@code this.readableBytes}
    */
   void readBytes(byte[] dst);

   /**
    * Transfers this buffer's data to the specified destination starting at the current {@code readerIndex} and
    * increases the {@code readerIndex} by the number of the transferred bytes (= {@code length}).
    *
    * @param dstIndex The destination bufferIndex
    * @param length   the number of bytes to transfer
    * @param dst      The destination buffer
    * @throws IndexOutOfBoundsException if the specified {@code dstIndex} is less than {@code 0}, if {@code length} is
    *                                   greater than {@code this.readableBytes}, or if {@code dstIndex + length} is
    *                                   greater than {@code dst.length}
    */
   void readBytes(byte[] dst, int dstIndex, int length);

   /**
    * Transfers this buffer's data to the specified destination starting at the current {@code readerIndex} until the
    * destination's position reaches its limit, and increases the {@code readerIndex} by the number of the transferred
    * bytes.
    *
    * @param dst The destination buffer
    * @throws IndexOutOfBoundsException if {@code dst.remaining()} is greater than {@code this.readableBytes}
    */
   void readBytes(ByteBuffer dst);

   /**
    * Increases the current {@code readerIndex} by the specified {@code length} in this buffer.
    *
    * @param length The number of bytes to skip
    * @throws IndexOutOfBoundsException if {@code length} is greater than {@code this.readableBytes}
    */
   @Override
   int skipBytes(int length);

   /**
    * Sets the specified byte at the current {@code writerIndex} and increases the {@code writerIndex} by {@code 1} in
    * this buffer.
    *
    * @param value The specified byte
    * @throws IndexOutOfBoundsException if {@code this.writableBytes} is less than {@code 1}
    */
   void writeByte(byte value);

   /**
    * Sets the specified 16-bit short integer at the current {@code writerIndex} and increases the {@code writerIndex}
    * by {@code 2} in this buffer.
    *
    * @param value The specified 16-bit short integer
    * @throws IndexOutOfBoundsException if {@code this.writableBytes} is less than {@code 2}
    */
   void writeShort(short value);

   /**
    * Sets the specified unsigned 16-bit short integer at the current {@code writerIndex} and increases the {@code writerIndex}
    * by {@code 2} in this buffer.
    *
    * @param value The specified unsigned 16-bit short integer (0-65535)
    * @throws IllegalArgumentException if {@code value} is less than 0 or greater than 65535
    * @throws IndexOutOfBoundsException if {@code this.writableBytes} is less than {@code 2}
    */
   void writeUnsignedShort(int value);

   /**
    * Sets the specified 32-bit integer at the current {@code writerIndex} and increases the {@code writerIndex} by
    * {@code 4} in this buffer.
    *
    * @param value The specified 32-bit integer
    * @throws IndexOutOfBoundsException if {@code this.writableBytes} is less than {@code 4}
    */
   void writeInt(int value);

   /**
    * Sets the specified (potentially {@code null}) 32-bit integer at the current {@code writerIndex} and increases the
    * {@code writerIndex} by {@code 4} in this buffer.
    *
    * @param value The specified (potentially {@code null}) 32-bit integer
    * @throws IndexOutOfBoundsException if {@code this.writableBytes} is less than {@code 4}
    */
   void writeNullableInt(Integer value);

   /**
    * Sets the specified 64-bit long integer at the current {@code writerIndex} and increases the {@code writerIndex} by
    * {@code 8} in this buffer.
    *
    * @param value The specified 64-bit long integer
    * @throws IndexOutOfBoundsException if {@code this.writableBytes} is less than {@code 8}
    */
   void writeLong(long value);

   /**
    * Sets the specified (potentially {@code null}) 64-bit long integer at the current {@code writerIndex} and increases
    * the {@code writerIndex} by {@code 8} in this buffer.
    *
    * @param value The specified (potentially {@code null}) 64-bit long integer
    * @throws IndexOutOfBoundsException if {@code this.writableBytes} is less than {@code 8}
    */
   void writeNullableLong(Long value);

   /**
    * Sets the specified char at the current {@code writerIndex} and increases the {@code writerIndex} by {@code 2} in
    * this buffer.
    *
    * @param chr The specified char
    * @throws IndexOutOfBoundsException if {@code this.writableBytes} is less than {@code 2}
    */
   void writeChar(char chr);

   /**
    * Sets the specified float at the current {@code writerIndex} and increases the {@code writerIndex} by {@code 4} in
    * this buffer.
    *
    * @param value The specified float
    * @throws IndexOutOfBoundsException if {@code this.writableBytes} is less than {@code 4}
    */
   void writeFloat(float value);

   /**
    * Sets the specified double at the current {@code writerIndex} and increases the {@code writerIndex} by {@code 8} in
    * this buffer.
    *
    * @param value The specified double
    * @throws IndexOutOfBoundsException if {@code this.writableBytes} is less than {@code 8}
    */
   void writeDouble(double value);

   /**
    * Sets the specified boolean at the current {@code writerIndex}
    *
    * @param val The specified boolean
    */
   void writeBoolean(boolean val);

   /**
    * Sets the specified (potentially {@code null}) Boolean at the current {@code writerIndex}
    *
    * @param val The specified boolean
    */
   void writeNullableBoolean(Boolean val);

   /**
    * Sets the specified SimpleString (potentially {@code null}) at the current {@code writerIndex}
    *
    * @param val The specified SimpleString
    */
   void writeNullableSimpleString(SimpleString val);

   /**
    * Sets the specified String (potentially {@code null}) at the current {@code writerIndex}
    *
    * @param val The specified String
    */
   void writeNullableString(String val);

   /**
    * Sets the specified non-null SimpleString at the current {@code writerIndex}
    *
    * @param val The specified non-null SimpleString
    */
   void writeSimpleString(SimpleString val);

   /**
    * Sets the specified non-null String at the current {@code writerIndex}
    *
    * @param val The specified non-null String
    */
   void writeString(String val);

   /**
    * Sets the specified UTF-8 String at the current {@code writerIndex}
    *
    * @param utf The specified UTF-8 String
    */

   void writeUTF(String utf);

   /**
    * Transfers the specified source buffer's data to this buffer starting at the current {@code writerIndex} and
    * increases the {@code writerIndex} by the number of the transferred bytes (= {@code length}).  This method is
    * basically same with {@link #writeBytes(ActiveMQBuffer, int, int)}, except that this method increases the
    * {@code readerIndex} of the source buffer by the number of the transferred bytes (= {@code length}) while
    * {@link #writeBytes(ActiveMQBuffer, int, int)} does not.
    *
    * @param length the number of bytes to transfer
    * @param src    The source buffer
    * @throws IndexOutOfBoundsException if {@code length} is greater than {@code this.writableBytes} or if
    *                                   {@code length} is greater then {@code src.readableBytes}
    */
   void writeBytes(ActiveMQBuffer src, int length);

   /**
    * Transfers the specified source buffer's data to this buffer starting at the current {@code writerIndex} and
    * increases the {@code writerIndex} by the number of the transferred bytes (= {@code length}).
    *
    * @param srcIndex the first index of the source
    * @param length   the number of bytes to transfer
    * @param src      The source buffer
    * @throws IndexOutOfBoundsException if the specified {@code srcIndex} is less than {@code 0}, if
    *                                   {@code srcIndex + length} is greater than {@code src.capacity}, or if
    *                                   {@code length} is greater than {@code this.writableBytes}
    */
   void writeBytes(ActiveMQBuffer src, int srcIndex, int length);

   /**
    * Transfers the specified source array's data to this buffer starting at the current {@code writerIndex} and
    * increases the {@code writerIndex} by the number of the transferred bytes (= {@code src.length}).
    *
    * @param src The source buffer
    * @throws IndexOutOfBoundsException if {@code src.length} is greater than {@code this.writableBytes}
    */
   void writeBytes(byte[] src);

   /**
    * Transfers the specified source array's data to this buffer starting at the current {@code writerIndex} and
    * increases the {@code writerIndex} by the number of the transferred bytes (= {@code length}).
    *
    * @param srcIndex the first index of the source
    * @param length   the number of bytes to transfer
    * @param src      The source buffer
    * @throws IndexOutOfBoundsException if the specified {@code srcIndex} is less than {@code 0}, if
    *                                   {@code srcIndex + length} is greater than {@code src.length}, or if
    *                                   {@code length} is greater than {@code this.writableBytes}
    */
   void writeBytes(byte[] src, int srcIndex, int length);

   /**
    * Transfers the specified source buffer's data to this buffer starting at the current {@code writerIndex} until the
    * source buffer's position reaches its limit, and increases the {@code writerIndex} by the number of the transferred
    * bytes.
    *
    * @param src The source buffer
    * @throws IndexOutOfBoundsException if {@code src.remaining()} is greater than {@code this.writableBytes}
    */
   void writeBytes(ByteBuffer src);

   /**
    * Transfers the specified source buffer's data to this buffer starting at the current {@code writerIndex} until the
    * source buffer's position reaches its limit, and increases the {@code writerIndex} by the number of the transferred
    * bytes.
    *
    * @param src The source buffer
    * @throws IndexOutOfBoundsException if {@code src.remaining()} is greater than {@code this.writableBytes}
    */
   void writeBytes(ByteBuf src, int srcIndex, int length);

   /**
    * Returns a copy of this buffer's readable bytes.  Modifying the content of the returned buffer or this buffer does
    * not affect each other at all. This method is identical to
    * {@code buf.copy(buf.readerIndex(), buf.readableBytes())}. This method does not modify {@code readerIndex} or
    * {@code writerIndex} of this buffer.
    *
    * @return a copy of this buffer's readable bytes
    */
   ActiveMQBuffer copy();

   /**
    * Returns a copy of this buffer's sub-region.  Modifying the content of the returned buffer or this buffer does not
    * affect each other at all. This method does not modify {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index  Index into the buffer
    * @param length The number of bytes to copy
    * @return a copy of this buffer's readable bytes
    */
   ActiveMQBuffer copy(int index, int length);

   /**
    * Returns a slice of this buffer's readable bytes. Modifying the content of the returned buffer or this buffer
    * affects each other's content while they maintain separate indexes and marks.  This method is identical to
    * {@code buf.slice(buf.readerIndex(), buf.readableBytes())}. This method does not modify {@code readerIndex} or
    * {@code writerIndex} of this buffer.
    *
    * @return a slice of this buffer's readable bytes
    */
   ActiveMQBuffer slice();

   /**
    * Returns a slice of this buffer's sub-region. Modifying the content of the returned buffer or this buffer affects
    * each other's content while they maintain separate indexes and marks. This method does not modify
    * {@code readerIndex} or {@code writerIndex} of this buffer.
    *
    * @param index  Index into the buffer
    * @param length The number of bytes
    * @return a slice of this buffer's sub-region
    */
   ActiveMQBuffer slice(int index, int length);

   /**
    * Returns a buffer which shares the whole region of this buffer. Modifying the content of the returned buffer or
    * this buffer affects each other's content while they maintain separate indexes and marks. This method is identical
    * to {@code buf.slice(0, buf.capacity())}. This method does not modify {@code readerIndex} or {@code writerIndex} of
    * this buffer.
    *
    * @return a buffer which shares the whole region of this buffer
    */
   ActiveMQBuffer duplicate();

   /**
    * Converts this buffer's readable bytes into a NIO buffer.  The returned buffer might or might not share the content
    * with this buffer, while they have separate indexes and marks.  This method is identical to
    * {@code buf.toByteBuffer(buf.readerIndex(), buf.readableBytes())}. This method does not modify {@code readerIndex}
    * or {@code writerIndex} of this buffer.
    *
    * @return A converted NIO ByteBuffer
    */
   ByteBuffer toByteBuffer();

   /**
    * Converts this buffer's sub-region into a NIO buffer.  The returned buffer might or might not share the content
    * with this buffer, while they have separate indexes and marks. This method does not modify {@code readerIndex} or
    * {@code writerIndex} of this buffer.
    *
    * @param index  Index into the buffer
    * @param length The number of bytes
    * @return A converted NIO Buffer
    */
   ByteBuffer toByteBuffer(int index, int length);

   /**
    * Release any underlying resources held by this buffer
    */
   void release();

}
