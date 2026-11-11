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
package org.apache.activemq.artemis.utils;

import javax.transaction.xa.Xid;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;

import static org.apache.activemq.artemis.utils.BufferHelper.safeReadBytes;

public class XidCodecSupport {

   public static void encodeXid(final Xid xid, final ActiveMQBuffer out) {
      out.writeInt(xid.getFormatId());
      out.writeInt(xid.getBranchQualifier().length);
      out.writeBytes(xid.getBranchQualifier());
      out.writeInt(xid.getGlobalTransactionId().length);
      out.writeBytes(xid.getGlobalTransactionId());
   }

   public static Xid decodeXid(final ActiveMQBuffer in) {
      int formatID = in.readInt();
      byte[] bq = safeReadBytes(in);
      byte[] gtxid = safeReadBytes(in);
      return new XidImpl(bq, formatID, gtxid);
   }

   public static int getXidEncodeLength(final Xid xid) {
      return DataConstants.SIZE_INT * 3 + xid.getBranchQualifier().length + xid.getGlobalTransactionId().length;
   }

}
