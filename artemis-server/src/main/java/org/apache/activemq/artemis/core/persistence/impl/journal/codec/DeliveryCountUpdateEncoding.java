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

public class DeliveryCountUpdateEncoding implements EncodingSupport {

   public long queueID;

   public int count;

   public DeliveryCountUpdateEncoding() {
      super();
   }

   public DeliveryCountUpdateEncoding(final long queueID, final int count) {
      super();
      this.queueID = queueID;
      this.count = count;
   }

   @Override
   public void decode(final ActiveMQBuffer buffer) {
      queueID = buffer.readLong();
      count = buffer.readInt();
   }

   @Override
   public void encode(final ActiveMQBuffer buffer) {
      buffer.writeLong(queueID);
      buffer.writeInt(count);
   }

   @Override
   public int getEncodeSize() {
      return 8 + 4;
   }

   @Override
   public String toString() {
      return "DeliveryCountUpdateEncoding [queueID=" + queueID + ", count=" + count + "]";
   }

}
