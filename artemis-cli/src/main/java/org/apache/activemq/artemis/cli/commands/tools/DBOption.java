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

package org.apache.activemq.artemis.cli.commands.tools;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStoreFactory;
import org.apache.activemq.artemis.core.paging.impl.PagingManagerImpl;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreFactoryDatabase;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreFactoryNIO;
import org.apache.activemq.artemis.core.persistence.impl.journal.JDBCJournalStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.apache.activemq.artemis.utils.critical.EmptyCriticalAnalyzer;
import picocli.CommandLine.Option;

public class DBOption extends OptionalLocking {

   protected JournalStorageManager storageManager;

   protected Configuration config;

   protected ExecutorService executor;

   protected ExecutorFactory executorFactory;

   protected ScheduledExecutorService scheduledExecutorService;

   @Option(names = "--output", description = "Output name for the file.")
   private File output;

   private OutputStream fileOutputStream;

   private PrintStream originalOut;

   @Option(names = "--jdbc", description = "Whether to store message data in JDBC instead of local files.")
   Boolean jdbc;

   @Option(names = "--jdbc-bindings-table-name", description = "Name of the jdbc bindings table.")
   private String jdbcBindings = ActiveMQDefaultConfiguration.getDefaultBindingsTableName();

   @Option(names = "--jdbc-message-table-name", description = "Name of the jdbc messages table.")
   private String jdbcMessages = ActiveMQDefaultConfiguration.getDefaultMessageTableName();

   @Option(names = "--jdbc-large-message-table-name", description = "Name of the large messages table.")
   private String jdbcLargeMessages = ActiveMQDefaultConfiguration.getDefaultLargeMessagesTableName();

   @Option(names = "--jdbc-page-store-table-name", description = "Name of the page store messages table.")
   private String jdbcPageStore = ActiveMQDefaultConfiguration.getDefaultPageStoreTableName();

   @Option(names = "--jdbc-node-manager-table-name", description = "Name of the jdbc node manager table.")
   private String jdbcNodeManager = ActiveMQDefaultConfiguration.getDefaultNodeManagerStoreTableName();

   @Option(names = "--jdbc-connection-url", description = "The URL used for the database connection.")
   private String jdbcURL = null;

   @Option(names = "--jdbc-driver-class-name", description = "JDBC driver classname.")
   private String jdbcClassName = ActiveMQDefaultConfiguration.getDefaultDriverClassName();

   @Option(names = "--jdbc-user", description = "JDBC username.")
   private String jdbcUser = null;

   @Option(names = "--jdbc-password", description = "JDBC password.")
   private String jdbcPassword = null;

   public boolean isJDBC() throws Exception {
      parseDBConfig();
      return jdbc;
   }

   public File getOutput() {
      return output;
   }

   public DBOption setOutput(File output) {
      this.output = output;
      return this;
   }

   public String getJdbcBindings() throws Exception {
      parseDBConfig();
      return jdbcBindings;
   }

   public DBOption setJdbcBindings(String jdbcBindings) {
      this.jdbcBindings = jdbcBindings;
      return this;
   }

   public String getJdbcMessages() throws Exception {
      parseDBConfig();
      return jdbcMessages;
   }

   public DBOption setJdbcMessages(String jdbcMessages) {
      this.jdbcMessages = jdbcMessages;
      return this;
   }

   public String getJdbcLargeMessages() throws Exception {
      parseDBConfig();
      return jdbcLargeMessages;
   }

   public DBOption setJdbcLargeMessages(String jdbcLargeMessages) {
      this.jdbcLargeMessages = jdbcLargeMessages;
      return this;
   }

   public String getJdbcPageStore() throws Exception {
      parseDBConfig();
      return jdbcPageStore;
   }

   public DBOption setJdbcPageStore(String jdbcPageStore) {
      this.jdbcPageStore = jdbcPageStore;
      return this;
   }

   public String getJdbcNodeManager() throws Exception {
      parseDBConfig();
      return jdbcNodeManager;
   }

   public DBOption setJdbcNodeManager(String jdbcNodeManager) {
      this.jdbcNodeManager = jdbcNodeManager;
      return this;
   }

   public String getJdbcURL() throws Exception {
      parseDBConfig();
      return jdbcURL;
   }

   public DBOption setJdbcURL(String jdbcURL) {
      this.jdbcURL = jdbcURL;
      return this;
   }

   public String getJdbcClassName() throws Exception {
      parseDBConfig();
      return jdbcClassName;
   }

   public DBOption setJdbcClassName(String jdbcClassName) {
      this.jdbcClassName = jdbcClassName;
      return this;
   }

   public String getJdbcUser() {
      return jdbcUser;
   }

   public DBOption setJdbcUser(String jdbcUser) {
      this.jdbcUser = jdbcUser;
      return this;
   }

   public String getJdbcPassword() {
      return jdbcPassword;
   }

   public DBOption setJdbcPassword(String jdbcPassword) {
      this.jdbcPassword = jdbcPassword;
      return this;
   }

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);

      if (output != null) {
         fileOutputStream = new BufferedOutputStream(new FileOutputStream(output));
         originalOut = context.out;
         PrintStream printStream = new PrintStream(fileOutputStream);
         context.out = printStream;
      }
      return null;
   }

   @Override
   public void done() {
      super.done();
      if (fileOutputStream != null) {
         try {
            fileOutputStream.close();
         } catch (Throwable e) {
            e.printStackTrace();
         }
         getActionContext().out = originalOut;
         fileOutputStream = null;
         originalOut = null;
      }
   }

   private void parseDBConfig() throws Exception {
      if (jdbc == null) {
         FileConfiguration fileConfiguration = getFileConfiguration();
         jdbc = fileConfiguration.isJDBC();
         if (jdbc) {
            DatabaseStorageConfiguration storageConfiguration = (DatabaseStorageConfiguration) fileConfiguration.getStoreConfiguration();
            jdbcBindings = storageConfiguration.getBindingsTableName();
            jdbcMessages = storageConfiguration.getMessageTableName();
            jdbcLargeMessages = storageConfiguration.getLargeMessageTableName();
            jdbcPageStore = storageConfiguration.getPageStoreTableName();
            jdbcNodeManager = storageConfiguration.getNodeManagerStoreTableName();

            if (jdbcURL == null || jdbcURL.equals(ActiveMQDefaultConfiguration.getDefaultDatabaseUrl())) {
               jdbcURL = storageConfiguration.getJdbcConnectionUrl();
            }

            if (jdbcClassName == null || jdbcClassName.equals(ActiveMQDefaultConfiguration.getDefaultDriverClassName())) {
               jdbcClassName = storageConfiguration.getJdbcDriverClassName();
            }

            if (jdbcUser == null) {
               jdbcUser = storageConfiguration.getJdbcUser();
            }
            if (jdbcPassword == null) {
               jdbcPassword = storageConfiguration.getJdbcPassword();
            }
         }
      }
   }

   // Get a new configuration based on the passed parameters and not on the parsed configuration
   public Configuration getParameterConfiguration() throws Exception {
      Configuration configuration = readConfiguration();
      if (isJDBC()) {
         DatabaseStorageConfiguration storageConfiguration = new DatabaseStorageConfiguration();
         storageConfiguration.setJdbcConnectionUrl(getJdbcURL());
         storageConfiguration.setJdbcDriverClassName(getJdbcClassName());
         storageConfiguration.setBindingsTableName(getJdbcBindings());
         storageConfiguration.setMessageTableName(getJdbcMessages());
         storageConfiguration.setLargeMessageTableName(getJdbcLargeMessages());
         storageConfiguration.setPageStoreTableName(getJdbcPageStore());
         storageConfiguration.setNodeManagerStoreTableName(getJdbcNodeManager());
         storageConfiguration.setJdbcUser(getJdbcUser());
         storageConfiguration.setJdbcPassword(getJdbcPassword());
         configuration.setStoreConfiguration(storageConfiguration);
      } else {
         configuration.setBindingsDirectory(getBinding());
         configuration.setJournalDirectory(getJournal());
         configuration.setPagingDirectory(getPaging());
         configuration.setLargeMessagesDirectory(getLargeMessages());
         configuration.setJournalType(JournalType.NIO);
      }

      return configuration;
   }

   protected PagingManager pagingmanager;

   protected void initializeJournal(Configuration configuration) throws Exception {
      this.config = configuration;
      executor = Executors.newFixedThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      executorFactory = new OrderedExecutorFactory(executor);

      scheduledExecutorService = new ScheduledThreadPoolExecutor(configuration.getScheduledThreadPoolMaxSize(), r -> new Thread(r));

      HierarchicalRepository<AddressSettings> addressSettingsRepository = new HierarchicalObjectRepository<>(config.getWildcardConfiguration());
      addressSettingsRepository.setDefault(new AddressSettings());

      if (configuration.isJDBC()) {
         storageManager = new JDBCJournalStorageManager(config, null, scheduledExecutorService, executorFactory, executorFactory, null);

         PagingStoreFactory pageStoreFactory = new PagingStoreFactoryDatabase((DatabaseStorageConfiguration) configuration.getStoreConfiguration(),
                                                                              storageManager, 1000L,
                                                                              scheduledExecutorService, executorFactory,
                                                                             false, null);
         pagingmanager = new PagingManagerImpl(pageStoreFactory, addressSettingsRepository, configuration.getManagementAddress());
      } else {
         storageManager = new JournalStorageManager(config, EmptyCriticalAnalyzer.getInstance(), executorFactory, executorFactory);
         PagingStoreFactory pageStoreFactory = new PagingStoreFactoryNIO(storageManager, config.getPagingLocation(), 1000L, scheduledExecutorService, executorFactory, true, null);
         pagingmanager = new PagingManagerImpl(pageStoreFactory, addressSettingsRepository, configuration.getManagementAddress());

      }

   }

   protected void cleanup() throws Exception {
      executor.shutdown();
      scheduledExecutorService.shutdown();

      storageManager.stop();
   }

}
