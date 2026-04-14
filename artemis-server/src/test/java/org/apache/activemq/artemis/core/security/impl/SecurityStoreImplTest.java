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
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.management.impl.ManagementRemotingConnection;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyServerConnection;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.settings.impl.AuthenticationCacheKeyConfig;
import org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager5;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.utils.CertificateUtilTest;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), securityManager, 999, true, "", null, null, 0, 0, ConfigurationImpl.DEFAULT_AUTHENTICATION_CACHE_KEY);
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
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), securityManager, 999, true, "", null, null, 0, 0, ConfigurationImpl.DEFAULT_AUTHENTICATION_CACHE_KEY);

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
          1000,
          ConfigurationImpl.DEFAULT_AUTHENTICATION_CACHE_KEY);

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
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), wrongPrincipalSecurityManager, 999, true, "", null, null, 10, 0, ConfigurationImpl.DEFAULT_AUTHENTICATION_CACHE_KEY);
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
   public void testPresenceOfCacheAlgorithm() throws Exception {
      final String user = RandomUtil.randomUUIDString();
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), securityManager, 999, true, "", null, null, 0, 0, ConfigurationImpl.DEFAULT_AUTHENTICATION_CACHE_KEY);
      try (AssertionLoggerHandler handler = new AssertionLoggerHandler()) {
         securityStore.createAuthenticationCacheKey(user, RandomUtil.randomUUIDString(), null);
         assertFalse(handler.findText("AMQ224163"));
      }
   }

   @Test
   // There's no way to conclusively prove a String is a SHA-256 hash, but we can at least check that it's the right length and has the correct format
   public void testVerifySha256() throws Exception {
      SecurityStoreImpl securityStore = new SecurityStoreImpl(new HierarchicalObjectRepository<>(), securityManager, 999, true, "", null, null, 0, 0, ConfigurationImpl.DEFAULT_AUTHENTICATION_CACHE_KEY);
      assertTrue(securityStore.createAuthenticationCacheKey(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), null).matches("^[a-fA-F0-9]{64}$"));
   }

   @Test
   public void testIncludeUpnInAuthenticationCacheKeyEnabledWithDifferentUpns() throws Exception {
      final String user = RandomUtil.randomUUIDString();
      final String password = RandomUtil.randomUUIDString();
      SecurityStoreImpl securityStore = createSecurityStore(true);
      String keyOne = securityStore.createAuthenticationCacheKey(user, password, getConnectionWithUpn("user1@domain.com"));
      String keyTwo = securityStore.createAuthenticationCacheKey(user, password, getConnectionWithUpn("user2@domain.com"));
      assertNotEquals(keyOne, keyTwo);
   }

   @Test
   public void testIncludeUpnInAuthenticationCacheKeyEnabledWithAndWithoutUpn() throws Exception {
      final String user = RandomUtil.randomUUIDString();
      final String password = RandomUtil.randomUUIDString();
      SecurityStoreImpl securityStore = createSecurityStore(true);
      String keyOne = securityStore.createAuthenticationCacheKey(user, password, getConnectionWithUpn("user@domain.com"));
      String keyTwo = securityStore.createAuthenticationCacheKey(user, password, getConnectionWithUpn(null));
      assertNotEquals(keyOne, keyTwo);
   }

   @Test
   public void testIncludeUpnInAuthenticationCacheKeyEnabledWithIdenticalUpns() throws Exception {
      final String user = RandomUtil.randomUUIDString();
      final String password = RandomUtil.randomUUIDString();
      final String upn = "user@domain.com";
      SecurityStoreImpl securityStore = createSecurityStore(true);
      String keyOne = securityStore.createAuthenticationCacheKey(user, password, getConnectionWithUpn(upn));
      String keyTwo = securityStore.createAuthenticationCacheKey(user, password, getConnectionWithUpn(upn));
      assertEquals(keyOne, keyTwo);
   }

   @Test
   public void testIncludeUpnInAuthenticationCacheKeyEnabledWithNulls() throws Exception {
      SecurityStoreImpl securityStore = createSecurityStore(true);
      assertNull(securityStore.createAuthenticationCacheKey(null, null, null));
   }

   @Test
   public void testIncludeUpnInAuthenticationCacheKeyDisabledWithDifferentUpns() throws Exception {
      final String user = RandomUtil.randomUUIDString();
      final String password = RandomUtil.randomUUIDString();
      SecurityStoreImpl securityStore = createSecurityStore(false);
      String keyOne = securityStore.createAuthenticationCacheKey(user, password, getConnectionWithUpn("user1@domain.com"));
      String keyTwo = securityStore.createAuthenticationCacheKey(user, password, getConnectionWithUpn("user2@domain.com"));
      assertEquals(keyOne, keyTwo);
   }

   @Test
   public void testIncludeUpnInAuthenticationCacheKeyDisabledWithAndWithoutUpn() throws Exception {
      final String user = RandomUtil.randomUUIDString();
      final String password = RandomUtil.randomUUIDString();
      SecurityStoreImpl securityStore = createSecurityStore(false);
      String keyOne = securityStore.createAuthenticationCacheKey(user, password, getConnectionWithUpn("user@domain.com"));
      String keyTwo = securityStore.createAuthenticationCacheKey(user, password, getConnectionWithUpn(null));
      assertEquals(keyOne, keyTwo);
   }

   @Test
   public void testIncludeUpnInAuthenticationCacheKeyDisabledWithIdenticalUpns() throws Exception {
      final String user = RandomUtil.randomUUIDString();
      final String password = RandomUtil.randomUUIDString();
      final String upn = "user@domain.com";
      SecurityStoreImpl securityStore = createSecurityStore(false);
      String keyOne = securityStore.createAuthenticationCacheKey(user, password, getConnectionWithUpn(upn));
      String keyTwo = securityStore.createAuthenticationCacheKey(user, password, getConnectionWithUpn(upn));
      assertEquals(keyOne, keyTwo);
   }

   @Test
   public void testIncludeUpnInAuthenticationCacheKeyDisabledWithNulls() throws Exception {
      SecurityStoreImpl securityStore = createSecurityStore(false);
      assertNull(securityStore.createAuthenticationCacheKey(null, null, null));
   }

   private static RemotingConnection getConnectionWithUpn(String upn) throws Exception {
      RemotingConnection remotingConnection = Mockito.mock(RemotingConnection.class);
      NettyServerConnection serverConnection = Mockito.mock(NettyServerConnection.class);
      Mockito.when(serverConnection.getPeerCertificates()).thenReturn(new X509Certificate[]{CertificateUtilTest.generateCertificateWithUPN(upn)});
      Mockito.when(remotingConnection.getTransportConnection()).thenReturn(serverConnection);
      return remotingConnection;
   }

   private SecurityStoreImpl createSecurityStore(boolean includeUpnInAuthenticationCacheKey) throws NoSuchAlgorithmException {
      EnumSet<AuthenticationCacheKeyConfig> authenticationCacheKey = EnumSet.copyOf(ConfigurationImpl.DEFAULT_AUTHENTICATION_CACHE_KEY);
      if (includeUpnInAuthenticationCacheKey) {
         authenticationCacheKey.add(AuthenticationCacheKeyConfig.TLS_SAN_UPN);
      }
      return new SecurityStoreImpl(new HierarchicalObjectRepository<>(), securityManager, 999, true, "", null, null, 1, 0, authenticationCacheKey);
   }
}
