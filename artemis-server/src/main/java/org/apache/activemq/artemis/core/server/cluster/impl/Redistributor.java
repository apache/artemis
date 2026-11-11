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
package org.apache.activemq.artemis.core.server.cluster.impl;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.List;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.RefCountMessage;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.core.server.HandleStatus;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Redistributor implements Consumer {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private boolean active;

   private final PostOffice postOffice;

   private final Queue queue;

   private final long sequentialID;

   public Redistributor(final Queue queue,
                        final long sequentialID,
                        final PostOffice postOffice) {
      this.queue = queue;

      this.sequentialID = sequentialID;

      this.postOffice = postOffice;
   }

   @Override
   public long sequentialID() {
      return sequentialID;
   }

   @Override
   public Filter getFilter() {
      return null;
   }

   @Override
   public String debug() {
      return toString();
   }

   @Override
   public String toManagementString() {
      return "Redistributor[" + queue.getName() + "/" + queue.getID() + "]";
   }

   @Override
   public void disconnect() {
      //noop
   }

   public synchronized void start() {
      this.active = true;
   }

   public synchronized void stop() throws Exception {
      this.active = false;
   }

   public synchronized void close() {
      active = false;
   }

   @Override
   public synchronized HandleStatus handle(final MessageReference reference) throws Exception {
      if (!active) {
         return HandleStatus.BUSY;
      } else if (reference.getMessage().getGroupID() != null) {
         //we shouldn't redistribute with message groups return NO_MATCH so other messages can be delivered
         return HandleStatus.NO_MATCH;
      }

      if (logger.isDebugEnabled()) {
         logger.debug("Redistributing message {}, originatingQueue={}", reference.getMessage(), queue.getName());
      }

      final Pair<RoutingContext, Message> routingInfo = postOffice.redistribute(reference.getMessage(), queue);

      if (routingInfo == null) {
         logger.debug("postOffice.redistribute return null for message {}", reference);
         return HandleStatus.BUSY;
      }

      RoutingContext context = routingInfo.getA();
      Message message = routingInfo.getB();

      try {
         postOffice.processRoute(message, context, false);

         if (RefCountMessage.isRefTraceEnabled()) {
            RefCountMessage.deferredDebug(reference.getMessage(), "redistributing");
         }

         ackRedistribution(reference, context.getTransaction());
      } catch (Throwable e) {
         if (context.getTransaction() != null) {
            context.getTransaction().setAsync(true).rollback();
         }
         ActiveMQServerLogger.LOGGER.errorRedistributing(String.valueOf(this.queue.getName()), String.valueOf(message), e);
         return HandleStatus.NO_MATCH;
      }

      return HandleStatus.HANDLED;
   }

   @Override
   public void proceedDeliver(MessageReference ref) {
      // no op
   }


   @Override
   public void failed(Throwable t) {
      // no op... there's no proceedDeliver on this class
   }

   private void ackRedistribution(final MessageReference reference, final Transaction tx) throws Exception {
      reference.handled();

      queue.acknowledge(tx, reference);

      tx.commit();
   }

   @Override
   public List<MessageReference> getDeliveringMessages() {
      return Collections.emptyList();
   }

}
