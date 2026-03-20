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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.data.QueueData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InsertQueueStatement extends BatchableStatement<QueueData> {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public InsertQueueStatement(DatabaseProvider databaseProvider,
                               Connection connection,
                               int expectedSize) throws SQLException {
      super(databaseProvider, connection, getSQL(databaseProvider), expectedSize);
   }

   private static String getSQL(DatabaseProvider databaseProvider) {
      String tableName = databaseProvider.getSqlProvider().getQueue();
      String sql = databaseProvider.getSqlProvider().insertQueue(tableName);
      assert sql != null;
      return sql;
   }

   @Override
   protected void doOne(QueueData task) throws Exception {
      preparedStatement.setLong(1, task.id);
      preparedStatement.setLong(2, task.addressId);
      preparedStatement.setString(3, task.name);
      preparedStatement.setString(4, task.isMulticast ? "Y" : "N");
      preparedStatement.setString(5, task.isAnycast ? "Y" : "N");
      if (task.filter != null) {
         byte[] stringBytes = task.filter.getBytes(StandardCharsets.UTF_8);
         InputStream blobStream = new ByteArrayInputStream(stringBytes);
         preparedStatement.setBinaryStream(6, blobStream);
      } else {
         preparedStatement.setNull(6, Types.VARBINARY);
      }
      if (task.queueConfigJson != null) {
         byte[] configBytes = task.queueConfigJson.getBytes(StandardCharsets.UTF_8);
         preparedStatement.setBinaryStream(7, new ByteArrayInputStream(configBytes));
      } else {
         preparedStatement.setNull(7, Types.VARBINARY);
      }
   }

}
