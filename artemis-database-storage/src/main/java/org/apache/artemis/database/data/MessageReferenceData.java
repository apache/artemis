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

package org.apache.artemis.database.data;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.artemis.database.worker.DataWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageReferenceData extends DBData {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public long messageID;
   public long queueID;
   public boolean paged;
   public Long txID;

   public MessageReferenceData(long messageID, long queueID, boolean paged, Long txID, IOCompletion context) {
      super(context);
      this.messageID = messageID;
      this.queueID = queueID;
      this.paged = paged;
      this.txID = txID;
   }

   @Override
   public void perform(DataWorker worker) {
      if (tx != null && tx.getStatus() == TXStatus.twoPhaseCommitting) {
         worker.updateTXReferenceStatement.addElement(this, context);
      } else {
         worker.insertReferencesStatement.addElement(this, context);
      }
   }

   @Override
   public String toString() {
      return "MessageReferenceData{" + "messageID=" + messageID + ", queueID=" + queueID + ", paged=" + paged + ", tx=" + tx + '}';
   }
}