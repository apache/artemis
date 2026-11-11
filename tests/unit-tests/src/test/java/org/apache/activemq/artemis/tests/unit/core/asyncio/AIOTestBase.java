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
package org.apache.activemq.artemis.tests.unit.core.asyncio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioFile;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * The base class for AIO Tests
 */
public abstract class AIOTestBase extends ActiveMQTestBase {
   // The AIO Test must use a local filesystem. Sometimes $HOME is on a NFS on
   // most enterprise systems

   protected String fileName = "fileUsedOnNativeTests.log";

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      assertTrue(LibaioContext.isLoaded(), String.format("libAIO is not loaded on %s %s %s", System.getProperty("os.name"), System.getProperty("os.arch"), System.getProperty("os.version")));
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      assertEquals(0, LibaioContext.getTotalMaxIO());

      super.tearDown();
   }

   protected void encodeBufer(final ByteBuffer buffer) {
      buffer.clear();
      int size = buffer.limit();
      for (int i = 0; i < size - 1; i++) {
         buffer.put((byte) ('a' + i % 20));
      }

      buffer.put((byte) '\n');

   }

   protected void preAlloc(final LibaioFile controller, final long size) throws ActiveMQException {
      controller.fill(controller.getBlockSize(), size);
   }

   protected static class CountDownCallback implements IOCallback {

      private final CountDownLatch latch;

      private final List<Integer> outputList;

      private final int order;

      private final AtomicInteger errors;

      public CountDownCallback(final CountDownLatch latch,
                               final AtomicInteger errors,
                               final List<Integer> outputList,
                               final int order) {
         this.latch = latch;

         this.outputList = outputList;

         this.order = order;

         this.errors = errors;
      }

      volatile boolean doneCalled = false;

      int errorCalled = 0;

      final AtomicInteger timesDoneCalled = new AtomicInteger(0);

      @Override
      public void done() {
         if (outputList != null) {
            outputList.add(order);
         }
         doneCalled = true;
         timesDoneCalled.incrementAndGet();
         if (latch != null) {
            latch.countDown();
         }
      }

      @Override
      public void onError(final int errorCode, final String errorMessage) {
         new Exception("Error called:: " + errorCode + " message::" + errorMessage).printStackTrace();
         errorCalled++;
         if (outputList != null) {
            outputList.add(order);
         }
         if (errors != null) {
            errors.incrementAndGet();
         }
         if (latch != null) {
            // even thought an error happened, we need to inform the latch,
            // or the test won't finish
            latch.countDown();
         }
      }

      public static void checkResults(final int numberOfElements, final ArrayList<Integer> result) {
         assertEquals(numberOfElements, result.size());
         int i = 0;
         for (Integer resultI : result) {
            assertEquals(i++, resultI.intValue());
         }
      }
   }

}
