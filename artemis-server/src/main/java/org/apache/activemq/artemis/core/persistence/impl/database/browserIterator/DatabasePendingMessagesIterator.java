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

package org.apache.activemq.artemis.core.persistence.impl.database.browserIterator;

import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.NoSuchElementException;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.persistence.impl.database.DatabaseStorageManager;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.MessageReferenceImpl;
import org.apache.activemq.artemis.utils.collections.CloseableIterator;
import org.apache.artemis.database.data.MessageData;
import org.apache.artemis.database.queries.QueryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabasePendingMessagesIterator implements CloseableIterator<MessageReference> {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final DatabaseStorageManager databaseStorageManager;
   private final Queue queue;
   private final int prefetch;
   private PreparedStatement statement;
   private Connection connection;
   private ResultSet resultSet;
   private long lastMessageID = -1;
   private final String query;

   private final ArrayDeque<MessageReference> cachedMessages;
   private boolean exhausted;

   public DatabasePendingMessagesIterator(DatabaseStorageManager databaseStorageManager, Queue queue, int prefetch) {
      this.databaseStorageManager = databaseStorageManager;
      this.queue = queue;
      this.prefetch = prefetch;
      this.cachedMessages = new ArrayDeque<>(prefetch);

      String messageTable = databaseStorageManager.getDatabaseProvider().getSqlProvider().getMessages();
      String referenceTable = databaseStorageManager.getDatabaseProvider().getSqlProvider().getRefs();
      this.query = databaseStorageManager.getDatabaseProvider().getSqlProvider().deliverPendingMessagesJoinFetch(messageTable, referenceTable, "DM.MESSAGE_ID>?");
   }

   public void openResources() throws Exception {
      connection = databaseStorageManager.getDatabaseProvider().getConnection();
      this.statement = connection.prepareStatement(query);
      statement.setLong(1, queue.getID());
      statement.setLong(2, lastMessageID);
      this.resultSet = statement.executeQuery();
   }

   @Override
   public boolean hasNext() {
      if (!cachedMessages.isEmpty()) {
         return true;
      }
      if (exhausted) {
         return false;
      }
      fetchBatch();
      return !cachedMessages.isEmpty();
   }

   @Override
   public MessageReference next() {
      if (cachedMessages.isEmpty() && !exhausted) {
         fetchBatch();
      }
      if (cachedMessages.isEmpty()) {
         throw new NoSuchElementException();
      }
      MessageReference reference = cachedMessages.poll();
      return reference;
   }

   private void fetchBatch() {
      boolean openHere = false;
      try {
         if (resultSet == null) {
            openResources();
            openHere = true;
         }
         for (int i = 0; i < prefetch; i++) {
            if (!resultSet.next()) {
               closeResources();
               exhausted = true;
               return;
            }
            MessageData messageData = QueryUtil.readMessageData(resultSet, 1, 2, 3, 4, 5, 6);
            lastMessageID = messageData.messageID;
            Message message = DatabaseStorageManager.decodeMessage(messageData.messageID, messageData.isLarge, messageData.isCore, messageData.messageBufferSupplier, messageData.largeBodySupplier, databaseStorageManager);
            cachedMessages.add(new MessageReferenceImpl(message, queue));
         }
      } catch (Exception e) {
         logger.warn("Error fetching next browse message for queue {}: {}", queue.getID(), e.getMessage(), e);
         exhausted = true;
      } finally {
         if (openHere) {
            closeResources();
         }
      }
   }

   public void closeResources() {
      if (resultSet != null) {
         try {
            resultSet.close();
         } catch (Exception ignored) {
         }
         resultSet = null;
      }
      if (statement != null) {
         try {
            statement.close();
         } catch (Exception ignored) {
         }
         statement = null;
      }
      if (connection != null) {
         try {
            connection.close();
         } catch (Exception ignored) {
         }
         connection = null;
      }
   }

   @Override
   public void close() {
      closeResources();
   }
}
