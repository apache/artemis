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
package org.apache.activemq.artemis.core.transaction;

import org.apache.activemq.artemis.json.JsonArrayBuilder;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import javax.transaction.xa.Xid;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.impl.RefsOperation;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.utils.JsonLoader;

public abstract class TransactionDetail {

   public static final String KEY_CREATION_TIME = "creation_time";

   public static final String KEY_XID_AS_BASE64 = "xid_as_base64";

   public static final String KEY_XID_FORMAT_ID = "xid_format_id";

   public static final String KEY_XID_GLOBAL_TXID = "xid_global_txid";

   public static final String KEY_XID_BRANCH_QUAL = "xid_branch_qual";

   public static final String KEY_TX_RELATED_MESSAGES = "tx_related_messages";

   public static final String KEY_MSG_OP_TYPE = "message_operation_type";

   public static final String KEY_MSG_BODY_BUFFER = "message_body";

   public static final String KEY_MSG_TYPE = "message_type";

   public static final String KEY_MSG_PROPERTIES = "message_properties";

   private final Xid xid;
   private final Transaction transaction;
   private final Long creationTime;

   public TransactionDetail(Xid xid, Transaction tx, Long creation) {
      this.xid = xid;
      this.transaction = tx;
      this.creationTime = creation;
   }

   public JsonObject toJSON() throws Exception {
      DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
      JsonObjectBuilder detailJson = JsonLoader.createObjectBuilder().add(KEY_CREATION_TIME, dateFormat.format(new Date(this.creationTime))).add(KEY_XID_AS_BASE64, XidImpl.toBase64String(this.xid)).add(KEY_XID_FORMAT_ID, this.xid.getFormatId()).add(KEY_XID_GLOBAL_TXID, new String(this.xid.getGlobalTransactionId())).add(KEY_XID_BRANCH_QUAL, new String(this.xid.getBranchQualifier()));

      JsonArrayBuilder msgsJson = JsonLoader.createArrayBuilder();

      List<TransactionOperation> txops = null;

      if (this.transaction != null) {
         txops = this.transaction.getAllOperations();
      }
      if (txops == null) {
         return detailJson.build();
      }

      for (TransactionOperation op : txops) {
         String opClassName = op.getClass().getName();
         String opType = null;
         if (opClassName.equals("org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl$AddOperation")) {
            opType = "(+) send";
         } else if (opClassName.equals(RefsOperation.class.getName())) {
            opType = "(-) receive";
         }

         List<MessageReference> msgs = op.getRelatedMessageReferences();
         if (msgs == null) {
            continue;
         }

         for (MessageReference ref : msgs) {
            JsonObjectBuilder msgJson = JsonLoader.createObjectBuilder();

            msgJson.add(KEY_MSG_OP_TYPE, opType);

            Message msg = ref.getMessage().copy();

            msgJson.add(KEY_MSG_TYPE, decodeMessageType(msg));
            JsonUtil.addToObject(KEY_MSG_PROPERTIES, decodeMessageProperties(msg), msgJson);
            msgsJson.add(msgJson);
         }
      }
      detailJson.add(KEY_TX_RELATED_MESSAGES, msgsJson);
      return detailJson.build();
   }

   public abstract String decodeMessageType(Message msg);

   public abstract Map<String, Object> decodeMessageProperties(Message msg);
}
