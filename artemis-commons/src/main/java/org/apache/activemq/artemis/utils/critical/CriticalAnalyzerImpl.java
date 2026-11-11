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
package org.apache.activemq.artemis.utils.critical;

import java.security.PrivilegedAction;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class CriticalAnalyzerImpl implements CriticalAnalyzer {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private volatile long timeoutNanoSeconds;

   // one minute by default.. the server will change it for sure
   private volatile long checkTimeNanoSeconds = TimeUnit.SECONDS.toNanos(60);

   private final ActiveMQScheduledComponent scheduledComponent;

   public CriticalAnalyzerImpl() {
      // this will make the scheduled component to start its own pool

      /*
       * Important: The scheduled component should have its own thread pool...
       *  otherwise in case of a deadlock, or a starvation of the server the analyzer won't pick up any
       *  issues and won't be able to shutdown the server or halt the VM
       */
      this.scheduledComponent = new ActiveMQScheduledComponent(null, null, checkTimeNanoSeconds, TimeUnit.NANOSECONDS, false) {

         @Override
         public void run() {
            logger.trace("Checking critical analyzer");
            check();
         }

         @Override
         protected ActiveMQThreadFactory getThreadFactory() {
            return new ActiveMQThreadFactory("critical-analyzer", true, getThisClassLoader());
         }

         private ClassLoader getThisClassLoader() {
            return SecurityManagerShim.doPrivileged((PrivilegedAction<ClassLoader>) () -> CriticalAnalyzerImpl.this.getClass().getClassLoader());

         }

      };

   }

   @Override
   public void clear() {
      actions.clear();
      components.clear();
   }

   private List<CriticalAction> actions = new CopyOnWriteArrayList<>();

   private final Set<CriticalComponent> components = ConcurrentHashMap.newKeySet();

   @Override
   public int getNumberOfComponents() {
      return components.size();
   }

   @Override
   public boolean isMeasuring() {
      return true;
   }

   @Override
   public void add(CriticalComponent component) {
      components.add(component);
   }

   @Override
   public void remove(CriticalComponent component) {
      components.remove(component);
   }

   @Override
   public CriticalAnalyzer setCheckTime(long timeout, TimeUnit unit) {
      this.checkTimeNanoSeconds = unit.toNanos(timeout);
      this.scheduledComponent.setPeriod(timeout, unit);
      return this;
   }

   @Override
   public long getCheckTimeNanoSeconds() {
      if (checkTimeNanoSeconds == 0) {
         checkTimeNanoSeconds = getTimeout(TimeUnit.NANOSECONDS) / 2;
      }
      return checkTimeNanoSeconds;
   }

   @Override
   public CriticalAnalyzer setTimeout(long timeout, TimeUnit unit) {
      if (checkTimeNanoSeconds <= 0) {
         this.setCheckTime(timeout / 2, unit);
      }
      this.timeoutNanoSeconds = unit.toNanos(timeout);
      return this;
   }

   @Override
   public long getTimeout(TimeUnit unit) {
      if (timeoutNanoSeconds == 0) {
         timeoutNanoSeconds = TimeUnit.MINUTES.toNanos(2);
      }
      return unit.convert(timeoutNanoSeconds, TimeUnit.NANOSECONDS);
   }

   @Override
   public long getTimeoutNanoSeconds() {
      return timeoutNanoSeconds;
   }

   @Override
   public CriticalAnalyzer addAction(CriticalAction action) {
      this.actions.add(action);
      return this;
   }

   @Override
   public void check() {
      boolean retry = true;
      while (retry) {
         try {
            for (CriticalComponent component : components) {

               if (component.checkExpiration(timeoutNanoSeconds, true)) {
                  fireActions(component);
                  // no need to keep running if there's already a component failed
                  return;
               }
            }
            retry = false; // got to the end of the list, no need to retry
         } catch (ConcurrentModificationException dontCare) {
            // lets retry on the loop
         }
      }
   }

   protected void fireActions(CriticalComponent component) {
      for (CriticalAction action : actions) {
         try {
            action.run(component);
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
         }
      }
   }

   @Override
   public void start() {
      scheduledComponent.start();
   }

   @Override
   public void stop() {
      scheduledComponent.stop();
   }

   @Override
   public boolean isStarted() {
      return scheduledComponent.isStarted();
   }
}
