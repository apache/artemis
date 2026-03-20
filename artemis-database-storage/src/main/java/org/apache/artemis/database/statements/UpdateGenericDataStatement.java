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
import java.sql.Types;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.data.UpdateGenericData;

public class UpdateGenericDataStatement extends BatchableStatement<UpdateGenericData> {

   public UpdateGenericDataStatement(DatabaseProvider databaseProvider,
                                     Connection connection,
                                     int expectedSize) throws SQLException {
      super(databaseProvider, connection, getSQL(databaseProvider, databaseProvider.getSqlProvider().getBrokerData()), expectedSize);
   }

   public UpdateGenericDataStatement(DatabaseProvider databaseProvider,
                                     Connection connection,
                                     int expectedSize,
                                     String tableName) throws SQLException {
      super(databaseProvider, connection, getSQL(databaseProvider, tableName), expectedSize);
   }

   private static String getSQL(DatabaseProvider databaseProvider, String tableName) {
      String sql = databaseProvider.getSqlProvider().updateGenericData(tableName);
      assert sql != null;
      return sql;
   }

   @Override
   protected void doOne(UpdateGenericData task) throws Exception {
      if (task.dataSupplier != null) {
         ActiveMQBuffer buffer = task.dataSupplier.get();
         preparedStatement.setBinaryStream(1, blobInputStream(buffer));
      } else {
         preparedStatement.setNull(1, Types.VARBINARY);
      }
      if (task.txId != null) {
         preparedStatement.setLong(2, task.txId);
      } else {
         preparedStatement.setNull(2, Types.NUMERIC);
      }
      preparedStatement.setLong(3, task.id);
   }
}
