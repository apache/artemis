/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.transport.netty;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;

/**
 * Transport for communicating over WebSockets
 */
public class NettyWSTransport extends NettyTcpTransport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String AMQP_SUB_PROTOCOL = "amqp";

   /**
    * Create a new transport instance
    *
    * @param remoteLocation the URI that defines the remote resource to connect to.
    * @param options        the transport options used to configure the socket connection.
    */
   public NettyWSTransport(URI remoteLocation, NettyTransportOptions options) {
      this(null, remoteLocation, options);
   }

   /**
    * Create a new transport instance
    *
    * @param listener       the TransportListener that will receive events from this Transport.
    * @param remoteLocation the URI that defines the remote resource to connect to.
    * @param options        the transport options used to configure the socket connection.
    */
   public NettyWSTransport(NettyTransportListener listener, URI remoteLocation, NettyTransportOptions options) {
      super(listener, remoteLocation, options);
   }

   @Override
   public void sendVoidPromise(ByteBuf output) throws IOException {
      writeAndFlush(output, channel.voidPromise(), BinaryWebSocketFrame::new);
   }

   @Override
   public ChannelFuture send(ByteBuf output) throws IOException {
      return writeAndFlush(output, channel.newPromise(), BinaryWebSocketFrame::new);
   }

   @Override
   protected ChannelInboundHandlerAdapter createChannelHandler() {
      return new NettyWebSocketTransportHandler();
   }

   @Override
   protected void addAdditionalHandlers(ChannelPipeline pipeline) {
      pipeline.addLast(new HttpClientCodec());
      pipeline.addLast(new HttpObjectAggregator(8192));
   }

   @Override
   protected void handleConnected(Channel channel) throws Exception {
      logger.trace("Channel has become active, awaiting WebSocket handshake! Channel is {}", channel);
   }

   // ----- Handle connection events -----------------------------------------//

   private class NettyWebSocketTransportHandler extends NettyDefaultHandler<Object> {

      private final WebSocketClientHandshaker handshaker;

      NettyWebSocketTransportHandler() {
         handshaker = WebSocketClientHandshakerFactory.newHandshaker(
            getRemoteLocation(), WebSocketVersion.V13, options.getWsSubProtocol(),
            true, new DefaultHttpHeaders(), getMaxFrameSize());
      }

      @Override
      public void channelActive(ChannelHandlerContext context) throws Exception {
         handshaker.handshake(context.channel());

         super.channelActive(context);
      }

      @Override
      protected void channelRead0(ChannelHandlerContext ctx, Object message) throws Exception {
         logger.trace("New data read: incoming: {}", message);

         Channel ch = ctx.channel();
         if (!handshaker.isHandshakeComplete()) {
            handshaker.finishHandshake(ch, (FullHttpResponse) message);
            logger.trace("WebSocket Client connected! {}", ctx.channel());
            // Now trigger super processing as we are really connected.
            NettyWSTransport.super.handleConnected(ch);
            return;
         }

         // We shouldn't get this since we handle the handshake previously.
         if (message instanceof FullHttpResponse response) {
            throw new IllegalStateException(
               "Unexpected FullHttpResponse (getStatus=" + response.status() + ", content=" + response.content().toString(StandardCharsets.UTF_8) + ')');
         }

         WebSocketFrame frame = (WebSocketFrame) message;
         if (frame instanceof TextWebSocketFrame textFrame) {
            logger.warn("WebSocket Client received message: {}", textFrame.text());
            ctx.fireExceptionCaught(new IOException("Received invalid frame over WebSocket."));
         } else if (frame instanceof BinaryWebSocketFrame binaryFrame) {
            logger.trace("WebSocket Client received data: {} bytes", binaryFrame.content().readableBytes());
            listener.onData(binaryFrame.content());
         } else if (frame instanceof ContinuationWebSocketFrame continuationFrame) {
            logger.trace("WebSocket Client received data continuation: {} bytes", continuationFrame.content().readableBytes());
            listener.onData(continuationFrame.content());
         } else if (frame instanceof PingWebSocketFrame) {
            logger.trace("WebSocket Client received ping, response with pong");
            ch.write(new PongWebSocketFrame(frame.content()));
         } else if (frame instanceof CloseWebSocketFrame) {
            logger.trace("WebSocket Client received closing");
            ch.close();
         }
      }
   }
}
