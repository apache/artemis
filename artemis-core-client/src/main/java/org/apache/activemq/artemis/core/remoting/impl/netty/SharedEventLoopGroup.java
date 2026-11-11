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

import java.security.PrivilegedAction;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;

import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryImpl;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;

public class SharedEventLoopGroup extends DelegatingEventLoopGroup {

   private static SharedEventLoopGroup instance;

   private final AtomicReference<ScheduledFuture<?>> shutdown = new AtomicReference<>();
   private final AtomicLong channelFactoryCount = new AtomicLong();
   private final Promise<?> terminationPromise = ImmediateEventExecutor.INSTANCE.newPromise();

   private SharedEventLoopGroup(EventLoopGroup eventLoopGroup) {
      super(eventLoopGroup);
   }

   public static synchronized void forceShutdown() {
      if (instance != null) {
         instance.forEach(executor -> executor.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS));
         instance.channelFactoryCount.set(0);
         instance = null;
      }
   }

   public static synchronized SharedEventLoopGroup getInstance(Function<ThreadFactory, EventLoopGroup> eventLoopGroupSupplier) {
      if (instance != null) {
         ScheduledFuture<?> f = instance.shutdown.getAndSet(null);
         if (f != null) {
            f.cancel(false);
         }
      } else {
         instance = new SharedEventLoopGroup(eventLoopGroupSupplier.apply((ThreadFactory) SecurityManagerShim.doPrivileged((PrivilegedAction) () -> new ActiveMQThreadFactory("client-remoting", true, ClientSessionFactoryImpl.class.getClassLoader()))));
      }
      instance.channelFactoryCount.incrementAndGet();
      return instance;
   }

   @Override
   public Future<?> terminationFuture() {
      return terminationPromise;
   }

   @Override
   public Future<?> shutdownGracefully() {
      return shutdownGracefully(100, 3000, TimeUnit.MILLISECONDS);
   }

   @Override
   public Future<?> shutdownGracefully(final long l, final long l2, final TimeUnit timeUnit) {
      synchronized (SharedEventLoopGroup.class) {
         if (channelFactoryCount.decrementAndGet() == 0) {
            shutdown.compareAndSet(null, next().scheduleAtFixedRate(() -> {
               synchronized (SharedEventLoopGroup.class) {
                  if (shutdown.get() != null) {
                     Future<?> future = SharedEventLoopGroup.super.shutdownGracefully(l, l2, timeUnit);
                     future.addListener((FutureListener<Object>) future1 -> {
                        if (future1.isSuccess()) {
                           terminationPromise.setSuccess(null);
                        } else {
                           terminationPromise.setFailure(future1.cause());
                        }
                     });
                     instance = null;
                  }
               }
            }, 10, 10, TimeUnit.SECONDS));
         }
         return terminationPromise;
      }
   }
}
