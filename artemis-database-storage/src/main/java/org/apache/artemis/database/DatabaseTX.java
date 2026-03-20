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

package org.apache.artemis.database;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.activemq.artemis.core.journal.StorageTX;
import org.apache.artemis.database.data.DBData;
import org.apache.artemis.database.data.TXStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseTX implements StorageTX {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public List<DBData> dataList;
   long txid;
   IOCompletion context;
   TXStatus status;

   public DatabaseTX(long txid) {
      this.txid = txid;
   }

   @Override
   public void beforeIO() {
      if (status == null) {
         status = TXStatus.onePhaseCommitting;
      } else {
         if (status == TXStatus.prepared) {
            this.setStatus(TXStatus.twoPhaseCommitting);
         }
      }
   }

   @Override
   public long getId() {
      return txid;
   }

   public TXStatus getStatus() {
      return status;
   }

   public void setStatus(TXStatus status) {
      this.status = status;
   }

   public boolean isPreparing() {
      return status == TXStatus.prepared || status == TXStatus.preparing;
   }

   @Override
   public void afterIO() {
      if (context != null) {
         context.done();
      }

      if (status != null) {
         switch (status) {
            case preparing -> setStatus(TXStatus.prepared);
            case onePhaseCommitting, twoPhaseCommitting -> setStatus(TXStatus.committed);
         }
      }
   }

   @Override
   public IOCompletion getContext() {
      return this.context;
   }

   @Override
   public void setContext(IOCompletion context) {
      this.context = context;
   }

   public void addData(DBData data) {
      if (dataList == null) {
         dataList = new ArrayList<>();
      }
      data.setTx(this);
      dataList.add(data);
   }

   @Override
   public boolean isEmpty() {
      return dataList == null || dataList.isEmpty();
   }

   @Override
   public String toString() {
      return "DatabaseTX{txid=" + txid + ", status=" + status + '}';
   }
}
