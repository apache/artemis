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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
public class HttpAcceptorHandlerTest {

   private static final String HTTP_HANDLER = "http-handler";

   private HttpKeepAliveRunnable spy;

   @BeforeEach
   public void setUp() throws Exception {
      spy = spy(new HttpKeepAliveRunnable());
   }

   @Test
   public void testUnregisterIsCalledTwiceWhenChannelIsInactive() {
      EmbeddedChannel channel = new EmbeddedChannel();

      HttpAcceptorHandler httpHandler = new HttpAcceptorHandler(spy, 1000, channel);
      channel.pipeline().addLast(HTTP_HANDLER, httpHandler);

      channel.close();

      Mockito.verify(spy, Mockito.times(2)).unregisterKeepAliveHandler(httpHandler);
   }

   @Test
   public void testUnregisterIsCalledWhenHandlerIsRemovedFromPipeline() {
      EmbeddedChannel channel = new EmbeddedChannel();

      HttpAcceptorHandler httpHandler = new HttpAcceptorHandler(spy, 1000, channel);
      channel.pipeline().addLast(HTTP_HANDLER, httpHandler);

      channel.pipeline().remove(HTTP_HANDLER);

      Mockito.verify(spy).unregisterKeepAliveHandler(httpHandler);
   }

   @Test
   public void testGetWithoutUpgradeIsConsumedAndReleased() {
      EmbeddedChannel channel = new EmbeddedChannel();
      channel.pipeline().addLast(HTTP_HANDLER, new HttpAcceptorHandler(spy, 1000, channel));

      FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
      channel.writeInbound(request);

      assertNull(channel.readInbound());
      assertEquals(0, request.refCnt());

      channel.finishAndReleaseAll();
   }

   @Test
   public void testGetWithUpgradeIsForwarded() {
      EmbeddedChannel channel = new EmbeddedChannel();
      channel.pipeline().addLast(HTTP_HANDLER, new HttpAcceptorHandler(spy, 1000, channel));

      FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
      request.headers().set(HttpHeaderNames.UPGRADE, "websocket");
      channel.writeInbound(request);

      assertSame(request, channel.readInbound());
      assertEquals(1, request.refCnt());
      request.release();

      channel.finishAndReleaseAll();
   }

   /**
    * Regression for ARTEMIS-6172: an async server write can consume the only HTTP response
    * slot from a prior POST. A subsequent client GET must enqueue a new slot so the
    * request reply is not starved.
    */
   @Test
   public void testGetUnblocksReplyAfterAsyncPacketConsumesResponseSlot() throws Exception {
      EmbeddedChannel channel = new EmbeddedChannel();
      channel.pipeline().addLast(HTTP_HANDLER, new HttpAcceptorHandler(spy, 1000, channel));

      FullHttpRequest post = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/",
         Unpooled.wrappedBuffer(new byte[]{1}));
      assertTrue(channel.writeInbound(post));
      ByteBuf inbound = channel.readInbound();
      assertNotNull(inbound);
      inbound.release();

      // Server-initiated / async packet consumes the only response slot.
      channel.writeOutbound(Unpooled.wrappedBuffer(new byte[]{0xA}));
      FullHttpResponse asyncResponse = awaitOutboundHttpResponse(channel);
      assertEquals(1, asyncResponse.content().readableBytes());
      assertEquals(0xA, asyncResponse.content().getByte(asyncResponse.content().readerIndex()));
      asyncResponse.release();

      // Request reply has no remaining slot and must not flush yet.
      channel.writeOutbound(Unpooled.wrappedBuffer(new byte[]{0xB}));
      assertFalse(Wait.waitFor(() -> {
         channel.runPendingTasks();
         return channel.outboundMessages().peek() != null;
      }, 200, 20));

      // Idle GET polls for pending server data when the response queue is empty.
      FullHttpRequest get = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
      channel.writeInbound(get);
      assertNull(channel.readInbound());
      assertEquals(0, get.refCnt());

      FullHttpResponse replyResponse = awaitOutboundHttpResponse(channel);
      assertEquals(1, replyResponse.content().readableBytes());
      assertEquals(0xB, replyResponse.content().getByte(replyResponse.content().readerIndex()));
      replyResponse.release();

      channel.finishAndReleaseAll();
   }

   @Test
   public void testGetDoesNotEnqueueExtraSlotWhenResponsesAlreadyQueued() throws Exception {
      EmbeddedChannel channel = new EmbeddedChannel();
      channel.pipeline().addLast(HTTP_HANDLER, new HttpAcceptorHandler(spy, 1000, channel));

      FullHttpRequest post = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/",
         Unpooled.wrappedBuffer(new byte[]{1}));
      assertTrue(channel.writeInbound(post));
      ByteBuf inbound = channel.readInbound();
      assertNotNull(inbound);
      inbound.release();

      // Response slot already exists from the POST; GET must not add another.
      FullHttpRequest get = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
      channel.writeInbound(get);
      assertNull(channel.readInbound());
      assertEquals(0, get.refCnt());

      channel.writeOutbound(Unpooled.wrappedBuffer(new byte[]{0xA}));
      FullHttpResponse first = awaitOutboundHttpResponse(channel);
      assertEquals(0xA, first.content().getByte(first.content().readerIndex()));
      first.release();

      // Only one slot was available; a second write stays blocked without another request.
      channel.writeOutbound(Unpooled.wrappedBuffer(new byte[]{0xB}));
      assertFalse(Wait.waitFor(() -> {
         channel.runPendingTasks();
         return channel.outboundMessages().peek() != null;
      }, 200, 20));

      FullHttpRequest getForReply = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
      channel.writeInbound(getForReply);
      assertEquals(0, getForReply.refCnt());

      FullHttpResponse second = awaitOutboundHttpResponse(channel);
      assertEquals(0xB, second.content().getByte(second.content().readerIndex()));
      second.release();

      channel.finishAndReleaseAll();
   }

   private static FullHttpResponse awaitOutboundHttpResponse(EmbeddedChannel channel) throws Exception {
      Wait.assertTrue(() -> {
         channel.runPendingTasks();
         return channel.outboundMessages().peek() != null;
      }, 5000, 10);
      Object outbound = channel.readOutbound();
      assertNotNull(outbound);
      assertTrue(outbound instanceof FullHttpResponse, "unexpected outbound: " + outbound);
      return (FullHttpResponse) outbound;
   }
}
