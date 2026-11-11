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
package org.apache.activemq.artemis.core.server.impl.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;

import org.apache.activemq.artemis.jdbc.store.drivers.AbstractJDBCDriver;
import org.apache.activemq.artemis.jdbc.store.drivers.JDBCConnectionProvider;
import org.apache.activemq.artemis.jdbc.store.sql.SQLProvider;
import org.apache.activemq.artemis.utils.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * JDBC implementation of a {@link SharedStateManager}.
 */
@SuppressWarnings("SynchronizeOnNonFinalField")
final class JdbcSharedStateManager extends AbstractJDBCDriver implements SharedStateManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private static final int MAX_SETUP_ATTEMPTS = 20;
   private final String holderId;
   private final long lockExpirationMillis;
   private final long queryTimeoutMillis;
   private final long allowedTimeDiff;
   private JdbcLeaseLock primaryLock;
   private JdbcLeaseLock backupLock;
   private String readNodeId;
   private String writeNodeId;
   private String initializeNodeId;
   private String readState;
   private String writeState;

   public static JdbcSharedStateManager usingConnectionProvider(String holderId,
                                                                long locksExpirationMillis,
                                                                long allowedTimeDiff,
                                                                JDBCConnectionProvider connectionProvider,
                                                                SQLProvider provider) {
      return usingConnectionProvider(holderId, locksExpirationMillis, allowedTimeDiff, -1, connectionProvider, provider);
   }

   public static JdbcSharedStateManager usingConnectionProvider(String holderId,
                                                                long locksExpirationMillis,
                                                                long queryTimeoutMillis,
                                                                long allowedTimeDiff,
                                                                JDBCConnectionProvider connectionProvider,
                                                                SQLProvider provider) {
      final JdbcSharedStateManager sharedStateManager = new JdbcSharedStateManager(holderId, locksExpirationMillis,
                                                                                   queryTimeoutMillis, allowedTimeDiff);
      sharedStateManager.setJdbcConnectionProvider(connectionProvider);
      sharedStateManager.setSqlProvider(provider);
      try {
         sharedStateManager.start();
         return sharedStateManager;
      } catch (SQLException e) {
         throw new IllegalStateException(e);
      }
   }

   @Override
   protected void createSchema() {
      try {
         createTable(sqlProvider.createNodeManagerStoreTableSQL(), sqlProvider.createNodeIdSQL(), sqlProvider.createStateSQL(), sqlProvider.createPrimaryLockSQL(), sqlProvider.createBackupLockSQL());
      } catch (SQLException e) {
         //no op: if a table already exists is not a problem in this case, the prepareStatements() call will fail right after it if the table is not correctly initialized
         logger.debug("Error while creating the schema of the JDBC shared state manager", e);
      }
   }

   static JdbcLeaseLock createPrimaryLock(String holderId,
                                          JDBCConnectionProvider connectionProvider,
                                          SQLProvider sqlProvider,
                                          long expirationMillis,
                                          long allowedTimeDiff) {
      return createPrimaryLock(holderId, connectionProvider, sqlProvider, expirationMillis, -1, allowedTimeDiff);
   }

   static JdbcLeaseLock createPrimaryLock(String holderId,
                                          JDBCConnectionProvider connectionProvider,
                                          SQLProvider sqlProvider,
                                          long expirationMillis,
                                          long queryTimeoutMillis,
                                          long allowedTimeDiff) {
      return new JdbcLeaseLock(holderId, connectionProvider, sqlProvider.tryAcquirePrimaryLockSQL(),
                               sqlProvider.tryReleasePrimaryLockSQL(), sqlProvider.renewPrimaryLockSQL(),
                               sqlProvider.isPrimaryLockedSQL(), sqlProvider.currentTimestampSQL(),
                               sqlProvider.currentTimestampTimeZoneId(), expirationMillis, queryTimeoutMillis,
                               "PRIMARY", allowedTimeDiff);
   }

   static JdbcLeaseLock createBackupLock(String holderId,
                                         JDBCConnectionProvider connectionProvider,
                                         SQLProvider sqlProvider,
                                         long expirationMillis,
                                         long queryTimeoutMillis,
                                         long allowedTimeDiff) {
      return new JdbcLeaseLock(holderId, connectionProvider, sqlProvider.tryAcquireBackupLockSQL(),
                               sqlProvider.tryReleaseBackupLockSQL(), sqlProvider.renewBackupLockSQL(),
                               sqlProvider.isBackupLockedSQL(), sqlProvider.currentTimestampSQL(),
                               sqlProvider.currentTimestampTimeZoneId(), expirationMillis, queryTimeoutMillis,
                               "BACKUP", allowedTimeDiff);
   }

   @Override
   protected void prepareStatements() {
      this.primaryLock = createPrimaryLock(this.holderId, this.connectionProvider, sqlProvider, lockExpirationMillis, queryTimeoutMillis, allowedTimeDiff);
      this.backupLock = createBackupLock(this.holderId, this.connectionProvider, sqlProvider, lockExpirationMillis, queryTimeoutMillis, allowedTimeDiff);
      this.readNodeId = sqlProvider.readNodeIdSQL();
      this.writeNodeId = sqlProvider.writeNodeIdSQL();
      this.initializeNodeId = sqlProvider.initializeNodeIdSQL();
      this.writeState = sqlProvider.writeStateSQL();
      this.readState = sqlProvider.readStateSQL();
   }

   private JdbcSharedStateManager(String holderId, long lockExpirationMillis, long queryTimeoutMillis, long allowedTimeDiff) {
      this.holderId = holderId;
      this.lockExpirationMillis = lockExpirationMillis;
      this.queryTimeoutMillis = queryTimeoutMillis;
      this.allowedTimeDiff = allowedTimeDiff;
   }

   @Override
   public LeaseLock primaryLock() {
      return this.primaryLock;
   }

   @Override
   public LeaseLock backupLock() {
      return this.backupLock;
   }

   private UUID rawReadNodeId(Connection connection) throws SQLException {
      try (PreparedStatement preparedStatement = connection.prepareStatement(this.readNodeId)) {
         try (ResultSet resultSet = preparedStatement.executeQuery()) {
            if (!resultSet.next()) {
               return null;
            } else {
               final String nodeId = resultSet.getString(1);
               if (nodeId != null) {
                  return new UUID(UUID.TYPE_TIME_BASED, UUID.stringToBytes(nodeId));
               } else {
                  return null;
               }
            }
         }
      }
   }

   @Override
   public UUID readNodeId() {
      try (Connection connection = connectionProvider.getConnection()) {
         try {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            final boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(true);
            try {
               return rawReadNodeId(connection);
            } finally {
               connection.setAutoCommit(autoCommit);
            }
         } catch (SQLException e) {
            throw new IllegalStateException(e);
         }
      } catch (SQLException e) {
         throw new IllegalStateException(e);
      }
   }

   @Override
   public void writeNodeId(UUID nodeId) {
      try (Connection connection = connectionProvider.getConnection()) {
         try {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            final boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(true);
            try {
               rawWriteNodeId(connection, nodeId);
            } finally {
               connection.setAutoCommit(autoCommit);
            }
         } catch (SQLException e) {
            throw new IllegalStateException(e);
         }
      } catch (SQLException e) {
         throw new IllegalStateException(e);
      }
   }

   private void rawWriteNodeId(Connection connection, UUID nodeId) throws SQLException {
      try (PreparedStatement preparedStatement = connection.prepareStatement(this.writeNodeId)) {
         preparedStatement.setString(1, nodeId.toString());
         if (preparedStatement.executeUpdate() != 1) {
            throw new IllegalStateException("can't write NodeId on the JDBC Node Manager Store!");
         }
      }
   }

   private boolean rawInitializeNodeId(Connection connection, UUID nodeId) throws SQLException {
      try (PreparedStatement preparedStatement = connection.prepareStatement(this.initializeNodeId)) {
         preparedStatement.setString(1, nodeId.toString());
         final int rows = preparedStatement.executeUpdate();
         assert rows <= 1;
         return rows > 0;
      }
   }

   @Override
   public UUID setup(Supplier<? extends UUID> nodeIdFactory) {
      SQLException lastError = null;
      try (Connection connection = connectionProvider.getConnection()) {
         final UUID newNodeId = nodeIdFactory.get();
         for (int attempts = 0; attempts < MAX_SETUP_ATTEMPTS; attempts++) {
            lastError = null;
            try {
               final UUID nodeId = initializeOrReadNodeId(connection, newNodeId);
               if (nodeId != null) {
                  return nodeId;
               }
            } catch (SQLException e) {
               logger.debug("Error while attempting to setup the NodeId", e);
               lastError = e;
            }
         }
      } catch (SQLException e) {
         lastError = e;
      }
      if (lastError != null) {
         logger.error("Unable to setup a NodeId on the JDBC shared state", lastError);
      } else {
         logger.error("Unable to setup a NodeId on the JDBC shared state");
      }
      throw new IllegalStateException("FAILED TO SETUP the JDBC Shared State NodeId");
   }

   private UUID initializeOrReadNodeId(Connection connection, final UUID newNodeId) throws SQLException {
      synchronized (connection) {
         connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
         final boolean autoCommit = connection.getAutoCommit();
         connection.setAutoCommit(false);
         try {
            final UUID nodeId;
            //optimistic try to initialize nodeId
            if (rawInitializeNodeId(connection, newNodeId)) {
               nodeId = newNodeId;
            } else {
               nodeId = rawReadNodeId(connection);
            }
            if (nodeId != null) {
               connection.commit();
               return nodeId;
            } else {
               //rawInitializeNodeId has failed just due to contention or nodeId wasn't committed yet
               connection.rollback();
               logger.debug("Rollback after failed to update NodeId to {} and haven't found any NodeId", newNodeId);
               return null;
            }
         } catch (SQLException e) {
            connection.rollback();
            logger.debug("Rollback while trying to update NodeId to {}", newNodeId, e);
            return null;
         } finally {
            connection.setAutoCommit(autoCommit);
         }
      }
   }

   private static State decodeState(String s) {
      if (s == null) {
         return State.NOT_STARTED;
      }
      return switch (s) {
         case "L" -> State.ACTIVE;
         case "F" -> State.FAILING_BACK;
         case "P" -> State.PAUSED;
         case "N" -> State.NOT_STARTED;
         default -> throw new IllegalStateException("unknown state [" + s + "]");
      };
   }

   private static String encodeState(State state) {
      return switch (state) {
         case ACTIVE -> "L";
         case FAILING_BACK -> "F";
         case PAUSED -> "P";
         case NOT_STARTED -> "N";
         default -> throw new IllegalStateException("unknown state [" + state + "]");
      };
   }

   @Override
   public State readState() {
      try (Connection connection = connectionProvider.getConnection()) {
         connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
         final boolean autoCommit = connection.getAutoCommit();
         connection.setAutoCommit(false);
         final State state;
         try (PreparedStatement preparedStatement = connection.prepareStatement(this.readState)) {
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
               if (!resultSet.next()) {
                  state = State.FIRST_TIME_START;
               } else {
                  state = decodeState(resultSet.getString(1));
               }
            }
            connection.commit();
            return state;
         } catch (SQLException ie) {
            connection.rollback();
            throw new IllegalStateException(ie);
         } finally {
            connection.setAutoCommit(autoCommit);
         }
      } catch (SQLException e) {
         throw new IllegalStateException(e);
      }
   }

   @Override
   public void writeState(State state) {
      final String encodedState = encodeState(state);
      try (Connection connection = connectionProvider.getConnection()) {
         connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
         final boolean autoCommit = connection.getAutoCommit();
         connection.setAutoCommit(false);
         try (PreparedStatement preparedStatement = connection.prepareStatement(this.writeState)) {
            preparedStatement.setString(1, encodedState);
            if (preparedStatement.executeUpdate() != 1) {
               throw new IllegalStateException("can't write state to the JDBC Node Manager Store!");
            }
            connection.commit();
         } catch (SQLException ie) {
            connection.rollback();
            connection.setAutoCommit(true);
            throw new IllegalStateException(ie);
         } finally {
            connection.setAutoCommit(autoCommit);
         }
      } catch (SQLException e) {
         throw new IllegalStateException(e);
      }
   }

   @Override
   public void stop() throws SQLException {
      //release all the managed resources inside the connection lock
      //synchronized (connection) {
      this.primaryLock.close();
      this.backupLock.close();
      super.stop();
      //}
   }

   @Override
   public void close() throws SQLException {
      stop();
   }
}
