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

import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.data.AddressData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddressJDBCQuery {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   Connection connection;
   DatabaseProvider databaseProvider;

   public AddressJDBCQuery(DatabaseProvider databaseProvider, Connection connection) {
      this.databaseProvider = databaseProvider;
      this.connection = connection;
   }

   public void query(Consumer<AddressData> consumer) throws Exception {
      String tableName = databaseProvider.getSqlProvider().getAddress();
      String sql = databaseProvider.getSqlProvider().selectAddress(tableName);
      Statement statement = connection.createStatement();
      statement.setFetchSize(500);
      try (ResultSet resultSet = statement.executeQuery(sql)) {
         while (resultSet.next()) {
            long addressId = resultSet.getLong(1);
            String addressName = resultSet.getString(2);
            String isMulticast = resultSet.getString(3);
            String isAnycast = resultSet.getString(4);

            AddressData data = new AddressData(addressId, addressName, String.valueOf(isMulticast).equals("Y"), String.valueOf(isAnycast).equals("Y"));
            consumer.accept(data);
         }
      }
   }

}
