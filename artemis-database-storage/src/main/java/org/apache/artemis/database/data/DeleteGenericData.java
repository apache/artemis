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

import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.artemis.database.worker.DataWorker;

public class DeleteGenericData extends DBData {

   public final long id;
   public final boolean bindingsRecord;

   public DeleteGenericData(long id, boolean bindingsRecord, IOCompletion context) {
      super(context);
      this.id = id;
      this.bindingsRecord = bindingsRecord;
   }

   @Override
   public void perform(DataWorker worker) {
      if (bindingsRecord) {
         worker.deleteBindingsGenericDataStatement.addElement(this, context);
      } else {
         worker.deleteGenericDataStatement.addElement(this, context);
      }
   }

   @Override
   public String toString() {
      return "DeleteGenericData{" + "id=" + id + ", bindings=" + bindingsRecord + '}';
   }
}
