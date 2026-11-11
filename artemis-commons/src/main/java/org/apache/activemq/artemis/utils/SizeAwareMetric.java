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
package org.apache.activemq.artemis.utils;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class SizeAwareMetric {

   public interface AddCallback {
      void add(int delta, boolean sizeOnly);
   }

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int PENDING_FREE = 0, FREE = 1, PENDING_OVER_SIZE = 2, OVER_SIZE = 3, PENDING_OVER_ELEMENTS = 4, OVER_ELEMENTS = 5, NOT_USED = -1;

   private static final AtomicLongFieldUpdater<SizeAwareMetric> elementsUpdater = AtomicLongFieldUpdater.newUpdater(SizeAwareMetric.class, "elements");
   private volatile long elements;

   private static final AtomicLongFieldUpdater<SizeAwareMetric> sizeUpdater = AtomicLongFieldUpdater.newUpdater(SizeAwareMetric.class, "size");
   private volatile long size;

   private static final AtomicIntegerFieldUpdater<SizeAwareMetric> flagUpdater = AtomicIntegerFieldUpdater.newUpdater(SizeAwareMetric.class, "flag");
   private volatile int flag = NOT_USED;

   private long maxElements = -1; // disabled by default

   private long lowerMarkElements;

   private long maxSize = -1; // disabled by default

   private long lowerMarkSize;

   private AddCallback onSizeCallback;

   private Runnable overCallback;

   private Runnable underCallback;

   /**
    * To be used in a case where we just measure elements
    */
   public SizeAwareMetric() {
   }


   public SizeAwareMetric(long maxSize, long lowerMarkSize, long maxElements, long lowerMarkElements) {
      if (lowerMarkSize > maxSize) {
         throw new IllegalArgumentException("lowerMark must be <= maxSize");
      }
      if (lowerMarkElements > maxElements) {
         throw new IllegalArgumentException("lowerMarkElements must be <= maxElements");
      }
      this.maxElements = maxElements;
      this.lowerMarkElements = lowerMarkElements;
      this.maxSize = maxSize;
      this.lowerMarkSize = lowerMarkSize;
   }

   public boolean isOver() {
      return flag > FREE; // equivalent to flag != FREE && flag != NOT_USED;
   }

   public boolean isOverSize() {
      return flag == OVER_SIZE;
   }

   public boolean isOverElements() {
      return flag == OVER_ELEMENTS;
   }

   public long getSize() {
      return size;
   }

   public boolean isElementsEnabled() {
      return maxElements >= 0;
   }

   public long getElements() {
      return elements;
   }

   public boolean isSizeEnabled() {
      return maxSize >= 0;
   }

   public SizeAwareMetric setOnSizeCallback(AddCallback onSize) {
      this.onSizeCallback = onSize;
      return this;
   }

   public SizeAwareMetric setOverCallback(Runnable over) {
      this.overCallback = over;
      return this;
   }

   public SizeAwareMetric setUnderCallback(Runnable under) {
      this.underCallback = under;
      return this;
   }

   protected void over() {
      if (overCallback != null) {
         try {
            overCallback.run();
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
         }
      }
   }

   protected void under() {
      if (underCallback != null) {
         try {
            underCallback.run();
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
         }
      }
   }

   private boolean changeFlag(int expected, int newValue) {
      return flagUpdater.compareAndSet(this, expected, newValue);
   }

   public final long addSize(final int delta) {
      return addSize(delta, false);
   }

   public final long addSize(final int delta, boolean sizeOnly) {
      return addSize(delta, sizeOnly, true);
   }

   public final long addSize(final int delta, final boolean sizeOnly, boolean affectCallbacks) {
      if (delta == 0) {
         if (logger.isDebugEnabled()) {
            logger.debug("SizeAwareMetric ignored with size 0", new Exception("trace"));
         }
         return sizeUpdater.get(this);
      }

      changeFlag(NOT_USED, FREE);

      if (onSizeCallback != null && affectCallbacks) {
         try {
            onSizeCallback.add(delta, sizeOnly);
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
         }
      }

      long currentSize = sizeUpdater.addAndGet(this, delta);

      long currentElements;
      if (sizeOnly) {
         currentElements = elementsUpdater.get(this);
      } else if (delta > 0) {
         currentElements = elementsUpdater.incrementAndGet(this);
      } else {
         currentElements = elementsUpdater.decrementAndGet(this);
      }

      if (delta > 0) {
         checkOver(currentElements, currentSize);
      } else { // (delta < 0)
         checkUnder(currentElements, currentSize);
      }

      return currentSize;
   }

   public void setMax(long maxSize, long lowerMarkSize, long maxElements, long lowerMarkElements) {
      this.maxSize = maxSize;
      this.lowerMarkSize = lowerMarkSize;
      this.maxElements = maxElements;
      this.lowerMarkElements = lowerMarkElements;
      long currentSize = sizeUpdater.get(this);
      long currentElements = elementsUpdater.get(this);
      checkOver(currentElements, currentSize);
      checkUnder(currentElements, currentSize);
   }

   private void checkUnder(long currentElements, long currentSize) {
      if (isUnderSize(currentSize) && changeFlag(OVER_SIZE, PENDING_FREE)) {
         if (isOverElements(currentElements) && changeFlag(PENDING_FREE, OVER_ELEMENTS)) {
            logger.debug("Switch from OVER_SIZE to OVER_ELEMENTS [currentSize={}, currentElements={}, lowerMarkSize={}, maxElements={}]",
                    currentSize, currentElements, lowerMarkSize, maxElements);
            return;
         }

         try {
            logger.debug("UnderSize [currentSize={}, lowerMarkSize={}]", currentSize, lowerMarkSize);
            under();
         } finally {
            changeFlag(PENDING_FREE, FREE);
         }
      }

      if (isUnderElements(currentElements) && changeFlag(OVER_ELEMENTS, PENDING_FREE)) {
         if (isOverSize(currentSize) && changeFlag(PENDING_FREE, OVER_SIZE)) {
            logger.debug("Switch from OVER_ELEMENTS to OVER_SIZE [currentElements={}, currentSize={}, lowerMarkElements={}, maxSize={}]",
                    currentElements, currentSize, lowerMarkElements, maxSize);
            return;
         }

         try {
            logger.debug("UnderElements [currentElements={}, lowerMarkElements={}]", currentElements, lowerMarkElements);
            under();
         } finally {
            changeFlag(PENDING_FREE, FREE);
         }
      }
   }

   private boolean isUnderSize(long currentSize) {
      return isSizeEnabled() && currentSize < lowerMarkSize;
   }

   private boolean isOverSize(long currentSize) {
      return isSizeEnabled() && currentSize >= maxSize;
   }

   private boolean isUnderElements(long currentElements) {
      return isElementsEnabled() && currentElements < lowerMarkElements;
   }

   private boolean isOverElements(long currentElements) {
      return isElementsEnabled() && currentElements >= 0 && currentElements >= maxElements;
   }

   private void checkOver(long currentElements, long currentSize) {
      if (isOverSize(currentSize) && changeFlag(FREE, PENDING_OVER_SIZE)) {
         try {
            logger.debug("OverSize [currentSize={}, maxSize={}]", currentSize, maxSize);
            over();
         } finally {
            changeFlag(PENDING_OVER_SIZE, OVER_SIZE);
         }
      }

      if (isOverElements(currentElements) && changeFlag(FREE, PENDING_OVER_ELEMENTS)) {
         try {
            logger.debug("currentElements [currentSize={}, maxElements={}]", currentElements, maxElements);
            over();
         } finally {
            changeFlag(PENDING_OVER_ELEMENTS, OVER_ELEMENTS);
         }
      }
   }

   @Override
   public String toString() {
      return "SizeAwareMetric{" + "elements=" + elements + ", size=" + size + '}';
   }
}
