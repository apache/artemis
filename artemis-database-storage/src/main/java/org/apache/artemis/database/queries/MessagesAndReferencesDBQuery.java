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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.data.MessageDataReferenceMerged;
import org.apache.artemis.database.data.MessageDataReferenceMerged.QueueRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes a single LEFT JOIN of DB_MESSAGES and DB_REFERENCES ordered by
 * MESSAGE_ID. Consecutive rows belonging to the same message are collapsed
 * in-place: queue references are accumulated into a list and the complete
 * {@link MessageDataReferenceMerged} is handed to the consumer only when the
 * next different MESSAGE_ID is seen (or the result set is exhausted).
 */
public class MessagesAndReferencesDBQuery {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final DatabaseProvider databaseProvider;
   private final Connection connection;
   private final String whereClause;

   public MessagesAndReferencesDBQuery(DatabaseProvider databaseProvider, Connection connection) {
      this(databaseProvider, connection, null);
   }

   public MessagesAndReferencesDBQuery(DatabaseProvider databaseProvider, Connection connection, String whereClause) {
      this.databaseProvider = databaseProvider;
      this.connection = connection;
      this.whereClause = whereClause;
   }

   private static MessageDataReferenceMerged build(long messageID,
                                                   Long tx,
                                                   int memEst,
                                                   byte[] bytes,
                                                   byte[] largeBodyBytes,
                                                   List<QueueRef> queues,
                                                   boolean isLarge,
                                                   boolean isCore) {
      ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(bytes);
      Supplier<ActiveMQBuffer> largeBodyBufferSupplier = null;
      if (largeBodyBytes != null) {
         ActiveMQBuffer largeBuffer = ActiveMQBuffers.wrappedBuffer(largeBodyBytes);
         largeBodyBufferSupplier = () -> largeBuffer;
      }
      return new MessageDataReferenceMerged(messageID, tx, memEst, () -> buffer, largeBodyBufferSupplier, queues, isLarge, isCore);
   }

   public void query(Consumer<MessageDataReferenceMerged> consumer) throws Exception {
      String messagesTable = databaseProvider.getSqlProvider().getMessages();
      String referencesTable = databaseProvider.getSqlProvider().getRefs();
      String sql = whereClause != null
         ? databaseProvider.getSqlProvider().selectMessagesWithReferences(messagesTable, referencesTable, whereClause)
         : databaseProvider.getSqlProvider().selectMessagesWithReferences(messagesTable, referencesTable);

      try (Statement statement = connection.createStatement()) {
         statement.setFetchSize(500);
         try (ResultSet rs = statement.executeQuery(sql)) {

            // state for the current message being accumulated
            long currentID = Long.MIN_VALUE;
            Long currentTx = null;
            int currentMemEst = 0;
            byte[] currentBytes = null;
            List<QueueRef> currentQueues = new ArrayList<>();
            boolean currentIsLarge = false;
            boolean currentIsCore = false;
            byte[] currentLargeBody = null;

            while (rs.next()) {
               long messageID = rs.getLong(1);
               long txRaw = rs.getLong(2);
               Long tx = rs.wasNull() ? null : txRaw;
               int memEst = rs.getInt(3);
               byte[] bytes = rs.getBytes(4);
               boolean isLarge = "Y".equals(rs.getString(7));
               byte[] largeBody = rs.getBytes(8);
               boolean isCore = "Y".equals(rs.getString(9));

               // nullable — LEFT JOIN produces NULL when there are no refs
               long queueID = rs.getLong(5);
               boolean hasRef = !rs.wasNull();
               boolean paged = hasRef && "Y".equals(rs.getString(6));
               long ackTXRaw = rs.getLong(10);
               Long ackTX = rs.wasNull() ? null : ackTXRaw;

               if (messageID != currentID) {
                  // flush the previous message if there is one
                  if (currentID != Long.MIN_VALUE) {
                     consumer.accept(build(currentID, currentTx, currentMemEst, currentBytes, currentLargeBody, currentQueues, currentIsLarge, currentIsCore));
                  }
                  // start a new accumulation
                  currentID = messageID;
                  currentTx = tx;
                  currentMemEst = memEst;
                  currentBytes = bytes;
                  currentQueues = new ArrayList<>();
                  currentIsLarge = isLarge;
                  currentLargeBody = largeBody;
                  currentIsCore = isCore;
               }

               if (hasRef) {
                  currentQueues.add(new QueueRef(queueID, paged, ackTX));
               }
            }

            // flush the last message
            if (currentID != Long.MIN_VALUE) {
               consumer.accept(build(currentID, currentTx, currentMemEst, currentBytes, currentLargeBody, currentQueues, currentIsLarge, currentIsCore));
            }
         }
      }
   }
}
