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
package org.apache.activemq.artemis.core.server.reload;

import java.io.File;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class ReloadManagerImpl extends ActiveMQScheduledComponent implements ReloadManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private volatile Runnable tick;

   private final Map<URL, ReloadRegistry> registry = new ConcurrentHashMap<>();

   public ReloadManagerImpl(ScheduledExecutorService scheduledExecutorService, Executor executor, long checkPeriod) {
      super(scheduledExecutorService, executor, checkPeriod, TimeUnit.MILLISECONDS, false);
   }

   @Override
   public void run() {
      tick();
   }

   @Override
   public synchronized void setTick(Runnable tick) {
      this.tick = tick;
   }

   @Override
   public synchronized void removeCallbacks(URL uri) {
      final ReloadRegistry uriRegistry = registry.remove(uri);

      if (uriRegistry != null) {
         uriRegistry.clear();
      }
   }

   @Override
   public synchronized void addCallback(URL uri, ReloadCallback callback) {
      if (!isStarted()) {
         start();
      }
      ReloadRegistry uriRegistry = getRegistry(uri);
      uriRegistry.add(callback);
   }

   private synchronized void tick() {
      for (ReloadRegistry item : registry.values()) {
         item.check();
      }

      if (tick != null) {
         tick.run();
         tick = null;
      }
   }

   private ReloadRegistry getRegistry(URL uri) {
      ReloadRegistry uriRegistry = registry.get(uri);
      if (uriRegistry == null) {
         uriRegistry = new ReloadRegistry(uri);
         registry.put(uri, uriRegistry);
      }

      return uriRegistry;
   }

   class ReloadRegistry {

      private File file;
      private final URL uri;
      private long lastModified;

      private final List<ReloadCallback> callbacks = new LinkedList<>();

      ReloadRegistry(URL uri) {
         try {
            file = new File(uri.toURI()); // artemis-features will have this as "file:etc/artemis.xml"
                                          // so, we need to make sure we catch the exception and try
                                          // a simple path as it will be a relative path
         } catch (Exception e) {
            logger.debug(e.getMessage(), e);
            file = new File(uri.getPath());
         }

         if (!file.exists()) {
            ActiveMQServerLogger.LOGGER.fileDoesNotExist(file.toString());
         }

         this.lastModified = file.lastModified();
         this.uri = uri;
      }

      public void check() {

         long fileModified = file.lastModified();

         if (logger.isDebugEnabled()) {
            logger.debug("Validating lastModified {} modified = {} on {}", lastModified, fileModified, uri);
         }

         if ((lastModified > 0 && fileModified > lastModified) ||
            // newly created file, first valid modified time
            (fileModified > 0 && lastModified == 0)) {

            for (ReloadCallback callback : callbacks) {
               try {
                  callback.reload(uri);
               } catch (Throwable e) {
                  ActiveMQServerLogger.LOGGER.configurationReloadFailed(e);
               }
            }
         }

         this.lastModified = fileModified;
      }

      public List<ReloadCallback> getCallbacks() {
         return callbacks;
      }

      public void add(ReloadCallback callback) {
         callbacks.add(callback);
      }

      public void clear() {
         callbacks.clear();
      }
   }
}
