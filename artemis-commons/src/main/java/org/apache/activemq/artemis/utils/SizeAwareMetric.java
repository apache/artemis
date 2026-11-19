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
import java.util.concurrent.locks.StampedLock;

public class SizeAwareMetric {

   public interface AggregateCallback {
      void aggregate(long deltaElements, long deltaSize);
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

   private AggregateCallback[] parentCallback;

   private Runnable overCallback;

   private Runnable underCallback;

   private final StampedLock stampedLock = new StampedLock();

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

   public boolean fitsElements(int elements) {
      // maxElements <= 0 means not configured
      return maxElements <= 0 || elementsUpdater.get(this) + elements < maxElements;
   }

   public boolean fitsBytes(long bytesToWrite) {
      // maxSize <= 0 means not configured
      return maxSize <= 0 || sizeUpdater.get(this) + bytesToWrite < maxSize;
   }

   public long getElements() {
      return elements;
   }

   public boolean isSizeEnabled() {
      return maxSize >= 0;
   }

   public SizeAwareMetric addCallback(SizeAwareMetric parentMetric) {
      return addCallback(parentMetric::aggregate);
   }

   public SizeAwareMetric addCallback(AggregateCallback onAggregate) {
      long stamp = stampedLock.writeLock();
      try {
         // sending current elements to recently added callback
         onAggregate.aggregate(elementsUpdater.get(this), sizeUpdater.get(this));
         if (parentCallback == null) {
            parentCallback = new AggregateCallback[] {onAggregate};
         } else {
            AggregateCallback[] replacingCallback = new AggregateCallback[parentCallback.length + 1];
            System.arraycopy(parentCallback, 0, replacingCallback, 0, parentCallback.length);
            replacingCallback[parentCallback.length] = onAggregate;
            this.parentCallback = replacingCallback;
         }
      } finally {
         stampedLock.unlockWrite(stamp);
      }
      return this;
   }

   public SizeAwareMetric removeCallback(AggregateCallback callback) {
      long stamp = stampedLock.writeLock();
      try {
         if (parentCallback == null) {
            return this;
         }
         int index = -1;
         for (int i = 0; i < parentCallback.length; i++) {
            if (parentCallback[i] == callback) {
               index = i;
               break;
            }
         }
         if (index < 0) {
            return this;
         }
         callback.aggregate(-elementsUpdater.get(this), -sizeUpdater.get(this));
         if (parentCallback.length == 1) {
            parentCallback = null;
         } else {
            AggregateCallback[] replacingCallback = new AggregateCallback[parentCallback.length - 1];
            System.arraycopy(parentCallback, 0, replacingCallback, 0, index);
            System.arraycopy(parentCallback, index + 1, replacingCallback, index, parentCallback.length - index - 1);
            parentCallback = replacingCallback;
         }
      } finally {
         stampedLock.unlockWrite(stamp);
      }
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

   public void aggregate(SizeAwareMetric input) {
      aggregate(input.getElements(), input.getSize());
   }

   public void aggregate(final long deltaElements, final long deltaSize) {
      aggregate(deltaElements, deltaSize, true);
   }

   public void aggregate(final long deltaElements, final long deltaSize, final boolean affectCallbacks) {
      if (deltaElements == 0 && deltaSize == 0) {
         return;
      }

      changeFlag(NOT_USED, FREE);

      long stampRead = stampedLock.readLock();
      try {
         if (parentCallback != null && affectCallbacks) {
            executeAggregationCallbacks(deltaElements, deltaSize);
         }

         long currentSize = deltaSize != 0 ? sizeUpdater.addAndGet(this, deltaSize) : sizeUpdater.get(this);

         long currentElements;
         if (deltaElements != 0) {
            currentElements = elementsUpdater.addAndGet(this, deltaElements);
         } else {
            currentElements = elementsUpdater.get(this);
         }
         boolean adding = deltaElements > 0 || (deltaElements == 0 && deltaSize >= 0);
         if (adding) {
            checkOver(currentElements, currentSize);
         } else {
            checkUnder(currentElements, currentSize);
         }
      } finally {
         stampedLock.unlockRead(stampRead);
      }

   }

   private void executeAggregationCallbacks(final long deltaElements, final long deltaSize) {
      for (AggregateCallback callback : parentCallback) {
         try {
            callback.aggregate(deltaElements, deltaSize);
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
         }
      }
   }

   public final long addElement(final long deltaSize) {
      return addElement(deltaSize, true);
   }

   public final long addElement(final long deltaSize, boolean affectCallbacks) {
      return doAdd(deltaSize, false, affectCallbacks);
   }

   public final long incrementElement() {
      changeFlag(NOT_USED, FREE);
      long stampRead = stampedLock.readLock();
      try {
         if (parentCallback != null) {
            executeAggregationCallbacks(1, 0);
         }
         long currentElements = elementsUpdater.incrementAndGet(this);
         checkOver(currentElements, sizeUpdater.get(this));
         return currentElements;
      } finally {
         stampedLock.unlockRead(stampRead);
      }
   }

   public final long decrementElement() {
      changeFlag(NOT_USED, FREE);
      long stampRead = stampedLock.readLock();
      try {
         if (parentCallback != null) {
            executeAggregationCallbacks(-1, 0);
         }
         long currentElements = elementsUpdater.decrementAndGet(this);
         checkUnder(currentElements, sizeUpdater.get(this));
         return currentElements;
      } finally {
         stampedLock.unlockRead(stampRead);
      }
   }

   public final long addSize(final long delta) {
      return addSize(delta, true);
   }

   public final long addSize(final long delta, boolean affectCallbacks) {
      return doAdd(delta, true, affectCallbacks);
   }

   private long doAdd(final long delta, final boolean sizeOnly, boolean affectCallbacks) {
      if (delta == 0) {
         if (logger.isDebugEnabled()) {
            logger.debug("Ignoring zero-delta add", new Exception("trace"));
         }
         return sizeUpdater.get(this);
      }

      changeFlag(NOT_USED, FREE);

      long stampRead = stampedLock.readLock();
      try {
         long elementDelta;
         if (sizeOnly) {
            elementDelta = 0;
         } else if (delta > 0) {
            elementDelta = 1;
         } else {
            elementDelta = -1;
         }

         if (parentCallback != null && affectCallbacks) {
            executeAggregationCallbacks(elementDelta, delta);
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
      } finally {
         stampedLock.unlockRead(stampRead);
      }
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
            logger.debug("OverElements [currentElements={}, maxElements={}]", currentElements, maxElements);
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
