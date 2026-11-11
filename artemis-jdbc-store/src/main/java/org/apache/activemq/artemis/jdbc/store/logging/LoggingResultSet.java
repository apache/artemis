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
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Map;

import org.slf4j.Logger;


public class LoggingResultSet implements ResultSet {

   private final ResultSet resultSet;

   private final String resultSetID;

   private final Logger logger;

   public LoggingResultSet(ResultSet resultSet, Logger logger) {
      this.resultSet = resultSet;
      this.logger = logger;
      this.resultSetID = LoggingUtil.getID(resultSet);
   }

   public ResultSet getResultSet() {
      return resultSet;
   }

   public String getResultSetID() {
      return resultSetID;
   }

   @Override
   public boolean next() throws SQLException {
      boolean b = resultSet.next();
      logger.trace("{}.next() = {}", resultSetID, b);
      return b;
   }

   @Override
   public void close() throws SQLException {
      logger.trace("{}.close()", resultSetID);
      resultSet.close();
   }

   @Override
   public boolean wasNull() throws SQLException {
      boolean b = resultSet.wasNull();
      logger.trace("{}.wasNull() = {}", resultSetID, b);
      return b;
   }

   @Override
   public String getString(int columnIndex) throws SQLException {
      String x = resultSet.getString(columnIndex);
      logger.trace("{}.getString({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public boolean getBoolean(int columnIndex) throws SQLException {
      boolean x = resultSet.getBoolean(columnIndex);
      logger.trace("{}.getBoolean({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public byte getByte(int columnIndex) throws SQLException {
      byte x = resultSet.getByte(columnIndex);
      logger.trace("{}.getByte({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public short getShort(int columnIndex) throws SQLException {
      short x = resultSet.getShort(columnIndex);
      logger.trace("{}.getShort({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public int getInt(int columnIndex) throws SQLException {
      int x = resultSet.getInt(columnIndex);
      logger.trace("{}.getInt({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public long getLong(int columnIndex) throws SQLException {
      long x = resultSet.getLong(columnIndex);
      logger.trace("{}.getLong({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public float getFloat(int columnIndex) throws SQLException {
      float x = resultSet.getFloat(columnIndex);
      logger.trace("{}.getFloat({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public double getDouble(int columnIndex) throws SQLException {
      double x = resultSet.getDouble(columnIndex);
      logger.trace("{}.getDouble({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
      BigDecimal x = resultSet.getBigDecimal(columnIndex);
      logger.trace("{}.getBigDecimal({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public byte[] getBytes(int columnIndex) throws SQLException {
      byte[] x = resultSet.getBytes(columnIndex);
      logger.trace("{}.getBytes({}) = {}", resultSetID, columnIndex, Arrays.toString(x));
      return x;
   }

   @Override
   public Date getDate(int columnIndex) throws SQLException {
      Date x = resultSet.getDate(columnIndex);
      logger.trace("{}.getDate({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public Time getTime(int columnIndex) throws SQLException {
      Time x = resultSet.getTime(columnIndex);
      logger.trace("{}.getTime({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public Timestamp getTimestamp(int columnIndex) throws SQLException {
      Timestamp x = resultSet.getTimestamp(columnIndex);
      logger.trace("{}.getTimestamp({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public InputStream getAsciiStream(int columnIndex) throws SQLException {
      InputStream x = resultSet.getAsciiStream(columnIndex);
      logger.trace("{}.getAsciiStream({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public InputStream getUnicodeStream(int columnIndex) throws SQLException {
      InputStream x = resultSet.getUnicodeStream(columnIndex);
      logger.trace("{}.getUnicodeStream({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public InputStream getBinaryStream(int columnIndex) throws SQLException {
      InputStream x = resultSet.getBinaryStream(columnIndex);
      logger.trace("{}.getBinaryStream({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public String getString(String columnLabel) throws SQLException {
      String x = resultSet.getString(columnLabel);
      logger.trace("{}.getString({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public boolean getBoolean(String columnLabel) throws SQLException {
      boolean x = resultSet.getBoolean(columnLabel);
      logger.trace("{}.getBoolean({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public byte getByte(String columnLabel) throws SQLException {
      byte x = resultSet.getByte(columnLabel);
      logger.trace("{}.getByte({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public short getShort(String columnLabel) throws SQLException {
      short x = resultSet.getShort(columnLabel);
      logger.trace("{}.getShort({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public int getInt(String columnLabel) throws SQLException {
      int x = resultSet.getInt(columnLabel);
      logger.trace("{}.getInt({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public long getLong(String columnLabel) throws SQLException {
      long x = resultSet.getLong(columnLabel);
      logger.trace("{}.getLong({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public float getFloat(String columnLabel) throws SQLException {
      float x = resultSet.getFloat(columnLabel);
      logger.trace("{}.getFloat({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public double getDouble(String columnLabel) throws SQLException {
      double x = resultSet.getDouble(columnLabel);
      logger.trace("{}.getDouble({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
      BigDecimal x = resultSet.getBigDecimal(columnLabel);
      logger.trace("{}.getBigDecimal({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public byte[] getBytes(String columnLabel) throws SQLException {
      byte[] x = resultSet.getBytes(columnLabel);
      logger.trace("{}.getBytes({}) = {}", resultSetID, columnLabel, Arrays.toString(x));
      return x;
   }

   @Override
   public Date getDate(String columnLabel) throws SQLException {
      Date x = resultSet.getDate(columnLabel);
      logger.trace("{}.getDate({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public Time getTime(String columnLabel) throws SQLException {
      Time x = resultSet.getTime(columnLabel);
      logger.trace("{}.getTime({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public Timestamp getTimestamp(String columnLabel) throws SQLException {
      Timestamp x = resultSet.getTimestamp(columnLabel);
      logger.trace("{}.getTimestamp({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public InputStream getAsciiStream(String columnLabel) throws SQLException {
      InputStream x = resultSet.getAsciiStream(columnLabel);
      logger.trace("{}.getAsciiStream({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public InputStream getUnicodeStream(String columnLabel) throws SQLException {
      InputStream x = resultSet.getUnicodeStream(columnLabel);
      logger.trace("{}.getUnicodeStream({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public InputStream getBinaryStream(String columnLabel) throws SQLException {
      InputStream x = resultSet.getBinaryStream(columnLabel);
      logger.trace("{}.getBinaryStream({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public SQLWarning getWarnings() throws SQLException {
      SQLWarning x = resultSet.getWarnings();
      logger.trace("{}.getWarnings) = {}", resultSetID, x);
      return x;
   }

   @Override
   public void clearWarnings() throws SQLException {
      logger.trace("{}.clearWarnings()", resultSetID);
      resultSet.clearWarnings();
   }

   @Override
   public String getCursorName() throws SQLException {
      String x = resultSet.getCursorName();
      logger.trace("{}.getCursorName() = {}", resultSetID, x);
      return x;
   }

   @Override
   public ResultSetMetaData getMetaData() throws SQLException {
      ResultSetMetaData x = resultSet.getMetaData();
      logger.trace("{}.getMetaData() = {}", resultSetID, x);
      return x;
   }

   @Override
   public Object getObject(int columnIndex) throws SQLException {
      String x = resultSet.getString(columnIndex);
      logger.trace("{}.getString({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public Object getObject(String columnLabel) throws SQLException {
      Object x = resultSet.getObject(columnLabel);
      logger.trace("{}.getObject({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public int findColumn(String columnLabel) throws SQLException {
      int x = resultSet.findColumn(columnLabel);
      logger.trace("{}.findColumn({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public Reader getCharacterStream(int columnIndex) throws SQLException {
      Reader x = resultSet.getCharacterStream(columnIndex);
      logger.trace("{}.getCharacterStream({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public Reader getCharacterStream(String columnLabel) throws SQLException {
      Reader x = resultSet.getCharacterStream(columnLabel);
      logger.trace("{}.getCharacterStream({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
      BigDecimal x = resultSet.getBigDecimal(columnIndex);
      logger.trace("{}.getBigDecimal({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
      BigDecimal x = resultSet.getBigDecimal(columnLabel);
      logger.trace("{}.getBigDecimal({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public boolean isBeforeFirst() throws SQLException {
      boolean x = resultSet.isBeforeFirst();
      logger.trace("{}.isBeforeFirst() = {}", resultSetID, x);
      return x;
   }

   @Override
   public boolean isAfterLast() throws SQLException {
      boolean x = resultSet.isAfterLast();
      logger.trace("{}.isAfterLast() = {}", resultSetID, x);
      return x;
   }

   @Override
   public boolean isFirst() throws SQLException {
      boolean x = resultSet.isFirst();
      logger.trace("{}.isFirst() = {}", resultSetID, x);
      return x;
   }

   @Override
   public boolean isLast() throws SQLException {
      boolean x = resultSet.isLast();
      logger.trace("{}.isLast() = {}", resultSetID, x);
      return x;
   }

   @Override
   public void beforeFirst() throws SQLException {
      logger.trace("{}.beforeFirst()", resultSetID);
      resultSet.beforeFirst();
   }

   @Override
   public void afterLast() throws SQLException {
      logger.trace("{}.afterLast()", resultSetID);
      resultSet.afterLast();
   }

   @Override
   public boolean first() throws SQLException {
      boolean x = resultSet.first();
      logger.trace("{}.first() = {}", resultSetID, x);
      return x;
   }

   @Override
   public boolean last() throws SQLException {
      boolean x = resultSet.last();
      logger.trace("{}.last() = {}", resultSetID, x);
      return x;
   }

   @Override
   public int getRow() throws SQLException {
      int x = resultSet.getRow();
      logger.trace("{}.getRow() = {}", resultSetID, x);
      return x;
   }

   @Override
   public boolean absolute(int row) throws SQLException {
      boolean x = resultSet.absolute(row);
      logger.trace("{}.absolute({}) = {}", resultSetID, row, x);
      return x;
   }

   @Override
   public boolean relative(int rows) throws SQLException {
      boolean x = resultSet.relative(rows);
      logger.trace("{}.relative({}) = {}", resultSetID, rows, x);
      return x;
   }

   @Override
   public boolean previous() throws SQLException {
      boolean x = resultSet.previous();
      logger.trace("{}.previous() = {}", resultSetID, x);
      return x;
   }

   @Override
   public void setFetchDirection(int direction) throws SQLException {
      logger.trace("{}.setFetchDirection({})", resultSetID, direction);
      resultSet.setFetchDirection(direction);
   }

   @Override
   public int getFetchDirection() throws SQLException {
      int x = resultSet.getFetchDirection();
      logger.trace("{}.getFetchDirection() = {}", resultSetID, x);
      return x;
   }

   @Override
   public void setFetchSize(int rows) throws SQLException {
      logger.trace("{}.setFetchSize({})", resultSetID, rows);
      resultSet.setFetchSize(rows);
   }

   @Override
   public int getFetchSize() throws SQLException {
      int x = resultSet.getFetchSize();
      logger.trace("{}.getFetchSize() = {}", resultSetID, x);
      return x;
   }

   @Override
   public int getType() throws SQLException {
      int x = resultSet.getType();
      logger.trace("{}.getType() = {}", resultSetID, x);
      return x;
   }

   @Override
   public int getConcurrency() throws SQLException {
      int x = resultSet.getConcurrency();
      logger.trace("{}.getConcurrency() = {}", resultSetID, x);
      return x;
   }

   @Override
   public boolean rowUpdated() throws SQLException {
      boolean x = resultSet.rowUpdated();
      logger.trace("{}.rowUpdated() = {}", resultSetID, x);
      return x;
   }

   @Override
   public boolean rowInserted() throws SQLException {
      boolean x = resultSet.rowInserted();
      logger.trace("{}.rowInserted() = {}", resultSetID, x);
      return x;
   }

   @Override
   public boolean rowDeleted() throws SQLException {
      boolean x = resultSet.rowDeleted();
      logger.trace("{}.rowDeleted() = {}", resultSetID, x);
      return x;
   }

   @Override
   public void updateNull(int columnIndex) throws SQLException {
      logger.trace("{}.updateNull({})", resultSetID, columnIndex);
      resultSet.updateNull(columnIndex);
   }

   @Override
   public void updateBoolean(int columnIndex, boolean x) throws SQLException {
      logger.trace("{}.updateBoolean({}, {})", resultSetID, columnIndex, x);
      resultSet.updateBoolean(columnIndex, x);
   }

   @Override
   public void updateByte(int columnIndex, byte x) throws SQLException {
      logger.trace("{}.updateByte({}, {})", resultSetID, columnIndex, x);
      resultSet.updateByte(columnIndex, x);
   }

   @Override
   public void updateShort(int columnIndex, short x) throws SQLException {
      logger.trace("{}.updateShort({}, {})", resultSetID, columnIndex, x);
      resultSet.updateShort(columnIndex, x);
   }

   @Override
   public void updateInt(int columnIndex, int x) throws SQLException {
      logger.trace("{}.updateInt({}, {})", resultSetID, columnIndex, x);
      resultSet.updateInt(columnIndex, x);
   }

   @Override
   public void updateLong(int columnIndex, long x) throws SQLException {
      logger.trace("{}.updateLong({}, {})", resultSetID, columnIndex, x);
      resultSet.updateLong(columnIndex, x);
   }

   @Override
   public void updateFloat(int columnIndex, float x) throws SQLException {
      logger.trace("{}.updateFloat({}, {})", resultSetID, columnIndex, x);
      resultSet.updateFloat(columnIndex, x);
   }

   @Override
   public void updateDouble(int columnIndex, double x) throws SQLException {
      logger.trace("{}.updateDouble({}, {})", resultSetID, columnIndex, x);
      resultSet.updateDouble(columnIndex, x);
   }

   @Override
   public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
      logger.trace("{}.updateBigDecimal({}, {})", resultSetID, columnIndex, x);
      resultSet.updateBigDecimal(columnIndex, x);
   }

   @Override
   public void updateString(int columnIndex, String x) throws SQLException {
      logger.trace("{}.updateString({}, {})", resultSetID, columnIndex, x);
      resultSet.updateString(columnIndex, x);
   }

   @Override
   public void updateBytes(int columnIndex, byte[] x) throws SQLException {
      logger.trace("{}.updateBytes({}, {})", resultSetID, columnIndex, x);
      resultSet.updateBytes(columnIndex, x);
   }

   @Override
   public void updateDate(int columnIndex, Date x) throws SQLException {
      logger.trace("{}.updateDate({}, {})", resultSetID, columnIndex, x);
      resultSet.updateDate(columnIndex, x);
   }

   @Override
   public void updateTime(int columnIndex, Time x) throws SQLException {
      logger.trace("{}.updateTime({}, {})", resultSetID, columnIndex, x);
      resultSet.updateTime(columnIndex, x);
   }

   @Override
   public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
      logger.trace("{}.updateTimestamp({}, {})", resultSetID, columnIndex, x);
      resultSet.updateTimestamp(columnIndex, x);
   }

   @Override
   public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
      logger.trace("{}.updateAsciiStream({}, {}, {})", resultSetID, columnIndex, x, length);
      resultSet.updateAsciiStream(columnIndex, x, length);
   }

   @Override
   public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
      logger.trace("{}.updateBinaryStream({}, {}, {})", resultSetID, columnIndex, x, length);
      resultSet.updateBinaryStream(columnIndex, x, length);
   }

   @Override
   public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
      logger.trace("{}.updateCharacterStream({}, {}, {})", resultSetID, columnIndex, x, length);
      resultSet.updateCharacterStream(columnIndex, x, length);
   }

   @Override
   public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
      logger.trace("{}.updateObject({}, {}, {})", resultSetID, columnIndex, x, scaleOrLength);
      resultSet.updateObject(columnIndex, x, scaleOrLength);
   }

   @Override
   public void updateObject(int columnIndex, Object x) throws SQLException {
      logger.trace("{}.updateObject({}, {})", resultSetID, columnIndex, x);
      resultSet.updateObject(columnIndex, x);
   }

   @Override
   public void updateNull(String columnLabel) throws SQLException {
      logger.trace("{}.updateNull({})", resultSetID, columnLabel);
      resultSet.updateNull(columnLabel);
   }

   @Override
   public void updateBoolean(String columnLabel, boolean x) throws SQLException {
      logger.trace("{}.updateBoolean({}, {})", resultSetID, columnLabel, x);
      resultSet.updateBoolean(columnLabel, x);
   }

   @Override
   public void updateByte(String columnLabel, byte x) throws SQLException {
      logger.trace("{}.updateByte({}, {})", resultSetID, columnLabel, x);
      resultSet.updateByte(columnLabel, x);
   }

   @Override
   public void updateShort(String columnLabel, short x) throws SQLException {
      logger.trace("{}.updateShort({}, {})", resultSetID, columnLabel, x);
      resultSet.updateShort(columnLabel, x);
   }

   @Override
   public void updateInt(String columnLabel, int x) throws SQLException {
      logger.trace("{}.updateInt({}, {})", resultSetID, columnLabel, x);
      resultSet.updateInt(columnLabel, x);
   }

   @Override
   public void updateLong(String columnLabel, long x) throws SQLException {
      logger.trace("{}.updateLong({}, {})", resultSetID, columnLabel, x);
      resultSet.updateLong(columnLabel, x);
   }

   @Override
   public void updateFloat(String columnLabel, float x) throws SQLException {
      logger.trace("{}.updateFloat({}, {})", resultSetID, columnLabel, x);
      resultSet.updateFloat(columnLabel, x);
   }

   @Override
   public void updateDouble(String columnLabel, double x) throws SQLException {
      logger.trace("{}.updateDouble({}, {})", resultSetID, columnLabel, x);
      resultSet.updateDouble(columnLabel, x);
   }

   @Override
   public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
      logger.trace("{}.updateBigDecimal({}, {})", resultSetID, columnLabel, x);
      resultSet.updateBigDecimal(columnLabel, x);
   }

   @Override
   public void updateString(String columnLabel, String x) throws SQLException {
      logger.trace("{}.updateString({}, {})", resultSetID, columnLabel, x);
      resultSet.updateString(columnLabel, x);
   }

   @Override
   public void updateBytes(String columnLabel, byte[] x) throws SQLException {
      logger.trace("{}.updateBytes({}, {})", resultSetID, columnLabel, x);
      resultSet.updateBytes(columnLabel, x);
   }

   @Override
   public void updateDate(String columnLabel, Date x) throws SQLException {
      logger.trace("{}.updateDate({}, {})", resultSetID, columnLabel, x);
      resultSet.updateDate(columnLabel, x);
   }

   @Override
   public void updateTime(String columnLabel, Time x) throws SQLException {
      logger.trace("{}.updateTime({}, {})", resultSetID, columnLabel, x);
      resultSet.updateTime(columnLabel, x);
   }

   @Override
   public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {
      logger.trace("{}.updateTimestamp({}, {})", resultSetID, columnLabel, x);
      resultSet.updateTimestamp(columnLabel, x);
   }

   @Override
   public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
      logger.trace("{}.updateAsciiStream({}, {}, {})", resultSetID, columnLabel, x, length);
      resultSet.updateAsciiStream(columnLabel, x, length);
   }

   @Override
   public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException {
      logger.trace("{}.updateBinaryStream({}, {}, {})", resultSetID, columnLabel, x, length);
      resultSet.updateBinaryStream(columnLabel, x, length);
   }

   @Override
   public void updateCharacterStream(String columnLabel, Reader x, int length) throws SQLException {
      logger.trace("{}.updateCharacterStream({}, {}, {})", resultSetID, columnLabel, x, length);
      resultSet.updateCharacterStream(columnLabel, x, length);
   }

   @Override
   public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
      logger.trace("{}.updateObject({}, {}, {})", resultSetID, columnLabel, x, scaleOrLength);
      resultSet.updateObject(columnLabel, x, scaleOrLength);
   }

   @Override
   public void updateObject(String columnLabel, Object x) throws SQLException {
      logger.trace("{}.updateObject({}, {})", resultSetID, columnLabel, x);
      resultSet.updateObject(columnLabel, x);
   }

   @Override
   public void insertRow() throws SQLException {
      logger.trace("{}.insertRow()", resultSetID);
      resultSet.insertRow();
   }

   @Override
   public void updateRow() throws SQLException {
      logger.trace("{}.updateRow()", resultSetID);
      resultSet.updateRow();
   }

   @Override
   public void deleteRow() throws SQLException {
      logger.trace("{}.deleteRow()", resultSetID);
      resultSet.deleteRow();
   }

   @Override
   public void refreshRow() throws SQLException {
      logger.trace("{}.refreshRow()", resultSetID);
      resultSet.refreshRow();
   }

   @Override
   public void cancelRowUpdates() throws SQLException {
      logger.trace("{}.cancelRowUpdates()", resultSetID);
      resultSet.cancelRowUpdates();
   }

   @Override
   public void moveToInsertRow() throws SQLException {
      logger.trace("{}.moveToInsertRow()", resultSetID);
      resultSet.moveToInsertRow();
   }

   @Override
   public void moveToCurrentRow() throws SQLException {
      logger.trace("{}.moveToCurrentRow()", resultSetID);
      resultSet.moveToCurrentRow();
   }

   @Override
   public Statement getStatement() throws SQLException {
      Statement x = resultSet.getStatement();
      logger.trace("{}.getStatement() = {}", resultSetID, x);
      return x;
   }

   @Override
   public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
      Object x = resultSet.getObject(columnIndex, map);
      logger.trace("{}.getObject({}, {}) = {}", resultSetID, columnIndex, map, x);
      return x;
   }

   @Override
   public Ref getRef(int columnIndex) throws SQLException {
      Ref x = resultSet.getRef(columnIndex);
      logger.trace("{}.getRef({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public Blob getBlob(int columnIndex) throws SQLException {
      Blob x = resultSet.getBlob(columnIndex);
      logger.trace("{}.getBlob({}) = {} (length: {})", resultSetID, columnIndex, x, x == null ? null : x.length());
      return x;
   }

   @Override
   public Clob getClob(int columnIndex) throws SQLException {
      Clob x = resultSet.getClob(columnIndex);
      logger.trace("{}.getClob({}) = {} (length: {})", resultSetID, columnIndex, x, x == null ? null : x.length());
      return x;
   }

   @Override
   public Array getArray(int columnIndex) throws SQLException {
      Array x = resultSet.getArray(columnIndex);
      logger.trace("{}.getArray({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
      Object x = resultSet.getObject(columnLabel, map);
      logger.trace("{}.getObject({}, {}) = {}", resultSetID, columnLabel, map, x);
      return x;
   }

   @Override
   public Ref getRef(String columnLabel) throws SQLException {
      Ref x = resultSet.getRef(columnLabel);
      logger.trace("{}.getRef({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public Blob getBlob(String columnLabel) throws SQLException {
      Blob x = resultSet.getBlob(columnLabel);
      logger.trace("{}.getBlob({}) = {} (length: {})", resultSetID, columnLabel, x, x == null ? null : x.length());
      return x;
   }

   @Override
   public Clob getClob(String columnLabel) throws SQLException {
      Clob x = resultSet.getClob(columnLabel);
      logger.trace("{}.getClob({}) = {} (length: {})", resultSetID, columnLabel, x, x == null ? null : x.length());
      return x;
   }

   @Override
   public Array getArray(String columnLabel) throws SQLException {
      Array x = resultSet.getArray(columnLabel);
      logger.trace("{}.getArray({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public Date getDate(int columnLabel, Calendar cal) throws SQLException {
      Date x = resultSet.getDate(columnLabel, cal);
      logger.trace("{}.getDate({}) = {}", resultSetID, columnLabel, cal, x);
      return x;
   }

   @Override
   public Date getDate(String columnLabel, Calendar cal) throws SQLException {
      Date x = resultSet.getDate(columnLabel, cal);
      logger.trace("{}.getDate({}) = {}", resultSetID, columnLabel, cal, x);
      return x;
   }

   @Override
   public Time getTime(int columnLabel, Calendar cal) throws SQLException {
      Time x = resultSet.getTime(columnLabel, cal);
      logger.trace("{}.getTime({}) = {}", resultSetID, columnLabel, cal, x);
      return x;
   }

   @Override
   public Time getTime(String columnLabel, Calendar cal) throws SQLException {
      Time x = resultSet.getTime(columnLabel, cal);
      logger.trace("{}.getTime({}) = {}", resultSetID, columnLabel, cal, x);
      return x;
   }

   @Override
   public Timestamp getTimestamp(int columnLabel, Calendar cal) throws SQLException {
      Timestamp x = resultSet.getTimestamp(columnLabel, cal);
      logger.trace("{}.getTimestamp({}) = {}", resultSetID, columnLabel, cal, x);
      return x;
   }

   @Override
   public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
      Timestamp x = resultSet.getTimestamp(columnLabel, cal);
      logger.trace("{}.getTimestamp({}) = {}", resultSetID, columnLabel, cal, x);
      return x;
   }

   @Override
   public URL getURL(int columnLabel) throws SQLException {
      URL x = resultSet.getURL(columnLabel);
      logger.trace("{}.getURL({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public URL getURL(String columnLabel) throws SQLException {
      URL x = resultSet.getURL(columnLabel);
      logger.trace("{}.getURL({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public void updateRef(int columnIndex, Ref x) throws SQLException {
      logger.trace("{}.updateRef({}, {})", resultSetID, columnIndex, x);
      resultSet.updateRef(columnIndex, x);
   }

   @Override
   public void updateRef(String columnLabel, Ref x) throws SQLException {
      logger.trace("{}.updateRef({}, {})", resultSetID, columnLabel, x);
      resultSet.updateRef(columnLabel, x);
   }

   @Override
   public void updateBlob(int columnIndex, Blob x) throws SQLException {
      logger.trace("{}.updateBlob({}, {})", resultSetID, columnIndex, x);
      resultSet.updateBlob(columnIndex, x);
   }

   @Override
   public void updateBlob(String columnLabel, Blob x) throws SQLException {
      logger.trace("{}.updateBlob({}, {})", resultSetID, columnLabel, x);
      resultSet.updateBlob(columnLabel, x);
   }

   @Override
   public void updateClob(int columnIndex, Clob x) throws SQLException {
      logger.trace("{}.updateClob({}, {})", resultSetID, columnIndex, x);
      resultSet.updateClob(columnIndex, x);
   }

   @Override
   public void updateClob(String columnLabel, Clob x) throws SQLException {
      logger.trace("{}.updateClob({}, {})", resultSetID, columnLabel, x);
      resultSet.updateClob(columnLabel, x);
   }

   @Override
   public void updateArray(int columnIndex, Array x) throws SQLException {
      logger.trace("{}.updateArray({}, {})", resultSetID, columnIndex, x);
      resultSet.updateArray(columnIndex, x);
   }

   @Override
   public void updateArray(String columnLabel, Array x) throws SQLException {
      logger.trace("{}.updateArray({}, {})", resultSetID, columnLabel, x);
      resultSet.updateArray(columnLabel, x);
   }

   @Override
   public RowId getRowId(int columnIndex) throws SQLException {
      RowId x = resultSet.getRowId(columnIndex);
      logger.trace("{}.getRowId({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public RowId getRowId(String columnLabel) throws SQLException {
      RowId x = resultSet.getRowId(columnLabel);
      logger.trace("{}.getRowId({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public void updateRowId(int columnIndex, RowId x) throws SQLException {
      logger.trace("{}.updateRowId({}, {})", resultSetID, columnIndex, x);
      resultSet.updateRowId(columnIndex, x);
   }

   @Override
   public void updateRowId(String columnLabel, RowId x) throws SQLException {
      logger.trace("{}.updateRowId({}, {})", resultSetID, columnLabel, x);
      resultSet.updateRowId(columnLabel, x);
   }

   @Override
   public int getHoldability() throws SQLException {
      int x = resultSet.getHoldability();
      logger.trace("{}.getHoldability() = {}", resultSetID, x);
      return x;
   }

   @Override
   public boolean isClosed() throws SQLException {
      boolean x = resultSet.isClosed();
      logger.trace("{}.isClosed() = {}", resultSetID, x);
      return x;
   }

   @Override
   public void updateNString(int columnIndex, String x) throws SQLException {
      logger.trace("{}.updateNString({}, {})", resultSetID, columnIndex, x);
      resultSet.updateNString(columnIndex, x);
   }

   @Override
   public void updateNString(String columnLabel, String x) throws SQLException {
      logger.trace("{}.updateNString({}, {})", resultSetID, columnLabel, x);
      resultSet.updateNString(columnLabel, x);
   }

   @Override
   public void updateNClob(int columnIndex, NClob x) throws SQLException {
      logger.trace("{}.updateNClob({}, {})", resultSetID, columnIndex, x);
      resultSet.updateNClob(columnIndex, x);
   }

   @Override
   public void updateNClob(String columnLabel, NClob x) throws SQLException {
      logger.trace("{}.updateNClob({}, {})", resultSetID, columnLabel, x);
      resultSet.updateNClob(columnLabel, x);
   }

   @Override
   public NClob getNClob(int columnIndex) throws SQLException {
      NClob x = resultSet.getNClob(columnIndex);
      logger.trace("{}.getNClob({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public NClob getNClob(String columnLabel) throws SQLException {
      NClob x = resultSet.getNClob(columnLabel);
      logger.trace("{}.getNClob({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public SQLXML getSQLXML(int columnIndex) throws SQLException {
      SQLXML x = resultSet.getSQLXML(columnIndex);
      logger.trace("{}.getSQLXML({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public SQLXML getSQLXML(String columnLabel) throws SQLException {
      SQLXML x = resultSet.getSQLXML(columnLabel);
      logger.trace("{}.getSQLXML({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public void updateSQLXML(int columnIndex, SQLXML x) throws SQLException {
      logger.trace("{}.updateSQLXML({}, {})", resultSetID, columnIndex, x);
      resultSet.updateSQLXML(columnIndex, x);
   }

   @Override
   public void updateSQLXML(String columnLabel, SQLXML x) throws SQLException {
      logger.trace("{}.updateSQLXML({}, {})", resultSetID, columnLabel, x);
      resultSet.updateSQLXML(columnLabel, x);
   }

   @Override
   public String getNString(int columnIndex) throws SQLException {
      String x = resultSet.getNString(columnIndex);
      logger.trace("{}.getNString({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public String getNString(String columnLabel) throws SQLException {
      String x = resultSet.getNString(columnLabel);
      logger.trace("{}.getNString({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public Reader getNCharacterStream(int columnIndex) throws SQLException {
      Reader x = resultSet.getNCharacterStream(columnIndex);
      logger.trace("{}.getNCharacterStream({}) = {}", resultSetID, columnIndex, x);
      return x;
   }

   @Override
   public Reader getNCharacterStream(String columnLabel) throws SQLException {
      Reader x = resultSet.getNCharacterStream(columnLabel);
      logger.trace("{}.getNCharacterStream({}) = {}", resultSetID, columnLabel, x);
      return x;
   }

   @Override
   public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
      logger.trace("{}.updateNCharacterStream({}, {}, {})", resultSetID, columnIndex, x, length);
      resultSet.updateNCharacterStream(columnIndex, x, length);
   }

   @Override
   public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
      logger.trace("{}.updateNCharacterStream({}, {}, {})", resultSetID, columnLabel, reader, length);
      resultSet.updateNCharacterStream(columnLabel, reader, length);
   }

   @Override
   public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
      logger.trace("{}.updateAsciiStream({}, {}, {})", resultSetID, columnIndex, x, length);
      resultSet.updateAsciiStream(columnIndex, x, length);
   }

   @Override
   public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
      logger.trace("{}.updateBinaryStream({}, {}, {})", resultSetID, columnIndex, x, length);
      resultSet.updateBinaryStream(columnIndex, x, length);
   }

   @Override
   public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
      logger.trace("{}.updateCharacterStream({}, {}, {})", resultSetID, columnIndex, x, length);
      resultSet.updateCharacterStream(columnIndex, x, length);
   }

   @Override
   public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException {
      logger.trace("{}.updateAsciiStream({}, {}, {})", resultSetID, columnLabel, x, length);
      resultSet.updateAsciiStream(columnLabel, x, length);
   }

   @Override
   public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException {
      logger.trace("{}.updateBinaryStream({}, {}, {})", resultSetID, columnLabel, x, length);
      resultSet.updateBinaryStream(columnLabel, x, length);
   }

   @Override
   public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
      logger.trace("{}.updateCharacterStream({}, {}, {})", resultSetID, columnLabel, reader, length);
      resultSet.updateCharacterStream(columnLabel, reader);
   }

   @Override
   public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException {
      logger.trace("{}.updateBlob({}, {}, {})", resultSetID, columnIndex, inputStream, length);
      resultSet.updateBlob(columnIndex, inputStream, length);
   }

   @Override
   public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException {
      logger.trace("{}.updateBlob({}, {}, {})", resultSetID, columnLabel, inputStream, length);
      resultSet.updateBlob(columnLabel, inputStream, length);
   }

   @Override
   public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
      logger.trace("{}.updateClob({}, {}, {})", resultSetID, columnIndex, reader, length);
      resultSet.updateClob(columnIndex, reader, length);
   }

   @Override
   public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
      logger.trace("{}.updateClob({}, {}, {})", resultSetID, columnLabel, reader, length);
      resultSet.updateClob(columnLabel, reader, length);
   }

   @Override
   public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
      logger.trace("{}.updateNClob({}, {}, {})", resultSetID, columnIndex, reader, length);
      resultSet.updateNClob(columnIndex, reader, length);
   }

   @Override
   public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
      logger.trace("{}.updateNClob({}, {}, {})", resultSetID, columnLabel, reader, length);
      resultSet.updateNClob(columnLabel, reader, length);
   }

   @Override
   public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
      logger.trace("{}.updateNCharacterStream({}, {})", resultSetID, columnIndex, x);
      resultSet.updateNCharacterStream(columnIndex, x);
   }

   @Override
   public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
      logger.trace("{}.updateNCharacterStream({}, {})", resultSetID, columnLabel, reader);
      resultSet.updateNCharacterStream(columnLabel, reader);
   }

   @Override
   public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
      logger.trace("{}.updateAsciiStream({}, {})", resultSetID, columnIndex, x);
      resultSet.updateAsciiStream(columnIndex, x);
   }

   @Override
   public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
      logger.trace("{}.updateBinaryStream({}, {})", resultSetID, columnIndex, x);
      resultSet.updateBinaryStream(columnIndex, x);
   }

   @Override
   public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
      logger.trace("{}.updateCharacterStream({}, {})", resultSetID, columnIndex, x);
      resultSet.updateCharacterStream(columnIndex, x);
   }

   @Override
   public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
      logger.trace("{}.updateAsciiStream({}, {})", resultSetID, columnLabel, x);
      resultSet.updateAsciiStream(columnLabel, x);
   }

   @Override
   public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
      logger.trace("{}.updateBinaryStream({}, {})", resultSetID, columnLabel, x);
      resultSet.updateBinaryStream(columnLabel, x);
   }

   @Override
   public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
      logger.trace("{}.updateCharacterStream({}, {})", resultSetID, columnLabel, reader);
      resultSet.updateCharacterStream(columnLabel, reader);
   }

   @Override
   public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
      logger.trace("{}.updateBlob({}, {})", resultSetID, columnIndex, inputStream);
      resultSet.updateBlob(columnIndex, inputStream);
   }

   @Override
   public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
      logger.trace("{}.updateBlob({}, {})", resultSetID, columnLabel, inputStream);
      resultSet.updateBlob(columnLabel, inputStream);
   }

   @Override
   public void updateClob(int columnIndex, Reader reader) throws SQLException {
      logger.trace("{}.updateClob({}, {})", resultSetID, columnIndex, reader);
      resultSet.updateClob(columnIndex, reader);
   }

   @Override
   public void updateClob(String columnLabel, Reader reader) throws SQLException {
      logger.trace("{}.updateClob({}, {})", resultSetID, columnLabel, reader);
      resultSet.updateClob(columnLabel, reader);
   }

   @Override
   public void updateNClob(int columnIndex, Reader reader) throws SQLException {
      logger.trace("{}.updateNClob({}, {})", resultSetID, columnIndex, reader);
      resultSet.updateNClob(columnIndex, reader);
   }

   @Override
   public void updateNClob(String columnLabel, Reader reader) throws SQLException {
      logger.trace("{}.updateNClob({}, {})", resultSetID, columnLabel, reader);
      resultSet.updateNClob(columnLabel, reader);
   }

   @Override
   public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
      T x = resultSet.getObject(columnIndex, type);
      logger.trace("{}.getObject({}, {}) = {}", resultSetID, columnIndex, type, x);
      return x;
   }

   @Override
   public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
      T x = resultSet.getObject(columnLabel, type);
      logger.trace("{}.getObject({}, {}) = {}", resultSetID, columnLabel, type, x);
      return x;
   }

   @Override
   public void updateObject(int columnIndex, Object x, SQLType targetSqlType, int scaleOrLength) throws SQLException {
      logger.trace("{}.updateObject({}, {}, {}, {})", resultSetID, columnIndex, x, targetSqlType, scaleOrLength);
      resultSet.updateObject(columnIndex, x, targetSqlType);
   }

   @Override
   public void updateObject(String columnLabel,
                            Object x,
                            SQLType targetSqlType,
                            int scaleOrLength) throws SQLException {
      logger.trace("{}.updateObject({}, {}, {}, {})", resultSetID, columnLabel, x, targetSqlType, scaleOrLength);
      resultSet.updateObject(columnLabel, x, targetSqlType, scaleOrLength);
   }

   @Override
   public void updateObject(int columnIndex, Object x, SQLType targetSqlType) throws SQLException {
      logger.trace("{}.updateObject({}, {}, {})", resultSetID, columnIndex, x, targetSqlType);
      resultSet.updateObject(columnIndex, x, targetSqlType);
   }

   @Override
   public void updateObject(String columnLabel, Object x, SQLType targetSqlType) throws SQLException {
      logger.trace("{}.updateObject({}, {}, {})", resultSetID, columnLabel, x, targetSqlType);
      resultSet.updateObject(columnLabel, x, targetSqlType);
   }

   @Override
   public <T> T unwrap(Class<T> iface) throws SQLException {
      T x = resultSet.unwrap(iface);
      logger.trace("{}.unwrap({}) = {}", resultSetID, iface, x);
      return x;
   }

   @Override
   public boolean isWrapperFor(Class<?> iface) throws SQLException {
      boolean x = resultSet.isWrapperFor(iface);
      logger.trace("{}.isWrapperFor({}) = {}", resultSetID, iface, x);
      return x;
   }
}
