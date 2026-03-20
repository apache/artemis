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

import org.apache.artemis.database.sql.SQLProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JDBCUtils {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   /**
    * Append to {@code errorMessage} a detailed description of the provided {@link SQLException}.
    * <p>
    * The information appended are:
    * <ul>
    * <li>SQL STATEMENTS</li>
    * <li>SQL EXCEPTIONS details ({@link SQLException#getSQLState},
    * {@link SQLException#getErrorCode} and {@link SQLException#getMessage}) of the linked list ({@link SQLException#getNextException}) of exceptions</li>
    * </ul>
    *
    * @param errorMessage  the target where append the exceptions details
    * @param exception     the SQL exception (or warning)
    * @param sqlStatements the SQL statements related to the {@code exception}
    * @return {@code errorMessage}
    */
   public static StringBuilder appendSQLExceptionDetails(StringBuilder errorMessage,
                                                         SQLException exception,
                                                         CharSequence sqlStatements) {
      errorMessage.append("\nSQL STATEMENTS: \n").append(sqlStatements);
      return appendSQLExceptionDetails(errorMessage, exception);
   }

   /**
    * Append to {@code errorMessage} a detailed description of the provided {@link SQLException}.
    * <p>
    * The information appended are:
    * <ul>
    * <li>SQL EXCEPTIONS details ({@link SQLException#getSQLState},
    * {@link SQLException#getErrorCode} and {@link SQLException#getMessage}) of the linked list ({@link SQLException#getNextException}) of exceptions</li>
    * </ul>
    *
    * @param errorMessage the target where append the exceptions details
    * @param exception    the SQL exception (or warning)
    * @return {@code errorMessage}
    */
   public static StringBuilder appendSQLExceptionDetails(StringBuilder errorMessage, SQLException exception) {
      errorMessage.append("\nSQL EXCEPTIONS: ");
      SQLException nextEx = exception;
      int level = 0;
      do {
         errorMessage.append('\n');
         errorMessage.append(" ".repeat(level));
         formatSqlException(errorMessage, nextEx);
         nextEx = nextEx.getNextException();
         level++;
      }
      while (nextEx != null);
      return errorMessage;
   }

   private static StringBuilder formatSqlException(StringBuilder errorMessage, SQLException exception) {
      final String sqlState = exception.getSQLState();
      final int errorCode = exception.getErrorCode();
      final String message = exception.getMessage();
      return errorMessage.append("SQLState: ").append(sqlState).append(" ErrorCode: ").append(errorCode).append(" Message: ").append(message);
   }

   public static void createTable(Connection connection,
                                  SQLProvider sqlProvider,
                                  String tableName,
                                  String... sqls) throws SQLException {
      logger.trace("Validating if table {} didn't exist before creating", tableName);
      try {
         connection.setAutoCommit(false);
         boolean tableExists = false;
         try (ResultSet rs = connection.getMetaData().getTables(null, null, tableName, null)) {
            if (rs != null && rs.next()) {
               logger.trace("Table {} already existed", tableName);
               tableExists = true;
            } else if (rs != null) {
               final SQLWarning sqlWarning = rs.getWarnings();
               if (sqlWarning != null) {
                  logger.warn(JDBCUtils.appendSQLExceptionDetails(new StringBuilder(), sqlWarning).toString());
               }
            }
         }
         if (!tableExists) {
            // Some databases use lower case (postgresql)
            try (ResultSet rs = connection.getMetaData().getTables(null, null, tableName.toLowerCase(), null)) {
               if (rs != null && rs.next()) {
                  logger.trace("Table {} already existed (lowercase match)", tableName);
                  tableExists = true;
               }
            }
         }
         if (!tableExists) {
            if (logger.isTraceEnabled()) {
               logger.trace("Table {} did not exist, creating it with SQL={}", tableName, Arrays.toString(sqls));
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
                     logger.trace("Executing {}", upperCaseSql);
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
