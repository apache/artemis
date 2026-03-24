/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.utils.critical;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.apache.activemq.artemis.utils.ArtemisCloseable;
import org.jboss.logging.Logger;

public class CriticalMeasure {

   public static boolean isDummy(ArtemisCloseable closeable) {
      return closeable == dummyCloseable;
   }

   private static final Logger logger = Logger.getLogger(CriticalMeasure.class);

   // this is used on enterCritical, if the logger is in trace mode
   private volatile Exception traceEnter;
   static final AtomicIntegerFieldUpdater<CriticalMeasure> CURRENT_MEASURING = AtomicIntegerFieldUpdater.newUpdater(CriticalMeasure.class, "measuring");

   private final CriticalCloseable autoCloseable = new CriticalCloseable() {
      ArtemisCloseable beforeClose;

      @Override
      public void beforeClose(ArtemisCloseable closeable) {
         beforeClose = closeable;
      }

      @Override
      public void close() {
         try {
            if (beforeClose != null) {
               beforeClose.close();
               beforeClose = null;
            }
         } finally {
            leaveCritical();
            CURRENT_MEASURING.set(CriticalMeasure.this, 0);
         }
      }
   };

   protected static final CriticalCloseable dummyCloseable = new CriticalCloseable() {
      @Override
      public void beforeClose(ArtemisCloseable runnable) {
         throw new IllegalStateException("The dummy closeable does not support beforeClose. Check before CriticalMeasure.isDummy(closeable) before you call beforeClose(runnable)");
      }

      @Override
      public void close() {
      }
   };

   // this is working like a boolean, although using AtomicIntegerFieldUpdater instead
   protected volatile int measuring;
   protected volatile long timeEnter;

   private final int id;
   private final CriticalComponent component;

   public CriticalMeasure(CriticalComponent component, int id) {
      this.id = id;
      this.component = component;
      this.timeEnter = 0;
   }

   public CriticalCloseable measure() {
      // I could have chosen to simply store the time on this value, however I would be calling nanoTime a lot of times
      // to just waste the value
      // So, I keep a measuring atomic to protect the thread sampling,
      // and I will still do the set using a single thread.
      if (CURRENT_MEASURING.compareAndSet(this, 0, 1)) {
         enterCritical();
         return autoCloseable;
      } else {
         return dummyCloseable;
      }
   }

   protected void enterCritical() {
      timeEnter = System.nanoTime();

      if (logger.isTraceEnabled()) {
         traceEnter = new Exception("entered");
      }
   }

   protected void leaveCritical() {
      if (logger.isTraceEnabled()) {

         CriticalAnalyzer analyzer = component != null ? component.getCriticalAnalyzer() : null;
         if (analyzer != null) {
            long nanoTimeout = analyzer.getTimeoutNanoSeconds();
            if (checkExpiration(nanoTimeout, false)) {
               logger.trace("Path " + id + " on component " + getComponentName() + " is taking too long, leaving at", new Exception("left"));
               logger.trace("Path " + id + " on component " + getComponentName() + " is taking too long, entered at", traceEnter);
            }
         }
         traceEnter = null;
      }
      timeEnter = 0L;
   }

   protected String getComponentName() {
      if (component == null) {
         return "null";
      } else {
         return component.getClass().getName();
      }
   }

   public boolean checkExpiration(long timeout, boolean reset) {
      final long thisTimeEnter = this.timeEnter;
      if (thisTimeEnter != 0L) {
         long time = System.nanoTime();
         boolean expired = time - thisTimeEnter > timeout;

         if (expired) {
            Exception lastTraceEnter = this.traceEnter;

            if (lastTraceEnter != null) {
               logger.warn("Component " + getComponentName() + " is expired on path " + id, lastTraceEnter);
            } else {
               logger.warn("Component " + getComponentName() + " is expired on path " + id);
            }

            if (reset) {
               this.timeEnter = 0;
            }

         }
         return expired;
      }
      return false;
   }
}