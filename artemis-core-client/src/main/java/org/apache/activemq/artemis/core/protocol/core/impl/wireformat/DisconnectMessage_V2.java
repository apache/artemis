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
import org.apache.activemq.artemis.api.core.SimpleString;

public class DisconnectMessage_V2 extends DisconnectMessage {

   private SimpleString scaleDownNodeID;

   public DisconnectMessage_V2(final SimpleString nodeID, final String scaleDownNodeID) {
      super(DISCONNECT_V2);

      this.nodeID = nodeID;

      this.scaleDownNodeID = SimpleString.of(scaleDownNodeID);
   }

   public DisconnectMessage_V2() {
      super(DISCONNECT_V2);
   }

   public SimpleString getScaleDownNodeID() {
      return scaleDownNodeID;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      super.encodeRest(buffer);
      buffer.writeNullableSimpleString(scaleDownNodeID);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      super.decodeRest(buffer);
      scaleDownNodeID = buffer.readNullableSimpleString();
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", nodeID=" + nodeID);
      sb.append(", scaleDownNodeID=" + scaleDownNodeID);
      return sb.toString();
   }

   @Override
   public int hashCode() {
      return super.hashCode() + Objects.hashCode(scaleDownNodeID);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof DisconnectMessage_V2 other)) {
         return false;
      }

      return Objects.equals(scaleDownNodeID, other.scaleDownNodeID);
   }
}