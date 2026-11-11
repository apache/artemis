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

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQAddressFullException;
import org.apache.activemq.artemis.api.core.ActiveMQInterruptedException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.core.client.ActiveMQClientMessageBundle;

public class ClientProducerCreditsImpl extends AbstractProducerCreditsImpl {


   private final Semaphore semaphore;

   public ClientProducerCreditsImpl(ClientSessionInternal session, SimpleString address, int windowSize) {
      super(session, address, windowSize);


      // Doesn't need to be fair since session is single threaded
      semaphore = new Semaphore(0, false);

   }


   @Override
   protected void afterAcquired(int credits) throws ActiveMQAddressFullException {
      // check to see if the blocking mode is FAIL on the server
      synchronized (this) {
         super.afterAcquired(credits);

         if (serverRespondedWithFail) {
            serverRespondedWithFail = false;

            // remove existing credits to force the client to ask the server for more on the next send
            semaphore.drainPermits();
            pendingCredits = 0;
            arriving = 0;

            throw ActiveMQClientMessageBundle.BUNDLE.addressIsFull(address.toString(), credits);
         }
      }
   }

   @Override
   protected void actualAcquire(int credits) {

      boolean tryAcquire;
      synchronized (this) {
         tryAcquire = semaphore.tryAcquire(credits);
      }

      if (!tryAcquire && !closed) {
         this.blocked = true;
         try {
            while (!semaphore.tryAcquire(credits, 10, TimeUnit.SECONDS)) {
               // I'm using string concatenation here in case address is null
               // better getting a "null" string than a NPE
               ActiveMQClientLogger.LOGGER.outOfCreditOnFlowControl("" + address);
            }
         } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ActiveMQInterruptedException(interrupted);
         } finally {
            this.blocked = false;
         }
      }
   }


   @Override
   public synchronized void reset() {
      // Any pendingCredits credits from before failover won't arrive, so we re-initialise

      semaphore.drainPermits();

      super.reset();
   }


   @Override
   public void close() {
      super.close();

      // Closing a producer that is blocking should make it return
      semaphore.release(Integer.MAX_VALUE / 2);
   }

   @Override
   public void receiveCredits(final int credits) {
      synchronized (this) {
         super.receiveCredits(credits);
      }

      semaphore.release(credits);
   }

   @Override
   public int getBalance() {
      return semaphore.availablePermits();
   }


}

