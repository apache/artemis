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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;

import org.postgresql.PGConnection;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;

/**
 * Helper class for when the postresql driver is not directly availalbe.
 */
public class PostgresLargeObjectManager {

   /**
    * This mode indicates we want to write to an object
    */
   public static final int WRITE = 0x00020000;

   /**
    * This mode indicates we want to read an object
    */
   public static final int READ = 0x00040000;

   /**
    * This mode is the default. It indicates we want read and write access to a large object
    */
   public static final int READWRITE = READ | WRITE;

   private boolean shouldUseReflection;


   public PostgresLargeObjectManager() {
      try {
         this.getClass().getClassLoader().loadClass("org.postgresql.PGConnection");
         shouldUseReflection = false;
      } catch (ClassNotFoundException ex) {
         shouldUseReflection = true;
      }
   }

   public final Long createLO(Connection connection) throws SQLException {
      if (shouldUseReflection) {
         Object largeObjectManager = getLargeObjectManager(connection);
         try {
            Method method = largeObjectManager.getClass().getMethod("createLO");
            return (Long) method.invoke(largeObjectManager);
         } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new SQLException("Couldn't access org.postgresql.largeobject.LargeObjectManager", ex);
         }
      } else {
         return (connection.unwrap(PGConnection.class)).getLargeObjectAPI().createLO();
      }
   }

   public final void deleteLO(Connection connection, long oid) throws SQLException {
      Object largeObjectManager = getLargeObjectManager(connection);
      if (shouldUseReflection) {
         try {
            Method method = largeObjectManager.getClass().getMethod("delete", long.class);
            method.invoke(largeObjectManager, oid);
         } catch (Exception ex) {
            throw new SQLException("Couldn't access org.postgresql.largeobject.LargeObjectManager", ex);
         }
      } else {
         if (largeObjectManager != null) {
            ((LargeObjectManager) largeObjectManager).delete(oid);
         }
      }

   }

   public Object open(Connection connection, long oid, int mode) throws SQLException {
      if (shouldUseReflection) {
         Object largeObjectManager = getLargeObjectManager(connection);
         try {
            Method method = largeObjectManager.getClass().getMethod("open", long.class, int.class);
            return method.invoke(largeObjectManager, oid, mode);
         } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new SQLException("Couldn't access org.postgresql.largeobject.LargeObjectManager", ex);
         }
      } else {
         return (connection.unwrap(PGConnection.class)).getLargeObjectAPI().open(oid, mode);
      }
   }

   public int size(Object largeObject) throws SQLException {
      if (shouldUseReflection) {
         try {
            Method method = largeObject.getClass().getMethod("size");
            return (int) method.invoke(largeObject);
         } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new SQLException("Couldn't access org.postgresql.largeobject.LargeObject", ex);
         }
      } else {
         return ((LargeObject) largeObject).size();
      }
   }

   public void close(Object largeObject) throws SQLException {
      if (shouldUseReflection) {
         try {
            Method method = largeObject.getClass().getMethod("close");
            method.invoke(largeObject);
         } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new SQLException("Couldn't access org.postgresql.largeobject.LargeObject", ex);
         }
      } else {
         ((LargeObject) largeObject).close();
      }
   }

   public byte[] read(Object largeObject, int length) throws SQLException {
      if (shouldUseReflection) {
         try {
            Method method = largeObject.getClass().getMethod("read", int.class);
            return (byte[]) method.invoke(largeObject, length);
         } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new SQLException("Couldn't access org.postgresql.largeobject.LargeObject", ex);
         }
      } else {
         return ((LargeObject) largeObject).read(length);
      }
   }

   public void write(Object largeObject, byte[] data) throws SQLException {
      if (shouldUseReflection) {
         try {
            Method method = largeObject.getClass().getMethod("write", byte[].class);
            method.invoke(largeObject, data);
         } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new SQLException("Couldn't access org.postgresql.largeobject.LargeObject", ex);
         }
      } else {
         ((LargeObject) largeObject).write(data);
      }
   }

   public void seek(Object largeObject, int position) throws SQLException {
      if (shouldUseReflection) {
         try {
            Method method = largeObject.getClass().getMethod("seek", int.class);
            method.invoke(largeObject, position);
         } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new SQLException("Couldn't access org.postgresql.largeobject.LargeObject", ex);
         }
      } else {
         ((LargeObject) largeObject).seek(position);
      }
   }

   public void truncate(Object largeObject, int position) throws SQLException {
      if (shouldUseReflection) {
         try {
            Method method = largeObject.getClass().getMethod("truncate", int.class);
            method.invoke(largeObject, position);
         } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new SQLException("Couldn't access org.postgresql.largeobject.LargeObject", ex);
         }
      } else {
         ((LargeObject) largeObject).truncate(position);
      }
   }

   private Object getLargeObjectManager(Connection connection) throws SQLException {
      if (shouldUseReflection) {
         try {
            Connection conn = unwrap(connection);
            Method method = conn.getClass().getMethod("getLargeObjectAPI");
            return method.invoke(conn);
         } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new SQLException("Couldn't access org.postgresql.largeobject.LargeObjectManager", ex);
         }
      } else {
         return (connection.unwrap(PGConnection.class)).getLargeObjectAPI();
      }
   }

   public final Connection unwrap(Connection connection) throws SQLException {
      return unwrapIronJacamar(unwrapDbcp(unwrapDbcp2(unwrapSpring(connection.unwrap(Connection.class)))));
   }

   private Connection unwrapIronJacamar(Connection conn) {
      try {
         Method method = conn.getClass().getMethod("getUnderlyingConnection");
         return (Connection) method.invoke(conn);
      } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
         return conn;
      }
   }

   private Connection unwrapDbcp(Connection conn) {
      try {
         Method method = conn.getClass().getMethod("getDelegate");
         return (Connection) method.invoke(conn);
      } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
         return conn;
      }
   }

   private Connection unwrapDbcp2(Connection conn) {
      try {
         Method method = conn.getClass().getMethod("getInnermostDelegateInternal");
         return (Connection) method.invoke(conn);
      } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
         return conn;
      }
   }

   private Connection unwrapSpring(Connection conn) {
      try {
         Method method = conn.getClass().getMethod("getTargetConnection");
         return (Connection) method.invoke(conn);
      } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
         return conn;
      }
   }
}
