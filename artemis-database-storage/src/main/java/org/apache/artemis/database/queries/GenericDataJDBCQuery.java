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

package org.apache.artemis.database.queries;

import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.DatabaseTX;
import org.apache.artemis.database.data.GenericData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericDataJDBCQuery {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   Connection connection;
   DatabaseProvider databaseProvider;
   String tableName;
   boolean bindingsRecord;
   String whereClause;

   public GenericDataJDBCQuery(DatabaseProvider databaseProvider, Connection connection, String tableName, boolean bindingsRecord) {
      this(databaseProvider, connection, tableName, bindingsRecord, null);
   }

   public GenericDataJDBCQuery(DatabaseProvider databaseProvider, Connection connection, String tableName, boolean bindingsRecord, String whereClause) {
      this.databaseProvider = databaseProvider;
      this.connection = connection;
      this.tableName = tableName;
      this.bindingsRecord = bindingsRecord;
      this.whereClause = whereClause;
   }

   public void query(Consumer<GenericData> consumer) throws Exception {
      String sql = whereClause != null
         ? databaseProvider.getSqlProvider().selectGenericData(tableName, whereClause)
         : databaseProvider.getSqlProvider().selectGenericData(tableName);
      query(consumer, sql);
   }

   public void query(Consumer<GenericData> consumer, String sql) throws Exception {
      Statement statement = connection.createStatement();
      statement.setFetchSize(500);
      try (ResultSet resultSet = statement.executeQuery(sql)) {
         while (resultSet.next()) {
            long id = resultSet.getLong(1);
            byte recordType = (byte) resultSet.getShort(2);
            long txIdValue = resultSet.getLong(3);
            Long txId = resultSet.wasNull() ? null : txIdValue;
            DatabaseTX tx = null;
            if (txId != null) {
               tx = new DatabaseTX(txId);
            }

            byte[] bytes = resultSet.getBytes(4);
            ActiveMQBuffer buffer = null;
            if (bytes != null) {
               buffer = ActiveMQBuffers.wrappedBuffer(bytes);
            }

            final ActiveMQBuffer finalBuffer = buffer;
            GenericData data = new GenericData(id, recordType, finalBuffer != null ? () -> finalBuffer : null, bindingsRecord, null);
            data.setTx(tx);
            consumer.accept(data);
         }
      }
   }
}
