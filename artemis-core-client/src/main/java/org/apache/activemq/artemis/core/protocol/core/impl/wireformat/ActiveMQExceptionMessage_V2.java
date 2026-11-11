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
package org.apache.activemq.artemis.core.protocol.core.impl.wireformat;

import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.utils.DataConstants;

public class ActiveMQExceptionMessage_V2 extends ActiveMQExceptionMessage {

   private long correlationID;



   public ActiveMQExceptionMessage_V2(final long correlationID, final ActiveMQException exception) {
      super(exception);
      this.correlationID = correlationID;
   }

   public ActiveMQExceptionMessage_V2() {
      super();
   }

   @Override
   public boolean isResponse() {
      return true;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      super.encodeRest(buffer);
      buffer.writeLong(correlationID);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      super.decodeRest(buffer);
      if (buffer.readableBytes() >= DataConstants.SIZE_LONG) {
         correlationID = buffer.readLong();
      }
   }

   @Override
   public final boolean isResponseAsync() {
      return true;
   }

   @Override
   public long getCorrelationID() {
      return this.correlationID;
   }

   @Override
   public int hashCode() {
      return super.hashCode() + Objects.hashCode(correlationID);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof ActiveMQExceptionMessage_V2 other)) {
         return false;
      }
      return correlationID == other.correlationID;
   }
}
