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

import java.util.function.Supplier;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.artemis.database.worker.DataWorker;

public class GenericData extends DBData {

   public final long id;
   public final byte recordType;
   public final Supplier<ActiveMQBuffer> dataSupplier;
   public final boolean bindingsRecord;

   public GenericData(long id,
                      byte recordType,
                      Supplier<ActiveMQBuffer> dataSupplier,
                      boolean bindingsRecord,
                      IOCompletion context) {
      super(context);
      this.id = id;
      this.recordType = recordType;
      this.dataSupplier = dataSupplier;
      this.bindingsRecord = bindingsRecord;
   }

   @Override
   public void perform(DataWorker worker) {
      if (bindingsRecord) {
         worker.insertBindingsGenericDataStatement.addElement(this, context);
      } else {
         if (tx != null && tx.getStatus() == TXStatus.twoPhaseCommitting) {
            worker.updateTXGenericDataStatement.addElement(this, context);
         } else {
            worker.insertGenericDataStatement.addElement(this, context);
         }
      }
   }

   @Override
   public String toString() {
      return "GenericData{" + "id=" + id + ", recordType=" + recordType + ", txId=" + tx + ", bindings=" + bindingsRecord + '}';
   }
}
