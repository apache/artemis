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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;

import org.slf4j.Logger;

public class LoggingPreparedStatement extends LoggingStatement implements PreparedStatement {

   private final PreparedStatement preparedStatement;

   public LoggingPreparedStatement(PreparedStatement preparedStatement, Logger logger) {
      super(preparedStatement, logger);
      this.preparedStatement = preparedStatement;
   }

   @Override
   public ResultSet executeQuery() throws SQLException {
      LoggingResultSet rs = new LoggingResultSet(preparedStatement.executeQuery(), logger);
      logger.trace("{}.executeQuery() = {}", statementID, rs.getResultSetID());
      return rs;
   }

   @Override
   public int executeUpdate() throws SQLException {
      int i = preparedStatement.executeUpdate();
      logger.trace("{}.executeUpdate() = {}", statementID, i);
      return i;
   }

   @Override
   public void setNull(int parameterIndex, int sqlType) throws SQLException {
      logger.trace("{}.setNull({}, {})", statementID, parameterIndex, sqlType);
      preparedStatement.setNull(parameterIndex, sqlType);
   }

   @Override
   public void setBoolean(int parameterIndex, boolean x) throws SQLException {
      logger.trace("{}.setBoolean({}, {})", statementID, parameterIndex, x);
      preparedStatement.setBoolean(parameterIndex, x);
   }

   @Override
   public void setByte(int parameterIndex, byte x) throws SQLException {
      logger.trace("{}.setByte({}, {})", statementID, parameterIndex, x);
      preparedStatement.setByte(parameterIndex, x);
   }

   @Override
   public void setShort(int parameterIndex, short x) throws SQLException {
      logger.trace("{}.setShort({}, {})", statementID, parameterIndex, x);
      preparedStatement.setShort(parameterIndex, x);
   }

   @Override
   public void setInt(int parameterIndex, int x) throws SQLException {
      logger.trace("{}.setInt({}, {})", statementID, parameterIndex, x);
      preparedStatement.setInt(parameterIndex, x);
   }

   @Override
   public void setLong(int parameterIndex, long x) throws SQLException {
      logger.trace("{}.setLong({}, {})", statementID, parameterIndex, x);
      preparedStatement.setLong(parameterIndex, x);
   }

   @Override
   public void setFloat(int parameterIndex, float x) throws SQLException {
      logger.trace("{}.setFloat({}, {})", statementID, parameterIndex, x);
      preparedStatement.setFloat(parameterIndex, x);
   }

   @Override
   public void setDouble(int parameterIndex, double x) throws SQLException {
      logger.trace("{}.setDouble({}, {})", statementID, parameterIndex, x);
      preparedStatement.setDouble(parameterIndex, x);
   }

   @Override
   public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
      logger.trace("{}.setBigDecimal({}, {})", statementID, parameterIndex, x);
      preparedStatement.setBigDecimal(parameterIndex, x);
   }

   @Override
   public void setString(int parameterIndex, String x) throws SQLException {
      logger.trace("{}.setString({}, {})", statementID, parameterIndex, x);
      preparedStatement.setString(parameterIndex, x);
   }

   @Override
   public void setBytes(int parameterIndex, byte[] x) throws SQLException {
      logger.trace("{}.setBytes({}, {})", statementID, parameterIndex, x);
      preparedStatement.setBytes(parameterIndex, x);
   }

   @Override
   public void setDate(int parameterIndex, Date x) throws SQLException {
      logger.trace("{}.setDate({}, {})", statementID, parameterIndex, x);
      preparedStatement.setDate(parameterIndex, x);
   }

   @Override
   public void setTime(int parameterIndex, Time x) throws SQLException {
      logger.trace("{}.setTime({}, {})", statementID, parameterIndex, x);
      preparedStatement.setTime(parameterIndex, x);
   }

   @Override
   public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
      logger.trace("{}.setTimestamp({}, {})", statementID, parameterIndex, x);
      preparedStatement.setTimestamp(parameterIndex, x);
   }

   @Override
   public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
      logger.trace("{}.setAsciiStream({}, {}, {})", statementID, parameterIndex, x, length);
      preparedStatement.setAsciiStream(parameterIndex, x, length);
   }

   @Override
   public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
      logger.trace("{}.setUnicodeStream({}, {}, {})", statementID, parameterIndex, x, length);
      preparedStatement.setUnicodeStream(parameterIndex, x, length);
   }

   @Override
   public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
      logger.trace("{}.setBinaryStream({}, {}, {})", statementID, parameterIndex, x, length);
      preparedStatement.setBinaryStream(parameterIndex, x, length);
   }

   @Override
   public void clearParameters() throws SQLException {
      logger.trace("{}.clearParameters()", statementID);
      preparedStatement.clearParameters();
   }

   @Override
   public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
      logger.trace("{}.setObject({}, {}, {})", statementID, parameterIndex, x, targetSqlType);
      preparedStatement.setObject(parameterIndex, x, targetSqlType);
   }

   @Override
   public void setObject(int parameterIndex, Object x) throws SQLException {
      logger.trace("{}.setObject({}, {})", statementID, parameterIndex, x);
      preparedStatement.setObject(parameterIndex, x);
   }

   @Override
   public boolean execute() throws SQLException {
      boolean b = preparedStatement.execute();
      logger.trace("{}.execute() = {}", statementID, b);
      return b;
   }

   @Override
   public void addBatch() throws SQLException {
      logger.trace("{}.addBatch()", statementID);
      preparedStatement.addBatch();
   }

   @Override
   public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
      logger.trace("{}.setCharacterStream({}, {}, {})", statementID, parameterIndex, reader, length);
      preparedStatement.setCharacterStream(parameterIndex, reader, length);
   }

   @Override
   public void setRef(int parameterIndex, Ref x) throws SQLException {
      logger.trace("{}.setRef({}, {})", statementID, parameterIndex, x);
      preparedStatement.setRef(parameterIndex, x);
   }

   @Override
   public void setBlob(int parameterIndex, Blob x) throws SQLException {
      logger.trace("{}.setBlob({}, {})", statementID, parameterIndex, x);
      preparedStatement.setBlob(parameterIndex, x);
   }

   @Override
   public void setClob(int parameterIndex, Clob x) throws SQLException {
      logger.trace("{}.setClob({}, {})", statementID, parameterIndex, x);
      preparedStatement.setClob(parameterIndex, x);
   }

   @Override
   public void setArray(int parameterIndex, Array x) throws SQLException {
      logger.trace("{}.setArray({}, {})", statementID, parameterIndex, x);
      preparedStatement.setArray(parameterIndex, x);
   }

   @Override
   public ResultSetMetaData getMetaData() throws SQLException {
      ResultSetMetaData resultSetMetaData = preparedStatement.getMetaData();
      logger.trace("{}.getMetaData() = {}", statementID, LoggingUtil.getID(resultSetMetaData));
      return resultSetMetaData;
   }

   @Override
   public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
      logger.trace("{}.setDate({}, {}, {})", statementID, parameterIndex, x, cal);
      preparedStatement.setDate(parameterIndex, x, cal);
   }

   @Override
   public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
      logger.trace("{}.setTime({}, {}, {})", statementID, parameterIndex, x, cal);
      preparedStatement.setTime(parameterIndex, x, cal);
   }

   @Override
   public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
      logger.trace("{}.setTimestamp({}, {}, {})", statementID, parameterIndex, x, cal);
      preparedStatement.setTimestamp(parameterIndex, x, cal);
   }

   @Override
   public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
      logger.trace("{}.setNull({}, {}, {})", statementID, parameterIndex, sqlType, typeName);
      preparedStatement.setNull(parameterIndex, sqlType, typeName);
   }

   @Override
   public void setURL(int parameterIndex, URL x) throws SQLException {
      logger.trace("{}.setURL({}, {})", statementID, parameterIndex, x);
      preparedStatement.setURL(parameterIndex, x);
   }

   @Override
   public ParameterMetaData getParameterMetaData() throws SQLException {
      ParameterMetaData x = preparedStatement.getParameterMetaData();
      logger.trace("{}.getParameterMetaData() = {}", statementID, x);
      return x;
   }

   @Override
   public void setRowId(int parameterIndex, RowId x) throws SQLException {
      logger.trace("{}.setRowId({}, {})", statementID, parameterIndex, x);
      preparedStatement.setRowId(parameterIndex, x);
   }

   @Override
   public void setNString(int parameterIndex, String value) throws SQLException {
      logger.trace("{}.setNString({}, {})", statementID, parameterIndex, value);
      preparedStatement.setNString(parameterIndex, value);
   }

   @Override
   public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
      logger.trace("{}.setNCharacterStream({}, {}, {})", statementID, parameterIndex, value, length);
      preparedStatement.setNCharacterStream(parameterIndex, value, length);
   }

   @Override
   public void setNClob(int parameterIndex, NClob value) throws SQLException {
      logger.trace("{}.setNClob({}, {})", statementID, parameterIndex, value);
      preparedStatement.setNClob(parameterIndex, value);
   }

   @Override
   public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
      logger.trace("{}.setClob({}, {}, {})", statementID, parameterIndex, reader, length);
      preparedStatement.setClob(parameterIndex, reader, length);
   }

   @Override
   public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
      logger.trace("{}.setBlob({}, {}, {})", statementID, parameterIndex, inputStream, length);
      preparedStatement.setBlob(parameterIndex, inputStream, length);
   }

   @Override
   public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
      logger.trace("{}.setNClob({}, {}, {})", statementID, parameterIndex, reader, length);
      preparedStatement.setNClob(parameterIndex, reader, length);
   }

   @Override
   public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
      logger.trace("{}.setSQLXML({}, {})", statementID, parameterIndex, xmlObject);
      preparedStatement.setSQLXML(parameterIndex, xmlObject);
   }

   @Override
   public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
      logger.trace("{}.setNull({}, {})", statementID, parameterIndex, x);
      preparedStatement.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
   }

   @Override
   public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
      logger.trace("{}.setNull({}, {})", statementID, parameterIndex, x);
      preparedStatement.setAsciiStream(parameterIndex, x, length);
   }

   @Override
   public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
      logger.trace("{}.setNull({}, {})", statementID, parameterIndex, x);
      preparedStatement.setBinaryStream(parameterIndex, x, length);
   }

   @Override
   public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
      logger.trace("{}.setCharacterStream({}, {}, {})", statementID, parameterIndex, reader, length);
      preparedStatement.setCharacterStream(parameterIndex, reader, length);
   }

   @Override
   public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
      logger.trace("{}.setAsciiStream({}, {})", statementID, parameterIndex, x);
      preparedStatement.setAsciiStream(parameterIndex, x);
   }

   @Override
   public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
      logger.trace("{}.setBinaryStream({}, {})", statementID, parameterIndex, x);
      preparedStatement.setBinaryStream(parameterIndex, x);
   }

   @Override
   public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
      logger.trace("{}.setCharacterStream({}, {})", statementID, parameterIndex, reader);
      preparedStatement.setCharacterStream(parameterIndex, reader);
   }

   @Override
   public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
      logger.trace("{}.setNCharacterStream({}, {})", statementID, parameterIndex, value);
      preparedStatement.setNCharacterStream(parameterIndex, value);
   }

   @Override
   public void setClob(int parameterIndex, Reader reader) throws SQLException {
      logger.trace("{}.setClob({}, {})", statementID, parameterIndex, reader);
      preparedStatement.setClob(parameterIndex, reader);
   }

   @Override
   public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
      logger.trace("{}.setBlob({}, {})", statementID, parameterIndex, inputStream);
      preparedStatement.setBlob(parameterIndex, inputStream);
   }

   @Override
   public void setNClob(int parameterIndex, Reader reader) throws SQLException {
      logger.trace("{}.setNClob({}, {})", statementID, parameterIndex, reader);
      preparedStatement.setNClob(parameterIndex, reader);
   }

   @Override
   public void setObject(int parameterIndex, Object x, SQLType targetSqlType, int scaleOrLength) throws SQLException {
      logger.trace("{}.setObject({}, {}, {}, {})", statementID, parameterIndex, x, targetSqlType, scaleOrLength);
      preparedStatement.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
   }

   @Override
   public void setObject(int parameterIndex, Object x, SQLType targetSqlType) throws SQLException {
      logger.trace("{}.setObject({}, {}, {})", statementID, parameterIndex, x, targetSqlType);
      preparedStatement.setObject(parameterIndex, x, targetSqlType);
   }

   @Override
   public long executeLargeUpdate() throws SQLException {
      long l = preparedStatement.executeLargeUpdate();
      logger.trace("{}.executeLargeUpdate() = {}", statementID, l);
      return l;
   }
}
