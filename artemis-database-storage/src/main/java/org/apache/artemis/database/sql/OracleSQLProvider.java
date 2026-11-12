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

public class OracleSQLProvider extends SQLProvider {

   @Override
   public String createMessages(String tableName) {
      return String.format("CREATE TABLE %s(MESSAGE_ID NUMBER(19), TX_ID NUMBER(19), MESSAGE_RECORD BLOB, MEMORY_ESTIMATE NUMBER(10), IS_LARGE CHAR(1) NOT NULL, LARGE_BODY BLOB, IS_CORE CHAR(1) NOT NULL, PRIMARY KEY (MESSAGE_ID))", tableName);
   }

   @Override
   public String createReferences(String tableName) {
      return String.format("CREATE TABLE %s(MESSAGE_ID NUMBER(19), QUEUE_ID NUMBER(19), PAGED CHAR NOT NULL, TX_ID NUMBER(19), ACK_TX_ID NUMBER(19), PRIMARY KEY (MESSAGE_ID, QUEUE_ID))", tableName);
   }

   @Override
   public String createAddress(String tableName) {
      return String.format("CREATE TABLE %s(ADDRESS_ID NUMBER(19) NOT NULL, ADDRESS_NAME VARCHAR(255) NOT NULL, IS_MULTICAST CHAR, IS_ANYCAST CHAR, PRIMARY KEY (ADDRESS_ID))", tableName);
   }

   @Override
   public String createQueue(String tableName) {
      return String.format("CREATE TABLE %s(QUEUE_ID NUMBER(19) NOT NULL, ADDRESS_ID NUMBER(19) NOT NULL, QUEUE_NAME VARCHAR(255), IS_MULTICAST CHAR, IS_ANYCAST CHAR, FILTER_STRING BLOB, QUEUE_CONFIG BLOB, PRIMARY KEY (QUEUE_ID))", tableName);
   }

   @Override
   public String createPage(String tableName) {
      return String.format("CREATE TABLE %s(ADDRESS_ID NUMBER(19) NOT NULL, PAGE_ID NUMBER(19) NOT NULL, PAGE_NR NUMBER(19) NOT NULL, MESSAGE_ID NUMBER(19) NOT NULL, TX_ID NUMBER(19), MESSAGE_RECORD BLOB, PRIMARY KEY (ADDRESS_ID, PAGE_ID, PAGE_NR))", tableName);
   }

   @Override
   public String createPageReferences(String tableName) {
      return String.format("CREATE TABLE %s(ADDRESS_ID NUMBER(19) NOT NULL, PAGE_ID NUMBER(19) NOT NULL, PAGE_NR NUMBER(19) NOT NULL, QUEUE_ID NUMBER(19) NOT NULL, PRIMARY KEY (ADDRESS_ID, PAGE_ID, PAGE_NR, QUEUE_ID))", tableName);
   }

   @Override
   public String createGenericData(String tableName) {
      return String.format("CREATE TABLE %s(ID NUMBER(19) NOT NULL, RECORD_TYPE NUMBER(5) NOT NULL, TX_ID NUMBER(19), DATA_RECORD BLOB, PRIMARY KEY (ID))", tableName);
   }

   @Override
   public String createPrepTx(String tableName) {
      return String.format("CREATE TABLE %s(TX_ID NUMBER(19) NOT NULL, XID BLOB, PRIMARY KEY (TX_ID))", tableName);
   }
}
