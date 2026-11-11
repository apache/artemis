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
package org.apache.activemq.artemis.jdbc.store.logging;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.Arrays;

import org.slf4j.Logger;

public class LoggingStatement implements Statement {

   private final Statement statement;

   protected final String statementID;

   protected final Logger logger;

   public LoggingStatement(Statement statement, Logger logger) {
      this.statement = statement;
      this.logger = logger;
      this.statementID = LoggingUtil.getID(statement);
   }

   public Statement getStatement() {
      return statement;
   }

   public String getStatementID() {
      return statementID;
   }

   @Override
   public ResultSet executeQuery(String sql) throws SQLException {
      LoggingResultSet rs = new LoggingResultSet(statement.executeQuery(sql), logger);
      logger.trace("{}.executeQuery({}) = {}", statementID, sql, rs.getResultSetID());
      return rs;
   }

   @Override
   public int executeUpdate(String sql) throws SQLException {
      int i = statement.executeUpdate(sql);
      logger.trace("{}.executeUpdate({}) = {}", statementID, sql, i);
      return i;
   }

   @Override
   public void close() throws SQLException {
      logger.trace("{}.close()", statementID);
      statement.close();
   }

   @Override
   public int getMaxFieldSize() throws SQLException {
      int i = statement.getMaxFieldSize();
      logger.trace("{}.getMaxFieldSize() = {}", statementID, i);
      return i;
   }

   @Override
   public void setMaxFieldSize(int max) throws SQLException {
      logger.trace("{}.setMaxFieldSize({})", statementID, max);
      statement.setMaxFieldSize(max);
   }

   @Override
   public int getMaxRows() throws SQLException {
      int i = statement.getMaxRows();
      logger.trace("{}.getMaxRows() = {}", statementID, i);
      return i;
   }

   @Override
   public void setMaxRows(int max) throws SQLException {
      logger.trace("{}.setMaxRows()", statementID);
      statement.setMaxRows(max);
   }

   @Override
   public void setEscapeProcessing(boolean enable) throws SQLException {
      logger.trace("{}.setEscapeProcessing({})", statementID, enable);
      statement.setEscapeProcessing(enable);
   }

   @Override
   public int getQueryTimeout() throws SQLException {
      int i = statement.getQueryTimeout();
      logger.trace("{}.getQueryTimeout() = {}", statementID, i);
      return i;
   }

   @Override
   public void setQueryTimeout(int seconds) throws SQLException {
      logger.trace("{}.setQueryTimeout({})", statementID, seconds);
      statement.setQueryTimeout(seconds);
   }

   @Override
   public void cancel() throws SQLException {
      logger.trace("{}.cancel()", statementID);
      statement.cancel();
   }

   @Override
   public SQLWarning getWarnings() throws SQLException {
      logger.trace("{}.getWarnings()", statementID);
      return statement.getWarnings();
   }

   @Override
   public void clearWarnings() throws SQLException {
      logger.trace("{}.clearWarnings()", statementID);
      statement.clearWarnings();
   }

   @Override
   public void setCursorName(String name) throws SQLException {
      logger.trace("{}.setCursorName({})", statementID, name);
      statement.setCursorName(name);
   }

   @Override
   public boolean execute(String sql) throws SQLException {
      boolean b = statement.execute(sql);
      logger.trace("{}.execute({}) = {}", statementID, sql, b);
      return b;
   }

   @Override
   public ResultSet getResultSet() throws SQLException {
      LoggingResultSet rs = new LoggingResultSet(statement.getResultSet(), logger);
      logger.trace("{}.executeQuery() = {}", statementID, rs.getResultSetID());
      return rs;
   }

   @Override
   public int getUpdateCount() throws SQLException {
      int i = statement.getUpdateCount();
      logger.trace("{}.getUpdateCount() = {}", statementID, i);
      return i;
   }

   @Override
   public boolean getMoreResults() throws SQLException {
      boolean b = statement.getMoreResults();
      logger.trace("{}.getMoreResults() = {}", statementID, b);
      return b;
   }

   @Override
   public void setFetchDirection(int direction) throws SQLException {
      logger.trace("{}.setFetchDirection()", statementID);
      statement.setFetchDirection(direction);
   }

   @Override
   public int getFetchDirection() throws SQLException {
      int i = statement.getFetchDirection();
      logger.trace("{}.getFetchDirection() = {}", statementID, i);
      return i;
   }

   @Override
   public void setFetchSize(int rows) throws SQLException {
      logger.trace("{}.setFetchSize({})", statementID, rows);
      statement.setFetchSize(rows);
   }

   @Override
   public int getFetchSize() throws SQLException {
      int i = statement.getFetchSize();
      logger.trace("{}.getFetchSize() = {}", statementID, i);
      return i;
   }

   @Override
   public int getResultSetConcurrency() throws SQLException {
      int i = statement.getResultSetConcurrency();
      logger.trace("{}.getResultSetConcurrency() = {}", statementID, i);
      return i;
   }

   @Override
   public int getResultSetType() throws SQLException {
      int i = statement.getResultSetType();
      logger.trace("{}.getResultSetType() = {}", statementID, i);
      return i;
   }

   @Override
   public void addBatch(String sql) throws SQLException {
      logger.trace("{}.addBatch({}, {})", statementID);
      statement.addBatch(sql);
   }

   @Override
   public void clearBatch() throws SQLException {
      logger.trace("{}.clearBatch()", statementID);
      statement.clearBatch();
   }

   @Override
   public int[] executeBatch() throws SQLException {
      int[] i = statement.executeBatch();
      logger.trace("{}.executeBatch() = {}", statementID, Arrays.toString(i));
      return i;
   }

   @Override
   public Connection getConnection() throws SQLException {
      LoggingConnection connection = new LoggingConnection(statement.getConnection(), logger);
      logger.trace("{}.getConnection() = {}", statementID, connection.getConnectionID());
      return connection;
   }

   @Override
   public boolean getMoreResults(int current) throws SQLException {
      boolean b = statement.getMoreResults(current);
      logger.trace("{}.getMoreResults({}) = {}", statementID, current, b);
      return b;
   }

   @Override
   public ResultSet getGeneratedKeys() throws SQLException {
      LoggingResultSet rs = new LoggingResultSet(statement.getGeneratedKeys(), logger);
      logger.trace("{}.getGeneratedKeys() = {}", statementID, rs.getResultSetID());
      return rs;
   }

   @Override
   public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
      int i = statement.executeUpdate(sql, autoGeneratedKeys);
      logger.trace("{}.executeUpdate({}, {}) = {}", statementID, sql, autoGeneratedKeys, i);
      return i;
   }

   @Override
   public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
      int i = statement.executeUpdate(sql, columnIndexes);
      logger.trace("{}.executeUpdate({}, {}) = {}", statementID, sql, Arrays.toString(columnIndexes), i);
      return i;
   }

   @Override
   public int executeUpdate(String sql, String[] columnNames) throws SQLException {
      int i = statement.executeUpdate(sql, columnNames);
      logger.trace("{}.executeUpdate({}, {}) = {}", statementID, sql, Arrays.toString(columnNames), i);
      return i;
   }

   @Override
   public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
      boolean b = statement.execute(sql, autoGeneratedKeys);
      logger.trace("{}.execute({}, {}) = {}", statementID, sql, autoGeneratedKeys, b);
      return b;
   }

   @Override
   public boolean execute(String sql, int[] columnIndexes) throws SQLException {
      boolean b = statement.execute(sql, columnIndexes);
      logger.trace("{}.execute({}, {}) = {}", statementID, sql, Arrays.toString(columnIndexes), b);
      return b;
   }

   @Override
   public boolean execute(String sql, String[] columnNames) throws SQLException {
      boolean b = statement.execute(sql, columnNames);
      logger.trace("{}.execute({}, {}) = {}", statementID, sql, Arrays.toString(columnNames), b);
      return b;
   }

   @Override
   public int getResultSetHoldability() throws SQLException {
      int i = statement.getResultSetHoldability();
      logger.trace("{}.getResultSetHoldability() = {}", statementID, i);
      return i;
   }

   @Override
   public boolean isClosed() throws SQLException {
      boolean b = statement.isClosed();
      logger.trace("{}.isClosed() = {}", statementID, b);
      return b;
   }

   @Override
   public void setPoolable(boolean poolable) throws SQLException {
      logger.trace("{}.setPoolable({})", statementID, poolable);
      statement.setPoolable(poolable);
   }

   @Override
   public boolean isPoolable() throws SQLException {
      boolean b = statement.isPoolable();
      logger.trace("{}.isPoolable() = {}", statementID, b);
      return b;
   }

   @Override
   public void closeOnCompletion() throws SQLException {
      logger.trace("{}.closeOnCompletion()", statementID);
      statement.closeOnCompletion();
   }

   @Override
   public boolean isCloseOnCompletion() throws SQLException {
      boolean b = statement.isCloseOnCompletion();
      logger.trace("{}.isCloseOnCompletion() = {}", statementID, b);
      return b;
   }

   @Override
   public long getLargeUpdateCount() throws SQLException {
      long x = statement.getLargeUpdateCount();
      logger.trace("{}.getLargeUpdateCount() = {}", statementID, x);
      return x;
   }

   @Override
   public void setLargeMaxRows(long max) throws SQLException {
      logger.trace("{}.setLargeMaxRows({})", statementID, max);
      statement.setLargeMaxRows(max);
   }

   @Override
   public long getLargeMaxRows() throws SQLException {
      long x = statement.getLargeMaxRows();
      logger.trace("{}.getLargeMaxRows() = {}", statementID, x);
      return x;
   }

   @Override
   public long[] executeLargeBatch() throws SQLException {
      long[] x = statement.executeLargeBatch();
      logger.trace("{}.executeLargeBatch() = {}", statementID, x);
      return x;
   }

   @Override
   public long executeLargeUpdate(String sql) throws SQLException {
      long x = statement.executeLargeUpdate(sql);
      logger.trace("{}.executeLargeUpdate({}) = {}", statementID, sql, x);
      return x;
   }

   @Override
   public long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
      long x = statement.executeLargeUpdate(sql, autoGeneratedKeys);
      logger.trace("{}.executeLargeUpdate() = {}", statementID, sql, autoGeneratedKeys, x);
      return x;
   }

   @Override
   public long executeLargeUpdate(String sql, int[] columnIndexes) throws SQLException {
      long x = statement.executeLargeUpdate(sql, columnIndexes);
      logger.trace("{}.executeLargeUpdate({}, {}) = {}", statementID, sql, Arrays.toString(columnIndexes), x);
      return x;
   }

   @Override
   public long executeLargeUpdate(String sql, String[] columnNames) throws SQLException {
      long x = statement.executeLargeUpdate(sql, columnNames);
      logger.trace("{}.executeLargeUpdate({}, {}) = {}", statementID, sql, Arrays.toString(columnNames), x);
      return x;
   }

   @Override
   public <T> T unwrap(Class<T> iface) throws SQLException {
      T x = statement.unwrap(iface);
      logger.trace("{}.unwrap({}) = {}", statementID, iface, x);
      return x;
   }

   @Override
   public boolean isWrapperFor(Class<?> iface) throws SQLException {
      boolean b = statement.isWrapperFor(iface);
      logger.trace("{}.isWrapperFor({}) = {}", statementID, iface, b);
      return b;
   }
}
