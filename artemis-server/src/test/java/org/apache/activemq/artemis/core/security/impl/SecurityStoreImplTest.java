/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.security.impl;

import javax.security.auth.Subject;
import java.security.Principal;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.management.impl.ManagementRemotingConnection;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.core.server.management.NotificationService;
import org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager5;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SecurityStoreImplTest {

   final ActiveMQSecurityManager5 securityManager = new ActiveMQSecurityManager5() {
      @Override
      public Subject authenticate(String user,
                                  String password,
                                  RemotingConnection remotingConnection,
                                  String securityDomain) {
         Subject subject = new Subject();
         subject.getPrincipals().add(new UserPrincipal(user));
         return subject;
      }

      @Override
      public boolean authorize(Subject subject, Set<Role> roles, CheckType checkType, String address) {
         return true;
      }

      @Override
      public boolean validateUser(String user, String password) {
         return false;
      }

      @Override
      public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
         return false;
      }
   };

   final ActiveMQSecurityManager5 wrongPrincipalSecurityManager = new ActiveMQSecurityManager5() {
      @Override
      public Subject authenticate(String user,
                                  String password,
                                  RemotingConnection remotingConnection,
                                  String securityDomain) {
         Subject subject = new Subject();
         subject.getPrincipals().add(new Principal() {
            @Override
            public String getName() {
               return "wrong";
            }
         });
         return subject;
      }

      @Override
      public boolean authorize(Subject subject, Set<Role> roles, CheckType checkType, String address) {
         return true;
      }

      @Override
      public boolean validateUser(String user, String password) {
         return false;
      }

      @Override
      public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
         return false;
      }
   };

   @Test
   public void zeroCacheSizeTest() throws Exception {
      final String user = RandomUtil.randomUUIDString();
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), securityManager, 999, true, "", null, null, 0, 0);
      assertNull(securityStore.getAuthenticationCache());
      assertEquals(user, securityStore.authenticate(user, RandomUtil.randomUUIDString(), null));
      assertEquals(0, securityStore.getAuthenticationCacheSize());
      securityStore.invalidateAuthenticationCache(); // ensure this doesn't throw an NPE

      assertNull(securityStore.getAuthorizationCache());
      securityStore.check(RandomUtil.randomUUIDSimpleString(), CheckType.SEND, new SecurityAuth() {
         @Override
         public String getUsername() {
            return RandomUtil.randomUUIDString();
         }

         @Override
         public String getPassword() {
            return RandomUtil.randomUUIDString();
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return null;
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      });
      assertEquals(0, securityStore.getAuthorizationCacheSize());
      securityStore.invalidateAuthorizationCache(); // ensure this doesn't throw an NPE
   }

   @Test
   public void getCaller() throws Exception {
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), securityManager, 999, true, "", null, null, 0, 0);

      assertNull(securityStore.getCaller(null, null));
      assertEquals("joe", securityStore.getCaller("joe", null));
      Subject subject = new Subject();
      assertEquals("joe", securityStore.getCaller("joe", subject));
      subject.getPrincipals().add(new RolePrincipal("r"));
      assertEquals("joe", securityStore.getCaller("joe", subject));
      assertNull(securityStore.getCaller(null, subject));
      subject.getPrincipals().add(new UserPrincipal("u"));
      assertEquals("u", securityStore.getCaller(null, subject));
      assertEquals("joe", securityStore.getCaller("joe", subject));
   }

   @Test
   public void testManagementAuthorizationAfterNullAuthenticationFailure() throws Exception {
      ActiveMQSecurityManager5 securityManager = Mockito.mock(ActiveMQSecurityManager5.class);
      Mockito.when(securityManager.authorize(ArgumentMatchers.any(Subject.class),
          ArgumentMatchers.isNull(),
          ArgumentMatchers.any(CheckType.class),
          ArgumentMatchers.anyString())).
          thenReturn(true);

      SecurityStoreImpl securityStore = new SecurityStoreImpl(
          new HierarchicalObjectRepository<>(),
          securityManager,
          10000,
          true,
          "",
          null,
          null,
          1000,
          1000);

      try {
         securityStore.authenticate(null, null, Mockito.mock(RemotingConnection.class), null);
         fail("Authentication must fail");
      } catch (Throwable t) {
         assertEquals(ActiveMQSecurityException.class, t.getClass());
      }

      SecurityAuth session = Mockito.mock(SecurityAuth.class);
      Mockito.when(session.getRemotingConnection()).thenReturn(new ManagementRemotingConnection());

      Subject viewSubject = new Subject();
      viewSubject.getPrincipals().add(new UserPrincipal("v"));
      viewSubject.getPrincipals().add(new RolePrincipal("viewers"));

      Boolean checkResult = SecurityManagerShim.callAs(viewSubject, (Callable<Boolean>) () -> {
         try {
            securityStore.check(SimpleString.of("test"), CheckType.VIEW, session);
            return true;
         } catch (Exception ignore) {
            return false;
         }
      });

      assertNotNull(checkResult);
      assertTrue(checkResult);
   }

   @Test
   public void testWrongPrincipal() throws Exception {
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), wrongPrincipalSecurityManager, 999, true, "", null, null, 10, 0);
      try {
         securityStore.authenticate(null, null, Mockito.mock(RemotingConnection.class), null);
         fail();
      } catch (ActiveMQSecurityException ignored) {
         // ignored
      }
      assertEquals(0, securityStore.getAuthenticationSuccessCount());
      assertEquals(1, securityStore.getAuthenticationFailureCount());
      assertEquals(0, securityStore.getAuthenticationCacheSize());
   }

   @Test
   public void testCacheAlgorithm() throws Exception {
      final String user = RandomUtil.randomUUIDString();
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), securityManager, 999, true, "", null, null, 0, 0);
      try (AssertionLoggerHandler handler = new AssertionLoggerHandler()) {
         securityStore.createAuthenticationCacheKey(user, RandomUtil.randomUUIDString(), null);
         assertFalse(handler.findText("AMQ224163"));
      }
   }

   @Test
   public void testHasPermissionSecurityDisabled() throws Exception {
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), securityManager, 999, false, "", null, null, 0, 0);

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return "user";
         }

         @Override
         public String getPassword() {
            return "pass";
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return null;
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      assertTrue(securityStore.hasPermission(SimpleString.of("test.address"), null, CheckType.SEND, session));
   }

   @Test
   public void testHasPermissionClusterUser() throws Exception {
      final String clusterUser = "clusterUser";
      final String clusterPassword = "clusterPassword";
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), securityManager, 999, true, clusterUser, clusterPassword, null, 0, 0);

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return clusterUser;
         }

         @Override
         public String getPassword() {
            return clusterPassword;
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return null;
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      assertTrue(securityStore.hasPermission(SimpleString.of("test.address"), null, CheckType.SEND, session));
   }

   @Test
   public void testHasPermissionAuthenticationFailure() throws Exception {
      ActiveMQSecurityManager5 nullSubjectManager = new ActiveMQSecurityManager5() {
         @Override
         public Subject authenticate(String user, String password, RemotingConnection remotingConnection, String securityDomain) {
            return null; // Simulate authentication failure
         }

         @Override
         public boolean authorize(Subject subject, Set<Role> roles, CheckType checkType, String address) {
            return true;
         }

         @Override
         public boolean validateUser(String user, String password) {
            return false;
         }

         @Override
         public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
            return false;
         }
      };

      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), nullSubjectManager, 999, true, "", null, null, 0, 0);

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return "user";
         }

         @Override
         public String getPassword() {
            return "pass";
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return Mockito.mock(RemotingConnection.class);
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      try {
         securityStore.hasPermission(SimpleString.of("test.address"), null, CheckType.SEND, session);
         fail("Should throw authentication exception");
      } catch (ActiveMQSecurityException e) {
         // Expected
         assertEquals(1, securityStore.getAuthenticationFailureCount());
      }
   }

   @Test
   public void testHasPermissionAuthorized() throws Exception {
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), securityManager, 999, true, "", null, null, 10, 10);

      final String user = "authorizedUser";
      securityStore.authenticate(user, "password", Mockito.mock(RemotingConnection.class));

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return user;
         }

         @Override
         public String getPassword() {
            return "password";
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return Mockito.mock(RemotingConnection.class);
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      assertTrue(securityStore.hasPermission(SimpleString.of("test.address"), null, CheckType.SEND, session));
   }

   @Test
   public void testHasPermissionNotAuthorized() throws Exception {
      ActiveMQSecurityManager5 denyingManager = new ActiveMQSecurityManager5() {
         @Override
         public Subject authenticate(String user, String password, RemotingConnection remotingConnection, String securityDomain) {
            Subject subject = new Subject();
            subject.getPrincipals().add(new UserPrincipal(user));
            return subject;
         }

         @Override
         public boolean authorize(Subject subject, Set<Role> roles, CheckType checkType, String address) {
            return false; // Deny authorization
         }

         @Override
         public boolean validateUser(String user, String password) {
            return false;
         }

         @Override
         public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
            return false;
         }
      };

      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), denyingManager, 999, true, "", null, null, 10, 10);

      final String user = "unauthorizedUser";
      securityStore.authenticate(user, "password", Mockito.mock(RemotingConnection.class));

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return user;
         }

         @Override
         public String getPassword() {
            return "password";
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return Mockito.mock(RemotingConnection.class);
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      assertFalse(securityStore.hasPermission(SimpleString.of("test.address"), null, CheckType.SEND, session));
   }

   @Test
   public void testHasPermissionUsesCache() throws Exception {
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), securityManager, 999, true, "", null, null, 10, 10);

      final String user = "cachedUser";
      RemotingConnection connection = Mockito.mock(RemotingConnection.class);
      securityStore.authenticate(user, "password", connection);

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return user;
         }

         @Override
         public String getPassword() {
            return "password";
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return connection;
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      SimpleString address = SimpleString.of("test.address");

      // First call - should authorize and cache
      assertTrue(securityStore.hasPermission(address, null, CheckType.SEND, session));
      assertEquals(1, securityStore.getAuthorizationCacheSize());

      // Second call - should use cache
      assertTrue(securityStore.hasPermission(address, null, CheckType.SEND, session));
      assertEquals(1, securityStore.getAuthorizationCacheSize());
   }

   @Test
   public void testHasPermissionNoSideEffects() throws Exception {
      ActiveMQSecurityManager5 denyingManager = new ActiveMQSecurityManager5() {
         @Override
         public Subject authenticate(String user, String password, RemotingConnection remotingConnection, String securityDomain) {
            Subject subject = new Subject();
            subject.getPrincipals().add(new UserPrincipal(user));
            return subject;
         }

         @Override
         public boolean authorize(Subject subject, Set<Role> roles, CheckType checkType, String address) {
            return false; // Deny
         }

         @Override
         public boolean validateUser(String user, String password) {
            return false;
         }

         @Override
         public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
            return false;
         }
      };

      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), denyingManager, 999, true, "", null, null, 10, 10);

      final String user = "deniedUser";
      securityStore.authenticate(user, "password", Mockito.mock(RemotingConnection.class));

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return user;
         }

         @Override
         public String getPassword() {
            return "password";
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return Mockito.mock(RemotingConnection.class);
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      // hasPermission should return false without incrementing failure counter
      long initialFailureCount = securityStore.getAuthorizationFailureCount();
      assertFalse(securityStore.hasPermission(SimpleString.of("test.address"), null, CheckType.SEND, session));
      assertEquals(initialFailureCount, securityStore.getAuthorizationFailureCount(), "hasPermission should not increment failure counter");
   }

   @Test
   public void testCheckSecurityDisabled() throws Exception {
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), securityManager, 999, false, "", null, null, 0, 0);

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return "user";
         }

         @Override
         public String getPassword() {
            return "pass";
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return null;
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      // Should not throw exception when security is disabled
      securityStore.check(SimpleString.of("test.address"), null, CheckType.SEND, session);
      assertEquals(0, securityStore.getAuthorizationSuccessCount());
      assertEquals(0, securityStore.getAuthorizationFailureCount());
   }

   @Test
   public void testCheckAuthorizedIncrementsSuccessCounter() throws Exception {
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), securityManager, 999, true, "", null, null, 10, 10);

      final String user = "authorizedUser";
      RemotingConnection connection = Mockito.mock(RemotingConnection.class);
      securityStore.authenticate(user, "password", connection);

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return user;
         }

         @Override
         public String getPassword() {
            return "password";
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return connection;
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      long initialSuccessCount = securityStore.getAuthorizationSuccessCount();
      securityStore.check(SimpleString.of("test.address"), null, CheckType.SEND, session);
      assertEquals(initialSuccessCount + 1, securityStore.getAuthorizationSuccessCount(), "check should increment success counter");
      assertEquals(0, securityStore.getAuthorizationFailureCount());
   }

   @Test
   public void testCheckDeniedThrowsException() throws Exception {
      ActiveMQSecurityManager5 denyingManager = new ActiveMQSecurityManager5() {
         @Override
         public Subject authenticate(String user, String password, RemotingConnection remotingConnection, String securityDomain) {
            Subject subject = new Subject();
            subject.getPrincipals().add(new UserPrincipal(user));
            return subject;
         }

         @Override
         public boolean authorize(Subject subject, Set<Role> roles, CheckType checkType, String address) {
            return false; // Deny authorization
         }

         @Override
         public boolean validateUser(String user, String password) {
            return false;
         }

         @Override
         public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
            return false;
         }
      };

      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), denyingManager, 999, true, "", null, null, 10, 10);

      final String user = "deniedUser";
      RemotingConnection connection = Mockito.mock(RemotingConnection.class);
      securityStore.authenticate(user, "password", connection);

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return user;
         }

         @Override
         public String getPassword() {
            return "password";
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return connection;
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      SimpleString address = SimpleString.of("test.address");
      long initialFailureCount = securityStore.getAuthorizationFailureCount();

      try {
         securityStore.check(address, null, CheckType.SEND, session);
         fail("Should throw ActiveMQSecurityException");
      } catch (ActiveMQSecurityException e) {
         // Expected
         assertTrue(e.getMessage().contains(user), "Exception should contain username");
         assertTrue(e.getMessage().contains(address.toString()), "Exception should contain address");
         assertTrue(e.getMessage().contains("SEND"), "Exception should contain check type");
         assertEquals(initialFailureCount + 1, securityStore.getAuthorizationFailureCount(), "check should increment failure counter");
      }
   }

   @Test
   public void testCheckDeniedWithQueueThrowsCorrectException() throws Exception {
      ActiveMQSecurityManager5 denyingManager = new ActiveMQSecurityManager5() {
         @Override
         public Subject authenticate(String user, String password, RemotingConnection remotingConnection, String securityDomain) {
            Subject subject = new Subject();
            subject.getPrincipals().add(new UserPrincipal(user));
            return subject;
         }

         @Override
         public boolean authorize(Subject subject, Set<Role> roles, CheckType checkType, String address) {
            return false;
         }

         @Override
         public boolean validateUser(String user, String password) {
            return false;
         }

         @Override
         public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
            return false;
         }
      };

      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), denyingManager, 999, true, "", null, null, 10, 10);

      final String user = "deniedUser";
      RemotingConnection connection = Mockito.mock(RemotingConnection.class);
      securityStore.authenticate(user, "password", connection);

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return user;
         }

         @Override
         public String getPassword() {
            return "password";
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return connection;
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      SimpleString address = SimpleString.of("test.address");
      SimpleString queue = SimpleString.of("test.queue");

      try {
         securityStore.check(address, queue, CheckType.CONSUME, session);
         fail("Should throw ActiveMQSecurityException");
      } catch (ActiveMQSecurityException e) {
         // Expected
         assertTrue(e.getMessage().contains(user), "Exception should contain username");
         assertTrue(e.getMessage().contains(queue.toString()), "Exception should contain queue name");
         assertTrue(e.getMessage().contains(address.toString()), "Exception should contain address");
         assertTrue(e.getMessage().contains("CONSUME"), "Exception should contain check type");
      }
   }

   @Test
   public void testCheckDelegatesClusterUserBypass() throws Exception {
      final String clusterUser = "clusterUser";
      final String clusterPassword = "clusterPassword";
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), securityManager, 999, true, clusterUser, clusterPassword, null, 0, 0);

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return clusterUser;
         }

         @Override
         public String getPassword() {
            return clusterPassword;
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return null;
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      long initialSuccessCount = securityStore.getAuthorizationSuccessCount();
      securityStore.check(SimpleString.of("test.address"), null, CheckType.SEND, session);
      assertEquals(initialSuccessCount + 1, securityStore.getAuthorizationSuccessCount(), "Cluster user should increment success counter");
   }

   @Test
   public void testCheckCachesSuccessfulAuthorization() throws Exception {
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), securityManager, 999, true, "", null, null, 10, 10);

      final String user = "cachedUser";
      RemotingConnection connection = Mockito.mock(RemotingConnection.class);
      securityStore.authenticate(user, "password", connection);

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return user;
         }

         @Override
         public String getPassword() {
            return "password";
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return connection;
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      SimpleString address = SimpleString.of("test.address");

      // First check should cache
      securityStore.check(address, null, CheckType.SEND, session);
      assertEquals(1, securityStore.getAuthorizationCacheSize());

      // Second check should use cache
      securityStore.check(address, null, CheckType.SEND, session);
      assertEquals(1, securityStore.getAuthorizationCacheSize());
   }

   @Test
   public void testCheckSendsNotificationOnFailure() throws Exception {
      ActiveMQSecurityManager5 denyingManager = new ActiveMQSecurityManager5() {
         @Override
         public Subject authenticate(String user, String password, RemotingConnection remotingConnection, String securityDomain) {
            Subject subject = new Subject();
            subject.getPrincipals().add(new UserPrincipal(user));
            return subject;
         }

         @Override
         public boolean authorize(Subject subject, Set<Role> roles, CheckType checkType, String address) {
            return false; // Deny
         }

         @Override
         public boolean validateUser(String user, String password) {
            return false;
         }

         @Override
         public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
            return false;
         }
      };

      NotificationService notificationService = Mockito.mock(NotificationService.class);
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), denyingManager, 999, true, "", null, notificationService, 10, 10);

      final String user = "deniedUser";
      RemotingConnection connection = Mockito.mock(RemotingConnection.class);
      Mockito.when(connection.getSubject()).thenReturn(new Subject());
      securityStore.authenticate(user, "password", connection);

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return user;
         }

         @Override
         public String getPassword() {
            return "password";
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return connection;
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      SimpleString address = SimpleString.of("test.address");

      try {
         securityStore.check(address, null, CheckType.SEND, session);
         fail("Should throw ActiveMQSecurityException");
      } catch (ActiveMQSecurityException e) {
         // Expected
      }

      // Verify notification was sent
      ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
      Mockito.verify(notificationService, Mockito.times(1)).sendNotification(notificationCaptor.capture());

      Notification notification = notificationCaptor.getValue();
      assertEquals(CoreNotificationType.SECURITY_PERMISSION_VIOLATION, notification.getType());
      assertEquals(address, notification.getProperties().getSimpleStringProperty(ManagementHelper.HDR_ADDRESS));
      assertEquals(SimpleString.of(CheckType.SEND.toString()), notification.getProperties().getSimpleStringProperty(ManagementHelper.HDR_CHECK_TYPE));
      assertEquals(SimpleString.of(user), notification.getProperties().getSimpleStringProperty(ManagementHelper.HDR_USER));
   }

   @Test
   public void testCheckSendsNotificationWithQueueInfo() throws Exception {
      ActiveMQSecurityManager5 denyingManager = new ActiveMQSecurityManager5() {
         @Override
         public Subject authenticate(String user, String password, RemotingConnection remotingConnection, String securityDomain) {
            Subject subject = new Subject();
            subject.getPrincipals().add(new UserPrincipal(user));
            return subject;
         }

         @Override
         public boolean authorize(Subject subject, Set<Role> roles, CheckType checkType, String address) {
            return false;
         }

         @Override
         public boolean validateUser(String user, String password) {
            return false;
         }

         @Override
         public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
            return false;
         }
      };

      NotificationService notificationService = Mockito.mock(NotificationService.class);
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), denyingManager, 999, true, "", null, notificationService, 10, 10);

      final String user = "deniedUser";
      RemotingConnection connection = Mockito.mock(RemotingConnection.class);
      Mockito.when(connection.getSubject()).thenReturn(new Subject());
      securityStore.authenticate(user, "password", connection);

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return user;
         }

         @Override
         public String getPassword() {
            return "password";
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return connection;
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      SimpleString address = SimpleString.of("test.address");

      try {
         securityStore.check(address, null, CheckType.CONSUME, session);
         fail("Should throw ActiveMQSecurityException");
      } catch (ActiveMQSecurityException e) {
         // Expected
      }

      // Verify notification was sent with correct check type
      ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
      Mockito.verify(notificationService, Mockito.times(1)).sendNotification(notificationCaptor.capture());

      Notification notification = notificationCaptor.getValue();
      assertEquals(CoreNotificationType.SECURITY_PERMISSION_VIOLATION, notification.getType());
      assertEquals(SimpleString.of(CheckType.CONSUME.toString()), notification.getProperties().getSimpleStringProperty(ManagementHelper.HDR_CHECK_TYPE));
   }

   @Test
   public void testCheckNoNotificationWhenServiceIsNull() throws Exception {
      ActiveMQSecurityManager5 denyingManager = new ActiveMQSecurityManager5() {
         @Override
         public Subject authenticate(String user, String password, RemotingConnection remotingConnection, String securityDomain) {
            Subject subject = new Subject();
            subject.getPrincipals().add(new UserPrincipal(user));
            return subject;
         }

         @Override
         public boolean authorize(Subject subject, Set<Role> roles, CheckType checkType, String address) {
            return false;
         }

         @Override
         public boolean validateUser(String user, String password) {
            return false;
         }

         @Override
         public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
            return false;
         }
      };

      // notificationService is null
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), denyingManager, 999, true, "", null, null, 10, 10);

      final String user = "deniedUser";
      RemotingConnection connection = Mockito.mock(RemotingConnection.class);
      Mockito.when(connection.getSubject()).thenReturn(new Subject());
      securityStore.authenticate(user, "password", connection);

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return user;
         }

         @Override
         public String getPassword() {
            return "password";
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return connection;
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      try {
         securityStore.check(SimpleString.of("test.address"), null, CheckType.SEND, session);
         fail("Should throw ActiveMQSecurityException");
      } catch (ActiveMQSecurityException e) {
         // Expected - should still throw exception even though no notification sent
         assertTrue(e.getMessage().contains(user));
      }
   }

   @Test
   public void testHasPermissionDoesNotSendNotification() throws Exception {
      ActiveMQSecurityManager5 denyingManager = new ActiveMQSecurityManager5() {
         @Override
         public Subject authenticate(String user, String password, RemotingConnection remotingConnection, String securityDomain) {
            Subject subject = new Subject();
            subject.getPrincipals().add(new UserPrincipal(user));
            return subject;
         }

         @Override
         public boolean authorize(Subject subject, Set<Role> roles, CheckType checkType, String address) {
            return false; // Deny
         }

         @Override
         public boolean validateUser(String user, String password) {
            return false;
         }

         @Override
         public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
            return false;
         }
      };

      NotificationService notificationService = Mockito.mock(NotificationService.class);
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), denyingManager, 999, true, "", null, notificationService, 10, 10);

      final String user = "deniedUser";
      RemotingConnection connection = Mockito.mock(RemotingConnection.class);
      securityStore.authenticate(user, "password", connection);

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return user;
         }

         @Override
         public String getPassword() {
            return "password";
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return connection;
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      // Call hasPermission - should return false
      assertFalse(securityStore.hasPermission(SimpleString.of("test.address"), null, CheckType.SEND, session));

      // Verify NO notification was sent (hasPermission has no side effects)
      Mockito.verify(notificationService, Mockito.never()).sendNotification(ArgumentMatchers.any());
   }

   @Test
   public void testCheckNoNotificationOnSuccess() throws Exception {
      NotificationService notificationService = Mockito.mock(NotificationService.class);
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), securityManager, 999, true, "", null, notificationService, 10, 10);

      final String user = "authorizedUser";
      RemotingConnection connection = Mockito.mock(RemotingConnection.class);
      securityStore.authenticate(user, "password", connection);

      SecurityAuth session = new SecurityAuth() {
         @Override
         public String getUsername() {
            return user;
         }

         @Override
         public String getPassword() {
            return "password";
         }

         @Override
         public RemotingConnection getRemotingConnection() {
            return connection;
         }

         @Override
         public String getSecurityDomain() {
            return null;
         }
      };

      // Successful check
      securityStore.check(SimpleString.of("test.address"), null, CheckType.SEND, session);

      // Verify NO notification was sent on success
      Mockito.verify(notificationService, Mockito.never()).sendNotification(ArgumentMatchers.any());
   }
}
