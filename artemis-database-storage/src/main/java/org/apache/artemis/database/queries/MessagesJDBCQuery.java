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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;

import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.data.MessageData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessagesJDBCQuery {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   Connection connection;
   DatabaseProvider databaseProvider;

   public MessagesJDBCQuery(DatabaseProvider databaseProvider, Connection connection) {
      this.databaseProvider = databaseProvider;
      this.connection = connection;
   }

   public void query(Consumer<MessageData> consumer) throws Exception {
      String messagesTable = databaseProvider.getSqlProvider().getMessages();
      String referencesTable = databaseProvider.getSqlProvider().getRefs();
      String sql = databaseProvider.getSqlProvider().reloadMessages(messagesTable, referencesTable);
      results(consumer, sql, true);
   }

   public void queryOrphaned(Consumer<MessageData> consumer) throws Exception {
      String messagesTable = databaseProvider.getSqlProvider().getMessages();
      String referencesTable = databaseProvider.getSqlProvider().getRefs();
      String sql = databaseProvider.getSqlProvider().orphanedMessages(messagesTable, referencesTable);
      results(consumer, sql, false);
   }

   public int deleteOrphaned() throws SQLException {
      logger.info("Deleting orphaned queues... ");
      String messagesTable = databaseProvider.getSqlProvider().getMessages();
      String referencesTable = databaseProvider.getSqlProvider().getRefs();
      String sql = databaseProvider.getSqlProvider().deleteOrphanedMessages(messagesTable, referencesTable);
      try (Statement statement = connection.createStatement()) {
         return statement.executeUpdate(sql);
      } finally {
         logger.info("Orphaned queues deleted");
      }

   }

   private void results(Consumer<MessageData> consumer, String sql, boolean readLargeFields) throws SQLException {
      try (Statement statement = connection.createStatement()) {
         statement.setFetchSize(500);
         try (ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
               MessageData messageData;
               if (readLargeFields) {
                  messageData = QueryUtil.readMessageData(resultSet, 1, 2, 3, 4, 5, 6);
               } else {
                  messageData = QueryUtil.readMessageData(resultSet, 1, 2, 3);
               }
               consumer.accept(messageData);
            }
         }
      }
   }

}
