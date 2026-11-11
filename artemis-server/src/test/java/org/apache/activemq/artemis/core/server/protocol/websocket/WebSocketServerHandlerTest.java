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
package org.apache.activemq.artemis.core.server.protocol.websocket;

import java.util.Arrays;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class WebSocketServerHandlerTest {

   private int maxFramePayloadLength;
   private List<String> supportedProtocols;
   private WebSocketServerHandler spy;

   @BeforeEach
   public void setup() throws Exception {
      maxFramePayloadLength = 8192;
      supportedProtocols = Arrays.asList("STOMP");
      spy = spy(new WebSocketServerHandler(supportedProtocols, maxFramePayloadLength, WebSocketFrameEncoderType.BINARY, false));
   }

   @Test
   public void testRead0HandleContinuationFrame() throws Exception {
      ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
      Object msg = new ContinuationWebSocketFrame();

      spy.channelRead0(ctx, msg); //test

      verify(spy).channelRead0(ctx, msg);
      verify(ctx).fireChannelRead(any(ByteBuf.class));
      verifyNoMoreInteractions(spy, ctx);
   }

   @Test
   public void testRead0HandleBinaryFrame() throws Exception {
      ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
      Object msg = new BinaryWebSocketFrame();

      spy.channelRead0(ctx, msg); //test

      verify(spy).channelRead0(ctx, msg);
      verify(ctx).fireChannelRead(any(ByteBuf.class));
      verifyNoMoreInteractions(spy, ctx);
   }

   @Test
   public void testRead0HandlePongFrame() throws Exception {
      ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
      Object msg = new PongWebSocketFrame();

      spy.channelRead0(ctx, msg);

      verify(spy).channelRead0(ctx, msg);
      verifyNoMoreInteractions(spy, ctx);
   }

   @Test
   public void testRead0HandleTextFrame() throws Exception {
      ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
      Object msg = new TextWebSocketFrame();

      spy.channelRead0(ctx, msg); //test

      verify(spy).channelRead0(ctx, msg);
      verify(ctx).fireChannelRead(any(ByteBuf.class));
      verifyNoMoreInteractions(spy, ctx);
   }
}
