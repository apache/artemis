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
package org.apache.activemq.artemis.jdbc.store.drivers;

import java.sql.SQLException;
import java.util.Map;

import org.apache.activemq.artemis.jdbc.store.sql.PropertySQLProvider;
import org.apache.activemq.artemis.jdbc.store.sql.SQLProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class JDBCUtils {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static SQLProvider.Factory getSQLProviderFactory(String url) {
      PropertySQLProvider.Factory.SQLDialect dialect = PropertySQLProvider.Factory.identifyDialect(url);
      logger.trace("getSQLProvider Returning SQL provider for dialect {} for url::{}", dialect, url);
      return new PropertySQLProvider.Factory(dialect);
   }

   public static SQLProvider getSQLProvider(String driverClass, String tableName, SQLProvider.DatabaseStoreType storeType) {
      PropertySQLProvider.Factory.SQLDialect dialect = PropertySQLProvider.Factory.identifyDialect(driverClass);
      logger.trace("getSQLProvider Returning SQL provider for dialect {} for driver::{}, tableName::{}", dialect, driverClass, tableName);
      PropertySQLProvider.Factory factory = new PropertySQLProvider.Factory(dialect);
      return factory.create(tableName, storeType);
   }

   public static SQLProvider getSQLProvider(Map<String, Object> dataSourceProperties, String tableName, SQLProvider.DatabaseStoreType storeType) {
      PropertySQLProvider.Factory.SQLDialect dialect = PropertySQLProvider.Factory.investigateDialect(dataSourceProperties);
      logger.trace("getSQLProvider Returning SQL provider for dialect {}, tableName::{}", dialect, tableName);
      PropertySQLProvider.Factory factory = new PropertySQLProvider.Factory(dialect);
      return factory.create(tableName, storeType);
   }

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
}
