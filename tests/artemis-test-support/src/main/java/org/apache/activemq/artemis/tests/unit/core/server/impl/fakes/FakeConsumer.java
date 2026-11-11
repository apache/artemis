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
package org.apache.activemq.artemis.tests.unit.core.server.impl.fakes;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.core.server.HandleStatus;
import org.apache.activemq.artemis.core.server.MessageReference;

public class FakeConsumer implements Consumer {

   private HandleStatus statusToReturn = HandleStatus.HANDLED;

   private HandleStatus newStatus;

   private int delayCountdown = 0;

   private final LinkedList<MessageReference> references = new LinkedList<>();

   private final Filter filter;

   public FakeConsumer() {
      filter = null;
   }

   @Override
   public void failed(Throwable t) {
      // no op
   }

   public FakeConsumer(final Filter filter) {
      this.filter = filter;
   }

   @Override
   public Filter getFilter() {
      return filter;
   }

   @Override
   public String debug() {
      return toString();
   }

   public synchronized MessageReference waitForNextReference(long timeout) {
      while (references.isEmpty() && timeout > 0) {
         long start = System.currentTimeMillis();
         try {
            wait();
         } catch (InterruptedException e) {
         }
         timeout -= System.currentTimeMillis() - start;
      }

      if (timeout <= 0) {
         throw new IllegalStateException("Timed out waiting for reference");
      }

      return references.removeFirst();
   }

   public synchronized void setStatusImmediate(final HandleStatus newStatus) {
      statusToReturn = newStatus;
   }

   public synchronized void setStatusDelayed(final HandleStatus newStatus, final int numReferences) {
      this.newStatus = newStatus;

      delayCountdown = numReferences;
   }

   @Override
   public long sequentialID() {
      return 0;
   }

   public synchronized List<MessageReference> getReferences() {
      return references;
   }

   public synchronized void clearReferences() {
      references.clear();
   }

   @Override
   public synchronized HandleStatus handle(final MessageReference reference) {
      try {
         if (statusToReturn == HandleStatus.BUSY) {
            return HandleStatus.BUSY;
         }

         if (filter != null) {
            if (filter.match(reference.getMessage())) {
               references.addLast(reference);
               reference.getQueue().referenceHandled(reference);
               notify();

               return HandleStatus.HANDLED;
            } else {
               return HandleStatus.NO_MATCH;
            }
         }

         if (newStatus != null) {
            if (delayCountdown == 0) {
               statusToReturn = newStatus;

               newStatus = null;
            } else {
               delayCountdown--;
            }
         }

         if (statusToReturn == HandleStatus.HANDLED) {
            reference.getQueue().referenceHandled(reference);
            references.addLast(reference);
            notify();
         }

         return statusToReturn;
      } catch (Exception e) {
         e.printStackTrace();
         throw new IllegalStateException(e.getMessage(), e);
      }
   }

   @Override
   public void proceedDeliver(MessageReference ref) throws Exception {
      // no op
   }

   @Override
   public String toManagementString() {
      return toString();
   }

   @Override
   public void disconnect() {
      //To change body of implemented methods use File | Settings | File Templates.
   }

   @Override
   public List<MessageReference> getDeliveringMessages() {
      return Collections.emptyList();
   }

   @Override
   public String toString() {
      if (filter != null) {
         return filter + ", " + super.toString();
      }
      return super.toString();
   }
}
