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

public class PostgreSQLProvider extends SQLProvider {

   @Override
   public String createMessages(String tableName) {
      return String.format("CREATE TABLE IF NOT EXISTS %s(MESSAGE_ID BIGINT NOT NULL, TX_ID BIGINT, MESSAGE_RECORD BYTEA, MEMORY_ESTIMATE INT, IS_LARGE CHAR(1) NOT NULL, LARGE_BODY BYTEA, IS_CORE CHAR(1) NOT NULL, PRIMARY KEY (MESSAGE_ID))", tableName);
   }

   @Override
   public String createReferences(String tableName) {
      return String.format("CREATE TABLE IF NOT EXISTS %s(MESSAGE_ID BIGINT NOT NULL, QUEUE_ID BIGINT NOT NULL, PAGED CHAR NOT NULL, TX_ID BIGINT, ACK_TX_ID BIGINT, PRIMARY KEY (MESSAGE_ID, QUEUE_ID))", tableName);
   }

   @Override
   public String createAddress(String tableName) {
      return String.format("CREATE TABLE IF NOT EXISTS %s(ADDRESS_ID BIGINT NOT NULL, ADDRESS_NAME VARCHAR(255) NOT NULL, IS_MULTICAST CHAR, IS_ANYCAST CHAR, PRIMARY KEY (ADDRESS_ID))", tableName);
   }

   @Override
   public String createQueue(String tableName) {
      return String.format("CREATE TABLE IF NOT EXISTS %s(QUEUE_ID BIGINT NOT NULL, ADDRESS_ID BIGINT NOT NULL, QUEUE_NAME VARCHAR(255), IS_MULTICAST CHAR, IS_ANYCAST CHAR, FILTER_STRING BYTEA, QUEUE_CONFIG BYTEA, PRIMARY KEY (QUEUE_ID))", tableName);
   }

   @Override
   public String createPage(String tableName) {
      return String.format("CREATE TABLE IF NOT EXISTS %s(ADDRESS_ID BIGINT NOT NULL, PAGE_ID BIGINT NOT NULL, PAGE_NR BIGINT NOT NULL, MESSAGE_ID BIGINT NOT NULL, TX_ID BIGINT, MESSAGE_RECORD BYTEA, PRIMARY KEY (ADDRESS_ID, PAGE_ID, PAGE_NR))", tableName);
   }

   @Override
   public String createPageReferences(String tableName) {
      return String.format("CREATE TABLE IF NOT EXISTS %s(ADDRESS_ID BIGINT NOT NULL, PAGE_ID BIGINT NOT NULL, PAGE_NR BIGINT NOT NULL, QUEUE_ID BIGINT NOT NULL, PRIMARY KEY (ADDRESS_ID, PAGE_ID, PAGE_NR, QUEUE_ID))", tableName);
   }

   @Override
   public String createGenericData(String tableName) {
      return String.format("CREATE TABLE IF NOT EXISTS %s(ID BIGINT NOT NULL, RECORD_TYPE SMALLINT NOT NULL, TX_ID BIGINT, DATA_RECORD BYTEA, PRIMARY KEY (ID))", tableName);
   }

   @Override
   public String createPrepTx(String tableName) {
      return String.format("CREATE TABLE IF NOT EXISTS %s(TX_ID BIGINT NOT NULL, XID BYTEA, PRIMARY KEY (TX_ID))", tableName);
   }
}
