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
package org.apache.artemis.nativo.jlibaio.ffm;

import org.apache.artemis.nativo.jlibaio.SubmitInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class IOControl<Callback extends SubmitInfo> {

   private static final Logger logger = LoggerFactory.getLogger(IOControl.class);

   private final Object iocbLock = new Object();
   private final Object pollLock = new Object();

   private MemorySegment ioContext;
   private MemorySegment events;
   private int queueSize;
   private int iocbPut;
   private int iocbGet;
   private int used;
   private MemorySegment[] iocbPool;
   private AtomicReferenceArray<Callback> callbackRegistry;

   // -1: delete, 0: free, 1: used
   private AtomicIntegerArray iocbState;

   public MemorySegment ioContext() {
      return this.ioContext;
   }

   public void setIoContext(MemorySegment ioContext) {
      this.ioContext = ioContext;
   }

   public MemorySegment events() {
      return this.events;
   }

   public void setEvents(MemorySegment events) {
      this.events = events;
   }

   public int queueSize() {
      return queueSize;
   }

   public void setQueueSize(int size) {
      this.queueSize = size;
      callbackRegistry = new AtomicReferenceArray<>(size);
      iocbState = new AtomicIntegerArray(size);
   }

   public int iocbPut() {
      return this.iocbPut;
   }

   public int iocbGet() {
      return this.iocbGet;
   }

   public int used() {
      return this.used;
   }

   public MemorySegment[] iocbPool() {
      return this.iocbPool;
   }

   public void setIocbPool(MemorySegment[] iocbPool) {
      this.iocbPool = iocbPool;
   }

   public void addCallback(int idx, Callback callback) {
      if (callbackRegistry.get(idx) != null) {
         throw new IllegalStateException("callback already registered");
      }
      callbackRegistry.set(idx, callback);
   }

   public Callback takeCallback(int idx) {
      return callbackRegistry.getAndSet(idx, null);
   }

   public AtomicIntegerArray getIocbState() {
      return this.iocbState;
   }

   public void withIocbLock(Runnable action) {
      synchronized (iocbLock) {
         action.run();
      }
   }

   public void withPollLock(Runnable action) {
      synchronized (pollLock) {
         action.run();
      }
   }

   public MemorySegment getIOCB() {
      synchronized (iocbLock) {
         final int qSize = this.queueSize;
         if (qSize <= 0 || used >= qSize || iocbPool == null) {
            return null;
         }

         final int idx = iocbGet;
         if (idx < 0 || idx >= qSize) {
            return null;
         }

         final MemorySegment seg = iocbPool[idx];
         if (seg == null || seg.address() == 0L) {
            logger.error("getIOCB: null IOCB at index {}", idx);
            return null;
         }

         used++;
         iocbGet = (idx + 1);
         if (iocbGet >= qSize) {
            iocbGet = 0;
         }
         if (logger.isTraceEnabled()) {
            logger.trace("getIOCB: getIdx={} used={}", idx, used);
         }
         return seg;
      }
   }

   public void putIOCB(MemorySegment iocb) {
      if (iocb == null || iocb.address() == 0L) {
         logger.warn("putIOCB: null IOCB ignored");
         return;
      }
      synchronized (iocbLock) {
         final int qSize = this.queueSize;
         if (qSize <= 0 || used <= 0 || iocbPool == null) {
            return;
         }

         int idx = this.iocbPut;
         if (idx < 0 || idx >= qSize) {
            logger.error("putIOCB: invalid putIdx={} queueSize={}", idx, qSize);
            return;
         }

         iocbPool[idx] = iocb;
         used--;
         iocbPut = (idx + 1);
         if (iocbPut >= qSize) {
            iocbPut = 0;
         }
         if (logger.isTraceEnabled()) {
            logger.trace("putIOCB: putIdx={} used={}", idx, used);
         }
      }
   }

   public boolean isValid() {
      if (ioContext == null || ioContext.address() == 0) {
         return false;
      }
      if (events == null || events.address() == 0) {
         return false;
      }

      if (queueSize <= 0) {
         return false;
      }

      if (used < 0 || used > queueSize) {
         return false;
      }

      if (iocbPool == null || iocbPool.length != queueSize) {
         return false;
      }

      return iocbPut >= 0 && iocbPut < queueSize && iocbGet >= 0 && iocbGet < queueSize;
   }
}
