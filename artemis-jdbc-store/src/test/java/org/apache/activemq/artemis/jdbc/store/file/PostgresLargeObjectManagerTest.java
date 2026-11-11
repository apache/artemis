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

import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class PostgresLargeObjectManagerTest {

   @Test
   public void testShouldNotUseReflection() throws SQLException {
      PostgresLargeObjectManager manager = new PostgresLargeObjectManager();
      try {
         manager.createLO(new MockConnection());
         fail("Shouldn't be using reflection");
      } catch (ClassCastException ex) {
      }
   }

   @Test
   public void testShouldUseReflection() throws Exception {
      ClassLoader loader = new FunkyClassLoader();
      Class funkyClass = loader.loadClass("org.apache.activemq.artemis.jdbc.store.file.PostgresLargeObjectManager");
      Object manager = funkyClass.getConstructor().newInstance();
      try {
         funkyClass.getMethod("createLO", Connection.class).invoke(manager, new MockConnection());
         fail("Shouldn't be using reflection");
      } catch (java.lang.reflect.InvocationTargetException ex) {
         assertEquals("Couldn't access org.postgresql.largeobject.LargeObjectManager", ex.getCause().getMessage());
      }
   }

   private static final class FunkyClassLoader extends URLClassLoader {

      private FunkyClassLoader() {
         super(new URL[]{
            FunkyClassLoader.class.getProtectionDomain().getCodeSource().getLocation(),
            PostgresLargeObjectManager.class.getProtectionDomain().getCodeSource().getLocation()
         },
              ClassLoader.getSystemClassLoader());
      }

      @Override
      public Class<?> loadClass(String name) throws ClassNotFoundException {
         if ("org.postgresql.PGConnection".equals(name)) {
            throw new ClassNotFoundException(name);
         }
         if ("org.apache.activemq.artemis.jdbc.store.file.PostgresLargeObjectManager".equals(name)) {
            return this.findClass(name);
         }
         return super.loadClass(name);
      }
   }

   private static final class MockConnection implements Connection {

      @Override
      public Statement createStatement() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public PreparedStatement prepareStatement(String sql) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public CallableStatement prepareCall(String sql) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public String nativeSQL(String sql) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void setAutoCommit(boolean autoCommit) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public boolean getAutoCommit() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void commit() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void rollback() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void close() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public boolean isClosed() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public DatabaseMetaData getMetaData() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void setReadOnly(boolean readOnly) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public boolean isReadOnly() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void setCatalog(String catalog) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public String getCatalog() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void setTransactionIsolation(int level) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public int getTransactionIsolation() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public SQLWarning getWarnings() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void clearWarnings() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public Map<String, Class<?>> getTypeMap() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void setHoldability(int holdability) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public int getHoldability() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public Savepoint setSavepoint() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public Savepoint setSavepoint(String name) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void rollback(Savepoint savepoint) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void releaseSavepoint(Savepoint savepoint) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public Clob createClob() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public Blob createBlob() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public NClob createNClob() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public SQLXML createSQLXML() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public boolean isValid(int timeout) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void setClientInfo(String name, String value) throws SQLClientInfoException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void setClientInfo(Properties properties) throws SQLClientInfoException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public String getClientInfo(String name) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public Properties getClientInfo() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void setSchema(String schema) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public String getSchema() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void abort(Executor executor) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public int getNetworkTimeout() throws SQLException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public <T> T unwrap(Class<T> iface) throws SQLException {
         return (T) this;
      }

      @Override
      public boolean isWrapperFor(Class<?> iface) throws SQLException {
         return false;
      }
   }
}
