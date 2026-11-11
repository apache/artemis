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

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.utils.DataConstants;

/**
 * Message indicating that the primary is stopping (a scheduled stop).
 * <p>
 * The backup starts the fail-over immediately after receiving this.
 */
public final class ReplicationPrimaryIsStoppingMessage extends PacketImpl {

   public enum PrimaryStopping {
      /**
       * Notifies the backup that its primary is going to stop. The backup will then NOT fail-over if it gets signals
       * from the cluster that its primary sent a disconnect.
       */
      STOP_CALLED(0),
      /**
       * Orders the backup to fail-over immediately. Meant as a follow-up message to {@link #STOP_CALLED}.
       */
      FAIL_OVER(1);
      private final int code;

      PrimaryStopping(int code) {
         this.code = code;
      }
   }

   private PrimaryStopping primaryStopping;

   public ReplicationPrimaryIsStoppingMessage() {
      super(PacketImpl.REPLICATION_SCHEDULED_FAILOVER);
   }

   public ReplicationPrimaryIsStoppingMessage(PrimaryStopping b) {
      this();
      this.primaryStopping = b;
   }

   @Override
   public int expectedEncodeSize() {
      return PACKET_HEADERS_SIZE +
         DataConstants.SIZE_INT; // buffer.writeInt(liveStopping.code);
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeInt(primaryStopping.code);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      primaryStopping = buffer.readInt() == 0 ? PrimaryStopping.STOP_CALLED : PrimaryStopping.FAIL_OVER;
   }

   /**
    * The first message is sent to turn-off the quorumManager, which in some cases would trigger a faster fail-over than
    * what would be correct.
    */
   public PrimaryStopping isFinalMessage() {
      return primaryStopping;
   }

   @Override
   protected String getPacketString() {
      return super.getPacketString() + ", primaryStopping=" + primaryStopping;
   }
}
