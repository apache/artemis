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
import org.apache.artemis.nativo.jlibaio.ffm.ReleaseCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.artemis.nativo.jlibaio.ffm.AIORing.AIO_RING_HEADER_SIZE;
import static org.apache.artemis.nativo.jlibaio.ffm.AIORing.AIO_RING_HEAD_VH;
import static org.apache.artemis.nativo.jlibaio.ffm.AIORing.AIO_RING_NR_VH;
import static org.apache.artemis.nativo.jlibaio.ffm.AIORing.AIO_RING_TAIL_VH;
import static org.apache.artemis.nativo.jlibaio.ffm.AIORing.hasUsableRing;
import static org.apache.artemis.nativo.jlibaio.ffm.AIORing.toAioRing;
import static org.apache.artemis.nativo.jlibaio.ffm.Constants.LOCK_EX;
import static org.apache.artemis.nativo.jlibaio.ffm.Constants.LOCK_NB;
import static org.apache.artemis.nativo.jlibaio.ffm.Constants.ONE_MEGA;
import static org.apache.artemis.nativo.jlibaio.ffm.Constants.O_CREAT;
import static org.apache.artemis.nativo.jlibaio.ffm.Constants.O_DIRECT;
import static org.apache.artemis.nativo.jlibaio.ffm.Constants.O_RDWR;
import static org.apache.artemis.nativo.jlibaio.ffm.Constants.PERMISSION_MODE;
import static org.apache.artemis.nativo.jlibaio.ffm.Constants.RING_REAPER;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.CAPTURE_STATE_LAYOUT;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.CLOSE_HANDLE;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.ERRNO_VH;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.FALLOCATE_HANDLE;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.FDATASYNC_HANDLE;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.FLOCK_HANDLE;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.FREE_BUF_HANDLE;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.FSTAT_HANDLE;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.FSYNC_HANDLE;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.IO_GETEVENTS_HANDLE;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.IO_QUEUE_INIT_HANDLE;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.IO_QUEUE_RELEASE_HANDLE;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.IO_SUBMIT_HANDLE;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.LSEEK_HANDLE;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.MEMSET_HANDLE;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.OPEN_HANDLE;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.POSIX_MEMALIGN_HANDLE;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.STAT_HANDLE;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.WRITE_HANDLE;
import static org.apache.artemis.nativo.jlibaio.ffm.FFMHandles.oneMegaMutex;
import static org.apache.artemis.nativo.jlibaio.ffm.IOCBInit.IOCB_LAYOUT_SIZE;
import static org.apache.artemis.nativo.jlibaio.ffm.IOEvent.IO_EVENT_LAYOUT;
import static org.apache.artemis.nativo.jlibaio.ffm.Stat.STAT_LAYOUT;

public class FFMNativeHelper<Callback extends SubmitInfo> {

   private static final Logger logger = LoggerFactory.getLogger(FFMNativeHelper.class);

   private static volatile MemorySegment oneMegaBuffer;

   private static final AtomicBoolean forceSysCall = new AtomicBoolean(false);

   private static final ThreadLocal<SharedContext> SHARED_CONTEXT = ThreadLocal.withInitial(SharedContext::new);

   private static final AtomicReference<Integer> DUMB_FD = new AtomicReference<>(-1);

   private static volatile String DUMB_PATH;

   private static final int DUMB_WRITE_HANDLER;

   static {
      DUMB_WRITE_HANDLER = initDumbFd();
   }

   private static int initDumbFd() {
      try {
         Integer fd = DUMB_FD.get();
         if (fd != null && fd >= 0) {
            if (logger.isTraceEnabled()) {
               logger.trace("Dumb FD already initialized: {}", fd);
            }
            return fd;
         }
         Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
         Path tempFile;
         try {
            tempFile = Files.createTempFile(tempDir, "artemisDumb", ".tmp");
            DUMB_PATH = tempFile.toString();
         } catch (Exception e) {
            throw new RuntimeException("Failed to create temp file for shutdown signaling", e);
         }
         fd = open(DUMB_PATH, false);
         if (fd < 0) {
            Files.deleteIfExists(tempFile);
            throw new RuntimeException("Failed to open dumb file: " + tempFile);
         }

         DUMB_FD.set(fd);
         if (logger.isDebugEnabled()) {
            logger.debug("Dumb FD created: {}, path = {}", fd, DUMB_PATH);
         }
         return fd;
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   private static void closeDumbFd() {
      try {
         Integer fd = DUMB_FD.getAndSet(-1);
         if (fd != null && fd >= 0) {
            try {
               close(fd);
               if (DUMB_PATH != null) {
                  Path path = Path.of(DUMB_PATH);
                  Files.deleteIfExists(path);
               }
               if (logger.isDebugEnabled()) {
                  logger.debug("Dumb FD closed and file removed: fd={}, path={}", fd, DUMB_PATH);
               }
            } catch (IOException e) {
               logger.warn("Failed to close/remove dumb FD {}: {}", fd, e.getMessage());
            }
         }
      } finally {
         DUMB_PATH = null;
      }
   }

   private final ReleaseCallback releaseCallback;

   public FFMNativeHelper(ReleaseCallback releaseCallback) {
      this.releaseCallback = releaseCallback;
   }

   //It implements a user space batch read io events implementation that attempts to read io avoiding any sys calls
   // This implementation will look at the internal structure (aio_ring) and move along the memory result
   private int ringioGetEvents(MemorySegment aioCtxAddr,
                               MemorySegment events,
                               int min,
                               int max,
                               MemorySegment timeout) throws Throwable {
      if (aioCtxAddr == null || aioCtxAddr.address() == 0) {
         if (logger.isTraceEnabled()) {
            logger.trace("ringioGetEvents: aioCtxAddr is null -> syscall");
         }
         return ioGetEvents(aioCtxAddr, events, min, max, timeout);
      }

      if (min < 0 || max <= 0 || min > max) {
         logger.warn("ringioGetEvents: invalid parameters: min={}, max={}", min, max);
         return ioGetEvents(aioCtxAddr, events, min, max, timeout);
      }

      MemorySegment ring = toAioRing(aioCtxAddr);
      if (ring.address() == 0) {
         if (logger.isTraceEnabled()) {
            logger.trace("toAioRing failed -> syscall");
         }
         return ioGetEvents(aioCtxAddr, events, min, max, timeout);
      }

      //checks if it could be completed in user space, saving a sys call
      if (!(RING_REAPER && !isForceSyscall() && hasUsableRing(ring))) {
         if (logger.isTraceEnabled()) {
            logger.trace("kernel not supporting ring buffer");
         }
         return ioGetEvents(aioCtxAddr, events, min, max, timeout);
      }

      int ringNr = (int) AIO_RING_NR_VH.getAcquire(ring, 0L);
      if (ringNr <= 0) {
         if (logger.isTraceEnabled()) {
            logger.trace("ringioGetEvents: invalid ring size {} -> syscall", ringNr);
         }
         return ioGetEvents(aioCtxAddr, events, min, max, timeout);
      }

      // We're assuming to be the exclusive writer to head, so we just need a compiler barrier
      // instead of compiler barrier, using getAcquired
      int head = (int) AIO_RING_HEAD_VH.getAcquire(ring, 0L);
      int tail = (int) AIO_RING_TAIL_VH.getAcquire(ring, 0L);

      int available = tail - head;
      if (available < 0) {
         available += ringNr;
      }

      if (logger.isTraceEnabled()) {
         logger.trace("tail={}, head={} nr={} available={}", tail, head, ringNr, available);
      }

      boolean timeoutZero = false;
      if (timeout != null && timeout.address() != 0) {
         timeoutZero = timeout.get(ValueLayout.JAVA_LONG, 0L) == 0 && timeout.get(ValueLayout.JAVA_LONG, 8L) == 0;
      }

      if (available < min && !timeoutZero) {
         if (logger.isTraceEnabled()) {
            logger.trace("ringioGetEvents: not enough available events -> syscall");
         }
         return ioGetEvents(aioCtxAddr, events, min, max, timeout);
      }

      if (available == 0) {
         return 0;
      }

      if (available >= max) {
         // This is to trap a possible bug from the kernel:
         //       https://bugzilla.redhat.com/show_bug.cgi?id=1845326
         //       https://issues.apache.org/jira/browse/ARTEMIS-2800
         //
         // On the race available would eventually be >= max, while ring->tail was invalid
         // we could work around by waiting ring-tail to change:
         // while (ring->tail == tail) mem_barrier();
         //
         // however eventually we could have available==max in a legal situation what could lead to infinite loop here
         if (logger.isTraceEnabled()) {
            logger.trace("ringioGetEvents: ring full ({}>= {}) → syscall", available, max);
         }
         return ioGetEvents(aioCtxAddr, events, min, max, timeout);

         // also: I could have called io_getevents to the one at the end of this method
         //       but I really hate goto, so I would rather have a duplicate code here
         //       and I did not want to create another memory flag to stop the rest of the code
      }

      //the kernel has written ring->tail from an interrupt:
      //we need to load acquire the completed events here

      // available < max ( this is always true )
      // old code -> int availableNr = available < max ? available : max;
      //if isn't needed to wrap we can avoid % operations that are quite expansive
      int needMod = ((head + available) >= ringNr) ? 1 : 0;

      long eventSize = IO_EVENT_LAYOUT.byteSize();
      long requiredBytes;
      try {
         requiredBytes = Math.multiplyExact((long) max, eventSize);
      } catch (ArithmeticException e) {
         logger.warn("ringioGetEvents: overflow computing required event bytes max={}, eventSize={}", max, eventSize);
         return ioGetEvents(aioCtxAddr, events, min, max, timeout);
      }

      MemorySegment usableEvents = events.reinterpret(requiredBytes);

      int eventIdx = head;
      int contiguous = Math.min(available, ringNr - head);

      // first contiguous chunk
      for (int i = 0; i < contiguous; i++) {
         long eventOffset = AIO_RING_HEADER_SIZE + (long) (eventIdx + i) * eventSize;
         MemorySegment srcEvent = ring.asSlice(eventOffset, eventSize);
         MemorySegment dstEvent = usableEvents.asSlice((long) i * eventSize, eventSize);
         dstEvent.copyFrom(srcEvent);
      }

      // wrap around chunk, if any
      if (contiguous < available) {
         for (int i = contiguous; i < available; i++) {
            long eventOffset = AIO_RING_HEADER_SIZE + (long) (i - contiguous) * eventSize;
            MemorySegment srcEvent = ring.asSlice(eventOffset, eventSize);
            MemorySegment dstEvent = usableEvents.asSlice((long) i * eventSize, eventSize);
            dstEvent.copyFrom(srcEvent);
         }
      }
      //it allow the kernel to build its own view of the ring buffer size
      //and push new events if there are any
      int newHead = (head + available) % ringNr;
      AIO_RING_HEAD_VH.setRelease(ring, 0L, newHead);

      if (logger.isTraceEnabled()) {
         logger.trace("consumed non sys-call = {}", available);
      }
      return available;
   }

   private int ioGetEvents(MemorySegment aioCtx,
                           MemorySegment events,
                           long min,
                           long max,
                           MemorySegment timeout) throws Throwable {
      MemorySegment captureState = SHARED_CONTEXT.get().getStateCapture();
      // Direct syscall wrapper
      int result = (int) IO_GETEVENTS_HANDLE.invoke(captureState, aioCtx, min, max, events, (timeout == null ? MemorySegment.NULL : timeout));

      if (result < 0) {
         int errno = (int) ERRNO_VH.get(captureState, 0L);
         logger.warn("ioGetEvents: failed to call IO_GETEVENTS_HANDLE. result={}, errno={}", result, errno);
      }
      return result;
   }

   private static void freeOneMegaBuffer() {
      oneMegaMutex.lock();
      try {
         if (oneMegaBuffer != null) {
            freeBuffer(oneMegaBuffer);
            oneMegaBuffer = null;
            logger.debug("One mega buffer freed");
         }
      } finally {
         oneMegaMutex.unlock();
      }
   }

   public static void shutdownHook() {
      logger.debug("FFMNativeHelper shutdown hook executing");
      closeDumbFd();
      freeOneMegaBuffer();
   }

   public static void setForceSyscall(boolean value) {
      forceSysCall.set(value);
      logger.info("forceSysCall={}", value);
   }

   public static boolean isForceSyscall() {
      return forceSysCall.get() || !RING_REAPER;
   }

   public IOControl<Callback> newContext(int queueSize) {
      if (logger.isDebugEnabled()) {
         logger.debug("Initializing context with QueueSize={}", queueSize);
      }

      IOControl<Callback> ioControl = new IOControl<>();
      try {
         MemorySegment ioContext = ioQueueInit(queueSize);
         ioControl.setIoContext(ioContext);

         MemorySegment events = Arena.global().allocate(IO_EVENT_LAYOUT, queueSize);
         if (events.address() == 0) {
            ioQueueRelease(ioContext);
            throw new OutOfMemoryError("Arena allocation failed: events array(queueSize = " + queueSize + ")");
         }
         ioControl.setEvents(events);

         MemorySegment[] iocbPool = new MemorySegment[queueSize];
         for (int i = 0; i < queueSize; i++) {
            MemorySegment iocb = Arena.global().allocate(IOCBInit.IOCB_LAYOUT);
            if (iocb.address() == 0) {
               for (int j = 0; j < i; j++) {
                  if (iocbPool[j] != null && iocbPool[j].address() != 0) {
                     freeBuffer(iocbPool[j]);
                  }
               }
               destroyIOCBs(events, queueSize);
               ioQueueRelease(ioContext);
               throw new OutOfMemoryError(String.format("Arena memory allocation failed: iocb[%d/%d]", i, queueSize));
            }
            IOCBInit.setAioData(iocb, i);
            iocbPool[i] = iocb;
         }
         ioControl.setIocbPool(iocbPool);
         ioControl.setQueueSize(queueSize);

         if (logger.isDebugEnabled()) {
            logger.debug("Context created successfully: queueSize={}, ioContext=0x{}", queueSize, Long.toHexString(ioContext.address()));
         }
         return ioControl;
      } catch (Throwable t) {
         logger.error("newContext failed: queueSize={}, error={}", queueSize, t.getMessage(), t);
         throw new RuntimeException(t);
      }
   }

   private void ioQueueRelease(MemorySegment ioContext) {
      if (ioContext == null || ioContext.address() == 0) {
         return;
      }
      try {
         MemorySegment captureState = SHARED_CONTEXT.get().getStateCapture();
         int result = (int) IO_QUEUE_RELEASE_HANDLE.invoke(captureState, ioContext);
         if (result < 0) {
            logger.warn("io_queue_release(0x{}) failed: errno={}", Long.toHexString(ioContext.address()), ERRNO_VH.get(captureState, 0L));
         } else {
            if (logger.isTraceEnabled()) {
               logger.trace("io_queue_release(0x{}) successful", Long.toHexString(ioContext.address()));
            }
         }
      } catch (Throwable e) {
         logger.warn("ioQueueRelease failed: error:{}", e.getMessage(), e);
      }
   }

   private void destroyIOCBs(MemorySegment array, int size) throws Throwable {
      destroyIOCBsBounded(array, size);
   }

   private void destroyIOCBsBounded(MemorySegment iocbArray, int upperBound) throws Throwable {
      for (int i = 0; i < upperBound; i++) {
         MemorySegment iocb = iocbArray.getAtIndex(ValueLayout.ADDRESS, i);
         if (iocb.address() != 0) {
            freeBuffer(iocb);
         }
      }
      freeBuffer(iocbArray);
   }

   private MemorySegment ioQueueInit(int queueSize) {
      try (Arena arena = Arena.ofConfined()) {
         MemorySegment ctx = arena.allocate(ValueLayout.ADDRESS);
         MemorySegment captureState = arena.allocate(CAPTURE_STATE_LAYOUT);
         int result = (int) IO_QUEUE_INIT_HANDLE.invokeExact(captureState, queueSize, ctx);
         if (result < 0) {
            throw new IOException("io_queue_init failed: " + ERRNO_VH.get(captureState, 0L));
         }
         long rawAddress = ctx.get(ValueLayout.JAVA_LONG, 0L);
         if (logger.isTraceEnabled()) {
            logger.trace("ioQueueInit({}) → 0x{} (result={})", queueSize, Long.toHexString(rawAddress), result);
         }
         return MemorySegment.ofAddress(rawAddress).reinterpret(1, Arena.global(), null);
      } catch (Throwable e) {
         throw new RuntimeException(e);
      }
   }

   public void deleteContext(IOControl ioControl) {
      if (ioControl == null) {
         if (logger.isDebugEnabled()) {
            logger.debug("deleteContext: null ioControl");
         }
         return;
      }
      if (!ioControl.isValid()) {
         logger.warn("deleteContext: invalid ioControl");
         return;
      }
      if (logger.isDebugEnabled()) {
         logger.debug("deleteContext: queueSize={}, ioContext=0x{}", ioControl.queueSize(), Long.toHexString(ioControl.ioContext().address()));
      }
      try {
         MemorySegment dumbIocb = ioControl.getIOCB();
         if (dumbIocb == null || dumbIocb.address() == 0) {
            throw new IOException("Not enough space in libaio queue during shutdown");
         }
         ioPrepPOp(dumbIocb, DUMB_WRITE_HANDLER, MemorySegment.NULL, 0L, 0L, 1);
         int iocbId = (int) IOCBInit.getAioData(dumbIocb);
         ioControl.getIocbState().set(iocbId, -1);

         if (!submit(ioControl, dumbIocb)) {
            logger.warn("deleteContext: submit failed: Continuing cleanup");
            return;
         } else {
            if (logger.isDebugEnabled()) {
               logger.debug("deleteContext: dumb write submitted (fd={})", DUMB_WRITE_HANDLER);
            }
         }

         // to make sure the poll has finished
         ioControl.withPollLock(() -> {
         });

         // To return any pending IOCBs
         int drained = 0;
         while (true) {
            try {
               int result = ringioGetEvents(ioControl.ioContext(), ioControl.events(), 0, 1, null);
               if (result <= 0) {
                  if (logger.isTraceEnabled()) {
                     logger.trace("deleteContext: drain complete (result={})", result);
                  }
                  break;
               }
               if (logger.isDebugEnabled()) {
                  logger.debug("deleteContext: drained {} pending IOCBs", result);
               }
               MemorySegment events = ioControl.events();
               events = events.reinterpret((long) result * IO_EVENT_LAYOUT.byteSize());
               for (int i = 0; i < result; i++) {
                  MemorySegment event = events.asSlice(i * IO_EVENT_LAYOUT.byteSize(), IO_EVENT_LAYOUT.byteSize());
                  MemorySegment iocbp = event.get(ValueLayout.ADDRESS, 8L);
                  if (iocbp != null && iocbp.address() != 0) {
                     ioControl.putIOCB(iocbp);
                  }
               }
               drained += result;
            } catch (Throwable t) {
               logger.warn("deleteContext: drain unexpected error: {}", t.getMessage());
               break;
            }
         }
         if (logger.isTraceEnabled()) {
            logger.trace("deleteContext: drained {} IOCBs under lock", drained);
         }

         ioQueueRelease(ioControl.ioContext());

         MemorySegment[] iocbPool = ioControl.iocbPool();
         if (iocbPool != null) {
            for (MemorySegment iocb : iocbPool) {
               if (iocb != null && iocb.address() != 0) {
                  freeBuffer(iocb);
               }
            }
         }

         freeBuffer(ioControl.events());
         if (logger.isDebugEnabled()) {
            logger.debug("deleteContext completed successfully");
         }
      } catch (IOException e) {
         logger.warn("deleteContext: {}", e.getMessage());
      } catch (Throwable e) {
         logger.error("deleteContext: unexpected error", e);
      }
   }

   public static int open(String filePath, boolean direct) throws IOException {
      int flags = O_RDWR | O_CREAT;
      if (direct) {
         flags |= O_DIRECT;
         if (logger.isDebugEnabled()) {
            logger.debug("Opening with O_DIRECT= {}", Integer.toHexString(O_DIRECT));
         }
      }
      try (Arena arena = Arena.ofConfined()) {
         // manually ensuring null termination by adding "\0"
         MemorySegment path = arena.allocateFrom(filePath + "\0");
         MemorySegment captureState = arena.allocate(CAPTURE_STATE_LAYOUT);

         int fd = (int) OPEN_HANDLE.invoke(captureState, path, flags, (int) PERMISSION_MODE);

         if (fd < 0) {
            int errorCode = (int) ERRNO_VH.get(captureState, 0L);
            logger.error("open failed: path={}, flags={}, direct={}, errno={}", filePath, Integer.toHexString(flags), direct, errorCode);
            throw new IOException("Open failed for filePath = " + filePath + " with fd errno = " + errorCode);
         }
         if (logger.isDebugEnabled()) {
            logger.debug("Opened {} with fd = {}", direct ? "O_DIRECT" : "normal", fd);
         }
         return fd;
      } catch (Throwable t) {
         throw new IOException("Failed to open " + filePath, t);
      }
   }

   public static void close(int fd) throws IOException {
      try (Arena arena = Arena.ofConfined()) {
         MemorySegment captureState = arena.allocate(CAPTURE_STATE_LAYOUT);

         int res = (int) CLOSE_HANDLE.invoke(captureState, fd);

         if (res < 0) {
            int errorCode = (int) ERRNO_VH.get(captureState, 0L);
            throw new IOException("Error during close for fd = " + fd + ", error code = " + errorCode);
         }
         if (logger.isDebugEnabled()) {
            logger.debug("File with fd = {} is successfully closed", fd);
         }
      } catch (Throwable t) {
         throw new IOException(t);
      }
   }

   public static MemorySegment newAlignedBuffer(int size, int alignment) {
      if (size % alignment != 0) {
         throw new IllegalArgumentException("size " + size + " must be aligned to " + alignment);
      }
      try (Arena arena = Arena.ofConfined()) {
         MemorySegment prtOut = arena.allocate(ValueLayout.ADDRESS);
         MemorySegment captureState = arena.allocate(CAPTURE_STATE_LAYOUT);

         int res = (int) POSIX_MEMALIGN_HANDLE.invoke(captureState, prtOut, (long) alignment, (long) size);
         if (res != 0) {
            int errno = (int) ERRNO_VH.get(captureState, 0L);
            throw new RuntimeException("posix_memalign failed: result= " + res + " errno=" + errno + "(size= " + size + ", align= " + alignment + ")");
         }
         // get allocated pointer
         MemorySegment memorySegment = prtOut.get(ValueLayout.ADDRESS, 0L).reinterpret(size);
         if (memorySegment.address() == 0) {
            throw new RuntimeException("posix_memalign returned NULL!");
         }
         //zero initialization
         MEMSET_HANDLE.invoke(memorySegment, 0, (long) size);
         if (logger.isDebugEnabled()) {
            logger.debug("posix_memalign(addrs={}, size={}, align={})", Long.toHexString(memorySegment.address()), size, alignment);
         }
         return memorySegment;
      } catch (Throwable t) {
         throw new RuntimeException("newAlignedBuffer failed", t);
      }
   }

   public static void freeBuffer(MemorySegment memorySegment) {
      if (memorySegment == null || memorySegment.address() == 0) {
         if (logger.isDebugEnabled()) {
            logger.debug("freeBuffer: memorySegment is null");
         }
      }
      try {
         if (logger.isTraceEnabled()) {
            logger.trace("freeing buffer at address: 0x{} with capacity={}", Long.toHexString(memorySegment.address()), memorySegment.asByteBuffer().capacity());
         }
         FREE_BUF_HANDLE.invoke(memorySegment);
      } catch (Throwable t) {
         throw new RuntimeException("freeBuffer: Native free failed for address 0x" + Long.toHexString(memorySegment.address()), t);
      }
   }

   private boolean submit(IOControl ioControl, MemorySegment iocb) throws IOException {
      Objects.requireNonNull(ioControl.ioContext(), "Attempted to submit I/O to a null context");
      SharedContext ctx = SHARED_CONTEXT.get();
      int result = -1;
      try {
         ctx.getIocbArray().setAtIndex(ValueLayout.JAVA_LONG, 0, iocb.address());

         if (logger.isTraceEnabled()) {
            logger.trace("submit: ctx=0x{}, iocb=0x{}, iocbArray=0x{}", Long.toHexString(ioControl.ioContext().address()), Long.toHexString(iocb.address()), Long.toHexString(ctx.getIocbArray().address()));
         }

         result = (int) IO_SUBMIT_HANDLE.invokeExact(ioControl.ioContext(), 1L, ctx.getIocbArray());

         if (result < 0) {
            throw new IOException("Error while submitting IO: result = " + result);
         }
         return true;
      } catch (Throwable t) {
         throw new IOException(t);
      } finally {
         if (result < 0) {
            // return to the pool
            ioControl.putIOCB(iocb);
         }
      }
   }

   public void submitWrite(int fd,
                           IOControl ioControl,
                           long position,
                           int size,
                           ByteBuffer bufferWrite,
                           Callback callback) throws IOException {

      MemorySegment iocb = ioControl.getIOCB();
      if (iocb == null || iocb.address() == 0) {
         throw new IOException("IOCB pool exhausted (used=" + ioControl.used() + "/queueSize=" + ioControl.queueSize() + ")");
      }
      int callbackId = (int) IOCBInit.getAioData(iocb);
      if (logger.isTraceEnabled()) {
         logger.trace("submitWrite called! callbackId: {}", callbackId);
      }
      boolean submitted = false;
      try {
         if (!ioControl.getIocbState().compareAndSet(callbackId, 0, 1)) {
            throw new IOException("submitWrite failed: callbackId=" + callbackId + " already in use");
         }
         ioControl.addCallback(callbackId, callback);
         ioPrepPOp(iocb, fd, MemorySegment.ofBuffer(bufferWrite.duplicate().clear()), size, position, 1);

         submit(ioControl, iocb);
         submitted = true;
      } catch (Throwable e) {
         throw new IOException("submitWrite failed", e);
      } finally {
         if (!submitted) {
            ioControl.takeCallback(callbackId);
         }
      }
   }

   /*
    * Unable to load io_prep_pwrite and io_prep_pread from libaio because it is defined as a static inline function
    * in the <libaio.h> header file
    * Because it is an inline function, the code is compiled directly into any C program that
    * includes the header. It does not exist as a named symbol inside the copiled libaio.so shared lib file.
    *
    * 0: IO_CMD_PREAD
    * 1: IO_CMD_PWRITE
    * 2: IO_CMD_FSYNC
    * 3: IO_CMD_FDSYNC
    * 7: IO_CMD_NOOP
    * 8: IO_CMD_PREADV (Vectorized read)
    *
    * */
   private void ioPrepPOp(MemorySegment iocb, int fd, MemorySegment buffer, long nbytes, long offset, int op) {
      if (iocb == null) {
         if (logger.isTraceEnabled()) {
            logger.trace("ioPrepPOp: iocb is null");
         }
         return;
      }
      IOCBInit.setAioFildes(iocb, fd);
      IOCBInit.setAioLioOpcode(iocb, (short) op);
      IOCBInit.setAioReqprio(iocb, (short) 0);
      IOCBInit.setAioBuf(iocb, buffer.address());
      IOCBInit.setAioNbytes(iocb, nbytes);
      IOCBInit.setAioOffset(iocb, offset);
   }

   public void submitRead(int fd,
                          IOControl ioControl,
                          long position,
                          int size,
                          ByteBuffer bufferWrite,
                          Callback callback) throws IOException {

      MemorySegment iocb = ioControl.getIOCB();
      if (iocb == null || iocb.address() == 0) {
         throw new IOException("IOCB pool exhausted");
      }

      if (logger.isTraceEnabled()) {
         logger.trace("submitRead called!");
      }
      long callbackId = IOCBInit.getAioData(iocb);
      boolean submitted = false;
      try {
         if (!ioControl.getIocbState().compareAndSet((int) callbackId, 0, 1)) {
            throw new IOException("submitRead failed: callbackId=" + callbackId + " already in use");
         }
         ioControl.addCallback((int) callbackId, callback);
         ioPrepPOp(iocb, fd, MemorySegment.ofBuffer(bufferWrite.duplicate().clear()), size, position, 0);

         submit(ioControl, iocb);
         submitted = true;
      } catch (Throwable e) {
         throw new IOException("submitRead failed", e);
      } finally {
         if (!submitted) {
            ioControl.takeCallback((int) callbackId);
         }
      }
   }

   public int poll(IOControl<Callback> ioControl, Callback[] callbacks, int min, int max) {
      if (ioControl == null || !ioControl.isValid()) {
         logger.warn("poll: invalid context");
         return 0;
      }

      try {
         int result = ringioGetEvents(ioControl.ioContext(), ioControl.events(), min, max, null);
         if (logger.isTraceEnabled()) {
            logger.trace("poll harvested {} events (min={}, max={})", result, min, max);
         }
         if (result <= 0) {
            return result;
         }

         MemorySegment events = ioControl.events();
         if (!events.scope().isAlive()) {
            logger.error("Poll:: CRITICAL: Events segment is closed before polling!");
            return 0;
         }

         events = events.reinterpret((long) result * IO_EVENT_LAYOUT.byteSize());
         for (int i = 0; i < result; i++) {
            MemorySegment event = events.asSlice(i * IO_EVENT_LAYOUT.byteSize(), IO_EVENT_LAYOUT.byteSize());
            MemorySegment iocbp = event.get(ValueLayout.ADDRESS, 8L).reinterpret(64);
            int eventResult = (int) event.get(ValueLayout.JAVA_LONG, 16L);
            if (logger.isTraceEnabled()) {
               logger.trace("poll[{}]: res={}, iocbp=0x{}, AioData: {}", i, eventResult, Long.toHexString(iocbp.address()), IOCBInit.getAioData(iocbp));
            }

            if (eventResult < 0) {
               logger.warn("poll[{}]: I/O error: {}", i, eventResult);
            }

            int callbackIdRaw = (int) IOCBInit.getAioData(iocbp);
            int iocbState = ioControl.getIocbState().get(callbackIdRaw);
            if (iocbState == 0 || iocbState == -1) {
               logger.warn("poll[{}]: invalid callback=0x{}", i, Long.toHexString(callbackIdRaw));
               ioControl.putIOCB(iocbp);
               continue;
            }

            Callback callback = ioControl.takeCallback(callbackIdRaw);
            if (callback != null) {
               callbacks[i] = callback;
               if (eventResult < 0) {
                  callback.onError(eventResult, "I/O error");
               } else {
                  callback.done();
               }
               if (releaseCallback != null) {
                  releaseCallback.release();
               }
            } else {
               logger.warn("poll[{}]: callback not found for id=0x{}", i, Long.toHexString(callbackIdRaw));
            }
            ioControl.getIocbState().set(callbackIdRaw, 0);
            ioControl.putIOCB(iocbp);
         }
         return result;
      } catch (Throwable e) {
         logger.error("poll failed", e);
         return -1;
      }
   }

   public void blockedPoll(IOControl<Callback> ioControl, boolean useFdatasync) {
      if (logger.isDebugEnabled()) {
         logger.debug("blockedPoll starting(useFdatasync={})", useFdatasync);
      }
      if (ioControl == null || !ioControl.isValid()) {
         logger.warn("blockedPoll: invalid context");
         return;
      }

      ioControl.withPollLock(() -> {
         try (Arena arena = Arena.ofConfined()) {
            boolean running = true;
            int lastFile = -1;

            while (running) {
               if (!ioControl.isValid()) {
                  if (logger.isDebugEnabled()) {
                     logger.debug("blockedPoll: context destroyed - self-exit");
                  }
                  break;
               }
               int result = ringioGetEvents(ioControl.ioContext(), ioControl.events(), 1, ioControl.queueSize(), null);
               if (result == -4) {
                  if (logger.isTraceEnabled()) {
                     logger.trace("blockedPoll: EINTR - ignoring (jmap?)");
                  }
                  continue;
               }

               if (result < 0) {
                  logger.error("blockedPoll: ringio_get_events failed: {}", result);
                  throw new IOException("blockedPoll: ringio_get_events failed:" + result);
               }

               if (logger.isTraceEnabled()) {
                  logger.trace("blockedPoll returned: {} events", result);
               }
               lastFile = -1;

               MemorySegment harvestedEvents = ioControl.events().reinterpret((long) result * IO_EVENT_LAYOUT.byteSize());

               for (int i = 0; i < result; i++) {

                  MemorySegment event = harvestedEvents.asSlice(i * IO_EVENT_LAYOUT.byteSize(), IO_EVENT_LAYOUT.byteSize());
                  MemorySegment iocbp = IOEvent.getObj(event).reinterpret(IOCB_LAYOUT_SIZE);

                  int fd = IOCBInit.getAioFildes(iocbp);
                  if (fd == DUMB_WRITE_HANDLER) {
                     if (logger.isTraceEnabled()) {
                        logger.trace("blockedPoll: shutdown signal detected (dumb fd={})", fd);
                     }
                     ioControl.putIOCB(iocbp);
                     running = false;
                     break;
                  }

                  int eventResult = (int) event.get(ValueLayout.JAVA_LONG, 16L);

                  if (useFdatasync && lastFile != fd) {
                     try {
                        fdatasync(arena, fd);
                     } catch (Throwable t) {
                        String errorMessage = "fdatasync failed: " + t.getMessage();
                        logger.warn("blockedPoll: {}", errorMessage);
                     }
                  }

                  int callbackIdRaw = (int) IOCBInit.getAioData(iocbp);
                  if (logger.isTraceEnabled()) {
                     logger.trace("blockedPoll: callbackIdRaw: {}", callbackIdRaw);
                  }

                  // this IOCB state is to detect invalid elements on the buffer.
                  if (ioControl.getIocbState().compareAndSet(callbackIdRaw, 1, 0)) {
                     ioControl.putIOCB(iocbp);
                     Callback callback = ioControl.takeCallback(callbackIdRaw);
                     if (callback != null) {
                        if (eventResult < 0) {
                           logger.error("blockedPoll[{}]: I/O error fd={}, {}", i, fd, eventResult);
                           callback.onError(eventResult, "I/O error in blockedPoll");
                        } else {
                           callback.done();
                           if (logger.isTraceEnabled()) {
                              logger.trace("callback executed!");
                           }
                        }
                        if (releaseCallback != null) {
                           releaseCallback.release();
                        }
                     }
                  } else {
                     if (!forceSysCall.get()) {
                        logger.warn("blockedPoll: Warning from ActiveMQ Artemis Native Layer: Your system is hitting duplicate / invalid records from libaio, which is a bug on the Linux Kernel you are using.You should set property org.apache.activemq.artemis.native.jlibaio.FORCE_SYSCALL=1 or upgrade to a kernel version that contains a fix");
                     }
                     setForceSyscall(true);
                  }
               }
            }
         } catch (Throwable e) {
            logger.error("blockedPoll error", e);
         }
      });
      if (logger.isDebugEnabled()) {
         logger.debug("blockedPoll completed");
      }
   }

   private static void fdatasync(Arena arena, int fd) throws Throwable {
      MemorySegment captureState = arena.allocate(CAPTURE_STATE_LAYOUT);
      int res = (int) FDATASYNC_HANDLE.invoke(captureState, fd);
      if (res < 0) {
         throw new IOException("fdatasync(fd = " + fd + ") failed, errno: " + ERRNO_VH.get(captureState, 0L));
      }
   }

   public static int getNativeVersion() {
      return 200;
   }

   public static boolean lock(int fd) {
      if (fd < 0) {
         return false;
      }
      try (Arena arena = Arena.ofConfined()) {
         MemorySegment captureState = arena.allocate(CAPTURE_STATE_LAYOUT);
         int result = (int) FLOCK_HANDLE.invokeExact(captureState, fd, LOCK_EX | LOCK_NB);
         return result == 0;
      } catch (Throwable t) {
         logger.warn("lock(fd={}) failed", fd);
         return false;
      }
   }

   public static void memsetBuffer(ByteBuffer buffer, int size) {
      if (!buffer.isDirect()) {
         throw new IllegalArgumentException("libaio requires NativeBuffer (Direct ByteBuffer)");
      }
      if (size <= 0 || size > buffer.capacity()) {
         throw new IllegalArgumentException("Invalid size: " + size + " (capacity = " + buffer.capacity() + ")");
      }

      try {
         ByteBuffer dup = buffer.duplicate();
         dup.clear();
         MemorySegment seg = MemorySegment.ofBuffer(dup);
         long addr = seg.address();
         if (logger.isTraceEnabled()) {
            logger.trace("memset(buffer={}, size={})", buffer, size);
         }
         MemorySegment nativeSeg = MemorySegment.ofAddress(addr).reinterpret(buffer.capacity());
         // memset(buffer, 0, size)
         MemorySegment ignore = (MemorySegment) MEMSET_HANDLE.invokeExact(nativeSeg, 0, (long) size);
         if (logger.isTraceEnabled()) {
            logger.trace("memset completed!");
         }
      } catch (Throwable t) {
         throw new RuntimeException("memset failed", t);
      }
   }

   public static long getSize(int fd) throws IOException {
      try (Arena arena = Arena.ofConfined()) {
         MemorySegment statbuf = arena.allocate(STAT_LAYOUT);
         MemorySegment captureState = arena.allocate(CAPTURE_STATE_LAYOUT);

         int res = (int) FSTAT_HANDLE.invokeExact(captureState, fd, statbuf);
         if (res < 0) {
            int errno = (int) ERRNO_VH.get(captureState, 0L);
            throw new IOException("fstat failed for fd=" + fd + ": errno=" + errno);
         }

         long size = Stat.getSize(statbuf);
         if (logger.isDebugEnabled()) {
            logger.debug("getSize(fd = {}): {} bytes", fd, size);
         }
         return size;
      } catch (Throwable t) {
         throw new IOException("getSize failed for fd = " + fd, t);
      }
   }

   public static int getBlockSizeFD(int fd) throws IOException {
      try (Arena arena = Arena.ofConfined()) {
         MemorySegment statbuf = arena.allocate(STAT_LAYOUT);
         MemorySegment captureState = arena.allocate(CAPTURE_STATE_LAYOUT);
         int res = (int) FSTAT_HANDLE.invokeExact(captureState, fd, statbuf);
         if (res < 0) {
            int errno = (int) ERRNO_VH.get(captureState, 0L);
            throw new IOException("fstat failed for fd=" + fd + ": errno=" + errno);
         }

         int blksize = Stat.getBlksize(statbuf);
         if (blksize <= 0 || blksize > 65536) {
            logger.warn("Invalid st_blksize={} for fd={}, using 4096", blksize, fd);
            return 4096;
         }
         if (logger.isTraceEnabled()) {
            logger.trace("getBlockSizeFD(fd = {}) = {} bytes", fd, blksize);
         }
         return blksize;
      } catch (Throwable t) {
         throw new IOException("getBlockSizeFD failed for fd=" + fd, t);
      }
   }

   public static int getBlockSize(String path) throws IOException {
      try (Arena arena = Arena.ofConfined()) {
         MemorySegment pathSeg = arena.allocateFrom(path);
         MemorySegment statbuf = arena.allocate(STAT_LAYOUT);
         MemorySegment captureState = arena.allocate(CAPTURE_STATE_LAYOUT);
         int res = (int) STAT_HANDLE.invokeExact(captureState, pathSeg, statbuf);
         if (res < 0) {
            int errno = (int) ERRNO_VH.get(captureState, 0L);
            throw new IOException("statx failed path=" + path + ": errno = " + errno);
         }
         int blksize = Stat.getBlksize(statbuf);
         if (blksize <= 0 || blksize > 65536) {
            logger.warn("Invalid st_blksize={} for path={}, using 4096", blksize, path);
            return 4096;
         }
         if (logger.isTraceEnabled()) {
            logger.trace("getBlockSize(path = {}) = {} bytes", path, blksize);
         }
         return blksize;
      } catch (Throwable t) {
         logger.warn("getBlockSize failed '{}', fallback 4096", path, t);
         return 4096;
      }
   }

   public static void fallocate(int fd, long size) throws IOException {
      try {
         MemorySegment captureState = SHARED_CONTEXT.get().getStateCapture();
         // fallocate(fd, mode=0, offset=0, len=size)
         int res = (int) FALLOCATE_HANDLE.invoke(captureState, fd, 0, 0L, size);
         if (res < 0) {
            int errno = (int) ERRNO_VH.get(captureState, 0L);
            throw new IOException("fallocate failed fd=" + fd + " size=" + size + ": errno= " + errno);
         }
         // fsync(fd) - ensure allocation hits the disk
         res = (int) FSYNC_HANDLE.invoke(captureState, fd);
         if (res < 0) {
            int errno = (int) ERRNO_VH.get(captureState, 0L);
            logger.warn("fsync after allocation failed fd={}: errno={}", fd, errno);
         }
         //lseek(fd, 0, SEEK_SET) - reset position
         long pos = (long) LSEEK_HANDLE.invoke(captureState, fd, 0L, 0);
         if (pos < 0) {
            int errno = (int) ERRNO_VH.get(captureState, 0L);
            logger.warn("lseek reset failed fd={}: errno={}", fd, errno);
         }
         if (logger.isDebugEnabled()) {
            logger.debug("fallocate(fd={}, size={}) + fsync + lseek(reset)", fd, size);
         }
      } catch (Throwable t) {
         throw new IOException("fallocate failed fd=" + fd + " size=" + size, t);
      }
   }

   private static MemorySegment verifyBuffer(int alignment) {
      oneMegaMutex.lock();
      try {
         if (oneMegaBuffer == null) {
            if (logger.isDebugEnabled()) {
               logger.debug("Allocating 1MB shared buffer (align={})", alignment);
            }
            oneMegaBuffer = newAlignedBuffer((int) ONE_MEGA, alignment);
         }
         return oneMegaBuffer;
      } finally {
         oneMegaMutex.unlock();
      }
   }

   public static void fill(int fd, int alignment, long size) throws IOException {
      if (logger.isDebugEnabled()) {
         logger.debug("fill(fd={}, alignment={}, size={})", fd, alignment, size);
      }

      long blocks = size / ONE_MEGA;
      long rest = size % ONE_MEGA;

      //verify/create 1MB buffer
      verifyBuffer(alignment);

      try {
         MemorySegment captureState = SHARED_CONTEXT.get().getStateCapture();
         // lseek (fd, 0, SEEK_SET)
         LSEEK_HANDLE.invoke(captureState, fd, 0L, 0);
         //Write full blocks
         for (long i = 0; i < blocks; i++) {
            MemorySegment bufferAddrs = oneMegaBuffer;
            long written = (long) WRITE_HANDLE.invoke(captureState, fd, bufferAddrs, ONE_MEGA);
            if (written < 0) {
               int errno = (int) ERRNO_VH.get(captureState, 0L);
               throw new IOException("write failed block " + i + ": errno= " + errno);
            }
         }

         // Remainder
         if (rest > 0) {
            MemorySegment bufferAddrs = oneMegaBuffer;
            long written = (long) WRITE_HANDLE.invoke(captureState, fd, bufferAddrs, rest);
            if (written < 0) {
               int errno = (int) ERRNO_VH.get(captureState, 0L);
               throw new IOException("write rest failed: errno= " + errno);
            }
         }

         //Reset position
         LSEEK_HANDLE.invoke(captureState, fd, 0L, 0);
      } catch (Throwable t) {
         throw new IOException("fill failed fd=" + fd + " size=" + size, t);
      }
      if (logger.isDebugEnabled()) {
         logger.debug("fill completed: {} bytes written.", size);
      }
   }

   public static void writeInternal(int fd, long position, long size, ByteBuffer bufferWrite) throws IOException {
      // No Impl
   }
}
