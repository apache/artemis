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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executor;

import org.apache.activemq.artemis.jdbc.store.logging.LoggingConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class JDBCConnectionProvider {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private DataSource dataSource;
   private Executor networkTimeoutExecutor;
   private int networkTimeoutMillis;
   private boolean supportNetworkTimeout;
   private final String user;
   private final String password;

   public JDBCConnectionProvider(DataSource dataSource) {
      this(dataSource, null, null);
   }

   public JDBCConnectionProvider(DataSource dataSource, String user, String password) {
      this.dataSource = dataSource;
      this.networkTimeoutExecutor = null;
      this.networkTimeoutMillis = -1;
      this.supportNetworkTimeout = true;
      this.user = user;
      this.password = password;
   }

   public synchronized Connection getConnection() throws SQLException {
      Connection connection;
      try {
         if (user != null || password != null) {
            connection = dataSource.getConnection(user, password);
         } else {
            connection = dataSource.getConnection();
         }
         if (logger.isTraceEnabled() && !(connection instanceof LoggingConnection)) {
            connection = new LoggingConnection(connection, logger);
         }
      } catch (SQLException e) {
         logger.error(JDBCUtils.appendSQLExceptionDetails(new StringBuilder(), e).toString());
         throw e;
      }

      if (this.networkTimeoutMillis >= 0 && this.networkTimeoutExecutor == null) {
         logger.warn("Unable to set a network timeout on the JDBC connection: networkTimeoutExecutor is null");
      }

      if (this.networkTimeoutMillis >= 0 && this.networkTimeoutExecutor != null) {
         if (supportNetworkTimeout) {
            try {
               connection.setNetworkTimeout(this.networkTimeoutExecutor, this.networkTimeoutMillis);
            } catch (SQLException e) {
               supportNetworkTimeout = false;
               logger.warn(JDBCUtils.appendSQLExceptionDetails(new StringBuilder(), e).toString());
               logger.warn("Unable to set a network timeout on the JDBC connection: won't retry again in the future");
            } catch (Throwable throwable) {
               supportNetworkTimeout = false;
               //it included SecurityExceptions and UnsupportedOperationException
               logger.warn("Unable to set a network timeout on the JDBC connection: won't retry again in the future", throwable);
            }
         }
      }
      return connection;
   }

   public int getNetworkTimeoutMillis() {
      return networkTimeoutMillis;
   }

   public void setNetworkTimeout(Executor executor, int milliseconds) {
      this.networkTimeoutExecutor = executor;
      this.networkTimeoutMillis = milliseconds;
   }
}
