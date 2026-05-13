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

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import org.apache.activemq.artemis.utils.StringUtil;

import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;
import static io.netty.handler.codec.http.HttpUtil.setContentLength;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

   private HttpRequest httpRequest;
   private WebSocketServerHandshaker handshaker;
   private List<String> supportedProtocols;
   private int maxFramePayloadLength;
   private boolean allowExtensions;
   private WebSocketFrameEncoderType encoderType;

   public WebSocketServerHandler(List<String> supportedProtocols, int maxFramePayloadLength, WebSocketFrameEncoderType encoderType, boolean allowExtensions) {
      this.supportedProtocols = supportedProtocols;
      this.maxFramePayloadLength = maxFramePayloadLength;
      this.encoderType = encoderType;
      this.allowExtensions = allowExtensions;
   }

   @Override
   public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof FullHttpRequest request) {
         handleHttpRequest(ctx, request);
      } else if (msg instanceof WebSocketFrame frame) {
         boolean handle = handleWebSocketFrame(ctx, frame);
         if (handle) {
            ctx.fireChannelRead(frame.content().retain());
         }
      }
   }

   private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
      // Allow only GET methods.
      if (req.method() != GET) {
         sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
         return;
      }

      // Handshake
      String supportedProtocolsCSV = StringUtil.joinStringList(supportedProtocols, ",");
      WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(this.getWebSocketLocation(req), supportedProtocolsCSV, allowExtensions, maxFramePayloadLength);
      this.httpRequest = req;
      this.handshaker = wsFactory.newHandshaker(req);
      if (this.handshaker == null) {
         WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
      } else {
         ChannelFuture handshake = this.handshaker.handshake(ctx.channel(), req);
         handshake.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
               // we need to insert an encoder that takes the underlying ChannelBuffer of a StompFrame.toActiveMQBuffer and
               // wrap it in a web socket frame before letting the wsencoder send it on the wire
               WebSocketFrameEncoder encoder = new WebSocketFrameEncoder(maxFramePayloadLength, encoderType);
               future.channel().pipeline().addAfter("wsencoder", "websocket-frame-encoder", encoder);
            } else {
               // Handshake failed, fire an exceptionCaught event
               future.channel().pipeline().fireExceptionCaught(future.cause());
            }
         });
      }
   }

   private boolean handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {

      // Check for closing frame
      if (frame instanceof CloseWebSocketFrame socketFrame) {
         this.handshaker.close(ctx.channel(), socketFrame.retain());
         return false;
      } else if (frame instanceof PingWebSocketFrame) {
         ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
         return false;
      } else if (frame instanceof PongWebSocketFrame) {
         return false;
      } else if (!(frame instanceof TextWebSocketFrame) && !(frame instanceof BinaryWebSocketFrame) && !(frame instanceof ContinuationWebSocketFrame)) {
         throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass().getName()));
      }
      return true;
   }

   private void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req, FullHttpResponse res) {
      // Generate an error page if response status code is not OK (200).
      if (res.status().code() != 200) {
         res.content().writeBytes(res.status().toString().getBytes(StandardCharsets.UTF_8));
         setContentLength(res, res.content().readableBytes());
      }

      // Send the response and close the connection if necessary.
      ChannelFuture f = ctx.writeAndFlush(res);
      if (!isKeepAlive(req) || res.status().code() != 200) {
         f.addListener(ChannelFutureListener.CLOSE);
      }
   }

   @Override
   public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      cause.printStackTrace();
      ctx.close();
   }

   private String getWebSocketLocation(HttpRequest req) {
      return "ws://" + req.headers().get(HttpHeaderNames.HOST);
   }

   public HttpRequest getHttpRequest() {
      return this.httpRequest;
   }
}
