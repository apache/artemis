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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;

public class DelegatingEventLoopGroup implements EventLoopGroup {

   private final EventLoopGroup delegate;

   public DelegatingEventLoopGroup(EventLoopGroup eventLoopGroup) {
      this.delegate = eventLoopGroup;
   }

   @Override
   public EventLoop next() {
      return delegate.next();
   }

   @Override
   public ChannelFuture register(Channel channel) {
      return delegate.register(channel);
   }

   @Override
   public ChannelFuture register(ChannelPromise channelPromise) {
      return delegate.register(channelPromise);
   }

   @Override
   @Deprecated
   public ChannelFuture register(Channel channel, ChannelPromise channelPromise) {
      return delegate.register(channel, channelPromise);
   }

   @Override
   public boolean isShuttingDown() {
      return delegate.isShuttingDown();
   }

   @Override
   public Future<?> shutdownGracefully() {
      return delegate.shutdownGracefully();
   }

   @Override
   public Future<?> shutdownGracefully(long l, long l1, TimeUnit timeUnit) {
      return delegate.shutdownGracefully(l, l1, timeUnit);
   }

   @Override
   public Future<?> terminationFuture() {
      return delegate.terminationFuture();
   }

   @Override
   @Deprecated
   public void shutdown() {
      delegate.shutdown();
   }

   @Override
   @Deprecated
   public List<Runnable> shutdownNow() {
      return delegate.shutdownNow();
   }

   @Override
   public Iterator<EventExecutor> iterator() {
      return delegate.iterator();
   }

   @Override
   public Future<?> submit(Runnable runnable) {
      return delegate.submit(runnable);
   }

   @Override
   public <T> Future<T> submit(Runnable runnable, T t) {
      return delegate.submit(runnable, t);
   }

   @Override
   public <T> Future<T> submit(Callable<T> callable) {
      return delegate.submit(callable);
   }

   @Override
   public io.netty.util.concurrent.ScheduledFuture<?> schedule(Runnable runnable, long l, TimeUnit timeUnit) {
      return delegate.schedule(runnable, l, timeUnit);
   }

   @Override
   public <V> io.netty.util.concurrent.ScheduledFuture<V> schedule(Callable<V> callable, long l, TimeUnit timeUnit) {
      return delegate.schedule(callable, l, timeUnit);
   }

   @Override
   public io.netty.util.concurrent.ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable,
                                                                          long l,
                                                                          long l1,
                                                                          TimeUnit timeUnit) {
      return delegate.scheduleAtFixedRate(runnable, l, l1, timeUnit);
   }

   @Override
   public io.netty.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(Runnable runnable,
                                                                             long l,
                                                                             long l1,
                                                                             TimeUnit timeUnit) {
      return delegate.scheduleWithFixedDelay(runnable, l, l1, timeUnit);
   }

   @Override
   public boolean isShutdown() {
      return delegate.isShutdown();
   }

   @Override
   public boolean isTerminated() {
      return delegate.isTerminated();
   }

   @Override
   public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return delegate.awaitTermination(timeout, unit);
   }

   @Override
   public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
      return delegate.invokeAll(tasks);
   }

   @Override
   public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                                             long timeout,
                                                             TimeUnit unit) throws InterruptedException {
      return delegate.invokeAll(tasks, timeout, unit);
   }

   @Override
   public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
      return delegate.invokeAny(tasks);
   }

   @Override
   public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                          long timeout,
                          TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      return delegate.invokeAny(tasks, timeout, unit);
   }

   @Override
   public void execute(Runnable command) {
      delegate.execute(command);
   }

   @Override
   public void forEach(Consumer<? super EventExecutor> action) {
      delegate.forEach(action);
   }

   @Override
   public Spliterator<EventExecutor> spliterator() {
      return delegate.spliterator();
   }

}
