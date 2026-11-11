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

import javax.transaction.xa.Xid;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.XidCodecSupport;

public class HeuristicCompletionEncoding implements EncodingSupport {

   public Xid xid;

   public boolean isCommit;

   @Override
   public String toString() {
      return "HeuristicCompletionEncoding [xid=" + xid + ", isCommit=" + isCommit + "]";
   }

   public HeuristicCompletionEncoding(final Xid xid, final boolean isCommit) {
      this.xid = xid;
      this.isCommit = isCommit;
   }

   public HeuristicCompletionEncoding() {
   }

   @Override
   public void decode(final ActiveMQBuffer buffer) {
      xid = XidCodecSupport.decodeXid(buffer);
      isCommit = buffer.readBoolean();
   }

   @Override
   public void encode(final ActiveMQBuffer buffer) {
      XidCodecSupport.encodeXid(xid, buffer);
      buffer.writeBoolean(isCommit);
   }

   @Override
   public int getEncodeSize() {
      return XidCodecSupport.getXidEncodeLength(xid) + DataConstants.SIZE_BOOLEAN;
   }
}
