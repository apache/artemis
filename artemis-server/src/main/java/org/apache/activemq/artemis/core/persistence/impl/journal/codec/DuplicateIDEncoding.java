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

import java.nio.ByteBuffer;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.UUID;

public class DuplicateIDEncoding implements EncodingSupport {

   public SimpleString address;

   public byte[] duplID;

   public DuplicateIDEncoding(final SimpleString address, final byte[] duplID) {
      this.address = Objects.requireNonNull(address);
      this.duplID = Objects.requireNonNull(duplID);
   }

   public DuplicateIDEncoding() {
   }

   @Override
   public void decode(final ActiveMQBuffer buffer) {
      address = buffer.readSimpleString();

      int size = buffer.readInt();

      duplID = new byte[size];

      buffer.readBytes(duplID);
   }

   @Override
   public void encode(final ActiveMQBuffer buffer) {
      buffer.writeSimpleString(address);

      buffer.writeInt(duplID.length);

      buffer.writeBytes(duplID);
   }

   @Override
   public int getEncodeSize() {
      return SimpleString.sizeofString(address) + DataConstants.SIZE_INT + duplID.length;
   }

   @Override
   public String toString() {
      // this would be useful when testing. Most tests on the testsuite will use a SimpleString on the duplicate ID
      // and this may be useful to validate the journal on those tests
      // You may uncomment these two lines on that case and replcate the toString for the PrintData

      // SimpleString simpleStr = SimpleString.of(duplID);
      // return "DuplicateIDEncoding [address=" + address + ", duplID=" + simpleStr + "]";

      String bridgeRepresentation = null;

      // The bridge will generate IDs on these terms:
      // This will make them easier to read
      if (address != null && address.toString().startsWith("BRIDGE") && duplID.length == 24) {
         try {
            ByteBuffer buff = ByteBuffer.wrap(duplID);

            // 16 for UUID
            byte[] bytesUUID = new byte[16];

            buff.get(bytesUUID);

            UUID uuid = new UUID(UUID.TYPE_TIME_BASED, bytesUUID);

            long id = buff.getLong();
            bridgeRepresentation = "nodeUUID=" + uuid.toString() + " messageID=" + id;
         } catch (Throwable ignored) {
            bridgeRepresentation = null;
         }
      }

      if (bridgeRepresentation != null) {
         return "DuplicateIDEncoding [address=" + address + ", duplID=" + ByteUtil.bytesToHex(duplID, 2) + " / " +
            bridgeRepresentation + "]";
      } else {
         return "DuplicateIDEncoding [address=" + address + ", duplID=" + ByteUtil.bytesToHex(duplID, 2) + "]";
      }
   }
}
