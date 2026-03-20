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

import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.data.MessageData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InsertMessageStatement extends BatchableStatement<MessageData> {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public InsertMessageStatement(DatabaseProvider databaseProvider,
                                 Connection connection,
                                 int expectedSize) throws SQLException {
      super(databaseProvider, connection, getSQL(databaseProvider), expectedSize);
   }

   private static String getSQL(DatabaseProvider databaseProvider) {
      String tableName = databaseProvider.getSqlProvider().getMessages();
      String sql = databaseProvider.getSqlProvider().insertMessages(tableName);
      assert sql != null;
      return sql;
   }

   @Override
   protected void doOne(MessageData task) throws Exception {
      ActiveMQBuffer buffer = task.messageBufferSupplier.get();
      preparedStatement.setLong(1, task.messageID);
      preparedStatement.setBinaryStream(2, blobInputStream(buffer));
      if (task.getTx() != null && task.getTx().isPreparing()) {
         preparedStatement.setLong(3, task.getTx().getId());
      } else {
         preparedStatement.setNull(3, Types.NUMERIC);
      }
      preparedStatement.setInt(4, task.memoryEstimate);
      preparedStatement.setString(5, task.isLarge ? "Y" : "N");
      ActiveMQBuffer largeBody = task.largeBodySupplier != null ? task.largeBodySupplier.get() : null;
      if (largeBody != null) {
         largeBody.readerIndex(0);
         preparedStatement.setBinaryStream(6, blobInputStream(largeBody));
      } else {
         preparedStatement.setBytes(6, null);
      }
      preparedStatement.setString(7, task.isCore ? "Y" : "N");
   }

}
