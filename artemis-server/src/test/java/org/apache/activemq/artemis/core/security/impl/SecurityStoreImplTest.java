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
import org.apache.activemq.artemis.core.management.impl.ManagementRemotingConnection;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager5;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.junit.jupiter.api.Test;
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
         assertFalse(handler.findText("AMQ224159"));
      }
   }
}
