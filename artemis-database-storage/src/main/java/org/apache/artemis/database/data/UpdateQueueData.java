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

public class UpdateQueueData extends DBData {

   public final long id;
   public final long addressId;
   public final String name;
   public final String filter;
   public final boolean isMulticast;
   public final boolean isAnycast;
   public final String queueConfigJson;

   public UpdateQueueData(long addressId,
                          long id,
                          String name,
                          String filter,
                          boolean isMulticast,
                          boolean isAnycast,
                          String queueConfigJson,
                          IOCompletion context) {
      super(context);
      this.addressId = addressId;
      this.id = id;
      this.name = name;
      this.filter = filter;
      this.isMulticast = isMulticast;
      this.isAnycast = isAnycast;
      this.queueConfigJson = queueConfigJson;
   }

   @Override
   public void perform(DataWorker worker) {
      worker.updateQueueStatement.addElement(this, context);
   }

   @Override
   public String toString() {
      return "UpdateQueueData{" + "id=" + id + ", addressId=" + addressId + ", name='" + name + '\'' + ", filter='" + filter + '\'' + ", isMulticast=" + isMulticast + ", isAnycast=" + isAnycast + '}';
   }
}
