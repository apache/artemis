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
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.data.GenericData;
import org.apache.artemis.database.data.TXStatus;

public class InsertGenericDataStatement extends BatchableStatement<GenericData> {

   public InsertGenericDataStatement(DatabaseProvider databaseProvider,
                                     Connection connection,
                                     int expectedSize,
                                     Consumer<GenericData> duplicateReaper) throws SQLException {
      super(databaseProvider, connection, getSQL(databaseProvider, databaseProvider.getSqlProvider().getBrokerData()), expectedSize, duplicateReaper);
   }

   public InsertGenericDataStatement(DatabaseProvider databaseProvider,
                                     Connection connection,
                                     int expectedSize,
                                     String tableName,
                                     Consumer<GenericData> duplicateReaper) throws SQLException {
      super(databaseProvider, connection, getSQL(databaseProvider, tableName), expectedSize, duplicateReaper);
   }

   private static String getSQL(DatabaseProvider databaseProvider, String tableName) {
      String sql = databaseProvider.getSqlProvider().insertGenericData(tableName);
      assert sql != null;
      return sql;
   }

   @Override
   protected void doOne(GenericData task) throws Exception {
      preparedStatement.setLong(1, task.id);
      preparedStatement.setShort(2, task.recordType);
      if (task.getTx() != null && task.getTx().getStatus() == TXStatus.preparing) {
         preparedStatement.setLong(3, task.getTx().getId());
      } else {
         preparedStatement.setNull(3, Types.NUMERIC);
      }
      if (task.dataSupplier != null) {
         ActiveMQBuffer buffer = task.dataSupplier.get();
         preparedStatement.setBinaryStream(4, blobInputStream(buffer));
      } else {
         preparedStatement.setNull(4, Types.VARBINARY);
      }
   }
}
