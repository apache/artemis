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
package org.apache.activemq.artemis.core.transaction.impl;

import javax.transaction.xa.Xid;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionDetail;

public class CoreTransactionDetail extends TransactionDetail {

   public CoreTransactionDetail(Xid xid, Transaction tx, Long creation) {
      super(xid, tx, creation);
   }

   @Override
   public String decodeMessageType(Message msg) {
      if (!(msg instanceof ICoreMessage coreMessage)) {
         return "N/A";
      }
      return switch (coreMessage.getType()) {
         case Message.DEFAULT_TYPE -> "Default"; // 0
         case Message.OBJECT_TYPE -> "ObjectMessage"; // 2
         case Message.TEXT_TYPE -> "TextMessage"; // 3
         case Message.BYTES_TYPE -> "ByteMessage"; // 4
         case Message.MAP_TYPE -> "MapMessage"; // 5
         case Message.STREAM_TYPE -> "StreamMessage"; // 6
         default -> "(Unknown Type)";
      };
   }

   @Override
   public Map<String, Object> decodeMessageProperties(Message msg) {
      return msg.toMap(256);
   }
}
