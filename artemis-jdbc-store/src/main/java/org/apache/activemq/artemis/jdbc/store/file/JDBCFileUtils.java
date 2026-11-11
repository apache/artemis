/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.jdbc.store.file;

import java.sql.Connection;
import java.sql.SQLException;

import org.apache.activemq.artemis.jdbc.store.drivers.JDBCConnectionProvider;
import org.apache.activemq.artemis.jdbc.store.sql.PropertySQLProvider;
import org.apache.activemq.artemis.jdbc.store.sql.SQLProvider;

import static org.apache.activemq.artemis.jdbc.store.sql.PropertySQLProvider.Factory.SQLDialect.DB2;
import static org.apache.activemq.artemis.jdbc.store.sql.PropertySQLProvider.Factory.SQLDialect.POSTGRESQL;

class JDBCFileUtils {

   static JDBCSequentialFileFactoryDriver getDBFileDriver(JDBCConnectionProvider connectionProvider, SQLProvider provider) throws SQLException {
      final JDBCSequentialFileFactoryDriver dbDriver;
      final PropertySQLProvider.Factory.SQLDialect sqlDialect;
      try (Connection connection = connectionProvider.getConnection()) {
         sqlDialect = PropertySQLProvider.Factory.investigateDialect(connection);
      }
      if (POSTGRESQL.equals(sqlDialect)) {
         dbDriver = new PostgresSequentialSequentialFileDriver(connectionProvider, provider);
      } else if (DB2.equals(sqlDialect)) {
         dbDriver = new Db2SequentialFileDriver(connectionProvider, provider);
      } else {
         dbDriver = new JDBCSequentialFileFactoryDriver(connectionProvider, provider);
      }
      return dbDriver;
   }
}
