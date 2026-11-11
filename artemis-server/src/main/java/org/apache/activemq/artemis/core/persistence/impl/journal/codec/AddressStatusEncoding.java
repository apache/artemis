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
package org.apache.activemq.artemis.core.persistence.impl.journal.codec;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.persistence.AddressQueueStatus;
import org.apache.activemq.artemis.utils.DataConstants;

public class AddressStatusEncoding implements EncodingSupport {

   private AddressQueueStatus status;

   private long addressId;

   private long id;

   public AddressStatusEncoding(long addressId, AddressQueueStatus status) {
      this.status = status;
      this.addressId = addressId;
   }

   public AddressStatusEncoding() {
   }

   public AddressQueueStatus getStatus() {
      return status;
   }

   public void setStatus(AddressQueueStatus status) {
      this.status = status;
   }

   public long getAddressId() {
      return addressId;
   }

   public void setAddressId(long addressId) {
      this.addressId = addressId;
   }

   public long getId() {
      return id;
   }

   public void setId(long id) {
      this.id = id;
   }

   @Override
   public int getEncodeSize() {
      return DataConstants.SIZE_LONG + DataConstants.SIZE_SHORT;
   }

   @Override
   public void encode(ActiveMQBuffer buffer) {
      buffer.writeLong(addressId);
      buffer.writeShort(status.id);
   }

   @Override
   public void decode(ActiveMQBuffer buffer) {
      this.addressId = buffer.readLong();
      short shortStatus = buffer.readShort();
      this.status = AddressQueueStatus.fromID(shortStatus);
   }

   @Override
   public String toString() {
      return "AddressStatusEncoding{" + "status=" + status + ", id=" + addressId + '}';
   }

}
