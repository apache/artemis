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
import org.apache.artemis.database.data.PrepareTXData;
import org.apache.artemis.database.data.TXStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreparedTXQuery {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   Connection connection;
   DatabaseProvider databaseProvider;

   public PreparedTXQuery(DatabaseProvider databaseProvider, Connection connection) {
      this.databaseProvider = databaseProvider;
      this.connection = connection;
   }

   public void query(Consumer<PrepareTXData> consumer) throws Exception {
      String tableName = databaseProvider.getSqlProvider().getPrepTx();
      String sql = databaseProvider.getSqlProvider().selectPrepTx(tableName);
      Statement statement = connection.createStatement();
      statement.setFetchSize(500);
      try (ResultSet resultSet = statement.executeQuery(sql)) {
         while (resultSet.next()) {
            long txId = resultSet.getLong(1);
            byte[] xidBytes = resultSet.getBytes(2);
            ActiveMQBuffer xidBuffer = ActiveMQBuffers.wrappedBuffer(xidBytes);

            DatabaseTX tx = new DatabaseTX(txId);
            tx.setStatus(TXStatus.prepared);

            PrepareTXData data = new PrepareTXData(tx, xidBuffer, null);
            consumer.accept(data);
         }
      }
   }
}
