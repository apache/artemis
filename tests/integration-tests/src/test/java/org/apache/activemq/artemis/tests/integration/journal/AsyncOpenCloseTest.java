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
package org.apache.activemq.artemis.tests.integration.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.aio.AIOSequentialFileFactory;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class AsyncOpenCloseTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testCloseOnSubmit() throws Exception {
      assumeTrue(LibaioContext.isLoaded());
      AtomicInteger errors = new AtomicInteger(0);

      SequentialFileFactory factory = new AIOSequentialFileFactory(temporaryFolder, (Throwable error, String message, String file) -> errors.incrementAndGet(), 4 * 1024);
      factory.start();

      SequentialFile file = factory.createSequentialFile("fileAIO.bin");
      file.open(1024, true);

      final int WRITES = 100;
      final int RECORD_SIZE = 4 * 1024;
      final int OPEN_TIMES = 25;

      file.fill(WRITES * RECORD_SIZE);

      ByteBuffer buffer = factory.newBuffer(RECORD_SIZE);
      ActiveMQBuffer buffer2 = ActiveMQBuffers.wrappedBuffer(buffer);

      try {

         file.close(true, false);
         AtomicInteger submit = new AtomicInteger(0);

         ReusableLatch valve = new ReusableLatch(0);

         byte writtenByte = (byte) 'a';
         for (int nclose = 0; nclose < OPEN_TIMES; nclose++) {
            logger.debug("************************************************** test {}", nclose);
            writtenByte++;
            if (writtenByte >= (byte) 'z') {
               writtenByte = (byte) 'a';
            }
            buffer2.setIndex(0, 0);
            for (int s = 0; s < RECORD_SIZE; s++) {
               buffer2.writeByte(writtenByte);
            }
            file.open(1024, true);
            CyclicBarrier blocked = new CyclicBarrier(2);
            for (int i = 0; i < WRITES; i++) {
               if (i == 10) {
                  valve.countUp();
               }
               file.position(i * RECORD_SIZE);
               submit.incrementAndGet();
               buffer2.setIndex(0, RECORD_SIZE);
               file.write(buffer2, true, new IOCallback() {
                  @Override
                  public void done() {
                     try {
                        if (!valve.await(1, TimeUnit.MILLISECONDS)) {
                           logger.debug("blocking");
                           blocked.await(5, TimeUnit.SECONDS);
                           valve.await(5, TimeUnit.SECONDS);
                           logger.debug("unblocking");
                        }
                     } catch (Exception e) {
                        e.printStackTrace();
                        errors.incrementAndGet();
                     }
                     submit.decrementAndGet();
                  }

                  @Override
                  public void onError(int errorCode, String errorMessage) {
                     errors.incrementAndGet();
                  }
               });
            }
            blocked.await();
            logger.debug("Closing");
            file.close(false, false);
            // even though the callback is blocked, the content of the file should already be good as written
            validateFile(file, writtenByte);
            valve.countDown();
            Wait.assertEquals(0, submit::get, 5000, 10);

         }
         Wait.assertEquals(0, submit::get);
      } finally {
         factory.releaseBuffer(buffer);
         factory.stop();
      }

      assertEquals(0, errors.get());

   }

   private void validateFile(SequentialFile file, byte writtenByte) throws IOException {
      FileInputStream fileInputStream = new FileInputStream(file.getJavaFile());
      byte[] wholeFile = fileInputStream.readAllBytes();
      for (int i = 0; i < wholeFile.length; i++) {
         assertEquals(writtenByte, (byte) wholeFile[i]);
      }
      fileInputStream.close();
   }

}
