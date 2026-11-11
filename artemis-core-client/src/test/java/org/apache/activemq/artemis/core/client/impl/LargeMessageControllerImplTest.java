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

package org.apache.activemq.artemis.core.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LargeMessageControllerImplTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Timeout(10)
   public void testControllerTimeout() throws Exception {

      ClientConsumerInternal consumerMock = Mockito.mock(ClientConsumerInternal.class);
      LargeMessageControllerImpl largeMessageController = new LargeMessageControllerImpl(consumerMock, 1000, 1);

      AtomicInteger bytesWritten = new AtomicInteger();
      AtomicInteger errors = new AtomicInteger(0);
      final byte filling = (byte) 3;

      largeMessageController.setOutputStream(new OutputStream() {
         @Override
         public void write(int b) throws IOException {
            bytesWritten.incrementAndGet();
            if (b != filling) {
               errors.incrementAndGet();
            }
         }
      });

      largeMessageController.addPacket(createBytes(100, filling), 1000, true);

      int exceptionCounter = 0;
      try {
         largeMessageController.waitCompletion(0);
      } catch (ActiveMQException e) {
         logger.debug(e.getMessage(), e);
         exceptionCounter++;
      }

      assertEquals(1, exceptionCounter);

      largeMessageController.addPacket(createBytes(900, filling), 1000, false);

      assertTrue(largeMessageController.waitCompletion(0));
      assertEquals(1000, bytesWritten.get());
      assertEquals(0, errors.get());
   }

   byte[] createBytes(int size, byte fill) {
      byte[] bytes = new byte[size];
      Arrays.fill(bytes, fill);
      return bytes;
   }


}
