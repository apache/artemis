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
package org.apache.activemq.artemis.core.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;
import org.apache.activemq.artemis.core.remoting.impl.netty.ConnectionCreator;
import org.apache.activemq.artemis.core.remoting.impl.netty.HAProxyMessageEnforcer;
import org.apache.activemq.artemis.core.remoting.impl.netty.HttpAcceptorHandler;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptor;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettySNIHostnameHandler;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyServerConnection;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.protocol.websocket.WebSocketFrameEncoderType;
import org.apache.activemq.artemis.core.server.protocol.websocket.WebSocketServerHandler;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.utils.ConfigurationHelper;
import org.apache.activemq.artemis.utils.ProxyProtocolUtil;
import org.apache.activemq.artemis.utils.SocketAddressUtil;

import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class ProtocolHandler {

   private Map<String, ProtocolManager> protocolMap;

   private NettyAcceptor nettyAcceptor;

   private ScheduledExecutorService scheduledThreadPool;

   private final List<String> websocketSubprotocolIds;

   public ProtocolHandler(Map<String, ProtocolManager> protocolMap,
                          NettyAcceptor nettyAcceptor,
                          ScheduledExecutorService scheduledThreadPool) {
      this.protocolMap = protocolMap;
      this.nettyAcceptor = nettyAcceptor;
      this.scheduledThreadPool = scheduledThreadPool;

      websocketSubprotocolIds = new ArrayList<>();
      for (ProtocolManager pm : protocolMap.values()) {
         if (pm.websocketSubprotocolIdentifiers() != null) {
            websocketSubprotocolIds.addAll(pm.websocketSubprotocolIdentifiers());
         }
      }
   }

   public Map<String, ProtocolManager> getProtocolMap() {
      return protocolMap;
   }

   public ChannelHandler getProtocolDecoder() {
      return new ProtocolDecoder(true, false);
   }

   public ProtocolManager getProtocol(String name) {
      return this.protocolMap.get(name);
   }

   class ProtocolDecoder extends ByteToMessageDecoder {

      private static final String HTTP_HANDLER = "http-handler";

      private final boolean http;

      private final boolean httpEnabled;

      private ScheduledFuture<?> timeoutFuture;

      private int handshakeTimeout;

      private NettySNIHostnameHandler nettySNIHostnameHandler;

      ProtocolDecoder(boolean http, boolean httpEnabled) {
         this.http = http;
         this.httpEnabled = httpEnabled;
         this.handshakeTimeout = ConfigurationHelper.getIntProperty(TransportConstants.HANDSHAKE_TIMEOUT, TransportConstants.DEFAULT_HANDSHAKE_TIMEOUT, nettyAcceptor.getConfiguration());
      }

      @Override
      public void channelActive(ChannelHandlerContext ctx) throws Exception {
         nettySNIHostnameHandler = ctx.pipeline().get(NettySNIHostnameHandler.class);

         if (handshakeTimeout > 0) {
            timeoutFuture = scheduledThreadPool.schedule(() -> {
               ActiveMQServerLogger.LOGGER.handshakeTimeout(handshakeTimeout, nettyAcceptor.getName(), ProxyProtocolUtil.getRemoteAddress(ctx.channel()));
               ctx.channel().close();
            }, handshakeTimeout, TimeUnit.SECONDS);
         }
      }

      @Override
      public void channelInactive(ChannelHandlerContext ctx) throws Exception {
         super.channelInactive(ctx);
         if (handshakeTimeout > 0 && timeoutFuture != null) {
            timeoutFuture.cancel(true);
            timeoutFuture = null;
         }
      }

      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
         if (msg instanceof FullHttpRequest httpRequest) {
            HttpHeaders headers = httpRequest.headers();
            String upgrade = headers.get("upgrade");

            if (upgrade != null && upgrade.equalsIgnoreCase("websocket")) {
               int stompMaxFramePayloadLength = ConfigurationHelper.getIntProperty(TransportConstants.STOMP_MAX_FRAME_PAYLOAD_LENGTH, -1, nettyAcceptor.getConfiguration());
               if (stompMaxFramePayloadLength != -1) {
                  ActiveMQServerLogger.LOGGER.deprecatedConfigurationOption(TransportConstants.STOMP_MAX_FRAME_PAYLOAD_LENGTH, TransportConstants.WEB_SOCKET_MAX_FRAME_PAYLOAD_LENGTH);
               }
               stompMaxFramePayloadLength = stompMaxFramePayloadLength != -1 ? stompMaxFramePayloadLength : TransportConstants.DEFAULT_WEB_SOCKET_MAX_FRAME_PAYLOAD_LENGTH;

               int webSocketMaxFramePayloadLength = ConfigurationHelper.getIntProperty(TransportConstants.WEB_SOCKET_MAX_FRAME_PAYLOAD_LENGTH, -1, nettyAcceptor.getConfiguration());
               webSocketMaxFramePayloadLength = webSocketMaxFramePayloadLength != -1 ? webSocketMaxFramePayloadLength : stompMaxFramePayloadLength;

               final boolean enableCompression = ConfigurationHelper.getBooleanProperty(
                  TransportConstants.WEB_SOCKET_COMPRESSION_SUPPORTED, TransportConstants.DEFAULT_WEB_SOCKET_COMPRESSION_SUPPORTED, nettyAcceptor.getConfiguration());
               final String encoderConfigType = ConfigurationHelper.getStringProperty(
                  TransportConstants.WEB_SOCKET_ENCODER_TYPE, TransportConstants.DEFAULT_WEB_SOCKET_ENCODER_TYPE, nettyAcceptor.getConfiguration());

               if (enableCompression) {
                  ctx.pipeline().addLast(new WebSocketServerCompressionHandler());
               }

               ctx.pipeline().addLast("websocket-handler", new WebSocketServerHandler(websocketSubprotocolIds, webSocketMaxFramePayloadLength, WebSocketFrameEncoderType.valueOfType(encoderConfigType), enableCompression));
               ctx.pipeline().addLast(new ProtocolDecoder(false, false));
               ctx.pipeline().remove(this);
               ctx.pipeline().remove(HTTP_HANDLER);
               ctx.fireChannelRead(msg);
            } else if (upgrade != null && upgrade.equalsIgnoreCase(NettyConnector.ACTIVEMQ_REMOTING)) {
               // Send the response and close the connection if necessary.
               ctx.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN)).addListener(ChannelFutureListener.CLOSE);
            }
         } else {
            super.channelRead(ctx, msg);
         }
      }

      @Override
      protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
         if (ctx.isRemoved()) {
            return;
         }

         // Will use the first N bytes to detect a protocol depending on the protocol.
         if (in.readableBytes() < 8) {
            return;
         }

         if (handshakeTimeout > 0 && timeoutFuture != null) {
            timeoutFuture.cancel(true);
            timeoutFuture = null;
         }

         if (HAProxyMessageEnforcer.isProxyProtocol(in)) {
            ActiveMQServerLogger.LOGGER.proxyProtocolViolation(SocketAddressUtil.toString(ctx.channel().remoteAddress()), nettyAcceptor.getName(), false, "used");
            ctx.channel().close();
         }

         if (http && isHttp(in)) {
            switchToHttp(ctx);
            return;
         }
         String protocolToUse = null;
         Set<String> protocolSet = protocolMap.keySet();
         if (!protocolSet.isEmpty()) {
            // Use getBytes(...) as this works with direct and heap buffers.
            byte[] bytes = new byte[8];
            in.getBytes(0, bytes);

            for (String protocol : protocolSet) {
               ProtocolManager protocolManager = protocolMap.get(protocol);
               if (protocolManager.isProtocol(bytes)) {
                  protocolToUse = protocol;
                  break;
               }
            }
         }

         //if we get here we assume we use the core protocol as we match nothing else
         if (protocolToUse == null) {
            for (Map.Entry<String, ProtocolManager> entry : protocolMap.entrySet()) {
               if (entry.getValue().acceptsNoHandshake()) {
                  protocolToUse = entry.getKey();
                  break;
               }
            }
            if (protocolToUse == null) {
               protocolToUse = ActiveMQClient.DEFAULT_CORE_PROTOCOL;
            }
         }

         ProtocolManager protocolManagerToUse = protocolMap.get(protocolToUse);
         if (protocolManagerToUse == null) {
            ActiveMQServerLogger.LOGGER.failedToFindProtocolManager(ctx.channel() == null ? null : ProxyProtocolUtil.getRemoteAddress(ctx.channel()), ctx.channel() == null ? null : SocketAddressUtil.toString(ctx.channel().localAddress()), protocolToUse, protocolMap.keySet().toString());
            return;
         }
         ConnectionCreator channelHandler = nettyAcceptor.createConnectionCreator();
         ChannelPipeline pipeline = ctx.pipeline();
         protocolManagerToUse.addChannelHandlers(pipeline);
         pipeline.addLast("handler", channelHandler);
         NettyServerConnection connection = channelHandler.createConnection(ctx, protocolToUse, httpEnabled);
         connection.setSNIHostname(nettySNIHostnameHandler != null ? nettySNIHostnameHandler.getHostname() : null);
         protocolManagerToUse.handshake(connection, new ChannelBufferWrapper(in));
         pipeline.remove(this);

         ctx.flush();
      }

      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
         try {
            ActiveMQServerLogger.LOGGER.failureDuringProtocolHandshake(SocketAddressUtil.toString(ctx.channel().localAddress()), ProxyProtocolUtil.getRemoteAddress(ctx.channel()), cause);
         } finally {
            ctx.close();
         }
      }

      private boolean isHttp(ByteBuf in) {
         final int magic1 = in.getUnsignedByte(in.readerIndex());
         final int magic2 = in.getUnsignedByte(in.readerIndex() + 1);
         return magic1 == 'G' && magic2 == 'E' || // GET
            magic1 == 'P' && magic2 == 'O' || // POST
            magic1 == 'P' && magic2 == 'U' || // PUT
            magic1 == 'H' && magic2 == 'E' || // HEAD
            magic1 == 'O' && magic2 == 'P' || // OPTIONS
            magic1 == 'P' && magic2 == 'A' || // PATCH
            magic1 == 'D' && magic2 == 'E' || // DELETE
            magic1 == 'T' && magic2 == 'R'; // TRACE
         //magic1 == 'C' && magic2 == 'O'; // CONNECT
      }

      private void switchToHttp(ChannelHandlerContext ctx) {
         ChannelPipeline p = ctx.pipeline();
         p.addLast("http-decoder", new HttpRequestDecoder());
         p.addLast("http-aggregator", new HttpObjectAggregator(Integer.MAX_VALUE));
         p.addLast("http-encoder", new HttpResponseEncoder());
         HttpAcceptorHandler httpHandler = new HttpAcceptorHandler(ctx.channel());
         p.addLast(HTTP_HANDLER, httpHandler);
         p.addLast(new ProtocolDecoder(false, true));
         p.remove(this);
      }
   }
}
