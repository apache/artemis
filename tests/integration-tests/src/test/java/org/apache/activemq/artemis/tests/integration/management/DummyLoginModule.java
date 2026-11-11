/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.management;


import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import java.io.IOException;
import java.util.Map;

public class DummyLoginModule implements LoginModule {
   private Subject subject;
   private CallbackHandler callbackHandler;
   private Map<String, ?> sharedState;
   private Map<String, ?> options;

   @Override
   public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options) {
      this.subject = subject;
      this.callbackHandler = callbackHandler;
      this.sharedState = sharedState;
      this.options = options;
   }

   @Override
   public boolean login() throws LoginException {
      Callback[] callbacks = new Callback[2];

      callbacks[0] = new NameCallback("Username: ");
      callbacks[1] = new PasswordCallback("Password: ", false);
      try {
         callbackHandler.handle(callbacks);
      } catch (IOException ioe) {
         throw new LoginException(ioe.getMessage());
      } catch (UnsupportedCallbackException uce) {
         throw new LoginException(uce.getMessage() + " not available to obtain information from user");
      }
      String user = ((NameCallback) callbacks[0]).getName();
      char[] tmpPassword = ((PasswordCallback) callbacks[1]).getPassword();
      if (tmpPassword == null) {
         tmpPassword = new char[0];
      }
      if (user == null) {
         throw new FailedLoginException("User is null");
      }
      subject.getPrincipals().add(new RolePrincipal("amq"));
      // String password = users.getProperty(user);

     /*if (password == null) {
        throw new FailedLoginException("User does not exist: " + user);
     }*/

      return true;
   }

   @Override
   public boolean commit() throws LoginException {
      return true;
   }

   @Override
   public boolean abort() throws LoginException {
      return false;
   }

   @Override
   public boolean logout() throws LoginException {
      return false;
   }
}
