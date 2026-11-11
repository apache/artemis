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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WebSocketFrameEncoderTest {

   private int maxFramePayloadLength = 100;
   private WebSocketFrameEncoder binarySpy;
   private WebSocketFrameEncoder textSpy;

   @Mock
   private ChannelHandlerContext ctx;
   @Mock
   private ChannelPromise promise;

   @BeforeEach
   public void setUp() throws Exception {
      binarySpy = spy(new WebSocketFrameEncoder(maxFramePayloadLength, WebSocketFrameEncoderType.BINARY));
      textSpy = spy(new WebSocketFrameEncoder(maxFramePayloadLength, WebSocketFrameEncoderType.TEXT));
   }

   @Test
   public void testWriteNonByteBufBinary() throws Exception {
      testWriteNonByteBuf(binarySpy);
   }

   @Test
   public void testWriteNonByteBufText() throws Exception {
      testWriteNonByteBuf(textSpy);
   }

   private void testWriteNonByteBuf(WebSocketFrameEncoder spy) throws Exception {
      Object msg = "Not a ByteBuf";

      spy.write(ctx, msg, promise); //test

      verify(spy).write(ctx, msg, promise);
      verify(ctx).write(msg, promise);
      verifyNoMoreInteractions(spy, ctx);
      verifyNoMoreInteractions(promise);
   }

   @Test
   public void testWriteReleaseBufferBinary() throws Exception {
      testWriteReleaseBuffer(binarySpy, BinaryWebSocketFrame.class);
   }

   @Test
   public void testWriteReleaseBufferText() throws Exception {
      testWriteReleaseBuffer(textSpy, TextWebSocketFrame.class);
   }

   private void testWriteReleaseBuffer(WebSocketFrameEncoder spy, Class webSocketFrameClass) throws Exception {
      String content = "Buffer should be released";

      int utf8Bytes = ByteBufUtil.utf8Bytes(content);
      ByteBuf msg = Unpooled.directBuffer(utf8Bytes);
      ByteBufUtil.reserveAndWriteUtf8(msg, content, utf8Bytes);

      ArgumentCaptor<WebSocketFrame> frameCaptor = ArgumentCaptor.forClass(WebSocketFrame.class);

      spy.write(ctx, msg, promise); //test

      assertEquals(0, msg.refCnt());
      assertEquals(0, msg.readableBytes());
      verify(ctx).writeAndFlush(frameCaptor.capture(), eq(promise));
      WebSocketFrame frame = frameCaptor.getValue();
      assertTrue(webSocketFrameClass.isInstance(frame));
      assertTrue(frame.isFinalFragment());
      assertEquals(content, frame.content().toString(StandardCharsets.UTF_8));
   }

   @Test
   public void testWriteSingleFrameBinary() throws Exception {
      testWriteSingleFrame(binarySpy, BinaryWebSocketFrame.class);
   }

   @Test
   public void testWriteSingleFrameText() throws Exception {
      testWriteSingleFrame(textSpy, TextWebSocketFrame.class);
   }

   private void testWriteSingleFrame(WebSocketFrameEncoder spy, Class webSocketFrameClass) throws Exception {
      String content = "Content MSG length less than max frame payload length: " + maxFramePayloadLength;
      ByteBuf msg = Unpooled.copiedBuffer(content, StandardCharsets.UTF_8);
      ArgumentCaptor<WebSocketFrame> frameCaptor = ArgumentCaptor.forClass(WebSocketFrame.class);

      spy.write(ctx, msg, promise); //test

      assertEquals(0, msg.readableBytes());
      verify(ctx).writeAndFlush(frameCaptor.capture(), eq(promise));
      WebSocketFrame frame = frameCaptor.getValue();
      assertTrue(webSocketFrameClass.isInstance(frame));
      assertTrue(frame.isFinalFragment());
      assertEquals(content, frame.content().toString(StandardCharsets.UTF_8));
   }

   @Test
   public void testWriteContinuationFramesBinary() throws Exception {
      testWriteContinuationFrames(binarySpy, BinaryWebSocketFrame.class);
   }

   @Test
   public void testWriteContinuationFramesText() throws Exception {
      testWriteContinuationFrames(textSpy, TextWebSocketFrame.class);
   }

   private void testWriteContinuationFrames(WebSocketFrameEncoder spy, Class webSocketFrameClass) throws Exception {
      String contentPart = "Content MSG Length @ ";
      StringBuilder contentBuilder = new StringBuilder(3 * maxFramePayloadLength);

      while (contentBuilder.length() < 2 * maxFramePayloadLength) {
         contentBuilder.append(contentPart);
         contentBuilder.append(contentBuilder.length());
         contentBuilder.append('\n');
      }

      String content = contentBuilder.toString();
      int length = content.length();
      assertTrue(length > 2 * maxFramePayloadLength); //at least 3 frames of data
      ByteBuf msg = Unpooled.copiedBuffer(content, StandardCharsets.UTF_8);
      ArgumentCaptor<WebSocketFrame> frameCaptor = ArgumentCaptor.forClass(WebSocketFrame.class);

      spy.write(ctx, msg, promise); //test

      assertEquals(0, msg.readableBytes());
      verify(spy).write(ctx, msg, promise);
      verify(ctx, times(3)).writeAndFlush(frameCaptor.capture(), eq(promise));
      List<WebSocketFrame> frames = frameCaptor.getAllValues();
      assertEquals(3, frames.size());

      int offset = 0;
      WebSocketFrame first = frames.get(0);
      assertTrue(webSocketFrameClass.isInstance(first));
      assertFalse(first.isFinalFragment());
      assertEquals(content.substring(offset, offset + maxFramePayloadLength),
                   first.content().toString(StandardCharsets.UTF_8));

      offset += maxFramePayloadLength;
      WebSocketFrame second = frames.get(1);
      assertInstanceOf(ContinuationWebSocketFrame.class, second);
      assertFalse(second.isFinalFragment());
      assertEquals(content.substring(offset, offset + maxFramePayloadLength),
                   second.content().toString(StandardCharsets.UTF_8));

      offset += maxFramePayloadLength;
      WebSocketFrame last = frames.get(2);
      assertInstanceOf(ContinuationWebSocketFrame.class, last);
      assertTrue(last.isFinalFragment());
      assertEquals(content.substring(offset), last.content().toString(StandardCharsets.UTF_8));

      verifyNoMoreInteractions(spy, ctx);
      verifyNoMoreInteractions(promise);
   }
}
