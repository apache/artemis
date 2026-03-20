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

package org.apache.artemis.database.statements;

import java.sql.Connection;
import java.sql.SQLException;

import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.data.DeleteGenericData;

public class DeleteGenericDataStatement extends BatchableStatement<DeleteGenericData> {

   public DeleteGenericDataStatement(DatabaseProvider databaseProvider,
                                     Connection connection,
                                     int expectedSize) throws SQLException {
      super(databaseProvider, connection, getSQL(databaseProvider, databaseProvider.getSqlProvider().getBrokerData()), expectedSize);
   }

   public DeleteGenericDataStatement(DatabaseProvider databaseProvider,
                                     Connection connection,
                                     int expectedSize,
                                     String tableName) throws SQLException {
      super(databaseProvider, connection, getSQL(databaseProvider, tableName), expectedSize);
   }

   private static String getSQL(DatabaseProvider databaseProvider, String tableName) {
      String sql = databaseProvider.getSqlProvider().deleteGenericData(tableName);
      assert sql != null;
      return sql;
   }

   @Override
   protected void doOne(DeleteGenericData task) throws Exception {
      preparedStatement.setLong(1, task.id);
   }
}
