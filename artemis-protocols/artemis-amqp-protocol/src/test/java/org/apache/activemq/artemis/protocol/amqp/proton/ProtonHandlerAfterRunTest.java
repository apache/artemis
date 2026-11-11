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
package org.apache.activemq.artemis.protocol.amqp.proton;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ProgressivePromise;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import org.apache.activemq.artemis.protocol.amqp.proton.handler.ProtonHandler;
import org.junit.jupiter.api.Test;

public class ProtonHandlerAfterRunTest {

   private static class FakeEventLoop implements EventLoop {
      @Override
      public EventLoopGroup parent() {
         return null;
      }

      @Override
      public EventLoop next() {
         return null;
      }

      @Override
      public ChannelFuture register(Channel channel) {
         return null;
      }

      @Override
      public ChannelFuture register(ChannelPromise promise) {
         return null;
      }

      @Override
      public ChannelFuture register(Channel channel, ChannelPromise promise) {
         return null;
      }

      @Override
      public boolean inEventLoop() {
         return true;
      }

      @Override
      public boolean inEventLoop(Thread thread) {
         return true;
      }

      @Override
      public <V> Promise<V> newPromise() {
         return null;
      }

      @Override
      public <V> ProgressivePromise<V> newProgressivePromise() {
         return null;
      }

      @Override
      public <V> Future<V> newSucceededFuture(V result) {
         return null;
      }

      @Override
      public <V> Future<V> newFailedFuture(Throwable cause) {
         return null;
      }

      @Override
      public boolean isShuttingDown() {
         return false;
      }

      @Override
      public Future<?> shutdownGracefully() {
         return null;
      }

      @Override
      public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
         return null;
      }

      @Override
      public Future<?> terminationFuture() {
         return null;
      }

      @Override
      public void shutdown() {

      }

      @Override
      public List<Runnable> shutdownNow() {
         return null;
      }

      @Override
      public Iterator<EventExecutor> iterator() {
         return null;
      }

      @Override
      public Future<?> submit(Runnable task) {
         return null;
      }

      @Override
      public <T> Future<T> submit(Runnable task, T result) {
         return null;
      }

      @Override
      public <T> Future<T> submit(Callable<T> task) {
         return null;
      }

      @Override
      public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
         return null;
      }

      @Override
      public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
         return null;
      }

      @Override
      public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
         return null;
      }

      @Override
      public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
         return null;
      }

      @Override
      public boolean isShutdown() {
         return false;
      }

      @Override
      public boolean isTerminated() {
         return false;
      }

      @Override
      public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
         return false;
      }

      @Override
      public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
         return null;
      }

      @Override
      public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
         return null;
      }

      @Override
      public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
         return null;
      }

      @Override
      public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
         return null;
      }

      @Override
      public void execute(Runnable command) {

      }
   }

   @Test
   public void testAfterRun() {
      ProtonHandler handler = new ProtonHandler(new FakeEventLoop(), null, true);

      AtomicInteger value = new AtomicInteger(0);
      AtomicInteger value2 = new AtomicInteger(0);
      Runnable run = value::incrementAndGet;
      Runnable run2 = value2::incrementAndGet;

      handler.afterFlush(run);
      handler.afterFlush(run);
      handler.afterFlush(run);

      handler.runAfterFlush();
      assertEquals(1, value.get());
      assertEquals(0, value2.get());

      handler.runAfterFlush();
      assertEquals(1, value.get());
      assertEquals(0, value2.get());


      handler.afterFlush(run);
      handler.runAfterFlush();
      assertEquals(2, value.get());
      assertEquals(0, value2.get());

      handler.afterFlush(run);
      handler.afterFlush(run);
      handler.afterFlush(run);
      handler.afterFlush(run2);
      handler.afterFlush(run2);
      handler.afterFlush(run2);
      handler.afterFlush(run2);
      handler.afterFlush(run);
      handler.afterFlush(run);
      handler.afterFlush(run);

      handler.runAfterFlush();
      assertEquals(3, value.get());
      assertEquals(1, value2.get());

      handler.runAfterFlush();
      assertEquals(3, value.get());
      assertEquals(1, value2.get());

      handler.afterFlush(run2);
      handler.runAfterFlush();
      assertEquals(3, value.get());
      assertEquals(2, value2.get());

      handler.runAfterFlush();
      assertEquals(3, value.get());
      assertEquals(2, value2.get());
   }

   @Test
   public void testRecursiveLoop() {
      ProtonHandler handler = new ProtonHandler(new FakeEventLoop(), null, true);

      Runnable run = handler::runAfterFlush;
      Runnable run2 = handler::runAfterFlush;

      handler.afterFlush(run);
      // make sure the code will not execute it recursevly if this is called in recursion
      handler.runAfterFlush();

      handler.afterFlush(run);
      handler.afterFlush(run2);
      handler.runAfterFlush();
   }
}
