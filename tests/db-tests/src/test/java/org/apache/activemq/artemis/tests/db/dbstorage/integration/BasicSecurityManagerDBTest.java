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
package org.apache.activemq.artemis.tests.db.dbstorage.integration;

import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.config.PersistedRole;
import org.apache.activemq.artemis.core.persistence.config.PersistedUser;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.spi.core.security.ActiveMQBasicSecurityManager;
import org.apache.activemq.artemis.tests.db.common.Database;
import org.apache.activemq.artemis.tests.db.common.ParameterDBTestBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisabledIf("isNoDatabaseSelected")
@ExtendWith(ParameterizedTestExtension.class)
public class BasicSecurityManagerDBTest extends ParameterDBTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected Configuration configuration;
   protected DatabaseStorageConfiguration storageConfiguration;

   @Parameters(name = "db={0}")
   public static Collection<Object[]> parameters() {
      List<Database> dbList = new ArrayList<>(Database.selectedList());
      if (!dbList.contains(Database.JOURNAL)) {
         dbList.add(Database.JOURNAL);
      }
      return convertParameters(dbList);
   }

   public static boolean isNoDatabaseSelected() {
      return parameters().isEmpty();
   }

   @Override
   protected final String getJDBCClassName() {
      return database.getDriverClass();
   }

   @BeforeEach
   public void setupTest() throws Exception {
      if (database != Database.JOURNAL) {
         storageConfiguration = createDefaultDatabaseStorageConfiguration();
         storageConfiguration.setStoreType(StoreConfiguration.StoreType.NEW_DATABASE);
         this.configuration = createDefaultNettyConfig();
         this.configuration.setStoreConfiguration(storageConfiguration);
      } else {
         this.configuration = createDefaultNettyConfig();
      }
      configuration.addAddressConfiguration(new CoreAddressConfiguration()
         .setName("DLQ")
         .addRoutingType(RoutingType.ANYCAST)
         .addQueueConfiguration(QueueConfiguration.of("DLQ").setRoutingType(RoutingType.ANYCAST)));
      configuration.addAddressConfiguration(new CoreAddressConfiguration()
         .setName("ExpiryQueue")
         .addRoutingType(RoutingType.ANYCAST)
         .addQueueConfiguration(QueueConfiguration.of("ExpiryQueue").setRoutingType(RoutingType.ANYCAST)));
   }

   private ActiveMQBasicSecurityManager createSecurityManager() {
      Map<String, String> initProperties = new HashMap<>();
      initProperties.put(ActiveMQBasicSecurityManager.BOOTSTRAP_USER, "admin");
      initProperties.put(ActiveMQBasicSecurityManager.BOOTSTRAP_PASSWORD, "admin123");
      initProperties.put(ActiveMQBasicSecurityManager.BOOTSTRAP_ROLE, "admins");
      return new ActiveMQBasicSecurityManager().init(initProperties);
   }

   private ActiveMQServer createSecuredServer() throws Exception {
      configuration.setSecurityEnabled(true);
      ActiveMQBasicSecurityManager securityManager = createSecurityManager();
      return addServer(ActiveMQServers.newActiveMQServer(configuration, ManagementFactory.getPlatformMBeanServer(), securityManager, true));
   }

   private void assertNoSQLExceptions(AssertionLoggerHandler loggerHandler) {
      assertFalse(loggerHandler.findTrace("ORA-0001"));
      assertFalse(loggerHandler.findTrace("java.sql.BatchUpdateException"));
      assertFalse(loggerHandler.findTrace("SQLException"));
   }

   // --- Authentication tests ---

   @TestTemplate
   public void testBootstrapUserAuthentication() throws Exception {
      ActiveMQServer server = createSecuredServer();

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();

         ServerLocator locator = createNettyNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);

         ClientSession session = sf.createSession("admin", "admin123", false, true, true, false, 0);
         session.close();

         assertNoSQLExceptions(loggerHandler);
      }
   }

   @TestTemplate
   public void testBootstrapBadPasswordRejected() throws Exception {
      ActiveMQServer server = createSecuredServer();

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();

         ServerLocator locator = createNettyNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);

         try {
            sf.createSession("admin", "wrongpassword", false, true, true, false, 0);
            fail("should have thrown exception");
         } catch (ActiveMQException e) {
            // expected
         }

         assertNoSQLExceptions(loggerHandler);
      }
   }

   @TestTemplate
   public void testAuthenticationForAddedUserHashed() throws Exception {
      internalTestAuthenticationForAddedUser(false);
   }

   @TestTemplate
   public void testAuthenticationForAddedUserPlainText() throws Exception {
      internalTestAuthenticationForAddedUser(true);
   }

   private void internalTestAuthenticationForAddedUser(boolean plaintext) throws Exception {
      ActiveMQServer server = createSecuredServer();

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();

         server.getActiveMQServerControl().addUser("foo", "bar", "baz", plaintext);

         ServerLocator locator = createNettyNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);

         ClientSession session = sf.createSession("foo", "bar", false, true, true, false, 0);
         session.close();

         assertNoSQLExceptions(loggerHandler);
      }
   }

   @TestTemplate
   public void testWithValidatedUser() throws Exception {
      ActiveMQServer server = createSecuredServer();
      server.getConfiguration().setPopulateValidatedUser(true);
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("admins", true, true, true, true, true, true, true, true, true, true, false, false));
      server.getConfiguration().putSecurityRoles("#", roles);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();

         server.createQueue(QueueConfiguration.of("queue").setAddress("address").setRoutingType(RoutingType.ANYCAST));

         ServerLocator locator = createNettyNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession("admin", "admin123", false, true, true, false, 0);

         ClientProducer producer = session.createProducer("address");
         producer.send(session.createMessage(true));
         session.commit();
         producer.close();

         ClientConsumer consumer = session.createConsumer("queue");
         session.start();
         ClientMessage message = consumer.receive(5000);
         assertNotNull(message);
         assertEquals("admin", message.getValidatedUserID());
         session.close();

         assertNoSQLExceptions(loggerHandler);
      }
   }

   // --- User management tests ---

   @TestTemplate
   public void testAddUserAndAuthenticate() throws Exception {
      ActiveMQServer server = createSecuredServer();

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();

         server.getActiveMQServerControl().addUser("testuser", "testpass", "users", false);

         ServerLocator locator = createNettyNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);

         ClientSession session = sf.createSession("testuser", "testpass", false, true, true, false, 0);
         session.close();

         assertFalse(loggerHandler.findText("AMQ232003"));
         assertFalse(loggerHandler.findText("AMQ232001"));
         assertNoSQLExceptions(loggerHandler);
      }
   }

   @TestTemplate
   public void testRemoveUser() throws Exception {
      ActiveMQServer server = createSecuredServer();

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();

         server.getActiveMQServerControl().addUser("tempuser", "temppass", "users", false);

         ServerLocator locator = createNettyNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);

         ClientSession session = sf.createSession("tempuser", "temppass", false, true, true, false, 0);
         session.close();

         server.getActiveMQServerControl().removeUser("tempuser");
         server.getActiveMQServerControl().clearAuthorizationCache();
         server.getActiveMQServerControl().clearAuthenticationCache();

         try {
            sf.createSession("tempuser", "temppass", false, true, true, false, 0);
            fail("should have thrown exception after user removal");
         } catch (ActiveMQException e) {
            // expected
         }

         assertNoSQLExceptions(loggerHandler);
      }
   }

   @TestTemplate
   public void testUpdateUserPassword() throws Exception {
      ActiveMQServer server = createSecuredServer();

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();

         server.getActiveMQServerControl().addUser("updateme", "oldpass", "users", false);

         ServerLocator locator = createNettyNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);

         ClientSession session = sf.createSession("updateme", "oldpass", false, true, true, false, 0);
         session.close();

         server.getActiveMQServerControl().resetUser("updateme", "newpass", "users", false);

         server.getActiveMQServerControl().clearAuthorizationCache();
         server.getActiveMQServerControl().clearAuthenticationCache();

         try {
            sf.createSession("updateme", "oldpass", false, true, true, false, 0);
            fail("old password should no longer work");
         } catch (ActiveMQException e) {
            // expected
         }

         session = sf.createSession("updateme", "newpass", false, true, true, false, 0);
         session.close();

         assertNoSQLExceptions(loggerHandler);
      }
   }

   @TestTemplate
   public void testListUsers() throws Exception {
      ActiveMQServer server = createSecuredServer();

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();

         server.getActiveMQServerControl().addUser("user1", "pass1", "role1", false);
         server.getActiveMQServerControl().addUser("user2", "pass2", "role2", false);

         String allUsers = server.getActiveMQServerControl().listUser("");
         assertNotNull(allUsers);
         assertTrue(allUsers.contains("admin"));
         assertTrue(allUsers.contains("user1"));
         assertTrue(allUsers.contains("user2"));

         assertNoSQLExceptions(loggerHandler);
      }
   }

   // --- Persistence tests ---

   @TestTemplate
   public void testUsersPersistAcrossRestart() throws Exception {
      ActiveMQServer server = createSecuredServer();

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();

         server.getActiveMQServerControl().addUser("persistuser", "persistpass", "users", false);

         ServerLocator locator = createNettyNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);

         ClientSession session = sf.createSession("persistuser", "persistpass", false, true, true, false, 0);
         session.close();
         sf.close();
         locator.close();

         assertNoSQLExceptions(loggerHandler);
      }

      server.stop();

      server = createSecuredServer();

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();
         assertNoSQLExceptions(loggerHandler);
      }

      ServerLocator locator = createNettyNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession("persistuser", "persistpass", false, true, true, false, 0);
      session.close();
   }

   @TestTemplate
   public void testUsersPersistAcrossRestartWithRoles() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("persistRoleAddr");
      final SimpleString QUEUE = SimpleString.of("persistRoleQueue");

      ActiveMQServer server = createSecuredServer();
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("producers", true, false, true, false, false, false, false, false, true, true, false, false));
      server.getConfiguration().putSecurityRoles("#", roles);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();

         server.getActiveMQServerControl().addUser("puser", "ppass", "producers", false);

         server.addAddressInfo(new AddressInfo(ADDRESS, RoutingType.ANYCAST));
         server.createQueue(QueueConfiguration.of(QUEUE).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST));

         ServerLocator locator = createNettyNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession("puser", "ppass", false, true, true, false, 0);
         ClientProducer producer = session.createProducer(ADDRESS);
         producer.send(session.createMessage(true));
         session.close();
         sf.close();
         locator.close();

         assertNoSQLExceptions(loggerHandler);
      }

      server.stop();

      server = createSecuredServer();
      server.getConfiguration().putSecurityRoles("#", roles);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();
         assertNoSQLExceptions(loggerHandler);
      }

      ServerLocator locator = createNettyNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession("puser", "ppass", false, true, true, false, 0);
      ClientProducer producer = session.createProducer(ADDRESS);
      producer.send(session.createMessage(true));
      session.close();

      assertEquals(2, server.locateQueue(QUEUE).getMessageCount());
   }

   // --- Comprehensive authorization tests (all 8 permission types, from BasicSecurityManagerTest) ---

   @TestTemplate
   public void testAuthorizationPositive() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("address");
      final SimpleString DURABLE_QUEUE = SimpleString.of("durableQueue");
      final SimpleString NON_DURABLE_QUEUE = SimpleString.of("nonDurableQueue");

      ActiveMQServer server = createSecuredServer();
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("admins", true, true, true, true, true, true, true, true, true, true, false, false));
      server.getConfiguration().putSecurityRoles("#", roles);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();

         ServerLocator locator = createNettyNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = addClientSession(sf.createSession("admin", "admin123", false, true, true, false, 0));

         // CREATE_DURABLE_QUEUE
         session.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS));

         // DELETE_DURABLE_QUEUE
         session.deleteQueue(DURABLE_QUEUE);

         // CREATE_NON_DURABLE_QUEUE
         session.createQueue(QueueConfiguration.of(NON_DURABLE_QUEUE).setAddress(ADDRESS).setDurable(false));

         // DELETE_NON_DURABLE_QUEUE
         session.deleteQueue(NON_DURABLE_QUEUE);

         session.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS));

         // PRODUCE
         ClientProducer producer = session.createProducer(ADDRESS);
         producer.send(session.createMessage(true));

         // CONSUME
         session.createConsumer(DURABLE_QUEUE);

         // MANAGE
         ClientProducer managementProducer = session.createProducer(server.getConfiguration().getManagementAddress());
         managementProducer.send(session.createMessage(true));

         // BROWSE
         session.createConsumer(DURABLE_QUEUE, true);

         session.close();

         assertNoSQLExceptions(loggerHandler);
      }
   }

   @TestTemplate
   public void testAuthorizationNegative() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("address");
      final SimpleString DURABLE_QUEUE = SimpleString.of("durableQueue");
      final SimpleString NON_DURABLE_QUEUE = SimpleString.of("nonDurableQueue");

      ActiveMQServer server = createSecuredServer();
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("admins", false, false, false, false, false, false, false, false, false, false, false, false));
      server.getConfiguration().putSecurityRoles("#", roles);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();

         server.addAddressInfo(new AddressInfo(ADDRESS, RoutingType.ANYCAST));
         server.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST));
         server.createQueue(QueueConfiguration.of(NON_DURABLE_QUEUE).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST).setDurable(false));

         ServerLocator locator = createNettyNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = addClientSession(sf.createSession("admin", "admin123", false, true, true, false, 0));

         // CREATE_DURABLE_QUEUE
         try {
            session.createQueue(QueueConfiguration.of(DURABLE_QUEUE).setAddress(ADDRESS));
            fail("should throw exception here");
         } catch (ActiveMQException e) {
            assertTrue(e.getMessage().contains("does not have permission='CREATE_DURABLE_QUEUE'"));
         }

         // DELETE_DURABLE_QUEUE
         try {
            session.deleteQueue(DURABLE_QUEUE);
            fail("should throw exception here");
         } catch (ActiveMQException e) {
            assertTrue(e.getMessage().contains("does not have permission='DELETE_DURABLE_QUEUE'"));
         }

         // CREATE_NON_DURABLE_QUEUE
         try {
            session.createQueue(QueueConfiguration.of(NON_DURABLE_QUEUE).setAddress(ADDRESS).setDurable(false));
            fail("should throw exception here");
         } catch (ActiveMQException e) {
            assertTrue(e.getMessage().contains("does not have permission='CREATE_NON_DURABLE_QUEUE'"));
         }

         // DELETE_NON_DURABLE_QUEUE
         try {
            session.deleteQueue(NON_DURABLE_QUEUE);
            fail("should throw exception here");
         } catch (ActiveMQException e) {
            assertTrue(e.getMessage().contains("does not have permission='DELETE_NON_DURABLE_QUEUE'"));
         }

         // PRODUCE
         try {
            ClientProducer producer = session.createProducer(ADDRESS);
            producer.send(session.createMessage(true));
            fail("should throw exception here");
         } catch (ActiveMQException e) {
            assertTrue(e.getMessage().contains("does not have permission='SEND'"));
         }

         // CONSUME
         try {
            session.createConsumer(DURABLE_QUEUE);
            fail("should throw exception here");
         } catch (ActiveMQException e) {
            assertTrue(e.getMessage().contains("does not have permission='CONSUME'"));
         }

         // MANAGE
         try {
            ClientProducer producer = session.createProducer(server.getConfiguration().getManagementAddress());
            producer.send(session.createMessage(true));
            fail("should throw exception here");
         } catch (ActiveMQException e) {
            assertTrue(e.getMessage().contains("does not have permission='MANAGE'"));
         }

         // BROWSE
         try {
            session.createConsumer(DURABLE_QUEUE, true);
            fail("should throw exception here");
         } catch (ActiveMQException e) {
            assertTrue(e.getMessage().contains("does not have permission='BROWSE'"));
         }

         session.close();

         assertNoSQLExceptions(loggerHandler);
      }
   }

   // --- Role-based tests ---

   @TestTemplate
   public void testMultipleUsersWithDifferentRoles() throws Exception {
      final SimpleString ADDRESS = SimpleString.of("roleAddress");
      final SimpleString QUEUE = SimpleString.of("roleQueue");

      ActiveMQServer server = createSecuredServer();
      Set<Role> roles = new HashSet<>();
      roles.add(new Role("senders", true, false, true, false, false, false, false, false, true, true, false, false));
      roles.add(new Role("receivers", false, true, false, false, false, false, false, true, true, true, false, false));
      server.getConfiguration().putSecurityRoles("#", roles);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();

         server.getActiveMQServerControl().addUser("sender", "senderpass", "senders", false);
         server.getActiveMQServerControl().addUser("receiver", "receiverpass", "receivers", false);

         server.addAddressInfo(new AddressInfo(ADDRESS, RoutingType.ANYCAST));
         server.createQueue(QueueConfiguration.of(QUEUE).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST));

         ServerLocator locator = createNettyNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);

         ClientSession senderSession = addClientSession(sf.createSession("sender", "senderpass", false, true, true, false, 0));
         ClientProducer producer = senderSession.createProducer(ADDRESS);
         producer.send(senderSession.createMessage(true));

         try {
            senderSession.createConsumer(QUEUE);
            fail("sender should not be able to consume");
         } catch (ActiveMQException e) {
            assertTrue(e.getMessage().contains("does not have permission='CONSUME'"));
         }
         senderSession.close();

         ClientSession receiverSession = addClientSession(sf.createSession("receiver", "receiverpass", false, true, true, false, 0));

         try {
            ClientProducer receiverProducer = receiverSession.createProducer(ADDRESS);
            receiverProducer.send(receiverSession.createMessage(true));
            fail("receiver should not be able to send");
         } catch (ActiveMQException e) {
            assertTrue(e.getMessage().contains("does not have permission='SEND'"));
         }

         ClientConsumer consumer = receiverSession.createConsumer(QUEUE);
         receiverSession.start();
         assertNotNull(consumer.receive(5000));
         receiverSession.close();

         assertNoSQLExceptions(loggerHandler);
      }
   }

   // --- Direct StorageManager tests (runs on both Journal and Database) ---

   @TestTemplate
   public void testStoreUserAndRoleDirectly() throws Exception {
      ActiveMQServer server = createServer(true, configuration);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();
         StorageManager storageManager = server.getStorageManager();

         storageManager.storeUser(new PersistedUser("user1", "pass1"));
         assertTrue(storageManager.getContext().waitCompletion(5000));
         assertNoSQLExceptions(loggerHandler);

         storageManager.storeRole(new PersistedRole("user1", Arrays.asList("admin", "developer")));
         assertTrue(storageManager.getContext().waitCompletion(5000));
         assertNoSQLExceptions(loggerHandler);

         storageManager.storeUser(new PersistedUser("user2", "pass2"));
         assertTrue(storageManager.getContext().waitCompletion(5000));

         storageManager.storeRole(new PersistedRole("user2", Arrays.asList("guest")));
         assertTrue(storageManager.getContext().waitCompletion(5000));

         assertEquals(2, storageManager.getPersistedUsers().size());
         assertEquals(2, storageManager.getPersistedRoles().size());

         assertNotNull(storageManager.getPersistedUsers().get("user1"));
         assertEquals("pass1", storageManager.getPersistedUsers().get("user1").getPassword());

         assertNotNull(storageManager.getPersistedUsers().get("user2"));
         assertEquals("pass2", storageManager.getPersistedUsers().get("user2").getPassword());

         PersistedRole role1 = storageManager.getPersistedRoles().get("user1");
         assertNotNull(role1);
         assertEquals(2, role1.getRoles().size());
         assertTrue(role1.getRoles().contains("admin"));
         assertTrue(role1.getRoles().contains("developer"));

         PersistedRole role2 = storageManager.getPersistedRoles().get("user2");
         assertNotNull(role2);
         assertEquals(1, role2.getRoles().size());
         assertTrue(role2.getRoles().contains("guest"));

         if (database != Database.JOURNAL) {
            try (Connection connection = storageConfiguration.getDatabaseProvider().getConnection()) {
               int configCount = selectCount(connection, "DB_CONFIG_DATA");
               logger.debug("DB_CONFIG_DATA row count after store: {}", configCount);
               assertTrue(configCount >= 4, "Expected at least 4 rows (2 users + 2 roles + id counter), got " + configCount);
            }
         }
      }
      server.stop();

      server = createServer(true, configuration);
      server.start();
      try {
         StorageManager storageManager2 = server.getStorageManager();

         assertEquals(2, storageManager2.getPersistedUsers().size());
         assertEquals(2, storageManager2.getPersistedRoles().size());

         assertEquals("pass1", storageManager2.getPersistedUsers().get("user1").getPassword());
         assertEquals("pass2", storageManager2.getPersistedUsers().get("user2").getPassword());

         assertTrue(storageManager2.getPersistedRoles().get("user1").getRoles().contains("admin"));
         assertTrue(storageManager2.getPersistedRoles().get("user2").getRoles().contains("guest"));
      } finally {
         server.stop();
      }
   }

   @TestTemplate
   public void testDeleteUserAndRoleDirectly() throws Exception {
      ActiveMQServer server = createServer(true, configuration);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();
         StorageManager storageManager = server.getStorageManager();

         storageManager.storeUser(new PersistedUser("userToDelete", "temppass"));
         storageManager.storeRole(new PersistedRole("userToDelete", Arrays.asList("tempRole")));
         assertTrue(storageManager.getContext().waitCompletion(5000));

         assertEquals(1, storageManager.getPersistedUsers().size());
         assertEquals(1, storageManager.getPersistedRoles().size());

         storageManager.deleteUser("userToDelete");
         storageManager.deleteRole("userToDelete");
         assertTrue(storageManager.getContext().waitCompletion(5000));

         assertEquals(0, storageManager.getPersistedUsers().size());
         assertEquals(0, storageManager.getPersistedRoles().size());
         assertNull(storageManager.getPersistedUsers().get("userToDelete"));
         assertNull(storageManager.getPersistedRoles().get("userToDelete"));

         assertNoSQLExceptions(loggerHandler);
      }
      server.stop();

      server = createServer(true, configuration);
      server.start();
      try {
         StorageManager storageManager2 = server.getStorageManager();

         assertEquals(0, storageManager2.getPersistedUsers().size());
         assertEquals(0, storageManager2.getPersistedRoles().size());
      } finally {
         server.stop();
      }
   }

   @TestTemplate
   public void testUpdateUserDirectly() throws Exception {
      ActiveMQServer server = createServer(true, configuration);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         server.start();
         StorageManager storageManager = server.getStorageManager();

         storageManager.storeUser(new PersistedUser("updateUser", "oldPassword"));
         storageManager.storeRole(new PersistedRole("updateUser", Arrays.asList("oldRole")));
         assertTrue(storageManager.getContext().waitCompletion(5000));

         storageManager.deleteUser("updateUser");
         storageManager.storeUser(new PersistedUser("updateUser", "newPassword"));

         storageManager.deleteRole("updateUser");
         storageManager.storeRole(new PersistedRole("updateUser", Arrays.asList("newRole1", "newRole2")));
         assertTrue(storageManager.getContext().waitCompletion(5000));

         assertEquals("newPassword", storageManager.getPersistedUsers().get("updateUser").getPassword());
         assertEquals(2, storageManager.getPersistedRoles().get("updateUser").getRoles().size());
         assertTrue(storageManager.getPersistedRoles().get("updateUser").getRoles().contains("newRole1"));
         assertTrue(storageManager.getPersistedRoles().get("updateUser").getRoles().contains("newRole2"));

         assertNoSQLExceptions(loggerHandler);
      }
      server.stop();

      server = createServer(true, configuration);
      server.start();
      try {
         StorageManager storageManager2 = server.getStorageManager();

         assertEquals("newPassword", storageManager2.getPersistedUsers().get("updateUser").getPassword());
         List<String> roles = storageManager2.getPersistedRoles().get("updateUser").getRoles();
         assertEquals(2, roles.size());
         assertTrue(roles.contains("newRole1"));
         assertTrue(roles.contains("newRole2"));
      } finally {
         server.stop();
      }
   }
}
