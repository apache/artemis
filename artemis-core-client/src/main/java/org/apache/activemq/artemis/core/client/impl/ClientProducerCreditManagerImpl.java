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

import java.lang.invoke.MethodHandles;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.spi.core.remoting.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientProducerCreditManagerImpl implements ClientProducerCreditManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final int MAX_ANONYMOUS_CREDITS_CACHE_SIZE = 1000;

   private final Map<SimpleString, ClientProducerCredits> producerCredits = new LinkedHashMap<>();

   private final Map<SimpleString, ClientProducerCredits> anonymousCredits = new LinkedHashMap<>();

   private final ClientSessionInternal session;

   private int windowSize;

   private ClientProducerFlowCallback callback;

   public ClientProducerCreditManagerImpl(final ClientSessionInternal session, final int windowSize) {
      this.session = session;

      this.windowSize = windowSize;
   }

   @Override
   public void setCallback(ClientProducerFlowCallback callback) {
      this.callback = callback;
   }

   @Override
   public synchronized ClientProducerCredits getCredits(final SimpleString address,
                                                        final boolean anon,
                                                        SessionContext context) {
      if (windowSize == -1) {
         return ClientProducerCreditsNoFlowControl.instance;
      } else {
         boolean needInit = false;
         ClientProducerCredits credits;

         synchronized (this) {
            credits = producerCredits.get(address);

            if (credits == null) {
               // Doesn't need to be fair since session is single threaded
               credits = build(address);
               needInit = true;

               producerCredits.put(address, credits);
            }

            if (anon) {
               addToAnonymousList(address, credits);
            } else {
               credits.incrementRefCount();

               // Remove from anon credits (if there)
               anonymousCredits.remove(address);
            }
         }

         // The init is done outside of the lock
         // otherwise packages may arrive with flow control
         // while this is still sending requests causing a dead lock
         if (needInit) {
            credits.init(context);
         }

         return credits;
      }
   }

   private ClientProducerCredits build(SimpleString address) {
      if (callback != null) {
         return new AsynchronousProducerCreditsImpl(session, address, windowSize, callback);
      } else {
         return new ClientProducerCreditsImpl(session, address, windowSize);
      }
   }

   @Override
   public synchronized void returnCredits(final SimpleString address) {
      ClientProducerCredits credits = producerCredits.get(address);

      if (credits != null && credits.decrementRefCount() == 0) {
         addToAnonymousList(address, credits);
      }
   }

   @Override
   public synchronized void receiveCredits(final SimpleString address, final int credits) {
      ClientProducerCredits cr = producerCredits.get(address);

      if (cr != null) {
         cr.receiveCredits(credits);
      }
   }

   @Override
   public synchronized void receiveFailCredits(final SimpleString address, int credits) {
      ClientProducerCredits cr = producerCredits.get(address);

      if (cr != null) {
         cr.receiveFailCredits(credits);
      }
   }

   @Override
   public synchronized void reset() {
      for (ClientProducerCredits credits : producerCredits.values()) {
         credits.reset();
      }
   }

   @Override
   public synchronized void close() {
      windowSize = -1;

      for (ClientProducerCredits credits : producerCredits.values()) {
         credits.close();
      }

      producerCredits.clear();

      anonymousCredits.clear();
   }

   @Override
   public synchronized int creditsMapSize() {
      return producerCredits.size();
   }

   @Override
   public synchronized int getMaxAnonymousCacheSize() {
      return anonymousCredits.size();
   }

   private void addToAnonymousList(final SimpleString address, final ClientProducerCredits credits) {
      anonymousCredits.put(address, credits);

      if (anonymousCredits.size() > MAX_ANONYMOUS_CREDITS_CACHE_SIZE) {
         logger.trace("Producer list has more than MAX_ANONYMOUS_CREDITS_CACHE_SIZE={}. clearing elements from the list", anonymousCredits.size());
         try {
            Iterator<Map.Entry<SimpleString, ClientProducerCredits>> iter = anonymousCredits.entrySet().iterator();
            while (iter.hasNext() && anonymousCredits.size() > MAX_ANONYMOUS_CREDITS_CACHE_SIZE) {
               Map.Entry<SimpleString, ClientProducerCredits> entry = iter.next();
               if (entry.getValue().getArriving() <= 0) {
                  logger.trace("Removing credit {}, {}", entry.getKey(), entry.getValue());
                  iter.remove();
                  producerCredits.remove(entry.getKey());
                  entry.getValue().close();
               } else {
                  logger.trace("Keeping credit for {}, {}", entry.getKey(), entry.getValue());
               }
            }
         } catch (Throwable e) {
            // this is not really an expected error. no need for a logger code
            logger.warn("Error clearing anonymousList - {}", e.getMessage(), e);
         }
      }
   }

   static class ClientProducerCreditsNoFlowControl implements ClientProducerCredits {

      static ClientProducerCreditsNoFlowControl instance = new ClientProducerCreditsNoFlowControl();

      @Override
      public int getArriving() {
         return 0;
      }

      @Override
      public void acquireCredits(int credits) {
      }

      @Override
      public void receiveCredits(int credits) {
      }

      @Override
      public void receiveFailCredits(int credits) {
      }

      @Override
      public boolean isBlocked() {
         return false;
      }

      @Override
      public void init(SessionContext ctx) {
      }

      @Override
      public void reset() {
      }

      @Override
      public void close() {
      }

      @Override
      public void incrementRefCount() {
      }

      @Override
      public int decrementRefCount() {
         return 1;
      }

      @Override
      public SimpleString getAddress() {
         return SimpleString.of("");
      }

      @Override
      public int getBalance() {
         return 0;
      }
   }
}
