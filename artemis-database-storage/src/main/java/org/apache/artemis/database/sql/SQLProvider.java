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

package org.apache.artemis.database.sql;

import java.util.List;
import java.util.StringJoiner;

public abstract class SQLProvider {

   public String getSelectCount(String tableName) {
      return String.format("SELECT COUNT(*) FROM %s", tableName);
   }

   public String getMessages() {
      return "DB_MESSAGES";
   }

   public String getRefs() {
      return "DB_REFERENCES";
   }

   public String getAddress() {
      return "DB_ADDRESS";
   }

   public String getQueue() {
      return "DB_QUEUE";
   }

   public String getBrokerData() {
      return "DB_BROKER_DATA";
   }

   public String getConfigData() {
      return "DB_CONFIG_DATA";
   }

   public String getPrepTx() {
      return "DB_PREP_TX";
   }

   public abstract String createMessages(String tableName);

   public abstract String createReferences(String tableName);

   public abstract String createAddress(String tableName);

   public abstract String createQueue(String tableName);

   public abstract String createPage(String tableName);

   public abstract String createPageReferences(String tableName);

   public abstract String createGenericData(String tableName);

   public abstract String createPrepTx(String tableName);

   public String insertPrepTx(String tableName) {
      return String.format("INSERT INTO %s (TX_ID, XID) VALUES (?,?)", tableName);
   }

   public String deletePrepTx(String tableName) {
      return String.format("DELETE FROM %s WHERE TX_ID=?", tableName);
   }

   public String selectPrepTx(String tableName) {
      return String.format("SELECT TX_ID, XID FROM %s ORDER BY TX_ID", tableName);
   }

   public String insertMessages(String tableName) {
      return String.format("INSERT INTO %s (MESSAGE_ID, MESSAGE_RECORD, TX_ID, MEMORY_ESTIMATE, IS_LARGE, LARGE_BODY, IS_CORE) VALUES (?,?,?,?,?,?,?)", tableName);
   }

   public String updateMessageTx(String tableName) {
      return String.format("UPDATE %s SET TX_ID = NULL WHERE MESSAGE_ID = ?", tableName);
   }

   public String updateReferenceTx(String tableName) {
      return String.format("UPDATE %s SET TX_ID = NULL WHERE MESSAGE_ID = ? AND QUEUE_ID = ?", tableName);
   }

   public String insertReferences(String tableName) {
      return String.format("INSERT INTO %s (MESSAGE_ID, QUEUE_ID, PAGED, TX_ID, ACK_TX_ID) VALUES (?,?,?,?,?)", tableName);
   }

   public String updateReferenceAckTx(String tableName) {
      return String.format("UPDATE %s SET ACK_TX_ID = ? WHERE QUEUE_ID = ? AND MESSAGE_ID = ?", tableName);
   }

   public String deleteReferences(String tableName) {
      return String.format("DELETE FROM %s WHERE QUEUE_ID=? AND MESSAGE_ID=?", tableName);
   }

   public String deleteMessages(String tableName, String refs) {
      return String.format("DELETE FROM %s WHERE MESSAGE_ID=? AND NOT EXISTS (SELECT 1 FROM %s WHERE %s.MESSAGE_ID = %s.MESSAGE_ID)", tableName, refs, refs, tableName);
   }

   public String insertAddress(String tableName) {
      return String.format("INSERT INTO %s (ADDRESS_ID, ADDRESS_NAME, IS_MULTICAST, IS_ANYCAST) VALUES (?, ?, ?, ?)", tableName);
   }

   public String deleteAddress(String tableName) {
      return String.format("DELETE FROM %s WHERE ADDRESS_ID=?", tableName);
   }

   public String insertQueue(String tableName) {
      return String.format("INSERT INTO %s (QUEUE_ID, ADDRESS_ID, QUEUE_NAME, IS_MULTICAST, IS_ANYCAST, FILTER_STRING, QUEUE_CONFIG) VALUES (?, ?, ?, ?, ?, ?, ?)", tableName);
   }

   public String updateQueue(String tableName) {
      return String.format("UPDATE %s SET ADDRESS_ID=?, QUEUE_NAME=?, IS_MULTICAST=?, IS_ANYCAST=?, FILTER_STRING=?, QUEUE_CONFIG=? WHERE QUEUE_ID=?", tableName);
   }

   public String deleteQueue(String tableName) {
      return String.format("DELETE FROM %s WHERE QUEUE_ID=?", tableName);
   }

   public String reloadMessages(String messagesTable, String referencesTable) {
      return String.format("SELECT a.MESSAGE_ID, a.MESSAGE_RECORD, a.MEMORY_ESTIMATE, a.IS_LARGE, a.LARGE_BODY, a.IS_CORE FROM %s a WHERE EXISTS (SELECT 1 FROM %s b WHERE b.MESSAGE_ID = a.MESSAGE_ID AND b.PAGED='N') AND TX_ID IS NULL ORDER BY a.MESSAGE_ID", messagesTable, referencesTable);
   }

   // returning only the messages that have at least one PAGED = "N"
   public String orphanedMessages(String messagesTable, String referencesTable) {
      return String.format("SELECT a.MESSAGE_ID, a.MESSAGE_RECORD, a.MEMORY_ESTIMATE FROM %s a WHERE a.MESSAGE_ID NOT IN (SELECT b.MESSAGE_ID FROM %s b WHERE a.MESSAGE_ID = b.MESSAGE_ID) ORDER BY MESSAGE_ID", messagesTable, referencesTable);
   }

   public String deleteOrphanedMessages(String messagesTable, String referencesTable) {
      return String.format("DELETE FROM %s WHERE NOT EXISTS (SELECT 1 FROM %s WHERE %s.MESSAGE_ID = %s.MESSAGE_ID)", messagesTable, referencesTable, referencesTable, messagesTable);
   }

   public String deliverPendingMessages(String referencesTable) {
      //return String.format("SELECT a.MESSAGE_ID MESSAGE_ID, a.MESSAGE_RECORD MESSAGE_RECORD, a.MEMORY_ESTIMATE MEMORY_ESTIMATE, b.PAGED PAGED FROM %s a, %s b WHERE a.MESSAGE_ID = b.MESSAGE_ID AND b.QUEUE_ID=? AND b.PAGED='Y' ORDER BY a.MESSAGE_ID", messagesTable, referencesTable);
      return String.format("SELECT DR.MESSAGE_ID FROM %s DR WHERE DR.QUEUE_ID=? AND DR.PAGED='Y' AND TX_ID IS NULL ORDER BY DR.MESSAGE_ID", referencesTable);
   }

   public String deliverPendingMessagesJoinFetch(String messagesTable, String referencesTable) {
      return deliverPendingMessagesJoinFetch(messagesTable, referencesTable, null);
   }

   public String deliverPendingMessagesJoinFetch(String messagesTable, String referencesTable, String extraClause) {
      String extraClauseAddition = extraClause != null ? (" AND " + extraClause) : "";
      return "SELECT DR.MESSAGE_ID, DM.MESSAGE_RECORD, DM.MEMORY_ESTIMATE, DM.IS_LARGE, DM.LARGE_BODY, DM.IS_CORE FROM " + referencesTable + " DR, " + messagesTable + " DM WHERE DR.QUEUE_ID=? AND DR.PAGED='Y' AND DR.MESSAGE_ID = DM.MESSAGE_ID AND DM.TX_ID IS NULL " + extraClauseAddition + " ORDER BY DR.MESSAGE_ID";
   }

   public String updatePendingDelivery(String tableName) {
      return String.format("UPDATE %s SET PAGED='N' WHERE QUEUE_ID=? AND MESSAGE_ID=?", tableName);
   }

   public String selectReferences(String tableName) {
      return String.format("SELECT MESSAGE_ID, QUEUE_ID, PAGED FROM %s WHERE PAGED='N' AND TX_ID IS NULL ORDER BY MESSAGE_ID, QUEUE_ID", tableName);
   }

   public String selectMessagesWithReferences(String messagesTable, String referencesTable) {
      return selectMessagesWithReferences(messagesTable, referencesTable, null);
   }

   public String selectMessagesWithReferences(String messagesTable, String referencesTable, String whereClause) {
      String whereFragment = (whereClause != null && !whereClause.isEmpty()) ? " WHERE " + whereClause : "";
      return String.format("SELECT a.MESSAGE_ID, a.TX_ID, a.MEMORY_ESTIMATE, a.MESSAGE_RECORD, b.QUEUE_ID, b.PAGED, a.IS_LARGE, a.LARGE_BODY, a.IS_CORE, b.ACK_TX_ID FROM %s a LEFT JOIN %s b ON a.MESSAGE_ID = b.MESSAGE_ID%s ORDER BY a.MESSAGE_ID, b.QUEUE_ID", messagesTable, referencesTable, whereFragment);
   }

   public String whereClauseLoadTXMessages() {
      return "(a.TX_ID is not null) or (b.ACK_Tx_ID is not null)";
   }

   public String whereClauseLoadTXGenericData() {
      return "TX_ID IS NOT NULL";
   }

   public String selectAddress(String tableName) {
      return String.format("SELECT ADDRESS_ID, ADDRESS_NAME, IS_MULTICAST, IS_ANYCAST FROM %s ORDER BY ADDRESS_ID", tableName);
   }

   public String selectQueue(String tableName) {
      return String.format("SELECT QUEUE_ID, ADDRESS_ID, QUEUE_NAME, IS_MULTICAST, IS_ANYCAST, FILTER_STRING, QUEUE_CONFIG FROM %s ORDER BY QUEUE_ID", tableName);
   }

   public String selectMessagesBlob(String tableName, List<String> ids) {
      StringJoiner inClause = new StringJoiner(",");
      ids.forEach(inClause::add);
      String query = String.format("SELECT MESSAGE_ID, MESSAGE_RECORD, MEMORY_ESTIMATE, IS_LARGE, LARGE_BODY, IS_CORE FROM %s WHERE MESSAGE_ID IN (%s) ORDER BY MESSAGE_ID", tableName, inClause);
      return query;
   }

   public String insertGenericData(String tableName) {
      return String.format("INSERT INTO %s (ID, RECORD_TYPE, TX_ID, DATA_RECORD) VALUES (?,?,?,?)", tableName);
   }

   public String updateGenericData(String tableName) {
      return String.format("UPDATE %s SET DATA_RECORD=?, TX_ID=? WHERE ID=?", tableName);
   }

   public String updateGenericDataTx(String tableName) {
      return String.format("UPDATE %s SET TX_ID = NULL WHERE ID = ?", tableName);
   }

   public String deleteGenericData(String tableName) {
      return String.format("DELETE FROM %s WHERE ID=?", tableName);
   }

   public String selectGenericData(String tableName) {
      return selectGenericData(tableName, "TX_ID IS NULL");
   }

   public String selectGenericData(String tableName, String whereClause) {
      String whereFragment = (whereClause != null && !whereClause.isEmpty()) ? " WHERE " + whereClause : "";
      return String.format("SELECT ID, RECORD_TYPE, TX_ID, DATA_RECORD FROM %s%s ORDER BY ID", tableName, whereFragment);
   }

   public String countPagedMessages(Boolean paged) {
      String messageTable = getMessages();
      String referencesTable = getRefs();
      String pagedClause = "";
      if (paged != null) {
         pagedClause = "AND xref.paged = '" + (paged ? "Y" : "N") + "'";
      }
      return String.format("SELECT xref.queue_id queue_id, count(*) msg_count, sum(dm.memory_estimate) mem_estimate " + "FROM %s xref, %s dm " + "WHERE dm.message_id = xref.message_id %s " + "GROUP BY xref.queue_id", referencesTable, messageTable, pagedClause);
   }
}
