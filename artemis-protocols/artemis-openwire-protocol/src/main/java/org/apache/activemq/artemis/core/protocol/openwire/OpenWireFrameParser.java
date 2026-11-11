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

package org.apache.activemq.artemis.core.protocol.openwire;

import java.lang.invoke.MethodHandles;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.apache.activemq.artemis.utils.DataConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This MessageDecoder is based on LengthFieldBasedFrameDecoder.
 *  When OpenWire clients send a Large Message (large in the context of size only as openwire does not support message chunk streaming).
 *  In that context the server will transfer the huge frame to a Heap Buffer, instead of keeping a really large native buffer.
 *
 *  There's a test showing this situation under ./soak-tests named OWLeakTest. The test will send 200MB messages. For every message sent we would have 200MB native buffers
 *  not leaving much space for the broker to handle its IO as most of the IO needs to be done with Native Memory.
 *  */
public class OpenWireFrameParser extends ByteToMessageDecoder {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   final int openwireMaxPacketChunkSize;

   public OpenWireFrameParser(int openwireMaxPacketChunkSize) {
      this.openwireMaxPacketChunkSize = openwireMaxPacketChunkSize;
   }

   ByteBuf outBuffer;
   int bufferSize = -1;

   @Override
   protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
      if (ctx.isRemoved()) {
         return;
      }

      if (bufferSize == -1) {
         if (in.readableBytes() < DataConstants.SIZE_INT) {
            return;
         }

         bufferSize = in.getInt(in.readerIndex()) + DataConstants.SIZE_INT;

         if (openwireMaxPacketChunkSize > 0 && bufferSize > openwireMaxPacketChunkSize) {
            if (logger.isTraceEnabled()) {
               logger.trace("Creating a heapBuffer sized as {} as it is beyond {} chunk limit", bufferSize, openwireMaxPacketChunkSize);
            }
            // we will use a heap buffer for large frames.
            // to avoid competing for resources with the broker on native buffers.
            // to save the broker in case users send huge messages in openwire.
            outBuffer = UnpooledByteBufAllocator.DEFAULT.heapBuffer(bufferSize);
         }
      }

      if (outBuffer != null) {

         int missingBytes = bufferSize - outBuffer.writerIndex();

         int bytesToRead = Math.min(missingBytes, in.readableBytes());

         outBuffer.writeBytes(in, bytesToRead);

         if (outBuffer.writerIndex() == bufferSize) {
            out.add(outBuffer);
            outBuffer = null;
            bufferSize = -1;
         }
      } else {
         if (in.readableBytes() >= bufferSize) {
            out.add(in.retainedSlice(in.readerIndex(), bufferSize));
            in.skipBytes(bufferSize);
            outBuffer = null;
            bufferSize = -1;
         }
      }
   }

}

