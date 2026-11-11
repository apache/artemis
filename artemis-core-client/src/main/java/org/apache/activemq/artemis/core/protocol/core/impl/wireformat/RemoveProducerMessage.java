/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.protocol.core.impl.wireformat;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;

import java.util.Objects;

public class RemoveProducerMessage extends PacketImpl {

   protected int id;

   public RemoveProducerMessage(int id) {
      super(PacketImpl.REMOVE_PRODUCER);
      this.id = id;
   }

   public RemoveProducerMessage() {
      super(PacketImpl.REMOVE_PRODUCER);
   }

   public int getId() {
      return id;
   }

   public void setId(int id) {
      this.id = id;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeInt(id);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      id = buffer.readInt();
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof RemoveProducerMessage other)) {
         return false;
      }

      return Objects.equals(id, other.id);
   }

   @Override
   public int hashCode() {
      return super.hashCode() + Objects.hashCode(id);
   }
}
