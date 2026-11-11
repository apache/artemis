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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;

/**
 * Ensures that every request has a response and also that any uninitiated responses always wait for a response.
 */
public class HttpAcceptorHandler extends ChannelDuplexHandler {

   private final BlockingQueue<ResponseHolder> responses = new LinkedBlockingQueue<>();

   private final BlockingQueue<Runnable> delayedResponses = new LinkedBlockingQueue<>();

   private final ExecutorService executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, delayedResponses);

   private final HttpKeepAliveRunnable httpKeepAliveTask;

   private final long responseTime;

   private Channel channel;

   public HttpAcceptorHandler(final HttpKeepAliveRunnable httpKeepAliveTask, final long responseTime, Channel channel) {
      super();
      this.responseTime = responseTime;
      this.httpKeepAliveTask = httpKeepAliveTask;
      this.channel = channel;
      httpKeepAliveTask.registerKeepAliveHandler(this);
   }

   @Override
   public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
      super.channelInactive(ctx);
      httpKeepAliveTask.unregisterKeepAliveHandler(this);
      channel = null;
   }

   @Override
   public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
      super.handlerRemoved(ctx);
      httpKeepAliveTask.unregisterKeepAliveHandler(this);
   }

   @Override
   public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
      FullHttpRequest request = (FullHttpRequest) msg;
      HttpMethod method = request.method();
      // if we are a post then we send upstream, otherwise we are just being prompted for a response.
      if (method.equals(HttpMethod.POST)) {
         ctx.fireChannelRead(ReferenceCountUtil.retain(((FullHttpRequest) msg).content()));
         // add a new response
         responses.put(new ResponseHolder(System.currentTimeMillis() + responseTime, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));
         ReferenceCountUtil.release(msg);
         return;
      } else if (method.equals(HttpMethod.GET) && !request.headers().contains(HttpHeaderNames.UPGRADE)) {
         // HTTP tunnel poll for a pending response; do not consume protocol Upgrade GETs (e.g. WebSocket).
         if (responses.isEmpty()) {
            responses.put(new ResponseHolder(System.currentTimeMillis() + responseTime,
               new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));
         }
         // Consumed here (not forwarded), so release; unlike POST we keep no content, so no retain.
         ReferenceCountUtil.release(msg);
         return;
      }
      super.channelRead(ctx, msg);
   }

   @Override
   public void write(final ChannelHandlerContext ctx, final Object msg, ChannelPromise promise) throws Exception {
      // we are either a channel buffer, which gets delayed until a response is available, or we are the actual response
      if (msg instanceof ByteBuf buf) {
         executor.execute(new ResponseRunner(buf, promise));
      } else {
         ctx.write(msg, promise);
      }
   }

   public void keepAlive(final long time) {
      // send some responses to catch up thus avoiding any timeout.
      int lateResponses = 0;
      for (ResponseHolder response : responses) {
         if (response.timeReceived < time) {
            lateResponses++;
         } else {
            break;
         }
      }
      for (int i = 0; i < lateResponses; i++) {
         executor.execute(new ResponseRunner());
      }
   }

   /**
    * this is prompted to delivery when a response is available in the response queue.
    */
   final class ResponseRunner implements Runnable {

      private final ByteBuf buffer;

      private final boolean bogusResponse;

      private final ChannelPromise promise;

      ResponseRunner(final ByteBuf buffer, ChannelPromise promise) {
         this.buffer = buffer;
         bogusResponse = false;
         this.promise = promise;
      }

      ResponseRunner() {
         bogusResponse = true;
         buffer = Unpooled.buffer(0);
         promise = channel.newPromise();
      }

      @Override
      public void run() {
         ResponseHolder responseHolder = null;
         do {
            try {
               responseHolder = responses.take();
            } catch (InterruptedException e) {
               if (executor.isShutdown()) {
                  return;
               }
               // otherwise ignore, we'll just try again
            }
         }
         while (responseHolder == null);
         if (!bogusResponse) {
            piggyBackResponses(responseHolder.response.content());
         } else {
            responseHolder.response.content().writeBytes(buffer);
         }
         responseHolder.response.headers().set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(responseHolder.response.content().readableBytes()));
         channel.writeAndFlush(responseHolder.response, promise);

         buffer.release();

      }

      // TODO: This can be optimized a lot
      private void piggyBackResponses(ByteBuf buf) {
         // if we are the last available response then we have to piggy back any remaining responses
         if (responses.isEmpty()) {
            buf.writeBytes(buffer);
            do {
               try {
                  ResponseRunner responseRunner = (ResponseRunner) delayedResponses.poll(0, TimeUnit.MILLISECONDS);
                  if (responseRunner == null) {
                     break;
                  }
                  buf.writeBytes(responseRunner.buffer);
                  responseRunner.buffer.release();
               } catch (InterruptedException e) {
                  break;
               }
            }
            while (responses.isEmpty());
            return;
         }
         buf.writeBytes(buffer);
      }

   }

   public void shutdown() {
      executor.shutdown();
      try {
         executor.awaitTermination(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         // no-op
      } finally {
         executor.shutdownNow();
      }
      responses.clear();
   }

   /**
    * a holder class so we know what time  the request first arrived
    */
   private static final class ResponseHolder {

      final FullHttpResponse response;

      final long timeReceived;

      private ResponseHolder(final long timeReceived, final FullHttpResponse response) {
         this.timeReceived = timeReceived;
         this.response = response;
      }
   }

}
