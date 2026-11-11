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
package org.apache.activemq.artemis.api.core;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.utils.ObjectCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

/**
 * RefCountMessage is a base-class for any message intending to do reference counting. Currently it is used for large
 * message removal.
 * <p>
 * Additional validation on reference counting will be done If you set a system property named "ARTEMIS_REF_DEBUG" and
 * enable logging on this class. Additional logging output will be written when reference counting is broken and these
 * debug options are applied.
 */
public class RefCountMessage {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final boolean REF_DEBUG = System.getProperty("ARTEMIS_REF_DEBUG") != null;

   public static boolean isRefDebugEnabled() {
      return REF_DEBUG && logger.isDebugEnabled();
   }

   public static boolean isRefTraceEnabled() {
      return REF_DEBUG && logger.isTraceEnabled();
   }

   /**
    * Sub classes constructors willing to debug reference counts, can register the objectCleaner through this method.
    */
   protected void registerDebug() {
      if (debugStatus == null) {
         debugStatus = new DebugState(this.toString());
         ObjectCleaner.register(this, debugStatus);
      }
   }

   private static class DebugState implements Runnable {
      private final List<Exception> debugCrumbs = new ArrayList<>();

      // this means the object is accounted for and it should not print any warnings
      volatile boolean accounted;

      volatile boolean referenced;

      String description;

      /**
       * Notice: This runnable cannot hold any reference back to message otherwise it won't ever happen and you will get
       * a memory leak.
       */
      Runnable runWhenLeaked;

      DebugState(String description) {
         this.description = description;
         addDebug("registered");
      }

      /**
       * this marks the Status as accounted for and no need to report an issue when DEBUG hits
       */
      void accountedFor() {
         accounted = true;
      }

      static String getTime() {
         return Instant.now().toString();
      }

      void addDebug(String event) {
         debugCrumbs.add(new Exception(event + " at " + getTime()));
         if (accounted) {
            logger.debug("Message Previously Released {}, {}, \n{}", description, event, debugLocations());
         }
      }

      void up(String description) {
         referenced = true;
         debugCrumbs.add(new Exception("up:" + description + " at " + getTime()));
      }

      void down(String description) {
         debugCrumbs.add(new Exception("down:" + description + " at " + getTime()));
      }

      @Override
      public void run() {
         if (!accounted && referenced) {
            runWhenLeaked.run();
            logger.debug("Message Leaked reference counting{}\n{}", description, debugLocations());
         }
      }

      String debugLocations() {
         StringWriter writer = new StringWriter();
         PrintWriter outWriter = new PrintWriter(writer);
         outWriter.println("Locations:");
         debugCrumbs.forEach(e -> e.printStackTrace(outWriter));
         return writer.toString();
      }
   }

   private DebugState debugStatus;

   private static final AtomicIntegerFieldUpdater<RefCountMessage> DURABLE_REF_COUNT_UPDATER = AtomicIntegerFieldUpdater.newUpdater(RefCountMessage.class, "durableRefCount");
   private static final AtomicIntegerFieldUpdater<RefCountMessage> REF_COUNT_UPDATER = AtomicIntegerFieldUpdater.newUpdater(RefCountMessage.class, "refCount");
   private static final AtomicIntegerFieldUpdater<RefCountMessage> REF_USAGE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(RefCountMessage.class, "usageCount");

   private volatile Map userContext;

   private volatile int durableRefCount = 0;

   private volatile int refCount = 0;

   private volatile int usageCount = 0;

   private volatile boolean released = false;

   private volatile boolean errorCheck = true;

   /**
    * has the refCount fired the action already?
    */
   public boolean isReleased() {
      return released;
   }

   public String debugLocations() {
      if (debugStatus != null) {
         return debugStatus.debugLocations();
      } else {
         return "";
      }
   }

   public static void deferredDebug(Message message, String debugMessage, Object... args) {
      if (message instanceof RefCountMessage countMessage && isRefDebugEnabled()) {
         deferredDebug(countMessage, debugMessage, args);
      }
   }

   public static void deferredDebug(RefCountMessage message, String debugMessage, Object... args) {
      String formattedDebug = MessageFormatter.arrayFormat(debugMessage, args).getMessage();
      message.deferredDebug(formattedDebug);
   }

   /**
    * Deferred debug, that will be used in case certain conditions apply to the RefCountMessage
    */
   public void deferredDebug(String message) {
      if (parentRef != null) {
         parentRef.deferredDebug(message);
      }
      if (debugStatus != null) {
         debugStatus.addDebug(message);
      }
   }

   public int getRefCount() {
      return REF_COUNT_UPDATER.get(this);
   }

   public int getUsage() {
      return REF_USAGE_UPDATER.get(this);
   }

   public int getDurableCount() {
      return DURABLE_REF_COUNT_UPDATER.get(this);
   }

   /**
    * in certain cases the large message is copied from another LargeServerMessage and the ref count needs to be on that
    * place.
    */
   private volatile RefCountMessage parentRef;

   public RefCountMessage getParentRef() {
      return parentRef;
   }

   protected void onUp() {
      if (debugStatus != null) {
         debugStatus.up(counterString());
      }
   }

   protected void released() {
      released = true;
      disableErrorCheck();
   }

   void runOnLeak(Runnable run) {
      if (debugStatus != null) {
         debugStatus.runWhenLeaked = run;
      }
   }

   public void disableErrorCheck() {
      this.errorCheck = false;
      if (debugStatus != null) {
         debugStatus.accountedFor();
      }
   }

   protected void onDown() {
      if (debugStatus != null) {
         debugStatus.down(counterString());
      }
      if (getRefCount() <= 0 && getUsage() <= 0 && getDurableCount() <= 0 && !released) {
         released();
         releaseComplete();
      }
   }

   protected String counterString() {
      return "refCount=" + getRefCount() + ", durableRefCount=" + getDurableCount() + ", usageCount=" + getUsage() + ", parentRef=" + this.parentRef;
   }

   public void setParentRef(RefCountMessage origin) {
      // if copy of a copy.. just go to the parent:
      if (origin.getParentRef() != null) {
         this.parentRef = origin.getParentRef();
      } else {
         this.parentRef = origin;
      }
   }

   protected void releaseComplete() {
   }

   public int usageUp() {
      if (parentRef != null) {
         return parentRef.usageUp();
      }
      int count = REF_USAGE_UPDATER.incrementAndGet(this);
      onUp();
      return count;
   }

   public int usageDown() {
      if (parentRef != null) {
         return parentRef.usageDown();
      }
      int count = REF_USAGE_UPDATER.decrementAndGet(this);
      onDown();
      return count;
   }

   public int durableUp() {
      if (parentRef != null) {
         return parentRef.durableUp();
      }
      int count = DURABLE_REF_COUNT_UPDATER.incrementAndGet(this);
      onUp();
      return count;
   }

   public int durableDown() {
      if (parentRef != null) {
         return parentRef.durableDown();
      }
      int count = DURABLE_REF_COUNT_UPDATER.decrementAndGet(this);
      if (errorCheck && count < 0) {
         reportNegativeCount();
      }
      onDown();
      return count;
   }

   private void reportNegativeCount() {
      if (debugStatus != null) {
         debugStatus.addDebug("Negative counter " + counterString());
      }
      ActiveMQClientLogger.LOGGER.negativeRefCount(String.valueOf(this), counterString(), debugLocations());
   }

   public int refDown() {
      if (parentRef != null) {
         return parentRef.refDown();
      }
      int count = REF_COUNT_UPDATER.decrementAndGet(this);
      if (errorCheck && count < 0) {
         reportNegativeCount();
      }
      onDown();
      return count;
   }

   public int refUp() {
      if (parentRef != null) {
         return parentRef.refUp();
      }
      int count = REF_COUNT_UPDATER.incrementAndGet(this);
      onUp();
      return count;
   }

   public Object getUserContext(Object key) {
      if (userContext == null) {
         return null;
      } else {
         return userContext.get(key);
      }
   }

   public void setUserContext(Object key, Object value) {
      if (userContext == null) {
         userContext = new HashMap();
      }
      userContext.put(key, value);
   }
}
