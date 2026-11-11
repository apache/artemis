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

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.haproxy.HAProxyMessage;

import static org.apache.activemq.artemis.utils.ProxyProtocolUtil.PROXY_PROTOCOL_DESTINATION_ADDRESS;
import static org.apache.activemq.artemis.utils.ProxyProtocolUtil.PROXY_PROTOCOL_SOURCE_ADDRESS;
import static org.apache.activemq.artemis.utils.ProxyProtocolUtil.PROXY_PROTOCOL_VERSION;

public class HAProxyMessageHandler extends ChannelInboundHandlerAdapter {

   private boolean skipProxyBytes = false;

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof HAProxyMessage haProxyMessage) {
         ctx.channel().attr(PROXY_PROTOCOL_SOURCE_ADDRESS).set(haProxyMessage.sourceAddress() + ":" + Integer.toString(haProxyMessage.sourcePort()));
         ctx.channel().attr(PROXY_PROTOCOL_DESTINATION_ADDRESS).set(haProxyMessage.destinationAddress() + ":" + Integer.toString(haProxyMessage.destinationPort()));
         ctx.channel().attr(PROXY_PROTOCOL_VERSION).set(haProxyMessage.protocolVersion().toString());
         skipProxyBytes = true;
      } else {
         if (skipProxyBytes) {
            // once we get the HAProxyMessage and set the proper attributes our job is done
            ctx.pipeline().remove(this);
            // slice off the proxy-related bytes that have already been read so other protocol handlers don't choke on them
            ctx.fireChannelRead(((ByteBuf) msg).slice());
         } else {
            throw new IOException("Did not receive expected HAProxyMessage; instead received: " + msg);
         }
      }
   }
}
