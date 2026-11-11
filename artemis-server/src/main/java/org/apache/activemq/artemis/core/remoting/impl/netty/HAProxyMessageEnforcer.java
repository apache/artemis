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
package org.apache.activemq.artemis.core.remoting.impl.netty;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.utils.SocketAddressUtil;

/**
 * This Netty handler enforces the presence or absence of PROXY protocol messages. It verifies conformity and then
 * removes itself from the pipeline.
 * <p>
 * If the incoming message protocol does not align with the enforcer's requirements the connection is closed.
 */
public class HAProxyMessageEnforcer extends ByteToMessageDecoder {

   final String acceptorName;

   HAProxyMessageEnforcer(String acceptorName) {
      this.acceptorName = acceptorName;
   }

   @Override
   protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
      if (in.readableBytes() < 4) {
         return;
      }
      ctx.pipeline().remove(this);
      if (!isProxyProtocol(in)) {
         ActiveMQServerLogger.LOGGER.proxyProtocolViolation(SocketAddressUtil.toString(ctx.channel().remoteAddress()), acceptorName, true, "did not use");
         ctx.close();
      }
   }

   public static boolean isProxyProtocol(ByteBuf in) {
      final int magic1 = in.getUnsignedByte(in.readerIndex());
      final int magic2 = in.getUnsignedByte(in.readerIndex() + 1);
      final int magic3 = in.getUnsignedByte(in.readerIndex() + 2);
      final int magic4 = in.getUnsignedByte(in.readerIndex() + 3);
      return (magic1 == 'P' && magic2 == 'R' && magic3 == 'O' && magic4 == 'X') || // V1
         (magic1 == '\r' && magic2 == '\n' && magic3 == '\r' && magic4 == '\n'); // V2
   }
}