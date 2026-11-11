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

import javax.transaction.xa.Xid;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.utils.XidCodecSupport;

public class SessionXAGetInDoubtXidsResponseMessage extends PacketImpl {

   private List<Xid> xids;

   public SessionXAGetInDoubtXidsResponseMessage(final List<Xid> xids) {
      super(SESS_XA_INDOUBT_XIDS_RESP);

      this.xids = xids;
   }

   public SessionXAGetInDoubtXidsResponseMessage() {
      super(SESS_XA_INDOUBT_XIDS_RESP);
   }

   @Override
   public boolean isResponse() {
      return true;
   }

   public List<Xid> getXids() {
      return xids;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeInt(xids.size());

      for (Xid xid : xids) {
         XidCodecSupport.encodeXid(xid, buffer);
      }
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      int len = buffer.readInt();
      xids = new ArrayList<>(len);
      for (int i = 0; i < len; i++) {
         Xid xid = XidCodecSupport.decodeXid(buffer);

         xids.add(xid);
      }
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", xids=" + xids);
      sb.append("]");
      return sb.toString();
   }

   @Override
   public int hashCode() {
      return super.hashCode() + Objects.hashCode(xids);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof SessionXAGetInDoubtXidsResponseMessage other)) {
         return false;
      }

      return Objects.equals(xids, other.xids);
   }
}
