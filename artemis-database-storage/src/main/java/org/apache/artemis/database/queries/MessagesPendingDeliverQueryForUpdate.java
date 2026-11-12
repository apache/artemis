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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.artemis.database.DatabaseProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessagesPendingDeliverQueryForUpdate {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private final boolean pageJoinFetch;
   Connection connection;
   DatabaseProvider databaseProvider;
   PreparedStatement deliveryPreparedStatement;

   public MessagesPendingDeliverQueryForUpdate(DatabaseProvider databaseProvider,
                                               Connection connection,
                                               final boolean pageJoinFetch) {
      this.databaseProvider = databaseProvider;
      this.connection = connection;
      this.pageJoinFetch = pageJoinFetch;
   }

   public void prepare() throws SQLException {
      String messagesTable = databaseProvider.getSqlProvider().getMessages();
      String referencesTable = databaseProvider.getSqlProvider().getRefs();
      String deliverSQL;
      if (pageJoinFetch) {
         deliverSQL = databaseProvider.getSqlProvider().deliverPendingMessagesJoinFetch(messagesTable, referencesTable);
      } else {
         deliverSQL = databaseProvider.getSqlProvider().deliverPendingMessages(referencesTable);
      }
      logger.debug("Query used:: {}", deliverSQL);
      deliveryPreparedStatement = connection.prepareStatement(deliverSQL);
      deliveryPreparedStatement.setFetchSize(100);
   }

   public void close() throws Exception {
      deliveryPreparedStatement.close();
   }

   public ResultSet execute(long queueID) throws SQLException {
      deliveryPreparedStatement.setLong(1, queueID);
      return deliveryPreparedStatement.executeQuery();
   }

}
