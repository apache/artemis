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
package org.apache.activemq.artemis.core.security.jaas;

import javax.naming.Context;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.security.auth.Subject;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.security.Principal;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ApplyLdifFiles;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

@RunWith(FrameworkRunner.class)
@CreateLdapServer(transports = {@CreateTransport(protocol = "LDAP", port = 1024)})
@ApplyLdifFiles("test.ldif")
public class LDAPModuleRoleExpansionTest extends AbstractLdapTestUnit {

   private static final String PRINCIPAL = "uid=admin,ou=system";
   private static final String CREDENTIALS = "secret";
   private final String loginConfigSysPropName = "java.security.auth.login.config";
   private String oldLoginConfig;

   @Before
   public void setLoginConfigSysProperty() {
      oldLoginConfig = System.getProperty(loginConfigSysPropName, null);
      System.setProperty(loginConfigSysPropName, "src/test/resources/login.config");
   }

   @After
   public void resetLoginConfigSysProperty() {
      if (oldLoginConfig != null) {
         System.setProperty(loginConfigSysPropName, oldLoginConfig);
      }
   }

   @Test
   public void testRunning() throws Exception {

      Hashtable<String, String> env = new Hashtable<>();
      env.put(Context.PROVIDER_URL, "ldap://localhost:1024");
      env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
      env.put(Context.SECURITY_AUTHENTICATION, "simple");
      env.put(Context.SECURITY_PRINCIPAL, PRINCIPAL);
      env.put(Context.SECURITY_CREDENTIALS, CREDENTIALS);
      DirContext ctx = new InitialDirContext(env);

      Set<String> set = new HashSet<>();

      NamingEnumeration<NameClassPair> list = ctx.list("ou=system");

      while (list.hasMore()) {
         NameClassPair ncp = list.next();
         set.add(ncp.getName());
      }

      assertTrue(set.contains("uid=admin"));
      assertTrue(set.contains("ou=users"));
      assertTrue(set.contains("ou=groups"));
      assertTrue(set.contains("ou=configuration"));
      assertTrue(set.contains("prefNodeName=sysPrefRoot"));

   }

   @Test
   public void testRoleExpansion() throws LoginException {
      LoginContext context = new LoginContext("ExpandedLDAPLogin", callbacks -> {
         for (int i = 0; i < callbacks.length; i++) {
            if (callbacks[i] instanceof NameCallback nameCallback) {
               nameCallback.setName("first");
            } else if (callbacks[i] instanceof PasswordCallback passwordCallback) {
               passwordCallback.setPassword("secret".toCharArray());
            } else {
               throw new UnsupportedCallbackException(callbacks[i]);
            }
         }
      });
      context.login();
      Subject subject = context.getSubject();
      boolean isAdmin = false;
      boolean isUser = false;
      for (Principal principal : subject.getPrincipals()) {
         if (principal instanceof RolePrincipal rolePrincipal) {
            if (rolePrincipal.getName().equalsIgnoreCase("admins")) {
               isAdmin = true;
            }
            if (rolePrincipal.getName().equalsIgnoreCase("users")) {
               isUser = true;
            }
         }
      }
      // Should be in users by virtue of being in admins
      assertTrue(isAdmin && isUser);
      context.logout();
   }
}
