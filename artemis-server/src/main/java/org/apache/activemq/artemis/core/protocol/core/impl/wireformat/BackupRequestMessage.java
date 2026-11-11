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
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;

public class BackupRequestMessage extends PacketImpl {

   private int backupSize;
   private SimpleString nodeID;
   private String journalDirectory;
   private String bindingsDirectory;
   private String largeMessagesDirectory;
   private String pagingDirectory;

   public BackupRequestMessage() {
      super(BACKUP_REQUEST);
   }

   public BackupRequestMessage(int backupSize,
                               String journalDirectory,
                               String bindingsDirectory,
                               String largeMessagesDirectory,
                               String pagingDirectory) {
      super(BACKUP_REQUEST);
      this.backupSize = backupSize;
      this.journalDirectory = journalDirectory;
      this.bindingsDirectory = bindingsDirectory;
      this.largeMessagesDirectory = largeMessagesDirectory;
      this.pagingDirectory = pagingDirectory;
   }

   public BackupRequestMessage(int backupSize, SimpleString nodeID) {
      super(BACKUP_REQUEST);
      this.backupSize = backupSize;
      this.nodeID = nodeID;
   }

   @Override
   public void encodeRest(ActiveMQBuffer buffer) {
      super.encodeRest(buffer);
      buffer.writeInt(backupSize);
      buffer.writeNullableString(journalDirectory);
      buffer.writeNullableString(bindingsDirectory);
      buffer.writeNullableString(largeMessagesDirectory);
      buffer.writeNullableString(pagingDirectory);
      buffer.writeNullableSimpleString(nodeID);
   }

   @Override
   public void decodeRest(ActiveMQBuffer buffer) {
      super.decodeRest(buffer);
      backupSize = buffer.readInt();
      journalDirectory = buffer.readNullableString();
      bindingsDirectory = buffer.readNullableString();
      largeMessagesDirectory = buffer.readNullableString();
      pagingDirectory = buffer.readNullableString();
      nodeID = buffer.readNullableSimpleString();
   }

   public int getBackupSize() {
      return backupSize;
   }

   public SimpleString getNodeID() {
      return nodeID;
   }

   public String getJournalDirectory() {
      return journalDirectory;
   }

   public String getBindingsDirectory() {
      return bindingsDirectory;
   }

   public String getLargeMessagesDirectory() {
      return largeMessagesDirectory;
   }

   public String getPagingDirectory() {
      return pagingDirectory;
   }
}
