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

import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.Arrays;
import java.util.stream.Stream;

import org.apache.artemis.database.sql.OracleSQLProvider;
import org.apache.artemis.database.sql.SQLProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to hold common database functionality such as drivers and connections
 */
public abstract class DatabaseEntity {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected DatabaseProvider connectionProvider;

   SQLProvider sqlProvider = new OracleSQLProvider();

   public DatabaseEntity() {
   }

   public DatabaseEntity(DatabaseProvider connectionProvider) {
      this.connectionProvider = connectionProvider;
   }

   public void start() throws SQLException {
      createSchema();
      prepareStatements();
   }

   public void stop() throws SQLException {

   }

   protected abstract void prepareStatements();

   protected abstract void createSchema() throws SQLException;

   private void createTableIfNotExists(String tableName, String... sqls) throws SQLException {
      logger.trace("Validating if table {} didn't exist before creating", tableName);
      try (Connection connection = connectionProvider.getConnection()) {
         try {
            connection.setAutoCommit(false);
            final boolean tableExists;
            try (ResultSet rs = connection.getMetaData().getTables(null, null, tableName, null)) {
               if (rs == null || !rs.next()) {
                  tableExists = false;
                  if (logger.isTraceEnabled()) {
                     logger.trace("Table {} did not exist, creating it with SQL={}", tableName, Arrays.toString(sqls));
                  }
                  if (rs != null) {
                     final SQLWarning sqlWarning = rs.getWarnings();
                     if (sqlWarning != null) {
                        logger.warn(JDBCUtils.appendSQLExceptionDetails(new StringBuilder(), sqlWarning).toString());
                     }
                  }
               } else {
                  tableExists = true;
               }
            }
            if (tableExists) {
               logger.trace("Validating if the existing table {} is initialized or not", tableName);
               try (Statement statement = connection.createStatement(); ResultSet cntRs = statement.executeQuery(sqlProvider.getSelectCount(tableName))) {
                  logger.trace("Validation of the existing table {} initialization is started", tableName);
                  int rows;
                  if (cntRs.next() && (rows = cntRs.getInt(1)) > 0) {
                     logger.trace("Table {} did exist but is not empty. Skipping initialization. Found {} rows.", tableName, rows);
                     if (logger.isDebugEnabled()) {
                        final long expectedRows = Stream.of(sqls).map(String::toUpperCase).filter(sql -> sql.contains("INSERT INTO")).count();
                        if (rows < expectedRows) {
                           logger.debug("Table {} was expected to contain {} rows while it has {} rows.", tableName, expectedRows, rows);
                        }
                     }
                     connection.commit();
                     return;
                  } else {
                     sqls = Stream.of(sqls).filter(sql -> {
                        final String upperCaseSql = sql.toUpperCase();
                        return !(upperCaseSql.contains("CREATE TABLE") || upperCaseSql.contains("CREATE INDEX"));
                     }).toArray(String[]::new);
                     if (sqls.length > 0) {
                        logger.trace("Table {} did exist but is empty. Starting initialization.", tableName);
                     } else {
                        logger.trace("Table {} did exist but is empty. Initialization completed: no initialization statements left.", tableName);
                        connection.commit();
                     }
                  }
               } catch (SQLException e) {
                  //that's not a real issue and do not deserve any user-level log:
                  //some DBMS just return stale information about table existence
                  //and can fail on later attempts to access them
                  if (logger.isTraceEnabled()) {
                     logger.trace(JDBCUtils.appendSQLExceptionDetails(new StringBuilder("Can't verify the initialization of table ").append(tableName).append(" due to:"), e, sqlProvider.getSelectCount(tableName)).toString());
                  }
                  try {
                     connection.rollback();
                  } catch (SQLException rollbackEx) {
                     logger.debug("Rollback failed while validating initialization of a table", rollbackEx);
                  }
                  connection.setAutoCommit(false);
                  logger.trace("Table {} seems to exist, but we can't verify the initialization. Keep trying to create and initialize.", tableName);
               }
            }
            if (sqls.length > 0) {
               try (Statement statement = connection.createStatement()) {
                  for (String sql : sqls) {
                     statement.executeUpdate(sql);
                     final SQLWarning statementSqlWarning = statement.getWarnings();
                     if (statementSqlWarning != null) {
                        logger.warn(JDBCUtils.appendSQLExceptionDetails(new StringBuilder(), statementSqlWarning, sql).toString());
                     }
                  }
               }

               connection.commit();
            }
         } catch (SQLException e) {
            final String sqlStatements = String.join("\n", sqls);
            logger.error(JDBCUtils.appendSQLExceptionDetails(new StringBuilder(), e, sqlStatements).toString());
            try {
               connection.rollback();
            } catch (SQLException rollbackEx) {
               logger.error(JDBCUtils.appendSQLExceptionDetails(new StringBuilder(), rollbackEx, sqlStatements).toString());
               throw rollbackEx;
            }
            throw e;
         }
      }
   }

   public SQLProvider getSqlProvider() {
      return sqlProvider;
   }

   public void setSqlProvider(SQLProvider sqlProvider) {
      this.sqlProvider = sqlProvider;
   }

   public DatabaseProvider getJdbcConnectionProvider() {
      return this.connectionProvider;
   }

   public void setJdbcConnectionProvider(DatabaseProvider connectionProvider) {
      this.connectionProvider = connectionProvider;
   }

}
