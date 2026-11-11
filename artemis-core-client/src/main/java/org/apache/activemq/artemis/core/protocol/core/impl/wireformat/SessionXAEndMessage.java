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

import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.utils.XidCodecSupport;

public class SessionXAEndMessage extends PacketImpl {

   private Xid xid;

   private boolean failed;

   public SessionXAEndMessage(final Xid xid, final boolean failed) {
      super(SESS_XA_END);

      this.xid = xid;

      this.failed = failed;
   }

   public SessionXAEndMessage() {
      super(SESS_XA_END);
   }

   public boolean isFailed() {
      return failed;
   }

   public Xid getXid() {
      return xid;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      XidCodecSupport.encodeXid(xid, buffer);
      buffer.writeBoolean(failed);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      xid = XidCodecSupport.decodeXid(buffer);
      failed = buffer.readBoolean();
   }

   @Override
   public String getPacketString() {
      return super.getPacketString() + ", xid=" + xid + ", failed=" + failed + "]";
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), failed, xid);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof SessionXAEndMessage other)) {
         return false;
      }

      return failed == other.failed &&
             Objects.equals(xid, other.xid);
   }
}
