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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

/**
 * A simple Runnable to allow {@link HttpAcceptorHandler}s to be called intermittently.
 */
public class HttpKeepAliveRunnable implements Runnable {

   private final List<HttpAcceptorHandler> handlers = new ArrayList<>();

   private boolean closed = false;

   private Future<?> future;

   @Override
   public synchronized void run() {
      if (closed) {
         return;
      }

      long time = System.currentTimeMillis();
      for (HttpAcceptorHandler handler : handlers) {
         handler.keepAlive(time);
      }
   }

   public List<HttpAcceptorHandler> getHandlers() {
      return Collections.unmodifiableList(handlers);
   }

   public synchronized void registerKeepAliveHandler(final HttpAcceptorHandler httpAcceptorHandler) {
      handlers.add(httpAcceptorHandler);
   }

   public synchronized void unregisterKeepAliveHandler(final HttpAcceptorHandler httpAcceptorHandler) {
      handlers.remove(httpAcceptorHandler);
      httpAcceptorHandler.shutdown();
   }

   public void close() {
      for (HttpAcceptorHandler handler : handlers) {
         handler.shutdown();
      }
      if (future != null) {
         future.cancel(true);
      }

      closed = true;
   }

   public synchronized void setFuture(final Future<?> future) {
      this.future = future;
   }
}
