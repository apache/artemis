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

import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ApplyLdifFiles;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(FrameworkRunner.class)
@CreateLdapServer(transports = {@CreateTransport(protocol = "LDAP", port = 1024)})
@ApplyLdifFiles("test.ldif")
public class LDAPLoginModuleMaskPasswordTest extends AbstractLdapTestUnit {

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
   public void testLoginMaskedPassword() throws LoginException {
      LoginContext context = new LoginContext("LDAPLoginMaskedPassword", callbacks -> {
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
      context.logout();
   }

   @Test
   public void testLoginMaskedPasswordUnauthenticated() throws LoginException {
      LoginContext context = new LoginContext("LDAPLoginMaskedPassword", callbacks -> {
         for (int i = 0; i < callbacks.length; i++) {
            if (callbacks[i] instanceof NameCallback nameCallback) {
               nameCallback.setName("first");
            } else if (callbacks[i] instanceof PasswordCallback passwordCallback) {
               passwordCallback.setPassword("nosecret".toCharArray());
            } else {
               throw new UnsupportedCallbackException(callbacks[i]);
            }
         }
      });
      try {
         context.login();
      } catch (FailedLoginException le) {
         assertEquals("Password does not match for user: first", le.getMessage());
         return;
      }
      fail("Should have failed authenticating");
   }

   @Test
   public void testLoginExternalCodec() throws LoginException {
      LoginContext context = new LoginContext("LDAPLoginExternalPasswordCodec", callbacks -> {
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
      context.logout();
   }

   @Test
   public void testLoginExternalCodec2() throws LoginException {
      LoginContext context = new LoginContext("LDAPLoginExternalPasswordCodec2", callbacks -> {
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
      context.logout();
   }

   @Test
   public void testLoginExternalCodecUnauthenticated() throws LoginException {
      LoginContext context = new LoginContext("LDAPLoginExternalPasswordCodec", callbacks -> {
         for (int i = 0; i < callbacks.length; i++) {
            if (callbacks[i] instanceof NameCallback nameCallback) {
               nameCallback.setName("first");
            } else if (callbacks[i] instanceof PasswordCallback passwordCallback) {
               passwordCallback.setPassword("nosecret".toCharArray());
            } else {
               throw new UnsupportedCallbackException(callbacks[i]);
            }
         }
      });
      try {
         context.login();
      } catch (FailedLoginException le) {
         assertEquals("Password does not match for user: first", le.getMessage());
         return;
      }
      fail("Should have failed authenticating");
   }
}
