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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.artemis.database.data.MessageData;

public class QueryUtil {

   public static MessageData readMessageData(ResultSet resultSet,
                                             int idColumn,
                                             int bytesColumn,
                                             int memoryEstimateColumn) throws SQLException {
      return readMessageData(resultSet, idColumn, bytesColumn, memoryEstimateColumn, -1, -1, -1);
   }

   public static MessageData readMessageData(ResultSet resultSet,
                                             int idColumn,
                                             int bytesColumn,
                                             int memoryEstimateColumn,
                                             int isLargeColumn,
                                             int largeBodyColumn,
                                             int isCoreColumn) throws SQLException {
      long messageID = resultSet.getLong(idColumn);
      byte[] bytes = resultSet.getBytes(bytesColumn);
      ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(bytes);
      int memoryEstimate = memoryEstimateColumn > 0 ? resultSet.getInt(memoryEstimateColumn) : 0;
      boolean isLarge = isLargeColumn > 0 && "Y".equals(resultSet.getString(isLargeColumn));
      boolean isCore = isCoreColumn > 0 && "Y".equals(resultSet.getString(isCoreColumn));

      Supplier<ActiveMQBuffer> largeBodySupplier = null;
      if (largeBodyColumn > 0) {
         byte[] largeBody = resultSet.getBytes(largeBodyColumn);
         if (largeBody != null) {
            largeBodySupplier = () -> ActiveMQBuffers.wrappedBuffer(largeBody);
         }
      }

      return new MessageData(messageID, () -> buffer, memoryEstimate, isLarge, isCore, largeBodySupplier, null);
   }
}
