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

package org.apache.activemq.artemis.tests.db.common;

import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ExtendWith(ParameterizedTestExtension.class)
public abstract class ParameterDBTestBase extends DBTestBase {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected void dropDerby() throws Exception {
      cleanupData(database.getName());
   }

   @Override
   protected final String getTestJDBCConnectionUrl() {
      return database.getJdbcURI();
   }

   int postgres_loCount;

   protected int countPostgresLargeObjects() {
      try (java.sql.Connection jdbcConnection = database.getConnection()) {
         ResultSet resultSet = jdbcConnection.createStatement().executeQuery("select count(*) from pg_largeobject");
         resultSet.next();
         return resultSet.getInt(1);
      } catch (Exception e) {
         // not throwing a SQLException as this method is used on Supplier<String>
         // however if an exception still happened, the test should receive an error
         throw new RuntimeException(e.getMessage(), e);
      }
   }
   public void prepareCheckPostgres() throws Exception {
      postgres_loCount = countPostgresLargeObjects();
   }

   public void checkPostgres() throws Exception {
      Wait.assertTrue(() -> "There are still " + countPostgresLargeObjects() + " pg_largeObject records", () -> countPostgresLargeObjects() - postgres_loCount < 10, 60_000, 100);
   }


   @Parameter(index = 0)
   public Database database;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
      disableCheckThread();
      if (database == Database.DERBY) {
         runAfter(this::shutdownDerby);
      }

      registerDB();

      dropDatabase();
   }

   // Register the database on driver and prepares the classLoader to be used
   private void registerDB() throws Exception {
      ClassLoader dbClassLoader = database.getDBClassLoader();
      if (dbClassLoader != null) {
         ClassLoader tccl = Thread.currentThread().getContextClassLoader();
         Thread.currentThread().setContextClassLoader(dbClassLoader);
         final Thread currentThread = Thread.currentThread();
         runAfter((() -> {
            currentThread.setContextClassLoader(tccl);
         }));
         database.registerDriver();
      }
   }


   protected static List<Object[]> convertParameters(List<Database> dbList) {
      List<Object[]> parameters = new ArrayList<>();
      dbList.forEach(s -> {
         logger.info("Adding {} to the list for the test", s);
         parameters.add(new Object[]{s});
      });

      return parameters;
   }

   protected ClassLoader getDBClassLoader() throws Exception {
      return database.getDBClassLoader();
   }

   public Connection getConnection() throws Exception {
      return database.getConnection();
   }

   public int dropDatabase() {
      if (database == Database.POSTGRES) {
         try (Connection connection = getConnection()) {
            logger.info("Removing large objects on postgres");
            connection.createStatement().execute("DELETE FROM pg_largeobject");
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
         }
      }

      return switch (database) {
         case JOURNAL -> 0;
         case DERBY -> {
            try {
               logger.info("Drop derby");
               dropDerby();
            } catch (Exception e) {
               logger.debug("Error dropping derby db: {}", e.getMessage());
            }
            yield 1;
         }
         default -> dropTables("MESSAGE", "LARGE_MESSAGE", "PAGE_STORE", "NODE_MANAGER", "BINDING");
      };
   }


   private int dropTables(String...tables) {
      int dropped = 0;

      try (Connection connection = getConnection()) {
         ResultSet data = connection.getMetaData().getTables(null, "%", "%", new String[]{"TABLE"});
         while (data.next()) {
            String table = data.getString("TABLE_NAME");

            logger.debug("Checking table {}", table);

            boolean drop = false;
            for (String str : tables) {
               logger.debug("Checking pattern {}", str);
               if (table.toUpperCase(Locale.ROOT).contains(str)) {
                  logger.debug("Table {} is part of the list as it is matching", table, str);
                  drop = true;
                  break;
               }
            }

            if (drop) {
               if (dropTable(connection, table)) {
                  dropped++;
               }
            }
         }
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         return -1;
      }

      return dropped;
   }

   private boolean dropTable(Connection connection, String table) {
      try {
         connection.createStatement().execute("DROP TABLE " + table);
         logger.debug("Dropped {}", table);
         return true;
      } catch (Exception e) {
         logger.warn("Error dropping {} -> {}", table, e.getMessage());
         return false;
      }
   }


   @Override
   protected Configuration createDefaultConfig(final int serverID, final boolean netty) throws Exception {
      Configuration configuration = super.createDefaultConfig(serverID, netty);
      if (database != Database.JOURNAL) {
         setDBStoreType(configuration);
      }
      configuration.getAddressSettings().clear();
      return configuration;
   }

   @Override
   protected DatabaseStorageConfiguration createDefaultDatabaseStorageConfiguration() {
      DatabaseStorageConfiguration dbStorageConfiguration = new DatabaseStorageConfiguration();
      String connectionURI = getTestJDBCConnectionUrl();
      dbStorageConfiguration.setJdbcDriverClassName(database.getDriverClass());
      dbStorageConfiguration.setJdbcConnectionUrl(connectionURI);
      dbStorageConfiguration.setBindingsTableName("BINDINGS");
      dbStorageConfiguration.setMessageTableName("MESSAGES");
      dbStorageConfiguration.setLargeMessageTableName("LARGE_MESSAGES");
      dbStorageConfiguration.setPageStoreTableName("PAGE_STORE");
      dbStorageConfiguration.setJdbcPassword(getJDBCPassword());
      dbStorageConfiguration.setJdbcUser(getJDBCUser());
      dbStorageConfiguration.setJdbcLockAcquisitionTimeoutMillis(getJdbcLockAcquisitionTimeoutMillis());
      dbStorageConfiguration.setJdbcLockExpirationMillis(getJdbcLockExpirationMillis());
      dbStorageConfiguration.setJdbcLockRenewPeriodMillis(getJdbcLockRenewPeriodMillis());
      dbStorageConfiguration.setJdbcNetworkTimeout(-1);
      dbStorageConfiguration.setJdbcAllowedTimeDiff(250L);
      return dbStorageConfiguration;
   }



}
