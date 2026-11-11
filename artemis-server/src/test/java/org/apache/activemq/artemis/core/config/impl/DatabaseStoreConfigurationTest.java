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
package org.apache.activemq.artemis.core.config.impl;

import static org.apache.activemq.artemis.jdbc.store.sql.PropertySQLProvider.Factory.SQLDialect.ORACLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jdbc.store.sql.PropertySQLProvider;
import org.apache.activemq.artemis.jdbc.store.sql.SQLProvider;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.junit.jupiter.api.Test;

public class DatabaseStoreConfigurationTest extends ServerTestBase {

   @Test
   public void databaseStoreConfigTest() throws Exception {
      Configuration configuration = createConfiguration("database-store-config.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      DatabaseStorageConfiguration storeConfiguration = (DatabaseStorageConfiguration) server.getConfiguration().getStoreConfiguration();
      assertEquals(StoreConfiguration.StoreType.DATABASE, storeConfiguration.getStoreType());
      assertEquals("sourcepassword", storeConfiguration.getJdbcUser());
      assertEquals("targetpassword", storeConfiguration.getJdbcPassword());
   }

   @Test
   public void databaseStoreConfigWithDataSourceTest() throws Exception {
      Configuration configuration = createConfiguration("database-store-with-data-source-config.xml");
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      DatabaseStorageConfiguration storeConfiguration = (DatabaseStorageConfiguration) server.getConfiguration().getStoreConfiguration();
      assertEquals(StoreConfiguration.StoreType.DATABASE, storeConfiguration.getStoreType());
      assertEquals("sourcepassword", storeConfiguration.getDataSourceProperty("username"));
      assertEquals("targetpassword", storeConfiguration.getDataSourceProperty("password"));
   }

   @Test
   public void testOracle12TableSize() {
      for (SQLProvider.DatabaseStoreType storeType : SQLProvider.DatabaseStoreType.values()) {
         Throwable rte = null;
         try {
            new PropertySQLProvider.Factory(ORACLE).create("_A_TABLE_NAME_THAT_IS_TOO_LONG_", storeType);
         } catch (Throwable t) {
            rte = t;
         }

         assertNotNull(rte);
         assertTrue(rte.getMessage().contains("The maximum name size for the " + storeType.name().toLowerCase() + " store table, when using Oracle12C is 30 characters."));
      }
   }

   protected Configuration createConfiguration(String fileName) throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager(fileName);
      deploymentManager.addDeployable(fc);

      deploymentManager.readConfiguration();

      // we need this otherwise the data folder will be located under activemq-server and not on the temporary directory
      fc.setPagingDirectory(getTestDir() + "/" + fc.getPagingDirectory());
      fc.setLargeMessagesDirectory(getTestDir() + "/" + fc.getLargeMessagesDirectory());
      fc.setJournalDirectory(getTestDir() + "/" + fc.getJournalDirectory());
      fc.setBindingsDirectory(getTestDir() + "/" + fc.getBindingsDirectory());

      return fc;
   }
}
