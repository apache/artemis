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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.sql.SQLProvider;

public class MessageDeliveryUpdater {

   // Selecting-for-update with a cursor requires a separate connection in some database (e.g Postgres)
   // we will open a new connection while the cursor is active
   final Connection connection;
   DatabaseProvider provider;
   PreparedStatement updateDeliveryStatement;

   public MessageDeliveryUpdater(DatabaseProvider provider, Connection connection) {
      this.provider = provider;
      this.connection = connection;
   }

   public void close() throws SQLException {
      updateDeliveryStatement.close();
   }

   public void init() throws SQLException {
      SQLProvider sqlProvider = provider.getSqlProvider();
      String updateSql = sqlProvider.updatePendingDelivery(sqlProvider.getRefs());
      updateDeliveryStatement = connection.prepareStatement(updateSql);
   }

   public void updateDelivery(long queueID, long messageID) throws SQLException {
      updateDeliveryStatement.setLong(1, queueID);
      updateDeliveryStatement.setLong(2, messageID);
      updateDeliveryStatement.addBatch();
   }

   public void flush() throws SQLException {
      updateDeliveryStatement.executeBatch();
   }

   public Connection getConnection() {
      return connection;
   }

}
