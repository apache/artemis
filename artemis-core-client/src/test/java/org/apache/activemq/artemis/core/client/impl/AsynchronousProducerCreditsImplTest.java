/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.core.client.impl;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AsynchronousProducerCreditsImplTest {

   @Test
   @Timeout(10)
   public void testZeroCredits() throws Exception {
      ClientSessionInternal session = Mockito.mock(ClientSessionInternal.class);
      AsynchronousProducerCreditsImpl producerCredits = new AsynchronousProducerCreditsImpl(session, null, 0, Mockito.mock(ClientProducerFlowCallback.class));
      producerCredits.receiveCredits(0);
      Mockito.verify(session).sendProducerCreditsMessage(0, null);
   }

   @Test
   @Timeout(10)
   public void testCreditsRequestedWhenMessageSizeExactlyEqualsBalance() throws Exception {
      ClientSessionInternal mockClientSession = Mockito.mock(ClientSessionInternal.class);

      AtomicInteger creditsRequested = new AtomicInteger(0);
      AtomicBoolean blocked = new AtomicBoolean(false);

      int producerWindowSize = 1000;

      Mockito.doAnswer(inv -> {
         creditsRequested.addAndGet(inv.getArgument(0));
         return null;
      }).when(mockClientSession).sendProducerCreditsMessage(Mockito.anyInt(), Mockito.any());

      AsynchronousProducerCreditsImpl producerCredits = new AsynchronousProducerCreditsImpl(mockClientSession, null, producerWindowSize, new ClientProducerFlowCallback() {

         @Override
         public void onCreditsFlow(boolean isBlocked, ClientProducerCredits credits) {
            blocked.set(isBlocked);
         }

         @Override
         public void onCreditsFail(ClientProducerCredits credits) {
         }

      });

      int internalWindowSize = producerWindowSize / 2;
      // drain balance to just above internalWindowSize
      producerCredits.actualAcquire(internalWindowSize - 100);

      int messageSize = producerCredits.getBalance();
      producerCredits.acquireCredits(messageSize);

      assertTrue(creditsRequested.get() > 0, "credits must be requested when message size exactly equals balance");

      producerCredits.receiveCredits(creditsRequested.get());
      assertTrue(producerCredits.getBalance() > 0);
      assertFalse(blocked.get());
   }

}
