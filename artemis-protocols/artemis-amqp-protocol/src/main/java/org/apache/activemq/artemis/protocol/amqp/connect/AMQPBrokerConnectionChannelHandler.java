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
package org.apache.activemq.artemis.protocol.amqp.connect;

import java.util.concurrent.Executor;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.group.ChannelGroup;
import org.apache.activemq.artemis.protocol.amqp.proton.handler.ProtonHandler;
import org.apache.activemq.artemis.spi.core.remoting.ClientConnectionLifeCycleListener;

/**
 * Common handler implementation for client and server side handler.
 */
public class AMQPBrokerConnectionChannelHandler extends ChannelDuplexHandler {

   private final ChannelGroup group;

   private final ProtonHandler handler;

   private final ClientConnectionLifeCycleListener listener;

   private final Executor listenerExecutor;

   private boolean active = true;

   protected AMQPBrokerConnectionChannelHandler(final ChannelGroup group, final ProtonHandler handler, ClientConnectionLifeCycleListener listener, Executor executor) {
      this.group = group;
      this.handler = handler;
      this.listener = listener;
      this.listenerExecutor = executor;
   }

   protected static Object channelId(Channel channel) {
      return channel.id();
   }

   @Override
   public void channelActive(final ChannelHandlerContext ctx) throws Exception {
      group.add(ctx.channel());
      ctx.fireChannelActive();
   }

   @Override
   public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      synchronized (this) {
         if (active) {
            listenerExecutor.execute(() -> listener.connectionDestroyed(channelId(ctx.channel()), true));
            super.channelInactive(ctx);
            active = false;
         }
      }
   }

   @Override
   public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
   }

   @Override
   public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
      ByteBuf buffer = (ByteBuf) msg;

      try {
         handler.inputBuffer(buffer);
      } finally {
         buffer.release();
      }
   }
}
