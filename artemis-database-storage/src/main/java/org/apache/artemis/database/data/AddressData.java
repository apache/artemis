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

public class AddressData extends DBData {

   public long id;
   public String address;
   public boolean isMulticast;
   public boolean isAnycast;

   public AddressData(long id, String address, boolean isMulticast, boolean isAnycast, IOCompletion context) {
      super(context);
      this.id = id;
      this.address = address;
      this.isMulticast = isMulticast;
      this.isAnycast = isAnycast;
   }

   public AddressData(long id, String address, boolean isMulticast, boolean isAnycast) {
      this(id, address, isMulticast, isAnycast, null);
   }

   @Override
   public void perform(DataWorker worker) {
      worker.insertAddressStatement.addElement(this, context);
   }

   @Override
   public String toString() {
      return "AddressData{" + "id=" + id + ", address='" + address + '\'' + ", isMulticast=" + isMulticast + ", isAnycast=" + isAnycast + '}';
   }
}
