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
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;

public class ActiveMQExceptionMessage extends PacketImpl {

   protected ActiveMQException exception;


   public ActiveMQExceptionMessage(final ActiveMQException exception) {
      super(EXCEPTION);

      this.exception = exception;
   }

   public ActiveMQExceptionMessage() {
      super(EXCEPTION);
   }

   @Override
   public boolean isResponse() {
      return true;
   }

   public ActiveMQException getException() {
      return exception;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeInt(exception.getType().getCode());
      buffer.writeNullableString(exception.getMessage());
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      int code = buffer.readInt();
      String msg = buffer.readNullableString();

      exception = ActiveMQExceptionType.createException(code, msg);
   }

   @Override
   protected String getPacketString() {
      return super.getPacketString() + ", exception=" + exception;
   }

   @Override
   public int hashCode() {
      return super.hashCode() + Objects.hashCode(exception);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof ActiveMQExceptionMessage other)) {
         return false;
      }
      return Objects.equals(exception, other.exception);
   }
}
