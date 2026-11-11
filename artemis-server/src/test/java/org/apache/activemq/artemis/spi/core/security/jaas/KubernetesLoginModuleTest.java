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
package org.apache.activemq.artemis.spi.core.security.jaas;

import static org.apache.activemq.artemis.spi.core.security.jaas.KubernetesLoginModule.K8S_ROLE_FILE_PROP_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginException;

import org.apache.activemq.artemis.spi.core.security.jaas.kubernetes.TokenCallbackHandler;
import org.apache.activemq.artemis.spi.core.security.jaas.kubernetes.client.KubernetesClient;
import org.apache.activemq.artemis.spi.core.security.jaas.kubernetes.model.TokenReview;
import org.junit.jupiter.api.Test;

public class KubernetesLoginModuleTest {

   private final KubernetesClient client = mock(KubernetesClient.class);
   private final KubernetesLoginModule loginModule = new KubernetesLoginModule(client);
   private static final String TOKEN = "the_token";

   public static final String USERNAME = "system:serviceaccounts:some-ns:kermit";
   public static final String AUTH_JSON = "{\"status\": {"
         + "\"authenticated\": true, "
         + "\"user\": {"
         + "  \"username\": \"" + USERNAME + "\""
         + "}}}";

   public static final String AUTH_JSON_WITH_GROUPS = "{\"status\": {"
      + "\"authenticated\": true, "
      + "\"user\": {"
      + "  \"username\": \"" + USERNAME + "\","
      + "  \"groups\": [\"developers\", \"qa\"]"
      + "}}}";

   public static final String UNAUTH_JSON = "{\"status\": {"
         + "\"authenticated\": false "
         + "}}";

   @Test
   public void testBasicLogin() throws LoginException {
      CallbackHandler handler = new TokenCallbackHandler(TOKEN);
      Subject subject = new Subject();
      loginModule.initialize(subject, handler, Collections.emptyMap(), getDefaultOptions());

      TokenReview tr = TokenReview.fromJsonString(AUTH_JSON);
      when(client.getTokenReview(TOKEN)).thenReturn(tr);

      assertTrue(loginModule.login());
      assertTrue(loginModule.commit());

      assertEquals(1, subject.getPrincipals(UserPrincipal.class).size());
      subject.getPrincipals(ServiceAccountPrincipal.class).forEach(p -> {
         assertEquals(USERNAME, p.getName());
         assertEquals("kermit", p.getSaName());
         assertEquals("some-ns", p.getNamespace());
      });
      Set<RolePrincipal> roles = subject.getPrincipals(RolePrincipal.class);
      assertEquals(2, roles.size());
      assertTrue(roles.contains(new RolePrincipal("muppet")));
      assertTrue(roles.contains(new RolePrincipal("admin")));

      assertTrue(loginModule.logout());
      assertFalse(loginModule.commit());
      assertEquals(0, subject.getPrincipals().size());
      verify(client, times(1)).getTokenReview(TOKEN);
   }

   @Test
   public void testFailedLogin() throws LoginException {
      CallbackHandler handler = new TokenCallbackHandler(TOKEN);
      Subject subject = new Subject();
      loginModule.initialize(subject, handler, Collections.emptyMap(), getDefaultOptions());

      TokenReview tr = TokenReview.fromJsonString(UNAUTH_JSON);
      when(client.getTokenReview(TOKEN)).thenReturn(tr);

      assertFalse(loginModule.login());
      assertFalse(loginModule.commit());
      assertEquals(0, subject.getPrincipals().size());
      verify(client, times(1)).getTokenReview(TOKEN);
   }

   @Test
   public void testNullToken() throws LoginException {
      CallbackHandler handler = new TokenCallbackHandler(null);
      Subject subject = new Subject();
      loginModule.initialize(subject, handler, Collections.emptyMap(), getDefaultOptions());

      try {
         assertFalse(loginModule.login());
         fail("Exception expected");
      } catch (LoginException e) {
         assertNotNull(e);
      }

      assertFalse(loginModule.commit());
      assertEquals(0, subject.getPrincipals().size());
      verifyNoInteractions(client);
   }

   @Test
   public void testUnableToVerifyToken() throws LoginException {
      CallbackHandler handler = new TokenCallbackHandler(TOKEN);
      Subject subject = new Subject();
      loginModule.initialize(subject, handler, Collections.emptyMap(), getDefaultOptions());

      when(client.getTokenReview(TOKEN)).thenReturn(new TokenReview());

      assertFalse(loginModule.login());
      assertFalse(loginModule.commit());
      assertEquals(0, subject.getPrincipals().size());
      verify(client, times(1)).getTokenReview(TOKEN);
   }

   @Test
   public void testRolesFromReview() throws LoginException {
      CallbackHandler handler = new TokenCallbackHandler(TOKEN);
      Subject subject = new Subject();
      loginModule.initialize(subject, handler, Collections.emptyMap(), Map.of());

      TokenReview tr = TokenReview.fromJsonString(AUTH_JSON_WITH_GROUPS);
      when(client.getTokenReview(TOKEN)).thenReturn(tr);

      assertTrue(loginModule.login());
      assertTrue(loginModule.commit());

      assertEquals(1, subject.getPrincipals(UserPrincipal.class).size());
      subject.getPrincipals(ServiceAccountPrincipal.class).forEach(p -> {
         assertEquals(USERNAME, p.getName());
         assertEquals("kermit", p.getSaName());
         assertEquals("some-ns", p.getNamespace());
      });
      Set<RolePrincipal> roles = subject.getPrincipals(RolePrincipal.class);
      assertEquals(2, roles.size());
      assertTrue(roles.contains(new RolePrincipal("developers")));
      assertTrue(roles.contains(new RolePrincipal("qa")));

      assertTrue(loginModule.logout());
      assertFalse(loginModule.commit());
      assertTrue(subject.getPrincipals().isEmpty());
      verify(client, times(1)).getTokenReview(TOKEN);
   }

   @Test
   public void testIgnoreRolesFromReview() throws LoginException {
      CallbackHandler handler = new TokenCallbackHandler(TOKEN);
      Subject subject = new Subject();
      loginModule.initialize(subject, handler, Collections.emptyMap(), Map.of("ignoreTokenReviewRoles", "true"));

      TokenReview tr = TokenReview.fromJsonString(AUTH_JSON_WITH_GROUPS);
      when(client.getTokenReview(TOKEN)).thenReturn(tr);

      assertTrue(loginModule.login());
      assertTrue(loginModule.commit());

      assertEquals(1, subject.getPrincipals(UserPrincipal.class).size());
      subject.getPrincipals(ServiceAccountPrincipal.class).forEach(p -> {
         assertEquals(USERNAME, p.getName());
         assertEquals("kermit", p.getSaName());
         assertEquals("some-ns", p.getNamespace());
      });
      Set<RolePrincipal> roles = subject.getPrincipals(RolePrincipal.class);
      assertEquals(0, roles.size());

      assertTrue(loginModule.logout());
      assertFalse(loginModule.commit());
      assertTrue(subject.getPrincipals().isEmpty());
      verify(client, times(1)).getTokenReview(TOKEN);
   }


   private Map<String, ?> getDefaultOptions() {
      String baseDirValue = new File(KubernetesLoginModuleTest.class.getClassLoader().getResource("k8s-roles.properties").getPath()).getParentFile().getAbsolutePath();
      return Map.of(K8S_ROLE_FILE_PROP_NAME, "k8s-roles.properties", "baseDir", baseDirValue);
   }
}
