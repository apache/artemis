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
package org.apache.artemis.database;

import javax.sql.DataSource;
import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.artemis.database.sql.DB2SQLProvider;
import org.apache.artemis.database.sql.HsqlSQLProvider;
import org.apache.artemis.database.sql.MSSQLProvider;
import org.apache.artemis.database.sql.MySQLSqlProvider;
import org.apache.artemis.database.sql.OracleSQLProvider;
import org.apache.artemis.database.sql.PostgreSQLProvider;
import org.apache.artemis.database.sql.SQLProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseProvider {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private final String user;
   private final String password;
   private final SQLProvider sqlProvider;
   private final DataSource dataSource;

   public DatabaseProvider(DataSource dataSource, String user, String password) throws SQLException {
      this.dataSource = dataSource;
      this.user = user;
      this.password = password;
      this.sqlProvider = detectSqlProvider(dataSource, user, password);
   }

   private static SQLProvider detectSqlProvider(DataSource dataSource,
                                                String user,
                                                String password) throws SQLException {
      try (Connection connection = (user != null || password != null) ? dataSource.getConnection(user, password) : dataSource.getConnection()) {
         String dbProduct = connection.getMetaData().getDatabaseProductName().toLowerCase();
         if (dbProduct.contains("mysql") || dbProduct.contains("mariadb")) {
            return new MySQLSqlProvider();
         } else if (dbProduct.contains("oracle")) {
            return new OracleSQLProvider();
         } else if (dbProduct.contains("postgresql")) {
            return new PostgreSQLProvider();
         } else if (dbProduct.contains("db2")) {
            return new DB2SQLProvider();
         } else if (dbProduct.contains("microsoft sql server")) {
            return new MSSQLProvider();
         } else if (dbProduct.contains("hsql")) {
            return new HsqlSQLProvider();
         } else {
            throw new SQLException("Unsupported database product: " + connection.getMetaData().getDatabaseProductName());
         }
      }
   }

   public SQLProvider getSqlProvider() {
      return sqlProvider;
   }

   public synchronized Connection getConnection() throws SQLException {
      Connection connection;
      try {
         if (user != null || password != null) {
            connection = dataSource.getConnection(user, password);
         } else {
            connection = dataSource.getConnection();
         }
      } catch (SQLException e) {
         logger.error(JDBCUtils.appendSQLExceptionDetails(new StringBuilder(), e).toString());
         throw e;
      }

      connection.setHoldability(ResultSet.HOLD_CURSORS_OVER_COMMIT);

      return connection;
   }

   public void createSchema() throws SQLException {
      String messagesTableName = sqlProvider.getMessages();
      String referencesTableName = sqlProvider.getRefs();
      String addressInfoTableName = sqlProvider.getAddress();
      String queueInfoTableName = sqlProvider.getQueue();
      String brokerDataTableName = sqlProvider.getBrokerData();
      String configDataTableName = sqlProvider.getConfigData();
      String prepTxTableName = sqlProvider.getPrepTx();

      try (Connection connection = getConnection()) {
         JDBCUtils.createTable(connection, sqlProvider, messagesTableName, sqlProvider.createMessages(messagesTableName));
         JDBCUtils.createTable(connection, sqlProvider, referencesTableName, sqlProvider.createReferences(referencesTableName));
         JDBCUtils.createTable(connection, sqlProvider, addressInfoTableName, sqlProvider.createAddress(addressInfoTableName));
         JDBCUtils.createTable(connection, sqlProvider, queueInfoTableName, sqlProvider.createQueue(queueInfoTableName));
         JDBCUtils.createTable(connection, sqlProvider, brokerDataTableName, sqlProvider.createGenericData(brokerDataTableName));
         JDBCUtils.createTable(connection, sqlProvider, configDataTableName, sqlProvider.createGenericData(configDataTableName));
         JDBCUtils.createTable(connection, sqlProvider, prepTxTableName, sqlProvider.createPrepTx(prepTxTableName));
      }
   }
}
