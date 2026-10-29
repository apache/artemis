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
package org.apache.activemq.artemis.core.config.storage;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.jdbc.store.drivers.JDBCConnectionProvider;
import org.apache.activemq.artemis.jdbc.store.drivers.JDBCDataSourceUtils;
import org.apache.activemq.artemis.jdbc.store.sql.SQLProvider;
import org.apache.artemis.database.DatabaseProvider;

public class DatabaseStorageConfiguration implements StoreConfiguration {

   private String messageTableName = ActiveMQDefaultConfiguration.getDefaultMessageTableName();

   private String bindingsTableName = ActiveMQDefaultConfiguration.getDefaultBindingsTableName();

   private String largeMessagesTableName = ActiveMQDefaultConfiguration.getDefaultLargeMessagesTableName();

   private String pageStoreTableName = ActiveMQDefaultConfiguration.getDefaultPageStoreTableName();

   private String nodeManagerStoreTableName = ActiveMQDefaultConfiguration.getDefaultNodeManagerStoreTableName();

   private String jdbcConnectionUrl = ActiveMQDefaultConfiguration.getDefaultDatabaseUrl();

   private String jdbcUser;

   private String jdbcPassword;

   private String jdbcDriverClassName = ActiveMQDefaultConfiguration.getDefaultDriverClassName();

   // mark that we created the datasource and it wasn't provided to us
   private boolean dataSourceCreated;

   private DataSource dataSource;

   private String dataSourceClassName = ActiveMQDefaultConfiguration.getDefaultDataSourceClassName();

   private Map<String, Object> dataSourceProperties = new HashMap();

   private JDBCConnectionProvider connectionProvider;

   private DatabaseProvider databaseProvider;

   private SQLProvider.Factory sqlProviderFactory;

   private int jdbcNetworkTimeout = ActiveMQDefaultConfiguration.getDefaultJdbcNetworkTimeout();

   private long jdbcLockRenewPeriodMillis = ActiveMQDefaultConfiguration.getDefaultJdbcLockRenewPeriodMillis();

   private long jdbcLockExpirationMillis = ActiveMQDefaultConfiguration.getDefaultJdbcLockExpirationMillis();

   private long jdbcLockAcquisitionTimeoutMillis = ActiveMQDefaultConfiguration.getDefaultJdbcLockAcquisitionTimeoutMillis();

   private long jdbcJournalSyncPeriodMillis = ActiveMQDefaultConfiguration.getDefaultJdbcJournalSyncPeriodMillis();

   private long jdbcAllowedTimeDiff = ActiveMQDefaultConfiguration.getDefaultJdbcAllowedTimeDiffMillis();

   private int maxPageSizeBytes = ActiveMQDefaultConfiguration.getDefaultJdbcMaxPageSizeBytes();

   private int databaseConnections = ActiveMQDefaultConfiguration.getDefaultDatabaseConnections();

   private int databaseMaxReadConnections = ActiveMQDefaultConfiguration.getDefaultDatabaseMaxReadConnections();

   private long databaseReadIdleTimeout = ActiveMQDefaultConfiguration.getDefaultDatabaseReadIdleTimeout();

   private int databaseMaxRetries = ActiveMQDefaultConfiguration.getDefaultDatabaseMaxRetries();

   private long databaseRetryIntervalMillis = ActiveMQDefaultConfiguration.getDefaultDatabaseRetryIntervalMillis();

   private long databaseFlushPeriodNanos = ActiveMQDefaultConfiguration.getDefaultDatabaseFlushPeriodNanos();

   private int maxPendingWrites = ActiveMQDefaultConfiguration.getDefaultDatabaseMaxPendingWrites();

   private boolean pageJoinFetch = ActiveMQDefaultConfiguration.getDefaultDatabasePageJoinFetch();

   private StoreType storeType = StoreType.DATABASE;

   @Override
   public StoreType getStoreType() {
      return storeType;
   }

   public DatabaseStorageConfiguration setStoreType(StoreType storeType) {
      this.storeType = storeType;
      return this;
   }

   public String getMessageTableName() {
      return messageTableName;
   }

   public void setMessageTableName(String messageTableName) {
      this.messageTableName = messageTableName;
   }

   public String getBindingsTableName() {
      return bindingsTableName;
   }

   public void setBindingsTableName(String bindingsTableName) {
      this.bindingsTableName = bindingsTableName;
   }

   public String getLargeMessageTableName() {
      return largeMessagesTableName;
   }

   public void setLargeMessageTableName(String largeMessagesTableName) {
      this.largeMessagesTableName = largeMessagesTableName;
   }

   public String getPageStoreTableName() {
      return pageStoreTableName;
   }

   public void setNodeManagerStoreTableName(String nodeManagerStoreTableName) {
      this.nodeManagerStoreTableName = nodeManagerStoreTableName;
   }

   public String getNodeManagerStoreTableName() {
      return nodeManagerStoreTableName;
   }

   public void setPageStoreTableName(String pageStoreTableName) {
      this.pageStoreTableName = pageStoreTableName;
   }

   public void setJdbcConnectionUrl(String jdbcConnectionUrl) {
      this.jdbcConnectionUrl = jdbcConnectionUrl;
   }

   public String getJdbcConnectionUrl() {
      return jdbcConnectionUrl;
   }

   public String getJdbcUser() {
      return jdbcUser;
   }

   public void setJdbcUser(String jdbcUser) {
      this.jdbcUser = jdbcUser;
   }

   public String getJdbcPassword() {
      return jdbcPassword;
   }

   public void setJdbcPassword(String jdbcPassword) {
      this.jdbcPassword = jdbcPassword;
   }

   public void setJdbcDriverClassName(String jdbcDriverClassName) {
      this.jdbcDriverClassName = jdbcDriverClassName;
   }

   public String getJdbcDriverClassName() {
      return jdbcDriverClassName;
   }

   public long getJdbcAllowedTimeDiff() {
      return jdbcAllowedTimeDiff;
   }

   public int getMaxPageSizeBytes() {
      return maxPageSizeBytes;
   }

   public DatabaseStorageConfiguration setMaxPageSizeBytes(int maxPageSizeBytes) {
      this.maxPageSizeBytes = maxPageSizeBytes;
      return this;
   }

   public int getDatabaseConnections() {
      return databaseConnections;
   }

   public DatabaseStorageConfiguration setDatabaseConnections(int databaseConnections) {
      this.databaseConnections = databaseConnections;
      return this;
   }

   public int getDatabaseMaxReadConnections() {
      return databaseMaxReadConnections;
   }

   public DatabaseStorageConfiguration setDatabaseMaxReadConnections(int databaseMaxReadConnections) {
      this.databaseMaxReadConnections = databaseMaxReadConnections;
      return this;
   }

   public long getDatabaseReadIdleTimeout() {
      return databaseReadIdleTimeout;
   }

   public DatabaseStorageConfiguration setDatabaseReadIdleTimeout(long databaseReadIdleTimeout) {
      this.databaseReadIdleTimeout = databaseReadIdleTimeout;
      return this;
   }

   public int getDatabaseMaxRetries() {
      return databaseMaxRetries;
   }

   public DatabaseStorageConfiguration setDatabaseMaxRetries(int databaseMaxRetries) {
      this.databaseMaxRetries = databaseMaxRetries;
      return this;
   }

   public long getDatabaseRetryIntervalMillis() {
      return databaseRetryIntervalMillis;
   }

   public DatabaseStorageConfiguration setDatabaseRetryIntervalMillis(long databaseRetryIntervalMillis) {
      this.databaseRetryIntervalMillis = databaseRetryIntervalMillis;
      return this;
   }

   public long getDatabaseFlushPeriodNanos() {
      return databaseFlushPeriodNanos;
   }

   public DatabaseStorageConfiguration setDatabaseFlushPeriodNanos(long databaseFlushPeriodNanos) {
      this.databaseFlushPeriodNanos = databaseFlushPeriodNanos;
      return this;
   }

   public int getMaxPendingWrites() {
      return maxPendingWrites;
   }

   public DatabaseStorageConfiguration setMaxPendingWrites(int maxPendingWrites) {
      this.maxPendingWrites = maxPendingWrites;
      return this;
   }

   public boolean isPageJoinFetch() {
      return pageJoinFetch;
   }

   public DatabaseStorageConfiguration setPageJoinFetch(boolean pageJoinFetch) {
      this.pageJoinFetch = pageJoinFetch;
      return this;
   }

   /**
    * The DataSource to use to store Artemis data in the data store (can be {@code null} if {@code jdbcConnectionUrl}
    * and {@code jdbcDriverClassName} are used instead).
    *
    * @return the DataSource used to store Artemis data in the JDBC data store
    */
   private DataSource getDataSource() {
      if (dataSource == null) {
         // the next settings are going to be applied only if the datasource is the default one
         if (ActiveMQDefaultConfiguration.getDefaultDataSourceClassName().equals(dataSourceClassName)) {
            // these default settings will be applied only if a custom configuration won't override them
            if (!dataSourceProperties.containsKey("driverClassName")) {
               addDataSourceProperty("driverClassName", jdbcDriverClassName);
            }
            if (!dataSourceProperties.containsKey("url")) {
               addDataSourceProperty("url", jdbcConnectionUrl);
            }
            if (!dataSourceProperties.containsKey("username")) {
               if (jdbcUser != null) {
                  addDataSourceProperty("username", jdbcUser);
               }
            }
            if (!dataSourceProperties.containsKey("password")) {
               if (jdbcPassword != null) {
                  addDataSourceProperty("password", jdbcPassword);
               }
            }
            if (!dataSourceProperties.containsKey("maxTotal")) {
               // Let the pool to have unbounded number of connections by default to prevent connection starvation
               addDataSourceProperty("maxTotal", "-1");
            }
            if (!dataSourceProperties.containsKey("poolPreparedStatements")) {
               // Let the pool to have unbounded number of cached prepared statements to save the initialization cost
               addDataSourceProperty("poolPreparedStatements", "true");
            }
         }
         dataSource = JDBCDataSourceUtils.getDataSource(dataSourceClassName, dataSourceProperties);
         dataSourceCreated = true;
      }
      return dataSource;
   }

   /**
    * Configure the DataSource to use to store Artemis data in the data store.
    */
   public void setDataSource(DataSource dataSource) {
      this.dataSource = dataSource;
   }

   public JDBCConnectionProvider getConnectionProvider() {
      if (connectionProvider == null) {
         // commons-dbcp2 doesn't support DataSource::getConnection(user, password)
         if (dataSourceClassName == ActiveMQDefaultConfiguration.getDefaultDataSourceClassName()) {
            connectionProvider = new JDBCConnectionProvider(getDataSource());
         } else {
            connectionProvider = new JDBCConnectionProvider(getDataSource(), getJdbcUser(), getJdbcPassword());
         }
      }
      return connectionProvider;
   }

   public DatabaseProvider getDatabaseProvider() throws SQLException {
      if (databaseProvider == null) {
         // commons-dbcp2 doesn't support DataSource::getConnection(user, password)
         if (dataSourceClassName == ActiveMQDefaultConfiguration.getDefaultDataSourceClassName()) {
            databaseProvider = new DatabaseProvider(getDataSource(), null, null);
         } else {
            databaseProvider = new DatabaseProvider(getDataSource(), getJdbcUser(), getJdbcPassword());
         }
      }
      return databaseProvider;
   }


   public DatabaseStorageConfiguration setConnectionProviderNetworkTimeout(Executor executor, int ms) {
      getConnectionProvider().setNetworkTimeout(executor, ms);
      return this;
   }

   public DatabaseStorageConfiguration clearConnectionProviderNetworkTimeout() {
      if (connectionProvider != null) {
         connectionProvider.setNetworkTimeout(null, -1);
      }
      return this;
   }

   public void addDataSourceProperty(String key, String value) {
      if (value.toLowerCase().equals("true") || value.toLowerCase().equals("false")) {
         dataSourceProperties.put(key, Boolean.parseBoolean(value.toLowerCase()));
      } else {
         try {
            int i = Integer.parseInt(value);
            dataSourceProperties.put(key, i);
         } catch (NumberFormatException nfe) {
            dataSourceProperties.put(key, value);
         }
      }
   }

   public Map<String, Object> getDataSourceProperties() {
      return dataSourceProperties;
   }

   public String getDataSourceProperty(String key) {
      return (String)dataSourceProperties.get(key);
   }

   public String getDataSourceClassName() {
      return dataSourceClassName;
   }

   public void setDataSourceClassName(String dataSourceClassName) {
      this.dataSourceClassName = dataSourceClassName;
   }

   /**
    * The {@link SQLProvider.Factory} used to communicate with the JDBC data store. It can be {@code null}. If the value
    * is {@code null} and {@code dataSource} is set, the
    * {@code {@link org.apache.activemq.artemis.jdbc.store.sql.PropertySQLProvider.Factory}} will be used, else the type
    * of the factory will be determined based on the {@code jdbcDriverClassName}.
    *
    * @return the factory used to communicate with the JDBC data store
    */
   public SQLProvider.Factory getSqlProviderFactory() {
      return sqlProviderFactory;
   }

   public void setSqlProvider(SQLProvider.Factory sqlProviderFactory) {
      this.sqlProviderFactory = sqlProviderFactory;
   }

   public int getJdbcNetworkTimeout() {
      return this.jdbcNetworkTimeout;
   }

   public void setJdbcNetworkTimeout(int jdbcNetworkTimeout) {
      this.jdbcNetworkTimeout = jdbcNetworkTimeout;
   }

   public long getJdbcLockRenewPeriodMillis() {
      return jdbcLockRenewPeriodMillis;
   }

   public void setJdbcLockRenewPeriodMillis(long jdbcLockRenewPeriodMillis) {
      this.jdbcLockRenewPeriodMillis = jdbcLockRenewPeriodMillis;
   }

   public long getJdbcLockExpirationMillis() {
      return jdbcLockExpirationMillis;
   }

   public void setJdbcLockExpirationMillis(long jdbcLockExpirationMillis) {
      this.jdbcLockExpirationMillis = jdbcLockExpirationMillis;
   }

   public long getJdbcLockAcquisitionTimeoutMillis() {
      return jdbcLockAcquisitionTimeoutMillis;
   }

   public void setJdbcLockAcquisitionTimeoutMillis(long jdbcLockAcquisitionTimeoutMillis) {
      this.jdbcLockAcquisitionTimeoutMillis = jdbcLockAcquisitionTimeoutMillis;
   }

   public long getJdbcJournalSyncPeriodMillis() {
      return jdbcJournalSyncPeriodMillis;
   }

   public void setJdbcJournalSyncPeriodMillis(long jdbcJournalSyncPeriodMillis) {
      this.jdbcJournalSyncPeriodMillis = jdbcJournalSyncPeriodMillis;
   }

   public void setJdbcAllowedTimeDiff(long jdbcAllowedTimeDiff) {
      this.jdbcAllowedTimeDiff = jdbcAllowedTimeDiff;
   }

   @Override
   public int getAllowedPageSize(int pageSize) {
      return Math.min(pageSize, maxPageSizeBytes);
   }

   public void checkDatasource() throws Exception {
      if (dataSourceCreated && dataSource instanceof AutoCloseable) {
         ((AutoCloseable) dataSource).close();
      }
      dataSourceCreated = false;
      dataSource = null;
      databaseProvider = null;
      connectionProvider = null;
   }

}
