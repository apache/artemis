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
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.function.Consumer;

import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.data.QueueData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueJDBCQuery {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   Connection connection;
   DatabaseProvider databaseProvider;

   public QueueJDBCQuery(DatabaseProvider databaseProvider, Connection connection) {
      this.databaseProvider = databaseProvider;
      this.connection = connection;
   }

   public void query(Consumer<QueueData> consumer) throws Exception {
      String tableName = databaseProvider.getSqlProvider().getQueue();
      String sql = databaseProvider.getSqlProvider().selectQueue(tableName);
      Statement statement = connection.createStatement();
      statement.setFetchSize(500);
      try (ResultSet resultSet = statement.executeQuery(sql)) {
         while (resultSet.next()) {
            long queueID = resultSet.getLong(1);
            long addressID = resultSet.getLong(2);
            String queueName = resultSet.getString(3);
            boolean isMulticast = String.valueOf(resultSet.getString(4)).equals("Y");
            boolean isAnycast = String.valueOf(resultSet.getString(5)).equals("Y");

            String filter = null;
            byte[] filterBytes = resultSet.getBytes(6);
            if (filterBytes != null) {
               filter = new String(filterBytes, StandardCharsets.UTF_8);
            }

            String queueConfigJson = null;
            byte[] configBytes = resultSet.getBytes(7);
            if (configBytes != null) {
               queueConfigJson = new String(configBytes, StandardCharsets.UTF_8);
            }
            QueueData data = new QueueData(addressID, queueID, queueName, filter, isMulticast, isAnycast, queueConfigJson, null);
            consumer.accept(data);
         }
      }
   }

}
